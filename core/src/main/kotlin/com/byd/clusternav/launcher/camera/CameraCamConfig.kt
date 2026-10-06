package com.byd.clusternav.launcher.camera

/**
 * ═══ 2.93 · CẤU HÌNH RIÊNG TỪNG CAMERA — tên khoá · miền hợp lệ · phép "theo chung", THUẦN ═══════════════════════════
 *
 * Spec `docs/specs/kachi-293-cam.html` R2. Owner 06/10: *"cho chỉnh size và vị trí từng camera không nhỉ?"* ⇒ mỗi camera
 * (sau · trái · phải · trước — [CameraWhich]) có: **góc mặc định** · **vị trí kéo-thả** · **cỡ %** · **hình khung** ·
 * **kiểu hình** · **xoay** · **lật gương**. Áp cho camera ấy BẤT KỂ ai mở nó (xi-nhan hay theo yêu cầu): cùng một ống kính,
 * cùng một chỗ người lái muốn nhìn.
 *
 * ## Mặc định = ĐÚNG hành vi 2.92 từng pixel (CLAUDE.md §6)
 *  • vị trí VẮNG ⇒ góc mặc định ([cornerKey]) — đường đặt chỗ 2.73–2.92 nguyên văn;
 *  • cỡ [SIZE_DEFAULT] = 100 % ⇒ đúng vùng cho phép hôm nay;
 *  • hình khung / kiểu hình = [FOLLOW] ⇒ đọc khoá CHUNG (`camera_shape` · `camera_projection`) như hôm nay;
 *  • xoay / lật của hai camera GƯƠNG dùng LẠI khoá 2.71/2.76 (`camera_rot_left` …) — không khoá thứ hai cho cùng một sự
 *    thật lắp đặt. Hai camera GIỮA có khoá mới, mặc định không xoay / không lật ([CHƯA BIẾT] 🚗 — chỉnh trên xe).
 *
 * ## Phạm vi lưu (bảng ở `ProfileScopeCluster`, có bài canh)
 * Góc · vị trí · cỡ · hình · kiểu = **sở thích trình bày** ⇒ theo HỒ SƠ (cùng họ `camera_pos_left`/`camera_shape`).
 * Xoay · lật = **sự thật lắp đặt** của chiếc xe ⇒ theo XE (cùng họ `camera_rot_left`).
 */
object CameraCamConfig {

    /** Giá trị *"theo cài đặt chung"* của hình khung / kiểu hình riêng — cũng là mã chip đầu hàng. */
    const val FOLLOW = "AUTO"

    // ── Tên khoá ──────────────────────────────────────────────────────────────────────────────────────────────────

    /** Góc mặc định (`"TL"`/`"TR"`). Hai camera gương: đúng khoá 2.35 `camera_pos_left/right`. */
    fun cornerKey(w: CameraWhich): String = "camera_pos_${w.code}"

    /**
     * Vị trí kéo-thả: tâm cửa sổ, phần nghìn của vùng cho phép (`"x,y"`); vắng ⇒ theo [cornerKey]. Tên `xy` (không
     * `place`): đây là toạ độ trên MÀN, không phải địa điểm — `ProfileSharePolicyTest` soi chữ `place` như dữ liệu vị trí.
     */
    fun placeKey(w: CameraWhich): String = "camera_xy_${w.code}"

    /** Cỡ `%` của vùng cho phép. */
    fun sizeKey(w: CameraWhich): String = "camera_size_${w.code}"

    /** Hình khung riêng ([FOLLOW] = theo `camera_shape`). */
    fun shapeKey(w: CameraWhich): String = "camera_shape_${w.code}"

    /** Kiểu hình riêng ([FOLLOW] = theo `camera_projection`). */
    fun projectionKey(w: CameraWhich): String = "camera_projection_${w.code}"

    /** Xoay. Hai camera gương: đúng khoá 2.71 `camera_rot_left/right`. */
    fun rotationKey(w: CameraWhich): String = "camera_rot_${w.code}"

