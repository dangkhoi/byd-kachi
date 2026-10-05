package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CLUSTER-THEME-SAFE (2.89, P0) — khoá LUẬT gửi opcode đổi theme cụm.
 *
 * Lỗi hiện trường khoá ở đây [ĐO xe 05/10, `docs/diagnostics/oncar-2026-10-05-slot-cluster.md` §4]: gửi 30/31 qua
 * AutoContainer khi màn ảo cụm còn lớp Android ⇒ SurfaceFlinger `DEAD_OBJECT` ⇒ `system_server` khởi động lại (hai lần);
 * `ClusterBlackActivity` của Kachi nằm lại trên màn ảo sau khi tắt chiếu. Luật: màn ảo trống ⇒ gửi · chỉ ClusterBlack ⇒ gỡ
 * rồi gửi · có lớp lạ ⇒ bỏ · đã gửi cùng theme + màn ảo không đổi ⇒ bỏ · đọc hỏng ⇒ bỏ (không bao giờ đoán).
 *
 * Fixture: dòng `Stack id=… displayId=…` + `configuration={… mActivityType=standard …}` theo đúng định dạng dump xe
 * `docs/diagnostics/carlog-kachi-20260914-2044/10-am-stack-list.txt`; component ClusterBlack đúng cách xe in
 * (`com.byd.launcher/com.byd.clusternav.modules.clustercast.ClusterBlackActivity`, `perf-oncar-2026-09-26/taskbar-window-dump.txt:70`);
 * cửa sổ theo `carlog-kachi-20260914-2044/10-window-windows.txt`. Màn ảo cụm = 8 như [ĐO 05/10] (sau `31`).
 *
 * B1a: các bài A1 chạy ở mức B (cờ `themeOnVacantVd` BẬT — hàm [decide] của bài); ma trận cờ TẮT (mặc định mọi hồ sơ) ở các
 * bài `B1a …`.
 */
class ClusterThemePlanTest {

    private val self = "com.byd.launcher"
    private val black = "$self/com.byd.clusternav.modules.clustercast.ClusterBlackActivity"
    private val vds = setOf(8)

    private val home = """
        Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
         configuration={1.0 100000010byd_theme452mcc1mnc [vi_VN] ldltr sw720dp w1280dp h604dp 240dpi lrg long land night finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1920, 1080) mAppBounds=Rect(0, 0 - 1920, 990) mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=home mAlwaysOnTop=undefined mRotation=ROTATION_0} s.38}
          taskId=4: com.byd.launcher/com.byd.clusternav.launcher.KachiHome bounds=[0,0][1920,1080] userId=0 visible=true topActivity=ComponentInfo{com.byd.launcher/com.byd.clusternav.launcher.KachiHome}
    """.trimIndent()

    private fun clusterStack(stackId: Int, taskId: Int, comp: String, type: String = "standard", mode: String = "freeform") = """
        Stack id=$stackId bounds=[0,0][1920,720] displayId=8 userId=0
         configuration={1.0 winConfig={ mBounds=Rect(0, 0 - 1920, 720) mWindowingMode=$mode mDisplayWindowingMode=fullscreen mActivityType=$type mAlwaysOnTop=undefined mRotation=ROTATION_0} s.3}
          taskId=$taskId: $comp bounds=[0,0][1920,720] userId=0 visible=true topActivity=ComponentInfo{$comp}
    """.trimIndent()

    private val homeWindow = ClusterThemePlan.WindowOnDisplay("com.byd.launcher/com.byd.clusternav.launcher.KachiHome", 0)

