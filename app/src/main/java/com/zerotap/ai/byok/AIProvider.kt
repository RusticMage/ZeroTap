package com.zerotap.ai.byok

import com.zerotap.ai.models.*
import com.zerotap.domain.model.AudioContext

/**
 * Pluggable AI Provider interface (Requirement Section 8).
 *
 * Implemented by:
 * - LocalOnDeviceAIProvider (heuristic / TFLite / Edge models)
 * - BYOKAIProvider (user-provided API key: OpenAI, Gemini, Claude, Ollama, etc.)
 * - ServerAIProvider (ZeroTap Spring Boot backend calling Vertex AI)
 */
interface AIProvider {

    val providerId: String
    val providerName: String
    val isAvailable: Boolean

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
