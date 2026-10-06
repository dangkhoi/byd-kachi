package com.byd.clusternav.vietmapwidget

import com.byd.clusternav.vietmapwidget.VietMapWidgetRestorePlan.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 2.90 · R10 — gốc badge giới hạn tốc độ không hiện sáng 06/10. Dòng log thật (`usage-all.log`, Kachi 2.89 trên Seal):
 * ```
 * 10-06 07:12:00.995 W/VietMapWidget(  592): getAppWidgetInfo(38) returned null for SPEED_LIMIT — keeping saved ID
 * 10-06 07:12:00.996 W/VietMapWidget(  592): getAppWidgetInfo(39) returned null for ALERTS — keeping saved ID
 * 10-06 07:12:00.997 W/VietMapWidget(  592): getAppWidgetInfo(40) returned null for ALERT_FULL — keeping saved ID
 * 10-06 07:12:01.516 I/SpeedBadgeOverlay(  592): overlay initialized for display 4 (1920x720)
 * ```
 * rồi 0 dòng `ClusterSpeedBadge show` tới 07:52 dù đang dẫn đường. Bản cũ giữ id chết mãi (tự khoá). Provider có cài (không zombie)
 * mà `getAppWidgetInfo` null ⇒ widget đã mất ([ĐO nguồn r47] `AppWidgetServiceImpl.java:1410-1417`, `:1725`) ⇒ bỏ id để bind lại.
 * Thử ĐỎ: trả nhánh `infoProvider == null` về `KEEP_WAIT` vô điều kiện (hành vi 2.89).
 */
class VietMapWidgetRestorePlanTest {
    private val speed = "vn.vietmap.live/vn.vietmap.live.homewidget.SpeedLimitWidgetProvider"

    @Test
    fun `log 06-10 - info null, provider con cai - BO id va bind lai`() {
        assertEquals(Action.DROP_REBIND, VietMapWidgetRestorePlan.decide(null, speed, providerInstalled = true))
    }

    @Test
    fun `info null, provider vang (dang cai lai, zombie luc khoi dong) - giu id cho`() {
        assertEquals(Action.KEEP_WAIT, VietMapWidgetRestorePlan.decide(null, speed, providerInstalled = false))
    }

    @Test
    fun `hanh vi cu giu nguyen - lech provider, provider da go, gan binh thuong`() {
        assertEquals(Action.DROP_MISMATCH, VietMapWidgetRestorePlan.decide("vn.other/x.Y", speed, providerInstalled = true))
        assertEquals(Action.DROP_UNINSTALLED, VietMapWidgetRestorePlan.decide(speed, speed, providerInstalled = false))
        assertEquals(Action.ATTACH, VietMapWidgetRestorePlan.decide(speed, speed, providerInstalled = true))
    }
}
