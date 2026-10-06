package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.modules.clustercast.simplified.BoundedCastExecutor
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.HealPhase

/**
 * ═══ 2.93 · READY-RESTART-MID-CAST — HOÃN lệnh tự force-stop của lượt chữa phím khi chiếu cụm đang dở (thuần, `:core`) ═══════
 *
 * ## Lỗi xe [ĐO log xe 06/10, xe 2.91]
 * 15:17:27.751 `theme 31 cụm=[2] → SEND` → 15:17:29.855 `16` → 15:17:30.725 `KachiReady: keys=STUCK(tat-may)->ESCALATE` →
 * 15:17:31.392 `->RESTARTING` ⇒ tiến trình chết giữa 16 và 35; tiến trình mới 15:17:34.286 `theme 31 … Skip(TOO_SOON, … còn
 * 8468 ms) ⇒ SKIP_KNOWN` ⇒ 16 → 35 ⇒ phiên "chưa rõ kiểu". Lượt chữa phím lớp 1 (tắt máy) và lượt tự chiếu khi tiến trình dựng
 * lại (`BubbleAutostart`, `START_STICKY`) cùng chạy ngay lúc tiến trình bật — trùng nhau là ca thường, không phải ca hiếm.
 *
 * ## Luật
 * Ngay trước nấc leo (đã thấy KẸT BỀN — `A11yLifecycleHeal.healIfStuck`), hỏi [hazard] (sự thật trong tiến trình — chiếu cụm có
 * thao tác đang bay / opcode theme gửi chưa đủ 15 s: `CastRestartHazard`). Không có ⇒ đi tiếp NGAY, không hỏi gì thêm (đường cũ
 * y nguyên). Có ⇒ chờ theo nhịp [POLL_MS], mỗi nhịp hỏi lại pha và mối nguy:
 *  • mối nguy hết ⇒ [Outcome.SETTLED] — leo;
 *  • pha qua (màn bật giữa lượt tắt-máy …) ⇒ [Outcome.PHASE_GONE] — CẮT, đúng nghĩa nhánh chờ đo lại của lượt chữa (lớp khác lo);
 *  • tới [waitUntil] mà vẫn còn ⇒ [Outcome.LIMIT] — leo như bản cũ (không bao giờ bỏ đói lượt chữa phím).
 *
 * ## Trần ([waitUntil]) — lượt chờ KHÔNG BAO GIỜ làm lượt chữa trượt khỏi cửa sổ của nó
 *  • [MAX_DEFER_MS] tuyệt đối (= hạn cứng của một lượt mở chiếu — thao tác dài nhất của executor chiếu).
 *  • CHỈ [HealPhase.TAT_MAY] được chờ (cổng chỉ là "màn còn tắt", không mép thời gian ⇒ tới [MAX_DEFER_MS]) — đúng ca log xe trên.
 *  • [HealPhase.MO_XE] / [HealPhase.KHOI_DONG] / [HealPhase.RUNNING]: KHÔNG chờ (= hành vi cũ). Senior review CAST Pass 1 F1 [P2]:
 *    chờ ở KHOI_DONG đụng lượt giữ 8 s của chuỗi sẵn sàng (`KeyReadyPlan.KEY_HOLD_MAX_MS`) — lượt giữ hết hạn trước ⇒ app của ô
 *    được gắn rồi bị giết ⇒ app ô MỞ HAI LẦN (đúng thứ 2.83 R-A2 đã chặn), mà lượt chờ thường chạm trần nên tiến trình kế vẫn
 *    `TOO_SOON` — được ít, mất nhiều. MO_XE tới đây ở +6…8 s của ân hạn 10 s ⇒ vốn không còn chỗ chờ. Phần mép cửa sổ
 *    ([ESCALATE_RESERVE_MS]) giữ trong [waitUntil] cho pha có mép nếu sau này mở lại.
 *
 * Vì sao RAM/trong tiến trình được phép (CLAUDE.md §5): mối nguy chỉ HOÃN một việc của chính Kachi trong tiến trình này, không
 * quyết đổi gì ngoài hệ thống — cùng lẽ `moXeBusy` của `A11yLifecycleHeal`.
 */
object HealCastDeferral {

    /** Trần tuyệt đối của một lượt chờ — đủ phủ phần còn lại của bất kỳ lượt mở chiếu nào đang bay (nó bắt đầu TRƯỚC lượt chờ). */
    const val MAX_DEFER_MS: Long = BoundedCastExecutor.OPEN_TIMEOUT_MS

    /**
     * Phần mép cửa sổ của pha giữ lại cho nấc leo sau lượt chờ: mở phiên dadb (kênh nền +1,0–1,3 s) + `dumpsys accessibility` ·
     * `dumpsys display` · `am stack list` · `settings get` ([ĐO usage-cycle2 29/09] 0,2–0,5 s/lệnh lúc mở xe) ≈ 2–3,3 s ⇒ 5 s.
     */
    const val ESCALATE_RESERVE_MS: Long = 5_000L

    /** Nhịp hỏi lại — cùng nhịp hỏi pha của lượt chờ đo lại (`A11yLifecycleHeal.PHASE_POLL_MS`). */
    const val POLL_MS: Long = 250L

    enum class Outcome(val fire: Boolean) {
        /** Chiếu yên ngay lần hỏi đầu — không chờ, không hỏi pha (đường cũ). */
        CLEAR(true),

        /** Đã chờ, mối nguy hết. */
        SETTLED(true),

        /** Tới trần ([waitUntil]) mà mối nguy còn — leo như bản cũ. */
        LIMIT(true),

