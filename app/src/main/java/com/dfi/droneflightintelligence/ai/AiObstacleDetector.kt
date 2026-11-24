package com.dfi.droneflightintelligence.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.dfi.droneflightintelligence.data.model.AiDetectionBox
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

class AiObstacleDetector(context: Context) {

    private var detector: ObjectDetector? = null
    var activeModelName: String = "Μη φορτωμένο"
        private set
    var initError: String? = null
        private set

    init {
        val assetList = context.assets.list("")?.toSet() ?: emptySet()
        val modelAsset = when {
            "drone_obstacles.tflite" in assetList -> "drone_obstacles.tflite"
            "efficientdet_lite0.tflite" in assetList -> "efficientdet_lite0.tflite"
            else -> null
        }

        if (modelAsset == null) {
            initError = "Δεν βρέθηκε μοντέλο .tflite στον φάκελο assets"
        } else {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelAsset)
                    .build()
                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setMaxResults(10)
                    .setScoreThreshold(0.32f)
                    .build()
                detector = ObjectDetector.createFromOptions(context, options)
                activeModelName = modelAsset
            } catch (e: Exception) {
                initError = "Σφάλμα φόρτωσης AI μοντέλου: ${e.localizedMessage}"
            }
        }
    }

    fun detect(bitmap: Bitmap): List<AiDetectionBox> {
        val activeDetector = detector ?: return emptyList()
        val w = bitmap.width.toFloat().coerceAtLeast(1f)
        val h = bitmap.height.toFloat().coerceAtLeast(1f)

        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = activeDetector.detect(mpImage)

        return result.detections().mapNotNull { det ->
            val topCat = det.categories().firstOrNull() ?: return@mapNotNull null
            val rawLabel = topCat.categoryName().lowercase()
            val box = det.boundingBox()
            val normalized = RectF(
                (box.left / w).coerceIn(0f, 1f),
                (box.top / h).coerceIn(0f, 1f),
                (box.right / w).coerceIn(0f, 1f),
                (box.bottom / h).coerceIn(0f, 1f)
            )
            val (labelEl, critical) = mapCategoryToGreek(rawLabel)
            AiDetectionBox(
                boundingBoxNormalized = normalized,
                categoryEl = labelEl,
                rawLabel = rawLabel,
                confidence = topCat.score(),
                isCriticalObstacle = critical
            )
        }
    }

    private fun mapCategoryToGreek(raw: String): Pair<String, Boolean> = when (raw) {
        "pylon", "power_tower", "transmission_tower" -> "Πυλώνας Ηλ. Ρεύματος" to true
        "power_line", "cable", "wire" -> "Καλώδιο / Γραμμή Τάσης" to true
        "antenna", "telecom_tower", "cell_tower" -> "Κεραία Τηλεπικοινωνιών" to true
        "bridge" -> "Γέφυρα" to true
        "building", "house" -> "Κτίριο" to true
        "tree", "potted plant" -> "Δέντρο / Βλάστηση" to true
        "bird" -> "Πτηνό (Εναέριο Εμπόδιο)" to true
        "airplane", "helicopter" -> "Αεροσκάφος" to true
        "traffic light" -> "Ιστός / Σηματοδότης" to true
        "person" -> "Άνθρωπος" to true
        "car", "truck", "bus" -> "Όχημα ($raw)" to false
        else -> "Αντικείμενο ($raw)" to false
    }

    fun close() {
        detector?.close()
        detector = null
    }
}
