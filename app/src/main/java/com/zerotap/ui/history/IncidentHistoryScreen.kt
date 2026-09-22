package com.zerotap.ui.history

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zerotap.ui.components.RiskStateBadge
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IncidentHistoryScreen(
    navController: NavHostController,
    viewModel: IncidentHistoryViewModel = viewModel()
) {
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val dateFormat = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Incident History",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Encrypted local audit trail of detected anomalies",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (incidents.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Incidents Recorded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "When an anomalous threat is detected, full situational evidence will be encrypted and logged here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(incidents.size) { idx ->
                val inc = incidents[idx]
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                                text = dateFormat.format(Date(inc.createdAt)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RiskStateBadge(state = inc.riskState)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Risk Score: ${(inc.riskScore * 100).toInt()}% · Status: ${inc.status.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!inc.summary.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = inc.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
