package com.expressocr.app.compass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.expressocr.app.R
import com.expressocr.app.databinding.ActivityCompassBinding
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

class CompassActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityCompassBinding
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var locationManager: LocationManager? = null

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val gravity = FloatArray(3)
    private var hasGravity = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocationUpdates()
        else Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompassBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.compass_title)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        binding.bottomNav.selectedItemId = R.id.nav_compass
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_compass -> {
                    binding.compassPanel.visibility = View.VISIBLE
                    binding.levelPanel.visibility = View.GONE
                    true
                }
                R.id.nav_level -> {
                    binding.compassPanel.visibility = View.GONE
                    binding.levelPanel.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }

        binding.latText.text = "${getString(R.string.compass_lat)}: --"
        binding.lngText.text = "${getString(R.string.compass_lng)}: --"
        binding.altText.text = "${getString(R.string.compass_alt)}: --"

        ensureLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gravitySensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        locationManager?.removeUpdates(this)
        super.onPause()
    }

    private fun ensureLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED -> startLocationUpdates()
            else -> permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ->
                LocationManager.GPS_PROVIDER
            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        }
        provider ?: return
        locationManager?.requestLocationUpdates(provider, 2000L, 1f, this)
        locationManager?.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                binding.compassDial.azimuthDeg = azimuth
                binding.headingText.text = "${azimuth.roundToInt()}° ${directionLabel(azimuth)}"
            }
            Sensor.TYPE_ORIENTATION -> {
                var azimuth = event.values[0]
                if (azimuth < 0) azimuth += 360f
                binding.compassDial.azimuthDeg = azimuth
                binding.headingText.text = "${azimuth.roundToInt()}° ${directionLabel(azimuth)}"
            }
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                gravity[0] = event.values[0]
                gravity[1] = event.values[1]
                gravity[2] = event.values[2]
                hasGravity = true
                updateLevel()
            }
        }
    }

    private fun updateLevel() {
        if (!hasGravity) return
        val g = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
        if (g < 0.1f) return
        val gx = gravity[0] / g
        val gy = gravity[1] / g
        val gz = gravity[2] / g

        // Phone flat on table: z ~ 1, bubble near center
        val roll = Math.toDegrees(atan2(gx.toDouble(), gz.toDouble())).toFloat()
        val pitch = Math.toDegrees(atan2((-gy).toDouble(), sqrt(gx * gx + gz * gz).toDouble())).toFloat()

        binding.rollText.text = "${roll.roundToInt()}°"
        binding.pitchText.text = "${pitch.roundToInt()}°"
        binding.levelBubble.offsetX = (gx * 2f).coerceIn(-1f, 1f)
        binding.levelBubble.offsetY = ((-gy) * 2f).coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onLocationChanged(location: Location) {
        binding.latText.text = "${getString(R.string.compass_lat)}: ${formatCoord(location.latitude, true)}"
        binding.lngText.text = "${getString(R.string.compass_lng)}: ${formatCoord(location.longitude, false)}"
        val alt = if (location.hasAltitude()) {
            String.format("%.1f 米", location.altitude)
        } else {
            getString(R.string.compass_unavailable)
        }
        binding.altText.text = "${getString(R.string.compass_alt)}: $alt"
    }

    private fun formatCoord(value: Double, isLat: Boolean): String {
        val abs = abs(value)
        val deg = abs.toInt()
        val minFloat = (abs - deg) * 60
        val min = minFloat.toInt()
        val sec = ((minFloat - min) * 60).roundToInt()
        val hemi = if (isLat) {
            if (value >= 0) "北纬" else "南纬"
        } else {
            if (value >= 0) "东经" else "西经"
        }
        return "$hemi $deg°$min'$sec\""
    }

    private fun directionLabel(azimuth: Float): String {
        val dirs = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
        val index = ((azimuth + 22.5f) / 45f).toInt() % 8
        return dirs[index]
    }
}
