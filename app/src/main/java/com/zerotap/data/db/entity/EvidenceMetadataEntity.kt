package com.zerotap.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zerotap.domain.model.EvidencePackage

@Entity(tableName = "evidence_metadata")
data class EvidenceMetadataEntity(
    @PrimaryKey val id: String,
    val incidentId: String,
    val createdAt: Long,
    val motionSampleCount: Int,
    val locationSampleCount: Int,
    val audioChunkCount: Int,
    val encryptionKeyAlias: String,
    val storagePath: String?,
    val sizeBytes: Long
)

fun EvidenceMetadataEntity.toDomain(): EvidencePackage {
    return EvidencePackage(
        id = id,
        incidentId = incidentId,
        createdAt = createdAt,
        motionSampleCount = motionSampleCount,
        locationSampleCount = locationSampleCount,
        audioChunkCount = audioChunkCount,
        encryptionKeyAlias = encryptionKeyAlias,
        storagePath = storagePath,
        sizeBytes = sizeBytes
    )
}

fun EvidencePackage.toEntity(): EvidenceMetadataEntity {
    return EvidenceMetadataEntity(
        id = id,
        incidentId = incidentId,
        createdAt = createdAt,
        motionSampleCount = motionSampleCount,
        locationSampleCount = locationSampleCount,
        audioChunkCount = audioChunkCount,
        encryptionKeyAlias = encryptionKeyAlias,
        storagePath = storagePath,
        sizeBytes = sizeBytes
    )
}
