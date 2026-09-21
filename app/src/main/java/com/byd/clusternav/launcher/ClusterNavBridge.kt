package com.byd.clusternav.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.byd.clusternav.NavConnect
import com.byd.clusternav.NavRepository
import com.byd.clusternav.NavigationSpeedSignOwner
import com.byd.clusternav.Prefs
import com.byd.clusternav.ThemeMode
import com.byd.clusternav.UpdateFlow
import com.byd.clusternav.VietMapAutostartService
import com.byd.clusternav.VmOverlayPosition
import com.byd.clusternav.comfort.Pm25FilterApplier
import com.byd.clusternav.comfort.SeatComfort
import com.byd.clusternav.comfort.SeatComfortApplier
import com.byd.clusternav.modules.clustercast.ClusterNavLaneWidget
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.navigation.NavSourceLabels
import com.byd.clusternav.navigation.NavigationOutputStatus
import com.byd.clusternav.navigation.NavigationOutputTarget
import com.byd.clusternav.navigation.SourceArbiter
import com.byd.clusternav.navigation.SpeedSignOutput
import com.byd.clusternav.speedbadge.BadgeLayout

/**
 * ═══ CẦU DUY NHẤT giữa Kachi Settings và cấu hình/hành động của ClusterNav ═══════════════════════
 *
 * Spec: `docs/specs/kachi-settings-ia-v2.html` §4.2 (kiến trúc), §4.3 (bản đồ khoá), N2 (UI không
 * rải `Prefs.set…`), N5 (đọc HAL trên thread nền).
 *
 * ## Vì sao LẶP LẠI thay vì gọi lại `MainActivity`
 * [ĐO] `activity_main.xml` + `res/values/strings.xml` nằm trong `T11_PATHS` của
 * `ExpansionTransportFenceTest` (hash byte-seal) và `MainActivity.kt` bị ~10 wiring-contract test đọc
 * source ⇒ **không được sửa một byte** để trích hàm ra dùng chung (spec §2 "Ràng buộc cứng", §9).
 * Nên mỗi hàm public ở đây **chép lại đúng chuỗi lời gọi** của màn cũ và KDoc ghi rõ
 * `lặp lại MainActivity.kt:<dòng>` để hai bên còn so được. Khi màn cũ bị gỡ (OQ1) thì cầu này thành
 * nguồn duy nhất.
 *
 * ## Hợp đồng
 *  - Nhận [app] = **applicationContext** (bridge sống lâu hơn Activity — không giữ View, không giữ
 *    Activity; xem test `ClusterNavBridgeWiringContractTest`).
 *  - [toast] = cách hiện thông báo ngắn của tầng gọi. Nó nhận **mã** [BridgeMsg], KHÔNG nhận câu —
 *    tầng `launcher/` bắt mọi chữ đi qua tài nguyên (`LauncherI18nContractTest`), còn nhánh ClusterNav
 *    đặt nhãn lúc chạy vì `strings.xml` đang byte-seal. Cầu đứng giữa nên không mang chữ của bên nào;
 *    KDoc từng giá trị [BridgeMsg] chép nguyên văn VI/EN của màn cũ để tầng Settings dịch lại y hệt.
 *  - [ui] = post một [Runnable] về luồng vẽ.
 *  - [activityProvider] chỉ dùng cho ĐÚNG một đường bắt buộc có Activity ([checkUpdate] →
 *    [UpdateFlow.start], mở dialog + cài APK). Mọi hàm khác chạy được không cần Activity.
 *  - Mọi hàm "trạng thái" trả **mã / dữ liệu thô** ([NavSourceView], [VoiceKeyStatus],
 *    `NavigationOutputStatus`, `SimpleCastState`, tên gói, mã phím) — không trả câu.
 *
 *
 * ⚠ **2026-09-13 — màn cũ đã GỠ HẲN** (`docs/specs/kachi-remove-legacy-screen.html` R1/R3). Mọi chỉ dẫn
 * `MainActivity.kt:<dòng>` dưới đây là **vết lịch sử**, không phải một tệp còn đọc được: chúng trỏ vào bản trước
 * commit gỡ màn (tra bằng `git log -- app/src/main/java/com/byd/clusternav/MainActivity.kt`). Giữ số dòng vì đó là
 * cách duy nhất còn lại để so hành vi của cầu với bản gốc; cầu nay là **nguồn duy nhất** của những hành vi đó.
 * Phần Cast và phần Phím nằm ở `ClusterNavBridgeCast.kt` / `ClusterNavBridgeKeys.kt` dưới dạng hàm
 * mở rộng của CHÍNH lớp này (giữ bề mặt phẳng `bridge.castFull(...)`, mà mỗi tệp vẫn dưới trần LOC).
 */