    /** Lật gương. Hai camera gương: đúng khoá 2.76 `camera_mirror_left/right`. */
    fun mirrorKey(w: CameraWhich): String = "camera_mirror_${w.code}"

    /** Khoá theo HỒ SƠ của một camera (thứ tự = thứ tự hàng trong bộ chỉnh). */
    fun profileKeys(w: CameraWhich): List<String> =
        listOf(cornerKey(w), placeKey(w), sizeKey(w), shapeKey(w), projectionKey(w))

    /** Khoá theo XE của một camera. */
    fun deviceKeys(w: CameraWhich): List<String> = listOf(rotationKey(w), mirrorKey(w))

    /** Mọi khoá của bộ chỉnh *Từng camera* (bốn camera × bảy khoá), theo thứ tự camera rồi thứ tự hàng. */
    val ALL_KEYS: List<String> = CameraWhich.ALL.flatMap { profileKeys(it) + deviceKeys(it) }

    /** Khoá theo HỒ SƠ của cả bốn camera. */
    val PROFILE_KEYS: List<String> = CameraWhich.ALL.flatMap { profileKeys(it) }

    /** Khoá theo XE của cả bốn camera. */
    val DEVICE_KEYS: List<String> = CameraWhich.ALL.flatMap { deviceKeys(it) }

    /** Khoá MỚI của 2.93 (trừ sáu khoá 2.35/2.71/2.76 của hai camera gương mà bộ chỉnh dùng lại). */
    val NEW_KEYS: List<String> = ALL_KEYS.filterNot { k ->
        CameraWhich.ALL.filter { it.side }.any { w -> k == cornerKey(w) || k == rotationKey(w) || k == mirrorKey(w) }
    }

    /**
     * Camera mà khoá [key] cấu hình (28 khoá của [ALL_KEYS], kể cả sáu khoá cũ của hai camera gương); khoá khác ⇒ `null`.
     * 2.93 wave 2B · D2 — `prefs_set` một khoá ấy ⇒ áp ngay nếu ĐÚNG camera ấy đang hiện, cùng luật Cài đặt.
     */
    fun cameraOf(key: String): CameraWhich? = CameraWhich.ALL.firstOrNull { key in profileKeys(it) || key in deviceKeys(it) }

    // ── Góc mặc định ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Góc mặc định khi khoá vắng: hai camera gương = 2.35 R4 (*"cùng bên"*); hai camera GIỮA = trên-PHẢI — [ĐOÁN] không có
     * sự thật nào chọn giữa hai góc cho camera sau/trước, chọn MỘT góc cho cả hai để dễ nói; người lái kéo được.
     */
    fun defaultCorner(w: CameraWhich): String =
        CameraSignalPolicy.defaultCorner(left = w == CameraWhich.LEFT)

    // ── Cỡ ───────────────────────────────────────────────────────────────────────────────────────────────────────

    /** Nhỏ nhất một nửa vùng hôm nay — nhỏ hơn thì ảnh camera hết đọc được trên màn xe. */
    const val SIZE_MIN = 50

    /** Lớn nhất 1,5× vùng hôm nay (vùng vẫn bị kẹp trong màn — [CameraPlacement]). */
    const val SIZE_MAX = 150

    /** 100 % = đúng vùng cho phép của 2.73–2.92. */
    const val SIZE_DEFAULT = 100

    /** Bước 5 % — cùng nhịp `camera_zoom` (thanh kéo 20 nấc). */
    const val SIZE_STEP = 5

    /** Số nấc thanh kéo cỡ. */
    const val SIZE_POSITIONS = (SIZE_MAX - SIZE_MIN) / SIZE_STEP

    fun isSizePct(v: Int): Boolean = v in SIZE_MIN..SIZE_MAX

