package com.zerotap.ai.llm

import com.zerotap.domain.model.Incident
import com.zerotap.domain.model.IncidentSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DevelopmentIncidentSummarizer : LocalIncidentSummarizer {
    override val engineName: String = "Development Template Summarizer v1.0"
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override suspend fun summarize(incident: Incident): IncidentSummary {
        val timeStr = dateFormat.format(Date(incident.createdAt))
        val score = String.format(Locale.US, "%.2f", incident.riskScore)
        val state = incident.riskState.displayName
        
        val signals = if (incident.contributingSignals.isNotEmpty()) {
            incident.contributingSignals.joinToString(", ") { it.description }
        } else "Observed context escalation"
        
        val locStr = if (incident.latitude != null && incident.longitude != null) {
            "${incident.latitude}, ${incident.longitude}"
        } else "Unknown"
        val status = incident.status.displayName
        
        val summaryText = buildString {
            appendLine("Safety incident detected at $timeStr with risk score $score ($state).")
            appendLine("Contributing factors: $signals.")
            appendLine("Location: $locStr.")
            append("Status: $status.")
        }
        
        return IncidentSummary(
            incidentId = incident.id,
            summary = summaryText,
            generatedAt = System.currentTimeMillis(),
            engineLabel = engineName
        )
    }
}
