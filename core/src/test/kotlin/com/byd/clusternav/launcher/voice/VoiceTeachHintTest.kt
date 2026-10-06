package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** 2.91 VOICE-APP-NAMES · C9 — điều kiện hiện nút *"Dạy tên «…»"* (spec §4.3 lối c). */
class VoiceTeachHintTest {

    private fun hint(heard: String): String? = VoiceTeachHint.pendingOf(VoiceIntentParser.parse(heard), heard)

    @Test
    fun `mo mot ten khong hieu thi goi y dung phan duoi`() {
        assertEquals("nep leag", hint("mở nep leag"))
        assertEquals("nep leag", hint("đưa nep leag vào ô số hai"))
    }

    @Test
    fun `ten ba chu cai van goi y - mau cho la luot 1, hop day doi them mot luot`() {
        // Quyết định điều phối 2.91 (spec §7 OQ4): [ĐO máy ảo] "mở Chrome" ⇒ «mở cơm» — đúng ca owner cần dạy.
        assertEquals("cơm", hint("mở cơm"))
    }

    @Test
    fun `khong goi y khi khong phai cau mo hoac da hieu hoac duoi khong ra ten`() {
        assertNull(hint("bật đèn đọc"))                 // hiểu được
        assertNull(hint("hôm nay trời đẹp quá"))        // không mở đầu bằng MỞ/BẬT
        assertNull(hint("mở nè"))                       // dưới sàn tuyệt đối 3 chữ cái
        assertNull(hint("dẫn đường đến nep leag"))      // không phải câu mở
    }
}

class VoiceTeachHintArgTest {
    @Test
    fun `tham so TEACH_APP vong tron va tham so la ra yeu cau rong`() {
        val s = VoiceTeachHint.Request(sample = "nep leag")
        assertEquals(s, VoiceTeachHint.decode(VoiceTeachHint.encode(s)))
        val p = VoiceTeachHint.Request(pkg = "com.example.flix")
        assertEquals(p, VoiceTeachHint.decode(VoiceTeachHint.encode(p)))
        assertEquals(VoiceTeachHint.Request(), VoiceTeachHint.decode("rac"))
        assertEquals(VoiceTeachHint.Request(), VoiceTeachHint.decode("p:../../etc"))
        assertEquals(VoiceTeachHint.Request(), VoiceTeachHint.decode(null))
    }

    @Test
    fun `mau tu intent ngoai phai dung hinh dang mot cai ten`() {
        // Activity HOME nhận intent của MỌI app: mẫu rác/khổng lồ/sai hình dạng ⇒ bỏ mẫu (vẫn mở trang, không gói).
        assertEquals(VoiceTeachHint.Request(), VoiceTeachHint.decode("s:" + "a".repeat(TeachSample.MAX_CHARS + 1)))
        assertEquals(VoiceTeachHint.Request(), VoiceTeachHint.decode("s:một hai ba bốn năm"))
        assertEquals(VoiceTeachHint.Request(), VoiceTeachHint.decode("s:ờ"))
        assertEquals(VoiceTeachHint.Request(), VoiceTeachHint.decode("s:nè"))
        assertEquals(VoiceTeachHint.Request(sample = "nep leag"), VoiceTeachHint.decode("s:NEP\tLEAG"))
    }
}
