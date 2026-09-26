package com.byd.clusternav.launcher.testbridge

/**
 * ═══ T-BRIDGE · CỠ ẢNH CHỤP KHUNG CAMERA (`camera_frame --es name <W>x<H>`) ═══════════════════════════════════
 *
 * Spec `docs/specs/camera-turn-signal-hal-socket.html` (chẩn đoán R7). Thuần Kotlin ⇒ kiểm off-device, cùng lý do
 * với [TestBridgeCommands.parse]: đây là chỗ **sai được mà không ai thấy** — một chuỗi lạ (`"abc"`, `"0x0"`,
 * `"99999x99999"`) cho ra một cỡ vô nghĩa thì lượt chụp trên xe trả về một tệp rỗng hoặc một lần cấp phát 40 GB
 * ngay trên đầu máy, mà người gõ lệnh chỉ thấy `ok:false` không rõ vì sao.
 *
 * ## Ba luật, theo đúng thứ tự
 *  1. **Không đọc được ⇒ MẶC ĐỊNH** ([DEFAULT_W] × [DEFAULT_H] = 5120×960 = cỡ ảnh fisheye 4-in-1
 *     [ĐO xe 2026-09-25], xem KDoc `CameraSignalPolicy.CamView`). KHÔNG ném, KHÔNG trả 0: lượt đo quý hơn một câu
 *     bắt lỗi cú pháp, và người gõ sai vẫn cần thấy cái khung.
 *  2. **Trần MỖI CẠNH** [MAX_SIDE] (8192) — trần texture/bitmap thực tế của GPU đầu máy; xin hơn thì
 *     `Bitmap.createBitmap` ném `OutOfMemoryError`, thứ mà `runCatching` ở tầng thi hành bắt được nhưng
 *     `Readback` có thể đã kịp giữ một GPU surface.
 *  3. **Trần DIỆN TÍCH** [MAX_AREA] (20 M px ≈ 80 MB ARGB_8888) — co ĐỀU hai cạnh theo `sqrt` để **giữ tỉ lệ**
 *     ảnh. Cắt một cạnh thay vì co đều sẽ làm méo đúng thứ lệnh này sinh ra để đo (vòng fisheye tròn hay bẹt).
 *
 * Trần đặt ở tầng PHÂN TÍCH (không ở tầng thi hành) vì cùng một lẽ với danh sách trắng của `prefs_set`: một cỡ
 * không dùng được không bao giờ được dựng thành một lệnh "chạy được" rồi mới hỏng ở giữa.
 */
object TestBridgeFrameSize {

    /** Bề rộng mặc định = ảnh fisheye 4-in-1 của AVMCamera id 1 [ĐO xe 2026-09-25]. */
    const val DEFAULT_W = 5120

    /** Bề cao mặc định — cùng nguồn đo với [DEFAULT_W]. */
    const val DEFAULT_H = 960

    /** Trần một cạnh (px). */
    const val MAX_SIDE = 8192

    /** Trần diện tích (px) — 20 M ≈ 80 MB ở ARGB_8888. */
    const val MAX_AREA = 20_000_000L

    /** Dấu phân tách của `<W>x<H>` — một chữ `x`, viết thường (chuỗi vào đã hạ chữ). */
    private const val SEP = 'x'

    /** Một cỡ đã kiểm: cả hai cạnh ≥ 1, mỗi cạnh ≤ [MAX_SIDE], `w * h` ≤ [MAX_AREA]. */
    data class Size(val w: Int, val h: Int) {
        /** Số điểm ảnh — `Long` vì `8192 × 8192` đã vượt trần `Int` khi ai đó nhân thêm 4 byte/px. */
        val area: Long get() = w.toLong() * h.toLong()
    }

    /** Cỡ mặc định, dựng MỘT lần ở đây để chỗ gọi không phải nhớ lại hai hằng. */
    val DEFAULT = Size(DEFAULT_W, DEFAULT_H)

    /**
     * `"5120x960"` → [Size]; chuỗi rỗng/lạ → [DEFAULT]. Nhận cả chữ `X` HOA và khoảng trắng thừa.
     *
     * @param arg giá trị `--es name` nguyên văn (có thể rỗng).
     */
    fun parse(arg: String): Size {
        val parts = arg.trim().lowercase().split(SEP)
        if (parts.size != 2) return DEFAULT
        val w = parts[0].trim().toIntOrNull() ?: return DEFAULT
        val h = parts[1].trim().toIntOrNull() ?: return DEFAULT
        if (w < 1 || h < 1) return DEFAULT
        return clamp(w, h)
    }

    /**
     * Áp hai trần (cạnh rồi diện tích) cho một cỡ đã đọc được.
     *
     * Thứ tự có chủ ý: hạ cạnh TRƯỚC (rẻ, không đổi tỉ lệ nếu chỉ một cạnh vượt — nhưng vẫn đúng trần), rồi mới co
     * đều theo diện tích. Làm ngược lại thì lượt co đều có thể nhả ra một cạnh vẫn > [MAX_SIDE].
     */
    private fun clamp(w0: Int, h0: Int): Size {
        val w1 = w0.coerceAtMost(MAX_SIDE)
        val h1 = h0.coerceAtMost(MAX_SIDE)
        val area = w1.toLong() * h1.toLong()
        if (area <= MAX_AREA) return Size(w1, h1)
        // `sqrt` trên tỉ lệ diện tích ⇒ hai cạnh cùng nhân một hệ số ⇒ tỉ lệ ảnh giữ nguyên. `floor` (phép đổi
        // `Int` của Kotlin) đảm bảo diện tích SAU khi làm tròn vẫn ≤ trần, không cần kiểm lại.
        val scale = Math.sqrt(MAX_AREA.toDouble() / area.toDouble())
        val w = (w1 * scale).toInt().coerceAtLeast(1)
        val h = (h1 * scale).toInt().coerceAtLeast(1)
        return Size(w, h)
    }
}
