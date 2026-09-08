package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit test (off-device, JUnit5) cho AppScale — serialize/parse/nudge/bounds. T-B verify. */
class AppScaleTest {

    @Test fun `default is auto with dpi 200`() {
        val s = AppScale()
        assertEquals(200, s.dpi)
        assertTrue(s.isAuto)
        assertEquals(-1, s.rectL)
    }

    @Test fun `isAuto true when any edge negative`() {
        assertTrue(AppScale(200, 0, 0, 100, -1).isAuto)
        assertTrue(AppScale(200, -1, 0, 100, 100).isAuto)
        assertFalse(AppScale(200, 0, 0, 100, 100).isAuto)
    }

    @Test fun `boundsOn auto returns full VD`() {
        val b = AppScale().boundsOn(1920, 720)
        assertEquals(listOf(0, 0, 1920, 720), b.toList())
    }

    @Test fun `boundsOn configured returns rect`() {
        val b = AppScale(210, 100, 90, 1200, 620).boundsOn(1920, 720)
        assertEquals(listOf(100, 90, 1200, 620), b.toList())
    }

    @Test fun `serialize then parse round-trips`() {
        val cases = listOf(
            AppScale(),
            AppScale(200, -1, -1, -1, -1),
            AppScale(240, 100, 90, 1200, 620),
            AppScale(120, 0, 0, 1920, 720),
        )
        for (s in cases) assertEquals(s, AppScale.parse(s.serialize()))
    }

    @Test fun `parse rejects malformed`() {
        assertNull(AppScale.parse(""))
        assertNull(AppScale.parse("200,-1,-1,-1"))       // 4 phần
        assertNull(AppScale.parse("200,-1,-1,-1,-1,-1")) // 6 phần
        assertNull(AppScale.parse("200,-1,-1,-1,x"))     // không phải số
    }

    // ── overscanOn (v0.35 — fallback size khi freeform chưa sống) ──

    @Test fun `overscanOn auto is zero insets`() {
        assertEquals(listOf(0, 0, 0, 0), AppScale().overscanOn(1920, 720).toList())
    }

    @Test fun `overscanOn maps rect to insets`() {
        // rect [100,40,1820,680] trên 1920×720 → insets [100, 40, 1920−1820, 720−680]
        assertEquals(listOf(100, 40, 100, 40), AppScale(200, 100, 40, 1820, 680).overscanOn(1920, 720).toList())
    }

    @Test fun `overscanOn clamps negative insets from oversized rect`() {
        // rect lưu từ cụm model khác TO HƠN VD hiện tại → không cho inset âm
        assertEquals(listOf(0, 0, 0, 0), AppScale(200, 0, 0, 2400, 900).overscanOn(1920, 720).toList())
    }

    @Test fun `overscanOn full rect is zero insets`() {
        assertEquals(listOf(0, 0, 0, 0), AppScale(200, 0, 0, 1920, 720).overscanOn(1920, 720).toList())
    }

    @Test fun `map serialize then parse round-trips`() {
        val m = linkedMapOf(
            "com.google.android.apps.maps" to AppScale(200, -1, -1, -1, -1),
            "com.apple.carplay" to AppScale(180, 360, 40, 1560, 680),
        )
        val round = AppScale.parseMap(AppScale.serializeMap(m))
        assertEquals(m, round)
    }

    @Test fun `parseMap tolerates blank and broken entries`() {
        val m = AppScale.parseMap("com.a=200,-1,-1,-1,-1||com.b=BAD|=100,0,0,0,0|com.c=210,10,20,30,40")
        assertEquals(2, m.size)                 // com.b (bad) + empty-key entry bỏ
        assertEquals(AppScale(200, -1, -1, -1, -1), m["com.a"])
        assertEquals(AppScale(210, 10, 20, 30, 40), m["com.c"])
    }

    @Test fun `serializeMap empty is blank and parseMap blank is empty`() {
        assertEquals("", AppScale.serializeMap(emptyMap()))
        assertTrue(AppScale.parseMap("").isEmpty())
    }

    @Test fun `nudgeRect taller expands height around center`() {
        // khung 900x500 tại (100,100) → cao +16 quanh tâm dọc
        val s = AppScale(200, 100, 100, 1000, 600).nudgeRect(1920, 720, 0, AppScale.STEP_WH)
        assertEquals(100, s.rectL)                     // ngang không đổi
        assertEquals(1000, s.rectR)
        assertEquals(92, s.rectT)                      // 100 - 8
        assertEquals(608, s.rectB)                     // 600 + 8
        assertEquals(516, s.rectB - s.rectT)           // cao 500 -> 516 (+16)
    }

