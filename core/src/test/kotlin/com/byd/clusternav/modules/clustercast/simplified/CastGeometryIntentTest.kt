package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1 · R1/R4/R7 — ý định của bốn thanh −/+ khung chiếu + phép kẹp có TRÀN (spec `kachi-301-cast-steps-dialog-ia.html` §2.1,
 * §2.2, §4.1).
 *
 * Khoá lỗi owner 13/10 *"−/+ khung chiếu cụm nhảy loạn"*: bản cũ đọc bản ghim (trễ — lệnh áp bất đồng bộ) ⇒ lần 1 không đổi số,
 * lần 2 nhảy 40, bấm nhanh nuốt bước. "Sự thật trễ" ở đây = `truth` KHÔNG đổi suốt chuỗi bấm (lệnh chưa chạy xong cái nào).
 * Thử ĐỎ: cho [CastGeometryIntent.nudge] cộng trên `truth` thay vì trên khung đang hiện ⇒ 5 cú = +20, bài đỏ.
 */
class CastGeometryIntentTest {

    private val w = 1920
    private val h = 720
    private val full = CastBounds(0, 0, w, h)

    /** Đúng phép `ClusterNavBridgeGeometry.clampToBand` cho TOÀN cụm Bo tròn (mép phải/dưới tràn +50 %). */
    private val clampFull = { b: CastBounds ->
        CastGeometryGuard.clampBounds(b, 0, w, 0, h, CastGeometryGuard.overflowMax(w), CastGeometryGuard.overflowMax(h))
    }

    /** Nửa TRÁI 50 %: mép phải KHÔNG được tràn sang nửa kia; mép dưới thì được. */
    private val clampLeftHalf = { b: CastBounds ->
        CastGeometryGuard.clampBounds(b, 0, 960, 0, h, 960, CastGeometryGuard.overflowMax(h))
    }

    @Test
    fun `nam lan bam nhanh truoc khi lenh nao xong = dung +100`() {
        val intent = CastGeometryIntent<String>()
        val sent = (1..5).mapNotNull { intent.nudge("full", full, CastEdge.TOP, 20, clampFull) }
        assertEquals(listOf(20, 40, 60, 80, 100), sent.map { it.top }, "mỗi cú đúng +20, không trễ nhịp, không nhảy đôi")
        assertEquals(100, intent.shown("full", full).top)
    }

    @Test
    fun `cham bien thi dung o bien va khong gui lenh thua`() {
        val intent = CastGeometryIntent<String>()
        assertNull(intent.nudge("full", full, CastEdge.LEFT, -20, clampFull), "trái 0 − 20 ⇒ vẫn 0 ⇒ không gửi")
        repeat(100) { intent.nudge("full", full, CastEdge.TOP, 50, clampFull) }
        assertEquals(h - CastGeometryGuard.MIN_SPAN, intent.shown("full", full).top, "mép trên chừa MIN_SPAN trong cụm")
        assertNull(intent.nudge("full", full, CastEdge.TOP, 50, clampFull))
    }

    @Test
    fun `quen y dinh thi so ve su that`() {
        val intent = CastGeometryIntent<String>()
        intent.nudge("full", full, CastEdge.TOP, 20, clampFull)
        assertTrue(intent.pending("full"))
        intent.forget("full")
        assertFalse(intent.pending("full"))
        val truth = full.copy(top = 7)
        assertEquals(truth, intent.shown("full", truth), "sau khi quên: hiện SỰ THẬT (vd lệnh hỏng ⇒ số cũ)")
    }

    @Test
    fun `R7 toan cum duoc tran phai duoi toi 50 phan tram, trai tren khong am`() {
        val intent = CastGeometryIntent<String>()
        repeat(200) { intent.nudge("full", full, CastEdge.RIGHT, 50, clampFull) }
        repeat(200) { intent.nudge("full", full, CastEdge.BOTTOM, 50, clampFull) }
        val b = intent.shown("full", full)
        assertEquals(2880, b.right, "1920 + 50 %")
        assertEquals(1080, b.bottom, "720 + 50 %")
        assertTrue(CastGeometryGuard.boundsOk(b), "khung tràn vẫn qua bất biến lưu/áp")
        assertEquals(b, CastGeometryGuard.bounds("${b.left},${b.top},${b.right},${b.bottom}"), "bản ghi lưu đọc lại được")
        assertEquals(0, clampFull(CastBounds(-40, -40, 1000, 500)).left)
        assertEquals(0, clampFull(CastBounds(-40, -40, 1000, 500)).top)
    }

    @Test
    fun `R7 mot nua khong tran phai sang nua kia nhung duoc tran duoi`() {
        val intent = CastGeometryIntent<String>()
        val half = CastBounds(0, 0, 960, h)
        assertNull(intent.nudge("L", half, CastEdge.RIGHT, 20, clampLeftHalf), "nửa trái chạm 960 ⇒ đứng")
        assertEquals(740, intent.nudge("L", half, CastEdge.BOTTOM, 20, clampLeftHalf)?.bottom)
    }

    @Test
    fun `phep kep 5 tham so giu nguyen hanh vi cu`() {
        val b = CastBounds(-500, -500, 9000, 9000)
        assertEquals(CastBounds(0, 0, w, h), CastGeometryGuard.clampBounds(b, 0, w, 0, h))
        assertEquals(CastBounds(0, 0, w, h), CastGeometryGuard.clampBounds(b, 0, w, h))
        assertEquals(8192, CastGeometryGuard.overflowMax(8000), "trần cứng DIMENSION_MAX")
    }

    @Test
    fun `buoc chi nhan 5 20 50, la thi 20`() {
        assertEquals(listOf(5, 20, 50), CastGeometrySteps.OPTIONS)
        listOf(5, 20, 50).forEach { assertEquals(it, CastGeometrySteps.normalize(it)) }
        listOf(null, 0, -5, 7, 999).forEach { assertEquals(20, CastGeometrySteps.normalize(it), "$it") }
    }

    @Test
    fun `geometryKey doi theo ban ghim khung, statusKey thi khong`() {
        val cfg = DisplayConfig.NORMAL_DEFAULT
        val a = SimpleCastState.CastingFull("a.b", AppType.NORMAL, cfg, pinned = cfg.copy(bounds = CastBounds(0, 0, 1900, 720)))
        val b = a.copy(pinned = cfg.copy(bounds = CastBounds(0, 20, 1900, 720)))
        assertEquals(a.statusKey(), b.statusKey())
        assertTrue(a.geometryKey() != b.geometryKey())
        assertEquals("", SimpleCastState.Off.geometryKey())
    }
}
