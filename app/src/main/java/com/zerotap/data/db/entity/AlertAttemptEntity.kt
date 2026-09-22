package com.zerotap.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zerotap.domain.model.AlertAttempt

@Entity(tableName = "alert_attempts")
data class AlertAttemptEntity(
    @PrimaryKey val id: String,
    val incidentId: String,
    val contactId: String,
    val transportName: String,
    val timestamp: Long,
    val success: Boolean,
    val errorMessage: String?
)

fun AlertAttemptEntity.toDomain(): AlertAttempt {
    return AlertAttempt(
        id = id,
        incidentId = incidentId,
        contactId = contactId,
        transportName = transportName,
        timestamp = timestamp,
        success = success,
        errorMessage = errorMessage
    )
}

fun AlertAttempt.toEntity(): AlertAttemptEntity {
    return AlertAttemptEntity(
        id = id,
        incidentId = incidentId,
        contactId = contactId,
        transportName = transportName,
        timestamp = timestamp,
        success = success,
        errorMessage = errorMessage
    )
}
