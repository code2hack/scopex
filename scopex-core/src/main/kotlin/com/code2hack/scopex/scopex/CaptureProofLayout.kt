package com.code2hack.scopex.scopex

import kotlin.math.min
import kotlin.math.roundToInt

private const val PHYSICAL_SCOPE_SHORT_SIDE_VIEW_FRACTION = 1f / 3f
private const val PHYSICAL_SCOPE_ASPECT_WIDTH = 4f
private const val PHYSICAL_SCOPE_ASPECT_HEIGHT = 3f
private const val DEFAULT_CAPTURE_PROOF_MAX_YAW_DEGREES = 30f
private const val DEFAULT_CAPTURE_PROOF_MAX_PITCH_DEGREES = 20f

enum class ScopeXCaptureProofCrosshairAnchor {
    Center,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

data class ScopeXCaptureProofLayout(
    val frameSize: IntSize,
    val viewSize: IntSize,
    val physicalScopeContentSize: IntSize,
    val scopeState: ScopeXState,
    val logicalDisplayDrawRect: FloatRect,
    val paddedLogicalDisplayDrawRect: FloatRect,
    val physicalScopeDrawRect: FloatRect,
    val crosshairDrawPoint: FloatPoint,
)

object ScopeXCaptureProofLayoutCalculator {
    fun layout(
        frameSize: IntSize,
        viewSize: IntSize,
        crosshairAnchor: ScopeXCaptureProofCrosshairAnchor,
    ): ScopeXCaptureProofLayout {
        require(frameSize.width >= 2 && frameSize.height >= 2) { "frameSize must be at least 2x2" }
        require(viewSize.width >= 2 && viewSize.height >= 2) { "viewSize must be at least 2x2" }

        val physicalScopeDrawSize = physicalScopeDrawSize(viewSize)
        val scale = min(
            (viewSize.width - physicalScopeDrawSize.width).toFloat() / frameSize.width,
            (viewSize.height - physicalScopeDrawSize.height).toFloat() / frameSize.height,
        )
        require(scale > 0f) { "view must have room for logical display and physical scope" }

        val physicalScopeContentSize = IntSize(
            width = (physicalScopeDrawSize.width / scale).roundToInt().coerceAtLeast(1),
            height = (physicalScopeDrawSize.height / scale).roundToInt().coerceAtLeast(1),
        )
        val scopeState = ScopeXMapper(
            ScopeXConfig(
                contentSize = frameSize,
                physicalScopeSize = physicalScopeContentSize,
                maxYawDegrees = DEFAULT_CAPTURE_PROOF_MAX_YAW_DEGREES,
                maxPitchDegrees = DEFAULT_CAPTURE_PROOF_MAX_PITCH_DEGREES,
            ),
        ).stateForCrosshairContentPoint(crosshairAnchor.contentPoint(frameSize))

        val paddedWidth = frameSize.width * scale + physicalScopeDrawSize.width
        val paddedHeight = frameSize.height * scale + physicalScopeDrawSize.height
        val logicalLeft = (viewSize.width - paddedWidth) / 2f + physicalScopeDrawSize.width / 2f
        val logicalTop = (viewSize.height - paddedHeight) / 2f + physicalScopeDrawSize.height / 2f
        val logicalDisplayDrawRect = FloatRect(
            left = logicalLeft,
            top = logicalTop,
            right = logicalLeft + frameSize.width * scale,
            bottom = logicalTop + frameSize.height * scale,
        )

        return ScopeXCaptureProofLayout(
            frameSize = frameSize,
            viewSize = viewSize,
            physicalScopeContentSize = physicalScopeContentSize,
            scopeState = scopeState,
            logicalDisplayDrawRect = logicalDisplayDrawRect,
            paddedLogicalDisplayDrawRect = scopeState.paddedLogicalDisplayRect.toDrawRect(logicalDisplayDrawRect, scale),
            physicalScopeDrawRect = scopeState.physicalScopeRect.toDrawRect(logicalDisplayDrawRect, scale),
            crosshairDrawPoint = scopeState.crosshairContentPoint.toDrawPoint(logicalDisplayDrawRect, scale),
        )
    }

    private fun physicalScopeDrawSize(viewSize: IntSize): IntSize {
        val height = (min(viewSize.width, viewSize.height) * PHYSICAL_SCOPE_SHORT_SIDE_VIEW_FRACTION)
            .roundToInt()
            .coerceAtLeast(1)
        return IntSize(
            width = (height * PHYSICAL_SCOPE_ASPECT_WIDTH / PHYSICAL_SCOPE_ASPECT_HEIGHT)
                .roundToInt()
                .coerceAtLeast(1),
            height = height,
        )
    }
}

private fun ScopeXCaptureProofCrosshairAnchor.contentPoint(frameSize: IntSize): FloatPoint =
    when (this) {
        ScopeXCaptureProofCrosshairAnchor.Center ->
            FloatPoint(frameSize.width / 2f, frameSize.height / 2f)
        ScopeXCaptureProofCrosshairAnchor.TopLeft ->
            FloatPoint(0f, 0f)
        ScopeXCaptureProofCrosshairAnchor.TopRight ->
            FloatPoint(frameSize.width.toFloat(), 0f)
        ScopeXCaptureProofCrosshairAnchor.BottomLeft ->
            FloatPoint(0f, frameSize.height.toFloat())
        ScopeXCaptureProofCrosshairAnchor.BottomRight ->
            FloatPoint(frameSize.width.toFloat(), frameSize.height.toFloat())
    }

private fun FloatRect.toDrawRect(
    logicalDisplayDrawRect: FloatRect,
    scale: Float,
): FloatRect =
    FloatRect(
        left = logicalDisplayDrawRect.left + left * scale,
        top = logicalDisplayDrawRect.top + top * scale,
        right = logicalDisplayDrawRect.left + right * scale,
        bottom = logicalDisplayDrawRect.top + bottom * scale,
    )

private fun FloatPoint.toDrawPoint(
    logicalDisplayDrawRect: FloatRect,
    scale: Float,
): FloatPoint =
    FloatPoint(
        x = logicalDisplayDrawRect.left + x * scale,
        y = logicalDisplayDrawRect.top + y * scale,
    )
