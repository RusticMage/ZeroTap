package com.zerotap.ui.incident

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zerotap.ui.components.HospitableSectionHeader
import com.zerotap.ui.components.RiskStateBadge

@Composable
fun ActiveIncidentScreen(
    navController: NavHostController,
    viewModel: ActiveIncidentViewModel = viewModel()
) {
    val incident by viewModel.currentIncident.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Active Incident",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Automatic zero-user-action emergency assessment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        incident?.let { inc ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "INCIDENT THREAT LEVEL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RiskStateBadge(state = inc.riskState)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Risk Score: ${(inc.riskScore * 100).toInt()} / 100",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = inc.summary ?: "Local AI summary being generated on-device...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                HospitableSectionHeader(title = "Evidence & Safeguards")
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Black Box Evidence Package",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Status: Encrypted with Android Keystore AES-GCM",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Incident ID: ${inc.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.dismissIncident() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Dismiss (False Positive)")
                    }
                    Button(
                        onClick = { viewModel.resolveIncident() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("I'm Safe (Resolve)")
                    }
                }
            }
        } ?: run {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Active Incident",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your device context is currently safe.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
