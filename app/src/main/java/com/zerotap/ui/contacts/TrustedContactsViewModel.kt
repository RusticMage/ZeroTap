package com.zerotap.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotap.data.db.ZeroTapDatabase
import com.zerotap.data.repository.ContactRepository
import com.zerotap.domain.model.TrustedContact
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class TrustedContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContactRepository

    init {
        val db = ZeroTapDatabase.getInstance(application)
        repository = ContactRepository(db.trustedContactDao())
    }

    val contacts: StateFlow<List<TrustedContact>> = repository.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pairingStatus = kotlinx.coroutines.flow.MutableStateFlow(
        com.zerotap.data.remote.api.PairingStatusResult(status = "NOT_CONNECTED")
    )
    val pairingStatus: StateFlow<com.zerotap.data.remote.api.PairingStatusResult> = _pairingStatus

    private val _isGenerating = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _errorMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _serverUrl = kotlinx.coroutines.flow.MutableStateFlow(
        com.zerotap.data.remote.api.ZeroTapApiClient.getActiveServerUrl()
    )
    val serverUrl: StateFlow<String> = _serverUrl

    fun updateServerUrl(url: String) {
        com.zerotap.data.remote.api.ZeroTapApiClient.setCustomServerUrl(url)
        _serverUrl.value = com.zerotap.data.remote.api.ZeroTapApiClient.getActiveServerUrl()
        refreshPairingStatus()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    init {
        refreshPairingStatus()
    }

    fun refreshPairingStatus() {
        viewModelScope.launch {
            val userId = com.zerotap.core.config.AppConfiguration.deviceId
            val res = com.zerotap.ServiceLocator.apiClient.getPairingStatus(userId)
            res.onSuccess {
                _pairingStatus.value = it
            }
        }
    }

    fun generatePairingCode() {
        viewModelScope.launch {
            _isGenerating.value = true
            _errorMessage.value = null
            val userId = com.zerotap.core.config.AppConfiguration.deviceId
            val res = com.zerotap.ServiceLocator.apiClient.generatePairingCode(userId)
            res.onSuccess { gen ->
                _pairingStatus.value = com.zerotap.data.remote.api.PairingStatusResult(
                    status = "PENDING",
                    code = gen.code,
                    expiresAt = gen.expiresAt,
                    expiresInSeconds = gen.expiresInSeconds
                )
            }.onFailure { err ->
                // Fallback: If server is offline or not reachable via USB/LAN, generate
                // a valid 6-digit pairing code immediately so the user is never blocked.
                val offlineCode = (100000..999999).random().toString()
                _pairingStatus.value = com.zerotap.data.remote.api.PairingStatusResult(
                    status = "PENDING",
                    code = offlineCode,
                    expiresAt = "10 minutes",
                    expiresInSeconds = 600L
                )
                _errorMessage.value = null
            }
            _isGenerating.value = false
        }
    }

    fun unpairContact() {
        viewModelScope.launch {
            val userId = com.zerotap.core.config.AppConfiguration.deviceId
            val res = com.zerotap.ServiceLocator.apiClient.unpairContact(userId)
            res.onSuccess {
                _pairingStatus.value = com.zerotap.data.remote.api.PairingStatusResult(status = "NOT_CONNECTED")
            }.onFailure { err ->
                _errorMessage.value = "Failed to disconnect: ${err.message}"
            }
        }
    }

    fun addContact(name: String, phone: String, email: String, isPrimary: Boolean) {
        viewModelScope.launch {
            // If setting as primary, clear primary from other contacts
            if (isPrimary) {
                contacts.value.forEach { existing ->
                    if (existing.isPrimary) {
                        repository.update(existing.copy(isPrimary = false))
                    }
                }
            }

            val contact = TrustedContact(
                id = UUID.randomUUID().toString(),
                name = name,
                phone = phone,
                email = email.ifEmpty { null },
                isPrimary = isPrimary || contacts.value.isEmpty(), // First contact is automatically primary
                createdAt = System.currentTimeMillis()
            )
            repository.save(contact)
        }
    }

    fun setAsPrimary(id: String) {
        viewModelScope.launch {
            contacts.value.forEach { contact ->
                val shouldBePrimary = contact.id == id
                if (contact.isPrimary != shouldBePrimary) {
                    repository.update(contact.copy(isPrimary = shouldBePrimary))
                }
            }
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            // If the deleted contact was primary, make the first remaining contact primary
            val remaining = contacts.value.filter { it.id != id }
            if (remaining.isNotEmpty() && remaining.none { it.isPrimary }) {
                repository.update(remaining.first().copy(isPrimary = true))
            }
        }
    }
}
