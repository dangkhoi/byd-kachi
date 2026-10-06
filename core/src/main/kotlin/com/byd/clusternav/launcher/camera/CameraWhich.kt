package com.byd.clusternav.launcher.camera

/**
 * ═══ 2.93 · BỐN CAMERA của khung ghép 4-in-1 — danh tính MỘT camera, THUẦN ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-293-cam.html` R1/R2. Owner 06/10: *"mình có thể lấy 4 cam, bẻ lại theo đúng 3 options này"* ·
 * *"cho chỉnh size và vị trí từng camera không nhỉ?"*.
 *
 * Tới 2.92 Kachi chỉ biết HAI camera — hai bên xi-nhan ([CameraSignalPolicy.Turn]). Lớp này là danh tính chung cho cả
 * bốn, dùng ở MỌI chỗ hỏi *"camera nào"*: phiên camera (xi-nhan hay theo yêu cầu), khoá cấu hình riêng từng camera
 * ([CameraCamConfig]), mã đích phím/nút/giọng nói ([CameraDemand]).
 *
 * ## Dải nào là camera nào — dữ liệu ĐO CŨ, với hai camera giữa mới ở mức [SUY]
 * Khung 5120×960 = bốn dải đứng 1280×960 theo thứ tự **sau · trái · phải · trước** [ĐO khung thô Seal 27/09 + SL6 28/09 —
 * KDoc [CameraPanoCrop.panoStripFor]] ⇒ [strip] là hằng của chính danh tính, không phải pref. Dải 1/2 (trái/phải) chạy
 * hiện trường từ 2.73; dải 0/3 (sau/trước) CHƯA từng hiện trên xe ⇒ theo CLAUDE.md §14 số đo cũ chỉ là [SUY] cho chúng
 * tới buổi xe (spec `kachi-293-cam.html` §2.2 · V-oncar · OQ1). (Dải của hai camera GƯƠNG vẫn chỉnh được qua
 * `camera_strip_*`/`camera_pano_*` cũ — đường ấy KHÔNG đổi; [strip] chỉ quyết hai camera giữa.)
 *
 * ## Mã lưu bền = [code] (chữ thường, không dấu)
 * Đi vào tên khoá (`camera_size_rear`), mã đích phím (`cam:rear`), lệnh cầu kiểm thử (`demand:rear`). Chuỗi, không
 * `ordinal`: thứ tự enum đổi (thêm camera) không được đổi nghĩa dữ liệu đã lưu.
 */
enum class CameraWhich(val code: String, val strip: Int) {
    REAR("rear", 0),
    LEFT("left", 1),
    RIGHT("right", 2),
    FRONT("front", 3),
    ;

    /** Camera GƯƠNG (theo bên xi-nhan) — mọi pref theo bên của 2.71–2.92 (`camera_*_left/right`) là của hai camera này. */
    val side: Boolean get() = this == LEFT || this == RIGHT

    /**
     * Dấu dịch khung theo trục x ([CameraDewarpPrefs.panXSign]): trái `+1`, phải `−1` (hai gương soi gương nhau [ĐO khung
     * thô 27/09]). Camera GIỮA (sau/trước) = `0`: chúng không có *"phía đuôi theo bên"* ⇒ hai núm dịch dùng chung của
     * camera gương (`camera_dewarp_pan_x` · `camera_wide_pan_x`) KHÔNG áp lên chúng — dịch theo một dấu đoán là kéo
     * khung về một phía không ai chọn.
     */
    val panXSign: Int get() = when (this) {
        LEFT -> CameraDewarpPrefs.panXSign(left = true)
        RIGHT -> CameraDewarpPrefs.panXSign(left = false)
        else -> 0
    }

    /** Bên xi-nhan tương ứng — chỉ hai camera gương có ([CameraSignalPolicy.Turn.NONE] cho hai camera giữa). */
    val turn: CameraSignalPolicy.Turn get() = when (this) {
        LEFT -> CameraSignalPolicy.Turn.LEFT
        RIGHT -> CameraSignalPolicy.Turn.RIGHT
        else -> CameraSignalPolicy.Turn.NONE
    }

    companion object {
        /** Thứ tự chip trong Cài đặt + danh sách đích phím: theo vòng xe sau → trái → phải → trước (= thứ tự dải). */
        val ALL: List<CameraWhich> = entries.toList()

        /** [code] → camera; chịu hoa/thường + khoảng trắng thừa (chuỗi từ `prefs_set`/cầu kiểm thử). Lạ ⇒ `null`. */
        fun ofCode(code: String?): CameraWhich? {
            val c = code?.trim().orEmpty()
            return if (c.isEmpty()) null else entries.firstOrNull { it.code.equals(c, ignoreCase = true) }
        }

        /** Camera của một bên xi-nhan; [CameraSignalPolicy.Turn.NONE] ⇒ `null`. */
        fun ofTurn(turn: CameraSignalPolicy.Turn): CameraWhich? = when (turn) {
            CameraSignalPolicy.Turn.LEFT -> LEFT
            CameraSignalPolicy.Turn.RIGHT -> RIGHT
            CameraSignalPolicy.Turn.NONE -> null
        }
    }
}
