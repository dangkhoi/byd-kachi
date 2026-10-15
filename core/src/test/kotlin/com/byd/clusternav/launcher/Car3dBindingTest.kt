package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R25 — khoá bảng ràng buộc 3D: mọi điểm chạm của mọi mô hình trỏ vào nhóm/nút CÓ THẬT (không đường ghi thứ
 * hai), chạm kính đi đúng vòng đóng → 50 % → mở hẳn → đóng của bảng Kính 2D, cốp/đèn bấm ô sẵn có, cửa chỉ xem.
 * Dựng từ [CapabilityGroups] + [GroupBoard] THẬT với trạng thái xe rỗng (off-car).
 */
class Car3dBindingTest {

    private fun model(g: String) = GroupBoard.of(g, CarStatus())!!

    @Test
    fun `moi diem cham tro vao nhom va nut co that`() {
        for (m in Car3dCatalog.ORDER) {
            for (h in m.hotspots) {
                val gm = GroupBoard.of(h.group, CarStatus())
                requireNotNull(gm) { "${m.id}/${h.id}: nhóm ${h.group} không có" }
                val r = Car3dHotspots.resolve(gm, h)
                if (h.target.isNotEmpty()) assertTrue(r.actions.isNotEmpty(), "${m.id}/${h.id}: không có ô nút cho '${h.target}'")
                if (h.read != null) assertEquals(h.read, r.cell?.id, "${m.id}/${h.id}: ô xem")
            }
            Car3dCatalog.PILLS.forEach { id -> assertTrue(model("g_windows").actions.any { it.id == id }, "viên $id") }
        }
    }

    @Test
    fun `cham kinh di dung vong cua bang Kinh 2D`() {
        val r = Car3dHotspots.resolve(model("g_windows"), Car3dCatalog.BYD_AVM.hotspots.first { it.id == "win-lf" })
        assertEquals(setOf("win_lf", "win_half_lf"), r.actions.map { it.id }.toSet())
        assertEquals("window_lf", r.cell?.id)
        assertEquals(Car3dHotspots.Tap.Set(r.actions.first { it.id == "win_half_lf" }, true), Car3dHotspots.tap(r, null))
        assertEquals(Car3dHotspots.Tap.Set(r.actions.first { it.id == "win_half_lf" }, true), Car3dHotspots.tap(r, 0))
        assertEquals(Car3dHotspots.Tap.Set(r.actions.first { it.id == "win_lf" }, true), Car3dHotspots.tap(r, 50))
        assertEquals(Car3dHotspots.Tap.Set(r.actions.first { it.id == "win_lf" }, false), Car3dHotspots.tap(r, 100))
    }