    /** Mức B (cờ `themeOnVacantVd` BẬT) — luật A1 đầy đủ; bài B1a truyền `vacantOk = false` (mặc định mọi hồ sơ). */
    private fun decide(
        stackOut: String?,
        windows: List<ClusterThemePlan.WindowOnDisplay>? = listOf(homeWindow),
        marker: ClusterThemePlan.Marker? = null,
        primary: Int = 8,
        afterRemoval: Boolean = false,
        displays: Set<Int>? = vds,
        vacantOk: Boolean = true,
        gap: Long? = null,
    ) = ClusterThemePlan.decide(
        30, displays, primary, marker, stackOut?.let { StackParse.parse(it) }, windows, self, afterRemoval,
        themeOnVacantVd = vacantOk, gapRemainingMs = gap,
    )

    // ── B1a — mức A là đường DUY NHẤT gửi theme khi cờ tắt ─────────────────────────────────────────────────────────

    @Test
    fun `B1a ma tran - co tat - man ao co (trong, ClusterBlack, Maps, chi overlay) deu VD_PRESENT`() {
        val cases = mapOf(
            "trống" to (home to listOf(homeWindow)),
            "chỉ ClusterBlack" to ((home + "\n" + clusterStack(57, 58, black)) to listOf(homeWindow, ClusterThemePlan.WindowOnDisplay(black, 8))),
            "Maps" to ((home + "\n" + clusterStack(60, 61, "com.google.android.apps.maps/com.google.android.maps.MapsActivity")) to listOf(homeWindow)),
            "chỉ overlay (gói trần)" to (home to listOf(homeWindow, ClusterThemePlan.WindowOnDisplay(self, 8))),
        )
        for ((name, c) in cases) {
            val d = decide(c.first, c.second, vacantOk = false)
            assertEquals(ClusterThemePlan.Reason.VD_PRESENT, (d as ClusterThemePlan.Decision.Skip).reason, name)
        }
        // Bản đọc stack/cửa sổ VẮNG (bộ thi hành không đọc khi cờ tắt) vẫn ra VD_PRESENT, không UNREADABLE.
        assertEquals(ClusterThemePlan.Reason.VD_PRESENT, (decide(null, null, vacantOk = false) as ClusterThemePlan.Decision.Skip).reason)
    }

    @Test
    fun `B1a ma tran - chua co man ao cum - GUI ke ca khi khong doc stack-cua so (null)`() {
        assertEquals(ClusterThemePlan.Decision.Send, decide(null, null, displays = emptySet(), primary = -1, vacantOk = false))
        assertEquals(ClusterThemePlan.Decision.Send, decide(null, null, displays = emptySet(), primary = -1, vacantOk = true))
    }

    @Test
    fun `B1a ma tran - display null luon UNREADABLE (truoc moi luat khac)`() {
        for (flag in listOf(true, false)) {
            val d = decide(home, displays = null, vacantOk = flag, gap = 5_000)
            assertEquals(ClusterThemePlan.Reason.UNREADABLE, (d as ClusterThemePlan.Decision.Skip).reason)
        }
    }

    @Test
    fun `B1a - TOO_SOON chan ca muc A, VD_PRESENT dung truoc TOO_SOON`() {
        val a = decide(null, null, displays = emptySet(), primary = -1, gap = 9_000)
        assertEquals(ClusterThemePlan.Reason.TOO_SOON, (a as ClusterThemePlan.Decision.Skip).reason)
        assertTrue(a.detail.contains("9000"), a.detail)
        val b = decide(home, vacantOk = false, gap = 9_000)
        assertEquals(ClusterThemePlan.Reason.VD_PRESENT, (b as ClusterThemePlan.Decision.Skip).reason)
    }

    @Test
    fun `B1a - muc B (co bat) - trong ma TOO_SOON thi bo, khong go`() {
        val d = decide(home + "\n" + clusterStack(57, 58, black), gap = 1_000)
        assertEquals(ClusterThemePlan.Reason.TOO_SOON, (d as ClusterThemePlan.Decision.Skip).reason)
    }

