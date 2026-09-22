package com.zerotap

import com.zerotap.ai.hierarchical.HierarchicalInferenceCoordinator
import com.zerotap.ai.hierarchical.InferenceTier
import com.zerotap.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HierarchicalInferenceCoordinatorTest {

    private val coordinator = HierarchicalInferenceCoordinator()

    @Test
    fun testDeviceAtRestIsQuiescentTier0() {
        val staticMotion = MotionSample(
            timestamp = System.currentTimeMillis(),
            accelerationX = 0.05f,
            accelerationY = 0.02f,
            accelerationZ = 9.80f,
            gyroX = 0.01f,
            gyroY = 0.01f,
            gyroZ = 0.01f
        )
        val quietAudio = AudioMetadata(
            timestamp = System.currentTimeMillis(),
            amplitudeDb = 35.0f,
            isRecording = true
        )

        val isQuiescent = coordinator.isDeviceQuiescent(staticMotion, quietAudio)
        assertTrue("Device resting undisturbed on a flat table should be quiescent", isQuiescent)
    }

    @Test
    fun testMotionDisturbanceWakesFromQuiescent() {
        val activeMotion = MotionSample(
            timestamp = System.currentTimeMillis(),
            accelerationX = 3.5f,
            accelerationY = 2.0f,
            accelerationZ = 12.0f,
            gyroX = 0.5f,
            gyroY = 0.3f,
            gyroZ = 0.2f
        )
        val quietAudio = AudioMetadata(
            timestamp = System.currentTimeMillis(),
            amplitudeDb = 35.0f,
            isRecording = true
        )

        val isQuiescent = coordinator.isDeviceQuiescent(activeMotion, quietAudio)
        assertFalse("Device in motion should NOT be quiescent", isQuiescent)
    }

    @Test
    fun testAudioDisturbanceWakesFromQuiescent() {
        val staticMotion = MotionSample(
            timestamp = System.currentTimeMillis(),
            accelerationX = 0.0f,
            accelerationY = 0.0f,
            accelerationZ = 9.81f,
            gyroX = 0.0f,
            gyroY = 0.0f,
            gyroZ = 0.0f
        )
        val loudAudio = AudioMetadata(
            timestamp = System.currentTimeMillis(),
            amplitudeDb = 68.0f,
            isRecording = true
        )

        val isQuiescent = coordinator.isDeviceQuiescent(staticMotion, loudAudio)
        assertFalse("Loud acoustic environment should wake device from quiescence", isQuiescent)
    }

    @Test
    fun testTier1ExecutionActivatesTier2OnHighRisk() = runTest {
        // High risk state activates Tier 2 incident reasoning
        val (_, prediction) = coordinator.executeTier1(
            motionWindow = null,
            audioMetadata = AudioMetadata(
                timestamp = System.currentTimeMillis(),
                amplitudeDb = 85.0f,
                isRecording = true
            ),
            recentAudio = emptyList(),
            locationContext = null,
            currentState = TemporalRiskState.HIGH_RISK
        )

        assertEquals(InferenceTier.TIER_2_INCIDENT_REASONING, coordinator.currentActiveTier)
    }
}
