package com.zerotap.domain.response

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.zerotap.alert.*
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
    private val callTransport: CallAlertTransport = CallAlertTransport(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    @Volatile
    private var notificationDispatchedForEventId: String? = null

    private val _smsDeliveryState = MutableStateFlow<SmsDeliveryState>(SmsDeliveryState.Idle)
    val smsDeliveryState: StateFlow<SmsDeliveryState> = _smsDeliveryState.asStateFlow()

    private val _emergencyCallStatus = MutableStateFlow<String?>(null)
    val emergencyCallStatus: StateFlow<String?> = _emergencyCallStatus.asStateFlow()

    fun resetState() {
        notificationDispatchedForEventId = null
        _smsDeliveryState.value = SmsDeliveryState.Idle
        _emergencyCallStatus.value = null
    }

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
            Logger.alert("ResponseManager", "[ZeroTap][Emergency] Personal safety incident escalated (Event: $eventId). Zero-tap response executing.")

            scope.launch {
                val contacts = contactDao.getAllContacts().first()
                val primaryContact = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()

                val payload = StructuredEmergencyPayload(
                    incidentId = eventId,
                    eventType = EmergencyEventType.PERSONAL_SAFETY,
                    confidence = prediction.scorePercent / 100f,
                    timestamp = prediction.timestamp,
                    latitude = locationContext?.latitude,
                    longitude = locationContext?.longitude,
                    locationAccuracy = locationContext?.accuracy,
                    contactName = primaryContact?.name,
                    contactPhone = primaryContact?.phone,
                    sensorTags = prediction.contributingFactors,
                    durationSeconds = prediction.durationInCurrentStateSeconds,
                    isTest = false
                )

                dispatchAutomatedEmergencyResponse(payload)
            }
        }
    }

    /**
     * Dispatches zero-tap emergency alerts for vehicle accident timeout.
     */
    fun onAccidentEscalation(
        incidentId: String,
        confidence: Float,
        locationContext: LocationContext?,
        evidenceTags: List<String>
    ) {
        if (notificationDispatchedForEventId == incidentId) return
        notificationDispatchedForEventId = incidentId

        Logger.alert("ResponseManager", "[ZeroTap][Emergency] Suspected vehicle accident escalated (Event: $incidentId). Zero-tap response executing.")

        scope.launch {
            val contacts = contactDao.getAllContacts().first()
            val primaryContact = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()

            val payload = StructuredEmergencyPayload(
                incidentId = incidentId,
                eventType = EmergencyEventType.VEHICLE_ACCIDENT_SUSPECTED,
                confidence = confidence,
                timestamp = System.currentTimeMillis(),
                latitude = locationContext?.latitude,
                longitude = locationContext?.longitude,
                locationAccuracy = locationContext?.accuracy,
                contactName = primaryContact?.name,
                contactPhone = primaryContact?.phone,
                sensorTags = evidenceTags,
                isTest = false
            )

            dispatchAutomatedEmergencyResponse(payload)
        }
    }

    private suspend fun dispatchAutomatedEmergencyResponse(payload: StructuredEmergencyPayload) {
        val phone = payload.contactPhone
        if (phone.isNullOrBlank()) {
            Logger.alert("ResponseManager", "[ZeroTap][Emergency] No trusted contact configured for automated response.")
            _smsDeliveryState.value = SmsDeliveryState.NoPrimaryContact
            _emergencyCallStatus.value = "No primary trusted contact phone number configured"
            return
        }

        // STEP 1: AUTOMATIC SMS DISPATCH
        _smsDeliveryState.value = SmsDeliveryState.Sending
        Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 1: Automated SMS dispatch started for ${payload.contactName ?: phone}")
        val smsResult = smsTransport.execute(payload)
        when (smsResult) {
            is EmergencyTransportResult.Success -> {
                Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 1: SMS dispatch successful to $phone")
                _smsDeliveryState.value = SmsDeliveryState.Sent(smsResult.timestamp, payload.contactName ?: "Primary Contact", phone)
            }
            is EmergencyTransportResult.PermissionDenied -> {
                Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 1: SMS failed — Permission denied")
                _smsDeliveryState.value = SmsDeliveryState.PermissionRequired(smsResult.missingPermission)
            }
            is EmergencyTransportResult.Failed -> {
                Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 1: SMS failed — ${smsResult.reason}")
                _smsDeliveryState.value = SmsDeliveryState.Failed(smsResult.reason)
            }
            is EmergencyTransportResult.InvalidNumber -> {
                Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 1: SMS failed — Invalid number")
                _smsDeliveryState.value = SmsDeliveryState.Failed("Invalid phone number")
            }
            is EmergencyTransportResult.NetworkUnavailable -> {
                Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 1: SMS failed — Network unavailable")
                _smsDeliveryState.value = SmsDeliveryState.Failed("Network unavailable")
            }
        }

        // STEP 2: AUTOMATIC PHONE CALL INITIATION (Even if SMS failed)
        Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 2: Automated phone call initiation started")
        val isDemoCallEnabled = userPreferences.demoEmergencyCallEnabled.first()
        callTransport.isSimulationMode = !isDemoCallEnabled

        val callResult = callTransport.execute(payload)
        when (callResult) {
            is EmergencyTransportResult.Success -> {
                Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 2: Phone call initiated (${callResult.details})")
                _emergencyCallStatus.value = callResult.details ?: "Call initiated"
            }
            is EmergencyTransportResult.PermissionDenied -> {
                Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 2: Call failed — CALL_PHONE permission missing")
                _emergencyCallStatus.value = "CALL_PHONE permission required"
            }
            is EmergencyTransportResult.Failed -> {
                Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 2: Call failed — ${callResult.reason}")
                _emergencyCallStatus.value = "Call failed: ${callResult.reason}"
            }
            is EmergencyTransportResult.InvalidNumber -> {
                Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 2: Call failed — Invalid number")
                _emergencyCallStatus.value = "Invalid phone number"
            }
            is EmergencyTransportResult.NetworkUnavailable -> {
                Logger.alert("ResponseManager", "[ZeroTap][Emergency] Step 2: Call failed — Cellular network unavailable")
                _emergencyCallStatus.value = "Cellular network unavailable"
            }
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
