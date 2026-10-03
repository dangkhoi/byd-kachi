package com.byd.clusternav.launcher.trip

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.byd.clusternav.KachiReadyLog
import com.byd.clusternav.Prefs
import com.byd.clusternav.a11yTatMayAt
import com.byd.clusternav.launcher.DefaultHome
import com.byd.clusternav.launcher.InstalledApps
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.behind.BehindHomeRunner
import com.byd.clusternav.launcher.behind.BehindHomeSequence
import com.byd.clusternav.launcher.behind.BehindMarksStore
import com.byd.clusternav.launcher.tripConfig
import com.byd.clusternav.modules.clustercast.ClusterProfile
import com.byd.clusternav.modules.clustercast.StackParse
import com.byd.clusternav.modules.navaccess.AccessibilityRebind
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * ═══ F2/F3 — CHUYẾN LÊN XE: lối vào DUY NHẤT (dòng CUỐI `EarlyShellChannel.readyChain`) ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R2 · R3 · §4.5 (R1 của Tasks). Chuỗi SẴN đã bảo đảm: kênh
 * UP + màn TƯƠNG TÁC + kiểm phím xong (`KeyReady.prepare` — lượt chữa phím có thể force-stop Kachi; khi đó tiến trình
 * này chết TRƯỚC dòng này và tiến trình mới chạy lại chuỗi). [onReady] chỉ đẩy việc sang luồng `kachi-trip` rồi trả:
 * chuyến có thể chờ tới [TripGate.TRIP_DEADLINE_MS], còn luồng `kachi-ready` còn phục vụ lượt up/screen_on/home.
 *
 * Không chạy khi tiến trình bật lúc màn TẮT (BYD dựng lại Kachi lúc tắt máy): chuỗi SẴN chỉ chạy khi màn tương tác.
 * Một lượt mỗi chuyến THẬT: sổ hai pha ở [TripGate] (ghi `commit()` trước việc). Nâng cấp APK giữa chuyến (tiến trình
 * bật lúc màn sáng, không có claim tắt-máy mới) ⇒ cùng id chuyến ⇒ sổ đã FIRED ⇒ 0 lệnh.
 */
object TripStart {

    const val TAG = "KachiTrip"

    /** MỘT luồng cho chuyến (daemon: không níu tiến trình). */
    private val EXEC = Executors.newSingleThreadExecutor { r -> Thread(r, "kachi-trip").apply { isDaemon = true } }

    /** Đang có một lượt chạy/chờ ⇒ lượt gọi trùng (màn tắt/bật lúc đang chờ) bỏ, không xếp hàng lượt thứ hai. */
    private val busy = AtomicBoolean(false)

