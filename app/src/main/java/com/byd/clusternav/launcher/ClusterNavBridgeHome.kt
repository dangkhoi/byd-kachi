package com.byd.clusternav.launcher

import com.byd.clusternav.AdbKeys
import com.byd.clusternav.Prefs
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalSetHomeOutcome
import com.byd.clusternav.carexec.LocalShellFailure

/**
 * ═══ S5 — "MÀN HÌNH CHÍNH" trên cầu Settings (hàm mở rộng của [ClusterNavBridge]) ═════════════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` §9 (S5). Nằm ở tệp riêng dưới dạng **hàm mở rộng** — cùng khuôn
 * `ClusterNavBridgeCast.kt` / `ClusterNavBridgeKeys.kt`: giữ bề mặt phẳng `bridge.setDefaultHome(…)` mà tệp lõi
 * `ClusterNavBridge.kt` không vượt trần 500 dòng (CLAUDE.md §4.1).
 *
 * ## Vì sao "màn hình chính" ở trên CẦU chứ không ở đường launcher khác
 * Đặt HOME cần **kênh dadb uid-shell** — đúng thứ mà cầu đã sở hữu cho OTA (`LocalDeviceShell.installApk`) và cấp
 * quyền. Owner 2026-09-14: *"bấm home trên màn nó không hiện hộp chọn, cần option trong Setting cho user chọn,
 * chọn xong gọi adb set luôn"*. [ĐO] DiLink3.0: `cmd package set-home-activity <comp>` từ shell uid 2000 ⇒
 * `Success`, resolve-activity HOME → Kachi. Cầu là chỗ duy nhất Settings đọc/ghi những việc-cần-shell đó.
 *
 * ## Đọc thì KHÔNG shell (ràng buộc C4), đặt thì mới shell
 * [isDefaultHome]/[currentHomePackage] đọc qua [DefaultHome] (API `PackageManager`, mọi app đọc được) — không mở
 * kênh chỉ để đọc, cùng luật `PermissionPreflight`. Chỉ [setDefaultHome] mới chạy lệnh.
 */

/** Kachi có đang là màn hình chính không (ĐỌC thuần, không shell). Không đọc được ⇒ coi như chưa (`false`). */
fun ClusterNavBridge.isDefaultHome(): Boolean = DefaultHome.isCurrent(app) == true

/** Gói đang là màn hình chính (cho dòng "hệ thống đang dùng <gói>"), hoặc `null` nếu không phân giải được. */
fun ClusterNavBridge.currentHomePackage(): String? = DefaultHome.currentPackage(app)

/**
 * Đặt Kachi làm màn hình chính qua dadb uid-shell — chạy trên **thread NỀN** rồi post kết quả về luồng vẽ qua
 * [ClusterNavBridge.ui] (spec N5 — cùng khuôn `seatCount`/`pm25Level`, không chặn luồng chính).
 *
 * [onResult] nhận [LocalSetHomeOutcome] (sealed, khớp mẫu `LocalInstallOutcome`): tầng Settings tra câu song ngữ
 * từ `R.string` theo nhánh — cầu KHÔNG mang chữ (nó ở gói `launcher`, `LauncherI18nContractTest` cấm literal).
 * Ngoại lệ ngoài dự kiến (khoá adb hỏng…) ⇒ [LocalSetHomeOutcome.NoShellChannel] để UI nói đúng việc.
 */
fun ClusterNavBridge.setDefaultHome(onResult: (LocalSetHomeOutcome) -> Unit) {
    Thread({
        // BƯỚC 1 (2026-09-15, DuDu-style): BẬT lối vào HOME (alias tắt sẵn để GUI-install không bị chặn). Bật xong hệ
        // thống có ứng viên home mới ⇒ ROM có thể tự hiện hộp chọn launcher (owner [ĐO] DuDu). Không cần shell.
        // BƯỚC 2: `set-home-activity` nhắm alias — fallback tất định nếu ROM không hiện hộp chọn (KDoc cũ [ĐO] DL3).
        // Ghi marker `homeChosen` khi Ok để KachiAutostart re-apply sau nâng cấp (alias mới tắt sẵn ⇒ Home rơi về
        // launcher3 nếu không re-apply — đường trả lại theo CLAUDE.md §5).
        val outcome = runCatching {
            DefaultHome.enableHomeEntry(app)
            LocalDeviceShell.setHomeActivity(AdbKeys.ensure(app), DefaultHome.component(app))
        }.getOrElse { LocalSetHomeOutcome.NoShellChannel(LocalShellFailure.UNKNOWN) }
        if (outcome is LocalSetHomeOutcome.Ok) runCatching { WorkspacePrefs(app).setHomeChosen(true) }
        ui(Runnable { onResult(outcome) })
    }, "bridge-set-home").start()
}

