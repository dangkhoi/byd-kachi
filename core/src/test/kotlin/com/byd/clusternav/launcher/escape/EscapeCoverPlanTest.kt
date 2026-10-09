package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.escape.EscapeCoverPlan.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R7 — hình học lớp che + bảng mã + dấu bền, bằng bản đọc THẬT:
 *  - `window-windows-oncar-2026-09-14-bars.txt` — trích nguyên văn `docs/diagnostics/carlog-kachi-20260914-2044/10-window-windows.txt`
 *    (xe Seal): `StatusBar` `[0,0][1920,84]`, `NavigationBar0` `[0,990][1920,1080]`, kèm `ScreenDecorOverlay*`/`BydQSBar` (không tính).
 *  - `window-windows-emulator-2026-10-09-r7-freeform.txt` — máy ảo A10, Waze freeform `[19,89][1901,985]`, chỉ có thanh trạng thái 36 px.
 *  - `parcel-emulator-2026-10-09-r7-*.txt` — nguyên văn `service call activity_task 59/89` (task 301 / 99999).
 */
class EscapeCoverPlanTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture core/src/test/resources/diagnostics/$name.txt")

    @Test
    fun `khung hai thanh BYD doc tu cua so that tren xe`() {
        val b = EscapeCoverPlan.parseBars(fixture("window-windows-oncar-2026-09-14-bars"))
        assertEquals(PxRect(0, 0, 1920, 84), b.status)
        assertEquals(PxRect(0, 990, 1920, 1080), b.nav)
    }

    @Test
    fun `may ao chi co thanh trang thai, cua so app freeform khong bi nham la thanh`() {
        val b = EscapeCoverPlan.parseBars(fixture("window-windows-emulator-2026-10-09-r7-freeform"))
        assertEquals(PxRect(0, 0, 1920, 36), b.status)
        assertNull(b.nav)
        assertEquals(EscapeCoverPlan.Bars.NONE, EscapeCoverPlan.parseBars("rác"))
        assertEquals(EscapeCoverPlan.Bars.NONE, EscapeCoverPlan.parseBars(fixture("window-windows-oncar-2026-09-14-bars"), display = 2))
    }

    @Test
    fun `vung che thanh tieu de bang dung do cao do tren may ao`() {
        // [ĐO máy ảo] thanh tiêu đề 89..152 (64 px @ 240 dpi) ⇒ che 65 px (43 dp làm tròn lên) — phủ hết, thừa 1 px nội dung.
        assertEquals(65, EscapeCoverPlan.captionPx(240))
        assertTrue(EscapeCoverPlan.captionPx(240) >= 64)
        val screen = PxRect(0, 0, 1920, 1080)
        val c = EscapeCoverPlan.covers(PxRect(19, 89, 1901, 985), EscapeCoverPlan.Bars(PxRect(0, 0, 1920, 84), PxRect(0, 990, 1920, 1080)), 240, screen)
        // Khung task = khung ô (không lớp che / dấu cũ) ⇒ cả thanh tiêu đề nằm trong ô = GAP (nền sau ô — owner 09/10 bỏ "thanh xám").
        assertEquals(listOf(Kind.GAP, Kind.STATUS, Kind.NAV, Kind.EDGE, Kind.EDGE, Kind.EDGE, Kind.EDGE), c.map { it.kind })
        assertEquals(PxRect(19, 89, 1901, 154), c[0].rect)
        assertEquals(listOf(Kind.GAP, Kind.EDGE, Kind.EDGE, Kind.EDGE, Kind.EDGE),
            EscapeCoverPlan.covers(PxRect(19, 89, 1901, 985), EscapeCoverPlan.Bars.NONE, 240, screen).map { it.kind })
    }

    @Test
    fun `vien tay nam doi co phu dung vung cham do tren may ao`() {
        // [ĐO máy ảo 09/10 `dumpsys input`] Waze freeform [19,89][1901,985] ⇒ touchableRegion=[0,44][1920,1030].
        val edges = EscapeCoverPlan.covers(PxRect(19, 89, 1901, 985), EscapeCoverPlan.Bars.NONE, 240, PxRect(0, 0, 1920, 1080))
            .filter { it.kind == Kind.EDGE }.map { it.rect }
        assertEquals(
            listOf(PxRect(0, 44, 1920, 89), PxRect(0, 985, 1920, 1030), PxRect(0, 89, 19, 985), PxRect(1901, 89, 1920, 985)),
            edges,
        )
        assertNull(EscapeCoverPlan.clip(PxRect(-50, 0, -1, 10), PxRect(0, 0, 1920, 1080)))
    }

    @Test
    fun `parcel ma 59 va 89 doc dung nguyen van`() {
        assertEquals(PxRect(19, 89, 1901, 985), TaskBinderCodes.parseRect(fixture("parcel-emulator-2026-10-09-r7-bounds-slot")))
        assertEquals(PxRect(0, 0, 1920, 1080), TaskBinderCodes.parseRect(fixture("parcel-emulator-2026-10-09-r7-bounds-fullscreen")))
        assertNull(TaskBinderCodes.parseRect(fixture("parcel-emulator-2026-10-09-r7-bounds-notask")), "task không có ⇒ khung rỗng ⇒ null")
        assertTrue(TaskBinderCodes.parseOk(fixture("parcel-emulator-2026-10-09-r7-mode-void")))
        // Ngoại lệ (SecurityException…) — từ đầu khác 0 ⇒ không phải khung, không phải OK.
        val ex = "Result: Parcel(\n  0x00000000: ffffffff 0000004a 00650053 00750063 '....J...S.e.c.u.'\n)"
        assertNull(TaskBinderCodes.parseRect(ex))
        assertFalse(TaskBinderCodes.parseOk(ex))
        assertFalse(TaskBinderCodes.parseOk("service: not found"))
        // Chữ giống hex trong phần ASCII không được lẫn vào.
        assertEquals(listOf(0), TaskBinderCodes.words("Result: Parcel(00000000    'deadbeef')"))
    }

    @Test
    fun `bang ma chi dung dung doi API da do`() {
        assertTrue(TaskBinderCodes.ANDROID_10_R47.usableOn(29))
        assertFalse(TaskBinderCodes.ANDROID_10_R47.usableOn(31), "DL5 Android 12 ⇒ mã khác")
        assertFalse(TaskBinderCodes.ANDROID_10_R47.usableOn(30))
    }

    @Test
    fun `dau ben ma hoa giai ma va bo muc la`() {
        val a = EscapeMarker("com.waze", 0, 301, PxRect(19, 89, 1901, 985))
        val b = EscapeMarker("com.foo.bar", 2, 7, PxRect(0, 100, 900, 1000))
        val raw = EscapeMarkers.encode(listOf(a, b))
        assertEquals("com.waze|0|301|19,89,1901,985;com.foo.bar|2|7|0,100,900,1000", raw)
        assertEquals(listOf(a, b), EscapeMarkers.decode(raw))
        assertEquals(listOf(a), EscapeMarkers.decode("$raw;x|y;com.bad pkg|0|1|0,0,1,1;com.z|0|0|0,0,10,10;com.e|0|5|0,0,0,0".replace(";com.foo.bar|2|7|0,100,900,1000", "")))
        assertEquals(emptyList<EscapeMarker>(), EscapeMarkers.decode(null))
        val moved = a.copy(slot = 1)
        assertEquals(listOf(b, moved), EscapeMarkers.upsert(listOf(a, b), moved))
        assertEquals(listOf(b), EscapeMarkers.remove(listOf(a, b), "com.waze"))
        val many = (1..12).map { EscapeMarker("com.p$it", 0, it, PxRect(0, 0, 10, 10)) }
        assertEquals(EscapeMarkers.MAX, EscapeMarkers.decode(EscapeMarkers.encode(many)).size)
    }
}
