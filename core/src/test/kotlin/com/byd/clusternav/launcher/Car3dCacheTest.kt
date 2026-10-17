package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
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

    /**
     * 3.1.14 PERF-SL6-3113 R5 — SL6 17/10: đơ lúc nổ máy VÀ sau đó, qua nhiều lần reset. Lượt dựng nguội (~33 s CPU Seal) không được
     * lặp mỗi lần nổ máy cho một khoá hỏng, không quá một lượt/lần khởi động, không trong 30 s đầu. ĐỎ khi gỡ một trong ba cổng.
     */
    @Test
    fun `dung nguoi - mot luot moi lan khoi dong, tran qua cac lan no may, cho qua luc khoi dong`() {
        assertTrue(Car3dCache.mayColdBuild(failed = 0, triedThisProcess = false))
        assertFalse(Car3dCache.mayColdBuild(failed = 0, triedThisProcess = true), "một lượt/lần khởi động (FAULT 2·8·32 s không dựng lại)")
        assertTrue(Car3dCache.mayColdBuild(failed = Car3dCache.MAX_COLD_FAILS - 1, triedThisProcess = false))
        assertFalse(Car3dCache.mayColdBuild(failed = Car3dCache.MAX_COLD_FAILS, triedThisProcess = false), "hỏng ${Car3dCache.MAX_COLD_FAILS} lần nổ máy ⇒ thôi")
        assertEquals(Car3dCache.COLD_AFTER_MS, Car3dCache.coldDelayMs(0))
        assertEquals(10_000L, Car3dCache.coldDelayMs(Car3dCache.COLD_AFTER_MS - 10_000L))
        assertEquals(0L, Car3dCache.coldDelayMs(120_000L), "qua lúc khởi động ⇒ dựng ngay")
        assertTrue(Car3dCache.COLD_AFTER_MS in 20_000L..60_000L)
        assertEquals(0, Car3dCache.parseFails(null)); assertEquals(0, Car3dCache.parseFails("rác")); assertEquals(2, Car3dCache.parseFails("2\n"))
        val mark = Car3dCache.failMark("car3d-123.glb")
        assertEquals("car3d-fail-car3d-123.txt", mark)
        assertFalse(Car3dCache.stale(mark), "dấu hỏng không bị dọn cùng đệm (nó phải sống qua lượt dọn trước khi dựng)")
    }
}
