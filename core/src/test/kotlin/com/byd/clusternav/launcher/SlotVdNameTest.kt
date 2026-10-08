package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá 2.98 · R4 (VD-DISPLAY-SETTINGS-GROWTH). [ĐO máy ảo 08/10] mỗi lần dựng ô với tên `kachi-slot-<ô>-<ms>`,
 * `/data/system/display_settings.xml` dài thêm 124 B (một mục uniqueId mới, không bao giờ gỡ — KDoc [SlotVdName]).
 * Bài này khoá: (1) tập tên hữu hạn qua nhiều chuyến, (2) không bao giờ trùng tên một màn ảo đang sống — vì
 * [SlotVdLedger.adopt] coi trùng tên là CÙNG màn ảo và sẽ không trả màn cũ ra để giải phóng (rò kiểu H2·1).
 */
class SlotVdNameTest {

    @Test
    fun `ten goc on dinh theo o - cung o qua cac chuyen la cung mot ten`() {
        assertEquals("kachi-slot-0", SlotVdName.pick(0, emptySet()))
        assertEquals("kachi-slot-3", SlotVdName.pick(3, setOf("kachi-slot-0", "kachi-slot-1")))
        assertTrue(SlotVdName.pick(2, emptySet()).startsWith(SlotVdName.PREFIX))
    }

    @Test
    fun `ten dang song bi ne bang hau to the he nho nhat con trong`() {
        assertEquals("kachi-slot-1-g1", SlotVdName.pick(1, setOf("kachi-slot-1")))
        assertEquals("kachi-slot-1-g2", SlotVdName.pick(1, setOf("kachi-slot-1", "kachi-slot-1-g1")))
        assertEquals("kachi-slot-1-g1", SlotVdName.pick(1, setOf("kachi-slot-1", "kachi-slot-1-g2")))
    }

    /** Mô phỏng 200 lần dựng ô theo cả hai thứ tự đo được: nhả-rồi-tạo (thường) và tạo-rồi-nhả (H2·1, hai màn Kachi chồng). */
    @Test
    fun `200 lan dung lai - tap ten huu han, sổ luon giai phong man cu`() {
        val ledger = SlotVdLedger<String>()
        val everUsed = HashSet<String>()
        var freed = 0
        repeat(200) { i ->
            val owner = "ws@$i"
            for (slot in 0..2) {
                if (i % 2 == 0) ledger.release("ws@${i - 1}", slot)?.let { freed++ }   // nhả trước (đổi hồ sơ / bố cục)
                val name = SlotVdName.pick(slot, ledger.live().mapTo(HashSet()) { it.name })
                everUsed += name
                val stale = ledger.adopt(owner, slot, name, name)
                freed += stale.size                                                       // chồng: màn cũ trả ra qua adopt
                assertEquals(1, ledger.live().count { it.slot == slot }, "mỗi ô đúng một màn ảo sống (H2·1)")
            }
        }
        assertTrue(everUsed.size <= 6, "tên phải hữu hạn (≤ 2 thế hệ × 3 ô), có: $everUsed")
        assertEquals(200 * 3 - 3, freed, "mọi màn ảo cũ đều được trả ra để release() — không màn nào rời sổ âm thầm")
    }

    @Test
    fun `chong - ten moi khac ten man cu dang song nen adopt tra man cu ra`() {
        val ledger = SlotVdLedger<String>()
        val old = SlotVdName.pick(0, emptySet())
        ledger.adopt("ws@old", 0, old, "vd-old")
        val fresh = SlotVdName.pick(0, ledger.live().mapTo(HashSet()) { it.name })
        assertNotEquals(old, fresh)
        val stale = ledger.adopt("ws@new", 0, fresh, "vd-new")
        assertEquals(listOf("vd-old"), stale.map { it.handle }, "màn cũ phải được trả ra để giải phóng")
    }

    /** App đỗ ở ô 7 mang tên ô cũ (khoá âm của `ParkedApps`) — ô đó dựng mới không được trùng tên với màn đỗ. */
    @Test
    fun `man ao do o 7 van giu ten - o cu dung moi ne ten do`() {
        val ledger = SlotVdLedger<String>()
        ledger.adopt("ws@a", 4, SlotVdName.pick(4, emptySet()), "vd-a")
        ledger.adopt("park", -1, "kachi-slot-4", "vd-a")   // SlotVdOwner.move: cùng tên ⇒ chỉ đổi khoá
        val fresh = SlotVdName.pick(4, ledger.live().mapTo(HashSet()) { it.name })
        assertEquals("kachi-slot-4-g1", fresh)
        assertTrue(ledger.adopt("ws@b", 4, fresh, "vd-b").isEmpty(), "màn đỗ không bị nhả nhầm")
    }
}
