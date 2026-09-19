package com.byd.clusternav

import android.app.Application
import com.byd.clusternav.launcher.voice.PiperTtsService
import com.byd.clusternav.launcher.voice.VoiceEngine
import com.byd.clusternav.launcher.voice.VoiceVad
import com.byd.clusternav.launcher.voice.VoiceWakeService

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
        // ⚠ #0 (2026-09-18) — Application chạy ở **MỌI** tiến trình của app, và từ 1.79 app có tiến trình thứ hai
        // (`:tts`, chỉ chứa [PiperTtsService]); từ lượt crowd-test 2026-09-19 có tiến trình thứ ba (`:wake`, chỉ
        // chứa [VoiceWakeService]). Không có cổng này thì cả hai cũng nạp sẵn mô hình NGHE (74 MB int8) + Silero
        // VAD: vừa vô ích (một bên chỉ ĐỌC, một bên chỉ cần KWS ~5 MB mà nó TỰ nạp) vừa đúng thứ gây ra chính lỗi
        // đang vá — SIGSEGV của `OfflineTts.generate` là `SEGV_MAPERR` dưới áp lực RAM ([ĐO] xe chạy GMaps +
        // VietMap + cdr). Cô lập tiến trình mà nhân đôi (nay là nhân ba) RAM là cô lập hỏng.
        if (isBackgroundVoiceProcess()) return
        AppContainer.get(this)
        // V3 · R4 — nạp sẵn mô hình NGHE trên luồng nền ưu tiên thấp, sau 3 s. Ở đây chứ không ở màn chính:
        // tiến trình launcher sống suốt chuyến còn màn chính thì dựng lại nhiều lần, nên đặt ở activity là
        // nạp lại một thứ đã nằm sẵn trong RAM. Hàm tự rút lui khi chưa tải mô hình — xem KDoc [VoiceEngine.preload].
        VoiceEngine.preload(this)
        // B1.1 (1.70) — hâm sẵn Silero VAD (0,64 MB ONNX) để bỏ phần nạp ONNX khỏi đường "bấm → mic mở"
        // ([ĐO xe 2026-09-17] 1,5 s lần đầu). Giữ MỘT instance sống, mỗi lượt chỉ reset — xem KDoc VoiceVad.
        VoiceVad.preload(this)
    }

    /**
     * Đang chạy trong một tiến trình VOICE NỀN (`:tts` đọc · `:wake` nghe câu gọi) chứ không phải tiến trình
     * launcher?
     *
     * Cả hai tiến trình ấy đều **không** cần đồ thị DI của launcher lẫn mô hình NGHE 74 MB:
     *  • `:tts` chỉ tổng hợp tiếng (Piper VITS, tự nạp gói giọng của nó).
     *  • `:wake` chỉ chạy keyword-spotter ~5 MB — và **service tự nạp** model đó khi lần đầu được nghe
     *    ([VoiceWakeListener]), nên nạp trước ở đây là nạp sai thứ vào sai chỗ.
     *
     * ⚠ [ĐO] `android.jar` của compileSdk 37 khai `Application.getProcessName()` là **static** (API 28), và
     * KHÔNG phơi `Context.getProcessName()` — nên phải gọi qua tên lớp, không phải `this.processName` (dạng đó
     * không biên dịch: *"Unresolved reference 'processName'"*). minSdk 29 ⇒ luôn có, không cần đọc `/proc` hay
     * quét `ActivityManager.runningAppProcesses`.
     *
     * So bằng chính [PiperTtsService.PROCESS_SUFFIX] / [VoiceWakeService.PROCESS_SUFFIX] để manifest và mã không
     * thể lệch nhau — bài canh khoá cả hai cặp đó.
     */
    private fun isBackgroundVoiceProcess(): Boolean {
        val name = runCatching { Application.getProcessName() }.getOrNull() ?: return false
        return name.endsWith(PiperTtsService.PROCESS_SUFFIX) || name.endsWith(VoiceWakeService.PROCESS_SUFFIX)
    }
}