/**
 * "Giữ Kachi làm màn hình chính khi nổ máy" — theo XE ([ProfileScope.DEVICE_KEYS]), lưu ở [WorkspacePrefs].
 *
 * Ở trên cầu cạnh [setDefaultHome] vì cùng một việc ("Kachi là HOME"): công tắc quyết định đường **khởi động nguội**
 * ([com.byd.clusternav.KachiAutostart]) có đặt lại HOME một lần hay không. Mặc định TẮT — xem KDoc
 * [WorkspacePrefs.keepHomeOnBoot].
 */
fun ClusterNavBridge.keepHomeOnBoot(): Boolean = WorkspacePrefs(app).keepHomeOnBoot()

/** Xem [keepHomeOnBoot]. */
fun ClusterNavBridge.setKeepHomeOnBoot(on: Boolean) = WorkspacePrefs(app).setKeepHomeOnBoot(on)

/**
 * BỎ chọn Kachi làm màn hình chính — TRẢ quyền HOME cho launcher khác (owner 2026-09-18: *"bỏ chọn Kachi làm
 * launcher → không trả về launcher mặc định mà vẫn keep Kachi"*). Ba việc, ĐÚNG thứ tự:
 *
 *  1. **XOÁ hai marker** `homeChosen` + `keepHomeOnBoot` TRƯỚC. Đây là gốc lỗi "vẫn keep Kachi": `KachiAutostart`
 *     re-assert Kachi làm HOME mỗi lần nổ máy khi `keepHomeOnBoot() || homeChosen()`, mà `homeChosen` set một lần
 *     rồi không bao giờ xoá ⇒ bỏ chọn kiểu gì boot sau cũng bị giành lại. Không xoá hai cờ này thì mọi bước sau vô ích.
 *  2. **TẮT alias HOME** ⇒ Kachi thôi là ứng viên HOME (không cần shell).
 *  3. **`set-home-activity <launcher khác>`** (nếu tìm được) — Android chỉ SET được HOME, phải chỉ đích launcher
 *     stock để hệ chuyển sang. Không có launcher khác ⇒ bước 1+2 vẫn đủ để hệ tự phân giải lại (báo [LocalSetHomeOutcome.Failed]).
 *
 * [onResult] trên luồng vẽ: Ok = đã trỏ sang launcher khác; NoShellChannel = xoá cờ+tắt alias xong nhưng không
 * set được đích (thiếu kênh ADB); Failed = không có launcher khác (vẫn đã bỏ Kachi khỏi HOME).
 */
fun ClusterNavBridge.clearDefaultHome(onResult: (LocalSetHomeOutcome) -> Unit) {
    Thread({
        runCatching { WorkspacePrefs(app).apply { setHomeChosen(false); setKeepHomeOnBoot(false) } }
        DefaultHome.disableHomeEntry(app)
        val other = DefaultHome.otherHomeComponent(app)
        val outcome = when {
            other == null -> LocalSetHomeOutcome.Failed("no-other-home")
            else -> runCatching { LocalDeviceShell.setHomeActivity(AdbKeys.ensure(app), other) }
                .getOrElse { LocalSetHomeOutcome.NoShellChannel(LocalShellFailure.UNKNOWN) }
        }
        ui(Runnable { onResult(outcome) })
    }, "bridge-clear-home").start()
}

/**
 * UX-OVERHAUL WP1 · R1.3 — công tắc **"Kính thật (làm mờ nền)"** (`ui_glass_real`).
 *
 * Ở đây, không ở `ClusterNavBridge.kt`: tệp đó đã **499 dòng** trước WP1 (trần 500 — CLAUDE.md §4.1), nên thêm bất
 * cứ gì vào nó là vượt trần. Cùng khuôn mọi `ClusterNavBridge*` khác: màn Cài đặt không ghi `Prefs.set` trực tiếp
 * (`SettingsScreenWiringContractTest` cấm), mọi state bền đi qua một cửa.
 *
 * Theo XE, mặc định TẮT; chỉ thấy được ở API ≥ 31 ([KachiGlassMode]) nên **trên xe DiLink (API 29) nó luôn lùi về
 * glass GIẢ**; áp ở lượt dựng màn kế tiếp.
 */
fun ClusterNavBridge.glassReal(): Boolean = Prefs.glassReal(app)

/** Xem [glassReal]. */
fun ClusterNavBridge.setGlassReal(on: Boolean) = Prefs.setGlassReal(app, on)
