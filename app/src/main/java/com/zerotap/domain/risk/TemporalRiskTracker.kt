package com.zerotap.domain.risk

import com.zerotap.domain.model.TemporalRiskState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Tracks temporal persistence of elevated risk conditions over time.
 * Evaluates on a 1000ms tick and manages the 15-second emergency countdown.
 */
class TemporalRiskTracker {

    private val _temporalState = MutableStateFlow(TemporalRiskState.NORMAL)
    val temporalState: StateFlow<TemporalRiskState> = _temporalState.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(15)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _currentEmergencyEventId = MutableStateFlow<String?>(null)
    val currentEmergencyEventId: StateFlow<String?> = _currentEmergencyEventId.asStateFlow()

    private var timeInCurrentStateStart = System.currentTimeMillis()
    private var highRiskTimeStart = 0L
    private var elevatedTimeStart = 0L
    private var lowScoreTimeStart = 0L

    val durationInCurrentStateSeconds: Long
        get() = ((System.currentTimeMillis() - timeInCurrentStateStart) / 1000L).coerceAtLeast(0L)

    /**
     * Process a new risk score evaluation on the 1000ms engine cycle.
     */
    @Synchronized
    fun update(scorePercent: Int): TemporalRiskState {
        val now = System.currentTimeMillis()
        val current = _temporalState.value

        // Handle active emergency states
        if (current == TemporalRiskState.EMERGENCY_PENDING) {
            val rem = _countdownSeconds.value
            if (rem > 1) {
                _countdownSeconds.value = rem - 1
                return current
            } else {
                // Countdown expired -> Transition to EMERGENCY_TRIGGERED
                _countdownSeconds.value = 0
                transitionTo(TemporalRiskState.EMERGENCY_TRIGGERED)
                return TemporalRiskState.EMERGENCY_TRIGGERED
            }
        }

        if (current == TemporalRiskState.EMERGENCY_TRIGGERED) {
            // Remains in EMERGENCY_TRIGGERED until explicitly resolved or cancelled
            return current
        }

        // Hysteresis / escalation tracker
        if (scorePercent >= 75) {
            if (highRiskTimeStart == 0L) highRiskTimeStart = now
            lowScoreTimeStart = 0L
            val durationHighMs = now - highRiskTimeStart

            if (durationHighMs >= 8000L) {
                // Persisted at >= 75 for 8 seconds -> Trigger EMERGENCY_PENDING
                _countdownSeconds.value = 15
                _currentEmergencyEventId.value = UUID.randomUUID().toString()
                transitionTo(TemporalRiskState.EMERGENCY_PENDING)
                return TemporalRiskState.EMERGENCY_PENDING
            } else if (durationHighMs >= 4000L && current.severity < TemporalRiskState.HIGH_RISK.severity) {
                transitionTo(TemporalRiskState.HIGH_RISK)
            } else if (current.severity < TemporalRiskState.ELEVATED.severity) {
                transitionTo(TemporalRiskState.ELEVATED)
            }
        } else if (scorePercent >= 50) {
            if (elevatedTimeStart == 0L) elevatedTimeStart = now
            highRiskTimeStart = 0L
            lowScoreTimeStart = 0L
            val durationElevatedMs = now - elevatedTimeStart

            if (durationElevatedMs >= 4000L && current.severity < TemporalRiskState.HIGH_RISK.severity) {
                transitionTo(TemporalRiskState.HIGH_RISK)
            } else if (current.severity < TemporalRiskState.ELEVATED.severity) {
                transitionTo(TemporalRiskState.ELEVATED)
            }
        } else if (scorePercent >= 25) {
            elevatedTimeStart = 0L
            highRiskTimeStart = 0L
            lowScoreTimeStart = 0L
            if (current == TemporalRiskState.NORMAL) {
                transitionTo(TemporalRiskState.ELEVATED)
            }
        } else {
            // Score < 25 (Low/Safe regime)
            highRiskTimeStart = 0L
            elevatedTimeStart = 0L
            if (lowScoreTimeStart == 0L) lowScoreTimeStart = now
            val durationLowMs = now - lowScoreTimeStart

            // Require 4 seconds of low score before de-escalating to NORMAL
            if (durationLowMs >= 4000L && current != TemporalRiskState.NORMAL) {
                transitionTo(TemporalRiskState.NORMAL)
            }
        }

        return _temporalState.value
    }

    /**
     * User tapped "I'M SAFE - CANCEL" or dismissed emergency.
     */
    @Synchronized
    fun cancelEmergency() {
        _countdownSeconds.value = 15
        _currentEmergencyEventId.value = null
        highRiskTimeStart = 0L
        elevatedTimeStart = 0L
        lowScoreTimeStart = System.currentTimeMillis()
        transitionTo(TemporalRiskState.NORMAL)
    }

    /**
     * User tapped "CALL 112 NOW" or "SIMULATE 112 NOW" to skip remaining countdown.
     */
    @Synchronized
    fun triggerImmediately() {
        if (_currentEmergencyEventId.value == null) {
            _currentEmergencyEventId.value = UUID.randomUUID().toString()
        }
        _countdownSeconds.value = 0
        transitionTo(TemporalRiskState.EMERGENCY_TRIGGERED)
    }

    private fun transitionTo(newState: TemporalRiskState) {
        if (_temporalState.value != newState) {
            _temporalState.value = newState
            timeInCurrentStateStart = System.currentTimeMillis()
        }
    }
}
