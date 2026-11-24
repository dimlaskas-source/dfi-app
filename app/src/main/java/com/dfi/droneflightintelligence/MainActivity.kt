package com.dfi.droneflightintelligence

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.dfi.droneflightintelligence.service.ScreenCaptureService
import com.dfi.droneflightintelligence.ui.screens.*
import com.dfi.droneflightintelligence.ui.theme.DfiBg
import com.dfi.droneflightintelligence.ui.theme.DfiCyan
import com.dfi.droneflightintelligence.ui.theme.DfiSurface
import com.dfi.droneflightintelligence.ui.theme.DfiTheme
import com.dfi.droneflightintelligence.ui.viewmodel.MainViewModel

private enum class DfiTab(val labelEl: String, val icon: ImageVector) {
    MAP("Χάρτης", Icons.Default.Map),
    FLIGHT("Πτήση", Icons.Default.FlightTakeoff),
    AI("AI", Icons.Default.Visibility),
    WEATHER("Καιρός", Icons.Default.Air),
    SETTINGS("Ρυθμίσεις", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DfiTheme {
                val context = LocalContext.current
                var selectedTab by remember { mutableStateOf(DfiTab.MAP) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val hasLocation = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (hasLocation) {
                        viewModel.startLocationTracking()
                    }
                }

                val mediaProjectionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    if (res.resultCode == Activity.RESULT_OK && res.data != null) {
                        val svcIntent = Intent(context, ScreenCaptureService::class.java).apply {
                            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, res.resultCode)
                            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, res.data)
                        }
                        ContextCompat.startForegroundService(context, svcIntent)
                    }
                }

                LaunchedEffect(Unit) {
                    val perms = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        add(Manifest.permission.CAMERA)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                    permissionLauncher.launch(perms)
                }

                Scaffold(
                    containerColor = DfiBg,
                    bottomBar = {
                        NavigationBar(containerColor = DfiSurface) {
                            DfiTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.labelEl) },
                                    label = { Text(tab.labelEl) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DfiCyan,
                                        selectedTextColor = DfiCyan
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding), color = DfiBg) {
                        when (selectedTab) {
                            DfiTab.MAP -> MapScreen(viewModel)
                            DfiTab.FLIGHT -> FlightScreen(
                                viewModel = viewModel,
                                onRequestScreenCapture = {
                                    val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
                                },
                                onStopScreenCapture = {
                                    val stopIntent = Intent(context, ScreenCaptureService::class.java).apply {
                                        action = ScreenCaptureService.ACTION_STOP
                                    }
                                    context.startService(stopIntent)
                                },
                                onOpenSessionOnMap = { sessionId ->
                                    viewModel.loadSavedSessionOnMap(sessionId)
                                    selectedTab = DfiTab.MAP
                                }
                            )
                            DfiTab.AI -> AiScreen(viewModel)
                            DfiTab.WEATHER -> WeatherScreen(viewModel)
                            DfiTab.SETTINGS -> SettingsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
