package com.code2hack.scopex

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.code2hack.scopex.scopex.ScopeXCaptureProofCrosshairAnchor

class MainActivity : Activity() {
    private lateinit var captureView: CaptureProofView
    private lateinit var statusText: TextView
    private lateinit var activeIndicator: TextView
    private var captureActive = false
    private var captureRequestInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        CaptureProofFrameBus.setListener { frame ->
            captureView.replaceFrame(frame)
        }
        CaptureProofFrameBus.setStopListener { reason ->
            captureView.clearFrame()
            captureRequestInFlight = false
            setCaptureState(active = false, status = getString(statusForStopReason(reason)))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        captureRequestInFlight = false
        if (resultCode != RESULT_OK || data == null) {
            setCaptureState(active = false, status = getString(R.string.capture_status_denied))
            return
        }

        startForegroundService(ScopeXCaptureService.startIntent(this, resultCode, data))
        setCaptureState(active = true, status = getString(R.string.capture_status_active))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestCaptureConsent()
        } else {
            captureRequestInFlight = false
            setCaptureState(
                active = false,
                status = getString(R.string.capture_status_notification_denied),
            )
        }
    }

    override fun onStop() {
        captureRequestInFlight = false
        if (captureActive) stopCapture(getString(R.string.capture_status_stopped))
        super.onStop()
    }

    override fun onDestroy() {
        captureRequestInFlight = false
        CaptureProofFrameBus.setListener(null)
        CaptureProofFrameBus.setStopListener(null)
        captureView.clearFrame()
        super.onDestroy()
    }

    private fun buildContent(): View {
        captureView = CaptureProofView(this)
        statusText = label(getString(R.string.capture_status_idle), 16f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        activeIndicator = label("ACTIVE CAPTURE", 14f, Typeface.BOLD).apply {
            setTextColor(Color.rgb(160, 0, 0))
            visibility = View.GONE
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(label("scopex logical-display capture proof", 22f, Typeface.BOLD))
            addView(statusText)
            addView(activeIndicator)
            addView(captureView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
            addView(buttonRow(
                button("Start Capture") { startCapture() },
                button("Stop Capture") { stopCapture(getString(R.string.capture_status_stopped)) },
            ))
            addView(buttonRow(
                button("Center") {
                    captureView.setCrosshairAnchor(ScopeXCaptureProofCrosshairAnchor.Center)
                },
                button("Top Left") {
                    captureView.setCrosshairAnchor(ScopeXCaptureProofCrosshairAnchor.TopLeft)
                },
                button("Top Right") {
                    captureView.setCrosshairAnchor(ScopeXCaptureProofCrosshairAnchor.TopRight)
                },
                button("Bottom Left") {
                    captureView.setCrosshairAnchor(ScopeXCaptureProofCrosshairAnchor.BottomLeft)
                },
                button("Bottom Right") {
                    captureView.setCrosshairAnchor(ScopeXCaptureProofCrosshairAnchor.BottomRight)
                },
            ))
            addView(label("Build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 14f))
        }
    }

    private fun startCapture() {
        if (captureActive || captureRequestInFlight) return
        captureRequestInFlight = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        requestCaptureConsent()
    }

    private fun requestCaptureConsent() {
        captureRequestInFlight = true
        setCaptureState(active = false, status = getString(R.string.capture_status_requesting))
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        val launched = requestCaptureConsentSafely(
            isAvailable = { intent.hasLaunchableActivity(packageManager) },
            launch = { startActivityForResult(intent, REQUEST_CAPTURE) },
            onUnavailable = {
                captureRequestInFlight = false
                setCaptureState(
                    active = false,
                    status = getString(R.string.capture_status_unavailable),
                )
            },
        )
        if (!launched) return
    }

    private fun stopCapture(status: String) {
        captureRequestInFlight = false
        stopService(ScopeXCaptureService.stopIntent(this))
        CaptureProofFrameBus.clear()
        captureView.clearFrame()
        setCaptureState(active = false, status = status)
    }

    private fun setCaptureState(active: Boolean, status: String) {
        captureActive = active
        statusText.text = status
        activeIndicator.visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun label(
        value: String,
        sizeSp: Float = 18f,
        style: Int = Typeface.NORMAL,
    ) = TextView(this).apply {
        text = value
        textSize = sizeSp
        typeface = if (style == Typeface.BOLD) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        gravity = Gravity.START
        setPadding(0, 0, 0, 14)
    }

    private fun button(value: String, onClick: () -> Unit) =
        Button(this).apply {
            text = value
            setOnClickListener { onClick() }
        }

    private fun buttonRow(vararg buttons: Button) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (button in buttons) {
                addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

    companion object {
        private const val REQUEST_CAPTURE = 10
        private const val REQUEST_NOTIFICATIONS = 11

        internal fun statusForStopReason(reason: CaptureProofStopReason): Int =
            when (reason) {
                CaptureProofStopReason.Stopped -> R.string.capture_status_stopped
                CaptureProofStopReason.Error -> R.string.capture_status_error
            }
    }
}

private fun Intent.hasLaunchableActivity(packageManager: PackageManager): Boolean {
    val explicitComponent = component
    return if (explicitComponent == null) {
        resolveActivity(packageManager) != null
    } else {
        packageManager.hasActivity(explicitComponent)
    }
}

@Suppress("DEPRECATION")
private fun PackageManager.hasActivity(component: ComponentName): Boolean =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else {
            getActivityInfo(component, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

internal fun requestCaptureConsentSafely(
    isAvailable: () -> Boolean = { true },
    launch: () -> Unit,
    onUnavailable: () -> Unit,
): Boolean =
    if (!isAvailable()) {
        onUnavailable()
        false
    } else try {
        launch()
        true
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
        false
    }
