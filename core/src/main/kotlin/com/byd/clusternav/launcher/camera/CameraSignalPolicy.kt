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

    /** Một view camera [ĐO BYDAutoPanoramaDevice.APA_OUTPUT_STATE_*]. */
    enum class CamView(val outputState: Int, val cameraId: Int, val labelVi: String, val labelEn: String) {
        // cameraId = tham số AVMCamera.open (đoán ban đầu; đổi được trên xe qua pref camera_cam_left/right).
        FRONT_LEFT(1, 0, "Trước-trái", "Front-left"),
        FRONT_RIGHT(2, 1, "Trước-phải", "Front-right"),
        REAR_LEFT(3, 2, "Sau-trái", "Rear-left"),
        REAR_RIGHT(4, 3, "Sau-phải", "Rear-right"),
        LEFT_FRONT(13, 0, "Trái (trước)", "Left (front)"),
        RIGHT_FRONT(14, 1, "Phải (trước)", "Right (front)"),
    }

    /** Mặc định: xi-nhan trái → camera FRONT_LEFT; phải → FRONT_RIGHT (owner có thể đổi loại trong Setting). */
    fun defaultView(turn: Turn): CamView? = when (turn) {
        Turn.LEFT -> CamView.FRONT_LEFT
        Turn.RIGHT -> CamView.FRONT_RIGHT
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
