package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.content.Intent
import android.os.Handler
import com.byd.clusternav.Lang
import com.byd.clusternav.modules.clustercast.simplified.AppMover
import com.byd.clusternav.modules.clustercast.simplified.BubbleGesturePlanner
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastIntent
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.system.PackageQueries

/**
 * Dispatches single-icon bubble gestures to the simplified Cast coordinator (R5 / #7).
 *
 * The decision logic is pure and lives in [BubbleGesturePlanner]; this class only wires those
 * decisions to the coordinator plus the Android bits (foreground detection, toasts, startActivity).
 *
 * Gestures:
 *  - [onTap] — Idle → cast the foreground FULL (with the launcher guard); a FULL cast + a different
 *    castable foreground → replace it in one tap (3.1.5 · R31); otherwise casting → return everything.
 *  - [onSubmenuAction] — long-press submenu: Trái/Phải on a free half casts the foreground into it, on
 *    an occupied half replaces it (different castable foreground, R31) or returns it; Cấu hình opens
 *    Kachi Settings › Cluster cast.
 *
 * The caller ([FloatingBubbleService]) invokes the cast paths on the tap executor (token-guarded,
 * off the main thread) because [castableForeground] performs shell I/O.
 */
internal class BubbleActionDispatcher(
    private val context: Context,
    private val handler: Handler,
    private val toast: (String) -> Unit,
) {
    /**
     * TAP. Idle → cast the foreground FULL (Wave-1 launcher guard); a FULL cast + a DIFFERENT castable foreground → REPLACE it in
     * one tap (3.1.5 · R31 — `CastFull`, whose handler returns the old app before pinning the new one); otherwise anything
     * casting → slot-less `Stop`. The decision is the pure [BubbleGesturePlanner.tapOutcome]; this only feeds it the foreground.
     * Called on the tap executor (already token-guarded) because the foreground probe is shell I/O.
     */
    fun onTap() {
        val coordinator = SimpleCastRuntime.coordinator(context)
        val state = coordinator.state
        // Idle = about to cast ⇒ the cast-path probe (dismisses a PiP first, as before). Casting ⇒ READ-ONLY probe: a tap that ends
        // up returning must not change anything on display 0 (R31). Split/transient ⇒ no probe (the outcome does not depend on it).
        val foreground = if (!BubbleGesturePlanner.tapNeedsForeground(state)) null
        else castableForeground(coordinator, dismissPip = state is SimpleCastState.Idle)
        when (BubbleGesturePlanner.tapOutcome(state, foreground)) {
            BubbleGesturePlanner.BubbleTapOutcome.RETURN -> {
                // Home / Kachi in front while casting ⇒ RETURN quietly (no "won't cast home" toast — nothing was cast).
                coordinator.dispatch(SimpleCastIntent.Stop())
                handler.post { toast(Lang.t("Đang trả app về…", "Returning app…")) }
            }
            BubbleGesturePlanner.BubbleTapOutcome.CAST_FULL -> {
                // Wave-1 launcher exclusion (R2): no castable foreground ⇒ the "won't cast home" toast, nothing dispatched.
                val pkg = foreground ?: return toastLauncherGuard()
                coordinator.dispatch(SimpleCastIntent.CastFull(pkg, AppMover.classifyApp(pkg)))
                handler.post { toast(castingToast(pkg)) }
            }
            BubbleGesturePlanner.BubbleTapOutcome.REPLACE_FULL -> {
                val pkg = foreground ?: return
                coordinator.dispatch(SimpleCastIntent.CastFull(pkg, AppMover.classifyApp(pkg)))
                handler.post { toast(replacingToast(pkg)) }
            }
            BubbleGesturePlanner.BubbleTapOutcome.PREPARING ->
                handler.post { toast(Lang.t("Đang chuẩn bị cụm…", "Preparing cluster…")) }
        }
    }

    /**
     * A long-press submenu choice. Trái/Phải cast the current foreground into that half (reusing the
     * launcher guard); Cấu hình opens the config screen. The slot mapping is the pure
     * [BubbleGesturePlanner.slotFor] contract. Cast branches run on the tap executor; [openConfig]
     * hops to the main thread itself.
     */
    fun onSubmenuAction(action: BubbleGesturePlanner.BubbleMenuAction) {
        val slot = BubbleGesturePlanner.slotFor(action)
        if (slot != null) onCastSlot(slot) else openConfig()
    }

    /**
     * A long-press submenu LEFT/RIGHT choice on [side] (owner 2026-08-12 toggle + 3.1.5 · R31 swap): a FREE side casts the
     * foreground into it; an OCCUPIED side is REPLACED in one press when the castable foreground is a different app not already on
     * the cluster (`CastSlot(replace = true)` — the coordinator returns the old half first), else RETURNED. The decision is the
     * pure [BubbleGesturePlanner.slotOutcome]. Runs on the tap executor.
     */
    fun onCastSlot(side: ClusterSlotSide) {
        val coordinator = SimpleCastRuntime.coordinator(context)
        val state = coordinator.state
        // Occupied side ⇒ READ-ONLY probe (it may only return that half); free side ⇒ cast-path probe, as before.
        val occupied = BubbleGesturePlanner.slotNeedsForegroundToDecide(state, side)
        val foreground = castableForeground(coordinator, dismissPip = !occupied)
        when (BubbleGesturePlanner.slotOutcome(state, side, foreground)) {
            BubbleGesturePlanner.BubbleSlotOutcome.RETURN -> {
                coordinator.dispatch(SimpleCastIntent.Stop(side))
                handler.post { toast(Lang.t("Đang trả app về…", "Returning app…")) }
            }
            BubbleGesturePlanner.BubbleSlotOutcome.CAST -> {
                val pkg = foreground ?: return toastLauncherGuard()
                coordinator.dispatch(SimpleCastIntent.CastSlot(pkg, side))
                handler.post { toast(castingToast(pkg)) }
            }
            BubbleGesturePlanner.BubbleSlotOutcome.REPLACE -> {
                val pkg = foreground ?: return
                coordinator.dispatch(SimpleCastIntent.CastSlot(pkg, side, replace = true))
                handler.post { toast(replacingToast(pkg)) }
            }
        }
    }

    /**
     * Open Kachi from the bubble (submenu → Cấu hình), landing **straight on Settings › Cluster cast**.
     *
     * Before 2026-09-13 this opened `MainActivity` (the old ClusterNav screen, now removed —
     * docs/specs/kachi-remove-legacy-screen.html R1). Dropping the user on the Kachi home screen instead
     * would cost three more taps to reach the same controls, so the intent carries
     * [com.byd.clusternav.launcher.EXTRA_OPEN_SETTINGS_GROUP]; an unknown id is ignored on the other side,
     * so a stale caller can never land somewhere surprising.
     *
     * Started with [Intent.FLAG_ACTIVITY_NEW_TASK] because a Service context has no task of its own.
     * Posted to the main thread so it is safe to call from the tap executor or a view click.
     */
    fun openConfig() {
        handler.post {
            runCatching {
                context.startActivity(
                    Intent(context, com.byd.clusternav.launcher.KachiHomeActivity::class.java)
                        .putExtra(
                            com.byd.clusternav.launcher.EXTRA_OPEN_SETTINGS_GROUP,
                            com.byd.clusternav.launcher.SettingsGroup.CAST.id,
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun castingToast(pkg: String): String {
        val name = pkg.substringAfterLast('.')
        return Lang.f("Chiếu {0}…", "Casting {0}…", name)
    }

    /** 3.1.5 · R31 — one-tap swap: the old app goes back, [pkg] takes its place. */
    private fun replacingToast(pkg: String): String {
        val name = pkg.substringAfterLast('.')
        return Lang.f("Đang thay bằng {0}…", "Replacing with {0}…", name)
    }

    private fun toastLauncherGuard() {
        handler.post { toast(Lang.t("Không cast màn hình chính", "Won't cast the launcher/home screen")) }
    }

    /**
     * Foreground package eligible for casting on the HOME display, or null when it must not be cast. Excludes ourselves plus
     * EVERY launcher/home package the platform knows about — the full CATEGORY_HOME resolver list (not just the default) UNIONed
     * with [AppMover.isLauncher] — so the BYD Dudu home can never be projected onto the cluster (#3 / R2, Wave 1). Posts NO
     * toast: since R31 a null here while casting means "return quietly"; the cast branches post [toastLauncherGuard] themselves.
     * [dismissPip] `false` = read-only probe (a single `am stack list`, no PiP removal).
     */
    private fun castableForeground(coordinator: SimpleCastCoordinator, dismissPip: Boolean): String? {
        val excluded = homePackages() + setOf(context.packageName)
        val foreground = runCatching { coordinator.foregroundPackage(HOME_DISPLAY_ID, excluded, dismissPip) }.getOrNull()
        if (foreground == null || foreground in excluded || AppMover.isLauncher(foreground)) return null
        return foreground
    }

    /**
     * Every package that declares a CATEGORY_HOME activity, not just the current default. BYD ships
     * more than one home candidate (Dudu + AOSP), and the user's default may differ from what is on
     * screen; excluding only the default let a non-default launcher slip through.
     */
    private fun homePackages(): Set<String> = runCatching {
        PackageQueries.queryActivities(
            context.packageManager, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        ).mapNotNull { it.activityInfo?.packageName }.toSet()
    }.getOrElse { emptySet() }

    companion object {
        private const val HOME_DISPLAY_ID = 0
    }
}
