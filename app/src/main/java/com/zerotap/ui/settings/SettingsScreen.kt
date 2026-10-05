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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val deploymentMode by viewModel.deploymentMode.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var isSendingTestSms by remember { mutableStateOf(false) }
    var testSmsResult by remember { mutableStateOf<SmsDeliveryResult?>(null) }
    var showTestDialog by remember { mutableStateOf(false) }
    var showCallPermissionRationale by remember { mutableStateOf(false) }
    var showByokDialog by remember { mutableStateOf(false) }
    var byokKeyInput by remember { mutableStateOf("") }

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
        // 1. APPEARANCE
        // ==========================================
        item {
            HospitableSectionHeader(title = "Appearance")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Theme Preference",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
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
        // 2. NETWORK & DEPLOYMENT
        // ==========================================
        item {
            HospitableSectionHeader(title = "Network & Cloud Sync")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Deployment Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = deploymentMode.displayName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = deploymentMode == com.zerotap.core.config.DeploymentMode.PRIVATE,
                            onClick = { viewModel.setDeploymentMode(com.zerotap.core.config.DeploymentMode.PRIVATE) },
                            label = { Text("Private") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = deploymentMode == com.zerotap.core.config.DeploymentMode.SERVER,
                            onClick = { viewModel.setDeploymentMode(com.zerotap.core.config.DeploymentMode.SERVER) },
                            label = { Text("Connected") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (deploymentMode == com.zerotap.core.config.DeploymentMode.PRIVATE) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AI API Key",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (viewModel.hasByokKey()) "Key configured" else "Using default model",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showByokDialog = true }) {
                                Text(if (viewModel.hasByokKey()) "Update" else "Add Key")
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. EMERGENCY CONTACTS
        // ==========================================
        item {
            HospitableSectionHeader(title = "Emergency Contacts")
        }

        item {
            Surface(
                onClick = { navController.navigate(Screen.TrustedContacts.route) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        val primary = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()
                        Text(
                            text = if (primary != null) "${primary.name} · ${primary.phone}" else "No contacts configured",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${contacts.size} contact(s) enrolled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "Manage >",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ==========================================
        // 4. TESTING & SIMULATION
        // ==========================================
        item {
            HospitableSectionHeader(title = "Testing & Diagnostics")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Emergency Call",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (demoEmergencyCall) "Calls 112 directly on confirmation" else "Simulation mode only",
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
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSendingTestSms
                        ) {
                            Text(if (isSendingTestSms) "Sending..." else "Send Test SMS")
                        }

                        FilledTonalButton(
                            onClick = { navController.navigate(Screen.DebugDashboard.route) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Diagnostics")
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. ABOUT
        // ==========================================
        item {
            HospitableSectionHeader(title = "About")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("ZT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Column {
                            Text("ZeroTap", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text(
                        "Zero-interaction personal safety system with real-time sensor anomaly detection and automated emergency escalation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

    // BYOK API KEY VAULT DIALOG
    if (showByokDialog) {
        AlertDialog(
            onDismissRequest = { showByokDialog = false },
            title = { Text("BYOK AI API Key Vault") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your private AI API key (OpenAI, Gemini, Claude). The key is hardware-encrypted via Android KeyStore (AES-GCM-256) and never sent to ZeroTap servers.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = byokKeyInput,
                        onValueChange = { byokKeyInput = it },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (byokKeyInput.isNotBlank()) {
                            viewModel.saveByokKey(byokKeyInput.trim())
                        }
                        showByokDialog = false
                    }
                ) {
                    Text("Save Key")
                }
            },
            dismissButton = {
                if (viewModel.hasByokKey()) {
                    TextButton(
                        onClick = {
                            viewModel.clearByokKey()
                            showByokDialog = false
                        }
                    ) {
                        Text("Delete Key", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { showByokDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
