package com.byd.clusternav.comfort

import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.HalReadTables

/**
 * ═══ SEAT-AUTOOFF-ANY-ON (3.1.12) · xe tự tắt ghế (firmware 2606) ⇒ bật lại — canh MỌI ghế đang bật, theo SỰ KIỆN xe ═══════════
 *
 * Owner 15/10: *"thấy tắt thì mở lại OK"*. Owner 17/10: *"cái mát ghế nó cũng tự tắt sau 10p nha"* — bản 3.1.6 không bật lại.
 * Gốc [ĐO log 17/10, `docs/diagnostics/seat-vent-auto-off-2026-10-14.md` §14]: bản 3.1.6 chỉ canh ghế mà CHÍNH `SeatComfort` vừa áp
 * lúc lên nguồn, giữ trong RAM, kèm cổng "lần lên nguồn" — lượt cài đè 11:05:14 làm luồng canh tự thôi ngay nhịp đầu
 * (`màn/xe tắt hoặc lần lên nguồn mới`), ghế bật bằng ô/giọng/phím (11:06:09, 11:12:53) không bao giờ được canh. Vi phạm
 * CLAUDE.md §5 (quyết bằng cờ RAM thay vì sự thật của xe).
 *
 * ── Luật mới (mỗi ghế) ─────────────────────────────────────────────────────────────────────────────────────────────────────────
 *  1. **Biết ghế đang bật từ XE**: [seed] (đọc trạng thái xe lúc tiến trình bật / lúc nối lại helper) và [onEvent] (sự kiện
 *     `0x4fa000xx` của chính ghế) — ai bật cũng vậy (ô Kachi, giọng, phím, UI BYD, lượt áp lúc lên nguồn). Không cửa sổ thời gian,
 *     không mốc lên nguồn.
 *  2. **Bị tắt** = sự kiện về Tắt của ĐÚNG chức năng đang bật ⇒ hẹn [decide] sau [decideAfterMs] (dấu `0x095` tới +3 s, §12.3/§14).
 *     Bật lại (bất kỳ mức) trước hạn ⇒ huỷ.
 *  3. **Xe tắt hay người tắt** ([decide]) — bật lại CHỈ khi đủ cả:
 *     - KHÔNG có lời ghi ghế của Kachi quanh lần tắt (dấu bền `filesDir`, mọi tiến trình — `SeatAutoOffWatch.noteKachiWrite`);
 *     - chức năng kia của ghế KHÔNG bật (mát ↔ sưởi loại trừ nhau ở MCU);
 *     - đường dấu vân tay quan sát được (helper đăng ký OK hai id `0x095`) VÀ thấy dấu trong
 *       [offAt − [SIGNATURE_BEFORE_MS], offAt + [decideAfterMs]] ([signatureOf]) — 3/3 chuyến có logcat (14/10, 15/10, 17/10) lần tắt
 *       của firmware luôn kèm dấu ở +3,0 s; lần tắt không dấu (17/10 11:35:39) ⇒ KHÔNG bật lại;
 *     - chưa hết ngân sách: ≤ [maxReapplies] lần mỗi ghế trong [budgetWindowMs] (danh sách mốc bền do app giữ — sống qua tiến trình).
 *     Không quan sát được dấu ⇒ KHÔNG bật lại (fail-safe: canh mọi ghế mà không có bằng chứng thì "người tắt" sẽ bị bật lại).
 *  4. Bật lại cùng chức năng + mức đã thấy bật cuối cùng.
 *
 * Luồng: KHÔNG an toàn đa luồng — bên gọi giữ khoá.
 */
