package com.zerotap.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zerotap.domain.model.SafeSpace
import com.zerotap.domain.model.SafeSpaceType

@Entity(tableName = "safe_spaces")
data class SafeSpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val phone: String,
    val source: String,
    val verified: Boolean,
    val priority: Int,
    val availableOffline: Boolean
)

fun SafeSpaceEntity.toDomain(): SafeSpace {
    val safeType = try {
        SafeSpaceType.valueOf(type)
    } catch (_: Exception) {
        SafeSpaceType.POLICE
    }
    return SafeSpace(
        id = id,
        name = name,
        type = safeType,
        latitude = latitude,
        longitude = longitude,
        address = address,
        phone = phone,
        source = source,
        verified = verified,
        priority = priority,
        availableOffline = availableOffline
    )
}

fun SafeSpace.toEntity(): SafeSpaceEntity {
    return SafeSpaceEntity(
        id = id,
        name = name,
        type = type.name,
        latitude = latitude,
        longitude = longitude,
        address = address,
        phone = phone,
        source = source,
        verified = verified,
        priority = priority,
        availableOffline = availableOffline
    )
}
