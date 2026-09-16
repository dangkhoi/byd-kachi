package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceIntent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.byd.clusternav.launcher.voice.VoiceRiskTable

/**
 * ═══ V1 · CỔNG XÁC NHẬN CỦA CÂU GHÉP — BÀI CHẠY THẬT, KHÔNG PHẢI QUÉT NGUỒN ════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R3 · R4 · §7 OQ4 (soát 2026-09-14 chốt theo hướng an toàn).
 *
 * ## Vì sao bài này dựng [VoiceDispatcher] THẬT trong khi các bài `:app` khác chỉ quét chuỗi nguồn
 * Thứ cần khoá ở đây là **thứ tự thời gian** (*"vế sau có chạy trước khi vế trước được đồng ý không"*), mà thứ tự
 * thì không đọc ra được từ một chuỗi ký tự — `SettingsScreenWiringContractTest` quét nguồn vì nó nói về **dây
 * nối**, còn đây nói về **hành vi**. Dựng được thật vì lớp này không chạm `android.*` trên đường đi của một nút
 * xe: `ControlTileState` là ConcurrentHashMap thuần, `actByKind` ở `:core`, và các lambda còn lại do bài tự cấp.
 *
 * [ĐO] bệnh nó khoá: bản đầu bắn **mọi** vế ngay lập tức và chỉ *hỏi thêm* cho vế CONFIRM ⇒ câu *"mở khoá cửa rồi
 * mở hết kính"* hạ hết kính **trong lúc** hộp hỏi của vế mở khoá còn đang mở.
 */
class VoiceDispatcherSafetyTest {

    /** Cổng xe giả — chỉ ghi lại lệnh nào đã bắn (không có gì để "thật" off-car). */
    private class Port : CarControlPort {
        val fired = ArrayList<String>()
        override fun toggle(id: String, on: Boolean): Boolean { fired += "toggle:$id:$on"; return true }
        override fun step(id: String, value: Int): Boolean { fired += "step:$id:$value"; return true }
        override fun cover(id: String, open: Boolean): Boolean { fired += "cover:$id:$open"; return true }
        override fun select(id: String, index: Int): Boolean { fired += "select:$id:$index"; return true }
        override fun press(id: String): Boolean { fired += "press:$id"; return true }
    }

    /** Hộp hỏi lại giả: **không** trả lời ngay — bài tự quyết định lúc nào bấm Đồng ý / Huỷ. */
    private class Ask {
        val asked = ArrayList<String>()
        private val yes = ArrayList<() -> Unit>()
        private val no = ArrayList<() -> Unit>()
        fun onConfirm(q: String, y: () -> Unit, n: () -> Unit) { asked += q; yes += y; no += n }
        fun agreeLast() = yes.removeAt(yes.size - 1).invoke()
        fun cancelLast() = no.removeAt(no.size - 1).invoke()
    }

    private class Rig {
        val port = Port()
        val ask = Ask()
        val said = ArrayList<String>()
        val profiles = ArrayList<String>()

        /** Số lần câu lệnh yêu cầu mở một phiên NGHE (V1 pha nghe · `launcher_voice`). */
        var listens = 0
        val dispatcher = VoiceDispatcher(
            control = { port },
            state = { HomeUiState(profiles = listOf("Mặc định", "Vợ")) },
            media = { error("bài này không chạm tới nhạc") },
            appsByLabel = { emptyMap() },
            openApp = { false },
            openAppList = {},
            openSettings = {},
            onSwitchProfile = { profiles += it },
            onListen = { listens++ },
            confirm = { q, y, n -> ask.onConfirm(q, y, n) },
            // ⚠ V3 · R7 (1.66): mặc định **không hỏi gì cả** (owner 2026-09-16). Bài này canh CƠ CHẾ của
            // cổng hỏi-lại, nên nó bật MỌI mã hỏi-được — không thì mọi ca dưới đây chạy thẳng và bài
            // trở thành một bài canh cho chính cái mặc định, không phải cho cổng.
            confirmIds = { VoiceRiskTable.askableIds().toSet() },
            say = { said += it },
            // V1.1 — bốn cổng mới; bài này không chạm tới chúng, nên chúng **nổ** nếu bị chạm. Một lambda trả
            // giá trị giả sẽ làm bài xanh trong khi một ý định đi nhầm đường.
            assignAppToSlot = { _, _ -> error("bài này không gắn app vào ô") },
            sendToApp = { error("bài này không giao việc cho app đích") },
            geocode = { error("bài này không tra toạ độ") },
            mediaPackage = { null },
            // Gói lệnh chạy NGAY trên thread gọi — bài cần kết quả tất định, không cần đo tính đa luồng.
            background = { it() },
        )
    }

    // ══ 1 · Vế CONFIRM DỪNG cả chuỗi ══════════════════════════════════════════════════════════════════════

    @Test
    fun `ve sau KHONG chay truoc khi ve CONFIRM duoc dong y`() {
        val r = Rig()
        r.dispatcher.submit("mở khoá cửa và bật đèn đọc")

        assertEquals(1, r.ask.asked.size, "phải hỏi đúng một lần, cho vế mở khoá")
        assertEquals(emptyList<String>(), r.port.fired, "CHƯA đồng ý mà đã có lệnh bắn — cổng xác nhận vô nghĩa")

        r.ask.agreeLast()
        assertEquals(listOf("press:door", "toggle:readl:true"), r.port.fired,
            "đồng ý rồi thì chạy vế CONFIRM TRƯỚC, xong mới tới vế sau — đúng thứ tự nói")
    }

