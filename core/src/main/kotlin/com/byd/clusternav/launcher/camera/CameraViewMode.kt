package com.byd.clusternav.launcher.camera

/**
 * ═══ KIỂU HÌNH camera xi-nhan (2.92 · `camera_projection`) + THU PHÓNG (`camera_zoom`) — mã lưu bền, THUẦN ═══════
 *
 * Spec `docs/specs/kachi-292-camera-full-view.html` R1–R6 · research `diagnostics/camera-rear-coverage-2026-10-06.md`.
 * Owner 06/10: *"mình cắt hơi lố"* · *"ko dịch, mà lấy hết được không?"* · *"không thẳng và lấy hết được hay sao"*.
 *
 * ## Ba kiểu — CHUỖI, không enum (cùng lẽ `SHAPE_*`/`ROTATE_*`: giá trị trên đĩa và mã chip là MỘT)
 *  • [STRAIGHT] *Nắn thẳng* — đường 2.74–2.91 **từng bit**, và là **mặc định** (CLAUDE.md §6). Phối cảnh thẳng không
 *    vẽ được θ ≥ 90° ⇒ với bộ Seal (F 55 %) mép khung dừng ở 76° [ĐO tính, research K2] — đúng lời phàn nàn.
 *  • [WIDE] *Thẳng rộng (thấy xa)* — họ phép chiếu κ (`r = κF·tan(θ/κ)`, [DewarpParams.kappa]) trên **trọn dải**, dịch
 *    khung về phía đuôi: tới ~96° phía đuôi, toàn khung đen ≤ 2 % [ĐO tính trên bộ [WIDE_KAPPA_PCT_DEFAULT] ·
 *    [WIDE_FOCAL_PCT_DEFAULT] · [WIDE_PAN_X_PCT_DEFAULT]; bộ số là [ĐOÁN] tới khi owner nhìn trên xe].
 *  • [FISHEYE] *Gương cầu (thấy hết)* — ảnh thô (không nắn) của **trọn dải**, **vừa** khung (viền đen, không cắt).
 *
 * Thứ tự [MODES] = thứ tự chip: mặc định đứng đầu, mã MỚI đứng sau (CLAUDE.md §6).
 *
 * ## Đường vẽ nào làm được kiểu nào — [effective] là MỘT phép quy, có test
 * `GL` làm đủ ba. `TV` (TextureView + ma trận) không có shader ⇒ không làm được κ ⇒ *Thẳng rộng* hiện như *Gương cầu*
 * (vẫn "thấy hết", vẫn không cắt). `SV` (SurfaceView, đường đo phụ không UI) không ma trận, không shader ⇒ giữ ĐÚNG
 * hành vi hôm nay (*Nắn thẳng* = ảnh thô theo vùng cắt cũ), bỏ qua cả thu phóng. Cả hai lượt quy đều ghi nhật ký ở `:app`.
 */
object CameraViewMode {

    /** *Nắn thẳng* — mặc định, đường 2.74–2.91. */
    const val STRAIGHT = "STRAIGHT"

    /** *Thẳng rộng (thấy xa)* — κ + trọn dải + dịch về đuôi. */
    const val WIDE = "WIDE"

    /** *Gương cầu (thấy hết)* — ảnh thô trọn dải, vừa khung. */
    const val FISHEYE = "FISHEYE"

    /** Mọi kiểu — cũng là thứ tự chip trong Cài đặt. */
    val MODES: List<String> = listOf(STRAIGHT, WIDE, FISHEYE)

    /** Mặc định = đường đang chạy hiện trường. */
    fun defaultMode(): String = STRAIGHT

    /** Mã đọc lên có dùng được không (prefs sửa tay được qua `prefs_set`). */
    fun isMode(v: String?): Boolean = v != null && v in MODES

    /**
     * Pref → kiểu. Khoá VẮNG/lạ mà `camera_dewarp_amount` của người lái đang là `0` (đã tắt *Nắn hình* trước 2.92)
     * ⇒ [FISHEYE]: đó là người thừa kế đúng ý *"tôi muốn ảnh thô"* (và với khung chữ nhật trọn dải thì hai đường cho
     * cùng một ảnh). Mọi ca khác ⇒ [STRAIGHT] — xe không chạm gì thì không đổi một pixel.
     */
    fun resolve(raw: String?, amountPct: Int): String = when {
        raw != null && isMode(raw) -> raw
        amountPct == CameraDewarpPrefs.AMOUNT_MIN -> FISHEYE
        else -> STRAIGHT
    }

