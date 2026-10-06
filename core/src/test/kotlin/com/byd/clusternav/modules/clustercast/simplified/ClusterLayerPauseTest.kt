package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.90 · R9 — luật THUẦN "dọn cụm / trả cụm" ([ClusterLayerPause]). Màn ảo cụm = display 4 như buổi xe 06/10
 * (`docs/diagnostics/oncar-2026-10-06-cluster-rect.md` F1/F5). Tên cửa sổ phủ = gói trần (`WindowState.getWindowTag()` r47).
 */
class ClusterLayerPauseTest {

    private val self = "com.byd.launcher"
    private val black = "$self/com.byd.clusternav.modules.clustercast.ClusterBlackActivity"
    private val vds = setOf(4)
    private val home = StackParse.parse(
        "Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0\n" +
            "  taskId=4: com.android.launcher3/com.android.launcher3.Launcher visible=true",
    )

    private fun w(name: String, d: Int = 4) = ClusterThemePlan.WindowOnDisplay(name, d)

    @Test
    fun `badge Kachi + 3 cua so bong VietMap tren cum - don duoc`() {
        val p = ClusterLayerPause.pausable(home, listOf(w(self), w("vn.vietmap.live"), w("vn.vietmap.live"), w("vn.vietmap.live"), w("InputMethod", 0)), vds, self)
        assertEquals(ClusterLayerPause.Pause(1, listOf("VietMap")), p)
    }

    @Test
    fun `chi badge Kachi - don duoc, khong app bong`() {
        assertEquals(ClusterLayerPause.Pause(1, emptyList()), ClusterLayerPause.pausable(home, listOf(w(self)), vds, self))
    }

    @Test
    fun `cua so app LA hoac cua so activity - khong don (luat cu FOREIGN)`() {
        assertNull(ClusterLayerPause.pausable(home, listOf(w(self), w("com.example.other")), vds, self))
        assertNull(ClusterLayerPause.pausable(home, listOf(w("vn.vietmap.live/vn.vietmap.MainActivity")), vds, self))
        assertNull(ClusterLayerPause.pausable(home, listOf(w("$self/com.byd.clusternav.Other")), vds, self), "cửa sổ activity của Kachi không phải lớp phủ")
    }

    @Test
    fun `co TASK la tren cum - khong don`() {
        val maps = StackParse.parse(
            "Stack id=37 bounds=[0,0][1920,720] displayId=4 userId=0\n" +
                "  taskId=40: com.google.android.apps.maps/com.google.android.maps.MapsActivity visible=true",
        )
        assertNull(ClusterLayerPause.pausable(home + maps, listOf(w(self)), vds, self))
    }

    @Test
    fun `khong co gi de don, doc hong, thieu selfPkg - null`() {
        assertNull(ClusterLayerPause.pausable(home, listOf(w(black)), vds, self), "chỉ placeholder ⇒ nhánh gỡ ClusterBlack có sẵn")
        assertNull(ClusterLayerPause.pausable(null, listOf(w(self)), vds, self))
        assertNull(ClusterLayerPause.pausable(home, null, vds, self))
        assertNull(ClusterLayerPause.pausable(home, listOf(w(self)), emptySet(), self))
        assertNull(ClusterLayerPause.pausable(home, listOf(w(self)), vds, ""))
    }

    @Test
    fun `cleared - chi con placeholder hoac trong`() {
        assertTrue(ClusterLayerPause.cleared(home, listOf(w(black), w("InputMethod", 0)), vds, self))
        assertFalse(ClusterLayerPause.cleared(home, listOf(w("vn.vietmap.live")), vds, self))
        assertFalse(ClusterLayerPause.cleared(home, listOf(w(self)), vds, self))
    }

    @Test
    fun `tra cum - id song, show tru khi nguoi lai chu dong an, khong cai thi khong gui`() {
        assertEquals(ClusterLayerPause.Resume(9, true), ClusterLayerPause.resume(9, hiddenByUser = false, installed = true))
        assertEquals(ClusterLayerPause.Resume(9, false), ClusterLayerPause.resume(9, hiddenByUser = true, installed = true))
        assertEquals(ClusterLayerPause.Resume(-1, null), ClusterLayerPause.resume(-1, hiddenByUser = false, installed = false))
        assertEquals(-1, ClusterLayerPause.resume(0, false, true).reattachOn, "không bao giờ display 0")
    }

    @Test
    fun `bubbleWanted - an khi dang don hoac nguoi lai chu dong an, mac dinh HIEN`() {
        // Khoá lỗi review 2.90: mặc định (chưa từng chạm công tắc) ⇒ HIỆN — người nâng cấp không mất bóng như trước 2.90.
        assertTrue(ClusterLayerPause.bubbleWanted(hiddenByUser = false, paused = false))
        assertFalse(ClusterLayerPause.bubbleWanted(hiddenByUser = false, paused = true))
        assertFalse(ClusterLayerPause.bubbleWanted(hiddenByUser = true, paused = false))
    }

    @Test
    fun `oldMod - chi khi quyet lai van la BUBBLE`() {
        assertTrue(ClusterLayerPause.oldMod(ClusterThemePlan.Decision.Skip(ClusterThemePlan.Reason.BUBBLE, "", listOf("VietMap"))))
        assertFalse(ClusterLayerPause.oldMod(ClusterThemePlan.Decision.Send))
        assertFalse(ClusterLayerPause.oldMod(ClusterThemePlan.Decision.Skip(ClusterThemePlan.Reason.FOREIGN, "")))
    }
}
