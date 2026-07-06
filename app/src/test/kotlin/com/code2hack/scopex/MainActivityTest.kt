package com.code2hack.scopex

import android.content.ActivityNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainActivityTest {
    @Test
    fun startCaptureDoesNothingWhileCaptureIsActive() {
        val activity = MainActivity()
        val activeField = MainActivity::class.java.getDeclaredField("captureActive").apply {
            isAccessible = true
        }
        val startCapture = MainActivity::class.java.getDeclaredMethod("startCapture").apply {
            isAccessible = true
        }

        activeField.set(activity, true)

        startCapture.invoke(activity)

        assertTrue(activeField.getBoolean(activity))
    }

    @Test
    fun startCaptureDoesNothingWhileCaptureRequestIsInFlight() {
        val activity = MainActivity()
        val inFlightField = MainActivity::class.java.getDeclaredField("captureRequestInFlight").apply {
            isAccessible = true
        }
        val startCapture = MainActivity::class.java.getDeclaredMethod("startCapture").apply {
            isAccessible = true
        }

        inFlightField.set(activity, true)

        startCapture.invoke(activity)

        assertTrue(inFlightField.getBoolean(activity))
    }

    @Test
    fun stopReasonMapsToStoppedOrErrorStatus() {
        assertEquals(
            R.string.capture_status_stopped,
            MainActivity.statusForStopReason(CaptureProofStopReason.Stopped),
        )
        assertEquals(
            R.string.capture_status_error,
            MainActivity.statusForStopReason(CaptureProofStopReason.Error),
        )
    }

    @Test
    fun missingCaptureConsentActivityReportsUnavailableStatus() {
        var launchAttempted = false
        var unavailableStatus: Int? = null

        val launched = requestCaptureConsentSafely(
            isAvailable = { false },
            launch = { launchAttempted = true },
            onUnavailable = { unavailableStatus = R.string.capture_status_unavailable },
        )

        assertFalse(launched)
        assertFalse(launchAttempted)
        assertEquals(R.string.capture_status_unavailable, unavailableStatus)
    }

    @Test
    fun missingCaptureConsentActivityExceptionReportsUnavailableStatus() {
        var unavailableStatus: Int? = null

        val launched = requestCaptureConsentSafely(
            launch = { throw ActivityNotFoundException("missing consent activity") },
            onUnavailable = { unavailableStatus = R.string.capture_status_unavailable },
        )

        assertFalse(launched)
        assertEquals(R.string.capture_status_unavailable, unavailableStatus)
    }
}
