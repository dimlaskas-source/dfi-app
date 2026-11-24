package com.dfi.droneflightintelligence.data.model

import android.graphics.RectF

data class GeoPointData(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val bearingDegrees: Float? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class ObstacleCategory(val labelEl: String) {
    POWER_TOWER("Πυλώνας Υψηλής Τάσης"),
    POWER_POLE("Στύλος Ρεύματος"),
    POWER_LINE("Γραμμή Υψηλής Τάσης"),
    SUBSTATION("Υποσταθμός ΔΕΗ/ΑΔΜΗΕ"),
    TELECOM_MAST("Κεραία Τηλεπικοινωνιών"),
    BRIDGE("Γέφυρα"),
    BUILDING("Κτίριο / Εμπόδιο")
}

data class MapObstacleNode(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val category: ObstacleCategory,
    val title: String,
    val voltage: String? = null
)

data class MapPowerLine(
    val id: Long,
    val points: List<Pair<Double, Double>>,
    val voltage: String? = null,
    val name: String? = null
)

data class InfrastructureSnapshot(
    val nodes: List<MapObstacleNode> = emptyList(),
    val powerLines: List<MapPowerLine> = emptyList(),
    val fetchedAtMs: Long = 0L,
    val errorMessage: String? = null
)

data class TerrainInfo(
    val groundElevationMeters: Double? = null,
    val minAreaElevationMeters: Double? = null,
    val maxAreaElevationMeters: Double? = null,
    val maxSlopePercent: Double? = null,
    val morphologyLabel: String = "Μη διαθέσιμο",
    val errorMessage: String? = null
)

data class HourlyForecastItem(
    val timeIso: String,
    val temperatureC: Double,
    val windSpeedKmh: Double,
    val windGustsKmh: Double,
    val precipitationProbability: Int,
    val weatherCode: Int
)

data class WeatherSnapshot(
    val temperatureC: Double? = null,
    val humidityPercent: Int? = null,
    val windSpeedKmh: Double? = null,
    val windGustsKmh: Double? = null,
    val windDirectionDeg: Int? = null,
    val precipitationProbability: Int? = null,
    val weatherCode: Int? = null,
    val hourlyForecast: List<HourlyForecastItem> = emptyList(),
    val updatedAtMs: Long = 0L,
    val errorMessage: String? = null
) {
    val windDirectionCardinal: String
        get() {
            val deg = windDirectionDeg ?: return "--"
            val dirs = listOf("Β", "ΒΑ", "Α", "ΝΑ", "Ν", "ΝΔ", "Δ", "ΒΔ")
            return dirs[((deg % 360) / 45.0).let { Math.round(it).toInt() % 8 }]
        }
}

data class DroneTelemetry(
    val altitudeMeters: Double? = null,
    val distanceMeters: Double? = null,
    val horizontalSpeedMs: Double? = null,
    val verticalSpeedMs: Double? = null,
    val headingDegrees: Double? = null,
    val gpsSatellites: Int? = null,
    val batteryPercent: Int? = null,
    val droneLatitude: Double? = null,
    val droneLongitude: Double? = null,
    val connectionStatus: String = "Αναμονή DJI Fly (MediaProjection)",
    val isScreenCaptureActive: Boolean = false,
    val rawOcrLines: List<String> = emptyList(),
    val lastUpdatedMs: Long = 0L
)

data class AiDetectionBox(
    val boundingBoxNormalized: RectF,
    val categoryEl: String,
    val rawLabel: String,
    val confidence: Float,
    val isCriticalObstacle: Boolean
)

enum class AiSourceMode(val labelEl: String) {
    CAMERA("Κάμερα Συσκευής"),
    DJI_FLY_SCREEN("Οθόνη DJI Fly")
}

enum class BaseMapStyle(val labelEl: String) {
    OSM_STANDARD("OpenStreetMap (Υποδομές & Οδοί)"),
    OPEN_TOPO("OpenTopoMap (Ανάγλυφο & Υψομετρικές)")
}
