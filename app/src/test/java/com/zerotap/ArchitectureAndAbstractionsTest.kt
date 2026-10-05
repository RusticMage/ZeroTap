package com.zerotap

import com.zerotap.ai.local.LocalAIProvider
import com.zerotap.ai.models.*
import com.zerotap.ai.service.AIServiceImpl
import com.zerotap.core.config.AppCapabilities
import com.zerotap.core.config.AppConfiguration
import com.zerotap.core.config.DeploymentMode
import com.zerotap.data.repository.NavigationRepositoryImpl
import com.zerotap.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArchitectureAndAbstractionsTest {

    @Test
    fun testDeploymentModeCapabilities() {
        val privateCaps = AppConfiguration.getCapabilities(DeploymentMode.PRIVATE)
        assertTrue(privateCaps.isLocalSafetyActive)
        assertTrue(privateCaps.isByokEnabled)
        assertFalse(privateCaps.isServerSyncEnabled)
        assertFalse(privateCaps.isRemoteMonitoringEnabled)
        assertFalse(privateCaps.isVertexAiEnabled)

        val serverCaps = AppConfiguration.getCapabilities(DeploymentMode.SERVER)
        assertTrue(serverCaps.isLocalSafetyActive) // Local safety logic is ALWAYS active
        assertFalse(serverCaps.isByokEnabled)
        assertTrue(serverCaps.isServerSyncEnabled)
        assertTrue(serverCaps.isRemoteMonitoringEnabled)
        assertTrue(serverCaps.isVertexAiEnabled)
    }

    @Test
    fun testNavigationTurnByTurnTrackingAndCompletion() = runBlocking {
        val navRepo = NavigationRepositoryImpl()
        val origin = RoutePoint(13.0827, 80.2707) // Central Chennai
        val dest = RoutePoint(13.0601, 80.2520)   // Thousand Lights
        val destName = "Thousand Lights Safe Space"

        val roadSteps = listOf(
            RouteStep(0, "Depart on Anna Salai", 800f, 60L, origin, RoutePoint(13.0750, 80.2650), ManeuverType.DEPART),
            RouteStep(1, "Turn slight right towards Thousand Lights", 1200f, 90L, RoutePoint(13.0750, 80.2650), dest, ManeuverType.ARRIVE)
        )
        val geometry = listOf(origin, RoutePoint(13.0750, 80.2650), dest)
        val testRoute = NavigationRoute(
            id = "test-route-1",
            origin = origin,
            destination = dest,
            destinationName = destName,
            totalDistanceMeters = 2000f,
            totalDurationSeconds = 150L,
            steps = roadSteps,
            geometryPoints = geometry,
            isOfflineCalculated = false
        )

        // Start Navigation
        navRepo.startNavigation(testRoute)
        assertTrue(navRepo.isNavigating())

        val initialProgress = navRepo.navigationProgress.value
        assertNotNull(initialProgress)
        assertEquals(0, initialProgress!!.currentStepIndex)
        assertFalse(initialProgress.destinationReached)

        // Simulate reaching destination within 10 meters
        navRepo.updateCurrentLocation(dest.latitude, dest.longitude, 45f, 5f)
        val finalProgress = navRepo.navigationProgress.value
        assertNotNull(finalProgress)
        assertTrue("Destination reached must be true", finalProgress!!.destinationReached)
        assertEquals(45f, finalProgress.currentBearingDegrees)

        navRepo.stopNavigation()
        assertFalse(navRepo.isNavigating())
    }

    @Test
    fun testLocalAIProviderAudioAndContextInference() = runBlocking {
        val localAi = LocalAIProvider()

        // 1. Audio Analysis
        val quietAudio = AudioInput(timestamp = 1000L, amplitudeDb = 40f)
        val quietContext = localAi.analyzeAudio(quietAudio)
        assertFalse(quietContext.distressLikePattern)
        assertFalse(quietContext.elevatedVocalEnergy)

        val distressAudio = AudioInput(timestamp = 2000L, amplitudeDb = 85f)
        val distressContext = localAi.analyzeAudio(distressAudio)
        assertTrue(distressContext.distressLikePattern)
        assertTrue(distressContext.elevatedVocalEnergy)

        // 2. Multimodal Context Inference
        val sensorContext = SensorContext(
            motion = MotionContext(
                timestamp = 3000L,
                peakAcceleration = 26f,
                meanAcceleration = 12f,
                jerkMagnitude = 55f,
                impactConfidence = 0.85f,
                fallConfidence = 0.1f,
                abruptMotionConfidence = 0.9f,
                stationaryConfidence = 0f,
                walkingConfidence = 0f,
                runningConfidence = 0f
            ),
            audio = distressContext,
            location = LocationContext(
                timestamp = 3000L,
                latitude = 13.0827,
                longitude = 80.2707,
                accuracy = 10f,
                speed = 0f,
                isMoving = false,
                isUnexpectedStop = true
            )
        )

        val inferred = localAi.inferContext(sensorContext)
        assertNotNull(inferred)
        assertTrue("Confidence must exceed safety threshold", inferred.confidence >= 0.70f)
        assertEquals("PHYSICAL_IMPACT", inferred.possibleEvent)

        // 3. Vehicle Plate Vision OCR
        val imgInput = ImageInput(
            imageUri = "file:///storage/DCIM/cabs/vehicle_TN07CB1234_capture.jpg",
            timestamp = 4000L
        )
        val vehicleEvidence = localAi.analyzeVehicleImage(imgInput)
        assertEquals("TN07CB1234", vehicleEvidence.plateNumber)
        assertTrue(vehicleEvidence.confidence >= 0.75f)
    }

    @Test
    fun testAIServiceDeterministicFallbackWhenOffline() = runBlocking {
        // In Private mode without BYOK key, AIServiceImpl falls back automatically to local engine
        AppConfiguration.currentMode = DeploymentMode.PRIVATE
        val localProvider = LocalAIProvider()
        val aiService = AIServiceImpl(localProvider = localProvider)

        val input = AudioInput(timestamp = 1000L, amplitudeDb = 82f)
        val result = aiService.analyzeAudio(input)

        assertNotNull(result)
        assertTrue(result.distressLikePattern)
    }

    @Test
    fun testEvidenceItemAndSyncStatusTransitions() {
        val item = EvidenceItem(
            id = "EV-100",
            type = EvidenceType.VEHICLE_PLATE,
            uri = "content://media/100",
            timestamp = System.currentTimeMillis(),
            latitude = 13.0827,
            longitude = 80.2707,
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        assertEquals(SyncStatus.LOCAL_ONLY, item.syncStatus)
        val pending = item.copy(syncStatus = SyncStatus.PENDING)
        assertEquals(SyncStatus.PENDING, pending.syncStatus)
        val synced = pending.copy(syncStatus = SyncStatus.SYNCED)
        assertEquals(SyncStatus.SYNCED, synced.syncStatus)
    }
}
