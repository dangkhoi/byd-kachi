package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.87 · SOÁT vòng 1 · P2 — DÂY của cầu `:wake` → chính cho bảng "lệnh cuối" ([ControlSentRelay]) ══════════════════
 *
 * Hợp đồng thuần (ghi ở `:wake` tới bảng chính, không vòng, kiểm hợp lệ) chạy thật ở `:core`
 * (`ControlLastSentTest.cau wake sang chinh …`). Ở đây khoá những mắt xích chỉ thấy trong mã Android:
 *  1. đầu GỬI nối ở phiên `:wake` (cả ba lối vào đi qua `buildSession`), và CHỈ ở đó;
 *  2. đầu NHẬN đăng ký ở tiến trình chính — SAU cổng tiến trình nền của `KachiApplication` — và CHỈ ở đó;
 *  3. kênh là broadcast TRONG GÓI: `setPackage` + `RECEIVER_NOT_EXPORTED`, không khai trong manifest (không exported);
 *  4. đầu nhận ghi qua `absorb` (kiểm hợp lệ, không chuyển tiếp) — không qua `record`.
 */
class ControlSentRelayWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val relay by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/ControlSentRelay.kt") }
    private val factory by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessionFactory.kt") }
    private val application by lazy { code("src/main/java/com/byd/clusternav/KachiApplication.kt") }

    @Test
    fun `gui o phien wake, nhan o tien trinh chinh sau cong nen`() {
        assertTrue(SourceRoots.body(factory, "internal fun VoiceWakeService.buildSession(): VoiceSession {")
            .contains("ControlSentRelay.forwardFromWake(app)"), "phiên `:wake` (phím vô-lăng · nút mic · Hey Kachi) phải nối cầu")
        val onCreate = SourceRoots.body(application, "override fun onCreate()")
        val gate = onCreate.indexOf("if (isBackgroundVoiceProcess()) return")
        val recv = onCreate.indexOf("ControlSentRelay.receiveInMain(this)")
        assertTrue(gate >= 0 && recv > gate, "đầu nhận chỉ ở tiến trình CHÍNH — sau cổng `:tts`/`:wake`")
    }

    @Test
    fun `kenh la broadcast trong goi, khong exported, ghi qua absorb`() {
        val send = SourceRoots.body(relay, "private fun send(ctx: Context, id: String, index: Int)")
        assertTrue(send.contains("Intent(ACTION_CONTROL_SENT).setPackage(ctx.packageName)"), "chỉ trong gói")
        val recv = SourceRoots.body(relay, "fun receiveInMain(ctx: Context)")
        assertTrue(recv.contains("ContextCompat.registerReceiver(") && recv.contains("ContextCompat.RECEIVER_NOT_EXPORTED"),
            "receiver động KHÔNG exported (cùng khuôn VoiceEntry.ACTION_LISTEN_ACK)")
        assertTrue(SourceRoots.body(relay, "fun forwardFromWake(ctx: Context)")
            .contains("ControlLastSent.shared.forwardTo { id, index -> send(app, id, index) }"))
        val onReceive = SourceRoots.body(relay, "override fun onReceive(c: Context?, i: Intent?)")
        assertTrue(onReceive.contains("ControlLastSent.shared.absorb(id, index)"), "đầu nhận kiểm hợp lệ + KHÔNG chuyển tiếp")
        assertFalse(onReceive.contains(".record("), "ghi bằng record ở đầu nhận = mở đường cho vòng chuyển tiếp")
        val manifest = SourceRoots.text("src/main/AndroidManifest.xml")
        assertFalse(manifest.contains("com.byd.launcher.CONTROL_SENT"), "không khai receiver tĩnh/exported cho kênh nội bộ này")
    }

    /**
     * Soát vòng 2 [P3] — dòng từ `:wake` ĐỔI bảng thì ô nút vẽ lại NGAY (bản trước: ô vẫn "đóng" tới lần trạng thái xe đổi kế
     * tiếp, cú chạm kế làm ngược hình). Luật đếm ở `:core` (`ControlLastSentTest.dong tu wake doi bang…`); ở đây khoá dây:
     * màn chính THU [ControlLastSent.relayed] trong `repeatOnLifecycle(STARTED)` (luồng chính, tự huỷ khi màn khuất), chỉ khi
     * số đổi so với lượt đã vẽ, rồi đổ lại thanh nút + ô hành động giữa màn với trạng thái xe ĐANG CÓ.
     */
    @Test
    fun `dong tu wake doi bang thi o nut ve lai ngay tren luong chinh`() {
        val wiring = code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt")
        val collect = SourceRoots.body(wiring, "internal fun collectHome(")
        val seen = collect.indexOf("var seen = ControlLastSent.shared.relayed.value")
        val sub = collect.indexOf("ControlLastSent.shared.relayed.collect { v -> if (v != seen) { seen = v; resyncTiles() } }")
        val life = collect.lastIndexOf("repeatOnLifecycle(Lifecycle.State.STARTED)", sub)
        assertTrue(seen in 0 until life && life < sub, "thu trong repeatOnLifecycle(STARTED), mốc đã vẽ chụp TRƯỚC: $collect")
        assertTrue("collectHome(this, viewModel, container, resyncTiles = { resyncTiles() })" in
            code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt"), "màn chính nối đúng hàm vẽ lại")
        val resync = SourceRoots.body(code("src/main/java/com/byd/clusternav/launcher/KachiHomeRender.kt"), "internal fun KachiHomeActivity.resyncTiles()")
        assertTrue("val car = viewModel.uiState.value.carStatus" in resync && "dock.setCarStatus(car)" in resync &&
            "WidgetRefreshers.resyncActions(workspace, car)" in resync, "thanh nút + ô giữa màn, trạng thái xe ĐANG CÓ: $resync")
        val walk = SourceRoots.body(code("src/main/java/com/byd/clusternav/launcher/WidgetRefreshers.kt"), "fun resyncActions(")
        assertTrue("refreshAction(root, car)" in walk && "FitGridLayout.contentChanged(root)" in walk, "đổ qua đúng hàm đổ của ô, không dựng view")
        assertFalse("addView(" in walk || "removeView" in walk, "bất biến 1: hàm đổ không dựng/tháo view")
    }

    /** CLAUDE.md §8 + chống nối ở CẢ HAI đầu: mỗi đầu đúng MỘT chỗ gọi, đúng tệp. */
    @Test
    fun `moi dau cau dung mot cho goi`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.map { f ->
                f.name to f.readText().replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
                    .replace(Regex("(?m)//.*$"), "")
            }.toList()
        }
        fun callers(token: String) = all.filter { (name, src) -> name != "ControlSentRelay.kt" && src.contains(token) }.map { it.first }
        assertEquals(listOf("VoiceWakeSessionFactory.kt"), callers("ControlSentRelay.forwardFromWake("))
        assertEquals(listOf("KachiApplication.kt"), callers("ControlSentRelay.receiveInMain("))
        assertEquals(listOf("ControlSentRelay.kt"), all.filter { it.second.contains(".forwardTo {") }.map { it.first },
            "chỉ cầu `:wake` được nối nơi chuyển tiếp của bảng")
    }
}