    /** Dòng CUỐI của chuỗi SẴN. Không chặn: chỉ đẩy việc. */
    fun onReady(app: Context) {
        if (!busy.compareAndSet(false, true)) {
            Log.i(TAG, "busy: a trip pass is already running/waiting -> skip")
            return
        }
        try {
            EXEC.execute {
                try {
                    TripRun(app.applicationContext).run()
                } catch (e: IOException) {
                    Log.e(TAG, "trip I/O error", e)
                } catch (e: RuntimeException) {
                    // Cùng lẽ `EarlyShellChannel.guarded`: lọt ra luồng nền là sập HOME ⇒ crash-loop mỗi lần nổ máy.
                    Log.e(TAG, "trip error", e)
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
            Log.e(TAG, "trip queue rejected", e)
        }
    }

    /** Nhánh BOOT_COMPLETED (`RebindReceiver`): ghi khoá lần khởi động này (R2.3a). Không phụ thuộc công tắc tự mở nào. */
    fun onBootCompleted(ctx: Context) {
        val key = bootKey(ctx)
        Log.i(TAG, "boot_completed boot=$key saved=${TripLedgerStore(ctx).markBootSeen(key)}")
    }

    /** Khoá lần khởi động máy ([TripGate.bootKey]) — `Settings.Global.BOOT_COUNT` (API công khai, chỉ đọc). */
    fun bootKey(ctx: Context): String {
        val count = try {
            Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT)
        } catch (e: Settings.SettingNotFoundException) {
            null
        } catch (e: SecurityException) {
            null
        }
        return TripGate.bootKey(count, System.currentTimeMillis(), SystemClock.elapsedRealtime())
    }

    /** Kết quả chuyến gần nhất (Cài đặt › Mở app khi nổ máy — R2.7). Chỉ đọc. */
    fun last(ctx: Context): TripGate.Result? = TripLedgerStore(ctx).last()

    /** L4 · D1 — kết quả đang hiện có phải của lần nổ máy NÀY ([TripGate.now]). Chỉ đọc (sổ bền + claim tắt-máy bền). */
    fun now(ctx: Context): TripGate.Now {
        val s = TripLedgerStore(ctx)
        val current = TripGate.tripId(bootKey(ctx), Prefs.a11yTatMayAt(ctx), SystemClock.elapsedRealtime())
        return TripGate.now(current, s.ledger(), s.last())
    }

    /** Một dòng cho màn Chẩn đoán (CLAUDE.md §11 — anh em chụp màn gửi về). */
    fun describe(ctx: Context): String {
        val s = TripLedgerStore(ctx)
        // ASCII cố ý (cùng luật dòng `KachiReady`): bản chụp màn từ máy tiếng Việt và tiếng Anh phải so được với nhau.
        return "trip ledger=${s.ledger()?.let(TripGate::encode) ?: "-"} | last=${s.last()?.let(TripGate::encodeResult) ?: "-"} | " +
            "boot=${bootKey(ctx)} (seen=${s.bootSeen() ?: "-"}) | firstWake=${KachiReadyLog.firstWakeAt()}"
    }
}

/**
 * MỘT lượt chuyến trên luồng `kachi-trip`: cổng [TripGate] → claim → chờ bằng sự thật (R2.3) → các bước [TripPlan]
 * theo thứ tự → đóng sổ. Mọi lệnh đổi cửa sổ đi qua đường đã đo: chạy nền = `BehindHomeRunner` của màn chính (R0.3) — ô
 * sống trước, màn ảo ẩn SAU (L4 · D2); mở bình thường = K10 ([TripPlan.normalCmd], rào camera trong CÙNG một chuỗi shell);
 * nhạc = [TripMusicRun] (phiên nhạc, K4-VIEW khi phiên không nhận link — L4 · D3).
 *
 * L4 · D1: mỗi bước để lại MỘT [TripStep] (mã bền, Cài đặt dịch thành câu) và mã chuyến suy từ các bước
 * ([TripOutcome.tripCode]) — chuyến toàn bước không làm gì là `NOOP`, không bao giờ "đã chạy".
 */
internal class TripRun(private val app: Context, private val sleep: (Long) -> Unit = { Thread.sleep(it) }) {

    private val store = TripLedgerStore(app)
    private val notes = ArrayList<String>()
    private val steps = ArrayList<TripStep>()
    private val now: Long get() = SystemClock.elapsedRealtime()

    /** HOME của Kachi đúng các dạng `am stack list` in (alias + activity thật) — [DefaultHome.shownComponents], một nguồn. */
    private val homeComps: List<String> = DefaultHome.shownComponents(app)

    fun run() {
        val boot = TripStart.bootKey(app)
        val trip = TripGate.tripId(boot, Prefs.a11yTatMayAt(app), now)
        val firstWake = KachiReadyLog.firstWakeAt()
        when (val d = TripGate.decide(store.ledger(), trip, firstWake, now)) {
            is TripGate.Decision.Done -> Log.i(TripStart.TAG, "skip trip=$trip why=${d.why}")
            is TripGate.Decision.Close -> {
                val ok = store.close(d.fired, TripGate.Result(trip, d.code, System.currentTimeMillis(), ""))
                Log.i(TripStart.TAG, "close trip=$trip code=${d.code} tries=${d.fired.tries} saved=$ok")
            }
            is TripGate.Decision.Run -> {
                // Sổ CLAIMED TRƯỚC mọi việc (CLAUDE.md §5). Ghi hỏng ⇒ KHÔNG chạy: thà mất một chuyến còn hơn chạy hai lần.
                if (!store.write(d.claim)) { Log.e(TripStart.TAG, "claim write failed trip=$trip -> no trip"); return }
                val t0 = now
                val code = body(boot, firstWake)
                // `d=` giữ ghi chú KHÔNG phải bước (chờ bao lâu / hết hạn); mã từng bước đi trường `s=` riêng (L4 · D1).
                val result = TripGate.Result(trip, code, System.currentTimeMillis(), notes.firstOrNull().orEmpty(), steps.toList())
                val ok = store.close(d.claim.copy(phase = TripGate.Phase.FIRED), result)
                Log.i(TripStart.TAG, "run trip=$trip tries=${d.claim.tries} -> $code in=${now - t0}ms saved=$ok " +
                    "s=${TripOutcome.encode(steps)} :: ${notes.joinToString(" | ")}")
            }
        }
    }

