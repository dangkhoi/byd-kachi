package com.byd.clusternav

import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellRetry
import com.byd.clusternav.carexec.LocalShellText
import com.byd.clusternav.modules.navaccess.A11yBindJournal
import com.byd.clusternav.modules.navaccess.A11yBindJournalStore
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates
import com.byd.clusternav.modules.navaccess.AccessibilityRebind
import dadb.AdbKeyPair
import com.byd.clusternav.modules.navaccess.NavAccessibilitySource
import com.byd.clusternav.navigation.NlsHealPolicy
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.byd.clusternav.modules.clustercast.DisplayParse
import com.byd.clusternav.modules.clustercast.StackParse
import com.byd.clusternav.modules.clustercast.simplified.ClusterDisplayResolver
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * BIND lại nav listener qua dadb (ADB local client, localhost:5555, uid=shell) — `cmd notification disallow/allow_listener`
 * y như DashCast. Lần đầu có popup "Allow USB debugging" trên xe → bấm Allow 1 lần (key lưu ở filesDir).
 *
 * ĐÍNH CHÍNH FIX286 (03/10, [ĐO nguồn]): câu cũ "firmware BYD BỎ QUA requestRebind" quy nhầm cho BYD — đó là ngữ nghĩa
 * AOSP: `requestRebind` chỉ gỡ "snooze" (r47 NMS `:3127-3139` → `ManagedServices.java:707-711`), không gắn lại một bộ
 * nghe đã CẤP mà chưa GẮN. Đường gắn lại thật (disallow → 1,5 s → allow) nay nằm ở [NlsHeal] / [NlsHealShell].
 *
 * - [reconnect]  : nút *Kết nối lại* — ép disallow→allow ngay, phiên HỎI, báo kết quả THẬT.
 * - [ensureConnected] : công tắc BẬT — chờ bind tự nhiên ≤4,5 s, CHƯA bound mới disallow→allow (không ngắt nav đang chạy).
 */
object NavConnect {
    private const val TAG = "NavConnect"
    // This app's own installed package = BuildConfig.APPLICATION_ID (com.byd.clusternav2). Class FQNs keep the
    // internal namespace com.byd.clusternav.* (unchanged) → component = "<appId>/com.byd.clusternav.<Class>".
    // Fully isolated from the legacy com.byd.clusternav app.
    /** `internal` (FIX286): [NlsHeal] dùng CHUNG chuỗi này — một nguồn, không chép. */
    internal val COMP = "${BuildConfig.APPLICATION_ID}/com.byd.clusternav.NavNotificationListener"
    /** `internal` (2.83) cho đúng một người đọc nữa: bộ đo kẹt của [A11yLifecycleHeal] — cùng một chuỗi, không chép. */
    internal val ACC_COMP = "${BuildConfig.APPLICATION_ID}/com.byd.clusternav.modules.navaccess.NavAccessibilityService"
    private val grantingAcc = java.util.concurrent.atomic.AtomicBoolean(false)    // single-flight cho grantAccessibility (dadb read-modify-write)
    private val grantGen = java.util.concurrent.atomic.AtomicInteger(0)          // #6: dấu thế hệ chống session timed-out ghi chồng

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
    fun isAccessibilityBound(ctx: Context): Boolean =
        boundPerAccessibilityManager(ctx) ?: NavAccessibilitySource.connected

