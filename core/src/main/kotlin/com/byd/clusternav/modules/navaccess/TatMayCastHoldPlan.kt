package com.byd.clusternav.modules.navaccess

/**
 * ═══ 2.96 R11 · TAT-MAY-CAST-HOLD — tiến trình bật lúc màn TẮT: hoãn lượt TỰ MỞ CHIẾU tới khi lượt chữa phím lớp 1 kết luận ═══
 *
 * ## Lỗi hiện trường [ĐO log xe 07/10, hai lần nổ máy — `docs/diagnostics/startup-timeline-2026-10-07.md`]
 * Tắt máy ⇒ BYD giết Kachi ⇒ Android dựng lại HOME lúc màn tắt. Trong tiến trình đó HAI việc cùng bật một lúc:
 *  • lượt tự chiếu (`BubbleAutostart`) chạy TRỌN chuỗi mở chiếu + đặt GMaps/VietMap lên cụm (~22 s);
 *  • lượt chữa phím lớp 1 (`A11yLifecycleHeal`, pha tắt-máy) thấy KẸT BỀN ở t≈5,8 s (log 06/10 trước khi có lượt chờ) nhưng
 *    phải CHỜ chiếu yên (`HealCastDeferral`, `keys-defer … OP_IN_FLIGHT waited=17969 / 18224`) rồi mới tự force-stop ở t≈24 s.
 * ⇒ Tiến trình con (dựng lại sau force-stop) thấy app của tiến trình trước còn trên cụm ⇒ GỠ hết (`am start --display 0` ×3,
 * opcode 18 · 0, dọn cụm ≈ 6–8 s) rồi mở LẠI từ đầu — 20:48 lượt mở này chạm trần 25 s (`TIMEOUT: openProjection`) và cụm chỉ
 * xong 38 s sau khi bật màn. Những lần màn bật GIỮA lượt chờ (5 ca 07/10, `-> PHASE_GONE`) thì lớp 1 bị cắt và lớp 2 (mở xe)
 * tự force-stop launcher 6–11 s SAU khi màn đã sáng — người lái thấy launcher khởi động lại trước mặt.
 *
 * ## Luật (thuần — bên gọi tiêm đồng hồ + sự thật)
 * Chỉ tiến trình bật lúc màn KHÔNG tương tác mới "được giữ" ([armedAt] ≥ 0). Giữ khi một trong hai:
 *  • lượt lớp 1 của chính tiến trình này CHƯA kết luận ([verdictPending]);
 *  • lượt đó ĐÃ bắn force-stop (mốc leo bền `a11y_forcestop_elapsed` nằm trong `[armedAt, now]`) ⇒ tiến trình sắp chết, mở
 *    chiếu lúc này chỉ để bị giết giữa chừng (đúng lỗi `TOO_SOON` 06/10 mà `CastRestartHazard` sinh ra để tránh).
 * Mốc leo > now = mốc của lần khởi động máy TRƯỚC (đồng hồ `elapsedRealtime` về 0 khi reboot) ⇒ không tính.
 * Trần cứng [CAP_MS] kể từ [armedAt] (fail-open): quá trần thì mở chiếu như cũ, và `HealCastDeferral` vẫn che lượt chữa —
 * tức trường hợp xấu nhất = hành vi trước bản vá, trễ thêm ≤ [CAP_MS] lúc màn đang TẮT.
 *
 * Vì sao trần 12 s: lớp 1 = đo (mở phiên dadb + `dumpsys accessibility`) + chờ [AccessibilityHealGates.STUCK_CONFIRM_GAP_MS] +
 * đo lại + nấc leo; [ĐO log 06/10] `ESCALATE` t=5795–5815 ms, `RESTARTING` t=6437–6475 ms, lệnh tách rời giết sau ≤ 4 s ⇒ ≈ 10,5 s.
 *
 * CLAUDE.md §6: không đổi một lệnh nào của chuỗi mở chiếu hay của lượt chữa — chỉ dời THỜI ĐIỂM lượt tự chiếu bắt đầu, và chỉ
 * trong pha màn tắt (không ai đang nhìn cụm). Cờ RAM chỉ HOÃN một việc của chính Kachi trong tiến trình (cùng lẽ `moXeBusy`).
 */
object TatMayCastHoldPlan {

    /** Trần giữ, tính từ lúc tiến trình bật — xem KDoc lớp. */
    const val CAP_MS: Long = 12_000L

    /** Nhịp hỏi lại (cùng nhịp `HealCastDeferral.POLL_MS`). */
    const val POLL_MS: Long = 250L

    /** Vì sao giữ / thả — chỉ để ghi nhật ký đo trên xe. */
    enum class Verdict { GO_NOT_ARMED, GO_RELEASED, GO_CAP, HOLD_PENDING, HOLD_DYING }

    /**
     * @param armedAt `elapsedRealtime` lúc tiến trình bật nếu màn KHÔNG tương tác, không thì `-1`.
     * @param verdictPending lượt lớp 1 của tiến trình này chưa kết luận.
     * @param escalatedAt mốc leo bền (`Prefs.a11yEscalatedAt`), `-1` = chưa từng.
     */
    fun verdict(armedAt: Long, verdictPending: Boolean, escalatedAt: Long, now: Long): Verdict = when {
        armedAt < 0L -> Verdict.GO_NOT_ARMED
        now - armedAt >= CAP_MS -> Verdict.GO_CAP
        escalatedAt in armedAt..now -> Verdict.HOLD_DYING
        verdictPending -> Verdict.HOLD_PENDING
        else -> Verdict.GO_RELEASED
    }

    /** `0` = mở chiếu ngay; `> 0` = hỏi lại sau chừng này ms (không bao giờ vượt quá trần còn lại). */
    fun holdMs(armedAt: Long, verdictPending: Boolean, escalatedAt: Long, now: Long): Long =
        when (verdict(armedAt, verdictPending, escalatedAt, now)) {
            Verdict.HOLD_PENDING, Verdict.HOLD_DYING -> minOf(POLL_MS, armedAt + CAP_MS - now).coerceAtLeast(1L)
            else -> 0L
        }
}
