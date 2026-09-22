package com.zerotap.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Protection : Screen("protection")
    object Map : Screen("map")
    object IncidentHistory : Screen("incident_history")
    object TrustedContacts : Screen("trusted_contacts")
    object Settings : Screen("settings")
    object DebugDashboard : Screen("debug_dashboard")
    object ActiveIncident : Screen("active_incident")
    object EmergencyCountdown : Screen("emergency_countdown")
}
