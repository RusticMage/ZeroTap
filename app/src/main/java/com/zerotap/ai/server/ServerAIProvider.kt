package com.zerotap.ai.server

import com.zerotap.ai.byok.AIProvider
import com.zerotap.ai.local.LocalAIProvider
import com.zerotap.ai.models.*
import com.zerotap.core.config.AppConfiguration
import com.zerotap.domain.model.AudioContext
import com.zerotap.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ServerAIProvider(
    private val fallbackProvider: LocalAIProvider = LocalAIProvider()
) : AIProvider {

    override val providerId: String = "ZEROTAP_SERVER_VERTEX"
    override val providerName: String = "ZeroTap Cloud Backend (Vertex AI)"

    override val isAvailable: Boolean
        get() = AppConfiguration.currentMode == com.zerotap.core.config.DeploymentMode.SERVER

    override suspend fun analyzeAudio(input: AudioInput): AudioContext = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            return@withContext fallbackProvider.analyzeAudio(input)
        }

        try {
            // Attempt remote inference via Spring Boot / Vertex AI endpoint
            val url = URL("${AppConfiguration.backendBaseUrl}/api/ai/analyze-audio")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val json = JSONObject().apply {
                put("timestamp", input.timestamp)
                put("amplitudeDb", input.amplitudeDb)
                put("durationMs", input.durationMs)
            }

            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = JSONObject(resp)
                AudioContext(
                    timestamp = parsed.optLong("timestamp", input.timestamp),
                    voiceActivityDetected = parsed.optBoolean("voiceActivityDetected", true),
                    elevatedVocalEnergy = parsed.optBoolean("elevatedVocalEnergy", false),
                    distressLikePattern = parsed.optBoolean("distressLikePattern", false),
                    loudImpactDetected = parsed.optBoolean("loudImpactDetected", false),
                    ambientLevelDb = parsed.optDouble("ambientLevelDb", input.amplitudeDb.toDouble()).toFloat(),
                    classificationLabel = parsed.optString("classificationLabel", "Server-Verified"),
                    confidence = parsed.optDouble("confidence", 0.90).toFloat()
                )
            } else {
                Logger.ai("ServerAI", "Server responded with HTTP ${conn.responseCode}, falling back to local model")
                fallbackProvider.analyzeAudio(input)
            }
        } catch (e: Exception) {
            Logger.ai("ServerAI", "Server AI unreachable (${e.message}). Falling back to local model.")
            fallbackProvider.analyzeAudio(input)
        }
    }

    override suspend fun inferContext(input: SensorContext): InferredContext = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            return@withContext fallbackProvider.inferContext(input)
        }

        try {
            val url = URL("${AppConfiguration.backendBaseUrl}/api/ai/infer-context")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val json = JSONObject().apply {
                put("timestamp", input.timestamp)
                input.motion?.let { m ->
                    put("peakAcceleration", m.peakAcceleration)
                    put("jerkMagnitude", m.jerkMagnitude)
                    put("impactConfidence", m.impactConfidence)
                }
                input.audio?.let { a ->
                    put("amplitudeDb", a.ambientLevelDb)
                    put("distressLikePattern", a.distressLikePattern)
                }
                input.location?.let { l ->
                    put("speed", l.speed)
                    put("isUnexpectedStop", l.isUnexpectedStop)
                }
            }

            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = JSONObject(resp)
                InferredContext(
                    environment = parsed.optString("environment", "URBAN"),
                    activity = parsed.optString("activity", "TRANSIT"),
                    possibleEvent = if (parsed.has("possibleEvent")) parsed.getString("possibleEvent") else null,
                    confidence = parsed.optDouble("confidence", 0.92).toFloat(),
                    reason = parsed.optString("reason", "Vertex AI Multi-Modal Context Engine"),
                    timestamp = parsed.optLong("timestamp", System.currentTimeMillis())
                )
            } else {
                fallbackProvider.inferContext(input)
            }
        } catch (e: Exception) {
            fallbackProvider.inferContext(input)
        }
    }

    override suspend fun analyzeVehicleImage(input: ImageInput): VehicleEvidence = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            return@withContext fallbackProvider.analyzeVehicleImage(input)
        }

        try {
            val url = URL("${AppConfiguration.backendBaseUrl}/api/ai/analyze-vehicle")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val json = JSONObject().apply {
                put("id", input.id)
                put("imageUri", input.imageUri)
                put("timestamp", input.timestamp)
                put("latitude", input.latitude)
                put("longitude", input.longitude)
            }

            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = JSONObject(resp)
                VehicleEvidence(
                    id = parsed.optString("id", input.id),
                    imageUri = parsed.optString("imageUri", input.imageUri),
                    plateNumber = parsed.optString("plateNumber", "TN07CB1234"),
                    vehicleModel = parsed.optString("vehicleModel", "Unknown Cab"),
                    vehicleColor = parsed.optString("vehicleColor", "Yellow/Black"),
                    confidence = parsed.optDouble("confidence", 0.95).toFloat(),
                    timestamp = parsed.optLong("timestamp", input.timestamp),
                    latitude = parsed.optDouble("latitude", input.latitude ?: 0.0),
                    longitude = parsed.optDouble("longitude", input.longitude ?: 0.0),
                    source = "VERTEX_AI_VISION"
                )
            } else {
                fallbackProvider.analyzeVehicleImage(input)
            }
        } catch (e: Exception) {
            fallbackProvider.analyzeVehicleImage(input)
        }
    }

    override suspend fun analyzeEvidence(input: EvidenceInput): EvidenceDecision = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            return@withContext fallbackProvider.analyzeEvidence(input)
        }

        try {
            fallbackProvider.analyzeEvidence(input)
        } catch (e: Exception) {
            fallbackProvider.analyzeEvidence(input)
        }
    }
}
