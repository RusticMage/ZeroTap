package com.zerotap.domain.model

enum class ManeuverType {
    DEPART,
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    TURN_SLIGHT_LEFT,
    TURN_SLIGHT_RIGHT,
    U_TURN,
    ARRIVE
}

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0
)

data class RouteStep(
    val stepIndex: Int,
    val instruction: String,
    val distanceMeters: Float,
    val durationSeconds: Long,
    val startPoint: RoutePoint,
    val endPoint: RoutePoint,
    val maneuver: ManeuverType = ManeuverType.STRAIGHT
)

data class NavigationRoute(
    val id: String,
    val origin: RoutePoint,
    val destination: RoutePoint,
    val destinationName: String,
    val totalDistanceMeters: Float,
    val totalDurationSeconds: Long,
    val steps: List<RouteStep>,
    val geometryPoints: List<RoutePoint>,
    val isOfflineCalculated: Boolean = true
)

data class NavigationProgress(
    val routeId: String,
    val currentStepIndex: Int,
    val currentInstruction: String,
    val distanceToNextStepMeters: Float,
    val remainingDistanceMeters: Float,
    val remainingDurationSeconds: Long,
    val currentBearingDegrees: Float,
    val isOffRoute: Boolean = false,
    val destinationReached: Boolean = false
)
