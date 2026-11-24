package com.dfi.droneflightintelligence.service

import android.graphics.Bitmap
import com.dfi.droneflightintelligence.data.model.DroneTelemetry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class DjiFlyOcrParser {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val altRegex = Regex("""(?:^|\b)H\s*[:=]?\s*(-?\d+(?:[.,]\d+)?)\s*m\b""", RegexOption.IGNORE_CASE)
    private val distRegex = Regex("""(?:^|\b)D\s*[:=]?\s*(\d+(?:[.,]\d+)?)\s*m\b""", RegexOption.IGNORE_CASE)
    private val hSpeedRegex = Regex("""H\.?\s*S\s*[:=]?\s*(\d+(?:[.,]\d+)?)\s*(m/s|km/h)?""", RegexOption.IGNORE_CASE)
    private val vSpeedRegex = Regex("""V\.?\s*S\s*[:=]?\s*(-?\d+(?:[.,]\d+)?)\s*(m/s|km/h)?""", RegexOption.IGNORE_CASE)
    private val batteryRegex = Regex("""\b(100|[1-9]?\d)\s*%""")
    private val gpsRegex = Regex("""(?:GPS|SAT|RC)?\s*\b([1-3]?\d)\b""")

    suspend fun parseScreenBitmap(bitmap: Bitmap): DroneTelemetry = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        val w = bitmap.width.toFloat().coerceAtLeast(1f)
        val h = bitmap.height.toFloat().coerceAtLeast(1f)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                var altitude: Double? = null
                var distance: Double? = null
                var hSpeed: Double? = null
                var vSpeed: Double? = null
                var battery: Int? = null
                var sats: Int? = null
                val capturedLines = mutableListOf<String>()

                for (block in result.textBlocks) {
                    for (line in block.lines) {
                        val txt = line.text.trim()
                        if (txt.isBlank()) continue
                        capturedLines.add(txt)

                        val box = line.boundingBox
                        val relX = (box?.centerX() ?: 0) / w
                        val relY = (box?.centerY() ?: 0) / h

                        if (altitude == null) {
                            altRegex.find(txt)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()?.let {
                                if (it in -50.0..1500.0) altitude = it
                            }
                        }
                        if (distance == null) {
                            distRegex.find(txt)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()?.let {
                                if (it in 0.0..20000.0) distance = it
                            }
                        }
                        if (hSpeed == null) {
                            hSpeedRegex.find(txt)?.let { match ->
                                val v = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                                val unit = match.groupValues.getOrNull(2).orEmpty()
                                if (v != null && v in 0.0..100.0) {
                                    hSpeed = if (unit.equals("km/h", true)) v / 3.6 else v
                                }
                            }
                        }
                        if (vSpeed == null) {
                            vSpeedRegex.find(txt)?.let { match ->
                                val v = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                                val unit = match.groupValues.getOrNull(2).orEmpty()
                                if (v != null && v in -30.0..30.0) {
                                    vSpeed = if (unit.equals("km/h", true)) v / 3.6 else v
                                }
                            }
                        }
                        if (battery == null && relY < 0.25f) {
                            batteryRegex.find(txt)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                                if (it in 1..100) battery = it
                            }
                        }
                        if (sats == null && relY < 0.22f && relX > 0.55f && !txt.contains("%") && !txt.contains(":")) {
                            gpsRegex.find(txt)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                                if (it in 4..40) sats = it
                            }
                        }
                    }
                }

                val hasAnyMetric = listOf(altitude, distance, hSpeed, battery, sats).any { it != null }
                val status = if (hasAnyMetric) {
                    "Ενεργή ανάγνωση DJI Fly (OCR)"
                } else {
                    "Ενεργή λήψη οθόνης — ανοίξτε το DJI Fly"
                }

                if (cont.isActive) {
                    cont.resume(
                        DroneTelemetry(
                            altitudeMeters = altitude,
                            distanceMeters = distance,
                            horizontalSpeedMs = hSpeed,
                            verticalSpeedMs = vSpeed,
                            gpsSatellites = sats,
                            batteryPercent = battery,
                            connectionStatus = status,
                            isScreenCaptureActive = true,
                            rawOcrLines = capturedLines.take(15),
                            lastUpdatedMs = System.currentTimeMillis()
                        )
                    )
                }
            }
            .addOnFailureListener {
                if (cont.isActive) {
                    cont.resume(
                        DroneTelemetry(
                            connectionStatus = "Σφάλμα OCR ανάγνωσης",
                            isScreenCaptureActive = true,
                            lastUpdatedMs = System.currentTimeMillis()
                        )
                    )
                }
            }
    }

    fun close() {
        recognizer.close()
    }
}
