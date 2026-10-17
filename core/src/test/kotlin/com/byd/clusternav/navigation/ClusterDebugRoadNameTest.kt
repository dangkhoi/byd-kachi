package com.byd.clusternav.navigation

import com.byd.clusternav.navigation.ClusterDebugRoadName.Availability
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.12 · HUD-ROADNAME-ALT-PATH — khoá bộ mã khúc tên đường của đường B ([ClusterDebugRoadName]).
 *
 * Gói chuẩn lấy NGUYÊN VĂN từ `scripts/oncar/hud-clusterdebug-nav.sh` (`PKT_ST_EN` / `PKT_ST_VI`) — cùng gói đã bơm trên Seal
 * 17/10 và tới `BYDAutoTestDevice.set(0xAA00020F)` [ĐO log xe]. Lệch một byte ⇒ đỏ.
 */
class ClusterDebugRoadNameTest {

    private val pktEn =
        "43,FA,10,08,08,A1,14,4B,00,61,00,63,00,43,FA,20,08,08,A2,68,00,69,00,20,00,74,43,FA,30,08,08,A3,00,65,00,73,00,74,00," +
            "43,FA,40,08,08,A4,C0,FF,FF,FF,FF,FF,FF"
    private val pktVi =
        "43,FA,10,08,08,A1,18,10,01,B0,01,DD,1E,43,FA,20,08,08,A2,6E,00,67,00,20,00,4C,43,FA,30,08,08,A3,00,EA,00,20,00,4C,00," +
            "43,FA,40,08,08,A4,E3,1E,69,00,BE,FF,FF"

    private fun bytesOf(hex: String) = hex.split(",").map { it.toInt(16).toByte() }.toByteArray()

    // Khoá: khuôn khúc byte-y-hệt gói xe "Kachi test" (Latin).
    @Test fun `Kachi test khop byte goi H2a`() {
        assertEquals(pktEn, ClusterDebugRoadName.hex(ClusterDebugRoadName.encode("Kachi test")))
    }

    // Khoá: tiếng Việt NFC (Đ ư ờ ê ợ) byte-y-hệt gói H2b, tổng kiểm 0xBE, đệm FF.
    @Test fun `Duong Le Loi NFC khop byte goi H2b`() {
        assertEquals(pktVi, ClusterDebugRoadName.hex(ClusterDebugRoadName.encode("Đường Lê Lợi")))
    }

    // Khoá: trần 48 đơn vị; cặp surrogate ở ranh giới bị bỏ NGUYÊN cặp (không nửa cặp ra dây).
    @Test fun `cat 48 don vi tai ranh gioi surrogate`() {
        val emoji = "🚗"   // 🚗 = 2 đơn vị
        val road47 = "a".repeat(47) + emoji
        val fit = ClusterDebugRoadName.fit(road47)
        assertEquals(47, fit.length)
        assertFalse(Character.isHighSurrogate(fit.last()))
        val road46 = "a".repeat(46) + emoji + "bbb"
        assertEquals("a".repeat(46) + emoji, ClusterDebugRoadName.fit(road46))
        assertEquals(48, ClusterDebugRoadName.fit("x".repeat(200)).length)
        // 48 đơn vị = 96 byte ⇒ 98 byte nội dung ⇒ đúng 14 khúc, khúc cuối id 0x43FAE008.
        val enc = ClusterDebugRoadName.encode("x".repeat(200))
        assertEquals(14 * 13, enc.size)
        assertTrue(ClusterDebugRoadName.allowed(enc))
    }

    @Test fun `surrogate mo coi va ky tu dieu khien`() {
        assertEquals("a b", ClusterDebugRoadName.fit("a\tb"))
        assertEquals("ab", ClusterDebugRoadName.fit("a\uDE97b"))
        assertEquals(ClusterDebugRoadName.CLEAR_NAME, ClusterDebugRoadName.fit("   "))
        assertEquals(ClusterDebugRoadName.CLEAR_NAME, ClusterDebugRoadName.fit(""))
    }

