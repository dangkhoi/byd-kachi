package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá bài học H6 (PERF 2026-09-16): nạp sẵn mô hình là một phép ĐỔI CHÁC (RAM lấy 15 s chờ), nên nó phải hỏi
 * xem còn RAM không — và phải hỏi theo **cỡ gói đang chọn**, không theo một con số cứng.
 */
class VoicePreloadPolicyTest {

    private val mb = 1024L * 1024L

    @Test
    fun `xe chat RAM thi khong nap san goi fp32`() {
        // [ĐO xe 2026-09-16]: còn 60 MB trống, gói fp32 266 MB.
        assertFalse(VoicePreloadPolicy.shouldPreload(60 * mb, lowMemory = false, modelBytes = 266 * mb))
    }

    @Test
    fun `he thong bao thieu thi khong nap du goi nho`() {
        assertFalse(VoicePreloadPolicy.shouldPreload(500 * mb, lowMemory = true, modelBytes = 74 * mb))
    }

    @Test
    fun `nguong theo CO GOI chu khong phai so cung`() {
        // Cùng 200 MB trống: gói int8 (74 + thở 111 = 185 MB) nạp được, gói fp32 (266 + 399) thì không. Một ngưỡng
        // cứng "còn < 200 MB thì thôi" sẽ chặn nhầm cả hai. (180 → 200 ở 2026-09-25 khi thở đổi sang 1,5 × gói.)
        assertTrue(VoicePreloadPolicy.shouldPreload(200 * mb, lowMemory = false, modelBytes = 74 * mb))
        assertFalse(VoicePreloadPolicy.shouldPreload(200 * mb, lowMemory = false, modelBytes = 266 * mb))
    }

    // ── 2026-09-25 · wake: thở theo đỉnh transient lúc nạp (audit RAM §4.2) ────────────────────────────────

    @Test
    fun `tho bang 1,5 lan goi - int8 74 MB can 111, fp32 266 can 399, goi 0 giu san 96`() {
        assertEquals(111 * mb, VoicePreloadPolicy.headroomBytes(74 * mb))
        assertEquals(399 * mb, VoicePreloadPolicy.headroomBytes(266 * mb))
        assertEquals(VoicePreloadPolicy.HEADROOM_BYTES, VoicePreloadPolicy.headroomBytes(0))
        // Gói nhỏ (< 64 MB) không được tụt dưới sàn 96 MB.
        assertEquals(VoicePreloadPolicy.HEADROOM_BYTES, VoicePreloadPolicy.headroomBytes(40 * mb))
    }

    @Test
    fun `dinh transient - 74 MB goi tren may con 180 MB thi KHONG nap nua`() {
        // Trước 2026-09-25: 74 + 96 = 170 ≤ 180 ⇒ nạp ⇒ đỉnh +71…+126 MB chạm LMK dù policy cho qua.
        assertFalse(VoicePreloadPolicy.shouldPreload(180 * mb, lowMemory = false, modelBytes = 74 * mb))
        assertTrue(VoicePreloadPolicy.shouldPreload(185 * mb, lowMemory = false, modelBytes = 74 * mb))
    }

    @Test
    fun `reason noi dung so tho theo goi`() {
        val r = VoicePreloadPolicy.reason(100 * mb, lowMemory = false, modelBytes = 74 * mb)
        assertTrue(r.contains("cần 185 MB"), r)
        assertTrue(r.contains("thở 111 MB"), r)
    }

    // ── 2026-09-25 · wake: một mô hình cho cả máy ────────────────────────────────────────────────────────

    @Test
    fun `wake BAT thi tien trinh chinh khong nap san - mo hinh song o wake`() {
        assertFalse(VoicePreloadPolicy.shouldPreloadInMain(wakeEnabled = true))
    }

    @Test
    fun `wake TAT thi giu nap san nhu cu (V3 R4)`() {
        assertTrue(VoicePreloadPolicy.shouldPreloadInMain(wakeEnabled = false))
    }

    @Test
    fun `chua biet co goi thi giu hanh vi cu`() {
        assertTrue(VoicePreloadPolicy.shouldPreload(10 * mb, lowMemory = false, modelBytes = 0))
    }

    @Test
    fun `con du cho mo hinh cong khoang tho thi nap`() {
        val model = 74 * mb
        val need = model + VoicePreloadPolicy.headroomBytes(model)
        assertTrue(VoicePreloadPolicy.shouldPreload(need, false, model))
        assertFalse(VoicePreloadPolicy.shouldPreload(need - 1, false, model))
    }
}
