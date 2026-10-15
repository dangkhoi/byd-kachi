package com.byd.clusternav.comfort

import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.HalReadTables

/**
 * ═══ 3.1.6 · SEAT-AUTO-OFF-REAPPLY — bật lại MỘT lần ghế mà xe tự tắt (firmware 2606) ════════════════════════════════════════
 *
 * Owner 15/10: *"fix cái ghế theo hướng này đi … thấy tắt thì mở lại OK nhé"*. Sự thật nền (`docs/diagnostics/
 * seat-vent-auto-off-2026-10-14.md` §10–§12):
 *  - [ĐO 2/2 logcat] ghế do `SeatComfort` bật lúc lên nguồn bị **chính xe** tắt ở +8,5…+10′ (`0x4fa00008 = 1`, không lời ghi IVI nào);
 *    4/4 lần lên nguồn gần nhất đều thế (§10.2);
 *  - [ĐO 2/2] bật lại SAU mốc đó thì giữ ≥ 30′ (§12.4);
 *  - [ĐO owner] sưởi và mát CÙNG một ghế đè nhau.
 *
 * Bộ máy thuần (không Android) quyết định cho từng ghế: *"lần tắt này là xe làm, bật lại một lần"* hay *"không đụng"*. Bên app
 * (`SeatAutoOffWatch`) chỉ đọc số, hỏi máy này, và ghi qua đúng đường ghi của `SeatComfortApplier`.
 *
 * ── Luật (mỗi ghế, mỗi lần lên nguồn) ─────────────────────────────────────────────────────────────────────────────────────────
 *  1. Chỉ ghế mà `SeatComfort` VỪA áp lúc lên nguồn ([arm]: chức năng + mức + mốc lên nguồn). Mức Tắt ⇒ không canh.
 *  2. Phải THẤY mức đã áp ít nhất một lần ([Phase.confirmed]) — lệnh đầu chưa ăn thì "đang tắt" không phải là "bị tắt".
 *  3. Bị tắt = chức năng đã áp về Tắt **và** chức năng kia của cùng ghế cũng Tắt (đọc được, không `null`), thấy ở **hai** lượt
 *     đọc liền nhau (chống một lượt đọc rách).
 *  4. Phân biệt *xe tắt* với *người tắt bằng UI BYD* ([Signature]):
 *     - đường dấu vân tay quan sát được ⇒ thấy khung `0x095` quanh mốc tắt ⇒ bật lại; không thấy ⇒ không;
 *     - không quan sát được ([Signature.UNOBSERVABLE], bản 3.1.6) ⇒ cửa sổ thời gian: mốc tắt trong
 *       [windowStartMs]…[windowEndMs] sau lên nguồn ⇒ bật lại; ngoài cửa sổ ⇒ không.
 *  5. MỘT lần mỗi ghế mỗi lần lên nguồn ([Phase.DONE]); lần tắt thứ hai ⇒ không làm gì.
 *  6. Thôi canh ngay khi: Kachi ghi ghế đó ([onKachiWrite], mọi tiến trình) · chức năng kia bật · người dùng đổi mức · màn/xe
 *     tắt (`active = false`) · quá [windowEndMs].
 *
 * Luồng: KHÔNG an toàn đa luồng — bên gọi giữ khoá (app gọi từ một luồng canh duy nhất).
 */
