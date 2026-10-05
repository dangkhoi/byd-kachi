package com.byd.clusternav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.carexec.EarlyPlan
import com.byd.clusternav.carexec.EarlyStep
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellAdmission
import com.byd.clusternav.carexec.LocalShellFailure
import com.byd.clusternav.carexec.ReadyEvent
import com.byd.clusternav.carexec.ShellChannelPhase
import com.byd.clusternav.carexec.ShellReadinessPolicy
import com.byd.clusternav.carexec.ShellReadinessState
import com.byd.clusternav.launcher.ShellApprovalProbe
import com.byd.clusternav.launcher.behind.BehindHomeRecovery
import com.byd.clusternav.launcher.trip.TripStart
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * ═══ READY-AT-HOME R2 — NỐI KÊNH NGAY KHI TIẾN TRÌNH BẬT (spec §4.4–§4.8, chế độ DỰ PHÒNG) ════════════════════════
 *
 * [ĐO xe 29/09] ngòi bật hay gặp nhất: BYD giết Kachi mỗi lần tắt máy, Android dựng lại HOME 0,25–0,33 s sau lúc màn ĐÃ
 * tắt; ở chính lượt đó kênh nền bằng khoá đã duyệt đã dùng được ở +1,0–1,3 s — nhưng ô chỉ dựng 5,9 s sau màn bật vì
 * màn chính tự dò lại sau khi có tiêu điểm + 1,5 s. Lớp này nối SỚM (dòng cuối `KachiApplication.onCreate`) và công
 * bố kết quả qua [ShellReadiness]; màn chính nhận kênh đã sẵn (`ShellChannelGate.adopt`).
 *
 * ## Chỉ nối sớm khi dấu còn tươi (R2 · R3, chế độ dự phòng §4.4.3)
 * Không có dấu tươi = có thể là khoá CHƯA được duyệt ⇒ mọi lần nối là một hộp "Cho phép gỡ lỗi USB?" (adbd chỉ hỏi khi
 * nhận khoá công khai, dadb luôn gửi — spec §2.5). Hộp dựng từ nền lúc HOME chưa ở trước là đúng bệnh F4 (14/09). Nên:
 * không dấu ⇒ KHÔNG nối, để F4 hỏi đúng lúc (HOME ở trước + có tiêu điểm + 1,5 s yên).
 *
 * ## Lượt dò = đúng lượt dò của F4
 * [ShellApprovalProbe.probe] (phiên RỜI, `FirstOpenApproval.PROBE`: một lần, đọc 6 s, ép bắt tay) — KHÔNG đi
 * `ShellTransport` (đọc 10 s + tự thử lại = có thể hai lượt hỏi, kết nối dùng chung — điều tra §6 rủi ro 8).
 *
 * ## Chuỗi SẴN (R2.3/R2.6) chỉ chạy khi màn TƯƠNG TÁC
 * Lúc màn tắt (tắt máy), lớp 1 của 2.83 là chủ việc phím — không chen. Màn bật (hoặc kênh lên lúc màn đang bật) ⇒ kiểm
 * phím ([KeyReady.prepare]) rồi khởi keep-alive + watchdog (best-effort; đường cũ trong `bringUpShellChannel` vẫn còn).
 */
internal object EarlyShellChannel {

    private const val TAG = KachiReadyLog.TAG

    private val started = AtomicBoolean(false)
    private val windowRead = AtomicBoolean(false)
    /** Lần màn bật (hoặc mốc tiến trình) mà chuỗi SẴN đã chạy — một lượt mỗi lần màn bật. */
    private val chainFor = AtomicLong(Long.MIN_VALUE)

