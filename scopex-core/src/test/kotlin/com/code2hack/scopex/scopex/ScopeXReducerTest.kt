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
        val states = listOf(
            ScopeXInteractionState.Recording(),
            inputCachePanelOpen(),
        )

        for (state in states) {
            val transition = ScopeXReducer.reduce(
                state = state,
                event = ScopeXEvent.Canonical.RecenterScope(ScopeXInputSource.Glasses),
            )

            assertEquals(state, transition.state)
            assertEquals(emptyList(), transition.effects)
        }
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
    fun competingSourceRecenterAndEscapeAreRejectedWithoutEffects() {
        val sourceLock = ScopeXSourceLock(
            activeSource = ScopeXInputSource.Glasses,
            ownsActions = true,
        )
        val state = liveScope(sourceLock = sourceLock)
        val confirmingState = liveScope(
            sourceLock = sourceLock,
            quitConfirmationActive = true,
            systemMessage = QUIT_CONFIRMATION_MESSAGE,
        )
        val transitions = listOf(
            state to ScopeXEvent.Canonical.RecenterScope(ScopeXInputSource.Remote),
            state to ScopeXEvent.Canonical.Escape(ScopeXInputSource.Remote),
            confirmingState to ScopeXEvent.Canonical.Escape(ScopeXInputSource.Remote),
        )

        for ((lockedState, event) in transitions) {
            val transition = ScopeXReducer.reduce(lockedState, event)

            assertEquals(lockedState, transition.state)
            assertEquals(emptyList(), transition.effects)
        }
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

    @Test
    fun recenterScopeRecentersCrosshairAndStopsEdgeScroll() {
        val state = liveScope(
            crosshairContentPoint = FloatPoint(25f, 400f),
            edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
        )
        val center = FloatPoint(500f, 400f)

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.RecenterScope(ScopeXInputSource.Glasses),
        )

        assertEquals(
            liveScope(
                crosshairContentPoint = center,
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(
                ScopeXEffectCommand.RecenterScope(center),
                ScopeXEffectCommand.StopEdgeScroll,
            ),
            transition.effects,
        )
    }

    @Test
    fun escapeShowsQuitConfirmationThenSecondEscapeQuits() {
        val state = liveScope()

        val first = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.Escape(ScopeXInputSource.Glasses),
        )

        val confirmingState = liveScope(
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
            quitConfirmationActive = true,
            systemMessage = QUIT_CONFIRMATION_MESSAGE,
        )
        assertEquals(confirmingState, first.state)
        assertEquals(
            listOf(
                ScopeXEffectCommand.ShowMessage(QUIT_CONFIRMATION_MESSAGE),
                ScopeXEffectCommand.StartQuitConfirmationTimer(DEFAULT_QUIT_CONFIRMATION_TIMEOUT_MILLIS),
            ),
            first.effects,
        )

        val second = ScopeXReducer.reduce(
            state = first.state,
            event = ScopeXEvent.Canonical.Escape(ScopeXInputSource.Glasses),
        )

        assertEquals(confirmingState, second.state)
        assertEquals(listOf(ScopeXEffectCommand.QuitScopeX), second.effects)
    }

    @Test
    fun escapeWhileEdgeScrollingShowsQuitConfirmationAndKeepsEdgeScroll() {
        val state = liveScope(edgeScrollDirection = ScopeXEdgeScrollDirection.Left)

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.Escape(ScopeXInputSource.Glasses),
        )

        assertEquals(
            liveScope(
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
                edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
                quitConfirmationActive = true,
                systemMessage = QUIT_CONFIRMATION_MESSAGE,
            ),
            transition.state,
        )
        assertEquals(
            listOf(
                ScopeXEffectCommand.ShowMessage(QUIT_CONFIRMATION_MESSAGE),
                ScopeXEffectCommand.StartQuitConfirmationTimer(DEFAULT_QUIT_CONFIRMATION_TIMEOUT_MILLIS),
            ),
            transition.effects,
        )
    }

    @Test
    fun inputCacheAppendsOrderedEntriesAndPreservesDuplicates() {
        val state = liveScope()

        val first = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.AppendInputCacheEntry("same"),
        ).state
        val second = ScopeXReducer.reduce(
            state = first,
            event = ScopeXEvent.Result.AppendInputCacheEntry("same"),
        ).state

        assertEquals(
            ScopeXInputCache(entries = listOf("same", "same")),
            second.inputCache,
        )
    }

    @Test
    fun inputCacheDefaultLimitDropsOldestHeadEntries() {
        val finalState = (1..51).fold(liveScope() as ScopeXInteractionState) { state, index ->
            ScopeXReducer.reduce(
                state = state,
                event = ScopeXEvent.Result.AppendInputCacheEntry("line-$index"),
            ).state
        }

        assertEquals(50, finalState.inputCache.entries.size)
        assertEquals("line-2", finalState.inputCache.entries.first())
        assertEquals("line-51", finalState.inputCache.entries.last())
        assertEquals(DEFAULT_INPUT_CACHE_ACTIVE_LIMIT, finalState.inputCache.activeLimit)
    }

    @Test
    fun toggleInputCacheOpensPanelFreezesScopeAndHighlightsTail() {
        val inputCache = ScopeXInputCache(entries = listOf("first", "second"))
        val state = liveScope(inputCache = inputCache)

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.ToggleInputCache(ScopeXInputSource.Glasses),
        )

        assertEquals(
            inputCachePanelOpen(
                inputCache = inputCache.copy(highlightedIndex = 1),
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun toggleInputCacheStopsEdgeScrollWhenOpeningPanel() {
        val inputCache = ScopeXInputCache(entries = listOf("first", "second"))
        val state = liveScope(
            inputCache = inputCache,
            edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.ToggleInputCache(ScopeXInputSource.Glasses),
        )

        assertEquals(
            inputCachePanelOpen(
                inputCache = inputCache.copy(highlightedIndex = 1),
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(listOf(ScopeXEffectCommand.StopEdgeScroll), transition.effects)
    }

    @Test
    fun toggleInputCacheOnEmptyCacheKeepsLiveScopeAndShowsPrompt() {
        val state = liveScope()

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.ToggleInputCache(ScopeXInputSource.Glasses),
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
        assertEquals(listOf(ScopeXEffectCommand.ShowEmptyInputCachePrompt), transition.effects)
    }

    @Test
    fun toggleInputCacheClosesPanelAndUnfreezesScope() {
        val inputCache = ScopeXInputCache(
            entries = listOf("first", "second"),
            highlightedIndex = 1,
        )
        val state = inputCachePanelOpen(inputCache = inputCache)

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.ToggleInputCache(ScopeXInputSource.Glasses),
        )

        assertEquals(
            liveScope(
                inputCache = inputCache.copy(highlightedIndex = null),
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun moveCacheHighlightWrapsThroughEntriesAndResetsScrollOffset() {
        val state = inputCachePanelOpen(
            inputCache = ScopeXInputCache(
                entries = listOf("old", "middle", "new"),
                highlightedIndex = 2,
            ),
            highlightedLineScrollOffset = 4,
        )

        val wrappedToHead = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.MoveCacheHighlight(
                source = ScopeXInputSource.Glasses,
                offset = 1,
            ),
        )

        assertEquals(
            inputCachePanelOpen(
                inputCache = state.inputCache.copy(highlightedIndex = 0),
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            wrappedToHead.state,
        )
        assertEquals(emptyList(), wrappedToHead.effects)

        val wrappedToTail = ScopeXReducer.reduce(
            state = wrappedToHead.state,
            event = ScopeXEvent.Canonical.MoveCacheHighlight(
                source = ScopeXInputSource.Glasses,
                offset = -1,
            ),
        )

        assertEquals(
            inputCachePanelOpen(
                inputCache = state.inputCache.copy(highlightedIndex = 2),
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            wrappedToTail.state,
        )
        assertEquals(emptyList(), wrappedToTail.effects)
    }

    @Test
    fun insertHighlightedCacheLineRequiresEditableFocus() {
        val state = inputCachePanelOpen(
            inputCache = ScopeXInputCache(entries = listOf("old", "new"), highlightedIndex = 1),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.InsertHighlightedCacheLine(ScopeXInputSource.Glasses),
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
            listOf(ScopeXEffectCommand.ShowMessage(MOVE_CROSSHAIR_TO_INPUT_AREA_MESSAGE)),
            transition.effects,
        )
    }

    @Test
    fun editableFocusResultUpdatesOpenInputCachePanel() {
        val state = inputCachePanelOpen(
            inputCache = ScopeXInputCache(entries = listOf("old"), highlightedIndex = 0),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.FrozenCrosshairTargetEditableFocusChanged(
                hasEditableFocus = true,
            ),
        )

        assertEquals(
            state.copy(frozenCrosshairTargetHasEditableFocus = true),
            transition.state,
        )
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun insertHighlightedCacheLineEmitsInsertTextAndSuccessRemovesLine() {
        val state = inputCachePanelOpen(
            inputCache = ScopeXInputCache(
                entries = listOf("old", "selected", "newer"),
                highlightedIndex = 1,
            ),
            frozenCrosshairTargetHasEditableFocus = true,
        )

        val insertion = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.InsertHighlightedCacheLine(ScopeXInputSource.Glasses),
        )

        val pendingState = state.copy(
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
            pendingInsertedCacheIndex = 1,
        )
        assertEquals(pendingState, insertion.state)
        assertEquals(listOf(ScopeXEffectCommand.InsertText("selected")), insertion.effects)

        val success = ScopeXReducer.reduce(
            state = insertion.state,
            event = ScopeXEvent.Result.TextInsertionSucceeded,
        )

        assertEquals(
            pendingState.copy(
                inputCache = ScopeXInputCache(
                    entries = listOf("old", "newer"),
                    highlightedIndex = 1,
                ),
                pendingInsertedCacheIndex = null,
            ),
            success.state,
        )
        assertEquals(emptyList(), success.effects)
    }

    @Test
    fun insertingOnlyRemainingCacheLineClosesPanelAndUnfreezesScope() {
        val state = inputCachePanelOpen(
            inputCache = ScopeXInputCache(entries = listOf("only"), highlightedIndex = 0),
            frozenCrosshairTargetHasEditableFocus = true,
        )

        val insertion = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.InsertHighlightedCacheLine(ScopeXInputSource.Glasses),
        )
        val success = ScopeXReducer.reduce(
            state = insertion.state,
            event = ScopeXEvent.Result.TextInsertionSucceeded,
        )

        assertEquals(
            liveScope(
                inputCache = ScopeXInputCache(),
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            success.state,
        )
        assertEquals(emptyList(), success.effects)
    }

    @Test
    fun deleteHighlightedCacheLineRemovesLineAndWrapsOrClosesPanel() {
        val state = inputCachePanelOpen(
            inputCache = ScopeXInputCache(
                entries = listOf("old", "middle", "new"),
                highlightedIndex = 2,
            ),
            highlightedLineScrollOffset = 3,
        )

        val deletedTail = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.DeleteHighlightedCacheLine(ScopeXInputSource.Glasses),
        )

        assertEquals(
            inputCachePanelOpen(
                inputCache = ScopeXInputCache(entries = listOf("old", "middle"), highlightedIndex = 0),
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            deletedTail.state,
        )
        assertEquals(emptyList(), deletedTail.effects)

        val oneLineState = inputCachePanelOpen(
            sourceLock = ScopeXSourceLock(
                activeSource = ScopeXInputSource.Glasses,
                ownsActions = true,
            ),
            inputCache = ScopeXInputCache(entries = listOf("only"), highlightedIndex = 0),
        )
        val deletedOnly = ScopeXReducer.reduce(
            state = oneLineState,
            event = ScopeXEvent.Canonical.DeleteHighlightedCacheLine(ScopeXInputSource.Glasses),
        )

        assertEquals(
            liveScope(
                inputCache = ScopeXInputCache(),
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            deletedOnly.state,
        )
        assertEquals(emptyList(), deletedOnly.effects)
    }

    @Test
    fun textInsertionFailureKeepsCacheEntriesAndClearsPendingInsert() {
        val state = inputCachePanelOpen(
            inputCache = ScopeXInputCache(entries = listOf("old", "selected"), highlightedIndex = 1),
            pendingInsertedCacheIndex = 1,
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.TextInsertionFailed,
        )

        assertEquals(state.copy(pendingInsertedCacheIndex = null), transition.state)
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun cacheLineDisplaysTruncateNonHighlightedLongLinesToTail() {
        val state = inputCachePanelOpen(
            inputCache = ScopeXInputCache(
                entries = listOf("0123456789abcdefghijklmnopqrstuvwxyz", "focus"),
                highlightedIndex = 1,
            ),
        )

        assertEquals(
            listOf(
                ScopeXCacheLineDisplay(text = "uvwxyz"),
                ScopeXCacheLineDisplay(text = "focus"),
            ),
            state.cacheLineDisplays(visibleCharacters = 6),
        )
    }

    @Test
    fun cacheLineScrollTimerAdvancesHighlightedLongLineRecurrently() {
        val longLine = "0123456789abcdefghijklmnopqrstuvwxyz"
        val state = inputCachePanelOpen(
            inputCache = ScopeXInputCache(entries = listOf(longLine), highlightedIndex = 0),
        )

        assertEquals(
            listOf(ScopeXCacheLineDisplay(text = "012345", scrollOffset = 0)),
            state.cacheLineDisplays(visibleCharacters = 6),
        )

        val scrolled = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Timer.CacheLineScrollDelay,
        )

        assertEquals(
            state.copy(highlightedLineScrollOffset = 1),
            scrolled.state,
        )
        assertEquals(
            listOf(ScopeXCacheLineDisplay(text = "123456", scrollOffset = 1)),
            (scrolled.state as ScopeXInteractionState.InputCachePanelOpen)
                .cacheLineDisplays(visibleCharacters = 6),
        )
        assertEquals(emptyList(), scrolled.effects)
    }

    @Test
    fun configurationEventOverridesInputCacheActiveLimitAndEvictsHead() {
        val state = liveScope(
            inputCache = ScopeXInputCache(
                entries = listOf("first", "second", "third"),
                activeLimit = 3,
                highlightedIndex = 2,
            ),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Configuration.SetInputCacheActiveLimit(2),
        )

        assertEquals(
            state.copy(
                inputCache = ScopeXInputCache(
                    entries = listOf("second", "third"),
                    activeLimit = 2,
                    highlightedIndex = 1,
                ),
            ),
            transition.state,
        )
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun configurationEventRejectsNonPositiveInputCacheActiveLimit() {
        assertFailsWith<IllegalArgumentException> {
            ScopeXEvent.Configuration.SetInputCacheActiveLimit(0)
        }
    }

    @Test
    fun clipboardImportIsActiveOnlyWhenOptedInAndSessionActive() {
        val optedIn = ScopeXReducer.reduce(
            state = liveScope(),
            event = ScopeXEvent.Configuration.SetClipboardImportOptedIn(true),
        )

        assertEquals(false, optedIn.state.inputCache.clipboardImportActive)
        assertEquals(emptyList(), optedIn.effects)

        val active = ScopeXReducer.reduce(
            state = optedIn.state,
            event = ScopeXEvent.Configuration.SetScopeXSessionActive(true),
        )

        assertEquals(true, active.state.inputCache.clipboardImportActive)
        assertEquals(listOf(ScopeXEffectCommand.ShowClipboardImportBadge), active.effects)

        val inactive = ScopeXReducer.reduce(
            state = active.state,
            event = ScopeXEvent.Configuration.SetScopeXSessionActive(false),
        )

        assertEquals(false, inactive.state.inputCache.clipboardImportActive)
        assertEquals(listOf(ScopeXEffectCommand.HideClipboardImportBadge), inactive.effects)
    }

    @Test
    fun clipboardImportOptOutHidesBadgeOnlyWhenItWasActive() {
        val inactiveOptOut = ScopeXReducer.reduce(
            state = liveScope(),
            event = ScopeXEvent.Configuration.SetClipboardImportOptedIn(false),
        )

        assertEquals(false, inactiveOptOut.state.inputCache.clipboardImportActive)
        assertEquals(emptyList(), inactiveOptOut.effects)

        val activeState = liveScope(
            inputCache = ScopeXInputCache(
                clipboardImportOptedIn = true,
                sessionActive = true,
            ),
        )
        val activeOptOut = ScopeXReducer.reduce(
            state = activeState,
            event = ScopeXEvent.Configuration.SetClipboardImportOptedIn(false),
        )

        assertEquals(false, activeOptOut.state.inputCache.clipboardImportActive)
        assertEquals(listOf(ScopeXEffectCommand.HideClipboardImportBadge), activeOptOut.effects)
    }

    @Test
    fun startRecordingFromLiveScopeOpensPanelFreezesScopeStartsAsrAndStopsEdgeScroll() {
        val inputCache = ScopeXInputCache(entries = listOf("old"))
        val state = liveScope(
            inputCache = inputCache,
            edgeScrollDirection = ScopeXEdgeScrollDirection.Left,
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.StartRecording(ScopeXInputSource.Glasses),
        )

        val recordingCache = inputCache.copy(highlightedIndex = 0)
        assertEquals(
            recording(
                inputCache = recordingCache,
                preRecordingInputCache = recordingCache,
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(
                ScopeXEffectCommand.StopEdgeScroll,
                ScopeXEffectCommand.ShowMicIcon,
                ScopeXEffectCommand.StartAsr,
            ),
            transition.effects,
        )
    }

    @Test
    fun startRecordingFromInputCachePanelReusesPanelAndPreservesHighlight() {
        val inputCache = ScopeXInputCache(
            entries = listOf("first", "second"),
            highlightedIndex = 0,
        )
        val state = inputCachePanelOpen(inputCache = inputCache)

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.StartRecording(ScopeXInputSource.Glasses),
        )

        assertEquals(
            recording(
                inputCache = inputCache,
                preRecordingInputCache = inputCache,
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.ShowMicIcon, ScopeXEffectCommand.StartAsr),
            transition.effects,
        )
    }

    @Test
    fun recordingIgnoresInteractionsExceptFinishAndEscape() {
        val state = recording()
        val ignoredEvents = listOf(
            ScopeXEvent.Canonical.ClickCrosshair(ScopeXInputSource.Glasses),
            ScopeXEvent.Canonical.ToggleInputCache(ScopeXInputSource.Glasses),
            ScopeXEvent.Canonical.RecenterScope(ScopeXInputSource.Glasses),
        )

        for (event in ignoredEvents) {
            val transition = ScopeXReducer.reduce(state, event)

            assertEquals(state, transition.state)
            assertEquals(emptyList(), transition.effects)
        }
    }

    @Test
    fun asrTranscriptUpdatesConfirmedAndPartialNewLineBuffer() {
        val state = recording()

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.AsrTranscript(
                confirmedText = "hello",
                partialText = " wor",
            ),
        )

        assertEquals(
            state.copy(
                newLineBuffer = ScopeXNewLineBuffer(
                    confirmedText = "hello",
                    partialText = " wor",
                ),
            ),
            transition.state,
        )
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun longSilenceAndAsrEndpointCommitConfirmedTextAndResetBuffer() {
        val state = recording(
            newLineBuffer = ScopeXNewLineBuffer(
                confirmedText = " first ",
                partialText = " ignored",
            ),
        )

        val first = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Timer.LongSilenceTimeout,
        )

        assertEquals(
            recording(
                inputCache = ScopeXInputCache(entries = listOf("first")),
                savedLineCount = 1,
            ),
            first.state,
        )
        assertEquals(emptyList(), first.effects)

        val secondBuffer = ScopeXReducer.reduce(
            state = first.state,
            event = ScopeXEvent.Result.AsrTranscript(confirmedText = "second"),
        ).state
        val second = ScopeXReducer.reduce(
            state = secondBuffer,
            event = ScopeXEvent.Result.AsrEndpoint,
        )

        assertEquals(
            recording(
                inputCache = ScopeXInputCache(entries = listOf("first", "second")),
                savedLineCount = 2,
            ),
            second.state,
        )
        assertEquals(emptyList(), second.effects)
    }

    @Test
    fun emptyBuffersAndPartialTextAreNotCommitted() {
        val state = recording(
            newLineBuffer = ScopeXNewLineBuffer(
                confirmedText = "   ",
                partialText = "draft",
            ),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Timer.LongSilenceTimeout,
        )

        assertEquals(recording(), transition.state)
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun finishRecordingCommitsFinalConfirmedTextHidesMicAndHighlightsNewestTail() {
        val inputCache = ScopeXInputCache(entries = listOf("old"))
        val state = recording(
            inputCache = inputCache,
            preRecordingInputCache = inputCache.copy(highlightedIndex = 0),
            newLineBuffer = ScopeXNewLineBuffer(confirmedText = " final "),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.FinishRecording(ScopeXInputSource.Glasses),
        )

        assertEquals(
            inputCachePanelOpen(
                inputCache = ScopeXInputCache(
                    entries = listOf("old", "final"),
                    highlightedIndex = 1,
                ),
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.FinishAsr, ScopeXEffectCommand.HideMicIcon),
            transition.effects,
        )
    }

    @Test
    fun finishRecordingWithNoTextRestoresPreRecordingHighlight() {
        val inputCache = ScopeXInputCache(
            entries = listOf("first", "second"),
            highlightedIndex = 0,
        )
        val state = recording(
            inputCache = inputCache,
            preRecordingInputCache = inputCache,
            newLineBuffer = ScopeXNewLineBuffer(partialText = "draft"),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.FinishRecording(ScopeXInputSource.Glasses),
        )

        assertEquals(
            inputCachePanelOpen(
                inputCache = inputCache,
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.FinishAsr, ScopeXEffectCommand.HideMicIcon),
            transition.effects,
        )
    }

    @Test
    fun finishRecordingWithNoTextAndEmptyCacheClosesPanel() {
        val state = recording()

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.FinishRecording(ScopeXInputSource.Glasses),
        )

        assertEquals(
            liveScope(
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.FinishAsr, ScopeXEffectCommand.HideMicIcon),
            transition.effects,
        )
    }

    @Test
    fun recordingEscapeRollsBackCommittedSessionLinesAndRestoresPreRecordingPanelHighlight() {
        val preRecordingInputCache = ScopeXInputCache(
            entries = listOf("first", "second"),
            highlightedIndex = 0,
        )
        val state = recording(
            inputCache = preRecordingInputCache.copy(
                entries = listOf("first", "second", "session-1", "session-2"),
            ),
            preRecordingInputCache = preRecordingInputCache,
            newLineBuffer = ScopeXNewLineBuffer(
                confirmedText = "unsaved",
                partialText = " draft",
            ),
            savedLineCount = 2,
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.Escape(ScopeXInputSource.Glasses),
        )

        assertEquals(
            inputCachePanelOpen(
                inputCache = preRecordingInputCache,
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.AbortAsr, ScopeXEffectCommand.HideMicIcon),
            transition.effects,
        )
    }

    @Test
    fun recordingEscapeWithEmptyPreCacheClosesPanelAndUnfreezesScope() {
        val state = recording(
            inputCache = ScopeXInputCache(entries = listOf("session")),
            preRecordingInputCache = ScopeXInputCache(),
            newLineBuffer = ScopeXNewLineBuffer(
                confirmedText = "unsaved",
                partialText = " draft",
            ),
            savedLineCount = 1,
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Canonical.Escape(ScopeXInputSource.Glasses),
        )

        assertEquals(
            liveScope(
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(ScopeXEffectCommand.AbortAsr, ScopeXEffectCommand.HideMicIcon),
            transition.effects,
        )
    }

    @Test
    fun asrFailureKeepsInputCachePanelOpenWhenCacheEntriesRemain() {
        val inputCache = ScopeXInputCache(
            entries = listOf("old"),
            highlightedIndex = 0,
        )
        val state = recording(
            inputCache = inputCache,
            preRecordingInputCache = inputCache,
            newLineBuffer = ScopeXNewLineBuffer(
                confirmedText = "unsaved",
                partialText = " draft",
            ),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.AsrFailure,
        )

        assertEquals(
            inputCachePanelOpen(
                inputCache = inputCache,
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(
                ScopeXEffectCommand.HideMicIcon,
                ScopeXEffectCommand.ShowMessage(ASR_FAILURE_MESSAGE),
            ),
            transition.effects,
        )
    }

    @Test
    fun asrFailureClosesPanelWhenCacheIsEmpty() {
        val state = recording(
            newLineBuffer = ScopeXNewLineBuffer(
                confirmedText = "unsaved",
                partialText = " draft",
            ),
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.AsrFailure,
        )

        assertEquals(
            liveScope(
                sourceLock = ScopeXSourceLock(
                    activeSource = ScopeXInputSource.Glasses,
                    ownsActions = true,
                ),
            ),
            transition.state,
        )
        assertEquals(
            listOf(
                ScopeXEffectCommand.HideMicIcon,
                ScopeXEffectCommand.ShowMessage(ASR_FAILURE_MESSAGE),
            ),
            transition.effects,
        )
    }

    @Test
    fun microphonePermissionDenialShowsGlassesMessageAndRoutesCompanion() {
        val state = liveScope()

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Result.MicrophonePermissionDenied,
        )

        assertEquals(state, transition.state)
        assertEquals(
            listOf(
                ScopeXEffectCommand.ShowMessage(MICROPHONE_PERMISSION_DENIED_MESSAGE),
                ScopeXEffectCommand.ShowPermissionRoute,
            ),
            transition.effects,
        )
    }

    @Test
    fun quitConfirmationTimeoutClearsConfirmationState() {
        val state = liveScope(
            quitConfirmationActive = true,
            systemMessage = QUIT_CONFIRMATION_MESSAGE,
        )

        val transition = ScopeXReducer.reduce(
            state = state,
            event = ScopeXEvent.Timer.QuitConfirmationTimeout,
        )

        assertEquals(liveScope(), transition.state)
        assertEquals(emptyList(), transition.effects)
    }

    private fun liveScope(
        sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
        inputCache: ScopeXInputCache = ScopeXInputCache(),
        crosshairContentPoint: FloatPoint = this.crosshairContentPoint,
        edgeScrollDirection: ScopeXEdgeScrollDirection? = null,
        lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
        edgeZoneSize: Float = 100f,
        quitConfirmationActive: Boolean = false,
        systemMessage: String? = null,
    ) = ScopeXInteractionState.LiveScope(
        crosshairContentPoint = crosshairContentPoint,
        logicalDisplaySize = logicalDisplaySize,
        sourceLock = sourceLock,
        inputCache = inputCache,
        edgeScrollDirection = edgeScrollDirection,
        lastDominantMovementAxis = lastDominantMovementAxis,
        edgeZoneSize = edgeZoneSize,
        quitConfirmationActive = quitConfirmationActive,
        systemMessage = systemMessage,
    )

    private fun inputCachePanelOpen(
        sourceLock: ScopeXSourceLock = ScopeXSourceLock(),
        inputCache: ScopeXInputCache = ScopeXInputCache(),
        crosshairContentPoint: FloatPoint = this.crosshairContentPoint,
        lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
        edgeZoneSize: Float = 100f,
        frozenCrosshairTargetHasEditableFocus: Boolean = false,
        pendingInsertedCacheIndex: Int? = null,
        highlightedLineScrollOffset: Int = 0,
    ) = ScopeXInteractionState.InputCachePanelOpen(
        crosshairContentPoint = crosshairContentPoint,
        logicalDisplaySize = logicalDisplaySize,
        lastDominantMovementAxis = lastDominantMovementAxis,
        edgeZoneSize = edgeZoneSize,
        sourceLock = sourceLock,
        inputCache = inputCache,
        frozenCrosshairTargetHasEditableFocus = frozenCrosshairTargetHasEditableFocus,
        pendingInsertedCacheIndex = pendingInsertedCacheIndex,
        highlightedLineScrollOffset = highlightedLineScrollOffset,
    )

    private fun recording(
        sourceLock: ScopeXSourceLock = ScopeXSourceLock(
            activeSource = ScopeXInputSource.Glasses,
            ownsActions = true,
        ),
        inputCache: ScopeXInputCache = ScopeXInputCache(),
        crosshairContentPoint: FloatPoint = this.crosshairContentPoint,
        lastDominantMovementAxis: ScopeXMovementAxis = ScopeXMovementAxis.Horizontal,
        edgeZoneSize: Float = 100f,
        newLineBuffer: ScopeXNewLineBuffer = ScopeXNewLineBuffer(),
        savedLineCount: Int = 0,
        preRecordingInputCache: ScopeXInputCache = ScopeXInputCache(),
    ) = ScopeXInteractionState.Recording(
        crosshairContentPoint = crosshairContentPoint,
        logicalDisplaySize = logicalDisplaySize,
        lastDominantMovementAxis = lastDominantMovementAxis,
        edgeZoneSize = edgeZoneSize,
        sourceLock = sourceLock,
        inputCache = inputCache,
        newLineBuffer = newLineBuffer,
        savedLineCount = savedLineCount,
        preRecordingInputCache = preRecordingInputCache,
    )
}
