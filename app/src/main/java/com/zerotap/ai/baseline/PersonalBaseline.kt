package com.zerotap.ai.baseline

import android.content.Context
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * PERSONAL BASELINE
 * Learns what "normal" looks like for THIS user (per activity), so the risk engine can tell
 * "unusual for her" from "unusual for everyone". Pure statistics: no training, no ML library.
 * Call evaluate() once per second (the service already ticks every 1000 ms).
 */

/** Named ActivityContext (not MotionContext) because com.zerotap.domain.model.MotionContext already exists. */
enum class ActivityContext { STATIONARY, WALKING, VEHICLE }

enum class BaselineFeature { ACCEL, GYRO, AUDIO }

data class BaselineResult(
    val context: ActivityContext,
    /** 0.0 = normal for this user ... 1.0 = very unusual. Already reduced during cold start. */
    val score: Double,
    /** Distance from normal in standard deviations (negative = calmer than normal). */
    val zAccel: Double,
    val zGyro: Double,
    val zAudio: Double,
    /** 0..1 how much data the baseline has (0 = just installed). */
    val confidence: Double,
    /** Consecutive seconds with score > 0.5 (temporal persistence). */
    val sustainedSeconds: Int,
    /** How many different signals are unusual at the same time. */
    val agreeingSignals: Int,
    /** How many dB louder than the last ~45 s of ambient noise (0 if no audio). */
    val audioAboveNoiseFloorDb: Double
)

interface BaselineStore {
    fun load(): String?
    fun save(data: String)
}

/** Saves the learned baseline on the phone so it survives app restarts. */
class PrefsBaselineStore(context: Context) : BaselineStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("zerotap_baseline", Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString("data", null)
    override fun save(data: String) { prefs.edit().putString("data", data).apply() }
}

/** Running mean/spread for ONE signal in ONE activity. priorN = trust in the starting guess (0 = learn from data only). */
class RunningStat(
    private val alphaMin: Double,
    private val minStd: Double,
    priorMean: Double,
    priorStd: Double,
    priorN: Int
) {
    var mean = priorMean; private set
    var variance = priorStd * priorStd; private set
    private var n = priorN.toLong()
    var realSamples = 0L; private set

    fun std(): Double = max(sqrt(variance), minStd)
    fun z(x: Double): Double = (x - mean) / std()

    fun update(x: Double) {
        n++
        realSamples++
        val alpha = max(1.0 / n, alphaMin)   // learns fast at first, then slowly adapts
        val d = x - mean
        mean += alpha * d
        variance = (1 - alpha) * (variance + alpha * d * d)
    }

    fun encode() = "$realSamples|$n|$mean|$variance"

    fun decode(s: String) {
        val p = s.split("|")
        if (p.size == 4) {
            realSamples = p[0].toLongOrNull() ?: realSamples
            n = p[1].toLongOrNull() ?: n
            mean = p[2].toDoubleOrNull() ?: mean
            variance = p[3].toDoubleOrNull() ?: variance
        }
    }
}

