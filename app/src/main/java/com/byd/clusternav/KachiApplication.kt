package com.byd.clusternav

import android.app.Application
import com.byd.clusternav.launcher.voice.VoiceEngine
import com.byd.clusternav.launcher.voice.VoiceVad

/**
 * Application của Kachi — điểm dựng [AppContainer] (đồ thị DI thủ công phía launcher) sớm nhất trong tiến trình,
 * để `ShellTransport.get` / `WindowCommandDispatcher.get` (nay uỷ quyền về container) luôn phân giải về MỘT đồ thị.
 *
 * Tối giản: chỉ khởi tạo container; KHÔNG chạm mạng/dadb (các field container đều `by lazy` — chỉ dựng khi được
 * truy cập lần đầu). Đăng ký ở `AndroidManifest.xml` qua `android:name`.
 */
class KachiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.get(this)
        // V3 · R4 — nạp sẵn mô hình NGHE trên luồng nền ưu tiên thấp, sau 3 s. Ở đây chứ không ở màn chính:
        // tiến trình launcher sống suốt chuyến còn màn chính thì dựng lại nhiều lần, nên đặt ở activity là
        // nạp lại một thứ đã nằm sẵn trong RAM. Hàm tự rút lui khi chưa tải mô hình — xem KDoc [VoiceEngine.preload].
        VoiceEngine.preload(this)
        // B1.1 (1.70) — hâm sẵn Silero VAD (0,64 MB ONNX) để bỏ phần nạp ONNX khỏi đường "bấm → mic mở"
        // ([ĐO xe 2026-09-17] 1,5 s lần đầu). Giữ MỘT instance sống, mỗi lượt chỉ reset — xem KDoc VoiceVad.
        VoiceVad.preload(this)
    }
}
