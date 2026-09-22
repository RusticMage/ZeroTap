package com.zerotap.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerotap.domain.model.RiskAssessment
import com.zerotap.domain.model.RiskPredictionResult
import com.zerotap.domain.model.RiskSignal
import com.zerotap.domain.model.RiskState
import com.zerotap.domain.model.SensorDiagnostics
import com.zerotap.domain.model.TemporalRiskState
import com.zerotap.service.ProtectionForegroundService
import kotlinx.coroutines.flow.*

class HomeViewModel : ViewModel() {

    val protectionEnabled: StateFlow<Boolean> = ProtectionForegroundService.isRunning

    val currentAssessment: StateFlow<RiskAssessment?> = ProtectionForegroundService.currentRiskAssessment

    val predictionResult: StateFlow<RiskPredictionResult?> = ProtectionForegroundService.predictionResult

    val temporalRiskState: StateFlow<TemporalRiskState> = ProtectionForegroundService.temporalRiskState

    val countdownSeconds: StateFlow<Int> = ProtectionForegroundService.countdownSeconds

    val recentSignals: StateFlow<List<RiskSignal>> = ProtectionForegroundService.recentSignals

    val diagnostics: StateFlow<SensorDiagnostics> = ProtectionForegroundService.diagnostics

    val currentRiskState: StateFlow<RiskState> = currentAssessment.map { assessment ->
        if (protectionEnabled.value) {
            assessment?.state ?: RiskState.NORMAL
        } else {
            RiskState.NORMAL
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RiskState.NORMAL)

    val riskScore: StateFlow<Float> = predictionResult.map { pred ->
        if (protectionEnabled.value) {
            (pred?.scorePercent ?: 0) / 100f
        } else {
            0.0f
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0f)

    val isEmergencyActive: StateFlow<Boolean> = combine(
        protectionEnabled,
        temporalRiskState
    ) { enabled, tempState ->
        enabled && (tempState == TemporalRiskState.EMERGENCY_PENDING || tempState == TemporalRiskState.EMERGENCY_TRIGGERED)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleProtection(context: Context, enabled: Boolean) {
        if (enabled) {
            ProtectionForegroundService.start(context)
        } else {
            ProtectionForegroundService.stop(context)
        }
    }
}
