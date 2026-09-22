package com.zerotap.sensor.audio

import com.zerotap.domain.model.AudioMetadata
import com.zerotap.domain.model.AudioPrediction

interface AudioInferenceEngine {
    suspend fun classify(metadata: AudioMetadata, recentHistory: List<AudioMetadata>): AudioPrediction
    val engineName: String
}
