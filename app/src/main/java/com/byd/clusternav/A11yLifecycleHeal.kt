package com.byd.clusternav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.modules.navaccess.A11yBindJournal
import com.byd.clusternav.modules.navaccess.A11yBindJournalStore
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.BindObservation
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.HealPhase
import com.byd.clusternav.modules.navaccess.KeyReadyPlan
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * TỰ CHỮA PHÍM VÔ-LĂNG THEO VÒNG ĐỜI XE — lớp 1 (tắt máy) + lớp 2 (mở xe), 2.83, owner chốt 2026-09-29.
 *
 * ## Gốc rễ [ĐO xe 29/09, tái hiện 2 lần từ trạng thái sạch]
 * Mỗi lần TẮT MÁY, `AccModeManagerService` của BYD giết cả ba tiến trình Kachi (`am_kill … stop com.byd.launcher`,
 * `c2-logcat` 11:33:25.170–.182) mà KHÔNG phát `PACKAGE_RESTARTED` ⇒ dịch vụ Hỗ trợ đang Bound bị đẩy vào
 * `mBindingServices` và không bao giờ gắn lại ([ĐO AOSP android-10.0.0_r47 `AccessibilityManagerService.java:4114-4117`,
 * `:1630-1631`]). Android dựng lại Kachi (HOME) 0,3 s sau (`am_proc_start` 11:33:25.515) — lúc đó màn ĐÃ tắt
 * (`screen_toggled 0` 11:33:24.475). Lượt chữa của 2.81 chỉ tới khi mở xe (11:34:29) và bị cổng "app khách" chặn vì
 * ô đã có app.
 *
 * ## Hai lớp ở đây (lớp 3 — đang chạy — là nút "Kiểm tra / Sửa ngay", không tự chữa)
 *  • LỚP 1 — [install] chạy ở MỌI lần tiến trình launcher khởi động; nếu màn KHÔNG tương tác
 *    (`PowerManager.isInteractive() == false`) thì mở một lượt [HealPhase.TAT_MAY].
 *  • LỚP 2 — bộ thu `ACTION_SCREEN_ON` đăng ký ĐỘNG trên applicationContext, sống cùng tiến trình: mỗi lần màn bật
 *    SAU MỘT LẦN TẮT MÁY ([AccessibilityHealGates.moXeFollowsTatMay] — tắt/bật màn lúc đang lái không tính) mở một
 *    lượt [HealPhase.MO_XE] trong ân hạn [AccessibilityHealGates.MO_XE_GRACE_MS].
 *  • LỚP 2 MỞ RỘNG — ÂN HẠN KHỞI ĐỘNG (READY-AT-HOME 02/10, [onBootGrace]): tiến trình DỰNG LẠI lúc màn ĐANG bật (BYD
 *    giết lúc màn sáng, crash, OOM) không có `ACTION_SCREEN_ON` nào để lớp 2 bắt ⇒ [ĐO E2E máy ảo 02/10 ca 1] phím kẹt
 *    tới lần tắt màn sau. Lượt [HealPhase.KHOI_DONG] chạy MỘT lần mỗi tiến trình, chỉ trong
 *    [AccessibilityHealGates.BOOT_GRACE_MS] kể từ lúc tiến trình bật, cổng thuần
 *    [AccessibilityHealGates.bootGraceMayRun]. Cùng thân [healIfStuck], cùng đường leo — không có đường thứ hai. Màn TẮT
 *    giữa lượt (đã thấy kẹt, còn trong ân hạn) ⇒ trao LỚP 1 nguyên vẹn ([handOffToTatMay], [ĐO E2E C6]).
 *  • LỚP 3 — CHỈ ĐO, không chữa: nhớ lần dump gần nhất nói KẸT ([noteStuckDump]) để watchdog 30 s (và alarm 60 s
 *    `RebindReceiver` khi keep-alive chết) thôi toggle vô ích,
 *    và kiểm lại chậm ([recheckRunningStuck], mỗi [AccessibilityHealGates.STUCK_RECHECK_MS]) trên cùng luồng nối tiếp.
 *
 * ⚠ Vì sao bộ thu nằm ở tầng TIẾN TRÌNH chứ không ở FGS keep-alive: [ĐO xe c2] màn bật 11:34:14.236 (broadcast xong
 * 11:34:15.242) nhưng `VoiceKeyKeepAliveService` lên lúc 11:34:18.725 — BYD đã giết nó lúc tắt máy và chỉ màn chính
 * gọi `sync()` lại khi được resume. Đăng ký trong service là LỠ đúng lần màn bật cần bắt. Tiến trình launcher thì
 * sống suốt khoảng tắt máy (HOME, được dựng lại ngay), nên bộ thu ở đây bắt được.
 *
 * API [ĐO AOSP android-10.0.0_r47 + Context7]: `Intent.ACTION_SCREEN_ON` "cannot receive through components declared
 * in manifests, only by explicitly registering … Context.registerReceiver()" (`Intent.java:2204-2226`); nó được gửi
 * kèm `FLAG_RECEIVER_REGISTERED_ONLY | FLAG_RECEIVER_FOREGROUND` dạng ordered (`Notifier.java:176-178`, `:748-754`)
 * ⇒ `onReceive` phải trả ngay (chỉ lấy mốc + đẩy sang luồng nền). `PowerManager.isInteractive()` = "device is in an
 * interactive state", và SCREEN_ON/OFF được gửi mỗi khi trạng thái này đổi (`PowerManager.java:1342-1382`). Context7
 * (developer.android.com `ContextWrapper.registerReceiver`): cờ `RECEIVER_EXPORTED/NOT_EXPORTED` KHÔNG bắt buộc khi
 * chỉ đăng ký broadcast hệ thống — cùng cách `VoiceWakeService` đang đăng ký SCREEN_ON/OFF (đã qua `lintRelease`).
 *
 * ## Chống vòng lặp
 *  • Một lượt mỗi SỰ KIỆN: marker `commit()` TRƯỚC khi đo (CLAUDE.md §5) — [Prefs.setA11yTatMayAt] /
 *    [Prefs.setA11yMoXeAt]; luật dedupe thuần ở [AccessibilityHealGates.tatMayMayRun] / [AccessibilityHealGates.moXeFresh].
 *  • Chính lượt force-stop của lớp 1 làm Android dựng lại Kachi lúc màn vẫn tắt ⇒ tiến trình mới thấy claim của
 *    cùng lần tắt máy (chưa có lần mở xe nào xen giữa, trong [AccessibilityHealGates.TAT_MAY_SAME_EVENT_MS]) ⇒ bỏ.
 *  • Lượt force-stop của lớp 2 làm Kachi dựng lại lúc màn ĐANG bật ⇒ không phải lớp 1, và không có SCREEN_ON mới.
 *    Nó LẠI là ứng viên ân hạn khởi động — chốt bằng mốc leo `a11y_forcestop_elapsed` (ghi `commit()` TRƯỚC mỗi lần bắn,
 *    lượt tự động không bắn nếu ghi hỏng — [AccessibilityHealGates.autoFireAllowed]) + claim chấm điểm: tiến trình ĐẦU
 *    TIÊN bật sau mốc đó là CON của lượt chữa ⇒ KHÔNG leo ([AccessibilityHealGates.ownHealChild]). Cùng chốt cho con của
 *    lượt khởi động, của nút *Sửa ngay*, và của lớp 1 (lớp 1 dựng lại lúc màn tắt, đằng nào cũng không qua cổng
 *    `tương tác`). Một lần giết MỚI từ bên ngoài sau đó vẫn được chữa (2.83 R-A4).
 *  • KẸT phải BỀN: hai lần `dumpsys accessibility` cách ≥ [AccessibilityHealGates.STUCK_CONFIRM_GAP_MS]; ngay sau
 *    force-stop, `Binding` có thể có mặt TẠM THỜI trong lúc hệ gắn lại bình thường.
 *  • Hạn mức cũ `a11y_forcestop_elapsed` (một lần mỗi lần nổ máy, nhả khi xe thức sau ngủ dài) KHÔNG còn chặn gì:
 *    lớp 3 tự động không leo nữa, nút bấm vẫn bỏ qua nó như trước, lớp 1/2 dùng hạn mức theo sự kiện. Mốc đó VẪN được
 *    ghi trước mỗi lần bắn (marker §5, màn Chẩn đoán, chấm điểm "sau-chua-*" của watchdog và của [scoreAfterHeal]).
 */
