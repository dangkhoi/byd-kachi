package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test TEARDOWN-GUARD v0.60 (P0) — [ClusterCast.phoneProjectionSinksOn] + [ClusterCast.isPhoneProjection].
 *
 * HỒI QUY lỗi hiện trường 22/07 (diag-0722-172848, Seal DL3): chiếu CarPlay lên VD cụm → VD bị huỷ/tái tạo
 * (cmd16 re-project / teardownSeq) khi sink CÒN BÁM → cửa sổ mồ côi (WM thấy, AM không) → phải reboot xe.
 * Guard phải PHÁT HIỆN sink chiếu-điện-thoại đang bám VD (từ `am stack list`) để bê ra display 0 TRƯỚC khi
 * huỷ/tái tạo VD. Fixture dựng theo ĐÚNG định dạng ActivityManager.StackInfo.toString() trên Android 10.
 *
 * PURE (parse + predicate) → chạy off-device, KHÔNG cần xe.
 */
class SinkGuardTest {

    // Trạng thái TRƯỚC teardown: CarPlay + Android Auto đang bám VD cụm (display 1); Vietmap + launcher ở đầu xe.
    private val SINK_ON_VD = """
        Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
          configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=home} s.9}
          taskId=5: com.android.launcher3/.Launcher bounds=[0,0][1920,1080] userId=0 visible=true
        Stack id=44 bounds=[0,0][1920,1080] displayId=0 userId=0
          configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=standard} s.4}
          taskId=50: vn.vietmap.live/.MainActivity bounds=[0,0][1920,1080] userId=0 visible=false
        Stack id=66 bounds=[0,0][1920,720] displayId=1 userId=0
          configuration={1.0 winConfig={ mBounds=Rect(0, 0 - 1920, 720) mWindowingMode=fullscreen mActivityType=standard} s.3}
          taskId=71: com.byd.carplay.ui/com.byd.carplay.ui.VideoActivity bounds=[0,0][1920,720] userId=0 visible=true
        Stack id=65 bounds=[0,0][1920,720] displayId=1 userId=0
          configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=standard} s.3}
          taskId=70: com.byd.androidauto/com.google.android.projection.sink.ui.AAPVideoActivity bounds=[0,0][1920,720] userId=0 visible=true
    """.trimIndent()

    @Test
    fun `phat hien sink CP va AA dang bam VD cum`() {
        val e = StackParse.parse(SINK_ON_VD)
        val sinks = ClusterCast.phoneProjectionSinksOn(e, vd = 1)
        // đúng 2 sink trên VD: carplay + androidauto (KHÔNG dính vietmap/launcher ở display 0)
        assertEquals(setOf(70, 71), sinks.map { it.taskId }.toSet())
        assertTrue(sinks.all { it.displayId == 1 })
    }

    @Test
    fun `khong nham vietmap-launcher lam sink`() {
        val e = StackParse.parse(SINK_ON_VD)
        // trên display 0 (đầu xe) KHÔNG có sink nào (vietmap/launcher không phải chiếu-điện-thoại)
        assertTrue(ClusterCast.phoneProjectionSinksOn(e, vd = 0).isEmpty())
    }

    @Test
    fun `vd khong hop le tra rong`() {
        val e = StackParse.parse(SINK_ON_VD)
        assertTrue(ClusterCast.phoneProjectionSinksOn(e, vd = -1).isEmpty())
        assertTrue(ClusterCast.phoneProjectionSinksOn(e, vd = 0).isEmpty())
    }

    @Test
    fun `isPhoneProjection nhan dien theo hanh vi khong hardcode ten goi`() {
        // sink CP/AA — nhận theo component/gói
        assertTrue(ClusterCast.isPhoneProjection("com.byd.carplay.ui/com.byd.carplay.ui.VideoActivity", "com.byd.carplay.ui"))
        assertTrue(ClusterCast.isPhoneProjection("com.byd.androidauto/com.google.android.projection.sink.ui.AAPVideoActivity", "com.byd.androidauto"))
        assertTrue(ClusterCast.isPhoneProjection(null, "com.byd.androidauto"))
        // app dẫn đường THƯỜNG — KHÔNG phải sink → guard không được bê nhầm
        assertFalse(ClusterCast.isPhoneProjection("vn.vietmap.live/.MainActivity", "vn.vietmap.live"))
        assertFalse(ClusterCast.isPhoneProjection("com.waze/.MainActivity", "com.waze"))
        assertFalse(ClusterCast.isPhoneProjection("app.revanced.android.apps.maps/.MapsActivity", "app.revanced.android.apps.maps"))
    }

    /** VD sạch (chỉ app thường) → guard không có gì để bê → cast tiếp bình thường. */
    @Test
    fun `vd chi co app thuong thi khong co sink`() {
        val onlyVietmap = """
            Stack id=66 bounds=[0,0][1920,720] displayId=1 userId=0
              configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=standard} s.3}
              taskId=71: vn.vietmap.live/.MainActivity bounds=[0,0][1920,720] userId=0 visible=true
        """.trimIndent()
        assertTrue(ClusterCast.phoneProjectionSinksOn(StackParse.parse(onlyVietmap), vd = 1).isEmpty())
    }
}
