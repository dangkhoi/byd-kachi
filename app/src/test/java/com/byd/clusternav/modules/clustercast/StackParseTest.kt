package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test [StackParse] — parser thay 3 helper "first-match wins" cũ.
 *
 * FIXTURE dựng theo ĐÚNG định dạng ActivityManager.StackInfo.toString() trên Android 10, tái hiện tình huống
 * hiện trường 2026-07-21 (BYD Seal): Android Auto có task trên display 0 VÀ task chiếu trên VD 1, cộng 1 stack PIP.
 * Parser cũ "first-match wins" trả về entry đầu tiên gặp → kết luận "chưa bám VD" sai.
 * ⚠ Thứ tự display trong output là theo Z-ORDER và ĐỔI theo thời điểm — không được dựa vào nó
 * (xem đính chính trong [StackParse]); test `pick dung ke ca khi VD in truoc display 0` chốt điều này.
 */
class StackParseTest {

    private val TWO_DISPLAYS = """
        Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
          configuration={1.0 ?mcc0mnc [vi_VN] ldltr sw600dp w960dp h540dp 200dpi winConfig={ mBounds=Rect(0, 0 - 1920, 1080) mWindowingMode=fullscreen mActivityType=standard} s.9}
          taskId=12: com.android.launcher/.Launcher bounds=[0,0][1920,1080] userId=0 visible=true
        Stack id=78 bounds=[0,0][1920,1080] displayId=0 userId=0
          configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=standard} s.4}
          taskId=80: com.byd.androidauto/.MainActivity bounds=[0,0][1920,1080] userId=0 visible=false
        Stack id=90 bounds=[1400,60][1880,330] displayId=0 userId=0
          configuration={1.0 winConfig={ mWindowingMode=pinned mActivityType=standard} s.2}
          taskId=91: anddea.youtube/.WatchWhileActivity bounds=[1400,60][1880,330] userId=0 visible=true
        Stack id=101 bounds=[0,0][1920,720] displayId=1 userId=0
          configuration={1.0 winConfig={ mBounds=Rect(0, 0 - 1920, 720) mWindowingMode=fullscreen mActivityType=standard} s.3}
          taskId=102: com.byd.androidauto/.ProjectionActivity bounds=[0,0][1920,720] userId=0 visible=true
    """.trimIndent()

    @Test
    fun `parse doc duoc moi task kem display va windowing mode`() {
        val e = StackParse.parse(TWO_DISPLAYS)
        assertEquals(4, e.size)
        assertEquals(listOf(0, 0, 0, 1), e.map { it.displayId })
        assertEquals(listOf("fullscreen", "fullscreen", "pinned", "fullscreen"), e.map { it.mode })
        assertEquals(listOf(12, 80, 91, 102), e.map { it.taskId })
        assertEquals("com.byd.androidauto", e[1].pkg)
        assertTrue(e[2].isPinned)
        assertEquals(false, e[1].visible)
    }

    /** ★ HỒI QUY GỐC: app có task ở CẢ display 0 lẫn VD → phải trả đúng VD, bất kể entry nào in trước. */
    @Test
    fun `pick uu tien dung display khi app co nhieu task`() {
        val e = StackParse.parse(TWO_DISPLAYS)
        assertEquals(1, StackParse.displayOf(e, "com.byd.androidauto", preferDisplay = 1))
        assertEquals(102, StackParse.pick(e, "com.byd.androidauto", preferDisplay = 1)!!.taskId)
        // không yêu cầu display cụ thể → lấy entry non-pinned đầu tiên (giữ hành vi cũ)
        assertEquals(80, StackParse.pick(e, "com.byd.androidauto")!!.taskId)
    }

    /** ★ PIP KHÔNG BAO GIỜ được chọn — resize nhầm stack pinned là bắn sai task. */
    @Test
    fun `pick bo qua stack pinned`() {
        val e = StackParse.parse(TWO_DISPLAYS)
        assertNull(StackParse.pick(e, "anddea.youtube"))
        assertEquals(1, StackParse.pinnedOf(e, "anddea.youtube").size)
        assertEquals(91, StackParse.pinnedOf(e, "anddea.youtube")[0].taskId)
    }

    @Test
    fun `evictableOnVd lay dung stack tren VD`() {
        assertEquals(listOf(101), StackParse.evictableOnVd(StackParse.parse(TWO_DISPLAYS), 1).map { it.stackId })
    }

    /** khớp TRỌN tên gói: "com.byd.androidauto" không được dính "com.byd.androidauto2". */
    @Test
    fun `khong dinh prefix ten goi`() {
        val out = """
            Stack id=5 bounds=[0,0][100,100] displayId=0 userId=0
              configuration={1.0 winConfig={ mWindowingMode=fullscreen} s.1}
              taskId=7: com.byd.androidauto2/.Main bounds=[0,0][100,100] userId=0 visible=true
        """.trimIndent()
        val e = StackParse.parse(out)
        assertEquals(1, e.size)
        assertNull(StackParse.pick(e, "com.byd.androidauto"))
        assertEquals(7, StackParse.pick(e, "com.byd.androidauto2")!!.taskId)
    }