class PersonalBaseline(
    private val store: BaselineStore? = null,
    /** Seconds of real data before we fully trust the baseline. 120 = 2 min (demo). Use 600+ for real life. */
    private val warmupTicks: Int = 120
) {
    companion object {
        const val GRAVITY = 9.81

        fun inferContext(speedMps: Double, accelDev: Double): ActivityContext = when {
            speedMps >= 4.0 -> ActivityContext.VEHICLE                    // > ~14 km/h
            speedMps >= 0.7 || accelDev > 1.2 -> ActivityContext.WALKING   // moving on foot / phone being handled
            else -> ActivityContext.STATIONARY
        }
    }

    private val stats = HashMap<String, RunningStat>()
    // Short-term ambient noise floor, about the last 45 seconds
    private val noiseFloor = RunningStat(alphaMin = 2.0 / 46.0, minStd = 3.0, priorMean = 0.0, priorStd = 10.0, priorN = 0)
    private var sustained = 0
    private var sinceSave = 0

    init { loadFromStore() }

    private fun stat(c: ActivityContext, f: BaselineFeature): RunningStat =
        stats.getOrPut("${c.name}:${f.name}") {
            when (f) {
                // Starting guesses (units: m/s^2 deviation from gravity, rad/s). Real data replaces them within minutes.
                BaselineFeature.ACCEL -> when (c) {
                    ActivityContext.STATIONARY -> RunningStat(0.002, 0.30, 0.30, 0.30, 30)
                    ActivityContext.WALKING -> RunningStat(0.002, 0.80, 3.50, 2.00, 30)
                    ActivityContext.VEHICLE -> RunningStat(0.002, 0.60, 1.50, 1.20, 30)
                }
                BaselineFeature.GYRO -> when (c) {
                    ActivityContext.STATIONARY -> RunningStat(0.002, 0.05, 0.05, 0.10, 30)
                    ActivityContext.WALKING -> RunningStat(0.002, 0.30, 1.50, 1.00, 30)
                    ActivityContext.VEHICLE -> RunningStat(0.002, 0.20, 0.60, 0.50, 30)
                }
                // Audio scale depends on the phone mic, so learn it purely from data.
                BaselineFeature.AUDIO -> RunningStat(0.002, 3.0, 0.0, 10.0, 0)
            }
        }

    /**
     * @param accelDev how far the strongest acceleration in the last ~2 s was from gravity (m/s^2)
     * @param gyroPeak strongest rotation in the last ~2 s (rad/s)
     * @param audioDb current mic level in dB, or null if the mic isn't running
     * @param speedMps GPS speed in m/s (0 if unknown)
     * @param learn pass false while an emergency is building, so incidents never become "normal"
     */
    fun evaluate(accelDev: Double, gyroPeak: Double, audioDb: Double?, speedMps: Double, learn: Boolean): BaselineResult {
        val ctx = inferContext(speedMps, accelDev)
        val sA = stat(ctx, BaselineFeature.ACCEL)
        val sG = stat(ctx, BaselineFeature.GYRO)
        val sU = stat(ctx, BaselineFeature.AUDIO)

        // 1) score against the PAST first, 2) learn afterwards
        val zA = sA.z(accelDev)
        val zG = sG.z(gyroPeak)
        val zU = when {
            audioDb == null -> 0.0
            noiseFloor.realSamples < 5 -> 0.0                        // not enough data yet
            sU.realSamples < 20 -> noiseFloor.z(audioDb)             // long-term not ready: short-term only
            else -> min(noiseFloor.z(audioDb), sU.z(audioDb))        // must be unusual vs BOTH
        }
        val audioAbove = if (audioDb != null && noiseFloor.realSamples >= 5) audioDb - noiseFloor.mean else 0.0

        val scores = listOf(toScore(zA), toScore(zG), toScore(zU)).sortedDescending()
        // One unusual signal alone can never exceed 0.4. Two agreeing signals can reach 1.0.
        val raw = 0.4 * scores[0] + 0.6 * scores[1]
        val confidence = min(1.0, sA.realSamples.toDouble() / warmupTicks)
        val score = raw * (0.3 + 0.7 * confidence)                   // quiet during cold start

        sustained = if (score > 0.5) sustained + 1 else 0
        val agreeing = listOf(zA, zG, zU).count { toScore(it) > 0.5 }

        if (learn) {
            sA.update(accelDev)
            sG.update(gyroPeak)
            if (audioDb != null) { sU.update(audioDb); noiseFloor.update(audioDb) }
        }
        if (++sinceSave >= 30) { sinceSave = 0; save() }

        return BaselineResult(ctx, score, zA, zG, zU, confidence, sustained, agreeing, audioAbove)
    }

    /** z=2 -> 0.0, z=5 -> 1.0. Calmer-than-normal (negative z) -> 0. */
    private fun toScore(z: Double) = min(1.0, max(0.0, (z - 2.0) / 3.0))

    fun save() {
        val s = store ?: return
        val sb = StringBuilder()
        for ((k, v) in stats) sb.append(k).append('=').append(v.encode()).append('\n')
        s.save(sb.toString())
    }

    private fun loadFromStore() {
        val text = store?.load() ?: return
        for (line in text.lines()) {
            val i = line.indexOf('=')
            if (i <= 0) continue
            val parts = line.substring(0, i).split(":")
            if (parts.size != 2) continue
            val c = runCatching { ActivityContext.valueOf(parts[0]) }.getOrNull() ?: continue
            val f = runCatching { BaselineFeature.valueOf(parts[1]) }.getOrNull() ?: continue
            stat(c, f).decode(line.substring(i + 1))
        }
    }

    /** Forget everything (handy for a "reset baseline" button in the demo). */
    fun reset() { stats.clear(); sustained = 0; store?.save("") }
}
