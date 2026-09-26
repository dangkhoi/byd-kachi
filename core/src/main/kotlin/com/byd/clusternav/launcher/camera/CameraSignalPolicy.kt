package com.byd.clusternav.launcher.camera

/**
 * ═══ CAMERA THEO XI-NHAN — luật thuần (`:core`, cấm android.*) ═══════════════════════════════════════════════
 *
 * Owner 2026-09-22: *"xi-nhan trái → camera trái → overlay bên trái màn; xi-nhan phải → camera phải → bên phải.
 * Cho chọn loại camera + vị trí trong Setting, default TẮT (đang phát triển)."*
 *
 * RE `docs/diagnostics/camera-panorama-RE-2026-09-22.md`: HAL `BYDAutoPanoramaDevice` chọn view bằng
 * `setPanoOutputState(int)` với hằng `APA_OUTPUT_STATE_*`. Lớp này chỉ QUYẾT ĐỊNH (view nào + bên nào của màn);
 * gọi HAL + dựng SurfaceView là việc `:app`.
 *
 * ## Hằng output [ĐO jadx-tmap BYDAutoPanoramaDevice]
 * Đây là các giá trị THẬT của HAL — KHÔNG bịa. Owner có thể đổi mapping trong Setting (mỗi xe khác nhau).
 */
object CameraSignalPolicy {

    /** Bên xi-nhan. */
    enum class Turn { LEFT, RIGHT, NONE }

    /** Vị trí overlay trên màn. */
    enum class Side { LEFT, RIGHT }

    // ── GÓC hiện overlay (spec `camera-turn-signal-hal-socket.html` R2–R4) ───────────────────────
    //
    // Chuỗi, KHÔNG enum: đây là **giá trị lưu bền** của `camera_pos_left`/`camera_pos_right` và cũng là mã của
    // chip trong Cài đặt. Một enum sẽ cần bảng đổi enum↔chuỗi ở CẢ HAI đầu (prefs + chipRow) — tức hai bản sao
    // của cùng một sự thật, đúng bẫy mà `ProfileNames` (khoá ≠ nhãn) đã trả giá.
    //
    // Chỉ có hai góc TRÊN: overlay phải nằm dưới thanh trên và KHÔNG đè nội dung dưới cùng của khung làm việc
    // (R2). Góc dưới không có trong tập chọn vì đó là chỗ thanh nút xe.

    /** Góc trên-TRÁI của khung launcher. */
    const val CORNER_TOP_LEFT = "TL"

    /** Góc trên-PHẢI của khung launcher. */
    const val CORNER_TOP_RIGHT = "TR"

    /** R4 — mặc định "cùng bên": xi-nhan trái → [CORNER_TOP_LEFT], phải → [CORNER_TOP_RIGHT]. */
    fun defaultCorner(left: Boolean): String = if (left) CORNER_TOP_LEFT else CORNER_TOP_RIGHT

    /**
     * Chuỗi góc đọc lên có dùng được không.
     *
     * Cần vì prefs là dữ liệu **sửa tay được** (`prefs_set`) và còn giữ giá trị của bản cũ (`camera_lvds_option`
     * từng nhận "A".."BCDH"). Giá trị lạ ⇒ chỗ đọc rơi về [defaultCorner], KHÔNG ném và cũng không im lặng đặt
     * overlay vào một góc thứ ba không tồn tại.
     */
    fun isCorner(v: String): Boolean = v == CORNER_TOP_LEFT || v == CORNER_TOP_RIGHT

