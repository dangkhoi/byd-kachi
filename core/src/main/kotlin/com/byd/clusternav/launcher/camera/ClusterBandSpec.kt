package com.byd.clusternav.launcher.camera

/**
 * Số đo dải giữa của MỘT đời cụm, theo toạ độ display chiếu cỡ [refW]×[refH]. Trường của hồ sơ xe (`ClusterProfile`).
 *
 * @property left mép trái vẽ được (px, bao gồm) · @property right mép phải (px, không bao gồm) — tương tự [top]/[bottom].
 * @property radiusPx bán kính bo góc cửa sổ ở cỡ tham chiếu [SUY — chọn, không đo được cung tròn nào trên kính].
 */
data class ClusterBandSpec(
    val refW: Int,
    val refH: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val radiusPx: Int,
    /**
     * Mép NGOÀI cong bên trái: `n ≥ 2` mẫu `x` chia đều từ [top] tới [bottom] (mẫu `i` ở `top + i × (bottom−top)/(n−1)`),
     * mỗi mẫu trong `0..left`. Rỗng = **chưa đo đời cụm này** ⇒ tường thẳng [left] (hành vi 2.76, không thoái hoá).
     */
    val leftEdge: List<Int> = emptyList(),
    /**
     * Mép NGOÀI cong bên PHẢI: cùng khuôn [leftEdge] (`n ≥ 2` mẫu chia đều từ [top] tới [bottom]) nhưng mỗi mẫu nằm
     * trong `right..refW` — bên phải "ra ngoài" là `x` LỚN hơn. Rỗng = **chưa đo** ⇒ tường thẳng [right] (2.77).
     */
    val rightEdge: List<Int> = emptyList(),
) {
    init {
        require(refW > 0 && refH > 0) { "cỡ tham chiếu phải dương" }
        require(left in 0 until right && right <= refW) { "dải ngang phải nằm trong 0..$refW: $left..$right" }
        require(top in 0 until bottom && bottom <= refH) { "dải dọc phải nằm trong 0..$refH: $top..$bottom" }
        require(radiusPx >= 0)
        require(leftEdge.isEmpty() || leftEdge.size >= 2) { "bảng mép cong phải có ≥ 2 mẫu (hoặc rỗng = chưa đo)" }
        require(leftEdge.all { it in 0..left }) { "mép cong chỉ được nới RA ngoài tường thẳng $left: $leftEdge" }
        require(rightEdge.isEmpty() || rightEdge.size >= 2) { "bảng mép cong phải có ≥ 2 mẫu (hoặc rỗng = chưa đo)" }
        require(rightEdge.all { it in right..refW }) { "mép cong phải chỉ được nới RA ngoài $right (≤ $refW): $rightEdge" }
    }

    companion object {
        /**
         * Seal DL3 [ĐO 27/09 ±8 px] — xem KDoc [CameraClusterBand]. Dải `1640×428`.
         *
         * ⚠ **[top] 136 → 132 ở 2.79** [ĐO 27/09 tối, doc §13]. Owner: *"vẫn còn dư phía trên top, nên kéo lên thêm
         * 1 tý"*. 136 của §2 là *"hàng đầu tiên chắc chắn nhìn rõ"* (mép trên thanh tìm gmaps), KHÔNG phải mép của
         * thanh hệ thống. Đo thẳng mép ấy: nắn cả ba ảnh cụm về không gian framebuffer rồi đọc **đường kẻ phân cách**
         * chạy hết bề ngang — `cum-2` (7 neo, sai số **3,8 px**, neo cùng nửa với chỗ đọc) cho đáy đường kẻ ở
         * **y = 131**; `cum-0` (10 neo, 11,0 px) cho 122–126; `cum-1` (8 neo, 20,7 px) cho ~128. Lấy ước lượng **SÂU
         * NHẤT** (131) rồi xuống đúng một hàng ⇒ **132**. Lề chỉ còn 1 px và nó **nằm trong** sai số của chính phép
         * đo, nên nếu lệch thì hệ thống đè ≤ 4 px mép trên cửa sổ — và nó vẽ ĐÈ LÊN (F7/D6) y như mũi tên xi-nhan,
         * không phải ngược lại. Đổi lại: dải cao thêm **4 px** (424 → 428) cho cả ba hình.
         */
        val SEAL_DL3 = ClusterBandSpec(
            refW = 1920, refH = 720, left = 140, top = 132, right = 1780, bottom = 560, radiusPx = 24,
            // [ĐO 2026-09-27 chiều, dựng lại 27/09 tối cho [top] mới] biên vùng sáng vật lý, dò trong không gian
            // framebuffer qua homography ngược của 3 ảnh cụm; lấy ước lượng NGOÀI CÙNG của `cum-0`/`cum-1` ở mỗi hàng
            // (lệch giữa hai ảnh tới 48 px, và lệch theo hướng "ra ngoài" chỉ làm mất vài px ảnh SAU viền — còn lệch
            // vào trong thì để lại đúng khe đen mà owner đang chê). 9 mẫu, `y = 132, 185, …, 560`; sai số nội suy
            // 6,3 px. Doc §11 (phép đo) + §13 (dựng lại ở mốc 132).
            leftEdge = listOf(46, 19, 19, 29, 41, 50, 62, 74, 89),
            // [ĐO 2026-09-27 tối, doc §12] mép phải, CÙNG phép dò biên trong không gian framebuffer. Gộp `cum-0` +
            // `cum-2` bằng `min` — bên phải `min` là ước lượng TRONG CÙNG (ngược nghĩa với bên trái, cùng lý do
            // *"không để mất video"*): lệch VÀO chỉ để hở vài px kính, lệch RA thì đẩy video ra sau viền đục.
            // `cum-1` bị loại (neo dồn về nửa trái ⇒ ngoại suy sang phải lệch tới 185 px). Sai số nội suy 7,9 px.
            rightEdge = listOf(1829, 1869, 1876, 1873, 1866, 1856, 1844, 1824, 1800),
        )

        /**
         * [SEAL_DL3] **không mang** đường cong đã đo — mặc định cho đời cụm CHƯA ĐO ⇒ tường thẳng [left], tức đúng
         * hành vi 2.76 (`CameraClusterBand.leftEdge` trả rỗng, mặt nạ `glassMask` trả `null`).
         *
         * Vì sao phải có bản này thay vì cho mọi đời dùng chung [SEAL_DL3]: dải (`left/top/right/bottom`) chỉ ĐẶT cửa
         * sổ, đặt lệch thì nhìn thấy ngay; còn bảng `leftEdge` **CẮT điểm ảnh** (tới 122 px bên trái) theo đúng miếng
         * kính của MỘT đời cụm. Áp đường cong của Seal lên một cụm hình khác là xén ảnh mà không ai biết vì sao —
         * đúng loại lỗi CLAUDE.md §7 cấm (khác biệt đời xe phải nằm trong hồ sơ, không nằm trong một mặc định dùng
         * chung). Đo được đời nào thì khai [ClusterBandSpec] riêng cho đời ấy, y như [ClusterProfile.cameraFor].
         */
        val SEAL_DL3_NO_CURVE = SEAL_DL3.copy(leftEdge = emptyList(), rightEdge = emptyList())
    }
}
