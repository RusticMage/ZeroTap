package com.zerotap.sensor.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.zerotap.domain.model.MotionSample
import com.zerotap.sensor.SensorDataSource
import com.zerotap.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class MotionDataSource(context: Context) : SensorDataSource<MotionSample>, SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    private val _dataFlow = MutableSharedFlow<MotionSample>(extraBufferCapacity = 128)
    override val dataFlow: Flow<MotionSample> = _dataFlow
    
    override var isActive: Boolean = false
        private set

    val isAccelerometerAvailable: Boolean get() = accelerometer != null
    val isGyroscopeAvailable: Boolean get() = gyroscope != null
    val isMagnetometerAvailable: Boolean get() = magnetometer != null

    private var lastAccel: FloatArray? = null
    private var lastGyro: FloatArray? = null

    override fun start() {
        if (isActive) return
        
        if (accelerometer == null && gyroscope == null) {
            Logger.sensor("Sensors", "Neither Accelerometer nor Gyroscope is available on this device!")
            return
        }

        accelerometer?.let {
            // SENSOR_DELAY_UI (approx 60ms) or SENSOR_DELAY_GAME (approx 20ms)
            // Using SENSOR_DELAY_GAME provides crisp kinematic curves for fall/jerk detection
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        
        isActive = true
        Logger.sensor("Sensors", "MotionDataSource started. Accel: ${accelerometer != null}, Gyro: ${gyroscope != null}")
    }

    override fun stop() {
        if (!isActive) return
        sensorManager.unregisterListener(this)
        isActive = false
        lastAccel = null
        lastGyro = null
        Logger.sensor("Sensors", "MotionDataSource stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> lastAccel = event.values.clone()
            Sensor.TYPE_GYROSCOPE -> lastGyro = event.values.clone()
        }
        
        val accel = lastAccel
        val gyro = lastGyro
        
        if (accel != null) {
            val sample = MotionSample(
                timestamp = System.currentTimeMillis(),
                accelerationX = accel[0],
                accelerationY = accel[1],
                accelerationZ = accel[2],
                gyroX = gyro?.get(0) ?: 0f,
                gyroY = gyro?.get(1) ?: 0f,
                gyroZ = gyro?.get(2) ?: 0f
            )
            _dataFlow.tryEmit(sample)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