    @Test
    fun `cop va den bam o san co, cua chi xem`() {
        val trunk = Car3dHotspots.resolve(model("g_doors"), Car3dCatalog.BYD_AVM.hotspots.first { it.id == "trunk" })
        assertEquals(Car3dHotspots.Tap.Press(trunk.actions.single()), Car3dHotspots.tap(trunk, null))
        assertEquals("trunk", trunk.actions.single().id)
        val headl = Car3dHotspots.resolve(model("g_lights"), Car3dCatalog.BYD_AVM.hotspots.first { it.id == "headl" })
        assertEquals("headl", (Car3dHotspots.tap(headl, null) as Car3dHotspots.Tap.Press).action.id)
        val door = Car3dHotspots.resolve(model("g_doors"), Car3dCatalog.BYD_AVM.hotspots.first { it.id == "door-lf" })
        assertEquals(Car3dHotspots.Tap.None, Car3dHotspots.tap(door, null))
        // Off-car: chưa đọc ⇒ vòng đứt nét (không giấu "chưa biết" thành "đóng"), biến thể ở bản đóng.
        assertEquals(CarDots.Kind.UNKNOWN, Car3dHotspots.dot(door, null).kind)
        assertFalse(Car3dHotspots.active(door.cell))
        // Owner 14/10 "cửa đâu có nhấn được nên ko cần chấm tròn": điểm CHỈ-XEM không bao giờ chấm (kể cả cửa MỞ). Điểm có nút luôn chấm.
        assertFalse(Car3dHotspots.shows(door)); assertTrue(Car3dHotspots.shows(trunk)); assertTrue(Car3dHotspots.shows(headl))
        val open = CarStatus().let { it.copy(body = it.body.copy(doorLfOpen = true, doorRfOpen = false, doorLrOpen = false, doorRrOpen = false)) }
        val doorOpen = Car3dHotspots.resolve(GroupBoard.of("g_doors", open)!!, Car3dCatalog.BYD_AVM.hotspots.first { it.id == "door-lf" })
        assertTrue(Car3dHotspots.active(doorOpen.cell)); assertFalse(Car3dHotspots.shows(doorOpen))
        // Biến thể nhiều ô `a|b`: bật khi BẤT KỲ ô nào bật; rỗng ⇒ không bao giờ.
        val cells = GroupBoard.of("g_doors", open)!!.cells.associateBy { it.id }
        assertTrue(Car3dHotspots.activeAny("door_rf|door_lf") { cells[it] }); assertFalse(Car3dHotspots.activeAny("door_rf") { cells[it] })
        assertFalse(Car3dHotspots.activeAny("") { cells[it] })
        // 3.1.6 · R32 — cốp theo SỐ ĐỌC `tailgate_status` (`getDoorState(6)`), không theo lệnh cuối (`cmd:` đã gỡ). Khoá: đổi
        // điểm cốp về nhóm Kính / bỏ `read` ⇒ ô đọc `null` ⇒ đỏ.
        assertEquals("tailgate_status", trunk.cell?.id)
        assertEquals(CarDots.Kind.UNKNOWN, Car3dHotspots.dot(trunk, null).kind)   // off-car: chưa đọc ⇒ vòng đứt, KHÔNG "đóng"
        val boot = CarStatus().let { it.copy(body = it.body.copy(tailgateOpen = true)) }
        val bootOpen = Car3dHotspots.resolve(GroupBoard.of("g_doors", boot)!!, Car3dCatalog.BYD_AVM.hotspots.first { it.id == "trunk" })
        assertTrue(Car3dHotspots.active(bootOpen.cell))
        val lid = Car3dCatalog.BYD_AVM.variants.single { it.closed == "trunk*" }
        assertEquals("tailgate_status", lid.read)
        assertTrue(Car3dHotspots.activeAny(lid.read) { id -> GroupBoard.of("g_doors", boot)!!.cells.firstOrNull { it.id == id } })
        assertFalse(Car3dHotspots.activeAny(lid.read) { id -> cells[id] })   // đọc ra ĐÓNG (`tailgateOpen = null` ⇒ chưa đọc) ⇒ nắp nghỉ
    }

    @Test
    fun `ten nut theo mau, bien the mo dong`() {
        assertTrue(Car3dNames.matches("doorlf*", "DoorLF_paint"))
        assertTrue(Car3dNames.matches("body_lamp_*front*", "body_lamp_turnleftfront"))
        assertFalse(Car3dNames.matches("trunk*", "body_trunk"))
        assertFalse(Car3dNames.matches("a.b", "axb"))   // dấu chấm là chữ, không phải regex
        val v = Car3dCatalog.BYD_AVM.variants.first { it.read == "door_lf" }
        assertEquals(true, Car3dNames.visible(v, "doorlf_paint_open", open = true))
        assertEquals(false, Car3dNames.visible(v, "doorlf_paint", open = true))
        assertEquals(true, Car3dNames.visible(v, "doorlf_paint", open = false))
        assertNull(Car3dNames.visible(v, "hood", open = true))
    }

