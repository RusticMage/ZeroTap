package com.zerotap.domain.model

/**
 * Single accelerometer + gyroscope reading.
 */
data class MotionSample(
    val timestamp: Long,
    val accelerationX: Float,
    val accelerationY: Float,
    val accelerationZ: Float,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f
) {
    val accelerationMagnitude: Float
        get() = kotlin.math.sqrt(
            accelerationX * accelerationX +
            accelerationY * accelerationY +
            accelerationZ * accelerationZ
        )

    val gyroMagnitude: Float
        get() = kotlin.math.sqrt(
            gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ
        )
}

/**
 * Location reading from fused location provider.
 */
data class LocationSample(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val altitude: Double = 0.0
)

/**
 * Audio metadata (not raw audio data).
 */
data class AudioMetadata(
    val timestamp: Long,
    val amplitudeDb: Float,
    val isRecording: Boolean
)

/**
 * A sliding window of motion samples for inference.
 */
data class MotionWindow(
    val samples: List<MotionSample>,
    val startTimestamp: Long,
    val endTimestamp: Long
) {
    val durationMs: Long get() = endTimestamp - startTimestamp
    val sampleCount: Int get() = samples.size

    val meanAccelMagnitude: Float
        get() = if (samples.isEmpty()) 0f
                else samples.map { it.accelerationMagnitude }.average().toFloat()

    val peakAccelMagnitude: Float
        get() = samples.maxOfOrNull { it.accelerationMagnitude } ?: 0f

    val varianceAccelMagnitude: Float
        get() {
            if (samples.size < 2) return 0f
            val mean = meanAccelMagnitude
            return samples.map { (it.accelerationMagnitude - mean).let { d -> d * d } }
                .average().toFloat()
        }

    val meanGyroMagnitude: Float
        get() = if (samples.isEmpty()) 0f
                else samples.map { it.gyroMagnitude }.average().toFloat()

    val peakGyroMagnitude: Float
        get() = samples.maxOfOrNull { it.gyroMagnitude } ?: 0f
}

/**
 * Motion inference result.
 */
data class MotionPrediction(
    val classification: MotionClassification,
    val confidence: Float,
    val timestamp: Long
)

/**
 * Audio inference result.
 */
data class AudioPrediction(
    val classification: AudioClassification,
    val confidence: Float,
    val timestamp: Long
)
