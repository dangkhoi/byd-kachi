package com.byd.clusternav.navigation

import com.byd.clusternav.carexec.LocalShellFailure

/**
 * ═══ FIX286 R-HUD — cổng + từ vựng kết quả của lượt TỰ GẮN LẠI nguồn thông báo (thuần, `:core`) ══════════════════════
 *
 * Gốc [ĐO nguồn AOSP] (spec `kachi-286-field-fixes.html` §3.9): sau lượt BYD giết Kachi lúc tắt máy, NMS chỉ hẹn gắn lại
 * bộ nghe 10 s một lần cho mỗi thẻ (`ManagedServices.java` r47 `:97,:1143-1158` · r34 `:100,:1521-1528`); thẻ kẹt trong
 * `mServicesRebinding` thì không bao giờ hẹn lại. Đường cũ của Kachi (`requestRebind` ở watchdog 60 s và ở
 * `onListenerDisconnected`) là no-op khi component không bị "snooze" (r47 NMS `:3127-3139` → `ManagedServices.java:707-711`;
 * r34 NMS `:4389-4401` → `ManagedServices.java:1038-1041`). Đường DUY NHẤT gắn lại được là cặp lệnh của công tắc
 * *Dẫn đường lên cụm đồng hồ*: `cmd notification disallow_listener` → 1,5 s → `allow_listener` ⇒ `setPackageOrComponentEnabled`
 * ⇒ `rebindServices` (r47 `ManagedServices.java:492` · r34 `:801`).
 *
 * Owner 03/10: *"Sửa cái HUD thì phải kiểm tra là có enable chưa trong setting mới đi bind nhé"* ⇒ [step] đặt cổng
 * [Facts.navEnabled] ĐẦU TIÊN: công tắc tắt thì không đọc, không ghi gì.
 */
object NlsHealPolicy {

    /** > hẹn 10 s của NMS (r47 `:97`) + biên: để NMS tự gắn trước, không giành việc với nó. */
    const val SETTLE_AFTER_START_MS = 15_000L

    /** Một lần mỗi tiến trình — chữa xong ([Ledger.healed]) là thôi; chưa xong thì thử lại tối đa tới trần này. */
    const val MAX_FIRES_PER_PROCESS = 3

    /** Giãn cách giữa hai lần bắn cặp lệnh (thử lại). */
    const val RETRY_GAP_MS = 120_000L

    /** Đọc thấy Live (mà RAM chưa nhận callback) / không đọc được ⇒ chờ chừng này mới đọc lại — 1 dump/5 phút là trần. */
    const val RECHECK_GAP_MS = 300_000L

    /** Khoảng giữa disallow và allow — ĐÚNG số của đường công tắc đã chạy ngoài hiện trường (`NavConnect` 2.85 `:480`). */
    const val TOGGLE_PAUSE_SEC = "1.5"
    const val TOGGLE_PAUSE_MS = 1_500L

    /** Chờ `onListenerConnected` sau `allow` trước khi đọc lại sự thật. */
    const val VERIFY_WAIT_MS = 6_000L

    /** Ai gọi lượt này — chỉ để ghi log / hiện ở Cài đặt. */
    enum class Trigger { READY, WATCHDOG, SWITCH, BUTTON, SETTINGS_READ }

    /**
     * Sự thật đầu vào, đọc NGAY trước khi quyết. [boundInProcess] = callback `onListenerConnected` đã tới và chưa có
     * `onListenerDisconnected` — chỉ dùng để TIẾT KIỆM một lượt đọc dump, KHÔNG BAO GIỜ để quyết bắn lệnh (việc đó chỉ
     * theo [NlsLiveDump.Verdict] — CLAUDE.md §5).
     */
    data class Facts(
        val navEnabled: Boolean,
        val granted: Boolean?,
        val interactive: Boolean?,
        val shellUp: Boolean,
        val boundInProcess: Boolean,
        val sinceProcessStartMs: Long,
    )

    /** Sổ của TIẾN TRÌNH này (chết theo tiến trình là đúng ý: "một lần mỗi tiến trình"). */
    data class Ledger(
        val fires: Int = 0,
        val lastFireAt: Long = NEVER,
        val lastCheckAt: Long = NEVER,
        val healed: Boolean = false,
    )

    enum class Step { OFF, NO_ACCESS, SCREEN_OFF, NO_SHELL, SETTLING, BOUND, HEALED, CAPPED, BACKOFF, CHECK }

    /** Cổng của lượt TỰ ĐỘNG (READY + watchdog). Thứ tự là một quyết định — đọc KDoc lớp. */
    fun step(f: Facts, l: Ledger, nowMs: Long): Step = when {
        !f.navEnabled -> Step.OFF
        f.granted != true -> Step.NO_ACCESS
        f.interactive != true -> Step.SCREEN_OFF
        !f.shellUp -> Step.NO_SHELL
        f.sinceProcessStartMs < SETTLE_AFTER_START_MS -> Step.SETTLING
        f.boundInProcess -> Step.BOUND
        l.healed -> Step.HEALED
        l.fires >= MAX_FIRES_PER_PROCESS -> Step.CAPPED
        l.lastFireAt != NEVER && nowMs - l.lastFireAt < RETRY_GAP_MS -> Step.BACKOFF
        l.lastCheckAt != NEVER && nowMs - l.lastCheckAt < RECHECK_GAP_MS -> Step.BACKOFF
        else -> Step.CHECK
    }

