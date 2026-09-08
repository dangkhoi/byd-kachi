package com.byd.clusternav.navigation

import com.byd.clusternav.navigation.screencapture.CameraMatch
import com.byd.clusternav.navigation.screencapture.ScreenCaptureSignal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * **B3.49 — luật THUẦN của đường đổi menu nguồn, khoá ở CHÍNH module chứa nó (`:core`).**
 *
 * VÌ SAO PHẢI CÓ FILE NÀY (phát hiện [P3] của phản biện 08-23): [NavSourceModeSwitch] và
 * [ScreenCaptureSignal.dropDisallowed] sống ở `:core`, nhưng toàn bộ test khoá chúng lại nằm ở `:app`
 * (`NavSourceModeSwitchTest`). Quy trình chạy test của repo này khuyến nghị chạy `./gradlew :core:test`
 * RIÊNG (bẫy E6) — lượt đó sẽ **không canh** được hai hàm trên. Ở đây khoá phần thuần; ca đi qua
 * `NavOutputOwner` (Android, `:app`) vẫn ở `:app`.
 */
class NavSourceModeSwitchRulesTest {

    private val waze = NavApps.WAZE_RES_PREFIX
    private val wazeMod = "com.chisadin.wazemod"
    private val gmaps = NavApps.GMAPS.first()
    private val vietmap = NavApps.VIETMAP_LIVE
    private val lanes = LaneInfo(listOf(Lane(listOf(Maneuver.STRAIGHT), recommended = true)))

    @BeforeEach fun reset() = ScreenCaptureSignal.clear()
    @AfterEach fun tearDown() = ScreenCaptureSignal.clear()

    // ── 1. Cổng nhóm: kênh sống sót phải khớp ĐÚNG `SourceArbiter.allowedByMode` ───────────────────────
    @Test fun `duyet moi to hop mode x goi - kenh song sot dung bang cong allowedByMode`() {
        val modes = listOf(
            NavSourceMode.AUTO to null,
            NavSourceMode.PREFER_GMAPS to NavApps.GMAPS,
            NavSourceMode.PREFER_WAZE to NavApps.WAZE,
            NavSourceMode.PREFER_VIETMAP to NavApps.VIETMAP,
        )
        val allPkgs = (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP).toList()
        modes.forEach { (mode, allowed) ->
            allPkgs.forEach { pkg ->
                ScreenCaptureSignal.clear()
                ScreenCaptureSignal.publishArrow(pkg, Maneuver.STRAIGHT, amap = 9, now = 1_000L)
                val prev = if (mode == NavSourceMode.AUTO) NavSourceMode.PREFER_GMAPS else NavSourceMode.AUTO
                NavSourceModeSwitch.onModeSelected(prev, mode) {}
                val shouldSurvive = allowed == null || pkg in allowed
                assertEquals(
                    shouldSurvive, ScreenCaptureSignal.arrow != null,
                    "mode=$mode pkg=$pkg — kênh phải sống đúng bằng cổng SourceArbiter.allowedByMode",
                )
                assertEquals(
                    if (shouldSurvive) emptySet<String>() else setOf(pkg),
                    ScreenCaptureSignal.consumeFrameRelease(),
                    "mode=$mode pkg=$pkg — chỉ gói bị gỡ kênh mới được yêu cầu nhả khung",
                )
            }
        }
    }

    /** NHÓM ≠ GÓI: `PREFER_WAZE` phải giữ CẢ HAI thành viên (zin + WazeMod — cấu hình có thật của owner). */
    @Test fun `PREFER_WAZE giu ca hai goi cua nhom`() {
        ScreenCaptureSignal.publishArrow(waze, Maneuver.TURN_LEFT, amap = 2, now = 1_000L)
        ScreenCaptureSignal.publishLane(wazeMod, lanes, now = 1_000L)
        val changed = NavSourceModeSwitch.onModeSelected(NavSourceMode.AUTO, NavSourceMode.PREFER_WAZE) {}
        assertTrue(changed, "mode đổi thật")
        assertNotNull(ScreenCaptureSignal.arrow, "Waze zin thuộc nhóm ⇒ giữ")
        assertNotNull(ScreenCaptureSignal.lane, "WazeMod cũng thuộc nhóm ⇒ giữ")
        assertTrue(ScreenCaptureSignal.consumeFrameRelease().isEmpty(), "không gỡ gì ⇒ không nhả khung")
    }

