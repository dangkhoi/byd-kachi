package com.byd.clusternav

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import com.byd.clusternav.carexec.LocalShellRetry
import com.byd.clusternav.carexec.NlsHealShell
import com.byd.clusternav.launcher.PermissionPreflight
import com.byd.clusternav.navigation.NlsHealPolicy
import com.byd.clusternav.navigation.NlsHealPolicy.Outcome
import com.byd.clusternav.navigation.NlsHealPolicy.Trigger
import com.byd.clusternav.navigation.NlsLiveDump
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ FIX286 R-HUD — TỰ GẮN LẠI nguồn thông báo (nguồn DUY NHẤT của HUD) + đường bấm tay báo ĐÚNG kết quả ═════════════
 *
 * Spec `docs/specs/kachi-286-field-fixes.html` §3.9/§4.7. Ba lối vào, MỘT phiên lệnh ([NlsHealShell]):
 *  • [onReady] — chuỗi SẴN (`EarlyShellChannel.readyChain`, kênh UP + màn sáng), hẹn tới mốc
 *    [NlsHealPolicy.SETTLE_AFTER_START_MS] sau lúc tiến trình bật (để NMS tự gắn trước — hẹn 10 s của nó).
 *  • [onWatchdog] — alarm 60 s `RebindReceiver` (thay cho `requestRebind` vô tác dụng trên AOSP 10).
 *  • [userEnsure] / [userReconnect] — công tắc *Dẫn đường lên cụm đồng hồ* / nút *Kết nối lại*, phiên HỎI.
 *
 * Lượt tự động đi qua [NlsHealPolicy.step]: công tắc TẮT ⇒ không đọc, không ghi (owner 03/10); chưa CẤP ⇒ không tự
 * cấp (đó là việc của công tắc / Preflight); màn tắt / kênh chưa lên ⇒ chờ. Chỉ bắn khi dump của NMS nói NOT_LIVE
 * (CLAUDE.md §5) — một lần mỗi tiến trình, trần [NlsHealPolicy.MAX_FIRES_PER_PROCESS], đọc lại sau mỗi lần bắn.
 */
internal object NlsHeal {

    private const val TAG = "NlsHeal"

