package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit test (off-device, JUnit5) cho ClusterProfile — export/parse + detect + seed integrity. T-F verify. */
class ClusterProfileTest {

    @Test fun `seed seal_dl3 tái tạo chính xác sequence hiện tại`() {
        val p = ClusterProfile.SEAL_DL3
        assertEquals("seal_dl3", p.id)
        assertEquals(3, p.diLink)
        assertEquals(listOf(30, 16, 35), p.castSeq)      // 30->16->35 (hành vi cũ)
        assertEquals(listOf(18, 0), p.teardownSeq)       // 18->0
        assertEquals(1920, p.clusterW)
        assertEquals(720, p.clusterH)
        assertTrue(p.vdNameHint.contains("xdja") || p.vdNameHint.contains("fission"))
    }

    @Test fun `generic fallback dùng recipe seal-like + dò fission`() {
        val p = ClusterProfile.GENERIC_FALLBACK
        assertEquals(listOf(30, 16, 35), p.castSeq)
        assertEquals(listOf(18, 0), p.teardownSeq)
        assertTrue(p.vdNameHint.contains("xdja") || p.vdNameHint.contains("fission"))
    }

    @Test fun `export then parse round-trips seeds`() {
        for (p in listOf(ClusterProfile.SEAL_DL3, ClusterProfile.GENERIC_FALLBACK)) {
            assertEquals(p, ClusterProfile.parse(p.export()))
        }
    }

    @Test fun `export format is stable`() {
        // v0.42: thêm phần thứ 8 = tên service (DiLink5 dùng "auto_container", 2/3/4 dùng "AutoContainer")
        assertEquals("seal_dl3;3;1920;720;30-16-35;18-0;xdja;AutoContainer;30-31", ClusterProfile.SEAL_DL3.export())
    }

    // ── v0.42: đa đời DiLink (RE từ DashCast v1.5.4) ──

    /** Chuỗi 7 phần anh em đã chia sẻ trong nhóm PHẢI nhập được, mặc định service "AutoContainer". */
    @Test fun `parse chuoi 7 phan cu van chay, mac dinh AutoContainer`() {
        val p = ClusterProfile.parse("seal_dl3;3;1920;720;30-16-35;18-0;xdja")
        assertEquals("AutoContainer", p?.svcName)
        assertEquals("seal_dl3", p?.id)
        // chuỗi cũ không có styleOps → suy từ castSeq (có 30 ⇒ đổi kiểu được)
        assertEquals(30 to 31, p?.styleOps)
    }

    @Test fun `chuoi cu khong co opcode 30 thi khong doi kieu duoc`() {
        assertNull(ClusterProfile.parse("dl5;5;1920;720;16;18-0;fission;auto_container")?.styleOps)
    }

    @Test fun `parse chuoi 8 phan lay dung ten service`() {
        val p = ClusterProfile.parse("dilink5;5;1920;720;16;18-0;fission;auto_container")
        assertEquals("auto_container", p?.svcName)
        assertEquals(5, p?.diLink)
    }

    /** Tên service đi THẲNG vào lệnh shell → phải chặn ký tự lạ, nếu không là chèn lệnh tuỳ ý. */
    @Test fun `ten service co ky tu la thi ve mac dinh`() {
        assertEquals("AutoContainer", ClusterProfile.parse("x;3;100;100;16;18;h;rm -rf /")?.svcName)
        assertEquals("AutoContainer", ClusterProfile.parse("x;3;100;100;16;18;h;a b")?.svcName)
        assertEquals("AutoContainer", ClusterProfile.parse("x;3;100;100;16;18;h;\$(id)")?.svcName)
    }

    @Test fun `round-trip giu nguyen ten service`() {
        assertEquals(ClusterProfile.DL5, ClusterProfile.parse(ClusterProfile.DL5.export()))
    }

    /** DiLink5 phải được nhận diện TRƯỚC nhánh "byd" chung — sai nhánh là gọi nhầm tên service. */
    @Test fun `detectSeed nhan ra DiLink5`() {
        assertEquals("auto_container",
            ClusterProfile.detectSeed("BYD AUTO", "byd", "byd", "dilink5").svcName)
        assertEquals("auto_container",
            ClusterProfile.detectSeed("BYD AUTO", "byd", "byd", "DiLink 5.0 build").svcName)
        // DL3 vẫn như cũ
        assertEquals("AutoContainer", ClusterProfile.detectSeed("BYD AUTO", "byd", "byd", "dilink3").svcName)
    }

    @Test fun `sanitize bo dong nhan voi ca 7 va 8 phan`() {
        val withLabel7 = "Hồ sơ hiện tại (copy dòng dưới):\nseal_dl3;3;1920;720;30-16-35;18-0;xdja"
        val withLabel8 = "Hồ sơ hiện tại (copy dòng dưới):\ndilink5;5;1920;720;16;18-0;fission;auto_container"
        assertEquals("seal_dl3", ClusterProfile.parse(withLabel7)?.id)
        assertEquals("dilink5", ClusterProfile.parse(withLabel8)?.id)
    }

    @Test fun `parse custom override with empty teardown`() {
        val p = ClusterProfile.parse("sl6_dl3;3;1600;600;16-35;;fission")
        assertEquals("sl6_dl3", p?.id)
        assertEquals(1600, p?.clusterW)
        assertEquals(listOf(16, 35), p?.castSeq)
        assertEquals(emptyList<Int>(), p?.teardownSeq)
        assertEquals("fission", p?.vdNameHint)
    }

