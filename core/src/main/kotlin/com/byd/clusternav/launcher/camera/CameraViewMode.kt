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
 *  • [WIDE] *Thẳng rộng (thấy xa)* — 2.94 R1: camera GƯƠNG ⇒ phép chiếu **TRỤ** trục = thân xe ([CameraDewarpCylinder],
 *    `uCyl`) trên **trọn dải**, dịch khung về phía đuôi: đường song song thân xe (bậu cửa, vạch làn, mép lề) THẲNG, mép
 *    đuôi ~86°, đen ~0,9 % [ĐO tính, research port NCC 0,991 · bộ số [ĐOÁN] tới khi owner nhìn trên xe]. Camera
 *    sau/trước ⇒ *Thẳng rộng* = *Nắn thẳng* ([forCamera], owner 07/10). (2.92–2.93: κ xuyên tâm, đuôi ~96° nhưng cong.)
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

    /**
     * Lượt CHỌN kiểu [pick] khi `camera_dewarp_amount` đang là [amountPct]: độ nắn phải ghi KÈM, `null` = giữ nguyên. Chọn
     * [STRAIGHT] lúc ô tích *Nắn hình* đời 2.91 đang TẮT (amount 0) ⇒ nắn đủ — không thì chip nói "Nắn thẳng" mà ảnh vẫn thô
     * (khoá amount không còn hàng nào để người lái tự bật lại). 2.93 wave 2C · PREFS-SET-CAM-GLOBAL-REAPPLY: MỘT luật cho chip
     * Cài đặt lẫn `prefs_set camera_projection` (cửa chung `CameraReapply.setProjection` ở `:app`).
     */
    fun amountOnPick(pick: String, amountPct: Int): Int? =
        if (pick == STRAIGHT && amountPct == CameraDewarpPrefs.AMOUNT_MIN) CameraDewarpPrefs.AMOUNT_MAX else null

    /**
     * 2.94 R1 — kiểu theo VAI camera, gọi TRƯỚC [effective]. Owner 07/10: *"cam trái phải thì theo hướng trụ, cam sau
     * trước thì lấy option 3"* ⇒ camera giữa (sau/trước — `!which.side`) chọn [WIDE] thì vẽ ĐÚNG [STRAIGHT]. Quyết theo
     * [CameraWhich] (dữ liệu), không theo góc xoay: người lái xoay camera nào thì luật của camera ấy vẫn giữ (CLAUDE.md §7).
     * Pref không bị ghi đè — chip vẫn hiện lựa chọn của người lái.
     */
    fun forCamera(mode: String, which: CameraWhich): String =
        if (mode == WIDE && !which.side) STRAIGHT else mode

    /**
     * 2.94 QA F1 [ĐO máy ảo 07/10]: [forCamera] quy *Thẳng rộng* của camera sau/trước về [STRAIGHT] — nhưng *Nắn thẳng* đọc mức
     * nắn ĐÃ LƯU, mà người lái từng tắt nắn trước 2.92 có `camera_dewarp_amount = 0` ⇒ ảnh thô, không phải "như Nắn thẳng" (owner
     * chọn phương án 3). Chọn *Nắn thẳng* thật thì [amountOnPick] đã ép 100; đường quy này ép đúng như thế, KHÔNG ghi đè khoá đã lưu.
     */
    fun forcesFullAmount(asked: String, mode: String): Boolean = asked == WIDE && mode == STRAIGHT

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
    // 2.94 R1 — cùng ba khoá, nghĩa trên phép TRỤ ([CameraDewarpCylinder]): `kappa` = κ DỌC trục (thân xe; > 1 nén phần
    // đuôi xa vào khung, 2 đã vượt cực ⇒ gập — chốt cực trả đen) · `focal` = F cả dọc lẫn quanh trục (lớn ⇒ phóng vào,
    // ít đen) · `pan_x` = dịch cửa sổ dọc thân xe như cũ. Bộ số research công cụ ngoài repo (README §3) (F 0,55 · κ 1,5 ·
    // dịch 0,15 về đuôi): đuôi 86,3°, đen 0,87 % ở gương trái rot −90 [ĐO tính · bộ ĐOÁN tới khi owner nhìn trên xe].
    // ĐỔI mặc định so với 2.92–2.93 (F 100 → 122 · dịch −20 → −15): ai đã `prefs_set` thì giữ số của mình.

    /** κ 1,5 dọc trục — κ 2 vượt cực (ảnh gập, research `tmp/var1.png`); 1 ⇒ phần đuôi xa chạm cực sớm hơn. */
    const val WIDE_KAPPA_PCT_DEFAULT = 150

    /**
     * F 122 % của bộ suy ra (`0,452335 × 1,22 = 0,5518` ≈ F 0,55 của research; cùng nghĩa `camera_dewarp_focal`). F nhỏ
     * hơn ⇒ quanh trục rộng hơn nhưng đen tăng (F 100 % ⇒ góc khung vượt vòng ảnh).
     */
    const val WIDE_FOCAL_PCT_DEFAULT = 122

    /** Dịch khung 15 % về phía đuôi — dấu theo bên như `camera_dewarp_pan_x` ([CameraDewarpPrefs.panXSign]); −20 sát cực, nhoè đuôi. */
    const val WIDE_PAN_X_PCT_DEFAULT = -15
}