    /** Một luồng nối tiếp cho lượt tự động: READY và watchdog không bao giờ chồng nhau. */
    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "kachi-nls-heal").apply { isDaemon = true } }

    /** Single-flight CHUNG cho mọi đường đụng quyền bộ nghe (tự gắn · công tắc · Kết nối lại · `NavConnect.selfGrant`). */
    val busy = AtomicBoolean(false)

    @Volatile private var ledger = NlsHealPolicy.Ledger()
    @Volatile private var readyPending: ScheduledFuture<*>? = null

    /** Một lượt — cho dòng tình trạng ở Cài đặt. Giờ TƯỜNG chỉ để hiển thị. */
    data class Last(val wallMs: Long, val trigger: Trigger, val outcome: Outcome, val verdict: NlsLiveDump.Verdict?)

    /** Lượt HÀNH ĐỘNG gần nhất (tự động / công tắc / nút) — lượt chỉ-đọc của Cài đặt KHÔNG đè lên nó. */
    @Volatile var lastAction: Last? = null
        private set

    /** Lần gần nhất đọc được SỰ THẬT NMS (mọi lượt có dump, kể cả lượt chỉ-đọc của Cài đặt). */
    @Volatile var lastTruth: Last? = null
        private set

    private val main by lazy { Handler(Looper.getMainLooper()) }

    // ─── Lượt tự động ─────────────────────────────────────────────────────────────────────────────────────

    fun onReady(ctx: Context) {
        val app = ctx.applicationContext
        if (readyPending?.isDone == false) return
        val delay = (NlsHealPolicy.SETTLE_AFTER_START_MS - sinceProcessStart()).coerceAtLeast(0L) + READY_MARGIN_MS
        readyPending = try {
            exec.schedule({ guarded(Trigger.READY) { autoPass(app, Trigger.READY) } }, delay, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            Log.e(TAG, "không hẹn được lượt READY", e); null
        }
    }

    fun onWatchdog(ctx: Context) {
        val app = ctx.applicationContext
        try {
            exec.execute { guarded(Trigger.WATCHDOG) { autoPass(app, Trigger.WATCHDOG) } }
        } catch (e: RejectedExecutionException) {
            Log.e(TAG, "không xếp được lượt watchdog", e)
        }
    }

    private fun autoPass(app: Context, trigger: Trigger) {
        val step = NlsHealPolicy.step(facts(app), ledger, SystemClock.elapsedRealtime())
        if (step != NlsHealPolicy.Step.CHECK) {
            // READY một dòng INFO mỗi lần màn bật; watchdog 60 s chỉ DEBUG (không làm đầy usage log).
            if (trigger == Trigger.READY) Log.i(TAG, "nls $trigger → $step (không đọc, không ghi)")
            else Log.d(TAG, "nls $trigger → $step (không đọc, không ghi)")
            return
        }
        if (!busy.compareAndSet(false, true)) { Log.i(TAG, "nls $trigger → BUSY"); return }
        try {
            val run = NlsHealShell.run(
                AdbKeys.ensure(app), LocalShellRetry.BACKGROUND_READ_CAP, NavConnect.COMP, app.packageName,
                checkFirst = true,
                // Cổng phút chót (tầng THI HÀNH — CLAUDE.md §5): công tắc vẫn bật và màn vẫn sáng.
                fireGate = { Prefs.enabled(app) && interactive(app) == true },
                awaitBound = ::awaitFreshConnect,
            )
            if (run.fired) requestRebind(app)
            ledger = NlsHealPolicy.record(ledger, run.outcome, run.fired, SystemClock.elapsedRealtime())
            publish(trigger, run)
        } finally {
            busy.set(false)
        }
    }

    // ─── Đường bấm tay (S2) ───────────────────────────────────────────────────────────────────────────────

    /**
     * Công tắc BẬT: chờ callback tự nhiên ≤ 4,5 s (hành vi 2.85 — không ngắt nguồn đang lên), chưa có thì bắn cặp lệnh
     * qua phiên HỎI. [onResult] chạy trên luồng chính, nhận kết quả THẬT (đọc lại dump), không phải "đã gửi".
     */
    fun userEnsure(ctx: Context, onResult: (Outcome) -> Unit) = userThread(ctx, Trigger.SWITCH, onResult)

    /** Nút *Kết nối lại*: bắn ngay (hành vi đã chạy ngoài hiện trường), phiên HỎI, báo kết quả thật. */
    fun userReconnect(ctx: Context, onResult: (Outcome) -> Unit) = userThread(ctx, Trigger.BUTTON, onResult)

    private fun userThread(ctx: Context, trigger: Trigger, onResult: (Outcome) -> Unit) {
        val app = ctx.applicationContext
        Thread({
            val o = try {
                userPass(app, trigger)
            } catch (e: RuntimeException) {
                Log.e(TAG, "nls $trigger lỗi", e); Outcome.SHELL_FAILED
            }
            main.post { onResult(o) }
        }, "kachi-nls-user").start()
    }

    private fun userPass(app: Context, trigger: Trigger): Outcome {
        requestRebind(app)
        if (trigger == Trigger.SWITCH && waitConnected(NATURAL_BIND_WAIT_MS)) {
            Log.i(TAG, "nls $trigger → BOUND_IN_PROCESS (callback tự tới, không lệnh shell)")
            return Outcome.BOUND_IN_PROCESS.also { lastAction = Last(System.currentTimeMillis(), trigger, it, null) }
        }
        if (!busy.compareAndSet(false, true)) { Log.i(TAG, "nls $trigger → BUSY"); return Outcome.BUSY }
        try {
            val run = NlsHealShell.run(
                AdbKeys.ensure(app), LocalShellRetry.USER_READ_CAP, NavConnect.COMP, app.packageName,
                checkFirst = false,
                fireGate = { trigger != Trigger.SWITCH || Prefs.enabled(app) },
                awaitBound = ::awaitFreshConnect,
            )
            if (run.fired) requestRebind(app)
            publish(trigger, run)
            return run.outcome
        } finally {
            busy.set(false)
        }
    }

    // ─── Đọc cho Cài đặt (S4) ─────────────────────────────────────────────────────────────────────────────

    /** Đọc sự thật lúc mở trang — CHỈ đọc, chỉ khi công tắc bật + kênh đã lên; [onDone] trên luồng chính. */
    fun readForSettings(ctx: Context, onDone: () -> Unit) {
        val app = ctx.applicationContext
        val prev = lastTruth
        val fresh = prev != null && System.currentTimeMillis() - prev.wallMs in 0 until SETTINGS_READ_GAP_MS
        if (fresh || !Prefs.enabled(app) || !ShellReadiness.isUp() || busy.get()) { onDone(); return }
        try {
            exec.execute {
                guarded(Trigger.SETTINGS_READ) {
                    if (busy.compareAndSet(false, true)) {
                        try {
                            val run = NlsHealShell.read(AdbKeys.ensure(app), LocalShellRetry.BACKGROUND_READ_CAP, NavConnect.COMP, app.packageName)
                            publish(Trigger.SETTINGS_READ, run)
                        } finally { busy.set(false) }
                    }
                }
                main.post(onDone)
            }
        } catch (e: RejectedExecutionException) {
            Log.e(TAG, "không xếp được lượt đọc cho Cài đặt", e); onDone()
        }
    }

    // ─── Nội bộ ───────────────────────────────────────────────────────────────────────────────────────────

    private fun facts(app: Context) = NlsHealPolicy.Facts(
        navEnabled = Prefs.enabled(app),
        granted = PermissionPreflight.notificationListenerGranted(app),
        interactive = interactive(app),
        shellUp = ShellReadiness.isUp(),
        boundInProcess = NavNotificationListener.connected,
        sinceProcessStartMs = sinceProcessStart(),
    )

    private fun publish(trigger: Trigger, run: NlsHealShell.Run) {
        val l = Last(System.currentTimeMillis(), trigger, run.outcome, run.after ?: run.before)
        if (l.verdict != null) lastTruth = l
        if (trigger != Trigger.SETTINGS_READ) lastAction = l
        // Sổ trần chỉ áp cho lượt TỰ ĐỘNG — lượt bấm tay không đếm, không in số đếm (đỡ đọc nhầm).
        val tally = if (trigger == Trigger.READY || trigger == Trigger.WATCHDOG) {
            " fires=${ledger.fires}/${NlsHealPolicy.MAX_FIRES_PER_PROCESS}"
        } else ""
        Log.i(TAG, "nls $trigger → ${run.outcome} before=${run.before ?: "-"} after=${run.after ?: "-"} fired=${run.fired}$tally")
    }

    /** Đợi callback `onListenerConnected` MỚI (mốc sau lúc bắn), tối đa [maxMs]. */
    private fun awaitFreshConnect(maxMs: Long): Boolean {
        val since = SystemClock.elapsedRealtime() - NlsHealPolicy.TOGGLE_PAUSE_MS - FRESH_SLACK_MS
        return poll(maxMs) { NavNotificationListener.connected && NavNotificationListener.connectedAtElapsed > since }
    }

    private fun waitConnected(maxMs: Long): Boolean = poll(maxMs) { NavNotificationListener.connected }

    private inline fun poll(maxMs: Long, ok: () -> Boolean): Boolean {
        var waited = 0L
        while (waited < maxMs) {
            if (ok()) return true
            try { Thread.sleep(POLL_MS) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); return ok() }
            waited += POLL_MS
        }
        return ok()
    }

    /** Lưới phụ y như đường công tắc 2.85: gỡ "snooze" nếu có (no-op khi không snooze — NMS r47 `:3127-3139`). */
    private fun requestRebind(app: Context) {
        try {
            NotificationListenerService.requestRebind(ComponentName(app, NavNotificationListener::class.java))
        } catch (e: RuntimeException) {
            Log.w(TAG, "requestRebind lỗi: ${e.message}")
        }
    }

    private fun sinceProcessStart(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

    private fun interactive(app: Context): Boolean? = try {
        (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive
    } catch (e: RuntimeException) {
        Log.w(TAG, "không hỏi được isInteractive: ${e.message}"); null
    }

    /** Ranh giới lỗi một lượt nền (cùng mẫu `EarlyShellChannel.guarded`): lọt ngoại lệ ra luồng nền = sập HOME. */
    private inline fun guarded(trigger: Trigger, task: () -> Unit) {
        try {
            task()
        } catch (e: IOException) {
            Log.e(TAG, "nls $trigger lỗi I/O", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "nls $trigger lỗi", e)
        }
    }

    private const val READY_MARGIN_MS = 500L
    private const val NATURAL_BIND_WAIT_MS = 4_500L
    private const val SETTINGS_READ_GAP_MS = 10_000L
    private const val FRESH_SLACK_MS = 1_000L
    private const val POLL_MS = 300L
}
