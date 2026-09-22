package com.zerotap.ui.protection

import androidx.lifecycle.ViewModel
import com.zerotap.domain.model.RiskAssessment
import com.zerotap.domain.model.RiskSignal
import com.zerotap.domain.model.SensorDiagnostics
import com.zerotap.service.ProtectionForegroundService
import kotlinx.coroutines.flow.StateFlow

class ProtectionViewModel : ViewModel() {
    val isRunning: StateFlow<Boolean> = ProtectionForegroundService.isRunning
    val diagnostics: StateFlow<SensorDiagnostics> = ProtectionForegroundService.diagnostics
    val recentSignals: StateFlow<List<RiskSignal>> = ProtectionForegroundService.recentSignals
    val currentRiskAssessment: StateFlow<RiskAssessment?> = ProtectionForegroundService.currentRiskAssessment
}
