package com.byd.clusternav

import android.content.Context
import android.util.Log
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellResult
import com.byd.clusternav.carexec.LocalShellRetry
import com.byd.clusternav.carexec.LocalShellText
import com.byd.clusternav.core.FloatAppList
import com.byd.clusternav.navigation.NavApps

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

    // ── B3 (on-car 2026-09-07): CHỜ-ĐỘNG thay sleep(1500) cứng ở nhánh bóng silent-bg ────────────
    // Bug owner báo trên xe (v1.36): bật bóng nhưng chạy silent thì bóng KHÔNG lên — phải mở VietMap bằng
    // tay, đợi nó boot VÀO MAP, rồi hạ xuống thì bóng mới lên. [SUY] gốc: cũ = `launch → Thread.sleep(1500)
    // → hạ nền`. 1.5s là delay CỨNG, quá ngắn cho cold start Flutter + map SDK + `VMBluetoothService` (service
    // dựng bóng, runbook §10) — nhất là khi mạng chậm (owner: "tuỳ network, có khi nhanh có khi lâu"). Hạ nền
    // TRƯỚC khi VietMap vào map xong ⇒ service chưa dựng bóng ⇒ không có bóng để hiện. Sửa: POLL tới khi VietMap
    // thật sự resumed (đã vào map) và GIỮ foreground liên tục ≥ [SETTLE_MS], RỒI mới hạ nền — mô phỏng đúng thao
    // tác tay của owner. Thoát SỚM khi ready (mạng nhanh); [POLL_TIMEOUT_MS] chỉ là chặn trên khi VietMap không
    // vào map (chưa login / lỗi) để không treo service vô hạn.
    /** Nhịp poll trạng thái resumed giữa 2 lần đọc dumpsys. */
    const val POLL_INTERVAL_MS = 500L
    /** Chặn trên tổng thời gian chờ VietMap vào map (rộng vì tuỳ network); thoát sớm khi đã settle. */
    const val POLL_TIMEOUT_MS = 25_000L
    /** VietMap phải GIỮ foreground liên tục bấy nhiêu để chắc đã vào map ổn định (splash→map đã xong) + service bóng kịp dựng. */
    const val SETTLE_MS = 2_500L

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
     * [pkg] không. Dùng để (a) guard "đã foreground → bỏ launch" và (b) poll chờ VietMap vào map.
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
     * PURE — activity đang resumed có phải **MÀN CHÍNH (đã vào map)** của VietMap không, KHÔNG tính màn splash/flash.
     *
     * ## Bug owner báo 2026-09-21 (vì sao cần hàm này TÁCH khỏi [isResumedActivity])
     * VietMap cold-start chậm dừng ở **màn flash** vài giây. Màn flash cũng là một activity của `vn.vietmap.live`,
     * nên nó khớp [isResumedActivity] ("bất kỳ activity của gói") ⇒ poll tưởng "đã vào map", hạ VietMap xuống nền
     * **trong khi còn ở flash** ⇒ `VMBluetoothService` chưa kịp dựng bóng ⇒ bóng không lên. Owner phải bấm vào
     * VietMap cho nó chạy xong rồi hạ tay thì bóng mới lên.
     *
     * VietMap là Flutter: activity host map là `.MainActivity` (cũng là dòng resumed điển hình trong KDoc trên).
     * Màn flash mang tên KHÁC (`.SplashActivity`/`.LaunchActivity`/…). Nên "đã vào map" = resumed activity là
     * `pkg/` **và** tên activity chứa `MainActivity`. Không có `MainActivity` (đang splash) ⇒ CHƯA vào map ⇒ poll
     * tiếp. Nếu bản VietMap nào đặt tên khác thì poll sẽ hết giờ (POLL_TIMEOUT_MS) rồi vẫn hạ nền — an toàn hơn
     * hạ sớm, và đó là ca hiếm; giữ khớp `MainActivity` vì đó là tên [ĐO] thấy ở dòng resumed thực tế.
     */
    internal fun isInMapActivity(dumpsysResumedGrep: String, pkg: String = PKG): Boolean {
        if (dumpsysResumedGrep.isBlank()) return false
        return dumpsysResumedGrep.lineSequence().any { line ->
            line.contains("ResumedActivity") && line.contains("$pkg/") && line.contains("MainActivity")
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
     * ĐỒNG BỘ (block thread gọi) — được [VietMapAutostartService] gọi trên thread nền của nó (FGS giữ tiến
     * trình sống tới khi poll-vào-map xong; một thread rời có thể bị kill sau finish()). Không tự spawn thread
     * ở đây — vòng đời do service quản. No-op nếu CẢ badge tốc độ LẪN toggle bong bóng VietMap đều tắt (và
     * không phải cast-default) / VietMap chưa cài. Chống-loop (in-flight + cooldown) nằm ngay trong hàm.
     *
     * @param returnToSelfPkg  package đưa lại foreground sau khi VietMap vào map; null = về HOME (boot headless).
     */
    fun runNow(ctx: Context, returnToSelfPkg: String?) {
        val app = ctx.applicationContext
        // Tín hiệu CAST-MẶC-ĐỊNH: VietMap có phải app tự-chiếu-lên-cụm không. Đọc THẲNG pref "clustercast/autoCast"
        // (KHÔNG phụ thuộc singleton ClusterCast đã load chưa — runNow chạy từ boot/nền). Cặp file/khoá PHẢI khớp
        // producer [ClusterCast.save] / [ClusterCast.loadPrefs] (PREF="clustercast", key "autoCast", String) — đổi
        // một bên phải đổi bên kia.
        val castDefault = runCatching {
            app.getSharedPreferences("clustercast", Context.MODE_PRIVATE).getString("autoCast", "") == PKG
        }.getOrDefault(false)
        val silentReason = Prefs.badgeEnabled(app) || Prefs.vmBubbleEnabled(app)   // badge tốc độ / bong bóng
        if (!castDefault && !silentReason) return                                  // không lý do nào ⇒ thôi
        if (runCatching { app.packageManager.getLaunchIntentForPackage(PKG) }.getOrNull() == null) return  // chưa cài
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
                // FLOAT/OVERLAY WHITELIST (một lần): bản mod VietMap vẽ BÓNG lên cụm, nhưng BYD IVI TỪ CHỐI
                // overlay của gói KHÔNG có trong CSV toàn cục `byd_float_app_list` (toast "Hệ thống IVI không hỗ
                // trợ hoạt động này"). Thêm VietMap vào list đó + cấp SYSTEM_ALERT_WINDOW — CÙNG công thức đã
                // proven mà AssistantLauncher dùng cho Google/Gemini (merge dùng chung com.byd.clusternav.core.
                // FloatAppList, KHÔNG clobber gói khác). Cổng: bóng BẬT + cờ một-lần chưa set. Degrade-safe: bọc
                // runCatching để hỏng (vd dadb rớt giữa chừng) KHÔNG chặn launch phía dưới; và cờ chỉ set khi
                // THÀNH CÔNG (nằm cuối runCatching) ⇒ hỏng thì lần autostart sau thử lại. Chạy trên dadb uid-shell
                // (cùng phiên) nên có quyền ghi Settings.Global + appops.
                if (Prefs.vmBubbleEnabled(app) && !Prefs.vmFloatWhitelistApplied(app)) {
                    runCatching {
                        val curFloat = sh("settings get global byd_float_app_list").output.trim()
                        val mergedFloat = FloatAppList.merge(curFloat, listOf(PKG))
                        sh("settings put global byd_float_app_list $mergedFloat")
                        sh("appops set $PKG SYSTEM_ALERT_WINDOW allow")
                        Prefs.setVmFloatWhitelistApplied(app, true)   // CHỈ set khi cả 2 lệnh trên không ném
                        Log.i(TAG, "float-whitelist: thêm VietMap vào byd_float_app_list + SYSTEM_ALERT_WINDOW allow (list=$mergedFloat)")
                    }.onFailure {
                        // KHÔNG set cờ ⇒ lần autostart kế thử lại; KHÔNG rethrow ⇒ launch phía dưới vẫn chạy.
                        Log.w(TAG, "float-whitelist: áp dụng thất bại, sẽ thử lại lần sau: ${it.message}")
                    }
                }
                // (b) VietMap ĐÃ ở foreground rồi → launch lại chỉ gây "giật" (flash), không cần. Đọc activity
                // đang resumed/focus; degrade-safe (đọc lỗi / grep vắng ⇒ coi như KHÔNG-foreground ⇒ giữ hành vi
                // cũ = vẫn launch). Chỉ có ý nghĩa khi process đang sống (running).
                val foreground = running && runCatching {
                    isResumedActivity(sh("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity'").output)
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
                        // BẬT BÓNG (chưa có activity record) HOẶC process chưa sống: launch activity, CHỜ VietMap
                        // thật sự VÀO MAP (poll resumed + giữ liên tục ≥ SETTLE_MS — thay Thread.sleep(1500) cứng,
                        // xem B3) rồi ĐƯA VỀ NỀN (returnToSelfPkg=app-open ClusterNav / HOME=boot) — để bóng đã
                        // init chắc chắn hiện khi VietMap ở nền. Poll thoát sớm khi mạng nhanh.
                        sh("monkey -p $PKG -c android.intent.category.LAUNCHER 1")
                        val ready = pollUntilInMap(sh)
                        if (returnToSelfPkg != null) sh("monkey -p $returnToSelfPkg -c android.intent.category.LAUNCHER 1")
                        else sh("am start -a android.intent.action.MAIN -c android.intent.category.HOME")
                        Log.i(TAG, "autostart silent-bg → launch VietMap + chờ-vào-map(ready=$ready) + trả nền (${returnToSelfPkg ?: "HOME"}) [bubbleOn=$bubbleOn running=$running hasActivity=$hasActivity] ⇒ VietMap ở nền để bóng hiện")
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
     * Poll tới khi VietMap resumed (đã VÀO MAP) và GIỮ foreground liên tục ≥ [SETTLE_MS] ⇒ `true`; hết
     * [POLL_TIMEOUT_MS] mà chưa settle ⇒ `false` (caller vẫn hạ nền — chặn trên, tránh treo service khi VietMap
     * không vào map: chưa login / lỗi). Đọc dumpsys mỗi [POLL_INTERVAL_MS]; lỗi đọc / không nối được ⇒ coi như
     * chưa resumed (degrade-safe). `resumedSinceMs` reset khi rớt foreground (splash→map chuyển màn) nên chỉ
     * `true` khi VietMap đã Ở YÊN trong map đủ lâu. Chạy TRONG phiên dadb (dùng lại [sh]); mỗi lệnh ngắn nên
     * KHÔNG chạm hạn đọc per-read 30s của [LocalShellRetry.BACKGROUND_READ_CAP] (sleep giữa 2 lệnh không phải
     * lần read()).
     */
    private fun pollUntilInMap(sh: (String) -> LocalShellText): Boolean {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var readySinceMs = 0L
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
            val resumed = runCatching {
                isInMapActivity(sh("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity'").output)
            }.getOrDefault(false)
            // [ĐO xe 2026-09-21] MainActivity resumed ≠ bóng đã dựng: VMBluetoothService (service dựng bóng nổi)
            // khởi CHẬM hơn map ⇒ hạ nền chỉ theo "resumed" thì bóng chưa kịp lên. Chờ THÊM: service dựng bóng đã
            // chạy. Đọc lỗi service (rỗng) ⇒ coi như CHƯA sẵn (an toàn: chờ tiếp tới timeout).
            //
            // ⚠ [SOÁT 2026-09-21 · P2] Chỉ đọc service KHI ĐÃ resumed — bóng không thể dựng trước khi app lên, nên
            // một lượt `dumpsys` khi `resumed == false` không bao giờ đổi được kết quả. Vòng này chạy ĐÚNG lúc nổ
            // máy, nơi [ĐO xe] load đã tới 14; hỏi vô điều kiện là nhân đôi số lệnh dumpsys (tới 2×50 lượt) trên
            // chính giây phút hệ đang đói CPU.
            val bubbleUp = resumed && runCatching {
                hasBubbleService(sh("dumpsys activity services vn.vietmap.live").output)
            }.getOrDefault(false)
            val nowMs = System.currentTimeMillis()
            if (resumed && bubbleUp) {
                if (readySinceMs == 0L) readySinceMs = nowMs
                if (nowMs - readySinceMs >= SETTLE_MS) return true
            } else {
                readySinceMs = 0L   // chưa vào map thật HOẶC bóng chưa dựng ⇒ chờ tiếp
            }
        }
        return false
    }

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
