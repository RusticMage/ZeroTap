package com.zerotap.domain.response

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.zerotap.alert.SmsAlertTransport
import com.zerotap.alert.SmsDeliveryResult
import com.zerotap.data.datastore.UserPreferences
import com.zerotap.data.db.dao.TrustedContactDao
import com.zerotap.domain.model.*
import com.zerotap.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

sealed class SmsDeliveryState {
    object Idle : SmsDeliveryState()
    object Sending : SmsDeliveryState()
    data class Sent(val timestamp: Long, val contactName: String, val phone: String) : SmsDeliveryState()
    data class Failed(val reason: String) : SmsDeliveryState()
    data class PermissionRequired(val permission: String) : SmsDeliveryState()
    object NoPrimaryContact : SmsDeliveryState()
}

/**
 * Decoupled response orchestrator.
 * Enforces single-dispatch deduplication and strict 112 call safety guards.
 */
class ResponseManager(
    private val context: Context,
    private val contactDao: TrustedContactDao,
    private val userPreferences: UserPreferences,
    private val smsTransport: SmsAlertTransport = SmsAlertTransport(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    @Volatile
    private var notificationDispatchedForEventId: String? = null

    private val _smsDeliveryState = MutableStateFlow<SmsDeliveryState>(SmsDeliveryState.Idle)
    val smsDeliveryState: StateFlow<SmsDeliveryState> = _smsDeliveryState.asStateFlow()

    private val _emergencyCallStatus = MutableStateFlow<String?>(null)
    val emergencyCallStatus: StateFlow<String?> = _emergencyCallStatus.asStateFlow()

    fun onEngineTick(
        temporalState: TemporalRiskState,
        prediction: RiskPredictionResult,
        locationContext: LocationContext?,
        eventId: String?
    ) {
        if (temporalState == TemporalRiskState.NORMAL) {
            notificationDispatchedForEventId = null
            _smsDeliveryState.value = SmsDeliveryState.Idle
            _emergencyCallStatus.value = null
            return
        }

        if (temporalState == TemporalRiskState.EMERGENCY_TRIGGERED && eventId != null) {
            if (notificationDispatchedForEventId == eventId) {
                // Deduplication: Already dispatched for this specific emergency event
                return
            }

            notificationDispatchedForEventId = eventId
            Logger.alert("ResponseManager", "Emergency triggered (Event ID: $eventId). Initiating single dispatch.")

            scope.launch {
                dispatchEmergencyResponse(prediction, locationContext, eventId)
            }
        }
    }

    private suspend fun dispatchEmergencyResponse(
        prediction: RiskPredictionResult,
        locationContext: LocationContext?,
        eventId: String
    ) {
        // 1. Fetch primary trusted contact
        val contacts = contactDao.getAllContacts().first()
        val primaryContact = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()

        if (primaryContact == null) {
            Logger.alert("ResponseManager", "No trusted contact configured.")
            _smsDeliveryState.value = SmsDeliveryState.NoPrimaryContact
        } else {
            _smsDeliveryState.value = SmsDeliveryState.Sending
            val payload = AlertPayload(
                incidentId = eventId,
                message = "ZeroTap Emergency Triggered",
                latitude = locationContext?.latitude,
                longitude = locationContext?.longitude,
                riskScore = prediction.scorePercent / 100f,
                riskState = RiskState.INCIDENT,
                timestamp = prediction.timestamp,
                contactPhone = primaryContact.phone,
                contactName = primaryContact.name,
                locationAccuracy = locationContext?.accuracy,
                riskDurationSeconds = prediction.durationInCurrentStateSeconds,
                contributingFactors = prediction.contributingFactors,
                isTest = false
            )

            when (val result = smsTransport.sendSms(payload)) {
                is SmsDeliveryResult.Success -> {
                    _smsDeliveryState.value = SmsDeliveryState.Sent(result.timestamp, primaryContact.name, primaryContact.phone)
                }
                is SmsDeliveryResult.Failed -> {
                    _smsDeliveryState.value = SmsDeliveryState.Failed(result.reason)
                }
                is SmsDeliveryResult.PermissionRequired -> {
                    _smsDeliveryState.value = SmsDeliveryState.PermissionRequired(result.missingPermission)
                }
            }
        }

        // 2. 112 Emergency Call flow (Controlled strictly by Demo Emergency Call toggle)
        val isDemoCallEnabled = userPreferences.demoEmergencyCallEnabled.first()
        if (isDemoCallEnabled) {
            val callPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            if (callPermission == PackageManager.PERMISSION_GRANTED) {
                _emergencyCallStatus.value = "DEMO CALL ENABLED: Calling emergency 112..."
                Logger.alert("ResponseManager", "Demo Emergency Call = ON with permission. Placing call to 112.")
                executeEmergencyCall("112")
            } else {
                _emergencyCallStatus.value = "CALL_PHONE permission missing — cannot place 112 call"
                Logger.alert("ResponseManager", "Demo Emergency Call = ON but CALL_PHONE permission not granted.")
            }
        } else {
            _emergencyCallStatus.value = "Emergency call simulation: 112 call would be placed now (Demo Emergency Call is OFF)"
            Logger.alert("ResponseManager", "Demo Emergency Call = OFF. Simulated 112 call only.")
        }
    }

    suspend fun sendTestSms(): DeliveryResult {
        val contacts = contactDao.getAllContacts().first()
        val primaryContact = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()
        if (primaryContact == null) {
            return DeliveryResult(
                success = false,
                transportName = "SMS",
                timestamp = System.currentTimeMillis(),
                errorMessage = "No trusted contact configured"
            )
        }

        val testAlert = AlertPayload(
            incidentId = "test_${UUID.randomUUID()}",
            message = "ZeroTap DEMO TEST — no emergency has been detected. Contact verification successful.",
            riskScore = 0f,
            riskState = RiskState.NORMAL,
            contactPhone = primaryContact.phone,
            contactName = primaryContact.name,
            isTest = true
        )

        return when (val result = smsTransport.sendSms(testAlert)) {
            is SmsDeliveryResult.Success -> DeliveryResult(true, "SMS", result.timestamp)
            is SmsDeliveryResult.Failed -> DeliveryResult(false, "SMS", System.currentTimeMillis(), result.reason)
            is SmsDeliveryResult.PermissionRequired -> DeliveryResult(false, "SMS", System.currentTimeMillis(), "SEND_SMS permission required")
        }
    }

    private fun executeEmergencyCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.alert("ResponseManager", "Failed to place ACTION_CALL: ${e.message}")
        }
    }
}
