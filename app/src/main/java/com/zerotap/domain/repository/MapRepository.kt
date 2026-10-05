package com.zerotap.domain.repository

import kotlinx.coroutines.flow.Flow
import java.io.File

interface MapRepository {

    val isOfflineMapAvailable: Boolean

    fun getOfflineCacheDirectory(): File

    suspend fun preloadOfflineArea(
        northLat: Double,
        southLat: Double,
        eastLng: Double,
        westLng: Double,
        minZoom: Int = 10,
        maxZoom: Int = 16
    ): Flow<Float> // Progress 0..100%

    fun clearCache()
}