    @Test
    fun `B1a verdict - VD_PRESENT la SKIP_KNOWN, TOO_SOON theo man ao co tu truoc`() {
        fun skip(r: ClusterThemePlan.Reason) = ClusterThemePlan.Decision.Skip(r, "x")
        assertEquals(ThemeVerdict.SKIP_KNOWN, ClusterThemePlan.verdict(skip(ClusterThemePlan.Reason.VD_PRESENT), vdBefore = true))
        assertEquals(ThemeVerdict.SKIP_KNOWN, ClusterThemePlan.verdict(skip(ClusterThemePlan.Reason.TOO_SOON), vdBefore = true))
        assertEquals(ThemeVerdict.ABORT, ClusterThemePlan.verdict(skip(ClusterThemePlan.Reason.TOO_SOON), vdBefore = false))
    }

    @Test
    fun `man ao cum trong - GUI`() {
        assertEquals(ClusterThemePlan.Decision.Send, decide(home))
    }

    @Test
    fun `chua co man ao cum nao (tap rong) - GUI vi khong lop nao co the nam tren no`() {
        assertEquals(ClusterThemePlan.Decision.Send, decide(home, primary = -1, displays = emptySet()))
    }

    @Test
    fun `chi con ClusterBlack cua Kachi - GO dung stack cua no roi moi gui`() {
        val out = home + "\n" + clusterStack(57, 58, black)
        val d = decide(out, listOf(homeWindow, ClusterThemePlan.WindowOnDisplay(black, 8)))
        assertEquals(ClusterThemePlan.Decision.RemovePlaceholder(listOf(57)), d)
    }

    @Test
    fun `sau luot go van con ClusterBlack - KHONG gui (STILL_OCCUPIED)`() {
        val out = home + "\n" + clusterStack(57, 58, black)
        val d = decide(out, afterRemoval = true)
        assertEquals(ClusterThemePlan.Reason.STILL_OCCUPIED, (d as ClusterThemePlan.Decision.Skip).reason)
    }

    @Test
    fun `task la tren man ao cum (05-10 Maps tren display 8) - BO theme, 0 lenh go`() {
        val out = home + "\n" + clusterStack(57, 58, black) + "\n" +
            clusterStack(60, 61, "com.google.android.apps.maps/com.google.android.maps.MapsActivity")
        val d = decide(out)
        assertTrue(d is ClusterThemePlan.Decision.Skip, "có app lạ ⇒ không gửi, không gỡ ClusterBlack vô ích: $d")
        assertEquals(ClusterThemePlan.Reason.FOREIGN, (d as ClusterThemePlan.Decision.Skip).reason)
        assertTrue(d.detail.contains("com.google.android.apps.maps"))
    }

    @Test
    fun `cua so phu (khong phai task) tren man ao cum - BO theme`() {
        val w = listOf(homeWindow, ClusterThemePlan.WindowOnDisplay(self, 8))   // cửa sổ phủ của chính Kachi (tên = gói trần)
        val d = decide(home, w)
        assertEquals(ClusterThemePlan.Reason.FOREIGN, (d as ClusterThemePlan.Decision.Skip).reason)
    }

    @Test
    fun `da gui cung theme trong tien trinh nay va man ao khong doi - BO (SAME_THEME)`() {
        val d = decide(home, marker = ClusterThemePlan.Marker(30, 8))
        assertEquals(ClusterThemePlan.Reason.SAME_THEME, (d as ClusterThemePlan.Decision.Skip).reason)
    }

    @Test
    fun `dau RAM chi duoc DO luot gui - man ao doi id hoac theme khac thi xet lai bang ban doc`() {
        assertEquals(ClusterThemePlan.Decision.Send, decide(home, marker = ClusterThemePlan.Marker(30, 2)))
        assertEquals(ClusterThemePlan.Decision.Send, decide(home, marker = ClusterThemePlan.Marker(31, 8)))
        // Dấu khớp nhưng bản đọc có app lạ: vẫn BỎ — dấu không bao giờ biến thành lý do để gửi.
        val busy = home + "\n" + clusterStack(60, 61, "com.google.android.apps.maps/com.google.android.maps.MapsActivity")
        assertTrue(decide(busy, marker = ClusterThemePlan.Marker(31, 8)) is ClusterThemePlan.Decision.Skip)
    }