class SeatAutoOffGuard(
    /** Hẹn quyết sau lần tắt bao lâu (dấu `0x095` tới +3,0 s; chừa trễ bus/helper). */
    val decideAfterMs: Long = SIGNATURE_LAG_MS,
    /** Số lần bật lại tối đa mỗi ghế trong [budgetWindowMs]. */
    val maxReapplies: Int = MAX_REAPPLIES,
    val budgetWindowMs: Long = BUDGET_WINDOW_MS,
) {

    /** Dấu vân tay `0x0950000F`/`0x09500022` quanh mốc tắt (§12.3). */
    enum class Signature { UNOBSERVABLE, SEEN, NOT_SEEN }

    /** Trạng thái MỘT ghế đọc từ xe: mã khung thô (1 = Tắt · 2/3 = bật), `null` = không đọc được. */
    data class Reading(val heat: Int?, val vent: Int?)

    /** Những gì app biết lúc [decide] — mọi mốc cùng một đồng hồ (wall ms của `HalEventCache`). */
    data class Evidence(
        /** Helper đang nối VÀ hai id dấu đã đăng ký OK. */
        val signatureLive: Boolean,
        /** Mốc thấy dấu `0x095` gần nhất (`null` = chưa thấy). */
        val signatureAtMs: Long?,
        /** Mốc lời ghi ghế này gần nhất của Kachi (`null` = chưa có). */
        val kachiWriteAtMs: Long?,
        /** Ghế lúc quyết (một lượt đọc, cache sự kiện trước): chức năng kia bật ⇒ không; chức năng bị tắt đã bật lại ⇒ không. */
        val now: Reading,
        /** Các mốc Kachi đã bật lại ghế này (bền). */
        val reappliedAtMs: List<Long>,
    )

    sealed class Verdict {
        object Keep : Verdict()

        /** App hẹn [decide] cho [seatIndex] lúc [atMs]. */
        data class DecideAt(val seatIndex: Int, val mode: SeatComfort.SeatMode, val state: Int, val offAtMs: Long, val atMs: Long) : Verdict()

        /** Bật lại [seatIndex] bằng [mode] + [state]. [signatureLagMs] = dấu tới bao lâu sau lần tắt (để log). */
        data class Reapply(val seatIndex: Int, val mode: SeatComfort.SeatMode, val state: Int, val offAtMs: Long, val signatureLagMs: Long) : Verdict()

        /** Ghế bị tắt nhưng KHÔNG bật lại (lý do để log). */
        data class Skip(val seatIndex: Int, val mode: SeatComfort.SeatMode, val state: Int, val reason: String) : Verdict()
    }

    private data class On(val mode: SeatComfort.SeatMode, val state: Int)
    private data class Pending(val mode: SeatComfort.SeatMode, val state: Int, val offAtMs: Long)

    private val lastOn = HashMap<Int, On>()
    private val pending = HashMap<Int, Pending>()

    /** Chức năng + mức đang bật của ghế theo hiểu biết hiện tại (`null` = tắt/không biết) — để test. */
    fun onState(seatIndex: Int): Pair<SeatComfort.SeatMode, Int>? = lastOn[seatIndex]?.let { it.mode to it.state }

    /**
     * Sự thật của xe lúc tiến trình bật / lúc nối lại helper: THAY hiểu biết về ghế (đọc thấy Tắt ⇒ quên mức cũ). Không đọc được
     * (`null` cả hai) ⇒ giữ nguyên. Không đụng lần tắt đang chờ quyết (quyết theo bằng chứng của chính lần tắt đó).
     */
    fun seed(seatIndex: Int, reading: Reading) {
        if (reading.heat == null && reading.vent == null) return
        val on = when {
            (reading.vent ?: 0) > SeatComfort.STATE_OFF -> On(SeatComfort.SeatMode.COOL, reading.vent!!)
            (reading.heat ?: 0) > SeatComfort.STATE_OFF -> On(SeatComfort.SeatMode.HEAT, reading.heat!!)
            else -> null
        }
        if (on == null) lastOn.remove(seatIndex) else lastOn[seatIndex] = on
    }

    /** Sự kiện của xe: chức năng [mode] của [seatIndex] vừa thành [raw] lúc [atMs]. */
    fun onEvent(seatIndex: Int, mode: SeatComfort.SeatMode, raw: Int?, atMs: Long): Verdict {
        if (raw == null) return Verdict.Keep
        if (raw > SeatComfort.STATE_OFF) {
            lastOn[seatIndex] = On(mode, raw)
            pending.remove(seatIndex)                     // bật lại trước hạn quyết ⇒ không còn gì để bật lại
            return Verdict.Keep
        }
        val on = lastOn[seatIndex]?.takeIf { it.mode == mode } ?: return Verdict.Keep   // tắt một chức năng vốn không bật
        lastOn.remove(seatIndex)
        if (pending.containsKey(seatIndex)) return Verdict.Keep
        pending[seatIndex] = Pending(on.mode, on.state, atMs)
        return Verdict.DecideAt(seatIndex, on.mode, on.state, atMs, atMs + decideAfterMs)
    }

    /** Tới hạn quyết của [seatIndex] (lúc [nowMs]). Không còn lần tắt chờ (đã bật lại / đã quyết) ⇒ [Verdict.Keep]. */
    fun decide(seatIndex: Int, nowMs: Long, ev: Evidence): Verdict {
        val p = pending[seatIndex] ?: return Verdict.Keep
        if (nowMs < p.offAtMs + decideAfterMs) return Verdict.DecideAt(seatIndex, p.mode, p.state, p.offAtMs, p.offAtMs + decideAfterMs)
        pending.remove(seatIndex)
        fun skip(why: String) = Verdict.Skip(seatIndex, p.mode, p.state, why)
        val kachi = ev.kachiWriteAtMs
        if (kachi != null && kachi >= p.offAtMs - KACHI_WRITE_ECHO_MS && kachi <= nowMs) return skip("Kachi vừa ghi ghế này (người dùng tắt qua Kachi)")
        val applied = if (p.mode == SeatComfort.SeatMode.COOL) ev.now.vent else ev.now.heat
        val other = if (p.mode == SeatComfort.SeatMode.COOL) ev.now.heat else ev.now.vent
        if (other != null && other > SeatComfort.STATE_OFF) return skip("chức năng kia của ghế đang bật (mát ↔ sưởi loại trừ nhau)")
        if (applied != null && applied > SeatComfort.STATE_OFF) return skip("ghế đã bật lại (mức $applied)")
        when (signatureOf(ev.signatureLive, p.offAtMs, ev.signatureAtMs, decideAfterMs)) {
            Signature.UNOBSERVABLE -> return skip("không quan sát được dấu 0x095 (helper chưa nối / chưa đăng ký) ⇒ không đoán")
            Signature.NOT_SEEN -> return skip("không có dấu 0x095 quanh lần tắt ⇒ người dùng/đường khác tắt")
            Signature.SEEN -> Unit
        }
        val recent = ev.reappliedAtMs.count { nowMs - it in 0 until budgetWindowMs }
        if (recent >= maxReapplies) return skip("đã bật lại $recent lần trong ${budgetWindowMs / 60_000}′ ⇒ thôi")
        return Verdict.Reapply(seatIndex, p.mode, p.state, p.offAtMs, ev.signatureAtMs!! - p.offAtMs)
    }

    companion object {
        /** Dấu tới +3,0 s sau lần tắt [ĐO 3/3]; quyết ở +8 s (chừa 5 s trễ bus/helper). */
        const val SIGNATURE_LAG_MS = 8_000L

        /** Dấu đến TRƯỚC sự kiện tắt tối đa ngần này vẫn tính (thứ tự hai luồng sự kiện không bảo đảm). */
        const val SIGNATURE_BEFORE_MS = 2_000L

        /** Lời ghi của Kachi tới TRƯỚC sự kiện của chính nó ~25 ms [ĐO 15/10 §12.4]; dấu ghi sau lúc ghi trả ⇒ nới 5 s. */
        const val KACHI_WRITE_ECHO_MS = 5_000L

        /** Ngân sách: 2 lần mỗi ghế mỗi 60′ (một chuyến + một lần tắt-nổ máy ngắn); xe tắt lần thứ ba ⇒ thôi, không giành nhau với xe. */
        const val MAX_REAPPLIES = 2
        const val BUDGET_WINDOW_MS = 60 * 60_000L

        /** Cấp nguồn bật máy (`BYDAutoBodyworkDevice`: 0 OFF · 1 ACC · **2 ON** · **3 OK** · 4 FAKE_OK). */
        private val CAR_ON_LEVELS = setOf(2, 3)

        /** Xe đang bật? `null` (không đọc được) ⇒ không chặn — cổng màn sáng vẫn giữ. ACC/OFF/FAKE_OK ⇒ không. */
        fun carOn(powerLevel: Int?): Boolean = powerLevel == null || powerLevel in CAR_ON_LEVELS

        /** Mã khung ghế từ chuỗi getter/sự kiện: số ngoài 0..15 (sentinel `BYDAutoEventValue`) / không phải số ⇒ `null`. */
        fun raw(s: String?): Int? = s?.trim()?.toIntOrNull()?.takeIf { it in 0..15 }

        /**
         * Dấu vân tay quanh lần tắt lúc [offAtMs]: đường không sống ([live] `false`) ⇒ [Signature.UNOBSERVABLE]; thấy dấu trong
         * [offAtMs − SIGNATURE_BEFORE_MS, offAtMs + [afterMs]] ⇒ [Signature.SEEN]; còn lại ⇒ [Signature.NOT_SEEN] (đường sống, lần tắt
         * tới qua chính luồng sự kiện, đã chờ đủ [afterMs] mà dấu không tới).
         */
        fun signatureOf(live: Boolean, offAtMs: Long, signatureAtMs: Long?, afterMs: Long = SIGNATURE_LAG_MS): Signature {
            if (!live) return Signature.UNOBSERVABLE
            if (signatureAtMs != null && signatureAtMs - offAtMs in -SIGNATURE_BEFORE_MS..afterMs) return Signature.SEEN
            return Signature.NOT_SEEN
        }
    }
}

