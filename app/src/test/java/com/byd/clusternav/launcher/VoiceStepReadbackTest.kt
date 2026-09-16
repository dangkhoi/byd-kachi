package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceReply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R5 · ĐỌC LẠI GIÁ TRỊ THẬT + L7 · BỐ CỤC BẰNG GIỌNG — BÀI CHẠY THẬT ══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R5 · T10** và `kachi-voice-command.html` **L7**.
 *
 * ## Vì sao dựng [VoiceDispatcher] THẬT, không quét nguồn
 * Cùng lý do đã ghi ở KDoc [VoiceDispatcherSafetyTest]: thứ cần khoá là **hành vi** (*"câu trả lời nói con số
 * nào"*), mà con số thì không đọc ra được từ một chuỗi ký tự. Bài này dựng cầu thật với một cổng xe giả trả
 * đúng những gì một chiếc xe trả.
 *
 * [ĐO] bệnh nó khoá: tới 1.65 câu trả lời dựng từ **con số vừa gửi**, nên một chiếc xe kẹp 24 °C về 17 vẫn nghe
 * là *"✓ Đặt Nhiệt độ = 24"* — máy nói dối đúng cái ca người lái không tự kiểm được vì đang nhìn đường.
 */
class VoiceStepReadbackTest {

    /**
     * Câu KHỚP mong đợi, dựng từ chính [VoiceReply] chứ không chép tay chuỗi.
     *
     * Chép tay `"✓ Đặt Nhiệt độ = 24"` là ghim luôn cả cái đuôi *"— chưa kiểm trên xe"* (nút `temp` đang ở mức
     * bằng chứng thấp), tức bài canh sẽ đỏ vì một thứ nó **không** canh mỗi lần mức bằng chứng của một nút đổi.
     * Thứ cần khoá ở đây là *"nhánh đọc-lại trả về ĐÚNG câu của nhánh cũ"*, và cách nói điều đó là so với
     * [VoiceReply.done].
     */
    private val done24 = VoiceReply.done(VoiceIntent.Control("temp", 24))

    /**
     * Cổng xe giả: ghi lại lệnh đã bắn, và trả về giá trị *"xe đang báo"* theo một bảng do bài đặt.
     *
     * [reads] là một **hàng đợi** cho mỗi id, không phải một con số: ca quan trọng nhất của R5 là *"lượt đọc đầu
     * trả số CŨ, lượt sau trả số mới"* — một con số cố định không dựng lại được ca đó.
     */
    private class Port(private val reads: MutableMap<String, MutableList<Int?>>) : CarControlPort {
        val fired = ArrayList<String>()
        var readCount = 0
            private set

        override fun toggle(id: String, on: Boolean): Boolean { fired += "toggle:$id:$on"; return true }
        override fun step(id: String, value: Int): Boolean { fired += "step:$id:$value"; return true }
        override fun cover(id: String, open: Boolean): Boolean { fired += "cover:$id:$open"; return true }
        override fun select(id: String, index: Int): Boolean { fired += "select:$id:$index"; return true }
        override fun press(id: String): Boolean { fired += "press:$id"; return true }
        override fun readStep(id: String): Int? {
            readCount++
            val q = reads[id] ?: return null
            return if (q.size > 1) q.removeAt(0) else q.firstOrNull()
        }
    }

    private class Rig(reads: Map<String, List<Int?>> = emptyMap()) {
        val port = Port(reads.mapValues { it.value.toMutableList() }.toMutableMap())
        val said = ArrayList<String>()
        val presets = ArrayList<LayoutPreset>()

        /** `false` ⇒ dựng cầu KHÔNG nối đường bố cục (ca của ô *"Gõ lệnh chữ"* khi màn chính chưa dựng). */
        fun dispatcher(layoutWired: Boolean = true) = VoiceDispatcher(
            control = { port },
            state = { HomeUiState(profiles = listOf("Mặc định")) },
            media = { error("bài này không chạm tới nhạc") },
            appsByLabel = { emptyMap() },
            openApp = { false },
            openAppList = {},
            openSettings = {},
            onSwitchProfile = {},
            onListen = {},
            confirm = { _, _, n -> n() },
            say = { said += it },
            assignAppToSlot = { _, _ -> error("bài này không gắn app vào ô") },
            onLayout = { p -> if (layoutWired) { presets += p; true } else false },
            sendToApp = { error("bài này không giao việc cho app đích") },
            geocode = { error("bài này không tra toạ độ") },
            mediaPackage = { null },
            // Đọc lại sau khi lệch chạy trên luồng NỀN; bài cần kết quả tất định nên chạy thẳng.
            background = { it() },
        )
    }

    // ══ R5 · ba nhánh của lượt đọc lại ════════════════════════════════════════════════════════════════════

    /** Xe không đọc được (off-car, máy ảo, trim không provision) ⇒ giữ nguyên câu cũ, **không bịa số**. */
    @Test
    fun `khong doc duoc thi giu nguyen cau cu`() {
        val r = Rig()
        r.dispatcher().submit("đặt nhiệt độ 24")
        assertEquals(listOf("step:temp:24"), r.port.fired)
        assertEquals(listOf(done24), r.said)
    }

    /** Xe báo ĐÚNG số vừa gửi ⇒ câu y hệt bản cũ: không thêm một chữ nào cho một kết quả khớp. */
    @Test
    fun `xe bao dung so vua gui thi cau tra loi khong doi`() {
        val r = Rig(mapOf("temp" to listOf(24)))
        r.dispatcher().submit("đặt nhiệt độ 24")
        assertEquals(listOf(done24), r.said)
        assertEquals(1, r.port.readCount, "khớp ngay thì KHÔNG được đọc lần thứ hai (một lượt chờ 300 ms vô ích)")
    }

    /**
     * Xe báo một số KHÁC và vẫn khác sau lượt đọc lại ⇒ nói ra **cả hai** con số.
     *
     * Không sửa câu thành *"đã đặt 23"* (giấu mất việc vừa xảy ra), cũng không đổi thành ✗ (lệnh KHÔNG hỏng —
     * nó được nhận, xe chỉ đang ở một con số khác). Xem KDoc `VoiceReply.doneActual`.
     */
    @Test
    fun `xe bao so khac thi noi ca hai con so`() {
        val r = Rig(mapOf("temp" to listOf(23)))
        r.dispatcher().submit("đặt nhiệt độ 24")
        assertEquals(1, r.said.size, "một lệnh ⇒ đúng MỘT câu trả lời, không phải hai. Thấy: ${r.said}")
        val line = r.said.single()
        assertTrue(line.startsWith("✓"), "lệnh được nhận ⇒ vẫn là ✓, không phải ✗: $line")
        assertTrue(line.contains("24") && line.contains("23"), "phải nói CẢ số đã gửi lẫn số xe báo: $line")
        assertEquals(2, r.port.readCount, "lệch ⇒ phải đọc lại đúng MỘT lần nữa, không nhiều hơn")
    }

    /**
     * ⚠ Ca quan trọng nhất của R5: **xe chưa kịp áp**, lượt đọc đầu trả số CŨ rồi lượt sau trả số mới.
     *
     * Không có lượt đọc lại thì đây là một câu trả lời SAI trên một lệnh hoàn toàn thành công — và nó sai đúng
     * theo kiểu làm người ta thôi tin cả tính năng.
     */
    @Test
    fun `xe cham mot nhip thi luot doc lai cuu duoc cau tra loi`() {
        val r = Rig(mapOf("temp" to listOf(22, 24)))
        r.dispatcher().submit("đặt nhiệt độ 24")
        assertEquals(listOf(done24), r.said, "đọc lại thấy khớp ⇒ phải nói câu khớp")
        assertEquals(24, ControlTileState.shared.value(ControlRegistry.byId("temp")!!),
            "bảng trạng thái dùng chung phải mang số THẬT — hai bề mặt nói cùng một điều về một cái xe")
    }

    /** Lệnh TOGGLE không có đường đọc lại: R5 chỉ áp cho STEP, và không được gọi `readStep` cho loại khác. */
    @Test
    fun `nut khong phai STEP thi khong doc lai gi`() {
        val r = Rig(mapOf("readl" to listOf(0)))
        r.dispatcher().submit("bật đèn đọc")
        assertEquals(0, r.port.readCount, "TOGGLE mà cũng đi hỏi giá trị là một lượt HAL thừa mỗi lần bấm")
        assertEquals(listOf(VoiceReply.done(VoiceIntent.Control("readl", 1))), r.said)
    }

    // ══ L7 · bố cục bằng giọng nói ════════════════════════════════════════════════════════════════════════

    @Test
    fun `bo cuc bang giong noi di dung duong da noi`() {
        val r = Rig()
        r.dispatcher().submit("đổi sang bố cục 2 cột")
        assertEquals(listOf(LayoutPreset.TWO_COL), r.presets, "phải gọi ĐÚNG lambda bố cục một lần")
        assertEquals(1, r.said.size)
        assertTrue(r.said.single().startsWith("✓"), "đổi được thì báo ✓: ${r.said}")
        assertTrue(r.said.single().contains(LayoutPreset.TWO_COL.label), "câu trả lời phải đọc NHÃN của bố cục")
    }

    /**
     * Bề mặt không nối được đường bố cục ⇒ **nói ra**, không báo ✓ cho một việc chưa xảy ra.
     *
     * Đây là mặc định của `VoiceDispatcher.onLayout` (trả `false`) — cùng luật với `assignAppToSlot`.
     */
    @Test
    fun `be mat khong noi duoc duong bo cuc thi noi thang`() {
        val r = Rig()
        r.dispatcher(layoutWired = false).submit("bố cục 4 ô")
        assertEquals(emptyList<LayoutPreset>(), r.presets)
        assertEquals(1, r.said.size)
        assertFalse(r.said.single().startsWith("✓"), "chưa đổi được mà báo ✓ là nói dối: ${r.said}")
    }
}
