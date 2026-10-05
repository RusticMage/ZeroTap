package com.zerotap.sensor.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
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
    private val _dataFlow = MutableSharedFlow<LocationSample>(extraBufferCapacity = 20)
    override val dataFlow: Flow<LocationSample> = _dataFlow

    override var isActive: Boolean = false
        private set

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager: LocationManager? = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                emitLocation(loc)
            }
        }
    }

    private val nativeLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            emitLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun emitLocation(loc: Location) {
        val sample = LocationSample(
            timestamp = if (loc.time > 0) loc.time else System.currentTimeMillis(),
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = if (loc.hasAccuracy()) loc.accuracy else 25f,
            speed = if (loc.hasSpeed()) loc.speed else 0f,
            bearing = if (loc.hasBearing()) loc.bearing else 0f
        )
        _dataFlow.tryEmit(sample)
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Logger.sensor("LocationData", "Location permission not granted")
            return
        }

        // 1. Immediately emit best available cached/last-known location from all providers
        emitBestLastKnownLocation()

        if (isActive) return

        try {
            // 2. Start FusedLocationProviderClient updates
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .build()

            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    emitLocation(loc)
                    Logger.sensor("LocationData", "Emitted fused last-known: ${loc.latitude}, ${loc.longitude}")
                }
            }
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            isActive = true
            Logger.sensor("LocationData", "Started fused location updates")
        } catch (e: Exception) {
            Logger.sensor("LocationData", "Fused client request failed, fallback to native: ${e.message}")
        }

        // 3. Register native LocationManager updates as robust backup
        try {
            locationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1f, nativeLocationListener, Looper.getMainLooper())
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 1f, nativeLocationListener, Looper.getMainLooper())
                }
                isActive = true
            }
        } catch (e: Exception) {
            Logger.sensor("LocationData", "Native location manager request failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun emitBestLastKnownLocation() {
        try {
            var bestLoc: Location? = null
            locationManager?.let { lm ->
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                for (provider in providers) {
                    try {
                        if (lm.isProviderEnabled(provider)) {
                            val loc = lm.getLastKnownLocation(provider)
                            if (loc != null) {
                                if (bestLoc == null || loc.time > bestLoc!!.time || (loc.accuracy < bestLoc!!.accuracy && loc.accuracy > 0)) {
                                    bestLoc = loc
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            bestLoc?.let { loc ->
                emitLocation(loc)
                Logger.sensor("LocationData", "Emitted best cached location: ${loc.latitude}, ${loc.longitude} (${loc.provider})")
            }
        } catch (_: Exception) {}
    }

    override fun stop() {
        if (!isActive) return
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        try {
            locationManager?.removeUpdates(nativeLocationListener)
        } catch (_: Exception) {}
        isActive = false
        Logger.sensor("LocationData", "Stopped location data source")
    }
}
