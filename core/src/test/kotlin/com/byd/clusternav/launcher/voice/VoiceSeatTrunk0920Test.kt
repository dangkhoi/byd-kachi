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

    /** GUARD: "hạ" KHÔNG vào bảng verb chung — "hạ kính" (kính hạ xuống = MỞ) KHÔNG được hiểu thành đóng. */
    @Test fun `ha khong pha kinh`() {
        assertEquals(VoiceUnknownReason.NO_VERB, (one("hạ kính") as? VoiceIntent.Unknown)?.reason)
    }

    // ══ (3) BỤI MỊN NGOÀI XE — datum NEEDS_CAR (getter RE trên xe) ═════════════════════════════════════
    @Test fun `co datum bui min ngoai xe cho RE tren xe`() {
        val d = TelemetryRegistry.ALL.firstOrNull { it.id == "pm25_outside" }
        assertNotNull(d, "phải có datum pm25_outside (bụi mịn ngoài xe)")
        assertEquals(EvidenceTier.NEEDS_CAR, d!!.tier, "chưa có getter thật off-car ⇒ NEEDS_CAR, sweep trên xe")
        assertEquals("µg/m³", d.unit)
    }
}
