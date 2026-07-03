package com.code2hack.scopex.scopex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScopeXMapperTest {
    private val mapper = ScopeXMapper(
        ScopeXConfig(
            contentSize = IntSize(1920, 1080),
            physicalScopeSize = IntSize(640, 480),
            maxYawDegrees = 30f,
            maxPitchDegrees = 20f,
        ),
    )

    @Test
    fun logicalDisplayAddsHalfPhysicalScopeOnEachSide() {
        assertEquals(FloatRect(-320f, -240f, 2240f, 1320f), mapper.logicalDisplayRect())
    }

    @Test
    fun centerPoseMapsCrosshairToContentCenter() {
        val state = mapper.stateForNormalizedPose(0f, 0f)

        assertEquals(FloatPoint(960f, 540f), state.crosshairContentPoint)
        assertEquals(FloatRect(640f, 300f, 1280f, 780f), state.physicalScopeRect)
    }

    @Test
    fun normalizedPoseCanReachAllContentCorners() {
        assertEquals(FloatPoint(0f, 0f), mapper.stateForNormalizedPose(-1f, -1f).crosshairContentPoint)
        assertEquals(FloatPoint(1920f, 0f), mapper.stateForNormalizedPose(1f, -1f).crosshairContentPoint)
        assertEquals(FloatPoint(0f, 1080f), mapper.stateForNormalizedPose(-1f, 1f).crosshairContentPoint)
        assertEquals(FloatPoint(1920f, 1080f), mapper.stateForNormalizedPose(1f, 1f).crosshairContentPoint)
    }

    @Test
    fun physicalScopeExtendsIntoDisplayPaddingAtContentCorners() {
        assertEquals(FloatRect(-320f, -240f, 320f, 240f), mapper.stateForCrosshairContentPoint(FloatPoint(0f, 0f)).physicalScopeRect)
        assertEquals(FloatRect(1600f, 840f, 2240f, 1320f), mapper.stateForCrosshairContentPoint(FloatPoint(1920f, 1080f)).physicalScopeRect)
    }

    @Test
    fun normalizedPoseAndCrosshairPointsClampToContentBounds() {
        assertEquals(FloatPoint(0f, 1080f), mapper.stateForNormalizedPose(-2f, 2f).crosshairContentPoint)
        assertEquals(FloatPoint(1920f, 0f), mapper.stateForCrosshairContentPoint(FloatPoint(5000f, -50f)).crosshairContentPoint)
    }

    @Test
    fun poseDeltaUsesConfiguredYawAndPitchLimits() {
        assertEquals(FloatPoint(1920f, 1080f), mapper.stateForPoseDelta(PoseDelta(30f, 20f)).crosshairContentPoint)
        assertEquals(FloatPoint(0f, 0f), mapper.stateForPoseDelta(PoseDelta(-30f, -20f)).crosshairContentPoint)
    }

    @Test
    fun invalidSizesAndPoseLimitsFailClearly() {
        assertFailsWith<IllegalArgumentException> { IntSize(0, 1080) }
        assertFailsWith<IllegalArgumentException> {
            ScopeXConfig(
                contentSize = IntSize(1920, 1080),
                physicalScopeSize = IntSize(640, 480),
                maxYawDegrees = 0f,
                maxPitchDegrees = 20f,
            )
        }
    }
}
