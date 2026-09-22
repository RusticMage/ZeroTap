package com.zerotap.sensor.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.zerotap.domain.model.AudioMetadata
import com.zerotap.sensor.SensorDataSource
import com.zerotap.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.log10

class AudioDataSource(private val context: Context) : SensorDataSource<AudioMetadata> {
    private val _dataFlow = MutableSharedFlow<AudioMetadata>(extraBufferCapacity = 10)
    override val dataFlow: Flow<AudioMetadata> = _dataFlow
    
    override var isActive: Boolean = false
        private set
        
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val updateIntervalMs = 500L

    override fun start() {
        if (isActive) return
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Logger.sensor("AudioData", "RECORD_AUDIO permission not granted")
            return
        }
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Logger.sensor("AudioData", "Failed to initialize AudioRecord")
                return
            }
            
            audioRecord?.startRecording()
            isActive = true
            Logger.sensor("AudioData", "Started audio data source")
            
            recordingJob = scope.launch {
                val buffer = ShortArray(bufferSize)
                while (isActive) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        var maxAmplitude = 0
                        for (i in 0 until readSize) {
                            val absVal = Math.abs(buffer[i].toInt())
                            if (absVal > maxAmplitude) {
                                maxAmplitude = absVal
                            }
                        }
                        
                        // Calculate dB
                        val amplitudeDb = if (maxAmplitude > 0) 20 * log10(maxAmplitude.toDouble()) else 0.0
                        
                        _dataFlow.tryEmit(
                            AudioMetadata(
                                timestamp = System.currentTimeMillis(),
                                amplitudeDb = amplitudeDb.toFloat(),
                                isRecording = true
                            )
                        )
                    }
                    delay(updateIntervalMs)
                }
            }
        } catch (e: SecurityException) {
            Logger.sensor("AudioData", "SecurityException starting audio record: ${e.message}")
        }
    }

    override fun stop() {
        if (!isActive) return
        isActive = false
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Logger.sensor("AudioData", "Stopped audio data source")
    }
}
