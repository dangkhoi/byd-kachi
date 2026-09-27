package com.byd.clusternav.launcher.camera

/**
 * Một lượt chụp khung camera cho cầu kiểm thử (`camera_frame`) — xem [CameraSignalController.grabFrame].
 *
 * Tách khỏi `CameraSignalController.kt` ở 2.76 (**pure move**, tệp kia chạm trần 500 dòng — CLAUDE.md §4.1) và
 * thêm đúng một trường [channel] cho R3.
 *
 * Gói **cả ảnh lẫn ngữ cảnh** vào một giá trị vì hai thứ đó chỉ đúng khi đọc CÙNG một nhịp main thread: hỏi ảnh
 * rồi hỏi tiếp *"đang hiện cam nào"* qua một lời gọi thứ hai là mở đường cho một nhịp xi-nhan chen vào giữa, và
 * lượt đo sẽ ghi một cái tên cam không thuộc về cái ảnh vừa chụp.
 *
 * @property bitmap khung đã chụp, `null` khi chưa có `SurfaceTexture` / đường kết xuất không chụp được / lượt chụp
 *   hỏng. Chỗ gọi PHẢI `recycle`.
 * @property available [CameraOverlayView.available] tại đúng nhịp chụp.
 * @property capturable [CameraOverlayView.capturable] — `false` = lớp video là `SurfaceView` (không có `getBitmap`).
 * @property overlayShowing cửa sổ overlay có đang treo hay không.
 * @property render mã đường kết xuất ĐANG treo ([CameraSignalPolicy.RENDERS]), rỗng = không hiện.
 * @property view tên [CameraSignalPolicy.CamView] đang hiện (rỗng = không có).
 * @property camId `cameraId` đã truyền cho `AvmCamera.open`, `-1` = chưa mở lần nào.
 * @property crop vùng crop đang áp, dạng `x0,y0,x1,y1` (rỗng = hiện nguyên khung).
 * @property rotationDeg góc xoay đang áp cho view (độ, dương = ↻).
 * @property content [CONTENT_RAW] hay [CONTENT_DEWARPED] — **ảnh này là khung gì**, và đây KHÔNG phải trang trí:
 *   trên đường `TV` thì `getBitmap` bỏ ma trận `setTransform` nên ảnh là khung **GỐC** (bằng chứng AOSP ở KDoc
 *   `CameraOverlayView.captureFrame`), còn trên đường `GL` thì shader ghi thẳng vào cửa ra nên ảnh là khung **ĐÃ
 *   NẮN**. Hai thứ trông không khác nhau nếu chỉ nhìn PNG, mà mọi phép đo bán kính/tâm vòng ảnh chỉ đúng trên khung
 *   thô ⇒ không nói ra là mời một vòng chẩn đoán sai địa chỉ (CLAUDE.md §2).
 * @property glStats `frames=N busySkip=M` của luồng vẽ GL, rỗng ở hai đường kia.
 * @property synthSize cỡ ảnh tổng hợp đang bơm (`camera_synth`), rỗng khi không bật.
 * @property glInfo `GL_MAX_TEXTURE_SIZE` + `GL_RENDERER` đã đo (RE §7 Q13), `"chưa đo"` khi chưa dựng ngữ cảnh nào.
 * @property channel phiên đang treo lấy **một kênh** camera (khung bị HAL kéo ngang ×STRIPS — R3 · 2.76) hay khung
 *   ghép. Lấy từ quyết định lúc dựng phiên (`Shown`), vì pref có thể đã đổi sau đó.
 */
data class CameraFrameShot(
    val bitmap: android.graphics.Bitmap?,
    val available: Boolean,
    val capturable: Boolean,
    val overlayShowing: Boolean,
    val render: String,
    val view: String,
    val camId: Int,
    val crop: String,
    val rotationDeg: Int,
    val content: String = CONTENT_RAW,
    val glStats: String = "",
    val synthSize: String = "",
    val glInfo: String = "",
    val channel: Boolean = false,
) {
    companion object {
        /** Ảnh là khung HAL đổ ra, **chưa** qua phép nắn nào. */
        const val CONTENT_RAW = "raw"

        /** Ảnh là khung **sau** shader nắn (đường [CameraSignalPolicy.RENDER_GL]). */
        const val CONTENT_DEWARPED = "dewarped"
    }
}
