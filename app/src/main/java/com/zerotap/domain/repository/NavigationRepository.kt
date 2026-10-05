package com.zerotap.domain.repository

import com.zerotap.domain.model.NavigationProgress
import com.zerotap.domain.model.NavigationRoute
import com.zerotap.domain.model.RoutePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NavigationRepository {

    val activeRoute: StateFlow<NavigationRoute?>

    val navigationProgress: StateFlow<NavigationProgress?>

    suspend fun calculateRoute(
        origin: RoutePoint,
        destination: RoutePoint,
        destinationName: String
    ): Result<NavigationRoute>

    suspend fun startNavigation(route: NavigationRoute)

    fun updateCurrentLocation(
        currentLatitude: Double,
        currentLongitude: Double,
        currentBearing: Float,
        speedMs: Float
    )

    fun stopNavigation()

    fun isNavigating(): Boolean
}