    @Test
    fun `khong doc duoc am stack list - BO, khong bao gio doan`() {
        assertEquals(ClusterThemePlan.Reason.UNREADABLE, (decide(null) as ClusterThemePlan.Decision.Skip).reason)
    }

    @Test
    fun `khong doc duoc cua so hoac display - BO`() {
        assertEquals(ClusterThemePlan.Reason.UNREADABLE, (decide(home, windows = null) as ClusterThemePlan.Decision.Skip).reason)
        assertEquals(ClusterThemePlan.Reason.UNREADABLE, (decide(home, displays = null) as ClusterThemePlan.Decision.Skip).reason)
    }

    @Test
    fun `rao go - loai stack trong, home, pinned, lan task khac hay sai display deu tu choi`() {
        val blankType = StackParse.parse(home + "\nStack id=57 bounds=[0,0][1920,720] displayId=8 userId=0\n  taskId=58: $black visible=true")
        assertFalse(ClusterThemePlan.admissible(57, blankType, vds, self), "loại stack trống KHÔNG tính là standard (A12 không tự kiểm)")
        val homeType = StackParse.parse(home + "\n" + clusterStack(57, 58, black, type = "home"))
        assertFalse(ClusterThemePlan.admissible(57, homeType, vds, self))
        val pinned = StackParse.parse(home + "\n" + clusterStack(57, 58, black, mode = "pinned"))
        assertFalse(ClusterThemePlan.admissible(57, pinned, vds, self))
        val mixed = StackParse.parse(home + "\n" + clusterStack(57, 58, black) + "\n  taskId=59: com.other/.A bounds=[0,0][1,1] userId=0 visible=true")
        assertFalse(ClusterThemePlan.admissible(57, mixed, vds, self), "stack lẫn task app khác ⇒ không gỡ")
        val ok = StackParse.parse(home + "\n" + clusterStack(57, 58, black))
        assertFalse(ClusterThemePlan.admissible(57, ok, setOf(2), self), "sai display ⇒ không gỡ")
        assertFalse(ClusterThemePlan.admissible(0, ok, vds, self), "stack 0 không bao giờ")
        assertTrue(ClusterThemePlan.admissible(57, ok, vds, self))
        // ClusterBlack loại lạ ⇒ quyết định cấp cao cũng là BỎ theme (không đoán, không gỡ).
        val d = decide(home + "\n" + clusterStack(57, 58, black, type = "home"))
        assertEquals(ClusterThemePlan.Reason.NOT_REMOVABLE, (d as ClusterThemePlan.Decision.Skip).reason)
    }

    @Test
    fun `placeholder chi la ClusterBlack cua CHINH Kachi - goi khac cung ten lop thi la app la`() {
        assertTrue(ClusterThemePlan.isPlaceholderComp(black, self))
        assertTrue(ClusterThemePlan.isPlaceholderComp("$self/.modules.clustercast.ClusterBlackActivity", self))
        assertFalse(ClusterThemePlan.isPlaceholderComp("com.byd.clusternav/com.byd.clusternav.modules.clustercast.ClusterBlackActivity", self))
        assertFalse(ClusterThemePlan.isPlaceholderComp(self, self), "cửa sổ phủ của Kachi (gói trần) không phải placeholder")
        val other = home + "\n" + clusterStack(57, 58, "com.byd.clusternav/com.byd.clusternav.modules.clustercast.ClusterBlackActivity")
        assertEquals(ClusterThemePlan.Reason.FOREIGN, (decide(other) as ClusterThemePlan.Decision.Skip).reason)
    }

