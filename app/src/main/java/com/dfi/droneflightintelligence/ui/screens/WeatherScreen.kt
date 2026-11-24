package com.dfi.droneflightintelligence.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfi.droneflightintelligence.data.remote.WeatherRepository
import com.dfi.droneflightintelligence.ui.theme.*
import com.dfi.droneflightintelligence.ui.viewmodel.MainViewModel

@Composable
fun WeatherScreen(viewModel: MainViewModel) {
    val weather by viewModel.weather.collectAsState()
    val location by viewModel.operatorLocation.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val wind = weather.windSpeedKmh ?: 0.0
    val gusts = weather.windGustsKmh ?: 0.0
    val isStrongWind = wind >= settings.windWarningThresholdKmh || gusts >= settings.gustWarningThresholdKmh

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DfiBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Καιρός & Άνεμος (Open-Meteo)", color = DfiTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        WeatherRepository.describeWmoCode(weather.weatherCode),
                        color = DfiCyan,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = {
                    val lat = location?.latitude ?: 37.9838
                    val lon = location?.longitude ?: 23.7275
                    viewModel.refreshEnvironment(lat, lon)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Ανανέωση", tint = DfiCyan)
                }
            }
        }

        if (weather.errorMessage != null) {
            item {
                Surface(color = DfiRed.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
                    Text(weather.errorMessage!!, color = DfiRed, modifier = Modifier.padding(12.dp))
                }
            }
        }

        if (isStrongWind) {
            item {
                Surface(
                    color = DfiRed.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = DfiRed)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("ΠΡΟΕΙΔΟΠΟΙΗΣΗ ΙΣΧΥΡΟΥ ΑΝΕΜΟΥ", color = DfiRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Άνεμος ${wind.toInt()} km/h (Ριπές ${gusts.toInt()} km/h). Υπέρβαση ορίου ασφαλούς πτήσης για DJI Lito 1.",
                                color = DfiTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeatherCard("Ταχύτητα Ανέμου", weather.windSpeedKmh?.let { "${it.toInt()} km/h" } ?: "--", Modifier.weight(1f))
                    WeatherCard("Ριπές Ανέμου", weather.windGustsKmh?.let { "${it.toInt()} km/h" } ?: "--", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeatherCard("Διεύθυνση", "${weather.windDirectionCardinal} (${weather.windDirectionDeg ?: "--"}°)", Modifier.weight(1f))
                    WeatherCard("Θερμοκρασία", weather.temperatureC?.let { String.format("%.1f °C", it) } ?: "--", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeatherCard("Υγρασία", weather.humidityPercent?.let { "$it%" } ?: "--", Modifier.weight(1f))
                    WeatherCard("Πιθανότητα Βροχής", weather.precipitationProbability?.let { "$it%" } ?: "--", Modifier.weight(1f))
                }
            }
        }

        if (weather.hourlyForecast.isNotEmpty()) {
            item {
                Text("Ωριαία Πρόγνωση (Επόμενες 24 ώρες)", color = DfiCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            items(weather.hourlyForecast) { item ->
                Surface(color = DfiSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.timeIso.takeLast(5), color = DfiTextPrimary, fontWeight = FontWeight.Bold)
                        Text("${item.temperatureC.toInt()}°C", color = DfiTextMuted)
                        Text("Άνεμος ${item.windSpeedKmh.toInt()} / Ριπές ${item.windGustsKmh.toInt()} km/h", color = DfiCyan, fontSize = 12.sp)
                        Text("Βροχή ${item.precipitationProbability}%", color = DfiAmber, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = DfiSurface, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, color = DfiTextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = DfiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
