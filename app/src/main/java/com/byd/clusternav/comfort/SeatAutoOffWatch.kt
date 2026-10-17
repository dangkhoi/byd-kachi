package com.byd.clusternav.comfort

import android.app.Application
import android.content.Context
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Prefs
import com.byd.clusternav.system.ScreenLit
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * ═══ 3.1.12 · SEAT-AUTOOFF-ANY-ON — canh MỌI ghế đang bật theo SỰ KIỆN xe; xe tự tắt (dấu `0x095`) ⇒ bật lại ═══════════════════
 *
 * Luật nằm ở `:core` ([SeatAutoOffGuard], test thuần dựng từ log xe 17/10). Lớp này chỉ: xin sự kiện ghế + dấu `0x095`
 * ([AppContainer.carEvents]), đọc trạng thái xe một lượt lúc bắt đầu / lúc helper nối lại ([seed]), chuyển sự kiện ghế vào luật,
 * hẹn MỘT lượt quyết +8 s sau mỗi lần tắt, và ghi qua [SeatComfortApplier.reapplySeat] (đường ghi DUY NHẤT của ghế). Log tag
 * [SeatComfortApplier.TAG]. Chẩn đoán: `docs/diagnostics/seat-vent-auto-off-2026-10-14.md` §13–§14.
 *
 * ── Vì sao không còn "canh ghế vừa áp lúc lên nguồn" (3.1.6) ─────────────────────────────────────────────────────────────────
 * [ĐO 17/10] bản cũ giữ danh sách ghế trong RAM + cổng "lần lên nguồn": cài đè 11:05 ⇒ thôi canh ngay; ghế bật bằng ô/giọng/phím
 * không bao giờ được canh ⇒ 11:08:17 xe tắt cả hai ghế (kèm dấu `0x095` +3,0 s) mà Kachi không bật lại. Nay: biết ghế đang bật
 * từ chính XE (đọc + sự kiện), ai bật cũng vậy; tiến trình chết ⇒ tiến trình mới đọc lại xe ở [install].
 *
 * ── Chi phí (owner: mỗi bản sửa phải chứng minh không nặng thêm) ─────────────────────────────────────────────────────────────
 *  - 0 poll mới. Sự kiện: 4 id ghế + 2 id dấu (`sub` chung của [AppContainer.carEvents], cùng helper đã chạy cho màn). Giữ nguồn
 *    helper `SEAT_WATCH` khi công tắc ghế BẬT (tiến trình chết theo mỗi lần tắt máy [ĐO 29/09] ⇒ không giữ qua đêm).
 *  - Đọc HAL: 4 lượt mỗi lần [seed] (tiến trình bật · helper nối lại), +2 lượt ghế (cache sự kiện trước) +1 lượt `power_level` mỗi lần ghế bị tắt,
 *    +1 lượt kiểm sau bật lại. Một luồng daemon hẹn giờ `seat-autooff`, rỗi khi không có lần tắt chờ quyết.
 *
 * ── Phân biệt "xe tắt" và "người tắt" ([SeatAutoOffGuard.decide]) ───────────────────────────────────────────────────────────
 * Lời ghi ghế của KACHI (mọi tiến trình — giọng nói ở `:wake`) để lại dấu bền ở [noteKachiWrite]; lần tắt cùng lúc với dấu đó là
 * người dùng tắt qua Kachi. Còn lại chỉ bật lại khi THẤY dấu `0x095` quanh lần tắt (3/3 mẫu xe tự tắt có logcat đều kèm dấu).
 */
object SeatAutoOffWatch {

    private const val THREAD = "seat-autooff"
    private const val MARKER_PREFIX = "seat-kachi-write-"
    private const val REAPPLY_PREFIX = "seat-reapply-"
    private const val VERIFY_DELAY_MS = 3_000L

    private val guard = SeatAutoOffGuard()

    /** Tiến trình NÀY đang xin sự kiện ghế (chỉ để biết có chuyển sự kiện vào luật hay không — quyết định đi theo dữ liệu xe). */
    @Volatile private var watching = false

