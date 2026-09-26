package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VOICE-WAKE-SLOT-LAYOUT (2.69) — *"mở YouTube vào ô 2"* / *"đổi bố cục …"* qua `:wake` phải LÀM THẬT và NÓI THẬT ═══
 *
 * [SUY, review Pass 2 2.68] `buildSession` để `assignAppToSlot`/`onLayout` ở mặc định `false` ⇒ wake ON thì nút mic
 * (đi `:wake`) từ chối hai câu mà in-process làm được. Bài này canh bốn dây (quét SOURCE, cùng lệ
 * [VoiceEntryRouteWiringContractTest]):
 *  1. `buildSession` nối cả ba lambda (không còn mặc định), hai lambda Boolean đi [VoiceWakeHomeRelay.perform];
 *  2. `perform` CHỜ ack có hạn trên luồng riêng và chỉ trả `true` khi Activity báo `true` — không lạc quan, không im;
 *  3. Activity thi hành bằng ĐÚNG lambda của dispatcher in-process, kiểm hạn TRƯỚC, ack SAU;
 *  4. mặc định `false` của `VoiceWiring.dispatcher` giữ nguyên (bề mặt không nối được vẫn phải nói "không").
 */
class VoiceWakeHomeRelayWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val factory by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessionFactory.kt") }
    private val relay by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeHomeRelay.kt") }
    private val entry by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceEntry.kt") }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val voiceWiring by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWiring.kt") }

    // ══ (1) buildSession nối đủ, không còn mặc định ═══════════════════════════════════════════════════════════

    @Test
    fun `buildSession noi assignAppToSlot va onLayout qua relay perform, onListen khong con rong`() {
        val build = SourceRoots.body(factory, "internal fun VoiceWakeService.buildSession(): VoiceSession {")
        assertTrue(build.contains("assignAppToSlot = { idx, pkg -> relay.perform(VoiceHomeAction.ASSIGN_APP_TO_SLOT, VoiceHomeRelay.encodeSlot(idx, pkg)) }"),
            "gắn app vào ô từ `:wake` phải đi relay.perform với tham số mã hoá ở :core (ô 0-based như KachiHomeSlots.assignApp)")
        assertTrue(build.contains("onLayout = { preset -> relay.perform(VoiceHomeAction.SET_LAYOUT, VoiceHomeRelay.encodeLayout(preset)) }"),
            "đổi bố cục từ `:wake` phải đi relay.perform")
        assertFalse(build.contains("onListen = { }"), "`Kachi nghe` trong phiên `:wake` không được là lambda rỗng (nói xong mà không làm gì)")
        assertTrue(build.contains("onListen = { VoiceWakeSessions.acquire { buildSession() }.start() }"),
            "onListen phải cùng nghĩa in-process: start() lên phiên đang giữ (no-op khi đang chạy)")
        listOf("{ _, _ -> false }", "onLayout = { false }").forEach { assertFalse(build.contains(it), "buildSession không được để mặc định từ chối `$it`") }
        // Bốn việc cũ vẫn fire-and-forget qua relay.send — không chờ (không có Boolean nào để trả).
        assertTrue(build.contains("val openHome = { action: VoiceHomeAction, arg: String? -> relay.send(action, arg) }"))
    }

    // ══ (2) perform: chờ ack có hạn trên luồng riêng, trả đúng kết quả ════════════════════════════════════════

    @Test
    fun `perform cho ack tren HandlerThread rieng, het han thi false, chi true khi Activity bao true`() {
        val fn = SourceRoots.body(relay, "fun perform(action: VoiceHomeAction, arg: String?): Boolean {")
        // Receiver KHÔNG được nhận trên main: luồng gọi (main của `:wake`) đang đứng chờ chính ack này.
        assertTrue(fn.contains("Handler(ackThread.looper)"), "receiver ack phải đăng ký với Handler của luồng riêng — main đang chặn thì ack không bao giờ tới")
        assertTrue(relay.contains("HandlerThread(\"KachiHomeAck\")"), "luồng ack là HandlerThread riêng")
        assertTrue(fn.contains("ContextCompat.RECEIVER_NOT_EXPORTED"), "broadcast của app ⇒ cờ export bắt buộc (lint UnspecifiedRegisterReceiverFlag)")
        assertTrue(fn.contains("latch.await(timeoutMs, TimeUnit.MILLISECONDS)"), "chờ CÓ HẠN — không chờ vô hạn một tiến trình khác")
        assertTrue(fn.contains("val warm = mainAlive()") && fn.contains("val timeoutMs = VoiceHomeRelay.ackTimeoutMs(warm)"),
            "hạn lấy từ :core theo phép ĐO, không hằng rải")
        assertTrue(fn.contains("Activity ack sau \$ms ms (hạn \$timeoutMs ms, \$who)"),
            "🚗 phải có MỘT dòng log thời gian ack THẬT — không thể chốt hạn 1,5/4 s trên xe nếu không đo được")
        assertTrue(fn.contains("return acked && done.get()"), "chỉ true khi ack TỚI và Activity báo true — không lạc quan")
        assertFalse(fn.contains("return true"), "không có đường nào trả true mà không qua ack")
        assertTrue(fn.contains("if (i.getStringExtra(EXTRA_VOICE_HOME_NONCE) != nonce) return"), "ack của lượt khác (về muộn) không được tính cho lượt này")
        assertTrue(fn.contains("val deadline = now() + timeoutMs"), "hạn gửi cho Activity = cùng hạn `:wake` chờ")
        assertTrue(fn.contains("if (!registered) return false"), "không đăng ký được receiver ⇒ không hứa (false), không gửi việc")
        assertTrue(fn.contains("finally {") && fn.contains("unregisterReceiver(rx)"), "receiver phải gỡ ở mọi đường thoát")
        val launch = SourceRoots.body(relay, "private fun launch(action: VoiceHomeAction, arg: String?, nonce: String?, deadline: Long): Boolean")
        assertTrue(launch.contains("putExtra(EXTRA_VOICE_HOME_NONCE, nonce).putExtra(EXTRA_VOICE_HOME_DEADLINE, deadline)"), "intent phải mang nonce + hạn")
    }

    /**
     * ═══ Ba vá của lượt soát 2.69 (Pass 4) — mỗi vá một dây, khoá lại đúng bài học ═══════════════════════════════
     *
     *  1. **[P1] rò luồng**: `ackThread` từng là `by lazy` của **instance** relay, mà relay được dựng trong
     *     `buildSession()` ⇒ mỗi phiên-có-lệnh-gắn-ô một `HandlerThread` không ai `quit()`. ⇒ đưa lên companion:
     *     MỘT luồng cho cả tiến trình `:wake`.
     *  2. **[P1] từ chối oan**: hạn 1,5 s cố định ⇒ khi LMK đã giết tiến trình chính (ca thường — chính lý do
     *     CLOSE-3 tồn tại) thì `startActivity` phải dựng lạnh cả launcher, không kịp ack. ⇒ hạn theo phép ĐO
     *     `isMainProcessAlive` (`runningAppProcesses`, CLAUDE.md §5), tối đa 4 s < trần input 5 s của AOSP r47.
     *  3. **[P2] đường sập**: `require(...)` trong `perform` ném ra luồng main của `:wake` — mà `VoiceSession.execute`
     *     chạy trong một `post` NGOÀI `try/catch` của `runSession` ⇒ ngoại lệ ở đây là cả tiến trình chết.
     */
    @Test
    fun `mot luong ack cho ca tien trinh, han theo phep do, khong co duong nem`() {
        assertTrue(
            Regex("private companion object \\{[\\s\\S]*val ackThread: HandlerThread by lazy").containsMatchIn(relay),
            "luồng ack phải ở companion (mức TIẾN TRÌNH): một HandlerThread mỗi relay = một luồng rò mỗi phiên",
        )
        assertFalse(
            Regex("^ {4}private val ackThread", RegexOption.MULTILINE).containsMatchIn(relay),
            "không được quay lại `ackThread` của instance — relay sống theo phiên, luồng thì không ai quit()",
        )
        assertTrue(
            relay.contains("private val mainAlive: () -> Boolean = { VoiceWakeService.isMainProcessAlive(ctx) }"),
            "hạn chờ phải ĐO tiến trình chính (CLAUDE.md §5), không tin hằng cố định",
        )
        assertTrue(
            code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt")
                .contains("fun isMainProcessAlive(ctx: Context): Boolean = processAlive(ctx, ctx.packageName)"),
            "tiến trình chính = applicationId (manifest chỉ khai android:process cho :wake và :tts)",
        )
        assertFalse(relay.contains("require("), "một launcher không được chết vì tính năng phụ — cổng là log + từ chối")
        assertTrue(
            SourceRoots.body(relay, "fun perform(action: VoiceHomeAction, arg: String?): Boolean {")
                .contains("if (Looper.myLooper() == ackThread.looper) {"),
            "gọi trên chính luồng ack ⇒ từ chối có log, không ném",
        )
    }

    // ══ (3) Activity: cùng lambda, kiểm hạn TRƯỚC, ack SAU ═══════════════════════════════════════════════════

    @Test
    fun `Activity thi hanh bang dung lambda in-process, kiem han truoc, ack sau khi lam`() {
        val perform = SourceRoots.body(entry, "fun perform(action: VoiceHomeAction, arg: String?): Boolean = when (action) {")
        assertTrue(perform.contains("VoiceHomeAction.ASSIGN_APP_TO_SLOT -> VoiceHomeRelay.decodeSlot(arg)?.let { assignAppToSlot(it.slot, it.pkg) } ?: false"),
            "gắn ô phải giải mã ở :core rồi gọi CHÍNH lambda assignAppToSlot; tham số hỏng ⇒ false")
        assertTrue(perform.contains("VoiceHomeAction.SET_LAYOUT -> VoiceHomeRelay.decodeLayout(arg)?.let { onLayout(it) } ?: false"))
        val fromIntent = SourceRoots.body(entry, "fun performFromIntent(ctx: Context, intent: Intent, action: VoiceHomeAction, arg: String?): Boolean {")
        val expired = fromIntent.indexOf("VoiceHomeRelay.expired(deadline")
        val done = fromIntent.indexOf("val done = perform(action, arg)")
        val ack = fromIntent.indexOf("VoiceEntry.ackHome(ctx, nonce, done)")
        assertTrue(expired in 0 until done, "kiểm hạn phải TRƯỚC khi thi hành — quá hạn thì `:wake` đã nói không, làm nữa là màn khác lời")
        assertTrue(ack > done, "ack phải SAU khi thi hành, mang đúng kết quả")
        assertTrue(fromIntent.contains("intent.removeExtra(com.byd.clusternav.launcher.EXTRA_VOICE_HOME_NONCE)"), "singleTask: extra ở lại getIntent() ⇒ xoá")
        // KachiHomeWiring: sáu lambda, hai cái mới là đúng hai cái dispatcher in-process nhận (assignAppToSlot · onLayout).
        assertTrue(wiring.contains("VoiceHomeActions(openAppList, openSettings, openPermissions, onSwitchProfile, assignAppToSlot, onLayout)"))
        assertTrue(activity.contains("assignAppToSlot = { idx, pkg -> slots.assignApp(idx, pkg); true }"), "Activity vẫn truyền CHÍNH slots.assignApp (đường ngăn kéo)")
        assertTrue(activity.contains("onLayout = { preset -> selectPreset(preset); true }"), "Activity vẫn truyền CHÍNH selectPreset (đường chip bố cục)")
        assertTrue(entry.contains("const val ACTION_HOME_ACTION_ACK = \"com.byd.launcher.HOME_ACTION_ACK\""))
        assertTrue(SourceRoots.body(entry, "fun ackHome(ctx: Context, nonce: String, done: Boolean)").contains("setPackage(ctx.packageName)"), "ack chỉ trong gói")
    }

    // ══ (4) Mặc định của bộ dây chung KHÔNG đổi ═══════════════════════════════════════════════════════════════

    @Test
    fun `VoiceWiring dispatcher van mac dinh tu choi - be mat khong noi duoc phai noi khong`() {
        assertTrue(voiceWiring.contains("assignAppToSlot: (Int, String) -> Boolean = { _, _ -> false }"))
        assertTrue(voiceWiring.contains("onLayout: (com.byd.clusternav.launcher.LayoutPreset) -> Boolean = { false }"))
    }

    @Test
    fun `cac tep cua luot nay khong vuot tran 500 dong`() {
        listOf(
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt",
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessionFactory.kt",
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeHomeRelay.kt",
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessions.kt",
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceEntry.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt",
        ).forEach { rel -> val n = SourceRoots.text(rel).lines().size; assertTrue(n <= 500, "$rel dài $n dòng — trần là 500") }
    }
}
