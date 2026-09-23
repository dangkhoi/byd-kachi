package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.EvidenceTier
import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.launcher.TelemetryRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * ═══ 2026-09-20 · GHẾ 2 MỨC · CỐP ĐÓNG (đóng/hạ) · BỤI MỊN NGOÀI XE ══════════════════════════════════════════
 *
 * Owner off-car: (1) ghế mát/sưởi thêm **mức 1 + mức 2** (trước chỉ 1 mức) cả nút lẫn voice; (2) cốp sau thêm
 * dictionary **đóng cốp / đóng cốp sau / hạ cốp sau** (trước chỉ "tắt cốp" nghe kỳ). Steering: thêm datum **bụi
 * mịn NGOÀI xe** (in-cabin đã chạy). Mỗi fix khoá cả hai chiều.
 */
class VoiceSeatTrunk0920Test {

    private fun one(s: String): VoiceIntent = VoiceIntentParser.parseOne(s)
    private fun both(s: String): List<VoiceIntent> = VoiceIntentParser.parse(s)

    // ══ V2 (owner on-car 2026-09-22) — "sưởi/mát CẢ 2 GHẾ [mức N]" ⇒ hai lệnh ghế (lái + phụ) ══════════
    @Test fun `suoi mat ca 2 ghe no ra hai lenh ghe`() {
        assertEquals(
            listOf(VoiceIntent.Control("seath", 2), VoiceIntent.Control("seath_r", 2)),
            both("sưởi cả 2 ghế mức 2"),
        )
        assertEquals(
            listOf(VoiceIntent.Control("seath", 2), VoiceIntent.Control("seath_r", 2)),
            both("sưởi cả hai ghế mức 2"),
        )
        // Không nêu mức ⇒ bật (mức 1) cả hai ghế.
        assertEquals(
            listOf(VoiceIntent.Control("seath", 1), VoiceIntent.Control("seath_r", 1)),
            both("sưởi hai ghế"),
        )
        assertEquals(
            listOf(VoiceIntent.Control("seatc", 1), VoiceIntent.Control("seatc_r", 1)),
            both("mát cả 2 ghế"),
        )
    }

    /** Câu MỘT ghế (có "lái"/"phụ") KHÔNG bị nở thành hai — vẫn đúng một lệnh. */
    @Test fun `ca 2 ghe khong nuot cau mot ghe`() {
        assertEquals(listOf(VoiceIntent.Control("seath", 2)), both("sưởi ghế lái mức 2"))
        assertEquals(listOf(VoiceIntent.Control("seath_r", 1)), both("sưởi ghế phụ"))
    }

    // ══ V1 (owner on-car 2026-09-22) — ASR rớt chữ "ghế" ("tắt sưởi ghế phụ"→"tắt sưởi") ⇒ "sưởi" trần = ghế lái
    @Test fun `suoi tran roi chu ghe van ra ghe lai khong loop`() {
        assertEquals(VoiceIntent.Control("seath", 0), one("tắt sưởi"))
        assertEquals(VoiceIntent.Control("seath", 1), one("bật sưởi"))
        assertEquals(VoiceIntent.Control("seath", 1), one("sưởi"))
    }

    /** Dài-trước vẫn thắng: có "ghế lái"/"ghế phụ" thì KHÔNG rơi về "sưởi" trần. */
    @Test fun `suoi tran khong nuot cau co neu ghe`() {
        assertEquals(VoiceIntent.Control("seath_r", 1), one("sưởi ghế phụ"))
        assertEquals(VoiceIntent.Control("seath_r", 0), one("tắt sưởi ghế phụ"))
        // "sấy kính trước" (defrost) dùng "sấy", không phải "sưởi" ⇒ không bị kéo về ghế.
        assertEquals(VoiceIntent.Control("defrost", 1), one("sấy kính trước"))
    }

    /** V2 (owner off-car 2026-09-23) "mát ghế phụ cứ hỏi lại" — biến thể phải ra seatc_r (ghế PHỤ), KHÔNG seatc. */
    @Test fun `mat ghe phu bien the ra dung ghe phu`() {
        for (s in listOf("mát ghế phụ", "mát ghế bên phụ", "mát ghế phải", "quạt ghế phụ", "mát ghế người ngồi")) {
            assertEquals(VoiceIntent.Control("seatc_r", 1), one(s), "câu \"$s\" phải ra ghế PHỤ (seatc_r)")
        }
    }

