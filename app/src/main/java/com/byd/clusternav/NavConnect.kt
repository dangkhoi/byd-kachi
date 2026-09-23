package com.byd.clusternav

import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellRetry
import com.byd.clusternav.carexec.LocalShellText
import com.byd.clusternav.modules.navaccess.AccessibilityRebind
import dadb.AdbKeyPair
import com.byd.clusternav.modules.navaccess.NavAccessibilitySource
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * BIND lại nav listener — cách DUY NHẤT ăn trên firmware BYD head-unit (firmware BỎ QUA requestRebind).
 * Dùng dadb (ADB local client, localhost:5555, uid=shell) chạy `cmd notification disallow/allow_listener`
 * y như DashCast. Lần đầu có popup "Allow USB debugging" trên xe → bấm Allow 1 lần (key lưu ở filesDir).
 *
 * - [reconnect]  : ép disallow→allow ngay (nút tay + auto khi chưa bound).
 * - [ensureConnected] : gọi lúc mở app — chờ bind tự nhiên ~1.8s, CHƯA bound thì mới reconnect qua dadb
 *   (không disallow/allow khi đang chạy tốt → tránh ngắt nav đang chạy). Đây là "auto connect khi khởi động app".
 */
object NavConnect {
    private const val TAG = "NavConnect"
    // This app's own installed package = BuildConfig.APPLICATION_ID (com.byd.clusternav2). Class FQNs keep the
    // internal namespace com.byd.clusternav.* (unchanged) → component = "<appId>/com.byd.clusternav.<Class>".
    // Fully isolated from the legacy com.byd.clusternav app.
    private val COMP = "${BuildConfig.APPLICATION_ID}/com.byd.clusternav.NavNotificationListener"
    private val ACC_COMP = "${BuildConfig.APPLICATION_ID}/com.byd.clusternav.modules.navaccess.NavAccessibilityService"
    private val reconnecting = java.util.concurrent.atomic.AtomicBoolean(false)   // single-flight: tap dồn dập / ensure trùng → 1 chu kỳ disallow→allow
    private val grantingAcc = java.util.concurrent.atomic.AtomicBoolean(false)    // single-flight cho grantAccessibility (dadb read-modify-write)

    // Force-rebind toggle timings (post-reboot ENABLED-but-NOT-BOUND heal). SETTLE lets a JUST-written enable
    // bind naturally first (fresh grants usually self-bind) so we don't toggle needlessly; TOGGLE_PAUSE is the
    // brief gap between the remove and the re-add that makes the framework observe the OUT state and rebind.
    private const val REBIND_SETTLE_MS = 1200L
    private const val REBIND_TOGGLE_PAUSE_MS = 800L
    // #2 — POLL bound sau toggle: dưới CPU load cao hệ bind CHẬM; poll cho đủ thời gian, trả kết quả THẬT.
    private const val REBIND_VERIFY_TRIES = 6
    private const val REBIND_VERIFY_EVERY_MS = 1000L

    // TASK 3 (R2 · docs/specs/clusternav-closeout-1.28.html) — grant-body timeout. A HUNG dadb session (stuck
    // socket read/write during the accessibility read-modify-write or the force-rebind toggle) must NOT pin the
    // [grantingAcc] single-flight forever: if it did, every later grant (incl. re-toggling 'Nút vật lý') would
    // no-op until an app RESTART. On timeout we interrupt the worker and force-release the flag. One attempt per
    // call — NO auto-loop/backoff. Kept comfortably above the ~2 s of settle+toggle sleeps in forceRebindIfNeeded.
    // [ĐO xe 2026-09-18, load 14] 9s KHÔNG đủ dưới tải: dadb chậm ⇒ settle(1.2s)+toggle(0.8s)+nhiều lượt đọc
    // verify vượt 9s ⇒ worker bị cắt GIỮA toggle ⇒ rebind thất bại ("phím gán không ăn"). [ĐO] toggle a11y
    // TRỰC TIẾP (settings, không dadb) thì bind lại NGAY cả khi load 14 ⇒ cơ chế đúng, chỉ thiếu thời gian.
    // Nới 20s để hoàn tất dưới tải nặng; single-flight vẫn được nhả sau timeout (không kẹt vĩnh viễn).
    private const val GRANT_TIMEOUT_MS = 30_000L