    /** MỘT luồng nối tiếp cho lượt sớm + chuỗi SẴN: không bao giờ có hai lượt chồng nhau. */
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "kachi-ready").apply { isDaemon = true } }

    /** Dòng CUỐI của `KachiApplication.onCreate` (tiến trình launcher). Trên luồng chính: chỉ đăng ký + đẩy việc. */
    fun start(ctx: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        KachiReadyLog.line("proc interactive=${interactive(app)}")
        try {
            app.registerReceiver(ScreenOnReceiver(), IntentFilter(Intent.ACTION_SCREEN_ON))
        } catch (e: RuntimeException) {
            // Không bao giờ làm sập Application.onCreate (HOME sập = crash-loop mỗi lần tắt máy). Mất bộ thu thì chuỗi
            // SẴN vẫn chạy khi kênh lên lúc màn bật, và màn chính vẫn nhận kênh.
            Log.e(TAG, "không đăng ký được bộ thu màn bật — chuỗi SẴN chỉ chạy khi kênh lên", e)
        }
        ShellReadiness.addListener { s -> if (s.phase == ShellChannelPhase.UP) submit("up") { onUp(app) } }
        submit("early") { early(app) }
    }

    private class ScreenOnReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_ON) return
            KachiReadyLog.wake(SystemClock.elapsedRealtime(), "broadcast")
            val app = context?.applicationContext ?: return
            if (ShellReadiness.isUp()) submit("screen_on") { readyChain(app, KachiReadyLog.lastScreenOnAt()) }
        }
    }

    // ─── Lượt sớm ────────────────────────────────────────────────────────────────────────────────────────

    private fun early(app: Context) {
        try {
            earlyBody(app)
        } finally {
            // Cổng thi hành cho đường nền CHỜ khi kênh ở STARTING/CHECKING (≤ 8 s mỗi lần). Một ngoại lệ giữa lượt sớm
            // mà để kênh kẹt ở hai pha đó là MỌI lệnh nền về sau chờ 8 s — luôn trả kênh về một pha đã quyết.
            when (ShellReadiness.phase()) {
                ShellChannelPhase.STARTING -> ShellReadiness.apply(ReadyEvent.EarlySkipped, "early-finally")
                ShellChannelPhase.CHECKING -> ShellReadiness.reportEnvironment(LocalShellFailure.UNKNOWN, "early-finally")
                else -> Unit
            }
        }
    }

    private fun earlyBody(app: Context) {
        val fp = ShellReadiness.fingerprint()
        val plan = ShellReadinessPolicy.earlyPlan(ShellReadiness.ledger(), fp, System.currentTimeMillis())
        if (plan != EarlyPlan.CONNECT) {
            ShellReadiness.apply(ReadyEvent.EarlySkipped, "early")
            KachiReadyLog.line("early mode=LEDGER plan=$plan fp=${fp?.take(8) ?: "-"} -> gate(F4)")
            return
        }
        ShellReadiness.apply(ReadyEvent.EarlyStart, "early")
        var attempt = 0
        while (true) {
            attempt++
            val t0 = SystemClock.elapsedRealtime()
            val failure = LocalShellAdmission.labeled("early") { ShellApprovalProbe.probe(app) }
            val step = ShellReadinessPolicy.afterEarly(failure, attempt)
            KachiReadyLog.line("early mode=LEDGER fp=${fp?.take(8)} try=$attempt -> ${failure ?: "UP"} in=${SystemClock.elapsedRealtime() - t0}ms")
            when (step) {
                EarlyStep.Up -> { ShellReadiness.reportUp("early"); return }
                // Khoá không còn được nhận ⇒ xoá dấu, KHÔNG thử lại từ nền: F4 hỏi lại đúng lúc HOME ở trước (R3.1).
                EarlyStep.Revoked -> { ShellReadiness.reportRevoked("early"); return }
                is EarlyStep.GiveUp -> { ShellReadiness.reportEnvironment(step.reason, "early"); return }
                is EarlyStep.Retry -> try {
                    Thread.sleep(step.delayMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    ShellReadiness.reportEnvironment(failure ?: LocalShellFailure.UNKNOWN, "early")
                    return
                }
            }
        }
    }

    // ─── Kênh lên / màn bật ⇒ chuỗi SẴN ──────────────────────────────────────────────────────────────────

    /**
     * HOME vừa nhận kênh lúc màn tương tác (`ShellChannelGate.adopt`) — tín hiệu "màn bật" SỚM hơn broadcast (xem
     * [KachiReadyLog.wake]) ⇒ chuỗi SẴN (kiểm phím) chạy ngay, không đợi `ACTION_SCREEN_ON`. Cùng lần thức ⇒ một lượt.
     */
    fun homeVisible(app: Context) {
        KachiReadyLog.wake(SystemClock.elapsedRealtime(), "home")
        if (ShellReadiness.isUp()) submit("home") { readyChain(app, KachiReadyLog.lastScreenOnAt()) }
    }

    private fun onUp(app: Context) {
        readWindowOnce(app)
        if (interactive(app) != true) return
        // Kênh lên lúc màn ĐANG bật (tiến trình bật khi màn sáng, hoặc F4 vừa đo xong) ⇒ coi là một mốc "thức" để lượt
        // HOME hiện ngay sau đó (cùng lần thức, < 10 s) không chạy chuỗi SẴN lần hai.
        KachiReadyLog.wake(SystemClock.elapsedRealtime(), "up")
        readyChain(app, KachiReadyLog.lastScreenOnAt())
    }

    /** Một lượt mỗi lần màn bật: kiểm phím (2.83) rồi keep-alive + watchdog, nguồn HUD (FIX286), rồi chuyến lên xe. */
    private fun readyChain(app: Context, epoch: Long) {
        val prev = chainFor.get()
        if (prev == epoch || !chainFor.compareAndSet(prev, epoch)) return
        if (!ShellReadiness.isUp() || interactive(app) != true) return
        BehindHomeRecovery.onReady(app)   // app Kachi đẩy ra sau nhà nổi lên vì Kachi bị giết ⇒ HOME lên lại (§5)
        KeyReady.prepare(app)
        // R2.6 — best-effort: Android 12 (DL5) có thể chặn khởi FGS từ nền [SUY 2.83 §4.2] (`sync` tự bắt + log). Đường
        // cũ trong `bringUpShellChannel` vẫn gọi lại khi HOME nhận kênh — đây chỉ là khởi SỚM hơn.
        try {
            VoiceKeyKeepAliveService.sync(app)
            RebindReceiver.scheduleWatchdog(app)
        } catch (e: RuntimeException) {
            Log.w(TAG, "khởi keep-alive/watchdog sớm hỏng (đường HOME sẽ gọi lại): ${e.message}")
        }
        // FIX286 R-HUD: nguồn thông báo (nguồn duy nhất của HUD) đã GẮN thật chưa — hẹn sang luồng `kachi-nls-heal`, trả
        // ngay; công tắc Dẫn đường TẮT ⇒ lượt đó không đọc, không ghi gì (cổng `NlsHealPolicy.step`).
        NlsHeal.onReady(app)
        // 2.89 · B2 VM-PREREQ-TRUTH: miễn pin/vẽ nổi của app Kachi tự mở ở nền, theo SỰ THẬT (không cờ một-lần) — luồng
        // `kachi-app-prereqs`, trả ngay. Chạy TRƯỚC chuyến lên xe để app được mở sau đó đã đủ điều kiện.
        AppPrereqs.onReady(app)
        TripStart.onReady(app)   // F2/F3 chuyến lên xe — đẩy sang luồng `kachi-trip`, trả ngay (spec shortcuts-autostart §4.5)
    }

    /** `adb_allowed_connection_time` — lệnh CHỈ ĐỌC, một lần mỗi tiến trình, khi kênh đã lên (§4.4.2, R-nf1). */
    private fun readWindowOnce(app: Context) {
        if (!windowRead.compareAndSet(false, true)) return
        val out = try {
            LocalDeviceShell.run(AdbKeys.ensure(app), "settings get global adb_allowed_connection_time") { f ->
                Log.w(TAG, "đọc adb_allowed_connection_time hỏng: $f")
            }
        } catch (e: IOException) {
            Log.w(TAG, "khoá adb lỗi khi đọc hạn duyệt", e); null
        }
        val win = ShellReadinessPolicy.parseWindow(out)
        KachiReadyLog.line("window adb_allowed_connection_time=${win ?: "default"}")
        if (win != null) ShellReadiness.setWindow(win)
    }

    private fun interactive(app: Context): Boolean? = try {
        (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive
    } catch (e: RuntimeException) {
        Log.w(TAG, "không hỏi được isInteractive: ${e.message}")
        null
    }

    private fun submit(label: String, task: () -> Unit) {
        try {
            worker.execute { guarded(label, task) }
        } catch (e: RejectedExecutionException) {
            Log.e(TAG, "không xếp được việc '$label'", e)
        }
    }

    /**
     * Ranh giới lỗi một lượt — cùng mẫu `A11yLifecycleHeal.guarded` (lý do ghi tại chỗ, R-nf5): luồng chuẩn bị chạy ở
     * MỌI lần launcher khởi động; ngoại lệ lọt ra luồng nền là sập HOME ⇒ crash-loop mỗi lần tắt máy. Log ERROR kèm stack.
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

    /** Cho màn Chẩn đoán: trạng thái hiện tại của kênh. */
    fun describe(): String {
        val s: ShellReadinessState = ShellReadiness.state()
        return "phase=${s.phase}${if (s.lost) " lost" else ""}${s.reason?.let { " reason=$it" } ?: ""}"
    }
}
