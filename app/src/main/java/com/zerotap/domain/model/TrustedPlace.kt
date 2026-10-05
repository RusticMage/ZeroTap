package com.zerotap.domain.model

enum class TrustedPlaceType(val displayName: String) {
    HOME("Home"),
    COLLEGE("College / Work"),
    FAMILY("Family"),
    FRIEND("Friend"),
    HOSPITAL("Hospital"),
    CUSTOM("Custom")
}

data class TrustedPlace(
    val id: String,
    val name: String,
    val type: TrustedPlaceType,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 100f,
    val createdAt: Long = System.currentTimeMillis()
)
