package com.zerotap.ai.models

import com.zerotap.domain.model.AudioContext
import com.zerotap.domain.model.LocationContext
import com.zerotap.domain.model.MotionContext
import java.util.UUID

/**
 * Raw or preprocessed audio input for AI inference.
 */
data class AudioInput(
    val timestamp: Long,
    val amplitudeDb: Float,
    val durationMs: Long = 1000L,
    val spectralFeatures: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioInput
        return timestamp == other.timestamp && amplitudeDb == other.amplitudeDb
    }

    override fun hashCode(): Int = 31 * timestamp.hashCode() + amplitudeDb.hashCode()
}

/**
 * Sensor input for contextual inference.
 */
data class SensorContext(
    val motion: MotionContext?,
    val audio: AudioContext?,
    val location: LocationContext?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Typed, structured output from contextual inference engine.
 * Never natural-language free text parsing in the safety engine.
 */
data class InferredContext(
    val environment: String, // e.g. "INDOOR", "TRANSIT", "VEHICLE", "WALKING_OUTDOORS", "NIGHT_STREET"
    val activity: String,    // e.g. "STATIONARY", "WALKING", "RUNNING", "PASSENGER_IN_CAR"
    val possibleEvent: String?, // e.g. "SUDDEN_STOP", "POSSIBLE_COLLISION", "DISTRESS_VOCAL"
    val confidence: Float,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Image input for vehicle / license plate detection.
 */
data class ImageInput(
    val id: String = UUID.randomUUID().toString(),
    val imageUri: String,
    val imageBytes: ByteArray? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImageInput
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Vehicle / Number Plate evidence extracted from vision model.
 */
data class VehicleEvidence(
    val id: String = UUID.randomUUID().toString(),
    val imageUri: String,
    val plateNumber: String?,
    val vehicleModel: String? = null,
    val vehicleColor: String? = null,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: String = "ON_DEVICE_VISION"
)

/**
 * Evidence item for incident relevance analysis.
 */
data class EvidenceInput(
    val incidentId: String,
    val recentMediaUris: List<String>,
    val incidentTimestamp: Long,
    val incidentLatitude: Double?,
    val incidentLongitude: Double?
)

/**
 * AI-generated decision on incident evidence correlation.
 */
data class EvidenceDecision(
    val incidentId: String,
    val relevantItems: List<VehicleEvidence> = emptyList(),
    val relevanceScore: Float,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)
