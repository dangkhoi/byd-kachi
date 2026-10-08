package com.byd.clusternav.core

import com.byd.clusternav.core.UpdateApkSweep.Verdict.DELETE
import com.byd.clusternav.core.UpdateApkSweep.Verdict.KEEP
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 2.98 · R6-H — APK OTA ~45 MB nằm lại sau khi cài (perf-inventory-2026-10-08 §4 H). Khoá: không bao giờ xoá trước khi
 * biết đã cài; xoá bản đã cài / bản tải dở; giữ bản chưa cài để cài tay.
 */
class UpdateApkSweepTest {

    private val own = "com.byd.launcher"
    private val old = UpdateApkSweep.FRESH_MS + 1

    @Test
    fun `da cai xong hoac co ban moi hon thi xoa`() {
        assertEquals(DELETE, UpdateApkSweep.verdict(old, own, 200, own, 200), "2.97 (200) đã cài, tệp 200")
        assertEquals(DELETE, UpdateApkSweep.verdict(old, own, 199, own, 200), "tệp cũ hơn bản đang cài")
    }

    @Test
    fun `tai roi ma chua cai thi giu de cai tay`() {
        assertEquals(KEEP, UpdateApkSweep.verdict(old, own, 201, own, 200))
    }

    @Test
    fun `khong biet ban dang cai thi giu tat ca`() {
        assertEquals(KEEP, UpdateApkSweep.verdict(old, own, 100, own, null))
        assertEquals(KEEP, UpdateApkSweep.verdict(old, null, 0, own, null), "kể cả tệp hỏng")
    }

    @Test
    fun `tai do khong doc duoc va khong con tuoi thi xoa, con tuoi thi giu`() {
        assertEquals(DELETE, UpdateApkSweep.verdict(old, null, 0, own, 200))
        assertEquals(KEEP, UpdateApkSweep.verdict(UpdateApkSweep.FRESH_MS - 1, null, 0, own, 200), "có thể đang tải")
        assertEquals(KEEP, UpdateApkSweep.verdict(-5_000, null, 0, own, 200), "đồng hồ lùi ⇒ coi là tươi")
    }

    @Test
    fun `goi khac thi khong doan`() {
        assertEquals(KEEP, UpdateApkSweep.verdict(old, "vn.vietmap.live", 1, own, 200))
    }
}
