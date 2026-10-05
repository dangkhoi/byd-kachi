package com.byd.clusternav

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellResult
import com.byd.clusternav.carexec.LocalShellRetry
import com.byd.clusternav.carexec.LocalShellText
import com.byd.clusternav.core.FloatAppList
import com.byd.clusternav.launcher.HomeActivityCmd
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.trip.TripGate
import com.byd.clusternav.launcher.trip.TripStart
import com.byd.clusternav.launcher.tripConfig
import com.byd.clusternav.navigation.NavApps
import com.byd.clusternav.navigation.VietMapBubbleWait
import com.byd.clusternav.system.AppPrereqPlan
import com.byd.clusternav.system.Truth

/**
 * Auto-start VietMap để widget/notification có nguồn speed-limit (badge cụm mirror). Dùng chung cho 2 case:
 *  • BOOT headless ([BootSetupService]) → sau khi start, VỀ HOME (không đè launcher; app mình vốn không foreground).
 *  • Mở app / bật công tắc badge-bong bóng ([com.byd.clusternav.launcher.ClusterNavBridge]) → sau khi start,
 *    đưa app mình lại TRƯỚC (user đang xem nó). Màn ClusterNav cũ — chỗ gọi trước 2026-09-13 — đã gỡ.
 *
 * SỬA 2 bug on-car (2026-08-21): (1) guard cũ dùng runningAppProcesses (Android 10+ chỉ thấy process mình → luôn
 * relaunch); nay dùng `pidof` qua dadb (uid shell, tin cậy cross-app) → CHỈ start khi CHƯA chạy. (2) không để VietMap
 * đè: sau start thì trả foreground về đúng chỗ (HOME cho boot / ClusterNav cho app-open). Chạy NỀN, degrade-safe.
 */
object VietMapAutostart {
    private const val TAG = "VietMapAutostart"
    /** §7 — KHÔNG chép lại tên gói: roster ở [NavApps] là nguồn sự thật duy nhất. */
    const val PKG = NavApps.VIETMAP_LIVE

    // ── B2 (on-car 2026-09-06): CHỐNG LOOP autostart ────────────────────────────────────────────
    // Bug: [runNow] không có dedup/cooldown; bóng bật ⇒ mỗi onCreate (mở app · recreate khi
    // đổi ngôn ngữ/giao diện · auto-open lúc boot) chạy "launch VietMap → sleep 1500 → trả ClusterNav" ⇒
    // VietMap nhảy foreground rồi lùi = "loop flash" owner thấy. Vá bằng 3 lớp: (a) cooldown + in-flight ở đây;
    // (b) bỏ launch nếu VietMap ĐÃ foreground (trong [runNow]); (c) không gọi lúc dựng lại màn (gate ở chỗ gọi).
    /** Khoảng cách tối thiểu giữa 2 lần autostart. Trong cửa sổ này (recreate/mở lại nhanh) ⇒ KHÔNG launch lại. */
    const val COOLDOWN_MS = 30_000L
    private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastRunAtMs = 0L

    // ── B3 (on-car 2026-09-07) → 2.89 · B2: CHỜ-ĐỘNG trước khi hạ VietMap xuống nền ──────────────────────────
    // 1.37 thay `sleep(1500)` cứng bằng poll "resumed + giữ ≥ 2,5 s" (trần 25 s); 2026-09-21 thêm "đã vào map (không
    // tính màn chờ) + service bóng chạy". Owner 05/10 (2.88): mạng chậm ⇒ VietMap đứng ở màn chờ quá 25 s ⇒ poll hết giờ
    // nhưng bên gọi VẪN hạ nền ⇒ bóng không lên. 2.89: luật + hằng số chuyển sang `:core` [VietMapBubbleWait] (trần 60 s,
    // còn ở màn chờ thì KHÔNG hạ, người dùng chuyển app thì dừng không gửi HOME, hạ xong đọc lại service bóng một lần).

    /** Lượt chờ bóng gần nhất — CHỈ để hiển thị ở màn Chẩn đoán (không bao giờ là căn cứ quyết định). */
    @Volatile var lastBubbleWait: String? = null
        private set

    /** PURE (device-free, unit-tested): [nowMs] đã ra ngoài cooldown so với [lastRunAtMs] chưa (0 = chưa từng chạy). */
    internal fun outsideCooldown(nowMs: Long, lastRunAtMs: Long, cooldownMs: Long = COOLDOWN_MS): Boolean =
        lastRunAtMs == 0L || nowMs - lastRunAtMs >= cooldownMs