class SeatAutoOffGuard(
    /** Mốc tắt sớm nhất (tính từ lên nguồn) còn được coi là "xe tự tắt" khi không có dấu vân tay. */
    val windowStartMs: Long = WINDOW_START_MS,
    /** Hết hạn canh: sau mốc này không đọc, không bật lại (trần cứng của luồng canh). */
    val windowEndMs: Long = WINDOW_END_MS,
    /** Nhịp đọc trước khi tới gần cửa sổ (chỉ để xác nhận mức đã ăn). */
    val coarsePeriodMs: Long = COARSE_PERIOD_MS,
    /** Nhịp đọc trong cửa sổ (độ trễ bật lại ≈ 1–2 nhịp). */
    val finePeriodMs: Long = FINE_PERIOD_MS,
) {

    /** Dấu vân tay `0x0950000F`/`0x09500022` quanh mốc tắt (§12.3) — app báo cái nó biết. */
    enum class Signature { UNOBSERVABLE, SEEN, NOT_SEEN }

    /** Một lượt đọc của MỘT ghế: mã khung thô (1 = Tắt · 2/3… = bật), `null` = không đọc được. */
    data class Reading(val heat: Int?, val vent: Int?)

    sealed class Verdict {
        /** Không có gì để làm (đang chờ, hoặc ghế không còn được canh). */
        object Keep : Verdict()

        /** Bật lại ĐÚNG một lần: cùng [mode] + [state] như lúc áp. [dropAtMs] = mốc thấy tắt, tính từ lên nguồn. */
        data class Reapply(val seatIndex: Int, val mode: SeatComfort.SeatMode, val state: Int, val dropAtMs: Long, val signature: Signature) : Verdict()

        /** Thôi canh ghế này (lý do để log). */
        data class Disarm(val seatIndex: Int, val reason: String) : Verdict()
    }

    private enum class Phase { ARMED, DONE, OFF }

    private class Slot(
        val mode: SeatComfort.SeatMode,
        val state: Int,
        val poweredAtMs: Long,
        val armedAtMs: Long,
    ) {
        var phase = Phase.ARMED
        var confirmed = false
        var firstOffAtMs: Long? = null
    }

    private val slots = HashMap<Int, Slot>()

    /** Lần lên nguồn mới: quên mọi ghế của lần trước. */
    fun reset() = slots.clear()

    /** `SeatComfort` vừa áp [state] cho [seatIndex] bằng [mode]. Mức Tắt ⇒ không canh (không có gì để bị tắt). */
    fun arm(seatIndex: Int, mode: SeatComfort.SeatMode, state: Int, poweredAtMs: Long, armedAtMs: Long) {
        if (state <= SeatComfort.STATE_OFF) return
        slots[seatIndex] = Slot(mode, state, poweredAtMs, armedAtMs)
    }

    /** Ghế đang được canh (để app biết đọc ghế nào). */
    fun armedSeats(): List<Int> = slots.filterValues { it.phase == Phase.ARMED }.keys.sorted()

    /** Kachi (bất kỳ tiến trình nào) đã ghi ghế này lúc [atMs] ⇒ ý người dùng, thôi canh. Ghi TRƯỚC lúc áp ⇒ bỏ qua. */
    fun onKachiWrite(seatIndex: Int, atMs: Long): Verdict {
        val s = slots[seatIndex]?.takeIf { it.phase == Phase.ARMED } ?: return Verdict.Keep
        if (atMs < s.armedAtMs) return Verdict.Keep
        return disarm(seatIndex, s, "Kachi đã ghi ghế này")
    }

    /**
     * Một lượt đọc của [seatIndex] lúc [nowMs]. [active] = màn sáng + xe bật + vẫn cùng lần thức. [signature] = cái app biết về
     * khung `0x095` quanh lượt đọc này.
     */
    fun observe(seatIndex: Int, nowMs: Long, reading: Reading, active: Boolean, signature: Signature): Verdict {
        val s = slots[seatIndex]?.takeIf { it.phase == Phase.ARMED } ?: return Verdict.Keep
        val since = nowMs - s.poweredAtMs
        if (since > windowEndMs) return disarm(seatIndex, s, "hết hạn canh (+${minutes(since)}′)")
        if (!active) return disarm(seatIndex, s, "màn/xe tắt hoặc lần lên nguồn mới")
        val applied = if (s.mode == SeatComfort.SeatMode.COOL) reading.vent else reading.heat
        val other = if (s.mode == SeatComfort.SeatMode.COOL) reading.heat else reading.vent
        if (other != null && other > SeatComfort.STATE_OFF) return disarm(seatIndex, s, "chức năng kia của ghế đang bật")
        when {
            applied == null -> return Verdict.Keep                                   // không đọc được ⇒ không suy gì
            applied == s.state -> { s.confirmed = true; s.firstOffAtMs = null; return Verdict.Keep }
            applied > SeatComfort.STATE_OFF -> return disarm(seatIndex, s, "mức đổi $applied ≠ ${s.state} (người dùng)")
        }
        // Chức năng đã áp đang TẮT.
        if (!s.confirmed || other == null) return Verdict.Keep                       // chưa từng thấy bật · không biết chức năng kia
        val first = s.firstOffAtMs ?: run { s.firstOffAtMs = nowMs; return Verdict.Keep }   // chống đọc rách: chờ lượt thứ hai
        val dropAt = first - s.poweredAtMs
        val carDidIt = when (signature) {
            Signature.SEEN -> true
            Signature.NOT_SEEN -> return disarm(seatIndex, s, "tắt nhưng không thấy khung 0x095 (người dùng?)")
            Signature.UNOBSERVABLE -> dropAt in windowStartMs..windowEndMs
        }
        if (!carDidIt) return disarm(seatIndex, s, "tắt ngoài cửa sổ +${minutes(windowStartMs)}…+${minutes(windowEndMs)}′ (lúc +${minutes(dropAt)}′)")
        s.phase = Phase.DONE
        return Verdict.Reapply(seatIndex, s.mode, s.state, dropAt, signature)
    }

    /**
     * Ngủ bao lâu tới lượt đọc kế (tính từ [nowMs]); `null` = hết việc (không còn ghế nào được canh, hoặc đã quá
     * [windowEndMs]) ⇒ luồng canh thoát. Trước cửa sổ một nhịp [finePeriodMs] đọc thưa [coarsePeriodMs].
     */
    fun nextDelayMs(nowMs: Long): Long? {
        val armed = slots.values.filter { it.phase == Phase.ARMED }
        if (armed.isEmpty()) return null
        val powered = armed.minOf { it.poweredAtMs }
        val since = nowMs - powered
        if (since >= windowEndMs) return null
        val period = if (since < windowStartMs - finePeriodMs) coarsePeriodMs else finePeriodMs
        return period.coerceAtMost(windowEndMs - since).coerceAtLeast(1L)
    }

    private fun disarm(seatIndex: Int, s: Slot, reason: String): Verdict {
        s.phase = Phase.OFF
        return Verdict.Disarm(seatIndex, reason)
    }

    companion object {
        /** Cửa sổ dự phòng (owner chốt 15/10): mẫu đo +8,5…+10′, nới hai phía. */
        const val WINDOW_START_MS = 6 * 60_000L
        const val WINDOW_END_MS = 15 * 60_000L
        const val COARSE_PERIOD_MS = 30_000L
        const val FINE_PERIOD_MS = 10_000L

        /** Cấp nguồn bật máy (`BYDAutoBodyworkDevice`: 0 OFF · 1 ACC · **2 ON** · **3 OK** · 4 FAKE_OK). */
        private val CAR_ON_LEVELS = setOf(2, 3)

        /** Xe đang bật? `null` (không đọc được) ⇒ không chặn — cổng màn sáng vẫn giữ. ACC/OFF/FAKE_OK ⇒ không. */
        fun carOn(powerLevel: Int?): Boolean = powerLevel == null || powerLevel in CAR_ON_LEVELS

        /** Mã khung ghế từ chuỗi getter: số ngoài 0..15 (sentinel `BYDAutoEventValue`) / không phải số ⇒ `null`. */
        fun raw(s: String?): Int? = s?.trim()?.toIntOrNull()?.takeIf { it in 0..15 }

        /** Mốc lên nguồn: lần màn bật gần nhất nếu hợp lệ (0 ≤ x ≤ now), không thì [nowMs] (lúc áp). */
        fun poweredAt(lastScreenOnMs: Long, nowMs: Long): Long = if (lastScreenOnMs in 0..nowMs) lastScreenOnMs else nowMs

        private fun minutes(ms: Long): String = String.format(java.util.Locale.ROOT, "%.1f", ms / 60_000.0)

        /** Dấu `0x095` đến +3 s sau lần tắt (seat-vent §12.3); hai lượt đọc xác nhận cách 10 s ⇒ nhìn lại 40 s là đủ phủ. */
        const val SIGNATURE_WINDOW_MS = 40_000L

        /** Sự kiện TẮT ghế phải cũ ít nhất ngần này mới kết luận "không thấy dấu" (dấu tới +3 s; chừa 5 s trễ của bus/helper). */
        const val SIGNATURE_LAG_MS = 8_000L

        /**
         * 3.1.6 HAL-EVENTS — app biết gì về dấu vân tay ở lượt đọc lúc [nowMs] (mọi mốc cùng một đồng hồ):
         *  - luồng sự kiện không chạy / id dấu chưa đăng ký OK ([live] `false`) ⇒ [Signature.UNOBSERVABLE] (cửa sổ thời gian, như 3.1.6);
         *  - thấy dấu trong [SIGNATURE_WINDOW_MS] ⇒ [Signature.SEEN];
         *  - không thấy dấu, NHƯNG chính lần tắt ghế đã tới qua luồng sự kiện ([seatOffEventAtMs]) đủ lâu ⇒ [Signature.NOT_SEEN] — luồng
         *    của device Setting chứng minh đang chạy mà dấu không tới ⇒ không phải mẫu "xe tự tắt";
         *  - còn lại ⇒ [Signature.UNOBSERVABLE]: KHÔNG bao giờ kết luận "không thấy" khi chưa chứng minh được là nhìn thấy được.
         */
        fun signatureOf(live: Boolean, nowMs: Long, signatureAtMs: Long?, seatOffEventAtMs: Long?): Signature {
            if (!live) return Signature.UNOBSERVABLE
            if (signatureAtMs != null && nowMs - signatureAtMs in 0..SIGNATURE_WINDOW_MS) return Signature.SEEN
            if (seatOffEventAtMs != null && nowMs - seatOffEventAtMs in SIGNATURE_LAG_MS..SIGNATURE_WINDOW_MS) return Signature.NOT_SEEN
            return Signature.UNOBSERVABLE
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
}
