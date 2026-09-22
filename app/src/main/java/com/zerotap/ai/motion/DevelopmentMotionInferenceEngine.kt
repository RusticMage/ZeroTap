package com.zerotap.ai.motion

import com.zerotap.domain.model.MotionClassification
import com.zerotap.domain.model.MotionPrediction
import com.zerotap.domain.model.MotionWindow
import com.zerotap.sensor.feature.ExtractedMotionFeatures
import com.zerotap.sensor.feature.SensorFeatureExtractor
import com.zerotap.sensor.feature.StandardSensorFeatureExtractor
import com.zerotap.sensor.motion.MotionInferenceEngine

/**
 * Development/deterministic implementation of MotionInferenceEngine.
 * Uses extracted features from StandardSensorFeatureExtractor.
 * Designed to be replaced with trained ML model without changing any other component.
 */
class DevelopmentMotionInferenceEngine(
    private val featureExtractor: SensorFeatureExtractor = StandardSensorFeatureExtractor()
) : MotionInferenceEngine {
    override val engineName: String = "Development Motion Classifier v1.1 (Feature-Based)"

    override suspend fun classify(window: MotionWindow): MotionPrediction {
        if (window.samples.isEmpty()) {
            return MotionPrediction(MotionClassification.UNKNOWN, 0.0f, System.currentTimeMillis())
        }
        val features = featureExtractor.extractMotionFeatures(window)
        return classifyFeatures(features)
    }

    override suspend fun classifyFeatures(features: ExtractedMotionFeatures): MotionPrediction {
        val now = System.currentTimeMillis()
        if (features.sampleCount == 0) {
            return MotionPrediction(MotionClassification.UNKNOWN, 0.0f, now)
        }

        val peakAccel = features.peakAccelMagnitude
        val meanAccel = features.meanAccelMagnitude
        val varianceAccel = features.varianceAccelMagnitude
        val jerk = features.jerkMagnitude
        val peakGyro = features.peakGyroMagnitude

        return when {
            // Freefall drop impact or major phone collision
            peakAccel > 26f || (peakAccel > 22f && features.minAccelMagnitude < 2.5f) -> {
                MotionPrediction(MotionClassification.PHONE_DROP, 0.85f, now)
            }
            // Violent movement, struggle or sudden snatch jerk
            (peakAccel > 18f && jerk > 60f) || (peakGyro > 6f && varianceAccel > 10f) -> {
                MotionPrediction(MotionClassification.SUDDEN_JERK, 0.75f, now)
            }
            // Running / active locomotion
            meanAccel > 13.5f && varianceAccel > 6f -> {
                MotionPrediction(MotionClassification.RUNNING, 0.70f, now)
            }
            // Walking pace
            meanAccel in 8.5f..13.5f && varianceAccel > 1.2f -> {
                MotionPrediction(MotionClassification.NORMAL_WALKING, 0.75f, now)
            }
            // Phone at rest (Earth gravity ~9.8m/s^2, low variance and low gyro)
            varianceAccel < 0.35f && peakGyro < 0.25f -> {
                MotionPrediction(MotionClassification.STATIONARY, 0.90f, now)
            }
            else -> {
                MotionPrediction(MotionClassification.UNKNOWN, 0.40f, now)
            }
        }
    }
}
