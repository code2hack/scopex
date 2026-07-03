package com.code2hack.scopex.scopex

data class IntSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "width must be > 0" }
        require(height > 0) { "height must be > 0" }
    }
}

data class FloatPoint(
    val x: Float,
    val y: Float,
)

data class FloatRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class PoseDelta(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float = 0f,
)

data class ScopeXConfig(
    val contentSize: IntSize,
    val physicalScopeSize: IntSize,
    val maxYawDegrees: Float,
    val maxPitchDegrees: Float,
) {
    init {
        require(maxYawDegrees > 0f) { "maxYawDegrees must be > 0" }
        require(maxPitchDegrees > 0f) { "maxPitchDegrees must be > 0" }
    }
}

data class ScopeXState(
    val crosshairContentPoint: FloatPoint,
    val physicalScopeRect: FloatRect,
    val logicalDisplayRect: FloatRect,
    val paddedLogicalDisplayRect: FloatRect,
)
