package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.modules.clustercast.simplified.CastStyle
import com.byd.clusternav.modules.clustercast.simplified.ProjectionRecipe
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
        // B1a: 30 tách khỏi chuỗi chiếu sang styleOps[CURVED] — chuỗi dây + chuỗi gửi lần mở đầu vẫn 30->16->35 (hành vi cũ)
        assertEquals(listOf(16, 35), p.castSeq)
        assertEquals(30, p.styleOps[CastStyle.CURVED])
        assertTrue(p.export().contains(";30-16-35;"))
        assertEquals(listOf(18, 0), p.teardownSeq)       // 18->0
        assertEquals(1920, p.clusterW)
        assertEquals(720, p.clusterH)
        assertTrue(p.vdNameHint.contains("xdja") || p.vdNameHint.contains("fission"))
    }

    @Test fun `generic fallback dùng recipe seal-like + dò fission`() {
        val p = ClusterProfile.GENERIC_FALLBACK
        assertEquals(listOf(16, 35), p.castSeq)
        assertEquals(30, p.styleOps[CastStyle.CURVED])
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
        // chuỗi cũ: opcode theme đầu tiên của trường cast BÓC thành CURVED; có 30 ⇒ RECT 31 như suy luận trước v0.44
        assertEquals(mapOf(CastStyle.CURVED to 30, CastStyle.RECT to 31), p?.styleOps)
        assertEquals(listOf(16, 35), p?.castSeq)
    }

    @Test fun `chuoi cu khong co opcode 30 thi khong doi kieu duoc`() {
        assertEquals(emptyMap<CastStyle, Int>(), ClusterProfile.parse("dl5;5;1920;720;16;18-0;fission;auto_container")?.styleOps)
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

    /** B1a (ĐỔI GHIM có lý do): Chữ nhật chỉ HIỆN khi kiểu gốc đã biết là chữ nhật (Seal car.type 138) — bảng B.2. */
    @Test fun `Seal 138 doi duoc kieu, Seal chua biet car type va DiLink5 thi khong`() {
        assertFalse(ClusterProfile.SEAL_DL3.supportsStyle, "car.type chưa biết ⇒ RECT ẩn")
        assertTrue(ClusterProfile.SEAL_DL3.forCarType("138").supportsStyle)
        assertEquals(mapOf(CastStyle.CURVED to 30, CastStyle.RECT to 31), ClusterProfile.SEAL_DL3.styleOps)
        assertFalse(ClusterProfile.DL5.forCarType("138").supportsStyle)   // không có opcode kiểu → UI phải ẨN
        assertEquals(emptyMap<CastStyle, Int>(), ClusterProfile.DL5.styleOps)
    }

    // ── W2-1: lệnh opcode chỉ dựng được từ hồ sơ đã resolve ──

    @Test fun `svcCall dung ten service cua tung doi xe`() {
        assertTrue(ClusterProfile.SEAL_DL3.svcCall(18).startsWith("service call AutoContainer "))
        assertTrue(ClusterProfile.DL5.svcCall(18).startsWith("service call auto_container "))
    }

    @Test fun `svcCall giu nguyen dinh dang lenh cu`() {
        assertEquals("service call AutoContainer 2 i32 1000 i32 16 s16 \"\"", ClusterProfile.SEAL_DL3.svcCall(16))
    }

    // ── CLUSTER-THEME-SAFE (2.89): hồ sơ → công thức lệnh của đường SimpleCast (trước đây là trường chết) ──

    @Test fun `projectionRecipe - Seal ra dung chuoi cu, DL5 ra auto_container + chi 16`() {
        val seal = ClusterProfile.SEAL_DL3.projectionRecipe()
        assertEquals(ProjectionRecipe.SEAL_DL3, seal)
        assertEquals(ClusterProfile.GENERIC_FALLBACK.projectionRecipe().castSeq, listOf(16, 35))
        assertEquals(30, ClusterProfile.GENERIC_FALLBACK.projectionRecipe().styleOps[CastStyle.CURVED])
        val dl5 = ClusterProfile.DL5.projectionRecipe()
        assertEquals("auto_container", dl5.svcName)
        assertEquals(listOf(16), dl5.castSeq)
        assertEquals(ClusterProfile.DL5.svcCall(18), dl5.command(18))
    }

    @Test fun `projectionRecipe - chuoi share co opcode cam 17 thi 17 khong bao gio di ra, opcode theme roi chuoi chieu`() {
        val p = ClusterProfile.parse("x;3;1920;720;17-31-16-35;18-0;xdja;AutoContainer;30-31")!!
        val r = p.projectionRecipe()
        assertEquals(listOf(16, 35), r.castSeq)
        assertEquals(31, r.styleOps[CastStyle.CURVED], "chuỗi cũ gửi 31 lúc mở ⇒ 31 là kiểu của lượt mở")
        assertTrue(r.isTheme(31) && r.isTheme(30))
        assertFalse(r.castSeq.contains(17))
    }

    // ── CLUSTER-THEME-SAFE B1a: car.type · trường 10 · chuỗi cũ ──

    @Test fun `car type 138 - Seal goc chu nhat, RECT hien - ma khac hoac khong doc duoc - RECT an`() {
        val seal138 = ClusterProfile.detectSeed("BYD AUTO", "byd", "BYD", "", "138")
        assertEquals("seal_dl3", seal138.id)
        assertEquals(CastStyle.RECT, seal138.nativeStyle)
        assertTrue(seal138.projectionRecipe().offers(CastStyle.RECT))
        for (t in listOf("162", "1", null)) {
            val p = ClusterProfile.detectSeed("BYD AUTO", "byd", "BYD", "", t)
            assertEquals(null, p.nativeStyle, "car.type=$t")
            assertFalse(p.projectionRecipe().offers(CastStyle.RECT), "car.type=$t")
            assertTrue(p.projectionRecipe().offers(CastStyle.CURVED))
        }
        assertEquals(ClusterProfile.SEAL_DL3, ClusterProfile.detectSeed("BYD AUTO", "", "", ""), "mặc định = không biết car.type")
    }

    @Test fun `car type ap ca cho override - kieu goc la cua chiec xe, khong phai cua chuoi share`() {
        val shared = ClusterProfile.parse("seal_dl3;3;1920;720;30-16-35;18-0;xdja;AutoContainer;30-31;RECT")!!
        assertEquals(CastStyle.RECT, shared.nativeStyle, "trường 10 round-trip")
        assertEquals(null, shared.forCarType(null).nativeStyle, "xe không đọc được car.type ⇒ RECT ẩn dù chuỗi nói RECT")
        assertEquals(null, shared.forCarType("162").nativeStyle)
        assertEquals(CastStyle.RECT, ClusterProfile.parse("x;3;1920;720;30-16-35;18-0;xdja")!!.forCarType("138").nativeStyle)
        assertEquals(null, ClusterProfile.DL5.forCarType("138").nativeStyle, "không có opcode ép RECT ⇒ không RECT")
    }

    @Test fun `export truong 10 - chi khi kieu goc da biet, round-trip, chuoi day giu 30-16-35`() {
        val s138 = ClusterProfile.SEAL_DL3.forCarType("138")
        assertEquals("seal_dl3;3;1920;720;30-16-35;18-0;xdja;AutoContainer;30-31;RECT", s138.export())
        assertEquals(s138, ClusterProfile.parse(s138.export()))
        assertEquals(9, ClusterProfile.SEAL_DL3.export().split(";").size, "kiểu gốc chưa biết ⇒ 9 trường (bản cũ nhập được)")
        assertEquals("dilink5;5;1920;720;16;18-0;fission;auto_container;", ClusterProfile.DL5.export())
    }

    @Test fun `chuoi cu 7-9 truong van khop - boc opcode theme dau tien thanh CURVED`() {
        val p7 = ClusterProfile.parse("seal_dl3;3;1920;720;30-16-35;18-0;xdja")!!
        val p9 = ClusterProfile.parse("seal_dl3;3;1920;720;30-16-35;18-0;xdja;AutoContainer;30-31")!!
        for (p in listOf(p7, p9)) {
            assertEquals(ClusterProfile.SEAL_DL3, p)
            assertEquals(ProjectionRecipe.SEAL_DL3, p.projectionRecipe())
        }
        // Chuỗi custom không có opcode theme ⇒ không bao giờ gửi theme (đúng như trước: castSeq [16,35] không có 30).
        val custom = ClusterProfile.parse("sl6_dl3;3;1600;600;16-35;;fission")!!
        assertEquals(emptyMap<CastStyle, Int>(), custom.styleOps)
        // Trường 9 khai kiểu lạ (42-43) ⇒ 42 rời chuỗi chiếu, không bao giờ gửi (ngoài cổng hay như theme).
        val odd = ClusterProfile.parse("x;3;1920;720;42-16-35;18-0;xdja;AutoContainer;42-43")!!.projectionRecipe()
        assertEquals(listOf(16, 35), odd.castSeq)
        assertTrue(odd.styleOps.isEmpty())
    }

    @Test fun `sanitize nhan 10 truong, themeOnVacantVd khong bao gio tu chuoi share`() {
        val withLabel = "Hồ sơ hiện tại:\nseal_dl3;3;1920;720;30-16-35;18-0;xdja;AutoContainer;30-31;RECT"
        assertEquals(CastStyle.RECT, ClusterProfile.parse(withLabel)?.nativeStyle)
        assertNull(ClusterProfile.parse("seal_dl3;3;1920;720;30-16-35;18-0;xdja;AutoContainer;30-31;RECT;true"), "11 trường ⇒ hỏng")
        assertFalse(ClusterProfile.SEAL_DL3.themeOnVacantVd)
        assertFalse(ClusterProfile.parse(ClusterProfile.SEAL_DL3.forCarType("138").export())!!.themeOnVacantVd)
        listOf(ClusterProfile.SEAL_DL3, ClusterProfile.DL5, ClusterProfile.GENERIC_FALLBACK).forEach {
            assertFalse(it.projectionRecipe().themeOnVacantVd, "${it.id}: mặc định TẮT cho mọi hồ sơ")
        }
    }
}
