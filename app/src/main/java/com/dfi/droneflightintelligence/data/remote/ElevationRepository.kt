package com.dfi.droneflightintelligence.data.remote

import com.dfi.droneflightintelligence.data.model.TerrainInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ElevationRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchTerrainProfile(lat: Double, lon: Double): TerrainInfo = withContext(Dispatchers.IO) {
        val delta = 0.0022
        val lats = listOf(lat, lat + delta, lat - delta, lat, lat)
        val lons = listOf(lon, lon, lon, lon + delta, lon - delta)
        val url = "https://api.open-meteo.com/v1/elevation" +
            "?latitude=${lats.joinToString(",")}&longitude=${lons.joinToString(",")}"

        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext TerrainInfo(errorMessage = "Σφάλμα υψομέτρου (HTTP ${resp.code})")
                }
                val body = resp.body?.string() ?: return@withContext TerrainInfo(errorMessage = "Κενή απάντηση DEM")
                val json = JSONObject(body)
                val arr = json.optJSONArray("elevation")
                    ?: return@withContext TerrainInfo(errorMessage = "Μη έγκυρα δεδομένα DEM")

                val values = (0 until arr.length()).map { arr.optDouble(it) }.filter { !it.isNaN() }
                if (values.isEmpty()) {
                    return@withContext TerrainInfo(errorMessage = "Μη διαθέσιμο υψόμετρο")
                }

                val center = values.first()
                val minVal = values.min()
                val maxVal = values.max()
                val maxDiffFromCenter = values.drop(1).maxOfOrNull { abs(it - center) } ?: 0.0
                val slopePercent = (maxDiffFromCenter / 245.0) * 100.0

                val morphology = when {
                    slopePercent < 3.0 -> "Πεδινό / Επίπεδο"
                    slopePercent < 10.0 -> "Ήπιας Κλίσης / Λοφώδες"
                    slopePercent < 20.0 -> "Έντονο Ανάγλυφο"
                    else -> "Απόκρημνο / Ορεινό"
                }

                TerrainInfo(
                    groundElevationMeters = center,
                    minAreaElevationMeters = minVal,
                    maxAreaElevationMeters = maxVal,
                    maxSlopePercent = slopePercent,
                    morphologyLabel = morphology
                )
            }
        } catch (e: Exception) {
            TerrainInfo(errorMessage = "Αδυναμία λήψης υψομέτρου: ${e.localizedMessage ?: "Σφάλμα δικτύου"}")
        }
    }
}
