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
import com.zerotap.domain.model.RoutePoint
import com.zerotap.domain.model.SensorDiagnostics
import com.zerotap.domain.model.TrustedPlace
import com.zerotap.domain.model.TrustedPlaceType
import com.zerotap.service.ProtectionForegroundService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class GpsStatus(val displayName: String) {
    PERMISSION_REQUIRED("Permission Needed"),
    PROVIDER_DISABLED("Location Disabled"),
    WAITING_FOR_FIX("Acquiring GPS Fix..."),
    POOR_ACCURACY("GPS Weak / Low Accuracy"),
    READY("GPS Active"),
    UNAVAILABLE("Sensors Offline")
}

data class DestinationSearchResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val type: String
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val userPreferences = UserPreferences(context)
    private val safetyDb = ChennaiSafetyDatabase(context)
    private val safeSpaceRepo = com.zerotap.ServiceLocator.safeSpaceRepository
    private val navigationRepo = com.zerotap.ServiceLocator.navigationRepository
    private val trustedPlaceRepo = com.zerotap.ServiceLocator.trustedPlaceRepository
    private val locationRepo = com.zerotap.ServiceLocator.locationRepository

    private val _diagnostics = MutableStateFlow(
        SensorDiagnostics(
            isLocationAvailable = false
        )
    )
    val diagnostics: StateFlow<SensorDiagnostics> = _diagnostics.asStateFlow()

    val activeRoute: StateFlow<com.zerotap.domain.model.NavigationRoute?> = navigationRepo.activeRoute
    val navigationProgress: StateFlow<com.zerotap.domain.model.NavigationProgress?> = navigationRepo.navigationProgress

    val denseSafeSpaces: StateFlow<List<com.zerotap.domain.model.SafeSpace>> = safeSpaceRepo.observeSafeSpaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trustedPlaces: StateFlow<List<TrustedPlace>> = trustedPlaceRepo.observeTrustedPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map Follow Mode: automatically follows user during navigation unless user manually panned/zoomed
    private val _isFollowModeEnabled = MutableStateFlow(true)
    val isFollowModeEnabled: StateFlow<Boolean> = _isFollowModeEnabled.asStateFlow()

    // Search query & results
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<DestinationSearchResult>>(emptyList())
    val searchResults: StateFlow<List<DestinationSearchResult>> = _searchResults.asStateFlow()

    // Selected destination / point on map (from long press or marker tap)
    private val _selectedDestinationPoint = MutableStateFlow<Pair<Double, Double>?>(null)
    val selectedDestinationPoint: StateFlow<Pair<Double, Double>?> = _selectedDestinationPoint.asStateFlow()

    // Navigation calculation error / status message
    private val _navigationError = MutableStateFlow<String?>(null)
    val navigationError: StateFlow<String?> = _navigationError.asStateFlow()

    init {
        // Ensure location tracking is active in LocationRepository
        viewModelScope.launch {
            try {
                locationRepo.startTracking()
                locationRepo.getCurrentLocationSample()?.let { sample ->
                    _diagnostics.update { current ->
                        current.copy(
                            isLocationAvailable = true,
                            locationLatitude = sample.latitude,
                            locationLongitude = sample.longitude,
                            locationAccuracy = sample.accuracy,
                            locationSpeed = sample.speed,
                            locationBearing = if (sample.bearing != 0f) sample.bearing else current.locationBearing
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // 1. Observe LocationRepository directly (independent of whether ProtectionService is running!)
        viewModelScope.launch {
            var lastMapSyncedTime = 0L
            locationRepo.observeLocationSample().collect { sample ->
                _diagnostics.update { current ->
                    current.copy(
                        isLocationAvailable = true,
                        locationLatitude = sample.latitude,
                        locationLongitude = sample.longitude,
                        locationAccuracy = sample.accuracy,
                        locationSpeed = sample.speed,
                        locationBearing = if (sample.bearing != 0f) sample.bearing else current.locationBearing
                    )
                }

                // Stream live GPS telemetry to responder command center
                val now = System.currentTimeMillis()
                if (now - lastMapSyncedTime >= 3000L) {
                    lastMapSyncedTime = now
                    try {
                        com.zerotap.ServiceLocator.syncRepository.syncCurrentLocation(
                            latitude = sample.latitude,
                            longitude = sample.longitude,
                            speed = sample.speed,
                            bearing = sample.bearing
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        // 2. Also observe ProtectionForegroundService diagnostics to merge service telemetry when active
        viewModelScope.launch {
            ProtectionForegroundService.diagnostics.collect { serviceDiag ->
                _diagnostics.update { current ->
                    current.copy(
                        isLocationAvailable = serviceDiag.isLocationAvailable || current.isLocationAvailable,
                        locationLatitude = serviceDiag.locationLatitude ?: current.locationLatitude,
                        locationLongitude = serviceDiag.locationLongitude ?: current.locationLongitude,
                        locationAccuracy = serviceDiag.locationAccuracy ?: current.locationAccuracy,
                        locationSpeed = serviceDiag.locationSpeed ?: current.locationSpeed,
                        locationBearing = serviceDiag.locationBearing ?: current.locationBearing,
                        temporalRiskState = serviceDiag.temporalRiskState
                    )
                }
            }
        }

        // 3. Forward live location & actual bearing updates to navigation engine
        viewModelScope.launch {
            diagnostics.collect { diag ->
                val lat = diag.locationLatitude
                val lng = diag.locationLongitude
                if (lat != null && lng != null && navigationRepo.isNavigating()) {
                    navigationRepo.updateCurrentLocation(
                        currentLatitude = lat,
                        currentLongitude = lng,
                        currentBearing = diag.locationBearing ?: 0f,
                        speedMs = diag.locationSpeed ?: 0f
                    )
                }
            }
        }
    }

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
            val acc = diag.locationAccuracy ?: 0f
            if (acc > 50f) {
                GpsStatus.POOR_ACCURACY
            } else {
                GpsStatus.READY
            }
        } else if (diag.isLocationAvailable) {
            GpsStatus.WAITING_FOR_FIX
        } else {
            GpsStatus.WAITING_FOR_FIX
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GpsStatus.WAITING_FOR_FIX)

    fun refreshLocation() {
        viewModelScope.launch {
            try {
                locationRepo.startTracking()
                locationRepo.getCurrentLocationSample()?.let { sample ->
                    _diagnostics.update { current ->
                        current.copy(
                            isLocationAvailable = true,
                            locationLatitude = sample.latitude,
                            locationLongitude = sample.longitude,
                            locationAccuracy = sample.accuracy,
                            locationSpeed = sample.speed,
                            locationBearing = if (sample.bearing != 0f) sample.bearing else current.locationBearing
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun setFollowMode(enabled: Boolean) {
        _isFollowModeEnabled.value = enabled
    }

    fun onUserMapDrag() {
        // User manually interacted with map: disable automatic snapping
        _isFollowModeEnabled.value = false
    }

    fun onLocateMePressed(): Boolean {
        // Restores follow mode and triggers refresh
        _isFollowModeEnabled.value = true
        refreshLocation()
        return diagnostics.value.locationLatitude != null && diagnostics.value.locationLongitude != null
    }

    fun setFilter(type: SafetyResourceType?) {
        _selectedFilter.value = type
    }

    fun setHomeLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            userPreferences.setHomeCoordinates(latitude, longitude, "Home")
            // Also save/update Home in Trusted Places table
            trustedPlaceRepo.addTrustedPlace(
                TrustedPlace(
                    id = "tp-home-default",
                    name = "Home",
                    type = TrustedPlaceType.HOME,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = 150f
                )
            )
        }
    }

    fun clearHomeLocation() {
        viewModelScope.launch {
            userPreferences.clearHomeCoordinates()
            trustedPlaceRepo.deleteTrustedPlace("tp-home-default")
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

    // --- Search Destination ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        val q = query.trim().lowercase()
        val results = mutableListOf<DestinationSearchResult>()

        // Match Trusted Places
        trustedPlaces.value.filter { it.name.lowercase().contains(q) || it.type.displayName.lowercase().contains(q) }
            .forEach {
                results.add(
                    DestinationSearchResult(
                        name = it.name,
                        address = "${it.type.displayName} · Trusted Place",
                        latitude = it.latitude,
                        longitude = it.longitude,
                        type = "TRUSTED_PLACE"
                    )
                )
            }

        // Match Emergency Safe Spaces (all 122 across Tamil Nadu)
        emergencyResources.filter { it.name.lowercase().contains(q) || it.address.lowercase().contains(q) }
            .take(8)
            .forEach {
                results.add(
                    DestinationSearchResult(
                        name = it.name,
                        address = it.address,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        type = it.type.displayName
                    )
                )
            }

        _searchResults.value = results
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    // --- Destination & Navigation ---
    fun startNavigationTo(destLat: Double, destLng: Double, name: String) {
        val currentLat = diagnostics.value.locationLatitude
        val currentLng = diagnostics.value.locationLongitude
        if (currentLat == null || currentLng == null) {
            _navigationError.value = "GPS fix not available. Cannot calculate navigation origin."
            return
        }

        _navigationError.value = null
        viewModelScope.launch {
            val origin = RoutePoint(currentLat, currentLng)
            val destination = RoutePoint(destLat, destLng)
            val result = navigationRepo.calculateRoute(origin, destination, name)
            result.onSuccess { route ->
                navigationRepo.startNavigation(route)
                _isFollowModeEnabled.value = true
                clearSearch()
            }.onFailure { err ->
                _navigationError.value = err.message ?: "Failed to calculate road route"
            }
        }
    }

    fun stopNavigation() {
        navigationRepo.stopNavigation()
        _isFollowModeEnabled.value = false
    }

    fun clearNavigationError() {
        _navigationError.value = null
    }

    // --- Trusted Places Management ---
    fun addTrustedPlace(name: String, type: TrustedPlaceType, latitude: Double, longitude: Double, radius: Float = 100f) {
        viewModelScope.launch {
            val place = TrustedPlace(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { type.displayName },
                type = type,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radius
            )
            trustedPlaceRepo.addTrustedPlace(place)
        }
    }

    fun updateTrustedPlace(place: TrustedPlace) {
        viewModelScope.launch {
            trustedPlaceRepo.updateTrustedPlace(place)
        }
    }

    fun deleteTrustedPlace(id: String) {
        viewModelScope.launch {
            trustedPlaceRepo.deleteTrustedPlace(id)
        }
    }
}
