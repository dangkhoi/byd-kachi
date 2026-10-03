package com.byd.clusternav.launcher

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.behind.BehindHomeSequence

/**
 * ═══ F1 · U5 — KEO NỐI lối tắt ứng dụng của MỘT màn chính: chạm → bảng quyết định → đường thi hành ═══════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R1.5 · §4.4.3. Tách khỏi [KachiHomeActivity] (trần 500 dòng);
 * không giữ đường riêng nào tới cửa sổ — mọi việc đi qua đúng các đường đã có:
 *
 *  | Kết quả [ShortcutPlan.decide] | Đường thi hành |
 *  |---|---|
 *  | `Prompt` | `ShellAccessUi.allowOrPrompt` (thẻ READY-AT-HOME / toast) — 0 lệnh |
 *  | `OpenFull` | [KachiHomeSlots.openAppFullscreen] — đường ngăn kéo "Ứng dụng" (Intent, không cần kênh) |
 *  | `PlaceTemp` | [KachiHomeSlots.placeTemporary] — đặt TẠM (không ghi `slot_n`), app cũ ra sau màn nhà (R0.1) |
 *  | `StartBehind` | [KachiHomeSlots.startBehind] qua ô sống ([stagingCandidates], A5) — mảnh chung R0.3 |
 *  | `Noop`/`Highlight` | nháy khung ô ([flashSlot]) + một dòng lý do |
 *  | `DetachToFull` | `allowOrPrompt` rồi [KachiHomeSlots.detachToFull] — K7 qua rào (T-M2 [ĐO]: Intent không tách được app khỏi màn ảo); về ô = K8 khi màn nhà hiện lại |
 *  | `Reopen` | `allowOrPrompt` rồi [KachiHomeSlots.reviveInSlot] — đường của thẻ "App đã đóng" (FIX286 R-SC2); ô chưa sẵn ⇒ nháy ô |
 *  | `Refuse` | một dòng lý do — 0 lệnh |
 *
 * FIX286 · R-SC2: dòng 4/9/12 (B được XẾP ở một ô) quyết theo SỰ THẬT lúc chạm — [ShortcutPlan.presenceSlot] chỉ ra ô cần đo,
 * [KachiHomeSlots.presence] đọc MỘT `am stack list` trên luồng nền rồi mới [decideAndAct]. Mọi dòng khác quyết ngay, 0 lệnh.
 *
 * Kiểu cần kênh (*Ô n* · *Chạy ngầm*) gọi `ShellAccessUi.allowOrPrompt` NGAY TRƯỚC khi thi hành (kênh có thể vừa mất giữa
 * lúc quyết và lúc làm); cổng thi hành READY-AT-HOME vẫn đứng sau (quên gọi thì chỉ mất lời nhắc, lệnh vẫn bị chặn).
 *
 * Chủ nhận chạm của màn ([ShortcutHub.Host]) + cổng trang Cài đặt ([ShortcutSettingsPort]). Dựng lười ở lượt render đầu
 * (`KachiHomeRender`) — lúc đó [ShortcutHub.publish] lần đầu cũng chạy ⇒ khối/widget có danh sách trước cú chạm đầu.
 */
