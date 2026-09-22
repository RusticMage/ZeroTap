package com.zerotap.domain.model

/**
 * Sealed hierarchy of signals that feed into the RiskEngine.
 * Each signal carries a weight hint and description for the risk scorer.
 */
sealed class RiskSignal {
    abstract val timestamp: Long
    abstract val weight: Float
    abstract val description: String

    data class AudioSignal(
        override val timestamp: Long,
        override val weight: Float,
        override val description: String,
        val classification: AudioClassification,
        val confidence: Float
    ) : RiskSignal()

    data class MotionSignal(
        override val timestamp: Long,
        override val weight: Float,
        override val description: String,
        val classification: MotionClassification,
        val confidence: Float
    ) : RiskSignal()

    data class LocationSignal(
        override val timestamp: Long,
        override val weight: Float,
        override val description: String,
        val latitude: Double,
        val longitude: Double,
        val speed: Float,
        val type: LocationSignalType
    ) : RiskSignal()

    data class RouteSignal(
        override val timestamp: Long,
        override val weight: Float,
        override val description: String,
        val deviationMeters: Float
    ) : RiskSignal()
}

enum class LocationSignalType {
    UNEXPECTED_STOP,
    SPEED_ANOMALY,
    PROLONGED_STOP,
    ROUTE_DEVIATION,
    NORMAL
}

/**
 * Output of the RiskEngine after evaluating all incoming signals.
 */
data class RiskAssessment(
    val score: Float,
    val state: RiskState,
    val contributingSignals: List<RiskSignal>,
    val timestamp: Long,
    val engineLabel: String = "development"
) {
    val isEscalated: Boolean
        get() = state.severity >= RiskState.SUSPICIOUS.severity
}

/**
 * Discrete contribution level for explainability display on UI and emergency alerts.
 */
enum class ContributionLevel(val displayName: String) {
    LOW("LOW"),
    MODERATE("MODERATE"),
    ELEVATED("ELEVATED"),
    HIGH("HIGH")
}

/**
 * Output of the RiskPredictionEngine with full explainability.
 * All contributions are mathematically derived during the scoring pass.
 */
data class RiskPredictionResult(
    val scorePercent: Int,                                    // 0..100
    val temporalState: TemporalRiskState,
    val contributingFactors: List<String>,                    // Human-readable descriptors
    val timestamp: Long,
    val engineLabel: String = "Development Risk Prediction Engine (Local Multi-Modal)",

    // Mathematical component values (0.0 .. 1.0)
    val motionContribution: Float = 0f,
    val audioContribution: Float = 0f,
    val locationContribution: Float = 0f,
    val temporalContribution: Float = 0f,

    // Categorical levels for clear, human-readable explainability
    val motionLevel: ContributionLevel = ContributionLevel.LOW,
    val voiceLevel: ContributionLevel = ContributionLevel.LOW,
    val locationLevel: ContributionLevel = ContributionLevel.LOW,
    val persistenceLevel: ContributionLevel = ContributionLevel.LOW,

    val durationInCurrentStateSeconds: Long = 0L
)
