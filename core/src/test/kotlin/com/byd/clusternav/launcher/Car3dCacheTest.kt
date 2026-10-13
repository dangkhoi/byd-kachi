package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Soát Pass 2 R25 — khoá luật tệp đệm xe 3D ([Car3dCache]): rác của lần dựng bị giết giữa chừng được dọn (không chỉ `.glb`),
 * tệp khác của `cacheDir` không bị đụng; đĩa sắp đầy ⇒ không ánh xạ ghi (SIGBUS trên tệp thưa = HOME sập).
 */
class Car3dCacheTest {

    @Test
    fun `don ca ban do va ban chep tam - khong dung tep khac`() {
        assertTrue(Car3dCache.owned("car3d-123456.glb"))
        assertTrue(Car3dCache.owned("car3d-123456.glb.part"), "bản dở của lần dựng bị giết (~30 MB) phải được dọn")
        assertTrue(Car3dCache.owned("car3d-src.tmp"), "bản chép lưới nén (~35 MB) phải được dọn")
        assertFalse(Car3dCache.owned("wallpaper.jpg"))
        assertFalse(Car3dCache.owned("car3d-notes.txt"))
        assertFalse(Car3dCache.owned("x-car3d-1.glb"))
    }

    @Test
    fun `dia sap day thi khong dung tep dem`() {
        val glb = 30L shl 20
        assertTrue(Car3dCache.roomFor(glb, 2L shl 30))
        assertFalse(Car3dCache.roomFor(glb, glb), "vừa khít = không chừa gì cho hệ thống")
        assertFalse(Car3dCache.roomFor(glb, glb + Car3dCache.MARGIN_BYTES - 1))
        assertTrue(Car3dCache.roomFor(glb, glb + Car3dCache.MARGIN_BYTES))
        assertFalse(Car3dCache.roomFor(0, 2L shl 30))
    }
}
