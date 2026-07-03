package com.code2hack.scopex.scopex

class ScopeXMapper(
    private val config: ScopeXConfig,
) {
    fun paddedLogicalDisplayRect(): FloatRect {
        val halfPhysicalScopeWidth = config.physicalScopeSize.width / 2f
        val halfPhysicalScopeHeight = config.physicalScopeSize.height / 2f
        return FloatRect(
            left = -halfPhysicalScopeWidth,
            top = -halfPhysicalScopeHeight,
            right = config.contentSize.width + halfPhysicalScopeWidth,
            bottom = config.contentSize.height + halfPhysicalScopeHeight,
        )
    }

    fun stateForNormalizedPose(x: Float, y: Float): ScopeXState {
        val clampedX = x.coerceIn(-1f, 1f)
        val clampedY = y.coerceIn(-1f, 1f)
        return stateForCrosshairContentPoint(
            FloatPoint(
                x = ((clampedX + 1f) / 2f) * config.contentSize.width,
                y = ((clampedY + 1f) / 2f) * config.contentSize.height,
            ),
        )
    }

    fun stateForPoseDelta(delta: PoseDelta): ScopeXState =
        stateForNormalizedPose(
            x = delta.yawDegrees / config.maxYawDegrees,
            y = delta.pitchDegrees / config.maxPitchDegrees,
        )

    fun stateForCrosshairContentPoint(point: FloatPoint): ScopeXState {
        val crosshair = FloatPoint(
            x = point.x.coerceIn(0f, config.contentSize.width.toFloat()),
            y = point.y.coerceIn(0f, config.contentSize.height.toFloat()),
        )
        val halfPhysicalScopeWidth = config.physicalScopeSize.width / 2f
        val halfPhysicalScopeHeight = config.physicalScopeSize.height / 2f
        val contentRect = FloatRect(
            left = 0f,
            top = 0f,
            right = config.contentSize.width.toFloat(),
            bottom = config.contentSize.height.toFloat(),
        )

        return ScopeXState(
            crosshairContentPoint = crosshair,
            physicalScopeRect = FloatRect(
                left = crosshair.x - halfPhysicalScopeWidth,
                top = crosshair.y - halfPhysicalScopeHeight,
                right = crosshair.x + halfPhysicalScopeWidth,
                bottom = crosshair.y + halfPhysicalScopeHeight,
            ),
            logicalDisplayRect = contentRect,
            paddedLogicalDisplayRect = paddedLogicalDisplayRect(),
        )
    }
}
