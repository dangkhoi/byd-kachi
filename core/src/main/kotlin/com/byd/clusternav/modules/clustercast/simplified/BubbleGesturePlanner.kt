package com.byd.clusternav.modules.clustercast.simplified

/**
 * Pure decision logic for the single-icon floating bubble (R5 / #7 —
 * docs/specs/cast-nav-ux-release-v104.html).
 *
 * The Android layer owns rendering, gesture detection and shell I/O (BubbleRenderer,
 * BubbleGestureHandler, BubbleSubmenuOverlay, BubbleActionDispatcher, FloatingBubbleService). Every
 * branch that can be decided WITHOUT Android lives here so it is unit-testable off-car — the same
 * split the spec asks for ("cover the decision logic in a pure/unit-testable function").
 *
 * Two gestures are modelled:
 *  - a single **TAP** toggles the full cluster ([tapOutcome]); and
 *  - a **LONG-PRESS** opens a three-item submenu ([submenuItems]) whose rows map to a cluster slot
 *    or the config screen ([slotFor]).
 *
 * DRAG is pure view mechanics (move-threshold in [BubbleGestureHandler]) with no policy, so it is
 * intentionally not represented here.
 *
 * Pure Kotlin, no Android import → lives in :core (LayeringRulesTest Q1).
 */
object BubbleGesturePlanner {

    /** What a single tap on the one-icon bubble should do, given the current cast state. */
    enum class BubbleTapOutcome {
        /** Projection open, nothing on the cluster → cast the current foreground app FULL. */
        CAST_FULL,

        /** Something is on the cluster (full OR split) → return it / stop, back to the gauges. */
        RETURN,

        /** Transient state (Off/Opening/Stopping/Closing/Error) → not actionable, just inform. */
        PREPARING,
    }

    /**
     * TAP = toggle full. [SimpleCastState.Idle] (projection open, nothing cast) invites a full cast;
     * either casting state ([SimpleCastState.CastingFull] or [SimpleCastState.CastingSplit]) returns
     * everything with a slot-less `Stop`; every transient state is not actionable.
     *
     * The RETURN branch deliberately covers split too: a slot-less `Stop` returns both halves (see
     * `SimpleCastCoordinator.handleStop`), which is the "return to cluster gauges" the spec asks for.
     */
    fun tapOutcome(state: SimpleCastState): BubbleTapOutcome = when (state) {
        is SimpleCastState.Idle -> BubbleTapOutcome.CAST_FULL
        is SimpleCastState.CastingFull, is SimpleCastState.CastingSplit -> BubbleTapOutcome.RETURN
        else -> BubbleTapOutcome.PREPARING
    }

    /** The three long-press submenu actions, in display / TalkBack focus order. */
    enum class BubbleMenuAction { CAST_LEFT, CAST_RIGHT, OPEN_CONFIG }

    /** One submenu row: a localized [label] and the [action] it dispatches when tapped. */
    data class BubbleSubmenuItem(val label: String, val action: BubbleMenuAction)

    /**
     * Long-press submenu contents, in order: **Trái** (cast LEFT), **Phải** (cast RIGHT),
     * **Cấu hình** (open MainActivity). The order is locked by test because it is also the TalkBack
     * reading order. Labels are Vietnamese to match the rest of the Cast UI (e.g.
     * `CastBubbleProjection.zoneShortLabel`).
     */
    fun submenuItems(): List<BubbleSubmenuItem> = listOf(
        BubbleSubmenuItem("Trái", BubbleMenuAction.CAST_LEFT),
        BubbleSubmenuItem("Phải", BubbleMenuAction.CAST_RIGHT),
        BubbleSubmenuItem("Cấu hình", BubbleMenuAction.OPEN_CONFIG),
    )

    /**
     * The cluster slot a submenu action casts into, or `null` for [BubbleMenuAction.OPEN_CONFIG]
     * (which opens the config screen instead of casting). This is the single source of truth the
     * dispatcher uses to route a submenu choice.
     */
    fun slotFor(action: BubbleMenuAction): ClusterSlotSide? = when (action) {
        BubbleMenuAction.CAST_LEFT -> ClusterSlotSide.LEFT
        BubbleMenuAction.CAST_RIGHT -> ClusterSlotSide.RIGHT
        BubbleMenuAction.OPEN_CONFIG -> null
    }

    /** What a long-press submenu LEFT/RIGHT choice should do for the given [side]. */
    enum class BubbleSlotOutcome {
        /** That side is already cast (split) → return just that app to the main display. */
        RETURN,

        /** That side is free → cast the current foreground app into it. */
        CAST,
    }

    /**
     * A submenu slot choice TOGGLES that side (owner 2026-08-12): if the [side] is already occupied
     * in a [SimpleCastState.CastingSplit], RETURN that app; otherwise CAST the current foreground app
     * into it. This mirrors the in-app zone buttons (MainActivityCastController.setupZoneButtons) so
     * the bubble and the Home card behave identically. Any non-split state (Idle/Full/transient) has
     * no occupied slot, so it CASTs — including turning a full cast or idle cluster into a split.
     */
    fun slotOutcome(state: SimpleCastState, side: ClusterSlotSide): BubbleSlotOutcome {
        val occupied = state is SimpleCastState.CastingSplit && when (side) {
            ClusterSlotSide.LEFT -> state.left != null
            ClusterSlotSide.RIGHT -> state.right != null
        }
        return if (occupied) BubbleSlotOutcome.RETURN else BubbleSlotOutcome.CAST
    }
}
