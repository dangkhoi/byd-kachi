package com.byd.clusternav.modules.clustercast.simplified

/**
 * Simplified Cluster Cast state model.
 *
 * Replaces the 11-state V2 machine with 4 stable states:
 * OFF → IDLE → CASTING_FULL / CASTING_SPLIT
 *
 * Field-proven 2026-08-02: all commands verified on vehicle.
 */

// ─── App classification ───────────────────────────────────────────────────────

enum class AppType {
    CARPLAY,
    ANDROID_AUTO,
    NORMAL;

    val isProtected: Boolean get() = this == CARPLAY || this == ANDROID_AUTO
    val isResizable: Boolean get() = this == NORMAL
}

// ─── Display configuration (measured on vehicle 2026-08-02) ───────────────────

data class CastBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    override fun toString() = "$left,$top,$right,$bottom"
}

data class DisplayConfig(
    val wmSize: String,
    val overscan: String,
    val density: String = "reset",
    /** Task bounds for `am task resize` — null means use default viewport. */
    val bounds: CastBounds? = null,
) {
    companion object {
        val CARPLAY = DisplayConfig(wmSize = "1422x800", overscan = "10,-120,10,50")
        val ANDROID_AUTO = DisplayConfig(wmSize = "1920x1080", overscan = "0,0,0,0")
        val NORMAL_DEFAULT = DisplayConfig(wmSize = "1920x720", overscan = "0,0,0,0", density = "240", bounds = CastBounds(0, 0, 1920, 720))

        fun forAppType(type: AppType): DisplayConfig = when (type) {
            AppType.CARPLAY -> CARPLAY
            AppType.ANDROID_AUTO -> ANDROID_AUTO
            AppType.NORMAL -> NORMAL_DEFAULT
        }
    }
}

// ─── Slot state (for split mode) ─────────────────────────────────────────────

data class SlotState(
    val pkg: String,
    val displayConfig: DisplayConfig,
)

enum class ClusterSlotSide { LEFT, RIGHT }

/**
 * Per-app geometry profile key (R3/R4).
 *
 * A profile is either [FULL] (full-cluster placement, stored under the legacy no-suffix prefs
 * keys) or a split placement keyed by ([side] ∈ {LEFT,RIGHT}, [percent] ∈ [SPLIT_PERCENTS]).
 * With the 9 supported ratios that is 9×2 split keys + FULL = **19 profiles per app** (R3).
 *
 * Data-driven, not a fixed enum: split percents come from [SPLIT_PERCENTS] so extending the ratio
 * set is a one-line change. The [key] string ("FULL", "L30", "R70") is the round-trippable prefs
 * token and is backward-compatible with keys the predecessor saved (which only used {50,30,70}).
 *
 * Pure Kotlin (no Android import) so it lives in :core (LayeringRulesTest Q1).
 */