    private fun body(boot: String, firstWake: Long): TripGate.Code {
        val cfg = WorkspacePrefs(app).tripConfig()
        if (cfg.empty) { notes += "config empty"; return TripGate.Code.NOTHING }
        val host = awaitReady(boot, firstWake) ?: return TripGate.Code.EXPIRED
        val view = TripHub.onMain(VIEW_TIMEOUT_MS) { host.view() } ?: run { notes += "home view lost"; return TripGate.Code.EXPIRED }
        val installed = InstalledApps.launchable(app).mapTo(HashSet()) { it.pkg }
        val facts = TripPlan.Facts(
            selfPkg = app.packageName, installed = installed,
            system = cfg.apps.map { it.pkg }.filterTo(HashSet()) { isSystem(it) }, inSlots = view.appSlots.toSet(),
            cameraKnown = ClusterProfile.resolveCached(app).cameraSignature != null,
        )
        val plan = TripPlan.steps(cfg, facts)
        for ((i, step) in plan.withIndex()) {
            if (!TripGate.withinDeadline(firstWake, now)) {
                notes += "deadline before $step"
                plan.drop(i).forEach { steps += deadlineStep(it, cfg) }
                break
            }
            when (step) {
                is TripPlan.Step.Skip -> record(step.pkg, kindOf(cfg, step.pkg), TripOutcome.ofSkip(step.why), "skip-${step.why}")
                is TripPlan.Step.Background -> behind(host, step.pkg).let { out ->
                    record(step.pkg, TripStepKind.BACKGROUND, TripOutcome.ofBehind(out.result), "bg-${out.result}")
                }
                is TripPlan.Step.Music -> TripMusicRun(app, sleep, musicPorts(host)).run(step.music, installed, view).let { r ->
                    steps += r.step
                    notes += r.note
                }
                is TripPlan.Step.Normal -> normal(host, step.pkg).let { (code, note) -> record(step.pkg, TripStepKind.NORMAL, code, "normal-$note") }
            }
        }
        return TripOutcome.tripCode(steps)
    }

    private fun record(pkg: String, kind: TripStepKind, code: TripStepCode, note: String) {
        steps += TripStep(pkg, kind, code)
        notes += "$pkg:$note"
    }

    private fun kindOf(cfg: TripConfig, pkg: String): TripStepKind =
        if (cfg.apps.firstOrNull { it.pkg == pkg }?.background == false) TripStepKind.NORMAL else TripStepKind.BACKGROUND

    /** Bước chưa tới lượt khi hết hạn chuyến — vẫn ghi để Cài đặt nói "hết hạn" thay vì im. */
    private fun deadlineStep(step: TripPlan.Step, cfg: TripConfig): TripStep = when (step) {
        is TripPlan.Step.Background -> TripStep(step.pkg, TripStepKind.BACKGROUND, TripStepCode.DEADLINE)
        is TripPlan.Step.Normal -> TripStep(step.pkg, TripStepKind.NORMAL, TripStepCode.DEADLINE)
        is TripPlan.Step.Skip -> TripStep(step.pkg, kindOf(cfg, step.pkg), TripOutcome.ofSkip(step.why))
        is TripPlan.Step.Music -> TripStep(step.music.mode.code, TripStepKind.MUSIC, TripStepCode.DEADLINE)
    }

