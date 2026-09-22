package com.zerotap.sensor.motion

import com.zerotap.domain.model.MotionPrediction
import com.zerotap.domain.model.MotionWindow
import com.zerotap.sensor.feature.ExtractedMotionFeatures

/**
 * Interface for motion classification.
 * Allows replacing rule-based heuristics with TensorFlow Lite / ONNX / Qualcomm NPU models.
 */
interface MotionInferenceEngine {
    suspend fun classify(window: MotionWindow): MotionPrediction
    suspend fun classifyFeatures(features: ExtractedMotionFeatures): MotionPrediction
    val engineName: String
}
