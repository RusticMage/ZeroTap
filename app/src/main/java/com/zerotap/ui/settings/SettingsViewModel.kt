package com.zerotap.ui.settings

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotap.alert.SmsAlertTransport
import com.zerotap.alert.SmsDeliveryResult
import com.zerotap.data.datastore.UserPreferences
import com.zerotap.data.db.ZeroTapDatabase
import com.zerotap.data.repository.ContactRepository
import com.zerotap.domain.model.AlertPayload
import com.zerotap.domain.model.RiskState
import com.zerotap.domain.model.TrustedContact
import com.zerotap.service.ProtectionForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application)
    private val contactRepo = ContactRepository(ZeroTapDatabase.getInstance(application).trustedContactDao())

    val themeMode: StateFlow<String> = userPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val demoEmergencyCall: StateFlow<Boolean> = userPreferences.demoEmergencyCallEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val trustedContacts: StateFlow<List<TrustedContact>> = contactRepo.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isProtectionActive: StateFlow<Boolean> = ProtectionForegroundService.isRunning

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
        }
    }

    fun toggleDemoEmergencyCall(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setDemoEmergencyCallEnabled(enabled)
        }
    }

    /**
     * Requirement P2 & P18: Test SMS runs independently of the ProtectionForegroundService.
     * Only requires valid contact, SMS permission, and mobile radio.
     */
    suspend fun sendTestSms(): SmsDeliveryResult {
        val contacts = contactRepo.getAllContacts().first()
        val primary = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()
        if (primary == null) {
            return SmsDeliveryResult.Failed("No trusted contact configured. Please add one first.")
        }

        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return SmsDeliveryResult.PermissionRequired(Manifest.permission.SEND_SMS)
        }

        val transport = SmsAlertTransport(app)
        val testPayload = AlertPayload(
            incidentId = UUID.randomUUID().toString(),
            message = "ZeroTap DEMO TEST — no emergency has been detected.",
            latitude = null,
            longitude = null,
            riskScore = 0f,
            riskState = RiskState.NORMAL,
            timestamp = System.currentTimeMillis(),
            contactPhone = primary.phone,
            contactName = primary.name,
            isTest = true
        )

        return transport.sendSms(testPayload)
    }
}