    /**
     * R2.3 — chờ bằng SỰ THẬT, hỏi lại mỗi [TripPlan.HOME_READ_GAP_MS]: (a) khởi động xong, (b) màn chính + kênh, (c) có ô app
     * thì ít nhất một ô đã thấy app sống, (d) HOME của Kachi đứng yên ở đỉnh display 0 qua [TripPlan.HOME_STEADY_READS] lần
     * đọc liền (KachiAutostart ≈ +11,7 s và VietMap tự mở ≤ 25 s có thể đè app mở sớm — [ĐO mã + log xe 29/09]).
     * Hết hạn chuyến ⇒ `null`.
     */
    private fun awaitReady(boot: String, firstWake: Long): TripHub.Host? {
        var streak = 0
        var lastWait: TripPlan.Wait? = null
        while (true) {
            if (!TripGate.withinDeadline(firstWake, now)) { notes += "expired waiting $lastWait"; return null }
            val host = TripHub.current()
            val sh = host?.shell()
            val view = host?.let { h -> TripHub.onMain(VIEW_TIMEOUT_MS) { h.view() } }
            val bootReady = TripGate.bootReady(store.bootSeen(), boot, firstWake, now)
            val entries = if (sh != null && view != null) StackParse.parse(runCatching { sh(BehindHomePlan.LIST_CMD) }.getOrDefault("")) else emptyList()
            streak = if (TripPlan.homeTopVisible(entries, homeComps)) streak + 1 else 0
            val wait = TripPlan.waitFor(bootReady, sh != null && view != null, view?.appSlots?.size ?: 0, view?.stages?.count { it.alive } ?: 0, streak)
            if (wait == null) { notes += "ready after ${now - firstWake}ms"; return host }
            if (wait != lastWait) Log.i(TripStart.TAG, "wait $wait (streak=$streak home=$homeComps)")
            lastWait = wait
            sleep(TripPlan.HOME_READ_GAP_MS)
        }
    }

    /**
     * R2.4 = R0.3 qua bên thi hành của màn chính (mutex `kachi-behind` chung với lối tắt). Ảnh chụp ô MỚI ngay trước (ô có
     * thể vừa chết). Ô sống ⇒ đường đã đo (LUÔN trước — CLAUDE.md §6); không có ô sống ⇒ L4 · D2(a): màn ảo ẨN của Kachi
     * ([BehindHomeSequence.startBehindHidden], đường mới đứng CUỐI). Chờ kết quả tối đa [BEHIND_TIMEOUT_MS].
     */
    private fun behind(host: TripHub.Host, pkg: String): BehindHomeSequence.Outcome {
        val stages = TripHub.onMain(VIEW_TIMEOUT_MS) { host.view() }?.stages
            ?: return BehindHomeSequence.Outcome(BehindHomeSequence.Result.NO_STAGE, "home view lost")
        await(pkg) { done -> host.startBehind(pkg, stages, done) != null }?.let { return it }
        return await(pkg) { done ->
            host.behindChain("behind-hidden X=$pkg", { kit -> kit.seq.startBehindHidden(pkg, kit.hidden) }, done)
            true
        } ?: BehindHomeSequence.Outcome(BehindHomeSequence.Result.NO_STAGE, "not accepted")
    }

