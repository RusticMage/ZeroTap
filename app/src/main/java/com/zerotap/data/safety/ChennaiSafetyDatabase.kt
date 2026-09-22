package com.zerotap.data.safety

import android.content.Context
import android.location.Location
import com.zerotap.util.Logger
import org.json.JSONObject

enum class SafetyResourceType(val displayName: String) {
    POLICE("Police Station"),
    HOSPITAL("Hospital / Medical"),
    METRO("Metro Transit Station")
}

data class SafetyResource(
    val name: String,
    val type: SafetyResourceType,
    val latitude: Double,
    val longitude: Double,
    val phone: String,
    val address: String
)

class ChennaiSafetyDatabase(private val context: Context) {

    private val emergencyResources = mutableListOf<SafetyResource>()

    init {
        loadData()
    }

    private fun loadData() {
        try {
            val jsonString = context.assets.open("chennai_safety_data.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            // Parse Emergency Resources (Physical safe places on Map)
            val resArray = root.optJSONArray("emergency_resources")
            if (resArray != null) {
                for (i in 0 until resArray.length()) {
                    val obj = resArray.getJSONObject(i)
                    val typeStr = obj.getString("type")
                    val type = when (typeStr) {
                        "POLICE" -> SafetyResourceType.POLICE
                        "HOSPITAL" -> SafetyResourceType.HOSPITAL
                        "METRO" -> SafetyResourceType.METRO
                        else -> SafetyResourceType.POLICE
                    }
                    emergencyResources.add(
                        SafetyResource(
                            name = obj.getString("name"),
                            type = type,
                            latitude = obj.getDouble("latitude"),
                            longitude = obj.getDouble("longitude"),
                            phone = obj.getString("phone"),
                            address = obj.getString("address")
                        )
                    )
                }
            }
            Logger.sensor("SafetyDB", "Loaded ${emergencyResources.size} verified emergency resources")
        } catch (e: Exception) {
            Logger.sensor("SafetyDB", "Failed to load safety database: ${e.message}")
        }
    }

    /**
     * Returns physical emergency resources for display on the offline map.
     */
    fun getAllEmergencyResources(): List<SafetyResource> {
        return emergencyResources
    }

    /**
     * Finds the nearest physical emergency resource to the given coordinates.
     */
    fun getNearestResource(latitude: Double, longitude: Double, type: SafetyResourceType? = null): Pair<SafetyResource, Float>? {
        var nearest: SafetyResource? = null
        var minDistance = Float.MAX_VALUE
        val results = FloatArray(1)

        val filtered = if (type != null) emergencyResources.filter { it.type == type } else emergencyResources

        for (res in filtered) {
            Location.distanceBetween(latitude, longitude, res.latitude, res.longitude, results)
            if (results[0] < minDistance) {
                minDistance = results[0]
                nearest = res
            }
        }

        return nearest?.let { Pair(it, minDistance) }
    }
}
