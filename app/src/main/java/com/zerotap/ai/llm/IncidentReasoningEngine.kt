package com.zerotap.ai.llm

import com.zerotap.alert.EmergencyEventType
import com.zerotap.domain.model.RiskPredictionResult

data class IncidentReasoningOutput(
    val explanationTitle: String,
    val deterrentSpeechPrompt: String,
    val formattedContextSummary: String
)

interface IncidentReasoningEngine {
    val engineName: String
    fun reason(
        eventType: EmergencyEventType,
        prediction: RiskPredictionResult?,
        evidenceTags: List<String>
    ): IncidentReasoningOutput
}

/**
 * Deterministic local incident reasoning engine.
 * Generates clear, honest situational context and deterrent phrases without external stochastic dependencies.
 */
class LocalDeterministicReasoningEngine : IncidentReasoningEngine {
    override val engineName: String = "On-Device Deterministic Reasoning Engine v1.0"

    override fun reason(
        eventType: EmergencyEventType,
        prediction: RiskPredictionResult?,
        evidenceTags: List<String>
    ): IncidentReasoningOutput {
        return when (eventType) {
            EmergencyEventType.VEHICLE_ACCIDENT_SUSPECTED -> {
                IncidentReasoningOutput(
                    explanationTitle = "Suspected Vehicle Accident",
                    deterrentSpeechPrompt = "Possible vehicle accident detected. Are you okay?",
                    formattedContextSummary = "Kinematic collision signature detected (${evidenceTags.take(2).joinToString(", ")})"
                )
            }
            EmergencyEventType.PERSONAL_SAFETY -> {
                val score = prediction?.scorePercent ?: 0
                val title = if (score >= 75) "Critical Personal Safety Incident" else "Elevated Safety Anomaly"
                val speech = if (score >= 75) {
                    "Safety monitoring active. Emergency contacts are being notified."
                } else {
                    "Unusual activity detected. Monitoring context."
                }
                val summary = prediction?.contributingFactors?.take(2)?.joinToString(", ") ?: "Multi-modal anomaly detected"
                IncidentReasoningOutput(
                    explanationTitle = title,
                    deterrentSpeechPrompt = speech,
                    formattedContextSummary = summary
                )
            }
        }
    }
}
