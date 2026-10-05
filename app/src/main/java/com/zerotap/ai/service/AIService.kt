package com.zerotap.ai.service

import com.zerotap.ai.models.*
import com.zerotap.domain.model.AudioContext

/**
 * Top-level application AI interface (Requirement Section 6).
 *
 * The rest of the application depends strictly on this abstraction.
 * It never directly calls Gemma, Gemini, Vertex AI, OpenAI, or raw HTTP APIs.
 */
interface AIService {

    suspend fun analyzeAudio(
        input: AudioInput
    ): AudioContext

    suspend fun inferContext(
        input: SensorContext
    ): InferredContext

    suspend fun analyzeVehicleImage(
        input: ImageInput
    ): VehicleEvidence

    suspend fun analyzeEvidence(
        input: EvidenceInput
    ): EvidenceDecision
}
