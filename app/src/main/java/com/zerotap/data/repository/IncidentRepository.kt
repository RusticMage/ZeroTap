package com.zerotap.data.repository

import com.zerotap.data.db.dao.IncidentDao
import com.zerotap.data.db.entity.toDomain
import com.zerotap.data.db.entity.toEntity
import com.zerotap.domain.model.Incident
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IncidentRepository(private val dao: IncidentDao) {

    fun getAllIncidents(): Flow<List<Incident>> {
        return dao.getAllIncidents().map { list -> list.map { it.toDomain() } }
    }
    
    fun getActiveIncident(): Flow<Incident?> {
        return dao.getActiveIncident().map { it?.toDomain() }
    }
    
    suspend fun getById(id: String): Incident? {
        return dao.getIncidentById(id)?.toDomain()
    }
    
    suspend fun save(incident: Incident) {
        dao.insert(incident.toEntity())
    }
    
    suspend fun update(incident: Incident) {
        dao.update(incident.toEntity())
    }
    
    suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
