package com.zerotap.data.repository

import com.zerotap.data.db.dao.EvidenceItemDao
import com.zerotap.data.db.dao.EvidenceMetadataDao
import com.zerotap.data.db.entity.toDomain
import com.zerotap.data.db.entity.toEntity
import com.zerotap.domain.model.EvidenceItem
import com.zerotap.domain.model.EvidencePackage
import com.zerotap.domain.model.SyncStatus
import com.zerotap.domain.repository.EvidenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EvidenceRepositoryImpl(
    private val metadataDao: EvidenceMetadataDao,
    private val itemDao: EvidenceItemDao
) : EvidenceRepository {

    override suspend fun saveEvidencePackage(evidence: EvidencePackage) {
        metadataDao.insert(evidence.toEntity())
    }

    override suspend fun getEvidencePackageByIncidentId(incidentId: String): EvidencePackage? {
        return metadataDao.getByIncidentId(incidentId)?.toDomain()
    }

    override fun observeEvidenceItems(incidentId: String?): Flow<List<EvidenceItem>> {
        return if (incidentId != null) {
            itemDao.getByIncident(incidentId).map { list -> list.map { it.toDomain() } }
        } else {
            itemDao.getAll().map { list -> list.map { it.toDomain() } }
        }
    }

    override suspend fun saveEvidenceItem(item: EvidenceItem) {
        itemDao.insert(item.toEntity())
    }

    override suspend fun getEvidenceItem(id: String): EvidenceItem? {
        return itemDao.getById(id)?.toDomain()
    }

    override suspend fun getPendingSyncItems(): List<EvidenceItem> {
        return itemDao.getPendingSync().map { it.toDomain() }
    }

    override suspend fun updateSyncStatus(id: String, status: SyncStatus) {
        itemDao.updateSyncStatus(id, status.name)
    }
}
