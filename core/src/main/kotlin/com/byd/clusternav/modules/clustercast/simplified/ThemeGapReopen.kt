package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ Review 2.89 Pass 3 · cluster-r2-6 — MỘT lượt mở lại bị cổng theme dừng vì khoảng 15 s ⇒ thử lại ĐÚNG MỘT lần (thuần) ═══
 *
 * "Áp ngay" của lựa chọn Bo tròn / Chữ nhật = `restoreCluster` (dừng → đóng chiếu → mở lại sau 2 s). Kiểu mới chỉ áp được khi màn
 * ảo cụm đã mất sau lượt đóng — đúng ca cổng có thể nói `TOO_SOON` (lần đổi theme trước < 15 s, [ThemeLedger.MIN_GAP_MS]); sổ
 * không chứng minh được kiểu mới ⇒ kế hoạch DỪNG ⇒ `Error` → `Off`. Vòng chờ hết khoảng duy nhất (`BubbleAutostart`, whole-r1-5)
 * chỉ chạy ở `FloatingBubbleService.onCreate` ⇒ không ai mở lại: Cast vẫn bật mà projection đóng tới khi người lái gạt công tắc
 * [SUY đọc mã — cần màn ảo cụm mất sau 18 → 0, CHƯA BIẾT].
 *
 * Lớp này xem các trạng thái của coordinator SAU MỘT lượt `openProjection` và quyết: lượt đó kết thúc ở `Error` mà
 * `themeGapRetryMs()` còn ⇒ hẹn ĐÚNG MỘT lượt mở nữa sau khoảng + [MARGIN_MS] (cùng luật của `BubbleAutostart`). Lượt thử lại
 * KHÔNG gắn bộ xem mới ⇒ không bao giờ vòng.
 *
 * Trạng thái phát NGAY lúc gắn bộ nghe (`addStateListener`) là trạng thái TRƯỚC lượt mở này: `Off`/`Error`/`Closing`/`Stopping`
 * ⇒ chờ `Opening`; đã `Idle`/đang chiếu ⇒ lượt mở là no-op (R10) ⇒ xong, không thử lại.
 */
class ThemeGapReopen(
    /** `SimpleCastCoordinator.themeGapRetryMs` — `null` = lượt dừng không do khoảng 15 s. */
    private val gapMs: () -> Long?,
    /** Hẹn một lượt mở lại sau [delayMs] (bên gọi tự gác công tắc Cast lúc chạy — CLAUDE.md §5). */
    private val retry: (delayMs: Long) -> Unit,
) {
    private var sawOpening = false
    private var done = false

    /** Một trạng thái của coordinator. `true` = bộ xem đã xong việc (bên gọi gỡ bộ nghe). */
    @Synchronized
    fun onState(s: SimpleCastState): Boolean {
        if (done) return true
        if (s == SimpleCastState.Opening) {
            sawOpening = true
            return false
        }
        if (!sawOpening) {
            val before = s == SimpleCastState.Off || s is SimpleCastState.Error ||
                s == SimpleCastState.Closing || s == SimpleCastState.Stopping
            if (before) return false
        } else if (s is SimpleCastState.Error) {
            gapMs()?.takeIf { it > 0 }?.let { retry(it + MARGIN_MS) }
        }
        done = true
        return true
    }

    companion object {
        /** Lề sau khi khoảng 15 s hết (đồng hồ của sổ và của `Handler` lệch nhau vài ms) — dùng chung với `BubbleAutostart`. */
        const val MARGIN_MS: Long = 500L
    }
}
