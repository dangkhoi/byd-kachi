package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.camera.CameraDemand
import com.byd.clusternav.launcher.camera.CameraDemand.Outcome
import com.byd.clusternav.launcher.camera.CameraWhich
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2B · VOICE-CAM-OFF-360-FALLBACK — BÀI CHẠY THẬT qua [VoiceDispatcher] (spec `kachi-293-cam.html` §10 D1 · OQ6) ═══
 *
 * [VoiceDispatcher] THẬT (bộ phân tích thật · làn ghi thật · đường nút xe thật tới một cổng xe giả). `onCamera` giả đứng
 * thay CẢ HAI tiến trình: tiến trình chính (`CameraDemandDispatch.fireForResult` áp thẳng, gọi lại ngay) và `:wake`
 * (broadcast có thứ tự, gọi lại SAU — ca [hoan lai ket qua]). Thứ khoá ở đây không đọc được từ chuỗi nguồn:
 *  • *"tắt camera"* trần + KHÔNG có camera theo yêu cầu nào mở ⇒ đúng một lệnh Camera 360 TẮT xuống cổng xe (≤ 2.92), câu
 *    trả lời là của nút ấy — KHÔNG *"✓ Tắt camera"* giả;
 *  • có camera đang mở ⇒ tắt nó, KHÔNG chạm Camera 360;
 *  • không tới được controller ⇒ nói *chưa làm được*, KHÔNG chạm Camera 360 (không biết camera nào đang mở);
 *  • vế sau của câu ghép chỉ chạy SAU khi vế camera có kết quả (làn ghi), và kết quả về hai lần không chạy đôi.
 */
class VoiceCameraFallbackDispatchTest {

    /** Cổng xe giả — chỉ ghi lại lệnh đã bắn. */
    private class Port : CarControlPort {
        val fired = ArrayList<String>()
        override fun toggle(id: String, on: Boolean): Boolean { fired += "toggle:$id:$on"; return true }
        override fun step(id: String, value: Int): Boolean { fired += "step:$id:$value"; return true }
        override fun cover(id: String, open: Boolean): Boolean { fired += "cover:$id:$open"; return true }
        override fun select(id: String, index: Int): Boolean { fired += "select:$id:$index"; return true }
        override fun press(id: String): Boolean { fired += "press:$id"; return true }
    }

    private class Rig(private val camera: (CameraDemand.Op, (Outcome) -> Unit) -> Unit) {
        val port = Port()
        val said = ArrayList<String>()
        val ops = ArrayList<CameraDemand.Op>()
        val dispatcher = VoiceDispatcher(
            control = { port },
            state = { HomeUiState() },
            media = { error("bài này không chạm tới nhạc") },
            appsByLabel = { emptyMap() },
            openApp = { false },
            openAppList = {},
            openSettings = {},
            onSwitchProfile = {},
            onListen = {},
            confirm = { _, _, no -> no() },
            say = { said += it },
            assignAppToSlot = { _, _ -> error("bài này không gắn app vào ô") },
            sendToApp = { error("bài này không giao việc cho app đích") },
            geocode = { error("bài này không tra toạ độ") },
            mediaPackage = { null },
            background = { it() },
            lang = Lang.VI,
            onCamera = { op, done -> ops += op; camera(op, done) },
        )
    }

    @AfterEach fun reset() {
        // Bảng trạng thái nút là bảng DÙNG CHUNG của tiến trình test — trả về mốc mặc định cho bài khác.
        ControlRegistry.byId("cam")?.let { ControlTileState.shared.setOn(it.id, false) }
        ControlRegistry.byId("readl")?.let { ControlTileState.shared.setOn(it.id, false) }
    }

    @Test fun `tat camera tran, khong co gi de tat - tat Camera 360 nhu 2_92`() {
        val r = Rig { _, done -> done(Outcome.NOTHING_TO_CLOSE) }
        r.dispatcher.submit("tắt camera")
        assertEquals(listOf<CameraDemand.Op>(CameraDemand.Op.CloseAll), r.ops, "hỏi camera theo yêu cầu TRƯỚC")
        assertEquals(listOf("toggle:cam:false"), r.port.fired, "rồi mới rơi về nút Camera 360 — đúng một lệnh TẮT")
        assertEquals(1, r.said.size, "${r.said}")
        assertTrue(r.said.single().startsWith("✓ Đã tắt camera 360"), "câu trả lời của nút ấy (≤ 2.92), không ✓ giả: ${r.said}")
    }

    @Test fun `tat camera tran, co camera dang mo - tat no, khong cham Camera 360`() {
        val r = Rig { _, done -> done(Outcome.CLOSED) }
        r.dispatcher.submit("tắt cam")
        assertEquals(emptyList<String>(), r.port.fired, "Camera 360 của xe KHÔNG bị đụng")
        assertEquals(listOf("✓ Đã tắt camera"), r.said)
    }

    @Test fun `khong toi duoc controller - noi chua lam duoc, khong cham Camera 360`() {
        val r = Rig { _, done -> done(Outcome.UNREACHABLE) }
        r.dispatcher.submit("tắt camera")
        assertEquals(emptyList<String>(), r.port.fired, "không biết camera nào đang mở ⇒ không đoán")
        assertEquals(listOf("✗ Tắt camera — chưa liên lạc được màn hình chính"), r.said)
    }

    @Test fun `tat dung mot camera khong mo - noi that, khong Camera 360`() {
        val r = Rig { _, done -> done(Outcome.NOTHING_TO_CLOSE) }
        r.dispatcher.submit("tắt cam sau")
        assertEquals(listOf<CameraDemand.Op>(CameraDemand.Op.Close(CameraWhich.REAR)), r.ops)
        assertEquals(emptyList<String>(), r.port.fired, "câu nêu một camera cụ thể không bao giờ chạm Camera 360")
        assertEquals(listOf("Camera sau đang không mở"), r.said)
    }

    @Test fun `cau mo noi dung chieu da xay ra`() {
        val opened = Rig { _, done -> done(Outcome.OPENED) }
        opened.dispatcher.submit("mở cam sau")
        assertEquals(listOf("✓ Đã mở camera sau"), opened.said)
        val closed = Rig { _, done -> done(Outcome.CLOSED) }
        closed.dispatcher.submit("mở cam sau")
        assertEquals(listOf("✓ Đã tắt camera sau"), closed.said, "nói câu mở lần hai ⇒ đã TẮT — nói đúng thế")
    }

    /** `:wake`: kết quả về SAU (broadcast có thứ tự) — vế sau của câu ghép CHỜ, không ghi xen vào; về hai lần ⇒ một lần. */
    @Test fun `hoan lai ket qua - ve sau cho, ket qua hai lan khong chay doi`() {
        var pending: ((Outcome) -> Unit)? = null
        val r = Rig { _, done -> pending = done }
        r.dispatcher.submit("tắt camera và bật đèn đọc")
        assertEquals(emptyList<String>(), r.port.fired, "vế đèn đọc CHƯA được chạy khi vế camera chưa có kết quả")
        pending!!(Outcome.NOTHING_TO_CLOSE)
        assertEquals(listOf("toggle:cam:false", "toggle:readl:true"), r.port.fired, "đúng thứ tự nói: Camera 360 rồi đèn đọc")
        pending!!(Outcome.NOTHING_TO_CLOSE)   // về muộn lần hai ⇒ bỏ
        assertEquals(listOf("toggle:cam:false", "toggle:readl:true"), r.port.fired, "không bắn đôi")
        assertEquals(2, r.said.size, "${r.said}")
    }
}
