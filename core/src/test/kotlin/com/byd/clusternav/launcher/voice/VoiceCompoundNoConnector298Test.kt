package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * ═══ 2.98 R11 `VOICE-COMPOUND-NO-CONNECTOR` — câu ghép KHÔNG liên từ không mất vế (spec `kachi-298-plan.html` §3) ═══════════
 *
 * [ĐO off-car 09/10] *"tắt sưởi ghế phụ tắt gió tự động"* ⇒ chỉ `seath_r` tắt (*"động"* bỏ dấu = *"đóng"* bị coi là ranh giới) ·
 * *"sưởi ghế phụ bật gió tự động"* ⇒ `seath_r=1`, vế sau mất (vế đầu không động từ bị bỏ rơi). Luật ở [VoiceClauseSplit].
 * Gỡ bản vá ⇒ các bài dưới đây đỏ (bài cặp registry: HEAD 25dd8a8 hụt 86/506 cặp *"tắt A tắt B"* + 221/242 cặp *"A bật B"*).
 */
class VoiceCompoundNoConnector298Test {

    private fun p(s: String) = VoiceIntentParser.parse(s)
    private fun c(id: String, v: Int) = VoiceIntent.Control(id, v)

    @Test
    fun `cau owner 1 - tat hai nut khong lien tu`() {
        val want = listOf(c("seath_r", 0), c("ac_auto", 0))
        assertEquals(want, p("tắt sưởi ghế phụ tắt gió tự động"))
        assertEquals(want, p("TẮT SƯỞI GHẾ PHỤ TẮT GIÓ TỰ ĐỘNG".lowercase()))   // mô hình in HOA có dấu
        assertEquals(want, p("tat suoi ghe phu tat gio tu dong"), "không dấu: «dong» nằm trong lòng cụm «gió tự động» ⇒ không phải ranh giới")
        assertEquals(p("tắt sưởi ghế phụ rồi tắt gió tự động"), p("tắt sưởi ghế phụ tắt gió tự động"), "không liên từ = có «rồi»")
    }

    @Test
    fun `cau owner 2 - ve dau khong dong tu`() {
        val want = listOf(c("seath_r", 1), c("ac_auto", 1))
        assertEquals(want, p("sưởi ghế phụ bật gió tự động"))
        assertEquals(want, p("SƯỞI GHẾ PHỤ BẬT GIÓ TỰ ĐỘNG".lowercase()))
        assertEquals(want, p("suoi ghe phu bat gio tu dong"))
        assertEquals(p("sưởi ghế phụ rồi bật gió tự động"), p("sưởi ghế phụ bật gió tự động"))
    }

    /** «động» mang dấu không phải «đóng»; «tự động» không bao giờ là ranh giới vế. */
    @Test
    fun `tu dong khong phai ranh gioi`() {
        val terms = VoiceGrammar.terms(emptyList(), emptyList())
        listOf("bật điều hòa tự động", "tắt gió tự động", "bat dieu hoa tu dong").forEach { s ->
            assertEquals(listOf(0), VoiceClauseSplit.starts(VoiceLexicon.tokenize(s), terms), s)
            assertEquals(1, p(s).size, s)
        }
        assertEquals(listOf(c("ac_auto", 1), c("trunk", 1)), p("bật điều hòa tự động mở cốp"))
    }

    /**
     * Sinh từ bộ đăng ký (không câu mẫu): với MỌI cặp nút bật/tắt mà câu CÓ «rồi» ra đúng hai lệnh, câu KHÔNG liên từ phải ra y hệt.
     * Khoá luôn ca động từ đứng sát nhau (*"tắt lấy gió trong"* là một vế, không cắt ra *"tắt"* trần).
     */
    @Test
    fun `moi cap nut - khong lien tu bang co roi`() {
        val tog = ControlRegistry.ALL.filter { it.kind == ControlKind.TOGGLE }
        var pairs = 0
        var leads = 0
        val miss = ArrayList<String>()
        for (a in tog) for (b in tog) {
            if (a.id == b.id) continue
            val la = a.label.lowercase()
            val lb = b.label.lowercase()
            val off = listOf(c(a.id, 0), c(b.id, 0))
            if (p("tắt $la rồi tắt $lb") == off) {
                pairs++
                if (p("tắt $la tắt $lb") != off) miss += "tắt $la tắt $lb"
            }
            val on = listOf(c(a.id, 1), c(b.id, 1))
            if (p(la) == listOf(c(a.id, 1)) && p("$la rồi bật $lb") == on) {
                leads++
                if (p("$la bật $lb") != on) miss += "$la bật $lb"
            }
        }
        assertEquals(emptyList<String>(), miss)
        assertFalse(pairs < 400 || leads < 200, "bài không được tự rỗng: pairs=$pairs leads=$leads")
    }

    /** Cổng an toàn: không tách khi vế sau không đối tượng, khi vế đầu không phải lệnh GHI, hay khi cụm danh từ đứng giữa câu. */
    @Test
    fun `khong tach bua`() {
        assertEquals(1, p("điều hòa tắt đi").size, "vế sau không đối tượng ⇒ như cũ")
        assertFalse(p("kính lái mở ra").any { it is VoiceIntent.Control }, "tên bộ phận chuyển động trần không ra lệnh")
        assertEquals(listOf(c("ac_auto", 0)), p("tắt điều hòa đèn đọc"), "cụm danh từ giữa câu: không đoán «bật đèn đọc»")
        assertEquals(1, p("tắt lấy gió trong").size)
        assertEquals(listOf(c("windows_all", 1)), p("mở tất cả kính"))
        // Câu MIX có động từ sẵn ở đầu mỗi vế: như cũ.
        assertEquals(listOf(c("win_lf", 1), c("recirc", 0), c("ac_auto", 0)), p("hạ kính lấy gió ngoài tắt máy lạnh"))
    }
}
