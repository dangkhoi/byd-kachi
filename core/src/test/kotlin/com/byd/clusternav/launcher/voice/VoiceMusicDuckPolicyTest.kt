package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceMusicDuckPolicy.Decision
import com.byd.clusternav.launcher.voice.VoiceMusicDuckPolicy.Marker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.0 R4 `VOICE-DUCK-MUSIC` — owner 11/10: hạ nhạc 20 % mức HIỆN TẠI khi nghe, trả mức cũ khi xong, một hành vi ═════
 *
 * Khoá: (1) mức hạ không bao giờ về 0 khi đang có tiếng; (2) người dùng vặn núm trong lúc nghe ⇒ KHÔNG trả đè;
 * (3) dấu sót do BYD giết Kachi lúc tắt máy ⇒ trả lại sau khi nổ máy (qua đêm), quá 24 h ⇒ chỉ xoá; (4) dấu hỏng ⇒ không
 * trả gì; (5) dấu của tiến trình khác còn sống ⇒ để yên.
 */
class VoiceMusicDuckPolicyTest {

    @Test fun `muc ha = 20 phan tram muc hien tai, lam tron`() {
        assertEquals(2, VoiceMusicDuckPolicy.target(10))
        assertEquals(3, VoiceMusicDuckPolicy.target(15))
        assertEquals(6, VoiceMusicDuckPolicy.target(30))
        assertEquals(5, VoiceMusicDuckPolicy.target(25))   // 5,0
        assertEquals(1, VoiceMusicDuckPolicy.target(3))    // 0,6 ⇒ 1
        assertEquals(2, VoiceMusicDuckPolicy.target(8))    // 1,6 ⇒ 2
    }

    @Test fun `khong bao gio ve 0 khi dang co tieng — tat tieng thi giu 0`() {
        assertEquals(1, VoiceMusicDuckPolicy.target(2))    // 0,4 ⇒ 0 ⇒ sàn 1
        assertEquals(1, VoiceMusicDuckPolicy.target(1))    // 1 ⇒ không hạ được (bên gọi thấy đích == hiện ⇒ không dấu)
        assertEquals(0, VoiceMusicDuckPolicy.target(0))
        assertEquals(0, VoiceMusicDuckPolicy.target(-1))
        for (cur in 1..100) assertTrue(VoiceMusicDuckPolicy.target(cur) in 1..cur, "cur=$cur")
    }

    @Test fun `ton trong san cua luong`() {
        assertEquals(4, VoiceMusicDuckPolicy.target(10, min = 4))
        assertEquals(3, VoiceMusicDuckPolicy.target(3, min = 5))   // không bao giờ VƯỢT mức hiện tại
    }

    private val now = 1_800_000_000_000L
    private fun marker(original: Int = 10, set: Int = 2, ageMs: Long = 5_000) = Marker(original, set, now - ageMs, 4242)

    @Test fun `het luot nghe, nuc chua doi ⇒ tra muc goc`() {
        assertEquals(Decision.RESTORE, VoiceMusicDuckPolicy.decide(marker(), current = 2, nowMs = now))
    }

    @Test fun `nguoi dung van num trong luc nghe ⇒ giu muc nguoi dung, khong tra de`() {
        assertEquals(Decision.KEEP_USER, VoiceMusicDuckPolicy.decide(marker(), current = 7, nowMs = now))
        assertEquals(Decision.KEEP_USER, VoiceMusicDuckPolicy.decide(marker(), current = 0, nowMs = now))   // tự tắt tiếng
        assertEquals(Decision.KEEP_USER, VoiceMusicDuckPolicy.decide(marker(), current = 10, nowMs = now))  // tự vặn về gốc
        // Không đọc được mức (bên app trả -1) ⇒ không trả mù.
        assertEquals(Decision.KEEP_USER, VoiceMusicDuckPolicy.decide(marker(), current = -1, nowMs = now))
    }

    @Test fun `dau sot qua dem (BYD giet Kachi luc tat may) ⇒ van tra`() {
        val overnight = marker(ageMs = 14L * 60 * 60 * 1000)
        assertEquals(Decision.RESTORE, VoiceMusicDuckPolicy.decide(overnight, current = 2, nowMs = now))
    }

    @Test fun `dau qua 24 h ⇒ chi xoa, tuoi am (RTC chinh lai) ⇒ coi la tuoi`() {
        val old = marker(ageMs = VoiceMusicDuckPolicy.MAX_MARKER_AGE_MS + 1)
        assertEquals(Decision.STALE, VoiceMusicDuckPolicy.decide(old, current = 2, nowMs = now))
        val future = marker(ageMs = -3_600_000)
        assertEquals(Decision.RESTORE, VoiceMusicDuckPolicy.decide(future, current = 2, nowMs = now))
    }

    @Test fun `dau vo ly (goc khong lon hon muc dat) ⇒ chi xoa`() {
        assertEquals(Decision.STALE, VoiceMusicDuckPolicy.decide(marker(original = 2, set = 2), current = 2, nowMs = now))
    }

    @Test fun `ma hoa va giai ma dau, dau hong ⇒ null`() {
        val m = Marker(12, 2, now, 31337)
        assertEquals(m, VoiceMusicDuckPolicy.decode(VoiceMusicDuckPolicy.encode(m)))
        assertEquals(m, VoiceMusicDuckPolicy.decode(VoiceMusicDuckPolicy.encode(m) + "\n"))
        assertNull(VoiceMusicDuckPolicy.decode(null))
        assertNull(VoiceMusicDuckPolicy.decode(""))
        assertNull(VoiceMusicDuckPolicy.decode("v1 12 2 $now"))           // thiếu pid
        assertNull(VoiceMusicDuckPolicy.decode("v2 12 2 $now 1"))         // khuôn lạ
        assertNull(VoiceMusicDuckPolicy.decode("v1 x 2 $now 1"))
        assertNull(VoiceMusicDuckPolicy.decode("v1 -3 2 $now 1"))
    }

    @Test fun `dau cua tien trinh khac con song ⇒ de yen, da chet hoac cua chinh minh ⇒ xu ly`() {
        val m = marker()
        assertTrue(VoiceMusicDuckPolicy.ownedElsewhere(m, myPid = 1, ownerAlive = true))
        assertFalse(VoiceMusicDuckPolicy.ownedElsewhere(m, myPid = 1, ownerAlive = false))
        assertFalse(VoiceMusicDuckPolicy.ownedElsewhere(m, myPid = m.pid, ownerAlive = true))
    }
}
