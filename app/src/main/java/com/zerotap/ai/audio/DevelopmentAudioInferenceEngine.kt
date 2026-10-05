package com.zerotap.ai.audio

import com.zerotap.domain.model.AudioContext
import com.zerotap.domain.model.AudioMetadata
import com.zerotap.domain.model.AudioPrediction
import com.zerotap.sensor.audio.AudioInferenceEngine
import com.zerotap.sensor.audio.RobustAudioEngine

/**
 * Production-ready on-device implementation of AudioInferenceEngine.
 * Integrates dynamic acoustic baseline, temporal persistence, and multi-modal anomaly scoring.
 */
class DevelopmentAudioInferenceEngine(
    val robustEngine: RobustAudioEngine = RobustAudioEngine()
) : AudioInferenceEngine {

    override val engineName: String = robustEngine.engineName

    override suspend fun classify(metadata: AudioMetadata, recentHistory: List<AudioMetadata>): AudioPrediction {
        return robustEngine.classify(metadata, recentHistory)
    }

    fun analyzeToContext(metadata: AudioMetadata, recentHistory: List<AudioMetadata>): AudioContext {
        return robustEngine.analyzeToContext(metadata, recentHistory)
    }
}
