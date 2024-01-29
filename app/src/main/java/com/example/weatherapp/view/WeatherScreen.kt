package com.example.weatherapp.view

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.location.Location
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.weatherapp.R
import com.example.weatherapp.model.City
import com.example.weatherapp.model.WeatherResponse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt


fun convertEpochSecondsToLocalTime(epochSeconds: Long): String {
    val instant = Instant.ofEpochSecond(epochSeconds)
    val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    return localDateTime.format(formatter)
}

fun isDaytime(sunrise: Long, sunset: Long, currentTime: Long = System.currentTimeMillis()): Boolean {
    return currentTime in sunrise..sunset
}

fun getWeatherIconId(weatherCondition: String, isDaytime: Boolean): Int {
    return when (weatherCondition) {
        "clear sky" -> if (isDaytime) R.drawable.clear_sky else R.drawable.clear_sky_night
        "few clouds" -> if (isDaytime) R.drawable.few_clouds else R.drawable.few_clouds_night
        "scattered clouds" -> if (isDaytime) R.drawable.scattered_clouds else R.drawable.scattered_clouds_night
        "broken clouds" -> if (isDaytime) R.drawable.broken_clouds else R.drawable.broke_clouds_night
        "shower rain" -> if (isDaytime) R.drawable.shower_rain else R.drawable.shower_rain_night
        "rain" -> if (isDaytime) R.drawable.rain else R.drawable.rain_night
        "thunderstorm" -> if (isDaytime) R.drawable.thunderstorm else R.drawable.thunderstorm_night
        "snow" -> if (isDaytime) R.drawable.snow else R.drawable.snow_night
        "mist" -> R.drawable.mist

        else -> R.drawable.clear_sky
    }
}

val popularCities = listOf(
    City("Wrocław", 51.10454748471072, 17.035797019028113),
    City("Katowice", 50.263182149880855, 19.013360088610156),
    City("Gdynia", 54.518063055000106, 18.532132278241576),
    City("Suwałki", 54.112547864672536, 22.929124914657127),
    City("Sosnowiec", 50.276854877635415, 19.127924940327887)
)

@Composable
fun WeatherScreen(
    location: Location?,
    weatherResponse: WeatherResponse?,
    onChangeLocationClick: () -> Unit,
    onToggleAutoUpdate: (Boolean) -> Unit,
    isAutoUpdateEnabled: Boolean,
    onCitySelected: (City) -> Unit
)  {

    var expanded by remember { mutableStateOf(false) }

    val isDaytime = weatherResponse?.let {
        isDaytime(it.sys.sunrise * 1000, it.sys.sunset * 1000)
    } ?: true


    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Weather Information", style = MaterialTheme.typography.headlineMedium, color = Color.White)

        if (location != null) {
            val lat = String.format(Locale.US, "%.2f", location.latitude).toDouble()
            val lon = String.format(Locale.US, "%.2f", location.longitude).toDouble()
            Text("Location: Latitude $lat, Longitude $lon", color = Color.White)

            if (weatherResponse != null) {
                val roundedTemp = weatherResponse.main.temp.roundToInt()
                val roundedFeelsLike = weatherResponse.main.feels_like.roundToInt()

                Text("Temperature: ${roundedTemp}°C", color = Color.White)
                Text("Feels like: ${roundedFeelsLike}°C", color = Color.White)
                Text("Weather: ${weatherResponse.weather.first().main}", color = Color.White)
                Text("Weather description: ${weatherResponse.weather.first().description}", color = Color.White)
                Text("Pressure: ${weatherResponse.main.pressure} hPa", color = Color.White)
                Text("Humidity: ${weatherResponse.main.humidity}%", color = Color.White)
                Text("Wind speed: ${weatherResponse.wind.speed} km/h", color = Color.White)
                var time = convertEpochSecondsToLocalTime(weatherResponse.sys.sunrise)
                Text("Sunrise: $time", color = Color.White)
                time = convertEpochSecondsToLocalTime(weatherResponse.sys.sunset)
                Text("Sunset: $time", color = Color.White)
                Text("City: ${weatherResponse.name}", color = Color.White)
                val weatherIconId = getWeatherIconId(weatherResponse.weather.first().description, isDaytime)
                Image(
                    painter = painterResource(id = weatherIconId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .padding(8.dp)
                )
                Button(onClick = onChangeLocationClick) {
                    Text("Change Location")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto Update: ", color = Color.White)
                    Switch(
                        checked = isAutoUpdateEnabled,
                        onCheckedChange = { onToggleAutoUpdate(it) }
                    )
                }
                Button(onClick = { expanded = true }) {
                    Text("Select City")
                }


                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(color = Color.Black)
                ) {
                    popularCities.forEach { city ->
                        DropdownMenuItem(onClick = {
                            expanded = false
                            onCitySelected(city)
                        }, text = {Text(city.name)})
                    }
                }
            } else {
                Text("Fetching weather data...")
            }
        } else {
            Text("Location not available. Please enable location services and permissions.")
            val context = LocalContext.current
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                Text("Enable Location Services")
            }
            Button(onClick = onChangeLocationClick) {
                Text("Select custom location manually")
            }
            Button(onClick = { expanded = true }) {
                Text("Select City manually")
            }


            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(color = Color.Black)
            ) {
                popularCities.forEach { city ->
                    DropdownMenuItem(onClick = {
                        expanded = false
                        onCitySelected(city)
                    }, text = {Text(city.name)})
                }
            }
        }
    }
}