package com.zerotap.domain.model

data class SensorDiagnostics(
    val isAccelerometerAvailable: Boolean = false,
    val isGyroscopeAvailable: Boolean = false,
    val isLocationAvailable: Boolean = false,
    val isAudioAvailable: Boolean = false,
    val isMagnetometerAvailable: Boolean = false,
    
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val accelMagnitude: Float = 0f,
    
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val gyroMagnitude: Float = 0f,
    
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationAccuracy: Float? = null,
    val locationSpeed: Float? = null,
    
    val audioAmplitudeDb: Float = 0f,
    val isAudioRecording: Boolean = false,
    
    val peakAccelMagnitude: Float = 0f,
    val meanAccelMagnitude: Float = 0f,
    val varianceAccelMagnitude: Float = 0f,
    val jerkMagnitude: Float = 0f,
    
    val motionClassification: MotionClassification = MotionClassification.UNKNOWN,
    val motionConfidence: Float = 0f,
    val audioClassification: AudioClassification = AudioClassification.NORMAL,
    
    val motionSampleCount: Long = 0L,
    val locationSampleCount: Long = 0L,
    val audioSampleCount: Long = 0L,
    val estimatedSamplingRateHz: Float = 0f,
    
    val currentRiskScore: Float = 0f,
    val currentRiskState: RiskState = RiskState.NORMAL,
    val activeSignalCount: Int = 0,
    
    val temporalRiskState: TemporalRiskState = TemporalRiskState.NORMAL,
    val predictionResult: RiskPredictionResult? = null,
    val riskHistory: List<Pair<Long, Int>> = emptyList()
)