    @Test fun `parse rejects malformed`() {
        assertNull(ClusterProfile.parse(""))
        assertNull(ClusterProfile.parse("id;3;1920;720;30-16-35;18-0"))     // 6 field
        assertNull(ClusterProfile.parse("id;x;1920;720;30-16-35;18-0;xdja")) // diLink không phải số
        assertNull(ClusterProfile.parse("id;3;W;720;30-16-35;18-0;xdja"))    // W không phải số
        assertNull(ClusterProfile.parse("id;3;1920;720;30-x-35;18-0;xdja"))  // castSeq có phần không phải số
        assertNull(ClusterProfile.parse(";3;1920;720;30-16-35;18-0;xdja"))   // id rỗng
    }

    @Test fun `detectSeed maps BYD AUTO to seal_dl3`() {
        // Head-unit BYD báo Build.MODEL = "BYD AUTO"
        assertEquals(ClusterProfile.SEAL_DL3, ClusterProfile.detectSeed("BYD AUTO", "", "", ""))
        assertEquals(ClusterProfile.SEAL_DL3, ClusterProfile.detectSeed("", "byd", "BYD", ""))
        assertEquals(ClusterProfile.SEAL_DL3, ClusterProfile.detectSeed("", "", "", "ro.product.model=byd_seal"))
    }

    @Test fun `detectSeed non-byd falls back to generic`() {
        assertEquals(ClusterProfile.GENERIC_FALLBACK, ClusterProfile.detectSeed("Pixel 6", "Google", "Google", ""))
        assertEquals(ClusterProfile.GENERIC_FALLBACK, ClusterProfile.detectSeed("", "", "", ""))
    }

    @Test fun `summary is human readable`() {
        assertTrue(ClusterProfile.SEAL_DL3.summary().contains("seal_dl3"))
        assertTrue(ClusterProfile.SEAL_DL3.summary().contains("1920×720"))
    }

    // ── VALIDATE (R-hardening): chuỗi share là untrusted → chặn bounds suy biến + mã lệnh tùy ý ──
    @Test fun `parse rejects W or H không dương`() {
        assertNull(ClusterProfile.parse("id;3;0;720;30-16-35;18-0;xdja"))     // W=0
        assertNull(ClusterProfile.parse("id;3;-5;720;30-16-35;18-0;xdja"))    // W âm
        assertNull(ClusterProfile.parse("id;3;1920;0;30-16-35;18-0;xdja"))    // H=0
    }

    @Test fun `parse rejects W hoặc H quá lớn`() {
        assertNull(ClusterProfile.parse("id;3;9000;720;30-16-35;18-0;xdja"))  // W>8192
        assertNull(ClusterProfile.parse("id;3;1920;99999;30-16-35;18-0;xdja"))
    }

    @Test fun `parse rejects diLink ngoài dải`() {
        assertNull(ClusterProfile.parse("id;0;1920;720;30-16-35;18-0;xdja"))
        assertNull(ClusterProfile.parse("id;99;1920;720;30-16-35;18-0;xdja"))
    }

    @Test fun `parse rejects mã lệnh ngoài 0-255 (chống service call tùy ý)`() {
        assertNull(ClusterProfile.parse("id;3;1920;720;300;;xdja"))           // cast cmd 300 > 255
        assertNull(ClusterProfile.parse("id;3;1920;720;30-16-35;999;xdja"))   // teardown cmd 999 > 255
    }

    @Test fun `parse rejects id quá dài`() {
        assertNull(ClusterProfile.parse("a".repeat(33) + ";3;1920;720;30;;xdja"))
    }

    @Test fun `parse chấp nhận biên hợp lệ`() {
        val p = ClusterProfile.parse("x;9;8192;1;255-0;;h")
        assertEquals("x", p?.id)
        assertEquals(9, p?.diLink)
        assertEquals(8192, p?.clusterW)
        assertEquals(listOf(255, 0), p?.castSeq)
    }

    // ── W2-6: opcode kiểu cụm phải do HỒ SƠ khai, không đoán từ ngoài ──

    @Test fun `Seal doi duoc kieu, DiLink5 thi khong`() {
        assertTrue(ClusterProfile.SEAL_DL3.supportsStyle)
        assertEquals(30 to 31, ClusterProfile.SEAL_DL3.styleOps)
        assertFalse(ClusterProfile.DL5.supportsStyle)   // castSeq=[16], không có opcode kiểu → UI phải ẨN nút
        assertNull(ClusterProfile.DL5.styleOps)
    }

    // ── W2-1: lệnh opcode chỉ dựng được từ hồ sơ đã resolve ──

    @Test fun `svcCall dung ten service cua tung doi xe`() {
        assertTrue(ClusterProfile.SEAL_DL3.svcCall(18).startsWith("service call AutoContainer "))
        assertTrue(ClusterProfile.DL5.svcCall(18).startsWith("service call auto_container "))
    }

    @Test fun `svcCall giu nguyen dinh dang lenh cu`() {
        assertEquals("service call AutoContainer 2 i32 1000 i32 16 s16 \"\"", ClusterProfile.SEAL_DL3.svcCall(16))
    }
}
