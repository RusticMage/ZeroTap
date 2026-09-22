package com.zerotap.domain.accident

/**
 * State machine states for vehicle accident safety mode.
 */
enum class AccidentState(val displayName: String) {
    NORMAL("Normal"),
    POSSIBLE_IMPACT("Possible Impact"),
    VERIFYING("Verifying Kinematics"),
    USER_CHECK("Safety Check Active"),
    CANCELLED("User Confirmed Fine"),
    ESCALATING("Escalating Alerts"),
    SMS_SENT("Emergency SMS Dispatched"),
    CALL_INITIATED("Emergency Call Initiated")
}

/**
 * Categorical level of physical accident evidence.
 */
enum class AccidentEvidenceLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Captured physical sensor evidence for a suspected vehicle accident event.
 */
data class AccidentEvidence(
    val peakAcceleration: Float = 0f,
    val peakGyro: Float = 0f,
    val jerkMagnitude: Float = 0f,
    val preImpactSpeedKmh: Float? = null,
    val postImpactSpeedKmh: Float? = null,
    val isPostImpactStationary: Boolean = false,
    val confidence: Float = 0f,
    val evidenceLevel: AccidentEvidenceLevel = AccidentEvidenceLevel.LOW,
    val contributingFactors: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
