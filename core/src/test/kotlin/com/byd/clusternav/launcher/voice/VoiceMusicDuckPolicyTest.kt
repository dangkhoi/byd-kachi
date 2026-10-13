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
 * 3.1 (owner 13/10, spec kachi-301 §2.4 V2–V3 · R16–R17): (6) âm lượng áp trễ/có dốc ⇒ vẫn trả, không kẹt nhỏ;
 * (7) "20 %" = 20 % độ to (−14 dB) theo đường cong thật, lùi về luật chỉ số khi máy không cho đường cong.
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
        assertEquals(Decision.KEEP_USER, VoiceMusicDuckPolicy.decide(marker(), current = 7, nowMs = now))   // vặn TO lên
        assertEquals(Decision.KEEP_USER, VoiceMusicDuckPolicy.decide(marker(), current = 0, nowMs = now))   // tự tắt tiếng
        assertEquals(Decision.KEEP_USER, VoiceMusicDuckPolicy.decide(marker(), current = 12, nowMs = now))  // vặn quá gốc
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

    /**
     * HỒI QUY 3.0 (V2, owner 13/10 *"lúc như dừng nhạc luôn"*). Âm lượng áp theo DỐC: đọc lại ngay sau `setStreamVolume`
     * thấy mức GIỮA dốc (10 → 6) ⇒ dấu ghi `set = 6`; dốc chạy tiếp tới đích 2. Lúc trả mức thật = 2 ≠ 6 ⇒ luật so khớp
     * tuyệt đối của 3.0 ra KEEP_USER ⇒ nhạc kẹt ở 2. Luật 3.1: 2 ≤ 6 và < 10 ⇒ RESTORE. Đỏ khi trả `decide` về `!=`.
     */
    @Test fun `am luong ap theo doc — doc lai giua doc, luc tra da toi dich ⇒ van tra (khong ket nho)`() {
        val midRamp = marker(original = 10, set = 6)
        assertEquals(Decision.RESTORE, VoiceMusicDuckPolicy.decide(midRamp, current = 2, nowMs = now))
        assertEquals(Decision.RESTORE, VoiceMusicDuckPolicy.decide(midRamp, current = 4, nowMs = now))
        assertEquals(Decision.RESTORE, VoiceMusicDuckPolicy.decide(midRamp, current = 6, nowMs = now))
    }

    /**
     * Áp TRỄ (V2a): lúc trả mức vẫn đúng mức gốc (lệnh hạ còn treo) ⇒ VẪN đặt lại mức gốc — vô hại nếu đã đúng mức, và đè
     * lệnh hạ còn treo. 3.0 xoá dấu ở đây ⇒ lệnh hạ áp muộn ⇒ nhạc nhỏ mãi, không ai trả. Cùng luật cho ca người dùng tự
     * vặn về gốc (đặt lại đúng mức họ đang nghe = không đổi gì).
     */
    @Test fun `ap tre chua toi — muc van bang goc ⇒ dat lai goc de de lenh ha con treo`() {
        assertEquals(Decision.RESTORE, VoiceMusicDuckPolicy.decide(marker(original = 10, set = 2), current = 10, nowMs = now))
    }

    // Đường cong giả lập: chỉ số 0 = -inf (câm), 1..15 = -60 + 4·i dB (tuyến tính theo dB như đường cong Android).
    private val linear = FloatArray(16) { i -> if (i == 0) Float.NEGATIVE_INFINITY else -60f + 4f * i }

    @Test fun `20 phan tram do to = -14 dB theo duong cong that, khong phai 20 phan tram chi so`() {
        // 15 ⇒ 0 dB, đích -13,98 dB ⇒ 12 (-12, cách 1,98) gần hơn 11 (-16, cách 2,02). Luật chỉ số cũ: 3 (-48 dB, gần câm).
        assertEquals(12, VoiceMusicDuckPolicy.duckTarget(15, 0, linear))
        assertEquals(3, VoiceMusicDuckPolicy.target(15))
        // 10 ⇒ -20 dB, đích -33,98 ⇒ 7 (-32, cách 1,98).
        assertEquals(7, VoiceMusicDuckPolicy.duckTarget(10, 0, linear))
        // Đường cong lệch (không tuyến tính) — chọn theo dB thật, không theo khoảng chỉ số.
        val skew = floatArrayOf(Float.NEGATIVE_INFINITY, -40f, -30f, -14f, -9f, -5f, -2f, 0f)
        assertEquals(4, VoiceMusicDuckPolicy.duckTarget(7, 0, skew))       // dB chọn 3 nhưng sàn 50 % (3.1.1) ⇒ 4
        assertEquals(VoiceMusicDuckPolicy.target(7), 1)                     // luật chỉ số cũ: 1 (-40 dB)
        // Dốc ở đầu dưới: 2 ⇒ -52, đích -66 ⇒ ứng viên duy nhất 1 (-56).
        assertEquals(1, VoiceMusicDuckPolicy.duckTarget(2, 0, linear))
    }

    @Test fun `theo dB cung khong bao gio ve 0, khong vuot muc hien tai, ton trong san`() {
        for (cur in 1..15) {
            val t = VoiceMusicDuckPolicy.duckTarget(cur, 0, linear)
            assertTrue(t in 1..cur, "cur=$cur t=$t")
        }
        assertEquals(1, VoiceMusicDuckPolicy.duckTarget(1, 0, linear))        // 1 ⇒ không hạ được
        assertEquals(0, VoiceMusicDuckPolicy.duckTarget(0, 0, linear))        // tắt tiếng giữ 0
        assertEquals(12, VoiceMusicDuckPolicy.duckTarget(15, 9, linear))      // sàn 9 dưới đích ⇒ không ảnh hưởng
        assertEquals(13, VoiceMusicDuckPolicy.duckTarget(15, 13, linear))     // sàn 13 chặn dưới
    }

    /** Lùi về luật chỉ số cũ (15 ⇒ 3) rồi chịu sàn 50 % (3.1.1) ⇒ 8. */
    private val FLOOR_15 = 8

    @Test fun `may khong cho duong cong ⇒ lui ve luat chi so cu`() {
        val nan = FloatArray(16) { Float.NaN }                               // API ném ⇒ bên app điền NaN
        assertEquals(FLOOR_15, VoiceMusicDuckPolicy.duckTarget(15, 0, nan))
        val negInf = FloatArray(16) { Float.NEGATIVE_INFINITY }
        assertEquals(FLOOR_15, VoiceMusicDuckPolicy.duckTarget(15, 0, negInf))
        val flat = FloatArray(16) { 0f }                                     // âm lượng cố định / bus: đường cong phẳng
        assertEquals(FLOOR_15, VoiceMusicDuckPolicy.duckTarget(15, 0, flat))
        assertEquals(FLOOR_15, VoiceMusicDuckPolicy.duckTarget(15, 0, null))
        assertEquals(FLOOR_15, VoiceMusicDuckPolicy.duckTarget(15, 0, FloatArray(8) { -it.toFloat() }))  // thiếu chỉ số
        // dB của chính mức hiện tại không hữu hạn ⇒ không có mốc ⇒ lùi.
        val badRef = linear.copyOf().also { it[15] = Float.NaN }
        assertEquals(FLOOR_15, VoiceMusicDuckPolicy.duckTarget(15, 0, badRef))
        // Vài chỉ số lỗi lẻ tẻ ⇒ bỏ qua chúng, vẫn chọn theo dB.
        val holes = linear.copyOf().also { it[12] = Float.NaN }
        assertEquals(11, VoiceMusicDuckPolicy.duckTarget(15, 0, holes))
    }

    /**
     * 3.1.1 · SÀN GIỮ NHẠC — đường cong THẬT của loa BYD [ĐO xe 13/10, `docs/diagnostics/oncar-2026-10-13/12-volume-curves.txt`]:
     * STREAM_MUSIC 0..39, điểm (phần trăm chỉ số, millibel) = (1,-5800) (20,-4000) (60,-1700) (100,0). Bản 3.0 hạ 10 ⇒ 2 (owner:
     * "như dừng nhạc"); −14 dB thuần ra 3. Sàn 50 % ⇒ 5.
     */
    @Test fun `duong cong loa BYD 0-39 - san 50 phan tram giu nhac nghe duoc`() {
        val pts = listOf(1f to -58f, 20f to -40f, 60f to -17f, 100f to 0f)
        fun db(i: Int): Float {
            if (i == 0) return Float.NEGATIVE_INFINITY
            val pct = i * 100f / 39f
            val (a, b) = pts.zipWithNext().firstOrNull { pct <= it.second.first } ?: pts.zipWithNext().last()
            return a.second + (pct - a.first).coerceAtLeast(0f) / (b.first - a.first) * (b.second - a.second)
        }
        val byd = FloatArray(40) { db(it) }
        assertEquals(5, VoiceMusicDuckPolicy.duckTarget(10, 0, byd))
        assertEquals(26, VoiceMusicDuckPolicy.duckTarget(39, 0, byd))         // mức cao: −14 dB (26) đã trên sàn 20 ⇒ giữ dB
        for (cur in 2..39) {
            val t = VoiceMusicDuckPolicy.duckTarget(cur, 0, byd)
            assertTrue(t >= (cur * 50 + 99) / 100 && t < cur, "cur=$cur t=$t")
        }
    }
}