object A11yLifecycleHeal {

    private const val TAG = "A11yLifecycle"

    /** Nhịp hỏi lại pha trong lúc chờ giữa hai lần đo (màn bật giữa lượt tắt-máy ⇒ nhường ngay cho lớp 2). */
    private const val PHASE_POLL_MS = 250L

    /**
     * Tiến trình khởi động ĐẦU TIÊN trong chừng này sau mốc leo = do CHÍNH lượt force-stop trước dựng lại ⇒ chấm điểm
     * (các lần khởi động sau trong cùng cửa sổ thì không — [Prefs.a11yScoredFor]).
     */
    private const val AFTER_HEAL_WINDOW_MS = AccessibilityHealGates.OWN_HEAL_WINDOW_MS

    /** Chấm điểm sau mốc leo chừng này: 4 s `sleep` của lệnh tách rời + thời gian hệ gắn lại dịch vụ. */
    private const val AFTER_HEAL_SCORE_DELAY_MS = 15_000L

    private val installed = AtomicBoolean(false)

    /**
     * Tiến trình NÀY là tiến trình do chính lượt force-stop trước dựng lại — tức nó vừa NHẬN chấm điểm lượt leo
     * ([AccessibilityHealGates.firstStartAfterHeal] + claim `commit()` ở [onProcessStart]). Sự thật THEO TIẾN TRÌNH
     * nên sống trong RAM là đúng phạm vi; chỉ dùng để GẮN NHÃN nhật ký, không quyết định đổi gì trên hệ thống (§5).
     *
     * Vì sao có — [ĐO máy ảo 29/09, E2E 2.83 vòng 2, 15:51:00] watchdog 30 s gắn `sau-chua-*` cho nhịp đầu của MỌI tiến
     * trình mới khi mốc leo còn trong lần nổ máy này ⇒ một lượt giết kiểu BYD 90 s sau lượt "Sửa ngay" ghi
     * `NOT_BOUND note=sau-chua-VAN-TAT` cho một lượt chữa ĐÃ ăn. Hỏi cờ này thì nhãn chỉ rơi đúng vào tiến trình sinh ra
     * từ lượt chữa. Chưa kịp đặt (luồng nền chậm hơn nhịp đầu) ⇒ nhãn thường: thà thiếu một nhãn còn hơn một nhãn oan.
     */
    private val bornFromOwnHeal = AtomicBoolean(false)