class CastProfile private constructor(
    /** LEFT/RIGHT for a split profile; null (together with [percent]) means [FULL]. */
    val side: ClusterSlotSide?,
    /** leftPercent bucket (one of [SPLIT_PERCENTS]) for a split profile; null means [FULL]. */
    val percent: Int?,
) {
    /** True for the full-cluster profile (legacy no-suffix prefs keys). */
    val isFull: Boolean get() = side == null || percent == null

    /**
     * Round-trippable prefs token: `"FULL"` for the full profile, else `"L<percent>"` /
     * `"R<percent>"` (e.g. `L30`, `R70`). Stable across releases — used verbatim as the prefs
     * key suffix, so it must never change format for an existing percent (backward compat, R3).
     */
    val key: String
        get() = if (isFull) FULL_KEY else (if (side == ClusterSlotSide.LEFT) "L" else "R") + percent

    override fun toString(): String = key
    override fun equals(other: Any?): Boolean =
        other is CastProfile && other.side == side && other.percent == percent
    override fun hashCode(): Int = 31 * (side?.ordinal ?: -1) + (percent ?: -1)

    companion object {
        /** All supported split ratios as leftPercent, step 10 → {10,20,…,90} (R3). */
        val SPLIT_PERCENTS: List<Int> = (10..90 step 10).toList()

        /** Fallback ratio when a saved/received percent is not one of [SPLIT_PERCENTS]. */
        const val DEFAULT_PERCENT: Int = 50

        private const val FULL_KEY = "FULL"

        /** The full-cluster profile (legacy no-suffix prefs keys). */
        val FULL: CastProfile = CastProfile(null, null)

        /**
         * Profile for a split ([side], [leftPercent]). A [leftPercent] outside [SPLIT_PERCENTS]
         * falls back to [DEFAULT_PERCENT] (R3: "unknown/legacy percent → nearest default (50)").
         */
        fun of(side: ClusterSlotSide, leftPercent: Int): CastProfile =
            CastProfile(side, normalizePercent(leftPercent))

        /** Clamp an arbitrary percent to a valid [SPLIT_PERCENTS] bucket (unknown → [DEFAULT_PERCENT]). */
        fun normalizePercent(percent: Int): Int =
            if (percent in SPLIT_PERCENTS) percent else DEFAULT_PERCENT

        /**
         * Parse a prefs [key] token back into a profile (backward-compat with saved keys).
         * `"FULL"` → [FULL]; `"L<pct>"`/`"R<pct>"` → the matching split (unknown percent →
         * [DEFAULT_PERCENT]); anything malformed → null.
         */
        fun fromKey(key: String): CastProfile? {
            if (key == FULL_KEY) return FULL
            if (key.length < 2) return null
            val side = when (key[0]) {
                'L' -> ClusterSlotSide.LEFT
                'R' -> ClusterSlotSide.RIGHT
                else -> return null
            }
            val pct = key.substring(1).toIntOrNull() ?: return null
            return of(side, pct)
        }
    }
}

// ─── Cast state (immutable, UI observes this) ─────────────────────────────────

sealed interface SimpleCastState {
    /** Projection closed. Cluster shows gauges. */
    object Off : SimpleCastState { override fun toString() = "Off" }

    /** Projection opening (transient, ~1-2s). */
    object Opening : SimpleCastState { override fun toString() = "Opening" }

    /** Projection open, no app on cluster. Ready to cast. */
    object Idle : SimpleCastState { override fun toString() = "Idle" }

    /** One app occupies the full cluster (CP/AA or single normal app). */
    data class CastingFull(
        val targetPkg: String,
        val appType: AppType,
        val displayConfig: DisplayConfig,
        val taskId: Int? = null,  // saved at cast time for exact return
    ) : SimpleCastState

    /** Two normal apps split the cluster left/right. */
    data class CastingSplit(
        val left: SlotState?,
        val right: SlotState?,
    ) : SimpleCastState {
        init {
            require(left != null || right != null) { "At least one slot must be occupied" }
        }
    }

    /** App being returned to main display (transient). */
    object Stopping : SimpleCastState { override fun toString() = "Stopping" }

    /** Transient error, auto-clears. */
    data class Error(val message: String) : SimpleCastState

    /** Projection closing (transient). */
    object Closing : SimpleCastState { override fun toString() = "Closing" }
}

// ─── Cast intent (what the user wants to do) ──────────────────────────────────

sealed interface SimpleCastIntent {
    /** Cast the given app to full cluster. */
    data class CastFull(val pkg: String, val appType: AppType) : SimpleCastIntent

    /** Cast app to a specific slot (left or right). */
    data class CastSlot(val pkg: String, val side: ClusterSlotSide) : SimpleCastIntent

    /** Stop everything (full mode) or stop a specific slot (split mode). */
    data class Stop(val slot: ClusterSlotSide? = null) : SimpleCastIntent

    /** Close projection entirely. */
    object Close : SimpleCastIntent { override fun toString() = "Close" }
}

// ─── Slot validation (R4 safety: CP/AA must never enter split/freeform) ───────

/**
 * Pre-condition validator for cast operations.
 *
 * AppType classification is CLOSED — callers cannot override or extend it.
 * CP/AA packages are identified by [AppMover.classifyApp] which is authoritative.
 *
 * Rules:
 * - Protected apps (CP/AA) → CastFull ONLY (fullscreen on standard stack).
 * - Protected apps CANNOT enter CastSlot (split/freeform → crash path on DiLink3).
 * - CastSlot rejects if the target slot is already occupied.
 * - Package must be non-blank and not contain shell-injection characters.
 */
object CastSlotValidator {

