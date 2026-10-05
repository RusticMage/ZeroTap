package com.zerotap.ui.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zerotap.data.safety.SafetyResource
import com.zerotap.data.safety.SafetyResourceType
import com.zerotap.domain.model.TrustedPlace
import com.zerotap.domain.model.TrustedPlaceType
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavHostController,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val diag by viewModel.diagnostics.collectAsStateWithLifecycle()
    val homeCoords by viewModel.homeCoordinates.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val gpsStatus by viewModel.gpsStatus.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRoute.collectAsStateWithLifecycle()
    val navProgress by viewModel.navigationProgress.collectAsStateWithLifecycle()
    val isFollowModeEnabled by viewModel.isFollowModeEnabled.collectAsStateWithLifecycle()
    val trustedPlaces by viewModel.trustedPlaces.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val navigationError by viewModel.navigationError.collectAsStateWithLifecycle()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var selectedResource by remember { mutableStateOf<SafetyResource?>(null) }
    var selectedTrustedPlace by remember { mutableStateOf<TrustedPlace?>(null) }
    var longPressedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var showAddTrustedPlaceDialog by remember { mutableStateOf(false) }
    var addPlaceInitialLat by remember { mutableStateOf(0.0) }
    var addPlaceInitialLng by remember { mutableStateOf(0.0) }
    var hasAutoCenteredUser by remember { mutableStateOf(false) }

    // Permission launcher for Location access
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            viewModel.refreshLocation()
        }
    }

    // Initialize osmdroid configuration & check permission
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName

        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.refreshLocation()
        }
    }

    val hasGpsFix = diag.locationLatitude != null && diag.locationLongitude != null
    // Fallback default center is Tamil Nadu center (Trichy 10.7905, 78.7047) ONLY for initial camera positioning when GPS is offline
    val centerLat = if (hasGpsFix) diag.locationLatitude!! else 10.7905
    val centerLng = if (hasGpsFix) diag.locationLongitude!! else 78.7047
    val defaultZoom = if (hasGpsFix) 15.0 else 7.8

    // Auto-center on user as soon as first GPS fix arrives
    LaunchedEffect(hasGpsFix) {
        if (hasGpsFix && !hasAutoCenteredUser && diag.locationLatitude != null && diag.locationLongitude != null) {
            hasAutoCenteredUser = true
            mapViewRef?.controller?.animateTo(GeoPoint(diag.locationLatitude!!, diag.locationLongitude!!))
            mapViewRef?.controller?.setZoom(16.0)
        }
    }

    val filteredResources = remember(selectedFilter, viewModel.emergencyResources) {
        if (selectedFilter == null) {
            viewModel.emergencyResources
        } else {
            viewModel.emergencyResources.filter { it.type == selectedFilter }
        }
    }

    // Follow-mode camera updates during navigation or active follow
    LaunchedEffect(diag.locationLatitude, diag.locationLongitude, isFollowModeEnabled, activeRoute != null) {
        if (isFollowModeEnabled && hasGpsFix) {
            mapViewRef?.controller?.animateTo(GeoPoint(diag.locationLatitude!!, diag.locationLongitude!!))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. OSMDroid MapView
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(defaultZoom)
                    controller.setCenter(GeoPoint(centerLat, centerLng))

                    // Intercept user manual drag/pan to suspend follow mode
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_MOVE && event.pointerCount >= 1) {
                            viewModel.onUserMapDrag()
                        }
                        false // Allow OSMDroid to process normal gestures
                    }

                    // Map Events Receiver for Long Press destination / add trusted place
                    val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = false

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            longPressedPoint = p
                            return true
                        }
                    })
                    overlays.add(0, mapEventsOverlay)

                    mapViewRef = this
                }
            },
            update = { mapView ->
                // Preserve the MapEventsOverlay at index 0, clear remaining overlays
                val baseEventOverlay = mapView.overlays.firstOrNull { it is MapEventsOverlay }
                mapView.overlays.clear()
                baseEventOverlay?.let { mapView.overlays.add(it) }

                // A. Current GPS Location Marker with Actual Bearing Rotation (P12)
                if (hasGpsFix) {
                    val userLocation = GeoPoint(diag.locationLatitude!!, diag.locationLongitude!!)
                    val userMarker = Marker(mapView).apply {
                        position = userLocation
                        title = "Your Location"
                        val bearingText = if (diag.locationBearing != null) " · Bearing: %.0f°".format(diag.locationBearing) else ""
                        snippet = "Accuracy: ±%.1fm · Speed: %.1f m/s%s".format(
                            diag.locationAccuracy ?: 0f,
                            diag.locationSpeed ?: 0f,
                            bearingText
                        )
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createUserLocationPuckDrawable(context, diag.locationBearing != null)
                        rotation = diag.locationBearing ?: 0f
                    }
                    mapView.overlays.add(userMarker)
                }

                // B. Home Location Marker
                val home = homeCoords
                if (home != null) {
                    val homeMarker = Marker(mapView).apply {
                        position = GeoPoint(home.first, home.second)
                        title = "Home Waypoint"
                        snippet = "Designated safe sanctuary"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createThemedMarkerDrawable(context, 0xFF4A7C59.toInt()) // Sage green
                    }
                    mapView.overlays.add(homeMarker)
                }

                // C. Trusted Places Markers
                trustedPlaces.forEach { tp ->
                    val tpMarker = Marker(mapView).apply {
                        position = GeoPoint(tp.latitude, tp.longitude)
                        title = tp.name
                        snippet = "${tp.type.displayName} · Trusted Zone (Radius: ${tp.radiusMeters.toInt()}m)"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createThemedMarkerDrawable(context, 0xFFD97706.toInt()) // Amber gold
                        setOnMarkerClickListener { _, _ ->
                            selectedTrustedPlace = tp
                            selectedResource = null
                            true
                        }
                    }
                    mapView.overlays.add(tpMarker)
                }

                // D. Emergency Resources Markers (Police, Hospitals, Metro)
                filteredResources.forEach { res ->
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(res.latitude, res.longitude)
                        title = res.name
                        snippet = "${res.type.displayName} · ${res.phone}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            selectedResource = res
                            selectedTrustedPlace = null
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }

                // E. Active Road Navigation Polyline
                activeRoute?.let { route ->
                    if (route.geometryPoints.isNotEmpty()) {
                        val polyline = Polyline(mapView).apply {
                            setPoints(route.geometryPoints.map { GeoPoint(it.latitude, it.longitude) })
                            outlinePaint.color = android.graphics.Color.parseColor("#E06D53")
                            outlinePaint.strokeWidth = 14f
                        }
                        mapView.overlays.add(polyline)
                    }
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. TOP CONTROLS: Search Bar, Status Card, Distance to Home & Filter Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter)
        ) {
            // A. Search Bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Search Tamil Nadu safe spaces or trusted places...", fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { viewModel.clearSearch() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // B. Search Results Dropdown Overlay
            AnimatedVisibility(visible = searchResults.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = 240.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(searchResults) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        mapViewRef?.controller?.animateTo(GeoPoint(result.latitude, result.longitude))
                                        mapViewRef?.controller?.setZoom(16.0)
                                        viewModel.startNavigationTo(result.latitude, result.longitude, result.name)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = result.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                SuggestionChip(
                                    onClick = {
                                        viewModel.startNavigationTo(result.latitude, result.longitude, result.name)
                                    },
                                    label = { Text("Navigate", fontSize = 11.sp) }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // C. Location Alert Bar (if permission required or location disabled)
            if (gpsStatus == GpsStatus.PERMISSION_REQUIRED) {
                Surface(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Location Permission Needed — Tap to grant permission",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (gpsStatus == GpsStatus.PROVIDER_DISABLED) {
                Surface(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Phone Location is Turned Off — Tap to open Settings",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // D. Header Card with GPS status labeling
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (gpsStatus == GpsStatus.PERMISSION_REQUIRED) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else if (gpsStatus == GpsStatus.PROVIDER_DISABLED) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    } catch (_: Exception) {}
                                } else {
                                    viewModel.refreshLocation()
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (gpsStatus) {
                                        GpsStatus.READY -> MaterialTheme.colorScheme.secondary
                                        GpsStatus.WAITING_FOR_FIX -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Tamil Nadu Safety & Resource Map",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hasGpsFix) {
                                    "GPS: %.4f, %.4f (±%.0fm)".format(
                                        diag.locationLatitude!!,
                                        diag.locationLongitude!!,
                                        diag.locationAccuracy ?: 0f
                                    )
                                } else {
                                    gpsStatus.displayName
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Save Current Location as Home Button
                    FilledTonalIconButton(
                        onClick = {
                            if (hasGpsFix) {
                                viewModel.setHomeLocation(diag.locationLatitude!!, diag.locationLongitude!!)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Home waypoint saved: %.4f, %.4f".format(diag.locationLatitude, diag.locationLongitude))
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Cannot set Home — waiting for active GPS fix")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Set current location as Home",
                            modifier = Modifier.size(20.dp),
                            tint = if (homeCoords != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // E. Distance to Home banner
            if (homeCoords != null && hasGpsFix) {
                val distBearing = viewModel.calculateDistanceAndBearingToHome(diag.locationLatitude!!, diag.locationLongitude!!)
                if (distBearing != null) {
                    val distMeters = distBearing.first
                    val bearing = distBearing.second
                    val distText = if (distMeters >= 1000f) "%.1f km".format(distMeters / 1000f) else "%.0f m".format(distMeters)
                    val dirText = when {
                        bearing >= 337.5 || bearing < 22.5 -> "North"
                        bearing < 67.5 -> "North-East"
                        bearing < 112.5 -> "East"
                        bearing < 157.5 -> "South-East"
                        bearing < 202.5 -> "South"
                        bearing < 247.5 -> "South-West"
                        bearing < 292.5 -> "West"
                        else -> "North-West"
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Home Waypoint: $distText ($dirText, %.0f°)".format(bearing),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // F. Resource Filter Chips (Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("All (${viewModel.emergencyResources.size})") }
                )
                FilterChip(
                    selected = selectedFilter == SafetyResourceType.POLICE,
                    onClick = { viewModel.setFilter(SafetyResourceType.POLICE) },
                    label = { Text("Police") }
                )
                FilterChip(
                    selected = selectedFilter == SafetyResourceType.HOSPITAL,
                    onClick = { viewModel.setFilter(SafetyResourceType.HOSPITAL) },
                    label = { Text("Hospitals") }
                )
                FilterChip(
                    selected = selectedFilter == SafetyResourceType.METRO,
                    onClick = { viewModel.setFilter(SafetyResourceType.METRO) },
                    label = { Text("Metro Transit") }
                )
            }
        }

        // 3. FLOATING ACTIONS: "Locate Me" Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (activeRoute != null || selectedResource != null || selectedTrustedPlace != null) 160.dp else 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Locate Me Floating Action Button
            FloatingActionButton(
                onClick = {
                    if (gpsStatus == GpsStatus.PERMISSION_REQUIRED) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        return@FloatingActionButton
                    }
                    if (gpsStatus == GpsStatus.PROVIDER_DISABLED) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } catch (_: Exception) {}
                        return@FloatingActionButton
                    }

                    val available = viewModel.onLocateMePressed()
                    if (available) {
                        mapViewRef?.controller?.animateTo(GeoPoint(diag.locationLatitude!!, diag.locationLongitude!!))
                        mapViewRef?.controller?.setZoom(16.0)
                        scope.launch {
                            snackbarHostState.showSnackbar("Centered on current location (Follow Mode on)")
                        }
                    } else {
                        viewModel.refreshLocation()
                        scope.launch {
                            snackbarHostState.showSnackbar("Acquiring GPS fix from satellites...")
                        }
                    }
                },
                containerColor = if (isFollowModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (isFollowModeEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Locate Me",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 4. NAVIGATION ERROR BANNER
        navigationError?.let { errorMsg ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 80.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    IconButton(onClick = { viewModel.clearNavigationError() }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // 5. BOTTOM OVERLAY: TRUSTED PLACE CARD
        selectedTrustedPlace?.let { place ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TRUSTED PLACE · ${place.type.displayName.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = place.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Safety Radius: ${place.radiusMeters.toInt()}m · Lat: %.4f, Lng: %.4f".format(place.latitude, place.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { selectedTrustedPlace = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.startNavigationTo(place.latitude, place.longitude, place.name)
                                selectedTrustedPlace = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Navigate Here")
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.deleteTrustedPlace(place.id)
                                selectedTrustedPlace = null
                                scope.launch {
                                    snackbarHostState.showSnackbar("Trusted place '${place.name}' deleted")
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }

        // 6. BOTTOM OVERLAY: SELECTED OR NEAREST EMERGENCY RESOURCE CARD
        val targetResource = selectedResource ?: if (hasGpsFix && activeRoute == null && selectedTrustedPlace == null) {
            viewModel.getNearestResource(diag.locationLatitude!!, diag.locationLongitude!!)?.first
        } else {
            null
        }

        if (targetResource != null && selectedTrustedPlace == null && activeRoute == null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = targetResource.type.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = targetResource.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = targetResource.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                viewModel.startNavigationTo(targetResource.latitude, targetResource.longitude, targetResource.name)
                                selectedResource = null
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = "Directions",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${targetResource.phone}")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Call",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // 7. FLOATING TURN-BY-TURN ROAD NAVIGATION BANNER
        navProgress?.let { progress ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = "Maneuver",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = progress.currentInstruction,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${progress.remainingDistanceMeters.toInt()}m remaining · ETA ~${(progress.remainingDurationSeconds / 60).coerceAtLeast(1)} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.stopNavigation() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Stop Navigation",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 8. MAP LONG-PRESS ACTION DIALOG (Set Destination or Add Trusted Place)
        longPressedPoint?.let { pt ->
            AlertDialog(
                onDismissRequest = { longPressedPoint = null },
                title = { Text("Selected Map Location") },
                text = {
                    Column {
                        Text("Coordinates: %.5f, %.5f".format(pt.latitude, pt.longitude), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("What would you like to do with this location?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.startNavigationTo(pt.latitude, pt.longitude, "Selected Map Point")
                            longPressedPoint = null
                        }
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Navigate Here")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                addPlaceInitialLat = pt.latitude
                                addPlaceInitialLng = pt.longitude
                                longPressedPoint = null
                                showAddTrustedPlaceDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Trusted Place")
                        }
                        TextButton(onClick = { longPressedPoint = null }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }

        // 9. ADD TRUSTED PLACE DIALOG
        if (showAddTrustedPlaceDialog) {
            AddTrustedPlaceDialog(
                initialLat = addPlaceInitialLat,
                initialLng = addPlaceInitialLng,
                onDismiss = { showAddTrustedPlaceDialog = false },
                onConfirm = { name, type, lat, lng, radius ->
                    viewModel.addTrustedPlace(name, type, lat, lng, radius)
                    showAddTrustedPlaceDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Trusted place '$name' saved")
                    }
                }
            )
        }

        // Snackbar Host for status notifications
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )
    }
}

@Composable
fun AddTrustedPlaceDialog(
    initialLat: Double,
    initialLng: Double,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: TrustedPlaceType, lat: Double, lng: Double, radius: Float) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TrustedPlaceType.HOME) }
    var radius by remember { mutableFloatStateOf(100f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Trusted Place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Place Name") },
                    placeholder = { Text("e.g., Mom's Office, Campus Hostel") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Place Category:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TrustedPlaceType.values().forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName, fontSize = 12.sp) }
                        )
                    }
                }

                Text("Safe Boundary Radius: ${radius.toInt()} meters", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 50f..500f,
                    steps = 8
                )

                Text(
                    text = "Coordinates: %.5f, %.5f".format(initialLat, initialLng),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, selectedType, initialLat, initialLng, radius)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Creates a circular user puck with directional arrow representing real GPS bearing.
 */
private fun createUserLocationPuckDrawable(context: Context, hasBearing: Boolean): Drawable {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Radar halo ring
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x332A85FF
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, haloPaint)

    // Solid inner core circle
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A85FF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 3.2f, corePaint)

    // Crisp white stroke outline
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 3.2f, strokePaint)

    // Directional heading arrow if moving / bearing available
    if (hasBearing) {
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(size / 2f, size / 6f) // Tip pointing up
            lineTo(size / 2f + 8f, size / 2.2f)
            lineTo(size / 2f - 8f, size / 2.2f)
            close()
        }
        canvas.drawPath(path, arrowPaint)
    }

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Creates custom pin markers for Home, Trusted Places, and Safe Havens.
 */
private fun createThemedMarkerDrawable(context: Context, colorRgb: Int): Drawable {
    val width = 48
    val height = 64
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorRgb
        style = Paint.Style.FILL
    }

    // Teardrop pin shape
    val path = Path().apply {
        moveTo(width / 2f, height.toFloat())
        cubicTo(
            0f, height * 0.6f,
            0f, 0f,
            width / 2f, 0f
        )
        cubicTo(
            width.toFloat(), 0f,
            width.toFloat(), height * 0.6f,
            width / 2f, height.toFloat()
        )
        close()
    }
    canvas.drawPath(path, paint)

    // White inner core
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, height * 0.35f, width / 4.5f, innerPaint)

    return BitmapDrawable(context.resources, bitmap)
}
