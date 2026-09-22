package com.zerotap.alert

import com.zerotap.domain.model.AlertAttempt
import com.zerotap.domain.model.AlertPayload
import com.zerotap.domain.model.DeliveryResult
import com.zerotap.domain.model.Incident
import com.zerotap.domain.model.TrustedContact
import com.zerotap.util.Logger

class AlertManager(
    private val transports: List<AlertTransport>
) {
    suspend fun sendAlert(payload: AlertPayload): List<DeliveryResult> {
        val results = mutableListOf<DeliveryResult>()
        for (transport in transports) {
            if (transport.isAvailable()) {
                Logger.alert("AlertManager", "Attempting delivery via ${transport.transportName}")
                val result = transport.send(payload)
                results.add(result)
                if (result.success) {
                    Logger.alert("AlertManager", "Delivery successful via ${transport.transportName}")
                    return results
                }
            }
        }
        Logger.alert("AlertManager", "All transports failed for payload: ${payload.incidentId}")
        return results
    }

    suspend fun sendToAllContacts(incident: Incident, contacts: List<TrustedContact>): List<AlertAttempt> {
        val attempts = mutableListOf<AlertAttempt>()
        for (contact in contacts) {
            val payload = AlertPayload(
                incidentId = incident.id,
                message = "ZeroTap Alert for incident ${incident.id}",
                latitude = incident.latitude,
                longitude = incident.longitude,
                riskScore = incident.riskScore,
                riskState = incident.riskState,
                timestamp = System.currentTimeMillis(),
                contactPhone = contact.phone,
                contactName = contact.name
            )
            val results = sendAlert(payload)
            val primaryResult = results.firstOrNull { it.success } ?: results.firstOrNull()
            attempts.add(
                AlertAttempt(
                    incidentId = incident.id,
                    contactId = contact.id,
                    transportName = primaryResult?.transportName ?: "None",
                    timestamp = System.currentTimeMillis(),
                    success = primaryResult?.success ?: false,
                    errorMessage = primaryResult?.errorMessage
                )
            )
        }
        return attempts
    }
}
