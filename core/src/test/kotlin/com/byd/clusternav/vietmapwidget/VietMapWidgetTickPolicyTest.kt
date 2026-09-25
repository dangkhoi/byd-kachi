package com.byd.clusternav.vietmapwidget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá BG-31 (`perf-inventory-2026-09-25.md`): `freshnessTick` 1 Hz suốt đời tiến trình dù VietMap không cài /
 * badge tắt / chưa bind — ≈7 binder/s trên main. Sau vá: 1 Hz CHỈ khi đủ ba điều kiện; còn lại 10 s.
 */
class VietMapWidgetTickPolicyTest {

    @Test
    fun `du ba dieu kien giu dung nhip hien truong 1 Hz`() {
        assertEquals(1_000L, VietMapWidgetTickPolicy.tickIntervalMs(installed = true, enabled = true, bound = true))
        assertEquals(1_000L, VietMapWidgetTickPolicy.FAST_MS)   // ghim: không ai "tối ưu" nhịp có người dùng
    }

    @Test
    fun `thieu bat ky dieu kien nao thi ngu 10 s`() {
        // Máy ảo 2.65 đứng yên: VietMap KHÔNG cài ⇒ trước vá vẫn 1 Hz. Đây là ca [ĐO] sinh ra fix.
        assertEquals(10_000L, VietMapWidgetTickPolicy.tickIntervalMs(installed = false, enabled = true, bound = true))
        assertEquals(10_000L, VietMapWidgetTickPolicy.tickIntervalMs(installed = true, enabled = false, bound = true))
        assertEquals(10_000L, VietMapWidgetTickPolicy.tickIntervalMs(installed = true, enabled = true, bound = false))
        assertEquals(10_000L, VietMapWidgetTickPolicy.tickIntervalMs(installed = false, enabled = false, bound = false))
        assertTrue(VietMapWidgetTickPolicy.SLOW_MS >= 10 * VietMapWidgetTickPolicy.FAST_MS, "nhịp ngủ phải ≥ 10× nhịp nhanh")
    }

    @Test
    fun `cache provider chi lam moi khi chua nap hoac qua TTL 60 s`() {
        assertTrue(VietMapWidgetTickPolicy.providerCacheStale(nowMs = 5_000L, loadedAtMs = null))
        assertFalse(VietMapWidgetTickPolicy.providerCacheStale(nowMs = 5_000L, loadedAtMs = 0L))
        assertFalse(VietMapWidgetTickPolicy.providerCacheStale(nowMs = 59_999L, loadedAtMs = 0L))
        assertTrue(VietMapWidgetTickPolicy.providerCacheStale(nowMs = 60_000L, loadedAtMs = 0L))
    }
}
