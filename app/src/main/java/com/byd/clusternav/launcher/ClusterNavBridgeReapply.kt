package com.byd.clusternav.launcher

import android.util.Log
import com.byd.clusternav.AppPrereqs
import com.byd.clusternav.NavRepository
import com.byd.clusternav.Prefs
import com.byd.clusternav.VmOverlayPosition
import com.byd.clusternav.automation.AutomationService
import com.byd.clusternav.comfort.Pm25FilterApplier
import com.byd.clusternav.comfort.SeatComfortApplier
import com.byd.clusternav.launcher.voice.VoiceWakeService

/**
 * ═══ S4 · R5 — ÁP LẠI cấu hình ClusterNav cho dịch vụ ĐANG CHẠY, sau một lượt đổi hồ sơ ═══════════════════════
 *
 * Hàm mở rộng của [ClusterNavBridge] ở tệp riêng vì tệp chính đã 491 dòng (trần 500 — CLAUDE.md §4.1), cùng cách
 * `ClusterNavBridgeCast.kt` / `ClusterNavBridgeKeys.kt` đã tách.
 *
 * ## ⚠⚠ Vì sao BẮT BUỘC phải có bước này
 * `WorkspacePrefsProfile.applyClusterNav` ghi giá trị của hồ sơ mới vào đúng tệp `SharedPreferences` mà runtime
 * ClusterNav đang đọc — nhưng [ĐO] **không một chỗ nào trong dự án đăng ký
 * `registerOnSharedPreferenceChangeListener`** (grep `app/src/main`, `core/`, `car-integration/`,
 * `vehicle-contracts/`, `offcar-planner/` — 0 kết quả; hit duy nhất là một fake trong
 * `app/src/test/.../VoiceKeyBindingMigrationTest.kt:56`). Nghĩa là ghi prefs xong thì **trên đĩa đã đúng mà không
 * có gì đang chạy biết** — biển báo vẫn cỡ cũ, ghế vẫn mức cũ, lọc bụi vẫn chạy theo hồ sơ trước.
 *
 * ## Ba loại khoá, và vì sao chỉ MỘT loại cần gọi lại
 * [ĐO] đọc từng consumer:
 *  1. **Tự áp** — consumer đọc lại khoá ở **mỗi sự kiện** (mỗi thông báo dẫn đường, mỗi khung HUD, mỗi lần bấm
 *     phím vô-lăng, mỗi lượt dispatch cast). Chúng tự đúng ở nhịp kế tiếp, gọi thêm chỉ là nhiễu:
 *     `marquee` (`ClusterBroadcaster.kt:129`, mỗi khung), `voicekey_enabled`/`voicekey_bindings`
 *     (`modules/navaccess/NavAccessibilityService.kt:91,95`, mỗi phím),
 *     `cast_bubble_visible` (vòng 2 s `FloatingBubbleService.syncBubbleWindow` — ẩn/hiện cửa sổ, KHÔNG dựng lại dịch
 *     vụ), 6 khoá camera sở thích (`CameraSignalController.openSession` đọc lại ở MỖI lượt xi-nhan; riêng
 *     `camera_signal_enabled` còn cần [AutomationService.sync], xem thân hàm).
 *     ⚠ V-CLUSTER (2026-09-30): `split_ratio_left_pct` + họ `config_*` (DPI/khung từng app) KHÔNG còn "tự áp giữa
 *     phiên" — phiên chiếu GHIM chúng lúc bắt đầu (`CastSessionPin.kt`), nên giá trị của hồ sơ mới có hiệu lực ở
 *     **lượt chiếu kế** (sửa refute C2/B6: trước đây repin + ô thứ hai đọc lại prefs ⇒ hình học của hồ sơ mới tự nổ
 *     lên cụm giữa chuyến).
 *  2. **Có applier sống** — phải GỌI LẠI, và đó là toàn bộ nội dung của [reapplyAll] dưới đây.
 *  3. **Chỉ đọc lúc khởi động / lúc dựng màn** — không có gì để gọi, và cố gọi là **đổi nghĩa của khoá**. Danh
 *     sách + lý do ở KDoc từng dòng bị bỏ qua, cuối hàm.
 *
 * ## Luật CLAUDE.md §6 áp ở đây
 * *Đường mới luôn xuống cuối, không đảo thứ tự/cơ chế đang chạy tốt trên xe.* Hàm này **không** dựng applier mới,
 * không đổi thứ tự bên trong applier nào: nó chỉ gọi đúng những hàm mà các `bridge.set*` tương ứng vẫn gọi.
 */
