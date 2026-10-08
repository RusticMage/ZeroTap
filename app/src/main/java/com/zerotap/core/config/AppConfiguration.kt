package com.zerotap.core.config

/**
 * Centralized application configuration.
 * Consolidates all tunable parameters, eliminating magic numbers across risk, sensor, GPS, and sync layers.
 */
object AppConfiguration {

    // --- Deployment & Defaults ---
    var currentMode: DeploymentMode = DeploymentMode.SERVER
    var backendBaseUrl: String = try {
        val isEmulator = android.os.Build.FINGERPRINT?.startsWith("generic") == true ||
                android.os.Build.MODEL?.contains("google_sdk") == true ||
                android.os.Build.HARDWARE?.contains("goldfish") == true ||
                android.os.Build.HARDWARE?.contains("ranchu") == true
        if (isEmulator) "http://10.0.2.2:8080" else "http://127.0.0.1:8080"
    } catch (_: Throwable) {
        "http://127.0.0.1:8080"
    }
    var deviceId: String = "user-device-1"

    // --- Safety & Emergency Timers ---
    const val EMERGENCY_COUNTDOWN_SECONDS: Int = 15
    const val ACCIDENT_GRACE_PERIOD_SECONDS: Int = 15
    const val RISK_PERSISTENCE_HIGH_THRESHOLD_MS: Long = 8000L
    const val RISK_PERSISTENCE_ELEVATED_THRESHOLD_MS: Long = 4000L
    const val RISK_PERSISTENCE_DEESCALATE_MS: Long = 4000L

    // --- Risk Scoring Thresholds (0..100) ---
    const val RISK_THRESHOLD_NORMAL: Int = 25
    const val RISK_THRESHOLD_ELEVATED: Int = 50
    const val RISK_THRESHOLD_HIGH: Int = 75
    const val RISK_THRESHOLD_INCIDENT: Int = 85

    // --- Sensor Kinematics & Motion Thresholds ---
    const val MOTION_PEAK_ACCEL_DROP_MS2: Float = 24.0f
    const val MOTION_MIN_ACCEL_FREEFALL_MS2: Float = 2.5f
    const val MOTION_ABRUPT_ACCEL_MS2: Float = 18.0f
    const val MOTION_ABRUPT_JERK_MS3: Float = 50.0f
    const val MOTION_REST_GRAVITY_DEVIATION: Float = 0.4f
    const val MOTION_REST_GYRO_MAGNITUDE: Float = 0.2f

    // --- Audio Thresholds ---
    const val AUDIO_QUIET_DB: Float = 45.0f
    const val AUDIO_VOICE_ACTIVITY_DB: Float = 55.0f
    const val AUDIO_ELEVATED_VOCAL_DB: Float = 70.0f
    const val AUDIO_EXTREME_LOUD_DB: Float = 85.0f

    // --- GPS & Location Tracking ---
    const val GPS_UPDATE_INTERVAL_MS: Long = 5000L
    const val GPS_FASTEST_INTERVAL_MS: Long = 2000L
    const val GPS_MAX_ACCEPTABLE_ACCURACY_METERS: Float = 50.0f
    const val GPS_UNEXPECTED_STOP_DURATION_MS: Long = 30000L // >30s halt after transit
    const val GPS_MIN_MOVING_SPEED_MS: Float = 1.2f // ~4.3 km/h

    // --- Safe Space Search & Mapping ---
    const val SAFE_SPACE_SEARCH_RADIUS_METERS: Float = 5000.0f // 5 km default
    const val SAFE_SPACE_EMERGENCY_RADIUS_METERS: Float = 10000.0f // 10 km in high risk
    const val SAFE_SPACE_MAX_RESULTS: Int = 50

    // --- AI Confidence Limits & Fallback ---
    const val AI_CONFIDENCE_THRESHOLD: Float = 0.65f
    const val AI_TIMEOUT_MS: Long = 4000L

    // --- Sync & Network ---
    const val SYNC_RETRY_INTERVAL_MS: Long = 15000L
    const val SYNC_MAX_BATCH_SIZE: Int = 20

    fun getCapabilities(mode: DeploymentMode = currentMode): AppCapabilities {
        return AppCapabilities(
            deploymentMode = mode,
            isLocalSafetyActive = true,
            isByokEnabled = mode == DeploymentMode.PRIVATE,
            isServerSyncEnabled = mode == DeploymentMode.SERVER,
            isRemoteMonitoringEnabled = mode == DeploymentMode.SERVER,
            isVertexAiEnabled = mode == DeploymentMode.SERVER
        )
    }
}
