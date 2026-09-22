package com.zerotap.alert

enum class EmergencyEventType(val displayName: String) {
    PERSONAL_SAFETY("Personal Safety Incident"),
    VEHICLE_ACCIDENT_SUSPECTED("Possible Vehicle Accident")
}

/**
 * Deterministic structured emergency payload consumed by all emergency transports.
 * Decoupled from stochastic model outputs or free-form text.
 */
data class StructuredEmergencyPayload(
    val incidentId: String,
    val eventType: EmergencyEventType,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val sensorTags: List<String> = emptyList(),
    val durationSeconds: Long = 0L,
    val isTest: Boolean = false,
    val customMessage: String? = null
)

/**
 * Strongly typed outcome of an emergency transport execution.
 */
sealed class EmergencyTransportResult {
    data class Success(
        val transportName: String,
        val timestamp: Long,
        val details: String? = null
    ) : EmergencyTransportResult()

    data class PermissionDenied(
        val transportName: String,
        val missingPermission: String
    ) : EmergencyTransportResult()

    data class NetworkUnavailable(
        val transportName: String,
        val reason: String
    ) : EmergencyTransportResult()

    data class InvalidNumber(
        val transportName: String,
        val phone: String
    ) : EmergencyTransportResult()

    data class Failed(
        val transportName: String,
        val reason: String
    ) : EmergencyTransportResult()
}

/**
 * Interface for all automated emergency response transports (SMS, ACTION_CALL, etc.)
 */
interface EmergencyTransport {
    val transportName: String
    suspend fun execute(payload: StructuredEmergencyPayload): EmergencyTransportResult
}
