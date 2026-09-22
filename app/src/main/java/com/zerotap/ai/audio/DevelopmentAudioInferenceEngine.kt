package com.zerotap.ai.audio

import com.zerotap.domain.model.AudioClassification
import com.zerotap.domain.model.AudioMetadata
import com.zerotap.domain.model.AudioPrediction
import com.zerotap.sensor.audio.AudioInferenceEngine

/**
 * Development/deterministic implementation of AudioInferenceEngine.
 * Note: These are NOT real audio classifiers.
 */
class DevelopmentAudioInferenceEngine : AudioInferenceEngine {
    override val engineName: String = "Development Audio Classifier v1.0"

    override suspend fun classify(metadata: AudioMetadata, recentHistory: List<AudioMetadata>): AudioPrediction {
        val amplitudeDb = metadata.amplitudeDb
        val recentHigh = recentHistory.count { it.amplitudeDb > 75 }
        val recentSilence = recentHistory.count { it.amplitudeDb < 20 }
        
        // Spike pattern: current is high, but history average is much lower
        val historyAvg = if (recentHistory.isNotEmpty()) recentHistory.map { it.amplitudeDb.toDouble() }.average().toFloat() else amplitudeDb
        val isSpike = amplitudeDb > 90 && (amplitudeDb - historyAvg) > 30

        val now = System.currentTimeMillis()
        return when {
            isSpike -> AudioPrediction(AudioClassification.LOUD_ACOUSTIC_EVENT, 0.4f, now)
            amplitudeDb > 85 -> AudioPrediction(AudioClassification.LOUD_NOISE, 0.6f, now)
            amplitudeDb > 75 && recentHigh >= 2 -> AudioPrediction(AudioClassification.DISTRESS_SOUND, 0.5f, now)
            amplitudeDb < 20 && recentSilence >= 4 -> AudioPrediction(AudioClassification.SILENCE, 0.7f, now)
            else -> AudioPrediction(AudioClassification.NORMAL, 0.8f, now)
        }
    }
}
