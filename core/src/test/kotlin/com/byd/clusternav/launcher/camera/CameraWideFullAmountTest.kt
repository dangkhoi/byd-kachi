package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.94 QA F1 [ĐO máy ảo 07/10] — camera sau/trước chọn *Thẳng rộng* phải hiện y như *Nắn thẳng*, kể cả khi người lái từng tắt
 * nắn (`camera_dewarp_amount = 0`): máy ảo thấy 91 838 / 218 700 điểm ảnh khác (ảnh thô) cho tới khi ép mức nắn đủ.
 */
class CameraWideFullAmountTest {

    @Test
    fun `sau truoc chon Thang rong thi nan du, cac ca khac giu muc da luu`() {
        CameraWhich.values().filter { !it.side }.forEach { w ->
            val mode = CameraViewMode.forCamera(CameraViewMode.WIDE, w)
            assertEquals(CameraViewMode.STRAIGHT, mode)
            assertTrue(CameraViewMode.forcesFullAmount(CameraViewMode.WIDE, mode), "$w: Thẳng rộng ⇒ Nắn thẳng nắn đủ")
        }
        CameraWhich.values().filter { it.side }.forEach { w ->
            val mode = CameraViewMode.forCamera(CameraViewMode.WIDE, w)
            assertFalse(CameraViewMode.forcesFullAmount(CameraViewMode.WIDE, mode), "$w: gương giữ hình trụ")
        }
        assertFalse(CameraViewMode.forcesFullAmount(CameraViewMode.STRAIGHT, CameraViewMode.STRAIGHT), "chọn Nắn thẳng thật ⇒ mức đã lưu")
        assertFalse(CameraViewMode.forcesFullAmount(CameraViewMode.FISHEYE, CameraViewMode.FISHEYE))
    }
}