    @Test
    fun `output rong hoac rac khong nem`() {
        assertEquals(0, StackParse.parse("").size)
        assertEquals(0, StackParse.parse("Error: could not connect\nrandom junk").size)
        assertEquals(-1, StackParse.displayOf(StackParse.parse(""), "com.foo"))
    }

    /** mode đọc không được (firmware in khác) → KHÔNG bị coi là pinned, vẫn dùng được. */
    @Test
    fun `thieu dong configuration van parse duoc task`() {
        val out = """
            Stack id=3 bounds=[0,0][1920,720] displayId=1 userId=0
              taskId=9: vn.vietmap.app/.MainActivity bounds=[0,0][1920,720] userId=0 visible=true
        """.trimIndent()
        val e = StackParse.parse(out)
        assertEquals(1, e.size)
        assertEquals("", e[0].mode)
        assertTrue(!e[0].isPinned)
        assertEquals(9, StackParse.pick(e, "vn.vietmap.app", preferDisplay = 1)!!.taskId)
    }

    /** bounds của stack phải parse được — [CastShell.stackResize] dựa vào nó để KIỂM CHỨNG lệnh resize có ăn thật. */
    @Test fun `parse doc duoc bounds cua stack`() {
        val e = StackParse.parse(TWO_DISPLAYS)
        assertEquals(listOf(0, 0, 1920, 1080), e[0].bounds!!.toList())
        assertEquals(listOf(1400, 60, 1880, 330), e[2].bounds!!.toList())   // stack pinned (PIP)
        assertEquals(listOf(0, 0, 1920, 720), e[3].bounds!!.toList())       // stack trên VD cụm
    }

    @Test fun `bounds null khi dong stack khong co bounds`() {
        val out = """
            Stack id=7 displayId=1 userId=0
              taskId=8: com.foo/.Main userId=0 visible=true
        """.trimIndent()
        assertNull(StackParse.parse(out)[0].bounds)
    }

    /** thứ tự display trong output KHÔNG cố định (z-order) → pick phải bám displayId, không bám vị trí. */
    @Test fun `pick dung ke ca khi VD in truoc display 0`() {
        val vdFirst = """
            Stack id=101 bounds=[0,0][1920,720] displayId=1 userId=0
              configuration={1.0 winConfig={ mWindowingMode=fullscreen} s.1}
              taskId=102: com.byd.androidauto/.ProjectionActivity bounds=[0,0][1920,720] userId=0 visible=true
            Stack id=78 bounds=[0,0][1920,1080] displayId=0 userId=0
              configuration={1.0 winConfig={ mWindowingMode=fullscreen} s.2}
              taskId=80: com.byd.androidauto/.MainActivity bounds=[0,0][1920,1080] userId=0 visible=false
        """.trimIndent()
        val e = StackParse.parse(vdFirst)
        assertEquals(102, StackParse.pick(e, "com.byd.androidauto", preferDisplay = 1)!!.taskId)
        assertEquals(80, StackParse.pick(e, "com.byd.androidauto", preferDisplay = 0)!!.taskId)
    }

    // ── v0.42: HỒI QUY CHO LỖI ĐƠ LAUNCHER (Dudu) ──────────────────────────────────────────────
    // Bản cũ bê MỌI stack có displayId>=1 về display 0. Kéo nhầm stack home/PIP/OEM là launcher đứng
    // hình, nặng thì stack thành mồ côi và chỉ khởi động lại đầu xe mới cứu được.

    private val MIXED_VD = """
        Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
          configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=home} s.9}
          taskId=1: com.dudu.launcher/.Home bounds=[0,0][1920,1080] userId=0 visible=true
        Stack id=50 bounds=[0,0][1920,720] displayId=1 userId=0
          configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=home} s.1}
          taskId=51: com.byd.cluster/.ClusterHome bounds=[0,0][1920,720] userId=0 visible=true
        Stack id=60 bounds=[1400,60][1880,330] displayId=1 userId=0
          configuration={1.0 winConfig={ mWindowingMode=pinned mActivityType=standard} s.2}
          taskId=61: anddea.youtube/.Watch bounds=[1400,60][1880,330] userId=0 visible=true
        Stack id=70 bounds=[0,0][1920,720] displayId=1 userId=0
          configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=standard} s.3}
          taskId=71: vn.vietmap.live/.MainActivity bounds=[0,0][1920,720] userId=0 visible=true
        Stack id=80 bounds=[0,0][1920,720] displayId=2 userId=0
          configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=standard} s.4}
          taskId=81: com.hud.app/.Main bounds=[0,0][1920,720] userId=0 visible=true
    """.trimIndent()

    @Test fun `evictableOnVd CHI lay app thuong dung display, KHONG home KHONG pinned`() {
        val e = StackParse.parse(MIXED_VD)
        val ev = StackParse.evictableOnVd(e, 1)
        assertEquals(listOf(70), ev.map { it.stackId })          // chỉ Vietmap
        // stack home của cụm (50), PIP (60) và display khác (80) đều KHÔNG được đụng
        assertTrue(ev.none { it.stackId == 50 || it.stackId == 60 || it.stackId == 80 })
    }

