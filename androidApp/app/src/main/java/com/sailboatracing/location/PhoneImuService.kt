package com.sailboatracing.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PhoneImuService(context: Context) {

    data class ImuReading(
        val heading: Float,
        val pitch: Float,
        val roll: Float,
        val gyroZ: Float,
        val accelX: Float,
        val accelY: Float,
        val accelZ: Float
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _readings = MutableSharedFlow<ImuReading>(extraBufferCapacity = 8)
    val readings: SharedFlow<ImuReading> = _readings.asSharedFlow()

    private var lastEmitMs = 0L
    private val throttleMs = 40L  // ~25 Hz

    private var latestGyroZ = 0f
    private var latestAccelX = 0f
    private var latestAccelY = 0f
    private var latestAccelZ = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val rotMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotMatrix, orientation)
                    var headingDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    if (headingDeg < 0f) headingDeg += 360f
                    val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    val rollDeg  = Math.toDegrees(orientation[2].toDouble()).toFloat()
                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs >= throttleMs) {
                        lastEmitMs = now
                        _readings.tryEmit(ImuReading(
                            heading = headingDeg,
                            pitch   = pitchDeg,
                            roll    = rollDeg,
                            gyroZ   = latestGyroZ,
                            accelX  = latestAccelX,
                            accelY  = latestAccelY,
                            accelZ  = latestAccelZ
                        ))
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    latestGyroZ = Math.toDegrees(event.values[2].toDouble()).toFloat()
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    latestAccelX = event.values[0]
                    latestAccelY = event.values[1]
                    latestAccelZ = event.values[2]
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private var running = false

    fun start() {
        if (running) return
        running = true
        val rotSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val gyroSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        rotSensor?.let   { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroSensor?.let  { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        accelSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(listener)
    }

    fun isRunning() = running
}
