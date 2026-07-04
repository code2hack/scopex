package com.code2hack.scopex.scopex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScopeXReducerTest {
    private val crosshairContentPoint = FloatPoint(100f, 200f)
    private val logicalDisplaySize = IntSize(1000, 800)

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
    fun liveScopeTouchActionsEmitPhoneSideEffectsAtCrosshair() {
        val state = liveScope()
        val scrollDelta = FloatPoint(0f, -120f)
        val actions = listOf(
            ScopeXEvent.Canonical.HoldCrosshair(ScopeXInputSource.Glasses) to
                ScopeXEffectCommand.InjectLongPress(crosshairContentPoint),
            ScopeXEvent.Canonical.MoveHeldCrosshair(ScopeXInputSource.Glasses) to
                ScopeXEffectCommand.InjectHeldMove(crosshairContentPoint),
            ScopeXEvent.Canonical.ScrollAtCrosshair(ScopeXInputSource.Glasses, scrollDelta) to
                ScopeXEffectCommand.InjectScroll(crosshairContentPoint, scrollDelta),
            ScopeXEvent.Canonical.ZoomAtCrosshair(ScopeXInputSource.Glasses, 1.25f) to
                ScopeXEffectCommand.InjectZoom(crosshairContentPoint, 1.25f),
        )

        for ((event, effect) in actions) {
            val transition = ScopeXReducer.reduce(state, event)

            assertEquals(
                state.copy(
                    sourceLock = ScopeXSourceLock(
                        activeSource = ScopeXInputSource.Glasses,
                        ownsActions = true,
                    ),
                ),
                transition.state,
            )
            assertEquals(listOf(effect), transition.effects)
        }
    }

    @Test
    fun competingSourceTouchActionsAreRejectedWithoutEffects() {
        val state = liveScope(
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
        )
        val scrollDelta = FloatPoint(0f, -120f)
        val actions: List<ScopeXEvent.Canonical> = listOf(
            ScopeXEvent.Canonical.HoldCrosshair(ScopeXInputSource.Remote),
            ScopeXEvent.Canonical.MoveHeldCrosshair(ScopeXInputSource.Remote),
            ScopeXEvent.Canonical.ScrollAtCrosshair(ScopeXInputSource.Remote, scrollDelta),
            ScopeXEvent.Canonical.ZoomAtCrosshair(ScopeXInputSource.Remote, 1.25f),
        )

        for (event in actions) {
            val transition = ScopeXReducer.reduce(state, event)

            assertEquals(state, transition.state)
            assertEquals(emptyList(), transition.effects)
        }
    }

    @Test
    fun canonicalTouchActionInNonLiveScopeStateIsNoOp() {
        val state = ScopeXInteractionState.Recording()

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.HoldCrosshair(ScopeXInputSource.Glasses),
        )

        assertEquals(state, transition.state)
        assertEquals(emptyList(), transition.effects)
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
    fun configurationEventOverridesEdgeZoneSizeInLiveScope() {
        val state = liveScope()

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Configuration.SetEdgeZoneSize(120f),
        )

        assertEquals(state.copy(edgeZoneSize = 120f), transition.state)
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun configurationEventSetEdgeZoneSizeIsNoOpInRecording() {
        val state = ScopeXInteractionState.Recording()

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Configuration.SetEdgeZoneSize(120f),
        )

        assertEquals(state, transition.state)
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun configurationEventRejectsNonPositiveActiveSourceIdleTimeout() {
        assertFailsWith<IllegalArgumentException> {
            ScopeXEvent.Configuration.SetActiveSourceIdleTimeout(0L)
        }
    }

    @Test
    fun edgeScrollStartsWhenCrosshairEntersEdgeZoneAndStopsWhenItLeaves() {
        val state = liveScope()

        val entered = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.CrosshairMoved(
                crosshairContentPoint = FloatPoint(50f, 400f),
                dominantMovementAxis = ScopeXMovementAxis.Horizontal,
            ),
        )

        assertEquals(
            liveScope(
                crosshairContentPoint = FloatPoint(50f, 400f),
                edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
            ),
            entered.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.StartEdgeScroll(ScopeXEdgeScrollDirection.Left)),
            entered.effects,
        )

        val left = ScopeXReducer.reduce(
            state = entered.state,
            event = ScopeXEvent.Result.CrosshairMoved(
                crosshairContentPoint = FloatPoint(500f, 400f),
                dominantMovementAxis = ScopeXMovementAxis.Horizontal,
            ),
        )

        assertEquals(liveScope(crosshairContentPoint = FloatPoint(500f, 400f)), left.state)
        assertEquals(listOf(ScopeXEffectCommand.StopEdgeScroll), left.effects)
    }

    @Test
    fun unchangedEdgeScrollDirectionUpdatesPointWithoutEffect() {
        val state = liveScope(
            crosshairContentPoint = FloatPoint(50f, 400f),
            edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.CrosshairMoved(
                crosshairContentPoint = FloatPoint(25f, 400f),
                dominantMovementAxis = ScopeXMovementAxis.Horizontal,
            ),
        )

        assertEquals(
            liveScope(
                crosshairContentPoint = FloatPoint(25f, 400f),
                edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
            ),
            transition.state,
        )
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun interiorCrosshairMovementWithoutActiveEdgeScrollEmitsNoEffect() {
        val state = liveScope()

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.CrosshairMoved(
                crosshairContentPoint = FloatPoint(500f, 400f),
                dominantMovementAxis = ScopeXMovementAxis.Horizontal,
            ),
        )

        assertEquals(liveScope(crosshairContentPoint = FloatPoint(500f, 400f)), transition.state)
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun changedEdgeScrollDirectionStartsNewDirection() {
        val state = liveScope(
            crosshairContentPoint = FloatPoint(50f, 400f),
            edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.CrosshairMoved(
                crosshairContentPoint = FloatPoint(950f, 400f),
                dominantMovementAxis = ScopeXMovementAxis.Horizontal,
            ),
        )

        assertEquals(
            liveScope(
                crosshairContentPoint = FloatPoint(950f, 400f),
                edgeScrollDirection = ScopeXEdgeScrollDirection.Right,
            ),
            transition.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.StartEdgeScroll(ScopeXEdgeScrollDirection.Right)),
            transition.effects,
        )
    }

    @Test
    fun cornerEdgeScrollUsesEventDominantMovementAxis() {
        val horizontalState = liveScope(lastDominantMovementAxis = ScopeXMovementAxis.Vertical)

        val horizontal = ScopeXReducer.reduce(
            state = horizontalState,
            event = ScopeXEvent.Result.CrosshairMoved(
                crosshairContentPoint = FloatPoint(50f, 50f),
                dominantMovementAxis = ScopeXMovementAxis.Horizontal,
            ),
        )

        assertEquals(
            liveScope(
                crosshairContentPoint = FloatPoint(50f, 50f),
                edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
            ),
            horizontal.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.StartEdgeScroll(ScopeXEdgeScrollDirection.Left)),
            horizontal.effects,
        )

        val verticalState = liveScope(lastDominantMovementAxis = ScopeXMovementAxis.Horizontal)

        val vertical = ScopeXReducer.reduce(
            state = verticalState,
            event = ScopeXEvent.Result.CrosshairMoved(
                crosshairContentPoint = FloatPoint(50f, 50f),
                dominantMovementAxis = ScopeXMovementAxis.Vertical,
            ),
        )

        assertEquals(
            liveScope(
                crosshairContentPoint = FloatPoint(50f, 50f),
                edgeScrollDirection = ScopeXEdgeScrollDirection.Up,
                lastDominantMovementAxis = ScopeXMovementAxis.Vertical,
            ),
            vertical.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.StartEdgeScroll(ScopeXEdgeScrollDirection.Up)),
            vertical.effects,
        )
    }

    @Test
    fun configurationEventRejectsNonPositiveEdgeZoneSize() {
        assertFailsWith<IllegalArgumentException> {
            ScopeXEvent.Configuration.SetEdgeZoneSize(0f)
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
        crosshairContentPoint: FloatPoint = this.crosshairContentPoint,
        edgeScrollDirection: ScopeXEdgeScrollDirection? = null,
        lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
        edgeZoneSize: Float = 100f,
    ) = ScopeXInteractionState.LiveScope(
        crosshairContentPoint = crosshairContentPoint,
        logicalDisplaySize = logicalDisplaySize,
        sourceLock = sourceLock,
        edgeScrollDirection = edgeScrollDirection,
        lastDominantMovementAxis = lastDominantMovementAxis,
        edgeZoneSize = edgeZoneSize,
    )
}
