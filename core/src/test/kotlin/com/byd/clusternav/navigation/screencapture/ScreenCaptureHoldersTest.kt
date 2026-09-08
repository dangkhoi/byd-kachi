package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.Lane
import com.byd.clusternav.navigation.LaneInfo
import com.byd.clusternav.navigation.Maneuver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pure off-car lock for the screen-capture holders (publish -> snapshot -> freshness -> clear). */
class ScreenCaptureHoldersTest {

    private val waze = "com.chisadin.wazemod"
    private val vietmap = "vn.vietmap.live"

    @Test
    fun `bounds source publishes and clears`() {
        CaptureBoundsSource.clear()
        assertNull(CaptureBoundsSource.snapshot())
        // §R-BI: publish giờ bắt buộc mang CHỦ (pkg) — rect vô chủ có thể crop nhầm cửa sổ app khác ⇒ SAI HƯỚNG.
        // B3.53-review: và bắt buộc mang MỤC TIÊU — rect đo cho node CAMERA từng được áp cho plan ARROW.
        CaptureBoundsSource.publish(waze, CaptureTarget.ARROW, 10, 20, 210, 120, now = 1000L)
        val snap = CaptureBoundsSource.snapshot()
        assertNotNull(snap)
        assertEquals(10, snap!!.rect.left)
        assertEquals(120, snap.rect.bottom)
        assertEquals(1000L, snap.capturedAtMs)
        assertEquals(waze, snap.pkg)
        assertEquals(CaptureTarget.ARROW, snap.target)
        CaptureBoundsSource.clear()
        assertNull(CaptureBoundsSource.snapshot())
    }

    @Test
    fun `bounds source rejects empty rect`() {
        CaptureBoundsSource.clear()
        CaptureBoundsSource.publish(waze, CaptureTarget.ARROW, 10, 10, 10, 10, now = 5L)
        assertNull(CaptureBoundsSource.snapshot())
    }

    /** KHOÁ (§R-BI, fail-CLOSED): bounds vô chủ KHÔNG được phát ra — consumer sẽ rơi về tier rect cố định. */
    @Test
    fun `bounds source rejects empty pkg`() {
        CaptureBoundsSource.clear()
        CaptureBoundsSource.publish("", CaptureTarget.ARROW, 10, 20, 210, 120, now = 1000L)
        assertNull(CaptureBoundsSource.snapshot())
    }

    /**
     * KHOÁ [P1] B3.53 vòng review: holder phải chuyển tiếp MỤC TIÊU đúng như producer khai. Với VietMap,
     * producer chọn node bằng `CaptureTarget.forPackage` = CAMERA; nếu nhãn này rụng, `CaptureRouter` áp rect
     * icon camera cho cả plan ARROW ⇒ khớp MỀM trên crop sai chỗ ⇒ SAI HƯỚNG (xem `CaptureRouterTest`).
     */
    @Test
    fun `bounds source giu nguyen MUC TIEU da khai (CAMERA khong bien thanh ARROW)`() {
        CaptureBoundsSource.clear()
        CaptureBoundsSource.publish(vietmap, CaptureTarget.CAMERA, 1500, 300, 1620, 420, now = 1000L)
        val snap = CaptureBoundsSource.snapshot()
        assertNotNull(snap)
        assertEquals(CaptureTarget.CAMERA, snap!!.target)
        assertEquals(vietmap, snap.pkg)
        CaptureBoundsSource.clear()
    }

    @Test
    fun `foreground source freshness window`() {
        CaptureForegroundSource.clear()
        assertFalse(CaptureForegroundSource.isFresh(0L))
        CaptureForegroundSource.publish("com.waze", now = 1000L)
        assertEquals("com.waze", CaptureForegroundSource.pkg)
        assertTrue(CaptureForegroundSource.isFresh(1000L + CaptureForegroundSource.FRESH_MS))
        assertFalse(CaptureForegroundSource.isFresh(1000L + CaptureForegroundSource.FRESH_MS + 1))
        CaptureForegroundSource.clear()
        assertNull(CaptureForegroundSource.pkg)
    }

    /**
     * KHOÁ (§R-BI): rect dải làn LUÔN đi kèm chủ sở hữu + số làn trong CÙNG một snapshot.
     * Trước B-I `laneCount` là biến toàn cục rời ⇒ có thể lệch pha với rect vừa đọc.
     */
    @Test
    fun `lane bounds mang pkg + so lan trong cung mot snapshot`() {
        LaneBoundsSource.clear()
        assertNull(LaneBoundsSource.snapshot())
        LaneBoundsSource.publish(waze, 30, 40, 330, 140, lanes = 4, now = 900L)
        val lb = LaneBoundsSource.snapshot()
        assertNotNull(lb)
        assertEquals(waze, lb!!.pkg)
        assertEquals(30, lb.rect.left)
        assertEquals(140, lb.rect.bottom)
        assertEquals(4, lb.laneCount)
        assertEquals(900L, lb.capturedAtMs)
        LaneBoundsSource.clear()
        assertNull(LaneBoundsSource.snapshot())
    }

