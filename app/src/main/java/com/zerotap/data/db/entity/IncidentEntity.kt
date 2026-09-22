package com.zerotap.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zerotap.domain.model.Incident
import com.zerotap.domain.model.IncidentStatus
import com.zerotap.domain.model.RiskState
import org.json.JSONArray

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val riskScore: Float,
    val riskState: String, // enum name
    val latitude: Double?,
    val longitude: Double?,
    val contributingSignalsJson: String, // JSON serialized list of signal descriptions
    val evidenceId: String?,
    val status: String, // enum name
    val summary: String?
)

fun IncidentEntity.toDomain(): Incident {
    return Incident(
        id = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        riskScore = riskScore,
        riskState = RiskState.valueOf(riskState),
        latitude = latitude,
        longitude = longitude,
        contributingSignals = emptyList(),
        evidenceId = evidenceId,
        status = IncidentStatus.valueOf(status),
        summary = summary
    )
}

fun Incident.toEntity(): IncidentEntity {
    val signalDescriptions = contributingSignals.map { it.description }
    return IncidentEntity(
        id = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        riskScore = riskScore,
        riskState = riskState.name,
        latitude = latitude,
        longitude = longitude,
        contributingSignalsJson = JSONArray(signalDescriptions).toString(),
        evidenceId = evidenceId,
        status = status.name,
        summary = summary
    )
}
