package com.zerotap.domain.risk

import com.zerotap.domain.model.*

/**
 * Deterministic, explainable local risk prediction engine.
 *
 * NOTE: This is a Development/Feature-Based model running 100% on-device.
 * It is NOT a trained neural network, but implements the exact architecture ready
 * to accept trained TFLite/ONNX/Qualcomm QNN models in future revisions.
 */
class DevelopmentRiskPredictionEngine : RiskPredictionEngine {

    override val engineLabel: String = "Development Risk Prediction Engine (Feature-Based Local Multi-Modal)"

    override fun evaluate(context: UnifiedSensorContext, currentState: TemporalRiskState): RiskPredictionResult {
        val now = context.timestamp

        var rawMotionScore = 0.0f
        var rawAudioScore = 0.0f
        var rawLocationScore = 0.0f
        val factors = mutableListOf<String>()

        // 1. EVALUATE MOTION CONTEXT
        val motion = context.motion
        if (motion != null) {
            // High impact or phone drop
            if (motion.impactConfidence > 0.3f || motion.fallConfidence > 0.3f || motion.peakAcceleration > 24f) {
                rawMotionScore += 0.45f * (motion.impactConfidence.coerceAtLeast(motion.fallConfidence).coerceAtLeast(0.6f))
                factors.add("Impact/Drop Kinematics (Peak: %.1f m/s²)".format(motion.peakAcceleration))
            } else if (motion.abruptMotionConfidence > 0.3f || motion.jerkMagnitude > 50f || motion.peakAcceleration > 18f) {
                rawMotionScore += 0.35f * (motion.abruptMotionConfidence.coerceAtLeast(0.5f))
                factors.add("Abrupt Movement (Jerk: %.1f m/s³)".format(motion.jerkMagnitude))
            } else if (motion.runningConfidence > 0.6f) {
                rawMotionScore += 0.12f * motion.runningConfidence
                factors.add("High Locomotion Activity (Running)")
            } else if (motion.walkingConfidence > 0.5f) {
                rawMotionScore += 0.02f
            }

            // Stationary dampening
            if (motion.stationaryConfidence > 0.7f && motion.peakAcceleration < 12f) {
                rawMotionScore = (rawMotionScore - 0.10f).coerceAtLeast(0.0f)
            }
        }

        // 2. EVALUATE AUDIO CONTEXT (Dynamic Acoustic Baseline & Anomaly Driven)
        val audio = context.audio
        if (audio != null && audio.modelAvailable) {
            val deviation = (audio.ambientLevelDb - audio.baselineDb).coerceAtLeast(0f)

            if (audio.distressLikePattern && audio.anomalyScore >= 0.35f) {
                // Persistent vocal distress well above baseline
                rawAudioScore += (0.35f * audio.anomalyScore * audio.confidence).coerceIn(0.15f, 0.35f)
                factors.add("Acoustic Vocal Distress Pattern (%.1f dB, dev +%.1f dB)".format(audio.ambientLevelDb, deviation))
            } else if (audio.loudImpactDetected && audio.anomalyScore >= 0.30f) {
                // Sharp acoustic impact spike
                rawAudioScore += (0.28f * audio.anomalyScore * audio.confidence).coerceIn(0.12f, 0.28f)
                factors.add("Acoustic Impact / Sharp Spike (%.1f dB, dev +%.1f dB)".format(audio.ambientLevelDb, deviation))
            } else if (audio.elevatedVocalEnergy && audio.anomalyScore >= 0.25f) {
                // Elevated shouting / vocal energy deviating from baseline
                rawAudioScore += (0.20f * audio.anomalyScore).coerceIn(0.08f, 0.20f)
                factors.add("Elevated Shouting / Vocal Activity (%.1f dB, dev +%.1f dB)".format(audio.ambientLevelDb, deviation))
            } else if (audio.anomalyScore >= 0.35f) {
                // Significant acoustic anomaly relative to current baseline
                rawAudioScore += (0.15f * audio.anomalyScore).coerceIn(0.05f, 0.15f)
                factors.add("Acoustic Baseline Anomaly (%.1f dB vs baseline %.1f dB)".format(audio.ambientLevelDb, audio.baselineDb))
            }
            // Note: Steady ambient noise (traffic, campus crowd, inside bus/train) matches baseline,
            // producing anomalyScore < 0.20, contributing 0 to rawAudioScore.
        }

        // 3. EVALUATE LOCATION CONTEXT (Kinematics & Movement Continuity)
        val location = context.location
        if (location != null) {
            if (location.isUnexpectedStop) {
                rawLocationScore += 0.25f
                factors.add("Unexpected Stop (>30s stationary halt after transit)")
            }
        }

        // 4. MULTI-MODAL SYNERGY (Correlation Bonus)
        var synergyScore = 0.0f
        if (rawMotionScore > 0.20f && rawAudioScore > 0.20f) {
            synergyScore = 0.15f
            factors.add("Multi-Modal Anomaly Correlation (Motion + Audio)")
        }

        // 5. TEMPORAL PERSISTENCE CONTRIBUTION
        val rawTemporalScore = when (currentState) {
            TemporalRiskState.EMERGENCY_TRIGGERED -> 0.30f
            TemporalRiskState.EMERGENCY_PENDING -> 0.25f
            TemporalRiskState.HIGH_RISK -> 0.15f
            TemporalRiskState.ELEVATED -> 0.08f
            TemporalRiskState.NORMAL -> 0.0f
        }
        if (rawTemporalScore > 0.10f) {
            factors.add("Risk persistence elevated (${currentState.displayName})")
        }

        // Total raw composite score
        val totalRaw = rawMotionScore + rawAudioScore + rawLocationScore + synergyScore + rawTemporalScore
        val clampedScore = totalRaw.coerceIn(0.0f, 1.0f)
        val scorePercent = (clampedScore * 100f).toInt().coerceIn(0, 100)

        // Calculate exact mathematical normalized contributions
        val denominator = if (totalRaw > 0.001f) totalRaw else 1.0f
        val motionContrib = (rawMotionScore / denominator).coerceIn(0f, 1f)
        val audioContrib = (rawAudioScore / denominator).coerceIn(0f, 1f)
        val locationContrib = (rawLocationScore / denominator).coerceIn(0f, 1f)
        val temporalContrib = ((rawTemporalScore + synergyScore) / denominator).coerceIn(0f, 1f)

        fun mapLevel(contrib: Float): ContributionLevel = when {
            contrib >= 0.35f -> ContributionLevel.HIGH
            contrib >= 0.20f -> ContributionLevel.ELEVATED
            contrib >= 0.10f -> ContributionLevel.MODERATE
            else -> ContributionLevel.LOW
        }

        return RiskPredictionResult(
            scorePercent = scorePercent,
            temporalState = currentState,
            contributingFactors = if (factors.isEmpty()) listOf("Phone context within normal parameters") else factors,
            timestamp = now,
            engineLabel = engineLabel,
            motionContribution = motionContrib,
            audioContribution = audioContrib,
            locationContribution = locationContrib,
            temporalContribution = temporalContrib,
            motionLevel = mapLevel(motionContrib),
            voiceLevel = mapLevel(audioContrib),
            locationLevel = mapLevel(locationContrib),
            persistenceLevel = mapLevel(temporalContrib)
        )
    }
}
