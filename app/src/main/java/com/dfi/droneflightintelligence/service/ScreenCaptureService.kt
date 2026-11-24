package com.dfi.droneflightintelligence.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.dfi.droneflightintelligence.data.model.DroneTelemetry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenCaptureBus {
    private val _telemetry = MutableStateFlow(DroneTelemetry())
    val telemetry = _telemetry.asStateFlow()

    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame = _latestFrame.asStateFlow()

    fun publishTelemetry(data: DroneTelemetry) {
        _telemetry.value = data
    }

    fun publishFrame(bitmap: Bitmap?) {
        _latestFrame.value = bitmap
    }

    fun reset() {
        _telemetry.value = DroneTelemetry(
            connectionStatus = "Ανενεργή λήψη DJI Fly",
            isScreenCaptureActive = false
        )
        _latestFrame.value = null
    }
}

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ocrParser = DjiFlyOcrParser()
    private var lastAnalyzedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DFI — Ανάλυση DJI Fly")
            .setContentText("Ενεργή ανάγνωση τηλεμετρίας & εμποδίων από οθόνη")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        startProjection(resultCode, data)
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mgr.getMediaProjection(resultCode, data) ?: run {
            stopSelfCleanly()
            return
        }
        mediaProjection = projection

        handlerThread = HandlerThread("DfiScreenCapture").apply { start() }
        bgHandler = Handler(handlerThread!!.looper)

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelfCleanly()
            }
        }, bgHandler)

        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val captureWidth = (metrics.widthPixels / 2).coerceAtLeast(540)
        val captureHeight = (metrics.heightPixels / 2).coerceAtLeast(960)
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (now - lastAnalyzedAt < 850L) return@setOnImageAvailableListener
                lastAnalyzedAt = now

                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * captureWidth

                val paddedBitmap = Bitmap.createBitmap(
                    captureWidth + rowPadding / pixelStride,
                    captureHeight,
                    Bitmap.Config.ARGB_8888
                )
                paddedBitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, captureWidth, captureHeight)
                if (cropped != paddedBitmap) paddedBitmap.recycle()

                ScreenCaptureBus.publishFrame(cropped)

                serviceScope.launch {
                    val telemetry = ocrParser.parseScreenBitmap(cropped)
                    ScreenCaptureBus.publishTelemetry(telemetry)
                }
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }, bgHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "DfiDjiFlyCapture",
            captureWidth,
            captureHeight,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            bgHandler
        )

        ScreenCaptureBus.publishTelemetry(
            DroneTelemetry(
                connectionStatus = "Συνδεδεμένο (MediaProjection ενεργό)",
                isScreenCaptureActive = true,
                lastUpdatedMs = System.currentTimeMillis()
            )
        )
    }

    private fun stopSelfCleanly() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        handlerThread?.quitSafely()
        handlerThread = null
        ScreenCaptureBus.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        ocrParser.close()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DFI Λήψη Οθόνης DJI Fly",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "dfi_screen_capture_channel"
        const val NOTIFICATION_ID = 4102
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val ACTION_STOP = "com.dfi.droneflightintelligence.STOP_CAPTURE"
    }
}
