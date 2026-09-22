package com.zerotap.domain.model

/**
 * Structured motion context derived from on-device feature extraction and motion classification.
 * Decoupled from raw sensor rates, ready for future on-device ML (e.g. 1D CNN, TCN, QNN).
 */
data class MotionContext(
    val timestamp: Long,
    val stationaryConfidence: Float = 0f,
    val walkingConfidence: Float = 0f,
    val runningConfidence: Float = 0f,
    val abruptMotionConfidence: Float = 0f,
    val impactConfidence: Float = 0f,
    val fallConfidence: Float = 0f,
    val peakAcceleration: Float = 0f,
    val meanAcceleration: Float = 0f,
    val jerkMagnitude: Float = 0f,
    val peakGyro: Float = 0f
) {
    val isElevatedKinematics: Boolean
        get() = abruptMotionConfidence > 0.4f || impactConfidence > 0.4f || fallConfidence > 0.4f || peakAcceleration > 18f || jerkMagnitude > 50f
}

/**
 * Structured acoustic context derived from on-device microphone RMS and temporal analysis.
 * Processed 100% on-device without recording or streaming raw audio.
 */
data class AudioContext(
    val timestamp: Long,
    val voiceActivityDetected: Boolean = false,
    val elevatedVocalEnergy: Boolean = false,
    val distressLikePattern: Boolean = false,
    val loudImpactDetected: Boolean = false,
    val ambientLevelDb: Float = 0f,
    val classificationLabel: String = "Normal",
    val confidence: Float = 0.5f
) {
    val isElevatedAcoustic: Boolean
        get() = distressLikePattern || loudImpactDetected || (elevatedVocalEnergy && ambientLevelDb > 75f)
}

/**
 * Historical safety context for a geographic area.
 * Used strictly as statistical context for the Risk Engine.
 * Does NOT label areas as "DANGEROUS" - uses neutral descriptive wording.
 */
data class HistoricalSafetyContext(
    val areaName: String,
    val historicalIncidentCount: Int,
    val historicalActivityLevel: String, // "LOW", "MODERATE", "ELEVATED"
    val recencyDescription: String = "Historical baseline"
)

/**
 * Structured location context combining live GPS kinematics with offline historical safety data.
 */
data class LocationContext(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val isMoving: Boolean = false,
    val isUnexpectedStop: Boolean = false,
    val stopDurationMs: Long = 0L,
    val historicalSafetyContext: HistoricalSafetyContext? = null
)

/**
 * Unified multimodal context presented to the Risk Prediction Engine every 1000ms.
 */
data class UnifiedSensorContext(
    val motion: MotionContext?,
    val audio: AudioContext?,
    val location: LocationContext?,
    val timestamp: Long = System.currentTimeMillis()
)
