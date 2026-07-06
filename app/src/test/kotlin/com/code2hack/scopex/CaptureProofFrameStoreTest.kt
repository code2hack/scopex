package com.code2hack.scopex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureProofFrameStoreTest {
    @Test
    fun replaceKeepsOnlyLatestFrame() {
        val store = CaptureProofFrameStore<String>()

        store.replace("first")
        store.replace("second")

        assertEquals("second", store.latest)
    }

    @Test
    fun clearRemovesLatestFrame() {
        val store = CaptureProofFrameStore<String>()

        store.replace("frame")
        store.clear()

        assertNull(store.latest)
    }
}
