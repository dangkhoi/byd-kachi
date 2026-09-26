package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.LayoutPreset

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
enum class VoiceHomeAction(
    val id: String,
    /**
     * VOICE-WAKE-SLOT-LAYOUT (2.69) — `:wake` phải **chờ kết quả** rồi mới trả lời người lái. Bốn việc cũ là
     * "mở màn X" (kết quả = màn hiện lên, không có gì để nói dối); hai việc mới là lambda `Boolean` của dispatcher
     * (*"đã gắn vào ô 2"* / *"không gắn được"*) — KDoc `VoiceWiring.dispatcher` cấm báo ✓ cho việc chưa xảy ra.
     */
    val awaitsResult: Boolean = false,
) {
    APP_LIST("app_list"),
    SETTINGS("settings"),
    PERMISSIONS("permissions"),
    /** Tham số = tên hồ sơ (extra `EXTRA_VOICE_HOME_ARG`). */
    SWITCH_PROFILE("switch_profile"),
    /** Tham số = [VoiceHomeRelay.encodeSlot] (ô 0-based + tên gói). Activity thi hành bằng lambda `assignAppToSlot`. */
    ASSIGN_APP_TO_SLOT("assign_app_to_slot", awaitsResult = true),
    /** Tham số = [VoiceHomeRelay.encodeLayout] (`LayoutPreset.name`). Activity thi hành bằng lambda `onLayout`. */
    SET_LAYOUT("set_layout", awaitsResult = true);

    companion object {
        /** `null`/lạ ⇒ `null`: intent bừa từ gói khác không được làm gì (cùng luật với `EXTRA_OPEN_SETTINGS_GROUP`). */
        fun of(id: String?): VoiceHomeAction? = values().firstOrNull { it.id == id }
    }
}

/**
 * ═══ VOICE-WAKE-SLOT-LAYOUT (2.69) — GIAO THỨC `:wake` → Activity → ack, phần THUẦN ═══════════════════════════
 *
 * Hai lambda `assignAppToSlot`/`onLayout` của `VoiceDispatcher` trả `Boolean` **đồng bộ**, còn việc phải làm nằm ở
 * `KachiHomeActivity` (tiến trình chính). `:wake` gửi intent kèm **nonce** + **hạn** (`elapsedRealtime`, đồng hồ
 * chung cả máy), chờ broadcast ack ≤ [ackTimeoutMs]; Activity **chỉ thi hành khi chưa quá hạn** — nên hai bên không bao
 * giờ nói hai điều khác nhau: hoặc việc xảy ra trong hạn và `:wake` báo ✓, hoặc không ai làm và `:wake` báo ✗.
 *
 * Ở `:core` chỉ có mã hoá tham số + phép so hạn (kiểm off-device); tầng Android (`VoiceWakeHomeRelay` ·
 * `VoiceHomeActions.performFromIntent`) chỉ làm việc chỉ Android làm được: intent, receiver, đồng hồ.
 */
object VoiceHomeRelay {

    /**
     * Hạn chờ Activity ack. 1,5 s = cùng lề với [VoiceEntryRoute.ACK_WARM_MS]: launcher là HOME nên gần như luôn
     * sống, chỉ cần một lượt `onNewIntent` + lambda + `sendBroadcast`. Hết hạn ⇒ `:wake` **từ chối thật** (không lạc
     * quan). 🚗 chưa đo trên xe — nếu log `KachiHomeRelay` thấy ack tới đều sau hạn thì nới ở ĐÂY, không ở chỗ gọi.
     */
    const val ACK_MS = 1_500L

    /**
     * [SOÁT 2.69 · P1] Hạn khi tiến trình CHÍNH **không sống** — LMK giết launcher trong lúc một app khác toàn
     * màn là ca THƯỜNG trên đầu xe 56–94 MB trống (chính lý do CLOSE-3 tồn tại). Khi ấy `startActivity` phải
     * spawn tiến trình + `Application.onCreate` + `KachiHomeActivity.onCreate` (bộ dây ô/ngăn kéo/voice) trước khi
     * `startVoiceIfRequested` chạy — 1,5 s là **từ chối oan**: `:wake` nói *"không gắn được"* rồi Activity cũng bỏ
     * việc vì quá hạn ⇒ câu nói của người lái rơi vào hư không dù mọi thứ đều lành.
     *
     * Dùng LẠI [VoiceEntryRoute.ACK_COLD_MS] (4 s): cùng phép đo (`runningAppProcesses`), cùng thang, một số duy
     * nhất trong cây. Trần an toàn của con số này là **5 s** vì `VoiceWakeHomeRelay.perform` chặn luồng MAIN của
     * `:wake`, và cửa sổ tấm chữ là cửa sổ NHẬN CHẠM — [ĐO AOSP android-10.0.0_r47
     * `services/core/java/com/android/server/wm/ActivityTaskManagerService.java`:
     * `public static final int KEY_DISPATCHING_TIMEOUT_MS = 5 * 1000;`]. Ba trần còn lại đều rộng hơn nhiều:
     * `ActiveServices.SERVICE_START_FOREGROUND_TIMEOUT = 10*1000` · `SERVICE_TIMEOUT = 20*1000` ·
     * `ActivityManagerService.BROADCAST_FG_TIMEOUT = 10*1000`. 🚗 chưa đo trên xe — chốt bằng `logcat -s KachiHomeRelay`.
     */
    fun ackTimeoutMs(mainProcessAlive: Boolean): Long =
        if (mainProcessAlive) ACK_MS else VoiceEntryRoute.ACK_COLD_MS

    /** Tham số của [VoiceHomeAction.ASSIGN_APP_TO_SLOT] đã giải mã. `slot` là **0-based** (như `KachiHomeSlots.assignApp`). */
    data class SlotAssign(val slot: Int, val pkg: String)

    /** Tên gói Android: ≥ 2 đoạn, mỗi đoạn bắt đầu bằng chữ — intent tới HOME activity ai cũng gửi được nên phải kiểm dạng. */
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun encodeSlot(slot: Int, pkg: String): String = "$slot:$pkg"

    /** `null` khi lạ/hỏng (ô âm, gói sai dạng, thiếu dấu `:`): không đoán, chỗ gọi từ chối. */
    fun decodeSlot(arg: String?): SlotAssign? {
        val at = arg?.indexOf(':') ?: return null
        if (at <= 0) return null
        val slot = arg.substring(0, at).toIntOrNull() ?: return null
        val pkg = arg.substring(at + 1)
        if (slot < 0 || !PKG.matches(pkg)) return null
        return SlotAssign(slot, pkg)
    }

    fun encodeLayout(preset: LayoutPreset): String = preset.name

    fun decodeLayout(arg: String?): LayoutPreset? = LayoutPreset.values().firstOrNull { it.name == arg }

    /**
     * Activity nhận việc đã **quá hạn** ⇒ không làm: `:wake` đã trả lời "không" cho người lái rồi, làm nữa là màn
     * đổi khác lời nói. `deadlineMs <= 0` = không có hạn (bốn việc cũ, fire-and-forget).
     */
    fun expired(deadlineMs: Long, nowMs: Long): Boolean = deadlineMs > 0L && nowMs > deadlineMs
}