internal class KachiHomeShortcuts(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
    private val slots: () -> KachiHomeSlots,
    private val workspace: () -> WorkspaceView,
    private val drawer: () -> DrawerController,
    /** Mở Cài đặt ở nhóm Thanh trạng thái & thanh nút (chạm ô "chưa có lối tắt"). */
    private val openSettingsBars: () -> Unit,
) : ShortcutHub.Host, ShortcutSettingsPort {

    init { ShortcutHub.bind(activity, this) }

    /** Đẩy danh sách đang hiệu lực tới hai bề mặt vẽ — gọi ở MỖI render (giống hệt ⇒ hub bỏ qua). */
    fun publish(state: HomeUiState) = ShortcutHub.publish(state.shortcuts)

    // ── ShortcutHub.Host ────────────────────────────────────────────────────────────────────────────────────────

    override fun onShortcut(sc: AppShortcut) {
        val st = viewModel.uiState.value
        val count = EffectiveLayout.slotCount(st.preset, st.customLayout)
        val shown = st.effectiveWorkspace.slots
        val pm = activity.packageManager
        val info = appInfo(pm, sc.pkg)
        val stages = if (sc.mode == ShortcutMode.Background) workspace().stagingCandidates(shown, count) else emptyList()
        val input = ShortcutPlan.Input(
            shortcut = sc, slots = shown, slotCount = count,
            usable = ShellAccessUi.usableNow(),
            installed = info != null,
            exclusion = exclusionOf(sc.pkg, info),
            hasLiveStage = BehindHomePlan.stagingSlot(stages, sc.pkg) != null,   // CÙNG bộ chọn mà runner dùng
            // T-M2 [ĐO máy ảo 02/10]: Intent từ HOME KHÔNG kéo được task ra khỏi màn ảo ô (4/4 app) ⇒ `false` (dòng 10 hỏi quyền).
            fullByIntent = false,
        )
        // FIX286 · R-SC2 — B được xếp ở ô (dòng 4/9/12): bố cục KHÔNG nói B còn sống ⇒ đo trước, quyết sau (CLAUDE.md §5).
        val probe = ShortcutPlan.presenceSlot(input)
        if (probe < 0) return decideAndAct(sc, input, count, stages)
        slots().presence(probe, sc.pkg) { measured -> decideAndAct(sc, input.copy(presence = measured), count, stages) }
    }

    /** Bảng thuần ⇒ một dòng log (kèm phép đo sống/chết nếu có) ⇒ đường thi hành. Luồng chính. */
    private fun decideAndAct(sc: AppShortcut, input: ShortcutPlan.Input, count: Int, stages: List<BehindHomePlan.Stage>) {
        val action = ShortcutPlan.decide(input)
        Log.i(TAG, "tap ${sc.pkg} ${AppShortcutCodec.modeCode(sc.mode)} usable=${input.usable} stages=${stages.size} presence=${input.presence} -> $action")
        act(sc, action, count, stages)
    }

    override fun onPickShortcuts() = openSettingsBars()

    // ── ShortcutSettingsPort ────────────────────────────────────────────────────────────────────────────────────

    override fun openPicker(selected: List<String>, onApply: (List<String>) -> Unit) =
        drawer().openShortcutPicker(selected, onApply)

    override fun save(items: List<AppShortcut>) = viewModel.setAppShortcuts(items)

    // ── Thi hành ────────────────────────────────────────────────────────────────────────────────────────────────

    private fun act(sc: AppShortcut, action: ShortcutAction, count: Int, stages: List<BehindHomePlan.Stage>) {
        when (action) {
            ShortcutAction.Prompt -> ShellAccessUi.allowOrPrompt(activity)
            is ShortcutAction.OpenFull -> {
                action.reason?.let { say(reasonText(it, sc, count, -1)) }
                slots().openAppFullscreen(sc.pkg)
            }
            is ShortcutAction.Noop -> {
                if (action.highlight >= 0) workspace().flashSlot(action.highlight)
                action.reason?.let { say(reasonText(it, sc, count, action.highlight)) }
            }
            is ShortcutAction.Highlight -> {
                workspace().flashSlot(action.slot)
                say(reasonText(action.reason, sc, count, action.slot))
            }
            is ShortcutAction.PlaceTemp -> {
                if (!ShellAccessUi.allowOrPrompt(activity)) return
                slots().placeTemporary(action.slot, sc.pkg)
            }
            // T-M2 [ĐO máy ảo 02/10]: Intent từ HOME KHÔNG tách app khỏi màn ảo (`am_new_intent` tại chỗ, 4/4 app) ⇒ K7 qua
            // kênh (tách được 4/4, pid giữ). Ô hiện thẻ "Đang mở toàn màn"; màn nhà hiện lại ⇒ K8 đưa app về ô (T-M6 [ĐO]).
            is ShortcutAction.DetachToFull -> {
                if (!ShellAccessUi.allowOrPrompt(activity)) return
                val started = slots().detachToFull(action.slot) { ok ->
                    if (!ok) say(activity.getString(R.string.kachi_sc_full_failed, label(sc.pkg)))
                }
                if (!started) { workspace().flashSlot(action.slot); say(reasonText(ShortcutPlan.Reason.IN_OTHER_SLOT, sc, count, action.slot)) }
            }
            // FIX286 · R-SC2 (dòng 4a/12a): bố cục xếp B ở ô, `am stack list` lúc chạm nói B KHÔNG còn task ⇒ mở lại vào ô, cùng
            // đường chạm thẻ "App đã đóng — chạm để mở lại". Ô chưa sẵn / lượt mở đang chạy dở ⇒ chỉ nháy ô, 0 lệnh.
            is ShortcutAction.Reopen -> {
                if (!ShellAccessUi.allowOrPrompt(activity)) return
                val ok = slots().reviveInSlot(action.slot, sc.pkg)
                Log.i(TAG, "mở lại ${sc.pkg} vào ô ${action.slot}: ${if (ok) "đã ra lệnh" else "ô đang mở dở / chưa sẵn ⇒ chỉ nháy ô"}")
                if (!ok) workspace().flashSlot(action.slot)
            }
            is ShortcutAction.Refuse -> say(reasonText(action.reason, sc, count, -1))
            ShortcutAction.StartBehind -> {
                if (!ShellAccessUi.allowOrPrompt(activity)) return
                val stage = slots().startBehind(sc.pkg, stages) { out -> onBehindDone(sc, out) }
                if (stage == null) say(reasonText(ShortcutPlan.Reason.NO_STAGE, sc, count, -1))
            }
        }
    }

    /** Kết quả chuỗi chạy ngầm (luồng chính): thành công thì im; app đang chạy / không dàn được thì nói một câu. */
    private fun onBehindDone(sc: AppShortcut, out: BehindHomeSequence.Outcome) {
        when (out.result) {
            BehindHomeSequence.Result.ALREADY_RUNNING -> say(activity.getString(R.string.kachi_sc_running, label(sc.pkg)))
            BehindHomeSequence.Result.X_NOT_STAGED -> say(activity.getString(R.string.kachi_sc_bg_failed, label(sc.pkg)))
            else -> Unit   // MOVED/MOVED_HOME_RESTORED: im lặng là đúng · KEPT_UNDER: app sống dưới app ô (O1), log đã ghi
        }
    }

    private fun reasonText(r: ShortcutPlan.Reason, sc: AppShortcut, count: Int, slot: Int): String = when (r) {
        ShortcutPlan.Reason.NOT_INSTALLED -> activity.getString(R.string.kachi_sc_not_installed, sc.pkg)
        ShortcutPlan.Reason.SLOT_ABSENT -> activity.getString(R.string.kachi_sc_reason_absent, count)
        ShortcutPlan.Reason.IN_OTHER_SLOT -> activity.getString(R.string.kachi_sc_in_slot, label(sc.pkg), slot + 1)
        ShortcutPlan.Reason.RUNNING -> activity.getString(R.string.kachi_sc_running, label(sc.pkg))
        ShortcutPlan.Reason.SYSTEM_APP -> activity.getString(R.string.kachi_sc_refuse_system)
        ShortcutPlan.Reason.SELF -> activity.getString(R.string.kachi_sc_refuse_self)
        ShortcutPlan.Reason.CAST -> activity.getString(R.string.kachi_sc_refuse_cast, label(sc.pkg))
        ShortcutPlan.Reason.NO_STAGE -> activity.getString(R.string.kachi_sc_no_stage)
    }

    /**
     * Loại trừ R0.6 đo được NGAY trong tiến trình (không shell): chính Kachi · app hệ thống (`FLAG_SYSTEM`). "Đang chiếu
     * cụm" cần `am stack list` ⇒ không đo ở luồng chính; chuỗi chạy ngầm tự dừng khi app đã có task (kể cả đang chiếu
     * — ⇒ `ALREADY_RUNNING`, 0 lệnh đổi cửa sổ).
     */
    private fun exclusionOf(pkg: String, info: ApplicationInfo?): ShortcutPlan.Exclusion? = when {
        pkg == activity.packageName -> ShortcutPlan.Exclusion.SELF
        info != null && info.flags and ApplicationInfo.FLAG_SYSTEM != 0 -> ShortcutPlan.Exclusion.SYSTEM_APP
        else -> null
    }

    private fun appInfo(pm: PackageManager, pkg: String): ApplicationInfo? = try {
        pm.getApplicationInfo(pkg, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun label(pkg: String): String = InstalledApps.labelOf(activity, pkg) ?: pkg

    /** Màn chính là cửa sổ Activity (không phải lớp phủ) ⇒ toast hiện được (khác ngăn kéo — `PickerCapNoticeContractTest`). */
    private fun say(msg: String) = Toast.makeText(activity.applicationContext, msg, Toast.LENGTH_SHORT).show()

    internal companion object {
        /** Một thẻ log cho cả chuỗi chạm lối tắt — kể cả phép đo sống/chết ở `KachiHomeSlots.presence` (FIX286 R-SC2). */
        const val TAG = "KachiShortcut"
    }
}
