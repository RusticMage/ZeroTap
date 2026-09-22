package com.zerotap.domain.model

/**
 * Temporal risk states tracking persistence of elevated risk over time.
 * Evaluated on a 1000ms tick cycle.
 */
enum class TemporalRiskState(val displayName: String, val severity: Int) {
    NORMAL("Normal", 0),
    ELEVATED("Elevated", 1),
    HIGH_RISK("High Risk", 2),
    EMERGENCY_PENDING("Emergency Pending", 3),
    EMERGENCY_TRIGGERED("Emergency Triggered", 4);

    val isEmergencyActive: Boolean
        get() = this == EMERGENCY_PENDING || this == EMERGENCY_TRIGGERED
}