    @Test
    fun `bien the gop - chi hien khi moi bien the nhan nut cung dong y`() {
        val m = Car3dCatalog.forAvm("eke")
        fun vis(name: String, vararg on: String) =
            Car3dNames.visibleAll(m.variants, m.variants.map { v -> v.read.split('|').any { it.isNotEmpty() && it in on } }, name)
        // Off-car (không gì bật): bản đóng/tắt hiện, bản mở/sáng ẩn.
        assertEquals(true, vis("doorlf_paint")); assertEquals(false, vis("doorlf_paint_open")); assertEquals(false, vis("doorlf_open"))
        assertEquals(true, vis("body_lamp_3")); assertEquals(false, vis("body_lamp_3on"))
        // Cửa mở: bản mở thay bản đóng.
        assertEquals(false, vis("doorlf_paint", "door_lf")); assertEquals(true, vis("doorlf_open", "door_lf"))
        // Xi-nhan trên gương cửa: phải cửa mở VÀ xi-nhan sáng mới hiện bản "_openon".
        assertEquals(false, vis("doorlf_lamp_turnleft_openon", "door_lf"))
        assertEquals(true, vis("doorlf_lamp_turnleft_openon", "door_lf", "light_left_turn"))
        assertEquals(true, vis("doorlf_lamp_turnlefton", "light_left_turn"))
        // Bảng đèn của AVM [ĐO mã máy]: dải đuôi 15 sáng theo cốt/pha (KHÔNG theo đèn vị trí); phanh (không datum) luôn tắt.
        assertEquals(true, vis("body_lamp_15on", "light_low_beam")); assertEquals(false, vis("body_lamp_15on", "light_side"))
        assertEquals(false, vis("body_lamp_16on", "light_low_beam"))
        // 3/4 = pha · 5/6 = cốt hoặc pha · 7/8 = ban ngày/vị trí.
        assertEquals(true, vis("body_lamp_3on", "light_high_beam")); assertEquals(false, vis("body_lamp_3on", "light_low_beam"))
        assertEquals(true, vis("body_lamp_6on", "light_low_beam")); assertEquals(true, vis("body_lamp_6on", "light_high_beam"))
        assertEquals(true, vis("body_lamp_8on", "light_drl")); assertEquals(false, vis("body_lamp_8on", "light_front_fog"))
        // Phần ban ngày của cụm xi-nhan trước theo DRL, KHÔNG theo xi-nhan (lỗi cũ: mẫu `*lamp_turnleft*on` nuốt nó).
        assertEquals(true, vis("body_lamp_turnleftfront4on", "light_drl"))
        assertEquals(false, vis("body_lamp_turnleftfront4on", "light_left_turn"))
        assertEquals(true, vis("body_lamp_turnleftfronton", "light_left_turn")); assertEquals(true, vis("body_lamp_turnleftrear2on", "light_left_turn"))
        assertEquals(false, vis("body_lamp_turnleftfronton", "light_drl"))
        assertNull(vis("body_paint")); assertNull(vis("wheellf_texture"))
    }

