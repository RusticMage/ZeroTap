package com.zerotap.domain.incident

import com.zerotap.ai.llm.LocalIncidentSummarizer
import com.zerotap.alert.AlertManager
import com.zerotap.data.repository.IncidentRepository
import com.zerotap.domain.model.Incident
import com.zerotap.domain.model.IncidentStatus
import com.zerotap.domain.model.RiskAssessment
import com.zerotap.domain.model.RiskSignal
import com.zerotap.domain.model.RiskState
import com.zerotap.domain.risk.RiskEngine
import com.zerotap.evidence.RollingEvidenceBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class IncidentManager(
    private val riskEngine: RiskEngine,
    private val evidenceBuffer: RollingEvidenceBuffer,
    private val alertManager: AlertManager,
    private val incidentSummarizer: LocalIncidentSummarizer,
    private val incidentRepository: IncidentRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val stateMachine = IncidentStateMachine()

    private val _currentIncident = MutableStateFlow<Incident?>(null)
    val currentIncident: StateFlow<Incident?> = _currentIncident.asStateFlow()

    private val _currentAssessment = MutableStateFlow(
        RiskAssessment(
            score = 0.0f,
            state = RiskState.NORMAL,
            contributingSignals = emptyList(),
            timestamp = System.currentTimeMillis(),
            engineLabel = riskEngine.engineLabel
        )
    )
    val currentAssessment: StateFlow<RiskAssessment> = _currentAssessment.asStateFlow()

    suspend fun processSignals(signals: List<RiskSignal>) = mutex.withLock {
        val currentState = _currentAssessment.value.state
        val assessment = riskEngine.evaluate(signals, currentState)
        _currentAssessment.value = assessment

        val incident = _currentIncident.value

        if (assessment.state.severity >= RiskState.HIGH_RISK.severity && incident == null) {
            val newIncident = Incident(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = IncidentStatus.DETECTED,
                riskScore = assessment.score,
                riskState = assessment.state,
                evidenceId = null
            )
            _currentIncident.value = newIncident
        }

        val updatedIncident = _currentIncident.value
        
        if (updatedIncident != null) {
            if (assessment.state == RiskState.INCIDENT && updatedIncident.status != IncidentStatus.ALERTING) {
                val escalated = stateMachine.transition(updatedIncident, IncidentStatus.ALERTING) ?: updatedIncident
                _currentIncident.value = escalated
                
                scope.launch {
                    val snapshot = evidenceBuffer.freeze()
                    val summary = incidentSummarizer.summarize(escalated)
                    incidentRepository.update(escalated.copy(summary = summary.summary))
                }
            }

            if (assessment.state.severity < RiskState.WATCH.severity) {
                val age = System.currentTimeMillis() - updatedIncident.createdAt
                if (age > 60_000) {
                    val resolved = stateMachine.transition(updatedIncident, IncidentStatus.RESOLVED) ?: updatedIncident
                    _currentIncident.value = null
                    incidentRepository.save(resolved)
                }
            }
        }
    }
    
    suspend fun dismissCurrentIncident() = mutex.withLock {
        _currentIncident.value?.let {
            val dismissed = stateMachine.transition(it, IncidentStatus.DISMISSED) ?: it
            _currentIncident.value = null
            incidentRepository.save(dismissed)
        }
    }

    suspend fun resolveCurrentIncident() = mutex.withLock {
        _currentIncident.value?.let {
            val resolved = stateMachine.transition(it, IncidentStatus.RESOLVED) ?: it
            _currentIncident.value = null
            incidentRepository.save(resolved)
        }
    }
}
