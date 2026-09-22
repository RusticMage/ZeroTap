package com.zerotap.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zerotap.domain.model.TrustedContact

@Entity(tableName = "trusted_contacts")
data class TrustedContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val email: String?,
    val isPrimary: Boolean,
    val createdAt: Long
)

fun TrustedContactEntity.toDomain(): TrustedContact {
    return TrustedContact(
        id = id,
        name = name,
        phone = phone,
        email = email,
        isPrimary = isPrimary,
        createdAt = createdAt
    )
}

fun TrustedContact.toEntity(): TrustedContactEntity {
    return TrustedContactEntity(
        id = id,
        name = name,
        phone = phone,
        email = email,
        isPrimary = isPrimary,
        createdAt = createdAt
    )
}
