package com.zerotap.domain.accident

import com.zerotap.domain.model.LocationSample
import com.zerotap.domain.model.MotionContext
import com.zerotap.sensor.feature.ExtractedMotionFeatures
import com.zerotap.util.Logger

/**
 * Detects possible vehicle accidents using an evidence/confidence model over shared physical sensor streams.
 *
 * Implements independent evidence evaluation:
 * - Impact / deceleration
 * - High jerk
 * - Rotational / angular velocity anomaly
 * - Post-impact stationary state
 * - Optional GPS speed collapse (corroborating bonus when present, never mandatory)
 */
class VehicleAccidentDetector(
    private val config: AccidentDetectionConfig = AccidentDetectionConfig()
) {

    /**
     * Evaluates latest motion and location data against accident evidence criteria.
     */
    fun evaluate(
        features: ExtractedMotionFeatures?,
        motionContext: MotionContext?,
        recentLocations: List<LocationSample>
    ): AccidentEvidence {
        if (features == null) {
            return AccidentEvidence(evidenceLevel = AccidentEvidenceLevel.LOW)
        }

        var confidence = 0.0f
        val factors = mutableListOf<String>()

        val peakAccel = features.peakAccelMagnitude
        val peakGyro = features.peakGyroMagnitude
        val jerk = features.jerkMagnitude

        // 1. Strong Impact / Deceleration Evidence (Independent Signal)
        if (peakAccel >= config.peakAccelerationThreshold) {
            confidence += 0.40f
            factors.add("Severe Impact / Deceleration (%.1f m/s²)".format(peakAccel))
        } else if (peakAccel >= 24.0f) {
            confidence += 0.20f
            factors.add("Moderate Deceleration Spike (%.1f m/s²)".format(peakAccel))
        }

        // 2. High Jerk Evidence (Independent Signal)
        if (jerk >= config.jerkMagnitudeThreshold) {
            confidence += 0.30f
            factors.add("Extreme Kinematic Jerk (%.1f m/s³)".format(jerk))
        } else if (jerk >= 40.0f) {
            confidence += 0.15f
            factors.add("Elevated Jerk Transient (%.1f m/s³)".format(jerk))
        }

        // 3. Rotational / Angular Velocity Anomaly (Independent Signal)
        if (peakGyro >= config.peakGyroThreshold) {
            confidence += 0.20f
            factors.add("Rotational Tumble / Gyro Surge (%.1f rad/s)".format(peakGyro))
        } else if (peakGyro >= 2.0f) {
            confidence += 0.10f
            factors.add("Moderate Angular Impulse (%.1f rad/s)".format(peakGyro))
        }

        // 4. Post-Impact Stationary State Evidence
        val isStationary = motionContext != null && (
            motionContext.stationaryConfidence > 0.5f ||
            (features.varianceAccelMagnitude < 2.0f && peakAccel < 12.0f)
        )
        if (isStationary && (peakAccel >= 24.0f || jerk >= 40.0f)) {
            confidence += 0.25f
            factors.add("Post-Impact Inactivity (Device Stationary)")
        }

        // 5. GPS Speed Corroboration (OPTIONAL BONUS — Never mandatory)
        var preSpeedKmh: Float? = null
        var postSpeedKmh: Float? = null

        if (recentLocations.size >= 2) {
            val preLoc = recentLocations.first()
            val postLoc = recentLocations.last()

            val preKmh = preLoc.speed * 3.6f
            val postKmh = postLoc.speed * 3.6f
            preSpeedKmh = preKmh
            postSpeedKmh = postKmh

            if (preKmh >= config.preImpactVehicleSpeedKmh && postKmh <= config.postImpactStationarySpeedKmh) {
                // High confidence vehicular transit followed by abrupt collapse
                confidence += 0.25f
                factors.add("Vehicle Speed Collapse (%.0f km/h → %.0f km/h)".format(preKmh, postKmh))
            } else if (postKmh >= config.preImpactVehicleSpeedKmh) {
                // Speed bump / pothole rejection: vehicle still traveling at normal vehicle speed!
                confidence = (confidence - 0.40f).coerceAtLeast(0.0f)
                factors.add("Speed Bump / Road Anomaly Filter (Transit Speed Maintained: %.0f km/h)".format(postKmh))
            }
        }

        // 6. Phone Drop / Normal Handling Rejection
        if (motionContext != null && motionContext.walkingConfidence > 0.5f) {
            // Walking resumed immediately after drop
            confidence = (confidence - 0.35f).coerceAtLeast(0.0f)
        }

        val clampedConfidence = confidence.coerceIn(0.0f, 1.0f)

        val level = when {
            clampedConfidence >= config.highConfidenceThreshold -> AccidentEvidenceLevel.HIGH
            clampedConfidence >= config.mediumConfidenceThreshold -> AccidentEvidenceLevel.MEDIUM
            else -> AccidentEvidenceLevel.LOW
        }

        if (level != AccidentEvidenceLevel.LOW) {
            Logger.alert("AccidentDetector", "[ZeroTap][Accident] Possible impact evaluated: Confidence: %.2f (Level: %s), Factors: %s".format(
                clampedConfidence, level.name, factors.joinToString(", ")
            ))
        }

        return AccidentEvidence(
            peakAcceleration = peakAccel,
            peakGyro = peakGyro,
            jerkMagnitude = jerk,
            preImpactSpeedKmh = preSpeedKmh,
            postImpactSpeedKmh = postSpeedKmh,
            isPostImpactStationary = isStationary,
            confidence = clampedConfidence,
            evidenceLevel = level,
            contributingFactors = factors,
            timestamp = System.currentTimeMillis()
        )
    }
}
