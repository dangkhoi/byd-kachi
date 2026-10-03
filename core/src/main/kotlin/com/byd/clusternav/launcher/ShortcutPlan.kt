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
 *
 * ## FIX286 · R-SC (lỗi xe owner 03/10, spec `docs/specs/kachi-286-field-fixes.html` §3.10) — ba dòng đổi
 *  - **Dòng 3** (ô n là widget): ~~mở toàn màn~~ ⇒ **đặt TẠM đè widget** như ô trống — owner: *"khi chọn app trên shortcut
 *    thì nó đạp widget ra để thay app vào đấy"*. Lớp tạm không ghi `slot_n` ([SlotOverlay]) ⇒ widget về khi khởi động lại
 *    Kachi / đổi hồ sơ; id widget bên thứ ba vẫn nằm trong lớp LƯU nên không bị thu hồi ([AppWidgetIds.used]).
 *  - **Dòng 4 / 12 / 9** (B ở ô theo bố cục): sống/chết quyết bằng SỰ THẬT đo lúc chạm ([Input.presence], [SlotPresence]) —
 *    owner: *"tắt gmaps … bấm lại icon gmaps ở shortcut, chỉ hiện icon gmaps … thay vì mở lại gmaps lên ô 1"*. Bố cục
 *    chỉ nói B ĐƯỢC XẾP ở ô; [presenceSlot] chỉ ra ô cần đo, lớp keo đo rồi mới gọi [decide].
 *
 * ## L8 (owner 03/10, OQ-L4-1) — dòng 14 thôi từ chối
 * *Chạy ngầm* khi bố cục không có ô app sống (chỉ widget) ⇒ [ShortcutAction.StartBehindHidden] (màn ảo ẩn của L4, cùng
 * chuỗi với chuyến lên xe) thay cho `Refuse(NO_STAGE)`. Ô sống vẫn LUÔN trước ([ShortcutAction.StartBehind], dòng 15).
 */
object ShortcutPlan {