    @Test
    fun `parseWindows - dang dump xe 14-09, dong mDisplayId DAU TIEN cua khoi`() {
        val out = """
              Window #0 Window{739f3cf u0 InputMethod}:
                mDisplayId=0 stackId=0 mSession=Session{b250230 3982:u0a10062} mClient=android.os.BinderProxy@bbadc2e
              Window #6 Window{a4544b6 u0 vn.vietmap.live}:
                mDisplayId=0 stackId=0 mSession=Session{93ef6e6 11258:u0a10134} mClient=android.os.BinderProxy@6aec578
              Window #14 Window{bc165a0 u0 com.byd.launcher/com.byd.clusternav.modules.clustercast.ClusterBlackActivity}:
                mDisplayId=8 stackId=57 mSession=Session{3ca6afa 2650:u0a10138} mClient=android.os.BinderProxy@38756a4
                mDisplayId=0 (dòng lạ thứ hai trong khối — bỏ qua)
        """.trimIndent()
        val w = ClusterThemePlan.parseWindows(out)!!
        assertEquals(listOf(0, 0, 8), w.map { it.displayId })
        assertEquals(black, w.last().name)
        assertNull(ClusterThemePlan.parseWindows(""), "không có tiêu đề cửa sổ nào ⇒ đọc hỏng (null), không phải 'trống'")
    }

    @Test
    fun `placeholderStacks cho luot tat chieu - chi ClusterBlack qua rao, khong dung app khac`() {
        val out = StackParse.parse(home + "\n" + clusterStack(57, 58, black) + "\n" +
            clusterStack(60, 61, "com.google.android.apps.maps/com.google.android.maps.MapsActivity"))
        assertEquals(listOf(57), ClusterThemePlan.placeholderStacks(out, vds, self))
        assertEquals(emptyList<Int>(), ClusterThemePlan.placeholderStacks(out, emptySet(), self))
    }

    /**
     * Review 2.89 Pass 1 · safety-1 — lời đáp cho `ProjectionManager`: "bỏ theme, đi tiếp" CHỈ khi theme cụm đã biết; đọc hỏng
     * hoặc chưa có màn ảo cụm trước lượt mở ⇒ DỪNG (16/35 trong theme gốc = mất km/h trên Seal [ĐO 05/10]).
     */
    @Test
    fun `verdict - SEND, SKIP_KNOWN chi khi theme da biet, con lai ABORT`() {
        fun skip(r: ClusterThemePlan.Reason) = ClusterThemePlan.Decision.Skip(r, "x")
        assertEquals(ThemeVerdict.SEND, ClusterThemePlan.verdict(ClusterThemePlan.Decision.Send, vdBefore = false))
        assertEquals(ThemeVerdict.ABORT, ClusterThemePlan.verdict(skip(ClusterThemePlan.Reason.UNREADABLE), vdBefore = true))
        assertEquals(ThemeVerdict.ABORT, ClusterThemePlan.verdict(skip(ClusterThemePlan.Reason.UNREADABLE), vdBefore = false))
        assertEquals(ThemeVerdict.SKIP_KNOWN, ClusterThemePlan.verdict(skip(ClusterThemePlan.Reason.SAME_THEME), vdBefore = true))
        for (r in listOf(ClusterThemePlan.Reason.FOREIGN, ClusterThemePlan.Reason.STILL_OCCUPIED, ClusterThemePlan.Reason.NOT_REMOVABLE)) {
            assertEquals(ThemeVerdict.SKIP_KNOWN, ClusterThemePlan.verdict(skip(r), vdBefore = true), "$r + màn ảo có từ trước")
            assertEquals(ThemeVerdict.ABORT, ClusterThemePlan.verdict(skip(r), vdBefore = false), "$r + chưa có màn ảo")
        }
        assertEquals(ThemeVerdict.ABORT, ClusterThemePlan.verdict(ClusterThemePlan.Decision.RemovePlaceholder(listOf(57)), vdBefore = true))
    }
}
