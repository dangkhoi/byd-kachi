package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ 2.94 · CLUSTER-BACKDROP-DAY — màu nền của màn chiếu cụm (lớp `ClusterBlackActivity` dưới app chiếu) — thuần ══════════════
 *
 * Owner 07/10: *"làm mảng đen projection theo mode, tối thì giữ nguyên đen, sáng thì lấy màu cụm ban ngày"*.
 *
 * - **Nguồn sáng/tối** = chế độ giao diện HỆ THỐNG của xe (`uiMode` của `Resources.getSystem()`), KHÔNG phải lựa chọn sáng/tối
 *   riêng của Kachi: [ĐO xe 07/10] chuyển Tối trong Cài đặt xe ⇒ `ui_night_mode=2`, cả cụm lẫn màn chính tối (owner xác nhận);
 *   tín hiệu đèn `LIGHT_DAY_NIGHT_MODE_STATUS` (980418626) đứng 0 suốt lượt ấy và khi bật đèn cos ⇒ không dùng.
 * - **Màu ban ngày** = trung bình theo alpha của vành ảnh nền ngày quanh lỗ chiếu, đọc từ ảnh theme trong firmware 2602030
 *   (`display_always/day/navi_full_bg.png`): theme2 Chữ nhật ≈ (181,198,212), theme1 Bo tròn ≈ (192,207,219). Vành ấy trong
 *   suốt một phần (alpha ≈ 86/255) nên lớp nền của Kachi lộ ra quanh mép — đen ⇒ viền tối trên cụm sáng.
 * - Không biết kiểu ([frame] `null`) ⇒ màu Bo tròn (đường mặc định của mọi đời xe — `CastSessionStyle.of`).
 */
object ClusterBackdrop {
    const val NIGHT: Int = 0xFF000000.toInt()
    const val DAY_RECT: Int = 0xFFB5C6D4.toInt()
    const val DAY_CURVED: Int = 0xFFC0CFDB.toInt()

    fun color(systemNight: Boolean, frame: CastStyle?): Int = when {
        systemNight -> NIGHT
        frame == CastStyle.RECT -> DAY_RECT
        else -> DAY_CURVED
    }
}
