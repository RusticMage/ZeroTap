package com.zerotap.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zerotap.domain.model.AudioClassification
import com.zerotap.domain.model.RiskState
import com.zerotap.domain.model.SensorDiagnostics
import com.zerotap.ui.components.RiskStateBadge
import com.zerotap.ui.navigation.Screen
import com.zerotap.ui.theme.riskStateColor

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
    val prediction by viewModel.predictionResult.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val isEmergencyActive by viewModel.isEmergencyActive.collectAsStateWithLifecycle()
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val isAccidentActive by viewModel.isAccidentUserCheckActive.collectAsStateWithLifecycle()
    val accidentCountdown by viewModel.accidentCountdownSeconds.collectAsStateWithLifecycle()
    val primaryContact by viewModel.primaryContact.collectAsStateWithLifecycle()
    val activeSafetyPing by com.zerotap.service.ProtectionForegroundService.activeSafetyPing.collectAsStateWithLifecycle()

    LaunchedEffect(isAccidentActive) {
        if (isAccidentActive) {
            navController.navigate(Screen.AccidentConfirmation.route) { launchSingleTop = true }
        }
    }

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
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.toggleProtection(context, true)
        }
    }

    fun setProtection(enabled: Boolean) {
        if (!enabled) {
            viewModel.toggleProtection(context, false)
            return
        }
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) viewModel.toggleProtection(context, true)
        else permissionLauncher.launch(missing.toTypedArray())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("ZeroTap", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "A little more peace of mind, wherever you go.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isEmergencyActive) {
            item {
                UrgentNotice(
                    title = "Safety check in progress · $countdownSeconds seconds",
                    message = "Open the check-in to review or cancel it.",
                    onClick = { navController.navigate(Screen.EmergencyCountdown.route) }
                )
            }
        }
        if (isAccidentActive) {
            item {
                UrgentNotice(
                    title = "Are you okay? · $accidentCountdown seconds",
                    message = "Tap to let ZeroTap know you’re safe.",
                    onClick = { navController.navigate(Screen.AccidentConfirmation.route) }
                )
            }
        }

        // 1. PROTECTION TOGGLE (LARGE, ERGONOMIC HERO CARD)
        item {
            ProtectionSummaryCard(
                isActive = isProtectionActive,
                onToggle = { setProtection(!isProtectionActive) }
            )
        }

        // 2. QUICK ACTIONS (BIGGER BUTTONS / TOUCH TARGETS)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Capture Vehicle Plate Button
                Surface(
                    onClick = { navController.navigate(Screen.VehiclePlateCapture.route) },
                    modifier = Modifier.weight(1f).height(100.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🚗", fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                "Vehicle Plate",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Capture & OCR",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Emergency Contact Card
                Surface(
                    onClick = { navController.navigate(Screen.TrustedContacts.route) },
                    modifier = Modifier.weight(1f).height(100.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🛡️", fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                "Contacts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                primaryContact?.let { it.name.take(12) } ?: "Add Contact",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. ZERO TAP AI REASONING (TELL WHAT ZEROTAP IS THINKING RN)
        item {
            ZeroTapAiReasoningCard(
                isActive = isProtectionActive,
                riskState = riskState,
                riskScore = riskScore,
                diagnostics = diagnostics,
                predictionFactors = prediction?.contributingFactors.orEmpty()
            )
        }

        // 4. HARDWARE SENSORS CARD (WITH RED / GREEN STATUS CIRCLES)
        item {
            SensorDetailsCard(
                isActive = isProtectionActive,
                diagnostics = diagnostics
            )
        }

        // 5. RADAR LINK
        item {
            Surface(
                onClick = { navController.navigate(Screen.Protection.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Detailed Sensor Radar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Full kinematics, gyro rates & live telemetry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    activeSafetyPing?.let { ping ->
        AlertDialog(
            onDismissRequest = { /* Must actively choose */ },
            title = {
                Text("🚨 Safety Check: ${ping.contactName}")
            },
            text = {
                Column {
                    Text(
                        text = "Your emergency contact wants to know if you're safe.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap 'I'M OK' to confirm your safety and update their web dashboard immediately.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        com.zerotap.service.ProtectionForegroundService.acknowledgeActivePingSafe(ping.pingId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("I'M OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        com.zerotap.service.ProtectionForegroundService.triggerEmergencyNow()
                    }
                ) {
                    Text("TRIGGER SOS", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ZeroTapAiReasoningCard(
    isActive: Boolean,
    riskState: RiskState,
    riskScore: Float,
    diagnostics: SensorDiagnostics,
    predictionFactors: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🧠", fontSize = 20.sp)
                    Text(
                        text = "What ZeroTap is Thinking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                RiskStateBadge(state = riskState)
            }

            if (!isActive) {
                Text(
                    text = "Protection is in standby. Enable protection above to start real-time multi-sensor AI reasoning.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val reasoningText = when (riskState) {
                    RiskState.NORMAL -> "Status Steady: Normal physiological and environmental baselines. Accelerometer indicates stable gravity alignment (9.8 m/s²), acoustic noise is ambient, and GPS tracking shows expected transit."
                    RiskState.WATCH -> "Status Watch: Minor sensor fluctuation detected. Analyzing kinematic patterns to verify routine movement."
                    RiskState.SUSPICIOUS -> "Status Suspicious: Elevated anomalies detected across multiple sensor streams. Monitoring for escalating distress patterns."
                    RiskState.HIGH_RISK, RiskState.INCIDENT -> "Alert Condition: Critical kinematic shock or acoustic distress detected. Immediate verification required."
                    RiskState.RESOLVED -> "Incident Resolved: Situation normalized. Protection resumed."
                }

                Text(
                    text = reasoningText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "Why Sensors Are Baseline vs Elevated",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Motion & Kinematics Reason
                val isMotionElevated = diagnostics.peakAccelMagnitude > 18f || diagnostics.jerkMagnitude > 40f
                SensorReasonRow(
                    sensorName = "Motion Kinematics",
                    isElevated = isMotionElevated,
                    explanation = if (isMotionElevated) {
                        "Elevated: Abrupt acceleration spike detected (peak ${"%.1f".format(diagnostics.peakAccelMagnitude)} m/s²). Monitoring for impact or drop."
                    } else {
                        "Baseline: Steady motion (${"%.1f".format(diagnostics.accelMagnitude)} m/s²). No abrupt drops or crash patterns."
                    }
                )

                // 2. Audio Acoustic Reason
                val isAudioElevated = diagnostics.audioClassification == AudioClassification.DISTRESS_SOUND ||
                        diagnostics.audioClassification == AudioClassification.LOUD_ACOUSTIC_EVENT ||
                        diagnostics.audioClassification == AudioClassification.SHOUTING ||
                        predictionFactors.any { it.contains("acoustic", ignoreCase = true) || it.contains("vocal", ignoreCase = true) }
                SensorReasonRow(
                    sensorName = "Acoustic Audio",
                    isElevated = isAudioElevated,
                    explanation = if (isAudioElevated) {
                        "Elevated: Acoustic anomaly detected (${diagnostics.audioClassification.displayName}, ${"%.0f".format(diagnostics.audioAmplitudeDb)} dB). Evaluating vocal distress signatures."
                    } else {
                        "Baseline: Ambient acoustic floor (${diagnostics.audioClassification.displayName}, ${"%.0f".format(diagnostics.audioAmplitudeDb)} dB). Dynamic baseline tracking active."
                    }
                )

                // 3. Location & Transit Reason
                val isLocationElevated = predictionFactors.any {
                    it.contains("stop", ignoreCase = true) || it.contains("halt", ignoreCase = true) || it.contains("transit", ignoreCase = true)
                }
                SensorReasonRow(
                    sensorName = "GPS & Transit",
                    isElevated = isLocationElevated,
                    explanation = if (isLocationElevated) {
                        "Elevated: Unexpected prolonged halt detected while in transit. Monitoring location safety."
                    } else {
                        "Baseline: Navigation fix active. Speed & bearing consistent with expected movement."
                    }
                )

                if (predictionFactors.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = "Key Contributing Factors",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    predictionFactors.take(3).forEach { factor ->
                        Text(
                            text = "• $factor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorReasonRow(
    sensorName: String,
    isElevated: Boolean,
    explanation: String
) {
    val indicatorColor = if (isElevated) Color(0xFFFFA000) else Color(0xFF4CAF50)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = sensorName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProtectionSummaryCard(isActive: Boolean, onToggle: () -> Unit) {
    val accent = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isActive) accent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isActive) "Protection Active" else "Protection Standby",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isActive) "Continuous zero-touch monitoring enabled" else "Tap below to activate automated guard",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    contentColor = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isActive) "Turn Protection Off" else "Turn Protection On",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun UrgentNotice(title: String, message: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun StatusSummaryCard(
    isActive: Boolean,
    riskState: RiskState,
    riskScore: Float,
    predictionFactors: List<String>,
    recentSignals: List<String>,
    durationSeconds: Long
) {
    val statusColor = if (isActive) riskStateColor(riskState) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
                Text(
                    if (!isActive) "Waiting for you to turn protection on" else when (riskState) {
                        RiskState.NORMAL -> "Everything looks steady"
                        RiskState.WATCH -> "A small change is being checked"
                        RiskState.SUSPICIOUS -> "A few changes are being checked"
                        RiskState.HIGH_RISK -> "Something unusual needs attention"
                        RiskState.INCIDENT -> "Safety check needed"
                        RiskState.RESOLVED -> "Check complete"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
            if (isActive) {
                LinearProgressIndicator(
                    progress = { riskScore.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text("Current activity level: ${(riskScore * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                if (durationSeconds > 2) Text("This has been showing for $durationSeconds seconds.", style = MaterialTheme.typography.bodySmall)
                if (predictionFactors.isNotEmpty()) {
                    Text("What ZeroTap noticed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    predictionFactors.take(3).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (recentSignals.isNotEmpty()) {
                    Text("Recent sensor signals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    recentSignals.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            } else {
                Text("Turn protection on to see your current status.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SensorDetailsCard(isActive: Boolean, diagnostics: com.zerotap.domain.model.SensorDiagnostics) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Hardware Sensors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SensorDetailRow(
                name = "Accelerometer",
                value = if (!isActive) "Inactive" else diagnostics.motionClassification.displayName,
                detail = if (isActive) "Peak ${"%.1f".format(diagnostics.peakAccelMagnitude)} m/s² · Rate ${"%.0f".format(diagnostics.estimatedSamplingRateHz)} Hz" else "Kinematic pattern detection",
                isActive = isActive && diagnostics.isAccelerometerAvailable
            )
            HorizontalDivider()
            SensorDetailRow(
                name = "Gyroscope",
                value = if (isActive && diagnostics.isGyroscopeAvailable) "Active" else "Inactive",
                detail = if (isActive) "Rotation rate ||ω||: ${"%.2f".format(diagnostics.gyroMagnitude)} rad/s" else "Angular velocity & orientation",
                isActive = isActive && diagnostics.isGyroscopeAvailable
            )
            HorizontalDivider()
            SensorDetailRow(
                name = "Acoustic Audio",
                value = if (isActive && diagnostics.isAudioRecording) "Active" else "Inactive",
                detail = if (isActive && diagnostics.isAudioRecording) "${"%.1f".format(diagnostics.audioAmplitudeDb)} dB · On-device processing" else "Ambient noise & anomaly monitoring",
                isActive = isActive && diagnostics.isAudioRecording
            )
            HorizontalDivider()
            SensorDetailRow(
                name = "GPS Location",
                value = if (isActive && diagnostics.isLocationAvailable) "Active" else "Inactive",
                detail = if (isActive && diagnostics.locationAccuracy != null) "Fix: ±${"%.0f".format(diagnostics.locationAccuracy)}m · Speed: ${"%.1f".format(diagnostics.locationSpeed ?: 0f)} m/s" else "Real-time navigation & tracking",
                isActive = isActive && diagnostics.isLocationAvailable
            )
        }
    }
}

@Composable
private fun SensorDetailRow(name: String, value: String, detail: String, isActive: Boolean) {
    val indicatorColor = if (isActive) Color(0xFF4CAF50) else Color(0xFFE53935) // Vibrant Green or Red

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = indicatorColor
                )
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
