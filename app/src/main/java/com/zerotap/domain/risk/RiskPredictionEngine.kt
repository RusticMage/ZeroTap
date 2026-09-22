package com.zerotap.domain.risk

import com.zerotap.domain.model.RiskPredictionResult
import com.zerotap.domain.model.TemporalRiskState
import com.zerotap.domain.model.UnifiedSensorContext

/**
 * High-level inference engine interface for multi-modal risk prediction.
 * Consumes structured contexts (Motion, Audio, Location) every 1000ms.
 * Designed to be replaced with a trained neural fusion model without touching sensors or response managers.
 */
interface RiskPredictionEngine {
    fun evaluate(context: UnifiedSensorContext, currentState: TemporalRiskState): RiskPredictionResult
    val engineLabel: String
}