    /**
     * PURE (device-free, unit-tested) — từ output của `dumpsys activity activities | grep <pkg>`, VietMap có
     * **BẢN GHI ACTIVITY** (activity record / task / recent) trong hệ thống window chưa.
     *
     * ⚠ SỬA on-car v1.33→v1.34 (bóng): guard cũ chỉ bỏ launch khi VietMap đang **RESUMED** (foreground). Nhưng
     * khi mở ClusterNav, ClusterNav mới là foreground nên VietMap KHÔNG resumed ⇒ nhánh bóng LUÔN relaunch dù
     * VietMap đã mở sẵn (activity đã dựng) ⇒ owner thấy VietMap giật/relaunch, bóng không lên. Bóng của bản mod
     * chỉ cần activity ĐÃ TỪNG DỰNG (đang chạy nền) — không cần resumed. Nên nếu ĐÃ có bản ghi activity ⇒ bóng đã
     * init ⇒ KHÔNG cần launch lại.
     *
     * `dumpsys activity activities` liệt kê stack/task/recents của ACTIVITY (KHÔNG liệt kê service/widget), nên
     * grep theo gói: có dòng tham chiếu component `pkg/…` (ActivityRecord{…pkg/.X}, realActivity=pkg/…,
     * baseActivity=…pkg/…) HOẶC dòng `ActivityRecord`/`Task{`/`Hist ` kèm pkg ⇒ CÓ activity record. Output rỗng
     * (chỉ process service/widget, không activity) ⇒ chưa init bóng ⇒ vẫn nên launch.
     */
    internal fun hasActivityRecord(dumpsysActivitiesGrep: String, pkg: String = PKG): Boolean {
        if (dumpsysActivitiesGrep.isBlank()) return false
        return dumpsysActivitiesGrep.lineSequence().any { line ->
            line.contains("$pkg/") ||
                (line.contains(pkg) && (line.contains("ActivityRecord") || line.contains("Task{") || line.contains("Hist ")))
        }
    }

    /**
     * PURE (device-free, unit-tested) — từ output của `dumpsys activity activities | grep -E
     * 'mResumedActivity|topResumedActivity|ResumedActivity'`, activity ĐANG resumed (foreground) có thuộc
     * [pkg] không. Chỉ dùng cho guard "đã foreground → bỏ launch" (BẤT KỲ dòng resumed — kể cả trong ô Kachi). Vòng chờ
     * bóng (2.89) KHÔNG dùng hàm này: nó đọc activity resumed của DISPLAY 0 ([VietMapBubbleWait.topOnDefaultDisplay] — Pass 2 ·
     * vietmap-dock-r1-1; dòng tổng là display giữ tiêu điểm, Pass 3 · r2-7).
     *
     * Dòng resumed điển hình: `mResumedActivity: ActivityRecord{… u0 vn.vietmap.live/.MainActivity t123}` —
     * nên match theo component `pkg/` (chắc chắn là activity của gói) VÀ dòng là loại *ResumedActivity (grep
     * đã lọc, nhưng hàm tự lọc lại để test độc lập). Rỗng/không match ⇒ false (không foreground).
     */
    internal fun isResumedActivity(dumpsysResumedGrep: String, pkg: String = PKG): Boolean {
        if (dumpsysResumedGrep.isBlank()) return false
        return dumpsysResumedGrep.lineSequence().any { line ->
            line.contains("ResumedActivity") && line.contains("$pkg/")
        }
    }

