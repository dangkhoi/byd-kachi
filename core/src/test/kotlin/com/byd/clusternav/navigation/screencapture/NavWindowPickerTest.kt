package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.screencapture.NavWindowPicker.TYPE_APPLICATION
import com.byd.clusternav.navigation.screencapture.NavWindowPicker.WinInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NavWindowPickerTest {

    private val NAV = setOf("com.chisadin.wazemod", "vn.vietmap.live", "com.google.android.apps.maps")
    private val TYPE_OVERLAY = 4   // AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY

    private fun win(pkg: String, l: Int, t: Int, r: Int, b: Int, type: Int = TYPE_APPLICATION, focused: Boolean = false, disp: Int = 0) =
        WinInfo(pkg, type, CropRect(l, t, r, b), disp, focused)

    @Test
    fun `B3_13 - nav window KHONG focus van duoc chon`() {
        // Waze fullscreen nhung KHONG focus (app khac dang focus) -> van chon (doc Waze khi khong active).
        val pick = NavWindowPicker.pick(
            listOf(
                win("com.android.launcher", 0, 0, 960, 720, focused = true),   // focus nhung khong phai nav
                win("com.chisadin.wazemod", 0, 0, 960, 720, focused = false),  // nav, khong focus
            ),
            NAV,
        )
        assertEquals("com.chisadin.wazemod", pick?.pkg)
    }

    @Test
    fun `B3_7 - overlay noi nho THUA app dan lon (chon dung app)`() {
        // VietMap activity fullscreen + WazeMod overlay nho -> chon VietMap (dien tich lon), KHONG phai overlay.
        val pick = NavWindowPicker.pick(
            listOf(
                win("com.chisadin.wazemod", 700, 40, 940, 260, focused = true),  // overlay nho, dang focus
                win("vn.vietmap.live", 0, 0, 960, 720, focused = false),          // app dan full
            ),
            NAV,
        )
        assertEquals("vn.vietmap.live", pick?.pkg)
    }

    @Test
    fun `loai window type khong phai APPLICATION (overlay a11y)`() {
        val pick = NavWindowPicker.pick(
            listOf(win("com.chisadin.wazemod", 0, 0, 960, 720, type = TYPE_OVERLAY)),
            NAV,
        )
        assertNull(pick)
    }

    @Test
    fun `loai pkg khong phai nav + bounds rong`() {
        val pick = NavWindowPicker.pick(
            listOf(
                win("com.android.chrome", 0, 0, 960, 720),
                win("vn.vietmap.live", 100, 100, 100, 100),   // rong (width=0)
            ),
            NAV,
        )
        assertNull(pick)
    }

    @Test
    fun `carry displayId cua window duoc chon`() {
        val pick = NavWindowPicker.pick(
            listOf(win("com.chisadin.wazemod", 0, 0, 480, 360, disp = 1)),
            NAV,
        )
        assertEquals(1, pick?.displayId)
        assertEquals("com.chisadin.wazemod", pick?.pkg)
    }

    @Test
    fun `khong co nav window - null`() {
        assertNull(NavWindowPicker.pick(emptyList(), NAV))
    }
}
