package com.zerotap.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zerotap.domain.model.TrustedPlace
import com.zerotap.domain.model.TrustedPlaceType

@Entity(tableName = "trusted_places")
data class TrustedPlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val createdAt: Long
)

fun TrustedPlaceEntity.toDomain(): TrustedPlace {
    val placeType = try {
        TrustedPlaceType.valueOf(type)
    } catch (_: Exception) {
        TrustedPlaceType.CUSTOM
    }
    return TrustedPlace(
        id = id,
        name = name,
        type = placeType,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        createdAt = createdAt
    )
}

fun TrustedPlace.toEntity(): TrustedPlaceEntity {
    return TrustedPlaceEntity(
        id = id,
        name = name,
        type = type.name,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        createdAt = createdAt
    )
}
