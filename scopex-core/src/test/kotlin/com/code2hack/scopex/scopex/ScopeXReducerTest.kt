package com.code2hack.scopex.scopex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScopeXReducerTest {
    private val crosshairContentPoint = FloatPoint(100f, 200f)

    @Test
    fun clickCrosshairAcquiresFirstActiveSourceAndEmitsInjectClickAtCrosshair() {
        val state = liveScope()

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.ClickCrosshair(ScopeXInputSource.Glasses),
        )

        assertEquals(
            state.copy(
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.InjectClick(crosshairContentPoint)),
            transition.effects,
        )
    }

    @Test
    fun competingSourceClickIsRejectedWithoutEffects() {
        val state = liveScope(
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.ClickCrosshair(ScopeXInputSource.Remote),
        )

        assertEquals(state, transition.state)
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun owningSourceClickKeepsProducingEffectsUntilIdleTimeout() {
        val state = liveScope(
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.ClickCrosshair(ScopeXInputSource.Glasses),
        )

        assertEquals(state, transition.state)
        assertEquals(
            listOf(ScopeXEffectCommand.InjectClick(crosshairContentPoint)),
            transition.effects,
        )
    }

    @Test
    fun activeSourceIdleTimeoutReleasesOwnershipAndPreservesTimeout() {
        val state = liveScope(
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
                activeSourceIdleTimeoutMillis = 750L,
            ),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Timer.ActiveSourceIdleTimeout,
        )

        assertEquals(
            state.copy(
                sourceLock = ScopeXSourceLock(activeSourceIdleTimeoutMillis = 750L),
            ),
            transition.state,
        )
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun configurationEventOverridesActiveSourceIdleTimeout() {
        val state = liveScope(
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Configuration.SetActiveSourceIdleTimeout(750L),
        )

        assertEquals(
            state.copy(
                sourceLock = state.sourceLock.copy(activeSourceIdleTimeoutMillis = 750L),
            ),
            transition.state,
        )
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun configurationEventRejectsNonPositiveActiveSourceIdleTimeout() {
        assertFailsWith<IllegalArgumentException> {
            ScopeXEvent.Configuration.SetActiveSourceIdleTimeout(0L)
        }
    }

    @Test
    fun sourceCanReacquireAfterIdleTimeoutRelease() {
        val state = liveScope(
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        )
        val releasedState = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Timer.ActiveSourceIdleTimeout,
        ).state

        val transition = ScopeXReducer.reduce(
            state = releasedState,
            event = ScopeXEvent.Canonical.ClickCrosshair(ScopeXInputSource.Remote),
        )

        assertEquals(
            liveScope(
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Remote,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.InjectClick(crosshairContentPoint)),
            transition.effects,
        )
    }

    private fun liveScope(
        sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
    ) = ScopeXInteractionState.LiveScope(
        crosshairContentPoint = crosshairContentPoint,
        sourceLock = sourceLock,
    )
}