    /**
     * NavAccessibilityService đã BOUND THẬT chưa — đọc [android.view.accessibility.AccessibilityManager]
     * (API chính thức, phản ánh service ĐANG CHẠY, không cần dadb). NGUỒN CHUNG cho watchdog + bridge.
     *
     * ⚠ KHÔNG dùng cờ `NavAccessibilitySource.connected` để GATE heal: cờ đó set ở onServiceConnected/onUnbind,
     * mà hệ có thể unbind KHÔNG gọi onUnbind (ngủ đông/CPU pressure) ⇒ cờ KẸT true ⇒ watchdog không bao giờ heal
     * (gốc "reset mới hết", owner 2026-09-23, chung v1/v2/launcher).
     */
    fun isAccessibilityBound(ctx: Context): Boolean = runCatching {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            ?: return@runCatching NavAccessibilitySource.connected
        am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.let { s -> s.packageName == ctx.packageName && s.name.contains("NavAccessibilityService") } == true }
    }.getOrElse { NavAccessibilitySource.connected }

    /** Reconnect NGAY qua dadb (chạy nền). An toàn gọi nhiều lần. */
    fun reconnect(ctx: Context) {
        val app = ctx.applicationContext
        Thread { doReconnect(app) }.start()
    }

    /**
     * CẤP QUYỀN notification-listener NGAY trong app qua dadb uid-shell (`cmd notification allow_listener`).
     * Đường CHUẨN trên BYD IVI khoá: màn Settings "Truy cập thông báo" KHÔNG mở được (startActivity bị chặn →
     * toast hệ thống "IVI không hỗ trợ hoạt động này"), NHƯNG quyền này là quyền adb
     * (settings secure enabled_notification_listeners) mà uid shell (2000) qua loopback ĐƯỢC PHÉP đặt — y như
     * DashCast. Lần đầu có popup "Allow USB debugging" trên xe → bấm Allow 1 lần (key lưu ở filesDir).
     *
     * KHÁC [reconnect]: dùng cho lần THIẾU quyền (nút "Cấp quyền" / bật công tắc). Chỉ `allow_listener`
     * (KHÔNG `disallow` trước — lần đầu chưa có trong danh sách) rồi requestRebind + chờ bind để phản hồi UI.
     *
     * @param onResult gọi trên MAIN thread: true nếu listener đã bound sau khi grant, false nếu grant/nối lỗi.
     */
    fun selfGrant(ctx: Context, onResult: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val ok = doSelfGrant(app)
            onResult?.let { cb -> main.post { cb(ok) } }
        }.start()
    }

    /** Lõi blocking của [selfGrant]. Chạy trên thread nền của caller. Trả true nếu listener đã bound. */
    private fun doSelfGrant(app: Context): Boolean {
        if (!reconnecting.compareAndSet(false, true)) { Log.i(TAG, "grant/reconnect đang chạy — bỏ lần trùng"); return NavNotificationListener.connected }
        try {
            return runCatching {
                val keyPair = AdbKeys.ensure(app)
                val allowed = LocalDeviceShell.session(keyPair, LocalShellRetry.BACKGROUND_READ_CAP) { sh ->
                    sh("cmd notification allow_listener $COMP").ok
                }
                if (allowed != true) {
                    Log.e(TAG, "selfGrant: dadb allow_listener không chạy được (allowed=$allowed)")
                    return@runCatching NavNotificationListener.connected
                }
                NotificationListenerService.requestRebind(ComponentName(app, NavNotificationListener::class.java))
                var waited = 0
                while (waited < 4500 && !NavNotificationListener.connected) { Thread.sleep(300); waited += 300 }
                Log.i(TAG, "selfGrant xong sau ${waited}ms: bound=${NavNotificationListener.connected}")
                NavNotificationListener.connected
            }.getOrElse { Log.e(TAG, "selfGrant qua dadb LỖI (popup Allow chưa bấm?)", it); false }
        } finally { reconnecting.set(false) }
    }

    /**
     * CẤP QUYỀN Hỗ trợ (accessibility) cho [NavAccessibilityService] qua dadb uid-shell — cần cho T3 (nút vật
     * lý → trợ lý) VÀ cho booster đọc màn GMaps. Cùng lý do như [selfGrant]: màn Settings > Hỗ trợ trên IVI
     * khoá có thể không mở/không bật được, nhưng `settings put secure enabled_accessibility_services` từ uid
     * shell thì được. ĐỌC-SỬA-GHI để KHÔNG đá văng service hỗ trợ khác đang bật (append, không overwrite).
     *
     * Sau khi enable, còn VERIFY service BOUND thật (`dumpsys accessibility` "Bound services", không chỉ
     * "Enabled") rồi FORCE-REBIND bằng toggle nếu enabled-nhưng-chưa-bound — chữa bug sau reboot (voice-key +
     * screen-read chết) mà không cần toggle tay. Xem [forceRebindIfNeeded].
     *
     * @param reset khi true (toggle 'Nút vật lý' TẮT→BẬT): XÓA single-flight [grantingAcc] đang kẹt TRƯỚC khi
     *   thử (một grant trước bị TREO không ghim được cờ mãi mãi), rồi chạy grant TƯƠI + force-rebind → voice-key
     *   sống lại sau reboot mà KHÔNG cần restart app. reset=false (đường Nav+HUD thường) giữ single-flight bình
     *   thường. KHÔNG auto-loop/backoff — mỗi lần gạt là một lần thử.
     * @param onResult gọi trên MAIN thread: true nếu phiên dadb chạy được (đã append + bật accessibility).
     */
    fun grantAccessibility(ctx: Context, reset: Boolean = false, onResult: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            // RESET (toggle OFF→ON): clear a stuck single-flight left by a PRIOR HUNG grant BEFORE attempting, so
            // a pinned grantingAcc can't turn this (and every later) call into a no-op that only an app restart
            // could recover. The fresh grant + forceRebindIfNeeded then run inside doGrantAccessibility as usual.
            if (reset) grantingAcc.set(false)
            val ok = doGrantAccessibilityWithTimeout(app)
            onResult?.let { cb -> main.post { cb(ok) } }
        }.start()
    }

    /**
     * Chạy [doGrantAccessibility] trên worker thread rồi JOIN có TIMEOUT ([GRANT_TIMEOUT_MS]): một phiên dadb
     * TREO (đọc/ghi kẹt) KHÔNG thể ghim [grantingAcc] mãi mãi. Hết giờ → interrupt worker + ép
     * `grantingAcc.set(false)` để lần grant sau (kể cả reset toggle) chạy được thay vì no-op tới khi restart app.
     * MỘT lần thử / lời gọi — KHÔNG loop/backoff. doGrantAccessibility vẫn tự nhả cờ trong finally khi chạy xong.
     */
    private fun doGrantAccessibilityWithTimeout(app: Context): Boolean {
        val result = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = Thread { result.set(doGrantAccessibility(app)) }
        worker.start()
        worker.join(GRANT_TIMEOUT_MS)
        if (worker.isAlive) {
            Log.e(TAG, "grantAccessibility TIMEOUT ${GRANT_TIMEOUT_MS}ms → interrupt + nhả single-flight")
            worker.interrupt()
            grantingAcc.set(false)   // never let a hung dadb session pin the single-flight forever
            return false
        }
        return result.get()
    }

    private fun doGrantAccessibility(app: Context): Boolean {
        if (!grantingAcc.compareAndSet(false, true)) { Log.i(TAG, "grantAccessibility đang chạy — bỏ lần trùng"); return false }
        try {
            return runCatching {
                val keyPair = AdbKeys.ensure(app)
                LocalDeviceShell.session(keyPair, LocalShellRetry.BACKGROUND_READ_CAP) { sh ->
                    val cur = sh("settings get secure enabled_accessibility_services").output.trim()
                    val has = cur.split(':').any { it.trim() == ACC_COMP }
                    if (!has) {
                        val next = if (cur.isBlank() || cur == "null") ACC_COMP else "$cur:$ACC_COMP"
                        sh("settings put secure enabled_accessibility_services $next")
                    }
                    sh("settings put secure accessibility_enabled 1")
                    Log.i(TAG, "grantAccessibility xong (đã có sẵn=$has)")
                    // ENABLED ≠ BOUND: sau reboot service liệt kê trong enabled_accessibility_services nhưng
                    // KHÔNG chạy (không ở "Bound services") → onKeyEvent/booster chết. Ép rebind trên CÙNG phiên.
                    // #2 (owner 2026-09-23): trả BOUND THẬT (verify sau toggle), KHÔNG phải "dadb chạy xong" —
                    // để "Kiểm tra/Sửa ngay" báo đúng OK/FAIL khớp status, không nói dối.
                    forceRebindIfNeeded(keyPair, sh)
                } ?: false
            }.getOrElse { Log.e(TAG, "grantAccessibility qua dadb LỖI (popup Allow chưa bấm?)", it); false }
        } finally { grantingAcc.set(false) }
    }

    /**
     * FORCE-REBIND accessibility service khi ENABLED-nhưng-CHƯA-BOUND (trạng thái sau reboot: có trong
     * enabled_accessibility_services nhưng vắng khỏi `dumpsys accessibility` "Bound services", nên
     * onServiceConnected không chạy → onKeyEvent + screen-read chết). Chạy trên CÙNG phiên dadb với các lệnh
     * enable ở trên (đã trong single-flight [grantingAcc]).
     *
     * An toàn (chạy trên xe owner qua OTA):
     *  - CHỈ toggle khi xác nhận enabled-nhưng-chưa-bound. Đã bound → [AccessibilityRebind.accessibilityRebindWrites]
     *    trả rỗng → KHÔNG làm gì (không flicker). Settle trước để enable vừa ghi kịp bind tự nhiên (tránh toggle thừa).
     *  - Chuỗi lệnh: remove (bỏ ClusterNav, GIỮ OEM services) → pause → re-add + accessibility_enabled 1.
     *  - KHÔNG BAO GIỜ để danh sách ở trạng thái REMOVED: nếu đã remove mà re-add chưa xong (sleep bị interrupt /
     *    shell ném), `finally` re-add lại về trạng thái an toàn — thử trên CHÍNH phiên trước, nếu phiên đó đã
     *    chết thì mở PHIÊN MỚI để re-add (adbd loopback vẫn sống, chỉ 1 kết nối rớt), nên setting không bao giờ
     *    kẹt ở trạng thái removed dù phiên đứt giữa toggle. Mọi lỗi được catch/log, không làm văng app.
     */
    private fun forceRebindIfNeeded(keyPair: AdbKeyPair, sh: (String) -> LocalShellText): Boolean {
        // Let a fresh enable bind on its own first; only the post-reboot state needs the forced toggle.
        runCatching { Thread.sleep(REBIND_SETTLE_MS) }.onFailure { Thread.currentThread().interrupt(); return false }
        val current = sh("settings get secure enabled_accessibility_services").output.trim()
        val bound = AccessibilityRebind.isClusterNavBound(sh("dumpsys accessibility").output)
        val writes = AccessibilityRebind.accessibilityRebindWrites(current, bound, ACC_COMP)
        if (writes.isEmpty()) { Log.i(TAG, "accessibility đã BOUND — không toggle (tránh flicker)"); return true }

        val remove = writes.first()
        val reAdd = writes.drop(1)   // [re-add danh sách đầy đủ, accessibility_enabled 1] = trạng thái AN TOÀN cuối
        // BIND-SELFHEAL (2026-09-23, team báo phím vẫn tạch dưới CPU load cao): gộp remove + sleep + re-add thành
        // MỘT lệnh shell chạy TRÊN XE (một `sh()` = một round-trip dadb). Trước đây 4 lượt round-trip riêng
        // (remove → sleep máy chủ → re-add → enable) — dưới load 14, dadb chậm giữa các lượt ⇒ dễ bị cắt GIỮA
        // toggle (danh sách kẹt REMOVED / hết timeout). Gộp: nếu lệnh LỌT vào xe thì cả chuỗi (kể cả re-add)
        // chạy trên xe bất kể client đọc kết quả có timeout hay không ⇒ KHÔNG còn cửa "chỉ remove landed".
        val pauseSec = REBIND_TOGGLE_PAUSE_MS / 1000.0
        // remove ; sleep <pause> ; <re-add lệnh 1> ; <re-add lệnh 2...>  — tất cả trên MỘT dòng shell.
        val combined = "$remove ; sleep $pauseSec ; " + reAdd.joinToString(" ; ")
        var inRemovedState = false
        var reboundOk = false
        try {
            Log.i(TAG, "accessibility ENABLED nhưng CHƯA BOUND → toggle ép rebind (1 lệnh gộp, chống treo dưới load)")
            inRemovedState = true
            sh(combined)                 // 1 round-trip: cả remove+sleep+re-add chạy trên xe
            inRemovedState = false
            // #2 (owner 2026-09-23) — POLL bound NHIỀU NHỊP, không đọc 1 lần: dưới CPU load cao hệ bind CHẬM vài
            // giây sau toggle; đọc 1 lần ngay ⇒ luôn thấy false ⇒ "Sửa ngay" báo fail (hoặc báo OK dối). Poll cho
            // hệ thời gian bind; trả kết quả THẬT để nút không nói dối.
            for (attempt in 0 until REBIND_VERIFY_TRIES) {
                reboundOk = AccessibilityRebind.isClusterNavBound(sh("dumpsys accessibility").output)
                if (reboundOk) break
                runCatching { Thread.sleep(REBIND_VERIFY_EVERY_MS) }.onFailure { Thread.currentThread().interrupt(); break }
            }
            Log.i(TAG, "accessibility force-rebind xong: bound=$reboundOk")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "accessibility rebind bị interrupt giữa toggle", e)
        } finally {
            // NEVER leave enabled_accessibility_services in the REMOVED state — re-add on any partial failure.
            if (inRemovedState) {
                // First try on the SAME session. If that session is the very thing that broke (the common
                // cause of getting here), re-adding on it throws too — so fall back to a FRESH dadb session.
                // The loopback adbd is still up (only this one connection died), so the fresh re-add lands and
                // the setting is never left removed — not merely self-healed on the next grant.
                val recoveredSameSession = runCatching { reAdd.forEach { sh(it) } }.isSuccess
                if (recoveredSameSession) {
                    Log.w(TAG, "accessibility rebind: khôi phục RE-ADDED (an toàn) sau lỗi")
                } else {
                    val freshOk = LocalDeviceShell.session(keyPair, LocalShellRetry.BACKGROUND_READ_CAP) { s2 -> reAdd.forEach { s2(it) }; true } ?: false
                    if (freshOk) Log.w(TAG, "accessibility rebind: khôi phục RE-ADDED qua phiên MỚI (an toàn)")
                    else Log.e(TAG, "accessibility rebind: khôi phục re-add THẤT BẠI cả phiên cũ lẫn phiên MỚI")
                }
            }
        }
        return reboundOk
    }

    /**
     * Auto-ensure lúc mở app: xin rebind, chờ ~1.8s cho hệ thống bind; nếu listener vẫn CHƯA bound
     * ([NavNotificationListener.connected] == false) thì reconnect qua dadb. Không đụng gì nếu đã bound.
     */
    fun ensureConnected(ctx: Context) {
        val app = ctx.applicationContext
        Thread {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(app, NavNotificationListener::class.java))
                // R5: POLL ~300ms tới ~4.5s thay vì chờ cứng 1.8s — bind tự nhiên xong thì THOÁT SỚM (tránh dadb
                // disallow/allow thừa làm rớt nav vừa mới lên, trễ frame đầu vài giây).
                var waited = 0
                while (waited < 4500) {
                    if (NavNotificationListener.connected) { Log.i(TAG, "listener đã bound (${waited}ms) → khỏi dadb"); return@runCatching }
                    Thread.sleep(300); waited += 300
                }
                Log.i(TAG, "listener chưa bound sau ${waited}ms → reconnect qua dadb")
                doReconnect(app)
            }.onFailure { Log.e(TAG, "ensureConnected failed", it) }
        }.start()
    }

    /** Lõi blocking: dadb connect localhost:5555 → disallow → allow. Chạy trên thread nền của caller. */
    private fun doReconnect(app: Context) {
        if (!reconnecting.compareAndSet(false, true)) { Log.i(TAG, "reconnect đang chạy — bỏ lần trùng"); return }
        try {
            runCatching {
                val keyPair = AdbKeys.ensure(app)   // key CHUNG, sinh nguyên tử + khoá chung (chống đua với các client dadb khác)
                LocalDeviceShell.session(keyPair, LocalShellRetry.BACKGROUND_READ_CAP) { sh ->
                    sh("cmd notification disallow_listener $COMP")
                    Thread.sleep(1500)
                    sh("cmd notification allow_listener $COMP")
                }
                // Fallback cho chắc.
                NotificationListenerService.requestRebind(ComponentName(app, NavNotificationListener::class.java))
                Log.i(TAG, "reconnect qua dadb xong")
            }.onFailure { Log.e(TAG, "reconnect qua dadb LỖI (popup Allow chưa bấm?)", it) }
        } finally { reconnecting.set(false) }
    }
}
