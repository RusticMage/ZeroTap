package com.zerotap.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zerotap.domain.model.RiskAssessment
import com.zerotap.domain.model.RiskState

@Entity(tableName = "risk_events")
data class RiskEventEntity(
    @PrimaryKey val id: String, // UUID
    val timestamp: Long,
    val score: Float,
    val state: String,
    val signalCount: Int,
    val engineLabel: String
)

fun RiskEventEntity.toDomain(): RiskAssessment {
    return RiskAssessment(
        score = score,
        state = RiskState.valueOf(state),
        contributingSignals = emptyList(),
        timestamp = timestamp,
        engineLabel = engineLabel
    )
}

fun RiskAssessment.toEntity(): RiskEventEntity {
    return RiskEventEntity(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = timestamp,
        score = score,
        state = state.name,
        signalCount = contributingSignals.size,
        engineLabel = engineLabel
    )
}