    /**
     * BOUND theo AccessibilityManager, hoặc `null` khi binder không hỏi được (service null / ném) — KHÔNG rơi về cờ RAM
     * ở đây, để [doGrantResult] chỉ bỏ đường shell khi có câu trả lời THẬT "đã bound".
     *
     * [ĐO AOSP android-10.0.0_r47 `AccessibilityManagerService.java:653-679`] `getEnabledAccessibilityServiceList`
     * duyệt `userState.mBoundServices` — CÙNG danh sách mà `dumpsys accessibility` in ở "Bound services:{" (`:2563`).
     * ⇒ đây chính là "Bound services" đọc qua binder, không phải "Enabled services" (`mEnabledServices`, `:2575`).
     * Xem KDoc [AccessibilityHealGates].
     */
    fun boundPerAccessibilityManager(ctx: Context): Boolean? = runCatching {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            ?: return@runCatching null
        am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.let { s -> s.packageName == ctx.packageName && s.name.contains("NavAccessibilityService") } == true }
    }.getOrNull()

    /**
     * Nút *Kết nối lại*: disallow→allow NGAY qua phiên HỎI (FIX286 S2 — 2.85 đi phiên NỀN nên bị cổng READY-AT-HOME
     * chặn khi kênh chưa lên mà vẫn log "xong"). [onResult] trên luồng chính, kết quả THẬT (đọc lại dump).
     */
    fun reconnect(ctx: Context, onResult: (NlsHealPolicy.Outcome) -> Unit = {}) = NlsHeal.userReconnect(ctx, onResult)

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
        if (!NlsHeal.busy.compareAndSet(false, true)) { Log.i(TAG, "grant/reconnect đang chạy — bỏ lần trùng"); return NavNotificationListener.connected }
        try {
            return runCatching {
                val keyPair = AdbKeys.ensure(app)
                // FIX286 S2: chỉ hai chỗ gọi, cả hai là người dùng vừa bấm (công tắc / Kết nối lại) ⇒ phiên HỎI — phiên NỀN
                // bị cổng READY-AT-HOME chặn khi kênh chưa lên (2.85) đúng lúc người dùng đang chờ kết quả.
                val allowed = LocalDeviceShell.session(keyPair, LocalShellRetry.USER_READ_CAP) { sh ->
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
        } finally { NlsHeal.busy.set(false) }
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
        grantAccessibilityDetailed(ctx, reset) { r -> onResult?.invoke(r == GrantResult.BOUND) }
    }

    /**
     * Kết quả cấp quyền Hỗ trợ, PHÂN BIỆT ba ca — để UI báo ĐÚNG (owner 2026-09-25: toast cũ đổ oan "bấm Allow
     * USB debugging" trong khi dadb rõ ràng chạy được, app cài xong mọi thứ đều qua dadb):
     *  • [BOUND] — service đã gắn thật (dumpsys "Bound services"), phím sống.
     *  • [NOT_BOUND] — dadb CHẠY, đã ghi enabled_accessibility_services, nhưng service chưa BIND (ROM `ssc_skip`
     *    drop bind, hoặc hệ chưa kịp bind). KHÔNG phải lỗi USB debugging. Thử lại / bật tay ở Cài đặt > Hỗ trợ.
     *    ⚠ ĐÍNH CHÍNH 2026-09-28: chỗ này từng ghi "xe tải cao" — [ĐO xe 2026-09-28] lúc phím chết xe ĐỨNG YÊN,
     *    tiến trình sống liên tục 10 g 13 ph, và gốc là lỗ hổng framework (xem [escalateIfStuck]). Quy kết cho
     *    tải là SAI và đã làm cả buổi chẩn đoán đi chệch hướng (CLAUDE.md §2).
     *  • [DADB_FAILED] — phiên dadb NÉM (auth/kết nối) — đây MỚI là ca "bấm Allow USB debugging".
     *  • [RESTARTING] — phát hiện KẸT ở `mBindingServices` và ĐÃ bắn lệnh tự force-stop + lắp lại: giao diện sắp
     *    khởi động lại một nhịp (xem [escalateIfStuck]).
     */
    enum class GrantResult { BOUND, NOT_BOUND, DADB_FAILED, RESTARTING }

    fun grantAccessibilityDetailed(ctx: Context, reset: Boolean = false, onResult: ((GrantResult) -> Unit)? = null) {
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            if (reset) grantingAcc.set(false)
            val r = doGrantResultWithTimeout(app, userAsked = reset)
            onResult?.let { cb -> main.post { cb(r) } }
        }.start()
    }

    private fun doGrantResultWithTimeout(app: Context, userAsked: Boolean): GrantResult {
        val result = java.util.concurrent.atomic.AtomicReference(GrantResult.DADB_FAILED)
        val worker = Thread { result.set(doGrantResult(app, userAsked)) }
        worker.start()
        worker.join(GRANT_TIMEOUT_MS)
        if (worker.isAlive) {
            Log.e(TAG, "grantAccessibility TIMEOUT ${GRANT_TIMEOUT_MS}ms → interrupt + nhả single-flight")
            worker.interrupt()
            grantGen.incrementAndGet()
            grantingAcc.set(false)
            return GrantResult.DADB_FAILED
        }
        return result.get()
    }

    /** Single-flight riêng của [escalateOnLifecycle] (hai lớp cùng chạy trên một luồng nối tiếp, đây là chốt thứ hai). */
    private val lifecycleEscalating = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * ĐƯỜNG VÀO LEO THANG cho LỚP 1 (tắt máy) / LỚP 2 (mở xe) — 2.83, owner chốt 2026-09-29.
     *
     * Chỉ gọi SAU KHI [A11yLifecycleHeal] đã thấy KẸT BỀN (hai lần `dumpsys accessibility` cách nhau ≥
     * [AccessibilityHealGates.STUCK_CONFIRM_GAP_MS]). Vì vậy đi THẲNG vào [escalateIfStuck], BỎ nấc toggle của
     * [grantViaShell]: [ĐO AOSP `:1630-1631`] toggle vô hiệu ở ca kẹt, mà nó tốn ~8 s (settle 1,2 s + toggle 0,8 s +
     * 6 lượt đọc) — quá nửa ân hạn [AccessibilityHealGates.MO_XE_GRACE_MS]. [escalateIfStuck] vẫn đọc lại
     * `dumpsys accessibility` lần thứ ba trước khi quyết.
     *
     * CỐ Ý KHÔNG lấy single-flight [grantingAcc] của đường grant: [ĐO xe c2 29/09] lượt grant của Preflight chạy
     * 11:34:19 → 11:34:29,7 — phủ trọn ân hạn mở xe (màn bật 11:34:14) ⇒ dùng chung cờ là lớp 2 KHÔNG BAO GIỜ tới
     * lượt. Hai đường cùng đọc-sửa-ghi `enabled_accessibility_services` vẫn an toàn: cả hai GIỮ nguyên dịch vụ hãng
     * và luôn thêm mình vào cuối; lượt ghi cuối cùng là của lệnh tách rời (sau `sleep 4`), cũng chứa mình ⇒ không có
     * thứ tự đan xen nào để lại danh sách thiếu mình hoặc mất dịch vụ hãng.
     *
     * Hạn giờ: phiên chạy với [LocalShellRetry.BACKGROUND_READ_CAP] (hạn ĐỌC 30 s — một socket câm không treo mãi,
     * F6) và không cần luồng thợ + `join` như [doGrantResultWithTimeout]: ở đây không có UI nào chờ kết quả, và cổng
     * [fireGate] hỏi lại pha NGAY trước khi bắn nên một lượt chậm tự bỏ thay vì giết muộn.
     *
     * @param fireGate hỏi lại NGAY TRƯỚC khi bắn (pha còn đúng không — [AccessibilityHealGates.lifecycleFireAllowed]).
     */
    internal fun escalateOnLifecycle(
        ctx: Context,
        phase: AccessibilityHealGates.HealPhase,
        fireGate: () -> Boolean,
    ): GrantResult {
        val app = ctx.applicationContext
        if (phase == AccessibilityHealGates.HealPhase.RUNNING) return GrantResult.NOT_BOUND   // lớp 3 không vào cửa này
        if (!lifecycleEscalating.compareAndSet(false, true)) return GrantResult.NOT_BOUND
        try {
            return runCatching {
                LocalDeviceShell.session(AdbKeys.ensure(app), LocalShellRetry.BACKGROUND_READ_CAP) { sh ->
                    escalateIfStuck(app, sh, userAsked = false, phase = phase, fireGate = fireGate)
                } ?: GrantResult.DADB_FAILED
            }.getOrElse { Log.e(TAG, "leo thang $phase qua dadb NÉM", it); GrantResult.DADB_FAILED }
        } finally { lifecycleEscalating.set(false) }
    }

    private fun doGrantResult(app: Context, userAsked: Boolean): GrantResult {
        if (!grantingAcc.compareAndSet(false, true)) { Log.i(TAG, "grantAccessibility đang chạy — bỏ lần trùng"); return GrantResult.NOT_BOUND }
        val myGen = grantGen.incrementAndGet()   // #6: dấu thế hệ của lượt grant này
        try {
            // B1 (BG-11/BG-14): hỏi binder TRƯỚC — đã bound ⇒ 0 lệnh shell, 0 ghi Secure Settings (trước đây mỗi lượt
            // watchdog ghi `accessibility_enabled 1` + sleep 1,2 s + dumpsys chỉ để kết luận "đã BOUND — không toggle").
            // Binder không trả lời được (null) ⇒ đi đường shell như cũ — không mất tự-heal 1.78.
            return AccessibilityHealGates.grantOrSkip(
                boundPerAccessibilityManager(app),
                skipped = { Log.i(TAG, "accessibility đã BOUND (AccessibilityManager) → bỏ dadb"); GrantResult.BOUND },
            ) { grantViaShell(app, myGen, userAsked) }
        } finally { grantingAcc.set(false) }
    }

    /** Đường dadb đầy đủ (đọc-sửa-ghi enabled list → verify dumpsys → toggle ép rebind). Chỉ chạy khi CHƯA bound. */
    private fun grantViaShell(app: Context, myGen: Int, userAsked: Boolean): GrantResult =
        runCatching {
            val keyPair = AdbKeys.ensure(app)
            // READY-AT-HOME R1.1: bấm tay (*Kiểm tra / Sửa ngay*) = đường HỎI — cổng thi hành cho qua, hộp "Cho phép gỡ
            // lỗi USB?" bung đúng lúc người dùng đang nhìn. Đường nền (watchdog, B1, alarm) giữ chính sách NỀN cũ.
            LocalDeviceShell.session(keyPair, if (userAsked) LocalShellRetry.USER_READ_CAP else LocalShellRetry.BACKGROUND_READ_CAP) { sh ->
                val cur = sh("settings get secure enabled_accessibility_services").output.trim()
                val has = cur.split(':').any { it.trim() == ACC_COMP }
                if (!has) {
                    val next = when {
                        cur == "null" -> ACC_COMP
                        cur.isBlank() -> ACC_COMP
                        else -> "$cur:$ACC_COMP"
                    }
                    sh("settings put secure enabled_accessibility_services \"$next\"")
                }
                sh("settings put secure accessibility_enabled 1")
                Log.i(TAG, "grantAccessibility xong (đã có sẵn=$has)")
                if (myGen != grantGen.get()) { Log.i(TAG, "grant gen cũ ($myGen≠${grantGen.get()}) → bỏ toggle"); return@session GrantResult.NOT_BOUND }
                // dadb CHẠY tới đây (ghi được settings) ⇒ KHÔNG phải lỗi USB debugging. forceRebind trả BOUND thật.
                if (forceRebindIfNeeded(keyPair, sh)) GrantResult.BOUND else escalateIfStuck(app, sh, userAsked)
            } ?: GrantResult.DADB_FAILED   // session mở không được ⇒ dadb/auth
        }.getOrElse { Log.e(TAG, "grantAccessibility qua dadb NÉM (auth/kết nối)", it); GrantResult.DADB_FAILED }

    /**
     * NẤC CUỐI của thang chữa — chỉ chạy khi [forceRebindIfNeeded] (nấc toggle) đã KHÔNG đưa được về BOUND.
     *
     * [ĐO xe 2026-09-28 + AOSP android-10.0.0_r47] có một trạng thái mà nấc toggle **không bao giờ** gỡ được:
     * component nằm trong `mBindingServices`. `AccessibilityManagerService.updateServicesLocked` mở đầu vòng lặp
     * bằng `if (mBindingServices.contains(componentName)) continue;` (`:1630-1631`) — dòng đó đứng TRÊN cả
     * `bindLocked()` (`:1642`) lẫn `unbindLocked()` (`:1645`) ⇒ vừa không gắn lại được vừa không gỡ được bằng
     * bất kỳ lệnh ghi settings nào. Vào trạng thái này khi một dịch vụ ĐANG GẮN bị đứt: `binderDied()` →
     * `serviceDisconnectedLocked` (`:4114-4117`) **đẩy ngược component vào `mBindingServices`**, mà chú thích
     * `:398` cho thấy ý đồ chỉ tính cho ca THAY GÓI — đứt vì lý do khác thì không ai dọn. Owner [ĐO nhiều lần]:
     * cài mới thì chạy tốt, để xe qua đêm standby rồi sáng bật lên mới kẹt.
     *
     * Đường thoát DUY NHẤT chứng minh được: `am force-stop` gói mình → `onHandleForceStop` (`:453-484`) gỡ khỏi
     * CẢ `mEnabledServices` LẪN `mBindingServices` rồi ghi đĩa ⇒ **lắp lại là bắt buộc**. Lệnh chạy TÁCH RỜI
     * (xem [AccessibilityRebind.forceStopRebindCommand]) vì chính tiến trình này sắp bị giết.
     *
     * Cổng giữ trước khi giết (xem [AccessibilityHealGates.healStep]) — 2.83, owner chốt 2026-09-29: đúng là ca
     * KẸT · phím-thoại BẬT (cùng cổng với watchdog 30 s — chính nó là đường lắp lại nếu nửa sau của lệnh tách rời
     * không chạy, spec R-nf5) · và PHA: đang chạy ([AccessibilityHealGates.HealPhase.RUNNING], lớp 3) thì KHÔNG tự
     * giết nữa; chỉ lượt tắt máy / mở xe (lớp 1/2, qua [escalateOnLifecycle]) hoặc người dùng tự bấm "Sửa ngay".
     * Cổng "không app khách" và hạn mức một-lần-mỗi-lần-nổ-máy của 2.79 đã GỠ: [ĐO xe 29/09] cổng app khách chặn
     * đúng ca cần chữa (ô đã có app khi lượt chữa tới nơi), còn kẹt sinh ra ở MỖI lần tắt máy. App khách vẫn được
     * ĐO và ghi vào log (bằng chứng lượt giết có chạm app nào trong ô không).
     *
     * @param phase pha vòng đời; mặc định [AccessibilityHealGates.HealPhase.RUNNING] cho đường grant (watchdog /
     *   alarm / Preflight / nút) — đường duy nhất được đổi pha là [escalateOnLifecycle].
     * @param fireGate hỏi lại NGAY TRƯỚC khi ghi marker + bắn; `false` ⇒ không leo (pha đã qua).
     */
    private fun escalateIfStuck(
        app: Context,
        sh: (String) -> LocalShellText,
        userAsked: Boolean,
        phase: AccessibilityHealGates.HealPhase = AccessibilityHealGates.HealPhase.RUNNING,
        fireGate: () -> Boolean = { true },
    ): GrantResult {
        // Lượt grant này có thể đã bị [doGrantResultWithTimeout] BỎ (join hết giờ → interrupt) trong khi thân
        // vẫn chạy nốt. Caller đã trả kết quả cho UI rồi ⇒ tuyệt đối không được tự giết tiến trình sau lưng nó.
        if (Thread.currentThread().isInterrupted) {
            Log.w(TAG, "lượt grant đã hết giờ (interrupted) → KHÔNG leo nấc force-stop")
            return GrantResult.NOT_BOUND
        }
        val stuck = AccessibilityRebind.isInBindingServices(sh("dumpsys accessibility").output, ACC_COMP)
        // Lớp 3 (2.83): kết quả ĐO bằng dump ⇒ watchdog 30 s thôi toggle vô ích khi đã biết là kẹt (KDoc A11yLifecycleHeal).
        A11yLifecycleHeal.noteStuckDump(stuck)
        // R7 — ghi NGAY tại chỗ phát hiện: đây là nơi DUY NHẤT phân biệt được KẸT với chỉ-là-chưa-gắn
        // (cần bản dump, watchdog 30 s không đọc nổi mỗi nhịp). Nhật ký nhờ đó có đủ ba trạng thái.
        A11yBindJournalStore.record(
            app,
            if (stuck) A11yBindJournal.State.STUCK else A11yBindJournal.State.NOT_BOUND,
            note = A11yBindJournal.grantNote(userAsked, phase),
        )
        // Màn ảo của các Ô do CHÍNH tiến trình này tạo ⇒ chúng chết theo ta, và app khách trong đó là thứ rơi lại
        // thành mảng đen ([ĐO xe 2026-09-28]). Đọc chủ sở hữu thật từ `dumpsys display` (cùng lệnh dò đã proven
        // của cast) thay vì đoán "display ≥ 1 là cụm" — [ĐO xe 2026-09-15] display 1 chính là `kachi-slot-0`.
        // Không đọc được ⇒ `null` ⇒ cổng ĐÓNG.
        val displayDump = sh(ClusterDisplayResolver.DETECT_CMD).output
        val ownVds = if (displayDump.isBlank()) null else DisplayParse.ownedVirtualDisplayIds(displayDump, app.packageName)
        val noGuest = StackParse.noGuestAppVisible(StackParse.parse(sh("am stack list").output), app.packageName, ownVds)
        val step = AccessibilityHealGates.healStep(
            bound = false,
            stuckInBinding = stuck,
            // R-nf5: đường tự động dùng ĐÚNG cổng của watchdog 30 s (phím-thoại bật) — xem KDoc `wanted`.
            wanted = Prefs.voiceKeyEnabled(app),
            userAsked = userAsked,
            phase = phase,
        )
        val where = "kẹt=$stuck, không app khách=$noGuest, ô của mình=$ownVds, tay=$userAsked, pha=$phase"
        if (step != AccessibilityHealGates.HealStep.FORCE_STOP) {
            Log.i(TAG, "a11y chưa bound ($where) → nấc $step, KHÔNG leo")
            return GrantResult.NOT_BOUND
        }
        val cur = sh("settings get secure enabled_accessibility_services").output.trim()
        // Bấm tay ⇒ LUÔN về màn nhà; lớp 1/2 ⇒ chỉ khi có cửa sổ mồ côi (KDoc `AccessibilityRebind.HomeTail`).
        val cmd = AccessibilityRebind.forceStopRebindCommand(cur, app.packageName, ACC_COMP, homeTail = AccessibilityRebind.homeTailFor(userAsked))
        if (cmd.isBlank()) {
            Log.e(TAG, "a11y KẸT nhưng không dựng được lệnh (gói lệch component?) → không leo")
            return GrantResult.NOT_BOUND
        }
        // Cổng CUỐI ở tầng thi hành (CLAUDE.md §5): lớp 1/2 hỏi lại pha còn đúng không (màn vẫn tắt / vẫn trong ân
        // hạn mở xe) ngay trước khi bắn — giữa lần đo đầu và đây có thể đã trôi vài giây, hoặc máy đã ngủ rồi thức.
        // Hỏi lại cả cờ interrupt: lượt grant có thể bị [doGrantResultWithTimeout] bỏ GIỮA chừng (sau chốt đầu hàm).
        if (Thread.currentThread().isInterrupted || !fireGate()) {
            Log.w(TAG, "a11y KẸT nhưng pha $phase đã qua ($where) → KHÔNG leo, để lớp khác / nút lo")
            return GrantResult.NOT_BOUND
        }
        val now = SystemClock.elapsedRealtime()
        // marker TRƯỚC khi đổi state ngoài (CLAUDE.md §5) — ghi đồng bộ. Nó là chốt chống vòng lặp của MỌI lượt tự động
        // (và là gốc của `AccessibilityHealGates.ownHealChild` cho ân hạn khởi động) ⇒ ghi hỏng thì không tự bắn.
        val marked = Prefs.setA11yEscalatedAt(app, now)
        if (!AccessibilityHealGates.autoFireAllowed(userAsked, marked)) {
            Log.e(TAG, "a11y KẸT nhưng không ghi được mốc leo ($where) → KHÔNG tự leo (tránh vòng lặp giết launcher)")
            return GrantResult.NOT_BOUND
        }
        Log.w(TAG, "a11y KẸT trong Binding services ($where) → tự force-stop + lắp lại; giao diện khởi động lại một nhịp")
        sh(cmd)
        return GrantResult.RESTARTING
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
        val bound = AccessibilityRebind.isClusterNavBound(sh("dumpsys accessibility").output, ACC_COMP)
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
                reboundOk = AccessibilityRebind.isClusterNavBound(sh("dumpsys accessibility").output, ACC_COMP)
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
     * Công tắc *Dẫn đường lên cụm đồng hồ* BẬT (FIX286 S2): xin rebind, chờ callback tự nhiên ≤4,5 s; CHƯA bound mới
     * disallow→allow qua phiên HỎI. Không đụng gì nếu đã bound. [onResult] trên luồng chính, kết quả THẬT.
     */
    fun ensureConnected(ctx: Context, onResult: (NlsHealPolicy.Outcome) -> Unit = {}) = NlsHeal.userEnsure(ctx, onResult)
}