    /** KHOÁ: giữ nguyên hành vi cũ với rect rỗng, VÀ không bao giờ phát ra bounds vô chủ (fail-CLOSED). */
    @Test
    fun `lane bounds — rect rong HOAC pkg rong deu ra snapshot null`() {
        LaneBoundsSource.clear()
        LaneBoundsSource.publish(waze, 10, 10, 10, 10, lanes = 3, now = 900L)
        assertNull(LaneBoundsSource.snapshot(), "rect rỗng")
        LaneBoundsSource.publish("", 30, 40, 330, 140, lanes = 3, now = 900L)
        assertNull(LaneBoundsSource.snapshot(), "pkg rỗng = vô chủ")
        LaneBoundsSource.clear()
    }

    @Test
    fun `signal stores arrow and camera with freshness`() {
        ScreenCaptureSignal.clear()
        assertFalse(ScreenCaptureSignal.arrowFresh(0L))
        ScreenCaptureSignal.publishArrow("com.waze", maneuver = null, amap = 2, now = 100L)
        assertEquals("com.waze", ScreenCaptureSignal.arrowPkg)
        assertEquals(2, ScreenCaptureSignal.arrowAmap)
        assertTrue(ScreenCaptureSignal.arrowFresh(100L + ScreenCaptureSignal.STALE_MS))
        assertFalse(ScreenCaptureSignal.arrowFresh(100L + ScreenCaptureSignal.STALE_MS + 1))

        ScreenCaptureSignal.publishCamera("vn.vietmap.live", CameraMatch(hasCamera = true, score = 0.9f, templateName = "fixed"), now = 200L)
        assertEquals("vn.vietmap.live", ScreenCaptureSignal.cameraPkg)
        assertTrue(ScreenCaptureSignal.cameraMatch!!.hasCamera)
        ScreenCaptureSignal.clear()
        assertNull(ScreenCaptureSignal.arrowPkg)
        assertNull(ScreenCaptureSignal.cameraMatch)
    }

    /**
     * KHOÁ (§R-BI): mỗi kênh là MỘT sample bất biến ⇒ không còn khả năng "đọc-xé" pkg/giá trị/mốc của cùng
     * một kênh (trước đây là 3–4 `@Volatile` rời, publish xen giữa hai lần đọc là ghép được pkg app mới với
     * giá trị app cũ). Publish kênh này KHÔNG đụng kênh kia.
     */
    @Test
    fun `moi kenh la MOT sample bat bien, doc mot lan ra bo nhat quan`() {
        ScreenCaptureSignal.clear()
        ScreenCaptureSignal.publishArrow(waze, Maneuver.TURN_LEFT, amap = 2, now = 100L)
        ScreenCaptureSignal.publishLane(vietmap, LaneInfo(listOf(Lane(listOf(Maneuver.STRAIGHT), true))), now = 150L)

        val a = ScreenCaptureSignal.arrow
        assertNotNull(a)
        assertEquals(waze, a!!.pkg)
        assertEquals(Maneuver.TURN_LEFT, a.maneuver)
        assertEquals(2, a.amap)
        assertEquals(100L, a.atMs)

        val l = ScreenCaptureSignal.lane
        assertNotNull(l)
        assertEquals(vietmap, l!!.pkg, "publish lane KHÔNG được đụng vào kênh arrow và ngược lại")
        assertEquals(150L, l.atMs)
        assertNull(ScreenCaptureSignal.camera, "kênh chưa publish vẫn null")

        // Biên tươi giữ nguyên đúng STALE_MS (không đổi hành vi cũ).
        assertTrue(ScreenCaptureSignal.arrowFresh(100L + ScreenCaptureSignal.STALE_MS))
        assertFalse(ScreenCaptureSignal.arrowFresh(100L + ScreenCaptureSignal.STALE_MS + 1))
        assertTrue(ScreenCaptureSignal.laneFresh(150L + ScreenCaptureSignal.STALE_MS))
        assertFalse(ScreenCaptureSignal.laneFresh(150L + ScreenCaptureSignal.STALE_MS + 1))
        ScreenCaptureSignal.clear()
    }
}
