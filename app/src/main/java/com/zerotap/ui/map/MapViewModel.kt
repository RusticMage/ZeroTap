package com.zerotap.ui.map

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotap.data.datastore.UserPreferences
import com.zerotap.data.safety.ChennaiSafetyDatabase
import com.zerotap.data.safety.SafetyResource
import com.zerotap.data.safety.SafetyResourceType
import com.zerotap.domain.model.SensorDiagnostics
import com.zerotap.service.ProtectionForegroundService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class GpsStatus(val displayName: String) {
    PERMISSION_REQUIRED("Permission Needed"),
    PROVIDER_DISABLED("Location Disabled"),
    WAITING_FOR_FIX("Acquiring GPS Fix..."),
    READY("GPS Active"),
    UNAVAILABLE("Sensors Offline")
}

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val userPreferences = UserPreferences(context)
    private val safetyDb = ChennaiSafetyDatabase(context)

    val diagnostics: StateFlow<SensorDiagnostics> = ProtectionForegroundService.diagnostics

    // Initial value is null - NEVER default to fake Chennai coordinates (P8)
    val homeCoordinates: StateFlow<Pair<Double, Double>?> = userPreferences.homeCoordinates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedFilter = MutableStateFlow<SafetyResourceType?>(null)
    val selectedFilter: StateFlow<SafetyResourceType?> = _selectedFilter.asStateFlow()

    val emergencyResources: List<SafetyResource> = safetyDb.getAllEmergencyResources()

    val gpsStatus: StateFlow<GpsStatus> = diagnostics.map { diag ->
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            return@map GpsStatus.PERMISSION_REQUIRED
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val isNetEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        if (locationManager != null && !isGpsEnabled && !isNetEnabled) {
            return@map GpsStatus.PROVIDER_DISABLED
        }

        if (diag.locationLatitude != null && diag.locationLongitude != null) {
            GpsStatus.READY
        } else if (diag.isLocationAvailable) {
            GpsStatus.WAITING_FOR_FIX
        } else {
            GpsStatus.UNAVAILABLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GpsStatus.WAITING_FOR_FIX)

    fun setFilter(type: SafetyResourceType?) {
        _selectedFilter.value = type
    }

    fun setHomeLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            userPreferences.setHomeCoordinates(latitude, longitude, "Home")
        }
    }

    fun clearHomeLocation() {
        viewModelScope.launch {
            userPreferences.clearHomeCoordinates()
        }
    }

    fun calculateDistanceAndBearingToHome(userLat: Double, userLng: Double): Pair<Float, Float>? {
        val home = homeCoordinates.value ?: return null
        val results = FloatArray(2)
        Location.distanceBetween(userLat, userLng, home.first, home.second, results)
        val distanceMeters = results[0]
        val bearingDegrees = (results[1] + 360f) % 360f
        return Pair(distanceMeters, bearingDegrees)
    }

    fun getNearestResource(lat: Double, lng: Double): Pair<SafetyResource, Float>? {
        return safetyDb.getNearestResource(lat, lng, _selectedFilter.value)
    }
}