    /** Kết quả một lượt. [ok] = nguồn đang gắn (hoặc vừa gắn được). */
    enum class Outcome(val ok: Boolean) {
        /** Đọc dump: đã Live — không đụng gì. */
        LIVE_ALREADY(true),
        /** Đường bấm tay: callback đã tới trong lúc chờ — không cần lệnh shell. */
        BOUND_IN_PROCESS(true),
        /** Đã bắn cặp lệnh, đọc lại thấy Live. */
        HEALED(true),
        /** Đã bắn cặp lệnh, đọc lại VẪN chưa Live. */
        STILL_NOT_LIVE(false),
        /** Lượt CHỈ ĐỌC (dòng tình trạng ở Cài đặt): chưa Live — không ghi gì. */
        NOT_LIVE(false),
        /** Không đọc ra khối Live ⇒ không hành động (không đoán). */
        UNREADABLE(false),
        /** Cổng phút chót đóng (công tắc vừa tắt / màn vừa tắt) hoặc lệnh bị từ chối dựng ⇒ không hành động. */
        SKIPPED(false),
        /** Phiên shell bị chặn / chưa được duyệt — cần "Cho phép gỡ lỗi USB". */
        NO_SHELL(false),
        /** Phiên shell hỏng (cổng đóng / đứt / không rõ). */
        SHELL_FAILED(false),
        /** Một lượt gắn lại / cấp quyền khác đang chạy. */
        BUSY(false),
    }

    fun fromShellFailure(f: LocalShellFailure): Outcome = when (f) {
        LocalShellFailure.NOT_APPROVED, LocalShellFailure.AWAITING_APPROVAL, LocalShellFailure.AUTH_REJECTED -> Outcome.NO_SHELL
        LocalShellFailure.PORT_CLOSED, LocalShellFailure.IO_ERROR, LocalShellFailure.UNKNOWN -> Outcome.SHELL_FAILED
    }

    /** Ghi sổ sau một lượt TỰ ĐỘNG. [fired] = cặp lệnh đã rời tiến trình (kể cả khi phiên hỏng sau đó). */
    fun record(l: Ledger, o: Outcome, fired: Boolean, nowMs: Long): Ledger = l.copy(
        fires = if (fired) l.fires + 1 else l.fires,
        lastFireAt = if (fired) nowMs else l.lastFireAt,
        // Đã bắn ⇒ giãn cách theo [RETRY_GAP_MS] (mốc lastFireAt); chỉ lượt đọc-không-bắn mới mở giãn cách đọc dài.
        lastCheckAt = if (fired) l.lastCheckAt else nowMs,
        healed = l.healed || (fired && o == Outcome.HEALED),
    )

    /**
     * Lệnh cặp disallow → [TOGGLE_PAUSE_SEC] → allow cho ĐÚNG component của mình, chạy TÁCH RỜI (`nohup … &`, cùng
     * khuôn `AccessibilityRebind.forceStopRebindCommand`): lệnh đã lọt vào xe là `allow` chạy trọn kể cả khi Kachi bị
     * giết giữa 1,5 s (lúc tắt máy) — không bao giờ để quyền ở trạng thái đã gỡ.
     *
     * CLAUDE.md §4 — (1) display: không đụng; (2) app: CHỈ component của chính [ownPackage] — gói lệch / chuỗi có ký
     * tự shell ⇒ `""` (không dựng lệnh); (3) loại: một mục trong danh sách duyệt bộ nghe thông báo (và mục gói tương
     * ứng của condition provider — NMS r47 `:3926-3929`), không chạm bộ nghe nào khác; (4) hoàn tác: chính nửa sau
     * `allow_listener` (đứng trong cùng lệnh tách rời) + đường cấp lại của Preflight (`PermissionPreflight` dùng đúng
     * `allow_listener` khi thấy thiếu quyền).
     */
    fun toggleCommand(component: String, ownPackage: String): String {
        if (!SAFE_COMPONENT.matches(component)) return ""
        if (ownPackage.isBlank() || component.substringBefore('/') != ownPackage) return ""
        val inner = "cmd notification disallow_listener $component ; sleep $TOGGLE_PAUSE_SEC ; " +
            "cmd notification allow_listener $component"
        return "nohup sh -c '$inner' >/dev/null 2>&1 </dev/null &"
    }

    const val NEVER = Long.MIN_VALUE

    /** Không `$`, không khoảng trắng, không nháy — chuỗi đi vào `sh -c '…'` lồng hai lớp. */
    private val SAFE_COMPONENT = Regex("""[A-Za-z0-9_.]+/[A-Za-z0-9_.]+""")
}