    /** Vì sao không làm / làm khác ý — tầng UI đổi ra chuỗi `kachi_sc_*`. */
    enum class Reason { NOT_INSTALLED, SLOT_ABSENT, IN_OTHER_SLOT, RUNNING, SYSTEM_APP, CAST, SELF, NO_STAGE }

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
     * @property presence FIX286 · R-SC2 — B còn task trên màn ảo của ô [presenceSlot] không, đo `am stack list` LÚC CHẠM.
     *   Không đo ⇒ [SlotPresence.UNKNOWN] ⇒ các dòng 4/9/12 giữ hành vi trước FIX286 (không bao giờ mở lại khi chưa đo —
     *   mở lại là `am force-stop` B).
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
        val presence: SlotPresence = SlotPresence.UNKNOWN,
    )

    /** Ô 0-based đang giữ [pkg] trong các ô đang hiện, -1 nếu không. */
    fun slotOf(slots: List<SlotContent>, slotCount: Int, pkg: String): Int =
        slots.take(slotCount).indexOfFirst { it is SlotContent.App && it.pkg == pkg }

    /**
     * Ô (0-based) mà kết quả của [decide] PHỤ THUỘC phép đo sống/chết ([Input.presence]) — B đang được xếp ở ô đó và kiểu
     * lối tắt sẽ đi tới dòng 4 (*Ô n*, B đã ở ô n), 9 (*Toàn màn*, B ở ô m, có kênh) hoặc 12 (*Chạy ngầm*, B ở ô m).
     * `-1` = kết quả không phụ thuộc phép đo ⇒ lớp keo quyết ngay, 0 lệnh shell. Chưa cài / chưa có kênh ⇒ `-1` (dòng
     * 0/1/10/11 không cần đo, và không có kênh thì cũng không đo được).
     */
    fun presenceSlot(i: Input): Int {
        if (!i.installed || !i.usable) return -1
        val m = slotOf(i.slots, i.slotCount, i.shortcut.pkg)
        if (m < 0) return -1
        return when (val mode = i.shortcut.mode) {
            is ShortcutMode.Slot -> if (mode.n <= i.slotCount && m == mode.n - 1) m else -1
            ShortcutMode.Full, ShortcutMode.Background -> m
        }
    }

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
                    m == n -> when (i.presence) {                                                       // 4 — FIX286: theo sự thật
                        SlotPresence.GONE -> ShortcutAction.Reopen(n)                                   // 4a
                        SlotPresence.ELSEWHERE -> ShortcutAction.Noop(highlight = n, reason = Reason.RUNNING) // 4b
                        SlotPresence.IN_SLOT, SlotPresence.UNKNOWN -> ShortcutAction.Noop(highlight = n) // 4c
                    }
                    m >= 0 -> ShortcutAction.Highlight(m, Reason.IN_OTHER_SLOT)                         // 5
                    // 3 · 6 · 7: ô widget / trống / có app A ⇒ đặt TẠM (FIX286: ô widget THÔI mở toàn màn); chỉ app A bị đẩy
                    else -> ShortcutAction.PlaceTemp(n, evict = (i.slots.getOrNull(n) as? SlotContent.App)?.pkg)
                }
            }
            ShortcutMode.Full -> when {
                m < 0 -> ShortcutAction.OpenFull(null)                                                  // 8
                // 9′ — FIX286: B được xếp ở ô m mà KHÔNG còn task nào ⇒ K7 không có gì để tách ⇒ mở bằng Intent như dòng 8
                i.usable && i.presence == SlotPresence.GONE -> ShortcutAction.OpenFull(null)
                i.usable -> ShortcutAction.DetachToFull(m, byIntent = i.fullByIntent)                   // 9
                i.fullByIntent -> ShortcutAction.DetachToFull(m, byIntent = true)                       // 10a
                else -> ShortcutAction.Prompt                                                           // 10b
            }
            ShortcutMode.Background -> when {
                !i.usable -> ShortcutAction.Prompt                                                      // 11
                m >= 0 && i.presence == SlotPresence.GONE -> ShortcutAction.Reopen(m)                   // 12a — FIX286
                m >= 0 || i.running -> ShortcutAction.Noop(highlight = m, reason = Reason.RUNNING)      // 12b
                i.exclusion != null -> ShortcutAction.Refuse(reasonOf(i.exclusion))                     // 13
                !i.hasLiveStage -> ShortcutAction.StartBehindHidden                                     // 14 — L8: màn ảo ẩn
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

    /**
     * Đặt TẠM B vào ô [slot]; [evict] = app A đang ở ô đó (⇒ ra sau màn nhà, R0.1), `null` = ô trống HOẶC ô widget (FIX286
     * R-SC1: widget chỉ bị che ở lớp tạm, lớp lưu giữ nguyên — không có gì để đẩy hay thu hồi).
     */
    data class PlaceTemp(val slot: Int, val evict: String?) : ShortcutAction

    /** B đang ở ô [slot] ⇒ kéo ra toàn màn (cơ chế theo đo T-M2: [byIntent] hay lệnh K7 qua kênh). */
    data class DetachToFull(val slot: Int, val byIntent: Boolean) : ShortcutAction

    /** Từ chối kèm lý do, không lệnh nào. */
    data class Refuse(val reason: ShortcutPlan.Reason) : ShortcutAction

    /**
     * FIX286 · R-SC2 — B được xếp ở ô [slot] mà phép đo lúc chạm thấy KHÔNG còn task ([SlotPresence.GONE]) ⇒ mở lại B vào
     * đúng ô đó bằng `VdAppHost.reopen` (golden; app Kachi đẩy ra sau màn nhà ⇒ K8; thẻ "đã đóng" cũ gỡ ở L6). Cần kênh.
     */
    data class Reopen(val slot: Int) : ShortcutAction

    /** Chạy B phía sau màn nhà (R0.3). */
    object StartBehind : ShortcutAction { override fun toString() = "StartBehind" }

    /**
     * L8 (dòng 14, trước là `Refuse(NO_STAGE)`): không có ô app sống ⇒ chạy B phía sau màn nhà qua màn ảo ẨN của Kachi —
     * CÙNG chuỗi của chuyến lên xe (L4 · D2(a), `BehindHomeSequence.startBehindHidden`), đường mới đứng CUỐI (CLAUDE.md §6):
     * chỉ khi đường ô sống không có. Không tạo được màn ảo ẩn ⇒ chuỗi trả `NO_STAGE` ⇒ lớp keo nói `kachi_sc_no_stage`.
     */
    object StartBehindHidden : ShortcutAction { override fun toString() = "StartBehindHidden" }
}
