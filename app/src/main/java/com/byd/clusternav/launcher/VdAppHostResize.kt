package com.byd.clusternav.launcher

import android.hardware.display.VirtualDisplay
import android.util.Log

/**
 * ═══ V3 · R15 — ĐỔI CỠ màn ảo của ô, **một đường duy nhất** — phần THI HÀNH + ĐÒN BẨY MẬT ĐỘ ═══════════════════════════════
 *
 * Nửa tách ra của [VdAppHost] (2.91 · F2b — `VdAppHost.kt` chạm 499/500 dòng): thân hai hàm chuyển NGUYÊN VĂN, cùng thứ tự nhật
 * ký, nhật ký giữ tag `VdAppHost` (đọc log xe theo `[slot-resize]` / `[slot-density]`). Trạng thái — cỡ / mật độ màn ảo đang mang,
 * có giữ mật độ hay không — vẫn do [VdAppHost] giữ và quyết; ở đây không có trạng thái nào. [apply] chỉ `VdAppHost.resize` gọi;
 * [slotDensity] thì `VdAppHost` gọi ở hai chỗ: tạo màn ảo (đường golden) và đổi cỡ khi KHÔNG giữ mật độ.
 *
 * ## Bệnh nó chữa — [ĐO xe 2026-09-16] `carlog-0916/slot-insets-bug.png`
 * Lúc khởi động, khi thanh trên/dưới của ROM còn hiện, ô được đo theo khung **đã bị co**: màn ảo dựng đúng
 * cỡ hụt ấy, app trong ô (YouTube) bố trí theo nó, và khi thanh ẩn đi thì ô rộng ra nhưng ảnh trong ô vẫn
 * giữ khung cũ ⇒ **dải xám ở trên**. Bấm Home lần nữa (lúc thanh đã ẩn) thì đúng.
 *
 * ## Vì sao là một hàm có TÊN, không phải ba dòng trong `surfaceChanged`
 * Từ 1.66 có **hai** thời điểm biết được cỡ ô đã đổi: `surfaceChanged` (đường cũ) và lượt bố trí lại do
 * inset đổi ([WorkspaceView] — đường mới). Hai chỗ tự viết ba dòng giống nhau là bản sao thứ hai của một
 * phép tính có **đòn bẩy mật độ** bên trong; một bên quên [slotDensity] là ô đó hiện chữ to gấp rưỡi ô bên
 * cạnh mà không ai hiểu vì sao.
 *
 * **Trùng cỡ ⇒ không làm gì** (`VdAppHost.resize`): đây là điều khiến đường mới không phải "cơ chế thứ hai" mà chỉ là một cái
 * kích thêm — gọi thừa bao nhiêu lần cũng vô hại, và `VirtualDisplay.resize` thì KHÔNG rẻ (nó đẩy một lượt
 * đổi cấu hình vào app đang chạy trong ô).
 */
internal object VdAppHostResize {

    private const val TAG = "VdAppHost"

    /**
     * Thi hành MỘT lượt đổi cỡ màn ảo [v] của ô [slot] từ [fromW]×[fromH] về [w]×[h]: in dòng `[slot-resize]` TRƯỚC, rồi mới lấy mật
     * độ [dpi] và gọi `VirtualDisplay.resize` trong cùng một rào lỗi — đúng thứ tự trước khi tách. Trả mật độ đã đặt; `null` = hệ từ
     * chối (đã log, không ném).
     */
    fun apply(slot: Int, v: VirtualDisplay, fromW: Int, fromH: Int, w: Int, h: Int, dpi: () -> Int): Int? {
        Log.i(TAG, "[slot-resize] ô $slot ${fromW}x$fromH → ${w}x$h")
        return runCatching { dpi().also { v.resize(w, h, it) } }
            .onFailure { Log.w(TAG, "[slot-resize] ô $slot hỏng", it) }
            .getOrNull()
    }

    /**
     * R5 · ĐÒN BẨY MẬT ĐỘ (generic, spec §4.4). Mật độ thật đặt cho màn ảo của ô = [SlotDensity.forTablet] theo
     * **cạnh ngắn** của ô, để `smallestScreenWidthDp ≥ 600` ⇒ app có layout tablet không đòi portrait ⇒ không rơi
     * size-compat (bug "YouTube co dải dọc giữa ô khi play", owner 2026-09-15). Truyền NGAY lúc
     * `createVirtualDisplay`/`resize` (không qua `wm density` shell ⇒ không ghi `display_settings.xml`, không cần
     * đường trả lại — CLAUDE §5). Không hỏi tên gói, không hỏi to/bé; ô đã ≥600dp thì giữ [densityDpi] nguyên.
     * Kỳ vọng: app đầy khung; video 16:9 chỉ full-pixel ở fullscreen player. Log một dòng để đo trên xe.
     */
    fun slotDensity(slot: Int, w: Int, h: Int, densityDpi: Int): Int {
        val short = minOf(w, h)
        val dpi = SlotDensity.forTablet(short, densityDpi)
        Log.i(
            TAG,
            "[slot-density] slot=$slot short=$short dpi=$densityDpi→$dpi" +
                " (%.1fdp→%.1fdp)".format(SlotDensity.dpOf(short, densityDpi), SlotDensity.dpOf(short, dpi)),
        )
        return dpi
    }
}
