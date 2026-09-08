package com.byd.clusternav

import com.byd.clusternav.modules.clustercast.MainActivityCastController
import com.byd.clusternav.comfort.SeatComfort
import com.byd.clusternav.comfort.SeatComfortApplier
import com.byd.clusternav.comfort.Pm25Filter
import com.byd.clusternav.comfort.Pm25FilterApplier
import com.byd.clusternav.vietmapwidget.VietMapWidgetDiagActivity
import com.byd.clusternav.navigation.NavigationOutputFailureReason
import com.byd.clusternav.navigation.NavigationSourceReason
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.navigation.NavigationFreshness
import com.byd.clusternav.navigation.NavigationOutputStatus
import com.byd.clusternav.navigation.NavigationOutputTarget
import com.byd.clusternav.navigation.NavigationPermission
import com.byd.clusternav.navigation.NavReadChannel
import com.byd.clusternav.navigation.NavSourceLabels
import com.byd.clusternav.navigation.SpeedSignOutput
import com.byd.clusternav.navigation.HeroArrow
import com.byd.clusternav.navigation.Maneuver
import com.byd.clusternav.navigation.toHeroArrow

/**
 * Home — MÀN HÌNH DUY NHẤT của app (docs/specs/cast-simplified-active-app-toggle.html): trái là
 * Navigation + HUD (không đổi), phải là toàn bộ Cluster Cast (trước đây là màn riêng
 * `ClusterCastActivity`, đã xoá). Renderer/dispatcher — nó không tự lập kế hoạch gì cho Cast, mọi
 * mutation đi qua [MainActivityCastController].
 */
class MainActivity : Activity() {
    private lateinit var navEnabled: Switch
    private lateinit var navDot: View
    private lateinit var navStatus: TextView
    private lateinit var laneStatus: TextView
    private lateinit var hudStatus: TextView
    // T3 (b3-full-nav-capture · R2): shows SourceArbiter.activeSource (the nav source currently driving).
    private lateinit var navSourceActive: TextView
    private val cast = MainActivityCastController(this)
    private val navClusterStatus = com.byd.clusternav.modules.clustercast.NavClusterOp39Status(this)
    // Speed-sign owner: nhận giới hạn tốc độ từ widget VietMap → badge cụm + HAL 0x4B40001C.
    // (Comment cũ ghi "Port 1.21 = Noop" đã LỖI THỜI — đường này chạy thật, chính nó vẽ badge trên cụm.)
    private val speedSign by lazy { NavigationSpeedSignOwner.get(applicationContext) }

    private val ui = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() { refresh(); cast.tick(); ui.postDelayed(this, 1_000) }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeMode.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ngôn ngữ (i18n): nạp lựa chọn ĐÃ giải nghĩa vào cache TRƯỚC khi dựng UI, rồi dịch MỘT lần các nhãn
        // TĨNH hardcode trong layout (id-free tree-walk) khi đang ở English. localizeTree chạy TRƯỚC các setter
        // Lang.t động bên dưới, nên view nào có setter riêng sẽ được chính setter đó ghi đè (không xung đột);
        // recreate() lúc đổi ngôn ngữ dựng lại layout (VI) rồi localize lại theo cache mới. VI mode = no-op.
        Lang.load(this)
        BilingualLabels.localizeTree(findViewById<View>(android.R.id.content))

        // D1 (closeout 1.28): mirror the persisted verbose-log flag into the in-memory NavLog gate so per-frame
        // hot paths read a @Volatile field (no SharedPreferences per frame). Entry point that always runs.
        NavLog.init(this)

        // CLAUDE.md §9: mỗi bản đã báo cho user phải tự hiện số hiệu — không ai phải đoán xe đang chạy bản nào.
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        val titleView = findViewById<TextView>(R.id.txt_app_title)
        titleView.text = "ClusterNav" + (versionName?.let { " · v$it" } ?: "")

        navEnabled = findViewById(R.id.switch_enabled)
        navDot = findViewById(R.id.dot_status)
        navStatus = findViewById(R.id.txt_status)
        laneStatus = findViewById(R.id.txt_lane_status)
        hudStatus = findViewById(R.id.txt_hud_status)
        // Cluster-lane output follows the master Navigation+HUD switch — the redundant cb_lane
        // checkbox is removed (owner 2026-08-11). Force lane ON once so it always tracks the master
        // (migrates anyone who had unchecked the old lane box).
        Prefs.setLane(this, true)
        cast.onCreate()
        speedSign.syncFromPrefs()

        navEnabled.isChecked = Prefs.enabled(this)
        navEnabled.setOnCheckedChangeListener { _, enabled ->
            Prefs.setEnabled(this, enabled)
            speedSign.onMasterEnabled(enabled)
            if (enabled) {
                // Lane always on when Navigation+HUD is on (no separate lane toggle anymore).
                Prefs.setLane(this, true)
                NavRepository.setOutputEnabled(this, NavigationOutputTarget.CLUSTER_LANE, true)
                speedSign.onOutputEnabled(SpeedSignOutput.CLUSTER, true)
                // Option B (1.13): user chủ động bật → GIỜ mới đụng adb. Thiếu quyền → tự cấp qua dadb;
                // đã có → chỉ ensure bind. (Mặc định TẮT nên onCreate không tự chạy nhánh này.)
                if (notificationAccessGranted()) {
                    NavConnect.ensureConnected(applicationContext)
                } else {
                    Toast.makeText(this, Lang.t("Đang cấp quyền đọc thông báo…", "Granting notification access…"), Toast.LENGTH_SHORT).show()
                    NavConnect.selfGrant(applicationContext) { ok ->
                        if (isFinishing) return@selfGrant
                        if (ok) refresh() else promptNotificationAccessFallback()
                    }
                }
                // Bộ đọc màn GMaps (screenRead ground-truth cho tinh chỉnh nội suy) + nút vật lý → trợ lý
                // cần accessibility service. Đây là quyền ADB (settings secure enabled_accessibility_services)
                // → tự cấp qua dadb như notification. Escalate khi THIẾU setting HOẶC enabled-nhưng-chưa-bound
                // (sau reboot: connected=false dù setting còn) — grantAccessibility verify dumpsys trước khi
                // toggle nên không flicker nếu đã bound. Trước đây chỉ UI voice-key cấp; voice-key gỡ đi →
                // grantAccessibility mồ côi → 2 chuyến screenRead RỖNG. Wire vào công tắc Nav+HUD để tự lành.
                if (!accessibilityBoosterGranted() || !com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected) NavConnect.grantAccessibility(applicationContext)
            } else {
                NavRepository.stop(this)
            }
            refresh()
        }
        // The master listener above only fires on CHANGE, so apply the current master state to the
        // cluster-lane output at startup too. The notification listener runs in THIS process and, on
        // bind, calls NavRepository.setPermission(GRANTED) → connect(), which may create the
        // coordinator before this Activity opens. A migrated user whose old cb_lane was unchecked has
        // lane=false persisted; the forced Prefs.setLane(true) above fixes the pref, but a coordinator
        // already built from the stale pref keeps CLUSTER_LANE OFF (connect() reads Prefs.lane only at
        // creation) and GMaps/VietMap nav would never reach the cluster. Re-assert it here (idempotent).
        if (Prefs.enabled(this)) {
            NavRepository.setOutputEnabled(this, NavigationOutputTarget.CLUSTER_LANE, true)
            speedSign.onOutputEnabled(SpeedSignOutput.CLUSTER, true)
        }
        // Chốt nguồn biển báo MỘT lần lúc khởi tạo. Trước 08-22 lời gọi này nằm trong listener của spinner
        // chọn nguồn; spinner đã gỡ (chỉ còn một nguồn thật) nên phải khẳng định tường minh ở đây, nếu không
        // owner chỉ được set nguồn qua syncFromPrefs/pusher và MainActivity không còn bảo đảm gì.
        speedSign.onSourceSelected(Prefs.speedLimitSource(this))
        // #6 (R1 · docs/specs/cast-nav-ux-release-v104.html): the independent nav→HUD output is
        // hidden from the UI (cb_hud/txt_hud_status = gone) and force-disabled here exactly once.
        // There is no user-reachable path to re-enable it. Navigation still flows to the cluster
        // lane (unchanged); NavigationOutputTarget.HUD stays in the enum for the isolation contract —
        // it is only kept OFF, not removed.
        Prefs.setHud(this, false)
        NavRepository.setOutputEnabled(this, NavigationOutputTarget.HUD, false)
        speedSign.onOutputEnabled(SpeedSignOutput.HUD, false)

        // ★ 2026-08-12 (owner "1B"): BẬT lại "tự bù theo tốc độ" cho mượt. MỘT cơ chế 2 nửa:
        //   (1) nội suy trừ dần cự ly theo TỐC ĐỘ XE thật giữa 2 notification (TurnDistanceInterpolator + SpeedProvider),
        //   (2) bộ đọc màn Maps (accessibility) kéo mốc về số thật (refine()).
        // Noti GMaps thưa → gửi RAW làm cụm "trễ khi tới ngã rẽ/điểm đến"; nội suy lấp khoảng giữa cho mượt.
        // Ép BẬT ở đây để migrate cả bản cài cũ từng bị ép TẮT (2026-07-13). Không có nút UI (giữ UI gọn);
        // muốn TẮT nếu overlay cụm tự animate rồi đánh nhau (verify trên xe) → đổi 2 dòng dưới thành false.
        Prefs.setInterpolate(this, true)
        Prefs.setAccBooster(this, true)

        // Nguồn dẫn đường: BỎ selector chọn nguồn (closing 2026-08-28, spec ui-closing-cleanup). Sau khi gỡ
        // nav VietMap/Waze, chỉ còn Google Maps (notification) làm nguồn dẫn đường ⇒ không còn gì để chọn.
        // `Prefs.sourceMode` giữ mặc định AUTO — SourceArbiter ở AUTO chạy đúng với một nguồn GMaps duy nhất.
        // Enum NavSourceMode + SourceArbiter + NavSourceModeSwitch GIỮ NGUYÊN (hợp đồng nội bộ, còn test :core).
        // Dòng "Đang dẫn: …" (txt_nav_source_active) là chỉ báo TRẠNG THÁI (read-only, KHÔNG phải selector) —
        // giữ lại; refresh() cập nhật theo SourceArbiter.activeSource.
        navSourceActive = findViewById(R.id.txt_nav_source_active)

        // ── Nguồn tốc độ: BỎ selector (08-22) ─────────────────────────────────────────────────────
        // Chỉ còn MỘT nguồn có thật — widget VietMap (proven: data 50/60/70/80 + đếm lùi cự ly). Lựa chọn
        // "Waze Mod (HLP)" đã gỡ khỏi cả code lẫn UI: đo trên máy không có HUD BLE thì `logcat WazeHudLink`
        // trả 0 dòng khi Waze ĐANG dẫn, tức chọn nó = badge trắng im lặng, không báo gì cho người dùng.
        // Một selector chỉ có một lựa chọn thì không phải lựa chọn — bỏ hẳn cho khỏi hiểu nhầm.

        // Chế độ hiển thị nav trên CỤM — ghi SET_NAVI_SCREEN_STATUS_SET (0x4C10E015) qua NavigationHudOwner
        // (đọc pref mỗi frame → áp dụng LIVE khi đang dẫn). ⚠️ value↔menu OEM chưa map chắc: dò trên xe rồi chốt.
        // TASK 4 (R3 · docs/specs/clusternav-closeout-1.28.html): on-car only OFF ever changed anything — the 3
        // layout modes (Đơn giản/Toàn/Nhỏ) hit the no-root wall and all render the same centre. Reduce to ON/OFF
        // so there are no dead buttons. ON = NAV_SCREEN_SIMPLE (centre "Giữa + ETA"); OFF = NAV_SCREEN_OFF. The
        // FULL/SMALL constants stay in Prefs (BydHal.NAV_SCREEN_MODE_ON back-compat) but are no longer selectable.
        // Level-2 (ui-visual-upgrade-l2): SegmentedControlView (seg_cluster_mode) thay Spinner — hành vi ON/OFF
        // giữ nguyên (persist NavClusterScreenMode + reapplyClusterMode). onSelected chỉ nổ khi USER chạm.
        val clusterModes = arrayOf(Lang.t("Giữa + ETA", "Centre + ETA"), Lang.t("Tắt", "Off"))
        val clusterModeValues = intArrayOf(Prefs.NAV_SCREEN_FULL, Prefs.NAV_SCREEN_OFF)
        findViewById<com.byd.clusternav.ui.SegmentedControlView>(R.id.seg_cluster_mode).apply {
            setOptions(clusterModes.toList())
            // Migrate old prefs gracefully: any non-OFF stored value (incl. legacy FULL/SMALL) → index 0 (Bật);
            // OFF → index 1 (Tắt). Prefs.navClusterScreenMode already collapses FULL/SMALL→SIMPLE on read.
            selectedIndex = if (Prefs.navClusterScreenMode(this@MainActivity) == Prefs.NAV_SCREEN_OFF) 1 else 0
            onSelected = { pos ->
                Prefs.setNavClusterScreenMode(this@MainActivity, clusterModeValues[pos])
                // I4 (1.14): áp NGAY (re-assert) thay vì chờ reboot / frame kế bị dedup nuốt.
                NavRepository.reapplyClusterMode(applicationContext)
            }
        }

