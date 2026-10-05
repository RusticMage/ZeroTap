package com.zerotap.ui.evidence

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerotap.ServiceLocator
import com.zerotap.core.config.AppConfiguration
import com.zerotap.core.config.DeploymentMode
import com.zerotap.domain.model.EvidenceItem
import com.zerotap.domain.model.EvidenceType
import com.zerotap.domain.model.SyncStatus
import com.zerotap.domain.repository.EvidenceRepository
import com.zerotap.domain.repository.LocationRepository
import com.zerotap.domain.repository.SyncRepository
import com.zerotap.domain.vision.ImageInput
import com.zerotap.domain.vision.VehiclePlateAnalyzer
import com.zerotap.domain.vision.normalizePlateNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class CaptureStep {
    object Idle : CaptureStep()
    object Analyzing : CaptureStep()
    data class Review(
        val imageFile: File,
        val detected: Boolean,
        val initialPlate: String,
        val confidence: Float
    ) : CaptureStep()
    data class Saved(
        val evidenceId: String,
        val registrationNumber: String,
        val synced: Boolean
    ) : CaptureStep()
    data class Error(val message: String) : CaptureStep()
}

class VehiclePlateViewModel(
    private val plateAnalyzer: VehiclePlateAnalyzer = ServiceLocator.vehiclePlateAnalyzer,
    private val evidenceRepository: EvidenceRepository = ServiceLocator.evidenceRepository,
    private val locationRepository: LocationRepository = ServiceLocator.locationRepository,
    private val syncRepository: SyncRepository = ServiceLocator.syncRepository
) : ViewModel() {

    private val _step = MutableStateFlow<CaptureStep>(CaptureStep.Idle)
    val step: StateFlow<CaptureStep> = _step.asStateFlow()

    private var pendingImageFile: File? = null

    /**
     * Creates a temporary image file in app-owned cache and returns its FileProvider content URI.
     */
    fun prepareImageCaptureUri(context: Context): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = File(context.cacheDir, "plates").apply { mkdirs() }
        val file = File(storageDir, "PLATE_${timeStamp}.jpg")
        pendingImageFile = file
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Invoked when image capture succeeds.
     */
    fun onImageCaptured() {
        val file = pendingImageFile ?: return
        if (!file.exists() || file.length() == 0L) {
            _step.value = CaptureStep.Error("No photo captured or file is empty")
            return
        }

        _step.value = CaptureStep.Analyzing
        viewModelScope.launch {
            try {
                val result = plateAnalyzer.analyze(ImageInput(file = file))
                val normalized = if (!result.registrationNumber.isNullOrBlank()) {
                    normalizePlateNumber(result.registrationNumber)
                } else {
                    ""
                }

                _step.value = CaptureStep.Review(
                    imageFile = file,
                    detected = result.plateDetected,
                    initialPlate = normalized,
                    confidence = result.confidence
                )
            } catch (e: Exception) {
                _step.value = CaptureStep.Error("Vision analysis failed: ${e.message}")
            }
        }
    }

    /**
     * Confirms and persists the vehicle plate evidence.
     * Extracts actual GPS coordinates from LocationRepository without inventing fake ones.
     */
    fun confirmAndSaveEvidence(
        editedPlate: String,
        confidence: Float
    ) {
        val currentReview = _step.value as? CaptureStep.Review ?: return
        val normalized = normalizePlateNumber(editedPlate)

        viewModelScope.launch {
            try {
                val evidenceId = "EV-${UUID.randomUUID().toString().take(8).uppercase()}"
                val now = System.currentTimeMillis()

                // Acquire current location if available - do NOT invent fake coordinates
                val loc = locationRepository.getCurrentLocationSample()
                val latitude = loc?.latitude
                val longitude = loc?.longitude

                // Determine initial sync status
                val initialSyncStatus = if (AppConfiguration.currentMode == DeploymentMode.SERVER) {
                    SyncStatus.PENDING
                } else {
                    SyncStatus.LOCAL_ONLY
                }

                val metadata = mapOf(
                    "registrationNumber" to normalized,
                    "confidence" to confidence.toString(),
                    "source" to "VEHICLE_PLATE_VISION"
                )

                val evidenceItem = EvidenceItem(
                    id = evidenceId,
                    type = EvidenceType.VEHICLE_PLATE,
                    uri = currentReview.imageFile.absolutePath,
                    timestamp = now,
                    latitude = latitude,
                    longitude = longitude,
                    metadata = metadata,
                    relevanceScore = confidence,
                    incidentId = null,
                    syncStatus = initialSyncStatus
                )

                evidenceRepository.saveEvidenceItem(evidenceItem)

                var wasSynced = false
                if (AppConfiguration.currentMode == DeploymentMode.SERVER) {
                    val syncResult = syncRepository.syncPendingData()
                    wasSynced = syncResult.isSuccess && (syncResult.getOrNull() ?: 0) > 0
                }

                _step.value = CaptureStep.Saved(
                    evidenceId = evidenceId,
                    registrationNumber = normalized,
                    synced = wasSynced
                )
            } catch (e: Exception) {
                _step.value = CaptureStep.Error("Failed to save evidence: ${e.message}")
            }
        }
    }

    fun retake() {
        pendingImageFile?.let {
            if (it.exists()) it.delete()
        }
        pendingImageFile = null
        _step.value = CaptureStep.Idle
    }
}
