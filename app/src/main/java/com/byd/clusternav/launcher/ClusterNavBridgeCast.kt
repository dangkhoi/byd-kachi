package com.byd.clusternav.launcher

import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.byd.clusternav.modules.clustercast.CastDeepRescueAction
import com.byd.clusternav.modules.clustercast.DisplayParse
import com.byd.clusternav.modules.clustercast.FloatingBubbleService
import com.byd.clusternav.modules.clustercast.simplified.AppMover
import com.byd.clusternav.modules.clustercast.simplified.CastProfile
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastIntent
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.system.PackageQueries

/**
 * ═══ Nửa "Chiếu màn lên cụm" của [ClusterNavBridge] ═════════════════════════════════════════════
 *
 * Tách tệp vì trần 500 dòng (CLAUDE.md §4.1 · spec N1), nhưng viết dưới dạng **hàm mở rộng của chính
 * [ClusterNavBridge]** để bề mặt vẫn phẳng (`bridge.castFull(pkg)`) — Kotlin không có partial class,
 * còn tách thành lớp con thì section phải biết hai vật, trái với N2 ("một cầu duy nhất").
 *
 * Mọi hàm lặp lại `CastEnableSwitch.kt` / `CastSplitRatioButtons.kt` / `CastAutostart.kt` /
 * `MainActivityCastController.kt` — có ghi dòng gốc ở từng KDoc.
 */

/** Coordinator process-singleton — `MainActivityCastController.kt:57`. */
internal val ClusterNavBridge.coordinator: SimpleCastCoordinator
    get() = SimpleCastRuntime.coordinator(app)

// ── Công tắc chính "Bật Cluster Cast" — lặp lại CastEnableSwitch.kt:44–80 ────────────────────────

/** `CastEnableSwitch.kt:45`. */
fun ClusterNavBridge.castEnabled(): Boolean = coordinator.prefs.castEnabled()

/**
 * Lặp lại `CastEnableSwitch.kt:49–53` + `enable()`/`disable()` (dòng 62–80) NGUYÊN thứ tự:
 *  - **BẬT** → persist → `openProjection()` (idempotent) → `startForegroundService(FloatingBubbleService)`
 *    → toast "Đã bật Cluster Cast".
 *  - **TẮT** → persist → `dispatch(Stop())` → `closeProjection()` → `stopService(...)` → toast.
 *    "Đứng xuống và KHÔNG mở lại" — đây là lựa chọn của người dùng, nên KHÔNG force-stop ai như
 *    `CastDeepRescueAction` (xem [deepRescue]).
 *
 * Ba lời gọi shell tự xếp hàng trên executor nối tiếp của coordinator ⇒ không chạy trên luồng vẽ.
 */
fun ClusterNavBridge.setCastEnabled(on: Boolean) {
    coordinator.prefs.setCastEnabled(on)
    if (on) {
        coordinator.openProjection()
        runCatching { app.startForegroundService(Intent(app, FloatingBubbleService::class.java)) }
        toast(BridgeMsg.CAST_ON)
    } else {
        runCatching { coordinator.dispatch(SimpleCastIntent.Stop()) }
        runCatching { coordinator.closeProjection() }
        runCatching { app.stopService(Intent(app, FloatingBubbleService::class.java)) }
        toast(BridgeMsg.CAST_OFF)
    }
}

// ── Tỉ lệ chia đôi — lặp lại CastSplitRatioButtons.kt:41–66 ─────────────────────────────────────

/** Phần trăm của nửa TRÁI (10…90) — `CastSplitRatioButtons.kt:62`. */
fun ClusterNavBridge.splitPct(): Int = CastProfile.normalizePercent(
    runCatching { coordinator.prefs.splitRatioLeftPercent() }.getOrDefault(CastProfile.DEFAULT_PERCENT),
)

/**
 * Lặp lại `CastSplitRatioButtons.kt:52–55`: một lời gọi [SimpleCastCoordinator.applySplitRatioLive]
 * vừa persist vào prefs SỐNG (bản mà cast đang chạy đọc) vừa resize tại chỗ cả hai ô nếu cụm đang
 * chia đôi — KHÔNG phải store v2 cũ đã mất kết nối.
 */
fun ClusterNavBridge.setSplitPct(pct: Int) {
    runCatching { coordinator.applySplitRatioLive(CastProfile.normalizePercent(pct)) }
}

/** Dải tỉ lệ hợp lệ (1:9 … 9:1) — nguồn duy nhất là `CastProfile.SPLIT_PERCENTS`. */
fun ClusterNavBridge.splitPctOptions(): List<Int> = CastProfile.SPLIT_PERCENTS.toList()

// ── Tự chiếu khi nổ máy — lặp lại CastAutostart.kt:31–57 ────────────────────────────────────────

/** `CastAutostart.kt:33`. */
fun ClusterNavBridge.autostartFull(): Boolean = coordinator.prefs.autoStartEnabled()

