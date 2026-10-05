package com.zerotap.ui.protection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zerotap.domain.model.SensorDiagnostics
import com.zerotap.ui.components.HospitableSectionHeader
import com.zerotap.ui.components.RiskStateBadge

@Composable
fun ProtectionScreen(
    navController: NavHostController,
    viewModel: ProtectionViewModel = viewModel()
) {
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val diag by viewModel.diagnostics.collectAsStateWithLifecycle()
    val recentSignals by viewModel.recentSignals.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Modern Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Protection Radar",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isRunning) "All sensors actively monitoring" else "Protection paused",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RiskStateBadge(state = diag.currentRiskState)
            }
        }

        // Live Sensors Grid/Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Hardware Sensors",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    SensorTelemetryRow(
                        name = "Accelerometer",
                        isActive = isRunning && diag.isAccelerometerAvailable,
                        primaryVal = "%.2f m/s²".format(diag.accelMagnitude),
                        subVal = "${diag.motionClassification.displayName} · ${(diag.motionConfidence * 100).toInt()}%"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SensorTelemetryRow(
                        name = "Gyroscope",
                        isActive = isRunning && diag.isGyroscopeAvailable,
                        primaryVal = "%.2f rad/s".format(diag.gyroMagnitude),
                        subVal = "Roll: %.1f · Pitch: %.1f · Yaw: %.1f".format(diag.gyroX, diag.gyroY, diag.gyroZ)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SensorTelemetryRow(
                        name = "Acoustic Audio",
                        isActive = isRunning && diag.isAudioRecording,
                        primaryVal = "%.1f dB".format(diag.audioAmplitudeDb),
                        subVal = diag.audioClassification.displayName
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SensorTelemetryRow(
                        name = "GPS Location",
                        isActive = isRunning && diag.isLocationAvailable,
                        primaryVal = if (diag.locationLatitude != null) "%.4f, %.4f".format(diag.locationLatitude, diag.locationLongitude) else "Fix pending",
                        subVal = if (diag.locationAccuracy != null) "±%.1fm · %.1f m/s".format(diag.locationAccuracy, diag.locationSpeed ?: 0f) else "Acquiring satellites"
                    )
                }
            }
        }

        // Kinematic Analysis Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Kinematic Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricBadge(label = "Peak Accel", value = "%.1f m/s²".format(diag.peakAccelMagnitude))
                        MetricBadge(label = "Jerk (da/dt)", value = "%.1f m/s³".format(diag.jerkMagnitude))
                        MetricBadge(label = "Sampling", value = "%.0f Hz".format(diag.estimatedSamplingRateHz))
                    }
                }
            }
        }

        // Live Risk Signals
        if (recentSignals.isNotEmpty()) {
            item {
                Text(
                    "Active Risk Signals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(recentSignals.size) { idx ->
                val sig = recentSignals[idx]
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sig.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "+%.2f".format(sig.weight),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorTelemetryRow(
    name: String,
    isActive: Boolean,
    primaryVal: String,
    subVal: String
) {
    val indicatorColor = if (isActive) androidx.compose.ui.graphics.Color(0xFF4CAF50) else androidx.compose.ui.graphics.Color(0xFFE53935)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Prominent Status Circle: Green if Active, Red if Inactive
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subVal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = primaryVal,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MetricBadge(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
