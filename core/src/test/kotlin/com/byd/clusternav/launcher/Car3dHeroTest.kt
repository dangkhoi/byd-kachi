package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R28 — widget xe 3D là hero: icon ghế theo mức thật + vòng xoay, nhãn lốp chỉ ở bánh phía thấy, hàng chân lùi khi hẹp,
 * bố cục mặc định cho cài mới, màu thêm của Seal, công tắc số lốp theo hồ sơ.
 */
class Car3dHeroTest {

    private val cam34 = Car3dCamera(V3.ZERO, 8.0, 40.0, 16.0, 30.0)   // ¾ trước-TRÁI (mặc định widget)
    private val camRight = Car3dCamera(V3.ZERO, 8.0, -140.0, 16.0, 30.0)   // ¾ sau-PHẢI

    @Test
    fun `ghe mat doc tho OFF=1 muc1=2 muc2=3 thanh chi so lua chon cua o SELECT`() {
        val car = CarStatus(climate = CarStatus.Climate(seatVentRaw = 3, seatHeatRaw = 1, seatVentRRaw = 2, seatHeatRRaw = null))
        val m = Car3dHero.seatControls(car)
        assertEquals(2, m["seatc"]); assertEquals(0, m["seath"]); assertEquals(1, m["seatc_r"])
        assertFalse("seath_r" in m, "chưa đọc ⇒ không khoá (ô giữ lệnh cuối, không bịa 0)")
    }

    @Test
    fun `muc ngoai so lua chon cua nut thi bo (khong ep vao o)`() {
        // `seath` [SUY] 4 mức thô (1..4) nhưng nút chỉ có Tắt/Mức 1/Mức 2 ⇒ mức 3 không có ô ⇒ không khoá.
        val m = Car3dHero.seatControls(CarStatus(climate = CarStatus.Climate(seatHeatRaw = 4)))
        assertFalse("seath" in m)
    }

    @Test
    fun `cham icon ghe xoay vong tat 1 2 tat tu muc dang thay`() {
        assertEquals(1, Car3dHero.nextSeat(0, 3)); assertEquals(2, Car3dHero.nextSeat(1, 3)); assertEquals(0, Car3dHero.nextSeat(2, 3))
        assertEquals(1, Car3dHero.nextSeat(null, 3), "chưa đọc ⇒ coi như tắt ⇒ mức 1")
        // Cùng phép với ô SELECT thật (không vòng thứ hai).
        for (l in 0..2) assertEquals(ControlTileLogic.nextSelectIndex(l, 3), Car3dHero.nextSeat(l, 3))
        assertEquals(3, ControlRegistry.byId("seatc")!!.args.size); assertEquals(3, ControlRegistry.byId("seath_r")!!.args.size)
    }

    @Test
    fun `ghe phia khuat an, phia thay hien`() {
        val d = V3(0.43, 0.15, 0.2); val p = V3(-0.43, 0.15, 0.2)
        assertTrue(Car3dHero.seatVisible(cam34, d, Car3dHero.Seat.DRIVER.side))
        assertFalse(Car3dHero.seatVisible(cam34, p, Car3dHero.Seat.PASSENGER.side), "¾ trước-trái: ghế phụ khuất sau thân")
        assertTrue(Car3dHero.seatVisible(camRight, p, Car3dHero.Seat.PASSENGER.side))
        val top = Car3dCamera(V3.ZERO, 8.0, 40.0, 70.0, 30.0)
        assertTrue(Car3dHero.seatVisible(top, p, Car3dHero.Seat.PASSENGER.side), "nhìn từ trên ⇒ thấy cả hai ghế")
    }

    @Test
    fun `hai cum icon ghe khong de nhau, giu thu tu trai phai`() {
        assertEquals(100f to 400f, Car3dHero.spread(100f, 400f, 160f), "đủ xa ⇒ giữ nguyên")
        val (a, b) = Car3dHero.spread(500f, 620f, 160f)
        assertEquals(160f, b - a, 1e-3f); assertEquals(560f, (a + b) / 2, 1e-3f)
        val (c, d) = Car3dHero.spread(620f, 500f, 160f)
        assertTrue(c > d, "thứ tự trái/phải giữ nguyên"); assertEquals(160f, c - d, 1e-3f)
    }

    @Test
    fun `diem ghe lui vao trong tu cua truoc`() {
        val s = Car3dHero.seatPoint(V3(0.85, -0.14, 0.35), V3(0.71, 0.44, 0.24))
        assertTrue(s.x in 0.3..0.6, "giữa sườn và tâm: ${s.x}")
        assertTrue(s.y > -0.14 && s.y < 0.44)
    }

    @Test
    fun `bon diem cham dat banh tu hop hop wheel - trai la +X, dau xe +Z`() {
        val w = Car3dHero.wheels(V3(-0.9, -0.74, -1.74), V3(0.9, -0.03, 1.9))
        val fl = w.getValue(TyreCorner.FRONT_LEFT); val rr = w.getValue(TyreCorner.REAR_RIGHT)
        assertEquals(0.9, fl.x, 1e-9); assertEquals(-0.74, fl.y, 1e-9); assertTrue(fl.z > 1.0)
        assertEquals(-0.9, rr.x, 1e-9); assertTrue(rr.z < -1.0)
    }

