package com.code2hack.scopex.scopex

const val DEFAULT_ACTIVE_SOURCE_IDLE_TIMEOUT_MILLIS: Long = 500L
const val DEFAULT_EDGE_ZONE_SIZE: Float = 64f

enum class ScopeXInputSource {
    Glasses,
    Remote,
    Debug,
}

enum class ScopeXMovementAxis {
    Horizontal,
    Vertical,
}

enum class ScopeXEdgeScrollDirection {
    Left,
    Right,
    Up,
    Down,
}

data class ScopeXSourceLock(
    val activeSource: ScopeXInputSource? = null,
    val ownsActions: Boolean = false,
    val activeSourceIdleTimeoutMillis: Long = DEFAULT_ACTIVE_SOURCE_IDLE_TIMEOUT_MILLIS,
)

sealed interface ScopeXInteractionState {
    val sourceLock: ScopeXSourceLock

    data class LiveScope(
        val crosshairContentPoint: FloatPoint,
        val logicalDisplaySize: IntSize,
        val edgeScrollDirection: ScopeXEdgeScrollDirection? = null,
        val lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
        val edgeZoneSize: Float = DEFAULT_EDGE_ZONE_SIZE,
        override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
    ) : ScopeXInteractionState

    data class Recording(
        override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
    ) : ScopeXInteractionState

    data class InputCachePanelOpen(
        override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
    ) : ScopeXInteractionState
}

sealed interface ScopeXEvent {
    sealed interface Canonical : ScopeXEvent {
        val source: ScopeXInputSource

        data class ClickCrosshair(
            override val source: ScopeXInputSource,
        ) : Canonical

        data class HoldCrosshair(
            override val source: ScopeXInputSource,
        ) : Canonical

        data class MoveHeldCrosshair(
            override val source: ScopeXInputSource,
        ) : Canonical

        data class ScrollAtCrosshair(
            override val source: ScopeXInputSource,
            val delta: FloatPoint,
        ) : Canonical

        data class ZoomAtCrosshair(
            override val source: ScopeXInputSource,
            val scaleFactor: Float,
        ) : Canonical
    }

    sealed interface Result : ScopeXEvent {
        data class CrosshairMoved(
            val crosshairContentPoint: FloatPoint,
            val dominantMovementAxis: ScopeXMovementAxis,
        ) : Result
    }

    sealed interface Timer : ScopeXEvent {
        data object ActiveSourceIdleTimeout : Timer
    }

    sealed interface Configuration : ScopeXEvent {
        data class SetActiveSourceIdleTimeout(
            val timeoutMillis: Long,
        ) : Configuration {
            init {
                require(timeoutMillis > 0) {
                    "active source idle timeout must be positive"
                }
            }
        }

        data class SetEdgeZoneSize(
            val size: Float,
        ) : Configuration {
            init {
                require(size > 0f) { "edge zone size must be positive" }
            }
        }
    }
}

sealed interface ScopeXEffectCommand {
    data class InjectClick(
        val crosshairContentPoint: FloatPoint,
    ) : ScopeXEffectCommand

    data class InjectLongPress(
        val crosshairContentPoint: FloatPoint,
    ) : ScopeXEffectCommand

    data class InjectHeldMove(
        val crosshairContentPoint: FloatPoint,
    ) : ScopeXEffectCommand

    data class InjectScroll(
        val crosshairContentPoint: FloatPoint,
        val delta: FloatPoint,
    ) : ScopeXEffectCommand

    data class InjectZoom(
        val crosshairContentPoint: FloatPoint,
        val scaleFactor: Float,
    ) : ScopeXEffectCommand

    data class StartEdgeScroll(
        val direction: ScopeXEdgeScrollDirection,
    ) : ScopeXEffectCommand

    data object StopEdgeScroll : ScopeXEffectCommand
}

data class ScopeXTransition(
    val state: ScopeXInteractionState,
    val effects: List<ScopeXEffectCommand> = emptyList(),
)

object ScopeXReducer {
    fun reduce(
        state: ScopeXInteractionState,
        event: ScopeXEvent,
    ): ScopeXTransition =
        when {
            event is ScopeXEvent.Configuration.SetActiveSourceIdleTimeout ->
                ScopeXTransition(
                    state.withSourceLock(
                        state.sourceLock.copy(
                            activeSourceIdleTimeoutMillis = event.timeoutMillis,
                        ),
                    ),
                )

            event is ScopeXEvent.Configuration.SetEdgeZoneSize ->
                when (state) {
                    is ScopeXInteractionState.LiveScope ->
                        ScopeXTransition(state.copy(edgeZoneSize = event.size))
                    else -> ScopeXTransition(state)
                }

            event == ScopeXEvent.Timer.ActiveSourceIdleTimeout ->
                ScopeXTransition(state.withSourceLock(state.sourceLock.release()))

            event is ScopeXEvent.Result.CrosshairMoved ->
                reduceCrosshairMoved(state, event)

            event is ScopeXEvent.Canonical ->
                reduceLiveScopeCanonical(state, event)

            else -> ScopeXTransition(state)
        }

