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

/**
 * Dispatches single-icon bubble gestures to the simplified Cast coordinator (R5 / #7).
 *
 * The decision logic is pure and lives in [BubbleGesturePlanner]; this class only wires those
 * decisions to the coordinator plus the Android bits (foreground detection, toasts, startActivity).
 *
 * Gestures:
 *  - [onTap] — TOGGLE full: Idle → cast the foreground FULL (with the launcher guard); casting
 *    (full or split) → return everything (slot-less `Stop`).
 *  - [onSubmenuAction] — long-press submenu: Trái/Phải TOGGLE that half (return it if it is already
 *    cast, else cast the foreground into it), Cấu hình opens [com.byd.clusternav.MainActivity].
 *
 * The caller ([FloatingBubbleService]) invokes the cast paths on the tap executor (token-guarded,
 * off the main thread) because [detectForeground] performs shell I/O.
 */
internal class BubbleActionDispatcher(
    private val context: Context,
    private val handler: Handler,
    private val toast: (String) -> Unit,
) {
    /**
     * TAP = toggle full. Reuses the existing CastFull path (with the Wave-1 launcher guard) when
     * idle, and the existing slot-less `Stop` ("return to cluster gauges") when anything is casting.
     * Called on the tap executor (already token-guarded).
     */
    fun onTap() {
        val coordinator = SimpleCastRuntime.coordinator(context)
        when (BubbleGesturePlanner.tapOutcome(coordinator.state)) {
            BubbleGesturePlanner.BubbleTapOutcome.RETURN -> {
                coordinator.dispatch(SimpleCastIntent.Stop())
                handler.post { toast(Lang.t("Đang trả app về…", "Returning app…")) }
            }
            BubbleGesturePlanner.BubbleTapOutcome.CAST_FULL -> {
                // detectForeground() posts the "won't cast home" guard toast when it returns null,
                // so bailing here is enough (Wave-1 launcher exclusion, R2 — preserved unchanged).
                val foreground = detectForeground(coordinator) ?: return
                coordinator.dispatch(SimpleCastIntent.CastFull(foreground, AppMover.classifyApp(foreground)))
                handler.post { toast(castingToast(foreground)) }
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
     * A long-press submenu LEFT/RIGHT choice TOGGLES that half (owner 2026-08-12): if that side is
     * already cast, RETURN it; otherwise CAST the current foreground app into it (reusing the
     * launcher guard). Mirrors the in-app zone buttons. The occupancy decision is the pure
     * [BubbleGesturePlanner.slotOutcome] contract. Runs on the tap executor.
     */
    fun onCastSlot(side: ClusterSlotSide) {
        val coordinator = SimpleCastRuntime.coordinator(context)
        when (BubbleGesturePlanner.slotOutcome(coordinator.state, side)) {
            BubbleGesturePlanner.BubbleSlotOutcome.RETURN -> {
                coordinator.dispatch(SimpleCastIntent.Stop(side))
                handler.post { toast(Lang.t("Đang trả app về…", "Returning app…")) }
            }
            BubbleGesturePlanner.BubbleSlotOutcome.CAST -> {
                // detectForeground() posts the launcher-guard toast + returns null when not castable.
                val foreground = detectForeground(coordinator) ?: return
                coordinator.dispatch(SimpleCastIntent.CastSlot(foreground, side))
                handler.post { toast(castingToast(foreground)) }
            }
        }
    }

    /**
     * Open [com.byd.clusternav.MainActivity] from the bubble (submenu → Cấu hình). Started with
     * [Intent.FLAG_ACTIVITY_NEW_TASK] because a Service context has no task of its own. Posted to the
     * main thread so it is safe to call from the tap executor or a view click.
     */
    fun openConfig() {
        handler.post {
            runCatching {
                context.startActivity(
                    Intent(context, com.byd.clusternav.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun castingToast(pkg: String): String {
        val name = pkg.substringAfterLast('.')
        return Lang.t("Chiếu $name…", "Casting $name…")
    }

    /**
     * Foreground package eligible for casting on the HOME display, or null when it must not be
     * cast. Excludes ourselves plus EVERY launcher/home package the platform knows about — the full
     * CATEGORY_HOME resolver list (not just the default) UNIONed with [AppMover.isLauncher] — so the
     * BYD Dudu home can never be projected onto the cluster (#3 / R2, Wave 1). On a null or
     * launcher/home result this posts the "won't cast home" toast and returns null; callers only need
     * `?: return`.
     */
    private fun detectForeground(coordinator: SimpleCastCoordinator): String? {
        val excluded = homePackages() + setOf(context.packageName)
        val foreground = runCatching { coordinator.foregroundPackage(HOME_DISPLAY_ID, excluded) }.getOrNull()
        if (foreground == null || foreground in excluded || AppMover.isLauncher(foreground)) {
            handler.post { toast(Lang.t("Không cast màn hình chính", "Won't cast the launcher/home screen")) }
            return null
        }
        return foreground
    }

    /**
     * Every package that declares a CATEGORY_HOME activity, not just the current default. BYD ships
     * more than one home candidate (Dudu + AOSP), and the user's default may differ from what is on
     * screen; excluding only the default let a non-default launcher slip through.
     */
    private fun homePackages(): Set<String> = runCatching {
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
        ).mapNotNull { it.activityInfo?.packageName }.toSet()
    }.getOrElse { emptySet() }

    companion object {
        private const val HOME_DISPLAY_ID = 0
    }
}
