package com.zerotap.ai.baseline

import android.content.Context
import com.zerotap.domain.model.AudioMetadata
import com.zerotap.domain.model.LocationContext
import com.zerotap.domain.model.LocationSample
import com.zerotap.domain.model.MotionContext
import com.zerotap.domain.model.RiskPredictionResult
import com.zerotap.sensor.feature.ExtractedMotionFeatures
import com.zerotap.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * ONE class the service talks to. It does three things each second:
 *   1. Personal baseline: how unusual is this moment FOR THIS USER?  -> nudges the risk score
 *   2. Recovery analysis: after a big impact, did movement resume or did the phone stay still?
 *   3. Gemma 4: when risk is elevated, ask Gemma whether it looks real or harmless -> nudges the score
 * Nothing here can cancel the countdown or send an alert. It only returns an adjusted RiskPredictionResult.
 */
class AdaptiveIncidentEngine(context: Context) {

    private val appContext = context.applicationContext
    val baseline = PersonalBaseline(PrefsBaselineStore(appContext), warmupTicks = 120)
    private val gemma = GemmaClient(loadKey(appContext))

    /** Latest values, handy for showing on a debug screen. */
    @Volatile var latestBaseline: BaselineResult? = null; private set
    @Volatile var latestGemma: GemmaVerdict? = null; private set

    private val gemmaInFlight = AtomicBoolean(false)
    private var lastGemmaCallMs = 0L

    // Recovery analysis state
    private var impactAtMs = 0L
    private var movementResumed = false

    companion object {
        /** Ask Gemma only when the on-device score is at least this (percent). */
        const val GEMMA_MIN_PERCENT = 40
        const val GEMMA_COOLDOWN_MS = 20_000L
        const val GEMMA_VALID_MS = 45_000L

        /** Reads the API key from app/src/main/assets/gemini_key.txt (this file is NOT committed to git). */
        private fun loadKey(c: Context): String = try {
            c.assets.open("gemini_key.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) { "" }
    }

    fun process(
        raw: RiskPredictionResult,
        features: ExtractedMotionFeatures?,
        audio: AudioMetadata?,
        location: LocationSample?,
        motion: MotionContext?,
        locationContext: LocationContext?,
        learn: Boolean,
        scope: CoroutineScope
    ): RiskPredictionResult {
        val now = System.currentTimeMillis()
        val factors = raw.contributingFactors.toMutableList()
        var percent = raw.scorePercent

        // ---- 1. Personal baseline ---------------------------------------------------------
        val accelDev = if (features == null) 0.0 else
            max(0.0, max(features.peakAccelMagnitude - PersonalBaseline.GRAVITY, PersonalBaseline.GRAVITY - features.minAccelMagnitude.toDouble()))
        val gyroPeak = features?.peakGyroMagnitude?.toDouble() ?: 0.0
        val audioDb = audio?.takeIf { it.isRecording }?.amplitudeDb?.toDouble()
        val speed = location?.speed?.toDouble() ?: 0.0

        val b = baseline.evaluate(accelDev, gyroPeak, audioDb, speed, learn)
        latestBaseline = b

        // factor: ~0.9x if everything is normal for this user, up to 1.3x if very unusual.
        // Multiplied by confidence, so during cold start the original score is untouched.
        val factor = (1.0 + 0.4 * (b.score - 0.25)).coerceIn(0.9, 1.3)
        val blended = 1.0 + (factor - 1.0) * b.confidence
        val afterBaseline = (percent * blended).toInt().coerceIn(0, 100)
        if (b.score >= 0.3 || abs(afterBaseline - percent) >= 3) {
            factors.add(
                "Personal baseline (%s): %.0f%% unusual for you, %d signal(s), learned %.0f%%".format(
                    b.context.name.lowercase(), b.score * 100, b.agreeingSignals, b.confidence * 100
                )
            )
        }
        percent = afterBaseline
        if (b.score >= 0.3) Logger.ai("Baseline", "ctx=${b.context} score=${"%.2f".format(b.score)} agree=${b.agreeingSignals} conf=${"%.2f".format(b.confidence)} raw=${raw.scorePercent}->$afterBaseline")

        // ---- 2. Recovery analysis ------------------------------------------------------------
        if (features != null && features.peakAccelMagnitude > 20f && now - impactAtMs > 8_000L) {
            impactAtMs = now          // a new impact
            movementResumed = false
        }
        val sinceImpactSec = if (impactAtMs > 0L) (now - impactAtMs) / 1000 else null
        if (impactAtMs > 0L && now - impactAtMs > 4_000L && (accelDev > 2.0 || gyroPeak > 1.0)) {
            movementResumed = true    // phone is moving again: picked up / user walking
        }
        if (sinceImpactSec != null && sinceImpactSec > 90) { impactAtMs = 0L; movementResumed = false }
        val stillSec = if (impactAtMs > 0L && !movementResumed && now - impactAtMs > 4_000L) sinceImpactSec else null

        // ---- 3. Gemma 4 ------------------------------------------------------------------------
        if (gemma.isConfigured && percent >= GEMMA_MIN_PERCENT &&
            now - lastGemmaCallMs > GEMMA_COOLDOWN_MS && gemmaInFlight.compareAndSet(false, true)
        ) {
            lastGemmaCallMs = now
            val f = IncidentFeatures(
                suspectedType = if (speed >= 4.0) "possible vehicle accident" else "personal safety / distress",
                userActivity = b.context.name,
                currentMotion = describeMotion(motion),
                speedKmh = speed * 3.6,
                impactPeakMs2 = features?.peakAccelMagnitude?.toDouble(),
                gyroPeakRads = features?.peakGyroMagnitude?.toDouble(),
                audioDbAboveNoiseFloor = if (audioDb != null) b.audioAboveNoiseFloorDb else null,
                onDeviceRiskPercent = percent,
                baselineScore = b.score,
                baselineZAccel = b.zAccel,
                baselineZGyro = b.zGyro,
                baselineZAudio = b.zAudio,
                independentSignalsUnusual = b.agreeingSignals,
                secondsUnusualInARow = b.sustainedSeconds,
                secondsSinceImpact = sinceImpactSec,
                stillSinceImpactSeconds = stillSec,
                movementResumedAfterImpact = if (impactAtMs > 0L) movementResumed else null,
                unexpectedStop = locationContext?.isUnexpectedStop
            )
            scope.launch(Dispatchers.IO) {
                try {
                    val v = gemma.classify(f)
                    latestGemma = v
                    Logger.ai("Gemma", "${v.source} -> ${v.label} (${"%.2f".format(v.confidence)}): ${v.reason}")
                } finally { gemmaInFlight.set(false) }
            }
        }

        latestGemma?.let { v ->
            if (now - v.receivedAtMs <= GEMMA_VALID_MS && v.source == "gemma") {
                percent = GemmaFusion.adjustPercent(percent, v)
                factors.add("Gemma 4: ${v.label} (%.0f%%) - ${v.reason}".format(v.confidence * 100))
            }
        }

        if (factors.size > 1) factors.remove("Phone context within normal parameters")
        return raw.copy(scorePercent = percent.coerceIn(0, 100), contributingFactors = factors)
    }

    private fun describeMotion(m: MotionContext?): String? = when {
        m == null -> null
        m.impactConfidence > 0.3f || m.fallConfidence > 0.3f -> "impact_or_drop"
        m.abruptMotionConfidence > 0.3f -> "abrupt_movement"
        m.runningConfidence > 0.5f -> "running"
        m.walkingConfidence > 0.5f -> "walking"
        m.stationaryConfidence > 0.5f -> "stationary"
        else -> "unclear"
    }
}