    /** Validate a CastFull intent. Returns null if valid, or a reject reason. */
    fun validateCastFull(pkg: String, appType: AppType, projectionOpen: Boolean): CastRejectReason? {
        if (!projectionOpen) return CastRejectReason.DISPLAY_UNAVAILABLE
        if (!isValidPackage(pkg)) return CastRejectReason.INVALID_PACKAGE
        // CP/AA full is allowed — that's the only mode they support
        return null
    }

    /**
     * Validate a CastSlot intent. Returns null if valid, or a reject reason.
     *
     * CRITICAL: Protected apps (CP/AA) MUST be rejected from slot mode.
     * On DiLink3, freeform windowing of CP causes surfaceflinger crash and
     * leaves the cluster in an unrecoverable state requiring power-cycle.
     */
    fun validateCastSlot(
        pkg: String,
        side: ClusterSlotSide,
        currentState: SimpleCastState,
        projectionOpen: Boolean,
    ): CastRejectReason? {
        if (!projectionOpen) return CastRejectReason.DISPLAY_UNAVAILABLE
        if (!isValidPackage(pkg)) return CastRejectReason.INVALID_PACKAGE

        // R4: Protected apps CANNOT enter split/freeform mode
        val appType = AppMover.classifyApp(pkg)
        if (appType.isProtected) return CastRejectReason.PROTECTED_FULLSCREEN_STACK_UNPROVEN

        // Slot occupancy check
        if (currentState is SimpleCastState.CastingSplit) {
            val occupied = when (side) {
                ClusterSlotSide.LEFT -> currentState.left
                ClusterSlotSide.RIGHT -> currentState.right
            }
            if (occupied != null) return CastRejectReason.SLOT_OCCUPIED
        }
        return null
    }

    /** Minimal package name validation — reject empty, blank, or shell-injection attempts. */
    private fun isValidPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        // Package names: [a-zA-Z0-9_.] only. Reject anything else.
        return pkg.all { it.isLetterOrDigit() || it == '.' || it == '_' }
    }
}

// ─── Shell interface (dependency inversion — core cannot import dadb) ─────────

interface SimpleCastShell {
    fun execute(command: String): ShellResult
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val success: Boolean get() = exitCode == 0
}

// ─── Prefs interface (persistence, implemented in app module) ─────────────────

interface SimpleCastPrefs {
    fun displayConfigFor(pkg: String): DisplayConfig?
    fun saveDisplayConfig(pkg: String, config: DisplayConfig)

    /**
     * Profile-scoped geometry (R4). The no-arg overloads above are equivalent to
     * [CastProfile.FULL] and remain the backward-compatible legacy keys.
     */
    fun displayConfigFor(pkg: String, profile: CastProfile): DisplayConfig?
    fun saveDisplayConfig(pkg: String, profile: CastProfile, config: DisplayConfig)
    fun lastDisplayId(): Int?
    fun saveLastDisplayId(id: Int)

    // Autostart
    fun autoStartPackage(): String?
    fun setAutoStartPackage(pkg: String?)
    fun autoStartEnabled(): Boolean
    fun setAutoStartEnabled(enabled: Boolean)

    // Split ratio
    fun splitRatioLeftPercent(): Int
    fun setSplitRatioLeftPercent(pct: Int)

    // One-time setup flags
    fun dozeWhitelistApplied(): Boolean
    fun setDozeWhitelistApplied(applied: Boolean)

    // Autostart split
    fun autoStartLeftPackage(): String?
    fun setAutoStartLeftPackage(pkg: String?)
    fun autoStartRightPackage(): String?
    fun setAutoStartRightPackage(pkg: String?)
    fun autoStartSplitEnabled(): Boolean
    fun setAutoStartSplitEnabled(enabled: Boolean)

    /**
     * Master Cluster-Cast enable. Default FALSE (owner 2026-08-11) = nav-only: the app opens NO
     * projection, shows NO floating bubble, runs NO cast-autostart, and leaves the cluster on native
     * gauges so navigation shows on the cluster with no competing projection. When TRUE the app opens
     * the projection + shows the floating bubble (opt-in). This ONLY gates the Cast track;
     * navigation→cluster (broadcast) and HUD are independent and keep working either way.
     */
    fun castEnabled(): Boolean
    fun setCastEnabled(enabled: Boolean)
}
