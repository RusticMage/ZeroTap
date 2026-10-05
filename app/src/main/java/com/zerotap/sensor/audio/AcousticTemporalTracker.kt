package com.zerotap.sensor.audio

import com.zerotap.domain.model.AudioClassification

/**
 * Handles temporal persistence, filtering, and hysteresis for acoustic classification.
 * Prevents transient, isolated acoustic spikes (e.g., slamming car door, momentary horn)
 * from triggering erratic risk level fluctuations.
 */
class AcousticTemporalTracker(
    private val persistenceThresholdFrames: Int = 2,
    private val recoveryFrames: Int = 3
) {
    var consecutiveAnomalousFrames: Int = 0
        private set

    var consecutiveNormalFrames: Int = 0
        private set

    var smoothedAnomalyScore: Float = 0.0f
        private set

    /**
     * Filters a candidate anomaly score (0.0 .. 1.0) and candidate classification
     * through a temporal persistence window with exponential hysteresis.
     *
     * @return Pair of (smoothedAnomalyScore: Float, confirmedClassification: AudioClassification)
     */
    fun processFrame(
        candidateScore: Float,
        candidateClass: AudioClassification
    ): Pair<Float, AudioClassification> {
        val isCandidateElevated = candidateScore >= 0.35f

        if (isCandidateElevated) {
            consecutiveAnomalousFrames++
            consecutiveNormalFrames = 0
        } else {
            consecutiveNormalFrames++
            if (consecutiveNormalFrames >= recoveryFrames) {
                consecutiveAnomalousFrames = 0
            }
        }

        // Temporal persistence weighting factor:
        // Frame 1: 45% impact (candidate only)
        // Frame 2: 85% impact (persistence confirmed)
        // Frame 3+: 100% impact
        val persistenceFactor = when (consecutiveAnomalousFrames) {
            0 -> 0.05f
            1 -> 0.45f
            2 -> 0.85f
            else -> 1.0f
        }

        val effectiveTarget = candidateScore * persistenceFactor
        // Hysteresis exponential smoothing (alpha = 0.50)
        smoothedAnomalyScore = 0.50f * smoothedAnomalyScore + 0.50f * effectiveTarget

        val confirmedClassification = when {
            // Sustained distress vocal requires at least 2 consecutive frames
            candidateClass == AudioClassification.DISTRESS_SOUND -> {
                if (consecutiveAnomalousFrames >= persistenceThresholdFrames) {
                    AudioClassification.DISTRESS_SOUND
                } else {
                    AudioClassification.LOUD_NOISE
                }
            }
            // Sustained shouting
            candidateClass == AudioClassification.SHOUTING -> {
                if (consecutiveAnomalousFrames >= persistenceThresholdFrames) {
                    AudioClassification.SHOUTING
                } else {
                    AudioClassification.LOUD_NOISE
                }
            }
            // Sharp acoustic impact (crash, gunshot, glass) requires high immediate confidence
            candidateClass == AudioClassification.LOUD_ACOUSTIC_EVENT -> {
                AudioClassification.LOUD_ACOUSTIC_EVENT
            }
            candidateClass == AudioClassification.TRAFFIC -> {
                AudioClassification.TRAFFIC
            }
            candidateClass == AudioClassification.SPEECH -> {
                AudioClassification.SPEECH
            }
            candidateClass == AudioClassification.SILENCE -> {
                if (consecutiveNormalFrames >= 2) AudioClassification.SILENCE else AudioClassification.NORMAL
            }
            smoothedAnomalyScore > 0.30f -> {
                AudioClassification.LOUD_NOISE
            }
            else -> {
                AudioClassification.NORMAL
            }
        }

        return Pair(smoothedAnomalyScore.coerceIn(0.0f, 1.0f), confirmedClassification)
    }

    fun reset() {
        consecutiveAnomalousFrames = 0
        consecutiveNormalFrames = 0
        smoothedAnomalyScore = 0.0f
    }
}