/**
 * Ánh xạ nút điều khiển ghế ↔ (chỉ số ghế, chức năng, datum đọc) — suy từ [ControlRegistry] + [HalReadTables.readArg], không liệt
 * kê tay mã nút: một nút là nút ghế khi `bindingKey` trỏ vào [SeatComfort.METHOD_VENTILATING]/[SeatComfort.METHOD_HEATING], và ghế
 * nào là do `seatID` mà đường đọc của chính nút ấy khai (1 = lái, 2 = phụ).
 */
object SeatControls {
    data class Bind(val controlId: String, val seatIndex: Int, val mode: SeatComfort.SeatMode, val readKey: String)

    val ALL: List<Bind> by lazy {
        ControlRegistry.ALL.mapNotNull { def ->
            val mode = when (def.bindingKey.substringAfterLast('.')) {
                SeatComfort.METHOD_VENTILATING -> SeatComfort.SeatMode.COOL
                SeatComfort.METHOD_HEATING -> SeatComfort.SeatMode.HEAT
                else -> return@mapNotNull null
            }
            val key = def.readKey.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val seatId = HalReadTables.readArg(key) ?: return@mapNotNull null
            Bind(def.id, seatId - 1, mode, key)
        }
    }

    /** Nút [controlId] ghi ghế nào (0-based); không phải nút ghế ⇒ `null`. */
    fun seatIndexOf(controlId: String): Int? = ALL.firstOrNull { it.controlId == controlId }?.seatIndex

    /** Datum đọc của ([seatIndex], [mode]); ghế không có nút ⇒ `null`. */
    fun datumOf(seatIndex: Int, mode: SeatComfort.SeatMode): String? =
        ALL.firstOrNull { it.seatIndex == seatIndex && it.mode == mode }?.readKey

    /** Datum đọc [datumId] là của nút ghế nào (sự kiện → ghế + chức năng); không phải datum ghế ⇒ `null`. */
    fun bindOfDatum(datumId: String): Bind? = ALL.firstOrNull { it.readKey == datumId }

    /** Mọi datum đọc của ghế (để xin sự kiện) — suy từ registry, không liệt kê tay. */
    val DATUMS: Set<String> by lazy { ALL.map { it.readKey }.toSet() }

    /** Các ghế có nút đọc được (0-based). */
    val SEAT_INDICES: List<Int> by lazy { ALL.map { it.seatIndex }.distinct().sorted() }
}
