package com.code2hack.scopex.scopex

import kotlin.test.Test
import kotlin.test.assertEquals

class ScopeXReducerTest {
    @Test
    fun liveScopeClickCrosshairEmitsInjectClickAtCrosshair() {
        val state = ScopeXInteractionState.LiveScope(
            crosshairContentPoint = FloatPoint(100f, 200f),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.ClickCrosshair,
        )

        assertEquals(state, transition.state)
        assertEquals(
            listOf(ScopeXEffectCommand.InjectClick(FloatPoint(100f, 200f))),
            transition.effects,
        )
    }
}