    @Test
    fun `nhan lop chi o banh phia thay`() {
        val w = Car3dHero.wheels(V3(-0.9, -0.74, -1.74), V3(0.9, -0.03, 1.9))
        val seen = w.filter { (c, p) -> Car3dHero.tyreVisible(cam34, p, c) }.keys
        assertEquals(setOf(TyreCorner.FRONT_LEFT, TyreCorner.REAR_LEFT), seen, "¾ trước-trái: chỉ hai bánh TRÁI")
        val seenR = w.filter { (c, p) -> Car3dHero.tyreVisible(camRight, p, c) }.keys
        assertEquals(setOf(TyreCorner.FRONT_RIGHT, TyreCorner.REAR_RIGHT), seenR)
    }

    @Test
    fun `hang chan an bo chinh nhiet gio truoc khi de nhau`() {
        // Bề rộng ĐO của cụm: bộ chỉnh nhiệt 302 · 3 nút kính 246 · bộ chỉnh gió 332 · lấy gió 72; khe nhóm 36.
        val all = listOf(302, 246, 332, 72); val need = all.sum() + 3 * 36
        assertTrue(Car3dHero.fits(need, all, 36))
        assertFalse(Car3dHero.fits(need - 1, all, 36), "thiếu 1 px ⇒ ẩn bộ chỉnh (không đè)")
        assertTrue(Car3dHero.fits(246 + 72 + 36, listOf(246, 72), 36))
        assertTrue(Car3dHero.fits(0, emptyList(), 36))
    }

    @Test
    fun `bo cuc mac dinh cai moi - xe 3D o o lon dau, bo cuc da luu khong doi`() {
        assertEquals(SlotContent.Widget("w_car3d"), WorkspaceState.DEFAULT.slots[0])
        val saved = WorkspaceState.of(LayoutPreset.THREE, SlotContent.Widget("w_board"))
        assertEquals(saved, WorkspaceDefault.resolve(saved, everSaved = true))
        assertEquals(WorkspaceState.DEFAULT, WorkspaceDefault.resolve(WorkspaceState.of(LayoutPreset.THREE), everSaved = false))
    }

    @Test
    fun `xe 3D doc so lop + ghe va o nut hang chan`() {
        val ws = WorkspaceState.of(LayoutPreset.ONE, SlotContent.Widget("w_car3d"))
        val st = HomeUiState(workspace = ws, topStrip = TopStripConfig(ids = emptyList()), dock = DockConfig(enabled = emptyList()))
        val datums = CarDataDemand.of(st)!!
        assertTrue(datums.containsAll(listOf("tyre_p_fl", "tyre_t_rr", "seat_vent_state", "seat_heat_state_r")))
        assertTrue(CarDataDemand.controlsOf(st).containsAll(listOf("temp", "fan", "ac_auto", "recirc")))
        val off = CarDataDemand.of(st.copy(colorChoice = ColorChoice(carTyres = false)))!!
        assertTrue(CarDataDemand.CAR3D_TYRES.none { it in off }, "tắt số lốp ⇒ không đọc lốp (không bề mặt nào khác cần)")
    }

    @Test
    fun `mau them cua Seal xep sau mau tep, tep rong thi khong co hang`() {
        val file = Car3dPaint.presets("white,86,87,89\ngrey,46,52,62")
        val seal = Car3dCatalog.forAvm("eke").extraPaints
        assertEquals(listOf("white", "grey", Car3dCatalog.SEAL_STEEL), Car3dPaint.withExtras(file, seal).map { it.name })
        assertTrue(Car3dPaint.withExtras(emptyList(), seal).isEmpty())
        assertTrue(Car3dCatalog.forAvm("hc").extraPaints.isEmpty(), "chỉ Seal có màu thêm")
        val c = Car3dCatalog.SEAL_STEEL_RGB
        assertTrue(c[2] > c[1] && c[1] > c[0], "xanh thép: B > G > R")
    }

    @Test
    fun `cong tac so lop theo ho so - mac dinh bat, chi ghi khi tat, giai ma lai dung`() {
        assertTrue(ColorChoice.DEFAULT.carTyres)
        assertEquals(ColorChoice.DEFAULT.encode(), ColorChoice(carTyres = true).encode(), "bật ⇒ chuỗi không đổi byte")
        val off = ColorChoice(carPaint = "grey", carTyres = false)
        assertFalse(ColorChoice.decode(off.encode()).carTyres)
        assertEquals("grey", ColorChoice.decode(off.encode()).carPaint)
        assertNull(ColorChoice.decode(off.encode()).carBackdrop)
        assertTrue(ColorChoice.decode("KACHI_BLUE;NEUTRAL").carTyres)
    }
}
