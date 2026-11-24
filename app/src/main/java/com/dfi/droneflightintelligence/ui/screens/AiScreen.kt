package com.dfi.droneflightintelligence.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dfi.droneflightintelligence.data.model.AiSourceMode
import com.dfi.droneflightintelligence.ui.theme.*
import com.dfi.droneflightintelligence.ui.viewmodel.MainViewModel
import java.util.concurrent.Executors

@Composable
fun AiScreen(viewModel: MainViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val sourceMode by viewModel.aiSourceMode.collectAsState()
    val detections by viewModel.aiDetections.collectAsState()
    val capturedFrame by viewModel.capturedScreenFrame.collectAsState()

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var lastAnalyzedMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DfiBg)
    ) {
        Surface(color = DfiSurface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "AI Ανίχνευση Εμποδίων",
                    color = DfiTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    "Ενεργό μοντέλο: ${viewModel.aiDetector.activeModelName}",
                    color = DfiTextMuted,
                    fontSize = 11.sp
                )
                viewModel.aiDetector.initError?.let { err ->
                    Text(err, color = DfiRed, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiSourceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = sourceMode == mode,
                            onClick = { viewModel.setAiSourceMode(mode) },
                            label = { Text(mode.labelEl, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (sourceMode == AiSourceMode.CAMERA) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()
                                .also { imageAnalysis ->
                                    imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                                        val now = System.currentTimeMillis()
                                        if (now - lastAnalyzedMs >= 250L) {
                                            lastAnalyzedMs = now
                                            val bmp = proxy.toBitmap()
                                            viewModel.analyzeBitmapForObstacles(bmp)
                                        }
                                        proxy.close()
                                    }
                                }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            } catch (_: Exception) {
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )
            } else {
                val frame = capturedFrame
                if (frame != null) {
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "Ροή οθόνης DJI Fly",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Ενεργοποιήστε τη λήψη DJI Fly από την καρτέλα «Πτήση»",
                            color = DfiTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val textPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 34f
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
                val bgPaint = Paint().apply {
                    style = Paint.Style.FILL
                }

                detections.forEach { det ->
                    val left = det.boundingBoxNormalized.left * size.width
                    val top = det.boundingBoxNormalized.top * size.height
                    val right = det.boundingBoxNormalized.right * size.width
                    val bottom = det.boundingBoxNormalized.bottom * size.height

                    val boxColor = if (det.isCriticalObstacle) DfiRed else DfiCyan
                    drawRect(
                        color = boxColor,
                        topLeft = Offset(left, top),
                        size = Size((right - left).coerceAtLeast(4f), (bottom - top).coerceAtLeast(4f)),
                        style = Stroke(width = 5f)
                    )

                    val labelText = "${det.categoryEl} ${(det.confidence * 100).toInt()}%"
                    val textWidth = textPaint.measureText(labelText)
                    bgPaint.color = if (det.isCriticalObstacle) {
                        android.graphics.Color.argb(210, 200, 30, 50)
                    } else {
                        android.graphics.Color.argb(210, 0, 140, 170)
                    }

                    val labelTop = (top - 44f).coerceAtLeast(0f)
                    drawContext.canvas.nativeCanvas.drawRect(
                        left,
                        labelTop,
                        left + textWidth + 16f,
                        labelTop + 42f,
                        bgPaint
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        left + 8f,
                        labelTop + 31f,
                        textPaint
                    )
                }
            }

            Surface(
                color = DfiBg.copy(alpha = 0.85f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    "Ενεργές ανιχνεύσεις: ${detections.size}",
                    color = if (detections.any { it.isCriticalObstacle }) DfiRed else DfiGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
