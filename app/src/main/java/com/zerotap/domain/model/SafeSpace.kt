package com.zerotap.domain.model

enum class SafeSpaceType(val displayName: String) {
    POLICE("Police Station"),
    HOSPITAL("Hospital / Emergency"),
    FIRE_STATION("Fire Station"),
    METRO_STATION("Metro Transit"),
    BUS_TERMINUS("Bus Terminus"),
    AIRPORT("Airport"),
    PHARMACY("24/7 Pharmacy"),
    PUBLIC_INSTITUTION("Government / Public Facility"),
    COMMUNITY_SAFE_ZONE("Verified Community Safe Space")
}

data class SafeSpace(
    val id: String,
    val name: String,
    val type: SafeSpaceType,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val phone: String,
    val source: String = "VERIFIED_OFFLINE_DB",
    val verified: Boolean = true,
    val priority: Int = 1, // Higher priority shown first in emergency
    val availableOffline: Boolean = true
)