    // ── XOAY video (spec `camera-turn-signal-hal-socket.html` R7 · owner 2026-09-26) ─────────────
    //
    // Owner (nguyên văn, off-car 2.67): *"cái xinhan bật cam mình cắt video ok, nhưng nó bị ngang, cần dọc video
    // lại, nên cần phải rotation 90 độ, bên trái là rotation 90 độ xoay qua trái, bên phải thì rotation 90 độ xoay
    // sang phải, nếu đc thì thêm option rotation trong setting"*.
    //
    // Owner (trên xe 2.69, 2026-09-26): *"xoay video cần làm 2 line setting độc lập cho camera trái và phải (khi
    // xinhan trái/phải), có thể 2 camera cần xoay khác nhau"*. ⇒ 2.71 bỏ mô hình "một chế độ cho cả hai bên"
    // (6 mã, có `SIDE`/`SIDEINV`): mỗi BÊN giữ một GÓC riêng trong bốn góc dưới đây. Khác biệt trái/phải không còn
    // là một "chế độ" nữa mà là hai lựa chọn — đúng khuôn `camera_pos_left/right` (R4), và khớp RE kinex
    // [ĐO `Y0/C0094o.java:308-315`]: hai số xoay ĐỘC LẬP theo bên, không có luật "trái ngược phải".
    //
    // [ĐO code] Vùng crop gương của ảnh fisheye 4-in-1 (5120×960) là dải DỌC (x rộng 0.10 × y cao 1.0) ⇒ căng vào
    // cửa sổ vuông thì hình nằm NGANG (`CamView.MIRROR_*`). Xoay ±90° đưa nó về chiều dọc.
    //
    // Chuỗi, KHÔNG enum — cùng lý do với `CORNER_*` ở trên: đây vừa là giá trị lưu bền của `camera_rot_left/right`,
    // vừa là mã chip trong Cài đặt; một bảng đổi mã ở hai đầu là hai bản sao của cùng một sự thật.
    //
    // Quy ước độ: **âm = ngược chiều kim đồng hồ (xoay qua trái)**, **dương = cùng chiều (xoay sang phải)** —
    // đúng chiều dương của `Matrix.postRotate` trên hệ toạ độ màn (trục y hướng xuống), nên tầng vẽ dùng thẳng số này.

    /** Không xoay. */
    const val ROTATE_NONE = "0"

    /** −90° (↺ qua trái). Mặc định của bên TRÁI. */
    const val ROTATE_LEFT = "L90"

    /** +90° (↻ sang phải). Mặc định của bên PHẢI. */
    const val ROTATE_RIGHT = "R90"

    /** 180°. */
    const val ROTATE_180 = "180"

    /**
     * Mã CŨ của khoá đơn `camera_rotation` (2.67–2.70): theo bên, trái −90 / phải +90. **Không còn trong
     * [ROTATIONS]** — chỉ [migrateRotation] còn đọc nó, để prefs đã ghi trên xe không bị mất khi nâng cấp.
     */
    const val ROTATE_BY_SIDE = "SIDE"

    /** Mã CŨ (review Pass 1 · 2.67): theo bên NGƯỢC, trái +90 / phải −90. Cùng số phận [ROTATE_BY_SIDE]. */
    const val ROTATE_BY_SIDE_INV = "SIDEINV"

    /** Mọi góc xoay hợp lệ cho MỘT bên — thứ tự này cũng là thứ tự chip của mỗi hàng trong Cài đặt. */
    val ROTATIONS: List<String> = listOf(ROTATE_NONE, ROTATE_LEFT, ROTATE_RIGHT, ROTATE_180)

    /**
     * Mặc định theo bên — trái [ROTATE_LEFT] (−90 ↺), phải [ROTATE_RIGHT] (+90 ↻): đúng nguyên văn owner 2.67
     * ("bên trái xoay qua trái, bên phải xoay sang phải"). Xe khác (SL6…) ghép ảnh 4-in-1 khác chiều thì chỉnh
     * từng hàng trong *Cài đặt › Tiện nghi xe*, không đổi hằng này. [ĐOÁN — CHƯA đo trên xe] rằng hai mặc định
     * này đúng chiều: RE kinex mặc định 0 cho cả hai, tức không có sự thật dòng xe nào để dựa; chốt bằng mắt owner.
     */
    fun defaultRotation(left: Boolean): String = if (left) ROTATE_LEFT else ROTATE_RIGHT

    /** Chuỗi góc xoay đọc lên có dùng được không — cùng vai [isCorner] (prefs sửa tay được qua `prefs_set`). */
    fun isRotation(v: String): Boolean = v in ROTATIONS

    /**
     * Góc xoay (độ) cho overlay của bên [left] theo mã [mode]: −90 / 0 / 90 / 180.
     *
     * [mode] lạ (kể cả mã cũ `SIDE`/`SIDEINV` lọt tới đây mà chưa qua [migrateRotation]) ⇒ coi như
     * [defaultRotation] của bên đó — không ném, không im lặng ra 0 (0 là một lựa chọn THẬT của owner, không phải
     * giá trị "không biết").
     */
    fun rotationDegrees(mode: String, left: Boolean): Int =
        when (if (isRotation(mode)) mode else defaultRotation(left)) {
            ROTATE_NONE -> 0
            ROTATE_LEFT -> -90
            ROTATE_RIGHT -> 90
            else -> 180   // ROTATE_180 — nhánh cuối vì `when` trên chuỗi cần else; tập đã đóng bởi isRotation
        }

