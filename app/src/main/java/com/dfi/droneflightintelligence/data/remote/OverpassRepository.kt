package com.dfi.droneflightintelligence.data.remote

import com.dfi.droneflightintelligence.data.model.InfrastructureSnapshot
import com.dfi.droneflightintelligence.data.model.MapObstacleNode
import com.dfi.droneflightintelligence.data.model.MapPowerLine
import com.dfi.droneflightintelligence.data.model.ObstacleCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OverpassRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun fetchObstacles(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 2500
    ): InfrastructureSnapshot = withContext(Dispatchers.IO) {
        val query = """
            [out:json][timeout:20];
            (
              node["power"="tower"](around:$radiusMeters,$lat,$lon);
              node["power"="pole"](around:$radiusMeters,$lat,$lon);
              way["power"="line"](around:$radiusMeters,$lat,$lon);
              way["power"="minor_line"](around:$radiusMeters,$lat,$lon);
              node["power"="substation"](around:$radiusMeters,$lat,$lon);
              way["power"="substation"](around:$radiusMeters,$lat,$lon);
              node["man_made"="mast"](around:$radiusMeters,$lat,$lon);
              node["man_made"="tower"]["tower:type"="communication"](around:$radiusMeters,$lat,$lon);
              way["bridge"="yes"](around:$radiusMeters,$lat,$lon);
            );
            out center geom;
        """.trimIndent()

        try {
            val requestBody = query.toRequestBody("text/plain; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("https://overpass-api.de/api/interpreter")
                .header("User-Agent", "DroneFlightIntelligence/1.0")
                .post(requestBody)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext InfrastructureSnapshot(
                        errorMessage = "Overpass API σφάλμα (HTTP ${resp.code})"
                    )
                }
                val raw = resp.body?.string()
                    ?: return@withContext InfrastructureSnapshot(errorMessage = "Κενή απάντηση OSM")
                val json = JSONObject(raw)
                val elements = json.optJSONArray("elements")
                    ?: return@withContext InfrastructureSnapshot()

                val nodes = mutableListOf<MapObstacleNode>()
                val lines = mutableListOf<MapPowerLine>()

                for (i in 0 until elements.length()) {
                    val el = elements.optJSONObject(i) ?: continue
                    val id = el.optLong("id")
                    val type = el.optString("type")
                    val tags = el.optJSONObject("tags") ?: JSONObject()

                    val power = tags.optString("power")
                    val manMade = tags.optString("man_made")
                    val bridge = tags.optString("bridge")
                    val voltage = tags.optString("voltage").takeIf { it.isNotBlank() }
                    val name = tags.optString("name").takeIf { it.isNotBlank() }

                    if (type == "way" && (power == "line" || power == "minor_line")) {
                        val geom = el.optJSONArray("geometry") ?: continue
                        val pts = mutableListOf<Pair<Double, Double>>()
                        for (g in 0 until geom.length()) {
                            val pt = geom.optJSONObject(g) ?: continue
                            pts.add(pt.optDouble("lat") to pt.optDouble("lon"))
                        }
                        if (pts.size >= 2) {
                            lines.add(MapPowerLine(id = id, points = pts, voltage = voltage, name = name))
                        }
                        continue
                    }

                    val center = el.optJSONObject("center")
                    val nodeLat = if (type == "node") el.optDouble("lat") else center?.optDouble("lat") ?: Double.NaN
                    val nodeLon = if (type == "node") el.optDouble("lon") else center?.optDouble("lon") ?: Double.NaN
                    if (nodeLat.isNaN() || nodeLon.isNaN()) continue

                    val category = when {
                        power == "tower" -> ObstacleCategory.POWER_TOWER
                        power == "pole" -> ObstacleCategory.POWER_POLE
                        power == "substation" -> ObstacleCategory.SUBSTATION
                        manMade == "mast" || manMade == "tower" -> ObstacleCategory.TELECOM_MAST
                        bridge == "yes" -> ObstacleCategory.BRIDGE
                        else -> continue
                    }

                    nodes.add(
                        MapObstacleNode(
                            id = id,
                            latitude = nodeLat,
                            longitude = nodeLon,
                            category = category,
                            title = name ?: category.labelEl,
                            voltage = voltage
                        )
                    )
                }

                InfrastructureSnapshot(
                    nodes = nodes,
                    powerLines = lines,
                    fetchedAtMs = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            InfrastructureSnapshot(
                errorMessage = "Αδυναμία φόρτωσης υποδομών: ${e.localizedMessage ?: "Σφάλμα δικτύου"}"
            )
        }
    }
}
