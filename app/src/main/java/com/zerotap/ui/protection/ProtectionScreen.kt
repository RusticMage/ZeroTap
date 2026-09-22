package com.zerotap.ui.protection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Sensor Telemetry",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Live physical inputs & feature extraction from Nothing Phone (2a)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Pipeline Status Summary Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isRunning) "Pipeline Running" else "Pipeline Paused",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Rate: %.1f Hz · Total Samples: %d".format(diag.estimatedSamplingRateHz, diag.motionSampleCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RiskStateBadge(state = diag.currentRiskState)
                }
            }
        }

        // 2. ACCELEROMETER SECTION
        item {
            HospitableSectionHeader(title = "1. Accelerometer (Real Hardware)")
        }

        item {
            SensorValueCard(
                title = "Linear 3-Axis Acceleration",
                isAvailable = diag.isAccelerometerAvailable,
                isActive = isRunning,
                lines = listOf(
                    "X-Axis" to "%.3f m/s²".format(diag.accelX),
                    "Y-Axis" to "%.3f m/s²".format(diag.accelY),
                    "Z-Axis" to "%.3f m/s²".format(diag.accelZ),
                    "Euclidean Magnitude ||a||" to "%.3f m/s²".format(diag.accelMagnitude)
                )
            )
        }

        // 3. GYROSCOPE SECTION
        item {
            HospitableSectionHeader(title = "2. Gyroscope (Real Hardware)")
        }

        item {
            SensorValueCard(
                title = "Angular Velocity / Rotation Rate",
                isAvailable = diag.isGyroscopeAvailable,
                isActive = isRunning,
                lines = listOf(
                    "Roll (X)" to "%.3f rad/s".format(diag.gyroX),
                    "Pitch (Y)" to "%.3f rad/s".format(diag.gyroY),
                    "Yaw (Z)" to "%.3f rad/s".format(diag.gyroZ),
                    "Angular Magnitude ||ω||" to "%.3f rad/s".format(diag.gyroMagnitude)
                )
            )
        }

        // 4. PREPROCESSED ML FEATURE VECTORS
        item {
            HospitableSectionHeader(title = "3. Statistical Feature Extraction (For ML Model)")
        }

        item {
            SensorValueCard(
                title = "Rolling Window Kinematics (2-3s Window)",
                isAvailable = true,
                isActive = isRunning,
                lines = listOf(
                    "Mean Acceleration" to "%.2f m/s²".format(diag.meanAccelMagnitude),
                    "Peak Acceleration" to "%.2f m/s²".format(diag.peakAccelMagnitude),
                    "Variance (Energy)" to "%.3f".format(diag.varianceAccelMagnitude),
                    "Kinematic Jerk (da/dt)" to "%.2f m/s³".format(diag.jerkMagnitude),
                    "Motion Classification" to "${diag.motionClassification.displayName} (%.0f%%)".format(diag.motionConfidence * 100)
                )
            )
        }

        // 5. ACOUSTIC & LOCATION CONTEXT
        item {
            HospitableSectionHeader(title = "4. Context & Environment")
        }

        item {
            SensorValueCard(
                title = "Acoustic Monitor (On-Device RMS)",
                isAvailable = true,
                isActive = isRunning && diag.isAudioAvailable,
                lines = listOf(
                    "Microphone Status" to if (diag.isAudioRecording) "Listening locally" else "Idle",
                    "RMS Amplitude" to "%.1f dB".format(diag.audioAmplitudeDb),
                    "Audio Classification" to diag.audioClassification.displayName
                )
            )
        }

        item {
            SensorValueCard(
                title = "Fused Location Provider",
                isAvailable = true,
                isActive = isRunning && diag.isLocationAvailable,
                lines = listOf(
                    "Coordinates" to if (diag.locationLatitude != null) "%.4f, %.4f".format(diag.locationLatitude, diag.locationLongitude) else "Acquiring fix...",
                    "Accuracy" to if (diag.locationAccuracy != null) "±%.1f meters".format(diag.locationAccuracy) else "N/A",
                    "Speed" to if (diag.locationSpeed != null) "%.1f m/s".format(diag.locationSpeed) else "0.0 m/s"
                )
            )
        }

        // 6. LIVE RISK ENGINE CONTRIBUTING SIGNALS
        item {
            HospitableSectionHeader(title = "5. Live Risk Signals Stream")
        }

        if (recentSignals.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = if (isRunning) "No high-risk signals active. Phone in safe regime." else "Start protection on Home screen to observe live signals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(recentSignals.size) { idx ->
                val sig = recentSignals[idx]
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sig.description,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Engine Weight: +%.2f".format(sig.weight),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorValueCard(
    title: String,
    isAvailable: Boolean,
    isActive: Boolean,
    lines: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        !isAvailable -> "NOT FOUND"
                        isActive -> "STREAMING"
                        else -> "STANDBY"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            lines.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
