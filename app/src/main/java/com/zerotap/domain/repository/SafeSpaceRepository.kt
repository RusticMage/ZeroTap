package com.zerotap.domain.repository

import com.zerotap.domain.model.SafeSpace
import com.zerotap.domain.model.SafeSpaceType
import kotlinx.coroutines.flow.Flow

interface SafeSpaceRepository {

    fun observeSafeSpaces(): Flow<List<SafeSpace>>

    suspend fun getAllSafeSpaces(): List<SafeSpace>

    suspend fun getNearbySafeSpaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = 5000f,
        type: SafeSpaceType? = null
    ): List<Pair<SafeSpace, Float>> // SafeSpace with distance in meters

    suspend fun getNearestSafeSpace(
        latitude: Double,
        longitude: Double,
        type: SafeSpaceType? = null
    ): Pair<SafeSpace, Float>?

    suspend fun insertSafeSpaces(safeSpaces: List<SafeSpace>)

    suspend fun clearSafeSpaces()
}