    /** Nấc → `%`. */
    fun sizeAt(pos: Int): Int = (SIZE_MIN + pos * SIZE_STEP).coerceIn(SIZE_MIN, SIZE_MAX)

    /** `%` → nấc gần nhất. */
    fun sizePosition(pct: Int): Int =
        ((pct.coerceIn(SIZE_MIN, SIZE_MAX) - SIZE_MIN + SIZE_STEP / 2) / SIZE_STEP).coerceIn(0, SIZE_POSITIONS)

    // ── Vị trí kéo-thả: tâm cửa sổ, PHẦN NGHÌN của vùng cho phép ────────────────────────────────────────────────

    /** Thang của toạ độ vị trí (phần nghìn): `0` = mép trái/trên của vùng, [PLACE_SCALE] = mép phải/dưới. */
    const val PLACE_SCALE = 1000

    /** Tâm cửa sổ theo phần nghìn vùng cho phép. */
    data class Place(val x: Int, val y: Int) {
        /** Chuỗi lưu bền `"x,y"`. */
        fun encode(): String = "$x,$y"
    }

    /** Dựng [Place] đã kẹp vào `[0, PLACE_SCALE]`. */
    fun place(x: Int, y: Int): Place = Place(x.coerceIn(0, PLACE_SCALE), y.coerceIn(0, PLACE_SCALE))

    /**
     * Chuỗi `"x,y"` → [Place]; vắng/rỗng/sai cú pháp/ngoài thang ⇒ `null` = **theo góc mặc định**. Ngoài thang KHÔNG kẹp
     * im lặng: số lạ trên đĩa (sửa tay qua `prefs_set`) phải về một hành vi biết trước, không về một mép màn bất ngờ.
     */
    fun parsePlace(raw: String?): Place? {
        val parts = raw?.split(',')?.map { it.trim() } ?: return null
        if (parts.size != 2) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        return if (x in 0..PLACE_SCALE && y in 0..PLACE_SCALE) Place(x, y) else null
    }

    // ── Hình khung / kiểu hình riêng ─────────────────────────────────────────────────────────────────────────────

    /** Mã hình khung riêng hợp lệ (gồm [FOLLOW]). */
    fun isShapeChoice(v: String?): Boolean = v == FOLLOW || (v != null && CameraSignalPolicy.isShape(v))

    /** Mã kiểu hình riêng hợp lệ (gồm [FOLLOW]). */
    fun isProjectionChoice(v: String?): Boolean = v == FOLLOW || CameraViewMode.isMode(v)

    /** Hình khung HIỆU LỰC: riêng hợp lệ (≠ [FOLLOW]) thắng; còn lại ⇒ [global] (đã qua phép kiểm của pref chung). */
    fun effectiveShape(own: String?, global: String): String =
        if (own != null && own != FOLLOW && CameraSignalPolicy.isShape(own)) own else global

    /** Kiểu hình HIỆU LỰC: riêng hợp lệ (≠ [FOLLOW]) thắng; còn lại ⇒ [global] (đã qua `CameraViewMode.resolve`). */
    fun effectiveProjection(own: String?, global: String): String =
        if (own != null && own != FOLLOW && CameraViewMode.isMode(own)) own else global

    /** Mã chip đang chọn của hàng hình khung riêng — giá trị lạ trên đĩa hiện thành [FOLLOW] (đúng thứ đang áp). */
    fun shapeChoice(raw: String?): String = if (raw != null && isShapeChoice(raw)) raw else FOLLOW

    /** Mã chip đang chọn của hàng kiểu hình riêng — xem [shapeChoice]. */
    fun projectionChoice(raw: String?): String = if (raw != null && isProjectionChoice(raw)) raw else FOLLOW

    /** Góc xoay mặc định của hai camera GIỮA: không xoay ([CHƯA BIẾT] chiều ghép dải sau/trước — 🚗). */
    const val CENTRE_ROTATION_DEFAULT = CameraSignalPolicy.ROTATE_NONE
}
