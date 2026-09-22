package com.zerotap.domain.risk

import com.zerotap.domain.model.RiskAssessment
import com.zerotap.domain.model.RiskSignal
import com.zerotap.domain.model.RiskState

interface RiskEngine {
    fun evaluate(signals: List<RiskSignal>, currentState: RiskState): RiskAssessment
    val engineLabel: String
}
