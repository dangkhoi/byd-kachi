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

        /**
         * 3.1.5 · R31 — a FULL cast is on the cluster and the castable foreground is a DIFFERENT app → replace it in ONE tap
         * (`CastFull(foreground)`; the coordinator returns the old app before pinning the new one).
         */
        REPLACE_FULL,

        /** Something is on the cluster (full OR split) → return it / stop, back to the gauges. */
        RETURN,

        /** Transient state (Off/Opening/Stopping/Closing/Error) → not actionable, just inform. */
        PREPARING,
    }

    /**
     * 3.1.5 · R31 — whether [tapOutcome] needs the castable foreground for [state]: Idle (cast it) and a FULL cast (replace or
     * return). Split and transient states decide without it, so the dispatcher skips the `am stack list` read there.
     */
    fun tapNeedsForeground(state: SimpleCastState): Boolean =
        state is SimpleCastState.Idle || state is SimpleCastState.CastingFull

    /**
     * TAP. [foreground] = the castable foreground package on the HOME display AFTER the launcher guard (Kachi + every
     * CATEGORY_HOME package + [AppMover.isLauncher] filtered out), or `null` when there is none / it is home.
     *
     *  - [SimpleCastState.Idle] → [BubbleTapOutcome.CAST_FULL] (a `null` foreground is the dispatcher's "won't cast home" toast).
     *  - [SimpleCastState.CastingFull] and [foreground] is a DIFFERENT app → [BubbleTapOutcome.REPLACE_FULL] (R31, owner 14/10:
     *    GMaps on the cluster + Waze in front used to take two taps — the first only returned GMaps).
     *  - [SimpleCastState.CastingFull] and [foreground] is the same app, or `null` (home / nothing castable) → RETURN, as before.
     *  - [SimpleCastState.CastingSplit] → RETURN everything (slot-less `Stop`), as before: `CastFull` from a split is ignored by
     *    the coordinator (`handleCastFull` only replaces a FULL cast), so a "replace" here would be a silent no-op.
     *  - every transient state is not actionable.
     */
    fun tapOutcome(state: SimpleCastState, foreground: String? = null): BubbleTapOutcome = when (state) {
        is SimpleCastState.Idle -> BubbleTapOutcome.CAST_FULL
        is SimpleCastState.CastingFull ->
            if (foreground != null && foreground != state.targetPkg) BubbleTapOutcome.REPLACE_FULL else BubbleTapOutcome.RETURN
        is SimpleCastState.CastingSplit -> BubbleTapOutcome.RETURN
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

        /**
         * 3.1.5 · R31 — that side holds a DIFFERENT app than the castable foreground → replace it in one press
         * (`CastSlot(foreground, side, replace = true)`; the coordinator returns the old half before pinning the new one).
         */
        REPLACE,
    }

    /** 3.1.5 · R31 — [slotOutcome] needs the foreground only to decide RETURN vs REPLACE on an OCCUPIED side. */
    fun slotNeedsForegroundToDecide(state: SimpleCastState, side: ClusterSlotSide): Boolean = occupant(state, side) != null

    /**
     * A submenu slot choice on [side] (owner 2026-08-12 toggle, extended by R31 2026-10-14). [foreground] is the castable
     * foreground after the launcher guard, or `null`.
     *  - side FREE (any non-split state, or the empty half of a split) → CAST, as before.
     *  - side OCCUPIED and [foreground] is an app NOT already on the cluster (neither half) → REPLACE.
     *  - side OCCUPIED and [foreground] is `null` (home / nothing castable) or already on the cluster → RETURN that half, as before.
     */
    fun slotOutcome(state: SimpleCastState, side: ClusterSlotSide, foreground: String? = null): BubbleSlotOutcome {
        occupant(state, side) ?: return BubbleSlotOutcome.CAST
        val split = state as SimpleCastState.CastingSplit
        val onCluster = setOfNotNull(split.left?.pkg, split.right?.pkg)
        return if (foreground != null && foreground !in onCluster) BubbleSlotOutcome.REPLACE else BubbleSlotOutcome.RETURN
    }

    private fun occupant(state: SimpleCastState, side: ClusterSlotSide): SlotState? =
        if (state !is SimpleCastState.CastingSplit) null else when (side) {
            ClusterSlotSide.LEFT -> state.left
            ClusterSlotSide.RIGHT -> state.right
        }
}