    @Test fun `nudgeRect narrower shrinks width around center`() {
        val s = AppScale(200, 100, 100, 1000, 600).nudgeRect(1920, 720, -AppScale.STEP_WH, 0)
        assertEquals(108, s.rectL)                     // 100 + 8
        assertEquals(992, s.rectR)                     // 1000 - 8
        assertEquals(884, s.rectR - s.rectL)           // rộng 900 -> 884 (-16)
    }

    @Test fun `nudgeRect from auto materializes full VD then clamps`() {
        // auto + "cao lên" khi đã full → giữ full (không vượt VD)
        val s = AppScale().nudgeRect(1920, 720, 0, AppScale.STEP_WH)
        assertFalse(s.isAuto)
        assertEquals(listOf(0, 0, 1920, 720), s.boundsOn(1920, 720).toList())
    }

    @Test fun `nudgeRect respects MIN_PX floor`() {
        // khung đã rất hẹp, thu tiếp không được nhỏ hơn MIN_PX
        val start = AppScale(200, 800, 300, 800 + AppScale.MIN_PX, 500)
        val s = start.nudgeRect(1920, 720, -AppScale.STEP_WH, 0)
        assertTrue(s.rectR - s.rectL >= AppScale.MIN_PX)
    }

    @Test fun `nudgeDpi clamps to range`() {
        assertEquals(210, AppScale(200).nudgeDpi(10).dpi)
        assertEquals(AppScale.DPI_MIN, AppScale(200).nudgeDpi(-500).dpi)
        assertEquals(AppScale.DPI_MAX, AppScale(200).nudgeDpi(500).dpi)
    }

    @Test fun `nudgeDpi keeps rect`() {
        val s = AppScale(200, 10, 20, 30, 40).nudgeDpi(10)
        assertEquals(10, s.rectL); assertEquals(40, s.rectB)
    }

    @Test fun `nudgeEdge left edge moves independently and recalculates size`() {
        val s = AppScale(200, 100, 100, 1000, 600).nudgeEdge(1920, 720, AppScale.Edge.LEFT, -16)
        assertEquals(84, s.rectL)        // 100 - 16 (rộng ra)
        assertEquals(1000, s.rectR)      // phải GIỮ
        assertEquals(100, s.rectT); assertEquals(600, s.rectB)  // dọc GIỮ
    }

    @Test fun `nudgeEdge right edge inward shrinks width, left kept`() {
        val s = AppScale(200, 100, 100, 1000, 600).nudgeEdge(1920, 720, AppScale.Edge.RIGHT, -16)
        assertEquals(984, s.rectR); assertEquals(100, s.rectL)
    }

    @Test fun `nudgeEdge bottom edge down grows height, top kept`() {
        val s = AppScale(200, 100, 100, 1000, 600).nudgeEdge(1920, 720, AppScale.Edge.BOTTOM, 16)
        assertEquals(616, s.rectB); assertEquals(100, s.rectT)
    }

    @Test fun `nudgeEdge clamps within VD bounds`() {
        assertEquals(0, AppScale(200, 10, 100, 1000, 600).nudgeEdge(1920, 720, AppScale.Edge.LEFT, -999).rectL)
        assertEquals(1920, AppScale(200, 100, 100, 1900, 600).nudgeEdge(1920, 720, AppScale.Edge.RIGHT, 999).rectR)
    }

    @Test fun `nudgeEdge keeps MIN_PX between opposite edges`() {
        val s = AppScale(200, 100, 100, 100 + AppScale.MIN_PX, 600).nudgeEdge(1920, 720, AppScale.Edge.LEFT, 999)
        assertTrue(s.rectR - s.rectL >= AppScale.MIN_PX)
    }

    @Test fun `nudgeEdge from auto materializes full then adjusts one edge`() {
        val s = AppScale().nudgeEdge(1920, 720, AppScale.Edge.RIGHT, -100)
        assertFalse(s.isAuto)
        assertEquals(0, s.rectL); assertEquals(0, s.rectT); assertEquals(720, s.rectB)
        assertEquals(1820, s.rectR)      // full 1920 → 1820
    }

    @Test fun `nudgeMove shifts frame keeping size`() {
        val s = AppScale(200, 100, 100, 1000, 600).nudgeMove(1920, 720, 50, -40)
        assertEquals(150, s.rectL); assertEquals(1050, s.rectR)   // +50 ngang, cỡ 900 giữ
        assertEquals(60, s.rectT); assertEquals(560, s.rectB)     // -40 dọc, cỡ 500 giữ
        assertEquals(900, s.rectR - s.rectL); assertEquals(500, s.rectB - s.rectT)
    }

