package com.byd.clusternav.launcher

import android.app.Activity
import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.behind.BehindHomeRunner
import com.byd.clusternav.launcher.behind.BehindHomeSequence
import com.byd.clusternav.launcher.trip.TripConfig
import com.byd.clusternav.launcher.trip.TripHub

/**
 * ═══ F2/F3 — KEO NỐI chuyến lên xe của MỘT màn chính ═════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.5 · U6. Hai vai, cùng mấy phụ thuộc của màn chính (cùng
 * khuôn `KachiHomeShortcuts` — tách khỏi [KachiHomeActivity] vì trần 500 dòng):
 *  1. **Chủ của chuyến** ([TripHub.Host]): chuyến chạy ở mức tiến trình (luồng `kachi-trip`) hỏi màn này ô nào đang
 *     hiện + màn ảo nào sống (A5 [stagingCandidates]), kênh shell của màn, và chạy R0.3 qua CHÍNH
 *     [KachiHomeSlots.startBehind] (mutex `kachi-behind` chung với lối tắt — không có bên thi hành thứ hai).
 *  2. **Cổng Cài đặt** ([TripSettingsPort]): ngăn kéo chọn app ở chế độ [AppDrawer.Mode.PICK_TRIP] + ghi cấu hình qua
 *     intent ViewModel (`HomeViewModel.setTripConfig` — `GridSeamGuardTest.chi ViewModel duoc ghi ben`).
 *
 * Không giữ state nào để quyết: [view] đọc lại state + cây ô mỗi lần hỏi (CLAUDE.md §5).
 */
internal class KachiHomeTrip(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
    private val slots: () -> KachiHomeSlots,
    private val workspace: () -> WorkspaceView,
    private val drawer: () -> DrawerController,
    /** Kênh shell của màn (`launcherSeam`) — đọc MỖI LẦN: nó được gán ở luồng nền sau khi màn đã mở. */
    private val channel: () -> ((String) -> String)?,
) : TripHub.Host, TripSettingsPort {

    init { TripHub.bind(this) }

    // ── TripHub.Host ────────────────────────────────────────────────────────────────────────────────────────────

    override fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    override fun shell(): ((String) -> String)? = channel()

    override fun view(): TripHub.HomeView {
        val st = viewModel.uiState.value
        val count = EffectiveLayout.slotCount(st.preset, st.customLayout)
        val shown = st.effectiveWorkspace.slots
        val apps = shown.take(count).withIndex().mapNotNull { (i, c) -> (c as? SlotContent.App)?.let { it.pkg to i } }
        return TripHub.HomeView(
            appSlots = apps.map { it.first },
            stages = workspace().stagingCandidates(shown, count),
            slots = apps.distinctBy { it.first }.toMap(),   // A2: gói → ô 0-based (ô đầu thắng — luật một-app-một-ô)
        )
    }

    override fun startBehind(
        pkg: String,
        stages: List<BehindHomePlan.Stage>,
        done: (BehindHomeSequence.Outcome) -> Unit,
    ): BehindHomePlan.Stage? = slots().startBehind(pkg, stages, done)

    override fun behindChain(
        what: String,
        body: (BehindHomeRunner.Kit) -> BehindHomeSequence.Outcome,
        done: (BehindHomeSequence.Outcome) -> Unit,
        needsAnchor: Boolean,
    ) = slots().behindChain(what, body, done, needsAnchor)

    // ── TripSettingsPort ────────────────────────────────────────────────────────────────────────────────────────

    override fun openPicker(selected: List<String>, onApply: (List<String>) -> Unit) =
        drawer().openShortcutPicker(selected, onApply, AppDrawer.Mode.PICK_TRIP)

    override fun save(cfg: TripConfig) = viewModel.setTripConfig(cfg)
}
