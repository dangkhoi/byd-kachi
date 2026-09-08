package com.byd.clusternav.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The BYD IVI float/overlay allow-list merge. A package absent from the global CSV `byd_float_app_list` is
 * refused an overlay with the "Hệ thống IVI không hỗ trợ hoạt động này" toast, so both the assistant path
 * ([com.byd.clusternav.modules.voicekey.AssistantLauncher]) and the VietMap-bubble path
 * ([com.byd.clusternav.VietMapAutostart]) APPEND to it without clobbering OEM / other entries. These lock
 * that shared merge reproduces the proven inline recipe exactly: trim, drop blanks + literal "null",
 * append verbatim, de-dup first-wins, comma-join, existing-first order.
 */
class FloatAppListTest {

    @Test
    fun `empty current yields just the added packages`() {
        assertEquals(VIETMAP, FloatAppList.merge("", listOf(VIETMAP)))
    }

    @Test
    fun `literal null current (unset key) is dropped, leaving just the added`() {
        // `settings get global byd_float_app_list` prints "null" when the key is unset.
        assertEquals(VIETMAP, FloatAppList.merge("null", listOf(VIETMAP)))
    }

    @Test
    fun `existing packages are preserved, added appended after them (order = existing-first)`() {
        assertEquals(
            "com.oem.launcher,com.oem.dashcam,$VIETMAP",
            FloatAppList.merge("com.oem.launcher,com.oem.dashcam", listOf(VIETMAP)),
        )
    }

    @Test
    fun `blanks and null segments inside the current list are filtered`() {
        assertEquals(
            "com.oem.launcher,$VIETMAP",
            FloatAppList.merge("com.oem.launcher,,null, ", listOf(VIETMAP)),
        )
    }

    @Test
    fun `whitespace around each existing entry is trimmed`() {
        assertEquals(
            "com.a,com.b,$VIETMAP",
            FloatAppList.merge("  com.a , com.b  ", listOf(VIETMAP)),
        )
    }

    @Test
    fun `adding a package already present is a no-op (idempotent, no duplicate)`() {
        assertEquals(
            "com.oem.launcher,$VIETMAP",
            FloatAppList.merge("com.oem.launcher,$VIETMAP", listOf(VIETMAP)),
        )
    }

    @Test
    fun `re-running merge on its own output is stable`() {
        val once = FloatAppList.merge("com.oem.launcher", listOf(VIETMAP))
        val twice = FloatAppList.merge(once, listOf(VIETMAP))
        assertEquals(once, twice)
    }

    @Test
    fun `duplicate entries already in the current list collapse to first occurrence`() {
        assertEquals(
            "com.a,com.b,$VIETMAP",
            FloatAppList.merge("com.a,com.b,com.a", listOf(VIETMAP)),
        )
    }

    @Test
    fun `reproduces the AssistantLauncher three-package recipe exactly`() {
        // AssistantLauncher appends listOf(PKG_GSA, PKG_BARD, self) onto the existing list.
        val gsa = "com.google.android.googlequicksearchbox"
        val bard = "com.google.android.apps.bard"
        val self = "com.byd.clusternav2"
        assertEquals(
            "com.oem.launcher,$gsa,$bard,$self",
            FloatAppList.merge("com.oem.launcher", listOf(gsa, bard, self)),
        )
        // And it stays idempotent when the three are already present (re-open case).
        val full = "com.oem.launcher,$gsa,$bard,$self"
        assertEquals(full, FloatAppList.merge(full, listOf(gsa, bard, self)))
    }

    companion object {
        private const val VIETMAP = "vn.vietmap.live"
    }
}
