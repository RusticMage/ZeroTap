package com.zerotap.core.config

/**
 * First-class architectural deployment mode for ZeroTap.
 *
 * PRIVATE: Operates 100% on-device or with user's own BYOK key. Zero communication with ZeroTap backend.
 * SERVER: Connects to Spring Boot backend for remote monitoring, cloud incident sync, and Vertex AI.
 */
enum class DeploymentMode(val displayName: String, val description: String) {
    PRIVATE(
        displayName = "Private / Self-Managed",
        description = "No ZeroTap server, maximum privacy, optional BYOK AI, offline-first operation."
    ),
    SERVER(
        displayName = "Connected / Server",
        description = "ZeroTap backend, remote monitoring, trusted contacts sync, Vertex AI incident analysis."
    )
}

/**
 * Capabilities enabled depending on DeploymentMode and local user configuration.
 * Centralized capability layer so mode checks are never scattered randomly.
 */
data class AppCapabilities(
    val deploymentMode: DeploymentMode,
    val isLocalSafetyActive: Boolean = true, // Core safety engine ALWAYS runs locally
    val isByokEnabled: Boolean = false,
    val isServerSyncEnabled: Boolean = deploymentMode == DeploymentMode.SERVER,
    val isRemoteMonitoringEnabled: Boolean = deploymentMode == DeploymentMode.SERVER,
    val isVertexAiEnabled: Boolean = deploymentMode == DeploymentMode.SERVER,
    val isOfflineMapAvailable: Boolean = true,
    val isOfflineNavigationAvailable: Boolean = true
)
