package com.example.tracking

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.data.model.HeadingSourceMode
import kotlin.math.roundToInt

object CompassMath {
    const val LOW_PASS_ALPHA = 0.15f
    const val GPS_BEARING_SPEED_THRESHOLD_MPS = 1.2f

    fun normalizeDegrees(degrees: Float): Float {
        val mod = degrees % 360f
        return if (mod < 0f) mod + 360f else mod
    }

    /**
     * Circular low-pass filter handling 359° <-> 0° wrap-around cleanly.
     */
    fun smoothAngleDegrees(
        current: Float?,
        target: Float,
        alpha: Float = LOW_PASS_ALPHA
    ): Float {
        val normTarget = normalizeDegrees(target)
        if (current == null) return normTarget
        val normCurrent = normalizeDegrees(current)
        var delta = normTarget - normCurrent
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return normalizeDegrees(normCurrent + alpha * delta)
    }

    fun applyDeclination(magneticHeadingDeg: Float, declinationDeg: Float): Float {
        return normalizeDegrees(magneticHeadingDeg + declinationDeg)
    }

    fun displayDegrees(degrees: Float): Int {
        return normalizeDegrees(degrees).roundToInt() % 360
    }

    fun formatCardinal(degrees: Float): String {
        val norm = normalizeDegrees(degrees)
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((norm + 22.5f) / 45f).toInt() % 8
        return directions[index]
    }

    data class ResolvedHeading(
        val headingDegrees: Float?,
        val isUsingGpsBearing: Boolean
    )

    fun resolveHeading(
        mode: HeadingSourceMode,
        magnetometerHeadingDeg: Float?,
        gpsBearingDeg: Float?,
        speedMps: Float
    ): ResolvedHeading {
        return when (mode) {
            HeadingSourceMode.MAGNETOMETER_ONLY -> {
                ResolvedHeading(
                    headingDegrees = magnetometerHeadingDeg?.let { normalizeDegrees(it) },
                    isUsingGpsBearing = false
                )
            }
            HeadingSourceMode.AUTO -> {
                if (speedMps > GPS_BEARING_SPEED_THRESHOLD_MPS && gpsBearingDeg != null) {
                    ResolvedHeading(
                        headingDegrees = normalizeDegrees(gpsBearingDeg),
                        isUsingGpsBearing = true
                    )
                } else {
                    ResolvedHeading(
                        headingDegrees = magnetometerHeadingDeg?.let { normalizeDegrees(it) },
                        isUsingGpsBearing = false
                    )
                }
            }
        }
    }
}

class CompassHeadingManager(
    context: Context,
    private val onHeadingChanged: (headingDeg: Float?, needsCalibration: Boolean) -> Unit
) : SensorEventListener {

    private val sensorManager = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var hasGravity = false
    private var hasGeomagnetic = false
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val inclinationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var smoothedHeadingDeg: Float? = null
    private var needsCalibration: Boolean = false
    private var cachedDeclinationDeg: Float = 0f
    private var isStarted = false

    val hasHardwareCompass: Boolean
        get() = accelerometer != null && magnetometer != null

    fun start() {
        if (isStarted || sensorManager == null) return
        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
            isStarted = true
        }
    }

    fun stop() {
        if (!isStarted || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isStarted = false
    }

    fun updateLocation(latitude: Double, longitude: Double, altitudeMeters: Double) {
        try {
            val geoField = GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                altitudeMeters.toFloat(),
                System.currentTimeMillis()
            )
            cachedDeclinationDeg = geoField.declination
        } catch (_: Exception) {
            // Keep previously cached declination on edge coordinates
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasGeomagnetic = true
            }
            else -> return
        }

        if (!hasGravity || !hasGeomagnetic) return

        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            gravity,
            geomagnetic
        )
        if (!success) return

        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthRad = orientation[0]
        val magneticAzimuthDeg = CompassMath.normalizeDegrees(Math.toDegrees(azimuthRad.toDouble()).toFloat())
        val trueNorthDeg = CompassMath.applyDeclination(magneticAzimuthDeg, cachedDeclinationDeg)
        val smoothed = CompassMath.smoothAngleDegrees(
            current = smoothedHeadingDeg,
            target = trueNorthDeg,
            alpha = CompassMath.LOW_PASS_ALPHA
        )
        smoothedHeadingDeg = smoothed
        onHeadingChanged(smoothed, needsCalibration)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type != Sensor.TYPE_MAGNETIC_FIELD) return
        val lowAccuracy = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
        if (lowAccuracy != needsCalibration) {
            needsCalibration = lowAccuracy
            onHeadingChanged(smoothedHeadingDeg, needsCalibration)
        }
    }
}
