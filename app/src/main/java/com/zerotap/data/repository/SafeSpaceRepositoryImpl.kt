package com.zerotap.data.repository

import android.content.Context
import android.location.Location
import com.zerotap.data.db.dao.SafeSpaceDao
import com.zerotap.data.db.entity.toDomain
import com.zerotap.data.db.entity.toEntity
import com.zerotap.domain.model.SafeSpace
import com.zerotap.domain.model.SafeSpaceType
import com.zerotap.domain.repository.SafeSpaceRepository
import com.zerotap.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

class SafeSpaceRepositoryImpl(
    private val context: Context,
    private val dao: SafeSpaceDao
) : SafeSpaceRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            seedInitialSafeSpacesIfNeeded()
        }
    }

    private suspend fun seedInitialSafeSpacesIfNeeded() {
        try {
            val count = dao.getCount()
            if (count > 0) return

            val jsonString = context.assets.open("safe_spaces_dense.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val array = root.optJSONArray("safe_spaces") ?: return

            val list = mutableListOf<SafeSpace>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val typeStr = obj.optString("type", "POLICE")
                val type = try {
                    SafeSpaceType.valueOf(typeStr)
                } catch (_: Exception) {
                    SafeSpaceType.POLICE
                }

                list.add(
                    SafeSpace(
                        id = obj.optString("id", "SP-$i"),
                        name = obj.getString("name"),
                        type = type,
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        address = obj.optString("address", ""),
                        phone = obj.optString("phone", ""),
                        verified = obj.optBoolean("verified", true),
                        priority = obj.optInt("priority", 1),
                        availableOffline = true
                    )
                )
            }

            if (list.isNotEmpty()) {
                dao.insertAll(list.map { it.toEntity() })
                Logger.sensor("SafeSpaceRepo", "Successfully seeded ${list.size} high-density safe spaces into database")
            }
        } catch (e: Exception) {
            Logger.sensor("SafeSpaceRepo", "Failed to seed safe spaces: ${e.message}")
        }
    }

    override fun observeSafeSpaces(): Flow<List<SafeSpace>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getAllSafeSpaces(): List<SafeSpace> {
        val count = dao.getCount()
        if (count == 0) {
            seedInitialSafeSpacesIfNeeded()
        }
        return dao.getAllSnapshot().map { it.toDomain() }
    }

    override suspend fun getNearbySafeSpaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        type: SafeSpaceType?
    ): List<Pair<SafeSpace, Float>> {
        val all = getAllSafeSpaces()
        val filtered = if (type != null) all.filter { it.type == type } else all

        val results = FloatArray(1)
        val distancePairs = mutableListOf<Pair<SafeSpace, Float>>()

        for (space in filtered) {
            Location.distanceBetween(latitude, longitude, space.latitude, space.longitude, results)
            val dist = results[0]
            if (dist <= radiusMeters) {
                distancePairs.add(Pair(space, dist))
            }
        }

        // Sort by distance ascending
        return distancePairs.sortedBy { it.second }
    }

    override suspend fun getNearestSafeSpace(
        latitude: Double,
        longitude: Double,
        type: SafeSpaceType?
    ): Pair<SafeSpace, Float>? {
        return getNearbySafeSpaces(latitude, longitude, radiusMeters = 50000f, type = type).firstOrNull()
    }

    override suspend fun insertSafeSpaces(safeSpaces: List<SafeSpace>) {
        dao.insertAll(safeSpaces.map { it.toEntity() })
    }

    override suspend fun clearSafeSpaces() {
        dao.deleteAll()
    }
}