    private fun reduceLiveScopeCanonical(
        state: ScopeXInteractionState,
        event: ScopeXEvent.Canonical,
    ): ScopeXTransition {
        if (!state.sourceLock.accepts(event.source)) {
            return ScopeXTransition(state)
        }

        val nextState = state.withSourceLock(state.sourceLock.acquire(event.source))
        if (nextState !is ScopeXInteractionState.LiveScope) {
            return ScopeXTransition(state)
        }

        val effect = when (event) {
            is ScopeXEvent.Canonical.ClickCrosshair ->
                ScopeXEffectCommand.InjectClick(nextState.crosshairContentPoint)
            is ScopeXEvent.Canonical.HoldCrosshair ->
                ScopeXEffectCommand.InjectLongPress(nextState.crosshairContentPoint)
            is ScopeXEvent.Canonical.MoveHeldCrosshair ->
                ScopeXEffectCommand.InjectHeldMove(nextState.crosshairContentPoint)
            is ScopeXEvent.Canonical.ScrollAtCrosshair ->
                ScopeXEffectCommand.InjectScroll(nextState.crosshairContentPoint, event.delta)
            is ScopeXEvent.Canonical.ZoomAtCrosshair ->
                ScopeXEffectCommand.InjectZoom(nextState.crosshairContentPoint, event.scaleFactor)
        }

        return ScopeXTransition(nextState, listOf(effect))
    }

    private fun reduceCrosshairMoved(
        state: ScopeXInteractionState,
        event: ScopeXEvent.Result.CrosshairMoved,
    ): ScopeXTransition {
        if (state !is ScopeXInteractionState.LiveScope) {
            return ScopeXTransition(state)
        }

        val direction = edgeScrollDirection(
            point = event.crosshairContentPoint,
            logicalDisplaySize = state.logicalDisplaySize,
            edgeZoneSize = state.edgeZoneSize,
            dominantMovementAxis = event.dominantMovementAxis,
        )
        val nextState = state.copy(
            crosshairContentPoint = event.crosshairContentPoint,
            edgeScrollDirection = direction,
            lastDominantMovementAxis = event.dominantMovementAxis,
        )
        val effects = when {
            state.edgeScrollDirection == direction -> emptyList()
            direction == null -> listOf(ScopeXEffectCommand.StopEdgeScroll)
            else -> listOf(ScopeXEffectCommand.StartEdgeScroll(direction))
        }

        return ScopeXTransition(nextState, effects)
    }
}

private fun ScopeXSourceLock.accepts(source: ScopeXInputSource): Boolean =
    !ownsActions || activeSource == null || activeSource == source

private fun ScopeXSourceLock.acquire(source: ScopeXInputSource): ScopeXSourceLock =
    copy(activeSource = source, ownsActions = true)

private fun ScopeXSourceLock.release(): ScopeXSourceLock =
    copy(activeSource = null, ownsActions = false)

private fun ScopeXInteractionState.withSourceLock(
    sourceLock: ScopeXSourceLock,
): ScopeXInteractionState =
    when (this) {
        is ScopeXInteractionState.LiveScope -> copy(sourceLock = sourceLock)
        is ScopeXInteractionState.Recording -> copy(sourceLock = sourceLock)
        is ScopeXInteractionState.InputCachePanelOpen -> copy(sourceLock = sourceLock)
    }

private fun edgeScrollDirection(
    point: FloatPoint,
    logicalDisplaySize: IntSize,
    edgeZoneSize: Float,
    dominantMovementAxis: ScopeXMovementAxis,
): ScopeXEdgeScrollDirection? {
    val horizontal = when {
        point.x <= edgeZoneSize -> ScopeXEdgeScrollDirection.Left
        point.x >= logicalDisplaySize.width - edgeZoneSize -> ScopeXEdgeScrollDirection.Right
        else -> null
    }
    val vertical = when {
        point.y <= edgeZoneSize -> ScopeXEdgeScrollDirection.Up
        point.y >= logicalDisplaySize.height - edgeZoneSize -> ScopeXEdgeScrollDirection.Down
        else -> null
    }

    return when {
        horizontal != null && vertical != null ->
            if (dominantMovementAxis == ScopeXMovementAxis.Horizontal) horizontal else vertical
        horizontal != null -> horizontal
        else -> vertical
    }
}
