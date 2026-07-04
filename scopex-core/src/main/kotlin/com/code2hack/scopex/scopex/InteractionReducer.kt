package com.code2hack.scopex.scopex

const val DEFAULT_ACTIVE_SOURCE_IDLE_TIMEOUT_MILLIS: Long = 500L
const val DEFAULT_EDGE_ZONE_SIZE: Float = 64f
const val DEFAULT_INPUT_CACHE_ACTIVE_LIMIT: Int = 50
const val DEFAULT_QUIT_CONFIRMATION_TIMEOUT_MILLIS: Long = 2_000L
const val QUIT_CONFIRMATION_MESSAGE: String = "Double click again to quit ScopeX"

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

data class ScopeXInputCache(
    val entries: List<String> = emptyList(),
    val activeLimit: Int = DEFAULT_INPUT_CACHE_ACTIVE_LIMIT,
    val highlightedIndex: Int? = null,
    val clipboardImportOptedIn: Boolean = false,
    val sessionActive: Boolean = false,
) {
    init {
        require(activeLimit > 0) { "input cache active limit must be positive" }
        require(highlightedIndex == null || highlightedIndex in entries.indices) {
            "highlighted input cache index must reference an entry"
        }
    }

    val clipboardImportActive: Boolean
        get() = clipboardImportOptedIn && sessionActive
}

sealed interface ScopeXInteractionState {
    val sourceLock: ScopeXSourceLock
    val inputCache: ScopeXInputCache

    data class LiveScope(
        val crosshairContentPoint: FloatPoint,
        val logicalDisplaySize: IntSize,
        val edgeScrollDirection: ScopeXEdgeScrollDirection? = null,
        val lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
        val edgeZoneSize: Float = DEFAULT_EDGE_ZONE_SIZE,
        val quitConfirmationActive: Boolean = false,
        val systemMessage: String? = null,
        override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
        override val inputCache: ScopeXInputCache = ScopeXInputCache(),
    ) : ScopeXInteractionState

    data class Recording(
        override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
        override val inputCache: ScopeXInputCache = ScopeXInputCache(),
    ) : ScopeXInteractionState

    data class InputCachePanelOpen(
        val crosshairContentPoint: FloatPoint,
        val logicalDisplaySize: IntSize,
        val lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
        val edgeZoneSize: Float = DEFAULT_EDGE_ZONE_SIZE,
        override val sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
        override val inputCache: ScopeXInputCache = ScopeXInputCache(),
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

        data class ToggleInputCache(
            override val source: ScopeXInputSource,
        ) : Canonical

        data class RecenterScope(
            override val source: ScopeXInputSource,
        ) : Canonical

        data class Escape(
            override val source: ScopeXInputSource,
        ) : Canonical
    }

    sealed interface Result : ScopeXEvent {
        data class AppendInputCacheEntry(
            val text: String,
        ) : Result

        data class CrosshairMoved(
            val crosshairContentPoint: FloatPoint,
            val dominantMovementAxis: ScopeXMovementAxis,
        ) : Result
    }

    sealed interface Timer : ScopeXEvent {
        data object ActiveSourceIdleTimeout : Timer
        data object QuitConfirmationTimeout : Timer
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

        data class SetInputCacheActiveLimit(
            val limit: Int,
        ) : Configuration {
            init {
                require(limit > 0) { "input cache active limit must be positive" }
            }
        }

        data class SetClipboardImportOptedIn(
            val enabled: Boolean,
        ) : Configuration

        data class SetScopeXSessionActive(
            val active: Boolean,
        ) : Configuration
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

    data class RecenterScope(
        val crosshairContentPoint: FloatPoint,
    ) : ScopeXEffectCommand

    data class ShowMessage(
        val message: String,
    ) : ScopeXEffectCommand

    data object ShowEmptyInputCachePrompt : ScopeXEffectCommand

    data object ShowClipboardImportBadge : ScopeXEffectCommand

    data object HideClipboardImportBadge : ScopeXEffectCommand

    data class StartQuitConfirmationTimer(
        val timeoutMillis: Long,
    ) : ScopeXEffectCommand

    data object QuitScopeX : ScopeXEffectCommand
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
            event is ScopeXEvent.Configuration.SetInputCacheActiveLimit ->
                ScopeXTransition(state.withInputCache(state.inputCache.withActiveLimit(event.limit)))

            event is ScopeXEvent.Configuration.SetClipboardImportOptedIn ->
                reduceClipboardImportConfiguration(state) {
                    it.copy(clipboardImportOptedIn = event.enabled)
                }

            event is ScopeXEvent.Configuration.SetScopeXSessionActive ->
                reduceClipboardImportConfiguration(state) {
                    it.copy(sessionActive = event.active)
                }

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

            event == ScopeXEvent.Timer.QuitConfirmationTimeout ->
                when (state) {
                    is ScopeXInteractionState.LiveScope ->
                        ScopeXTransition(
                            state.copy(
                                quitConfirmationActive = false,
                                systemMessage = null,
                            ),
                        )
                    else -> ScopeXTransition(state)
                }

            event == ScopeXEvent.Timer.ActiveSourceIdleTimeout ->
                ScopeXTransition(state.withSourceLock(state.sourceLock.release()))