    /** Gửi một lượt cho bên thi hành rồi CHỜ kết quả. [start] trả `false` = không nhận (0 lệnh) ⇒ `null`. */
    private fun await(pkg: String, start: ((BehindHomeSequence.Outcome) -> Unit) -> Boolean): BehindHomeSequence.Outcome? {
        val out = AtomicReference<BehindHomeSequence.Outcome?>(null)
        val latch = CountDownLatch(1)
        if (!start { o -> out.set(o); latch.countDown() }) return null
        val done = try {
            latch.await(BEHIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt(); false
        }
        if (!done) Log.w(TripStart.TAG, "$pkg: behind chain still running after ${BEHIND_TIMEOUT_MS}ms")
        return out.get() ?: BehindHomeSequence.Outcome(BehindHomeSequence.Result.TIMEOUT, "timeout")
    }

    /** Phần màn chính mà bước nhạc cần (L4 · D2/D3) — đều chặn, đều qua mutex `kachi-behind`. */
    private fun musicPorts(host: TripHub.Host) = object : TripMusicRun.Ports {
        override fun behind(pkg: String) = behind(host, pkg)

        override fun view(pkg: String, url: String, slotVd: Int?): String {
            val stages = TripHub.onMain(VIEW_TIMEOUT_MS) { host.view() }?.stages.orEmpty()
            val k4: (Int) -> String = { vd -> TripMusicPlan.viewCmd(vd, url, pkg) }
            val out = await(pkg) { done ->
                host.behindChain("view X=$pkg", { kit ->
                    if (slotVd != null) viewInSlot(kit, pkg, slotVd, url)
                    else BehindHomePlan.stageFor(stages, pkg).let { st ->
                        if (st.hidden) kit.seq.startBehindHidden(pkg, kit.hidden, view = k4) else kit.seq.startBehind(pkg, st, view = k4)
                    }
                }, done)
                true
            }
            return out?.result?.name ?: "not-accepted"
        }

        override fun facts(pkg: String): String {
            val sh = host.shell() ?: return "task=? pid=? (no channel)"
            val tasks = StackParse.parse(runCatching { sh(BehindHomePlan.LIST_CMD) }.getOrDefault("")).filter { it.pkg == pkg }
            val pid = runCatching { sh(BehindHomePlan.pidCmd(pkg)).trim() }.getOrDefault("?")
            return "task=${tasks.joinToString("/") { "${it.taskId}@d${it.displayId}" }.ifEmpty { "-" }} pid=${pid.ifEmpty { "-" }}"
        }

        override fun isSystem(pkg: String) = this@TripRun.isSystem(pkg)
    }

    /** K4-VIEW vào ô của app ([TripMusicView]) — kết quả đổi sang dạng chung của bên thi hành (chỉ để log + bộ đếm). */
    private fun viewInSlot(kit: BehindHomeRunner.Kit, pkg: String, vd: Int, url: String): BehindHomeSequence.Outcome {
        val marks = BehindMarksStore(kit.app)
        val o = TripMusicView(kit.sh, AccessibilityRebind.GO_HOME_UNLESS_CAMERA, homeComps, { id, p -> marks.add(id, p) }, sleep).inSlot(pkg, vd, url)
        val r = when (o.result) {
            TripMusicView.Result.STAYED, TripMusicView.Result.RETURNED -> BehindHomeSequence.Result.MOVED   // app ở ô: không phải lùi
            TripMusicView.Result.BEHIND -> BehindHomeSequence.Result.X_FRONT_HOME_RESTORED
            TripMusicView.Result.NOT_IN_SLOT -> BehindHomeSequence.Result.KEPT_UNDER
        }
        return BehindHomeSequence.Outcome(r, o.line)
    }

    /**
     * R2.5 — *Mở bình thường* (K10): một chuỗi shell đọc display 0 rồi mới mở (camera ⇒ không; HOME của Kachi không ở trước
     * ⇒ không giành màn). Camera / đọc hỏng ⇒ thử lại mỗi [TripPlan.CAMERA_RETRY_MS] trong [TripPlan.NORMAL_DEADLINE_MS].
     */
    private fun normal(host: TripHub.Host, pkg: String): Pair<TripStepCode, String> {
        val sig = ClusterProfile.resolveCached(app).cameraSignature ?: return TripStepCode.CAMERA_UNKNOWN to "CAMERA_UNKNOWN"
        val comp = app.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToString()
            ?.takeIf { BehindHomePlan.safeComponent(it) } ?: return TripStepCode.NOT_STAGED to "NO_COMPONENT"
        val until = now + TripPlan.NORMAL_DEADLINE_MS
        var last = TripPlan.Normal.UNREAD
        while (now < until) {
            val sh = host.shell() ?: return TripStepCode.NO_CHANNEL to "NO_CHANNEL"
            val out = runCatching { sh(TripPlan.normalCmd(sig, homeComps, comp)) }.getOrDefault("")
            val after = StackParse.parse(runCatching { sh(BehindHomePlan.LIST_CMD) }.getOrDefault(""))
            last = TripPlan.normalOutcome(out, after, sig, homeComps)
            if (last == TripPlan.Normal.OPENED) return TripStepCode.OPENED to last.name
            if (last == TripPlan.Normal.OTHER_FRONT) return TripStepCode.OTHER_FRONT to last.name
            sleep(TripPlan.CAMERA_RETRY_MS)
        }
        return (if (last == TripPlan.Normal.CAMERA) TripStepCode.CAMERA else TripStepCode.TIMEOUT) to "TIMEOUT_$last"
    }

    /** R0.6 — CÙNG phép với chip *Chạy nền* của Cài đặt (L4 · D5): [InstalledApps.isSystem]. */
    private fun isSystem(pkg: String): Boolean = InstalledApps.isSystem(app, pkg)

    private companion object {
        const val VIEW_TIMEOUT_MS = 2_000L
        const val BEHIND_TIMEOUT_MS = 30_000L
    }
}
