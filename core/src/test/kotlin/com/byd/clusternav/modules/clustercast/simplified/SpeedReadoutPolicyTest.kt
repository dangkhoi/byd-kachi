package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B1b · CLUSTER-RECT-OPTION (C.3 mục 9) — số km/h Kachi vẽ trên cụm Chữ nhật.
 *
 * Khoá ba thứ mà một bản vá "cho nhanh" dễ làm hỏng trước mặt người lái:
 *  • VỊ TRÍ — hộp số nằm trong vùng trống theme2 FULL, KHÔNG giao nền ADAS lớn / chữ ADAS bật lên / ô ADAS nhỏ / chữ "m/h" lạc
 *    [ĐO QML fw 2602030 + PNG, `cluster-rect-seal-2026-10-05.md` §2];
 *  • DỮ LIỆU CŨ — quá 1000 ms không có lần đọc tốt ⇒ "––" mờ; không giữ số cũ, không giả 0 (bài học W1-3 của `SpeedProvider`);
 *  • KHI NÀO HIỆN — chỉ Chữ nhật, chỉ trên id cụm đã xác minh, không CarPlay/Android Auto (D5), không khi latch theme bật.
 */
class SpeedReadoutPolicyTest {

    private val box = ClusterRectLayout.SPEED_BOX

