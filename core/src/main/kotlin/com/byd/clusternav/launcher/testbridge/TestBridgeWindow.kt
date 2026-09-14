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

    /** Cửa sổ mở tối đa cho một lượt bật: 60 phút. */
    const val WINDOW_MS: Long = 60L * 60L * 1000L

    /**
     * Sai số cho phép của *mốc nổ máy* (5 giây).
     *
     * Không so bằng `==`: `giờ treo tường` và `thời gian máy đã chạy` đọc ở hai lời gọi khác nhau nên hiệu của
     * chúng luôn lệch vài ms; và một lượt đồng bộ NTP nhỏ cũng đẩy nó đi vài giây. Rộng hơn nữa thì mất tính
     * chất (2) — nhưng 5 giây thì không đủ cho một chu kỳ tắt–bật máy nào.
     */
    const val BOOT_TOLERANCE_MS: Long = 5_000L

    private const val SEP = ':'

    /** Giá trị ghi xuống đĩa cho một lượt bật bắt đầu **ngay bây giờ**. */
    fun encode(nowWallMs: Long, upMs: Long): String = "${nowWallMs - upMs}$SEP${nowWallMs + WINDOW_MS}"

    /**
     * Còn lại bao nhiêu mili-giây, `0` = **đang tắt** (hết hạn · khác lần nổ máy · giá trị hỏng · đồng hồ nhảy).
     *
     * Trả về số mili-giây chứ không phải `Boolean` vì bề mặt nào cũng cần con số: màn Cài đặt nói *"còn N phút"*,
     * và JSON trả về cho script cũng ghi nó — *"đang bật"* mà không nói còn bao lâu thì lượt test dài sẽ đứt
     * giữa chừng mà không ai hiểu vì sao.
     */
    fun remainingMs(stored: String?, nowWallMs: Long, upMs: Long): Long {
        val raw = stored?.trim().orEmpty()
        val cut = raw.indexOf(SEP)
        if (cut <= 0) return 0
        val boot = raw.substring(0, cut).toLongOrNull() ?: return 0
        val until = raw.substring(cut + 1).toLongOrNull() ?: return 0
        // (2) khác lần nổ máy ⇒ coi như chưa bật, bất kể hạn còn bao lâu.
        if (abs(boot - (nowWallMs - upMs)) > BOOT_TOLERANCE_MS) return 0
        val left = until - nowWallMs
        // (3) hạn xa hơn cả cửa sổ ⇒ đồng hồ đã bị vặn lùi (hoặc giá trị bị sửa tay) ⇒ đóng, không kéo dài.
        if (left <= 0 || left > WINDOW_MS) return 0
        return left
    }

    /** Tiện đọc cho chỗ gọi chỉ cần biết mở hay đóng. */
    fun isOn(stored: String?, nowWallMs: Long, upMs: Long): Boolean = remainingMs(stored, nowWallMs, upMs) > 0

    /** Số PHÚT còn lại, làm tròn LÊN — *"còn 0 phút"* trong khi cửa vẫn mở là câu nói sai. */
    fun remainingMinutes(stored: String?, nowWallMs: Long, upMs: Long): Int {
        val left = remainingMs(stored, nowWallMs, upMs)
        return if (left <= 0) 0 else ((left + 59_999L) / 60_000L).toInt()
    }
}
