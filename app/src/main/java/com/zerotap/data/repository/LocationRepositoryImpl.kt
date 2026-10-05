package com.zerotap.data.repository

import android.content.Context
import android.location.Location
import com.zerotap.domain.model.LocationSample
import com.zerotap.domain.repository.LocationRepository
import com.zerotap.sensor.location.LocationDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class LocationRepositoryImpl(
    private val context: Context,
    private val locationDataSource: LocationDataSource = LocationDataSource(context)
) : LocationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentLocationSample = MutableStateFlow<LocationSample?>(null)
    private val _currentAndroidLocation = MutableStateFlow<Location?>(null)

    private var previousSample: LocationSample? = null

    init {
        scope.launch {
            locationDataSource.dataFlow.collect { sample ->
                // Calculate bearing and speed if missing from consecutive samples
                val smoothedSample = if (sample.bearing == 0f && previousSample != null) {
                    val prev = previousSample!!
                    val dt = (sample.timestamp - prev.timestamp) / 1000f
                    if (dt in 0.5f..15.0f) {
                        val results = FloatArray(2)
                        Location.distanceBetween(prev.latitude, prev.longitude, sample.latitude, sample.longitude, results)
                        val dist = results[0]
                        val bearing = (results[1] + 360f) % 360f
                        val speed = if (sample.speed > 0f) sample.speed else (dist / dt)
                        sample.copy(bearing = bearing, speed = speed)
                    } else sample
                } else sample

                previousSample = smoothedSample
                _currentLocationSample.value = smoothedSample

                val loc = Location("FusedProvider").apply {
                    latitude = smoothedSample.latitude
                    longitude = smoothedSample.longitude
                    accuracy = smoothedSample.accuracy
                    time = smoothedSample.timestamp
                    speed = smoothedSample.speed
                    bearing = smoothedSample.bearing
                }
                _currentAndroidLocation.value = loc
            }
        }
    }

    override fun observeLocation(): Flow<Location> = _currentAndroidLocation.asStateFlow().filterNotNull()

    override fun observeLocationSample(): Flow<LocationSample> = _currentLocationSample.asStateFlow().filterNotNull()

    override suspend fun getCurrentLocation(): Location? = _currentAndroidLocation.value

    override suspend fun getCurrentLocationSample(): LocationSample? = _currentLocationSample.value

    override suspend fun startTracking() {
        locationDataSource.start()
    }

    override suspend fun stopTracking() {
        locationDataSource.stop()
    }

    override val isTracking: Boolean
        get() = locationDataSource.isActive
}
