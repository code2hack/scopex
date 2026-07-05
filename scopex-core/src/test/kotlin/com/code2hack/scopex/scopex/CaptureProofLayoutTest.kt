package com.code2hack.scopex.scopex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CaptureProofLayoutTest {
    private val frameSize = IntSize(1920, 1080)
    private val viewSize = IntSize(1200, 900)

    @Test
    fun capturedFrameSizeIsLogicalDisplaySize() {
        val layout = layout()

        assertEquals(frameSize, layout.frameSize)
        assertEquals(FloatRect(0f, 0f, 1920f, 1080f), layout.scopeState.logicalDisplayRect)
        assertEquals(FloatRect(200f, 225f, 1000f, 675f), layout.logicalDisplayDrawRect)
        assertPointEquals(FloatPoint(600f, 450f), layout.crosshairDrawPoint)
    }

    @Test
    fun paddedLogicalDisplayAddsHalfPhysicalScopeOnEverySide() {
        val layout = layout()

        assertEquals(IntSize(960, 720), layout.physicalScopeContentSize)
        assertEquals(FloatRect(-480f, -360f, 2400f, 1440f), layout.scopeState.paddedLogicalDisplayRect)
        assertEquals(FloatRect(0f, 75f, 1200f, 825f), layout.paddedLogicalDisplayDrawRect)
        assertEquals(FloatRect(400f, 300f, 800f, 600f), layout.physicalScopeDrawRect)
    }

    @Test
    fun cornerAnchorsExposeDisplayPaddingAndScopeBounds() {
        val topLeft = layout(ScopeXCaptureProofCrosshairAnchor.TopLeft)
        val bottomRight = layout(ScopeXCaptureProofCrosshairAnchor.BottomRight)

        assertPointEquals(FloatPoint(200f, 225f), topLeft.crosshairDrawPoint)
        assertEquals(FloatRect(0f, 75f, 400f, 375f), topLeft.physicalScopeDrawRect)
        assertPointEquals(FloatPoint(1000f, 675f), bottomRight.crosshairDrawPoint)
        assertEquals(FloatRect(800f, 525f, 1200f, 825f), bottomRight.physicalScopeDrawRect)
    }

    @Test
    fun invalidTinyFrameAndViewSizesFailClearly() {
        val frameError = assertFailsWith<IllegalArgumentException> {
            ScopeXCaptureProofLayoutCalculator.layout(
                frameSize = IntSize(1, 1080),
                viewSize = viewSize,
                crosshairAnchor = ScopeXCaptureProofCrosshairAnchor.Center,
            )
        }
        val viewError = assertFailsWith<IllegalArgumentException> {
            ScopeXCaptureProofLayoutCalculator.layout(
                frameSize = frameSize,
                viewSize = IntSize(1, 900),
                crosshairAnchor = ScopeXCaptureProofCrosshairAnchor.Center,
            )
        }

        assertEquals("frameSize must be at least 2x2", frameError.message)
        assertEquals("viewSize must be at least 2x2", viewError.message)
    }

    private fun layout(
        crosshairAnchor: ScopeXCaptureProofCrosshairAnchor = ScopeXCaptureProofCrosshairAnchor.Center,
    ): ScopeXCaptureProofLayout =
        ScopeXCaptureProofLayoutCalculator.layout(
            frameSize = frameSize,
            viewSize = viewSize,
            crosshairAnchor = crosshairAnchor,
        )

    private fun assertPointEquals(expected: FloatPoint, actual: FloatPoint) {
        assertEquals(expected.x, actual.x, absoluteTolerance = 0.01f)
        assertEquals(expected.y, actual.y, absoluteTolerance = 0.01f)
    }
}