    // ── Vị trí ──────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `hop so nam trong vung trong va khong giao vung nao cua ADAS`() {
        assertEquals(CastBounds(70, 415, 310, 535), box, "hộp đã chốt (B.7): (70,415)-(310,535)")
        assertEquals(240, box.width)
        assertEquals(120, box.height)
        assertTrue(ClusterRectLayout.contains(ClusterRectLayout.FREE_AREA, box), "phải nằm TRỌN trong vùng trống")
        listOf(
            "nền ADAS lớn" to ClusterRectLayout.ADAS_PANEL,
            "chữ ADAS bật lên" to ClusterRectLayout.ADAS_POPUP_TEXT,
            "ô ADAS nhỏ" to ClusterRectLayout.ADAS_SMALL,
            "chữ m/h lạc" to ClusterRectLayout.STRAY_MPH,
        ).forEach { (name, zone) -> assertFalse(ClusterRectLayout.intersects(box, zone), "hộp số giao $name") }
        assertTrue(box.right < ClusterRectLayout.ADAS_POPUP_TEXT.left, "x < 551 ⇒ tránh dải chữ ADAS bật lên")
        assertTrue(box.right <= ClusterRectLayout.WIDTH && box.bottom <= ClusterRectLayout.HEIGHT)
    }

    @Test
    fun `vung trong khong giao nen ADAS lon`() {
        assertFalse(ClusterRectLayout.intersects(ClusterRectLayout.FREE_AREA, ClusterRectLayout.ADAS_PANEL))
        assertTrue(ClusterRectLayout.intersects(CastBounds(0, 0, 1920, 720), ClusterRectLayout.ADAS_PANEL), "phép giao còn sống")
    }

    // ── Dữ liệu ─────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `doi m-s sang km-h - lam tron, kep 0 den 299, null khong phai 0`() {
        assertEquals(50, SpeedReadoutPolicy.kmh(50 / 3.6))
        assertEquals(0, SpeedReadoutPolicy.kmh(0.0))
        assertEquals(36, SpeedReadoutPolicy.kmh(10.0))
        assertEquals(1, SpeedReadoutPolicy.kmh(0.14), "0,504 km/h ⇒ 1 (làm tròn, không cắt)")
        assertEquals(0, SpeedReadoutPolicy.kmh(0.13), "0,468 km/h ⇒ 0")
        assertEquals(299, SpeedReadoutPolicy.kmh(400 / 3.6), "kẹp 299 — ba chữ số")
        assertEquals(0, SpeedReadoutPolicy.kmh(-1.0), "âm ⇒ 0 (kẹp), không phải số âm")
        assertNull(SpeedReadoutPolicy.kmh(null), "không đọc được ⇒ null, KHÔNG phải 0")
        assertNull(SpeedReadoutPolicy.kmh(Double.NaN))
        assertNull(SpeedReadoutPolicy.kmh(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `moc 1000 ms - con trong han thi so, qua han thi gach mo`() {
        val t0 = 10_000L
        assertEquals(SpeedReadoutPolicy.Face("57", dim = false), SpeedReadoutPolicy.face(57, t0, t0))
        assertEquals(SpeedReadoutPolicy.Face("57", dim = false), SpeedReadoutPolicy.face(57, t0, t0 + 1_000), "đúng 1000 ms còn tin")
        val stale = SpeedReadoutPolicy.face(57, t0, t0 + 1_001)
        assertEquals(SpeedReadoutPolicy.Face("––", dim = true), stale, "quá 1000 ms ⇒ –– mờ, KHÔNG giữ 57")
        assertEquals(SpeedReadoutPolicy.NO_VALUE, SpeedReadoutPolicy.face(null, null, t0).text, "chưa đọc được lần nào ⇒ ––")
        assertTrue(SpeedReadoutPolicy.face(null, null, t0).dim)
        assertEquals(SpeedReadoutPolicy.NO_VALUE, SpeedReadoutPolicy.face(57, t0 + 5, t0).text, "đồng hồ lùi ⇒ không tin")
        assertFalse(SpeedReadoutPolicy.NO_VALUE.contains("0"), "dấu hiệu 'không có số' không được là số 0")
    }

    @Test
    fun `mau ngay dem va mau mo`() {
        val day = SpeedReadoutPolicy.palette(night = false)
        val night = SpeedReadoutPolicy.palette(night = true)
        assertEquals(0x99, day.background ushr 24, "nền ngày alpha 0,60")
        assertEquals(0xB8, night.background ushr 24, "nền đêm alpha 0,72")
        assertEquals(0, day.background and 0xFFFFFF, "nền đen")
        assertEquals(0xFFFFFFFF.toInt(), day.digits)
        assertEquals(0xFFDADADA.toInt(), night.digits)
        assertEquals(0xB3, day.unit ushr 24, "nhãn alpha 70 %")
        assertEquals(day.digits and 0xFFFFFF, SpeedReadoutPolicy.dimmed(day.digits) and 0xFFFFFF)
        assertTrue((SpeedReadoutPolicy.dimmed(day.digits) ushr 24) < 0xFF, "–– phải mờ hơn số")
    }

    // ── Khi nào hiện ────────────────────────────────────────────────────────────────────────────────────────────────

    private val rect = CastSessionStyle(CastStyle.RECT, BelievedStyle.RECT, CastStyle.RECT)
    private fun inp(
        state: SimpleCastState = SimpleCastState.Idle,
        session: CastSessionStyle? = rect,
        latched: Boolean = false,
        vd: Int = 2,
    ) = SpeedReadoutPolicy.Inputs(state, session, latched, vd)

    private fun full(type: AppType, cfg: DisplayConfig = DisplayConfig.NORMAL_DEFAULT) =
        SimpleCastState.CastingFull("vn.vietmap.live", type, cfg)

    @Test
    fun `hien o Idle, chieu toan cum app thuong, chia doi - khi phien Chu nhat`() {
        assertTrue(SpeedReadoutPolicy.visible(inp()))
        assertTrue(SpeedReadoutPolicy.visible(inp(state = full(AppType.NORMAL))))
        val slot = SlotState("vn.vietmap.live", DisplayConfig.NORMAL_DEFAULT)
        assertTrue(SpeedReadoutPolicy.visible(inp(state = SimpleCastState.CastingSplit(slot, null))))
        assertTrue(SpeedReadoutPolicy.visible(inp(), measuredSize = 1920 to 720))
    }

    @Test
    fun `kieu tin chua ro van hien - Bo tron thi khong`() {
        assertTrue(SpeedReadoutPolicy.visible(inp(session = rect.copy(believed = BelievedStyle.UNKNOWN))), "thà trùng số còn hơn mất")
        assertFalse(SpeedReadoutPolicy.visible(inp(session = rect.copy(believed = BelievedStyle.CURVED))))
        assertFalse(SpeedReadoutPolicy.visible(inp(session = CastSessionStyle(CastStyle.CURVED, BelievedStyle.CURVED, CastStyle.CURVED))))
        assertFalse(SpeedReadoutPolicy.visible(inp(session = null)), "không có phiên ⇒ không hiện")
    }

    @Test
    fun `go o moi trang thai chuyen tiep, loi, CarPlay va Android Auto`() {
        listOf(
            SimpleCastState.Opening, SimpleCastState.Closing, SimpleCastState.Stopping, SimpleCastState.Off,
            SimpleCastState.Error("x"), full(AppType.CARPLAY, DisplayConfig.CARPLAY), full(AppType.ANDROID_AUTO, DisplayConfig.ANDROID_AUTO),
        ).forEach { assertFalse(SpeedReadoutPolicy.visible(inp(state = it)), "phải gỡ ở $it") }
    }

    @Test
    fun `chi tren id cum da xac minh, khong khi latch, khong khi toa do khong 1-1`() {
        assertFalse(SpeedReadoutPolicy.visible(inp(vd = -1)), "chưa xác minh cụm ⇒ không gắn (không bao giờ đoán display 1)")
        assertFalse(SpeedReadoutPolicy.visible(inp(vd = 0)), "display 0 là màn chính")
        assertFalse(SpeedReadoutPolicy.visible(inp(latched = true)), "latch theme bật ⇒ gỡ")
        assertFalse(SpeedReadoutPolicy.visible(inp(), measuredSize = 1422 to 800), "kích đo được khác 1920×720 ⇒ hộp sai chỗ")
        val shrunk = DisplayConfig.NORMAL_DEFAULT.copy(wmSize = "1700x720")
        assertFalse(SpeedReadoutPolicy.visible(inp(state = full(AppType.NORMAL, shrunk))), "wm size khác ⇒ toạ độ không 1:1")
        val over = DisplayConfig.NORMAL_DEFAULT.copy(overscan = "10,0,0,0")
        assertFalse(SpeedReadoutPolicy.visible(inp(state = full(AppType.NORMAL, over))))
        assertTrue(SpeedReadoutPolicy.visible(inp(state = full(AppType.NORMAL, DisplayConfig.NORMAL_DEFAULT.copy(density = "160")))),
            "DPI không đổi toạ độ px")
    }

    /**
     * Review 2.89 Pass 3 · cluster-r2-4 — canh gác LUỒNG CHÍNH: luồng đọc HAL treo (binder không trả lời) thì số cũ KHÔNG được đứng
     * sáng đầy trên đồng hồ. Thử ĐỎ: bỏ nhánh `renderAge` (chỉ xét số tốt) ⇒ ca "HAL trả null nhanh" thành gỡ/gắn lặp; bỏ nhánh
     * `goodAge` ⇒ ca "số cũ" không mờ.
     */
    @Test
    fun `Pass 3 - canh gac luong chinh - so cu mo, luong doc treo thi go, HAL tra null nhanh khong go`() {
        val w = SpeedReadoutPolicy::watchdog
        assertEquals(SpeedReadoutPolicy.Watch.KEEP, w(1_000L, 1_000L, 1_000L + SpeedReadoutPolicy.STALE_MS))
        assertEquals(SpeedReadoutPolicy.Watch.DIM, w(1_000L, 1_900L, 1_000L + SpeedReadoutPolicy.STALE_MS + 1), "số tốt quá 1 s ⇒ mờ")
        assertEquals(SpeedReadoutPolicy.Watch.DIM, w(null, 5_000L, 5_100L), "chưa có số tốt ⇒ mờ")
        assertEquals(SpeedReadoutPolicy.Watch.DIM, w(0L, 9_900L, 10_000L), "HAL trả null nhanh: luồng đọc vẫn đẩy nhịp ⇒ mờ, KHÔNG gỡ")
        val stall = 2_000L + SpeedReadoutPolicy.WORKER_STALL_MS
        assertEquals(SpeedReadoutPolicy.Watch.DIM, w(1_000L, 2_000L, stall), "đúng ngưỡng ⇒ còn mờ")
        assertEquals(SpeedReadoutPolicy.Watch.DETACH, w(2_000L, 2_000L, stall + 1), "luồng đọc treo quá ngưỡng ⇒ gỡ")
        assertEquals(SpeedReadoutPolicy.Watch.DETACH, w(1_000L, null, 1_000L), "chưa từng đẩy nhịp ⇒ gỡ")
        assertEquals(SpeedReadoutPolicy.Watch.DETACH, w(1_000L, 5_000L, 4_000L), "đồng hồ lùi ⇒ không tin ⇒ gỡ")
        assertTrue(SpeedReadoutPolicy.WORKER_STALL_MS > SpeedReadoutPolicy.STALE_MS, "mờ TRƯỚC, gỡ SAU")
    }
}
