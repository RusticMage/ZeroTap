package com.zerotap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zerotap.MainActivity
import com.zerotap.sensor.audio.AudioInferenceEngine
import com.zerotap.ai.audio.DevelopmentAudioInferenceEngine
import com.zerotap.ai.llm.DevelopmentIncidentSummarizer
import com.zerotap.ai.llm.LocalIncidentSummarizer
import com.zerotap.ai.motion.DevelopmentMotionInferenceEngine
import com.zerotap.alert.AlertManager
import com.zerotap.alert.InternetAlertTransport
import com.zerotap.alert.SmsAlertTransport
import com.zerotap.alert.placeholder.BluetoothRelayTransport
import com.zerotap.alert.placeholder.WifiDirectRelayTransport
import com.zerotap.data.datastore.UserPreferences
import com.zerotap.data.db.ZeroTapDatabase
import com.zerotap.data.repository.IncidentRepository
import com.zerotap.data.safety.ChennaiSafetyDatabase
import com.zerotap.domain.incident.IncidentManager
import com.zerotap.domain.model.*
import com.zerotap.domain.response.ResponseManager
import com.zerotap.domain.risk.DevelopmentRiskEngine
import com.zerotap.domain.risk.DevelopmentRiskPredictionEngine
import com.zerotap.domain.risk.RiskEngine
import com.zerotap.domain.risk.RiskPredictionEngine
import com.zerotap.domain.risk.TemporalRiskTracker
import com.zerotap.evidence.RollingEvidenceBuffer
import com.zerotap.sensor.audio.AudioDataSource
import com.zerotap.sensor.feature.ExtractedMotionFeatures
import com.zerotap.sensor.feature.StandardSensorFeatureExtractor
import com.zerotap.sensor.location.LocationAnalysis
import com.zerotap.sensor.location.LocationContextAnalyzer
import com.zerotap.sensor.location.LocationDataSource
import com.zerotap.sensor.motion.MotionDataSource
import com.zerotap.sensor.motion.MotionInferenceEngine
import com.zerotap.util.Logger
import com.zerotap.util.RollingBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong

class ProtectionForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    // Run sensor preprocessing and inference strictly on Dispatchers.Default, never blocking Main thread
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)

    private lateinit var motionDataSource: MotionDataSource
    private lateinit var locationDataSource: LocationDataSource
    private lateinit var audioDataSource: AudioDataSource

    private val featureExtractor = StandardSensorFeatureExtractor()
    private val motionEngine: MotionInferenceEngine = DevelopmentMotionInferenceEngine(featureExtractor)
    private val audioEngine: AudioInferenceEngine = DevelopmentAudioInferenceEngine()
    private val locationAnalyzer = LocationContextAnalyzer()
    private val riskEngine: RiskEngine = DevelopmentRiskEngine()
    private val riskPredictionEngine: RiskPredictionEngine = DevelopmentRiskPredictionEngine()
    private val temporalTracker = TemporalRiskTracker()

    private lateinit var evidenceBuffer: RollingEvidenceBuffer
    private lateinit var summarizer: LocalIncidentSummarizer
    private lateinit var incidentRepository: IncidentRepository
    private lateinit var alertManager: AlertManager
    private lateinit var chennaiSafetyDb: ChennaiSafetyDatabase
    private lateinit var userPreferences: UserPreferences
    private lateinit var responseManager: ResponseManager

    // Rolling windows for feature extraction & inference
    private val motionWindowBuffer = RollingBuffer<MotionSample>(150) // ~2-3 seconds at 50-60Hz
    private val locationHistoryBuffer = RollingBuffer<LocationSample>(20)
    private val audioHistoryBuffer = RollingBuffer<AudioMetadata>(10)
    private val riskHistoryBuffer = RollingBuffer<Pair<Long, Int>>(300) // ~5 minutes of 1000ms score points

    private val motionCount = AtomicLong(0)
    private val locationCount = AtomicLong(0)
    private val audioCount = AtomicLong(0)

    private var lastMotionSample: MotionSample? = null
    private var lastLocationSample: LocationSample? = null
    private var lastAudioMetadata: AudioMetadata? = null
    private var lastFeatures: ExtractedMotionFeatures? = null
    private var lastMotionPrediction: MotionPrediction? = null
    private var lastAudioPrediction: AudioPrediction? = null
    private var lastLocationAnalysis: LocationAnalysis? = null

    private var sampleRateWindowStart = System.currentTimeMillis()
    private var sampleRateCount = 0L
    private var estimatedRateHz = 0f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        motionDataSource = MotionDataSource(this)
        locationDataSource = LocationDataSource(this)
        audioDataSource = AudioDataSource(this)

        val db = ZeroTapDatabase.getInstance(this)
        incidentRepository = IncidentRepository(db.incidentDao())
        summarizer = DevelopmentIncidentSummarizer()
        evidenceBuffer = RollingEvidenceBuffer()
        chennaiSafetyDb = ChennaiSafetyDatabase(this)
        userPreferences = UserPreferences(this)
        responseManager = ResponseManager(this, db.trustedContactDao(), userPreferences)

        activeTemporalTracker = temporalTracker
        activeResponseManager = responseManager

        scope.launch {
            responseManager.smsDeliveryState.collect { _smsDeliveryState.value = it }
        }
        scope.launch {
            responseManager.emergencyCallStatus.collect { _emergencyCallStatus.value = it }
        }

        alertManager = AlertManager(
            listOf(
                InternetAlertTransport(),
                SmsAlertTransport(),
                BluetoothRelayTransport(),
                WifiDirectRelayTransport()
            )
        )

        val manager = IncidentManager(
            riskEngine,
            evidenceBuffer,
            alertManager,
            summarizer,
            incidentRepository
        )
        incidentManager = manager

        startSensors()
        startProcessingPipelines()
        startDiagnosticsBroadcaster()

        _isRunning.value = true
        Logger.sensor("ProtectionService", "ProtectionForegroundService successfully started with 1000ms Risk Prediction Engine")
    }

    private fun startSensors() {
        motionDataSource.start()
        locationDataSource.start()
        audioDataSource.start()
    }

    private fun startProcessingPipelines() {
        // 1. MOTION INGESTION PIPELINE (Runs on Default Dispatcher)
        scope.launch {
            motionDataSource.dataFlow.collect { sample ->
                motionCount.incrementAndGet()
                sampleRateCount++
                lastMotionSample = sample
                motionWindowBuffer.add(sample)
                evidenceBuffer.addMotion(sample)
            }
        }

        // 2. LOCATION INGESTION PIPELINE
        scope.launch {
            locationDataSource.dataFlow.collect { sample ->
                locationCount.incrementAndGet()
                lastLocationSample = sample
                locationHistoryBuffer.add(sample)
                evidenceBuffer.addLocation(sample)
            }
        }

        // 3. AUDIO INGESTION PIPELINE
        scope.launch {
            audioDataSource.dataFlow.collect { metadata ->
                audioCount.incrementAndGet()
                lastAudioMetadata = metadata
                audioHistoryBuffer.add(metadata)
                evidenceBuffer.addAudio(metadata)
            }
        }

        // 4. PERIODIC INFERENCE & MULTI-MODAL RISK PREDICTION (Runs every 1000ms per Requirement)
        // Decoupled from sensor interrupts to prevent frame skipping or battery drain
        scope.launch {
            while (isActive) {
                delay(1000)
                evaluateSensorPipelines()
            }
        }
    }

    private suspend fun evaluateSensorPipelines() {
        val now = System.currentTimeMillis()

        // Calculate actual sampling rate every ~2 seconds
        val dtRate = now - sampleRateWindowStart
        if (dtRate >= 2000) {
            estimatedRateHz = (sampleRateCount * 1000f) / dtRate
            sampleRateWindowStart = now
            sampleRateCount = 0
            Logger.sensor("MotionData", "Sensor sampling rate: %.1f Hz, total samples: %d".format(estimatedRateHz, motionCount.get()))
        }

        val signals = mutableListOf<RiskSignal>()

        // A. Extract Motion Features & Run Motion Inference
        val currentSamples = motionWindowBuffer.getSnapshot()
        var motionContext: MotionContext? = null
        if (currentSamples.isNotEmpty()) {
            val window = MotionWindow(
                samples = currentSamples,
                startTimestamp = currentSamples.first().timestamp,
                endTimestamp = currentSamples.last().timestamp
            )
            val features = featureExtractor.extractMotionFeatures(window)
            lastFeatures = features

            val motionPrediction = motionEngine.classifyFeatures(features)
            lastMotionPrediction = motionPrediction

            motionContext = MotionContext(
                timestamp = now,
                stationaryConfidence = if (motionPrediction.classification == MotionClassification.STATIONARY) motionPrediction.confidence else 0f,
                walkingConfidence = if (motionPrediction.classification == MotionClassification.NORMAL_WALKING) motionPrediction.confidence else 0f,
                runningConfidence = if (motionPrediction.classification == MotionClassification.RUNNING) motionPrediction.confidence else 0f,
                abruptMotionConfidence = if (motionPrediction.classification == MotionClassification.SUDDEN_JERK) motionPrediction.confidence else 0f,
                impactConfidence = if (motionPrediction.classification == MotionClassification.PHONE_DROP) motionPrediction.confidence else 0f,
                fallConfidence = if (features.peakAccelMagnitude > 24f && features.minAccelMagnitude < 2.5f) 0.8f else 0f,
                peakAcceleration = features.peakAccelMagnitude,
                meanAcceleration = features.meanAccelMagnitude,
                jerkMagnitude = features.jerkMagnitude,
                peakGyro = features.peakGyroMagnitude
            )

            // Emit a real MotionSignal from physical phone kinematics
            signals.add(
                RiskSignal.MotionSignal(
                    timestamp = now,
                    weight = when (motionPrediction.classification) {
                        MotionClassification.PHONE_DROP -> 0.35f
                        MotionClassification.SUDDEN_JERK -> 0.28f
                        MotionClassification.RUNNING -> 0.10f
                        else -> 0.01f
                    },
                    description = "Motion: ${motionPrediction.classification.displayName} (Peak: %.1fm/s², Jerk: %.1f)".format(
                        features.peakAccelMagnitude, features.jerkMagnitude
                    ),
                    classification = motionPrediction.classification,
                    confidence = motionPrediction.confidence
                )
            )
        }

        // B. Audio Inference
        val currentAudio = lastAudioMetadata
        var audioContext: AudioContext? = null
        if (currentAudio != null && currentAudio.isRecording) {
            val audioHistory = audioHistoryBuffer.getSnapshot()
            val audioPrediction = audioEngine.classify(currentAudio, audioHistory)
            lastAudioPrediction = audioPrediction

            audioContext = AudioContext(
                timestamp = now,
                voiceActivityDetected = currentAudio.amplitudeDb > 55f,
                elevatedVocalEnergy = currentAudio.amplitudeDb > 70f,
                distressLikePattern = audioPrediction.classification == AudioClassification.DISTRESS_SOUND,
                loudImpactDetected = audioPrediction.classification == AudioClassification.LOUD_ACOUSTIC_EVENT,
                ambientLevelDb = currentAudio.amplitudeDb,
                classificationLabel = audioPrediction.classification.displayName,
                confidence = audioPrediction.confidence
            )

            if (audioPrediction.classification != AudioClassification.NORMAL) {
                signals.add(
                    RiskSignal.AudioSignal(
                        timestamp = now,
                        weight = if (audioPrediction.classification == AudioClassification.DISTRESS_SOUND) 0.40f else 0.20f,
                        description = "Audio: ${audioPrediction.classification.displayName} (%.1f dB)".format(currentAudio.amplitudeDb),
                        classification = audioPrediction.classification,
                        confidence = audioPrediction.confidence
                    )
                )
            }
        }

        // C. Location Anomaly Context (Kinematics & Movement Continuity)
        val locationHistory = locationHistoryBuffer.getSnapshot()
        var locationContext: LocationContext? = null
        if (locationHistory.isNotEmpty()) {
            val latest = locationHistory.last()
            val locAnalysis = locationAnalyzer.analyzeMovement(locationHistory)
            lastLocationAnalysis = locAnalysis

            locationContext = LocationContext(
                timestamp = now,
                latitude = latest.latitude,
                longitude = latest.longitude,
                accuracy = latest.accuracy,
                speed = latest.speed,
                bearing = latest.bearing,
                isMoving = locAnalysis.isMoving,
                isUnexpectedStop = locAnalysis.isUnexpectedStop,
                stopDurationMs = locAnalysis.stopDurationMs,
                historicalSafetyContext = null
            )

            if (locAnalysis.isUnexpectedStop) {
                signals.add(
                    RiskSignal.LocationSignal(
                        timestamp = now,
                        weight = 0.20f,
                        description = "Unexpected stop detected (>30s stationary after movement)",
                        latitude = latest.latitude,
                        longitude = latest.longitude,
                        speed = latest.speed,
                        type = LocationSignalType.UNEXPECTED_STOP
                    )
                )
            }
        }

        _recentSignals.value = signals

        // D. UNIFIED MULTI-MODAL CONTEXT & RISK PREDICTION EVALUATION (1000ms Cycle)
        val unifiedContext = UnifiedSensorContext(
            motion = motionContext,
            audio = audioContext,
            location = locationContext,
            timestamp = now
        )

        val prediction = riskPredictionEngine.evaluate(unifiedContext, temporalTracker.temporalState.value)
        val newTemporalState = temporalTracker.update(prediction.scorePercent)
        val finalPrediction = prediction.copy(
            temporalState = newTemporalState,
            durationInCurrentStateSeconds = temporalTracker.durationInCurrentStateSeconds
        )

        // Add to rolling history buffer (last 5 minutes)
        riskHistoryBuffer.add(Pair(now, finalPrediction.scorePercent))

        // Trigger Response Manager with deduplication (Requirement 11A)
        responseManager.onEngineTick(
            temporalState = newTemporalState,
            prediction = finalPrediction,
            locationContext = locationContext,
            eventId = temporalTracker.currentEmergencyEventId.value
        )

        // Update StateFlows
        _predictionResult.value = finalPrediction
        _temporalRiskState.value = newTemporalState
        _countdownSeconds.value = temporalTracker.countdownSeconds.value

        // Feed signals into existing IncidentManager
        incidentManager?.processSignals(signals)

        // Map to legacy RiskAssessment for backward compatibility with existing views
        val legacyState = when (newTemporalState) {
            TemporalRiskState.NORMAL -> RiskState.NORMAL
            TemporalRiskState.ELEVATED -> RiskState.WATCH
            TemporalRiskState.HIGH_RISK -> RiskState.HIGH_RISK
            TemporalRiskState.EMERGENCY_PENDING -> RiskState.HIGH_RISK
            TemporalRiskState.EMERGENCY_TRIGGERED -> RiskState.INCIDENT
        }

        _currentRiskAssessment.value = RiskAssessment(
            score = prediction.scorePercent / 100f,
            state = legacyState,
            contributingSignals = signals,
            timestamp = now,
            engineLabel = prediction.engineLabel
        )

        // Periodic logging
        if (prediction.scorePercent > 20) {
            Logger.risk("RiskEngine", "Score: %d/100 | State: %s | Factors: %s".format(
                prediction.scorePercent, newTemporalState.displayName, prediction.contributingFactors.joinToString(", ")
            ))
        }
    }

    private fun startDiagnosticsBroadcaster() {
        // Broadcast UI telemetry throttled to ~4 Hz (every 250ms) to ensure smooth 60/120fps UI rendering
        scope.launch {
            while (isActive) {
                delay(250)
                val mSample = lastMotionSample
                val lSample = lastLocationSample
                val aMeta = lastAudioMetadata
                val feats = lastFeatures
                val mPred = lastMotionPrediction
                val aPred = lastAudioPrediction
                val predResult = _predictionResult.value
                val tempState = _temporalRiskState.value

                val diag = SensorDiagnostics(
                    isAccelerometerAvailable = motionDataSource.isAccelerometerAvailable,
                    isGyroscopeAvailable = motionDataSource.isGyroscopeAvailable,
                    isLocationAvailable = locationDataSource.isActive,
                    isAudioAvailable = audioDataSource.isActive,
                    isMagnetometerAvailable = motionDataSource.isMagnetometerAvailable,

                    accelX = mSample?.accelerationX ?: 0f,
                    accelY = mSample?.accelerationY ?: 0f,
                    accelZ = mSample?.accelerationZ ?: 0f,
                    accelMagnitude = mSample?.accelerationMagnitude ?: 0f,

                    gyroX = mSample?.gyroX ?: 0f,
                    gyroY = mSample?.gyroY ?: 0f,
                    gyroZ = mSample?.gyroZ ?: 0f,
                    gyroMagnitude = mSample?.gyroMagnitude ?: 0f,

                    locationLatitude = lSample?.latitude,
                    locationLongitude = lSample?.longitude,
                    locationAccuracy = lSample?.accuracy,
                    locationSpeed = lSample?.speed,

                    audioAmplitudeDb = aMeta?.amplitudeDb ?: 0f,
                    isAudioRecording = aMeta?.isRecording ?: false,

                    peakAccelMagnitude = feats?.peakAccelMagnitude ?: 0f,
                    meanAccelMagnitude = feats?.meanAccelMagnitude ?: 0f,
                    varianceAccelMagnitude = feats?.varianceAccelMagnitude ?: 0f,
                    jerkMagnitude = feats?.jerkMagnitude ?: 0f,

                    motionClassification = mPred?.classification ?: MotionClassification.UNKNOWN,
                    motionConfidence = mPred?.confidence ?: 0f,
                    audioClassification = aPred?.classification ?: AudioClassification.NORMAL,

                    motionSampleCount = motionCount.get(),
                    locationSampleCount = locationCount.get(),
                    audioSampleCount = audioCount.get(),
                    estimatedSamplingRateHz = estimatedRateHz,

                    currentRiskScore = (predResult?.scorePercent ?: 0) / 100f,
                    currentRiskState = when (tempState) {
                        TemporalRiskState.NORMAL -> RiskState.NORMAL
                        TemporalRiskState.ELEVATED -> RiskState.WATCH
                        TemporalRiskState.HIGH_RISK -> RiskState.HIGH_RISK
                        TemporalRiskState.EMERGENCY_PENDING -> RiskState.HIGH_RISK
                        TemporalRiskState.EMERGENCY_TRIGGERED -> RiskState.INCIDENT
                    },
                    activeSignalCount = _recentSignals.value.size,

                    temporalRiskState = tempState,
                    predictionResult = predResult,
                    riskHistory = riskHistoryBuffer.getSnapshot()
                )
                _diagnostics.value = diag
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, PROTECTION_CHANNEL_ID)
            .setContentTitle("ZeroTap Protection Active")
            .setContentText("Continuously analyzing sensor context on-device (1000ms)")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        motionDataSource.stop()
        locationDataSource.stop()
        audioDataSource.stop()

        serviceJob.cancel()
        _isRunning.value = false
        _currentRiskAssessment.value = null
        _predictionResult.value = null
        _temporalRiskState.value = TemporalRiskState.NORMAL
        _recentSignals.value = emptyList()
        incidentManager = null
        activeTemporalTracker = null
        activeResponseManager = null
        Logger.sensor("ProtectionService", "ProtectionForegroundService destroyed and resources released")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PROTECTION_CHANNEL_ID,
                "ZeroTap Protection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while sensor protection is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val PROTECTION_CHANNEL_ID = "zerotap_protection_channel"
        private const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentRiskAssessment = MutableStateFlow<RiskAssessment?>(null)
        val currentRiskAssessment: StateFlow<RiskAssessment?> = _currentRiskAssessment.asStateFlow()

        private val _predictionResult = MutableStateFlow<RiskPredictionResult?>(null)
        val predictionResult: StateFlow<RiskPredictionResult?> = _predictionResult.asStateFlow()

        private val _temporalRiskState = MutableStateFlow(TemporalRiskState.NORMAL)
        val temporalRiskState: StateFlow<TemporalRiskState> = _temporalRiskState.asStateFlow()

        private val _countdownSeconds = MutableStateFlow(15)
        val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

        private val _recentSignals = MutableStateFlow<List<RiskSignal>>(emptyList())
        val recentSignals: StateFlow<List<RiskSignal>> = _recentSignals.asStateFlow()

        private val _diagnostics = MutableStateFlow(SensorDiagnostics())
        val diagnostics: StateFlow<SensorDiagnostics> = _diagnostics.asStateFlow()

        private val _smsDeliveryState = MutableStateFlow<com.zerotap.domain.response.SmsDeliveryState>(com.zerotap.domain.response.SmsDeliveryState.Idle)
        val smsDeliveryState: StateFlow<com.zerotap.domain.response.SmsDeliveryState> = _smsDeliveryState.asStateFlow()

        private val _emergencyCallStatus = MutableStateFlow<String?>(null)
        val emergencyCallStatus: StateFlow<String?> = _emergencyCallStatus.asStateFlow()

        var incidentManager: IncidentManager? = null
            private set

        private var activeTemporalTracker: TemporalRiskTracker? = null
        private var activeResponseManager: ResponseManager? = null

        fun cancelEmergency() {
            activeTemporalTracker?.cancelEmergency()
            _temporalRiskState.value = TemporalRiskState.NORMAL
            _countdownSeconds.value = 15
        }

        fun triggerEmergencyNow() {
            activeTemporalTracker?.triggerImmediately()
            _temporalRiskState.value = TemporalRiskState.EMERGENCY_TRIGGERED
            _countdownSeconds.value = 0
        }

        suspend fun sendTestSms(): DeliveryResult {
            return activeResponseManager?.sendTestSms() ?: DeliveryResult(
                success = false,
                transportName = "SMS",
                timestamp = System.currentTimeMillis(),
                errorMessage = "Protection service is not running"
            )
        }

        fun start(context: Context) {
            val intent = Intent(context, ProtectionForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProtectionForegroundService::class.java))
        }
    }
}
