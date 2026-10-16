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

    /**
     * 3.1.10 `CAR3D-CABIN-BUILD-COST` — bản nhớ phân loại khoang sống QUA lần dọn trước khi dựng GLB (đổi khoá đệm GLB không chạy
     * lại lượt 24,6 s); bản nhớ KHÁC khoá thì bỏ; bản tạm của nó là rác. Đảo (dọn cả bản nhớ, hoặc không dọn bản nhớ cũ) ⇒ đỏ.
     */
    @Test
    fun `ban nho khoang song qua lan don GLB, ban nho cu bi bo`() {
        val m = Car3dCache.memo("00ff00ff00ff00ff")
        assertTrue(Car3dCache.owned(m) && Car3dCache.isMemo(m))
        assertFalse(Car3dCache.stale(m), "dọn trước khi dựng GLB không được xoá bản nhớ khoang")
        assertTrue(Car3dCache.stale("car3d-123456.glb") && Car3dCache.stale("car3d-123456.glb.part"))
        assertTrue(Car3dCache.stale("$m.tmp"), "bản tạm của bản nhớ (bị giết giữa chừng) là rác")
        assertFalse(Car3dCache.staleMemo(m, "00ff00ff00ff00ff"))
        assertTrue(Car3dCache.staleMemo(Car3dCache.memo("1111111111111111"), "00ff00ff00ff00ff"))
        assertFalse(Car3dCache.staleMemo("car3d-123456.glb", "00ff00ff00ff00ff"), "bản nhớ không đụng tệp GLB")
        assertFalse(Car3dCache.owned("car3d-cabin-x.txt"))
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
