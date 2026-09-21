package com.byd.clusternav.launcher

import android.content.Context
import com.byd.clusternav.launcher.testbridge.TestBridgeStore

/**
 * ═══ CỔNG DUY NHẤT CHO BỀ MẶT DEV/DEBUG (UX-OVERHAUL · WP7) ══════════════════════════════════════════════════
 *
 * Owner 2026-09-20: *"ẩn hết đồ dev/debug/log/lấy-info-xe khỏi UI, giữ chức năng chạy qua adb — sau checkbox
 * «Chế độ kiểm thử qua adb»"*. Đây là phép kiểm *"có được thấy đồ dev không"*, dùng ở **mọi** chỗ dựng bề mặt dev.
 *
 * ## Vì sao gác bằng CHÍNH cửa sổ test-mode, không bằng một khoá thứ hai
 * Cửa sổ ấy ([TestBridgeStore]) đã có đúng ba tính chất mà một cổng dev cần, và cả ba đều **đã có bài canh**:
 *  1. **mặc định ĐÓNG** ⇒ người lái bình thường không bao giờ thấy;
 *  2. **tự hết hạn 60 phút** ⇒ bật rồi quên thì nó tự dọn, không để một cửa mở vĩnh viễn trên xe;
 *  3. **chết theo lần nổ máy** ([TestBridgeWindow] so mốc boot) ⇒ không sống qua một chuyến khác.
 *
 * Sinh một khoá `dev_mode` riêng là mở **cửa thứ hai** cho cùng một quyền, với hai vòng đời khác nhau — và cái thứ
 * hai sẽ là cái không ai nhớ tắt. Đúng bẫy hai-bản-sao mà dự án đã trả giá bốn lần (`unitPrefs` 4 bản ·
 * `customLayout` 2 bản).
 *
 * ## Hệ quả cố ý: owner vẫn bấm được TAY trên xe
 * Lời giao nói *"giữ chức năng chạy qua adb"*. Cổng này giữ **cả hai**: lệnh adb (cầu test-bridge — nó cũng đứng
 * sau đúng cửa sổ này) **và** bề mặt bấm tay, vì lúc RE trên xe owner ngồi trong xe và một số việc (nhìn đèn có
 * sáng không, cốp có mở không) không có cách nào đọc qua adb. Ẩn ≠ xoá: xoá mất luôn đường bấm tay mà buổi RE cần.
 *
 * @see TestBridgeStore.remainingMinutes cửa sổ còn bao nhiêu phút (0 = đóng)
 */
object DevMode {

    /**
     * Có được dựng bề mặt dev hay không — `true` **chỉ khi** cửa sổ test-mode đang mở.
     *
     * Đọc hỏng ⇒ **ĐÓNG** (`false`). Mặc định fail-safe ngược với `bubblePresence()` của WP6, và có lý do: ở đó
     * mặc định-mở giữ lối vào chính của một tính năng người dùng, còn ở đây mặc định-mở lại **bày đồ dev cho người
     * lái** — hai câu hỏi khác nhau nên hai mặc định khác nhau.
     */
    fun unlocked(context: Context): Boolean =
        runCatching { TestBridgeStore.remainingMinutes(context) > 0 }.getOrDefault(false)
}
