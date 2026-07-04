package com.code2hack.scopex.scopex

sealed interface ScopeXInteractionState {
    data class LiveScope(
        val crosshairContentPoint: FloatPoint,
    ) : ScopeXInteractionState

    data object Recording : ScopeXInteractionState

    data object InputCachePanelOpen : ScopeXInteractionState
}

sealed interface ScopeXEvent {
    sealed interface Canonical : ScopeXEvent {
        data object ClickCrosshair : Canonical
    }

    sealed interface Result : ScopeXEvent

    sealed interface Timer : ScopeXEvent

    sealed interface Configuration : ScopeXEvent
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
            state is ScopeXInteractionState.LiveScope &&
                event == ScopeXEvent.Canonical.ClickCrosshair ->
                ScopeXTransition(
                    state = state,
                    effects = listOf(
                        ScopeXEffectCommand.InjectClick(state.crosshairContentPoint),
                    ),
                )

            else -> ScopeXTransition(state)
        }
}
