package com.dfi.droneflightintelligence.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfi.droneflightintelligence.ui.theme.*
import com.dfi.droneflightintelligence.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FlightScreen(
    viewModel: MainViewModel,
    onRequestScreenCapture: () -> Unit,
    onStopScreenCapture: () -> Unit,
    onOpenSessionOnMap: (Long) -> Unit
) {
    val telemetry by viewModel.droneTelemetry.collectAsState()
    val operatorLoc by viewModel.operatorLocation.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val savedSessions by viewModel.savedSessions.collectAsState()
    val trackPoints by viewModel.activeTrackPoints.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DfiBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Πίνακας Πτήσης — DJI Lito 1 & RC-N3",
                color = DfiTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Απευθείας σύνδεση SDK: Μη υποστηριζόμενη από DJI Lito 1 • Ενεργή γέφυρα: MediaProjection + OCR",
                color = DfiTextMuted,
                fontSize = 12.sp
            )
        }

        item {
            Surface(
                color = DfiSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Κατάσταση Σύνδεσης", color = DfiTextMuted, fontSize = 12.sp)
                            Text(
                                telemetry.connectionStatus,
                                color = if (telemetry.isScreenCaptureActive) DfiGreen else DfiAmber,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        Button(
                            onClick = {
                                if (telemetry.isScreenCaptureActive) onStopScreenCapture()
                                else onRequestScreenCapture()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (telemetry.isScreenCaptureActive) DfiRed else DfiCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                if (telemetry.isScreenCaptureActive) Icons.Default.Stop else Icons.Default.ScreenShare,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (telemetry.isScreenCaptureActive) "Διακοπή" else "Σύνδεση DJI Fly")
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TelemetryTile(
                        title = "Ύψος Drone",
                        value = telemetry.altitudeMeters?.let { String.format("%.1f m", it) } ?: "Μη διαθέσιμο",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        title = "Απόσταση",
                        value = telemetry.distanceMeters?.let { String.format("%.1f m", it) } ?: "Μη διαθέσιμο",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TelemetryTile(
                        title = "Ταχύτητα (H.S)",
                        value = telemetry.horizontalSpeedMs?.let { String.format("%.1f m/s", it) } ?: "Μη διαθέσιμο",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        title = "Μπαταρία",
                        value = telemetry.batteryPercent?.let { "$it%" } ?: "Μη διαθέσιμο",
                        accent = if ((telemetry.batteryPercent ?: 100) < 25) DfiRed else DfiGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TelemetryTile(
                        title = "Κατεύθυνση",
                        value = telemetry.headingDegrees?.let { "${it.toInt()}°" }
                            ?: operatorLoc?.bearingDegrees?.let { "${it.toInt()}° (Κινητό)" }
                            ?: "Μη διαθέσιμο",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        title = "GPS / Δορυφόροι",
                        value = buildString {
                            append(telemetry.gpsSatellites?.let { "$it SAT" } ?: "OCR: --")
                            operatorLoc?.accuracyMeters?.let { append(" • ±${it.toInt()}m") }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Surface(
                color = DfiSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Διαδρομή Πτήσης", color = DfiTextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            if (activeSessionId != null) "Καταγραφή ενεργή (${trackPoints.size} σημεία)"
                            else "Έτοιμο για αποθήκευση διαδρομής",
                            color = DfiTextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = { viewModel.toggleFlightRecording() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeSessionId != null) DfiRed else DfiGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(if (activeSessionId != null) "Λήξη Καταγραφής" else "Έναρξη")
                    }
                }
            }
        }

        if (savedSessions.isNotEmpty()) {
            item {
                Text("Αποθηκευμένες Διαδρομές", color = DfiCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            items(savedSessions, key = { it.id }) { session ->
                val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                Surface(
                    color = DfiSurfaceAlt,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.title, color = DfiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                "${fmt.format(Date(session.startedAtMs))} • Μέγ. Ύψος: ${session.maxAltitudeMeters?.let { "${it.toInt()}m" } ?: "--"}",
                                color = DfiTextMuted,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { onOpenSessionOnMap(session.id) }) {
                            Icon(Icons.Default.Map, contentDescription = "Προβολή στον χάρτη", tint = DfiCyan)
                        }
                        IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Διαγραφή", tint = DfiRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = DfiCyan
) {
    Surface(
        color = DfiSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = DfiTextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
