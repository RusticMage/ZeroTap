package com.zerotap

import com.zerotap.domain.model.*
import com.zerotap.domain.risk.DevelopmentRiskPredictionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRiskIntegrationTest {

    private val riskPredictionEngine = DevelopmentRiskPredictionEngine()

    @Test
    fun testScenarioA_LoudTrafficWithNormalMovementProducesLowRisk() {
        val now = System.currentTimeMillis()

        // Scenario A: Loud traffic (78 dB), but baseline is also 77 dB -> anomaly score is very low (0.05)
        val audioTraffic = AudioContext(
            timestamp = now,
            detectedClass = AudioClassification.TRAFFIC,
            confidence = 0.90f,
            anomalyScore = 0.05f,
            ambientLevelDb = 78.0f,
            baselineDb = 77.0f,
            isBaselineWarmedUp = true,
            modelAvailable = true
        )

        val normalMotion = MotionContext(
            timestamp = now,
            walkingConfidence = 0.85f,
            peakAcceleration = 11.2f,
            jerkMagnitude = 8.5f
        )

        val normalLocation = LocationContext(
            timestamp = now,
            latitude = 12.9716,
            longitude = 80.2435,
            accuracy = 5.0f,
            isMoving = true,
            isUnexpectedStop = false
        )

        val unifiedContext = UnifiedSensorContext(
            motion = normalMotion,
            audio = audioTraffic,
            location = normalLocation,
            timestamp = now
        )

        val result = riskPredictionEngine.evaluate(unifiedContext, TemporalRiskState.NORMAL)

        // Result must be low risk (< 15%) despite loud 78 dB sound
        assertTrue(
            "Scenario A (Traffic + Normal Motion) must yield low score (< 15%), got: ${result.scorePercent}",
            result.scorePercent < 15
        )
        assertEquals(TemporalRiskState.NORMAL, result.temporalState)
    }

    @Test
    fun testScenarioB_UnusualAudioWithNormalMovementProducesModerateConcern() {
        val now = System.currentTimeMillis()

        // Scenario B: Unusual acoustic distress/shouting (anomaly score 0.65), but motion is calm/normal
        val distressAudio = AudioContext(
            timestamp = now,
            detectedClass = AudioClassification.DISTRESS_SOUND,
            confidence = 0.85f,
            anomalyScore = 0.65f,
            ambientLevelDb = 79.0f,
            baselineDb = 45.0f,
            isBaselineWarmedUp = true,
            modelAvailable = true,
            distressLikePattern = true,
            elevatedVocalEnergy = true
        )

        val calmMotion = MotionContext(
            timestamp = now,
            stationaryConfidence = 0.80f,
            peakAcceleration = 9.8f,
            jerkMagnitude = 2.0f
        )

        val unifiedContext = UnifiedSensorContext(
            motion = calmMotion,
            audio = distressAudio,
            location = null,
            timestamp = now
        )

        val result = riskPredictionEngine.evaluate(unifiedContext, TemporalRiskState.NORMAL)

        // Audio alone produces moderate concern (15% to 35%), bounded: it NEVER triggers an emergency alone!
        assertTrue(
            "Scenario B: Audio alone should cause moderate concern (15%..35%), got: ${result.scorePercent}",
            result.scorePercent in 15..35
        )
    }

    @Test
    fun testScenarioC_UnusualAudioWithStruggleMotionProducesHighConcern() {
        val now = System.currentTimeMillis()

        // Scenario C: Unusual audio + struggle motion (sudden jerk & drop kinematics)
        val distressAudio = AudioContext(
            timestamp = now,
            detectedClass = AudioClassification.DISTRESS_SOUND,
            confidence = 0.88f,
            anomalyScore = 0.75f,
            ambientLevelDb = 82.0f,
            baselineDb = 48.0f,
            isBaselineWarmedUp = true,
            modelAvailable = true,
            distressLikePattern = true,
            elevatedVocalEnergy = true
        )

        val struggleMotion = MotionContext(
            timestamp = now,
            abruptMotionConfidence = 0.70f,
            impactConfidence = 0.65f,
            peakAcceleration = 24.5f,
            jerkMagnitude = 65.0f
        )

        val unifiedContext = UnifiedSensorContext(
            motion = struggleMotion,
            audio = distressAudio,
            location = null,
            timestamp = now
        )

        val result = riskPredictionEngine.evaluate(unifiedContext, TemporalRiskState.NORMAL)

        // Multi-modal correlation synergy (audio + motion) triggers high score (> 45%)
        assertTrue(
            "Scenario C: Multimodal synergy should yield high score (> 45%), got: ${result.scorePercent}",
            result.scorePercent > 45
        )
        assertTrue(
            "Should include multimodal synergy factor",
            result.contributingFactors.any { it.contains("Multi-Modal", ignoreCase = true) }
        )
    }

    @Test
    fun testScenarioD_UnusualAudioWithStruggleMotionAndUnexpectedStopProducesEscalatedConcern() {
        val now = System.currentTimeMillis()

        // Scenario D: Audio anomaly + struggle motion + unexpected halt in transit
        val distressAudio = AudioContext(
            timestamp = now,
            detectedClass = AudioClassification.DISTRESS_SOUND,
            confidence = 0.90f,
            anomalyScore = 0.80f,
            ambientLevelDb = 84.0f,
            baselineDb = 45.0f,
            isBaselineWarmedUp = true,
            modelAvailable = true,
            distressLikePattern = true
        )

        val struggleMotion = MotionContext(
            timestamp = now,
            impactConfidence = 0.80f,
            fallConfidence = 0.75f,
            peakAcceleration = 26.0f,
            jerkMagnitude = 80.0f
        )

        val haltLocation = LocationContext(
            timestamp = now,
            latitude = 13.0418,
            longitude = 80.2341,
            accuracy = 4.0f,
            isMoving = false,
            isUnexpectedStop = true
        )

        val unifiedContext = UnifiedSensorContext(
            motion = struggleMotion,
            audio = distressAudio,
            location = haltLocation,
            timestamp = now
        )

        val result = riskPredictionEngine.evaluate(unifiedContext, TemporalRiskState.HIGH_RISK)

        // Multiple concurrent anomalies escalate risk significantly (>= 75%)
        assertTrue(
            "Scenario D: Multi-anomaly escalation should yield critical score (>= 75%), got: ${result.scorePercent}",
            result.scorePercent >= 75
        )
    }

    @Test
    fun testAudioModelUnavailableGracefulDegradation() {
        val now = System.currentTimeMillis()

        // When audio model is unavailable or failed to load
        val unavailableAudio = AudioContext(
            timestamp = now,
            modelAvailable = false,
            ambientLevelDb = 0f,
            anomalyScore = 0f
        )

        val unifiedContext = UnifiedSensorContext(
            motion = MotionContext(timestamp = now, walkingConfidence = 0.7f),
            audio = unavailableAudio,
            location = null,
            timestamp = now
        )

        // Risk engine should evaluate gracefully without crashing
        val result = riskPredictionEngine.evaluate(unifiedContext, TemporalRiskState.NORMAL)
        assertTrue("Score should be valid", result.scorePercent >= 0)
    }
}