internal fun ClusterNavBridge.reapplyAll() {
    // ── Dẫn đường + HUD ─────────────────────────────────────────────────────────────────────────
    // `enabled`: khúc ÁP của `setNavEnabled` (`ClusterNavBridge.kt:99` → `NavigationSpeedSignOwner.kt:44`).
    // ⚠ CỐ Ý bỏ khúc XIN QUYỀN của hàm đó (`NavConnect.selfGrant` / `grantAccessibility`): chúng đi dadb, chờ tới
    // ~31 s và có thể bật hộp thoại "Allow USB debugging" — chấp nhận được khi người dùng vừa gạt công tắc dẫn
    // đường, KHÔNG chấp nhận được khi họ chỉ đổi hồ sơ trên xe đang chạy. Cổng thông báo thì vốn đã tự áp
    // (`NavNotificationListener.kt:167` đọc lại mỗi thông báo).
    step("nav.master") { speedSign.onMasterEnabled(Prefs.enabled(app)) }
    // `nav_cluster_screen_mode`: đúng applier của `setClusterMode` (`ClusterNavBridge.kt:133`) — ép CLEAR rồi đẩy
    // lại NGAY thay vì chờ khung kế bị dedup nuốt (`NavigationHudOwner.kt:267`).
    step("nav.clusterMode") { NavRepository.reapplyClusterMode(app) }

    // ── Biển báo tốc độ (lớp phủ dùng chung trên cụm) ───────────────────────────────────────────
    // Bốn applier của bốn `bridge.set*` tương ứng (`ClusterNavBridge.kt:247,256,265,278`).
    // ⚠ CỐ Ý bỏ `VietMapAutostartService.startForAppOpen` mà `setBadgeEnabled` gọi khi BẬT: nó **mở một app**.
    // Đổi hồ sơ mà tự bung VietMap lên màn xe đang chạy là hành vi không ai yêu cầu.
    step("badge.enabled") { speedSign.onBadgeEnabledChanged() }
    step("badge.upcoming") { speedSign.onUpcomingBadgeEnabledChanged() }
    step("badge.alertChip") { speedSign.onAlertChipEnabledChanged() }
    // Một lời gọi cho CẢ `badge_size_dp` + `badge_center_x/y` — đúng như `setBadgeSizeDp`/`setBadgeCenter` làm.
    step("badge.layout") { speedSign.debugRefreshBadgeLayout() }

    // ── Bong bóng VietMap trên cụm ──────────────────────────────────────────────────────────────
    // `vm_bubble_x/y`: `VmOverlayPosition` vừa lưu vừa **bắn broadcast** cho mod VietMap trong cùng một hàm, nên
    // `applyOnOpen` (`VmOverlayPosition.kt:86`) là đúng đường phát lại — nó tự no-op khi Cast chưa live.
    step("bubble.pos") { VmOverlayPosition.applyOnOpen(app) }
    // 2.90 · R8 — `vm_bubble_hidden` của HỒ SƠ MỚI ⇒ ẩn/hiện bóng thật (`VM_BUBBLE_VIS`), không gate Cast ON (bóng nằm trên cụm cả khi tắt chiếu).
    step("bubble.vis") { com.byd.clusternav.VmBubbleVisibility.apply(app, "profile-reapply", force = true) }
    // Review 2.89 Pass 3 · vietmap-dock-r2-2 — điều kiện nền (miễn pin · appop vẽ nổi) theo HỒ SƠ MỚI: `vm_bubble_enabled` /
    // biển tốc độ / app tự chiếu theo hồ sơ, và lượt SẴN của hồ sơ trước có thể đã TRẢ appop vẽ nổi của VietMap (r1-2). Không
    // gọi lại thì hồ sơ bật bóng mất bóng tới lần màn sáng / nổ máy / mở chiếu kế. `onReady` = lượt nền (luồng riêng, KHÔNG mở
    // app nào — khác `VietMapAutostartService` bị cố ý bỏ ở trên): đọc → áp phần thiếu cho phạm vi mới → trả dấu đã rời phạm vi.
    step("app.prereqs") { AppPrereqs.onReady(app) }

    // ── Tiện nghi xe ────────────────────────────────────────────────────────────────────────────
    // `seat_comfort_mode` + `seat_level_*`: applier của `setSeatMode` (`ClusterNavBridge.kt:380`). Nó **tự gate**
    // theo `seat_comfort_enabled` bên trong (`SeatComfortApplier.kt:56`) nên công tắc chính cũng được tôn trọng.
    step("seat") { SeatComfortApplier.applyNow(app) }
    // `pm25_filter_enabled`: đúng cặp applier của `setPm25Enabled` (`ClusterNavBridge.kt:415`). Phải gọi cả nhánh
    // TẮT: vòng lọc là một thread đang chạy, không tắt thì nó lọc tiếp theo cấu hình của hồ sơ vừa rời.
    step("pm25") { if (Prefs.pm25FilterEnabled(app)) Pm25FilterApplier.enable(app) else Pm25FilterApplier.disable(app) }

    // ── Tự động hoá (camera theo xi-nhan · luật dẫn đường theo lịch) ─────────────────────────────
    // V-CLUSTER A3: `camera_signal_enabled` (theo hồ sơ từ 2026-09-30) và `nav_automation_rules` (theo hồ sơ từ
    // 2026-09-28) quyết việc FGS tự động hoá có sống không. Đổi hồ sơ mà không đồng bộ thì hồ sơ B tắt camera vẫn để
    // engine của A chạy (và ngược lại: B bật mà FGS đang dừng thì camera câm tới lần khởi động kế). Đúng hàm mà
    // `setCameraSignal`/`setRainDefrost*` gọi sau khi ghi — idempotent, tự gác theo `anyEnabled`, không ném.
    step("automation.sync") { AutomationService.sync(app) }

    // ── Giọng nói: chế độ `:wake` (FIX286 · VK2/VK4) ─────────────────────────────────────────────────
    // `voicekey_bindings` · `voicekey_enabled` · `voice_music_default_app` theo HỒ SƠ ⇒ đổi hồ sơ đổi `keyHold` (hồ sơ B
    // gán phím Kachi nghe, A thì không) và đổi prefs mà `:wake` đọc. `sync` công bố ảnh chụp tươi rồi bật/tắt FGS theo
    // chế độ — đúng hàm mà công tắc/gán phím gọi; idempotent (đang chạy đúng chế độ ⇒ chỉ một lượt `onStartCommand`).
    step("voice.wake") { VoiceWakeService.sync(app) }

    // ── ⚠ CỐ Ý KHÔNG gọi lại — mỗi dòng là một quyết định, không phải một chỗ quên ───────────────
    //
    //  • `recirc_on_start_enabled` — nghĩa của khoá là *"lấy gió trong khi NỔ MÁY"* (`RecircApplier.applyOnStart`,
    //    đọc một lần ở `BootSetupService.kt:97`). `applyNowAsync` thì **KHÔNG gate** theo công tắc
    //    (`RecircApplier.kt:56-58`) nên gọi nó ở đây là bật quạt lấy gió trong mỗi lần đổi hồ sơ.
    //  • `headless_autostart` — chỉ rẽ nhánh một quyết định của `RebindReceiver` lúc nhận BOOT_COMPLETED
    //    (`RebindReceiver.kt:42,64`). Không có dịch vụ nào đang chạy để báo.
    //  • `autostart_enabled` · `autostart_package` · `autostart_split_enabled` · `autostart_left_package` ·
    //    `autostart_right_package` — hai cờ đọc ở `FloatingBubbleService.onCreate`, tên gói đọc khi phiên tới Idle
    //    (`modules/clustercast/BubbleAutostart.kt:76-117`). "Tự chiếu **khi nổ máy**" mà áp ngay lúc đổi hồ sơ là
    //    bung một app lên cụm của xe đang chạy.
    //  • `cast_enabled` — theo HỒ SƠ từ V-CLUSTER (owner 2026-09-30) nhưng lượt áp ảnh chụp KHÔNG BAO GIỜ ghi khoá sống:
    //    giá trị của hồ sơ đợi ở `cast_enabled_pending` (`ClusterSnapshotPlan` + `CastEnableDeferral`) và được chốt ở
    //    `SimpleCastRuntime.create` của tiến trình kế (≈ lần nổ máy kế). Lý do của chốt S4-OQ2 cũ vẫn đúng: mọi cổng
    //    đọc đều live (`ClusterNavLaneWidget.kt:110` · `NavRepository.kt:215` · `FloatingBubbleService.kt:152,313`)
    //    nên đổi giá trị giữa phiên là cụm hai chủ; còn gọi `setCastEnabled` ở đây thì làm cụm trước mặt người lái tối
    //    đi/sáng lên vì một cú chạm chip. Người lái muốn áp ngay ⇒ nút *Áp ngay* ở Cài đặt › Chiếu cụm (đường thật).
    //  • `split_ratio_left_pct` · họ `config_*` — lượt chiếu kế (bản GHIM của phiên, xem đầu tệp). CẤM gọi
    //    `applySplitRatioLive`/`applyPinned`/`resize*`/`setDensity*` ở đây: đó là lệnh `am`/`wm` lên cụm (VC-R5).
    //  • `bubbleX`/`bubbleY` (tệp `cast-v2-app-catalog`) — lần dựng cửa sổ nút nổi kế (`FloatingBubbleService.showBubble`,
    //    kẹp theo màn). Dời cửa sổ đang hiện có thể đánh nhau với một lượt kéo đang dở (spec OQ-VC1).
    //  • `theme_choice` — `ThemeMode.setChoice` đọc ở `attachBaseContext`, và KDoc của nó
    //    (`ThemeMode.kt:47`) nói rõ *"caller chịu trách nhiệm recreate Activity đang hiện"*. Màn ClusterNav đang
    //    mở là ca hiếm (người dùng đang ở màn chính để chạm chip hồ sơ); lần mở sau đã đúng.
    //  • `voicekey_custom_buttons` — [ĐO] không có consumer sống: chỉ `ClusterNavBridgeKeys.kt:159,162` đọc để đổ
    //    danh sách trong màn Cài đặt.
    //
    // (Chú thích cũ *"`seat_level_1..3` KHÔNG theo hồ sơ"* đã hết đúng từ S4-SEAT 2026-09-23: ba ghế đã khai ở
    // `SettingsCatalogClusterNav.KEYS` nên vào ảnh chụp, và `SeatComfortApplier.applyNow` ở trên áp cả bốn.)
}

