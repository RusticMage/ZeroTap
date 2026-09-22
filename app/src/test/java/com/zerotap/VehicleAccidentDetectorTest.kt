package com.zerotap

import com.zerotap.domain.accident.AccidentDetectionConfig
import com.zerotap.domain.accident.AccidentEvidenceLevel
import com.zerotap.domain.accident.VehicleAccidentDetector
import com.zerotap.domain.model.LocationSample
import com.zerotap.domain.model.MotionContext
import com.zerotap.sensor.feature.ExtractedMotionFeatures
import org.junit.Assert.*
import org.junit.Test

class VehicleAccidentDetectorTest {

    private val detector = VehicleAccidentDetector()

    @Test
    fun testSevereImpactAndJerkProducesHighConfidence() {
        val features = ExtractedMotionFeatures(
            meanAccelMagnitude = 18.0f,
            peakAccelMagnitude = 42.0f, // > 35 threshold
            minAccelMagnitude = 1.0f,
            varianceAccelMagnitude = 25.0f,
            stdDevAccelMagnitude = 5.0f,
            meanGyroMagnitude = 2.0f,
            peakGyroMagnitude = 4.8f,   // > 4.0 threshold
            jerkMagnitude = 88.0f,      // > 75 threshold
            sampleCount = 100,
            durationMs = 2000L
        )
        val motionContext = MotionContext(
            timestamp = System.currentTimeMillis(),
            stationaryConfidence = 0.8f,
            walkingConfidence = 0.0f,
            runningConfidence = 0.0f,
            abruptMotionConfidence = 0.9f,
            impactConfidence = 0.9f,
            fallConfidence = 0.0f,
            peakAcceleration = 42.0f,
            meanAcceleration = 18.0f,
            jerkMagnitude = 88.0f,
            peakGyro = 4.8f
        )

        val evidence = detector.evaluate(features, motionContext, emptyList())

        assertEquals(AccidentEvidenceLevel.HIGH, evidence.evidenceLevel)
        assertTrue(evidence.confidence >= 0.70f)
        assertTrue(evidence.contributingFactors.any { it.contains("Severe Impact") })
        assertTrue(evidence.contributingFactors.any { it.contains("Kinematic Jerk") })
    }

    @Test
    fun testSpeedBumpRejectionWithTransitSpeedMaintained() {
        // High vertical acceleration spike (e.g. 26 m/s²) from speed bump, but vehicle maintains 35 km/h
        val features = ExtractedMotionFeatures(
            meanAccelMagnitude = 12.0f,
            peakAccelMagnitude = 26.0f,
            minAccelMagnitude = 4.0f,
            varianceAccelMagnitude = 14.0f,
            stdDevAccelMagnitude = 3.7f,
            meanGyroMagnitude = 0.8f,
            peakGyroMagnitude = 1.2f,
            jerkMagnitude = 42.0f,
            sampleCount = 100,
            durationMs = 2000L
        )
        val motionContext = MotionContext(
            timestamp = System.currentTimeMillis(),
            stationaryConfidence = 0.0f,
            walkingConfidence = 0.0f,
            runningConfidence = 0.0f,
            abruptMotionConfidence = 0.5f,
            impactConfidence = 0.3f,
            fallConfidence = 0.0f,
            peakAcceleration = 26.0f,
            meanAcceleration = 12.0f,
            jerkMagnitude = 42.0f,
            peakGyro = 1.2f
        )

        // 35 km/h ≈ 9.72 m/s
        val now = System.currentTimeMillis()
        val locations = listOf(
            LocationSample(timestamp = now - 2000, latitude = 13.0, longitude = 80.0, accuracy = 3.0f, speed = 9.72f),
            LocationSample(timestamp = now, latitude = 13.0001, longitude = 80.0001, accuracy = 3.0f, speed = 9.72f)
        )

        val evidence = detector.evaluate(features, motionContext, locations)

        // Speed bump filter reduces confidence back to LOW
        assertEquals(AccidentEvidenceLevel.LOW, evidence.evidenceLevel)
        assertTrue(evidence.contributingFactors.any { it.contains("Speed Bump") })
    }

    @Test
    fun testPhoneDropRejectionWithWalkingResumed() {
        // Phone dropped while walking: drop spike occurs, but walking confidence is high immediately after
        val features = ExtractedMotionFeatures(
            meanAccelMagnitude = 14.0f,
            peakAccelMagnitude = 25.0f,
            minAccelMagnitude = 1.5f,
            varianceAccelMagnitude = 15.0f,
            stdDevAccelMagnitude = 3.8f,
            meanGyroMagnitude = 1.5f,
            peakGyroMagnitude = 2.5f,
            jerkMagnitude = 42.0f,
            sampleCount = 100,
            durationMs = 2000L
        )
        val motionContext = MotionContext(
            timestamp = System.currentTimeMillis(),
            stationaryConfidence = 0.0f,
            walkingConfidence = 0.85f, // Walking resumed!
            runningConfidence = 0.0f,
            abruptMotionConfidence = 0.3f,
            impactConfidence = 0.6f,
            fallConfidence = 0.0f,
            peakAcceleration = 25.0f,
            meanAcceleration = 14.0f,
            jerkMagnitude = 42.0f,
            peakGyro = 2.5f
        )

        val evidence = detector.evaluate(features, motionContext, emptyList())

        assertEquals(AccidentEvidenceLevel.LOW, evidence.evidenceLevel)
    }

    @Test
    fun testConfigurableHeuristics() {
        // Custom strict config with higher threshold
        val strictConfig = AccidentDetectionConfig(
            peakAccelerationThreshold = 55.0f
        )
        val strictDetector = VehicleAccidentDetector(strictConfig)

        val features = ExtractedMotionFeatures(
            meanAccelMagnitude = 15.0f,
            peakAccelMagnitude = 40.0f, // Above default (35) but below strict (55)
            minAccelMagnitude = 1.0f,
            varianceAccelMagnitude = 15.0f,
            stdDevAccelMagnitude = 3.8f,
            meanGyroMagnitude = 0.5f,
            peakGyroMagnitude = 1.0f,
            jerkMagnitude = 30.0f,
            sampleCount = 100,
            durationMs = 2000L
        )

        val defaultEvidence = detector.evaluate(features, null, emptyList())
        val strictEvidence = strictDetector.evaluate(features, null, emptyList())

        // Default detector gives 0.40f for peakAccel >= 35, strict detector gives 0.20f because 40 < 55
        assertTrue(defaultEvidence.confidence > strictEvidence.confidence)
    }
}
