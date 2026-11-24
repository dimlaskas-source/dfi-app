package com.dfi.droneflightintelligence.data.remote

import com.dfi.droneflightintelligence.data.model.HourlyForecastItem
import com.dfi.droneflightintelligence.data.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WeatherRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherSnapshot = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,precipitation_probability,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m,wind_gusts_10m" +
            "&wind_speed_unit=kmh&timezone=auto&forecast_days=2"

        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext WeatherSnapshot(errorMessage = "Σφάλμα API καιρού (HTTP ${resp.code})")
                }
                val body = resp.body?.string() ?: return@withContext WeatherSnapshot(errorMessage = "Κενή απάντηση καιρού")
                val json = JSONObject(body)
                val current = json.optJSONObject("current")
                    ?: return@withContext WeatherSnapshot(errorMessage = "Μη έγκυρη δομή current")

                val hourlyObj = json.optJSONObject("hourly")
                val forecastList = mutableListOf<HourlyForecastItem>()
                if (hourlyObj != null) {
                    val times = hourlyObj.optJSONArray("time")
                    val temps = hourlyObj.optJSONArray("temperature_2m")
                    val winds = hourlyObj.optJSONArray("wind_speed_10m")
                    val gusts = hourlyObj.optJSONArray("wind_gusts_10m")
                    val probs = hourlyObj.optJSONArray("precipitation_probability")
                    val codes = hourlyObj.optJSONArray("weather_code")
                    val count = minOf(times?.length() ?: 0, 24)
                    for (i in 0 until count) {
                        forecastList.add(
                            HourlyForecastItem(
                                timeIso = times?.optString(i).orEmpty(),
                                temperatureC = temps?.optDouble(i) ?: 0.0,
                                windSpeedKmh = winds?.optDouble(i) ?: 0.0,
                                windGustsKmh = gusts?.optDouble(i) ?: 0.0,
                                precipitationProbability = probs?.optInt(i) ?: 0,
                                weatherCode = codes?.optInt(i) ?: 0
                            )
                        )
                    }
                }

                WeatherSnapshot(
                    temperatureC = current.optDouble("temperature_2m").takeIf { !it.isNaN() },
                    humidityPercent = if (current.has("relative_humidity_2m")) current.optInt("relative_humidity_2m") else null,
                    windSpeedKmh = current.optDouble("wind_speed_10m").takeIf { !it.isNaN() },
                    windGustsKmh = current.optDouble("wind_gusts_10m").takeIf { !it.isNaN() },
                    windDirectionDeg = if (current.has("wind_direction_10m")) current.optInt("wind_direction_10m") else null,
                    precipitationProbability = if (current.has("precipitation_probability")) current.optInt("precipitation_probability") else null,
                    weatherCode = if (current.has("weather_code")) current.optInt("weather_code") else null,
                    hourlyForecast = forecastList,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            WeatherSnapshot(errorMessage = "Αδυναμία σύνδεσης με Open-Meteo: ${e.localizedMessage ?: "Άγνωστο σφάλμα"}")
        }
    }

    companion object {
        fun describeWmoCode(code: Int?): String = when (code) {
            0 -> "Αίθριος"
            1, 2 -> "Αραιή νέφωση"
            3 -> "Νεφελώδης"
            45, 48 -> "Ομίχλη"
            51, 53, 55 -> "Ψιλόβροχο"
            61, 63, 65 -> "Βροχόπτωση"
            71, 73, 75 -> "Χιονόπτωση"
            80, 81, 82 -> "Μπόρες"
            95, 96, 99 -> "Καταιγίδα"
            else -> "Μεταβλητός"
        }
    }
}
