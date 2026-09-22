package com.zerotap.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotap.data.db.ZeroTapDatabase
import com.zerotap.data.db.entity.toDomain
import com.zerotap.domain.accident.AccidentEvidence
import com.zerotap.domain.accident.AccidentState
import com.zerotap.domain.model.*
import com.zerotap.service.ProtectionForegroundService
import kotlinx.coroutines.flow.*

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val protectionEnabled: StateFlow<Boolean> = ProtectionForegroundService.isRunning

    val currentAssessment: StateFlow<RiskAssessment?> = ProtectionForegroundService.currentRiskAssessment

    val predictionResult: StateFlow<RiskPredictionResult?> = ProtectionForegroundService.predictionResult

    val temporalRiskState: StateFlow<TemporalRiskState> = ProtectionForegroundService.temporalRiskState

    val countdownSeconds: StateFlow<Int> = ProtectionForegroundService.countdownSeconds

    val recentSignals: StateFlow<List<RiskSignal>> = ProtectionForegroundService.recentSignals

    val diagnostics: StateFlow<SensorDiagnostics> = ProtectionForegroundService.diagnostics

    // Vehicle accident state
    val accidentState: StateFlow<AccidentState> = ProtectionForegroundService.accidentState
    val accidentCountdownSeconds: StateFlow<Int> = ProtectionForegroundService.accidentCountdownSeconds
    val accidentEvidence: StateFlow<AccidentEvidence?> = ProtectionForegroundService.accidentEvidence

    val isAccidentUserCheckActive: StateFlow<Boolean> = accidentState.map {
        it == AccidentState.USER_CHECK
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Primary trusted contact
    private val contactDao = ZeroTapDatabase.getInstance(application).trustedContactDao()
    val primaryContact: StateFlow<TrustedContact?> = contactDao.getAllContacts().map { entities ->
        entities.firstOrNull { it.isPrimary }?.toDomain() ?: entities.firstOrNull()?.toDomain()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
