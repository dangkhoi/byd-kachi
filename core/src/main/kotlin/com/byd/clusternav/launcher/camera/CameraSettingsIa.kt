package com.byd.clusternav.launcher.camera

/**
 * ═══ IA màn *Tiện nghi xe › Camera* — hai tầng khoá, THUẦN, là hợp đồng cho Cài đặt lẫn bài canh ═════════════════
 *
 * 2.76 (spec `kachi-276-closing.html` R1). Owner trên xe 27/09: *"có quá nhiều setting dùng cho việc test ở màn
 * setting camera, cần bỏ ra… chỉ để lại setting mà user cần, đi theo xe, hoặc hiển thị"*. Tới 2.75 màn ấy bày **28
 * khoá** phẳng — 9 núm nắn, dải, bề rộng, kênh HAL, cameraId, ma trận texture — vì mỗi cái sinh ra như một *móc đo*
 * cho một buổi xe, và không cái nào được thu lại sau khi đo xong.
 *
 * ## Hai tầng, một cổng
 *  • [USER_KEYS] — thứ người lái **quyết**: bật/tắt, chiếu cụm, góc, xoay, hình, nắn bật/tắt, nguồn. Luôn hiện.
 *  • [TECH_KEYS] — móc đo: chỉ hiện trong khối gập *"Nâng cao (kỹ thuật)"*, và khối ấy **chỉ được dựng khi chế độ
 *    kiểm thử đang mở** (`TestBridgeStore.isOn`) — **cùng** cổng 60 phút của cầu kiểm thử, không sinh cờ mới.
 *    Khoá **không bị xoá**: `prefs_set` vẫn ghi/đọc mọi khoá, giá trị đã đặt trên xe vẫn có hiệu lực; chỉ hàng
 *    trên màn ẩn đi. Một khoá kỹ thuật bị *xoá* là owner mất bộ số đã dò ba buổi xe.
 *
 * ## Vì sao NGUỒN (PANO/CHANNEL) ở tầng user
 * Owner hỏi thẳng *"có chuyển mặc định sang một kênh không"* — tức đó là một lựa chọn owner muốn tự nắm, không phải
 * một móc đo. Nhưng nó **chỉ hiện khi hồ sơ xe có bản đồ kênh** ([CameraProfileDefaults.hasChannelMap]); xe chưa đo
 * thì hàng ấy vắng (không phải mờ): một chip *Một camera* trên xe chưa biết kênh nào là kênh nào là một chip nói dối.
 *
 * ## Vì sao "nắn" là MỘT ô tích ở tầng user, còn tám núm ở tầng kỹ thuật
 * Bộ `F/K/S/tâm/dịch` đã được owner **duyệt bằng mắt trên xe** và nay là mặc định của hồ sơ Seal
 * ([CameraProfileDefaults]); thứ người lái còn muốn quyết chỉ là *"có nắn hay không"* — `camera_dewarp_amount`
 * `100`/`0`. Núm tinh chỉnh còn đó, sau cổng kỹ thuật.
 *
 * Danh sách là **chuỗi khoá prefs** (không phải mã UI) để bài canh so được với danh sách trắng của `prefs_set`:
 * hợp của hai tầng phải bằng **đúng** tập `camera_*` mà cầu kiểm thử ghi được — một khoá mới sinh ra mà không xếp
 * tầng là đỏ ngay.
 */
object CameraSettingsIa {

    /** Khoá `camera_source` — hiện ở tầng user **có điều kiện** (hồ sơ có bản đồ kênh). */
    const val KEY_SOURCE = "camera_source"

    /** Tầng NGƯỜI LÁI — thứ tự này cũng là thứ tự hàng trên màn. */
    val USER_KEYS: List<String> = listOf(
        "camera_signal_enabled",
        "camera_on_cluster",
        "camera_pos_left",
        "camera_pos_right",
        "camera_rot_left",
        "camera_rot_right",
        // 2.76 L7 — LẬT GƯƠNG từng bên (`research-side-camera-orientation-2026-09-27.md` §6.2): tay gương của ảnh HAL
        // **[CHƯA BIẾT]** tới khi đo CAM-M1 ⇒ mặc định TẮT, người lái tự bật khi thấy ảnh ngược tay so với gương kính.
        "camera_mirror_left",
        "camera_mirror_right",
        "camera_shape",
        "camera_dewarp_amount",
        KEY_SOURCE,
    )

    /** Tầng KỸ THUẬT — chỉ sau cổng chế độ kiểm thử. Thứ tự = thứ tự trong khối gập. */
    val TECH_KEYS: List<String> = listOf(
        "camera_render",
        "camera_span",
        "camera_strip_left",
        "camera_strip_right",
        "camera_circle_scale",
        "camera_hal_mode",
        "camera_cam_left",
        "camera_cam_right",
        "camera_gl_texmatrix",
        "camera_dewarp_cx",
        "camera_dewarp_cy",
        "camera_dewarp_k",
        "camera_dewarp_focal",
        "camera_dewarp_scale",
        "camera_dewarp_pan_x",
        "camera_dewarp_pan_y",
    )
}
