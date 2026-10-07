package com.byd.clusternav.launcher

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.AdbKeys
import com.byd.clusternav.ShellReadiness
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalSetHomeOutcome
import com.byd.clusternav.carexec.LocalShellFailure
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.96 · R8 — NHỊP GIỮ HOME (luồng `kachi-home-guard`, tiến trình launcher) ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-296-plan.html` §3 R8. Luật ở [HomeGuardPolicy] (thuần, `:core`); đây chỉ đọc sự thật + thi hành.
 *
 * ## Vì sao nhịp nằm ở TIẾN TRÌNH, không dùng lại nhịp 10 s của [KachiHomeActivity]
 * Nhịp đó gỡ ở `onPause`. Ca cần bắt chính là lúc HOME đã bị giành và người dùng bấm Home ⇒ launcher kia lên trước,
 * màn Kachi bị pause ⇒ nhịp màn chính KHÔNG chạy đúng lúc cần. Tiến trình launcher thì sống suốt chuyến (cùng lẽ
 * [com.byd.clusternav.A11yLifecycleHeal] / `YoutubeResumeSampler`): cài một lần ở `KachiApplication.onCreate`, một luồng
 * daemon nhàn rỗi gần như toàn thời gian, mỗi nhịp chỉ một binder `resolveActivity` (KHÔNG shell — [DefaultHome.currentPackage]).
 *
 * ## Thi hành = CÙNG đường với nút Cài đặt
 * [DefaultHome.enableHomeEntry] + [LocalDeviceShell.setHomeActivity] (= `HomeActivityCmd.set` rồi đọc lại xác nhận) —
 * đúng thân `ClusterNavBridge.setDefaultHome`. Kênh shell chưa lên ([ShellReadiness.isUp]) ⇒ bỏ + log, nhịp sau thử lại;
 * KHÔNG tự mở kênh/xin quyền từ đây.
 *
 * ## Phạm vi (CLAUDE.md §4) — một lệnh, một đích
 * Chỉ `set-home-activity <alias HOME của chính Kachi>`; không display, không stack, không app khác. Hoàn tác = nút
 * "Bỏ chọn" (xoá marker ⇒ [HomeGuardPolicy.wantsKachiHome] false ⇒ guard im ngay nhịp sau).
 *
 * Mốc/bộ đếm chuyến nằm trong RAM là đúng phạm vi: chúng chỉ giới hạn tần suất của chính guard này, không quyết định
 * HOME là ai (CLAUDE.md §5 — "HOME là ai" đọc lại từ hệ thống mỗi nhịp).
 */
internal object HomeGuard {

    private const val TAG = "KachiHomeGuard"

    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "kachi-home-guard").apply { isDaemon = true } }
    private val started = AtomicBoolean(false)

    // Chỉ chạm trên luồng `exec` (một luồng) ⇒ không cần khoá.
    private var tripStartMs = 0L
    private var lastReassertMs: Long? = null
    private var reassertsThisTrip = 0
    private var prevInteractive: Boolean? = null
    private var prevDecision: HomeGuardPolicy.Decision? = null

    /** Một lần mỗi tiến trình (gọi lại = không làm gì). Không I/O trên luồng gọi. */
    fun install(ctx: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        // Đầu chuyến = lúc tiến trình bật ([ĐO firmware 03/10] BYD giết Kachi lúc tắt máy ⇒ nổ máy là tiến trình nguội).
        tripStartMs = Process.getStartElapsedRealtime()
        schedule(app, HomeGuardPolicy.FAST_TICK_MS)
    }

    private fun schedule(app: Context, delayMs: Long) {
        try {
            exec.schedule({ loop(app) }, delayMs, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            Log.e(TAG, "không hẹn được nhịp giữ HOME", e)
        }
    }

    private fun loop(app: Context) {
        try {
            tick(app)
        } catch (e: RuntimeException) {
            // Ranh giới lỗi một nhịp (cùng mẫu `YoutubeResumeSampler.guarded`): lọt ra là chuỗi hẹn giờ đứt vĩnh viễn.
            Log.e(TAG, "nhịp giữ HOME lỗi", e)
        } finally {
            schedule(app, HomeGuardPolicy.nextDelayMs(SystemClock.elapsedRealtime() - tripStartMs, prevInteractive))
        }
    }

    private fun tick(app: Context) {
        val now = SystemClock.elapsedRealtime()
        val interactive = interactive(app)
        if (HomeGuardPolicy.startsNewTrip(prevInteractive, interactive)) {
            tripStartMs = now; lastReassertMs = null; reassertsThisTrip = 0
            Log.i(TAG, "new trip (screen on) — counters reset")
        }
        prevInteractive = interactive
        // 2.96 · R18 — màn TẮT ⇒ `decide` chỉ có thể ra SCREEN_OFF/NOT_CHOSEN/ALREADY_HOME (không đặt lại) ⇒ khỏi đọc prefs + binder
        // `resolveActivity`; nhịp thưa [HomeGuardPolicy.SCREEN_OFF_TICK_MS]. Ghi một dòng khi vào trạng thái này (cùng luật log-khi-đổi).
        if (interactive == false) {
            val off = HomeGuardPolicy.Decision(false, HomeGuardPolicy.Reason.SCREEN_OFF, null)
            if (HomeGuardPolicy.shouldLog(prevDecision, off)) Log.i(TAG, "screen off — guard idle (no HOME read) t+${now - tripStartMs}ms")
            prevDecision = off
            return
        }
        val prefs = WorkspacePrefs(app)
        val facts = HomeGuardPolicy.Facts(
            chosen = HomeGuardPolicy.wantsKachiHome(prefs.homeChosen(), prefs.keepHomeOnBoot()),
            ownPackage = app.packageName,
            currentHomePackage = DefaultHome.currentPackage(app),
            interactive = interactive,
            channelUp = ShellReadiness.isUp(),
            nowMs = now,
            lastReassertMs = lastReassertMs,
            reassertsThisTrip = reassertsThisTrip,
        )
        val d = HomeGuardPolicy.decide(facts)
        val log = HomeGuardPolicy.shouldLog(prevDecision, d)
        prevDecision = d
        val sinceTrip = now - tripStartMs
        if (!d.reassert) {
            if (log) Log.i(TAG, "home=${facts.currentHomePackage ?: "unresolved"} reason=${d.reason} t+${sinceTrip}ms n=$reassertsThisTrip")
            return
        }
        lastReassertMs = now
        reassertsThisTrip++
        Log.w(TAG, "HOME taken by ${d.taker} t+${sinceTrip}ms — reasserting Kachi (#$reassertsThisTrip/${HomeGuardPolicy.MAX_PER_TRIP})")
        val outcome = reassert(app)
        val after = DefaultHome.currentPackage(app)
        Log.i(TAG, "reassert result=${describe(outcome)} home now=${after ?: "unresolved"}")
    }

    /** Đúng thân `ClusterNavBridge.setDefaultHome` (bật alias rồi `set-home-activity` + đọc lại xác nhận). */
    private fun reassert(app: Context): LocalSetHomeOutcome {
        DefaultHome.enableHomeEntry(app)
        return try {
            LocalDeviceShell.setHomeActivity(AdbKeys.ensure(app), DefaultHome.component(app))
        } catch (e: IOException) {
            Log.w(TAG, "không đọc được khoá adb: ${e.message}")
            LocalSetHomeOutcome.NoShellChannel(LocalShellFailure.UNKNOWN)
        } catch (e: RuntimeException) {
            Log.w(TAG, "đặt HOME lỗi: ${e.message}")
            LocalSetHomeOutcome.NoShellChannel(LocalShellFailure.UNKNOWN)
        }
    }

    private fun describe(o: LocalSetHomeOutcome): String = when (o) {
        LocalSetHomeOutcome.Ok -> "OK"
        is LocalSetHomeOutcome.NoShellChannel -> "NO_CHANNEL(${o.reason})"
        is LocalSetHomeOutcome.Failed -> "FAILED(${o.resolveOutput.take(120)})"
    }

    private fun interactive(app: Context): Boolean? = com.byd.clusternav.system.ScreenInteractive.read(app)
}
