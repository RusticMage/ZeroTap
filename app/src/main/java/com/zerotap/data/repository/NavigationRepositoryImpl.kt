package com.zerotap.data.repository

import com.zerotap.domain.model.*
import com.zerotap.domain.repository.NavigationRepository
import com.zerotap.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.*

class NavigationRepositoryImpl : NavigationRepository {

    private val _activeRoute = MutableStateFlow<NavigationRoute?>(null)
    override val activeRoute: StateFlow<NavigationRoute?> = _activeRoute.asStateFlow()

    private val _navigationProgress = MutableStateFlow<NavigationProgress?>(null)
    override val navigationProgress: StateFlow<NavigationProgress?> = _navigationProgress.asStateFlow()

    private var isRerouting = false

    companion object {
        /**
         * Standalone mathematical Haversine distance (meters) and initial forward azimuth bearing (degrees 0..360).
         */
        fun computeDistanceAndBearing(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Pair<Float, Float> {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)

            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val dist = (6371000.0 * c).toFloat()

            val y = sin(dLon) * cos(rLat2)
            val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
            val bearing = ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()

            return Pair(dist, bearing)
        }
    }

    override suspend fun calculateRoute(
        origin: RoutePoint,
        destination: RoutePoint,
        destinationName: String
    ): Result<NavigationRoute> = withContext(Dispatchers.IO) {
        // Attempt genuine road routing via OSRM public road router
        try {
            val osrmUrl = "https://router.project-osrm.org/route/v1/driving/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=geojson&steps=true"
            val connection = (URL(osrmUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseOsrmResponse(responseText, origin, destination, destinationName)
                if (parsed != null) {
                    Logger.sensor("Navigation", "Successfully fetched genuine road route with ${parsed.geometryPoints.size} road nodes")
                    return@withContext Result.success(parsed)
                }
            } else {
                Logger.sensor("Navigation", "OSRM responded with HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Logger.sensor("Navigation", "Online road router unreachable (${e.message}). Falling back to direct emergency evacuation vector.")
        }

        // Offline / Fallback: When road router is unreachable or device is offline,
        // provide immediate direct evacuation vector so user is NEVER stranded without navigation.
        val evacuationRoute = createDirectEvacuationRoute(origin, destination, destinationName)
        Result.success(evacuationRoute)
    }

    private fun createDirectEvacuationRoute(
        origin: RoutePoint,
        destination: RoutePoint,
        destinationName: String
    ): NavigationRoute {
        val (dist, bearing) = computeDistanceAndBearing(
            origin.latitude, origin.longitude,
            destination.latitude, destination.longitude
        )
        // Assume walking/running speed 1.4 m/s (~5 km/h)
        val durationSec = (dist / 1.4f).roundToLong().coerceAtLeast(30L)

        // Interpolate 10 points between origin and destination
        val points = mutableListOf<RoutePoint>()
        val stepsCount = 10
        for (i in 0..stepsCount) {
            val fraction = i.toDouble() / stepsCount.toDouble()
            val lat = origin.latitude + fraction * (destination.latitude - origin.latitude)
            val lon = origin.longitude + fraction * (destination.longitude - origin.longitude)
            points.add(RoutePoint(lat, lon))
        }

        val cardinal = when (((bearing + 22.5) / 45).toInt() % 8) {
            0 -> "North"
            1 -> "Northeast"
            2 -> "East"
            3 -> "Southeast"
            4 -> "South"
            5 -> "Southwest"
            6 -> "West"
            else -> "Northwest"
        }

        val steps = listOf(
            RouteStep(
                stepIndex = 0,
                instruction = "Head $cardinal (${bearing.toInt()}°) towards $destinationName",
                distanceMeters = dist * 0.8f,
                durationSeconds = (durationSec * 0.8f).roundToLong(),
                startPoint = origin,
                endPoint = points[points.size - 2],
                maneuver = ManeuverType.DEPART
            ),
            RouteStep(
                stepIndex = 1,
                instruction = "Arrive at $destinationName",
                distanceMeters = dist * 0.2f,
                durationSeconds = (durationSec * 0.2f).roundToLong(),
                startPoint = points[points.size - 2],
                endPoint = destination,
                maneuver = ManeuverType.ARRIVE
            )
        )

        return NavigationRoute(
            id = UUID.randomUUID().toString(),
            origin = origin,
            destination = destination,
            destinationName = destinationName,
            totalDistanceMeters = dist,
            totalDurationSeconds = durationSec,
            steps = steps,
            geometryPoints = points,
            isOfflineCalculated = true
        )
    }

    private fun parseOsrmResponse(
        jsonStr: String,
        origin: RoutePoint,
        destination: RoutePoint,
        destinationName: String
    ): NavigationRoute? {
        try {
            val root = JSONObject(jsonStr)
            val code = root.optString("code")
            if (code != "Ok") return null

            val routes = root.getJSONArray("routes")
            if (routes.length() == 0) return null
            val routeObj = routes.getJSONObject(0)

            val totalDistance = routeObj.getDouble("distance").toFloat()
            val totalDuration = routeObj.getDouble("duration").toLong()

            // Geometry points
            val geometryObj = routeObj.getJSONObject("geometry")
            val coords = geometryObj.getJSONArray("coordinates")
            val geometryPoints = mutableListOf<RoutePoint>()
            for (i in 0 until coords.length()) {
                val pt = coords.getJSONArray(i)
                val lng = pt.getDouble(0)
                val lat = pt.getDouble(1)
                geometryPoints.add(RoutePoint(lat, lng))
            }

            // Steps
            val steps = mutableListOf<RouteStep>()
            val legs = routeObj.getJSONArray("legs")
            var stepIndex = 0
            for (l in 0 until legs.length()) {
                val leg = legs.getJSONObject(l)
                val legSteps = leg.getJSONArray("steps")
                for (s in 0 until legSteps.length()) {
                    val step = legSteps.getJSONObject(s)
                    val dist = step.getDouble("distance").toFloat()
                    val dur = step.getDouble("duration").toLong()
                    val manObj = step.getJSONObject("maneuver")
                    val manTypeStr = manObj.optString("type")
                    val modifier = manObj.optString("modifier")
                    val name = step.optString("name", "road")

                    val maneuver = when {
                        manTypeStr == "depart" -> ManeuverType.DEPART
                        manTypeStr == "arrive" -> ManeuverType.ARRIVE
                        modifier.contains("slight right") -> ManeuverType.TURN_SLIGHT_RIGHT
                        modifier.contains("slight left") -> ManeuverType.TURN_SLIGHT_LEFT
                        modifier.contains("right") -> ManeuverType.TURN_RIGHT
                        modifier.contains("left") -> ManeuverType.TURN_LEFT
                        modifier.contains("u-turn") -> ManeuverType.U_TURN
                        else -> ManeuverType.STRAIGHT
                    }

                    val instruction = when (maneuver) {
                        ManeuverType.DEPART -> "Head on $name towards destination"
                        ManeuverType.ARRIVE -> "Arrive at $destinationName"
                        ManeuverType.TURN_RIGHT -> "Turn right onto $name"
                        ManeuverType.TURN_LEFT -> "Turn left onto $name"
                        ManeuverType.TURN_SLIGHT_RIGHT -> "Keep slight right onto $name"
                        ManeuverType.TURN_SLIGHT_LEFT -> "Keep slight left onto $name"
                        ManeuverType.U_TURN -> "Make a U-turn onto $name"
                        ManeuverType.STRAIGHT -> if (name.isNotBlank() && name != "road") "Continue onto $name" else "Continue straight"
                    }

                    val loc = manObj.getJSONArray("location")
                    val startPt = RoutePoint(loc.getDouble(1), loc.getDouble(0))
                    val endPt = if (geometryPoints.isNotEmpty()) geometryPoints.last() else destination

                    steps.add(
                        RouteStep(
                            stepIndex = stepIndex++,
                            instruction = instruction,
                            distanceMeters = dist,
                            durationSeconds = dur,
                            startPoint = startPt,
                            endPoint = endPt,
                            maneuver = maneuver
                        )
                    )
                }
            }

            if (steps.isEmpty()) {
                steps.add(
                    RouteStep(
                        stepIndex = 0,
                        instruction = "Proceed to $destinationName",
                        distanceMeters = totalDistance,
                        durationSeconds = totalDuration,
                        startPoint = origin,
                        endPoint = destination,
                        maneuver = ManeuverType.DEPART
                    )
                )
            }

            return NavigationRoute(
                id = UUID.randomUUID().toString(),
                origin = origin,
                destination = destination,
                destinationName = destinationName,
                totalDistanceMeters = totalDistance,
                totalDurationSeconds = totalDuration,
                steps = steps,
                geometryPoints = geometryPoints,
                isOfflineCalculated = false
            )
        } catch (e: Exception) {
            Logger.sensor("Navigation", "Error parsing OSRM route: ${e.message}")
            return null
        }
    }

    override suspend fun startNavigation(route: NavigationRoute) {
        _activeRoute.value = route
        val initialStep = route.steps.firstOrNull()
        _navigationProgress.value = NavigationProgress(
            routeId = route.id,
            currentStepIndex = 0,
            currentInstruction = initialStep?.instruction ?: "Proceed along route",
            distanceToNextStepMeters = initialStep?.distanceMeters ?: 0f,
            remainingDistanceMeters = route.totalDistanceMeters,
            remainingDurationSeconds = route.totalDurationSeconds,
            currentBearingDegrees = 0f,
            isOffRoute = false,
            destinationReached = false
        )
        Logger.sensor("Navigation", "Started turn-by-turn road navigation to ${route.destinationName}")
    }

    override fun updateCurrentLocation(
        currentLatitude: Double,
        currentLongitude: Double,
        currentBearing: Float,
        speedMs: Float
    ) {
        val route = _activeRoute.value ?: return
        val currentProgress = _navigationProgress.value ?: return
        if (currentProgress.destinationReached) return

        // Check distance to destination
        val (distToDest, _) = computeDistanceAndBearing(
            currentLatitude, currentLongitude,
            route.destination.latitude, route.destination.longitude
        )

        if (distToDest < 25f) {
            _navigationProgress.value = currentProgress.copy(
                currentInstruction = "You have arrived at ${route.destinationName}",
                distanceToNextStepMeters = 0f,
                remainingDistanceMeters = 0f,
                remainingDurationSeconds = 0L,
                currentBearingDegrees = currentBearing,
                destinationReached = true
            )
            return
        }

        // Find closest point on the actual road geometry points to check off-route
        var minDistanceToGeometry = Float.MAX_VALUE
        var closestGeometryIndex = 0
        val points = route.geometryPoints
        for (i in points.indices) {
            val (dist, _) = computeDistanceAndBearing(currentLatitude, currentLongitude, points[i].latitude, points[i].longitude)
            if (dist < minDistanceToGeometry) {
                minDistanceToGeometry = dist
                closestGeometryIndex = i
            }
        }

        // Off-route if user is > 80 meters from any point on the route geometry
        val isOffRoute = minDistanceToGeometry > 80f

        // Progress step tracking
        val steps = route.steps
        var activeStepIndex = currentProgress.currentStepIndex
        val activeStep = steps.getOrNull(activeStepIndex)

        if (activeStep != null) {
            val (distToEnd, _) = computeDistanceAndBearing(
                currentLatitude, currentLongitude,
                activeStep.endPoint.latitude, activeStep.endPoint.longitude
            )
            if (distToEnd < 30f && activeStepIndex < steps.size - 1) {
                activeStepIndex++
            }
        }

        val step = steps.getOrNull(activeStepIndex) ?: steps.lastOrNull()
        val distToStep = if (step != null) {
            computeDistanceAndBearing(currentLatitude, currentLongitude, step.endPoint.latitude, step.endPoint.longitude).first
        } else distToDest

        // Remaining distance along remaining geometry points
        var remainingMeters = distToDest
        if (points.isNotEmpty() && closestGeometryIndex < points.size) {
            var acc = 0f
            for (i in closestGeometryIndex until points.size - 1) {
                acc += computeDistanceAndBearing(points[i].latitude, points[i].longitude, points[i + 1].latitude, points[i + 1].longitude).first
            }
            if (acc > 0f) remainingMeters = acc
        }

        val remainingSec = (remainingMeters / (if (speedMs > 2f) speedMs else 8.33f)).toLong().coerceAtLeast(10L)

        _navigationProgress.value = NavigationProgress(
            routeId = route.id,
            currentStepIndex = activeStepIndex,
            currentInstruction = if (isOffRoute) "Off route — recalculating" else (step?.instruction ?: "Proceed along route"),
            distanceToNextStepMeters = distToStep,
            remainingDistanceMeters = remainingMeters,
            remainingDurationSeconds = remainingSec,
            currentBearingDegrees = currentBearing,
            isOffRoute = isOffRoute,
            destinationReached = false
        )
    }

    override fun stopNavigation() {
        _activeRoute.value = null
        _navigationProgress.value = null
        Logger.sensor("Navigation", "Navigation stopped")
    }

    override fun isNavigating(): Boolean = _activeRoute.value != null
}
