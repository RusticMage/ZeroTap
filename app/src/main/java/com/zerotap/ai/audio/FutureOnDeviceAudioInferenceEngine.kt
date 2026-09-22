package com.zerotap.ai.audio

import com.zerotap.domain.model.AudioMetadata
import com.zerotap.domain.model.AudioPrediction
import com.zerotap.sensor.audio.AudioInferenceEngine

class FutureOnDeviceAudioInferenceEngine : AudioInferenceEngine {
    override val engineName: String = "On-Device Audio Model (Not Yet Implemented)"

    override suspend fun classify(metadata: AudioMetadata, recentHistory: List<AudioMetadata>): AudioPrediction {
        throw UnsupportedOperationException("TFLite/ONNX on-device audio model not yet implemented")
    }
}
