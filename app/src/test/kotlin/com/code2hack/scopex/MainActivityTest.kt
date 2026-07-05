package com.code2hack.scopex

import kotlin.test.Test
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
}