    /** Kiểu THẬT SỰ vẽ được trên đường [render] — xem KDoc lớp. Mã lạ ⇒ [defaultMode]. */
    fun effective(mode: String, render: String): String {
        val m = if (isMode(mode)) mode else defaultMode()
        return when {
            !CameraSignalPolicy.usesTextureView(render) -> STRAIGHT
            m == WIDE && !CameraSignalPolicy.rotatesInShader(render) -> FISHEYE
            else -> m
        }
    }

    /** Kiểu lấy **trọn dải** làm nội dung (vùng cắt nội dung ≠ vùng cắt khung được). */
    fun fullView(mode: String): Boolean = mode == WIDE || mode == FISHEYE

    /**
     * Hình KHUNG thật sự dùng: *Theo cụm* + kiểu trọn dải ⇒ [CameraSignalPolicy.SHAPE_RECT] — bám đường cong kính
     * là PHẢI phóng rồi cắt ([CameraClusterBand.place]), trái với lời hứa "thấy hết" (spec R7). Kiểu khác ⇒ y nguyên.
     */
    fun frameShape(mode: String, shape: String): String =
        if (fullView(mode) && shape == CameraSignalPolicy.SHAPE_CLUSTER) CameraSignalPolicy.SHAPE_RECT else shape

    // ── THU PHÓNG (`camera_zoom`) — % tuyệt đối, MỘT khoá cho cả ba kiểu ────────────────────────────────────────

    /** Thu nhỏ tối đa còn một nửa — dưới nữa thì khung toàn viền đen. */
    const val ZOOM_MIN = 50

    /** Phóng tối đa 1,5× — hơn nữa là cắt mất chính phần vừa "lấy hết". */
    const val ZOOM_MAX = 150

    /** `100 %` = khung tự nhiên của kiểu đang chọn (Nắn thẳng: đúng hôm nay; hai kiểu kia: vừa khung). */
    const val ZOOM_DEFAULT = 100

    /** Bước `5 %` — cùng nhịp mọi núm `%` của camera (thấy được bằng mắt trên xe, trọn dải trong 20 nấc). */
    const val ZOOM_STEP = 5

    /** Số nấc của thanh kéo (`0..ZOOM_POSITIONS`). */
    const val ZOOM_POSITIONS = (ZOOM_MAX - ZOOM_MIN) / ZOOM_STEP

    /** `camera_zoom` đọc lên có dùng được không. */
    fun isZoomPct(v: Int): Boolean = v in ZOOM_MIN..ZOOM_MAX

    /** Nấc [pos] của thanh kéo → `%`. */
    fun zoomAt(pos: Int): Int = (ZOOM_MIN + pos * ZOOM_STEP).coerceIn(ZOOM_MIN, ZOOM_MAX)

    /** `%` → nấc gần nhất (giá trị lạ ⇒ kẹp vào miền trước). */
    fun zoomPosition(pct: Int): Int =
        ((pct.coerceIn(ZOOM_MIN, ZOOM_MAX) - ZOOM_MIN + ZOOM_STEP / 2) / ZOOM_STEP).coerceIn(0, ZOOM_POSITIONS)

    // ── BỘ SỐ *Thẳng rộng* (3 khoá theo XE `camera_wide_*`, chỉnh trên xe qua `prefs_set`) ───────────────────────

    /** κ 1,5 — research §4.3: mép đuôi 96,2°, mép trước 62,2°, hàng giữa 28,6 % cho vùng θ ≥ 76° [ĐO tính · bộ ĐOÁN]. */
    const val WIDE_KAPPA_PCT_DEFAULT = 150

    /**
     * F 100 % của bộ suy ra (cùng nghĩa `camera_dewarp_focal`, Seal F 55 % ở *Nắn thẳng*): khung rộng hơn ở tâm để phần
     * đuôi không phải dồn vào mép. Tâm vẫn không méo ở mọi F (độ phóng xuyên tâm = tiếp tuyến — bài `CameraDewarpKappaTest`).
     */
    const val WIDE_FOCAL_PCT_DEFAULT = 100

    /** Dịch khung 20 % về phía đuôi — dấu theo bên như `camera_dewarp_pan_x` ([CameraDewarpPrefs.panXSign]). */
    const val WIDE_PAN_X_PCT_DEFAULT = -20
}
