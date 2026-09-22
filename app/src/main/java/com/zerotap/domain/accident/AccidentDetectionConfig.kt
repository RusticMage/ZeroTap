package com.zerotap.domain.accident

/**
 * Configurable heuristic thresholds for Vehicle Accident Detection.
 * These are tuned heuristics for physical demo validation and can be adjusted without changing detector logic.
 */
data class AccidentDetectionConfig(
    val peakAccelerationThreshold: Float = 35.0f,     // m/s² (~3.5g severe impact)
    val peakGyroThreshold: Float = 4.0f,               // rad/s (angular tumbling/rotation)
    val jerkMagnitudeThreshold: Float = 75.0f,          // m/s³ (rapid rate of acceleration change)
    val preImpactVehicleSpeedKmh: Float = 20.0f,       // km/h (optional GPS corroboration threshold)
    val postImpactStationarySpeedKmh: Float = 4.0f,     // km/h (post-crash near-zero speed)
    val postImpactStationaryDurationMs: Long = 3000L,  // ms (sustained post-impact stillness)
    val gracePeriodSeconds: Int = 15,                  // seconds user has to tap "I'M FINE"
    val highConfidenceThreshold: Float = 0.70f,        // score required to enter USER_CHECK
    val mediumConfidenceThreshold: Float = 0.40f       // score required to enter VERIFYING
)