    @Test
    fun `mau son doc tu csv cua AVM - tuyen tinh phan tram, den la den`() {
        // Số GIẢ cùng định dạng `car_body_color.csv` (tên,r,g,b,6 tham số) — KHÔNG chép dòng nào của tệp BYD (chính sách 13/10: không byte
        // BYD trong đường git; soát 3.1 Pass 2).
        val csv = "white,85,86,88,1,1,1,1,1,1\r\nblack,3,3,3,1,1,1,1,1,1\r\nred,55,2,2,1,1,1,1,1,1\r\nbad,a,b,c\r\n"
        val p = Car3dPaint.presets(csv)
        assertEquals(listOf("white", "black", "red"), p.map { it.name })
        assertEquals(listOf(0.85, 0.86, 0.88), p[0].rgb)   // màu hiển thị của tệp
        // Lỗi bản đầu: chuẩn hoá theo kênh lớn nhất biến `black` (3,3,3) thành xám 0,9. Nay đen vẫn đen.
        assertTrue(p[1].style.rgba.take(3).all { it < 0.05 }, "${p[1].style.rgba}")
        assertEquals(1.0, p[1].style.rgba[3])
        assertEquals("white", Car3dPaint.pick(p, null)?.name); assertEquals("red", Car3dPaint.pick(p, "red")?.name)
        assertEquals("white", Car3dPaint.pick(p, "pink")?.name); assertNull(Car3dPaint.pick(emptyList(), "red"))
        assertTrue(Car3dPaint.presets(null).isEmpty()); assertTrue(Car3dPaint.presets("white,500,0,0").isEmpty())
        // Ô chọn = màu hiển thị của tệp: đỏ 55 % ⇒ 0x8C kênh R; đen gần đen.
        assertEquals(0x8C, (p[2].argb() shr 16) and 0xFF)
        assertTrue(((p[1].argb() shr 16) and 0xFF) < 0x10)
        // Lỗi "xám không chọn được" (owner 13/10): giá trị là màu HIỂN THỊ ⇒ vật liệu tuyến tính của grey phải TỐI hơn hẳn white
        // (bản trước dùng thẳng /100 làm tuyến tính: grey ≈0,45–0,60 sát white ≈0,85 ⇒ dưới nắng trông như nhau).
        val grey = Car3dPaint.presets("grey,45,50,60,1,1,1,1,1,1").single()
        assertTrue(grey.style.rgba.take(3).all { it < 0.36 }, "${grey.style.rgba}")
        assertTrue(p[0].style.rgba.take(3).all { it > 0.6 }, "${p[0].style.rgba}")
    }

    @Test
    fun `bang car type chep tu ma AVM, duong dan dung khuon`() {
        assertEquals("resource_pi/eke/CarModel/eke/car.mesh", BydAvmModels.of("138")?.meshPath)
        assertEquals("resource_pi/eke/CarModel/eke_right/car.mesh", BydAvmModels.of("138", rightDriver = true)?.meshPath)
        assertEquals("resource_pi/eke/CarModel/eke/wheel.mesh", BydAvmModels.of(" 138 ")?.file("wheel.mesh"))
        assertEquals("resource_pi/hc/CarModel/bev/car.mesh", BydAvmModels.of("92")?.meshPath)
        assertEquals("resource_pi/hc/CarModel/hcef/car.mesh", BydAvmModels.of("135")?.meshPath)
        assertEquals("resource_pi/hc/CarModel/phev/car.mesh", BydAvmModels.of("15")?.meshPath)
        assertEquals("resource_pi/stf/CarModel/fpv/car.mesh", BydAvmModels.of("54")?.meshPath)
        assertEquals("resource_pi/em_3ui/CarModel/em2eu/car.mesh", BydAvmModels.of("186")?.meshPath)
        assertEquals("resource_pi/em_3ui/CarModel/em_3ui/car.mesh", BydAvmModels.of("127")?.meshPath)
        assertEquals("resource_pi/sc2e/CarModel/bev/car.mesh", BydAvmModels.of("128")?.meshPath)
        assertEquals("resource_pi/eqeal/CarModel/car.mesh", BydAvmModels.of("215")?.meshPath)   // không thư mục con
        // AVM rơi mã lạ về "sa20"; ta KHÔNG đoán đời xe.
        assertNull(BydAvmModels.of("0")); assertNull(BydAvmModels.of("999")); assertNull(BydAvmModels.of(null)); assertNull(BydAvmModels.of("abc"))
        BydAvmModels.GROUPS.forEach { (v, g) ->
            val e = BydAvmModels.of(v.toString())!!
            assertTrue(Regex("resource_pi/[a-z0-9_]+/CarModel(/[a-z0-9_]+)?/car\\.mesh").matches(e.meshPath), "$e")
            assertEquals(g, e.group)
        }
    }
}
