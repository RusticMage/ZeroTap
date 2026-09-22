package com.zerotap.ai.llm

import com.zerotap.domain.model.Incident
import com.zerotap.domain.model.IncidentSummary

interface LocalIncidentSummarizer {
    suspend fun summarize(incident: Incident): IncidentSummary
    val engineName: String
}
