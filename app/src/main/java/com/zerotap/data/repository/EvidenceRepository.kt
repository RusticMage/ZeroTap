package com.zerotap.data.repository

import com.zerotap.data.db.dao.EvidenceMetadataDao
import com.zerotap.data.db.entity.toDomain
import com.zerotap.data.db.entity.toEntity
import com.zerotap.domain.model.EvidencePackage

class EvidenceRepository(private val dao: EvidenceMetadataDao) {

    suspend fun getByIncidentId(incidentId: String): EvidencePackage? {
        return dao.getByIncidentId(incidentId)?.toDomain()
    }
    
    suspend fun save(evidence: EvidencePackage) {
        dao.insert(evidence.toEntity())
    }
}
