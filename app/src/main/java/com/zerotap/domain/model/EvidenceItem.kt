package com.zerotap.domain.model

enum class EvidenceType {
    VEHICLE_PLATE,
    PHOTO,
    AUDIO_SNIPPET,
    MOTION_KINEMATICS,
    LOCATION_TRAIL,
    SENSOR_LOG
}

enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}

data class EvidenceItem(
    val id: String,
    val type: EvidenceType,
    val uri: String,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val metadata: Map<String, String> = emptyMap(),
    val relevanceScore: Float = 1.0f,
    val incidentId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)
