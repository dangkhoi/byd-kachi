package com.byd.clusternav.launcher

/**
 * PURE boot-time CAST-COORDINATION decision (:core, unit-tested) — split the workspace's per-slot apps into the
 * ones the launcher should mount into a slot vs the ones to SKIP because cluster-cast owns / will-cast them onto
 * the cluster (display 1).
 *
 * ── Why (B6) ────────────────────────────────────────────────────────────────────────────────────────────────
 * The launcher and cluster-cast share the one-location invariant enforced by
 * [com.byd.clusternav.system.AppLocationRegistry]: an app is EITHER in a launcher slot OR on the cluster, never
 * both. On boot the launcher must not fight cast — if cast already owns a package on the cluster, the launcher
 * must not (re)seed/mount it into a slot (which would overwrite the registry location and yank the app off the
 * cluster). This is the surface-INDEPENDENT decision the auto-start orchestration + the slot-seed path both use.
 *
 * Pure: no Android, no registry type — the caller passes a [castOwns] predicate (on-device this is
 * `!AppLocationRegistry.isCastable(pkg)`), so the decision is testable off-device with a plain lambda.
 */
object LauncherBootPlan {

    /** One workspace slot that holds a real app package. */
    data class SlotApp(val slot: Int, val pkg: String)

    /**
     * @property mount slot-apps the launcher OWNS and should bring up into their slot (cast does not own them).
     * @property skippedToCast slot-apps NOT mounted because cluster-cast owns/will-cast them onto the cluster.
     */
    data class Plan(val mount: List<SlotApp>, val skippedToCast: List<SlotApp>) {
        /** True iff there is at least one app slot to reason about (mount or skip). */
        val hasApps: Boolean get() = mount.isNotEmpty() || skippedToCast.isNotEmpty()
    }

    /**
     * From [slots] (indexed content, e.g. a persisted [WorkspaceState.slots]), collect the [SlotContent.App]
     * entries and partition them: [castOwns] == true ⇒ [Plan.skippedToCast] (do not fight cast), else
     * [Plan.mount]. Empty/Widget slots are ignored. Slot indices are preserved so the caller can place each
     * mounted app back into its exact slot.
     */
    fun plan(slots: List<SlotContent>, castOwns: (String) -> Boolean): Plan {
        val apps = slots.mapIndexedNotNull { index, content ->
            (content as? SlotContent.App)?.let { SlotApp(index, it.pkg) }
        }
        val (skip, mount) = apps.partition { castOwns(it.pkg) }
        return Plan(mount = mount, skippedToCast = skip)
    }

    /**
     * Kết quả RECONCILE cửa sổ launcher với [WorkspaceState] (quality-review 2026-09-15, R1/R2).
     *
     * @property mount app cần CÓ MẶT ở ô của nó (đúng vị trí theo state). Applier đảm bảo hosted đúng ô.
     * @property evict app đang hiện Ở MÀN LAUNCHER (display 0) mà state KHÔNG còn giữ ở ô nào ⇒ phải GỠ cửa sổ
     *   (release VdAppHost / đóng freeform) + gỡ khỏi registry. Đây là bước thiếu lâu nay: bug "một app hai ô" =
     *   app đã chuyển ô nhưng cửa sổ/host ở ô cũ không bị gỡ ngay (chờ death-poll 10-15s). Evict tường minh, không đợi.
     */
    data class Reconciliation(val mount: List<SlotApp>, val evict: List<String>)

    /**
     * PURE reconcile: từ [slots] (state = desired) + [placedOnLauncher] (pkg đang có cửa sổ Ở MÀN LAUNCHER, đọc
     * từ registry `onDisplay(0)` hoặc `am stack list`) → tính app cần đặt (mount) + app cần gỡ (evict = đang hiện
     * ở màn launcher nhưng không còn ô nào trong state giữ). [castOwns] loại app cast đang giữ khỏi mount (không
     * giành với cụm). App cast (không nằm ở display launcher) KHÔNG bị evict bởi hàm này. Test off-car.
     */
    fun reconcile(
        slots: List<SlotContent>,
        placedOnLauncher: Set<String>,
        castOwns: (String) -> Boolean,
    ): Reconciliation {
        val plan = plan(slots, castOwns)
        val desired = plan.mount.map { it.pkg }.toSet()
        val evict = placedOnLauncher.filter { it !in desired }
        return Reconciliation(mount = plan.mount, evict = evict)
    }
}
