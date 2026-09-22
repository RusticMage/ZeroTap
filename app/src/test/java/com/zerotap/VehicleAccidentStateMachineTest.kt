package com.zerotap

import com.zerotap.domain.accident.*
import org.junit.Assert.*
import org.junit.Test

class VehicleAccidentStateMachineTest {

    @Test
    fun testHighEvidenceTransitionsToUserCheck() {
        var userCheckEntered = false
        val stateMachine = VehicleAccidentStateMachine(
            onEnterUserCheck = { userCheckEntered = true }
        )

        val highEvidence = AccidentEvidence(
            peakAcceleration = 45f,
            peakGyro = 5.0f,
            jerkMagnitude = 90f,
            confidence = 0.85f,
            evidenceLevel = AccidentEvidenceLevel.HIGH,
            contributingFactors = listOf("Severe Impact"),
            timestamp = System.currentTimeMillis()
        )

        stateMachine.processEvidence(highEvidence)

        assertEquals(AccidentState.USER_CHECK, stateMachine.state.value)
        assertEquals(15, stateMachine.countdownSeconds.value)
        assertTrue(userCheckEntered)
    }

    @Test
    fun testUserAffirmsFineDismissesAlertsWithoutEscalation() {
        var escalationFired = false
        var userCheckEntered = false
        var checkCancelled = false

        val stateMachine = VehicleAccidentStateMachine(
            onEnterUserCheck = { userCheckEntered = true },
            onEscalationTimeout = { _, _ -> escalationFired = true },
            onCancelCheck = { checkCancelled = true }
        )

        val highEvidence = AccidentEvidence(
            confidence = 0.90f,
            evidenceLevel = AccidentEvidenceLevel.HIGH
        )

        stateMachine.processEvidence(highEvidence)
        assertEquals(AccidentState.USER_CHECK, stateMachine.state.value)
        assertTrue(userCheckEntered)

        // User explicitly taps "I'M FINE"
        stateMachine.userAffirmsFine()

        // State immediately returns to NORMAL, and zero escalation callbacks are fired
        assertEquals(AccidentState.NORMAL, stateMachine.state.value)
        assertTrue(checkCancelled)
        assertFalse(escalationFired)
    }

    @Test
    fun testZeroTapTimeoutEscalationOccursAutomatically() {
        var escalationFired = false
        var escalatedIncidentId: String? = null

        val config = AccidentDetectionConfig(gracePeriodSeconds = 3)
        val stateMachine = VehicleAccidentStateMachine(
            config = config,
            onEscalationTimeout = { _, eventId ->
                escalationFired = true
                escalatedIncidentId = eventId
            }
        )

        val highEvidence = AccidentEvidence(
            confidence = 0.90f,
            evidenceLevel = AccidentEvidenceLevel.HIGH
        )

        // Enter user check
        stateMachine.processEvidence(highEvidence)
        assertEquals(AccidentState.USER_CHECK, stateMachine.state.value)
        assertEquals(3, stateMachine.countdownSeconds.value)

        // Tick 1 (3 -> 2)
        stateMachine.processEvidence(highEvidence)
        assertEquals(2, stateMachine.countdownSeconds.value)
        assertFalse(escalationFired)

        // Tick 2 (2 -> 1)
        stateMachine.processEvidence(highEvidence)
        assertEquals(1, stateMachine.countdownSeconds.value)
        assertFalse(escalationFired)

        // Tick 3 (1 -> 0: TIMEOUT EXPIRED)
        stateMachine.processEvidence(highEvidence)
        assertEquals(0, stateMachine.countdownSeconds.value)
        assertEquals(AccidentState.ESCALATING, stateMachine.state.value)

        // Escalation fired automatically without any user touch (True Zero-Tap)
        assertTrue(escalationFired)
        assertNotNull(escalatedIncidentId)
    }
}
