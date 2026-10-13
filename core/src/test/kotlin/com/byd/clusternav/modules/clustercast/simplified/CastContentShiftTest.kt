package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1 · R6 — phần THUẦN của "Dịch nội dung": bộ đọc khung cửa sổ + phán quyết, khoá bằng dump THẬT (nguyên văn) của phép đo máy
 * ảo A10 13/10 (`docs/diagnostics/emu-overscan-neg-2026-10-13/`, chép y byte vào `src/test/resources/diagnostics/…`).
 *
 * Khoá hai điều: (1) ca DỜI (Chrome FirstRun — không `LAYOUT_IN_SCREEN`) ⇒ phán "nhận"; ca KHÔNG dời (Google Maps / Settings —
 * activity thường) ⇒ phán "không nhận"; (2) BẪY: các dump này đều có `mUnrestricted=[0,-T]…`, `mDisplayInfoOverscan=[0,-T]` dù
 * app không dời — bộ đọc chỉ được nhìn khối cửa sổ của ĐÚNG gói trên ĐÚNG display.
 */
class CastContentShiftTest {

    private fun fx(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/diagnostics/emu-overscan-neg-2026-10-13/$name.txt")) { "thiếu fixture $name" }
            .bufferedReader().readText()

    private val chrome = "com.android.chrome"
    private val maps = "com.google.android.apps.maps"
    private val settings = "com.android.settings"

    @Test
    fun `doc khung cua so Chrome FirstRun - khung doi theo overscan am`() {
        assertEquals(CastContentShift.WindowProbe(36, 0), CastContentShift.probe(fx("chrome-full-os0"), chrome, 1))
        assertEquals(CastContentShift.WindowProbe(16, 0), CastContentShift.probe(fx("chrome-full-os40"), chrome, 1))
        assertEquals(CastContentShift.WindowProbe(-24, 0), CastContentShift.probe(fx("chrome-full-os120"), chrome, 1))
    }

    @Test
    fun `ca DOI - Chrome nhan dich -40 va -120`() {
        val base = CastContentShift.probe(fx("chrome-full-os0"), chrome, 1)!!
        assertTrue(CastContentShift.moved(base, CastContentShift.probe(fx("chrome-full-os40"), chrome, 1)!!, -40))
        assertTrue(CastContentShift.moved(base, CastContentShift.probe(fx("chrome-full-os120"), chrome, 1)!!, -120))
        assertFalse(CastContentShift.moved(base, CastContentShift.probe(fx("chrome-full-os40"), chrome, 1)!!, +40), "sai chiều")
    }

    @Test
    fun `ca KHONG doi - Maps va Settings bo qua overscan am du DisplayFrames da doi (bay)`() {
        val dump = fx("maps-full-os40")
        assertTrue(dump.contains("mUnrestricted=[0,-40][1920,720]"), "fixture phải mang bẫy DisplayFrames")
        val m0 = CastContentShift.probe(fx("maps-full-os0"), maps, 1)!!
        val m40 = CastContentShift.probe(dump, maps, 1)!!
        assertEquals(m0, m40)
        assertFalse(CastContentShift.moved(m0, m40, -40))
        val s0 = CastContentShift.probe(fx("settings-full-os0"), settings, 1)!!
        assertFalse(CastContentShift.moved(s0, CastContentShift.probe(fx("settings-full-os120"), settings, 1)!!, -120))
    }

    @Test
    fun `overscan duong di duong inset - Maps +40 la dich that`() {
        val m0 = CastContentShift.probe(fx("maps-full-os0"), maps, 1)!!
        val p40 = CastContentShift.probe(fx("maps-full-osp40"), maps, 1)!!
        assertEquals(CastContentShift.WindowProbe(0, 40), p40)
        assertTrue(CastContentShift.moved(m0, p40, 40))
    }

    @Test
    fun `chi doc dung goi tren dung display`() {
        assertNull(CastContentShift.probe(fx("maps-full-os0"), maps, 2), "display khác")
        assertNull(CastContentShift.probe(fx("maps-full-os0"), "com.google.android.apps.map", 1), "gói là tiền tố của gói khác")
        assertNull(CastContentShift.probe(fx("maps-full-os0"), chrome, 1), "gói không có cửa sổ")
    }

    @Test
    fun `uu tien cua so BASE_APPLICATION`() {
        val dump = """
            |  Window #3 Window{aa u0 com.waze/com.waze.Popup}:
            |    mDisplayId=2 stackId=5 mSession=Session{0 0:u0a1} mClient=x
            |    mAttrs={(0,0)(wrapxwrap) ty=APPLICATION_PANEL
            |    mFrame=[100,300][400,500] last=[100,300][400,500]
            |  Window #4 Window{bb u0 com.waze/com.waze.MainActivity}:
            |    mDisplayId=2 stackId=5 mSession=Session{0 0:u0a1} mClient=x
            |    mAttrs={(0,0)(fillxfill) ty=BASE_APPLICATION
            |    mFrame=[0,-20][1920,720] last=[0,-20][1920,720]
            |    Cur insets: overscan=[0,0][0,0] content=[0,0][0,0]
            |""".trimMargin()
        assertEquals(CastContentShift.WindowProbe(-20, 0), CastContentShift.probe(dump, "com.waze", 2))
    }

    @Test
    fun `gia tri - top, giu ba canh, kep dai`() {
        assertEquals(-120, CastContentShift.topOf("10,-120,10,50"))
        assertEquals(0, CastContentShift.topOf("hỏng"))
        assertEquals("10,-20,10,50", CastContentShift.withTop("10,-120,10,50", -20))
        assertEquals("0,-20,0,0", CastContentShift.withTop(null, -20))
        assertEquals("0,-200,0,0", CastContentShift.withTop("0,0,0,0", -999))
        assertEquals(200, CastContentShift.clamp(5000))
        assertTrue(CastContentShift.moved(CastContentShift.WindowProbe(0, 0), CastContentShift.WindowProbe(0, 0), 0))
    }

    @Test
    fun `chi Android 10 tro xuong co wm overscan`() {
        assertTrue(CastContentShift.usableOn(29))
        assertFalse(CastContentShift.usableOn(30))
        assertFalse(CastContentShift.usableOn(31))
        assertNotNull(ProjectionRecipe.of("AutoContainer", listOf(16), listOf(18), emptyMap(), null, supportsOverscan = true)
            .takeIf { it.supportsOverscan })
        assertFalse(ProjectionRecipe.SEAL_DL3.supportsOverscan, "mặc định TẮT — đường mới chỉ bật khi hồ sơ nói có")
    }
}
