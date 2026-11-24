package com.dfi.droneflightintelligence.ui.screens

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dfi.droneflightintelligence.data.model.BaseMapStyle
import com.dfi.droneflightintelligence.data.model.ObstacleCategory
import com.dfi.droneflightintelligence.ui.theme.*
import com.dfi.droneflightintelligence.ui.viewmodel.MainViewModel
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val OpenTopoMapTileSource = object : OnlineTileSourceBase(
    "OpenTopoMap", 0, 17, 256, ".png",
    arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$z/$x/$y.png"
    }
}

@Composable
fun MapScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val location by viewModel.operatorLocation.collectAsState()
    val infra by viewModel.infrastructure.collectAsState()
    val terrain by viewModel.terrain.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val trackPoints by viewModel.activeTrackPoints.collectAsState()
    val historyPoints by viewModel.loadedHistoryPoints.collectAsState()
    val telemetry by viewModel.droneTelemetry.collectAsState()
    val isLoading by viewModel.isLoadingEnvironment.collectAsState()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var centeredOnce by remember { mutableStateOf(false) }

    val iconCache = remember { mutableMapOf<String, Drawable>() }
    fun getCategoryDrawable(cat: ObstacleCategory): Drawable {
        return iconCache.getOrPut(cat.name) { createObstacleIcon(context, cat) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    controller.setZoom(15.5)
                    controller.setCenter(GeoPoint(37.9838, 23.7275))
                    mapViewRef = this
                }
            },
            update = { map ->
                val targetSource = when (settings.baseMapStyle) {
                    BaseMapStyle.OSM_STANDARD -> TileSourceFactory.MAPNIK
                    BaseMapStyle.OPEN_TOPO -> OpenTopoMapTileSource
                }
                if (map.tileProvider.tileSource.name() != targetSource.name()) {
                    map.setTileSource(targetSource)
                }

                map.overlays.clear()

                if (settings.showPowerInfra) {
                    infra.powerLines.forEach { line ->
                        val poly = Polyline(map).apply {
                            setPoints(line.points.map { GeoPoint(it.first, it.second) })
                            outlinePaint.color = android.graphics.Color.parseColor("#FF5252")
                            outlinePaint.strokeWidth = 6f
                            title = "Γραμμή Υψηλής Τάσης ${line.voltage ?: ""}".trim()
                        }
                        map.overlays.add(poly)
                    }
                }

                infra.nodes.forEach { node ->
                    val isPower = node.category in setOf(
                        ObstacleCategory.POWER_TOWER,
                        ObstacleCategory.POWER_POLE,
                        ObstacleCategory.SUBSTATION
                    )
                    if ((isPower && settings.showPowerInfra) || (!isPower && settings.showTelecomAndBridges)) {
                        val marker = Marker(map).apply {
                            position = GeoPoint(node.latitude, node.longitude)
                            icon = getCategoryDrawable(node.category)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = node.title
                            snippet = buildString {
                                append(node.category.labelEl)
                                if (node.voltage != null) append(" • ${node.voltage}V")
                            }
                        }
                        map.overlays.add(marker)
                    }
                }

                if (trackPoints.size >= 2) {
                    val trackLine = Polyline(map).apply {
                        setPoints(trackPoints.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
                        outlinePaint.strokeWidth = 8f
                    }
                    map.overlays.add(trackLine)
                }

                if (historyPoints.size >= 2) {
                    val histLine = Polyline(map).apply {
                        setPoints(historyPoints.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = android.graphics.Color.parseColor("#00E676")
                        outlinePaint.strokeWidth = 7f
                    }
                    map.overlays.add(histLine)
                }

                location?.let { loc ->
                    val geo = GeoPoint(loc.latitude, loc.longitude)
                    val opMarker = Marker(map).apply {
                        position = geo
                        icon = createPilotIcon(context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Θέση Χειριστή (DJI RC-N3)"
                        snippet = "Ακρίβεια: ${loc.accuracyMeters?.toInt() ?: "--"}m"
                    }
                    map.overlays.add(opMarker)

                    if (!centeredOnce) {
                        map.controller.animateTo(geo)
                        centeredOnce = true
                    }
                }

                val dLat = telemetry.droneLatitude
                val dLon = telemetry.droneLongitude
                if (dLat != null && dLon != null) {
                    val droneMarker = Marker(map).apply {
                        position = GeoPoint(dLat, dLon)
                        icon = createDroneIcon(context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "DJI Lito 1"
                        snippet = "Ύψος: ${telemetry.altitudeMeters ?: "--"}m"
                    }
                    map.overlays.add(droneMarker)
                }

                map.invalidate()
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .fillMaxWidth(0.78f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = DfiBg.copy(alpha = 0.88f),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terrain, contentDescription = null, tint = DfiCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Έδαφος & Υποδομές (OSM)", color = DfiCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    val elev = terrain.groundElevationMeters?.let { "${it.toInt()} m" } ?: "--"
                    val slope = terrain.maxSlopePercent?.let { String.format("%.1f%%", it) } ?: "--"
                    val minMax = if (terrain.minAreaElevationMeters != null && terrain.maxAreaElevationMeters != null) {
                        "${terrain.minAreaElevationMeters!!.toInt()}–${terrain.maxAreaElevationMeters!!.toInt()} m"
                    } else "--"
                    Text("Υψόμετρο εδάφους: $elev • Κλίση: $slope", color = DfiTextPrimary, fontSize = 12.sp)
                    Text("Εύρος περιοχής: $minMax • ${terrain.morphologyLabel}", color = DfiTextMuted, fontSize = 11.sp)
                    Text(
                        "Εμπόδια: ${infra.nodes.size} σημεία • ${infra.powerLines.size} γραμμές Τάσης",
                        color = DfiAmber,
                        fontSize = 11.sp
                    )
                    if (infra.errorMessage != null || terrain.errorMessage != null) {
                        Text(
                            infra.errorMessage ?: terrain.errorMessage.orEmpty(),
                            color = DfiRed,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    val nextStyle = if (settings.baseMapStyle == BaseMapStyle.OSM_STANDARD) {
                        BaseMapStyle.OPEN_TOPO
                    } else {
                        BaseMapStyle.OSM_STANDARD
                    }
                    viewModel.updateSettings { it.copy(baseMapStyle = nextStyle) }
                },
                containerColor = DfiSurface,
                contentColor = DfiCyan,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Αλλαγή χάρτη")
            }

            FloatingActionButton(
                onClick = {
                    val center = mapViewRef?.mapCenter
                    if (center != null) {
                        viewModel.refreshEnvironment(center.latitude, center.longitude)
                    }
                },
                containerColor = DfiSurface,
                contentColor = DfiAmber,
                modifier = Modifier.size(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DfiAmber, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Σάρωση περιοχής")
                }
            }

            FloatingActionButton(
                onClick = {
                    location?.let {
                        mapViewRef?.controller?.animateTo(GeoPoint(it.latitude, it.longitude))
                    }
                },
                containerColor = DfiCyan,
                contentColor = Color.Black,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Κεντράρισμα GPS")
            }
        }
    }
}

private fun createObstacleIcon(context: Context, category: ObstacleCategory): Drawable {
    val size = 54
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    val (colorHex, glyph) = when (category) {
        ObstacleCategory.POWER_TOWER -> "#FF3D57" to "⚡"
        ObstacleCategory.POWER_POLE -> "#FF7043" to "I"
        ObstacleCategory.POWER_LINE -> "#FF5252" to "~"
        ObstacleCategory.SUBSTATION -> "#FFB300" to "S"
        ObstacleCategory.TELECOM_MAST -> "#AB47BC" to "T"
        ObstacleCategory.BRIDGE -> "#42A5F5" to "B"
        ObstacleCategory.BUILDING -> "#78909C" to "H"
    }

    bgPaint.color = android.graphics.Color.parseColor(colorHex)
    canvas.drawCircle(size / 2f, size / 2f, size / 2.3f, bgPaint)
    canvas.drawText(glyph, size / 2f, size / 2f + 9f, fgPaint)
    return BitmapDrawable(context.resources, bmp)
}

private fun createPilotIcon(context: Context): Drawable {
    val size = 56
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#5500E5FF") }
    val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#00E5FF") }
    canvas.drawCircle(size / 2f, size / 2f, 26f, outer)
    canvas.drawCircle(size / 2f, size / 2f, 12f, inner)
    return BitmapDrawable(context.resources, bmp)
}

private fun createDroneIcon(context: Context): Drawable {
    val size = 60
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#00E676") }
    val path = Path().apply {
        moveTo(size / 2f, 6f)
        lineTo(size - 10f, size - 10f)
        lineTo(size / 2f, size - 20f)
        lineTo(10f, size - 10f)
        close()
    }
    canvas.drawPath(path, paint)
    return BitmapDrawable(context.resources, bmp)
}
