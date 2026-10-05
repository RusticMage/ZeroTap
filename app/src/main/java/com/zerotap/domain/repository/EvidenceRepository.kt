package com.zerotap.domain.repository

import com.zerotap.domain.model.EvidenceItem
import com.zerotap.domain.model.EvidencePackage
import com.zerotap.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface EvidenceRepository {

    suspend fun saveEvidencePackage(evidence: EvidencePackage)

    suspend fun getEvidencePackageByIncidentId(incidentId: String): EvidencePackage?

    fun observeEvidenceItems(incidentId: String? = null): Flow<List<EvidenceItem>>

    suspend fun saveEvidenceItem(item: EvidenceItem)

    suspend fun getEvidenceItem(id: String): EvidenceItem?

    suspend fun getPendingSyncItems(): List<EvidenceItem>

    suspend fun updateSyncStatus(id: String, status: SyncStatus)
}
