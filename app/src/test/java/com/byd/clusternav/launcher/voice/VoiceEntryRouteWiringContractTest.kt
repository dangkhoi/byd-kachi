package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ CLOSE-3 (2026-09-26) — NÚT MIC + `EXTRA_START_VOICE` đi `:wake` khi "Hey Kachi" BẬT ════════════════════════
 *
 * [SUY, `ram-audit-2026-09-25.md` §1.1] wake bật ⇒ `:wake` đã giữ mô hình ASR int8; nút mic màn chính mở phiên
 * in-process ⇒ `VoiceRecognizer.open` nạp **bản thứ hai** (~15 s, +74 MB). Bài này canh BỐN dây, quét SOURCE (dự
 * án không dựng Activity/Service trong JVM — cùng lệ [VoiceWakeIsolationContractTest]):
 *  1. mọi lối vào của tiến trình chính đi qua `VoiceSession.start` ⇒ route (`VoiceEntry.tryWake`) TRƯỚC khi dựng gì;
 *  2. đường `:wake` KHÔNG chạm recognizer ở tiến trình chính (`VoiceEntry.kt` không gọi `VoiceEngine.recognizer`/
 *     `VoiceRecognizer.open`; `start(` quyết route trước `compareAndSet`);
 *  3. có đường LÙI (ack + `postDelayed` + `onFallback`) — nút mic không bao giờ chết vì `:wake` (trace-den-tan-cung);
 *  4. `:wake` trả việc cần Activity bằng `EXTRA_VOICE_HOME_ACTION`, KHÔNG còn gửi `EXTRA_START_VOICE` (vòng lặp
 *     `:wake` → Activity → route → `:wake` …) — và service ACK ở nhánh LISTEN_NOW.
 */
class VoiceEntryRouteWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val session by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt") }
    private val entry by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceEntry.kt") }
    private val service by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt") }
    // 2.69 — `buildSession` tách sang tệp riêng (trần 500 dòng); intent về Activity dựng ở relay.
    private val factory by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessionFactory.kt") }
    private val relay by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeHomeRelay.kt") }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val hooks by lazy { code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeHooks.kt") }
    private val assistant by lazy { code("src/main/java/com/byd/clusternav/modules/voicekey/AssistantLauncher.kt") }

    // ══ (1) Lối vào đi qua route ═════════════════════════════════════════════════════════════════════════════

    @Test
    fun `VoiceSession start - route wake TRUOC khi cam chot running va truoc khi dung bat cu thu gi`() {
        val start = SourceRoots.body(session, "fun start(")
        val route = start.indexOf("entry?.tryWake")
        val lock = start.indexOf("running.compareAndSet(false, true)")
        val overlay = start.indexOf("VoiceOverlay(ctx)")
        assertTrue(route >= 0, "start() phải hỏi VoiceEntry.tryWake (CLOSE-3)")
        assertTrue(lock > route, "route phải quyết TRƯỚC khi cầm chốt running — giao `:wake` rồi thì phiên chính không được coi là đang chạy")
        assertTrue(overlay > route, "route phải quyết TRƯỚC khi dựng tấm chữ — đường `:wake` không dựng gì ở tiến trình chính")
        assertTrue(start.contains("start(local = true)"), "đường lùi phải gọi lại start(local = true) — bỏ qua route, mở in-process")
        assertTrue(start.contains("if (!local && "), "cờ local phải chặn route (không thì lùi = lại giao `:wake` = vòng lặp)")
        // Đang có phiên in-process thì KHÔNG giao `:wake` (hai AudioRecord ở hai tiến trình).
        assertTrue(start.indexOf("if (running.get())") in 0 until route, "phải kiểm running TRƯỚC route")
    }

    /**
     * SOÁT 2.68 · P1 — đường lùi hẹn `postDelayed` 1,5–4 s, nên `start()` LẦN NÀY có thể chạy **sau** `onDestroy`.
     * `cancelled` không đỡ được (chính `start()` đặt nó về false), nên phải có cổng `stopped` và cổng ấy ở TRƯỚC route.
     */
    @Test
    fun `man chinh da huy thi bo moi start - ke ca luot lui da hen cua VoiceEntry`() {
        val start = SourceRoots.body(session, "fun start(")
        val gate = start.indexOf("if (stopped.get())")
        assertTrue(gate >= 0, "start() phải chặn khi màn đã huỷ (đường lùi `:wake` nổ sau onDestroy = overlay + AudioRecord mồ côi)")
        assertTrue(gate < start.indexOf("entry?.tryWake"), "cổng stopped phải ở TRƯỚC route — huỷ rồi thì cũng không giao `:wake`")
        assertTrue(SourceRoots.body(session, "fun stop(").contains("stopped.set(true)"), "stop() (onDestroy) phải khoá vĩnh viễn")
        assertTrue(entry.contains("ctx.applicationContext"), "receiver ack sống 1,5–4 s ⇒ đăng ký trên app context, không giữ Activity")
        // [SOÁT 2.68 · P1 · ĐO lintRelease] action của app ⇒ cờ export bắt buộc ở MỌI nhánh; `ContextCompat` bỏ hẳn nhánh.
        assertTrue(entry.contains("ContextCompat.registerReceiver(appCtx, rx, f, ContextCompat.RECEIVER_NOT_EXPORTED)"),
            "đăng ký phải khai cờ export trên mọi API (lint UnspecifiedRegisterReceiverFlag = ERROR, chặn bản release)")
        assertFalse(entry.contains("SDK_INT >= 33"), "không rẽ nhánh SDK nữa — nhánh dưới là chỗ quên cờ")
    }

    @Test
    fun `nut mic man chinh, EXTRA_START_VOICE va test bridge deu di voice start - khong ai goi local truc tiep`() {
        assertTrue(activity.contains("onVoice = { voice.start() }"), "pill mic thanh trên phải đi voice.start()")
        assertTrue(SourceRoots.body(wiring, "internal fun Activity.startVoiceIfRequested(").contains("session.start()"))
        assertTrue(hooks.contains("voice().start()"), "test bridge `listen` đi cùng đường")
        listOf("KachiHomeActivity" to activity, "KachiHomeWiring" to wiring, "TestBridgeHooks" to hooks, "AssistantLauncher" to assistant)
            .forEach { (n, src) -> assertFalse(src.contains("start(local = true)"), "$n không được bỏ qua route (start(local = true) chỉ dành cho đường lùi trong VoiceSession)") }
        // Phím vô-lăng vẫn đi thẳng `:wake` (đã là đường headless từ 2026-09-25).
        assertTrue(assistant.contains("VoiceWakeService.listenNow("))
    }

    @Test
    fun `voiceSession trong KachiHomeWiring truyen VoiceEntry voi dung 6 lambda cua dispatcher`() {
        val fn = SourceRoots.body(wiring, "internal fun Activity.voiceSession(")
        // 2.69 (VOICE-WAKE-SLOT-LAYOUT): 4 → 6 — thêm đúng hai lambda Boolean mà dispatcher in-process đã nhận.
        assertTrue(fn.contains("VoiceEntry(this, VoiceHomeActions(openAppList, openSettings, openPermissions, onSwitchProfile, assignAppToSlot, onLayout))"),
            "VoiceHomeActions phải là CÙNG sáu lambda mà VoiceWiring.dispatcher dùng — không mở đường thứ hai")
        assertTrue(fn.contains("entry = entry"), "VoiceSession của màn chính phải nhận entry")
    }

    // ══ (2) Đường `:wake` không chạm recognizer ở tiến trình chính ═══════════════════════════════════════════

    @Test
    fun `VoiceEntry khong dung recognizer, khong nap mo hinh, khong mo mic`() {
        listOf("VoiceEngine.recognizer(", "VoiceRecognizer.open", "VoiceEngine.preload(", "AudioRecord", "VoiceCapture(")
            .forEach { assertFalse(entry.contains(it), "VoiceEntry.kt không được chạm `$it` — mục đích CLOSE-3 là KHÔNG có bản mô hình thứ hai") }
        assertTrue(entry.contains("VoiceEntryRoute.decide("), "quyết định phải đi qua hàm thuần đã test ở :core")
        assertTrue(entry.contains("VoiceWakeService.isProcessAlive(ctx)"), "sống/chết đo bằng runningAppProcesses, không cờ RAM")
        assertTrue(entry.contains("VoiceWakeService.listenNow(ctx)"), "gửi bằng CÙNG đường phím vô-lăng/Hey Kachi")
    }

    // ══ (3) Đường lùi ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `tryWake co ack + het han thi onFallback, gui hong thi tra false ngay`() {
        val fn = SourceRoots.body(entry, "fun tryWake(")
        assertTrue(fn.contains("ACTION_LISTEN_ACK") || entry.contains("IntentFilter(ACTION_LISTEN_ACK)"), "phải lắng ack của `:wake`")
        assertTrue(fn.contains("postDelayed(") && fn.contains("plan.ackTimeoutMs"), "hạn chờ lấy từ Plan của :core, không hằng rải")
        assertTrue(fn.contains("onFallback()"), "hết hạn không ack ⇒ onFallback() (mở in-process)")
        assertTrue(fn.contains("if (!dispatched)") && fn.contains("return false"), "startForegroundService ném ⇒ trả false ngay, chỗ gọi mở in-process")
        assertTrue(fn.contains("VoiceEntryRoute.afterDispatch("), "kết luận lùi/ở lại đi qua hàm thuần")
    }

    // ══ (4) `:wake` → Activity: extra hành động, không còn EXTRA_START_VOICE; service ack ═══════════════════

    @Test
    fun `VoiceWakeService ack o nhanh LISTEN_NOW va buildSession khong gui EXTRA_START_VOICE nua`() {
        val cmd = SourceRoots.body(service, "override fun onStartCommand(")
        val listenNow = cmd.indexOf("ACTION_LISTEN_NOW")
        val ack = cmd.indexOf("VoiceEntry.ack(this)")
        assertTrue(listenNow >= 0 && ack > listenNow, "nhánh LISTEN_NOW phải ack — tiến trình chính đang chờ để KHÔNG mở phiên thứ hai")
        val build = SourceRoots.body(factory, "internal fun VoiceWakeService.buildSession(): VoiceSession {")
        assertFalse(build.contains("EXTRA_START_VOICE"), "gửi EXTRA_START_VOICE từ `:wake` = mở phiên nghe MỚI thay việc vừa nói + vòng lặp qua route")
        listOf("VoiceWakeService.kt" to service, "VoiceWakeSessionFactory.kt" to factory, "VoiceWakeHomeRelay.kt" to relay).forEach { (n, src) ->
            assertFalse(src.contains("EXTRA_START_VOICE"), "$n không được import/dùng EXTRA_START_VOICE ở mã (chú thích không tính)")
        }
        listOf("VoiceHomeAction.APP_LIST", "VoiceHomeAction.SETTINGS", "VoiceHomeAction.PERMISSIONS", "VoiceHomeAction.SWITCH_PROFILE")
            .forEach { assertTrue(build.contains(it), "buildSession phải trả `$it` về Activity qua extra") }
        assertTrue(relay.contains("putExtra(EXTRA_VOICE_HOME_ACTION, action.id)"), "intent về Activity dựng ở relay, mang id của VoiceHomeAction")
        assertTrue(service.contains("fun listenNow(ctx: Context): Boolean"), "listenNow phải trả Boolean để VoiceEntry biết gửi hỏng")
    }

    @Test
    fun `startVoiceIfRequested thi hanh EXTRA_VOICE_HOME_ACTION bang lambda cua entry va xoa extra`() {
        val fn = SourceRoots.body(wiring, "internal fun Activity.startVoiceIfRequested(")
        assertTrue(fn.contains("VoiceHomeAction.of(intent.getStringExtra(EXTRA_VOICE_HOME_ACTION))"))
        // 2.69: `performFromIntent` = `perform` (cùng lambda) + kiểm hạn + ack — xem `VoiceWakeHomeRelayWiringContractTest`.
        assertTrue(fn.contains("session.entry?.home?.performFromIntent(this, intent, action, arg)"), "phải thi hành bằng CÙNG lambda của dispatcher, không dựng đường mới")
        assertTrue(fn.contains("intent.removeExtra(EXTRA_VOICE_HOME_ACTION)"), "singleTask: extra ở lại getIntent() ⇒ phải xoá (cùng lẽ EXTRA_START_VOICE)")
        assertTrue(fn.contains("intent.removeExtra(EXTRA_START_VOICE)"))
        // Activity vẫn gọi ở CẢ onCreate và onNewIntent (singleTask ⇒ lời gọi thứ hai về onNewIntent).
        assertTrue(activity.split("startVoiceIfRequested(intent, voice)").size - 1 == 2, "startVoiceIfRequested phải được gọi ở onCreate VÀ onNewIntent")
    }

    // ══ Trần dòng: tệp bị tách vì CLOSE-3 ══════════════════════════════════════════════════════════════════════

    @Test
    fun `VoiceWakeStandDown da ra tep rieng va service van dung no`() {
        assertTrue(SourceRoots.exists("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeStandDown.kt"))
        assertFalse(service.contains("object VoiceWakeStandDown"), "object đã tách khỏi VoiceWakeService.kt (trần 500 dòng)")
        assertTrue(service.contains("VoiceWakeStandDown.decide("), "service vẫn phải gọi hàm thuần")
    }
}
