package com.byd.clusternav.launcher

/**
 * ═══ F1 — CHẠM MỘT LỐI TẮT: bảng quyết định (thuần, `:core`) ════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.4.3 (C3, R1.5). Mỗi dòng của bảng 15 dòng là một nhánh
 * ở [decide]; `ShortcutPlanTest` có đúng một ca cho mỗi dòng. Lớp keo (`KachiHomeShortcuts`, nhóm B) gọi
 * `ShellAccessUi.allowOrPrompt` ở đúng các kết quả [ShortcutAction.Prompt] — lớp này không biết Android.
 *
 * Dòng 0 (trước bảng, không đánh số trong spec): app đã gỡ ⇒ [ShortcutAction.Refuse] `NOT_INSTALLED` (R1.2 *"chạm báo
 * chưa cài"*), áp cho mọi kiểu.
 */
object ShortcutPlan {

    /** Vì sao không làm / làm khác ý — tầng UI đổi ra chuỗi `kachi_sc_*`. */
    enum class Reason { NOT_INSTALLED, SLOT_ABSENT, SLOT_WIDGET, IN_OTHER_SLOT, RUNNING, SYSTEM_APP, CAST, SELF, NO_STAGE }

    /** Loại trừ chung R0.6 — đo ở tầng `:app` (PackageManager · `am stack list` · tên gói của chính mình). */
    enum class Exclusion { SYSTEM_APP, CAST, SELF }

    /**
     * Mọi thứ [decide] cần — đều là SỰ THẬT đã đo ở tầng `:app`, không cờ RAM nào quyết định đổi cửa sổ (CLAUDE.md §5).
     *
     * @property slots nội dung các ô ĐANG HIỆN (effective workspace, đã áp lớp tạm), 0-based.
     * @property slotCount số ô đang hiện (`EffectiveLayout.slotCount`).
     * @property usable kênh điều khiển cửa sổ dùng được (`ShellReadinessPolicy.usable`).
     * @property installed gói còn cài.
     * @property running B đã có task (chỉ đo được qua kênh; không đo ⇒ `false` — chuỗi chạy ngầm tự đo lại trước lệnh).
     * @property exclusion loại trừ R0.6 của B, `null` = không.
     * @property hasLiveStage có ít nhất một ô app đang sống mà app ≠ B (chỗ dàn dựng của R0.3).
     * @property fullByIntent kết quả đo T-M2: Intent từ HOME kéo được task của B từ màn ảo ô ra display 0. Chưa đo ⇒
     *   `false` (dòng 10 hỏi quyền thay vì đoán).
     */
    data class Input(
        val shortcut: AppShortcut,
        val slots: List<SlotContent>,
        val slotCount: Int,
        val usable: Boolean,
        val installed: Boolean = true,
        val running: Boolean = false,
        val exclusion: Exclusion? = null,
        val hasLiveStage: Boolean = false,
        val fullByIntent: Boolean = false,
    )

    /** Ô 0-based đang giữ [pkg] trong các ô đang hiện, -1 nếu không. */
    fun slotOf(slots: List<SlotContent>, slotCount: Int, pkg: String): Int =
        slots.take(slotCount).indexOfFirst { it is SlotContent.App && it.pkg == pkg }

    fun decide(i: Input): ShortcutAction {
        val b = i.shortcut.pkg
        if (!i.installed) return ShortcutAction.Refuse(Reason.NOT_INSTALLED)                         // dòng 0
        val m = slotOf(i.slots, i.slotCount, b)
        return when (val mode = i.shortcut.mode) {
            is ShortcutMode.Slot -> {
                val n = mode.n - 1
                when {
                    !i.usable -> ShortcutAction.Prompt                                                  // 1
                    mode.n > i.slotCount -> ShortcutAction.OpenFull(Reason.SLOT_ABSENT)                 // 2
                    i.slots.getOrNull(n).let { it is SlotContent.Widget || it is SlotContent.AppWidget } ->
                        ShortcutAction.OpenFull(Reason.SLOT_WIDGET)                                     // 3
                    m == n -> ShortcutAction.Noop(highlight = n)                                        // 4
                    m >= 0 -> ShortcutAction.Highlight(m, Reason.IN_OTHER_SLOT)                         // 5
                    else -> ShortcutAction.PlaceTemp(n, evict = (i.slots.getOrNull(n) as? SlotContent.App)?.pkg) // 6 · 7
                }
            }
            ShortcutMode.Full -> when {
                m < 0 -> ShortcutAction.OpenFull(null)                                                  // 8
                i.usable -> ShortcutAction.DetachToFull(m, byIntent = i.fullByIntent)                   // 9
                i.fullByIntent -> ShortcutAction.DetachToFull(m, byIntent = true)                       // 10a
                else -> ShortcutAction.Prompt                                                           // 10b
            }
            ShortcutMode.Background -> when {
                !i.usable -> ShortcutAction.Prompt                                                      // 11
                m >= 0 || i.running -> ShortcutAction.Noop(highlight = m, reason = Reason.RUNNING)      // 12
                i.exclusion != null -> ShortcutAction.Refuse(reasonOf(i.exclusion))                     // 13
                !i.hasLiveStage -> ShortcutAction.Refuse(Reason.NO_STAGE)                               // 14
                else -> ShortcutAction.StartBehind                                                      // 15
            }
        }
    }

    private fun reasonOf(e: Exclusion): Reason = when (e) {
        Exclusion.SYSTEM_APP -> Reason.SYSTEM_APP
        Exclusion.CAST -> Reason.CAST
        Exclusion.SELF -> Reason.SELF
    }
}

/** Kết quả của [ShortcutPlan.decide]. Ô đều 0-based. */
sealed interface ShortcutAction {
    /** Chưa có kênh ⇒ lớp keo gọi `ShellAccessUi.allowOrPrompt` (thẻ READY-AT-HOME), không đổi state. */
    object Prompt : ShortcutAction { override fun toString() = "Prompt" }

    /** Mở toàn màn bằng Intent (không cần kênh), kèm lý do khi khác ý người dùng. */
    data class OpenFull(val reason: ShortcutPlan.Reason?) : ShortcutAction

    /** Không làm gì; [highlight] ≥ 0 ⇒ nháy viền ô đó. */
    data class Noop(val highlight: Int, val reason: ShortcutPlan.Reason? = null) : ShortcutAction

    /** B đã ở ô [slot] khác ô được chọn ⇒ chỉ nháy viền + "đang ở ô m" (không dời — tránh force-stop/relaunch B). */
    data class Highlight(val slot: Int, val reason: ShortcutPlan.Reason) : ShortcutAction

    /** Đặt TẠM B vào ô [slot]; [evict] = app A đang ở ô đó (⇒ ra sau màn nhà, R0.1), `null` = ô trống. */
    data class PlaceTemp(val slot: Int, val evict: String?) : ShortcutAction

    /** B đang ở ô [slot] ⇒ kéo ra toàn màn (cơ chế theo đo T-M2: [byIntent] hay lệnh K7 qua kênh). */
    data class DetachToFull(val slot: Int, val byIntent: Boolean) : ShortcutAction

    /** Từ chối kèm lý do, không lệnh nào. */
    data class Refuse(val reason: ShortcutPlan.Reason) : ShortcutAction

    /** Chạy B phía sau màn nhà (R0.3). */
    object StartBehind : ShortcutAction { override fun toString() = "StartBehind" }
}
