package com.zerotap.domain.model

import java.util.UUID

/**
 * A trusted contact who receives alerts during incidents.
 */
data class TrustedContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val email: String? = null,
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Alert payload sent to a trusted contact via a transport.
 */
data class AlertPayload(
    val incidentId: String,
    val message: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val riskScore: Float,
    val riskState: RiskState,
    val timestamp: Long = System.currentTimeMillis(),
    val contactPhone: String? = null,
    val contactName: String? = null,
    val locationAccuracy: Float? = null,
    val riskDurationSeconds: Long = 0L,
    val contributingFactors: List<String> = emptyList(),
    val isTest: Boolean = false
)

/**
 * Result of an alert delivery attempt.
 */
data class DeliveryResult(
    val success: Boolean,
    val transportName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

/**
 * Record of a single alert delivery attempt.
 */
data class AlertAttempt(
    val id: String = UUID.randomUUID().toString(),
    val incidentId: String,
    val contactId: String,
    val transportName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean,
    val errorMessage: String? = null
)
