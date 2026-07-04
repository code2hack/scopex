package com.code2hack.scopex.scopex

const val DEFAULT_ACTIVE_SOURCE_IDLE_TIMEOUT_MILLIS: Long = 500L

enum class ScopeXInputSource {
    Glasses,
    Remote,
    Debug,
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
    }

    sealed interface Result : ScopeXEvent

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
    }
}

sealed interface ScopeXEffectCommand {
    data class InjectClick(
        val crosshairContentPoint: FloatPoint,
    ) : ScopeXEffectCommand
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

            event == ScopeXEvent.Timer.ActiveSourceIdleTimeout ->
                ScopeXTransition(state.withSourceLock(state.sourceLock.release()))

            event is ScopeXEvent.Canonical.ClickCrosshair ->
                reduceClickCrosshair(state, event)

            else -> ScopeXTransition(state)
        }

    private fun reduceClickCrosshair(
        state: ScopeXInteractionState,
        event: ScopeXEvent.Canonical.ClickCrosshair,
    ): ScopeXTransition {
        if (!state.sourceLock.accepts(event.source)) {
            return ScopeXTransition(state)
        }

        val nextState = state.withSourceLock(state.sourceLock.acquire(event.source))
        return when (nextState) {
            is ScopeXInteractionState.LiveScope ->
                ScopeXTransition(
                    state = nextState,
                    effects = listOf(
                        ScopeXEffectCommand.InjectClick(nextState.crosshairContentPoint),
                    ),
                )

            else -> ScopeXTransition(state)
        }
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
