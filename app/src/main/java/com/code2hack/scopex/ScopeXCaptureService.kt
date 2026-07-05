package com.code2hack.scopex

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager

class ScopeXCaptureService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapture(stopProjection = false)
        }
    }

    private var captureThread: HandlerThread? = null
    private var imageReader: ImageReader? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isStopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.captureResultData()
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            createNotificationChannel()
            if (mediaProjection != null || imageReader != null || virtualDisplay != null || captureThread != null) {
                stopCapture(finishService = false, notifyStopped = false)
            }
            startForegroundCompat(buildNotification())
            startCapture(resultCode, resultData)
        } catch (_: RuntimeException) {
            stopCapture()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCapture(finishService = false)
        super.onDestroy()
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
            ?: error("MediaProjection unavailable")
        mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)

        val displayMetrics = resources.displayMetrics
        val captureWidth: Int
        val captureHeight: Int
        val densityDpi: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            captureWidth = bounds.width().coerceAtLeast(1)
            captureHeight = bounds.height().coerceAtLeast(1)
            densityDpi = resources.configuration.densityDpi.takeIf { it > 0 } ?: displayMetrics.densityDpi
        } else {
            captureWidth = displayMetrics.widthPixels.coerceAtLeast(1)
            captureHeight = displayMetrics.heightPixels.coerceAtLeast(1)
            densityDpi = displayMetrics.densityDpi
        }
        val reader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            2,
        )
        val thread = HandlerThread("ScopeXCaptureProof").also { it.start() }
        val sessionId = CaptureProofFrameBus.beginSession()
        reader.setOnImageAvailableListener({ reader -> onImageAvailable(reader, sessionId) }, Handler(thread.looper))

        imageReader = reader
        captureThread = thread
        virtualDisplay = projection.createVirtualDisplay(
            "ScopeXCaptureProof",
            captureWidth,
            captureHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            mainHandler,
        )
    }

    private fun onImageAvailable(reader: ImageReader, sessionId: Int) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return
            val frame = image.toBitmap()
            CaptureProofFrameBus.publish(sessionId, frame)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Failed to publish capture frame", error)
        } finally {
            runCatching { image?.close() }
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val width = width
        val height = height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val bitmapWidth = width + (rowStride - pixelStride * width) / pixelStride
        val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)

        if (bitmapWidth == width) return bitmap

        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        bitmap.recycle()
        return cropped
    }

    private fun stopCapture(
        stopProjection: Boolean = true,
        finishService: Boolean = true,
        notifyStopped: Boolean = true,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stopCapture(stopProjection, finishService, notifyStopped) }
            return
        }
        if (isStopping) return
        val shouldNotifyStopped = notifyStopped && (
            finishService ||
                mediaProjection != null ||
                imageReader != null ||
                virtualDisplay != null ||
                captureThread != null
            )
        isStopping = true

        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        imageReader?.close()
        captureThread?.quitSafely()
        CaptureProofFrameBus.clear()

        val projection = mediaProjection
        mediaProjection = null
        projection?.unregisterCallback(projectionCallback)
        if (stopProjection) projection?.stop()

        virtualDisplay = null
        imageReader = null
        captureThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (shouldNotifyStopped) CaptureProofFrameBus.notifyStopped()
        if (finishService) stopSelf()

        isStopping = false
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.capture_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = stopIntent(this)
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.capture_notification_stop),
                    stopPendingIntent,
                ).build(),
            )
            .build()
    }

    @Suppress("DEPRECATION")
    private fun Intent.captureResultData(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            getParcelableExtra(EXTRA_RESULT_DATA)
        }

    companion object {
        const val ACTION_STOP = "com.code2hack.scopex.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val TAG = "ScopeXCaptureService"
        private const val CHANNEL_ID = "scopex_capture"
        private const val NOTIFICATION_ID = 1

        fun startIntent(context: Context, resultCode: Int, resultData: Intent): Intent = Intent(context, ScopeXCaptureService::class.java).putExtra(EXTRA_RESULT_CODE, resultCode).putExtra(EXTRA_RESULT_DATA, resultData)

        fun stopIntent(context: Context): Intent = Intent(context, ScopeXCaptureService::class.java).setAction(ACTION_STOP)
    }
}
