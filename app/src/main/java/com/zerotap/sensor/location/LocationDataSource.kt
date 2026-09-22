package com.zerotap.sensor.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.zerotap.domain.model.LocationSample
import com.zerotap.sensor.SensorDataSource
import com.zerotap.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class LocationDataSource(private val context: Context) : SensorDataSource<LocationSample> {
    private val _dataFlow = MutableSharedFlow<LocationSample>(extraBufferCapacity = 10)
    override val dataFlow: Flow<LocationSample> = _dataFlow

    override var isActive: Boolean = false
        private set

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                val sample = LocationSample(
                    timestamp = loc.time,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracy = loc.accuracy,
                    speed = if (loc.hasSpeed()) loc.speed else 0f,
                    bearing = if (loc.hasBearing()) loc.bearing else 0f
                )
                _dataFlow.tryEmit(sample)
            }
        }
    }

    override fun start() {
        if (isActive) return
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Logger.sensor("LocationData", "Location permission not granted")
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
        
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val sample = LocationSample(
                        timestamp = loc.time,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = loc.accuracy,
                        speed = if (loc.hasSpeed()) loc.speed else 0f,
                        bearing = if (loc.hasBearing()) loc.bearing else 0f
                    )
                    _dataFlow.tryEmit(sample)
                    Logger.sensor("LocationData", "Emitted initial last-known location: ${loc.latitude}, ${loc.longitude}")
                }
            }
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            isActive = true
            Logger.sensor("LocationData", "Started location data source")
        } catch (e: SecurityException) {
            Logger.sensor("LocationData", "SecurityException requesting location: ${e.message}")
        }
    }

    override fun stop() {
        if (!isActive) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isActive = false
        Logger.sensor("LocationData", "Stopped location data source")
    }
}
