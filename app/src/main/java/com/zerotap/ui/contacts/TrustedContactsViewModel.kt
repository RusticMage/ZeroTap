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
