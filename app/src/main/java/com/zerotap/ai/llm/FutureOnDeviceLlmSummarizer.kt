package com.zerotap.ai.llm

import com.zerotap.domain.model.Incident
import com.zerotap.domain.model.IncidentSummary

class FutureOnDeviceLlmSummarizer : LocalIncidentSummarizer {
    override val engineName: String = "On-Device LLM Summarizer (Not Yet Implemented)"
    
    private val fallback = DevelopmentIncidentSummarizer()

    override suspend fun summarize(incident: Incident): IncidentSummary {
        // Fallback to development summarizer
        val fallbackSummary = fallback.summarize(incident)
        return fallbackSummary.copy(
            summary = "[LLM NOT IMPLEMENTED - FALLBACK]\n" + fallbackSummary.summary
        )
    }
}
