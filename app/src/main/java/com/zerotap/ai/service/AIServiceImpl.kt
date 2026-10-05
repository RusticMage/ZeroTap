package com.zerotap.ai.service

import com.zerotap.ai.byok.AIProvider
import com.zerotap.ai.byok.BYOKAIProvider
import com.zerotap.ai.local.LocalAIProvider
import com.zerotap.ai.models.*
import com.zerotap.ai.server.ServerAIProvider
import com.zerotap.core.config.AppConfiguration
import com.zerotap.core.config.DeploymentMode
import com.zerotap.domain.model.AudioContext
import com.zerotap.util.Logger

/**
 * Unified AIService orchestrator (Requirements 6, 7, 25).
 * Selects between Local, BYOK, and Server providers while guaranteeing
 * deterministic fallback if any AI fails, times out, or is offline.
 */
class AIServiceImpl(
    private val localProvider: LocalAIProvider = LocalAIProvider(),
    private val byokProvider: BYOKAIProvider? = null,
    private val serverProvider: ServerAIProvider = ServerAIProvider(localProvider)
) : AIService {

    private fun selectActiveProvider(): AIProvider {
        return when (AppConfiguration.currentMode) {
            DeploymentMode.PRIVATE -> {
                if (byokProvider != null && byokProvider.isAvailable) {
                    byokProvider
                } else {
                    localProvider
                }
            }
            DeploymentMode.SERVER -> {
                if (serverProvider.isAvailable) {
                    serverProvider
                } else {
                    localProvider
                }
            }
        }
    }

    override suspend fun analyzeAudio(input: AudioInput): AudioContext {
        val provider = selectActiveProvider()
        return try {
            provider.analyzeAudio(input)
        } catch (e: Exception) {
            Logger.ai("AIService", "Active provider ${provider.providerId} failed: ${e.message}. Falling back to local engine.")
            localProvider.analyzeAudio(input)
        }
    }

    override suspend fun inferContext(input: SensorContext): InferredContext {
        val provider = selectActiveProvider()
        return try {
            provider.inferContext(input)
        } catch (e: Exception) {
            Logger.ai("AIService", "Inference failed on ${provider.providerId}. Falling back to local engine.")
            localProvider.inferContext(input)
        }
    }

    override suspend fun analyzeVehicleImage(input: ImageInput): VehicleEvidence {
        val provider = selectActiveProvider()
        return try {
            provider.analyzeVehicleImage(input)
        } catch (e: Exception) {
            Logger.ai("AIService", "Vision analysis failed on ${provider.providerId}. Falling back to local engine.")
            localProvider.analyzeVehicleImage(input)
        }
    }

    override suspend fun analyzeEvidence(input: EvidenceInput): EvidenceDecision {
        val provider = selectActiveProvider()
        return try {
            provider.analyzeEvidence(input)
        } catch (e: Exception) {
            Logger.ai("AIService", "Evidence correlation failed on ${provider.providerId}. Falling back to local engine.")
            localProvider.analyzeEvidence(input)
        }
    }
}
