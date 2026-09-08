package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** Khoá bài học SL6 22/07: AA vào size-compat trên cụm (scale 0.727) → DPI không ăn; CP/AA không được force-stop. */
class SizeCompatTest {

    // nguyên văn từ diag-0722-163257.txt (block token của AA trên cụm)
    private val DUMP = """
    mStackId=7
      taskId=8
          Activity #0 AppWindowToken{ae902d7 token=Token{f602256 ActivityRecord{1102471 u0 com.byd.androidauto/com.google.android.projection.sink.ui.AAPVideoActivity t8}}}
            windows=[Window{6e3cd06 u0 com.byd.androidauto/com.google.android.projection.sink.ui.AAPVideoActivity}]
            mSizeCompatScale=0.72727275 mSizeCompatBounds=Rect(262, 0 - 1658, 720)
    mStackId=5
      taskId=6
          Activity #0 AppWindowToken{x token=Token{y ActivityRecord{z u0 vn.vietmap.live/.MainActivity t6}}}
""".trimIndent()

    @Test fun `bat duoc size-compat cua AA`() {
        val sc = DisplayParse.sizeCompatScale(DUMP, "com.byd.androidauto")
        assertEquals(0.72727275f, sc!!, 0.0001f)
    }

    @Test fun `app khac khong bi nham la size-compat`() {
        assertNull(DisplayParse.sizeCompatScale(DUMP, "vn.vietmap.live"), "Vietmap không có mSizeCompatScale → null")
    }

    @Test fun `nhan dien app chieu dien thoai theo hanh vi`() {
        val c = ClusterCast
        assertTrue(c.isPhoneProjection("com.byd.androidauto/com.google.android.projection.sink.ui.AAPVideoActivity", "com.byd.androidauto"))
        assertTrue(c.isPhoneProjection("com.byd.carplay.ui/com.byd.carplay.ui.MainActivity", "com.byd.carplay.ui"))
        assertFalse(c.isPhoneProjection("vn.vietmap.live/.MainActivity", "vn.vietmap.live"), "Vietmap KHÔNG phải app chiếu điện thoại — vẫn được force-stop nếu cần")
        assertFalse(c.isPhoneProjection(null, "com.google.android.apps.maps"))
    }
}
