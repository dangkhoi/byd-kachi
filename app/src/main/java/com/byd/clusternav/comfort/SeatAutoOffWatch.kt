package com.byd.clusternav.comfort

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.KachiReadyLog
import com.byd.clusternav.system.ScreenLit
import java.io.File

/**
 * ═══ 3.1.6 · SEAT-AUTO-OFF-REAPPLY — canh ghế sau lượt áp lúc lên nguồn, bật lại MỘT lần nếu xe tự tắt ═════════════════════════
 *
 * Luật nằm ở `:core` ([SeatAutoOffGuard], test thuần). Lớp này chỉ: đọc số · hỏi luật · ghi qua
 * [SeatComfortApplier.reapplySeat] (đường ghi DUY NHẤT của ghế) · log tag [SeatComfortApplier.TAG].
 * Chẩn đoán + quyết định: `docs/diagnostics/seat-vent-auto-off-2026-10-14.md` §13.
 *
 * ── Chi phí (owner: mỗi bản sửa phải chứng minh không nặng thêm) ─────────────────────────────────────────────────────────────
 *  - MỘT luồng daemon `seat-autooff` mỗi lần lên nguồn, CHỈ khi `SeatComfort` vừa áp ≥ 1 ghế; tự thoát khi hết ghế canh hoặc ở
 *    +15′ ([SeatAutoOffGuard.WINDOW_END_MS]). Không luồng thường trực, không listener.
 *  - Mỗi nhịp (30 s tới +5′50″, 10 s từ đó tới +15′): 2 lượt đọc HAL mỗi ghế đang canh — cùng datum `seat_*_state(_r)` mà ô/chip ghế đọc, qua
 *    `AppContainer.telemetryText` (bảng đọc chung, không đường đọc thứ hai) + `ScreenLit.read`. ≤ ~140 lượt đọc / ghế / lần lên nguồn.
 *  - Lúc thấy tắt: thêm 1 lượt `power_level`.
 *
 * ── Phân biệt "xe tắt" và "người tắt" ─────────────────────────────────────────────────────────────────────────────────────────
 * Dấu vân tay `0x0950000F`/`0x09500022` (+3 s sau lần tắt, §12.3): 3.1.6 HAL-EVENTS — lúc canh, [start] xin helper nghe hai id
 * này + sự kiện ghế (`CarEventFeed.seatWatch`, doc `hal-events-body-seat-2026-10-15.md`). Luật chọn ở
 * [SeatAutoOffGuard.signatureOf]: luồng sự kiện chạy + id đăng ký OK ⇒ THẤY/KHÔNG THẤY theo bằng chứng; còn lại `UNOBSERVABLE` ⇒
 * cửa sổ +6…+15′ như bản đầu (Autonomous Decision ở doc seat-vent §13). Mọi lời ghi ghế của KACHI (mọi tiến trình, kể cả giọng nói ở `:wake`) để lại dấu ở
 * [noteKachiWrite] ⇒ luật thôi canh — nên "người tắt" còn lại chỉ là UI/giọng BYD trong cửa sổ (rủi ro chấp nhận, §13).
 */
object SeatAutoOffWatch {

    data class Applied(val seatIndex: Int, val mode: SeatComfort.SeatMode, val state: Int)

    /**
     * 3.1.6 HAL-EVENTS — dấu vân tay của ghế [seat] ở lượt đọc này, từ luồng sự kiện ([AppContainer.halEvents]). Không có luồng /
     * chưa đăng ký ⇒ `UNOBSERVABLE` (cửa sổ thời gian — hành vi trước khi có helper sự kiện).
     */
    private fun signature(container: AppContainer, seat: Int): SeatAutoOffGuard.Signature {
        val ev = container.halEvents
        val now = System.currentTimeMillis()   // cùng đồng hồ với `HalEventCache`
        val sig = signatureIds(container)
        val live = ev.live && sig.isNotEmpty() && sig.all(ev::signalRegistered)
        val sigAt = sig.mapNotNull(ev::signalSeenAt).maxOrNull()
        val offAt = SeatComfort.SeatMode.values().mapNotNull { m ->
            SeatControls.datumOf(seat, m)?.let(ev::lastEvent)?.takeIf { (_, raw) -> (SeatAutoOffGuard.raw(raw) ?: 99) <= SeatComfort.STATE_OFF }?.first
        }.maxOrNull()
        return SeatAutoOffGuard.signatureOf(live, now, sigAt, offAt)
    }

