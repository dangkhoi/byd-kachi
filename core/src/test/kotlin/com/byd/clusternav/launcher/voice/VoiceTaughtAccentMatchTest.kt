package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2A · VOICE-TAUGHT-ACCENT-MATCH — tên GIỌNG một âm tiết chỉ khớp chữ MANG dấu khi đúng cách viết đã dạy ═══════
 *
 * Thiết kế đúng chữ OQ2 spec `kachi-293-voice.html` (spec `kachi-293-wave2a.html` §4.7). Khoá lỗi [ĐO off-car 06/10, spec
 * voice-app-names §9 F6]: dạy «nay» cho Drive ⇒ *"mở cái này"* mở Drive (đồng hình sau khi bỏ dấu). Luật = luật
 * [VoiceHomograph] của liên từ: chữ thô KHÔNG dấu ⇒ hành vi cũ (gõ không dấu, nhật ký); chữ MANG dấu ⇒ chỉ cách viết đã dạy.
 * Tên GÕ và tên nhiều từ không bị ràng. Gói `com.example.*` là bộ thử; «cơm» = chuỗi mô hình in cho *"Chrome"* [ĐO máy ảo TTS
 * 06/10 — KDoc `TeachSample.MIN_LETTERS`].
 */
class VoiceTaughtAccentMatchTest {

    private val drive = "com.example.drive"
    private val chrome = "com.example.chrome"
    private val labels = listOf("Drive" to drive, "Chrome" to chrome)

    private fun aliases(vararg taught: TaughtName): List<VoiceAppAlias> =
        VoiceAppIndex.build(labels, labels.map { it.second }.toSet(), taught.toList()).aliases

    private fun parse(text: String, aliases: List<VoiceAppAlias>) =
        VoiceIntentParser.parse(text, apps = labels.map { it.first }, aliases = aliases)

    private fun opens(text: String, aliases: List<VoiceAppAlias>, app: String) =
        parse(text, aliases).any { it is VoiceIntent.OpenApp && it.appName == app }

    @Test
    fun `nay day cho Drive - mo cai NAY khong con mo Drive, goi ten van mo`() {
        val a = aliases(TaughtName(drive, TaughtSource.SPEECH, "nay", "Drive"))
        assertTrue(!opens("mở cái này", a, "Drive"), "«này» mang dấu khác cách viết đã dạy «nay» ⇒ không phải tên: ${parse("mở cái này", a)}")
        assertTrue(opens("mở nay", a, "Drive"))
        assertTrue(opens("MỞ NAY", a, "Drive"), "mô hình in HOA — so theo chữ thường")
        assertEquals(VoiceIntent.OpenApp("Drive", 2), parse("đưa nay vào ô số hai", a).single(), "câu có ô vẫn chạy")
        assertTrue(opens("mo cai nay", a, "Drive"), "gõ KHÔNG dấu ⇒ không dữ liệu để tách ⇒ hành vi cũ (khớp)")
    }

    @Test
    fun `ten co dau - dung cach viet thi khop, lech thanh thi khong, khong dau thi khop`() {
        val a = aliases(TaughtName(chrome, TaughtSource.SPEECH, "cơm", "Chrome"))
        assertTrue(opens("mở cơm", a, "Chrome"))
        assertTrue(!opens("mở còm", a, "Chrome"), "«còm» ≠ «cơm» — chữ khác, chỉ trùng sau khi bỏ dấu")
        assertTrue(opens("mo com", a, "Chrome"), "gõ không dấu")
    }

    @Test
    fun `ten GO va ten nhieu tu khong bi rang (dung OQ2)`() {
        val typed = aliases(TaughtName(drive, TaughtSource.TYPED, "nay", "Drive"))
        assertTrue(opens("mở cái này", typed, "Drive"), "tên GÕ: chữ gõ không bảo đảm dấu ⇒ không ràng (hành vi 2.92)")
        val multi = aliases(TaughtName(chrome, TaughtSource.SPEECH, "cửa rôm", "Chrome"))
        assertTrue(opens("mở cửa rôm", multi, "Chrome"))
        assertTrue(opens("mở cửa rồm", multi, "Chrome"), "tên nhiều từ khớp nguyên dãy — không ràng dấu (đồng hình một chữ không xảy ra)")
    }

    @Test
    fun `cach viet bat buoc - chi ten GIONG mot am tiet`() {
        val speech = VoiceAppIndex.aliasesOf(mapOf("Drive" to drive), listOf(TaughtName(drive, TaughtSource.SPEECH, "Nây", "Drive"))).single()
        assertEquals("nây", VoiceHomograph.taughtSpelling(speech), "chữ thường, NFC")
        val typed = speech.copy(source = TaughtSource.TYPED)
        assertNull(VoiceHomograph.taughtSpelling(typed))
        val two = VoiceAppIndex.aliasesOf(mapOf("Drive" to drive), listOf(TaughtName(drive, TaughtSource.SPEECH, "nây đi", "Drive"))).single()
        assertNull(VoiceHomograph.taughtSpelling(two))
        val tok = VoiceLexicon.tokenize("này").single()
        assertTrue(VoiceHomograph.spelledOk(tok, null), "không ràng")
        assertTrue(!VoiceHomograph.spelledOk(tok, "nay"))
        assertTrue(VoiceHomograph.spelledOk(VoiceLexicon.tokenize("nay").single(), "này"), "chữ thô không dấu ⇒ không dữ liệu ⇒ khớp")
    }

    /**
     * Senior review wave 2A Pass 1 [P3] — hai quy ước ĐẶT dấu thanh (*"thuỷ"* ↔ *"thủy"*, *"hoà"* ↔ *"hòa"*) là CÙNG một chữ nhưng
     * hai chuỗi Unicode khác nhau kể cả sau NFC: so chuỗi thẳng làm tên GIỌNG đã dạy trượt khi nguồn chữ đặt dấu kiểu kia (2.92
     * khớp — hồi quy). Khác THANH (*"thúy"*) hay khác dấu (*"này"*) vẫn không khớp — luật đồng hình OQ2 giữ nguyên.
     */
    @Test
    fun `hai kieu dat dau thanh la cung mot chu - khac thanh van khong khop`() {
        val one = { s: String -> VoiceLexicon.tokenize(s).single() }
        assertTrue(VoiceHomograph.spelledOk(one("hòa"), "hoà"), "kiểu cũ «hòa» = kiểu mới «hoà»")
        assertTrue(VoiceHomograph.spelledOk(one("thủy"), "thuỷ"))
        assertTrue(VoiceHomograph.spelledOk(one("KHỎE"), "khoẻ"), "chữ HOA + kiểu đặt dấu khác")
        assertTrue(!VoiceHomograph.spelledOk(one("hóa"), "hoà"), "sắc ≠ huyền")
        assertTrue(!VoiceHomograph.spelledOk(one("thúy"), "thuỷ"), "sắc ≠ hỏi")
        assertTrue(!VoiceHomograph.spelledOk(one("cơm"), "còm"), "khác nguyên âm (ơ ≠ o)")
        val a = aliases(TaughtName(chrome, TaughtSource.SPEECH, "thuỷ", "Chrome"))
        assertTrue(opens("mở thủy", a, "Chrome"), "dạy «thuỷ», nguồn chữ in «thủy» ⇒ vẫn mở: ${parse("mở thủy", a)}")
        assertTrue(opens("mở thuỷ", a, "Chrome"))
    }
}
