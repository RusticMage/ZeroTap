package com.zerotap.domain.accident

import com.zerotap.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * State machine managing vehicle accident escalation and grace period confirmation.
 *
 * Architecture:
 * NORMAL
 *   ↓ (Physical anomaly detected: impact + jerk + gyro)
 * POSSIBLE_IMPACT
 *   ↓ (Kinematic verification)
 * VERIFYING
 *   ↓ (High evidence confirmed)
 * USER_CHECK (Vibration + "Are you okay?" prompt + 15s countdown)
 *   ├── User taps "I'M FINE" → CANCELLED → NORMAL (Dismissed, zero alerts)
 *   └── Timeout (0s) → ESCALATING → SMS_SENT → CALL_INITIATED (Zero-tap execution)
 */
class VehicleAccidentStateMachine(
    private val config: AccidentDetectionConfig = AccidentDetectionConfig(),
    private val onEnterUserCheck: ((AccidentEvidence) -> Unit)? = null,
    private val onEscalationTimeout: ((AccidentEvidence, String) -> Unit)? = null,
    private val onCancelCheck: (() -> Unit)? = null
) {
    private val _state = MutableStateFlow(AccidentState.NORMAL)
    val state: StateFlow<AccidentState> = _state.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(config.gracePeriodSeconds)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _currentEvidence = MutableStateFlow<AccidentEvidence?>(null)
    val currentEvidence: StateFlow<AccidentEvidence?> = _currentEvidence.asStateFlow()

    private val _currentEventId = MutableStateFlow<String?>(null)
    val currentEventId: StateFlow<String?> = _currentEventId.asStateFlow()

    private var verifyingTicks = 0

    @Synchronized
    fun processEvidence(evidence: AccidentEvidence) {
        _currentEvidence.value = evidence
        val current = _state.value

        when (current) {
            AccidentState.NORMAL, AccidentState.POSSIBLE_IMPACT -> {
                if (evidence.evidenceLevel == AccidentEvidenceLevel.HIGH) {
                    Logger.alert("AccidentState", "[ZeroTap][Accident] High evidence impact detected (Confidence: %.2f). Entering USER_CHECK.".format(evidence.confidence))
                    enterUserCheck(evidence)
                } else if (evidence.evidenceLevel == AccidentEvidenceLevel.MEDIUM) {
                    _state.value = AccidentState.VERIFYING
                    verifyingTicks = 1
                    Logger.alert("AccidentState", "[ZeroTap][Accident] Medium evidence impact detected. Verifying kinematics.")
                }
            }

            AccidentState.VERIFYING -> {
                if (evidence.evidenceLevel == AccidentEvidenceLevel.HIGH) {
                    enterUserCheck(evidence)
                } else if (evidence.evidenceLevel == AccidentEvidenceLevel.MEDIUM) {
                    verifyingTicks++
                    if (verifyingTicks >= 3) {
                        // Persisted medium evidence transitions to user check
                        enterUserCheck(evidence)
                    }
                } else {
                    // Transient road anomaly decayed back to low
                    _state.value = AccidentState.NORMAL
                    verifyingTicks = 0
                    Logger.alert("AccidentState", "[ZeroTap][Accident] Verification decayed to normal road noise. State reset to NORMAL.")
                }
            }

            AccidentState.USER_CHECK -> {
                val rem = _countdownSeconds.value
                if (rem > 1) {
                    _countdownSeconds.value = rem - 1
                } else {
                    // Countdown expired with NO USER RESPONSE -> Zero-Tap Automatic Escalation
                    _countdownSeconds.value = 0
                    _state.value = AccidentState.ESCALATING
                    val eventId = _currentEventId.value ?: UUID.randomUUID().toString()
                    Logger.alert("AccidentState", "[ZeroTap][Accident] User did not respond during grace period (Timeout). Initiating zero-tap emergency escalation.")
                    onEscalationTimeout?.invoke(evidence, eventId)
                }
            }

            AccidentState.CANCELLED -> {
                _state.value = AccidentState.NORMAL
            }

            AccidentState.ESCALATING, AccidentState.SMS_SENT, AccidentState.CALL_INITIATED -> {
                // Maintained until explicit dismissal or app reset
            }
        }
    }

    private fun enterUserCheck(evidence: AccidentEvidence) {
        _state.value = AccidentState.USER_CHECK
        _countdownSeconds.value = config.gracePeriodSeconds
        val eventId = UUID.randomUUID().toString()
        _currentEventId.value = eventId
        verifyingTicks = 0
        onEnterUserCheck?.invoke(evidence)
    }

    /**
     * User explicitly pressed "I'M FINE" during the safety check grace period.
     * Cancels all alert escalation immediately. Zero SMS sent. Zero calls made.
     */
    @Synchronized
    fun userAffirmsFine() {
        Logger.alert("AccidentState", "[ZeroTap][Accident] User indicated 'I\\'m Fine'. Cancelling accident escalation.")
        _state.value = AccidentState.CANCELLED
        _countdownSeconds.value = config.gracePeriodSeconds
        _currentEventId.value = null
        onCancelCheck?.invoke()
        _state.value = AccidentState.NORMAL
    }

    /**
     * Resets the accident state back to NORMAL.
     */
    @Synchronized
    fun reset() {
        _state.value = AccidentState.NORMAL
        _countdownSeconds.value = config.gracePeriodSeconds
        _currentEvidence.value = null
        _currentEventId.value = null
        verifyingTicks = 0
        onCancelCheck?.invoke()
    }

    /**
     * Injects synthetic possible accident for developer console testing.
     */
    fun triggerAccidentForTesting(simulatedEvidence: AccidentEvidence) {
        enterUserCheck(simulatedEvidence)
    }
}