        /** Pha qua trong lúc chờ — CẮT (lớp khác / nút lo). */
        PHASE_GONE(false),

        /** Luồng bị ngắt (tiến trình đóng) — CẮT, giữ cờ ngắt. */
        INTERRUPTED(false),
    }

    /** [hazard] = nhãn mối nguy THẤY ĐẦU TIÊN (`null` khi [Outcome.CLEAR]); [waitedMs] = thời gian đã chờ. */
    data class Result(val outcome: Outcome, val hazard: String?, val waitedMs: Long)

    /**
     * Mốc `elapsedRealtime` muộn nhất được phép chờ (xem KDoc lớp); không bao giờ trước [now]. [anchorAt] = mốc neo của pha (như
     * cổng cuối). Mép cửa sổ lấy từ [AccessibilityHealGates.fireWindowEnd] — một nguồn số với cổng cuối.
     */
    fun waitUntil(phase: HealPhase, anchorAt: Long, now: Long): Long {
        if (phase != HealPhase.TAT_MAY) return now     // xem KDoc lớp — chỉ lượt tắt-máy được chờ (review CAST F1)
        val cap = now + MAX_DEFER_MS
        val windowEnd = AccessibilityHealGates.fireWindowEnd(phase, anchorAt) ?: return cap
        return minOf(cap, windowEnd - ESCALATE_RESERVE_MS).coerceAtLeast(now)
    }

    /** Số lần tối đa cổng cuối (lượt tắt-máy) được TỪ CHỐI bắn vì mối nguy mới — sau đó bắn như [Outcome.LIMIT] (D3: không bỏ đói). */
    const val MAX_GATE_RECHECKS: Int = 2

    /**
     * 2.93 wave 2A · HEAL-DEFER-GATE-RECHECK (spec `kachi-293-wave2a.html` §4.5; OQ5 spec `kachi-293-cast.html`) — lượt chờ +
     * nấc leo, cổng cuối HỎI LẠI mối nguy (chỉ [HealPhase.TAT_MAY]).
     *
     * D1 của spec 293-cast đặt lượt chờ TRƯỚC nấc leo (không giữ phiên dadb nằm im, các bản đọc dựng lệnh tách rời luôn tươi) ⇒ một
     * thao tác chiếu BẮT ĐẦU trong ~1–2,5 s đọc của nấc leo (vd `BubbleAutostart` thử lại đúng lúc khoảng 15 s hết — cùng mốc lượt
     * chờ vừa xong) vẫn bị lệnh tách rời giết. Nay [fire] nhận cổng cuối = pha còn ∧ (mối nguy vắng ∨ đã tới [until] ∨ đã từ chối
     * [MAX_GATE_RECHECKS] lần). Cổng từ chối vì mối nguy ⇒ nấc leo không bắn (0 marker, 0 lệnh ghi hệ thống — `escalateIfStuck`
     * hỏi cổng TRƯỚC mọi thứ đổi trạng thái) ⇒ chờ LẠI rồi leo LẠI với phiên + bản đọc MỚI (giữ D1). Một trần [until] cho cả lượt.
     * Pha khác ⇒ đúng đường cũ từng bước: không chờ, cổng = [inPhase], không hỏi mối nguy.
     *
     * @return kết quả nấc leo cuối; `null` = CẮT (pha qua / luồng bị ngắt trong lúc chờ) — như nhánh chờ đo lại.
     */
    fun <R> escalate(
        phase: HealPhase,
        until: Long,
        hazard: () -> String?,
        inPhase: () -> Boolean,
        nowMs: () -> Long,
        sleepMs: (Long) -> Unit,
        onWait: (Result) -> Unit,
        onRecheck: (hazard: String, n: Int) -> Unit,
        fire: (gate: () -> Boolean) -> R,
    ): R? {
        if (phase != HealPhase.TAT_MAY) return fire(inPhase)
        var rechecks = 0
        while (true) {
            val waited = await(hazard, inPhase, nowMs, sleepMs, until)
            onWait(waited)
            if (!waited.outcome.fire) return null
            var refused: String? = null
            val r = fire {
                inPhase() && (rechecks >= MAX_GATE_RECHECKS || nowMs() >= until || hazard().also { refused = it } == null)
            }
            val h = refused ?: return r
            rechecks++
            onRecheck(h, rechecks)
        }
    }

    /**
     * Vòng chờ (chặn — chỉ gọi trên luồng nền của lượt chữa). [hazard] trả nhãn mối nguy hoặc `null`; [inPhase] = cổng pha
     * của lượt chữa; [nowMs] / [sleepMs] tiêm được (test chạy đồng hồ giả). Thứ tự mỗi nhịp: pha → trần → ngủ → mối nguy.
     */
    fun await(
        hazard: () -> String?,
        inPhase: () -> Boolean,
        nowMs: () -> Long,
        sleepMs: (Long) -> Unit,
        until: Long,
        pollMs: Long = POLL_MS,
    ): Result {
        val start = nowMs()
        val first = hazard() ?: return Result(Outcome.CLEAR, null, 0L)
        while (true) {
            val t = nowMs()
            if (!inPhase()) return Result(Outcome.PHASE_GONE, first, t - start)
            if (t >= until) return Result(Outcome.LIMIT, first, t - start)
            try {
                sleepMs(minOf(pollMs, until - t))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return Result(Outcome.INTERRUPTED, first, nowMs() - start)
            }
            if (hazard() == null) return Result(Outcome.SETTLED, first, nowMs() - start)
        }
    }
}
