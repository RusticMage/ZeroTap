package com.zerotap.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zerotap.domain.model.RiskState
import com.zerotap.ui.components.*
import com.zerotap.ui.navigation.Screen

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val isProtectionActive by viewModel.protectionEnabled.collectAsStateWithLifecycle()
    val riskState by viewModel.currentRiskState.collectAsStateWithLifecycle()
    val riskScore by viewModel.riskScore.collectAsStateWithLifecycle()
    val recentSignals by viewModel.recentSignals.collectAsStateWithLifecycle()
    val isEmergencyActive by viewModel.isEmergencyActive.collectAsStateWithLifecycle()
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val prediction by viewModel.predictionResult.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val isAccidentActive by viewModel.isAccidentUserCheckActive.collectAsStateWithLifecycle()
    val accidentCountdown by viewModel.accidentCountdownSeconds.collectAsStateWithLifecycle()
    val primaryContact by viewModel.primaryContact.collectAsStateWithLifecycle()

    // Auto navigate to accident confirmation when vehicle impact is detected
    LaunchedEffect(isAccidentActive) {
        if (isAccidentActive) {
            navController.navigate(Screen.AccidentConfirmation.route) {
                launchSingleTop = true
            }
        }
    }

    // Required runtime permissions handling
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    var hasRequestedPermissions by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineLocationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineLocationGranted) {
            viewModel.toggleProtection(context, true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ZeroTap",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Autonomous personal safety context",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Quick sensor health indicator
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (diagnostics.isAccelerometerAvailable) "Nothing (2a) Sensors" else "Sensors Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Active Incident Alert / Emergency Countdown Banner
        if (isEmergencyActive) {
            item {
                Surface(
                    onClick = { navController.navigate(Screen.EmergencyCountdown.route) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.error,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "EMERGENCY COUNTDOWN ($countdownSeconds s)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap now to review, cancel, or dispatch response",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        // Active Vehicle Accident Check Banner
        if (isAccidentActive) {
            item {
                Surface(
                    onClick = { navController.navigate(Screen.AccidentConfirmation.route) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.error,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Accident Alert",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "POSSIBLE VEHICLE ACCIDENT ($accidentCountdown s)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grace period active. Tap now to confirm you are fine.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        // 1. Central Live Risk Visualization Card with Explainability
        item {
            LiveRiskMeterCard(
                state = riskState,
                score = riskScore,
                isProtectionActive = isProtectionActive,
                prediction = prediction
            )
        }

        // 2. Protection Toggle Card
        item {
            HospitableProtectionCard(
                isEnabled = isProtectionActive,
                onToggle = { enable ->
                    if (enable) {
                        // Check permissions first
                        val missing = requiredPermissions.filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isNotEmpty()) {
                            permissionLauncher.launch(missing.toTypedArray())
                        } else {
                            viewModel.toggleProtection(context, true)
                        }
                    } else {
                        viewModel.toggleProtection(context, false)
                    }
                }
            )
        }

        // Unified Safety Status Card (Active, Sensors, Modes, Contact, Auto Escalation)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Unified Safety Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    UnifiedStatusRow(
                        label = "ZeroTap Protection",
                        value = if (isProtectionActive) "Active" else "Standby",
                        isHighlight = isProtectionActive
                    )
                    UnifiedStatusRow(
                        label = "Sensors",
                        value = "✓ Motion  ✓ Audio  ✓ Location",
                        isHighlight = isProtectionActive
                    )
                    UnifiedStatusRow(
                        label = "Protection Modes",
                        value = "✓ Personal Safety  ✓ Vehicle Accident",
                        isHighlight = isProtectionActive
                    )
                    UnifiedStatusRow(
                        label = "Emergency Contact",
                        value = primaryContact?.let { "${it.name} (${it.phone})" } ?: "None configured",
                        isHighlight = primaryContact != null
                    )
                    UnifiedStatusRow(
                        label = "Automatic Escalation",
                        value = "ENABLED (Zero-Tap)",
                        isHighlight = true
                    )
                }
            }
        }

        // 3. Sensor Pipeline Status Section
        item {
            HospitableSectionHeader(title = "Hardware Subsystems")
        }

        item {
            HospitableSensorTile(
                name = "Kinematics & Motion",
                statusText = if (isProtectionActive) "Active" else "Idle",
                isActive = isProtectionActive,
                isAvailable = diagnostics.isAccelerometerAvailable,
                detailText = if (isProtectionActive) {
                    "Peak: %.1f m/s² · Jerk: %.1f · %s".format(
                        diagnostics.peakAccelMagnitude,
                        diagnostics.jerkMagnitude,
                        diagnostics.motionClassification.displayName
                    )
                } else "Accelerometer & Gyroscope ready"
            )
        }

        item {
            HospitableSensorTile(
                name = "Acoustic Context",
                statusText = if (isProtectionActive && diagnostics.isAudioAvailable) "Active" else "Idle",
                isActive = isProtectionActive && diagnostics.isAudioAvailable,
                isAvailable = true,
                detailText = if (isProtectionActive && diagnostics.isAudioRecording) {
                    "Ambient level: %.1f dB · Local processing only".format(diagnostics.audioAmplitudeDb)
                } else "On-device amplitude monitor ready"
            )
        }

        item {
            HospitableSensorTile(
                name = "Fused Location",
                statusText = if (isProtectionActive && diagnostics.isLocationAvailable) "Active" else "Idle",
                isActive = isProtectionActive && diagnostics.isLocationAvailable,
                isAvailable = true,
                detailText = if (isProtectionActive && diagnostics.locationAccuracy != null) {
                    "Accuracy: ±%.1fm · Speed: %.1f m/s".format(
                        diagnostics.locationAccuracy ?: 0f,
                        diagnostics.locationSpeed ?: 0f
                    )
                } else "Anomaly & unexpected stop context"
            )
        }

        // Quick Jump to Live Diagnostics / Sensor Dashboard
        item {
            Surface(
                onClick = { navController.navigate(Screen.Protection.route) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Live Sensor Dashboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Inspect raw X/Y/Z telemetry & ML feature vectors",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "VIEW >",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedStatusRow(label: String, value: String, isHighlight: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

