package com.zerotap.domain.repository

import android.location.Location
import com.zerotap.domain.model.LocationSample
import kotlinx.coroutines.flow.Flow

/**
 * Authoritative location subsystem repository (Requirement Section 17).
 * Centralizes all GPS, bearing, speed, and location tracking across the app.
 */
interface LocationRepository {

    fun observeLocation(): Flow<Location>

    fun observeLocationSample(): Flow<LocationSample>

    suspend fun getCurrentLocation(): Location?

    suspend fun getCurrentLocationSample(): LocationSample?

    suspend fun startTracking()

    suspend fun stopTracking()

    val isTracking: Boolean
}
