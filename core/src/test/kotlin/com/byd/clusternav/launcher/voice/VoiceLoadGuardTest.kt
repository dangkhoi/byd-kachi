package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá bài học 2026-09-25 · wake: `/proc/loadavg` bị SELinux chặn ([ĐO máy ảo 2.65] `avc: denied` 1 dòng/giây)
 * ⇒ guard nhận `0.0` mãi ⇒ **mù**. Nguồn thay thế là CPU của chính tiến trình, thang "số lõi", ngưỡng theo phần
 * máy — và phép đổi ms→lõi phải không bao giờ trả số âm/NaN cho guard.
 */
class VoiceLoadGuardTest {

    // ── VoiceSelfCpuMeter: (ms CPU, ms tường) → số lõi ──────────────────────────────────────────────────────

    @Test
    fun `lan dau chi ghi moc, tra 0`() {
        val m = VoiceSelfCpuMeter()
        assertEquals(0.0, m.sample(cpuMs = 5_000, nowMs = 100_000))
    }

    @Test
    fun `mot giay tuong an 1300 ms CPU la 1,3 loi - dung con so su co 130 phan tram`() {
        val m = VoiceSelfCpuMeter()
        m.sample(cpuMs = 5_000, nowMs = 100_000)
        assertEquals(1.3, m.sample(cpuMs = 6_300, nowMs = 101_000), 1e-9)
    }

    @Test
    fun `tien trinh im thi 0 loi`() {
        val m = VoiceSelfCpuMeter()
        m.sample(cpuMs = 5_000, nowMs = 100_000)
        assertEquals(0.0, m.sample(cpuMs = 5_000, nowMs = 101_000), 1e-9)
    }

    @Test
    fun `khoang tuong 0 hoac CPU lui thi giu gia tri truoc, khong am khong NaN`() {
        val m = VoiceSelfCpuMeter()
        m.sample(cpuMs = 5_000, nowMs = 100_000)
        assertEquals(0.5, m.sample(cpuMs = 5_500, nowMs = 101_000), 1e-9)
        assertEquals(0.5, m.sample(cpuMs = 5_600, nowMs = 101_000), 1e-9) // cùng mốc tường
        assertEquals(0.5, m.sample(cpuMs = 100, nowMs = 102_000), 1e-9)   // CPU lùi (đặt lại)
        assertEquals(0.2, m.sample(cpuMs = 300, nowMs = 103_000), 1e-9)   // đã re-base, đo lại bình thường
    }

    // ── forSelfCpu: ngưỡng theo phần máy ────────────────────────────────────────────────────────────────────

    @Test
    fun `nguong tu-CPU theo so loi - 8 loi dung o tren 4, chay lai duoi 2`() {
        val g = VoiceLoadGuard.forSelfCpu(nproc = 8)
        assertEquals(4.0, g.suspendAbove, 1e-9)
        assertEquals(2.0, g.resumeBelow, 1e-9)
        assertEquals(VoiceLoadGuard.DEFAULT_RESUME_STABLE, g.resumeStableReads)
    }

    @Test
    fun `nproc rac thi coi nhu 1 loi, khong nem`() {
        val g = VoiceLoadGuard.forSelfCpu(nproc = 0)
        assertEquals(0.5, g.suspendAbove, 1e-9)
        assertEquals(0.25, g.resumeBelow, 1e-9)
    }

    @Test
    fun `su co 130 phan tram tren 8 loi KHONG dung - nhung nua may thi dung ngay`() {
        // [ĐO xe 2026-09-23] :wake 130 % ≈ 1,3 lõi: bệnh nhẹ, đã chữa bằng RMS-gate; guard nới để không điếc lúc
        // người lái đang gọi (F5 2026-09-19). Nửa máy (4,5 lõi) là bệnh rõ ⇒ dừng sau MỘT nhịp.
        val g = VoiceLoadGuard.forSelfCpu(nproc = 8)
        assertTrue(g.allow(1.3))
        assertFalse(g.allow(4.5))
        assertTrue(g.isSuspended())
        // Chạy lại CHẬM: phải < 2,0 liên tiếp 3 nhịp.
        assertFalse(g.allow(1.0)); assertFalse(g.allow(1.0)); assertTrue(g.allow(1.0))
    }

    @Test
    fun `load1 bang 0 mai la guard mu - do la ca dang chua`() {
        // Trước bản vá: SELinux chặn ⇒ getOrDefault(0.0) ⇒ dòng này là toàn bộ "lá chắn": không bao giờ dừng.
        val g = VoiceLoadGuard()
        repeat(100) { assertTrue(g.allow(0.0)) }
        assertFalse(g.isSuspended())
    }

    // ── parseLoadavg ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parse loadavg that`() {
        assertEquals(13.48, VoiceLoadGuard.parseLoadavg("13.48 14.52 16.36 3/1450 22019\n")!!, 1e-9)
        assertEquals(0.35, VoiceLoadGuard.parseLoadavg("0.35 0.40 0.38 1/512 1234")!!, 1e-9)
    }

    @Test
    fun `loadavg rong hoac rac thi null - khong phai 0`() {
        assertNull(VoiceLoadGuard.parseLoadavg(""))
        assertNull(VoiceLoadGuard.parseLoadavg("Permission denied"))
        assertNull(VoiceLoadGuard.parseLoadavg("-1 0 0"))
    }
}
