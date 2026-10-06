package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * 2.90 · R6 (review Pass 1) — khoá dựng lại trang Cài đặt › Chiếu cụm ([statusKey]). Bản đầu so theo LOẠI trạng thái
 * (`javaClass`) ⇒ `CastingFull(A) → CastingFull(B)` hay câu lỗi mới để chữ cũ trên trang. Thử ĐỎ: trả `statusKey` về
 * `javaClass.simpleName`.
 */
class CastStatusKeyTest {

    private val cfg = DisplayConfig.NORMAL_DEFAULT

    @Test
    fun `doi app dang chieu, doi cau loi, doi ti le - khoa doi`() {
        val a = SimpleCastState.CastingFull("com.a", AppType.NORMAL, cfg)
        val b = SimpleCastState.CastingFull("com.b", AppType.NORMAL, cfg)
        assertNotEquals(a.statusKey(), b.statusKey())
        assertNotEquals(SimpleCastState.Error("x").statusKey(), SimpleCastState.Error("y").statusKey())
        val s50 = SimpleCastState.CastingSplit(SlotState("com.a", cfg), null, leftPercent = 50)
        val s60 = SimpleCastState.CastingSplit(SlotState("com.a", cfg), null, leftPercent = 60)
        assertNotEquals(s50.statusKey(), s60.statusKey())
        assertNotEquals(SimpleCastState.Opening.statusKey(), SimpleCastState.Idle.statusKey())
    }

    @Test
    fun `chi cap nhat ban ghim (chinh -+) - khoa giu nguyen, khong dung lai trang`() {
        val a = SimpleCastState.CastingFull("com.a", AppType.NORMAL, cfg)
        val pinned = a.copy(pinned = cfg.copy(bounds = CastBounds(0, 0, 960, 720)), taskId = 7)
        assertEquals(a.statusKey(), pinned.statusKey())
    }
}
