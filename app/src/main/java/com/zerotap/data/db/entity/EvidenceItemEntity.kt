package com.zerotap.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zerotap.domain.model.EvidenceItem
import com.zerotap.domain.model.EvidenceType
import com.zerotap.domain.model.SyncStatus
import org.json.JSONObject

@Entity(tableName = "evidence_items")
data class EvidenceItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val uri: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val metadataJson: String,
    val relevanceScore: Float,
    val incidentId: String?,
    val syncStatus: String
)

fun EvidenceItemEntity.toDomain(): EvidenceItem {
    val meta = mutableMapOf<String, String>()
    try {
        val json = JSONObject(metadataJson)
        json.keys().forEach { k -> meta[k] = json.optString(k) }
    } catch (_: Exception) {}

    val evType = try {
        EvidenceType.valueOf(type)
    } catch (_: Exception) {
        EvidenceType.PHOTO
    }

    val sStatus = try {
        SyncStatus.valueOf(syncStatus)
    } catch (_: Exception) {
        SyncStatus.LOCAL_ONLY
    }

    return EvidenceItem(
        id = id,
        type = evType,
        uri = uri,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        metadata = meta,
        relevanceScore = relevanceScore,
        incidentId = incidentId,
        syncStatus = sStatus
    )
}

fun EvidenceItem.toEntity(): EvidenceItemEntity {
    val json = JSONObject()
    metadata.forEach { (k, v) -> json.put(k, v) }

    return EvidenceItemEntity(
        id = id,
        type = type.name,
        uri = uri,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        metadataJson = json.toString(),
        relevanceScore = relevanceScore,
        incidentId = incidentId,
        syncStatus = syncStatus.name
    )
}
