package com.dfi.droneflightintelligence.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfi.droneflightintelligence.ui.theme.*
import com.dfi.droneflightintelligence.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DfiBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ρυθμίσεις Εφαρμογής", color = DfiTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Surface(color = DfiSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Όριο Προειδοποίησης Ανέμου: ${settings.windWarningThresholdKmh.toInt()} km/h", color = DfiTextPrimary)
                Slider(
                    value = settings.windWarningThresholdKmh,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(windWarningThresholdKmh = v) } },
                    valueRange = 15f..60f
                )

                Text("Όριο Ριπών Ανέμου: ${settings.gustWarningThresholdKmh.toInt()} km/h", color = DfiTextPrimary)
                Slider(
                    value = settings.gustWarningThresholdKmh,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(gustWarningThresholdKmh = v) } },
                    valueRange = 20f..80f
                )
            }
        }

        Surface(color = DfiSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Επίπεδα Χάρτη Υποδομών", color = DfiCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Πυλώνες, Γραμμές Υψηλής Τάσης & Υποσταθμοί", color = DfiTextPrimary, fontSize = 13.sp)
                    Switch(
                        checked = settings.showPowerInfra,
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(showPowerInfra = checked) } }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Κεραίες Τηλεπικοινωνιών & Γέφυρες", color = DfiTextPrimary, fontSize = 13.sp)
                    Switch(
                        checked = settings.showTelecomAndBridges,
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(showTelecomAndBridges = checked) } }
                    )
                }
                Text("Ακτίνα σάρωσης Overpass: ${settings.overpassRadiusMeters} m", color = DfiTextMuted, fontSize = 12.sp)
                Slider(
                    value = settings.overpassRadiusMeters.toFloat(),
                    onValueChange = { r -> viewModel.updateSettings { it.copy(overpassRadiusMeters = r.toInt()) } },
                    valueRange = 1000f..5000f,
                    steps = 3
                )
            }
        }

        Surface(color = DfiSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Συμβατότητα DJI Lito 1 & DJI RC-N3", color = DfiCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Το DJI Lito 1 δεν διαθέτει επίσημο Android Mobile SDK. Η DFI χρησιμοποιεί Android MediaProjection (με έγκριση χρήστη) για ανάγνωση τηλεμετρίας από το DJI Fly μέσω ML Kit OCR και ανάλυση εμποδίων με MediaPipe AI.",
                    color = DfiTextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
