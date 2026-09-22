package com.zerotap.ui.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.preference.PreferenceManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zerotap.data.safety.SafetyResource
import com.zerotap.data.safety.SafetyResourceType
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

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

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var selectedResource by remember { mutableStateOf<SafetyResource?>(null) }

    // Initialize osmdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val hasGpsFix = diag.locationLatitude != null && diag.locationLongitude != null
    // Fallback default center is Chennai center (13.0827, 80.2707) ONLY for camera positioning, NEVER plotted as user location
    val centerLat = if (hasGpsFix) diag.locationLatitude!! else 13.0827
    val centerLng = if (hasGpsFix) diag.locationLongitude!! else 80.2707

    val filteredResources = remember(selectedFilter, viewModel.emergencyResources) {
        if (selectedFilter == null) {
            viewModel.emergencyResources
        } else {
            viewModel.emergencyResources.filter { it.type == selectedFilter }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. OSMDroid MapView
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(13.5)
                    controller.setCenter(GeoPoint(centerLat, centerLng))
                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                // A. Current GPS Location Marker (ONLY added if real GPS fix exists - P12)
                if (hasGpsFix) {
                    val userLocation = GeoPoint(diag.locationLatitude!!, diag.locationLongitude!!)
                    val userMarker = Marker(mapView).apply {
                        position = userLocation
                        title = "Current Location"
                        snippet = "Accuracy: ±%.1fm · Speed: %.1f m/s".format(diag.locationAccuracy ?: 0f, diag.locationSpeed ?: 0f)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapView.overlays.add(userMarker)
                }

                // B. Home Location Marker (ONLY added if user has saved home - P8)
                val home = homeCoords
                if (home != null) {
                    val homeMarker = Marker(mapView).apply {
                        position = GeoPoint(home.first, home.second)
                        title = "Home Waypoint"
                        snippet = "Configured safe zone"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapView.overlays.add(homeMarker)
                }

                // C. Emergency Resources Markers (Police, Hospitals, Metro - P11)
                filteredResources.forEach { res ->
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(res.latitude, res.longitude)
                        title = res.name
                        snippet = "${res.type.displayName} · ${res.phone}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            selectedResource = res
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. TOP OVERLAY CONTROLS (Status, Filter Chips, Distance to Home)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter)
        ) {
            // Header Card with genuine status labeling (P9)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                                text = "Chennai Safety & Resource Map",
                                style = MaterialTheme.typography.titleMedium,
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

                    // Set Home Button (P24, P25)
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

            // Distance to Home banner (P25)
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

                    Spacer(modifier = Modifier.height(6.dp))
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

            Spacer(modifier = Modifier.height(8.dp))

            // Resource Filter Chips (Horizontal Scroll)
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

        // 3. BOTTOM OVERLAY: SELECTED OR NEAREST RESOURCE CARD
        val targetResource = selectedResource ?: if (hasGpsFix) {
            viewModel.getNearestResource(diag.locationLatitude!!, diag.locationLongitude!!)?.first
        } else {
            filteredResources.firstOrNull()
        }

        if (targetResource != null) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = targetResource.type.displayName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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

        // Snackbar Host for status notifications
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )
    }
}