    /**
     * Đổi giá trị của khoá đơn cũ `camera_rotation` (2.67–2.70) sang góc của bên [left] cho khoá mới
     * `camera_rot_left/right` (2.71). Thuần, không đụng prefs — `Prefs.cameraRotation` gọi đúng một lần khi thấy
     * khoá cũ còn mà khoá mới chưa có.
     *
     * - `SIDE`    → trái [ROTATE_LEFT] / phải [ROTATE_RIGHT] (giữ nguyên hành vi mặc định cũ);
     * - `SIDEINV` → trái [ROTATE_RIGHT] / phải [ROTATE_LEFT];
     * - một trong [ROTATIONS] → giữ nguyên cho CẢ hai bên (xe đã chọn `R90` thì cả hai bên vẫn +90 — đúng thứ
     *   owner đang nhìn thấy trên xe trước khi nâng cấp, không âm thầm đổi);
     * - lạ → [defaultRotation] của bên.
     */
    fun migrateRotation(old: String, left: Boolean): String = when (old) {
        ROTATE_BY_SIDE -> if (left) ROTATE_LEFT else ROTATE_RIGHT
        ROTATE_BY_SIDE_INV -> if (left) ROTATE_RIGHT else ROTATE_LEFT
        else -> if (isRotation(old)) old else defaultRotation(left)
    }

    // ── ĐƯỜNG KẾT XUẤT khung hình (CLOSE-14 · CAM-LAG, owner 2026-09-26: camera "hơi giật lag khi xe chạy") ─────
    //
    // Phân tích `docs/diagnostics/camera-lag-analysis-2026-09-26.md` L2: `TextureView` vẽ **trong** cây view ⇒ mỗi
    // khung đi qua HWUI rồi mới tới SurfaceFlinger; `SurfaceView` là **layer riêng**, rẻ hơn một lượt GPU. Nhưng
    // `SurfaceView` mất `setTransform` ⇒ mất cả crop-bằng-ma-trận lẫn xoay (R7). [ĐO xe 2026-09-26] hai lượt
    // `gfxinfo` CÙNG bản 2.70 có camera hiện cho 26,9 % và 4,67 % khung giật — số liệu **mâu thuẫn**, nên L2 vẫn ở
    // mức [CHƯA BIẾT]. Vì thế đây là một LỰA CHỌN có mã lưu bền, mặc định giữ đúng đường đang chạy (CLAUDE.md §6:
    // đường mới xuống cuối, không đảo mặc định), để buổi xe tới đo được hai đường cạnh nhau mà không build lại.
    //
    // Chuỗi, KHÔNG enum — cùng lý do với `CORNER_*`/`ROTATE_*`: vừa là giá trị lưu bền của `camera_render`, vừa là
    // mã chip trong Cài đặt.

    /** `TextureView` — đường ĐANG CHẠY hiện trường (crop + xoay bằng ma trận). Mặc định. */
    const val RENDER_TEXTURE = "TV"

    /** `SurfaceView` + `setZOrderMediaOverlay` — layer riêng, rẻ hơn một lượt GPU; KHÔNG xoay được bằng ma trận. */
    const val RENDER_SURFACE = "SV"

    /** Mọi đường kết xuất hợp lệ — cũng là thứ tự chip trong Cài đặt (mặc định đứng đầu). */
    val RENDERS: List<String> = listOf(RENDER_TEXTURE, RENDER_SURFACE)

    /** Đường kết xuất mặc định = thứ đã chạy trên xe từ 2.3x. */
    fun defaultRender(): String = RENDER_TEXTURE

    /** Mã đường kết xuất đọc lên có dùng được không — cùng vai [isRotation] (prefs sửa tay được qua `prefs_set`). */
    fun isRender(v: String): Boolean = v in RENDERS

    /**
     * Đường [render] có xoay được bằng MA TRẬN hay không.
     *
     * `false` ⇒ tầng vẽ phải (a) nhờ HAL xoay hộ nếu ROM cho (`AVMCamera.setDisplayOrientation`, [ĐO RE] có trong
     * lớp framework) và (b) tính cỡ cửa sổ theo tỉ lệ **chưa xoay** khi HAL cũng không nhận — chứ KHÔNG im lặng bỏ
     * góc owner đã chọn rồi để khung sai tỉ lệ. Mã lạ ⇒ coi như [defaultRender] (mã lạ chỉ tới từ prefs sửa tay).
     */
    fun rotatesByMatrix(render: String): Boolean =
        (if (isRender(render)) render else defaultRender()) == RENDER_TEXTURE