            event is ScopeXEvent.Result.AppendInputCacheEntry ->
                ScopeXTransition(state.withInputCache(state.inputCache.appendEntry(event.text)))

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
        if (nextState is ScopeXInteractionState.InputCachePanelOpen &&
            event is ScopeXEvent.Canonical.ToggleInputCache
        ) {
            return ScopeXTransition(nextState.toLiveScope())
        }

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
            is ScopeXEvent.Canonical.ToggleInputCache -> {
                if (nextState.inputCache.entries.isEmpty()) {
                    return ScopeXTransition(
                        nextState,
                        listOf(ScopeXEffectCommand.ShowEmptyInputCachePrompt),
                    )
                }

                val effects = if (nextState.edgeScrollDirection == null) {
                    emptyList()
                } else {
                    listOf(ScopeXEffectCommand.StopEdgeScroll)
                }

                return ScopeXTransition(nextState.toInputCachePanelOpen(), effects)
            }
            is ScopeXEvent.Canonical.RecenterScope -> {
                val center = nextState.centerCrosshairContentPoint()
                val effects = buildList {
                    add(ScopeXEffectCommand.RecenterScope(center))
                    if (nextState.edgeScrollDirection != null) {
                        add(ScopeXEffectCommand.StopEdgeScroll)
                    }
                }
                return ScopeXTransition(
                    nextState.copy(
                        crosshairContentPoint = center,
                        edgeScrollDirection = null,
                    ),
                    effects,
                )
            }
            is ScopeXEvent.Canonical.Escape -> {
                if (nextState.quitConfirmationActive) {
                    return ScopeXTransition(nextState, listOf(ScopeXEffectCommand.QuitScopeX))
                }

                return ScopeXTransition(
                    nextState.copy(
                        quitConfirmationActive = true,
                        systemMessage = QUIT_CONFIRMATION_MESSAGE,
                    ),
                    listOf(
                        ScopeXEffectCommand.ShowMessage(QUIT_CONFIRMATION_MESSAGE),
                        ScopeXEffectCommand.StartQuitConfirmationTimer(
                            DEFAULT_QUIT_CONFIRMATION_TIMEOUT_MILLIS,
                        ),
                    ),
                )
            }
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

private fun ScopeXInteractionState.withInputCache(
    inputCache: ScopeXInputCache,
): ScopeXInteractionState =
    when (this) {
        is ScopeXInteractionState.LiveScope -> copy(inputCache = inputCache)
        is ScopeXInteractionState.Recording -> copy(inputCache = inputCache)
        is ScopeXInteractionState.InputCachePanelOpen -> copy(inputCache = inputCache)
    }

private fun ScopeXInputCache.appendEntry(text: String): ScopeXInputCache {
    val nextEntries = entries + text
    val droppedCount = (nextEntries.size - activeLimit).coerceAtLeast(0)
    val boundedEntries = nextEntries.drop(droppedCount)
    val nextHighlightedIndex = highlightedIndex
        ?.minus(droppedCount)
        ?.takeIf { it in boundedEntries.indices }

    return copy(entries = boundedEntries, highlightedIndex = nextHighlightedIndex)
}

private fun ScopeXInputCache.withActiveLimit(activeLimit: Int): ScopeXInputCache {
    val droppedCount = (entries.size - activeLimit).coerceAtLeast(0)
    val boundedEntries = entries.drop(droppedCount)
    val nextHighlightedIndex = highlightedIndex
        ?.minus(droppedCount)
        ?.takeIf { it in boundedEntries.indices }

    return copy(
        entries = boundedEntries,
        activeLimit = activeLimit,
        highlightedIndex = nextHighlightedIndex,
    )
}

private fun reduceClipboardImportConfiguration(
    state: ScopeXInteractionState,
    configure: (ScopeXInputCache) -> ScopeXInputCache,
): ScopeXTransition {
    val wasActive = state.inputCache.clipboardImportActive
    val nextState = state.withInputCache(configure(state.inputCache))
    val isActive = nextState.inputCache.clipboardImportActive
    val effects = when {
        !wasActive && isActive -> listOf(ScopeXEffectCommand.ShowClipboardImportBadge)
        wasActive && !isActive -> listOf(ScopeXEffectCommand.HideClipboardImportBadge)
        else -> emptyList()
    }

    return ScopeXTransition(nextState, effects)
}

private fun ScopeXInteractionState.LiveScope.toInputCachePanelOpen(): ScopeXInteractionState.InputCachePanelOpen =
    ScopeXInteractionState.InputCachePanelOpen(
        crosshairContentPoint = crosshairContentPoint,
        logicalDisplaySize = logicalDisplaySize,
        lastDominantMovementAxis = lastDominantMovementAxis,
        edgeZoneSize = edgeZoneSize,
        sourceLock = sourceLock,
        inputCache = inputCache.highlightTail(),
    )

private fun ScopeXInteractionState.InputCachePanelOpen.toLiveScope(): ScopeXInteractionState.LiveScope =
    ScopeXInteractionState.LiveScope(
        crosshairContentPoint = crosshairContentPoint,
        logicalDisplaySize = logicalDisplaySize,
        lastDominantMovementAxis = lastDominantMovementAxis,
        edgeZoneSize = edgeZoneSize,
        sourceLock = sourceLock,
        inputCache = inputCache.copy(highlightedIndex = null),
    )

private fun ScopeXInputCache.highlightTail(): ScopeXInputCache =
    copy(highlightedIndex = entries.lastIndex)

private fun ScopeXInteractionState.LiveScope.centerCrosshairContentPoint(): FloatPoint =
    FloatPoint(
        x = logicalDisplaySize.width / 2f,
        y = logicalDisplaySize.height / 2f,
    )

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