    // Khoá §4: allow-list CHỈ 14 id khúc tên đường; mọi id khác (trạng thái dẫn, mũi tên, cự ly, khúc thứ 15, họ kề) ⇒ chặn.
    @Test fun `allow-list chi nhan khuc ten duong`() {
        assertEquals(14, ClusterDebugRoadName.ALLOWED_IDS.size)
        assertEquals(0x43FA1008, ClusterDebugRoadName.ALLOWED_IDS.min())
        assertEquals(0x43FAE008, ClusterDebugRoadName.ALLOWED_IDS.max())
        assertTrue(ClusterDebugRoadName.allowed(bytesOf(pktEn)))
        assertTrue(ClusterDebugRoadName.allowed(bytesOf(pktVi)))
        fun rec(id: Int, seq: Int = (id ushr 12) and 0xFF, len: Int = 8) = byteArrayOf(
            (id ushr 24).toByte(), (id ushr 16).toByte(), (id ushr 8).toByte(), id.toByte(), len.toByte(), seq.toByte(),
            0, 0, 0, 0, 0, 0, 0,
        )
        listOf(0x43E0003A, 0x43F01018, 0x43F01010, 0x43F01030, 0x43FAF008, 0x43FB0008, 0x1F7A1008, 0x43FA1009, 0xAA00020F.toInt())
            .forEach { id -> assertFalse(ClusterDebugRoadName.allowed(rec(id)), "id 0x%08X phải bị chặn".format(id)) }
        assertFalse(ClusterDebugRoadName.allowed(rec(0x43FA1008, seq = 0xA2)), "số thứ tự sai")
        assertFalse(ClusterDebugRoadName.allowed(rec(0x43FA1008, len = 4)), "len sai")
        assertFalse(ClusterDebugRoadName.allowed(rec(0x43FA1008) + rec(0x43FA1008)), "id lặp")
        assertFalse(ClusterDebugRoadName.allowed(bytesOf(pktEn) + byteArrayOf(0x43)), "byte thừa")
        assertFalse(ClusterDebugRoadName.allowed(ByteArray(0)))
        // Gói trộn guidance + tên (khuôn DiAuto gộp) ⇒ chặn: đường B không bao giờ mang mũi tên/cự ly.
        assertFalse(ClusterDebugRoadName.allowed(bytesOf("43,F0,10,18,04,00,00,01,F4,$pktEn")))
    }

    // Khoá: bộ mã KHÔNG THỂ sinh ra id ngoài allow-list, với mọi độ dài/ký tự.
    @Test fun `encoder luon qua allow-list`() {
        val alphabet = "aĐườngLêLợi 0123456789🚗\t\u0000"
        val rnd = java.util.Random(17)
        repeat(2_000) {
            val s = buildString { repeat(rnd.nextInt(130)) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            val enc = ClusterDebugRoadName.encode(s)
            assertTrue(ClusterDebugRoadName.allowed(enc), "đầu vào #$it")
            assertTrue(ClusterDebugRoadName.hex(enc).matches(Regex("[0-9A-F]{2}(,[0-9A-F]{2})*")), "khuôn hex receiver parse được")
        }
    }

    @Test fun `kha dung chi khi dung su that da RE`() {
        fun a(
            installed: Boolean = true, system: Boolean = true, appEnabled: Boolean = true, vc: Long = 10_601_004L,
            found: Boolean = true, exported: Boolean = true, enabled: Boolean = true, perm: String? = null,
        ) = ClusterDebugRoadName.availability(installed, system, appEnabled, vc, found, exported, enabled, perm)
        assertEquals(Availability.OK, a())
        assertEquals(Availability.NOT_INSTALLED, a(installed = false))
        assertEquals(Availability.NOT_SYSTEM, a(system = false))
        assertEquals(Availability.DISABLED, a(appEnabled = false))
        assertEquals(Availability.NO_RECEIVER, a(found = false))
        assertEquals(Availability.DISABLED, a(enabled = false))
        assertEquals(Availability.PROTECTED, a(exported = false))
        assertEquals(Availability.PROTECTED, a(perm = "android.permission.BYDAUTO_TEST_SET"))
        assertEquals(Availability.UNKNOWN_VERSION, a(vc = 10_601_005L))
    }
}
