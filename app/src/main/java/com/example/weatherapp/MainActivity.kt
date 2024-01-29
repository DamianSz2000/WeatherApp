package com.example.weatherapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.weatherapp.view.WeatherScreen
import androidx.activity.compose.setContent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.weatherapp.controller.RetrofitClient
import com.example.weatherapp.model.City
import com.example.weatherapp.model.WeatherResponse
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import java.util.Locale

class MainActivity : AppCompatActivity() {


    private var location: Location? = null
    private var weatherResponse: WeatherResponse? = null

    private var locationServicesDialog: AlertDialog? = null

    private var autoUpdateEnabled = mutableStateOf(true)

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermissionAndFetchWeather()
        setContent {
            WeatherScreen(
                location = this@MainActivity.location,
                weatherResponse = this@MainActivity.weatherResponse,
                onChangeLocationClick = { showLocationInputDialog() },
                onToggleAutoUpdate = { isEnabled -> autoUpdateEnabled.value = isEnabled },
                isAutoUpdateEnabled = autoUpdateEnabled.value,
                onCitySelected = { city -> onCitySelected(city) }
            )
        }
    }


    override fun onResume() {
        super.onResume()
        if (autoUpdateEnabled.value) {
            locationServicesDialog?.dismiss()
            locationServicesDialog = null
            checkLocationPermissionAndFetchWeather()
        }
    }
    private fun onCitySelected(city: City) {
        val location = Location("").apply {
            latitude = city.latitude
            longitude = city.longitude
        }
        fetchWeatherData(location)
    }

    private fun checkLocationPermissionAndFetchWeather() {
        Log.d("MainActivity", "Checking permissions")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "Asking user for permissions")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1)
        } else {
            Log.d("MainActivity", "Checking if location is enabled")
            fetchWeatherDataIfLocationEnabled()
        }
    }

    private fun showLocationInputDialog() {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val latitudeInput = EditText(context).apply {
            hint = "Enter Latitude"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        val longitudeInput = EditText(context).apply {
            hint = "Enter Longitude"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        layout.addView(latitudeInput)
        layout.addView(longitudeInput)

        AlertDialog.Builder(context)
            .setTitle("Enter Location")
            .setView(layout)
            .setPositiveButton("OK") { dialog, _ ->
                val latitude = latitudeInput.text.toString().toDoubleOrNull()
                val longitude = longitudeInput.text.toString().toDoubleOrNull()
                if (latitude != null && longitude != null) {
                    fetchWeatherData(Location("").apply {
                        this.latitude = latitude
                        this.longitude = longitude
                    })
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun fetchWeatherDataIfLocationEnabled() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.d("MainActivity", "Asking user to enable GPS")
            showLocationServicesDisabledAlert()
        } else {
            Log.d("MainActivity", "Getting location")
            getLocation()
        }
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationRequest = LocationRequest.Builder(10000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.firstOrNull()?.let { location ->
                    Log.d("MainActivity", "Location: ${location.latitude}, ${location.longitude}")
                    fetchWeatherData(location)
                    fusedLocationClient.removeLocationUpdates(this)
                } ?: Log.d("MainActivity", "No location found")
            }
        }, Looper.getMainLooper())
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("weather_channel_id", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
            return
        }

        val builder = NotificationCompat.Builder(this, "weather_channel_id")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build())
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    fetchWeatherDataIfLocationEnabled()
                } else {
                    showNotification("Permissions required", "Local weather cannot be fetched without granting location permissions.")
                    Log.d("MainActivity", "Location permissions were denied")
                }
            }
            }
        }
    private fun showLocationServicesDisabledAlert() {
        showNotification("Location services required", "Please enable location services.")
        locationServicesDialog = AlertDialog.Builder(this)
            .setTitle("Location Services Disabled")
            .setMessage("Please enable location services to use this feature.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                setContent {
                    WeatherScreen(
                        location = null,
                        weatherResponse = null,
                        onChangeLocationClick = { showLocationInputDialog() },
                        onToggleAutoUpdate = { },
                        isAutoUpdateEnabled = autoUpdateEnabled.value,
                        onCitySelected = { city -> onCitySelected(city) }
                    )
                }
            }
            .show()
    }

    private fun fetchWeatherData(location: Location) {
        Log.d("MainActivity", "Fetching weather")
        lifecycleScope.launch {
            try {
                val lat = String.format(Locale.US, "%.2f", location.latitude).toDouble()
                val lon = String.format(Locale.US, "%.2f", location.longitude).toDouble()
                Log.d("MainActivity", "Getting response")
                val response = RetrofitClient.webservice.getCurrentWeather(lat, lon, "insert ur openweathermap api key", "metric")
                Log.d("MainActivity", "Got response")
                showNotification("Weather Update", "Weather data fetched successfully.")
                this@MainActivity.location = location
                this@MainActivity.weatherResponse = response
                setContent {
                    WeatherScreen(
                        location = this@MainActivity.location,
                        weatherResponse = this@MainActivity.weatherResponse,
                        onChangeLocationClick = { showLocationInputDialog() },
                        onToggleAutoUpdate = { isEnabled -> autoUpdateEnabled.value = isEnabled },
                        isAutoUpdateEnabled = autoUpdateEnabled.value,
                        onCitySelected = { city -> onCitySelected(city) }
                    )
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching weather data", e)
                setContent {
                    Text("Failed to fetch weather data: ${e.message}")
                }
            }
        }
    }
}