    /** Xem [bornFromOwnHeal] — watchdog 30 s hỏi trước khi gắn nhãn `sau-chua-*` cho nhịp đầu. */
    internal fun bornFromOwnHeal(): Boolean = bornFromOwnHeal.get()

    /** MỘT luồng nối tiếp cho cả hai lớp + chấm điểm: không bao giờ có hai lượt đo/leo chạy đè nhau. */
    private val worker = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "kachi-a11y-lifecycle").apply { isDaemon = true }
    }

    /**
     * Móc DUY NHẤT, gọi từ [KachiApplication.onCreate] ở tiến trình launcher (không ở `:tts`/`:wake` — dịch vụ Hỗ trợ
     * sống ở tiến trình chính). Chỉ làm hai việc rẻ trên luồng chính (hỏi binder PowerManager, đăng ký bộ thu); mọi
     * đọc prefs / shell đi luồng nền.
     */
    fun install(ctx: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        val startedAt = SystemClock.elapsedRealtime()
        val interactive = interactive(app)
        startedNonInteractive = interactive == false   // READY-AT-HOME §4.8 — xem [moXePending]
        // Ân hạn khởi động: báo "đang xét" NGAY (trước khi luồng nền kịp chạy — cùng mẫu bộ thu màn bật), nhả trong
        // `finally` của lượt [onBootGrace]. Chỉ tiến trình bật lúc màn SÁNG mới có thể là ứng viên.
        bootGraceBusy.set(interactive == true)
        try {
            app.registerReceiver(ScreenOnReceiver(app), IntentFilter(Intent.ACTION_SCREEN_ON))
        } catch (e: RuntimeException) {
            // Không bao giờ để việc đăng ký làm sập Application.onCreate của launcher (HOME sập = crash-loop mỗi lần
            // tắt máy). Mất lớp 2 thì lớp 1 + nút vẫn còn; ghi lỗi to, không nuốt câm.
            Log.e(TAG, "không đăng ký được bộ thu màn bật — lớp 2 tắt trong tiến trình này", e)
        }
        submit("khởi động (tương tác=$interactive)") { onProcessStart(app, interactive, startedAt) }
        // Đường MỚI xuống CUỐI (CLAUDE.md §6): xếp SAU lớp 1 trên cùng luồng nối tiếp — [onProcessStart] giữ nguyên.
        submit("ân hạn khởi động") { try { onBootGrace(app, interactive, startedAt) } finally { bootGraceBusy.set(false) } }
    }

    /** `onReceive` của broadcast ORDERED: chỉ lấy mốc sự kiện rồi trả ngay (`Notifier.java:748-754`). */
    private class ScreenOnReceiver(private val app: Context) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_ON) return
            val at = SystemClock.elapsedRealtime()
            // READY-AT-HOME §4.8 — báo "lớp 2 đang xét" NGAY lúc nhận (trước khi luồng nền kịp chạy), nhả khi xét xong.
            screenOnSeen.set(true); moXeBusy.set(true)
            submit("màn bật") { try { onScreenOn(app, at) } finally { moXeBusy.set(false) } }
        }
    }

    // ─── Lớp 1 ───────────────────────────────────────────────────────────────────────────────────────────

    private fun onProcessStart(app: Context, interactive: Boolean?, startedAt: Long) {
        val escalatedAt = Prefs.a11yEscalatedAt(app)
        // Chỉ tiến trình ĐẦU TIÊN sau lượt leo được chấm (luật + [ĐO máy ảo] ở KDoc `firstStartAfterHeal`). Nhận
        // TRƯỚC bằng `commit()`; ghi hỏng ⇒ không chấm — thà thiếu một dòng điểm còn hơn một dòng điểm oan.
        if (AccessibilityHealGates.firstStartAfterHeal(escalatedAt, Prefs.a11yScoredFor(app), startedAt, AFTER_HEAL_WINDOW_MS) &&
            Prefs.setA11yScoredFor(app, escalatedAt)
        ) {
            bornFromOwnHeal.set(true)
            scoreAfterHeal(app, escalatedAt)
        }
        if (!AccessibilityHealGates.tatMayMayRun(interactive, Prefs.a11yTatMayAt(app), Prefs.a11yMoXeAt(app), startedAt)) {
            Log.i(TAG, "khởi động tiến trình (tương tác=$interactive) → không mở lượt tắt-máy")
            return
        }
        // Claim TRƯỚC khi đo (CLAUDE.md §5) — một lượt mỗi lần tắt máy. Ghi HỎNG ⇒ KHÔNG chữa (fail-safe): claim là
        // thứ duy nhất ngăn tiến trình do chính lượt này dựng lại (màn vẫn tắt) mở lượt thứ hai = vòng lặp tự giết.
        if (!Prefs.setA11yTatMayAt(app, startedAt)) {
            Log.e(TAG, "không ghi được claim tắt-máy → bỏ lượt (tránh vòng lặp giết launcher)")
            return
        }
        healIfStuck(app, HealPhase.TAT_MAY, screenOnAt = -1L)
    }

    // ─── Lớp 2 ───────────────────────────────────────────────────────────────────────────────────────────

    private fun onScreenOn(app: Context, screenOnAt: Long) {
        val now = SystemClock.elapsedRealtime()
        // Mốc mở-xe TRƯỚC, đọc MỘT lần trước khi claim: vừa chống trùng, vừa để hỏi "từ lần màn bật trước tới giờ có
        // lần tắt máy nào không" ([AccessibilityHealGates.moXeFollowsTatMay]).
        val prevMoXe = Prefs.a11yMoXeAt(app)
        if (!AccessibilityHealGates.moXeFresh(screenOnAt, prevMoXe, now)) return
        // Claim TRƯỚC khi đo — đồng thời là bằng chứng "đã mở xe" cho lớp 1. Ghi hỏng ⇒ không chữa (fail-safe, như lớp 1).
        if (!Prefs.setA11yMoXeAt(app, screenOnAt)) {
            Log.e(TAG, "không ghi được claim mở-xe → bỏ lượt")
            return
        }
        if (!AccessibilityHealGates.withinMoXeGrace(screenOnAt, now)) {
            Log.w(TAG, "màn bật đã ${now - screenOnAt} ms (ân hạn ${AccessibilityHealGates.MO_XE_GRACE_MS}) → bỏ lượt mở-xe")
            return
        }
        // Màn bật mà KHÔNG có lần khởi động-lúc-màn-tắt nào từ lần màn bật trước = không phải mở xe (vd tắt/bật màn lúc
        // đang lái) ⇒ lớp 3: owner chốt không tự giết launcher khi xe đang dùng, nút "Sửa ngay" lo.
        if (!AccessibilityHealGates.moXeFollowsTatMay(Prefs.a11yTatMayAt(app), prevMoXe, now)) {
            Log.i(TAG, "màn bật không theo sau lần tắt máy nào (tắt-máy=${Prefs.a11yTatMayAt(app)}, mở-xe trước=$prevMoXe) → không tự chữa")
            return
        }
        healIfStuck(app, HealPhase.MO_XE, screenOnAt)
    }

    // ─── Lớp 2 mở rộng — ÂN HẠN KHỞI ĐỘNG (READY-AT-HOME 02/10) ──────────────────────────────────────────

    /**
     * MỘT lượt mỗi tiến trình (chỉ gọi từ [install]; không lối vào nào khác — mở Cài đặt, màn bật, nút đều không tới đây).
     * Cổng thuần [AccessibilityHealGates.bootGraceMayRun] trên sự thật bền: màn sáng lúc bật · có tiến trình launcher
     * trước trong lần nổ máy này (mình là DỰNG LẠI) · không phải con của lượt chữa ([AccessibilityHealGates.ownHealChild]).
     * Qua cổng ⇒ cùng
     * [healIfStuck] của lớp 1/2 (đo → chờ 5 s → đo lại → KẸT BỀN mới leo, cổng cuối hỏi lại ân hạn ngay trước khi bắn).
     */
    private fun onBootGrace(app: Context, interactive: Boolean?, startedAt: Long) {
        // Mốc tiến trình TRƯỚC: đọc MỘT lần, TRƯỚC khi ghi mốc của mình (đọc sau là luôn thấy chính mình ⇒ ai cũng là
        // "dựng lại"). Ghi ở MỌI lần bật, kể cả lúc màn tắt. Ghi hỏng chỉ làm tiến trình KẾ không biết có mình ⇒ nó bỏ
        // lượt (fail-safe); chốt chống vòng lặp của chính lượt này là mốc leo, không phải mốc này.
        val prevProcStart = Prefs.a11yProcStartAt(app)
        if (!Prefs.setA11yProcStartAt(app, startedAt)) Log.w(TAG, "không ghi được mốc tiến trình — tiến trình kế sẽ bỏ ân hạn khởi động")
        // Con của lượt chữa? — kết quả lượt NHẬN chấm điểm (`commit()`) của [onProcessStart] chạy TRƯỚC trên cùng luồng nối
        // tiếp, cộng mốc bền đọc lại (nhận hỏng ⇒ coi là con). Không quyết bằng cờ RAM xuyên tiến trình (CLAUDE.md §5).
        val escalatedAt = Prefs.a11yEscalatedAt(app)
        val ownChild = AccessibilityHealGates.ownHealChild(bornFromOwnHeal.get(), escalatedAt, Prefs.a11yScoredFor(app), startedAt)
        if (!AccessibilityHealGates.bootGraceMayRun(interactive, prevProcStart, ownChild, startedAt)) {
            Log.i(TAG, "ân hạn khởi động: không mở (tương tác=$interactive, tiến trình trước=$prevProcStart, leo=$escalatedAt, con của lượt chữa=$ownChild)")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!AccessibilityHealGates.withinBootGrace(startedAt, now)) {
            Log.w(TAG, "ân hạn khởi động đã qua (${now - startedAt} ms) → bỏ lượt, luật lớp 3")
            return
        }
        // [screenOnAt] của [healIfStuck] = mốc NEO của pha; ở đây là lúc tiến trình bật (KDoc `lifecycleFireAllowed`).
        if (healIfStuck(app, HealPhase.KHOI_DONG, screenOnAt = startedAt)) handOffToTatMay(app, startedAt)
    }

    /**
     * Lượt khởi động đã thấy KẸT mà bị cắt vì pha qua ⇒ màn vừa TẮT trong ân hạn thì trao LỚP 1 nguyên vẹn (cổng + claim
     * `commit()` TRƯỚC khi đo + [healIfStuck]) — luật + [ĐO E2E C6] ở [AccessibilityHealGates.bootGraceHandsOffToTatMay].
     * Chỉ gọi từ [onBootGrace] ⇒ một lần mỗi tiến trình.
     */
    private fun handOffToTatMay(app: Context, startedAt: Long) {
        val now = SystemClock.elapsedRealtime()
        if (!AccessibilityHealGates.bootGraceHandsOffToTatMay(interactive(app), startedAt, Prefs.a11yTatMayAt(app), Prefs.a11yMoXeAt(app), now)) {
            Log.i(TAG, "khoi-dong: pha qua, không trao lớp 1 (màn chưa tắt / quá ân hạn / lần tắt máy này đã có lượt)")
            return
        }
        if (!Prefs.setA11yTatMayAt(app, now)) {
            Log.e(TAG, "không ghi được claim tắt-máy (trao từ ân hạn khởi động) → bỏ lượt (tránh vòng lặp giết launcher)")
            return
        }
        Log.i(TAG, "khoi-dong: màn tắt giữa lượt (+${now - startedAt} ms) → trao lớp 1")
        healIfStuck(app, HealPhase.TAT_MAY, screenOnAt = -1L)
    }

    // ─── Lớp 3 — CHỈ ĐO (kẹt lúc đang chạy) ──────────────────────────────────────────────────────────────

    /**
     * Mốc `elapsedRealtime` của lần ĐO bằng dump gần nhất nói KẸT; `-1` = không biết là kẹt. Cổng
     * [AccessibilityHealGates.watchdogStep] của watchdog 30 s và của alarm 60 s (`RebindReceiver`) đọc nó.
     *
     * Vì sao RAM, không prefs (CLAUDE.md §5 cấm QUYẾT ĐỊNH bằng cờ RAM): đây là một PHÉP ĐO có hạn — quá
     * [AccessibilityHealGates.STUCK_RECHECK_MS] là đo lại bằng dump — và nó chỉ quyết định BỎ một việc đã chứng minh vô
     * ích (toggle ở ca kẹt), không bao giờ quyết định đổi gì trên hệ thống. Kẹt sinh ra khi tiến trình chết ([ĐO AOSP
     * `:4114-4117`]) và mọi đường chữa thật (nút, lớp 1, lớp 2) đều force-stop ⇒ tiến trình mới = mốc `-1` = đo lại từ
     * đầu bằng đường grant cũ (một lần toggle + dump) — đúng phạm vi của MỘT lần kẹt.
     */
    private val runningStuckAt = AtomicLong(-1L)

    /** Single-flight của [recheckRunningStuck]; nhả trong `finally` — kẹt cờ này là watchdog không bao giờ đo lại. */
    private val rechecking = AtomicBoolean(false)

    /** Kết quả một lần ĐO bằng dump — gọi từ `NavConnect.escalateIfStuck` (mọi đường có dump). */
    internal fun noteStuckDump(stuck: Boolean) {
        runningStuckAt.set(if (stuck) SystemClock.elapsedRealtime() else -1L)
    }

    /**
     * Mốc cho nhịp watchdog này. Binder nói ĐÃ GẮN ⇒ xoá mốc: lần đứt SAU đó là sự kiện mới, phải đi lại đường grant cũ
     * chứ không thừa hưởng "đã biết kẹt" của lần trước.
     */
    internal fun runningStuckSeenAt(bound: Boolean): Long {
        if (bound) runningStuckAt.set(-1L)
        return runningStuckAt.get()
    }

    /**
     * [AccessibilityHealGates.WatchdogStep.RECHECK] — MỘT lần [observe] (binder rồi `dumpsys accessibility`) trên luồng
     * nối tiếp của hai lớp. CHỈ ĐỌC: không toggle, không force-stop (owner: lớp 3 người dùng tự chữa).
     *  • vẫn kẹt ⇒ làm mới mốc; nhật ký cùng trạng thái ⇒ không thêm dòng;
     *  • đã gắn ⇒ MỘT dòng BOUND (`kiem-lai`), mốc `-1`;
     *  • hết kẹt mà chưa gắn (ca toggle chữa được) ⇒ một dòng NOT_BOUND, mốc `-1` ⇒ nhịp sau đi đường grant cũ;
     *  • đọc hỏng / ném ⇒ mốc `-1` ⇒ nhịp sau đi đường grant cũ (hành vi trước bản vá) — không đoán, không im mãi.
     */
    internal fun recheckRunningStuck(app: Context) {
        if (!rechecking.compareAndSet(false, true)) return
        try {
            worker.execute {
                var seen = -1L
                try {
                    guarded("kiểm lại kẹt") {
                        val o = observe(app) ?: return@guarded
                        seen = AccessibilityHealGates.stuckMarkOf(o)
                        A11yBindJournalStore.record(app, stateOf(o), A11yBindJournal.NOTE_RECHECK)
                        Log.i(TAG, "kiểm lại kẹt (lớp 3): bound=${o.bound}, binding=${o.inBinding}")
                    }
                } finally {
                    runningStuckAt.set(seen)
                    rechecking.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            rechecking.set(false)
            Log.e(TAG, "không xếp được lượt kiểm lại kẹt", e)
        }
    }

    // ─── READY-AT-HOME §4.8 — cho chuỗi chuẩn bị HỎI lớp 2, không chữa thêm gì ─────────────────────────────

    /**
     * Cờ RAM phối hợp TRONG tiến trình (cùng lý do [runningStuckAt]): nó chỉ quyết HOÃN một việc của chính Kachi (gắn
     * app vào ô / gọi cấp quyền) trong lúc lớp 2 đang đo, không quyết đổi gì trên hệ thống (CLAUDE.md §5).
     */
    private val moXeBusy = AtomicBoolean(false)
    private val screenOnSeen = AtomicBoolean(false)
    @Volatile private var startedNonInteractive = false

    /** Lượt ân hạn khởi động ([onBootGrace]) chưa kết luận — đặt ở [install], nhả trong `finally`. Cùng lý do [moXeBusy]. */
    private val bootGraceBusy = AtomicBoolean(false)

    /**
     * Lớp 2 đang xét (đã nhận `ACTION_SCREEN_ON`, chưa xong) HOẶC sắp xét (tiến trình bật lúc màn tắt, chưa thấy lần
     * màn bật nào, và lần màn bật kế là mở xe — luật thuần [KeyReadyPlan.layer2Expected]) HOẶC lượt ân hạn khởi động
     * (lớp 2 mở rộng) chưa kết luận — nó cũng có thể force-stop launcher, nên chuỗi kiểm phím và ô chờ y như lớp 2.
     */
    internal fun moXePending(app: Context): Boolean = moXeBusy.get() || bootGraceBusy.get() || KeyReadyPlan.layer2Expected(
        startedNonInteractive, screenOnSeen.get(), Prefs.voiceKeyEnabled(app),
        Prefs.a11yTatMayAt(app), Prefs.a11yMoXeAt(app), SystemClock.elapsedRealtime(),
    )

    /** Chờ lớp 2 kết luận, tối đa [maxMs] (fail-open). `true` = đã kết luận. ⚠ CHẶN — chỉ gọi trên luồng nền. */
    internal fun awaitMoXeVerdict(app: Context, maxMs: Long): Boolean {
        val until = SystemClock.elapsedRealtime() + maxMs
        while (moXePending(app)) {
            val left = until - SystemClock.elapsedRealtime()
            if (left <= 0L) return false
            try {
                Thread.sleep(minOf(left, VERDICT_POLL_MS))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return true
    }

    private const val VERDICT_POLL_MS = 100L

    // ─── Chung ───────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Đo → chờ → đo lại → KẸT BỀN thì vào ĐÚNG đường leo thang sẵn có ([NavConnect.escalateOnLifecycle] →
     * `escalateIfStuck` → `AccessibilityRebind.forceStopRebindCommand`). Không tự dựng lệnh nào ở đây.
     *
     * @return `true` = đã thấy KẸT nhưng lượt bị CẮT vì pha qua (chưa kết luận) — chỉ [onBootGrace] dùng (trao lớp 1).
     */
    private fun healIfStuck(app: Context, phase: HealPhase, screenOnAt: Long): Boolean {
        val note = A11yBindJournal.grantNote(userAsked = false, phase = phase)
        // R-nf5 — cùng cổng `wanted` của healStep; hỏi ở đây để không mở phiên shell vô ích khi phím-thoại tắt.
        if (!Prefs.voiceKeyEnabled(app)) {
            Log.i(TAG, "$note: phím-thoại TẮT → không đo")
            return false
        }
        val first = observe(app) ?: return false
        A11yBindJournalStore.record(app, stateOf(first), note)
        if (!first.stuck) {
            Log.i(TAG, "$note: không kẹt (bound=${first.bound}, binding=${first.inBinding})")
            return false
        }
        if (!waitInPhase(app, phase, screenOnAt, AccessibilityHealGates.STUCK_CONFIRM_GAP_MS)) {
            Log.i(TAG, "$note: pha đã qua trong lúc chờ đo lại → bỏ")
            return true
        }
        val second = observe(app) ?: return false
        if (!AccessibilityHealGates.stuckPersistent(first, second)) {
            Log.i(TAG, "$note: kẹt KHÔNG bền (lần 2 bound=${second.bound}, binding=${second.inBinding}) → không leo")
            return false
        }
        // READY-AT-HOME — dòng `keys=` (summary + màn Chẩn đoán) nói đúng điều lượt này thấy. Dòng TRƯỚC khi leo là dòng
        // duy nhất chắc ra kịp: lệnh bắn mở đầu bằng `am force-stop` ⇒ tiến trình này chết ngay sau đó.
        KachiReadyLog.keys("STUCK($note)->ESCALATE")
        val r = NavConnect.escalateOnLifecycle(app, phase) { stillInPhase(app, phase, screenOnAt) }
        KachiReadyLog.keys("STUCK($note)->$r")
        Log.w(TAG, "$note: kẹt BỀN ${second.atElapsed - first.atElapsed} ms → leo thang: $r")
        // Cổng cuối của đường leo nói "pha qua" ⇒ không bắn ⇒ cũng là CẮT (cùng nghĩa với nhánh chờ ở trên).
        return r != NavConnect.GrantResult.RESTARTING && !stillInPhase(app, phase, screenOnAt)
    }

    /**
     * Một lần nhìn. Binder `AccessibilityManager` nói "đã gắn" ⇒ xong, 0 lệnh shell (cùng nguồn `mBoundServices` —
     * KDoc [AccessibilityHealGates]). Còn lại ⇒ `dumpsys accessibility` qua dadb; phiên hỏng ⇒ `null` (không đoán).
     */
    private fun observe(app: Context): BindObservation? {
        if (NavConnect.boundPerAccessibilityManager(app) == true) {
            return BindObservation(SystemClock.elapsedRealtime(), bound = true, inBinding = false)
        }
        val dump = LocalDeviceShell.run(AdbKeys.ensure(app), "dumpsys accessibility") { f ->
            Log.w(TAG, "dumpsys accessibility qua dadb hỏng: $f")
        } ?: return null
        return AccessibilityHealGates.observe(dump, SystemClock.elapsedRealtime(), NavConnect.ACC_COMP)
    }

    private fun stateOf(o: BindObservation): A11yBindJournal.State = when {
        o.bound -> A11yBindJournal.State.BOUND
        o.stuck -> A11yBindJournal.State.STUCK
        else -> A11yBindJournal.State.NOT_BOUND
    }

    private fun stillInPhase(app: Context, phase: HealPhase, screenOnAt: Long): Boolean =
        AccessibilityHealGates.lifecycleFireAllowed(phase, interactive(app), screenOnAt, SystemClock.elapsedRealtime())

    /** Chờ [ms] nhưng hỏi lại pha mỗi [PHASE_POLL_MS]; pha qua ⇒ `false` ngay (màn bật giữa lượt tắt-máy ⇒ nhường lớp 2). */
    private fun waitInPhase(app: Context, phase: HealPhase, screenOnAt: Long, ms: Long): Boolean {
        val until = SystemClock.elapsedRealtime() + ms
        while (true) {
            if (!stillInPhase(app, phase, screenOnAt)) return false
            val left = until - SystemClock.elapsedRealtime()
            if (left <= 0L) return true
            try {
                Thread.sleep(minOf(left, PHASE_POLL_MS))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
    }

    /**
     * Chấm điểm lượt chữa vừa giết chính mình ("sau-chua-ON"/"sau-chua-VAN-TAT", cùng chữ của watchdog 2.81). Cần ở
     * đây vì sau lượt TẮT MÁY, keep-alive (chủ của watchdog) chỉ lên lại lúc mở xe — và nếu xe ngủ dài thì nhịp đầu
     * của nó nhả mốc trước khi kịp chấm. Chỉ hỏi binder, không shell; không hỏi được ⇒ không chấm bừa.
     */
    private fun scoreAfterHeal(app: Context, escalatedAt: Long) {
        val delay = (escalatedAt + AFTER_HEAL_SCORE_DELAY_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val score = Runnable {
            guarded("chấm điểm sau chữa") {
                val bound = NavConnect.boundPerAccessibilityManager(app) ?: return@guarded
                // READY-AT-HOME — tiến trình sinh từ lượt chữa thì `KeyReady` ghi `SKIP_OWN_HEAL` (không cấp chồng); kết
                // quả THẬT của lượt chữa tới ở đây ⇒ dòng `keys=` / màn Chẩn đoán nói phím có sống không.
                KachiReadyLog.keys(if (bound) "HEALED->BOUND" else "HEALED->NOT_BOUND")
                A11yBindJournalStore.record(
                    app,
                    if (bound) A11yBindJournal.State.BOUND else A11yBindJournal.State.NOT_BOUND,
                    A11yBindJournal.afterHealNote(bound),
                )
            }
        }
        worker.schedule(score, delay, TimeUnit.MILLISECONDS)
    }

    private fun interactive(app: Context): Boolean? = try {
        (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive
    } catch (e: RuntimeException) {
        // `rethrowFromSystemServer` (PowerManager.java:1376-1382) ⇒ không hỏi được ⇒ null ⇒ không đoán màn tắt.
        Log.w(TAG, "không hỏi được isInteractive: ${e.message}")
        null
    }

    private fun submit(label: String, task: () -> Unit) {
        worker.execute { guarded(label, task) }
    }

    /**
     * Ranh giới lỗi của một lượt. Bắt RỘNG có chủ ý (CLAUDE.md §4.1 — lý do ghi tại chỗ): đây là luồng tự chữa
     * best-effort chạy ở MỌI lần launcher khởi động; một ngoại lệ lọt ra luồng nền là SẬP tiến trình HOME ⇒ Android
     * dựng lại ⇒ lại chạy ⇒ crash-loop mỗi lần tắt máy. Không nuốt câm: log ERROR kèm stack. `IOException` (khoá adb,
     * tệp nhật ký) và `RuntimeException` (binder/shell) là hai họ mà các lời gọi bên trong có thể ném.
     */
    private inline fun guarded(label: String, task: () -> Unit) {
        try {
            task()
        } catch (e: IOException) {
            Log.e(TAG, "lượt '$label' lỗi I/O", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "lượt '$label' lỗi", e)
        }
    }
}
