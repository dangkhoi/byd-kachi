package com.byd.clusternav

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.byd.clusternav.launcher.KachiLog
import com.byd.clusternav.system.KachiCrashHandler
import com.byd.clusternav.system.StrictModeGate
import com.byd.clusternav.launcher.voice.ControlSentRelay
import com.byd.clusternav.launcher.voice.PiperTtsService
import com.byd.clusternav.launcher.voice.VoiceEngine
import com.byd.clusternav.launcher.voice.VoiceVad
import com.byd.clusternav.launcher.voice.VoiceWakeService

/**
 * Application của Kachi — điểm dựng [AppContainer] (đồ thị DI thủ công phía launcher) sớm nhất trong tiến trình,
 * để `ShellTransport.get` / `WindowCommandDispatcher.get` (nay uỷ quyền về container) luôn phân giải về MỘT đồ thị.
 *
 * Tối giản: chỉ khởi tạo container; KHÔNG chạm mạng/dadb TRÊN LUỒNG CHÍNH (các field container đều `by lazy` — chỉ
 * dựng khi được truy cập lần đầu; lượt đo phím vô-lăng của [A11yLifecycleHeal] chạy trên luồng nền riêng). Đăng ký ở
 * `AndroidManifest.xml` qua `android:name`.
 */
class KachiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hardening 2026-09-25 (audit F23) — cài TRƯỚC cổng tiến trình: `:tts`/`:wake` chết vì ngoại lệ Kotlin cũng
        // phải để lại vết. Ghi vết rồi CHUYỂN TIẾP cho handler Android (in stack + kill) — không nuốt, không restart.
        KachiCrashHandler.install { t, e ->
            Log.e(CRASH_TAG, "uncaught on thread '${t.name}'", e)
            KachiLog.writeCrash(this, t, e)
        }
        // ⚠ #0 (2026-09-18) — Application chạy ở **MỌI** tiến trình của app, và từ 1.79 app có tiến trình thứ hai
        // (`:tts`, chỉ chứa [PiperTtsService]); từ lượt crowd-test 2026-09-19 có tiến trình thứ ba (`:wake`, chỉ
        // chứa [VoiceWakeService]). Không có cổng này thì cả hai cũng nạp sẵn mô hình NGHE (74 MB int8) + Silero
        // VAD: vừa vô ích (một bên chỉ ĐỌC, một bên tự nạp bộ nghe của nó — từ 2026-09-22 `:wake` dùng ASR int8 74 MB làm hotword, `Prefs.wakeEngineAsr`) vừa đúng thứ gây ra chính lỗi
        // đang vá — SIGSEGV của `OfflineTts.generate` là `SEGV_MAPERR` dưới áp lực RAM ([ĐO] xe chạy GMaps +
        // VietMap + cdr). Cô lập tiến trình mà nhân đôi (nay là nhân ba) RAM là cô lập hỏng.
        if (isBackgroundVoiceProcess()) return
        // R4(b) — StrictMode CHỈ log, CHỈ build type `debug` (máy ảo). Đặt TRƯỚC `AppContainer.get` để vi phạm lúc
        // dựng đồ thị cũng lộ. Luật bật/tắt ở [StrictModeGate] (thuần, có bài canh). Không `penaltyDeath`.
        if (StrictModeGate.enabled(BuildConfig.DEBUG, BuildConfig.BUILD_TYPE)) enableStrictModeLogging()
        // 2.83 · lớp 1/2 tự chữa phím vô-lăng — SỚM NHẤT của tiến trình launcher: [ĐO xe 29/09] BYD giết Kachi mỗi lần
        // tắt máy và Android dựng lại nó 0,3 s sau lúc màn đã tắt; chỗ này là mã ĐẦU TIÊN chạy trong tiến trình mới.
        // Chỉ hỏi binder + đăng ký bộ thu màn bật trên luồng chính; phần đo/leo chạy nền (xem KDoc [A11yLifecycleHeal]).
        // READY-AT-HOME §4.6 — cổng thi hành của kênh shell phải có mặt TRƯỚC phiên tự chữa đầu tiên (luồng nền của
        // lớp 1 có thể chạy ngay sau dòng kế). Chỉ gán móc, không I/O, không nối gì.
        ShellReadiness.install(this)
        A11yLifecycleHeal.install(this)
        AppContainer.get(this)
        // V3 · R4 — nạp sẵn mô hình NGHE trên luồng nền ưu tiên thấp, sau 3 s. Ở đây chứ không ở màn chính:
        // tiến trình launcher sống suốt chuyến còn màn chính thì dựng lại nhiều lần, nên đặt ở activity là
        // nạp lại một thứ đã nằm sẵn trong RAM. Hàm tự rút lui khi chưa tải mô hình — xem KDoc [VoiceEngine.preload].
        VoiceEngine.preload(this)
        // B1.1 (1.70) — hâm sẵn Silero VAD (0,64 MB ONNX) để bỏ phần nạp ONNX khỏi đường "bấm → mic mở"
        // ([ĐO xe 2026-09-17] 1,5 s lần đầu). Giữ MỘT instance sống, mỗi lượt chỉ reset — xem KDoc VoiceVad.
        VoiceVad.preload(this)
        // 2.87 · SOÁT vòng 1 · P2 — nhận lượt ghi bảng "lệnh cuối" từ phiên giọng nói `:wake` vào bảng ô + phím của tiến
        // trình NÀY (chỉ tiến trình chính — sau cổng nền ở trên). Chỉ đăng ký receiver, không I/O, không phụ thuộc gì ở
        // trên/dưới; đứng TRƯỚC `EarlyShellChannel.start` vì dòng đó được chốt là lời gọi cuối (ReadyAtHomeWiringContractTest).
        ControlSentRelay.receiveInMain(this)
        // 2.93 · CAMERA-ON-DEMAND — nhận lệnh camera của phiên giọng nói `:wake` (*"mở camera sau"* qua phím vô-lăng): cùng
        // khuôn receiver trong gói ngay trên; chỉ đăng ký, không I/O. Đứng TRƯỚC dòng chốt cuối `EarlyShellChannel.start`.
        com.byd.clusternav.launcher.camera.CameraDemandDispatch.receiveInMain(this)
        // READY-AT-HOME R2.1 — nối kênh shell NGAY khi tiến trình bật (cả lượt BYD dựng lại Kachi lúc màn tắt), CHỈ khi
        // xe đã duyệt khoá này (dấu bền còn tươi); không thì để F4 hỏi đúng lúc. Đường MỚI ⇒ dòng CUỐI (CLAUDE.md §6).
        EarlyShellChannel.start(this)
    }

    /**
     * Đang chạy trong một tiến trình VOICE NỀN (`:tts` đọc · `:wake` nghe câu gọi) chứ không phải tiến trình
     * launcher?
     *
     * Cả hai tiến trình ấy đều **không** cần đồ thị DI của launcher lẫn mô hình NGHE 74 MB:
     *  • `:tts` chỉ tổng hợp tiếng (Piper VITS, tự nạp gói giọng của nó).
     *  • `:wake` **tự nạp** bộ nghe của nó khi lần đầu được nghe (KWS ~5 MB, hoặc ASR int8 74 MB khi `wakeEngineAsr` —
     *    mặc định từ 2026-09-22; xem `docs/diagnostics/ram-audit-2026-09-25.md` §1–2: đây là bản mô hình thứ hai trên máy)
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
        // Hardening 2026-09-25 (audit F8): đọc tên tiến trình hỏng ⇒ coi là tiến trình NỀN (bỏ nạp 74 MB model),
        // vì chiều ngược lại chỉ hoãn `AppContainer.get` (mọi field `by lazy`, tự dựng khi cần) — rẻ hơn nhiều.
        val name = runCatching { Application.getProcessName() }.getOrNull() ?: return true
        return name.endsWith(PiperTtsService.PROCESS_SUFFIX) || name.endsWith(VoiceWakeService.PROCESS_SUFFIX)
    }

    /**
     * `StrictMode` chỉ-log cho máy ảo (R4(b)): `ThreadPolicy` bắt disk/network/slow-call trên luồng chính,
     * `VmPolicy` bắt `Closeable`/registration rò. Không `penaltyDeath` — mục đích là ĐO (đọc bằng
     * `adb logcat -s StrictMode:*`), không phải chặn.
     */
    private fun enableStrictModeLogging() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }

    private companion object {
        const val CRASH_TAG = "KachiCrash"
    }
}
