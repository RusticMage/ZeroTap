package com.zerotap.domain.risk

import com.zerotap.domain.model.AudioClassification
import com.zerotap.domain.model.LocationSignalType
import com.zerotap.domain.model.MotionClassification
import com.zerotap.domain.model.RiskAssessment
import com.zerotap.domain.model.RiskSignal
import com.zerotap.domain.model.RiskState
import com.zerotap.util.Logger

class DevelopmentRiskEngine : RiskEngine {
    override val engineLabel: String = "Dynamic Multi-Signal Risk Engine v1.1"

    override fun evaluate(signals: List<RiskSignal>, currentState: RiskState): RiskAssessment {
        var score = 0.0f

        for (signal in signals) {
            when (signal) {
                is RiskSignal.AudioSignal -> {
                    when (signal.classification) {
                        AudioClassification.DISTRESS_SOUND -> score += 0.40f * signal.confidence
                        AudioClassification.LOUD_NOISE -> score += 0.15f * signal.confidence
                        AudioClassification.LOUD_ACOUSTIC_EVENT -> score += 0.30f * signal.confidence
                        AudioClassification.SILENCE -> score += 0.02f
                        else -> {}
                    }
                }
                is RiskSignal.MotionSignal -> {
                    when (signal.classification) {
                        MotionClassification.PHONE_DROP -> score += 0.35f * signal.confidence
                        MotionClassification.SUDDEN_JERK -> score += 0.28f * signal.confidence
                        MotionClassification.RUNNING -> score += 0.10f * signal.confidence
                        MotionClassification.NORMAL_WALKING -> score += 0.02f
                        MotionClassification.STATIONARY -> {
                            // Being stationary keeps baseline score low
                            score = (score - 0.05f).coerceAtLeast(0.0f)
                        }
                        MotionClassification.UNKNOWN -> score += 0.02f
                    }
                }
                is RiskSignal.LocationSignal -> {
                    when (signal.type) {
                        LocationSignalType.UNEXPECTED_STOP -> score += 0.18f
                        LocationSignalType.PROLONGED_STOP -> score += 0.25f
                        LocationSignalType.SPEED_ANOMALY -> score += 0.20f
                        LocationSignalType.ROUTE_DEVIATION -> score += 0.15f
                        LocationSignalType.NORMAL -> {}
                    }
                }
                is RiskSignal.RouteSignal -> {
                    score += 0.15f
                }
            }
        }

        score = score.coerceIn(0.0f, 1.0f)

        val targetState = when {
            score < 0.15f -> RiskState.NORMAL
            score < 0.30f -> RiskState.WATCH
            score < 0.50f -> RiskState.SUSPICIOUS
            score < 0.70f -> RiskState.HIGH_RISK
            else -> RiskState.INCIDENT
        }

        // Hysteresis: allow gradual de-escalation rather than sudden drops unless resolved
        var newState = targetState
        if (targetState.severity < currentState.severity && currentState != RiskState.RESOLVED) {
            // De-escalate smoothly: at most 1 level lower per evaluation interval
            val lowerSeverity = (currentState.severity - 1).coerceAtLeast(RiskState.NORMAL.severity)
            val stepDownState = RiskState.entries.firstOrNull { it.severity == lowerSeverity } ?: targetState
            newState = if (targetState.severity < stepDownState.severity) stepDownState else targetState
        }

        val assessment = RiskAssessment(
            score = score,
            state = newState,
            timestamp = System.currentTimeMillis(),
            contributingSignals = signals,
            engineLabel = engineLabel
        )

        return assessment
    }
}