class ClusterNavBridge(
    app: Context,
    internal val toast: (BridgeMsg) -> Unit,
    internal val ui: (Runnable) -> Unit,
    private val activityProvider: () -> Activity? = { null },
) {
    /** LUÔN là applicationContext — chặn ngay tại cửa việc lỡ truyền Activity vào một vật sống lâu. */
    internal val app: Context = app.applicationContext

    /**
     * Chủ biển báo tốc độ — process-singleton (`NavigationSpeedSignOwner.get`), KHÔNG phải thành viên
     * của Activity, nên gọi được từ ngoài màn cũ y hệt (lặp lại `MainActivity.kt:53`).
     */
    internal val speedSign: NavigationSpeedSignOwner get() = NavigationSpeedSignOwner.get(app)

    // ── Dẫn đường (Nav + HUD) ────────────────────────────────────────────────────────────────────

    /** Công tắc chính "Dẫn đường + HUD" — lặp lại `MainActivity.kt:96`. */
    fun navEnabled(): Boolean = Prefs.enabled(app)

    /**
     * Bật/tắt "Dẫn đường + HUD" — **lặp lại `MainActivity.kt:97–128`** ĐÚNG THỨ TỰ:
     * `Prefs.setEnabled` → `speedSign.onMasterEnabled` → (bật) `Prefs.setLane(true)` →
     * `NavRepository.setOutputEnabled(CLUSTER_LANE, true)` → `speedSign.onOutputEnabled(CLUSTER, true)`
     * → đã có quyền thông báo thì `NavConnect.ensureConnected`, chưa có thì toast + `NavConnect.selfGrant`
     * → cuối cùng `NavConnect.grantAccessibility` khi thiếu setting **HOẶC** service chưa bound;
     * (tắt) `NavRepository.stop`.
     *
     * [onDone] báo kết quả đường cấp quyền (đồng bộ ⇒ `true` ngay; selfGrant ⇒ theo callback), luôn
     * được gọi trên luồng vẽ qua [ui]. Thất bại ⇒ toast câu hướng dẫn bật tay (lặp lại
     * `promptNotificationAccessFallback`, `MainActivity.kt:681–702` — bridge KHÔNG mở màn Settings vì
     * IVI khoá màn đó, đúng lý do màn cũ đã chọn đường dadb).
     */
    fun setNavEnabled(on: Boolean, onDone: (Boolean) -> Unit = {}) {
        Prefs.setEnabled(app, on)
        speedSign.onMasterEnabled(on)
        if (on) {
            Prefs.setLane(app, true)
            NavRepository.setOutputEnabled(app, NavigationOutputTarget.CLUSTER_LANE, true)
            speedSign.onOutputEnabled(SpeedSignOutput.CLUSTER, true)
            if (notificationAccessGranted()) {
                NavConnect.ensureConnected(app)
                ui(Runnable { onDone(true) })
            } else {
                toast(BridgeMsg.GRANTING_NOTIFICATION)
                NavConnect.selfGrant(app) { ok ->
                    ui(
                        Runnable {
                            toast(if (ok) BridgeMsg.NOTIFICATION_GRANTED else BridgeMsg.NOTIFICATION_FALLBACK)
                            onDone(ok)
                        },
                    )
                }
            }
            if (!accessibilityBoosterGranted() || !accessibilityBound()) NavConnect.grantAccessibility(app)
        } else {
            NavRepository.stop(app)
            ui(Runnable { onDone(true) })
        }
    }

    /** Chế độ nav trên CỤM: [Prefs.NAV_SCREEN_FULL] (Giữa + ETA) hay [Prefs.NAV_SCREEN_OFF] — `MainActivity.kt:186`. */
    fun clusterMode(): Int = Prefs.navClusterScreenMode(app)

    /**
     * Đặt chế độ nav trên cụm — lặp lại `MainActivity.kt:190–194`: persist rồi **áp NGAY**
     * ([NavRepository.reapplyClusterMode]) thay vì chờ frame kế bị dedup nuốt.
     */
    fun setClusterMode(mode: Int) {
        Prefs.setNavClusterScreenMode(app, mode)
        NavRepository.reapplyClusterMode(app)
    }

    /** "Chạy chữ tên đường dài" — lặp lại `MainActivity.kt:200–203`. */
    fun marquee(): Boolean = Prefs.marquee(app)

    /** Lặp lại `MainActivity.kt:203`. */
    fun setMarquee(on: Boolean) = Prefs.setMarquee(app, on)

    /** App dẫn đường MẶC ĐỊNH khi câu KHÔNG nêu tên app (owner 2026-09-18) — key của [VoiceAppTargets]. */
    fun navDefaultApp(): String = Prefs.voiceNavDefaultApp(app)
    fun setNavDefaultApp(key: String) = Prefs.setVoiceNavDefaultApp(app, key)

    /** Các app dẫn đường chọn được (key) — nguồn sự thật [VoiceAppTargets.NAV], không chép tay. */
    fun navAppChoices(): List<String> =
        com.byd.clusternav.launcher.voice.VoiceAppTargets.NAV.map { it.key }

    // App NHẠC mặc định (owner 2026-09-21) — "" = tự chọn (app đang phát / app đầu tiên).
    fun musicDefaultApp(): String = Prefs.voiceMusicDefaultApp(app)
    fun setMusicDefaultApp(key: String) = Prefs.setVoiceMusicDefaultApp(app, key)

    /** Các app nhạc chọn được (key) — nguồn [VoiceAppTargets.MUSIC], kèm "" đứng đầu = tự chọn. */
    fun musicAppChoices(): List<String> =
        listOf("") + com.byd.clusternav.launcher.voice.VoiceAppTargets.MUSIC.map { it.key }

    /**
     * "Kết nối lại nguồn dẫn đường" — lặp lại `MainActivity.kt:255–276`: có quyền ⇒
     * [NavConnect.reconnect] + toast; chưa có ⇒ toast + [NavConnect.selfGrant] (KHÔNG mở màn Settings
     * hệ thống: IVI khoá màn đó, xem KDoc gốc ở màn cũ).
     */
    fun reconnect(onDone: (Boolean) -> Unit = {}) {
        if (notificationAccessGranted()) {
            NavConnect.reconnect(app)
            toast(BridgeMsg.RECONNECTING)
            ui(Runnable { onDone(true) })
        } else {
            toast(BridgeMsg.GRANTING_NOTIFICATION)
            NavConnect.selfGrant(app) { ok ->
                ui(
                    Runnable {
                        toast(if (ok) BridgeMsg.NOTIFICATION_GRANTED else BridgeMsg.NOTIFICATION_FALLBACK)
                        onDone(ok)
                    },
                )
            }
        }
    }

    /** Nút "Dừng dẫn đường" — lặp lại `MainActivity.kt:277–280`. */
    fun navStop() = NavRepository.stop(app)

    /**
     * Nguồn đang dẫn — dữ liệu THÔ của dòng "Đang dẫn: …" (`MainActivity.kt:429–439`).
     *
     * [ĐO] Cả ba mảnh đều là **object process-singleton ở `:core`** ([SourceArbiter.activeSource],
     * [SourceArbiter.isFresh], [NavSourceLabels]) ⇒ đọc được nguyên vẹn từ ngoài Activity, không phải
     * trả "—". `null` = chưa có nguồn nào đang dẫn (màn cũ hiện "Đang dẫn: —").
     *
     * Trả [NavSourceView] chứ không trả câu: tên thương hiệu là danh từ riêng (không dịch), còn kênh
     * đọc + cờ "cũ" là enum/boolean — tầng Settings ghép câu bằng tài nguyên của launcher.
     */
    fun navSource(): NavSourceView? {
        val pkg = SourceArbiter.activeSource ?: return null
        return NavSourceView(
            packageName = pkg,
            brand = NavSourceLabels.sourceLabel(pkg),
            channel = NavSourceLabels.readChannel(pkg),
            stale = !SourceArbiter.isFresh(System.currentTimeMillis()),
        )
    }

    /**
     * Trạng thái đầu ra CỤM (dòng `laneStatus`, lặp lại `MainActivity.kt:421`) — trả **enum `:core`**,
     * tầng Settings tra câu trong tài nguyên (bảng nhãn gốc ở `MainActivity.kt:560–571`).
     *
     * KHÔNG gọi `NavRepository.setPermission` như `refresh()` làm ở dòng 397: đó là tác dụng phụ của
     * vòng lặp 1 s ở màn cũ, không thuộc việc "đọc một trạng thái".
     */
    fun navOutputStatus(): NavigationOutputStatus? =
        runCatching { NavRepository.snapshot(app).clusterLane.status }.getOrNull()

    /**
     * Kết quả op-39 ("Giữa + ETA" trên cụm) mà widget vừa công bố — trả **enum thô**, tầng Settings tra
     * câu trong tài nguyên.
     *
     * Chuyển từ `NavClusterOp39Status` (đã gỡ 2026-09-13 cùng màn cũ): lớp đó chỉ làm đúng hai việc —
     * `findViewById(R.id.txt_cluster_op39_status)` rồi đọc [ClusterNavLaneWidget.status]. Việc thứ nhất
     * là hình (nay là một `statusRow` ở nhóm *Dẫn đường*), việc thứ hai là đây.
     *
     * CHỈ ĐỌC: nó phản chiếu kết quả của lớp khác, không tự gửi lệnh nào — đúng như KDoc bản gốc ghi
     * ("never touches Cast state and issues no shell command").
     */
    fun clusterOp39(): ClusterNavLaneWidget.Op39Status =
        runCatching { ClusterNavLaneWidget.status }.getOrDefault(ClusterNavLaneWidget.Op39Status.IDLE)

    /** Lặp lại `MainActivity.kt:781–785` (đọc thẳng secure setting — mọi app đọc được, không cần dadb). */
    fun notificationAccessGranted(): Boolean = hasSecureComponent(
        "enabled_notification_listeners",
        ComponentName(app, com.byd.clusternav.NavNotificationListener::class.java),
    )

    /** Lặp lại `MainActivity.kt:792–796`. */
    fun accessibilityBoosterGranted(): Boolean = hasSecureComponent(
        "enabled_accessibility_services",
        ComponentName(app, com.byd.clusternav.modules.navaccess.NavAccessibilityService::class.java),
    )

    /** Service Hỗ trợ đã **BOUND** chưa (ground-truth `onServiceConnected`) — `MainActivity.kt:126`. */
    fun accessibilityBound(): Boolean =
        com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected

    private fun hasSecureComponent(setting: String, expected: ComponentName): Boolean {
        val flat = Settings.Secure.getString(app.contentResolver, setting) ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it.trim()) == expected }
    }

    // ── Biển báo tốc độ (badge trên cụm) — lặp lại BadgePlacementController.kt:44–112 ─────────────

    /** `BadgePlacementController.kt:45`. */
    fun badgeEnabled(): Boolean = Prefs.badgeEnabled(app)

    /**
     * Lặp lại `BadgePlacementController.kt:47–56` — kể cả TÁC DỤNG PHỤ khi BẬT: auto-start VietMap một
     * lần ([VietMapAutostartService.startForAppOpen], dedup `pidof` bên trong) để widget có nguồn tốc độ
     * ngay; tắt thì KHÔNG đụng gì. Sau cùng đánh thức lại lớp phủ dùng chung.
     */
    fun setBadgeEnabled(on: Boolean) {
        Prefs.setBadgeEnabled(app, on)
        if (on) VietMapAutostartService.startForAppOpen(app)
        speedSign.onBadgeEnabledChanged()
    }

    /** "Hiện giới hạn sắp tới" — `BadgePlacementController.kt:62`. */
    fun upcomingBadge(): Boolean = Prefs.showUpcomingBadge(app)

    /** Lặp lại `BadgePlacementController.kt:64–67`. */
    fun setUpcomingBadge(on: Boolean) {
        Prefs.setShowUpcomingBadge(app, on)
        speedSign.onUpcomingBadgeEnabledChanged()
    }

    /** "Hiện cảnh báo/camera VietMap" — `BadgePlacementController.kt:74`. */
    fun alertChip(): Boolean = Prefs.showAlertChip(app)

    /** Lặp lại `BadgePlacementController.kt:76–79`. */
    fun setAlertChip(on: Boolean) {
        Prefs.setShowAlertChip(app, on)
        speedSign.onAlertChipEnabledChanged()
    }

    /** Cỡ biển báo (dp) — `BadgePlacementController.kt:105`. */
    fun badgeSizeDp(): Int = Prefs.badgeSizeDp(app)

    /**
     * Lặp lại `BadgePlacementController.kt:106–110` (thanh trượt): kẹp về dải hợp lệ bằng
     * [BadgeLayout.clampSizeDp] rồi persist + [NavigationSpeedSignOwner.debugRefreshBadgeLayout] để lớp
     * phủ DÙNG CHUNG đổi cỡ ngay trên cụm.
     */
    fun setBadgeSizeDp(sizeDp: Int) {
        Prefs.setBadgeSizeDp(app, BadgeLayout.clampSizeDp(sizeDp))
        speedSign.debugRefreshBadgeLayout()
    }

    /** Tâm biển báo theo toạ độ CỤM — `BadgePlacementController.kt:88`. */
    fun badgeCenter(): Pair<Int, Int> = Prefs.badgeCenterX(app) to Prefs.badgeCenterY(app)

    /**
     * Lặp lại `BadgePlacementController.kt:81–87` (thả kéo-thả): kẹp lại trên CỤM THẬT bằng
     * [BadgeLayout.clampCenter] (cỡ badge px + kích cụm đang chiếu) rồi persist x/y + áp live.
     */
    fun setBadgeCenter(cx: Int, cy: Int) {
        val (w, h) = clusterSize()
        val (ccx, ccy) = BadgeLayout.clampCenter(cx, cy, badgeSizePx(), w, h)
        Prefs.setBadgeCenterX(app, ccx)
        Prefs.setBadgeCenterY(app, ccy)
        speedSign.debugRefreshBadgeLayout()
    }

    /**
     * Cỡ badge quy ra px trên cụm — `BadgePlacementController.kt:138`.
     *
     * Đổi đơn vị đi qua [KachiTheme.dpi] (hàm đổi dp DUY NHẤT của launcher) thay vì tự nhân với
     * `displayMetrics.density`: `SpacingScaleContractTest.khong duoc khai ham doi dp nao ngoai danh sach`
     * cấm mở thêm một đường đổi đơn vị mang tên lạ. Phép tính **y hệt** bản gốc — `KachiTheme.dpi` cũng
     * là `(v * density).toInt()` — nên không đổi một pixel nào của badge.
     */
    fun badgeSizePx(): Int = KachiTheme.dpi(app, Prefs.badgeSizeDp(app)).coerceAtLeast(1)

    /** W/H cụm từ cấu hình chiếu đang chạy; fallback 1920×720 (Seal) — `BadgePlacementController.kt:141–152`. */
    fun clusterSize(): Pair<Int, Int> {
        val wmSize = when (val state = SimpleCastRuntime.coordinator(app).state) {
            is SimpleCastState.CastingFull -> state.displayConfig.wmSize
            is SimpleCastState.CastingSplit -> state.left?.displayConfig?.wmSize ?: state.right?.displayConfig?.wmSize
            else -> null
        }
        val parts = wmSize?.split("x")
        return (parts?.getOrNull(0)?.toIntOrNull() ?: 1920) to (parts?.getOrNull(1)?.toIntOrNull() ?: 720)
    }

    // ── Bong bóng VietMap trên cụm — lặp lại MainActivity.kt:1086–1116 ───────────────────────────

    /** `MainActivity.kt:1090`. */
    fun vmBubbleEnabled(): Boolean = Prefs.vmBubbleEnabled(app)

    /**
     * Lặp lại `MainActivity.kt:1091–1095`: persist rồi — khi BẬT — auto-start VietMap một lần
     * (giống hành vi badge tốc độ). Tắt không đụng gì.
     */
    fun setVmBubbleEnabled(on: Boolean) {
        Prefs.setVmBubbleEnabled(app, on)
        if (on) VietMapAutostartService.startForAppOpen(app)
    }

    /** Góc-trên-trái tuyệt đối của bong bóng trên cụm — `MainActivity.kt:1107`. */
    fun vmBubblePos(): Pair<Int, Int> = VmOverlayPosition.absLeftX(app) to VmOverlayPosition.absTopY(app)

    /**
     * Lặp lại `MainActivity.kt:1102–1105`: [VmOverlayPosition.setAbsoluteTopLeft] tự kẹp trong khung,
     * ghi prefs **và** bắn broadcast `VM_BUBBLE_POS` cho mod VietMap (gate `castOn` nằm trong đó).
     */
    fun setVmBubblePos(absX: Int, absY: Int) = VmOverlayPosition.setAbsoluteTopLeft(app, absX, absY)

    /** Cụm đã "live" chưa (điều kiện chỉnh vị trí bong bóng) — `MainActivity.kt:1131`. */
    fun vmBubbleAdjustable(): Boolean = Prefs.vmBubbleEnabled(app) && VmOverlayPosition.castOn(app)

    /**
     * Khung (W×H) và cỡ bong bóng (W×H) mà bộ kéo-thả PHẢI dùng — lặp lại `MainActivity.kt:1096–1100`.
     *
     * ## ⚠⚠ [SOÁT SENIOR 2026-09-13] KHÔNG được dùng [clusterSize] cho bong bóng
     * Hai thứ nghe giống nhau nhưng là hai khung KHÁC nhau:
     *  • [clusterSize] = kích cụm **đang chiếu thật** (đọc `wmSize` của phiên cast, fallback 1920×720) — đúng cho
     *    biển báo, vì `BadgeLayout.clampCenter` cũng nhận đúng khung đó;
     *  • bong bóng thì [ĐO] `VmOverlayPosition.set` kẹp vào `X_MAX`/`Y_MAX` dựng từ hằng **CỐ ĐỊNH** 1920×720 và
     *    371×158 — không đọc kích cụm thật lần nào.
     *
     * Cho bộ kéo-thả một khung mà đường ghi lại kẹp bằng khung khác = ngón tay thả một chỗ, bong bóng nhảy về chỗ
     * khác ngay khi [setVmBubblePos] kẹp lại (rồi `syncBubble` vẽ lại theo giá trị đã kẹp). Đó là lý do màn cũ
     * truyền **thẳng bốn hằng** của `VmOverlayPosition` vào `VmBubblePlacementView`, và cầu phơi lại đúng bốn số
     * đó — section không được `import VmOverlayPosition` (N2).
     */
    fun vmBubbleFrame(): Pair<Int, Int> = VmOverlayPosition.CLUSTER_WIDTH to VmOverlayPosition.CLUSTER_HEIGHT

    /** Xem [vmBubbleFrame] — cỡ bong bóng mà khung kẹp đang giả định. */
    fun vmBubbleSize(): Pair<Int, Int> = VmOverlayPosition.BUBBLE_WIDTH to VmOverlayPosition.BUBBLE_HEIGHT

    // ── Tiện nghi xe: ghế + PM2.5 — lặp lại MainActivity.kt:1215–1325 ────────────────────────────

    /** `MainActivity.kt:1221`. */
    fun seatEnabled(): Boolean = Prefs.seatComfortEnabled(app)

    /** Lặp lại `MainActivity.kt:1222–1225` (chỉ persist; áp HAL đi theo từng ghế/chế độ). */
    fun setSeatEnabled(on: Boolean) = Prefs.setSeatComfortEnabled(app, on)

    /** 0 = làm mát ([SeatComfort.SeatMode.COOL]), 1 = sưởi — `MainActivity.kt:1248`. */
    fun seatMode(): Int = Prefs.seatComfortMode(app)

    /**
     * Lặp lại `MainActivity.kt:1252–1256`: persist chế độ rồi **áp HAL ngay**
     * ([SeatComfortApplier.applyNow] tự no-op khi công tắc chính tắt — nút "Áp dụng ngay" đã bỏ).
     */
    fun setSeatMode(mode: Int) {
        Prefs.setSeatComfortMode(app, mode)
        SeatComfortApplier.applyNow(app)
    }

    /** Mức của một ghế (0 = tắt, 1, 2) — `MainActivity.kt:1238`. */
    fun seatLevel(seatIndex: Int): Int = Prefs.seatComfortLevel(app, seatIndex)

    /**
     * Lặp lại `MainActivity.kt:1240–1244`: persist rồi ghi HAL cho **chính ghế đó**
     * ([SeatComfortApplier.applySeat] — KHÔNG dùng đường bulk `applyNow`, vì đường bulk bỏ qua mức
     * "Tắt" ⇒ trước v1.34 không tắt được ghế).
     */
    fun setSeatLevel(seatIndex: Int, level: Int) {
        Prefs.setSeatComfortLevel(app, seatIndex, level)
        SeatComfortApplier.applySeat(app, seatIndex, level)
    }

    /**
     * Số ghế theo mẫu xe (2 = Seal / 4 = Han) — `MainActivity.kt:1237` gọi
     * [SeatComfortApplier.detectSeatCount] **đồng bộ trên luồng vẽ**. Bridge chạy nó trên thread NỀN
     * rồi post kết quả về (spec N5): hàm đó dò `BydHal` bằng reflection + `SystemProperties`, off-car
     * trả 2. Đây là khác biệt CÓ CHỦ Ý duy nhất so với màn cũ.
     */
    fun seatCount(onCount: (Int) -> Unit) {
        Thread({
            val n = runCatching { SeatComfortApplier.detectSeatCount(app) }.getOrDefault(2)
            ui(Runnable { onCount(n) })
        }, "bridge-seat-count").start()
    }

    /** `MainActivity.kt:1290`. */
    fun pm25Enabled(): Boolean = Prefs.pm25FilterEnabled(app)

    /** Lặp lại `MainActivity.kt:1291–1295`: persist + bật/tắt lọc-liên-tục (không popup). */
    fun setPm25Enabled(on: Boolean) {
        Prefs.setPm25FilterEnabled(app, on)
        if (on) Pm25FilterApplier.enable(app) else Pm25FilterApplier.disable(app)
    }

    /**
     * Nút "Lọc ngay" — lặp lại `MainActivity.kt:1303–1307`: quick-clean chủ động **bất kể** công tắc
     * auto, kèm toast song ngữ.
     */
    fun pm25CleanNow() {
        Pm25FilterApplier.cleanNow(app)
        toast(BridgeMsg.CLEANING_AIR)
    }

    /**
     * Mức bụi hiện tại — lặp lại `MainActivity.kt:1310–1322`: đọc HAL trên thread NỀN rồi post **mức
     * thô** về luồng vẽ. Nhãn ("Tốt"/"Good"…) do tầng Settings tra tài nguyên theo mức; bảng nhãn
     * thuần đã có sẵn ở `:core` (`Pm25Filter.levelLabelVi/En`) nếu cần đối chiếu. Mức không rõ /
     * off-car ⇒ giá trị INVALID của `Pm25Filter`.
     */
    fun pm25Level(onLevel: (level: Int) -> Unit) {
        Thread({
            val level = Pm25FilterApplier.readLevel(app)
            ui(Runnable { onLevel(level) })
        }, "bridge-pm25-read-level").start()
    }

    /** Lấy gió trong khi nổ máy (khoá đã có sẵn của Kachi, đặt cạnh ghế/PM2.5 cho đủ nhóm "Tiện nghi xe"). */
    fun recircOnStart(): Boolean = Prefs.recircOnStartEnabled(app)

    /** Xem [recircOnStart]. */
    fun setRecircOnStart(on: Boolean) = Prefs.setRecircOnStartEnabled(app, on)

    // ── Hệ thống ─────────────────────────────────────────────────────────────────────────────────

    /** "Tự khởi động nền" — lặp lại `MainActivity.kt:218`. */
    fun headlessAutostart(): Boolean = Prefs.headlessAutostart(app)

    /** Lặp lại `MainActivity.kt:219`. */
    fun setHeadlessAutostart(on: Boolean) = Prefs.setHeadlessAutostart(app, on)

    /**
     * "Kiểm tra cập nhật" — lặp lại `MainActivity.kt:284–287`.
     *
     * ⚠ **KHÔNG lặp được từ ngoài Activity**: [UpdateFlow.start] nhận `Activity` (nó dựng
     * `AlertDialog` xác nhận rồi gọi `startActivity` cài APK) và [ĐO] không có đường tĩnh nào bên dưới
     * để gọi thay. Bridge vì thế nhận `activityProvider` ở constructor; không có Activity ⇒ báo thẳng
     * cho người dùng thay vì im lặng.
     */
    fun checkUpdate(onText: (String) -> Unit) {
        val activity = activityProvider()
        if (activity == null) {
            toast(BridgeMsg.UPDATE_NEEDS_SCREEN)
            return
        }
        UpdateFlow.start(activity) { text, _ -> ui(Runnable { onText(text) }) }
    }

    // ⚠ `openLegacyScreen()` đã XOÁ 2026-09-13 cùng màn ClusterNav cũ (S3 · R1). Mục catalog
    // `system_advanced_screen` và nút "Màn nâng cao" ở nhóm *Hệ thống* biến mất theo — mọi cấu hình của màn đó
    // nay ở nhóm *Dẫn đường* / *Chiếu cụm* / *Phím vô-lăng* / *Tiện nghi xe*, ghi cùng khoá qua chính cầu này.

    /** "Dữ liệu VietMap" — lặp lại `MainActivity.kt:281–283`. */
    fun openVietMapData() = launch(com.byd.clusternav.vietmapwidget.VietMapWidgetDiagActivity::class.java)

    /** "Chẩn đoán" — lặp lại `MainActivityCastController.kt:69`. */
    fun openDiagnostics() = launch(com.byd.clusternav.modules.clustercast.DiagActivity::class.java)

    private fun launch(target: Class<*>) {
        runCatching {
            app.startActivity(Intent(app, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { toast(BridgeMsg.SCREEN_OPEN_FAILED) }
    }

    // ── Lấy gió trong: đường "áp ngay" (HomePanels cũ: bật ⇒ áp NGAY, không chờ lần nổ máy sau) ─────────
    /** Áp chế độ lấy gió trong NGAY (bất đồng bộ) — lặp lại `HomePanels.onRecircOnStart` trước IA v2. */
    fun applyRecircNow() = com.byd.clusternav.comfort.RecircApplier.applyNowAsync(app)

}
