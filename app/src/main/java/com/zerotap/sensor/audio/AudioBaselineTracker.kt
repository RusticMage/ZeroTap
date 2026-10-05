package com.zerotap.sensor.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Environmental noise classification based on acoustic baseline.
 */
enum class AcousticEnvironment(val displayName: String) {
    QUIET("Quiet Ambient"),
    MODERATE_AMBIENT("Moderate Ambient"),
    LOUD_AMBIENT("Loud Ambient (Campus/Cafeteria/Street)"),
    VERY_LOUD_AMBIENT("High Noise (Heavy Traffic/Transit)")
}

/**
 * Dynamic acoustic baseline tracker.
 *
 * Continuously learns the user's ambient noise floor via Exponential Moving Average (EMA)
 * with asymmetric adaptation and a mandatory warm-up window.
 *
 * This ensures loud environments (college campus, traffic, bus) do not trigger false alarms,
 * while genuine acoustic anomalies stand out against their respective environmental floor.
 */
class AudioBaselineTracker(
    val warmupSampleCount: Int = 6,
    private val standardAlpha: Float = 0.08f,
    private val spikeAlpha: Float = 0.015f
) {
    var sampleCount: Int = 0
        private set

    var baselineDb: Float = 45.0f
        private set

    var varianceDb: Float = 8.0f
        private set

    val isWarmedUp: Boolean
        get() = sampleCount >= warmupSampleCount

    /**
     * Ingest a new audio dB measurement and update the baseline estimate.
     */
    fun update(currentDb: Float) {
        sampleCount++

        if (sampleCount <= warmupSampleCount) {
            // Warm-up phase: cumulative running average
            baselineDb = baselineDb + (currentDb - baselineDb) / sampleCount.toFloat()
            val diff = abs(currentDb - baselineDb)
            varianceDb = varianceDb + (diff - varianceDb) / sampleCount.toFloat()
            return
        }

        val deviation = currentDb - baselineDb

        // Asymmetric update: If acoustic energy spikes > 10dB above current baseline,
        // use a much slower alpha so that screams, honks, or impacts do not instantly
        // become "the new normal baseline".
        val effectiveAlpha = if (deviation > 10.0f) {
            spikeAlpha
        } else {
            standardAlpha
        }

        baselineDb = baselineDb + effectiveAlpha * (currentDb - baselineDb)
        val dev = abs(currentDb - baselineDb)
        varianceDb = varianceDb + effectiveAlpha * (dev - varianceDb)
    }

    /**
     * Standard deviation of the ambient noise floor.
     */
    val stdDevDb: Float
        get() = sqrt(varianceDb.coerceAtLeast(1.0f))

    /**
     * Calculates deviation in dB above the current baseline.
     */
    fun getDeviationDb(currentDb: Float): Float {
        return (currentDb - baselineDb).coerceAtLeast(0.0f)
    }

    /**
     * Identifies the current acoustic environment type.
     */
    fun getEnvironmentType(): AcousticEnvironment = when {
        baselineDb < 45.0f -> AcousticEnvironment.QUIET
        baselineDb < 65.0f -> AcousticEnvironment.MODERATE_AMBIENT
        baselineDb < 78.0f -> AcousticEnvironment.LOUD_AMBIENT
        else -> AcousticEnvironment.VERY_LOUD_AMBIENT
    }

    fun reset() {
        sampleCount = 0
        baselineDb = 45.0f
        varianceDb = 8.0f
    }
}
