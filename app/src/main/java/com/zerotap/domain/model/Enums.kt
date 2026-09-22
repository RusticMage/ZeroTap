package com.zerotap.domain.model

/**
 * Risk states in the ZeroTap detection pipeline.
 * Ordered by severity - transitions should generally move forward,
 * though de-escalation is permitted.
 */
enum class RiskState(val displayName: String, val severity: Int) {
    NORMAL("Low", 0),
    WATCH("Watch", 1),
    SUSPICIOUS("Suspicious", 2),
    HIGH_RISK("High Risk", 3),
    INCIDENT("Incident", 4),
    RESOLVED("Resolved", -1)
}

/**
 * Motion classification output from the inference engine.
 */
enum class MotionClassification(val displayName: String) {
    STATIONARY("Stationary"),
    NORMAL_WALKING("Normal Walking"),
    RUNNING("Running"),
    SUDDEN_JERK("Sudden Jerk"),
    PHONE_DROP("Phone Drop"),
    UNKNOWN("Unknown")
}

/**
 * Audio classification output from the inference engine.
 */
enum class AudioClassification(val displayName: String) {
    NORMAL("Normal"),
    LOUD_NOISE("Loud Noise"),
    DISTRESS_SOUND("Distress Sound"),
    LOUD_ACOUSTIC_EVENT("Loud Acoustic Event"),
    SILENCE("Silence"),
    UNKNOWN("Unknown")
}

/**
 * Incident lifecycle states.
 */
enum class IncidentStatus(val displayName: String) {
    DETECTED("Detected"),
    ACTIVE("Active"),
    ALERTING("Alerting"),
    RESOLVED("Resolved"),
    DISMISSED("Dismissed")
}
