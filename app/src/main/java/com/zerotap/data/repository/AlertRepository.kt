package com.zerotap.data.repository

import com.zerotap.data.db.dao.AlertAttemptDao
import com.zerotap.data.db.entity.toDomain
import com.zerotap.data.db.entity.toEntity
import com.zerotap.domain.model.AlertAttempt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlertRepository(private val dao: AlertAttemptDao) {

    fun getByIncidentId(incidentId: String): Flow<List<AlertAttempt>> {
        return dao.getByIncidentId(incidentId).map { list -> list.map { it.toDomain() } }
    }
    
    suspend fun save(attempt: AlertAttempt) {
        dao.insert(attempt.toEntity())
    }
}
