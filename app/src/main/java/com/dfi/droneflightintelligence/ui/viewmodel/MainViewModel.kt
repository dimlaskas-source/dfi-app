package com.dfi.droneflightintelligence.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dfi.droneflightintelligence.ai.AiObstacleDetector
import com.dfi.droneflightintelligence.data.local.FlightDatabase
import com.dfi.droneflightintelligence.data.local.FlightPointEntity
import com.dfi.droneflightintelligence.data.local.FlightSessionEntity
import com.dfi.droneflightintelligence.data.location.LocationTracker
import com.dfi.droneflightintelligence.data.model.*
import com.dfi.droneflightintelligence.data.remote.ElevationRepository
import com.dfi.droneflightintelligence.data.remote.OverpassRepository
import com.dfi.droneflightintelligence.data.remote.WeatherRepository
import com.dfi.droneflightintelligence.service.ScreenCaptureBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AppSettingsState(
    val windWarningThresholdKmh: Float = 30f,
    val gustWarningThresholdKmh: Float = 40f,
    val overpassRadiusMeters: Int = 2500,
    val showPowerInfra: Boolean = true,
    val showTelecomAndBridges: Boolean = true,
    val baseMapStyle: BaseMapStyle = BaseMapStyle.OSM_STANDARD
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val locationTracker = LocationTracker(application)
    private val weatherRepo = WeatherRepository()
    private val elevationRepo = ElevationRepository()
    private val overpassRepo = OverpassRepository()
    private val flightDao = FlightDatabase.getInstance(application).flightDao()
    val aiDetector = AiObstacleDetector(application)

    private var locationJob: Job? = null

    private val _operatorLocation = MutableStateFlow<GeoPointData?>(null)
    val operatorLocation = _operatorLocation.asStateFlow()

    private val _weather = MutableStateFlow(WeatherSnapshot())
    val weather = _weather.asStateFlow()

    private val _terrain = MutableStateFlow(TerrainInfo())
    val terrain = _terrain.asStateFlow()

    private val _infrastructure = MutableStateFlow(InfrastructureSnapshot())
    val infrastructure = _infrastructure.asStateFlow()

    val droneTelemetry = ScreenCaptureBus.telemetry
    val capturedScreenFrame = ScreenCaptureBus.latestFrame

    private val _aiSourceMode = MutableStateFlow(AiSourceMode.CAMERA)
    val aiSourceMode = _aiSourceMode.asStateFlow()

    private val _aiDetections = MutableStateFlow<List<AiDetectionBox>>(emptyList())
    val aiDetections = _aiDetections.asStateFlow()

    private val _settings = MutableStateFlow(AppSettingsState())
    val settings = _settings.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId = _activeSessionId.asStateFlow()

    private val _activeTrackPoints = MutableStateFlow<List<GeoPointData>>(emptyList())
    val activeTrackPoints = _activeTrackPoints.asStateFlow()

    val savedSessions: StateFlow<List<FlightSessionEntity>> = flightDao.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _loadedHistoryPoints = MutableStateFlow<List<FlightPointEntity>>(emptyList())
    val loadedHistoryPoints = _loadedHistoryPoints.asStateFlow()

    private val _isLoadingEnvironment = MutableStateFlow(false)
    val isLoadingEnvironment = _isLoadingEnvironment.asStateFlow()

    private var lastAutoFetchLocation: GeoPointData? = null

    init {
        viewModelScope.launch {
            capturedScreenFrame.collect { frame ->
                if (frame != null && _aiSourceMode.value == AiSourceMode.DJI_FLY_SCREEN) {
                    analyzeBitmapForObstacles(frame)
                }
            }
        }
    }

    fun startLocationTracking() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            locationTracker.locationUpdates().collect { point ->
                _operatorLocation.value = point

                val sessionId = _activeSessionId.value
                if (sessionId != null) {
                    val telem = droneTelemetry.value
                    _activeTrackPoints.update { it + point }
                    flightDao.insertPoint(
                        FlightPointEntity(
                            sessionId = sessionId,
                            latitude = telem.droneLatitude ?: point.latitude,
                            longitude = telem.droneLongitude ?: point.longitude,
                            altitudeMeters = telem.altitudeMeters,
                            speedMs = telem.horizontalSpeedMs,
                            batteryPercent = telem.batteryPercent,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                }

                val prev = lastAutoFetchLocation
                if (prev == null || distanceMeters(prev, point) > 400.0) {
                    lastAutoFetchLocation = point
                    refreshEnvironment(point.latitude, point.longitude)
                }
            }
        }
    }

    fun refreshEnvironment(lat: Double, lon: Double) {
        viewModelScope.launch {
            _isLoadingEnvironment.value = true
            val wJob = launch { _weather.value = weatherRepo.fetchWeather(lat, lon) }
            val tJob = launch { _terrain.value = elevationRepo.fetchTerrainProfile(lat, lon) }
            val oJob = launch {
                _infrastructure.value = overpassRepo.fetchObstacles(
                    lat = lat,
                    lon = lon,
                    radiusMeters = _settings.value.overpassRadiusMeters
                )
            }
            wJob.join(); tJob.join(); oJob.join()
            _isLoadingEnvironment.value = false
        }
    }

    fun setAiSourceMode(mode: AiSourceMode) {
        _aiSourceMode.value = mode
        _aiDetections.value = emptyList()
    }

    fun analyzeBitmapForObstacles(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val detections = aiDetector.detect(bitmap)
            _aiDetections.value = detections
        }
    }

    fun toggleFlightRecording() {
        viewModelScope.launch {
            val currentId = _activeSessionId.value
            if (currentId == null) {
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val newId = flightDao.insertSession(
                    FlightSessionEntity(
                        title = "Πτήση Lito 1 — ${fmt.format(Date())}",
                        startedAtMs = System.currentTimeMillis()
                    )
                )
                _activeTrackPoints.value = emptyList()
                _activeSessionId.value = newId
            } else {
                val points = flightDao.getPointsForSession(currentId)
                val maxAlt = points.mapNotNull { it.altitudeMeters }.maxOrNull()
                val existing = savedSessions.value.firstOrNull { it.id == currentId }
                if (existing != null) {
                    flightDao.updateSession(
                        existing.copy(
                            endedAtMs = System.currentTimeMillis(),
                            maxAltitudeMeters = maxAlt
                        )
                    )
                }
                _activeSessionId.value = null
            }
        }
    }

    fun loadSavedSessionOnMap(sessionId: Long) {
        viewModelScope.launch {
            _loadedHistoryPoints.value = flightDao.getPointsForSession(sessionId)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            flightDao.deleteSession(sessionId)
        }
    }

    fun updateSettings(transform: (AppSettingsState) -> AppSettingsState) {
        _settings.update(transform)
    }

    private fun distanceMeters(a: GeoPointData, b: GeoPointData): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results)
        return results[0].toDouble()
    }

    override fun onCleared() {
        aiDetector.close()
        super.onCleared()
    }
}
