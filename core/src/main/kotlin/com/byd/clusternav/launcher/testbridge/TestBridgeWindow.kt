package com.byd.clusternav.launcher.testbridge

import kotlin.math.abs

/**
 * ═══ T-BRIDGE · CỬA SỔ THỜI GIAN CỦA CHẾ ĐỘ KIỂM THỬ ═════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` R2. Thuần Kotlin ⇒ kiểm off-device, và đây là chỗ **duy nhất** trả
 * lời câu *"cầu kiểm thử có đang mở không"*.
 *
 * ## Ba tính chất phải đúng CÙNG LÚC, và vì sao không tính chất nào bỏ được
 *  1. **Tự tắt sau [WINDOW_MS]** — một công tắc mở cho một buổi test mà sống mãi thì nó không còn là công tắc,
 *     nó là một cổng vào thường trực trên chiếc xe của người ta.
 *  2. **Chết khi TẮT MÁY** — người bật nó đang ngồi trong xe với cáp adb; tắt máy là buổi test kết thúc.
 *     Đây là ràng buộc §5 CLAUDE.md nói tới ở chiều ngược lại: state ghi ra ngoài **sống dai hơn tiến trình**,
 *     nên muốn nó chết theo máy thì phải **tự làm cho nó chết**, không thể trông vào việc process bị giết.
 *     ⚠ 2.93 · TEST-MODE-ACC-OFF (bảo mật) — [ĐO xe 29/09, spec 2.83 §2.10] tắt máy BYD KHÔNG khởi động lại máy ⇒
 *     `bootId` không đổi ⇒ trước bản này cửa sổ SỐNG QUA hai lần tắt máy (tới 60 phút, kể cả lúc xe đỗ không người),
 *     trái câu UI. Nay [remainingMs] nhận thêm mốc *claim tắt-máy* (`a11y_tat_may_elapsed` — lớp 1 của 2.83 ghi
 *     `commit()` mỗi lần tiến trình launcher dựng lại lúc màn TẮT, tức sau mỗi lượt BYD giết lúc tắt máy; cùng nguồn
 *     `TripGate` dùng để đếm lần nổ máy): claim mới hơn lúc MỞ cửa sổ ⇒ ĐÓNG. Hướng an toàn — dựng lại lúc màn tắt mà
 *     không phải tắt máy (D-10) cũng đóng; mở lại = bật tay trong Cài đặt.
 *  3. **Không tin một mình đồng hồ treo tường** — `System.currentTimeMillis` nhảy được (NTP, người dùng đổi giờ,
 *     đầu xe mất pin RTC). Một hạn dùng chỉ dựa vào nó thì vặn đồng hồ lùi lại là cửa mở thêm vài tiếng.
 *
 * ## Cách mã hoá: `"<mốc nổ máy>:<hạn dùng>"`, MỘT khoá
 * [ĐO] hai khoá (`…_enabled` + `…_until`) đẻ ra một trạng thái vô nghĩa mà máy dựng được: bật mà không có hạn,
 * hoặc có hạn mà không bật. Một chuỗi thì hai nửa **không thể** lệch nhau.
 *
 * *Mốc nổ máy* = `giờ treo tường − thời gian máy đã chạy`. Trong một lần nổ máy nó gần như hằng số (lệch vài ms
 * do làm tròn); sau khi khởi động lại thì `thời gian máy đã chạy` về 0 nên mốc nhảy hẳn sang giá trị khác. So
 * mốc là cách rẻ nhất để biết *"có phải vẫn cùng một lần nổ máy không"* mà không cần lưu thêm gì.
 */
object TestBridgeWindow {
    const val WINDOW_MS: Long = 60L * 60L * 1000L
    private const val SEP = ':'

    /**
     * Giá trị lưu = `<bootId>:<upUntilMs>`.
     *
     * ## Vì sao KHÔNG dùng giờ tường (bản đầu 2026-09-14 dùng `wall − uptime` làm mốc nổ máy, dung sai 5 s)
     * [ĐO] xe DiLink3 tối 14/09: owner bật công tắc, cầu vẫn trả `test_mode_off` — đầu xe chỉnh giờ tường (GPS/mạng)
     * hơn 5 s sau khi bật ⇒ "mốc nổ máy" tính lại lệch ⇒ bị coi là khác lần nổ máy ⇒ tự tắt. Giờ tường trên xe không
     * phải đại lượng ổn định. Nay: danh tính lần nổ máy = `bootId` (`/proc/sys/kernel/random/boot_id`, đổi mỗi lần
     * boot), hạn = `elapsedRealtime` lúc bật + 60 phút — cả hai đều không phụ thuộc giờ tường.
     */
    fun encode(bootId: String, upMs: Long): String = "${bootId.trim()}$SEP${upMs + WINDOW_MS}"

    /** Mốc claim tắt-máy "chưa từng" (cùng quy ước `Prefs.a11yTatMayAt`: `< 0`). */
    const val NEVER: Long = -1L

    /**
     * Thời gian còn lại của cửa sổ (ms), 0 = đóng.
     *
     * @param tatMayAt mốc `elapsedRealtime` của claim tắt-máy gần nhất (`Prefs.a11yTatMayAt`, [NEVER] = chưa từng). Claim
     *   nằm SAU lúc mở cửa sổ và không ở "tương lai" (mốc lớn hơn [upMs] = đời máy trước, `elapsedRealtime` về 0 khi khởi
     *   động lại — cùng luật `AccessibilityHealGates.escalatedThisBoot`) ⇒ đã tắt máy kể từ lúc bật ⇒ ĐÓNG.
     */
    fun remainingMs(stored: String?, bootId: String, upMs: Long, tatMayAt: Long = NEVER): Long {
        val raw = stored?.trim().orEmpty()
        val cut = raw.lastIndexOf(SEP)
        if (cut <= 0) return 0
        val boot = raw.substring(0, cut)
        val until = raw.substring(cut + 1).toLongOrNull() ?: return 0
        if (boot != bootId.trim() || boot.isEmpty()) return 0          // khác lần nổ máy ⇒ chưa bật
        val left = until - upMs
        if (left <= 0 || left > WINDOW_MS) return 0                     // hết hạn, hoặc giá trị bị sửa tay
        if (closedByIgnitionOff(until - WINDOW_MS, upMs, tatMayAt)) return 0
        return left
    }

    /** TEST-MODE-ACC-OFF — có lần tắt máy (claim) nằm trong `(openedAt, upMs]` không. */
    fun closedByIgnitionOff(openedAt: Long, upMs: Long, tatMayAt: Long): Boolean = tatMayAt > openedAt && tatMayAt <= upMs

    fun isOn(stored: String?, bootId: String, upMs: Long, tatMayAt: Long = NEVER): Boolean =
        remainingMs(stored, bootId, upMs, tatMayAt) > 0

    fun remainingMinutes(stored: String?, bootId: String, upMs: Long, tatMayAt: Long = NEVER): Int {
        val left = remainingMs(stored, bootId, upMs, tatMayAt)
        return if (left <= 0) 0 else ((left + 59_999L) / 60_000L).toInt()
    }
}
