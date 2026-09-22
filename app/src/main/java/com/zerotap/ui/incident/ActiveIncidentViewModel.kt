package com.zerotap.ui.incident

import androidx.lifecycle.ViewModel
import com.zerotap.domain.model.Incident
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ActiveIncidentViewModel : ViewModel() {
    private val _currentIncident = MutableStateFlow<Incident?>(null)
    val currentIncident = _currentIncident.asStateFlow()

    fun dismissIncident() {
        _currentIncident.value = null
    }

    fun resolveIncident() {
        _currentIncident.value = null
    }
}