    @Test fun `evictableOnVd rong khi vd khong hop le — khong bao gio quet mu`() {
        val e = StackParse.parse(MIXED_VD)
        assertEquals(0, StackParse.evictableOnVd(e, -1).size)
        assertEquals(0, StackParse.evictableOnVd(e, 0).size)
    }

    @Test fun `untouchableOnVd bao dung nhung gi bi giu lai`() {
        val u = StackParse.untouchableOnVd(StackParse.parse(MIXED_VD), 1)
        assertEquals(listOf(50, 60), u.map { it.stackId }.sorted())
    }

    @Test fun `doc duoc mActivityType va phan loai dung`() {
        val e = StackParse.parse(MIXED_VD)
        val home = e.first { it.stackId == 50 }
        assertEquals("home", home.activityType)
        assertTrue(home.isSystemStack); assertTrue(!home.isStandard)
        val map = e.first { it.stackId == 70 }
        assertTrue(map.isStandard); assertTrue(!map.isSystemStack)
    }

    /** firmware không in mActivityType → coi là standard (giữ hành vi cũ), nhưng pinned vẫn phải bị loại. */
    @Test fun `thieu mActivityType van coi la standard`() {
        val out = """
            Stack id=9 bounds=[0,0][1920,720] displayId=1 userId=0
              configuration={1.0 winConfig={ mWindowingMode=fullscreen} s.1}
              taskId=10: com.foo/.Main bounds=[0,0][1920,720] userId=0 visible=true
        """.trimIndent()
        assertEquals(listOf(9), StackParse.evictableOnVd(StackParse.parse(out), 1).map { it.stackId })
    }

    // ── W2-3: chọn nhánh warm/cold bằng SỰ THẬT, không bằng cờ RAM ──

    /** Tiến trình vừa bị kill: cờ casting=false, nhưng cụm ĐANG có app → phải là WARM, không được chiếu lại từ đầu. */
    @Test fun `co app tren cum thi la WARM du co RAM da mat`() {
        assertTrue(StackParse.isWarm(1, StackParse.parse(TWO_DISPLAYS)))
    }

    /** Ca hiện trường 21/07: VD tồn tại nhưng RỖNG (03-vd-stacks.txt trống) → phải là COLD. */
    @Test fun `VD ton tai nhung rong thi la COLD`() {
        val onlyD0 = """
            Stack id=78 bounds=[0,0][1920,1080] displayId=0 userId=0
              configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=standard} s.1}
              taskId=80: com.byd.androidauto/.MainActivity bounds=[0,0][1920,1080] userId=0 visible=true
        """.trimIndent()
        assertFalse(StackParse.isWarm(1, StackParse.parse(onlyD0)))
    }

    /** Chỉ có stack home trên cụm (chưa chiếu gì) → COLD, nếu không lần chiếu đầu sau nổ máy bỏ qua opcode 30/35. */
    @Test fun `chi co home tren cum thi van la COLD`() {
        val homeOnly = """
            Stack id=50 bounds=[0,0][1920,720] displayId=1 userId=0
              configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=home} s.1}
              taskId=51: com.byd.cluster/.ClusterHome bounds=[0,0][1920,720] userId=0 visible=true
        """.trimIndent()
        assertFalse(StackParse.isWarm(1, StackParse.parse(homeOnly)))
    }

    @Test fun `vd khong hop le thi khong bao gio WARM`() {
        assertFalse(StackParse.isWarm(-1, StackParse.parse(TWO_DISPLAYS)))
        assertFalse(StackParse.isWarm(0, StackParse.parse(TWO_DISPLAYS)))
    }

    /**
     * ★ W2-4: Android 12 (DiLink5) in "RootTask id=" thay vì "Stack id=". Parser phải nhận CẢ HAI, nếu không
     * mọi kiểm-chứng-bằng-sự-thật trên DL5 đều thấy "không có gì ở đâu" và cast tự huỷ với thông báo sai nguyên nhân.
     */
    @Test fun `parse duoc phuong ngu RootTask cua Android 12`() {
        val a12 = """
            RootTask id=101 bounds=[0,0][1920,720] displayId=1 userId=0
              configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=standard} s.3}
              taskId=102: vn.vietmap.live/.MainActivity bounds=[0,0][1920,720] userId=0 visible=true
            RootTask id=78 bounds=[0,0][1920,1080] displayId=0 userId=0
              configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=home} s.1}
              taskId=80: com.dudu.launcher/.Home bounds=[0,0][1920,1080] userId=0 visible=true
        """.trimIndent()
        val e = StackParse.parse(a12)
        assertEquals(2, e.size)
        assertEquals(listOf(101, 78), e.map { it.stackId })
        assertEquals("vn.vietmap.live", e[0].pkg)
        assertEquals("home", e[1].activityType)
        // và các phép lọc an toàn vẫn đúng trên phương ngữ mới
        assertEquals(listOf(101), StackParse.evictableOnVd(e, 1).map { it.stackId })
        assertTrue(StackParse.isWarm(1, e))
    }
}
