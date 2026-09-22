package com.zerotap.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.zerotap.ui.contacts.TrustedContactsScreen
import com.zerotap.ui.debug.DebugDashboardScreen
import com.zerotap.ui.emergency.EmergencyCountdownScreen
import com.zerotap.ui.history.IncidentHistoryScreen
import com.zerotap.ui.home.HomeScreen
import com.zerotap.ui.incident.ActiveIncidentScreen
import com.zerotap.ui.map.MapScreen
import com.zerotap.ui.protection.ProtectionScreen
import com.zerotap.ui.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    appContext: Context
) {
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // Hide bottom bar during high-priority emergency countdown
            if (currentRoute != Screen.EmergencyCountdown.route) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentRoute == Screen.Home.route,
                        onClick = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = "History") },
                        label = { Text("History") },
                        selected = currentRoute == Screen.IncidentHistory.route,
                        onClick = {
                            navController.navigate(Screen.IncidentHistory.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Place, contentDescription = "Map") },
                        label = { Text("Map") },
                        selected = currentRoute == Screen.Map.route,
                        onClick = {
                            navController.navigate(Screen.Map.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = "Contacts") },
                        label = { Text("Contacts") },
                        selected = currentRoute == Screen.TrustedContacts.route,
                        onClick = {
                            navController.navigate(Screen.TrustedContacts.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentRoute == Screen.Settings.route,
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen(navController) }
            composable(Screen.Protection.route) { ProtectionScreen(navController) }
            composable(Screen.IncidentHistory.route) { IncidentHistoryScreen(navController) }
            composable(Screen.Map.route) { MapScreen(navController) }
            composable(Screen.TrustedContacts.route) { TrustedContactsScreen(navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController) }
            composable(Screen.DebugDashboard.route) { DebugDashboardScreen(navController) }
            composable(Screen.ActiveIncident.route) { ActiveIncidentScreen(navController) }
            composable(Screen.EmergencyCountdown.route) { EmergencyCountdownScreen(navController) }
        }
    }
}