        // I2 (1.14): toggle marquee (chạy chữ tên đường dài). Mặc định BẬT (Prefs.marquee=true).
        findViewById<android.widget.Switch>(R.id.cb_marquee).also { cb ->
            cb.isChecked = Prefs.marquee(this)
            cb.setOnCheckedChangeListener { _, on -> Prefs.setMarquee(this, on) }
        }

        // 1.21 Item 1 (owner): "Tự khởi động nền" — nổ máy chỉ chạy setup nền (BootSetupService qua
        // RebindReceiver), KHÔNG bung MainActivity trên màn chính (né size-compat dudu). Mặc định BẬT; tắt →
        // giữ hành vi cũ (tự mở Home lúc nổ máy). Chỉ đổi hành vi lúc boot/OTA — mở app bằng icon vẫn như thường.
        findViewById<android.widget.Switch>(R.id.cb_headless_autostart).also { cb ->
            // Option B: dời sang card "Hệ thống"; nhãn đặt lúc chạy để nói rõ đây là cài đặt TOÀN CỤC (áp mọi
            // tính năng nền: phím-thoại, ghế, lọc bụi, dẫn đường), không riêng Nav+HUD.
            // Switch = toggle TRẦN (không nhãn) — hàng đã có TextView title "Tự khởi động nền" (dịch qua
            // BilingualLabels). Trước đây gán câu dài vào cb.text làm Switch wrap NHIỀU DÒNG → row cao vọt
            // (owner: "scale chiều cao quá lớn"). Ngữ cảnh "áp mọi tính năng nền" chuyển vào contentDescription.
            cb.text = ""
            cb.contentDescription = Lang.t(
                "Tự khởi động nền — áp mọi tính năng nền (phím-thoại, ghế, lọc bụi, dẫn đường)",
                "Background auto-start — applies all background features (voice key, seats, dust filter, nav)",
            )
            cb.isChecked = Prefs.headlessAutostart(this)
            cb.setOnCheckedChangeListener { _, on -> Prefs.setHeadlessAutostart(this, on) }
        }

        // ── Nút vật lý → Trợ lý giọng nói (switch + nút + cử chỉ + đích + học phím). Owner 2026-08-14:
        // map nút mic vô-lăng (NHẤN-GIỮ = keycode 328) → Kiki (ai.zalo.kiki.car). Chỉ "nuốt" đúng tổ hợp,
        // KHÔNG đổi chức năng gốc của nút. Service Hỗ trợ tự bật qua dadb khi bật công tắc. ──
        setupVoiceKeyControls()

        // Ghế: làm mát / sưởi tự động (spec seat-comfort-auto) — ĐẶT NGAY SAU khối voice-key ở cột trái.
        // Chọn chế độ toàn cục (làm mát ↔ sưởi) + mức từng ghế; hiện 2 hay 4 ghế theo mẫu xe. Mặc định TẮT.
        setupSeatComfortControls()

        // Tự lọc bụi mịn PM2.5 (spec pm25-auto-filter) — NGAY SAU mục ghế ở cột trái. Một công tắc + nhãn
        // mức bụi hiện tại. Bật → xe tự lọc liên tục không popup. Mặc định TẮT.
        setupPm25FilterControls()

        // Nav trên cụm chỉ còn op 39 "Giữa + ETA" (owner chốt 2026-08-12) — bỏ nút chọn mode + nút test.
        // Chỉ còn dòng trạng thái op39 (ASSERTED / Cast đang bật / chưa gửi được) để chẩn đoán.
        navClusterStatus.bind()

        // Item 4 (spec vietmap-overlay-position-ui): panel chỉnh VỊ TRÍ bong bóng VietMap-mod trên cụm
        // (kéo-thả trong khung; thả ra tự lưu + gửi). Gate: chỉ chỉnh được khi Cluster Cast ON.
        setupVmOverlayControls()

        // Option B (docs/specs/ui-redesign-options.html): mỗi tính năng là một card có header luôn hiện +
        // thân GẬP được. Nối các header/thân gập (đánh dấu bằng android:tag) — KHÔNG thêm @+id nào (bộ id
        // khoá 95). Card hay dùng mở sẵn, card cài-một-lần đóng sẵn.
        setupCollapsibleCards()

        // Ngôn ngữ / Language selector (nhóm Hệ thống) — seed từ Lang.choice() rồi cài onSelected (setChoice +
        // recreate). Đặt SAU setupCollapsibleCards để chắc chắn view seg_language đã có trong cây.
        setupLanguageSelector()

        // Giao diện / Theme selector (nhóm Hệ thống, ngay dưới Ngôn ngữ) — seed từ ThemeMode.choice rồi cài
        // onSelected (setChoice + recreate để cả Activity resolve lại values/ (LIGHT) hoặc values-night/ (DARK)).
        setupThemeSelector()
        findViewById<Button>(R.id.btn_reconnect_nav).setOnClickListener {
            if (notificationAccessGranted()) {
                NavConnect.reconnect(applicationContext)
                Toast.makeText(this, Lang.t("Đang kết nối lại nguồn dẫn đường…", "Reconnecting navigation source…"), Toast.LENGTH_SHORT).show()
            } else {
                // Quyền đọc thông báo là quyền ADB (settings secure enabled_notification_listeners) — KHÔNG cần
                // màn Settings (IVI khoá không mở được → toast hệ thống "không hỗ trợ hoạt động này"). Cấp THẲNG
                // qua dadb uid-shell (NavConnect.selfGrant, y như DashCast). Chỉ khi dadb lỗi mới hiện fallback.
                Toast.makeText(this, Lang.t("Đang cấp quyền đọc thông báo…", "Granting notification access…"), Toast.LENGTH_SHORT).show()
                NavConnect.selfGrant(applicationContext) { ok ->
                    if (isFinishing) return@selfGrant
                    if (ok) {
                        Toast.makeText(this, Lang.t("Đã cấp quyền — đã kết nối nguồn dẫn đường.", "Access granted — navigation connected."), Toast.LENGTH_SHORT).show()
                        refresh()
                    } else {
                        promptNotificationAccessFallback()
                    }
                }
            }
        }
        findViewById<Button>(R.id.btn_nav_stop).setOnClickListener {
            NavRepository.stop(applicationContext)
            refresh()
        }
        // ★ Revive: nút "Dữ liệu VietMap" mở VietMapWidgetDiagActivity (chẩn đoán widget speed-limit).
        findViewById<Button>(R.id.btn_vietmap_widget_diag).setOnClickListener {
            startActivity(Intent(this, VietMapWidgetDiagActivity::class.java))
        }
        findViewById<Button?>(R.id.btn_check_update)?.setOnClickListener {
            val btn = it as Button
            UpdateFlow.start(this) { text, _ -> btn.text = text }
        }

        // Option B (1.13): chỉ đụng adb khi Navigation+HUD đang BẬT. Mặc định TẮT → mở app KHÔNG chạy dadb
        // (tránh đua nhiều client dadb + popup Allow khi user chưa cần nav). Bật công tắc mới grant+connect.
        if (Prefs.enabled(this)) {
            NavConnect.ensureConnected(applicationContext)
        }
        // Accessibility service = HAI việc ĐỘC LẬP: (a) booster đọc cự-ly GMaps (cần Nav+HUD) + (b) nút vật lý →
        // trợ lý (cần voice-key). GRANT/force-rebind khi BẤT KỲ cái nào bật — phím-thoại KHÔNG phụ thuộc Nav+HUD
        // (owner 2026-09-01: hai tính năng riêng; trước gate chung vào Nav+HUD nên voice-key chết khi Nav+HUD tắt).
        // Reboot để service ENABLED-nhưng-CHƯA-bound → onKeyEvent chết; grantAccessibility verify dumpsys trước khi
        // toggle (không flicker nếu đã bound). Escalate khi thiếu setting HOẶC enabled-nhưng-chưa-bound.
        if (Prefs.enabled(this) || Prefs.voiceKeyEnabled(this)) {
            if (!accessibilityBoosterGranted() || !com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected) NavConnect.grantAccessibility(applicationContext)
        }
        runCatching { RebindReceiver.scheduleWatchdog(applicationContext) }
        // Nút nổi + chiếu cụm chỉ khởi động khi master switch "Cluster Cast" đang BẬT (MẶC ĐỊNH TẮT —
        // nav-only là mặc định; cụm giữ native + nav hiện ngay, không projection/cong/đen). Tắt Cast ⇒
        // không start service (service cũng tự đứng xuống nếu bị boot khởi động). startForegroundService idempotent.
        runCatching {
            if (com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
                    .coordinator(applicationContext).prefs.castEnabled()
            ) {
                startForegroundService(Intent(this, com.byd.clusternav.modules.clustercast.FloatingBubbleService::class.java))
            }
        }
        refresh()

        // D5 (closeout 1.28): one-time first-launch disclaimer (no warranty / not affiliated with BYD / install
        // at your own risk). Shown once, guarded by Prefs.disclaimerShown.
        maybeShowDisclaimer()

        // B1 (owner 2026-08-19): "badge bật → VietMap tự chạy để widget có nguồn" — with the speed badge enabled,
        // start VietMap once so its home-widget (the badge's speed-limit source) has a live process. Degrade-safe.
        // B2 (on-car 2026-09-06): CHỈ chạy khi onCreate là lần tạo THẬT (savedInstanceState==null) — recreate() lúc
        // đổi ngôn ngữ/giao diện luôn có savedInstanceState≠null ⇒ KHÔNG re-trigger autostart (chống flash loop).
        // Cooldown + in-flight trong VietMapAutostart là lớp chặn thứ hai; đây chặn ngay tại nguồn recreate.
        if (savedInstanceState == null) maybeAutoStartVietMap()

        // Ghế: nếu công tắc BẬT → áp mức làm-mát/sưởi lên HAL ~5 s sau khi mở app (degrade-safe, no-op off-car).
        SeatComfortApplier.applyOnStart(this)

