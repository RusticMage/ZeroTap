package com.zerotap.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
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
import com.zerotap.alert.SmsDeliveryResult
import com.zerotap.ui.components.HospitableSectionHeader
import com.zerotap.ui.navigation.Screen
import com.zerotap.ui.theme.ZtHighRiskRust
import com.zerotap.ui.theme.ZtWatchAmber
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val demoEmergencyCall by viewModel.demoEmergencyCall.collectAsStateWithLifecycle()
    val contacts by viewModel.trustedContacts.collectAsStateWithLifecycle()
    val isProtectionActive by viewModel.isProtectionActive.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var isSendingTestSms by remember { mutableStateOf(false) }
    var testSmsResult by remember { mutableStateOf<SmsDeliveryResult?>(null) }
    var showTestDialog by remember { mutableStateOf(false) }
    var showCallPermissionRationale by remember { mutableStateOf(false) }

    // Runtime permission launcher for SEND_SMS
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                isSendingTestSms = true
                val res = viewModel.sendTestSms()
                isSendingTestSms = false
                testSmsResult = res
                showTestDialog = true
            }
        } else {
            testSmsResult = SmsDeliveryResult.Failed("SEND_SMS permission was denied. Cannot dispatch SMS.")
            showTestDialog = true
        }
    }

    // Runtime permission launcher for CALL_PHONE
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleDemoEmergencyCall(true)
        } else {
            viewModel.toggleDemoEmergencyCall(false)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Appearance, emergency workflows, and demo safety",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ==========================================
        // 1. APPEARANCE SECTION
        // ==========================================
        item {
            HospitableSectionHeader(title = "Appearance")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Theme Preference",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select light, dark (#161412), or follow system default",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (mode, label) ->
                            val selected = themeMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. PROTECTION SECTION
        // ==========================================
        item {
            HospitableSectionHeader(title = "Protection Pipeline")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 1.dp
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
                            text = "Background Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isProtectionActive) "Running continuously at 1000ms intervals" else "Paused. Enable on Home screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isProtectionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3. EMERGENCY SECTION
        // ==========================================
        item {
            HospitableSectionHeader(title = "Emergency Response")
        }

        item {
            Surface(
                onClick = { navController.navigate(Screen.TrustedContacts.route) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
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
                            text = "Trusted Contacts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val primary = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()
                        Text(
                            text = if (primary != null) "Primary: ${primary.name} (${primary.phone})" else "${contacts.size} contacts configured (No contact set)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("MANAGE >", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ==========================================
        // 4. PRIVACY SECTION
        // ==========================================
        item {
            HospitableSectionHeader(title = "Privacy & Safety")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "100% On-Device Processing Guarantee",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Raw accelerometer, gyroscope, and microphone data are computed strictly in-memory on your phone. No raw sensor streams are ever uploaded to any cloud server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ==========================================
        // 5. DEVELOPER & DEMO SETTINGS (Visually Separated)
        // ==========================================
        item {
            HospitableSectionHeader(title = "Developer & Demo Settings")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ZtWatchAmber.copy(alpha = 0.5f)),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // DEMO EMERGENCY CALL TOGGLE (CRITICAL SAFETY GUARD)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Demo Emergency Call",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (demoEmergencyCall) ZtHighRiskRust else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (demoEmergencyCall) ZtHighRiskRust.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (demoEmergencyCall) "REAL CALL ON" else "SIMULATED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (demoEmergencyCall) ZtHighRiskRust else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "OFF: emergency call is simulated only.\nON: the app can place the configured emergency call.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = demoEmergencyCall,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        viewModel.toggleDemoEmergencyCall(true)
                                    } else {
                                        showCallPermissionRationale = true
                                    }
                                } else {
                                    viewModel.toggleDemoEmergencyCall(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ZtHighRiskRust
                            )
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // TEST EMERGENCY CONTACT BUTTON (P2 & P18 Independent Test)
                    OutlinedButton(
                        onClick = {
                            val hasSmsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                            if (!hasSmsPerm) {
                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            } else {
                                scope.launch {
                                    isSendingTestSms = true
                                    val res = viewModel.sendTestSms()
                                    isSendingTestSms = false
                                    testSmsResult = res
                                    showTestDialog = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSendingTestSms
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSendingTestSms) "Sending Test SMS..." else "Test Emergency Contact SMS",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // OPEN SYNTHETIC TEST HARNESS / CONSOLE
                    FilledTonalButton(
                        onClick = { navController.navigate(Screen.DebugDashboard.route) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Open Developer Simulation Console", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // TEST SMS CONFIRMATION DIALOG
    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = {
                Text(
                    text = when (testSmsResult) {
                        is SmsDeliveryResult.Success -> "SMS SENT"
                        is SmsDeliveryResult.PermissionRequired -> "SMS Permission Required"
                        else -> "SMS FAILED"
                    }
                )
            },
            text = {
                Text(
                    text = when (val res = testSmsResult) {
                        is SmsDeliveryResult.Success -> {
                            "Verification message successfully dispatched to ${res.recipient}:\n\n\"ZeroTap DEMO TEST — no emergency has been detected.\""
                        }
                        is SmsDeliveryResult.PermissionRequired -> {
                            "SEND_SMS permission is required to send alerts to your trusted contact. Please grant it to continue."
                        }
                        is SmsDeliveryResult.Failed -> {
                            "Failed to send SMS: ${res.reason}"
                        }
                        null -> ""
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (testSmsResult is SmsDeliveryResult.PermissionRequired) {
                            showTestDialog = false
                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                        } else {
                            showTestDialog = false
                        }
                    }
                ) {
                    Text(if (testSmsResult is SmsDeliveryResult.PermissionRequired) "Grant Permission" else "OK")
                }
            }
        )
    }

    // CALL_PHONE RATIONALE DIALOG
    if (showCallPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showCallPermissionRationale = false },
            title = { Text("Emergency Call Permission") },
            text = {
                Text("Enabling Real Emergency Call permits ZeroTap to place a direct phone call to 112 when an emergency event is confirmed. Android requires the Phone permission for this action.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCallPermissionRationale = false
                        callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallPermissionRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
