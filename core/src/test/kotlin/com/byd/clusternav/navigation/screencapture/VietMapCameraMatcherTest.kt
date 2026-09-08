package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.ArrayPixelFrame
import com.byd.clusternav.navigation.PixelFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá [VietMapCameraMatcher] off-car (R2, spec §4.5): fixture tổng hợp qua [ArrayPixelFrame] khớp CHÍNH nó
 * (positive), nền trống/khác KHÔNG false-positive (negative), và ngưỡng chặn được. Template thật = OQ4 on-car.
 */
class VietMapCameraMatcherTest {

    /** trắng ĐỤC nơi [ink], TRONG SUỐT nơi còn lại — cùng khuôn fixture của ManeuverSignatureTest. */
    private fun frame(w: Int, h: Int, ink: (Int, Int) -> Boolean): PixelFrame =
        ArrayPixelFrame(w, h, IntArray(w * h) { i ->
            if (ink(i % w, i / w)) 0xFFFFFFFF.toInt() else 0x00000000
        })

    private val cameraLike = frame(60, 60) { x, y ->
        // "icon" đặc trưng: khối trên-trái + viền chéo — đủ cấu trúc để NCC phân biệt với nền.
        (x in 8..28 && y in 8..28) || (x == y && x in 4..56)
    }

    private fun matcherWith(vararg frames: Pair<String, PixelFrame>): VietMapCameraMatcher =
        VietMapCameraMatcher(frames.mapNotNull { (n, f) -> VietMapCameraMatcher.templateFrom(n, f) })

    @Test
    fun `positive — khung khop chinh template cua no`() {
        val m = matcherWith("cam_fixed" to cameraLike)
        val r = m.match(cameraLike)
        assertTrue(r.hasCamera, "phải nhận camera trên fixture khớp")
        assertEquals("cam_fixed", r.templateName)
        assertTrue(r.score >= VietMapCameraMatcher.DEFAULT_MIN_SCORE, "score=${r.score}")
    }

    @Test
    fun `negative — nen TRONG (blank) khong false-positive`() {
        val m = matcherWith("cam_fixed" to cameraLike)
        val blank = frame(60, 60) { _, _ -> false }        // phương sai 0 → NONE
        assertEquals(CameraMatch.NONE, m.match(blank))
        assertFalse(m.match(blank).hasCamera)
    }

    @Test
    fun `negative — pattern KHAC (khong tuong quan) khong false-positive`() {
        // template = khối trên-trái; query = khối dưới-phải rời rạc → NCC thấp/âm → dưới ngưỡng.
        val m = matcherWith("cam_topleft" to frame(60, 60) { x, y -> x in 2..20 && y in 2..20 })
        val other = frame(60, 60) { x, y -> x in 40..58 && y in 40..58 }
        assertFalse(m.match(other).hasCamera)
    }

    @Test
    fun `nguong — minScore khong the dat thi ngay ca khung khop cung NONE`() {
        val strict = VietMapCameraMatcher(
            listOf(VietMapCameraMatcher.templateFrom("cam", cameraLike)!!),
            minScore = 1.1f,                                // NCC ≤ 1.0 → không bao giờ đạt
        )
        assertFalse(strict.match(cameraLike).hasCamera)
    }

    @Test
    fun `registry rong — luon NONE`() {
        assertEquals(CameraMatch.NONE, VietMapCameraMatcher(emptyList()).match(cameraLike))
        assertTrue(VietMapCameraMatcher.BUILTIN.isEmpty())      // template thật = OQ4 on-car
    }

    @Test
    fun `frame null hoac qua nho — NONE (khong crash)`() {
        val m = matcherWith("cam" to cameraLike)
        assertEquals(CameraMatch.NONE, m.match(null))
        assertEquals(CameraMatch.NONE, m.match(frame(4, 4) { _, _ -> true }))
    }

    @Test
    fun `signatureOf — dung kich thuoc GRID va null cho khung nho`() {
        val sig = VietMapCameraMatcher.signatureOf(cameraLike)
        assertNotNull(sig)
        assertEquals(VietMapCameraMatcher.GRID * VietMapCameraMatcher.GRID, sig!!.size)
        assertNull(VietMapCameraMatcher.signatureOf(frame(3, 3) { _, _ -> true }))
        assertNull(VietMapCameraMatcher.signatureOf(null))
    }

    @Test
    fun `speed va distance null vong nay (OQ4 on-car)`() {
        val r = matcherWith("cam" to cameraLike).match(cameraLike)
        assertNull(r.speedKmh)
        assertNull(r.distanceMeters)
    }

    // ── B3.6: registry NẠP-ĐƯỢC lúc chạy (orchestrator thêm template camera thật OQ4 sau) ──────────────

    @org.junit.jupiter.api.AfterEach
    fun clearLoaded() {
        VietMapCameraMatcher.clearLoaded()   // registry loaded là object toàn cục → dọn để không rò sang test khác
    }

    @Test
    fun `fromRegistry — rong (production) thi match NONE, khong false-positive`() {
        assertTrue(VietMapCameraMatcher.loadedTemplates().isEmpty())
        assertEquals(CameraMatch.NONE, VietMapCameraMatcher.fromRegistry().match(cameraLike))
    }

    @Test
    fun `register — template nap runtime duoc fromRegistry nhan va khop`() {
        val tmpl = VietMapCameraMatcher.templateFrom("cam_loaded", cameraLike)!!
        VietMapCameraMatcher.register(tmpl)
        assertEquals(listOf(tmpl), VietMapCameraMatcher.loadedTemplates())
        val r = VietMapCameraMatcher.fromRegistry().match(cameraLike)
        assertTrue(r.hasCamera, "template nạp phải khớp fixture của nó")
        assertEquals("cam_loaded", r.templateName)
    }

    @Test
    fun `load — thay the ca bo template da nap`() {
        VietMapCameraMatcher.register(VietMapCameraMatcher.templateFrom("old", cameraLike)!!)
        val fresh = VietMapCameraMatcher.templateFrom("cam_fixed", cameraLike)!!
        VietMapCameraMatcher.load(listOf(fresh))
        assertEquals(listOf(fresh), VietMapCameraMatcher.loadedTemplates())
        assertEquals("cam_fixed", VietMapCameraMatcher.fromRegistry().match(cameraLike).templateName)
    }

    @Test
    fun `register — trung y het khong them lan hai`() {
        val tmpl = VietMapCameraMatcher.templateFrom("cam", cameraLike)!!
        VietMapCameraMatcher.register(tmpl)
        VietMapCameraMatcher.register(tmpl)
        assertEquals(1, VietMapCameraMatcher.loadedTemplates().size)
    }

    @Test
    fun `clearLoaded — ve rong (BUILTIN)`() {
        VietMapCameraMatcher.register(VietMapCameraMatcher.templateFrom("cam", cameraLike)!!)
        VietMapCameraMatcher.clearLoaded()
        assertTrue(VietMapCameraMatcher.loadedTemplates().isEmpty())
    }
}