    @Test
    fun `huy thi ca chuoi dung, va noi ro con may viec khong chay`() {
        val r = Rig()
        r.dispatcher.submit("mở khoá cửa và bật đèn đọc")
        r.ask.cancelLast()

        assertEquals(emptyList<String>(), r.port.fired, "huỷ mà vẫn bắn là mất trắng cổng an toàn")
        assertTrue(r.said.any { it.contains("huỷ") && it.contains("1") },
            "phải nói ra là đã huỷ và còn 1 việc không chạy; im lặng ⇒ người dùng tưởng nửa sau đã chạy. Thấy: ${r.said}")
    }

    /** Hai vế CONFIRM liên tiếp: hộp thứ hai chỉ được mở SAU khi hộp thứ nhất được trả lời. */
    @Test
    fun `hai ve CONFIRM hoi lan luot, khong chong hop`() {
        val r = Rig()
        r.dispatcher.submit("mở khoá cửa và đổi sang hồ sơ Vợ")
        assertEquals(1, r.ask.asked.size, "hai hộp hỏi chồng nhau thì người lái không biết đang trả lời cho vế nào")

        r.ask.agreeLast()
        assertEquals(2, r.ask.asked.size, "trả lời xong vế đầu thì mới tới vế sau")
        assertEquals(emptyList<String>(), r.profiles, "hồ sơ chưa được đổi khi chưa đồng ý")
        r.ask.agreeLast()
        assertEquals(listOf("Vợ"), r.profiles)
    }

    // ══ 2 · Đường thường vẫn chạy thẳng, không hỏi ════════════════════════════════════════════════════════

    @Test
    fun `viec khong nguy hiem chay thang, khong hoi lai`() {
        val r = Rig()
        r.dispatcher.submit("bật đèn đọc và đặt nhiệt độ hai lăm")
        assertEquals(emptyList<String>(), r.ask.asked, "đừng hỏi lại những việc nói ngược lại là xong")
        assertEquals(listOf("toggle:readl:true", "step:temp:25"), r.port.fired)
    }

    /** Lệnh tương đối cộng vào **mức đang dùng** của bảng dùng chung, không vào mốc mặc định của registry. */
    @Test
    fun `tang giam cong vao muc dang dung`() {
        val r = Rig()
        val def = ControlRegistry.byId("fan")!!
        ControlTileState.shared.setValue("fan", 6)
        r.dispatcher.submit("tăng gió")
        assertEquals(listOf("step:fan:${def.clamp(7)}"), r.port.fired)
        ControlTileState.shared.setValue("fan", def.value)   // trả bảng dùng chung về mốc cũ
    }

    // ══ 3 · Không có đường nào bắn mà bỏ qua cổng ═════════════════════════════════════════════════════════

    /**
     * [VoiceDispatcher.preview] là cửa *"đã hiểu là…"* của màn thử — nó **chỉ được phân tích**. Một ngày nào đó ai
     * đó cho nó chạy luôn cho tiện thì mọi câu nguy hiểm sẽ bắn **trước cả khi** người dùng bấm "Chạy câu lệnh".
     */
    @Test
    fun `preview khong thi hanh bat cu thu gi`() {
        val r = Rig()
        val intents = r.dispatcher.preview("mở khoá cửa và bật đèn đọc")
        assertEquals(2, intents.size)
        assertTrue(intents.first() is VoiceIntent.Control)
        assertEquals(emptyList<String>(), r.port.fired)
        assertEquals(emptyList<String>(), r.ask.asked)
        assertEquals(emptyList<String>(), r.said)
    }

    // ══ 4 · V1 pha NGHE — `launcher_voice` là một việc THẬT, không phải một mã trơ ═════════════════════════

    /**
     * Nói *"nói với xe"* phải mở một phiên nghe.
     *
     * Bài này chạy [VoiceDispatcher] **thật** (lớp này không chạm `android.*` trên đường đi của một hành động
     * launcher), nên nó canh đúng thứ mà một phép quét nguồn không canh được: mã `launcher_voice` đi tới đúng
     * lambda, đúng **một** lần, và không rơi vào nhánh `else -> failed` như một mã lạ.
     */
    @Test
    fun `noi voi xe mo dung mot phien nghe`() {
        val r = Rig()
        r.dispatcher.submit("nói với xe")
        assertEquals(1, r.listens, "câu `nói với xe` phải mở đúng một phiên nghe")
        assertTrue(r.said.isNotEmpty(), "phải nói lại là đã làm gì")
    }

    /** Và nó nối được vào câu ghép như mọi việc khác — thứ tự nói là thứ tự làm. */
    @Test
    fun `noi voi xe ghep duoc vao cau ghep`() {
        val r = Rig()
        r.dispatcher.submit("bật đèn đọc rồi nói với xe")
        assertEquals(1, r.listens)
        assertTrue(r.port.fired.any { it.contains("readl") }, "vế đầu vẫn phải chạy: ${r.port.fired}")
    }
}