    @Test fun `nudgeMove clamps to left-top edge keeping size`() {
        val s = AppScale(200, 100, 100, 1000, 600).nudgeMove(1920, 720, -999, -999)
        assertEquals(0, s.rectL); assertEquals(0, s.rectT)        // dán mép trái-trên
        assertEquals(900, s.rectR); assertEquals(500, s.rectB)    // cỡ giữ nguyên
    }

    @Test fun `nudgeMove clamps to right-bottom edge keeping size`() {
        val s = AppScale(200, 100, 100, 1000, 600).nudgeMove(1920, 720, 9999, 9999)
        assertEquals(1920, s.rectR); assertEquals(720, s.rectB)   // dán mép phải-dưới
        assertEquals(1020, s.rectL); assertEquals(220, s.rectT)   // cỡ 900×500 giữ
    }

    @Test fun `nudgeMove from auto is full VD so no room to move`() {
        val s = AppScale().nudgeMove(1920, 720, 50, 50)
        assertFalse(s.isAuto)
        assertEquals(listOf(0, 0, 1920, 720), s.boundsOn(1920, 720).toList())  // full → clamp giữ full
    }

    // ── fit(): khung theo tỉ lệ, căn giữa (v0.36 — sửa gốc "YouTube méo") ──

    @Test fun `fit 16-9 tren cum 1920x720 cho 1280x720 can giua`() {
        val s = AppScale().fit(1920, 720, 16, 9)
        assertFalse(s.isAuto)
        assertEquals(listOf(320, 0, 1600, 720), s.boundsOn(1920, 720).toList())
        assertEquals(1280, s.rectR - s.rectL); assertEquals(720, s.rectB - s.rectT)
        // overscan tương đương chính là con số hiện trường đã mò ra bằng tay
        assertEquals(listOf(320, 0, 320, 0), s.overscanOn(1920, 720).toList())
    }

    @Test fun `fit chua inset doc cho thanh OEM`() {
        val s = AppScale().fit(1920, 720, 16, 9, insetV = 90)
        assertEquals(540, s.rectB - s.rectT)                       // 720 - 2*90
        assertEquals(960, s.rectR - s.rectL)                       // 540 * 16/9
        assertEquals(90, s.rectT); assertEquals(630, s.rectB)      // căn giữa theo chiều dọc
    }

    @Test fun `fit 21-9 rong hon 16-9 nhung khong tran VD`() {
        val w = AppScale().fit(1920, 720, 21, 9)
        assertEquals(1680, w.rectR - w.rectL)
        assertTrue(w.rectR <= 1920 && w.rectL >= 0)
    }

    @Test fun `fit khop theo be ngang khi ti le qua rong`() {
        val s = AppScale().fit(1920, 720, 32, 9)                   // 720*32/9 = 2560 > 1920
        assertEquals(1920, s.rectR - s.rectL)                      // kẹp về bề ngang VD
        assertEquals(540, s.rectB - s.rectT)                       // 1920*9/32
        assertEquals(0, s.rectL)
    }

    @Test fun `fit giu nguyen dpi va bo qua tham so vo ly`() {
        val base = AppScale(dpi = 240)
        assertEquals(240, base.fit(1920, 720).dpi)
        assertEquals(base, base.fit(0, 720))                       // w<=0 → giữ nguyên
        assertEquals(base, base.fit(1920, 720, 0, 9))              // tỉ lệ <=0 → giữ nguyên
    }

    // ── v0.37: nudgeRect phải KẸP KÍCH THƯỚC, không được trôi khung khi chạm sàn ──

    @Test fun `nudgeRect cham san MIN_PX thi DUNG YEN, khong troi`() {
        var s = AppScale().nudgeRect(1920, 720, 0, -2000)     // ép chiều cao xuống sàn
        assertEquals(AppScale.MIN_PX, s.rectB - s.rectT)
        val before = listOf(s.rectL, s.rectT, s.rectR, s.rectB)
        repeat(10) { s = s.nudgeRect(1920, 720, 0, -32) }     // bấm "Thấp" thêm 10 lần
        assertEquals(before, listOf(s.rectL, s.rectT, s.rectR, s.rectB))   // KHÔNG được xê dịch chút nào
    }