    private val exec: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, THREAD).apply { isDaemon = true } }
    }

    /** `AppContainer.get` (mọi tiến trình) — chỉ tiến trình chính canh. Chỉ hẹn một việc nền. */
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        if (!isMainProcess(app)) return
        runCatching { exec.execute { runCatching { ensureNow(app) }.onFailure { warn("bật canh ghế hỏng", it) } } }
    }

    /** Công tắc ghế / mức ghế vừa đổi (`SeatComfortApplier`) — bật hoặc thôi canh cho khớp công tắc. Không chặn. */
    fun ensure(ctx: Context) = install(ctx)

    private fun ensureNow(app: Context) {
        val on = Prefs.seatComfortEnabled(app)
        if (on == watching) return
        watching = on
        val feed = AppContainer.get(app).carEvents
        if (!on) {
            feed.seatWatch(null)
            log("thôi canh ghế: công tắc ghế tắt")
            return
        }
        feed.seatWatch(SeatControls.DATUMS)
        log("canh MỌI ghế đang bật theo sự kiện xe (${SeatControls.DATUMS.sorted().joinToString(",")} + dấu 0x095) — ai bật cũng canh")
        seed(app, "tiến trình bật")
    }

    /** Helper vừa chào (lần đầu / nối lại): sự kiện lúc đứt dây đã mất ⇒ đọc lại ghế một lượt (trên luồng canh, không chặn socket). */
    fun onLinkUp(ctx: Context) {
        if (!watching) return
        val app = ctx.applicationContext
        runCatching { exec.execute { runCatching { seed(app, "helper nối lại") } } }
    }

    /** Sự thật của xe ⇒ luật (chạy trên luồng canh). */
    private fun seed(app: Context, why: String) {
        if (!watching) return
        val container = AppContainer.get(app)
        val parts = SeatControls.SEAT_INDICES.map { seat ->
            val r = SeatAutoOffGuard.Reading(heat = read(container, seat, SeatComfort.SeatMode.HEAT), vent = read(container, seat, SeatComfort.SeatMode.COOL))
            synchronized(guard) { guard.seed(seat, r) }
            "${seatName(seat)} mát=${r.vent ?: "?"} sưởi=${r.heat ?: "?"}"
        }
        log("ghế theo xe ($why): ${parts.joinToString(" · ")}")
    }

    private fun read(container: AppContainer, seat: Int, mode: SeatComfort.SeatMode): Int? =
        SeatControls.datumOf(seat, mode)?.let { SeatAutoOffGuard.raw(container.telemetryText(it)) }

    /** `AppContainer.carEvents` báo datum đổi theo sự kiện (luồng đọc socket — không chặn). */
    fun onDatums(ctx: Context, ids: Set<String>) {
        if (!watching) return
        val container = AppContainer.get(ctx.applicationContext)
        for (id in ids) {
            val bind = SeatControls.bindOfDatum(id) ?: continue
            val (at, raw) = container.halEvents.lastEvent(id) ?: continue
            val v = synchronized(guard) { guard.onEvent(bind.seatIndex, bind.mode, SeatAutoOffGuard.raw(raw), at) }
            if (v is SeatAutoOffGuard.Verdict.DecideAt) {
                log("ghế ${seatName(v.seatIndex)} ${modeName(v.mode)} ${v.state} → Tắt — chờ ${(v.atMs - at) / 1000} s xem dấu 0x095")
                schedule(ctx.applicationContext, v)
            }
        }
    }

    private fun schedule(app: Context, v: SeatAutoOffGuard.Verdict.DecideAt) {
        val delay = (v.atMs - System.currentTimeMillis()).coerceAtLeast(0L)
        runCatching { exec.schedule({ runCatching { decide(app, v.seatIndex) }.onFailure { warn("quyết ghế ném (bỏ qua)", it) } }, delay, TimeUnit.MILLISECONDS) }
    }

    private fun decide(app: Context, seat: Int) {
        val container = AppContainer.get(app)
        val ev = evidence(app, container, seat)
        when (val v = synchronized(guard) { guard.decide(seat, System.currentTimeMillis(), ev) }) {
            is SeatAutoOffGuard.Verdict.Reapply -> reapply(app, container, v)
            is SeatAutoOffGuard.Verdict.Skip -> log("ghế ${seatName(v.seatIndex)} ${modeName(v.mode)} ${v.state} bị tắt, KHÔNG bật lại: ${v.reason}")
            is SeatAutoOffGuard.Verdict.DecideAt -> schedule(app, v)
            SeatAutoOffGuard.Verdict.Keep -> Unit
        }
    }

    /** Bằng chứng lúc quyết — dấu `0x095` từ luồng sự kiện ([AppContainer.halEvents]), dấu ghi của Kachi + ngân sách từ `filesDir`. */
    private fun evidence(app: Context, container: AppContainer, seat: Int): SeatAutoOffGuard.Evidence {
        val ev = container.halEvents
        val sig = com.byd.clusternav.launcher.HalEventMap.SEAT_SIGNATURE.mapNotNull { container.halGateway.featureIdByName(it) }
        return SeatAutoOffGuard.Evidence(
            signatureLive = ev.live && sig.isNotEmpty() && sig.all(ev::signalRegistered),
            signatureAtMs = sig.mapNotNull(ev::signalSeenAt).maxOrNull(),
            kachiWriteAtMs = lastKachiWrite(app, seat),
            now = SeatAutoOffGuard.Reading(heat = read(container, seat, SeatComfort.SeatMode.HEAT), vent = read(container, seat, SeatComfort.SeatMode.COOL)),
            reappliedAtMs = reapplied(app, seat),
        )
    }

    /** Kiểm lại xe bật + màn sáng ngay trước lúc ghi, ghi, ghi sổ ngân sách, hẹn một lượt đọc lại sau 3 s để log. */
    private fun reapply(app: Context, container: AppContainer, v: SeatAutoOffGuard.Verdict.Reapply) {
        val power = SeatAutoOffGuard.raw(container.telemetryText("power_level"))
        // Lệnh GHI tự khởi phát ⇒ cổng màn FAIL-CLOSED ([ScreenLit.readStrict]); `power_level` không đọc được thì để cổng màn quyết.
        if (!SeatAutoOffGuard.carOn(power) || !ScreenLit.readStrict(app)) {
            log("xe tự tắt ghế ${seatName(v.seatIndex)} nhưng KHÔNG bật lại: xe/màn tắt hoặc không rõ (power_level=$power)")
            return
        }
        val method = SeatComfort.methodFor(v.mode)
        val rc = SeatComfortApplier.reapplySeat(app, v.seatIndex, v.mode, v.state)
        if (!rc.startsWith("bỏ:") && !rc.startsWith("ném:")) noteReapplied(app, v.seatIndex)
        log(
            "xe tự tắt ghế (firmware) → bật lại: ghế ${seatName(v.seatIndex)} $method(seatId=${SeatComfort.seatId(v.seatIndex)}, state=${v.state}) " +
                "· dấu 0x095 THẤY +${String.format(java.util.Locale.ROOT, "%.1f", v.signatureLagMs / 1000.0)} s sau lần tắt · $rc",
        )
        val datum = SeatControls.datumOf(v.seatIndex, v.mode)
        runCatching {
            exec.schedule({ runCatching { log("sau bật lại: ghế ${seatName(v.seatIndex)} ${datum ?: "?"}=${datum?.let { container.telemetryText(it) }}") } }, VERIFY_DELAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    /** `WakeOnWriteControl` (mọi tiến trình) — mỗi lệnh ghi nút xe; chỉ nút ghế để lại dấu. */
    fun onControlWrite(ctx: Context, controlId: String) {
        val seat = SeatControls.seatIndexOf(controlId) ?: return
        noteKachiWrite(ctx, seat)
    }

    /**
     * Kachi vừa ghi ghế [seatIndex] (`null` = mọi ghế). Ghi DẤU (wall ms, cùng đồng hồ với `HalEventCache`) vào `filesDir` — thấy được từ
     * mọi tiến trình (giọng nói ghi ghế từ `:wake`, luồng canh ở tiến trình chính); RAM không đủ (CLAUDE.md §5).
     */
    fun noteKachiWrite(ctx: Context, seatIndex: Int?) {
        val app = ctx.applicationContext
        val at = System.currentTimeMillis().toString()
        val seats = seatIndex?.let { listOf(it) } ?: SeatComfort.SEATS.map { it.index }
        for (s in seats) runCatching { marker(app, s).writeText(at) }.onFailure { warn("ghi dấu ghế $s hỏng", it) }
    }

    private fun marker(app: Context, seat: Int) = File(app.filesDir, "$MARKER_PREFIX$seat")

    private fun lastKachiWrite(app: Context, seat: Int): Long? =
        runCatching { marker(app, seat).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() }.getOrNull()

    /** Sổ ngân sách bật lại (bền — sống qua tiến trình, nên cài đè giữa chuyến không mở thêm lượt). Giữ ≤ 8 mốc gần nhất. */
    private fun ledger(app: Context, seat: Int) = File(app.filesDir, "$REAPPLY_PREFIX$seat")

    private fun reapplied(app: Context, seat: Int): List<Long> =
        runCatching { ledger(app, seat).takeIf { it.exists() }?.readLines()?.mapNotNull { it.trim().toLongOrNull() } }.getOrNull().orEmpty()

    private fun noteReapplied(app: Context, seat: Int) {
        val all = (reapplied(app, seat) + System.currentTimeMillis()).takeLast(8)
        runCatching { ledger(app, seat).writeText(all.joinToString("\n")) }.onFailure { warn("ghi sổ bật lại ghế $seat hỏng", it) }
    }

    private fun isMainProcess(app: Context): Boolean =
        runCatching { Application.getProcessName() == app.packageName }.getOrDefault(false)

    private fun seatName(seat: Int): String = SeatComfort.SEATS.getOrNull(seat)?.labelKey ?: "#$seat"
    private fun modeName(m: SeatComfort.SeatMode): String = if (m == SeatComfort.SeatMode.COOL) "mát" else "sưởi"
    private fun log(msg: String) { runCatching { Log.i(SeatComfortApplier.TAG, msg) } }
    private fun warn(msg: String, t: Throwable) { runCatching { Log.w(SeatComfortApplier.TAG, msg, t) } }
}
