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
 *  2. **Chết theo lần nổ máy** — người bật nó đang ngồi trong xe với cáp adb; tắt máy là buổi test kết thúc.
 *     Đây là ràng buộc §5 CLAUDE.md nói tới ở chiều ngược lại: state ghi ra ngoài **sống dai hơn tiến trình**,
 *     nên muốn nó chết theo máy thì phải **tự làm cho nó chết**, không thể trông vào việc process bị giết.
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

    fun remainingMs(stored: String?, bootId: String, upMs: Long): Long {
        val raw = stored?.trim().orEmpty()
        val cut = raw.lastIndexOf(SEP)
        if (cut <= 0) return 0
        val boot = raw.substring(0, cut)
        val until = raw.substring(cut + 1).toLongOrNull() ?: return 0
        if (boot != bootId.trim() || boot.isEmpty()) return 0          // khác lần nổ máy ⇒ chưa bật
        val left = until - upMs
        if (left <= 0 || left > WINDOW_MS) return 0                     // hết hạn, hoặc giá trị bị sửa tay
        return left
    }

    fun isOn(stored: String?, bootId: String, upMs: Long): Boolean = remainingMs(stored, bootId, upMs) > 0

    fun remainingMinutes(stored: String?, bootId: String, upMs: Long): Int {
        val left = remainingMs(stored, bootId, upMs)
        return if (left <= 0) 0 else ((left + 59_999L) / 60_000L).toInt()
    }
}