    /**
     * Giành 1 suất chạy: trả `true` nếu được phép tiếp tục (đánh dấu in-flight + đóng dấu thời gian). Trả
     * `false` nếu ĐANG có phiên chạy (in-flight) HOẶC còn trong [COOLDOWN_MS]. Thành công ⇒ caller PHẢI gọi
     * [finishRun] khi xong (dùng `try/finally`). `internal` để test off-car lái được trọn vòng gate.
     */
    internal fun tryBeginRun(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!inFlight.compareAndSet(false, true)) return false      // đã có phiên đang chạy
        if (!outsideCooldown(nowMs, lastRunAtMs)) {                  // còn trong cooldown
            inFlight.set(false)
            return false
        }
        lastRunAtMs = nowMs
        return true
    }

    /** Nhả suất chạy (gọi trong `finally` của [runNow]). */
    internal fun finishRun() {
        inFlight.set(false)
    }

    /** Test-only: xả state gate giữa các test (state process-global trong object). */
    internal fun resetGateForTest() {
        inFlight.set(false)
        lastRunAtMs = 0L
    }

    /**
     * Tín hiệu CAST-MẶC-ĐỊNH: VietMap có phải app tự-chiếu-lên-cụm (khoá V1) không. Đọc THẲNG pref "clustercast/autoCast"
     * (KHÔNG phụ thuộc singleton ClusterCast đã load chưa — chạy từ boot/nền). Cặp file/khoá PHẢI khớp producer
     * [ClusterCast.save] / [ClusterCast.loadPrefs] (PREF="clustercast", key "autoCast", String). Dùng chung với
     * [AppPrereqs.facts] (một chỗ đọc — DRY).
     */
    internal fun castDefault(app: Context): Boolean = runCatching {
        app.getSharedPreferences("clustercast", Context.MODE_PRIVATE).getString("autoCast", "") == PKG
    }.getOrDefault(false)

    /**
     * ĐỒNG BỘ (block thread gọi) — được [VietMapAutostartService] gọi trên thread nền của nó (FGS giữ tiến
     * trình sống tới khi chờ-bóng xong; một thread rời có thể bị kill sau finish()). Không tự spawn thread
     * ở đây — vòng đời do service quản. VietMap chưa cài ⇒ no-op. Đã cài ⇒ LUÔN chữa điều kiện nền trước (B2), rồi
     * no-op nếu CẢ badge tốc độ LẪN bong bóng VietMap đều tắt (và không phải cast-default). Chống-loop (in-flight +
     * cooldown) nằm ngay trong hàm.
     *
     * @param returnToSelfPkg  package đưa lại foreground sau khi VietMap vào map; null = về HOME (boot headless).
     */
    fun runNow(ctx: Context, returnToSelfPkg: String?) {
        val app = ctx.applicationContext
        if (runCatching { app.packageManager.getLaunchIntentForPackage(PKG) }.getOrNull() == null) return  // chưa cài
        // 2.89 · B2 VM-PREREQ-TRUTH — TRƯỚC cổng sớm bên dưới: VietMap không được miễn pin thì CHÍNH nó bung hộp "IVI không
        // hỗ trợ" ở mỗi lần khởi động nguội (spec `kachi-289-field-fixes.html` §B2). Chữa cả khi người dùng đã tắt
        // bóng/biển mà vẫn tự mở VietMap (lượt nổ máy luôn gọi tới đây). Đọc sự thật → áp phần thiếu → đọc lại; phiên
        // hỏng ⇒ giữ nguyên, đi tiếp.
        // Pass 3 · vietmap-dock-r2-1: GIỮ kết quả — miễn pin chưa được chứng minh CÓ ⇒ hộp "IVI không hỗ trợ" là CHUYỆN ĐÃ BIẾT, vòng
        // chờ bên dưới không được đọc nó thành "người dùng đã rời" lúc nổ máy (KDoc [VietMapBubbleWait.mayGoHome]).
        val prereq = AppPrereqs.ensure(app, PKG, AppPrereqPlan.Role.AUTOSTART_PASS)
        val dialogExpected = (prereq?.after?.dozeExempt ?: Truth.UNKNOWN) != Truth.YES
        val castDefault = castDefault(app)
        val silentReason = Prefs.badgeEnabled(app) || Prefs.vmBubbleEnabled(app)   // badge tốc độ / bong bóng
        if (!castDefault && !silentReason) return                                  // không lý do nào ⇒ thôi
        // Log QUYẾT ĐỊNH (TRƯỚC dadb) — verify được cả khi dadb fail (vd emulator): nhánh nào + vì tín hiệu nào.
        Log.i(TAG, "autostart quyết định: castDefault=$castDefault badge=${Prefs.badgeEnabled(app)} bubble=${Prefs.vmBubbleEnabled(app)} → ${if (castDefault) "ACTIVE" else "silent-bg"}")
        // (a) CHỐNG LOOP (B2, on-car 2026-09-06): chỉ MỘT phiên chạy tại một thời điểm + cooldown giữa hai lần.
        // onCreate/recreate(đổi ngôn ngữ/giao diện)/boot bắn dồn ⇒ chỉ lần đầu đi qua; các lần trong COOLDOWN_MS bị
        // bỏ (khỏi lặp "launch → sleep 1500 → trả foreground" = flash loop owner thấy). finishRun() ở finally.
        if (!tryBeginRun()) {
            Log.i(TAG, "autostart: bỏ qua (đang chạy hoặc trong cooldown ${COOLDOWN_MS}ms) — chống loop onCreate/recreate/boot")
            return
        }
        try {
        runCatching {
            val keys = AdbKeys.ensure(app)
            // sessionResult (KHÔNG phải session): [LocalDeviceShell.session] nuốt lỗi MỞ PHIÊN thành `null` IM
            // LẶNG (nó chỉ map Failed→null, KHÔNG ném), nên `onFailure` bên dưới CHỈ bắt được ngoại lệ thật (vd
            // AdbKeys.ensure) — KHÔNG bắt được ca dadb không nối được localhost:5555, mà đó CHÍNH là dạng hỏng của
            // Bug 2 cần chẩn đoán trên xe. Đọc kết quả để log LÝ DO đã phân loại (PORT_CLOSED / AWAITING_APPROVAL /
            // IO_ERROR…). KHÔNG đổi hành vi thực thi: session() vốn gọi cùng sessionResult() rồi vứt Failed.
            val result = LocalDeviceShell.sessionResult(keys, LocalShellRetry.BACKGROUND_READ_CAP) { sh ->
                val running = sh("pidof $PKG").output.trim().isNotEmpty()
                // `byd_float_app_list`: vẫn ghi khi bóng BẬT (vô hại) nhưng [ĐO nguồn ROM 2602030] system/product KHÔNG chỗ
                // nào đọc khoá này (vendor [CHƯA BIẾT]) — hộp "IVI không hỗ trợ" là lời xin miễn pin của VietMap (B2), quyền
                // vẽ nổi là appop `SYSTEM_ALERT_WINDOW` (cả hai: [AppPrereqs], theo sự thật). 2.89: đọc trước, chỉ ghi khi
                // VẮNG (không còn cờ một-lần `vm_float_whitelist_applied`). Hỏng ⇒ log, launch phía dưới vẫn chạy.
                if (Prefs.vmBubbleEnabled(app)) {
                    runCatching {
                        // Review 2.89 Pass 2 · vietmap-dock-r1-6: đọc HỎNG (exit ≠ 0, settings provider chưa sẵn lúc nổ máy) KHÔNG
                        // phải "danh sách rỗng" — ghi đè lúc đó là xoá mục Google/Gemini mà AssistantLauncher đã gộp vào ⇒ bỏ ghi.
                        val read = sh("settings get global byd_float_app_list")
                        if (!read.ok) {
                            Log.w(TAG, "byd_float_app_list: đọc hỏng (exit=${read.exitCode}) — KHÔNG ghi (giữ danh sách hiện có)")
                            return@runCatching
                        }
                        val curFloat = read.output.trim()
                        if (!FloatAppList.contains(curFloat, PKG)) {
                            val mergedFloat = FloatAppList.merge(curFloat, listOf(PKG))
                            sh("settings put global byd_float_app_list $mergedFloat")
                            Log.i(TAG, "byd_float_app_list: thêm VietMap (list=$mergedFloat) — không tác dụng trên 2602030 system/product")
                        }
                    }.onFailure { Log.w(TAG, "byd_float_app_list: đọc/ghi hỏng (bỏ qua): ${it.message}") }
                }
                // (b) VietMap ĐÃ ở foreground rồi → launch lại chỉ gây "giật" (flash), không cần. Đọc activity
                // đang resumed/focus; degrade-safe (đọc lỗi / grep vắng ⇒ coi như KHÔNG-foreground ⇒ giữ hành vi
                // cũ = vẫn launch). Chỉ có ý nghĩa khi process đang sống (running).
                val foreground = running && runCatching {
                    isResumedActivity(sh(RESUMED_GREP).output)
                }.getOrDefault(false)
                if (foreground) {
                    Log.i(TAG, "autostart: VietMap đã ở foreground (running=$running) — bỏ launch (khỏi giật)")
                    return@sessionResult
                }
                if (castDefault) {
                    // CAST-default ⇒ VietMap phải ACTIVE để đường cast chiếu lên cụm. LUÔN launch activity — kể cả
                    // process đã sống (widget/service): pidof chỉ biết PROCESS, KHÔNG biết activity/nav đang mở.
                    // KHÔNG trả foreground (để VietMap active cho cast).
                    sh("monkey -p $PKG -c android.intent.category.LAUNCHER 1")
                    Log.i(TAG, "autostart CAST-default → launch VietMap ACTIVE (process đã chạy=$running)")
                } else {
                    // SILENT background (badge tốc độ / bóng VietMap).
                    // ⚠ BÓNG VietMap: bản mod chỉ hiện bóng lên CỤM khi VietMap Ở BACKGROUND, và cần ACTIVITY đã
                    //   dựng — `pidof` chỉ biết PROCESS (service/widget), KHÔNG biết activity đã mở chưa.
                    // BADGE-only: chỉ cần PROCESS sống (widget speed-limit); đã sống ⇒ GIỮ NGUYÊN (tránh churn).
                    val bubbleOn = Prefs.vmBubbleEnabled(app)
                    // (c) SỬA on-car v1.33→v1.34: bóng bật + VietMap ĐÃ có bản ghi activity (đã init, đang chạy nền)
                    //   ⇒ KHÔNG relaunch. Guard `foreground` phía trên vô dụng cho ca này vì mở ClusterNav thì
                    //   ClusterNav mới là foreground, VietMap không resumed ⇒ nhánh bóng cũ LUÔN relaunch (flash,
                    //   bóng không lên — bug owner báo on-car). Chỉ đọc khi process đang sống; degrade-safe: đọc
                    //   dumpsys lỗi/không nối được ⇒ hasActivity=false ⇒ rơi về hành vi cũ (vẫn launch).
                    val hasActivity = running && runCatching {
                        hasActivityRecord(sh("dumpsys activity activities | grep -E '$PKG'").output)
                    }.getOrDefault(false)
                    if (bubbleOn && hasActivity) {
                        Log.i(TAG, "autostart silent-bg (bóng): VietMap đã có bản ghi activity trong stack (running=$running) — bỏ launch, bóng đã init (chống relaunch/flash on-car v1.33)")
                    } else if (bubbleOn || !running) {
                        // BẬT BÓNG (chưa có activity record) HOẶC process chưa sống: launch MỘT lần, CHỜ theo luật
                        // [VietMapBubbleWait] (map + service bóng, trần 60 s) rồi chỉ hạ nền khi luật cho phép.
                        // Pass 3 · vietmap-dock-r2-3: service bóng ĐÃ chạy trước lượt mở (FGS sống lâu hơn activity) thì sự có mặt của nó
                        // không chứng minh gì cho lượt Dart MỚI — đọc MỘT lần trước `monkey` (đọc hỏng ⇒ `null` = coi như đã chạy).
                        val bubbleBefore = if (!bubbleOn || !running) false
                        else runCatching { hasBubbleService(sh(SERVICES_DUMP).output) }.getOrNull()
                        sh("monkey -p $PKG -c android.intent.category.LAUNCHER 1")
                        val wait = awaitBubble(sh, needBubble = bubbleOn, bubbleBefore = bubbleBefore)
                        // Pass 2 · whole-r1-1: lượt nổ máy còn ở màn chờ mà chuyến lên xe còn chờ màn nhà ⇒ vẫn về HOME (KDoc
                        // [VietMapBubbleWait.backgroundAfter]); sổ chuyến chỉ đọc khi cần. Pass 3 · vietmap-dock-r2-1: + USER_LEFT /
                        // NEVER_FOREGROUND khi miễn pin chưa chứng minh (hộp "IVI không hỗ trợ" đè display 0 — HOME cũng đóng nó).
                        val bootPath = returnToSelfPkg == null
                        val tripPending = bootPath && VietMapBubbleWait.mayGoHome(wait.outcome, dialogExpected) && tripPendingThisIgnition(app)
                        val goBack = VietMapBubbleWait.backgroundAfter(wait.outcome, bootPath, tripPending, dialogExpected)
                        val back = if (goBack) {
                            if (returnToSelfPkg != null) sh("monkey -p $returnToSelfPkg -c android.intent.category.LAUNCHER 1")
                            else sh(HomeActivityCmd.GO_HOME)   // byte y hệt chuỗi cũ — gom về một chỗ (DRY)
                            (returnToSelfPkg ?: "HOME") + if (tripPending) " (chuyến lên xe còn chờ màn nhà${if (dialogExpected) ", miễn pin chưa chứng minh" else ""})" else ""
                        } else {
                            "KHÔNG hạ"
                        }
                        // Hạ xong: đọc service bóng MỘT lần sau ~3 s để biết bóng còn sống ở nền (không vòng, không mở lại).
                        val after = if (goBack && bubbleOn) bubbleAfterBackground(sh) else null
                        record(wait, back, after, bubbleOn, running, hasActivity, goBack)
                    } else {
                        Log.i(TAG, "autostart silent-bg (badge-only) → VietMap process đã sống, giữ nguyên")
                    }
                }
                Unit
            }
            // Phiên dadb KHÔNG mở được (Bug 2 trên xe / emulator không có loopback) — session() sẽ nuốt thành null,
            // nên phải log tường minh ở đây để hiện trường biết VietMap CHƯA auto-start và VÌ SAO.
            if (result is LocalShellResult.Failed) {
                Log.w(TAG, "autostart: phiên dadb KHÔNG mở được (${result.reason}, ${result.attempts} lần thử) — VietMap CHƯA auto-start (localhost:5555 chưa sẵn?)")
            }
        }.onFailure { Log.w(TAG, "auto-start VietMap failed: ${it.message}") }
        } finally {
            finishRun()   // (a) nhả suất chạy dù thành công hay ném — lần autostart kế mới vào được sau cooldown
        }
    }

    /**
     * Lệnh đọc activity resumed — một chuỗi cho cả guard foreground lẫn vòng chờ. Pass 2 · vietmap-dock-r1-1: thêm dòng
     * `Display #N` để vòng chờ quyết theo DISPLAY 0 ([VietMapBubbleWait.topOnDefaultDisplay]); tập trên của lệnh cũ ⇒ guard
     * [isResumedActivity] đọc cùng đầu ra không đổi nghĩa.
     */
    private const val RESUMED_GREP = VietMapBubbleWait.PER_DISPLAY_GREP

    /**
     * Pass 2 · whole-r1-1 — chuyến lên xe của LẦN NỔ MÁY NÀY có việc và chưa có kết quả (sổ chuyến bền `TripLedgerStore` qua
     * [TripStart.now]: chưa chạy / đang chạy). Chỉ đọc (prefs + `Settings.Global.BOOT_COUNT`), không lệnh nào.
     */
    private fun tripPendingThisIgnition(app: Context): Boolean =
        !WorkspacePrefs(app).tripConfig().empty && TripStart.now(app) != TripGate.Now.SHOWN

    /** Kết quả vòng chờ: luật đã quyết gì, sau bao lâu, activity trên cùng lúc chốt. */
    private data class Wait(val outcome: VietMapBubbleWait.Outcome, val elapsedMs: Long, val top: VietMapBubbleWait.Top?)

    /**
     * Vòng chờ theo [VietMapBubbleWait.next] (luật thuần ở `:core`). Đọc activity resumed của DISPLAY 0
     * ([VietMapBubbleWait.topOnDefaultDisplay]) mỗi [VietMapBubbleWait.POLL_INTERVAL_MS]; service bóng CHỈ đọc khi `MainActivity` của
     * VietMap đã ở trên cùng ([SOÁT
     * 2026-09-21 · P2]: vòng này chạy đúng lúc nổ máy, [ĐO xe] load tới 14 — không nhân đôi lệnh dumpsys vô ích).
     * [needBubble] = false (chỉ biển tốc độ) ⇒ không đòi service bóng, chỉ cần `MainActivity` trên cùng. Đọc hỏng ⇒
     * `top = null` (chưa biết — không phải "người dùng đã rời").
     * Chạy TRONG phiên dadb (dùng lại [sh]); mỗi lệnh ngắn nên không chạm hạn đọc 30 s của
     * [LocalShellRetry.BACKGROUND_READ_CAP] (ngủ giữa hai lệnh không phải lượt read()).
     */
    private fun awaitBubble(sh: (String) -> LocalShellText, needBubble: Boolean, bubbleBefore: Boolean?): Wait {
        // Pass 2 · vietmap-dock-r1-3: đồng hồ ĐƠN ĐIỆU — đầu xe chỉnh giờ (GPS/mạng) ngay sau nổ máy, đúng lúc vòng này chạy; giờ
        // tường lùi thì trần 60 s giãn ra, tiến thì hết hạn ngay [SUY — hành vi đồng hồ ROM chưa đo]. Giờ tường chỉ cho Chẩn đoán.
        val startMs = SystemClock.elapsedRealtime()
        var state = VietMapBubbleWait.State()
        while (true) {
            Thread.sleep(VietMapBubbleWait.POLL_INTERVAL_MS)
            // Pass 2 · vietmap-dock-r1-1: activity trước mặt người lái (display 0), không phải display đang giữ tiêu điểm.
            val top = runCatching { VietMapBubbleWait.topOnDefaultDisplay(sh(RESUMED_GREP).output) }.getOrNull()
            // Màn chờ nằm TRONG MainActivity [ĐO manifest] ⇒ chỉ service bóng (Dart bật) mới là dấu "đã đi tiếp" (KDoc lớp luật).
            val nowMs = SystemClock.elapsedRealtime()
            // Pass 3 · vietmap-dock-r2-3: service đã chạy từ trước lượt mở ⇒ chỉ nhận khi `lastActivity` MỚI hơn lượt mở.
            val bubble = !needBubble || (VietMapBubbleWait.mainOnTop(top, PKG) && runCatching {
                val dump = sh(SERVICES_DUMP).output
                VietMapBubbleWait.freshBubble(hasBubbleService(dump), bubbleBefore,
                    VietMapBubbleWait.serviceLastActivityAgoMs(dump, BUBBLE_SERVICE), nowMs - startMs)
            }.getOrDefault(false))
            when (val step = VietMapBubbleWait.next(state, VietMapBubbleWait.Tick(top, bubble), nowMs, nowMs - startMs, PKG)) {
                is VietMapBubbleWait.Step.Wait -> state = step.state
                is VietMapBubbleWait.Step.Done -> return Wait(step.outcome, nowMs - startMs, step.state.lastTop)
            }
        }
    }

    /** Sau khi hạ nền: chờ [VietMapBubbleWait.RECHECK_AFTER_MS] rồi đọc service bóng MỘT lần (`null` = đọc hỏng). */
    private fun bubbleAfterBackground(sh: (String) -> LocalShellText): Boolean? {
        Thread.sleep(VietMapBubbleWait.RECHECK_AFTER_MS)
        return runCatching { hasBubbleService(sh(SERVICES_DUMP).output) }.getOrNull()
    }

    /** Một dòng log + bản ghi cho màn Chẩn đoán (CLAUDE.md §11 — chụp màn hình gửi về, không gõ adb). */
    private fun record(
        wait: Wait, back: String, after: Boolean?, bubbleOn: Boolean, running: Boolean, hasActivity: Boolean, wentBack: Boolean,
    ) {
        val top = wait.top?.let { "${it.pkg}/${it.activity}" } ?: "?"
        val afterText = when (after) {
            null -> if (wentBack && bubbleOn) "đọc hỏng" else "-"
            true -> "CÒN chạy"
            false -> "KHÔNG chạy"
        }
        val line = "${wait.outcome} sau ${wait.elapsedMs / 1000} s (${wait.outcome.why}) · trên cùng=$top · hạ nền=$back · " +
            "service bóng sau ${VietMapBubbleWait.RECHECK_AFTER_MS / 1000} s: $afterText [bubbleOn=$bubbleOn running=$running hasActivity=$hasActivity]"
        lastBubbleWait = "${android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())} $line"
        Log.i(TAG, "autostart silent-bg → $line")
    }

    /** Dump service của VietMap (lọc theo gói — không quét mù). */
    private const val SERVICES_DUMP = "dumpsys activity services $PKG"

    /**
     * PURE — từ `dumpsys activity services vn.vietmap.live`, service dựng BÓNG nổi (`VMBluetoothService`) đã chạy
     * chưa. Đây là service mod VietMap tạo overlay bóng trên cụm (runbook §10); [ĐO xe 2026-09-21] khi nó CHƯA
     * chạy thì bóng không lên dù MainActivity đã resumed. Rỗng/đọc-lỗi ⇒ false (chờ tiếp).
     */
    internal fun hasBubbleService(dumpsysServicesGrep: String, marker: String = BUBBLE_SERVICE): Boolean =
        dumpsysServicesGrep.contains(marker)

    /** Tên service dựng bóng của mod VietMap (ServiceRecord trong dumpsys). */
    const val BUBBLE_SERVICE = "VMBluetoothService"
}