    /** Một view camera [ĐO BYDAutoPanoramaDevice.APA_OUTPUT_STATE_*]. */
    /**
     * Một góc camera. `cameraId` = tham số AVMCamera.open. `crop` = vùng cắt (x0,y0,x1,y1 chuẩn hoá 0..1) của
     * ảnh camera; `null` = hiện nguyên khung.
     *
     * [hintW]×[hintH] = **gợi ý** cỡ ảnh nguồn (px), chỉ dùng cho lượt dựng cửa sổ ĐẦU TIÊN, trước khi
     * `AVMCamera.getPreviewWidth/getPreviewHeight` trả số thật (xem [CameraOverlayFrame]). `0` = không biết ⇒ cửa
     * sổ giữ đúng ô vuông của 2.72 tới khi đo được — KHÔNG đoán tỉ lệ. Hai view GƯƠNG có gợi ý vì crop của chúng
     * lấy từ ảnh 4-in-1 [ĐO RE kinex `Y0/C0094o.java:318,342,347`: pano `5120×960`, một cam `1280×960`], tức chính
     * cái crop đã giả định ảnh nguồn là 4-in-1; các view khác chưa có bằng chứng cỡ nào nên để trống.
     *
     * [ĐO xe 2026-09-25] AVMCamera **CHỈ mở được id 0 (fisheye 4-in-1, 5120×960) và id 1 (cam trước)**; id 2/3/4/5
     * KHÔNG lên hình. Cam GƯƠNG trái/phải KHÔNG phải cameraId riêng — chúng là **CROP vùng trái/phải của ảnh
     * fisheye id 0** (RE kinex `C0094o`: pano crop trái x[0.25..0.35], phải x[0.65..0.75] của ảnh 5120×960).
     */
    enum class CamView(
        val outputState: Int,
        val cameraId: Int,
        val labelVi: String,
        val labelEn: String,
        val crop: FloatArray? = null,
        val hintW: Int = 0,
        val hintH: Int = 0,
    ) {
        // Gương = cameraId 1 = fisheye 4-in-1 [ĐO owner 2026-09-25: id 1 ra fisheye đúng nguồn] + CROP vùng
        // trái/phải (kinex pano crop trái x[0.25-0.35], phải x[0.65-0.75] của ảnh 4-in-1). id 0 crop ra sai.
        MIRROR_LEFT(1, 1, "Gương trái", "Left mirror", floatArrayOf(0.25f, 0f, 0.35f, 1f), hintW = 5120, hintH = 960),
        MIRROR_RIGHT(2, 1, "Gương phải", "Right mirror", floatArrayOf(0.65f, 0f, 0.75f, 1f), hintW = 5120, hintH = 960),
        FRONT_LEFT(1, 0, "Trước-trái", "Front-left"),
        FRONT_RIGHT(2, 1, "Trước-phải", "Front-right"),
        REAR_LEFT(3, 2, "Sau-trái", "Rear-left"),
        REAR_RIGHT(4, 3, "Sau-phải", "Rear-right"),
        LEFT_FRONT(13, 0, "Trái (trước)", "Left (front)"),
        RIGHT_FRONT(14, 1, "Phải (trước)", "Right (front)");
    }

    /** Mặc định: xi-nhan trái → cam GƯƠNG trái (crop fisheye); phải → gương phải — owner đổi được qua picker. */
    fun defaultView(turn: Turn): CamView? = when (turn) {
        Turn.LEFT -> CamView.MIRROR_LEFT
        Turn.RIGHT -> CamView.MIRROR_RIGHT
        Turn.NONE -> null
    }

    /** Vị trí overlay mặc định: xi-nhan trái → overlay bên TRÁI màn; phải → bên PHẢI (owner: "cùng bên"). */
    fun defaultSide(turn: Turn): Side? = when (turn) {
        Turn.LEFT -> Side.LEFT
        Turn.RIGHT -> Side.RIGHT
        Turn.NONE -> null
    }

    /** Từ trạng thái xi-nhan (leftTurn/rightTurn của CarStatus.lights) → [Turn]. Cả hai bật ⇒ NONE (đèn khẩn). */
    fun turnOf(left: Boolean, right: Boolean): Turn = when {
        left && !right -> Turn.LEFT
        right && !left -> Turn.RIGHT
        else -> Turn.NONE
    }
}
