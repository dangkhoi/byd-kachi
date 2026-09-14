package com.byd.clusternav.launcher

/**
 * ═══ LUẬT "APP TRONG Ô CÒN SỐNG KHÔNG" (thuần JVM :core) ══════════════════════════════════════════════════════
 *
 * [ĐO] 2026-09-14 (`docs/diagnostics/waze-into-slot-research-2026-09-14.md` §5): app đang chiếu trong ô bị
 * `am force-stop` thì `SurfaceView` **giữ nguyên khung hình cuối** — ô trông vẫn sống, chạm vào không có gì xảy
 * ra. Kênh im lặng phải nói: muốn nói được thì trước hết phải **biết** nó chết, mà cái biết đó đến từ một phép
 * đo lặp (`am stack list` → còn task của gói trên màn ảo của ô không).
 *
 * Lớp này giữ phần QUYẾT ĐỊNH của phép đo đó, tách khỏi shell/Handler để test off-device. Hai luật:
 *
 *  1. **Chưa từng thấy sống thì không được kết luận chết.** `am start` tới lúc task hiện ra mất vài giây; nếu
 *     nhịp đo đầu tiên rơi vào khoảng đó mà đã kết luận thì ô vừa mở đã báo "app đã đóng" — và cái nhãn ấy sẽ
 *     hiện đúng ở lần dùng đầu tiên, tức là sai ở chỗ tệ nhất.
 *  2. **Phải trượt [missesToDie] nhịp liên tiếp.** Một nhịp hụt đơn lẻ (shell timeout, app đang đổi task, dump
 *     bị cắt) không phải cái chết. Mặc định 2 nhịp × 5 s = 10 s im lặng mới kết luận.
 *
 * Sau khi đã báo chết, bộ đếm **không tự bật lại**: ô chuyển sang thẻ "chạm để mở lại". Một chu kỳ đo mới chỉ
 * bắt đầu khi ô đăng ký lại với `SlotLiveProbe.watch` (người dùng bấm mở lại) — và lần ấy là **một bản mới** của
 * lớp này, không phải bản cũ được bật lại.
 *
 * ⚠ [SOÁT Pass H2 · §8] Bản đầu có thêm `reset()`/`hasSeenAlive()` cho đúng câu KDoc này, nhưng **không chỗ nào
 * trong mã sản phẩm gọi chúng** (đường mở lại dựng một `Sub` mới ⇒ một `SlotLiveness` mới). Hai hàm chỉ-test-gọi
 * cộng một câu KDoc mô tả cơ chế không tồn tại là đúng cái bẫy CLAUDE.md §8 nói tới, nên chúng đã bị gỡ.
 */
class SlotLiveness(private val missesToDie: Int = DEFAULT_MISSES) {

    private var seenAlive = false
    private var misses = 0
    private var reported = false

    /**
     * Nạp một nhịp đo. Trả `true` **đúng một lần**, tại nhịp mà ô chuyển từ sống sang chết.
     */
    fun observe(alive: Boolean): Boolean {
        if (reported) return false
        if (alive) { seenAlive = true; misses = 0; return false }
        if (!seenAlive) return false
        misses++
        if (misses < missesToDie) return false
        reported = true
        return true
    }

    companion object {
        /** Số nhịp hụt liên tiếp để kết luận chết (nhịp đo = [PROBE_PERIOD_MS]). */
        const val DEFAULT_MISSES = 2

        /** Chu kỳ đo: 5 giây — trần "không poll dày" của H2; 1 lệnh `am stack list` cho TẤT CẢ ô mỗi nhịp. */
        const val PROBE_PERIOD_MS = 5_000L
    }
}
