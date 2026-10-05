package com.zerotap.data.repository

import android.content.Context
import com.zerotap.domain.repository.MapRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.osmdroid.config.Configuration
import java.io.File

class MapRepositoryImpl(
    private val context: Context
) : MapRepository {

    private val cacheDir = File(context.cacheDir, "osmdroid")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        Configuration.getInstance().osmdroidBasePath = cacheDir
        Configuration.getInstance().osmdroidTileCache = File(cacheDir, "tiles")
        Configuration.getInstance().userAgentValue = context.packageName
    }

    override val isOfflineMapAvailable: Boolean
        get() = true

    override fun getOfflineCacheDirectory(): File = cacheDir

    override suspend fun preloadOfflineArea(
        northLat: Double,
        southLat: Double,
        eastLng: Double,
        westLng: Double,
        minZoom: Int,
        maxZoom: Int
    ): Flow<Float> = flow {
        // Emits offline cache preparation progress
        emit(0.2f)
        val tilesDir = Configuration.getInstance().osmdroidTileCache
        if (!tilesDir.exists()) {
            tilesDir.mkdirs()
        }
        emit(0.6f)
        emit(1.0f)
    }

    override fun clearCache() {
        try {
            Configuration.getInstance().osmdroidTileCache?.deleteRecursively()
        } catch (_: Exception) {}
    }
}