        // Lọc bụi mịn PM2.5: nếu công tắc BẬT → bật lọc-liên-tục (không popup) ~5 s sau khi mở app (degrade-safe).
        Pm25FilterApplier.applyOnStart(this)
    }

    override fun onResume() {
        super.onResume()
        runCatching { RebindReceiver.rebind(applicationContext) }
        // Quay lại từ màn "Truy cập thông báo": vừa bật quyền nhưng listener chưa bind (firmware BYD
        // bỏ qua requestRebind) → ép bind qua dadb. Chỉ chạy khi ĐÃ có quyền mà CHƯA bound (rẻ, không
        // đụng nav đang chạy tốt).
        if (Prefs.enabled(this) && notificationAccessGranted() && !NavNotificationListener.connected) {
            NavConnect.ensureConnected(applicationContext)
        }
        // Bug 1 (owner 2026-09-01): phím-thoại chết sau lái xe/reboot (service ENABLED nhưng KHÔNG bound → onKeyEvent
        // không chạy; 305 lẫn 328 rơi về mặc định). onCreate chỉ grant khi Nav+HUD BẬT — Nav+HUD default TẮT nên mở
        // app không tự lành, restart app vô ích (đúng triệu chứng owner). Nay: hễ phím-thoại BẬT mà service chưa
        // connected → grant + force-rebind qua dadb NGAY khi mở/quay lại app (không cần toggle tay, không gate Nav+HUD).
        //
        // reset=FALSE (KHÔNG dùng reset=true ở đây): onResume chạy NGAY sau onCreate, mà onCreate cũng grant khi
        // voiceKeyEnabled && !connected (đúng cảnh sau reboot). reset=true sẽ XOÁ single-flight [grantingAcc] mà
        // lần grant onCreate vừa đặt → HAI phiên dadb force-rebind toggle SONG SONG trên cùng
        // enabled_accessibility_services (đọc-sửa-ghi + remove/re-add đan nhau). Để reset=false cho single-flight tự
        // gộp: onCreate làm việc, onResume no-op nếu đang chạy (và tự thử lại ở resume sau khi cờ đã nhả). Grant treo
        // KHÔNG kẹt cờ vĩnh viễn — doGrantAccessibilityWithTimeout ép nhả sau GRANT_TIMEOUT_MS (9s). Toggle tay
        // OFF→ON vẫn giữ reset=true (đó là nơi cần xoá cờ kẹt aggressive theo yêu cầu tường minh của owner).
        if (Prefs.voiceKeyEnabled(this) && !com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected) {
            NavConnect.grantAccessibility(applicationContext, reset = false)
        }
        // (4b/4c) Cập nhật DÒNG TRẠNG THÁI phím-thoại NGAY (theo cờ connected hiện tại) rồi đọc lại TRỄ +2s/+5s:
        // onServiceConnected chạy bất đồng bộ vài giây sau grant/force-rebind ở trên, đọc tức thì có thể còn "mất kết nối".
        refreshVoiceKeyStatus()
        scheduleVoiceKeyStatusRecheck()
        cast.onResume()
        // Item 4: áp lại vị trí bong bóng VietMap-mod trên cụm (no-op nếu Cluster Cast OFF — cụm chưa live).
        // Gate + gửi broadcast VM_BUBBLE_POS nằm trong VmOverlayPosition; mod VietMap có receiver dời bong bóng.
        runCatching { VmOverlayPosition.applyOnOpen(this) }
        // Nút nổi hiện NGAY sau khi bật Cast + cấp quyền overlay, không cần mở lại app. onCreate() chỉ
        // start service khi overlay ĐÃ có; nếu user vừa cấp quyền ở màn hệ thống rồi quay lại, luồng về
        // đây qua onResume — start lại service để onStartCommand → showBubble() (idempotent, no-op nếu
        // bubble đã hiện). Service tự đứng xuống nếu Cast tắt hoặc overlay vẫn thiếu. runCatching để một
        // ROM thiếu Settings.canDrawOverlays không làm văng Home.
        runCatching {
            if (com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
                    .coordinator(applicationContext).prefs.castEnabled() &&
                Settings.canDrawOverlays(this)
            ) {
                startForegroundService(Intent(this, com.byd.clusternav.modules.clustercast.FloatingBubbleService::class.java))
            }
        }
        ui.post(refresher)
    }

    override fun onPause() {
        ui.removeCallbacks(refresher)
        super.onPause()
    }

    override fun onDestroy() {
        // Tránh rò Activity: VoiceKeyLearnBus là singleton (app-scoped) giữ lambda bắt `this`. Chỉ gỡ khi
        // FINISH thật (đóng app) — KHÔNG gỡ lúc config-change (isFinishing=false) để listener mà onCreate
        // của Activity mới vừa set không bị null oan. Genuine-finish thì không có Activity kế → gỡ = hết rò.
        if (isFinishing) com.byd.clusternav.modules.voicekey.VoiceKeyLearnBus.setListener(null)
        cast.onDestroy()
        super.onDestroy()
    }

    private fun refresh() {
        val permission = if (notificationAccessGranted()) NavigationPermission.GRANTED else NavigationPermission.MISSING
        NavRepository.setPermission(applicationContext, permission)
        val navigation = NavRepository.snapshot(applicationContext)
        val source = navigation.source
        val sourceText = when (val freshness = source.freshness) {
            // B3.57 — SOURCE-AWARE: đặt tên thương hiệu + KÊNH ĐỌC (GMaps = thông báo; VietMap/Waze = đọc màn
            // hình) thay cho package thô. Nhãn + kênh là logic THUẦN ở :core ([NavSourceLabels], test off-car);
            // ở đây chỉ dịch enum kênh sang câu cho người dùng.
            is NavigationFreshness.Fresh -> {
                val pkg = source.identity?.packageName
                val brand = source.identity?.displayName
                    ?: NavSourceLabels.sourceLabel(pkg).ifEmpty { Lang.t("Đang dẫn đường", "Navigating") }
                val channel = NavSourceLabels.readChannel(pkg).readable()
                if (channel.isEmpty()) brand else "$brand · $channel"
            }
            is NavigationFreshness.Stale -> Lang.t("Nguồn đã cũ", "Source stale") + " · ${freshness.reason.readable()}"
            is NavigationFreshness.Unknown -> when (permission) {
                NavigationPermission.MISSING -> Lang.t("Cần quyền truy cập thông báo để đọc dẫn đường", "Grant notification access to read navigation")
                else -> freshness.reason.readable()
            }
        }
        navStatus.text = sourceText
        navDot.tint(if (source.freshness is NavigationFreshness.Fresh) R.color.ok_green else if (permission == NavigationPermission.MISSING) R.color.err_red else R.color.warn_amber)
        laneStatus.text = "${Lang.t("Cụm", "Cluster")}: ${navigation.clusterLane.status.label()}"
        hudStatus.text = "HUD: ${navigation.hud.status.label()}"
        findViewById<Button>(R.id.btn_reconnect_nav).visibility =
            if (permission != NavigationPermission.GRANTED) View.VISIBLE else View.GONE
        // T3 (b3-full-nav-capture · R2): show the nav source currently driving (SourceArbiter.activeSource). The
        // arbiter is fed the WALL clock (System.currentTimeMillis()) by the notification/screen-capture sources,
        // so freshness is judged on the same clock. Null → "—"; a source past STALE_MS is marked "(cũ)/(stale)".
        val nowWall = System.currentTimeMillis()
        val activePkg = com.byd.clusternav.navigation.SourceArbiter.activeSource
        navSourceActive.text = if (activePkg == null) {
            Lang.t("Đang dẫn: —", "Active: —")
        } else {
            val label = NavSourceLabels.sourceLabel(activePkg)
            // B3.57 — kèm KÊNH ĐỌC (đọc màn hình / thông báo) để dòng "đang dẫn" phản ánh đúng nguồn ảnh/a11y.
            val channel = NavSourceLabels.readChannel(activePkg).readable()
            val stale = !com.byd.clusternav.navigation.SourceArbiter.isFresh(nowWall)
            Lang.t("Đang dẫn: ", "Active: ") + label +
                (if (channel.isNotEmpty()) " ($channel)" else "") +
                if (stale) Lang.t(" (cũ)", " (stale)") else ""
        }
        navClusterStatus.refresh()
        updateVoiceKeyLabel()
        // F3: quay lại màn hình phải thấy đúng danh sách gán đang lưu (vd vừa cài/gỡ app đích, hoặc màn
        // hình bị huỷ-dựng lại). Vẽ lại từ Prefs — không giữ bản sao trên UI.
        rebuildVoiceKeyBindingList()
        // Item 4: bật/tắt panel vị trí bong bóng VietMap theo Cluster Cast (cụm chỉ live khi Cast ON).
        refreshVmOverlayPanel()
        // Option B: card Biển báo tốc độ mờ + nhắc "cần Cast" khi Cast OFF (không ẩn cứng).
        refreshBadgeCastGate()
        // HERO (Level-2 · ui-visual-upgrade-l2): tóm tắt trạng thái sống trên cùng — CHỈ ĐỌC từ state sẵn có.
        updateHeroStrip(sourceText)
    }

    /**
     * HERO live-status strip (Level-2 · `docs/specs/ui-visual-upgrade-l2.html`) — READ-ONLY + degrade-safe.
     *
     * Đọc từ CÙNG nguồn các dòng trạng thái hiện có, KHÔNG thêm coupling runtime (không ghi pref, không
     * dispatch Cast):
     *  • `hero_road` ← [NavRepository.state] `road` (TÊN ĐƯỜNG/hướng kế) khi đang dẫn; chưa dẫn/rỗng ⇒ dòng
     *    trạng thái nguồn [navStatusText] làm placeholder.
     *  • `hero_nav_icon` ← [NavRepository.state] `maneuver` (Maneuver TRUNG LẬP) → [toHeroArrow] → drawable mũi
     *    tên có sẵn (ic_turn_left/right/straight); không maneuver/chưa dẫn/off-car ⇒ giữ placeholder tĩnh.
     *  • `hero_dist` ← [NavRepository.state] `distance` (cự ly-tới-rẽ); gate `active` = cách [NavRepository]
     *    đánh dấu phiên; không active/rỗng ⇒ "—".
     *  • `hero_speed` ← [SpeedProvider.mpsOrNull] = TỐC ĐỘ THẬT CỦA XE (BYDAutoSpeedDevice.getCurrentSpeed —
     *    đồng hồ tốc độ), ĐỘC LẬP VietMap/badge; null/off-car ⇒ dial "—".
     *  • `hero_cast` ← [com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime] prefs `castEnabled()`
     *    + `coordinator.state` (đọc-only, y như [com.byd.clusternav.modules.clustercast.MainActivityCastController]).
     *  • `hero_vk`   ← [Prefs.voiceKeyEnabled] + [com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected]
     *    (đúng cặp cờ [refreshVoiceKeyStatus] dùng).
     *
     * Mọi lookup view đều null-safe (parity đảm bảo có mặt, nhưng vẫn thủ) và snapshot state đọc bọc `runCatching`.
     */
    private fun updateHeroStrip(navStatusText: String) {
        // Đọc MỘT lần snapshot dẫn đường đã publish (trường @Volatile, đọc THUẦN — không polling/coupling).
        // active=true chính là cách [NavRepository] đánh dấu "đang có phiên": NotificationParser đặt active=true
        // khi có khung GMaps; [NavRepository.stop] publish NavState() (active=false) khi hết dẫn.
        val nav = runCatching { NavRepository.state }.getOrNull()
        val navigating = nav?.active == true

        // hero_road ← TÊN ĐƯỜNG/hướng kế tiếp khi đang dẫn; chưa dẫn (hoặc rỗng) ⇒ dòng TRẠNG THÁI nguồn
        // (navStatusText) làm placeholder. (Bug T1(a) cũ: LUÔN gán navStatusText ⇒ hiện chuỗi trạng thái thay
        // vì tên đường.)
        findViewById<TextView>(R.id.hero_road)?.text =
            nav?.road?.takeIf { navigating && it.isNotBlank() } ?: navStatusText

        // hero_nav_icon ← MŨI TÊN hướng rẽ suy từ Maneuver TRUNG LẬP (nguồn sự thật hướng rẽ) qua toHeroArrow
        // → drawable mũi tên có sẵn trong res/drawable. Không có maneuver / chưa dẫn / off-car ⇒ giữ placeholder
        // tĩnh (ic_turn_right_g), đúng hành vi cũ. (Bug T1(b) cũ: icon là placeholder TĨNH, không bao giờ đổi.)
        findViewById<ImageView>(R.id.hero_nav_icon)?.setImageResource(
            nav?.maneuver?.takeIf { navigating }?.let { heroArrowRes(it) } ?: R.drawable.ic_turn_right_g,
        )

        // hero_dist ← cự ly-tới-rẽ từ [NavRepository.state] (đọc THUẦN). Gate `active` khớp cách NavRepository
        // đánh dấu phiên (xác nhận T1(c)); không active hoặc rỗng ⇒ "—".
        findViewById<TextView>(R.id.hero_dist)?.text =
            nav?.distance?.takeIf { navigating && it.isNotBlank() } ?: "—"

        // hero_speed ← SpeedDialView (Level-2 · ui-visual-upgrade-l2): TỐC ĐỘ THẬT CỦA XE từ HAL
        // [SpeedProvider.mpsOrNull] đọc BYDAutoSpeedDevice.getCurrentSpeed() (đồng hồ tốc độ xe). ĐỘC LẬP
        // VietMap/badge — LUÔN có trên xe khi app có quyền HAL, không cần bật speed badge hay chạy VietMap.
        // Đọc THUẦN. null = off-car/không đọc được ⇒ dial vẽ "—". (speedometer đọc cao hơn thực ~5-8%.)
        findViewById<com.byd.clusternav.ui.SpeedDialView>(R.id.hero_speed)?.setSpeed(
            SpeedProvider.mpsOrNull()?.let { Math.round(it * 3.6).toInt() },
        )

        val coord = runCatching {
            com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime.coordinator(applicationContext)
        }.getOrNull()
        val castEnabled = runCatching { coord?.prefs?.castEnabled() }.getOrNull() ?: false
        findViewById<TextView>(R.id.hero_cast)?.text = when {
            !castEnabled -> Lang.t("Chiếu cụm: tắt", "Cast: off")
            else -> {
                val st = runCatching { coord?.state }.getOrNull()
                val casting = st is com.byd.clusternav.modules.clustercast.simplified.SimpleCastState.CastingFull ||
                    st is com.byd.clusternav.modules.clustercast.simplified.SimpleCastState.CastingSplit
                if (casting) Lang.t("Chiếu cụm: đang chiếu", "Cast: casting")
                else Lang.t("Chiếu cụm: sẵn sàng", "Cast: ready")
            }
        }

        // hero_cast_preview ← ClusterPreviewView (Level-2 · ui-visual-upgrade-l2): gương CHỈ-ĐỌC trạng thái
        // chiếu cụm — LUÔN vẽ DẢI CHIA (như mockup `.mini-cl`), không bao giờ là ô trơn. Đang chia đôi → tỉ lệ
        // trái đọc từ prefs splitRatioLeftPercent + nhãn "GMaps · VietMap (l:r)"; mọi trạng thái khác → preview
        // chờ setSplit(0.4f, null) (không nhãn). KHÔNG ghi pref, KHÔNG dispatch. Đọc state bọc runCatching.
        findViewById<com.byd.clusternav.ui.ClusterPreviewView>(R.id.hero_cast_preview)?.let { preview ->
            val st = runCatching { coord?.state }.getOrNull()
            if (st is com.byd.clusternav.modules.clustercast.simplified.SimpleCastState.CastingSplit) {
                val leftPct = runCatching { coord?.prefs?.splitRatioLeftPercent() }.getOrNull() ?: 50
                preview.setSplit(leftPct / 100f, "GMaps · VietMap ($leftPct:${100 - leftPct})")
            } else {
                preview.setSplit(0.4f, null)
            }
        }

        val vkEnabled = Prefs.voiceKeyEnabled(this)
        val vkConnected = com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected
        findViewById<TextView>(R.id.hero_vk)?.text = when {
            !vkEnabled -> Lang.t("Phím-thoại: tắt", "Voice key: off")
            vkConnected -> Lang.t("Phím-thoại: ✓", "Voice key: ✓")
            else -> Lang.t("Phím-thoại: mất kết nối", "Voice key: disconnected")
        }
    }

    /**
     * Mã drawable mũi tên HERO cho một [Maneuver] — nối [toHeroArrow] (phân loại THUẦN ở :core, có test đơn vị
     * [HeroArrowMappingTest]) sang drawable mũi tên CÓ SẴN trong `res/drawable`. UTURN dùng lại glyph trái
     * (VN/RHT — không có drawable quay-đầu riêng; khớp quy ước [Maneuver.toHudIcon] gộp u-turn về mã trái 9).
     */
    private fun heroArrowRes(m: Maneuver): Int = when (m.toHeroArrow()) {
        HeroArrow.LEFT -> R.drawable.ic_turn_left
        HeroArrow.RIGHT -> R.drawable.ic_turn_right
        HeroArrow.STRAIGHT -> R.drawable.ic_turn_straight
        HeroArrow.UTURN -> R.drawable.ic_turn_left
    }

    private fun View.tint(color: Int) {
        backgroundTintList = ColorStateList.valueOf(getColor(color))
    }

    private fun NavigationOutputStatus.label(): String = when (this) {
        NavigationOutputStatus.OFF -> Lang.t("tắt", "off")
        NavigationOutputStatus.STARTING -> Lang.t("đang khởi động", "starting")
        NavigationOutputStatus.EMITTING -> Lang.t("đang gửi", "emitting")
        // Trạng thái này KHÔNG BAO GIỜ xảy ra khi chạy thật: `markDisplayVerified` chỉ được gọi từ test,
        // không có producer nào trong `:app`. Giữ nhánh cho `when` vét cạn, nhưng nói đúng cơ sở — theo Q1
        // (đóng ngày 2026-07-27) không có tín hiệu nào của Android xác nhận cụm đang hiện gì, nên chữ
        // "đã xác minh" ở đây sẽ là tuyên bố không ai đặt được.
        NavigationOutputStatus.DISPLAY_VERIFIED -> Lang.t("cụm báo đã nhận", "cluster acknowledged")
        NavigationOutputStatus.STALE -> Lang.t("đã cũ", "stale")
        is NavigationOutputStatus.FAULT -> Lang.t("lỗi: ${reason.readable()}", "error: ${reason.readable()}")
    }

    /**
     * Lý do nguồn dẫn đường, viết cho người đọc.
     *
     * Trước 2026-07-27 chỗ này in `reason.name.replace('_',' ').lowercase()`, nên trên màn tiếng Việt hiện
     * ra "no active session". Cùng lỗi đã sửa ở màn Cast trong ngày: tên hằng trong mã không phải câu cho
     * người dùng. `when` vét cạn nên thêm giá trị mới là trình dịch bắt ngay, không lặng lẽ rơi về tên thô.
     */
    private fun NavigationSourceReason.readable(): String = when (this) {
        // B3.57 — "quyền truy cập thông báo" là GRANT app cần để kết nối phễu đọc dẫn đường Google Maps
        // (notification). Viết là "để đọc dẫn đường" thay vì ngầm định một nguồn cụ thể.
        NavigationSourceReason.PERMISSION_UNKNOWN -> Lang.t("Chưa rõ quyền truy cập thông báo", "Notification access unknown")
        NavigationSourceReason.PERMISSION_MISSING -> Lang.t("Cần quyền truy cập thông báo để đọc dẫn đường", "Grant notification access to read navigation")
        NavigationSourceReason.NO_ACTIVE_SESSION -> Lang.t("Chưa có phiên dẫn đường", "No active navigation session")
        NavigationSourceReason.WAITING_FOR_FRAME -> Lang.t("Đang chờ dữ liệu đầu tiên", "Waiting for first data frame")
        NavigationSourceReason.PROCESS_REHYDRATED_UNVERIFIED -> Lang.t("App vừa khởi động lại, chưa xác nhận nguồn", "App just restarted, source unverified")
        NavigationSourceReason.FRAME_EXPIRED -> Lang.t("Dữ liệu quá hạn", "Data expired")
        NavigationSourceReason.SOURCE_DISCONNECTED -> Lang.t("Mất kết nối với app dẫn đường", "Navigation app disconnected")
        NavigationSourceReason.SOURCE_CHANGED -> Lang.t("Nguồn dẫn đường vừa đổi", "Navigation source changed")
    }

    /**
     * B3.57 — KÊNH ĐỌC của nguồn, viết cho người dùng. Nói rõ nguồn đang dẫn được đọc bằng THÔNG BÁO (GMaps)
     * hay ĐỌC MÀN HÌNH (VietMap/Waze qua a11y + chụp), để dòng trạng thái không còn ngầm định notification là
     * đường duy nhất. Phân loại thuần ở :core ([NavSourceLabels.readChannel]); [NavReadChannel.UNKNOWN] → "".
     */
    private fun NavReadChannel.readable(): String = when (this) {
        NavReadChannel.NOTIFICATION -> Lang.t("thông báo", "notification")
        NavReadChannel.SCREEN_READ -> Lang.t("đọc màn hình", "screen-read")
        NavReadChannel.UNKNOWN -> ""
    }

    /** Lý do đầu ra lỗi, viết cho người đọc — cùng lý do như trên. */
    private fun NavigationOutputFailureReason.readable(): String = when (this) {
        NavigationOutputFailureReason.DELIVERY_THROWN -> Lang.t("gửi thất bại", "delivery failed")
        NavigationOutputFailureReason.DEADLINE_EXCEEDED -> Lang.t("quá thời gian chờ", "deadline exceeded")
        NavigationOutputFailureReason.QUEUE_SATURATED -> Lang.t("hàng chờ đã đầy", "queue saturated")
        NavigationOutputFailureReason.EXECUTOR_REJECTED -> Lang.t("luồng gửi đã dừng", "executor rejected")
        NavigationOutputFailureReason.DISPLAY_ACK_REJECTED -> Lang.t("cụm từ chối xác nhận", "cluster acknowledgement rejected")
        NavigationOutputFailureReason.INTERNAL_CONTRACT_ERROR -> Lang.t("sai hợp đồng nội bộ", "internal contract error")
    }

    /**
     * D5 (closeout 1.28): one-time first-launch disclaimer — no warranty, not affiliated with BYD, install at
     * your own risk (bilingual VI+EN, concise). Guarded by [Prefs.disclaimerShown] so it shows exactly once; the
     * flag is set BEFORE show() so a config-change/dismiss can't re-trigger it. Reuses the AlertDialog pattern
     * already used for [promptNotificationAccessFallback] / [showLearnNameDialog].
     */
    private fun maybeShowDisclaimer() {
        if (isFinishing || Prefs.disclaimerShown(this)) return
        Prefs.setDisclaimerShown(this, true)
        android.app.AlertDialog.Builder(this)
            .setTitle(Lang.t("Miễn trừ trách nhiệm", "Disclaimer"))
            .setMessage(
                Lang.t(
                    "ClusterNav là thử nghiệm cá nhân, KHÔNG liên kết với BYD. Không có bảo đảm về an toàn lái xe, " +
                        "tương thích hay độ ổn định. Cài và dùng với rủi ro của riêng bạn.",
                    "ClusterNav is a personal experiment, NOT affiliated with BYD. It comes with no warranty of " +
                        "driving safety, compatibility, or reliability. Install and use at your own risk.",
                ),
            )
            .setPositiveButton(Lang.t("Tôi hiểu", "I understand"), null)
            .show()
    }

    /**
     * FALLBACK khi tự cấp quyền qua dadb THẤT BẠI (thường vì chưa bấm "Allow USB debugging" trên xe lần
     * đầu). Cho THỬ LẠI selfGrant, hoặc mở màn Settings hệ thống để bật tay (một số máy IVI không có màn này
     * → openNotificationAccessSettings tự toast hướng dẫn). Đây KHÔNG còn là đường chính: đường chính là
     * NavConnect.selfGrant (cấp qua dadb), gọi khi bấm nút / bật công tắc lúc thiếu quyền.
     */
    private fun promptNotificationAccessFallback() {
        if (isFinishing) return
        android.app.AlertDialog.Builder(this)
            .setTitle(Lang.t("Chưa cấp được quyền", "Couldn't grant access"))
            .setMessage(
                Lang.t(
                    "Chưa tự cấp được quyền đọc thông báo qua ADB nội bộ. Lần đầu cần bật gỡ lỗi USB: khi màn " +
                        "xe hiện hộp thoại “Allow USB debugging?”, bấm Allow rồi Thử lại.\n\nHoặc mở cài đặt hệ " +
                        "thống để bật ClusterNav thủ công (một số máy không có màn này).",
                    "Couldn't self-grant notification access over local ADB. First time, allow USB debugging: " +
                        "when the car screen shows “Allow USB debugging?”, tap Allow, then Retry.\n\nOr open " +
                        "system settings to enable ClusterNav manually (some units lack this screen).",
                ),
            )
            .setPositiveButton(Lang.t("Thử lại", "Retry")) { _, _ ->
                Toast.makeText(this, Lang.t("Đang cấp quyền…", "Granting…"), Toast.LENGTH_SHORT).show()
                NavConnect.selfGrant(applicationContext) { ok ->
                    if (isFinishing) return@selfGrant
                    if (ok) refresh()
                    else Toast.makeText(this, Lang.t("Vẫn chưa được — kiểm tra hộp thoại Allow trên xe.", "Still failed — check the Allow dialog on the car."), Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton(Lang.t("Mở cài đặt", "Open settings")) { _, _ -> openNotificationAccessSettings() }
            .setNegativeButton(Lang.t("Đóng", "Close"), null)
            .show()
    }

    /**
     * Điều hướng tới màn Notification-access theo thứ tự ưu tiên: deep-link thẳng entry ClusterNav
     * (API 30+) → màn danh sách → trang chi tiết app. Mọi bước bọc try để không văng nếu ROM thiếu
     * activity nào; hết đường thì toast hướng dẫn tay.
     */
    private fun openNotificationAccessSettings() {
        val comp = ComponentName(this, NavNotificationListener::class.java).flattenToString()
        if (android.os.Build.VERSION.SDK_INT >= 30 && tryStartActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, comp),
            )
        ) {
            return
        }
        if (tryStartActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) return
        if (tryStartActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName"),
                ),
            )
        ) {
            return
        }
        Toast.makeText(
            this,
            Lang.t(
                "Không mở được cài đặt. Vào Cài đặt → Ứng dụng → Truy cập đặc biệt → Truy cập thông báo → bật ClusterNav.",
                "Couldn't open settings. Go to Settings → Apps → Special access → Notification access → enable ClusterNav.",
            ),
            Toast.LENGTH_LONG,
        ).show()
    }

    // ── Nút vật lý → Trợ lý giọng nói ───────────────────────────────────────────────────────────
    // Ứng viên keycode cho nút voice/steering. "Học phím" = sentinel -1: bấm nút THẬT trên xe để gán
    // keycode chưa biết. Nút mic vô-lăng trên xe này NHẤN-GIỮ = 328 (đo on-car 2026-08-13) → để sẵn làm
    // ứng viên đầu; nhấn ngắn phát mã KHÁC nên trợ lý gốc (小迪) giữ nguyên.
    // Preset keycode ứng viên (nút vô-lăng/táp-lô). Nút tự học thêm từ Prefs.voiceKeyCustomButtons.
    // Nút mic vô-lăng xe này giữ = 328 (đo on-car 2026-08-13); nhấn ngắn ra mã KHÁC nên native (小迪) giữ nguyên.
    private fun voiceKeyPresets(): List<Pair<String, Int>> = listOf(
        Lang.t("Nút mic vô-lăng — giữ (328)", "Steering-wheel mic — hold (328)") to 328,
        Lang.t("Trợ lý giọng nói (VOICE_ASSIST · 231)", "Voice assistant (VOICE_ASSIST · 231)") to 231,
        Lang.t("Trợ lý (ASSIST · 219)", "Assistant (ASSIST · 219)") to 219,
        "Play/Pause (85)" to 85,
        Lang.t("Bài trước (PREVIOUS · 88)", "Previous track (PREVIOUS · 88)") to 88,
        Lang.t("Bài sau (NEXT · 87)", "Next track (NEXT · 87)") to 87,
        "Headset hook (79)" to 79,
        Lang.t("Gọi (CALL · 5)", "Call (CALL · 5)") to 5,
        Lang.t("Tìm kiếm (SEARCH · 84)", "Search (SEARCH · 84)") to 84,
    )
    /** Dropdown nút = preset + nút tự học (persist). */
    private fun voiceKeyButtonList(): List<Pair<String, Int>> = voiceKeyPresets() + Prefs.voiceKeyCustomButtons(this)

    /**
     * Đích chọn được = 3 mục đặc biệt (ghim đầu) + mọi app có launcher. Dựng MỘT lần rồi dùng lại cho cả
     * dropdown lẫn nhãn từng dòng đã gán — hai nơi phải đọc CÙNG một bảng, nếu không dòng đã gán có thể
     * hiện tên khác với lúc chọn.
     */
    private val voiceKeyTargetSpecs: List<Pair<String, String>> by lazy {
        listOf(
            Lang.t("Trợ lý mặc định hệ thống", "System default assistant") to Prefs.VK_TARGET_ASSIST,
            Lang.t("Trợ lý qua phím cứng (Gemini · 231)", "System assistant via hard key (Gemini · 231)") to Prefs.VK_TARGET_GEMINI_KEY,
            Lang.t("Nhận dạng giọng nói", "Speech recognizer") to Prefs.VK_TARGET_RECOGNIZER,
        ) + com.byd.clusternav.modules.clustercast.ClusterCast.listInstalledApps(this).map { it.label to it.pkg }
    }

    /** Nhãn nút: ưu tiên tên trong dropdown (preset/tự học), không có thì dựng từ mã phím. */
    private fun voiceKeyButtonLabel(code: Int): String =
        voiceKeyButtonList().firstOrNull { it.second == code }?.first
            ?: (android.view.KeyEvent.keyCodeToString(code) + " ($code)")

    /** Nhãn đích: tên app trong bảng; app đã gỡ cài ⇒ hiện chính chuỗi spec để owner còn nhận ra mà xoá. */
    private fun voiceKeyTargetLabel(spec: String): String =
        voiceKeyTargetSpecs.firstOrNull { it.second == spec }?.first ?: spec

    /** Nhãn "nút ĐANG CHỌN trong dropdown" — F3: không còn khái niệm "nút hiện tại" vì gán được nhiều nút. */
    private fun updateVoiceKeyLabel() {
        val current = findViewById<TextView>(R.id.txt_voicekey_current) ?: return
        val kc = selectedVoiceKeyCode()
        current.text =
            if (kc == null) Lang.t("Nút đang chọn: —", "Selected button: —")
            else Lang.t("Nút đang chọn: ", "Selected button: ") + android.view.KeyEvent.keyCodeToString(kc) + " ($kc)"
    }

    private fun selectedVoiceKeyCode(): Int? {
        val spinner = findViewById<android.widget.Spinner>(R.id.spinner_voicekey_button) ?: return null
        return voiceKeyButtonList().getOrNull(spinner.selectedItemPosition)?.second
    }

    /**
     * (Re)nạp dropdown nút. [selectCode] = mã phím cần chọn sẵn (vd vừa học xong một nút mới).
     * Lựa chọn dropdown là trạng thái TẠM của màn hình — cấu hình thật nằm ở danh sách đã gán.
     */
    private fun rebuildVoiceKeyButtonSpinner(selectCode: Int? = null) {
        val spinner = findViewById<android.widget.Spinner>(R.id.spinner_voicekey_button) ?: return
        // Đọc mã đang chọn TRƯỚC khi thay adapter: gán adapter mới reset lựa chọn về 0, nên nếu đọc sau thì
        // "giữ nguyên lựa chọn" luôn ra mục đầu — owner vừa xoá một nút tự học là nút đang chọn nhảy mất.
        val keep = selectCode ?: selectedVoiceKeyCode()
        val list = voiceKeyButtonList()
        spinner.adapter = android.widget.ArrayAdapter(this, R.layout.cockpit_spinner_item, list.map { it.first })
            .apply { setDropDownViewResource(R.layout.cockpit_spinner_dropdown_item) }
        spinner.setSelection(list.indexOfFirst { it.second == keep }.coerceAtLeast(0))
        updateVoiceKeyLabel()
    }

    /**
     * F3 — vẽ lại DANH SÁCH ĐÃ GÁN từ `Prefs.voiceKeyBindings` (đúng danh sách mà
     * `NavAccessibilityService.onKeyEvent` nghe theo, không phải một bản sao khác trên UI).
     * Rỗng ⇒ hiện dòng nhắc "chưa gán nút nào ⇒ không có gì chạy" (yêu cầu của owner).
     */
    private fun rebuildVoiceKeyBindingList() {
        val container = findViewById<android.widget.LinearLayout>(R.id.list_voicekey_bindings) ?: return
        val items = Prefs.voiceKeyBindings(this)
        findViewById<TextView>(R.id.txt_voicekey_empty)?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        container.removeAllViews()
        for (b in items) {
            val row = layoutInflater.inflate(R.layout.row_voicekey_binding, container, false)
            row.findViewById<TextView>(R.id.txt_binding_label).text =
                voiceKeyButtonLabel(b.keyCode) + "  →  " + voiceKeyTargetLabel(b.targetSpec)
            // Nhãn nút đặt LÚC CHẠY, không lấy chữ cứng trong XML — KDoc của row_voicekey_binding.xml nói rõ
            // chuỗi không vào strings.xml (file đó nằm trong danh sách canh chống-sửa-lén T11) nên song ngữ
            // phải do đây lo. Bản F3 đầu tiên quên, nên máy đặt tiếng Anh vẫn thấy nút "Xoá".
            row.findViewById<Button>(R.id.btn_binding_remove).apply {
                text = Lang.t("Xoá", "Remove")
                contentDescription = Lang.t("Xoá dòng gán này", "Remove this binding")
                setOnClickListener {
                    Prefs.removeVoiceKeyBinding(this@MainActivity, b.keyCode)
                    rebuildVoiceKeyBindingList()
                    Toast.makeText(this@MainActivity, Lang.t("Đã xoá gán", "Binding removed"), Toast.LENGTH_SHORT).show()
                }
            }
            container.addView(row)
        }
    }

    /** Sau khi service bắt keycode mới: hỏi tên → lưu nút custom → nạp lại dropdown + chọn. */
    private fun showLearnNameDialog(code: Int) {
        if (isFinishing || isDestroyed) return
        val default = android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')
        val input = android.widget.EditText(this).apply { setText(default); setSelection(text.length) }
        android.app.AlertDialog.Builder(this)
            .setTitle(Lang.t("Đặt tên nút (mã $code)", "Name this button (code $code)"))
            .setView(input)
            .setPositiveButton(Lang.t("Lưu", "Save")) { _, _ ->
                // Học phím = thêm nút vào dropdown + chọn sẵn. CHƯA gán gì cả — owner còn phải chọn app rồi
                // bấm "Thêm gán" (F3). Trước F3 bước này ghi thẳng `voicekey_keycode`, tức học xong là đổi
                // luôn nút đang chạy; giờ cấu hình thật chỉ đổi khi owner bấm Thêm.
                val name = input.text.toString().ifBlank { default }
                Prefs.addVoiceKeyCustomButton(this, "$name (mã $code)", code)
                rebuildVoiceKeyButtonSpinner(code)
                Toast.makeText(
                    this,
                    Lang.t("Đã lưu nút. Chọn app rồi bấm “Thêm gán”.", "Button saved. Pick an app, then tap “Add binding”."),
                    Toast.LENGTH_LONG,
                ).show()
            }
            .setNegativeButton(Lang.t("Huỷ", "Cancel"), null)
            .show()
    }

    private fun setupVoiceKeyControls() {
        val vkSwitch = findViewById<Switch>(R.id.switch_voicekey_enabled)
        val targetSpinner = findViewById<android.widget.Spinner>(R.id.spinner_voicekey_target)

        vkSwitch.isChecked = Prefs.voiceKeyEnabled(this)
        vkSwitch.setOnCheckedChangeListener { _, on ->
            Prefs.setVoiceKeyEnabled(this, on)
            refreshVoiceKeyStatus()   // (4d) phản ánh NGAY bật/tắt vừa lưu; BOUND đọc lại ở onResume + nút "Kiểm tra".
            // TASK 3 (R2 · docs/specs/clusternav-closeout-1.28.html): toggle OFF→ON RESETS the grant state +
            // re-requests the key bind (fresh grant + force-rebind) so the voice-key recovers after a reboot
            // WITHOUT an app restart. Do NOT gate on accessibilityBoosterGranted(): after a reboot the service
            // stays ENABLED-in-the-setting but NOT BOUND, so an enabled-only check would skip the heal. reset=true
            // clears a hung single-flight (grantingAcc) before attempting; grantAccessibility still force-rebinds
            // only when actually enabled-but-not-bound (no flicker if already bound). No auto-loop/backoff.
            if (on) {
                Toast.makeText(this, Lang.t("Đang bật dịch vụ Hỗ trợ…", "Enabling accessibility service…"), Toast.LENGTH_SHORT).show()
                NavConnect.grantAccessibility(applicationContext, reset = true) { ok ->
                    if (isFinishing) return@grantAccessibility
                    Toast.makeText(
                        this,
                        if (ok) Lang.t("Đã bật. Bấm nút đã gán để mở app.", "Enabled. Press the mapped button to open the app.")
                        else Lang.t("Chưa bật được Hỗ trợ — bấm Allow USB debugging trên xe rồi thử lại, hoặc bật tay ở Cài đặt > Hỗ trợ.", "Couldn't enable accessibility — tap Allow USB debugging on the car and retry, or enable it in Settings > Accessibility."),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        // Voice-key BINDING STATUS + nút "Kiểm tra / Sửa ngay" (READ-ONLY status + heal thủ công theo yêu cầu).
        // Trạng thái đọc NavAccessibilitySource.connected (service Hỗ trợ đã BOUND chưa). Nút REUSE
        // NavConnect.grantAccessibility(reset=true) — CÙNG đường heal của toggle OFF→ON, KHÔNG đổi grant logic.
        // onServiceConnected chạy bất đồng bộ vài giây sau force-rebind ⇒ đọc lại TRỄ +2s/+5s cho khỏi kẹt "mất kết nối".
        findViewById<Button>(R.id.btn_voicekey_recheck).apply {
            text = Lang.t("Kiểm tra / Sửa ngay", "Check / Fix now")
            setOnClickListener {
                Toast.makeText(this@MainActivity, Lang.t("Đang kiểm tra…", "Checking…"), Toast.LENGTH_SHORT).show()
                NavConnect.grantAccessibility(applicationContext, reset = true) { ok ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        refreshVoiceKeyStatus()
                        Toast.makeText(
                            this@MainActivity,
                            if (ok) Lang.t("Phím-thoại đã sẵn sàng.", "Voice key ready.")
                            else Lang.t("Chưa bật được Hỗ trợ — bấm Allow USB debugging trên xe rồi thử lại, hoặc bật tay ở Cài đặt > Hỗ trợ.", "Couldn't enable accessibility — tap Allow USB debugging on the car and retry, or enable it in Settings > Accessibility."),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                scheduleVoiceKeyStatusRecheck()   // (4c) onServiceConnected bất đồng bộ → đọc lại +2s/+5s sau khi bấm.
            }
        }

        // Dropdown nút (preset + custom). F3: chọn nút KHÔNG còn ghi cấu hình — nó chỉ là bước 1 của
        // "chọn nút → chọn app → Thêm gán". Cấu hình thật chỉ đổi khi bấm Thêm/Xoá.
        rebuildVoiceKeyButtonSpinner()
        val btnSpinner = findViewById<android.widget.Spinner>(R.id.spinner_voicekey_button)
        btnSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) =
                updateVoiceKeyLabel()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        // Nhấn-giữ 1 mục để XOÁ nút tự học (preset không xoá).
        //
        // ⚠️ [ĐO 2026-08-24] KHỐI NÀY LÀ CODE CHẾT — đã đọc source AOSP `android-10.0.0_r47`, không phải trí nhớ:
        //   • `Spinner.java` KHÔNG có một chỗ nào gọi `performItemLongClick` / `performLongClick` / đọc
        //     `mOnItemLongClickListener`; `onTouchEvent` chỉ chuyển cho `mForwardingListener` rồi `super`,
        //     và `performClick()` chỉ MỞ POPUP.
        //   • `AdapterView.java` chỉ CẤT listener (`setOnItemLongClickListener` set field + `setLongClickable(true)`)
        //     và KHÔNG override `performLongClick()` để phát tới nó. Bên phát thật là `AbsListView.performLongPress`,
        //     mà `Spinner` không kế thừa `AbsListView`.
        //   ⇒ Nhấn-giữ dropdown chỉ mở popup; lambda dưới CHƯA TỪNG chạy kể từ 1.19.
        //
        // Hệ quả: owner hiện KHÔNG có đường xoá một nút tự học (F3 làm nó lộ rõ hơn — nhãn dòng đã gán rơi về
        // `KEYCODE_x (mã)`). Đây là lỗi CÓ TỪ 1.19, KHÔNG phải hồi quy của F3, và cách chữa (đổi sang dialog
        // chọn-để-xoá, hay nút "Xoá nút này" cạnh dropdown) là THÊM giao diện ⇒ quyết định của owner, không
        // được tự ý làm trong phạm vi F3. Đã ghi backlog F4. Giữ nguyên khối này để không im lặng đổi hành vi;
        // KHÔNG được tin nó đang chạy.
        btnSpinner.onItemLongClickListener = android.widget.AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val item = voiceKeyButtonList().getOrNull(pos)
            if (item != null && Prefs.voiceKeyCustomButtons(this).any { it.second == item.second }) {
                Prefs.removeVoiceKeyCustomButton(this, item.second)
                rebuildVoiceKeyButtonSpinner()
                Toast.makeText(this, Lang.t("Đã xoá nút", "Button removed"), Toast.LENGTH_SHORT).show()
                true
            } else false
        }

        // Nút "Học phím mới" — NGOÀI dropdown: bấm → chờ bấm nút vật lý → hiện ô đặt tên.
        findViewById<Button>(R.id.btn_voicekey_learn).setOnClickListener {
            Prefs.setVoiceKeyLearn(this, true)
            if (!accessibilityBoosterGranted()) NavConnect.grantAccessibility(applicationContext)
            Toast.makeText(this, Lang.t("Giữ màn hình này mở rồi bấm nút vật lý muốn dùng…", "Keep this screen open, then press the physical button…"), Toast.LENGTH_LONG).show()
        }

        // Đích = 3 mục đặc biệt (ghim đầu) + toàn bộ app có launcher (reuse ClusterCast.listInstalledApps).
        // F3: chọn app cũng KHÔNG ghi cấu hình — chỉ là bước 2. Không listener nào ở đây nữa.
        targetSpinner.adapter =
            android.widget.ArrayAdapter(this, R.layout.cockpit_spinner_item, voiceKeyTargetSpecs.map { it.first })
                .apply { setDropDownViewResource(R.layout.cockpit_spinner_dropdown_item) }

        // F3 — "3 · Thêm gán": ghi cặp (nút đang chọn → app đang chọn) vào danh sách. Đây là NƠI DUY NHẤT
        // ghi cấu hình gán, nên cũng là nơi chạy công thức "đặt trợ lý hệ thống = Google/Gemini" (trước F3
        // nằm ở listener của dropdown app — chạy cả khi owner chỉ lướt qua mục đó mà không gán gì).
        findViewById<Button>(R.id.btn_voicekey_add).setOnClickListener {
            val kc = selectedVoiceKeyCode()
            val target = voiceKeyTargetSpecs.getOrNull(targetSpinner.selectedItemPosition)
            if (kc == null || target == null) {
                Toast.makeText(this, Lang.t("Chọn nút và app trước đã.", "Pick a button and an app first."), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val replaced = Prefs.addVoiceKeyBinding(this, kc, target.second)
            rebuildVoiceKeyBindingList()
            val msg = when {
                replaced == null ->
                    Lang.t("Đã thêm: ", "Added: ") + voiceKeyButtonLabel(kc) + " → " + target.first
                replaced == target.second ->
                    Lang.t("Đã có sẵn: ", "Already set: ") + voiceKeyButtonLabel(kc) + " → " + target.first
                // GHI ĐÈ — báo rõ thay cái gì, cấm im lặng (một mã phím chỉ gán một app).
                else -> Lang.t("Nút này đã gán ", "This button was bound to ") + voiceKeyTargetLabel(replaced) +
                    Lang.t(" → đã THAY bằng ", " → REPLACED with ") + target.first
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

            // Chọn Gemini (sentinel 231 HOẶC lỡ chọn thẳng app Gemini/Google) → đặt luôn trợ lý hệ thống = Google/Gemini
            // (full recipe 8hare, một lần) để keyevent 231 mở Gemini dạng ASSISTANT (voice), không phải app home.
            // `replaced != target.second` ⇒ CHỈ chạy khi cấu hình thật sự đổi. Bấm Thêm lại đúng cặp đang có
            // (nhánh "Đã có sẵn") thì không đổi gì cả, mà công thức này bung một thread + một phiên dadb +
            // 2 Toast — đúng kiểu tác dụng phụ chạy oan mà F3 vừa dời khỏi listener dropdown để tránh.
            if (replaced != target.second &&
                com.byd.clusternav.modules.voicekey.AssistantLauncher.isGeminiVoiceSpec(target.second)
            ) {
                Toast.makeText(this, Lang.t("Đang đặt Gemini làm trợ lý hệ thống…", "Setting Gemini as system assistant…"), Toast.LENGTH_SHORT).show()
                Thread {
                    val err = com.byd.clusternav.modules.voicekey.AssistantLauncher.setSystemAssistant(this@MainActivity)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        Toast.makeText(this@MainActivity,
                            if (err.isEmpty()) Lang.t("Đã đặt trợ lý = Google/Gemini. Giữ nút mic để NÓI (không mở app).", "Assistant set to Google/Gemini. Long-press mic to TALK (not open app).")
                            else err,
                            Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
        }

        rebuildVoiceKeyBindingList()

        // F4e (bug owner 08-25): hold-mic → Gemini KHÔNG work lúc mở app, phải xoá+add lại mới chạy. Vì đích
        // Gemini đi `keyevent 231` (route tới TRỢ LÝ HỆ THỐNG) nên cần `setSystemAssistant` chạy TRƯỚC — mà
        // từ F3, recipe đó CHỈ chạy ở nút "Thêm". Mở app / sau reboot (ROM đặt lại trợ lý về 小迪) thì trợ lý
        // chưa phải Gemini ⇒ 231 route sai. (Kiki mở app THẲNG nên không dính — owner đo: Kiki OK ngay.)
        maybeReapplyGeminiAssistant()

        // Cầu học-phím: service bắt keycode → hiện dialog đặt tên (Activity foreground).
        com.byd.clusternav.modules.voicekey.VoiceKeyLearnBus.setListener { code -> runOnUiThread { showLearnNameDialog(code) } }

        // (4a) Hiện DÒNG TRẠNG THÁI phím-thoại NGAY khi dựng xong control (đọc pref + cờ connected hiện tại).
        refreshVoiceKeyStatus()
    }

    /**
     * Cập nhật DÒNG TRẠNG THÁI phím-thoại (<code>txt_voicekey_status</code>) + màu theo ground-truth
     * [com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected] (service Hỗ trợ đã
     * onServiceConnected/BOUND chưa). THUẦN ĐỌC — KHÔNG đụng grant logic. Ba trạng thái:
     * tắt (xám) · bật + bound (xanh ✓) · bật + chưa bound (đỏ, gợi ý bấm "Sửa ngay"). Nhãn song ngữ đặt lúc
     * chạy qua [Lang.t] (KHÔNG strings.xml — đang byte-seal). An toàn khi view chưa có (`?: return`).
     */
    private fun refreshVoiceKeyStatus() {
        val tv = findViewById<TextView>(R.id.txt_voicekey_status) ?: return
        val enabled = Prefs.voiceKeyEnabled(this)
        val connected = com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected
        when {
            !enabled -> {
                tv.text = Lang.t("Phím-thoại: đang tắt", "Voice key: off")
                tv.setTextColor(0xFF9E9E9E.toInt())
            }
            connected -> {
                tv.text = Lang.t("Phím-thoại: ĐANG HOẠT ĐỘNG ✓", "Voice key: ACTIVE ✓")
                tv.setTextColor(0xFF2E7D32.toInt())
            }
            else -> {
                tv.text = Lang.t("Phím-thoại: MẤT KẾT NỐI — bấm Sửa ngay", "Voice key: DISCONNECTED — tap Fix now")
                tv.setTextColor(0xFFC62828.toInt())
            }
        }
    }

    /**
     * Đọc lại trạng thái phím-thoại TRỄ +2s và +5s: NavAccessibilityService.onServiceConnected chạy BẤT ĐỒNG
     * BỘ vài giây sau khi force-rebind, nên một lần đọc tức thì sẽ còn thấy "mất kết nối" cũ. Dùng cho onResume
     * và sau khi bấm "Kiểm tra / Sửa ngay". Guard [isFinishing]/[isDestroyed] vì Activity có thể đã đóng khi callback nổ.
     */
    private fun scheduleVoiceKeyStatusRecheck() {
        longArrayOf(2_000L, 5_000L).forEach { delayMs ->
            ui.postDelayed({ if (!isFinishing && !isDestroyed) refreshVoiceKeyStatus() }, delayMs)
        }
    }

    /**
     * F4e — đặt lại **trợ lý hệ thống = Google/Gemini** lúc mở app NẾU có ít nhất một binding trỏ Gemini
     * (sentinel 231 / bard / GSA). Để hold-mic → Gemini work NGAY, không phải xoá+add lại (recipe của nút
     * "Thêm" chỉ chạy khi cấu hình ĐỔI). Giống accessibility-booster self-grant (v1.18): idempotent, chạy
     * NỀN (dadb ~1-2s, degrade-safe), CHỈ khi thật sự có binding Gemini (owner chỉ dùng Kiki thì không đụng).
     */
    private fun maybeReapplyGeminiAssistant() {
        if (!com.byd.clusternav.modules.voicekey.AssistantLauncher.hasGeminiBinding(this)) return
        Thread {
            // App-open: owner đang nhìn màn hình ⇒ setSystemAssistant dùng retry mặc định AWAIT_ADB_APPROVAL.
            val err = runCatching {
                com.byd.clusternav.modules.voicekey.AssistantLauncher.setSystemAssistant(this@MainActivity)
            }.getOrElse { "" }
            if (err.isNotEmpty()) android.util.Log.w("MainActivity", "re-apply Gemini assistant lúc mở app: $err")
        }.start()
    }

    private fun tryStartActivity(intent: Intent): Boolean =
        runCatching { startActivity(intent); true }.getOrDefault(false)

    private fun notificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val expected = ComponentName(this, NavNotificationListener::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it.trim()) == expected }
    }

    /**
     * Accessibility booster (đọc màn GMaps → screenRead ground-truth) đã được bật chưa. Đọc THẲNG secure
     * setting (mọi app đọc được — KHÔNG cần dadb), y như [notificationAccessGranted]. Chỉ khi thiếu mới gọi
     * [NavConnect.grantAccessibility] (dadb) để append → tránh mở phiên dadb thừa mỗi lần bật Nav+HUD / mở app.
     */
    private fun accessibilityBoosterGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_accessibility_services") ?: return false
        val expected = ComponentName(this, com.byd.clusternav.modules.navaccess.NavAccessibilityService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it.trim()) == expected }
    }

    /**
     * B1 (owner 2026-08-19, SỬA 2026-08-21 sau test on-car): "badge bật → VietMap tự chạy để widget có nguồn speed-limit".
     * Sửa 2 bug: (1) guard cũ `isAppForeground` dùng `runningAppProcesses` — Android 10+ chỉ thấy process của CHÍNH mình
     * → luôn trả false → LUÔN relaunch VietMap dù đã chạy; (2) `startActivity(VietMap)` → VietMap ĐÈ lên app mình.
     * Nay: kiểm VietMap chạy chưa bằng `pidof` qua dadb (uid shell = tin cậy cross-app); CHỈ start khi CHƯA chạy;
     * sau khi start thì đưa ClusterNav lại foreground (relaunch launcher qua shell — không BAL-block, không recreate)
     * để VietMap KHÔNG đè. Chạy nền, degrade-safe. Gọi ở CUỐI [onCreate] (chỉ khi tạo mới).
     */
    // ── Item 4: chỉnh VỊ TRÍ bong bóng VietMap-mod trên cụm (spec vietmap-overlay-position-ui) ──────
    // [VmOverlayPosition] lưu x/y (Prefs) + bắn broadcast VM_BUBBLE_POS tới mod VietMap để dời bong bóng.
    // Chỉ có nghĩa khi Cluster Cast ON (cụm mới "live" cho bong bóng lên) → gate: bật khung kéo-thả khi
    // castOn(), ngược lại mờ + nhắc bật Cast. Bong bóng nay CHỈ kéo-thả — thả ra là tự lưu + gửi ngay
    // (onMoved → setAbsoluteTopLeft → send). Không còn nút "Nửa phải"/"Đặt lại"/"Áp dụng". Nhãn song ngữ
    // đặt lúc chạy qua [Lang.t] (đúng lối đang dùng — không thêm vào strings.xml đang bị seal).
    private var vmPlacementView: VmBubblePlacementView? = null

    private fun setupVmOverlayControls() {
        // Toggle bong bóng VietMap trên cụm (owner 2026-08-28, default TẮT). Detach listener trước khi khôi phục
        // isChecked để không bắn sự kiện giả; nhãn song ngữ đặt lúc chạy qua Lang.t. Bật ⇒ auto-start VietMap MỘT
        // LẦN (VietMapAutostart, dedup pidof) giống hành vi badge tốc độ, rồi refresh panel kéo-thả.
        findViewById<Switch>(R.id.switch_vm_bubble_enabled)?.apply {
            setOnCheckedChangeListener(null)
            text = Lang.t("Hiện bong bóng VietMap trên cụm", "Show VietMap bubble on cluster")
            isChecked = Prefs.vmBubbleEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setVmBubbleEnabled(this@MainActivity, checked)
                if (checked) VietMapAutostartService.startForAppOpen(this@MainActivity)
                refreshVmOverlayPanel()
            }
        }
        findViewById<FrameLayout>(R.id.vm_bubble_placement_container)?.let { container ->
            val view = VmBubblePlacementView(
                this,
                VmOverlayPosition.CLUSTER_WIDTH, VmOverlayPosition.CLUSTER_HEIGHT,
                VmOverlayPosition.BUBBLE_WIDTH, VmOverlayPosition.BUBBLE_HEIGHT,
            ) { absX, absY ->
                // Kéo-thả xong → convert góc-trên-trái tuyệt đối sang offset-từ-tâm + lưu + bắn (nếu Cast ON).
                VmOverlayPosition.setAbsoluteTopLeft(this, absX, absY)
                refreshVmOverlayPanel()
            }
            view.setBubbleTopLeftCluster(VmOverlayPosition.absLeftX(this), VmOverlayPosition.absTopY(this))
            container.removeAllViews()
            container.addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            vmPlacementView = view
        }
        // Nút "Nửa phải" / "Đặt lại" / "Áp dụng" ĐÃ BỎ (owner: bong bóng nay CHỈ kéo-thả). Vị trí được GỬI
        // NGAY khi thả kéo-thả (onMoved → VmOverlayPosition.setAbsoluteTopLeft → set(sendNow=true) → send,
        // gate castOn). Không còn nút nào ở panel này.
        refreshVmOverlayPanel()
    }

    /**
     * Bật/tắt panel vị trí theo toggle bong bóng VietMap + Cluster Cast: chỉ chỉnh được khi CẢ toggle BẬT VÀ
     * Cast ON (cụm mới live cho bong bóng mod VietMap lên). Toggle TẮT ⇒ mờ + khoá + nhắc "Bật toggle để
     * chỉnh"; toggle bật nhưng Cast OFF ⇒ nhắc "Bật Cluster Cast để chỉnh vị trí". Gọi ở
     * [setupVmOverlayControls] (lúc dựng) và [refresh] (mỗi nhịp 1 s — bám công tắc lúc app đang mở).
     */
    private fun refreshVmOverlayPanel() {
        val enabled = Prefs.vmBubbleEnabled(this)
        val castOn = VmOverlayPosition.castOn(this)
        val on = enabled && castOn
        vmPlacementView?.isEnabled = on
        vmPlacementView?.alpha = if (on) 1f else 0.4f
        findViewById<TextView>(R.id.txt_vm_pos_hint)?.text = when {
            !enabled -> Lang.t("Bật toggle để chỉnh", "Turn on the toggle to adjust")
            !castOn -> Lang.t("Bật Cluster Cast để chỉnh vị trí", "Turn on Cluster Cast to adjust position")
            else -> Lang.t(
                "Kéo bong bóng trong khung để đặt vị trí — thả ra là tự lưu và gửi",
                "Drag the bubble in the frame to position it — releasing saves and sends",
            )
        }
    }

    // ── Option B: card gập (mỗi tính năng = header luôn hiện + thân gập được) ─────────────────────
    // Tái dùng mẫu cast_recovery_toggle nhưng KHÔNG thêm @+id nào (bộ id khoá 95): header/thân gập đánh dấu
    // bằng android:tag, tra bằng findViewWithTag. Card hay dùng mở sẵn; card cài-một-lần đóng sẵn.
    private fun setupCollapsibleCards() {
        setSummary("sum_nav", Lang.t("Dẫn đường trên cụm + HUD", "Guidance on cluster + HUD"))
        setSummary("sum_vk", Lang.t("Nút vật lý → mở app / trợ lý", "Hardware button → app / assistant"))
        setSummary("sum_bubble", Lang.t("Vị trí bóng VietMap trên cụm (cần Cast)", "VietMap bubble position (needs Cast)"))
        setSummary("sum_seat", Lang.t("Làm mát / sưởi ghế tự động", "Auto seat cooling / heating"))
        // (sum_badge đặt ĐỘNG theo trạng thái Cast trong refreshBadgeCastGate.)
        wireCollapse("toggle_nav", "body_nav", startExpanded = true)
        wireCollapse("toggle_vk", "body_vk", startExpanded = true)
        wireCollapse("toggle_badge", "body_badge", startExpanded = false)
        wireCollapse("toggle_bubble", "body_bubble", startExpanded = false)
        wireCollapse("toggle_seat", "body_seat", startExpanded = true)
    }

    /** Đặt chữ tóm tắt (tag) trong header card — no-op nếu biến thể layout không có tag đó. */
    private fun setSummary(tag: String, text: String) {
        (window.decorView.findViewWithTag<View>(tag) as? TextView)?.text = text
    }

    /**
     * Nối một header gập (tag [headerTag]) với thân gập (tag [bodyTag]): chạm header đảo hiện/ẩn thân; đặt
     * trạng thái ban đầu theo [startExpanded]. Tra bằng findViewWithTag từ decorView nên chạy trên CẢ hai
     * biến thể layout. Degrade-safe: thiếu tag (biến thể không có card đó) ⇒ no-op.
     */
    private fun wireCollapse(headerTag: String, bodyTag: String, startExpanded: Boolean) {
        val root = window.decorView
        val header = root.findViewWithTag<View>(headerTag) ?: return
        val body = root.findViewWithTag<View>(bodyTag) ?: return
        body.visibility = if (startExpanded) View.VISIBLE else View.GONE
        header.setOnClickListener {
            body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    /**
     * Option B: card "Biển báo tốc độ" là chức năng ĐỘC LẬP nhưng cần Cluster Cast ON để biển hiện trên cụm.
     * Cast OFF ⇒ mờ thân card + khoá control con + nhắc "cần Cast" ở dòng tóm tắt (sum_badge, đặt lúc chạy) —
     * KHÔNG ẩn cứng (owner: do NOT hard-hide). KHÔNG đụng runtime overlay badge. Dùng cùng castOn()-gating như
     * panel bóng VietMap. Gọi mỗi nhịp refresh().
     */
    private fun refreshBadgeCastGate() {
        val castOn = VmOverlayPosition.castOn(this)
        val root = window.decorView
        root.findViewWithTag<View>("body_badge")?.let { body ->
            body.alpha = if (castOn) 1f else 0.5f
            intArrayOf(
                R.id.switch_upcoming_badge, R.id.switch_alert_chip, R.id.btn_vietmap_widget_diag,
                R.id.seek_badge_size,
            ).forEach { id -> findViewById<View>(id)?.isEnabled = castOn }
        }
        (root.findViewWithTag<View>("sum_badge") as? TextView)?.text = if (castOn) {
            Lang.t("Biển báo tốc độ + cảnh báo trên cụm", "Speed sign + alerts on cluster")
        } else {
            Lang.t("⚠ Bật Cluster Cast để hiện trên cụm", "⚠ Turn on Cluster Cast to show on cluster")
        }
    }

    // ── Ghế: làm mát / sưởi tự động (spec seat-comfort-auto) ─────────────────────────────────────
    // Chế độ TOÀN CỤC (làm mát ↔ sưởi, loại trừ) + mỗi ghế 3 mức (Tắt / Mức 1 / Mức 2). Hiện 2 ghế (Seal)
    // hay 4 ghế (Han) theo mẫu xe. Mặc định TẮT. Nhãn song ngữ đặt lúc CHẠY qua Lang.t (KHÔNG thêm
    // strings.xml — file đó đang byte-seal). Detach listener trước khi khôi phục (như setupVmOverlayControls /
    // voice-key) để không bắn sự kiện giả. Áp HAL qua SeatComfortApplier (~5s sau start + nút "Áp dụng ngay").
    private fun setupSeatComfortControls() {
        findViewById<TextView>(R.id.txt_seat_comfort_title)?.text = Lang.t("Ghế: làm mát / sưởi", "Seats: cooling / heating")
        findViewById<TextView>(R.id.txt_seat_comfort_hint)?.text = Lang.t(
            "Tự áp dụng ~5 giây sau khi mở app / nổ máy. Làm mát và sưởi loại trừ nhau. Chạm ghế để chọn mức.",
            "Auto-applies ~5s after app open / boot. Cooling and heating are mutually exclusive. Tap a seat to set its level.",
        )

        // Master switch — detach trước khi khôi phục isChecked để không bắn sự kiện giả.
        findViewById<Switch>(R.id.switch_seat_comfort_enabled)?.apply {
            setOnCheckedChangeListener(null)
            text = Lang.t("Bật", "On")
            isChecked = Prefs.seatComfortEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Prefs.setSeatComfortEnabled(this@MainActivity, on)
                refreshSeatComfortPanel()
            }
        }

        // Sơ đồ ghế (chữ-ký, thay các hàng radio cũ) — seed số ghế theo mẫu xe + chế độ + mức từng ghế; chạm
        // ghế cycle 0→1→2. GIỮ NGUYÊN hợp đồng cũ: mỗi lần đổi mức ⇒ persist Prefs.seatComfortLevel + applyNow
        // (no-op nếu công tắc tắt). Off-car detectSeatCount → 2 ghế; getLevel/setLevel là thuần UI.
        val diagram = findViewById<com.byd.clusternav.comfort.SeatDiagramView>(R.id.seat_diagram)
        diagram?.apply {
            onSeatLevelChanged = null   // detach trước khi seed để không bắn callback giả
            setSeatCount(SeatComfortApplier.detectSeatCount(this@MainActivity))
            setMode(Prefs.seatComfortMode(this@MainActivity) != SeatComfort.SeatMode.HEAT.ordinal)   // true=Làm mát
            for (i in 0..3) setLevel(i, Prefs.seatComfortLevel(this@MainActivity, i))
            onSeatLevelChanged = { seat, level ->
                Prefs.setSeatComfortLevel(this@MainActivity, seat, level)
                // Auto-apply đổi mức 1 ghế ⇒ ghi HAL NGAY cho CHÍNH ghế đó, kể cả Tắt (state=1). applySeat tự
                // no-op nếu công tắc tắt. (Trước v1.34 gọi applyNow → đường bulk bỏ qua mức Tắt ⇒ không tắt được.)
                SeatComfortApplier.applySeat(this@MainActivity, seat, level)
            }
        }

        // Chế độ toàn cục (làm mát ↔ sưởi) — Level-2 (ui-visual-upgrade-l2): SegmentedControlView 2 đoạn loại
        // trừ (warm=true ⇒ đoạn chọn dùng gradient hồng→hổ phách) THAY RadioGroup; đổi chế độ → tô lại diagram +
        // persist + apply. selectedIndex seed từ pref KHÔNG nổ onSelected (chỉ USER chạm mới nổ) — không echo giả.
        findViewById<com.byd.clusternav.ui.SegmentedControlView>(R.id.seg_seat_mode)?.apply {
            warm = true
            setOptions(listOf(Lang.t("Làm mát", "Cool"), Lang.t("Sưởi", "Heat")))
            selectedIndex = if (Prefs.seatComfortMode(this@MainActivity) == SeatComfort.SeatMode.HEAT.ordinal) 1 else 0
            onSelected = { idx ->
                val mode = if (idx == 1) SeatComfort.SeatMode.HEAT.ordinal else SeatComfort.SeatMode.COOL.ordinal
                Prefs.setSeatComfortMode(this@MainActivity, mode)
                diagram?.setMode(mode != SeatComfort.SeatMode.HEAT.ordinal)   // tô lại mát(cyan)/sưởi(amber) tức thì
                // Auto-apply (nút "Áp dụng ngay" đã bỏ): đổi chế độ ⇒ ghi HAL ngay. applyNow tự no-op nếu tắt.
                SeatComfortApplier.applyNow(this@MainActivity)
            }
        }

        // Nút "Áp dụng ngay" ĐÃ BỎ (Option B · owner: "chỉnh xong là lưu, nút Áp dụng vô nghĩa") — thay bằng
        // auto-apply ngay trong callback đổi mức / đổi chế độ ở trên (SeatComfortApplier.applyNow, no-op nếu tắt).
        // Số ghế 2 (Seal) / 4 (Han) do seat_diagram.setSeatCount lo (detectSeatCount ở trên) — không còn seat_rear_row.

        refreshSeatComfortPanel()
    }

    /** Mờ + khoá diagram + chế độ khi công tắc chính TẮT (SeatDiagramView tự vẽ mờ + chặn chạm khi !isEnabled). */
    private fun refreshSeatComfortPanel() {
        val on = Prefs.seatComfortEnabled(this)
        intArrayOf(R.id.seat_diagram, R.id.seg_seat_mode)
            .forEach { id -> findViewById<View>(id)?.isEnabled = on }
    }

    // ── Tự lọc bụi mịn PM2.5 (spec pm25-auto-filter) ─────────────────────────────────────────────
    // MỘT công tắc + nhãn mức bụi hiện tại (đơn giản — KHÔNG chọn ngưỡng, KHÔNG option thừa). BẬT →
    // Pm25FilterApplier bật lọc-liên-tục KHÔNG popup (nền); TẮT → khôi phục. Nhãn song ngữ đặt lúc CHẠY qua
    // Lang.t. Detach listener TRƯỚC khi khôi phục isChecked (như voice-key / ghế) để không bắn sự kiện giả.
    private fun setupPm25FilterControls() {
        findViewById<TextView>(R.id.txt_pm25_title)?.text = Lang.t("Tự lọc bụi mịn", "Auto fine-dust filter")

        findViewById<Switch>(R.id.switch_pm25_filter)?.apply {
            setOnCheckedChangeListener(null)
            text = Lang.t("Bật", "On")
            isChecked = Prefs.pm25FilterEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Prefs.setPm25FilterEnabled(this@MainActivity, on)
                if (on) Pm25FilterApplier.enable(this@MainActivity) else Pm25FilterApplier.disable(this@MainActivity)
                refreshPm25Level()
            }
        }

        // Nút "Lọc ngay" — lọc-ngay chủ động (setQuickCleanAirState) NGAY, bất kể công tắc auto (owner yêu cầu
        // 2026-09-08: cần nhấn lọc luôn; đồng thời vá "popup hiện mà không auto-lọc" — quick-clean mới lọc thật).
        findViewById<Button>(R.id.btn_pm25_clean_now)?.apply {
            text = Lang.t("Lọc ngay", "Clean now")
            setOnClickListener {
                Pm25FilterApplier.cleanNow(this@MainActivity)
                Toast.makeText(this@MainActivity, Lang.t("Đang lọc bụi mịn…", "Cleaning the air…"), Toast.LENGTH_SHORT).show()
                refreshPm25Level()
            }
        }

        refreshPm25Level()
    }

    /**
     * Đọc mức PM2.5 trên thread NỀN (HAL reflection — không dùng ở main) rồi post nhãn song ngữ về
     * <code>txt_pm25_level</code>. INVALID / off-car → nhãn "—" (levelLabel tự trả "—" cho mức không rõ).
     */
    private fun refreshPm25Level() {
        val tv = findViewById<TextView>(R.id.txt_pm25_level)
        val gauge = findViewById<com.byd.clusternav.comfort.Pm25GaugeView>(R.id.pm25_gauge)
        if (tv == null && gauge == null) return
        Thread({
            val level = Pm25FilterApplier.readLevel(this)
            val label = Lang.t(Pm25Filter.levelLabelVi(level), Pm25Filter.levelLabelEn(level))
            ui.post {
                tv?.text = Lang.t("Mức bụi hiện tại: ", "Current dust level: ") + label
                gauge?.setLevel(level)   // INVALID / off-car → cung rỗng + "—" (degrade-safe)
            }
        }, "pm25-read-level").start()
    }

    /**
     * Ngôn ngữ / Language selector (nhóm Hệ thống). Lựa chọn 3-cách [Lang.Choice]: Theo xe (AUTO — theo locale
     * máy/xe) · VI · EN. Seed [com.byd.clusternav.ui.SegmentedControlView.selectedIndex] từ lựa chọn đã lưu MÀ
     * KHÔNG bắn onSelected (setter không echo — chỉ chạm tay mới bắn), rồi cài onSelected: lưu qua
     * [Lang.setChoice] + gọi [recreate] để dựng lại toàn UI + localize lại theo ngôn ngữ mới. Cùng khuôn
     * seg_cluster_mode / seg_seat_mode. Degrade-safe: no-op nếu view vắng (parity đảm bảo có ở cả hai biến thể).
     */
    private fun setupLanguageSelector() {
        findViewById<com.byd.clusternav.ui.SegmentedControlView>(R.id.seg_language)?.apply {
            setOptions(listOf(Lang.t("Theo xe", "System"), "VI", "EN"))
            selectedIndex = when (Lang.choice(this@MainActivity)) {
                Lang.Choice.AUTO -> 0
                Lang.Choice.VI -> 1
                Lang.Choice.EN -> 2
            }
            onSelected = { idx ->
                val choice = when (idx) {
                    1 -> Lang.Choice.VI
                    2 -> Lang.Choice.EN
                    else -> Lang.Choice.AUTO
                }
                Lang.setChoice(this@MainActivity, choice)
                recreate()
            }
        }
    }

    /**
     * Giao diện / Theme selector (nhóm Hệ thống, ngay dưới Ngôn ngữ). Seed [SegmentedControlView.selectedIndex]
     * từ [ThemeMode.choice] (SYSTEM=0 / LIGHT=1 / DARK=2) — setter KHÔNG bắn onSelected nên seed không tạo
     * sự kiện giả — RỒI mới cài [SegmentedControlView.onSelected] → [ThemeMode.setChoice] + [recreate] để cả
     * Activity attachBaseContext lại và resolve values/ (LIGHT) hoặc values-night/ (DARK). Nhãn đoạn đặt qua
     * [Lang.t] nên tự theo ngôn ngữ; các custom View đọc @color trong init nên bản dựng lại lấy đúng mode mới.
     */
    private fun setupThemeSelector() {
        findViewById<com.byd.clusternav.ui.SegmentedControlView>(R.id.seg_theme)?.apply {
            setOptions(listOf(Lang.t("Theo xe", "System"), Lang.t("Sáng", "Light"), Lang.t("Tối", "Dark")))
            selectedIndex = when (ThemeMode.choice(this@MainActivity)) {
                ThemeMode.Choice.SYSTEM -> 0
                ThemeMode.Choice.LIGHT -> 1
                ThemeMode.Choice.DARK -> 2
            }
            onSelected = { idx ->
                val choice = when (idx) {
                    1 -> ThemeMode.Choice.LIGHT
                    2 -> ThemeMode.Choice.DARK
                    else -> ThemeMode.Choice.SYSTEM
                }
                ThemeMode.setChoice(this@MainActivity, choice)
                recreate()
            }
        }
    }

    private fun maybeAutoStartVietMap() {
        // Autostart VietMap là thao tác NỀN (ClusterNav vốn chạy headless — headlessAutostart/BootSetupService).
        // dadb là cách DUY NHẤT launch app khác từ nền (startActivity-từ-nền bị Android 10 chặn BAL). Phân biệt
        // cast-active vs silent-bg + gate (cast-default / badge / bóng) nằm trong VietMapAutostart.runNow. Chạy
        // trong FGS RIÊNG ([VietMapAutostartService]) để độc lập với vòng đời Activity + không block; case MỞ APP
        // → trả ClusterNav lên trước sau khi VietMap vào map.
        VietMapAutostartService.startForAppOpen(this)
    }
}
