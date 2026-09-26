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
    // Owner (nguyên văn): *"cái xinhan bật cam mình cắt video ok, nhưng nó bị ngang, cần dọc video lại, nên cần
    // phải rotation 90 độ, bên trái là rotation 90 độ xoay qua trái, bên phải thì rotation 90 độ xoay sang phải,
    // nếu đc thì thêm option rotation trong setting"*.
    //
    // [ĐO code] Vùng crop gương của ảnh fisheye 4-in-1 (5120×960) là dải DỌC (x rộng 0.10 × y cao 1.0) ⇒ căng vào
    // cửa sổ vuông thì hình nằm NGANG (`CamView.MIRROR_*`). Xoay ±90° đưa nó về chiều dọc.
    //
    // [ĐOÁN — CHƯA đo trên xe] rằng hai bên phải xoay NGƯỢC nhau (mặc định [ROTATE_BY_SIDE]). Đây là **suy** từ
    // câu owner, KHÔNG phải từ một phép đo: [ĐO RE kinex `Y0/C0094o.java:308-315`] app kinex giữ hai số xoay ĐỘC
    // LẬP cho hai bên, cả hai **mặc định 0**, cộng hai cờ lật ngang — tức không có luật "trái ngược phải" nào
    // trong ROM/RE để dựa vào. Vì thế mới có [ROTATE_BY_SIDE_INV] và ba chip cố định: chốt bằng mắt owner trên xe
    // (spec §Verification), rồi mới đổi hằng mặc định nếu cần.
    //
    // Chuỗi, KHÔNG enum — cùng lý do với `CORNER_*` ở trên: đây vừa là giá trị lưu bền của `camera_rotation`, vừa
    // là mã chip trong Cài đặt; một bảng đổi mã ở hai đầu là hai bản sao của cùng một sự thật.
    //
    // Quy ước độ: **âm = ngược chiều kim đồng hồ (xoay qua trái)**, **dương = cùng chiều (xoay sang phải)** —
    // đúng chiều dương của `Matrix.postRotate` trên hệ toạ độ màn (trục y hướng xuống), nên tầng vẽ dùng thẳng số này.

    /** Theo BÊN xi-nhan: trái −90° (↺ qua trái), phải +90° (↻ sang phải). **Mặc định** (owner 2026-09-26). */
    const val ROTATE_BY_SIDE = "SIDE"

    /**
     * Theo bên, **NGƯỢC** [ROTATE_BY_SIDE]: trái +90° (↻), phải −90° (↺).
     *
     * ## Vì sao phải có chế độ này (review Pass 1 · 2026-09-26)
     * §Verification của spec hứa: *"nếu ngược ⇒ đổi chip Xoay video"*. Nhưng nếu cái ngược là **cặp theo bên** (khả
     * năng lớn nhất, vì chiều đúng đang ở mức [SUY] — xem Reviewer Log), thì năm chip đầu KHÔNG diễn tả nổi
     * `(trái +90, phải −90)`: `↺90`/`↻90` áp cho CẢ hai bên. Owner sẽ đứng ở xe với một đường phục hồi không tồn
     * tại, phải đợi bản build mới — đúng cái mà CLAUDE.md §4 câu 4 ("hoàn tác kiểu gì?") đòi phải trả lời trước.
     *
     * [ĐO RE kinex `Y0/C0094o.java:308-315` + `KinexBottomBarOverlayService.java:645-648`]: kinex giữ **hai** số
     * xoay ĐỘC LẬP (`blind_spot_left_rotation_deg` / `blind_spot_right_rotation_deg`, mặc định 0) kèm hai cờ lật
     * ngang — tức bên trái và bên phải KHÔNG bị buộc vào một cặp cố định nào. Chế độ này là phần cặp còn thiếu.
     */
    const val ROTATE_BY_SIDE_INV = "SIDEINV"

    /** Không xoay. */
    const val ROTATE_NONE = "0"

    /** Mọi bên −90° (↺). */
    const val ROTATE_LEFT = "L90"

    /** Mọi bên +90° (↻). */
    const val ROTATE_RIGHT = "R90"

    /** Mọi bên 180°. */
    const val ROTATE_180 = "180"

    /** Mọi chế độ xoay hợp lệ — thứ tự này cũng là thứ tự chip trong Cài đặt. */
    val ROTATIONS: List<String> = listOf(
        ROTATE_BY_SIDE, ROTATE_BY_SIDE_INV, ROTATE_NONE, ROTATE_LEFT, ROTATE_RIGHT, ROTATE_180,
    )

    /**
     * Mặc định = [ROTATE_BY_SIDE] — owner 2026-09-26 yêu cầu ĐÚNG hành vi này làm mặc định (đo trên Seal). Xe khác
     * (SL6…) ghép ảnh 4-in-1 khác chiều thì chỉnh lại trong *Cài đặt › Tiện nghi xe › Xoay video camera*, không đổi
     * hằng này.
     */
    fun defaultRotation(): String = ROTATE_BY_SIDE

    /** Chuỗi chế độ xoay đọc lên có dùng được không — cùng vai [isCorner] (prefs sửa tay được qua `prefs_set`). */
    fun isRotation(v: String): Boolean = v in ROTATIONS

    /**
     * Góc xoay (độ) cho overlay của bên [turn] theo chế độ [mode]: −90 / 0 / 90 / 180.
     *
     * [mode] lạ ⇒ coi như [defaultRotation] (không ném, không im lặng ra 0 — 0 là một lựa chọn THẬT của owner, không
     * phải giá trị "không biết"). [Turn.NONE] không có overlay; trả 0 để hàm toàn phần.
     */
    fun rotationDegrees(mode: String, turn: Turn): Int {
        val m = if (isRotation(mode)) mode else defaultRotation()
        return when (m) {
            ROTATE_NONE -> 0
            ROTATE_LEFT -> -90
            ROTATE_RIGHT -> 90
            ROTATE_180 -> 180
            ROTATE_BY_SIDE_INV -> when (turn) {
                Turn.LEFT -> 90
                Turn.RIGHT -> -90
                Turn.NONE -> 0
            }
            else -> when (turn) {   // ROTATE_BY_SIDE
                Turn.LEFT -> -90
                Turn.RIGHT -> 90
                Turn.NONE -> 0
            }
        }
    }

    /** Một view camera [ĐO BYDAutoPanoramaDevice.APA_OUTPUT_STATE_*]. */
    /**
     * Một góc camera. `cameraId` = tham số AVMCamera.open. `crop` = vùng cắt (x0,y0,x1,y1 chuẩn hoá 0..1) của
     * ảnh camera; `null` = hiện nguyên khung.
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
    ) {
        // Gương = cameraId 1 = fisheye 4-in-1 [ĐO owner 2026-09-25: id 1 ra fisheye đúng nguồn] + CROP vùng
        // trái/phải (kinex pano crop trái x[0.25-0.35], phải x[0.65-0.75] của ảnh 4-in-1). id 0 crop ra sai.
        MIRROR_LEFT(1, 1, "Gương trái", "Left mirror", floatArrayOf(0.25f, 0f, 0.35f, 1f)),
        MIRROR_RIGHT(2, 1, "Gương phải", "Right mirror", floatArrayOf(0.65f, 0f, 0.75f, 1f)),
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
