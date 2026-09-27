package com.byd.clusternav.launcher.camera

/**
 * ═══ IA màn *Tiện nghi xe › Camera* — MỘT tầng người lái, THUẦN, là hợp đồng cho Cài đặt lẫn bài canh ═════════════
 *
 * 2.76 dựng **hai** tầng (người lái / *Nâng cao (kỹ thuật)* sau cổng chế độ kiểm thử). 2.77 **gỡ hẳn tầng thứ hai**
 * theo owner ngồi trong xe 27/09: *"không biết chỉnh đâu, nên chốt theo cái nào best là được, bỏ hết phần nâng cao
 * đi, bỏ luôn nguồn vì chốt là toàn cảnh khung ghép rồi"* và *"bỏ cái 1 cam ra, nhiều option quá rối cho người dùng,
 * bỏ luôn ở phần kỹ thuật"*.
 *
 * ## Hai danh sách, một khác biệt: có hàng trên màn hay không
 *  • [USER_KEYS] — **10 khoá** người lái quyết. Đây là TOÀN BỘ những gì màn Cài đặt bày ra.
 *  • [NO_UI_KEYS] — **15 khoá còn lại**: không còn một hàng nào trên màn, nhưng **vẫn ghi/đọc được qua `prefs_set`**
 *    của cầu kiểm thử và giá trị đã đặt trên xe **vẫn có hiệu lực**. Mặc định của chúng nay là bộ owner đã DUYỆT
 *    trên xe, khai trong hồ sơ xe ([CameraProfileDefaults] — Seal DL3: `STRIP · GL · F 55 · K 100 · S 130 · tâm 0,0
 *    · dịch 0,0`, dải trái 1 / phải 2, cameraId 1). Xoá hẳn chúng là mất đường chẩn đoán trên xe (CLAUDE.md §15
 *    bước 2/3: đọc/ghi state bền rẻ hơn mò UI hàng chục lần) **và** mất bộ số owner đã dò ba buổi xe.
 *
 * ## Vì sao NGUỒN (`camera_source`) không còn ở danh sách nào
 * Nó **bị xoá**, không phải *"ẩn UI"*: [ĐO xe 27/09, hai khung thô cùng cảnh] cùng cỡ cảnh, dải ghép có năng lượng
 * cạnh **686 vs 351** và tỉ lệ chi tiết ngang/dọc **0,30 (ghép) vs 0,19 (một kênh)** ⇒ một kênh chỉ bị KÉO NGANG
 * nhiều hơn, KHÔNG mang thêm điểm ảnh thật. Một khoá không còn đường code nào đọc mà vẫn ở danh sách trắng là một
 * lệnh `prefs_set` báo `ok` rồi không làm gì — đúng thứ tệ hơn một lệnh lỗi. Cùng lẽ với `camera_hal_mode`: không
 * còn nguồn một kênh thì không còn kênh nào để chọn, `AvmCamera` chỉ còn đường dò `0..3` của 2.73.
 *
 * ## Vì sao "nắn" là MỘT ô tích ở tầng người lái, còn tám núm thì không có hàng
 * Bộ `F/K/S/tâm/dịch` đã được owner **duyệt bằng mắt trên xe** và nay là mặc định của hồ sơ Seal; thứ người lái còn
 * muốn quyết chỉ là *"có nắn hay không"* — `camera_dewarp_amount` `100`/`0`.
 *
 * Danh sách là **chuỗi khoá prefs** (không phải mã UI) để bài canh so được với danh sách trắng của `prefs_set`:
 * hợp của hai danh sách phải bằng **đúng** tập `camera_*` mà cầu kiểm thử ghi được — một khoá mới sinh ra mà không
 * được xếp vào một trong hai là đỏ ngay (kể cả khoá chỉ dùng để đo).
 */
object CameraSettingsIa {

    /**
     * Tầng NGƯỜI LÁI — thứ tự này cũng là thứ tự hàng trên màn, và là **toàn bộ** màn ấy.
     *
     * Đổi số ⇒ đổi doc `camera-ia-profile.md` §IA + dòng CAM-F1 của runbook: owner **ĐẾM hàng** trên xe.
     */
    val USER_KEYS: List<String> = listOf(
        "camera_signal_enabled",
        "camera_on_cluster",
        "camera_pos_left",
        "camera_pos_right",
        "camera_rot_left",
        "camera_rot_right",
        // 2.76 L7 — LẬT GƯƠNG từng bên. CAM-M1 (xe 27/09): owner so ảnh với gương thật ⇒ mặc định TẮT là ĐÚNG.
        "camera_mirror_left",
        "camera_mirror_right",
        "camera_shape",
        "camera_dewarp_amount",
    )

    /**
     * Khoá **KHÔNG có hàng nào trên màn** — chỉ còn đường của cầu kiểm thử: ghi bằng `prefs_set`, đọc bằng
     * `prefs --es file clusternav_prefs`. (⚠ **không có** lệnh `prefs_get` — audit 27/09 đã gỡ tên ấy khỏi hai
     * runbook vì nó chưa bao giờ tồn tại; chỗ khai lệnh đọc thật là `TestBridgeNoHome.kt:26`.)
     *
     * Trước 2.77 chúng là `TECH_KEYS` (khối gập *"Nâng cao (kỹ thuật)"*). Tên đổi vì cái khác nhau nay không còn là
     * *"kỹ thuật hay không"* mà là *"có hàng hay không"*: `camera_circle_scale` chưa bao giờ có hàng, mà vẫn ở đây.
     */
    val NO_UI_KEYS: List<String> = listOf(
        "camera_render",
        "camera_span",
        "camera_strip_left",
        "camera_strip_right",
        "camera_circle_scale",
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
