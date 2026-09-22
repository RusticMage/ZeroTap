package com.zerotap.ai.motion

import com.zerotap.domain.model.MotionPrediction
import com.zerotap.domain.model.MotionWindow
import com.zerotap.sensor.feature.ExtractedMotionFeatures
import com.zerotap.sensor.motion.MotionInferenceEngine

/**
 * Clean placeholder for on-device ML model (TFLite / ONNX Runtime / Qualcomm QNN).
 * Replace the classify/classifyFeatures methods with your trained model tensor evaluation.
 */
class FutureOnDeviceMotionInferenceEngine : MotionInferenceEngine {
    override val engineName: String = "On-Device Neural Motion Classifier (TFLite/QNN)"

    override suspend fun classify(window: MotionWindow): MotionPrediction {
        throw UnsupportedOperationException("TFLite / ONNX model file not yet bundled.")
    }

    override suspend fun classifyFeatures(features: ExtractedMotionFeatures): MotionPrediction {
        throw UnsupportedOperationException("TFLite / ONNX model file not yet bundled.")
    }
}