    @Test fun `nudgeRect giu tam khi thu nho`() {
        val s = AppScale().nudgeRect(1920, 720, -320, -160)
        assertEquals(1600, s.rectR - s.rectL); assertEquals(560, s.rectB - s.rectT)
        assertEquals((s.rectL + s.rectR) / 2, 960)            // vẫn giữa theo chiều ngang
        assertEquals((s.rectT + s.rectB) / 2, 360)
    }

    @Test fun `nudgeRect noi ra khong vuot VD`() {
        var s = AppScale().nudgeRect(1920, 720, -640, -320)
        repeat(40) { s = s.nudgeRect(1920, 720, 64, 64) }
        assertEquals(1920, s.rectR - s.rectL); assertEquals(720, s.rectB - s.rectT)
        assertEquals(0, s.rectL); assertEquals(0, s.rectT)
    }

    // ── forcedSizeOn / dpiForForcedSize cho tầng `wm size` ──

    @Test fun `forcedSizeOn tra ve dung kich thuoc khung`() {
        val s = AppScale().fit(1920, 720, 16, 9)              // 1280x720
        assertEquals(listOf(1280, 720), s.forcedSizeOn(1920, 720).toList())
    }

    @Test fun `forcedSizeOn khung auto la full VD`() {
        assertEquals(listOf(1920, 720), AppScale().forcedSizeOn(1920, 720).toList())
    }

    @Test fun `dpiForForcedSize bu lai phan phong to`() {
        // 16:9 trên cụm 1920×720 = 1280×720 → LogicalDisplay PILLARBOX: hệ số min(1920/1280, 720/720) = 1.0,
        // tức KHÔNG phóng gì cả (chỉ chừa hai dải đen hai bên) → DPI phải giữ nguyên.
        assertEquals(200, AppScale(dpi = 200).fit(1920, 720, 16, 9).dpiForForcedSize(1920, 720))
        assertEquals(200, AppScale(dpi = 200).dpiForForcedSize(1920, 720))   // auto = full → không phóng
        // thu ĐỀU cả hai chiều còn một nửa (960×360) → phóng thật 2× → dpi 150 → 300
        val half = AppScale(dpi = 150, rectL = 480, rectT = 180, rectR = 1440, rectB = 540)
        assertEquals(300, half.dpiForForcedSize(1920, 720))
        // vượt trần thì kẹp ở DPI_MAX
        assertEquals(
            AppScale.DPI_MAX,
            AppScale(dpi = 300, rectL = 480, rectT = 180, rectR = 1440, rectB = 540).dpiForForcedSize(1920, 720),
        )
    }

    // ── v0.42: preset theo PHẦN TRĂM cụm (thay 16:9 / 21:9 vốn vô nghĩa trên dải 2.667:1) ──

    @Test fun `scaled lay dung phan tram va can giua`() {
        val s = AppScale().scaled(1920, 720, 80)
        assertEquals(1536, s.rectR - s.rectL); assertEquals(576, s.rectB - s.rectT)
        assertEquals(192, s.rectL); assertEquals(72, s.rectT)              // căn giữa cả hai chiều
        assertEquals(listOf(192, 72, 192, 72), s.overscanOn(1920, 720).toList())
    }

    /** ★ Điểm mấu chốt: preset GIỮ NGUYÊN tỉ lệ cụm — đúng thứ 16:9/21:9 không làm được. */
    @Test fun `scaled giu nguyen ti le cum`() {
        for (pct in listOf(90, 80, 70, 50)) {
            val s = AppScale().scaled(1920, 720, pct)
            val ar = (s.rectR - s.rectL).toDouble() / (s.rectB - s.rectT)
            assertTrue(kotlin.math.abs(ar - 1920.0 / 720) < 0.02, "pct=$pct ar=$ar")
        }
    }

    @Test fun `scaled hoat dong tren cum kich thuoc khac`() {
        val s = AppScale().scaled(1280, 480, 75)                          // đời xe khác, cùng 8:3
        assertEquals(960, s.rectR - s.rectL); assertEquals(360, s.rectB - s.rectT)
    }

    @Test fun `scaled kep trong khoang hop le`() {
        assertEquals(1920, AppScale().scaled(1920, 720, 999).let { it.rectR - it.rectL })
        val tiny = AppScale().scaled(1920, 720, 1)
        assertTrue(tiny.rectB - tiny.rectT >= AppScale.MIN_PX)
        assertEquals(AppScale(), AppScale().scaled(0, 720, 80))            // kích thước vô lý → giữ nguyên
    }
}
