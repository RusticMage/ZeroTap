package com.zerotap.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotap.data.db.ZeroTapDatabase
import com.zerotap.data.repository.IncidentRepository
import com.zerotap.domain.model.Incident
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IncidentHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: IncidentRepository

    init {
        val db = ZeroTapDatabase.getInstance(application)
        repository = IncidentRepository(db.incidentDao())
    }

    val incidents: StateFlow<List<Incident>> = repository.getAllIncidents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteIncident(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }
}
