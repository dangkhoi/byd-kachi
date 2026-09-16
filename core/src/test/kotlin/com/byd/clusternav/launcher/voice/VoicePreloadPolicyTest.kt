package com.byd.clusternav.launcher.voice

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
        // Cùng 180 MB trống: gói int8 (74 MB) nạp được, gói fp32 (266 MB) thì không. Một ngưỡng cứng
        // "còn < 200 MB thì thôi" sẽ chặn nhầm cả hai.
        assertTrue(VoicePreloadPolicy.shouldPreload(180 * mb, lowMemory = false, modelBytes = 74 * mb))
        assertFalse(VoicePreloadPolicy.shouldPreload(180 * mb, lowMemory = false, modelBytes = 266 * mb))
    }

    @Test
    fun `chua biet co goi thi giu hanh vi cu`() {
        assertTrue(VoicePreloadPolicy.shouldPreload(10 * mb, lowMemory = false, modelBytes = 0))
    }

    @Test
    fun `con du cho mo hinh cong khoang tho thi nap`() {
        val model = 74 * mb
        assertTrue(VoicePreloadPolicy.shouldPreload(model + VoicePreloadPolicy.HEADROOM_BYTES, false, model))
        assertFalse(VoicePreloadPolicy.shouldPreload(model + VoicePreloadPolicy.HEADROOM_BYTES - 1, false, model))
    }
}