/**
 * Lặp lại `CastAutostart.kt:34–40`: bật "tự chiếu FULL" thì **tắt** "tự chiếu chia đôi" (hai chế độ
 * loại trừ nhau — hai driver cùng chạy từng gây đua SLOT_OCCUPIED, R1/T1).
 */
fun ClusterNavBridge.setAutostartFull(on: Boolean) {
    coordinator.prefs.setAutoStartEnabled(on)
    if (on) coordinator.prefs.setAutoStartSplitEnabled(false)
}

/** `CastAutostart.kt:110`. */
fun ClusterNavBridge.autostartPkg(): String? = coordinator.prefs.autoStartPackage()

/** `CastAutostart.kt:120` (null = "— Chọn app —"). */
fun ClusterNavBridge.setAutostartPkg(pkg: String?) = coordinator.prefs.setAutoStartPackage(pkg)

/** `CastAutostart.kt:46`. */
fun ClusterNavBridge.autostartSplit(): Boolean = coordinator.prefs.autoStartSplitEnabled()

/** Lặp lại `CastAutostart.kt:47–53` — nghịch đảo của [setAutostartFull]. */
fun ClusterNavBridge.setAutostartSplit(on: Boolean) {
    coordinator.prefs.setAutoStartSplitEnabled(on)
    if (on) coordinator.prefs.setAutoStartEnabled(false)
}

/** `CastAutostart.kt:108`. */
fun ClusterNavBridge.autostartLeftPkg(): String? = coordinator.prefs.autoStartLeftPackage()

/** `CastAutostart.kt:118`. */
fun ClusterNavBridge.setAutostartLeftPkg(pkg: String?) = coordinator.prefs.setAutoStartLeftPackage(pkg)

/** `CastAutostart.kt:109`. */
fun ClusterNavBridge.autostartRightPkg(): String? = coordinator.prefs.autoStartRightPackage()

/** `CastAutostart.kt:119`. */
fun ClusterNavBridge.setAutostartRightPkg(pkg: String?) = coordinator.prefs.setAutoStartRightPackage(pkg)

// ── Hành động chiếu — lặp lại MainActivityCastController.kt:157–218 ─────────────────────────────

/** Chiếu FULL cụm — `MainActivityCastController.kt:210` (`AppMover.classifyApp` chọn hồ sơ hiển thị). */
fun ClusterNavBridge.castFull(pkg: String) {
    coordinator.dispatch(SimpleCastIntent.CastFull(pkg, AppMover.classifyApp(pkg)))
}

/** Chiếu nửa TRÁI — `MainActivityCastController.kt:212`. */
fun ClusterNavBridge.castLeft(pkg: String) {
    coordinator.dispatch(SimpleCastIntent.CastSlot(pkg, ClusterSlotSide.LEFT))
}

/** Chiếu nửa PHẢI — `MainActivityCastController.kt:212`. */
fun ClusterNavBridge.castRight(pkg: String) {
    coordinator.dispatch(SimpleCastIntent.CastSlot(pkg, ClusterSlotSide.RIGHT))
}

/** Dừng chiếu (cả cụm, hoặc một nửa nếu truyền [slot]) — `MainActivityCastController.kt:188–190`. */
fun ClusterNavBridge.castStop(slot: ClusterSlotSide? = null) {
    coordinator.dispatch(SimpleCastIntent.Stop(slot))
    toast(BridgeMsg.CAST_RETURNING)
}

/**
 * "Trả cụm về đồng hồ" (cứu hộ thường) — lặp lại `MainActivityCastController.kt:81–89`:
 * Stop → đóng chiếu → toast → **mở lại chiếu sau 2 s**. Khác [deepRescue] đúng ở chỗ CÓ mở lại.
 */
fun ClusterNavBridge.restoreCluster() {
    coordinator.dispatch(SimpleCastIntent.Stop())
    coordinator.closeProjection()
    toast(BridgeMsg.CLUSTER_RESET_REOPENING)
    Handler(Looper.getMainLooper()).postDelayed({ coordinator.openProjection() }, 2_000)
}

/**
 * "Dọn sạch cụm (gỡ kẹt DashCast)" — lặp lại `CastDeepRescueAction.kt:71–98`.
 *
 * ⚠ **KHÔNG gọi lại được lớp gốc**: `CastDeepRescueAction.bind(button)` nhận `android.widget.Button`
 * và `execute()` là private ⇒ cầu này không giữ View nên phải chép lại **ba bước** của nó. Danh sách
 * app tranh chấp thì DÙNG CHUNG hằng gốc ([CastDeepRescueAction.CONFLICT_PACKAGES]) để hai đường
 * không trôi khỏi nhau.
 *
 * [onConfirm] = hộp xác nhận của tầng UI (lớp gốc tự dựng `AlertDialog`; bridge không dựng View).
 * Chỉ chạy khi tầng UI gọi lại `proceed()`. [onDone] nhận **danh sách gói đã force-stop** (rỗng =
 * không thấy app tranh chấp nào đang chạy) để tầng Settings ghép câu tổng kết bằng tài nguyên — câu
 * gốc ở `CastDeepRescueAction.kt:91–94`: VI "Đã dọn: dừng chiếu + force-stop <ai> + reset cụm. Hãy GỠ
 * DashCast rồi power-cycle xe." · EN "Cleaned: stopped casting + force-stopped <who> + reset cluster.
 * Please UNINSTALL DashCast and power-cycle." (<ai> rỗng ⇒ VI "(không thấy app tranh chấp đang chạy)"
 * · EN "(no conflicting app was running)").
 */
