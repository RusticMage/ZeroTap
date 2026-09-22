package com.zerotap.ai.hierarchical

import com.zerotap.ai.audio.DevelopmentAudioInferenceEngine
import com.zerotap.ai.motion.DevelopmentMotionInferenceEngine
import com.zerotap.domain.model.*
import com.zerotap.domain.risk.DevelopmentRiskPredictionEngine
import com.zerotap.domain.risk.RiskPredictionEngine
import com.zerotap.sensor.audio.AudioInferenceEngine
import com.zerotap.sensor.feature.ExtractedMotionFeatures
import com.zerotap.sensor.feature.SensorFeatureExtractor
import com.zerotap.sensor.feature.StandardSensorFeatureExtractor
import com.zerotap.sensor.motion.MotionInferenceEngine
import com.zerotap.util.Logger
import kotlin.math.abs

enum class InferenceTier {
    TIER_0_LOW_POWER_REST,
    TIER_1_LIGHTWEIGHT_INFERENCE,
    TIER_2_INCIDENT_REASONING
}

/**
 * Coordinates staged hierarchical edge inference for optimal battery efficiency without
 * regressing the authoritative multimodal verification logic.
 *
 * Tier 0: Cheap quiescence sleep check (only skips processing if device is completely undisturbed at rest).
 * Tier 1: Lightweight motion feature extraction and audio temporal inference.
 * Authoritative Layer: Multimodal risk prediction engine & temporal state machine.
 * Tier 2: Event-triggered incident reasoning & deterrence (only invoked during verified incidents).
 */
class HierarchicalInferenceCoordinator(
    private val featureExtractor: SensorFeatureExtractor = StandardSensorFeatureExtractor(),
    val motionEngine: MotionInferenceEngine = DevelopmentMotionInferenceEngine(featureExtractor),
    val audioEngine: AudioInferenceEngine = DevelopmentAudioInferenceEngine(),
    val riskEngine: RiskPredictionEngine = DevelopmentRiskPredictionEngine()
) {
    var currentActiveTier: InferenceTier = InferenceTier.TIER_0_LOW_POWER_REST
        private set

    /**
     * Tier 0: Very cheap quiescence filter.
     * Returns true ONLY if the device is resting completely static and silent.
     */
    fun isDeviceQuiescent(
        latestMotion: MotionSample?,
        latestAudio: AudioMetadata?
    ): Boolean {
        if (latestMotion == null) return false
        val devFromGravity = abs(latestMotion.accelerationMagnitude - 9.81f)
        val isMotionStatic = devFromGravity < 0.4f && latestMotion.gyroMagnitude < 0.2f
        val isAudioQuiet = latestAudio == null || latestAudio.amplitudeDb < 45.0f

        return isMotionStatic && isAudioQuiet
    }

    /**
     * Tier 1: Lightweight feature extraction & inference pass.
     */
    suspend fun executeTier1(
        motionWindow: MotionWindow?,
        audioMetadata: AudioMetadata?,
        recentAudio: List<AudioMetadata>,
        locationContext: LocationContext?,
        currentState: TemporalRiskState
    ): Pair<ExtractedMotionFeatures?, RiskPredictionResult> {
        currentActiveTier = InferenceTier.TIER_1_LIGHTWEIGHT_INFERENCE

        var features: ExtractedMotionFeatures? = null
        var motionContext: MotionContext? = null

        if (motionWindow != null && motionWindow.samples.isNotEmpty()) {
            val extracted = featureExtractor.extractMotionFeatures(motionWindow)
            features = extracted
            val pred = motionEngine.classifyFeatures(extracted)

            motionContext = MotionContext(
                timestamp = System.currentTimeMillis(),
                stationaryConfidence = if (pred.classification == MotionClassification.STATIONARY) pred.confidence else 0f,
                walkingConfidence = if (pred.classification == MotionClassification.NORMAL_WALKING) pred.confidence else 0f,
                runningConfidence = if (pred.classification == MotionClassification.RUNNING) pred.confidence else 0f,
                abruptMotionConfidence = if (pred.classification == MotionClassification.SUDDEN_JERK) pred.confidence else 0f,
                impactConfidence = if (pred.classification == MotionClassification.PHONE_DROP) pred.confidence else 0f,
                fallConfidence = if (extracted.peakAccelMagnitude > 24f && extracted.minAccelMagnitude < 2.5f) 0.8f else 0f,
                peakAcceleration = extracted.peakAccelMagnitude,
                meanAcceleration = extracted.meanAccelMagnitude,
                jerkMagnitude = extracted.jerkMagnitude,
                peakGyro = extracted.peakGyroMagnitude
            )
        }

        var audioContext: AudioContext? = null
        if (audioMetadata != null) {
            val pred = audioEngine.classify(audioMetadata, recentAudio)
            audioContext = AudioContext(
                timestamp = System.currentTimeMillis(),
                voiceActivityDetected = audioMetadata.amplitudeDb > 55f,
                elevatedVocalEnergy = audioMetadata.amplitudeDb > 70f,
                distressLikePattern = pred.classification == AudioClassification.DISTRESS_SOUND,
                loudImpactDetected = pred.classification == AudioClassification.LOUD_ACOUSTIC_EVENT,
                ambientLevelDb = audioMetadata.amplitudeDb,
                classificationLabel = pred.classification.displayName,
                confidence = pred.confidence
            )
        }

        val unifiedContext = UnifiedSensorContext(
            motion = motionContext,
            audio = audioContext,
            location = locationContext,
            timestamp = System.currentTimeMillis()
        )

        // Authoritative Multimodal Verification Engine
        val predictionResult = riskEngine.evaluate(unifiedContext, currentState)

        if (predictionResult.scorePercent >= 60 || currentState != TemporalRiskState.NORMAL) {
            currentActiveTier = InferenceTier.TIER_2_INCIDENT_REASONING
        }

        return Pair(features, predictionResult)
    }
}