    private fun signatureIds(container: AppContainer): List<Int> =
        com.byd.clusternav.launcher.HalEventMap.SEAT_SIGNATURE.mapNotNull { container.halGateway.featureIdByName(it) }

    private const val THREAD = "seat-autooff"
    private const val MARKER_PREFIX = "seat-kachi-write-"
    private const val VERIFY_DELAY_MS = 3_000L

    private val guard = SeatAutoOffGuard()

    /** Thế hệ: mỗi lần lên nguồn một số; luồng cũ thấy số đổi ⇒ thoát. */
    @Volatile private var generation = 0

    /** Gọi từ [SeatComfortApplier] (lượt áp lúc lên nguồn) — luồng nền. */
    fun start(ctx: Context, applied: List<Applied>, poweredAt: Long) {
        val app = ctx.applicationContext
        val now = SystemClock.elapsedRealtime()
        clearMarkers(app)   // dấu của lần lên nguồn trước (elapsedRealtime có thể đã quay về 0 sau khởi động lại)
        val gen = synchronized(guard) {
            guard.reset()
            applied.forEach { guard.arm(it.seatIndex, it.mode, it.state, poweredAt, now) }
            startedAt = now
            ++generation
        }
        val armed = guard.armedSeats()
        if (armed.isEmpty()) return
        // 3.1.6 HAL-EVENTS: nghe sự kiện ghế + dấu 0x095 suốt lúc canh (helper vắng ⇒ không có gì đổi: cửa sổ thời gian).
        val datums = armed.flatMap { s -> SeatComfort.SeatMode.values().mapNotNull { SeatControls.datumOf(s, it) } }.toSet()
        runCatching { AppContainer.get(app).carEvents.seatWatch(datums) }.onFailure { warn("xin sự kiện ghế hỏng (bỏ qua — cửa sổ thời gian)", it) }
        log("canh ghế $armed tới +${SeatAutoOffGuard.WINDOW_END_MS / 60_000}′ sau lên nguồn (dấu 0x095: theo luồng sự kiện nếu có, không thì cửa sổ thời gian)")
        Thread({ runCatching { loop(app, gen) }.onFailure { warn("luồng canh ném (bỏ qua)", it) } }, THREAD)
            .apply { isDaemon = true }
            .start()
    }

    /** `WakeOnWriteControl` (mọi tiến trình) — mỗi lệnh ghi nút xe; chỉ nút ghế để lại dấu. */
    fun onControlWrite(ctx: Context, controlId: String) {
        val seat = SeatControls.seatIndexOf(controlId) ?: return
        noteKachiWrite(ctx, seat)
    }

    /**
     * Kachi vừa ghi ghế [seatIndex] (`null` = mọi ghế). Ghi DẤU vào `filesDir` (thấy được từ mọi tiến trình — giọng nói ghi ghế
     * từ `:wake`, luồng canh ở tiến trình chính) — RAM không đủ (CLAUDE.md §5).
     */
    fun noteKachiWrite(ctx: Context, seatIndex: Int?) {
        val app = ctx.applicationContext
        val at = SystemClock.elapsedRealtime().toString()
        val seats = seatIndex?.let { listOf(it) } ?: SeatComfort.SEATS.map { it.index }
        for (s in seats) runCatching { marker(app, s).writeText(at) }.onFailure { warn("ghi dấu ghế $s hỏng", it) }
    }

    private fun loop(app: Context, gen: Int) {
        while (true) {
            val delay = synchronized(guard) { guard.nextDelayMs(SystemClock.elapsedRealtime()) } ?: break
            Thread.sleep(delay)
            if (gen != generation) return
            tick(app, gen)
        }
        if (gen == generation) {
            log("thôi canh ghế (hết hạn / hết ghế)")
            runCatching { AppContainer.get(app).carEvents.seatWatch(null) }
        }
    }

    private fun tick(app: Context, gen: Int) {
        val seats = synchronized(guard) { guard.armedSeats() }
        if (seats.isEmpty()) return
        val container = AppContainer.get(app)
        val active = ScreenLit.read(app) && !newWake()
        for (seat in seats) {
            lastKachiWrite(app, seat)?.let { at -> report(synchronized(guard) { guard.onKachiWrite(seat, at) }) }
            val modes = SeatComfort.SeatMode.values().associateWith { m ->
                SeatControls.datumOf(seat, m)?.let { SeatAutoOffGuard.raw(container.telemetryText(it)) }
            }
            val reading = SeatAutoOffGuard.Reading(heat = modes[SeatComfort.SeatMode.HEAT], vent = modes[SeatComfort.SeatMode.COOL])
            val v = synchronized(guard) { guard.observe(seat, SystemClock.elapsedRealtime(), reading, active, signature(container, seat)) }
            if (v is SeatAutoOffGuard.Verdict.Reapply) reapply(app, container, v, gen) else report(v)
        }
    }