fun ClusterNavBridge.deepRescue(
    onConfirm: (proceed: () -> Unit) -> Unit,
    onDone: (stoppedPackages: List<String>) -> Unit = {},
) {
    onConfirm {
        toast(BridgeMsg.DEEP_RESCUE_RUNNING)
        Thread({
            // 1. Đứng hẳn xuống — thôi giành cụm. KHÔNG mở lại chiếu (khác [restoreCluster]).
            runCatching { coordinator.dispatch(SimpleCastIntent.Stop()) }
            runCatching { coordinator.closeProjection() }
            runCatching { app.stopService(Intent(app, FloatingBubbleService::class.java)) }

            // 2. Force-stop bên tranh chấp để nó thôi giật lại cụm.
            val stopped = CastDeepRescueAction.CONFLICT_PACKAGES.filter { pkg ->
                runCatching { coordinator.executeShell("am force-stop $pkg").success }.getOrDefault(false)
            }

            // 3. Reset VD cụm về mặc định (best-effort; đúng nguồn kẹt "rò state trên VD").
            val vd = runCatching { DisplayParse.clusterDisplayId(coordinator.executeShell("dumpsys display").stdout) }
                .getOrDefault(-1)
            if (vd >= 0) {
                runCatching { coordinator.executeShell("wm size reset -d $vd") }
                runCatching { coordinator.executeShell("wm density reset -d $vd") }
                runCatching { coordinator.executeShell("wm overscan reset -d $vd") }
            }

            ui(Runnable { onDone(stopped) })
        }, "bridge-deep-rescue").start()
    }
}

/**
 * Trạng thái chiếu — trả **state thô của `:core`** (`MainActivityCastController.kt:220–240` là nơi
 * màn cũ dịch nó sang câu). Tầng Settings tra tài nguyên theo nhánh:
 * `Off` VI "Tắt"·EN "Off" · `Opening` VI "Đang mở cụm…"·EN "Opening cluster…" · `Idle` VI "Sẵn sàng ·
 * bấm nút nổi để chiếu"·EN "Ready · tap bubble to cast" · `CastingFull` VI/EN "Đang chiếu: <app>"/
 * "Casting: <app>" · `CastingSplit` VI "Chia đôi: <trái> | <phải>"·EN "Split: <left> | <right>" ·
 * `Stopping` VI "Đang trả app về…"·EN "Returning app…" · `Closing` VI "Đang đóng cụm…"·EN "Closing
 * cluster…" · `Error` VI "Lỗi: <thông điệp>"·EN "Error: <message>". Tên app lấy bằng
 * `pkg.substringAfterLast('.')` y như màn cũ.
 */
fun ClusterNavBridge.castState(): SimpleCastState = coordinator.state

/**
 * Danh sách app chiếu được (nhãn → gói) — lặp lại nguồn của hộp chọn
 * `MainActivityCastController.kt:194–206`: mọi app có LAUNCHER, bỏ chính mình + hai launcher gốc.
 */
fun ClusterNavBridge.installedCastApps(): List<Pair<String, String>> = launcherApps(excludeLaunchers = false)

/**
 * Danh sách app cho ô "tự chiếu" — lặp lại `CastAutostart.kt:97–104`: **khác** [installedCastApps] ở
 * chỗ loại thêm mọi launcher/home ([AppMover.isLauncher], guard R2 #3). Giữ nguyên hai danh sách
 * khác nhau đúng như màn cũ thay vì hợp nhất (CLAUDE.md §6: không đổi đường đang chạy tốt).
 */
fun ClusterNavBridge.autostartAppOptions(): List<Pair<String, String>> = launcherApps(excludeLaunchers = true)

private fun ClusterNavBridge.launcherApps(excludeLaunchers: Boolean): List<Pair<String, String>> = runCatching {
    val pm = app.packageManager
    val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val excluded = setOf(app.packageName, "com.android.launcher", "com.android.launcher3")
    PackageQueries.queryActivities(pm, launchIntent)
        .filter { it.activityInfo.packageName !in excluded }
        .filter { !excludeLaunchers || !AppMover.isLauncher(it.activityInfo.packageName) }
        .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        .distinctBy { it.second }
        .sortedBy { it.first.lowercase() }
}.getOrDefault(emptyList())
