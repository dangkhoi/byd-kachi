package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.91 VOICE-APP-NAMES · C2 — một lượt nói dạy ⇒ phần TÊN (spec §4.2 bảng 5 bước). Câu vào là chữ mô hình in (chữ
 * thường có dấu, như `VoiceRecognizer.decode` trả). Chuỗi *"nep leag"* là chuỗi THẬT mô hình in cho *"netflix"* trên
 * xe 27/09 [SUY — log xe cũ, `PROJECT-BACKLOG.md` mục PHASE-2].
 */
class TeachSampleTest {

    private fun name(heard: String): String {
        val r = TeachSample.normalize(heard)
        return (r as? TeachSample.Sample)?.accented ?: "REJECT:${(r as TeachSample.Rejected).why}"
    }

    @Test
    fun `cat dong tu mo bat dua va menh de o`() {
        assertEquals("nep leag", name("mở nep leag"))
        assertEquals("nep leag", name("bật nep leag"))
        assertEquals("nep leag", name("đưa nep leag vào ô số hai"))
        assertEquals("nep leag", name("mở nep leag vào ô số một"))
    }

    @Test
    fun `bo lich su dau cuoi tieng dem va tieng am u`() {
        assertEquals("nep leag", name("ờ mở giúp tôi nep leag vào ô số hai nhé"))
        assertEquals("nep leag", name("làm ơn mở nep leag"))
        assertEquals("nep leag", name("kachi ơi mở nep leag đi"))
    }

    @Test
    fun `bo nhan ung dung ngay sau dong tu`() {
        assertEquals("nep leag", name("mở ứng dụng nep leag"))
        // Chỉ nói "ứng dụng" ⇒ giữ lại để cổng an toàn chặn (đó là một lệnh launcher, không phải một cái tên).
        assertEquals("ứng dụng", name("mở ứng dụng"))
    }

    @Test
    fun `giu dau va chu thuong dung chu mo hinh in`() {
        val r = TeachSample.normalize("MỞ NÉT LÍCH") as TeachSample.Sample
        assertEquals("nét lích", r.accented)
        assertEquals("net lich", r.norm)
        assertEquals(listOf("net", "lich"), r.words)
    }

    @Test
    fun `hinh dang ten mot den bon tu it nhat ba chu cai`() {
        assertEquals("REJECT:EMPTY", name(""))
        assertEquals("REJECT:EMPTY", name("ừ ừm"))
        assertEquals("REJECT:EMPTY", name("mở"))
        // Sàn TUYỆT ĐỐI 3 chữ cái (quyết định điều phối 2.91, spec §7 OQ4): ≤ 2 chữ cái luôn loại.
        assertEquals("REJECT:TOO_SHORT", name("mở nè"))
        assertEquals("REJECT:TOO_SHORT", name("mở đ"))   // [ĐO máy ảo 06/10] "mở Drive" ở 185 wpm ⇒ «mở đ»
        assertEquals("REJECT:TOO_LONG", name("mở một hai ba bốn năm"))
        assertEquals("tóp tóp", name("mở tóp tóp"))
    }

    @Test
    fun `ba chu cai qua hinh dang nhung la ten NGAN - can lap lai o cong`() {
        // [ĐO máy ảo TTS 06/10, spec §9 F1] nhãn Anh đọc trần ra MỘT âm tiết 3 chữ cái — trước đây bị loại ở đây.
        listOf("mở cơm" to "cơm", "mở ghe" to "ghe", "mở nay" to "nay", "mở thể" to "thể").forEach { (heard, want) ->
            val s = TeachSample.normalize(heard) as TeachSample.Sample
            assertEquals(want, s.accented)
            assertEquals(3, s.letters)
            assertTrue(s.short, "«$want» dưới sàn một-lượt ⇒ cổng đòi ≥ ${TeachGuard.MIN_TAKES_SHORT} lượt giống hệt")
        }
        val long = TeachSample.normalize("mở nep leag") as TeachSample.Sample
        assertEquals(7, long.letters)
        assertFalse(long.short)
        // Dấu cách không tính là chữ cái: "a b" = 2 chữ cái ⇒ loại.
        assertEquals(TeachSample.Reject.TOO_SHORT, (TeachSample.shape("a b") as TeachSample.Rejected).why)
    }

    @Test
    fun `ten go tay chi kiem hinh dang khong cat dong tu`() {
        assertEquals("quản lý tệp", (TeachSample.shape("Quản lý tệp") as TeachSample.Sample).accented)
        assertEquals(TeachSample.Reject.TOO_SHORT, (TeachSample.shape("ab") as TeachSample.Rejected).why)
        assertEquals("abc", (TeachSample.shape("abc") as TeachSample.Sample).accented)
    }

    /**
     * 2.93 VOICE-TEACH-CONTEXT — [ĐO máy ảo 06/10, spec voice-app-names §9 F3] câu CÓ Ô đổi chuỗi tên (`google đy` ·
     * `cờ rôm`, trong khi câu *"mở …"* ra `google đ` · `cửa rôm`). Hộp dạy cho lượt [TeachSample.SLOT_TAKE] nói câu có ô;
     * bài này khoá: (1) lượt ấy nằm TRONG số lượt bắt buộc (không ai lưu mà bỏ sót nó), (2) cắt câu có ô ra đúng chuỗi
     * mô hình in cho TÊN ở ngữ cảnh ấy — khác chuỗi của câu *"mở …"* ⇒ thành một tên nữa.
     */
    @Test
    fun `luot thu hai la cau co o va cat ra dung chuoi ten cua ngu canh ay`() {
        assertEquals(TeachSample.Prompt.PLAIN, TeachSample.promptFor(1))
        assertEquals(TeachSample.Prompt.SLOT, TeachSample.promptFor(TeachSample.SLOT_TAKE))
        assertEquals(TeachSample.Prompt.PLAIN, TeachSample.promptFor(TeachSample.SLOT_TAKE + 1))
        assertTrue(TeachSample.SLOT_TAKE in 1..TeachSample.MIN_SPOKEN_TAKES, "lượt câu có ô phải nằm trong số lượt BẮT BUỘC")
        // Chuỗi THẬT máy ảo 06/10 (diagnostics voice-app-names §9 / spec §9 F3).
        assertEquals("google đy", name("đưa google đy vào ô số hai"))
        assertEquals("cờ rôm", name("đưa cờ rôm vào ô số hai"))
        assertEquals("google đ", name("mở google đ"))
        val plain = TeachSample.normalize("mở google đ") as TeachSample.Sample
        val slot = TeachSample.normalize("đưa google đy vào ô số hai") as TeachSample.Sample
        assertTrue(plain.norm != slot.norm, "hai ngữ cảnh ra hai chuỗi ⇒ hộp dạy giữ HAI mẫu (gộp theo norm)")
    }

    @Test
    fun `chuoi qua dai bi loai du chi mot den bon tu`() {
        // Trần ký tự chặn chuỗi rác/khổng lồ đi vào qua ô gõ · tệp hồ sơ nhập · intent TEACH_APP (dữ liệu ngoài).
        val huge = "a".repeat(TeachSample.MAX_CHARS + 1)
        assertEquals(TeachSample.Reject.TOO_LONG, (TeachSample.shape(huge) as TeachSample.Rejected).why)
        assertEquals(TeachSample.Reject.TOO_LONG, (TeachSample.normalize("mở $huge") as TeachSample.Rejected).why)
        assertTrue(TeachSample.shape("a".repeat(TeachSample.MAX_CHARS)) is TeachSample.Sample)
    }
}