    /** Một lần duy nhất: kiểm lại xe bật + màn sáng ngay trước lúc ghi, ghi, đọc lại sau 3 s để log. */
    private fun reapply(app: Context, container: AppContainer, v: SeatAutoOffGuard.Verdict.Reapply, gen: Int) {
        val seatName = SeatComfort.SEATS.getOrNull(v.seatIndex)?.labelKey ?: "#${v.seatIndex}"
        val power = SeatAutoOffGuard.raw(container.telemetryText("power_level"))
        // Soát Pass 6: lệnh GHI tự khởi phát ⇒ cổng màn FAIL-CLOSED ([ScreenLit.readStrict]: không hỏi được màn ⇒ không ghi); `power_level`
        // không đọc được thì để cổng màn quyết (doc seat-vent §13.3 — getter OVERDRIVE, owner chấp nhận).
        if (!SeatAutoOffGuard.carOn(power) || !ScreenLit.readStrict(app) || gen != generation) {
            log("xe tự tắt ghế $seatName nhưng KHÔNG bật lại: xe/màn tắt hoặc không rõ (power_level=$power)")
            return
        }
        val method = SeatComfort.methodFor(v.mode)
        val rc = SeatComfortApplier.reapplySeat(app, v.seatIndex, v.mode, v.state)
        log(
            "xe tự tắt ghế (firmware) → bật lại: ghế $seatName $method(seatId=${SeatComfort.seatId(v.seatIndex)}, state=${v.state}) " +
                "· tắt lúc +${minutes(v.dropAtMs)}′ sau lên nguồn · dấu 0x095: ${signatureText(v.signature)} · $rc",
        )
        runCatching { Thread.sleep(VERIFY_DELAY_MS) }
        val datum = SeatControls.datumOf(v.seatIndex, v.mode)
        log("sau bật lại: ghế $seatName ${datum ?: "?"}=${datum?.let { container.telemetryText(it) }}")
    }

    private fun report(v: SeatAutoOffGuard.Verdict) {
        if (v is SeatAutoOffGuard.Verdict.Disarm) log("thôi canh ghế #${v.seatIndex}: ${v.reason}")
    }

    /** Lúc bắt đầu canh lần lên nguồn này (elapsedRealtime). */
    @Volatile private var startedAt = Long.MAX_VALUE

    /**
     * Một lần màn bật MỚI được ghi SAU lúc bắt đầu canh ⇒ đã có lượt tắt-bật chen giữa (`KachiReadyLog.wake` gộp các tín hiệu
     * của cùng một lần thức) ⇒ không còn là lần lên nguồn đã áp; lần mới tự áp lại qua BOOT_COMPLETED.
     */
    private fun newWake(): Boolean = KachiReadyLog.lastScreenOnAt() > startedAt

    private fun signatureText(s: SeatAutoOffGuard.Signature): String = when (s) {
        SeatAutoOffGuard.Signature.SEEN -> "THẤY"
        SeatAutoOffGuard.Signature.NOT_SEEN -> "không thấy"
        SeatAutoOffGuard.Signature.UNOBSERVABLE -> "không quan sát được (theo cửa sổ +6…+15′)"
    }

    private fun marker(app: Context, seat: Int) = File(app.filesDir, "$MARKER_PREFIX$seat")

    private fun lastKachiWrite(app: Context, seat: Int): Long? =
        runCatching { marker(app, seat).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() }.getOrNull()

    private fun clearMarkers(app: Context) = SeatComfort.SEATS.forEach { runCatching { marker(app, it.index).delete() } }

    private fun minutes(ms: Long) = String.format(java.util.Locale.ROOT, "%.1f", ms / 60_000.0)
    private fun log(msg: String) { runCatching { Log.i(SeatComfortApplier.TAG, msg) } }
    private fun warn(msg: String, t: Throwable) { runCatching { Log.w(SeatComfortApplier.TAG, msg, t) } }
}
