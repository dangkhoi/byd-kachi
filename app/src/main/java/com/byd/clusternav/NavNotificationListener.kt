package com.byd.clusternav

import com.byd.clusternav.navigation.ManeuverHold
import com.byd.clusternav.navigation.NavArrivalGuard
import com.byd.clusternav.navigation.NavFormat
import com.byd.clusternav.navigation.NavParse
import com.byd.clusternav.navigation.SourceArbiter
import com.byd.clusternav.navigation.TurnDistanceInterpolator
import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.navigation.NavigationPermission
import com.byd.clusternav.contracts.SpeedLimitSource
import com.byd.clusternav.vietmapwidget.VietMapWidgetBridge
import com.byd.clusternav.vietmapwidget.VietMapWidgetFreshness
import com.byd.clusternav.vietmapwidget.VietMapWidgetOwner
import com.byd.clusternav.modules.clustercast.ClusterNavLaneWidget
import android.content.Context

/**
 * Adapter MỎNG cho notification dẫn đường (Google Maps / ReVanced). Chỉ làm:
 *   gate (đúng app + Prefs.enabled + ongoing) -> rút field thô + bitmap (lazy) -> hỏi SourceArbiter ->
 *   NotificationParser dựng NavState -> fan-out ClusterBroadcaster (làn nav zin) + NavRepository (card).
 */
class NavNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NavListener"
        // TRẠNG THÁI BIND: true khi hệ thống đã bind service (onListenerConnected). Dùng để auto-reconnect lúc mở
        // app: chỉ chạy dadb disallow/allow khi CHƯA bound (tránh ngắt kết nối đang chạy tốt).
        @Volatile var connected = false
        // Token cự ly (m/km/ft/mi) — dấu hiệu noti dẫn đường, dùng khi category không phải navigation.
        private val DIST_TOKEN = Regex("""\b\d+([.,]\d+)?\s?(m|km|ft|mi)\b""", RegexOption.IGNORE_CASE)
        // "Đã đến nơi" — phát hiện KẾT-THÚC-NAV dùng chung ở NavArrivalGuard.isArrivalText (R7/#2).
        /**
         * Roster **KÊNH NOTIFICATION** — nguồn sự thật duy nhất ở
         * [com.byd.clusternav.navigation.NavApps.NOTIFICATION] (đọc KDoc ở đó để biết ai trong/ngoài và VÌ SAO).
         *
         * ⚠ ĐÂY KHÔNG PHẢI [com.byd.clusternav.navigation.NavApps.ALL] nữa (đổi 08-23). Trước đó nó trỏ ALL, nên
         * VietMap đi qua `handle()` ⇒ `SourceArbiter.shouldFeed(pkg, …, DATA)` đóng mốc `lastDataByPkg[vietmap]`
         * ⇒ `isDataFresh(vietmap)` = true trong 6 s ⇒ **kênh IMAGE của chính VietMap bị chặn** ⇒ mũi tên
         * screen-capture không bao giờ lên được cụm (backlog B3.42). Gói ra khỏi roster này **không mất kênh
         * nào khác**: a11y (`NavApps.ALL` + XML) và widget/screen-capture giữ nguyên.
         */
        val MAPS_PACKAGES = com.byd.clusternav.navigation.NavApps.NOTIFICATION
    }

    // Speed-sign owner: giới hạn tốc độ từ widget VietMap → badge cụm + HAL. (Comment cũ "Noop" đã lỗi thời) —
    // đây là base research (xem NavigationSpeedSignOwner + docs/specs/waze-vietmap-signal-revival.html).
    private val speedSignOwner by lazy { NavigationSpeedSignOwner.get(applicationContext) }

    /**
     * R7 (#2): per-session arrival + distance-regression guard (pure logic in :core). Reset on
     * STOP / arrival / notification removal so each route starts clean.
     */
    private val arrivalGuard = NavArrivalGuard()

    // Giữ hướng rẽ hợp lệ gần nhất TRONG PHIÊN (chống nháy HUD, owner 2026-08-15): 1 noti GMaps lỡ không đọc
    // được arrow → dùng lại hướng trước thay vì rớt straight. Reset ở ranh giới phiên (đến nơi / gỡ noti).
    @Volatile private var lastManeuverIcon: Int = -1

    // D4 (closeout 1.28): last-logged dist|road|eta for log-on-change on the accepted-notification log — kills
    // per-notification spam while keeping a low-rate signal. Reset at session boundaries (like lastManeuverIcon).
    @Volatile private var lastNavLogKey: String? = null

    // RAW-notif capture collapse (diagnostic only): last raw (title\u0001text\u0001sub\u0001big) per package.
    // Touched ONLY on the listener callback thread (onNotificationPosted + the onListenerConnected
    // activeNotifications scan both run on the main looper), so a plain HashMap with no lock is safe. Skips
    // CONSECUTIVE-IDENTICAL notifs (GMaps redraws the same frame ~1/s) so the raw CSV isn't flooded. Bounded to
    // the five nav packages (`NavApps.ALL` — the gate of its owner [recordRawNotif]; NOT the notification-channel
    // roster `NavApps.NOTIFICATION`, xem KDoc ở đó). NOT reset at session boundaries — it never feeds the
    // cluster, purely a flood guard.
    private val lastRaw = HashMap<String, String>()

    /** Hệ thống THẢ binding (head-unit hay làm lúc chạy) → clear typed sources before teardown. */
    override fun onListenerDisconnected() {
        connected = false
        // TÍN HIỆU DƯƠNG "nguồn dừng" (B1 Lỗ 2, handoff 2026-08-15): binding rớt = KHÔNG còn noti feed nữa →
        // dừng phiên authoritative để hudOwner.stop() HUỶ nhịp keep-alive; nếu thiếu, nhịp tim + frame cũ bị
        // ghim vô hạn tới khi mở lại app (mũi tên sai mà cụm vẫn "tự tin"). Cùng shape với nhánh
        // onNotificationRemoved: stop() + ClusterNavLaneWidget.onNavIdle() + log, bọc runCatching như các
        // nhánh teardown khác trong hàm này.
        runCatching {
            // Ranh giới phiên = "rớt binding" (KDoc ManeuverHold + NavArrivalGuard): reset state per-phiên như
            // nhánh onNotificationRemoved/arrival để tuyến MỚI sau rebind không kế thừa hướng rẽ / mốc cự ly cũ.
            arrivalGuard.reset(); lastManeuverIcon = -1; lastNavLogKey = null
            NavRepository.stop(applicationContext)
            ClusterNavLaneWidget.onNavIdle()
            Log.i(TAG, "binding thả -> stop authoritative session (nguồn dừng)")
        }.onFailure { Log.e(TAG, "stop on disconnect failed", it) }
        // ★ Revive: teardown tín hiệu (speed-sign + VietMap widget bridge + Waze HUD poll) — cô lập trong runCatching
        // để KHÔNG chặn keep-alive stop ở trên (B1 1.30) nếu nguồn tín hiệu ném lỗi.
        runCatching {
            speedSignOwner.onProviderDisconnected(SpeedLimitSource.VIETMAP)
            val bridge = VietMapWidgetBridge.get(applicationContext)
            bridge.stop(VietMapWidgetOwner.NAVIGATION)
            bridge.removeListener(speedLimitPusher)
        }.onFailure { Log.e(TAG, "signal teardown on disconnect failed", it) }
        runCatching { NavRepository.setPermission(applicationContext, NavigationPermission.UNKNOWN) }
            .onFailure { Log.e(TAG, "permission state update failed", it) }
        runCatching {
            requestRebind(android.content.ComponentName(this, NavNotificationListener::class.java))
        }.onFailure { Log.e(TAG, "requestRebind on disconnect failed", it) }
    }

    /** Khi (re)cấp quyền / service bind lại: clear cờ kẹt + QUÉT noti đang hiện (nav có thể đã chạy trước). */
    override fun onListenerConnected() {
        connected = true
        // D1 (closeout 1.28): entry point that always runs on (re)bind → refresh the in-memory verbose gate
        // (set BEFORE the enabled early-return so the gate is correct even while Nav+HUD is OFF).
        NavLog.init(applicationContext)
        // Storage cap (defensive, always-on): trim the app-external diagnostics dir to the ~150 MB cap at every
        // session start. force=true bypasses the throttle so it always runs on connect. This bounds a long
        // verbose drive AND cleans data a previous verbose session left behind even if verbose is now OFF, so a
        // data-collection build can never fill the car's storage. Off-thread + degrade-safe (never throws).
        runCatching { DiagStorageCap.enforce(applicationContext, force = true) }
        // Khởi động nguồn tín hiệu: speed-sign sync + VietMap widget bridge. (Nhánh poll logcat của Waze đã
        // gỡ 08-22 — nó chạy trước cả cổng Prefs.enabled và tốn ~4000 lệnh shell/giờ để nhận về 0 dòng.)
        // Cô lập trong runCatching.
        runCatching {
            speedSignOwner.syncFromPrefs()
            val bridge = VietMapWidgetBridge.get(applicationContext)
            bridge.start(VietMapWidgetOwner.NAVIGATION)
            bridge.addListener(speedLimitPusher)
        }.onFailure { Log.e(TAG, "signal source start failed", it) }
        if (!Prefs.enabled(applicationContext)) return
        SourceArbiter.clear()
        runCatching { NavRepository.setPermission(applicationContext, NavigationPermission.GRANTED) }
            .onFailure { Log.e(TAG, "coordinator connect failed", it) }
        Log.i(TAG, "listener connected -> authoritative coordinator ready")
        // QUAN TRỌNG: nav có thể ĐÃ dẫn trước khi listener bind (cài/mở app sau khi đang dẫn, hoặc xe đỗ
        // -> noti đứng yên, onNotificationPosted không kích hoạt). Quét noti hiện tại + bơm ngay.
        runCatching {
            activeNotifications?.forEach { sbn ->
                // Cùng thứ tự như `onNotificationPosted`: đo trước (cả 5 gói), rồi mới tới cổng kênh.
                if (sbn.packageName !in com.byd.clusternav.navigation.NavApps.ALL) return@forEach
                recordRawNotif(sbn)
                if (sbn.packageName in MAPS_PACKAGES) handle(sbn)
            }
        }.onFailure { Log.e(TAG, "scan active notifications failed", it) }
    }

    override fun onDestroy() {
        connected = false
        // ★ Revive: teardown tín hiệu (cô lập).
        runCatching {
            speedSignOwner.onSourceStopped(SpeedLimitSource.VIETMAP)
            val bridge = VietMapWidgetBridge.get(applicationContext)
            bridge.stop(VietMapWidgetOwner.NAVIGATION)
            bridge.removeListener(speedLimitPusher)
        }.onFailure { Log.e(TAG, "signal teardown on destroy failed", it) }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        // Cổng 1 — roster ĐỌC-ĐƯỢC: mọi app dẫn đường ta quan tâm. GIỮ NGUYÊN như trước 08-23 để cái lưới an
        // toàn `ensureBridgeStarted` dưới đây không hẹp đi: nó CHỈ bật lại cầu widget VietMap (speed badge) và
        // TUYỆT ĐỐI KHÔNG chạm SourceArbiter ⇒ chạy nó cho một notification VietMap là vô hại, mà bỏ đi thì
        // một máy chỉ dùng VietMap mất đường hồi phục sau khi process bị giết mà onListenerConnected không
        // re-fire (đúng ca hàm này sinh ra để chữa).
        if (sbn.packageName !in com.byd.clusternav.navigation.NavApps.ALL) return
        if (!Prefs.enabled(applicationContext)) return        // công tắc tổng TẮT -> không đẩy cụm
        // Safety net: ensure the connected flag is set even if onListenerConnected was not re-fired after process restart
        ensureBridgeStarted()
        // MÁY ĐO đứng TRƯỚC cổng 2 (08-23 vòng 3): chẩn đoán thô phải phủ CẢ 5 gói `NavApps.ALL`, không co
        // theo roster kênh. Nó KHÔNG chạm SourceArbiter / feed cụm — cùng lý do và cùng chỗ đặt như
        // `ensureBridgeStarted` ngay trên.
        recordRawNotif(sbn)
        // Cổng 2 — roster KÊNH NOTIFICATION: chỉ app có DỮ LIỆU DẪN ĐƯỜNG trong notification mới được đi tiếp.
        // Đi tiếp = `handle()` = `SourceArbiter.shouldFeed(…, DATA)` = đóng mốc DATA của gói đó. Xem KDoc
        // [MAPS_PACKAGES] / `NavApps.NOTIFICATION`.
        if (sbn.packageName !in MAPS_PACKAGES) return
        runCatching { handle(sbn) }.onFailure { Log.e(TAG, "handle failed", it) }
    }

    private fun ensureBridgeStarted() {
        if (connected) return
        connected = true
        // ★ Revive: an toàn khởi động nguồn tín hiệu nếu onListenerConnected chưa (re)fire sau khi process restart.
        runCatching {
            // ⚠ PHẢI sync TRƯỚC addListener — hồi quy F1 (owner 08-24: "trước đây lên ngon lành, giờ không lên").
            // `SpeedSignLifecycleCoordinator` dựng kèm `onProcessRestart` nên khởi tạo TẮT SẠCH: masterEnabled=false,
            // selectedSource=NONE, MỌI cổng ra enabled=false (SpeedSignLifecycleCoordinator.kt:84-88).
            // Chỉ `syncFromPrefs()` bật lên. Đường `onListenerConnected` (:145) gọi đúng; đường lưới-an-toàn này
            // TRƯỚC 08-24 KHÔNG gọi ⇒ cầu chạy, widget về, pusher bắn — mà cổng cụm vẫn đóng ⇒ badge CÂM.
            // Vì sao trước 08-22 không lộ: vòng poll `WazeHudSource` gọi `onMasterEnabled(Prefs.enabled)` +
            // `onSourceSelected` MỖI NHỊP nên tự vá hộ. Gỡ nó (B3.30) là mất luôn cái vá vô tình đó.
            speedSignOwner.syncFromPrefs()
            val bridge = VietMapWidgetBridge.get(applicationContext)
            bridge.start(VietMapWidgetOwner.NAVIGATION)
            bridge.addListener(speedLimitPusher)
        }.onFailure { Log.e(TAG, "signal source start (safety net) failed", it) }
        Log.i(TAG, "listener connected (safety net from onNotificationPosted)")
    }

    // ─── Nguồn tín hiệu speed-limit: CHỈ widget VietMap ────────────────────────────────────────────────
    // 2026-08-22: gỡ hẳn nhánh Waze HLP (WazeHudSource). Nó poll `logcat -s WazeHudLink` qua dadb mỗi 900ms
    // (~4000 lệnh shell/giờ) và chạy VÔ ĐIỀU KIỆN — không theo lựa chọn nguồn, thậm chí TRƯỚC cổng
    // Prefs.enabled — để rồi nhận về 0 dòng: WazeMod chỉ phát tag đó khi có peer HUD BT/BLE (đo 08-22 trên
    // máy không HUD: Waze đang dẫn, logcat rỗng). Bỏ đi là bớt hao pin mà không mất tín hiệu nào.
    // Comment cũ "speed ports = Noop" LỖI THỜI: đường VietMap dưới đây chạy thật, chính nó vẽ badge trên cụm.
    private val speedLimitPusher: (com.byd.clusternav.vietmapwidget.VietMapWidgetSnapshot) -> Unit = { snapshot ->
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




    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in MAPS_PACKAGES) return
        // R-2: CHỈ xử lý khi noti bị gỡ đúng là noti DẪN ĐƯỜNG (category=navigation HOẶC có token cự ly) — mirror gate
        // ingest (L~100). Bỏ FLAG_ONGOING (B4, nhiều build không đặt) nhưng KHÔNG được tắt nav khi gỡ noti Maps KHÁC
        // (chia sẻ vị trí / commute / Assistant / lưu chỗ đỗ...) — trước đây cờ ongoing lọc hộ, giờ lọc bằng nav-content.
        run {
            val n = sbn.notification
            val ex = n?.extras
            val t = ex?.getCharSequence("android.title")?.toString().orEmpty()
            val x = ex?.getCharSequence("android.text")?.toString().orEmpty()
            val isNav = n?.category == Notification.CATEGORY_NAVIGATION
            val hasDist = DIST_TOKEN.containsMatchIn(t) || DIST_TOKEN.containsMatchIn(x)
            // PHẢI tính cả ARRIVAL: noti "Đã đến" (build ReVanced KHÔNG set category, arrival KHÔNG có m/km) chính là
            // tín hiệu KẾT-THÚC-NAV để idle cụm — nếu chỉ isNav||hasDist thì nó bị nuốt → cụm kẹt icon-đích 3' (STALE_MS).
            val isArrival = NavArrivalGuard.isArrivalText(t, x)
            if (!isNav && !hasDist && !isArrival) return
        }
        // CHỈ tắt cụm nếu app vừa-gỡ chính là nguồn đang giữ khoá — gỡ noti app nền KHÔNG tắt nav app đang dẫn.
        if (SourceArbiter.release(sbn.packageName)) {
            arrivalGuard.reset(); lastManeuverIcon = -1; lastNavLogKey = null
            NavRepository.stop(applicationContext)
            ClusterNavLaneWidget.onNavIdle()
            Log.i(TAG, "nguồn ${sbn.packageName} dừng -> stop authoritative session")
        }
    }

    /**
     * CHẨN ĐOÁN THÔ (T2b) — ghi MỌI notification của [com.byd.clusternav.navigation.NavApps.ALL] vào CSV kéo
     * được, kể cả loại mà `NavNotifLog` (đã parse) buộc phải giấu: "Waze is running", VietMap "Ứng dụng đang
     * chạy", status WazeMod, và loại chỉ có nội dung ở `subText`/`bigText` (title+text rỗng).
     *
     * ⚠ ĐỨNG NGOÀI [handle] LÀ CỐ Ý (chuyển ra 08-23 vòng 3 — [P2]). Trước đó nó nằm trong [handle], mà VIỆC B
     * thu [MAPS_PACKAGES] về `NavApps.NOTIFICATION` = chỉ GMaps ⇒ máy đo chết đúng với VietMap/Waze — hai app
     * mà repo đang còn câu hỏi mở phải trả bằng phép đo (CLAUDE.md §14 tầng 1: notification VietMap có
     * `category=navigation` không, Waze có bao giờ mang cự ly không). Gỡ máy đo đi rồi thì câu hỏi vĩnh viễn
     * ở mức "chưa biết".
     *
     * KHÔNG PHẢI ĐƯỜNG DỮ LIỆU: hàm này TUYỆT ĐỐI không chạm `SourceArbiter` / feed cụm / nav state, nên gọi nó
     * cho một gói ngoài roster kênh KHÔNG mở lại cái khoá DATA→IMAGE mô tả ở KDoc `NavApps.NOTIFICATION`.
     * verbose-gated (mặc định TẮT) + off-main (`NavNotifRawLog` có Executor daemon riêng) + degrade-safe.
     *
     * Gộp bản TRÙNG LIÊN TIẾP theo gói ([lastRaw]) để không ngập vì GMaps vẽ lại ~1 khung/giây. Khoá gộp gồm 4
     * ô chữ + `category` + có-large-icon, nên chuyển trạng thái status→nav (category lật) hay mũi tên
     * xuất hiện/biến mất vẫn được ghi là bản KHÁC. SOH (\u0001) ngăn ô: nó không bao giờ có trong chữ notif.
     *
     * [lastRaw] không khoá: cả hai call site (`onNotificationPosted` và vòng quét trong `onListenerConnected`)
     * đều chạy trên luồng callback của listener (main looper) — giữ nguyên bất biến từ trước khi tách hàm.
     */
    private fun recordRawNotif(sbn: StatusBarNotification) {
        if (!NavLog.verbose) return
        // runCatching ôm CẢ phần đọc extras, không chỉ lời gọi ghi: trước khi tách hàm, khối này nằm trong
        // `runCatching { handle(sbn) }` của `onNotificationPosted` nên đã được che. `Bundle.getCharSequence`
        // là đường unparcel — một notification hỏng không được phép làm chết callback của listener.
        runCatching {
            val n = sbn.notification ?: return
            val ex = n.extras ?: return
            val title = ex.getCharSequence("android.title")?.toString()?.trim().orEmpty()
            val text = ex.getCharSequence("android.text")?.toString()?.trim().orEmpty()
            val sub = ex.getCharSequence("android.subText")?.toString()?.trim().orEmpty()
            val big = ex.getCharSequence("android.bigText")?.toString()?.trim().orEmpty()
            val category = n.category ?: ""
            val hasLargeIcon = n.getLargeIcon() != null
            val rawKey = "$title\u0001$text\u0001$sub\u0001$big\u0001$category\u0001$hasLargeIcon"
            if (lastRaw[sbn.packageName] == rawKey) return
            lastRaw[sbn.packageName] = rawKey
            NavNotifRawLog.record(
                applicationContext, sbn.packageName, category,
                n.category == Notification.CATEGORY_NAVIGATION,
                DIST_TOKEN.containsMatchIn(title) || DIST_TOKEN.containsMatchIn(text),
                hasLargeIcon, title, text, sub, big,
            )
        }.onFailure { Log.w(TAG, "raw notif log failed", it) }
    }

    private fun handle(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val ex = n.extras ?: return
        val title = ex.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val text = ex.getCharSequence("android.text")?.toString()?.trim().orEmpty()
        val sub = ex.getCharSequence("android.subText")?.toString()?.trim().orEmpty()
        val big = ex.getCharSequence("android.bigText")?.toString()?.trim().orEmpty()
        // RAW notif capture ĐÃ CHUYỂN RA [recordRawNotif], gọi ở `onNotificationPosted` NGAY TRƯỚC cổng
        // [MAPS_PACKAGES] (08-23 vòng 3 — [P2]). VÌ SAO: đó là MÁY ĐO, không phải đường dữ liệu; mà từ VIỆC B
        // chỉ GMaps đi tới được `handle()`, nên để nó nằm ở đây là tự tay gỡ mất máy đo cho đúng hai app đang
        // còn câu hỏi mở ("notification VietMap có `category=navigation` không?" — CLAUDE.md §14 tầng 1).
        if (title.isEmpty() && text.isEmpty()) return
        // ĐÃ ĐẾN NƠI (R7/#2): GMaps/VietMap báo "Arrived/đã đến" → PHÁT STOP/CLEAR cụm (về đồng hồ),
        // KHÔNG cắm frame kẹt heart-beat STALE_MS. Trước đây nhánh này ingest 1 frame icon-đích (15) và
        // GIỮ tới khi noti bị gỡ; nếu noti không bị gỡ (hoặc frame khác đè), cụm kẹt (owner 2026-08:
        // "GMaps đã báo tới nơi mà cụm kẹt 3.5 km đi thẳng"). Giờ đóng đường về gauges ngay.
        if (NavArrivalGuard.isArrivalText(title, text, big)) {
            // R3: "đã đến" cũng phải qua trọng tài — app NỀN báo đến KHÔNG được đè cụm đang do app khác giữ.
            if (!SourceArbiter.shouldFeed(sbn.packageName, Prefs.sourceMode(applicationContext), System.currentTimeMillis())) {
                // …NHƯNG nếu chính gói này đang GIỮ cụm mà vừa bị cổng nguồn loại (B3.48, xem
                // [stopClusterOwnedBy]) thì lệnh dừng KHÔNG được nuốt: đó đúng là hồi quy R7/#2.
                if (SourceArbiter.release(sbn.packageName)) stopClusterOwnedBy(sbn.packageName, "đã đến nơi (nguồn vừa bị cổng loại)")
                return
            }
            stopClusterOwnedBy(sbn.packageName, "đã đến nơi")
            return
        }
        // NHẬN noti dẫn đường: category=navigation HOẶC có TOKEN CỰ LY trong title/text (bản GMaps patched/ReVanced
        // đôi khi không đặt category -> trước đây bị drop sạch = "mất tín hiệu"). Vẫn LOẠI noti không phải dẫn đường
        // (vd VietMap "Ứng dụng đang chạy" — không có cự ly). KHÔNG đòi FLAG_ONGOING nữa (một số build không đặt).
        val isNav = n.category == Notification.CATEGORY_NAVIGATION
        val hasDist = DIST_TOKEN.containsMatchIn(title) || DIST_TOKEN.containsMatchIn(text)
        if (!isNav && !hasDist) return

        // INSTRUMENTATION (chẩn đoán, không đụng feed cụm): ghi nhịp noti + giữ ref thô cho RemoteViews-introspection.
        NavDiag.record(sbn.packageName, title, text, sub, big, n.getLargeIcon() != null)
        NavDiag.lastRaw = n; NavDiag.lastRawPkg = sbn.packageName

        // Trọng tài chọn nguồn (theo chế độ Prefs): nếu không tới lượt thì BỎ QUA frame này.
        if (!SourceArbiter.shouldFeed(sbn.packageName, Prefs.sourceMode(applicationContext), System.currentTimeMillis())) {
            // B3.48 — CẮT NGUỒN THÌ PHẢI KÈM LỆNH DỪNG. `release` vừa HỎI vừa nhả: true ⇔ gói vừa bị loại
            // đúng là gói đang GIỮ cụm ⇒ không còn ai nuôi khung ⇒ đóng cụm về đồng hồ NGAY, thay vì để
            // nhịp tim ghim khung cuối (xem [stopClusterOwnedBy] để biết ghim bao lâu và vì sao chết người).
            if (SourceArbiter.release(sbn.packageName)) stopClusterOwnedBy(sbn.packageName, "nguồn bị cổng loại")
            else Log.i(TAG, "bỏ qua ${sbn.packageName}: nguồn khác đang giữ cụm")
            return
        }
        ClusterBroadcaster.selectSource(sbn.packageName)
        ClusterNavLaneWidget.onNavActive(applicationContext)

        // HƯỚNG RẼ: thử tên small-icon (ReVanced GMaps luôn logo -> trượt) rồi tới đọc ẢNH large-icon.
        val manIcon = IconResource.resolve(applicationContext, sbn.packageName, n.smallIcon)
        // Large-icon = nguồn hướng rẽ THẬT cho GMaps này -> LUÔN dựng (54×54, rẻ).
        val arrow = loadIconBitmap(n)

        // v1.03: classify maneuver BEFORE creating the immutable frame so it carries the
        // final code through the typed boundary. No global arrow lookup needed later.
        // Phân loại hướng rẽ frame NÀY (null = không đọc được: thiếu large-icon / chữ ký lệch ngưỡng / không verb).
        val freshIcon = manIcon.takeIf { it in 0..28 }
            ?: com.byd.clusternav.navigation.ManeuverSignature.classify(arrow?.asPixelFrame())
            ?: com.byd.clusternav.navigation.NavFormat.maneuverVerbIcon(title.ifBlank { text })
            ?: com.byd.clusternav.navigation.ArrowClassifier.classify(arrow?.asPixelFrame())
        // Chống nháy HUD: frame lỗi đọc → GIỮ hướng rẽ trước (không rớt -1 → straight); fresh hợp lệ → cập nhật mốc.
        val classifiedIcon = ManeuverHold.resolve(freshIcon, lastManeuverIcon)
        if (classifiedIcon in 0..28) lastManeuverIcon = classifiedIcon

        // TASK 1 (closeout 1.28): mang MANEUVER CÓ HƯỚNG cho họ vòng xuyến sang NavState.maneuver. Bottleneck cũ:
        // classifiedIcon là AMAP-int nên MỌI vòng xuyến gộp về 11 → NavRepository.ingest fromAmapIcon(11)=ROUNDABOUT
        // generic → HUD toHudIcon()=20 (mất hướng ra). classifyManeuver đọc CHÍNH large-icon frame này và CHỈ trả
        // non-null cho chữ ký vòng xuyến (ROUNDABOUT_LEFT/RIGHT/STRAIGHT/UTURN ± _CW). Mọi frame KHÁC — kể cả frame
        // lỗi đọc / bị ManeuverHold GIỮ (arrow không khớp registry) — rơi về fromAmapIcon(classifiedIcon): hành vi
        // non-roundabout KHÔNG đổi, và vòng xuyến trên frame bị-giữ degrade về generic (chấp nhận được per handoff:
        // generic còn hơn sai hướng). Ưu tiên SỐ-LỐI-RA (24+N) vẫn do NavRepository quyết — KHÔNG đụng ở đây.
        val maneuver = com.byd.clusternav.navigation.ManeuverSignature.classifyManeuver(arrow?.asPixelFrame())
            ?: com.byd.clusternav.navigation.Maneuver.fromAmapIcon(classifiedIcon)

        val state = (NotificationParser.parse(sbn.packageName, title, text, sub, big, arrow, classifiedIcon) ?: return)
            .copy(maneuver = maneuver)

        // T2 (telemetry): persist the RAW notification + the parsed NavState to a pullable CSV so a drive's
        // per-turn data can be pulled and used to improve arrow/road/distance accuracy. verbose-gated (default
        // OFF) + off-main (NavNotifLog writes on its own daemon Executor) + degrade-safe (never affects nav).
        if (NavLog.verbose) runCatching {
            NavNotifLog.record(
                applicationContext, sbn.packageName, title, text, sub, big, n.getLargeIcon() != null,
                state.maneuverIcon, state.distance, state.road, state.eta,
            )
        }

        // R7/#2: route complete (route-remaining collapsed to ~0) → clear cụm thay vì heart-beat frame cũ.
        val routeRemainMeters = NavParse.parseEta(state.eta).first
        if (arrivalGuard.arrivedByRouteRemaining(routeRemainMeters.takeIf { it >= 0 })) {
            arrivalGuard.reset(); lastManeuverIcon = -1; lastNavLogKey = null
            runCatching { TurnDistanceInterpolator.reset() }
            runCatching { NavRepository.stop(applicationContext) }.onFailure { Log.e(TAG, "route-end stop failed", it) }
            ClusterNavLaneWidget.onNavIdle()
            Log.i(TAG, "route-remaining ~0 (${sbn.packageName}) → clear cụm (stop)")
            return
        }

        // R7/#2: chặn cự ly NHẢY LÙI vô lý (đang tới gần mà vọt lên, cùng maneuver, không reroute) — giữ
        // frame cũ trên cụm thay vì để frame lỗi trở thành giá trị heart-beat (owner: 500 m → 3.5 km).
        val distMeters = NavParse.parseMeters(state.distance)
        if (distMeters >= 0) {
            val maneuverKey = NavFormat.cleanRoadName(state.road) + "|" + state.maneuverText
            if (!arrivalGuard.acceptDistance(distMeters, maneuverKey)) {
                Log.i(TAG, "bỏ frame cự ly nhảy vô lý: ${distMeters}m road='${state.road}' (giữ frame cũ)")
                return
            }
        }

        NavRepository.ingest(applicationContext, sbn.packageName, null, state)
        // D4 (closeout 1.28): log-on-change — only Log.i when dist|road|eta changes from the previous emission
        // (kills per-notification spam; W/E + state-change logs above stay unconditional).
        val navKey = "${state.distance}|${state.road}|${state.eta}"
        if (navKey != lastNavLogKey) {
            lastNavLogKey = navKey
            Log.i(TAG, "nav dist='${state.distance}' road='${state.road}' eta='${state.eta}'")
        }
    }

    /**
     * ĐÓNG CỤM VỀ ĐỒNG HỒ vì [pkg] không còn được nuôi khung nữa. Chuỗi teardown y nguyên nhánh "đã đến nơi"
     * (R7/#2) — chỉ tách ra thành hàm để **cửa thứ hai** dùng chung, không đổi một bước nào.
     *
     * HAI CỬA gọi hàm này:
     *  1. "đã đến nơi" — app báo Arrived/đã đến.
     *  2. **B3.48** — cổng nguồn TỪ CHỐI đúng gói đang GIỮ cụm (user đổi sang `PREFER_X` mà gói này ngoài
     *     nhóm X). Caller phải tự hỏi bằng `SourceArbiter.release(pkg)` trước, giống `onNotificationRemoved`.
     *
     * VÌ SAO CỬA 2 BẮT BUỘC PHẢI CÓ (cơ chế tất định từ source, chưa đo trên xe — CLAUDE.md §2 mức "nghi là"
     * cho hệ quả, "đã chứng minh" cho cơ chế): cổng chỉ CẮT nguồn, nó không phát lệnh dừng. Không có lệnh
     * dừng thì hai nhịp tim vẫn phát lại **khung CUỐI** của gói vừa bị cấm:
     *   · `AmapEmissionArbiter.heartbeat` 400 ms tới `validUntilMs = observedAt + freshForMs`, mà
     *     `freshForMs = ClusterBroadcaster.STALE_MS` = **180 s**;
     *   · `NavigationHudOwner.keepAliveTick` 250 ms re-assert cùng khung đó lên HAL tới trần tuổi
     *     `HudKeepAlivePolicy.DEFAULT_MAX_AGE_MS` = **180 s**.
     * ⇒ cụm + HUD hiện MỘT MŨI TÊN RẼ VỚI CỰ LY ĐỨNG YÊN trong lúc xe đang chạy — đúng lỗi hiện trường mà
     * R7/#2 sinh ra để chữa ("GMaps đã báo tới nơi mà cụm kẹt 3.5 km đi thẳng"). Owner chốt 2026-08-23 là
     * *"không hiện gì"*; ghim khung chết là *"hiện SAI"*, không phải im lặng.
     *
     * ⚠ ĐÂY KHÔNG PHẢI fallback/timeout/degrade cho `PREFER_*` (thứ owner đã cấm bù): nó không cho gói nào
     * lên thay, không nới cổng, không hẹn giờ mới — nó THI HÀNH đúng chữ "im lặng".
     *
     * ⚠ CỬA 2 KHÔNG BAO GIỜ BẮN Ở AUTO, theo cấu tạo (cửa 1 thì có — "đã đến nơi" vốn chạy ở mọi chế độ, y
     * như trước): biểu thức AUTO của `SourceArbiter.allows` là
     * `h == null || h == pkg || stale`, nên `!allows(pkg)` ⇒ `h != pkg` ⇒ `release(pkg)` = false. Bất biến đó
     * được khoá bằng test `SourceArbiterAllowsTest.AUTO - cong tu choi thi goi bi tu choi KHONG BAO GIO la
     * nguon dang giu`.
     */
    private fun stopClusterOwnedBy(pkg: String, why: String) {
        arrivalGuard.reset(); lastManeuverIcon = -1; lastNavLogKey = null
        runCatching { TurnDistanceInterpolator.reset() }
        runCatching { NavRepository.stop(applicationContext) }
            .onFailure { Log.e(TAG, "$why: stop failed", it) }
        ClusterNavLaneWidget.onNavIdle()
        Log.i(TAG, "$why ($pkg) → clear cụm (stop)")
    }

    private fun loadIconBitmap(n: Notification): Bitmap? {
        // Chỉ largeIcon mới là mũi tên maneuver; smallIcon là logo Maps -> bỏ.
        val icon = n.getLargeIcon() ?: return null
        return runCatching {
            val d: Drawable? = icon.loadDrawable(applicationContext)
            d?.let { BitmapUtil.drawableToBitmap(it) }
        }.getOrNull()
    }
}