    // ══ (1) GHẾ — SELECT 3 mức (Tắt/Mức 1/Mức 2) ══════════════════════════════════════════════════════
    @Test fun `ghe mat nhan muc 1 va muc 2 qua voice`() {
        assertEquals(VoiceIntent.Control("seatc", 1), one("ghế mát mức 1"))
        assertEquals(VoiceIntent.Control("seatc", 2), one("ghế mát mức 2"))
        assertEquals(VoiceIntent.Control("seatc", 2), one("ghế mát mức hai"))     // số nói chữ
        assertEquals(VoiceIntent.Control("seath", 2), one("ghế sưởi mức 2"))
    }

    /** "bật/tắt ghế mát" (không nêu mức) vẫn chạy: bật → mức 1 · tắt → 0. */
    @Test fun `bat tat ghe mat khong neu muc`() {
        assertEquals(VoiceIntent.Control("seatc", 1), one("bật ghế mát"))
        assertEquals(VoiceIntent.Control("seatc", 0), one("tắt ghế mát"))
    }

    /** seatc/seath là SELECT 3 mức; writeArgs đổi index (0/1/2) → state khung (1/2/3), seatID=1 (lái). */
    @Test fun `seat SELECT 3 muc va writeArgs level to state`() {
        listOf("seatc", "seath").forEach { id ->
            val def = ControlRegistry.byId(id)!!
            assertEquals(ControlKind.SELECT, def.kind, "$id phải là SELECT (3 mức)")
            assertEquals(listOf("Tắt", "Mức 1", "Mức 2"), def.args, "$id args = Tắt/Mức 1/Mức 2")
        }
        // Mức 2 (index 2) → state khung 3 (raw mức 2). Mức 1 → 2. Tắt → 1. seatID=1.
        assertEquals(listOf(1, 3), HalBindingTable.writeArgs(ControlRegistry.byId("seatc")!!, 2).toList())
        assertEquals(listOf(1, 2), HalBindingTable.writeArgs(ControlRegistry.byId("seatc")!!, 1).toList())
        assertEquals(listOf(1, 1), HalBindingTable.writeArgs(ControlRegistry.byId("seatc")!!, 0).toList())
    }

    // ══ (2) CỐP — COVER, đóng/hạ đều đóng ══════════════════════════════════════════════════════════════
    @Test fun `cop la COVER va dong bang nhieu tu`() {
        assertEquals(ControlKind.COVER, ControlRegistry.byId("trunk")!!.kind, "trunk phải là COVER (mở/đóng, không bật/tắt)")
        assertEquals(VoiceIntent.Control("trunk", 1), one("mở cốp"))
        assertEquals(VoiceIntent.Control("trunk", 0), one("đóng cốp"))
        assertEquals(VoiceIntent.Control("trunk", 0), one("đóng cốp sau"))
        assertEquals(VoiceIntent.Control("trunk", 0), one("tắt cốp sau"))     // "tắt" cũ vẫn chạy (không bỏ)
        assertEquals(VoiceIntent.Control("trunk", 0), one("hạ cốp sau"))      // "hạ" scoped cho cốp
        assertEquals(VoiceIntent.Control("trunk", 0), one("hạ cốp"))
    }

    /** "hạ kính" = kính HẠ XUỐNG = MỞ (owner phương ngữ 2026-09-22); "kéo/nâng kính lên" = ĐÓNG. "hạ cốp" vẫn đóng. */
    @Test fun `ha keo kinh la huong mo dong`() {
        assertEquals(VoiceIntent.Control("win_lf", 1), one("hạ kính lái"))       // hạ = mở
        assertEquals(VoiceIntent.Control("win_lf", 1), one("hạ kính trước trái xuống"))
        assertEquals(VoiceIntent.Control("win_lf", 0), one("kéo kính lái lên"))  // kéo lên = đóng
        assertEquals(VoiceIntent.Control("win_lf", 0), one("nâng kính trước trái lên"))
        assertEquals(VoiceIntent.Control("trunk", 0), one("hạ cốp"))             // cốp vẫn đóng (scoped riêng)
    }

    // ══ (3) BỤI MỊN NGOÀI XE — datum NEEDS_CAR (getter RE trên xe) ═════════════════════════════════════
    @Test fun `co datum bui min ngoai xe cho RE tren xe`() {
        val d = TelemetryRegistry.ALL.firstOrNull { it.id == "pm25_outside" }
        assertNotNull(d, "phải có datum pm25_outside (bụi mịn ngoài xe)")
        assertEquals(EvidenceTier.NEEDS_CAR, d!!.tier, "chưa có getter thật off-car ⇒ NEEDS_CAR, sweep trên xe")
        assertEquals("µg/m³", d.unit)
    }
}
