package com.byd.clusternav.launcher.voice

/**
 * ═══ CLOSE-3 (2026-09-26) — LỐI VÀO phiên nghe đi TIẾN TRÌNH NÀO: chính hay `:wake`? ════════════════════════
 *
 * [SUY, `ram-audit-2026-09-25.md` §1.1] Khi "Hey Kachi" BẬT, `:wake` đã nạp mô hình ASR int8 (≈ 85–105 MB) và
 * phiên lệnh R7/LISTEN_NOW chạy ở đó. Nút mic màn chính và `EXTRA_START_VOICE` lại mở phiên **trong tiến trình
 * chính** ⇒ `VoiceRecognizer.open` nạp **bản thứ hai** (~15 s lần bấm đầu, +74 MB) trên đầu xe còn 56–94 MB trống.
 * [VoicePreloadPolicy.shouldPreloadInMain] (2.66) chỉ bỏ *nạp sẵn*; lần bấm đầu vẫn nạp. Đây là quyết định còn
 * thiếu: **mọi lối vào** (nút mic · phím · EXTRA) đi `listenNow` của `:wake` khi wake bật.
 *
 * ## Vì sao "service sống" KHÔNG đổi đường, chỉ đổi HẠN CHỜ
 * Wake bật mà `:wake` không sống (LMK vừa giết, `START_STICKY` chưa dựng lại) thì `startForegroundService` **tự
 * dựng** nó — chọn in-process ở ca này là nạp đúng bản mô hình thứ hai mà chính sách này sinh ra để tránh. Cái
 * khác nhau thật giữa hai ca là **thời gian tới khi `:wake` nhận lệnh**: tiến trình đang sống trả lời trong vài
 * chục ms; tiến trình lạnh phải spawn + `Application.onCreate` + `startForeground` (đầu xe DL3 chậm) ⇒ hạn chờ
 * ack dài hơn. Hết hạn không ack ⇒ [afterDispatch] trả IN_PROCESS: nút mic **không bao giờ chết** vì `:wake`
 * (trace-den-tan-cung), chỉ chậm hơn một lần.
 *
 * Wake TẮT ⇒ IN_PROCESS y như V3 R4 (tiến trình chính là nơi duy nhất nghe, giữ nạp sẵn).
 *
 * THUẦN ⇒ test off-device; `:app` đọc `Prefs.wakeEnabled` + `ActivityManager.runningAppProcesses` rồi hỏi ở đây.
 */
object VoiceEntryRoute {

    enum class Route { IN_PROCESS, WAKE_PROCESS }

    /** Kế hoạch cho một lối vào: đi đâu, và (nếu đi `:wake`) chờ ack bao lâu trước khi lùi về in-process. */
    data class Plan(val route: Route, val ackTimeoutMs: Long)

    /**
     * Hạn chờ ack khi `:wake` **đang sống** — `onStartCommand` chỉ cần nền tảng chuyển intent; 1,5 s là lề rộng
     * cho một đầu xe đang bận (🚗 chưa đo trên xe, chốt bằng log `VoiceEntry` khi kiểm CLOSE-3).
     */
    const val ACK_WARM_MS = 1_500L

    /** Hạn chờ ack khi phải **dựng lạnh** `:wake` (spawn + `Application.onCreate` + `startForeground`). */
    const val ACK_COLD_MS = 4_000L

    /**
     * @param wakeEnabled `Prefs.wakeEnabled` — owner bật **và** cầu chì false-accept chưa nổ.
     * @param wakeProcessAlive tiến trình `:wake` đang chạy (đo bằng `runningAppProcesses`, không phải cờ RAM).
     */
    fun decide(wakeEnabled: Boolean, wakeProcessAlive: Boolean): Plan = when {
        !wakeEnabled -> Plan(Route.IN_PROCESS, 0L)
        wakeProcessAlive -> Plan(Route.WAKE_PROCESS, ACK_WARM_MS)
        else -> Plan(Route.WAKE_PROCESS, ACK_COLD_MS)
    }

    /**
     * Sau khi đã GỬI `listenNow`: gửi hỏng (`startForegroundService` ném) hoặc không ack trong hạn ⇒ lùi in-process.
     * Cả hai đều là đường lùi có log, không phải đường im.
     */
    fun afterDispatch(dispatched: Boolean, acked: Boolean): Route =
        if (dispatched && acked) Route.WAKE_PROCESS else Route.IN_PROCESS
}

/**
 * Việc mà phiên trong `:wake` **không tự làm được** vì cần Activity (ngăn kéo · bảng Cài đặt · nhóm quyền · đổi
 * hồ sơ qua ViewModel). `:wake` gửi `id` qua extra `EXTRA_VOICE_HOME_ACTION` của `KachiHomeActivity`; màn chính
 * thi hành bằng **đúng** lambda mà dispatcher in-process dùng (không mở đường thứ hai — KDoc `VoiceDispatcher`).
 *
 * Trước 2.68 `:wake` gửi `EXTRA_START_VOICE` cho cả ba việc — tức "mở Kachi rồi **mở phiên nghe mới**", không phải
 * việc người lái vừa nói; và khi EXTRA ấy cũng đi route `:wake` thì thành vòng lặp. Enum này thay chỗ đó.
 */
enum class VoiceHomeAction(val id: String) {
    APP_LIST("app_list"),
    SETTINGS("settings"),
    PERMISSIONS("permissions"),
    /** Tham số = tên hồ sơ (extra `EXTRA_VOICE_HOME_ARG`). */
    SWITCH_PROFILE("switch_profile");

    companion object {
        /** `null`/lạ ⇒ `null`: intent bừa từ gói khác không được làm gì (cùng luật với `EXTRA_OPEN_SETTINGS_GROUP`). */
        fun of(id: String?): VoiceHomeAction? = values().firstOrNull { it.id == id }
    }
}
