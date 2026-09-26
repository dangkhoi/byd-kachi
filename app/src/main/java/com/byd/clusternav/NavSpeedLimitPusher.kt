package com.byd.clusternav

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.contracts.SpeedLimitSource
import com.byd.clusternav.navigation.NavParse
import com.byd.clusternav.vietmapwidget.VietMapWidgetFreshness
import com.byd.clusternav.vietmapwidget.VietMapWidgetSnapshot

/**
 * ═══ VAI "cầu widget VietMap → badge tốc độ" của [NavNotificationListener] ═══════════════════════════════════
 *
 * Tách khỏi `NavNotificationListener.kt` (506 dòng → trần 500, CLAUDE.md §4.1) theo VAI, cùng khuôn `DEBT-500`
 * (`ControlRegistry`→`ControlDef`, `VoiceSession`→`VoiceSessionTurns`). Đây là **đúng cái lambda `speedLimitPusher`**
 * trước đây nằm trong listener — thân hàm [invoke] chép NGUYÊN VĂN, không đổi một nhánh, một chuỗi log hay một
 * cổng `Prefs` nào; chỉ đổi hình: lambda → lớp thực thi `(VietMapWidgetSnapshot) -> Unit`, để listener vẫn
 * `bridge.addListener(speedLimitPusher)` / `bridge.removeListener(speedLimitPusher)` bằng đúng MỘT thể hiện
 * (bridge so listener theo danh tính `===`).
 *
 * Vì sao là lớp riêng chứ không phải hàm mở rộng của listener: thân hàm chỉ chạm hai thứ của listener
 * (`applicationContext` + `speedSignOwner`), cả hai bất biến sau khi service attach ⇒ nhận qua constructor là
 * đủ, không cần mở `internal` trường nào của một `NotificationListenerService`.
 *
 * Bài canh nguồn đọc tệp này: `SpeedSignSourceLifecycleTest` · `SpeedBadgeArmingWiringTest` (F1: sync mỗi nhịp)
 * · `AlertChipWiringContractTest`.
 */
// ─── Nguồn tín hiệu speed-limit: CHỈ widget VietMap ────────────────────────────────────────────────
// 2026-08-22: gỡ hẳn nhánh Waze HLP (WazeHudSource). Nó poll `logcat -s WazeHudLink` qua dadb mỗi 900ms
// (~4000 lệnh shell/giờ) và chạy VÔ ĐIỀU KIỆN — không theo lựa chọn nguồn, thậm chí TRƯỚC cổng
// Prefs.enabled — để rồi nhận về 0 dòng: WazeMod chỉ phát tag đó khi có peer HUD BT/BLE (đo 08-22 trên
// máy không HUD: Waze đang dẫn, logcat rỗng). Bỏ đi là bớt hao pin mà không mất tín hiệu nào.
// Comment cũ "speed ports = Noop" LỖI THỜI: đường VietMap dưới đây chạy thật, chính nó vẽ badge trên cụm.
internal class NavSpeedLimitPusher(
    private val applicationContext: Context,
    private val speedSignOwner: NavigationSpeedSignOwner,
) : (VietMapWidgetSnapshot) -> Unit {

    private companion object {
        /** Cùng tag với listener — chuỗi log trên xe không đổi sau khi tách. */
        private const val TAG = "NavListener"
    }

    override fun invoke(snapshot: VietMapWidgetSnapshot) {
        // TỰ LÀNH THEO NHỊP — khôi phục đúng hành vi vòng poll `WazeHudSource` đã gỡ (B3.30, hồi quy F1):
        // bộ điều phối có thể bị đưa về TẮT SẠCH bất cứ lúc nào (owner được dựng lại sau khi process bị giết).
        // Rẻ: `onMasterEnabled`/`onOutputEnabled`/`onSourceSelected` đều return sớm khi giá trị không đổi.
        speedSignOwner.syncFromPrefs()
        if (snapshot.speedFreshness == VietMapWidgetFreshness.FRESH) {
            speedSignOwner.onSpeedLimit(
                source = SpeedLimitSource.VIETMAP,
                valueKph = snapshot.speedLimitKph ?: 0,
                observedAtMonotonicMs = snapshot.speedUpdatedAtElapsedMs ?: SystemClock.elapsedRealtime(),
            )
        } else {
            speedSignOwner.onProviderDisconnected(SpeedLimitSource.VIETMAP)
        }
        // ── Upcoming speed-limit badge (spec upcoming-speed-limit-badge, ADDITIVE) ──────────────────────
        // Mirror VietMap's "speed-limit ahead" (ALERT_FULL slot) onto a smaller badge + countdown BELOW the
        // main badge on the cluster. Pure decision in :core (UpcomingBadgeDecision) — OQ2: no own distance
        // threshold, show exactly when VietMap shows a FRESH upcoming limit; hide when null/stale/reached.
        // Gated by the user toggle Prefs.showUpcomingBadge (default ON). Degrade-safe (never throws into the feed).
        runCatching {
            if (Prefs.showUpcomingBadge(applicationContext)) {
                val d = com.byd.clusternav.navigation.UpcomingBadgeDecision.decide(
                    limitKph = snapshot.upcomingLimitKph,
                    distanceMeters = snapshot.upcomingDistanceMeters,
                    fresh = snapshot.alertFullFreshness == VietMapWidgetFreshness.FRESH,
                )
                if (d.show) {
                    speedSignOwner.setUpcomingBadge(d.limitKph, d.distanceMeters, snapshot.upcomingDistanceText)
                } else {
                    speedSignOwner.setUpcomingBadge(null, null, null)
                }
            } else {
                speedSignOwner.setUpcomingBadge(null, null, null)
            }
        }.onFailure { Log.w(TAG, "upcoming badge push failed", it) }
        // ── Road-alert / speed-camera chip (B3.20, ADDITIVE) ────────────────────────────────────────────
        // Mirror VietMap's sticky ALERTS-slot road alert (speed camera / hazard ahead + enforced limit +
        // distance) onto a chip to the RIGHT of the main badge. Pure decision in :core (RoadAlertChipDecision).
        // Gated by Prefs.showAlertChip (default OFF — opt-in, không phá bố trí badge hiện có). Degrade-safe.
        runCatching {
            if (Prefs.showAlertChip(applicationContext)) {
                val d = com.byd.clusternav.navigation.RoadAlertChipDecision.decide(
                    alerts = snapshot.alerts,
                    fresh = snapshot.alertsFreshness == VietMapWidgetFreshness.FRESH,
                )
                val text = d.distanceText?.trim()?.takeIf { it.isNotEmpty() }
                    ?: d.distanceMeters.takeIf { it > 0 }?.let { NavParse.formatMeters(it) }
                speedSignOwner.setRoadAlertChip(d.show, d.limitKph, text, d.hasIcon)
            } else {
                speedSignOwner.setRoadAlertChip(false, 0, null, false)
            }
        }.onFailure { Log.w(TAG, "alert chip push failed", it) }
    }
}