/**
 * Chạy một applier, **ghi log khi nó ném**, và đi tiếp.
 *
 * ## Vì sao bắt ở đây chứ không để nó nổ lên
 * Mọi applier trong [reapplyAll] đều chạm lớp ngoài app: HAL xe (ghế · lọc bụi), cửa sổ overlay (biển báo),
 * broadcast sang gói khác (bong bóng VietMap). Trên một chiếc xe thật chúng **được phép hỏng** — off-car, chưa
 * provision, quyền overlay vừa bị thu hồi. Để một cái ném ra ngoài thì `switchProfile` chết giữa chừng: hồ sơ đã
 * đổi trên đĩa, ảnh chụp đã áp, nhưng những applier **sau nó** không bao giờ chạy — tức người dùng nhận một nửa
 * cấu hình mà không ai nói gì.
 *
 * Đây KHÔNG phải `catch (e: Exception) {}` trần mà CLAUDE.md §4.1 cấm: mỗi lượt bắt có **tên** applier trong log,
 * nên một applier hỏng luôn truy được về đúng dòng ở đây (`adb logcat -s ClusterNavReapply`).
 */
private inline fun step(name: String, block: () -> Unit) {
    runCatching(block).onFailure { Log.w("ClusterNavReapply", "applier '$name' failed during profile switch", it) }
}
