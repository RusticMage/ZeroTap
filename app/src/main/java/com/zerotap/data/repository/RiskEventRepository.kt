package com.zerotap.data.repository

import com.zerotap.data.db.dao.RiskEventDao
import com.zerotap.data.db.entity.toDomain
import com.zerotap.data.db.entity.toEntity
import com.zerotap.domain.model.RiskAssessment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RiskEventRepository(private val dao: RiskEventDao) {

    fun getRecentEvents(limit: Int = 50): Flow<List<RiskAssessment>> {
        return dao.getRecentEvents(limit).map { list -> list.map { it.toDomain() } }
    }
    
    suspend fun save(assessment: RiskAssessment) {
        dao.insert(assessment.toEntity())
    }
    
    suspend fun cleanupOlderThan(timestamp: Long) {
        dao.deleteOlderThan(timestamp)
    }
}