    // ── 2. Bẫy Spinner ────────────────────────────────────────────────────────────────────────────────
    @Test fun `trung mode - no-op tuyet doi`() {
        ScreenCaptureSignal.publishArrow(gmaps, Maneuver.TURN_LEFT, amap = 2, now = 1_000L)
        var persistCalls = 0
        assertFalse(NavSourceModeSwitch.onModeSelected(NavSourceMode.PREFER_VIETMAP, NavSourceMode.PREFER_VIETMAP) { persistCalls++ })
        assertEquals(0, persistCalls, "trùng mode ⇒ không ghi prefs")
        assertNotNull(ScreenCaptureSignal.arrow, "trùng mode ⇒ không đụng kênh nào")
        assertTrue(ScreenCaptureSignal.consumeFrameRelease().isEmpty())
    }

    // ── 3. Yêu cầu nhả khung ──────────────────────────────────────────────────────────────────────────
    @Test fun `gom du CA BA kenh bi go vao mot yeu cau nha khung, one-shot`() {
        ScreenCaptureSignal.publishArrow(waze, Maneuver.TURN_LEFT, amap = 2, now = 1_000L)
        ScreenCaptureSignal.publishLane(gmaps, lanes, now = 1_000L)
        ScreenCaptureSignal.publishCamera(vietmap, CameraMatch(true, 1f, "t", null, 120), now = 1_000L)

        val dropped = ScreenCaptureSignal.dropDisallowed { it in NavApps.VIETMAP }
        assertEquals(setOf(waze, gmaps), dropped, "trả về ĐỦ các gói bị gỡ, không chỉ gói đầu")
        assertNull(ScreenCaptureSignal.arrow)
        assertNull(ScreenCaptureSignal.lane)
        assertNotNull(ScreenCaptureSignal.camera, "gói được phép KHÔNG bị đụng")

        assertEquals(setOf(waze, gmaps), ScreenCaptureSignal.consumeFrameRelease())
        assertTrue(ScreenCaptureSignal.consumeFrameRelease().isEmpty(), "one-shot")
    }

    /** `clear()` xoá sạch kênh ⇒ owner tự nhả khung theo `clear = !anyFresh`; yêu cầu cũ phải được dọn. */
    @Test fun `clear don luon yeu cau nha khung dang cho`() {
        ScreenCaptureSignal.publishArrow(waze, Maneuver.TURN_LEFT, amap = 2, now = 1_000L)
        ScreenCaptureSignal.dropDisallowed { it in NavApps.VIETMAP }
        ScreenCaptureSignal.clear()
        assertTrue(ScreenCaptureSignal.consumeFrameRelease().isEmpty(), "clear() dọn luôn yêu cầu treo")
    }

    // ── 4. Đua publish↔gỡ kênh: gỡ bằng compare-and-set, không nuốt nhịp MỚI ──────────────────────────
    /**
     * KHOÁ [P3] của phản biện 08-23: trước bản CAS, [ScreenCaptureSignal.dropDisallowed] là đọc-rồi-ghi trên
     * ô `@Volatile` — một `publish*` xen giữa `allow(pkg)` và `arrow = null` bị **mất trắng**.
     *
     * Test này tái lập cửa sổ đó **tất định** (không cần luồng thật): lời gọi lại [ScreenCaptureSignal
     * .publishArrow] nằm NGAY TRONG lambda `allow` — tức đúng vị trí luồng capture chen vào.
     */
    @Test fun `publish xen giua luc go kenh - nhip MOI khong bi nuot`() {
        ScreenCaptureSignal.publishArrow(waze, Maneuver.TURN_LEFT, amap = 2, now = 1_000L)
        val dropped = ScreenCaptureSignal.dropDisallowed { pkg ->
            if (pkg == waze) {
                // Luồng capture publish một nhịp MỚI của app VẪN ĐƯỢC PHÉP, ngay giữa lúc trọng tài đang hỏi.
                ScreenCaptureSignal.publishArrow(vietmap, Maneuver.TURN_RIGHT, amap = 3, now = 1_500L)
            }
            pkg in NavApps.VIETMAP
        }
        val a = ScreenCaptureSignal.arrow
        assertNotNull(a, "nhịp mới của app được phép KHÔNG được bị nuốt")
        assertEquals(vietmap, a!!.pkg)
        assertEquals(1_500L, a.atMs)
        assertTrue(dropped.isEmpty(), "ô đã đổi chủ ⇒ không tính là đã gỡ kênh nào")
        assertTrue(ScreenCaptureSignal.consumeFrameRelease().isEmpty(), "không gỡ gì ⇒ không nhả khung")
    }
}
