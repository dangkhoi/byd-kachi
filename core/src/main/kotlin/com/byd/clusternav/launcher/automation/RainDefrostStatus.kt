package com.byd.clusternav.launcher.automation

/**
 * Kết quả nhịp gần nhất của MỘT kính (kachi-automation V8.1 · R-V8.7).
 *
 * @property atWallMs đồng hồ TƯỜNG lúc nhịp chạy — chỉ để hiện HH:mm (nhịp đi theo đồng hồ đơn điệu, OQ-V8.7).
 * @property offAfterOnAtWallMs giờ của lệnh BẬT ở nhịp NGAY TRƯỚC của chính kính này — lệnh đó đã tới xe, vậy mà nhịp này
 *   xe vẫn báo tắt (đang mưa, Kachi vẫn giữ kính); `null` = không phải ca đó. Xem [RainGlassLast.follow].
 */
data class RainGlassLast(
    val atWallMs: Long,
    val outcome: RainGlassOutcome,
    val offAfterOnAtWallMs: Long? = null,
) {
    companion object {
        /**
         * Kết quả mới của một kính, biết kết quả NGAY TRƯỚC của CHÍNH kính đó (soát V8.1 Pass 8 · D17).
         *
         * Ca cần lộ ra: lệnh bật rc hợp lệ (✓ "tới xe") nhưng xe không làm theo — đúng dấu hiệu OC4 / OQ-V8.1
         * (*"ghi=ok mà nhịp sau đọc=tắt"*). Chỉ nhìn một nhịp thì mỗi lượt đều là *"xe báo: tắt → Kachi bật: tới xe ✓"*
         * xanh, và một ảnh chụp không phân biệt được lần bật đầu với lần thứ mười. Đánh dấu khi CẢ BA đúng:
         *  1. nhịp trước của kính này là `TurnOn` và lệnh tới xe (`writeOk == true`);
         *  2. nhịp này lại `TurnOn` — tức đang mưa và xe báo tắt;
         *  3. `before.owned` — Kachi vẫn giữ kính từ lệnh đó: không bị quên giữa chừng (đổi ô, động cơ dừng — khoảng
         *     trống có thể dài), không qua nhịp khô.
         *
         * Chỉ nói điều ĐO được (hai lần đọc + một rc), không quy kết: xe không làm theo · tự tắt ngay · hay có người vừa
         * tắt — ba ca cùng một dấu hiệu. Xe tự tắt sau ~14′ ([ĐO log 22/09]) KHÔNG bị đánh dấu: giữa hai lệnh có nhịp
         * *"xe báo: bật"* nên nhịp trước không còn là `TurnOn`.
         */
        fun follow(prev: RainGlassLast?, atWallMs: Long, outcome: RainGlassOutcome): RainGlassLast {
            val prevOnReached = prev != null &&
                prev.outcome.plan.action == RainDefrostAction.TurnOn && prev.outcome.writeOk == true
            val stillOff = prevOnReached && outcome.plan.action == RainDefrostAction.TurnOn && outcome.plan.before.owned
            return RainGlassLast(atWallMs, outcome, if (stillOff) prev?.atWallMs else null)
        }
    }
}

/**
 * Ký ức *"kết quả nhịp gần nhất từng kính"* — nguồn của dòng tình trạng trong Cài đặt (spec V8.1 · D10/D12).
 *
 * RAM, **không lưu bền**: kết quả của chuyến trước hiện lên như hiện tại là nói sai, và [ĐO 29/09] BYD giết tiến trình
 * Kachi mỗi lần tắt máy ⇒ "chưa có kết quả" đúng nghĩa *"chưa kiểm lần nào từ lúc mở xe"*. Luồng nền ghi, luồng vẽ
 * đọc (cùng tiến trình — [ĐO manifest]) ⇒ khoá ngắn trên map, không giữ qua HAL.
 *
 * Theo KÍNH, không một báo cáo chung (D12): tích thêm kính sau thì dòng kính sau nói "chưa kiểm" dù kính trước đã có
 * kết quả — kết quả của kính này không bao giờ được mượn cho kính kia.
 */
class RainGlassLastResults {
    private val lock = Any()
    private val last = HashMap<RainGlass, RainGlassLast>()

    /**
     * Ghi kết quả một nhịp — mỗi làn vào ô của CHÍNH kính đó, so với kết quả trước của CHÍNH kính đó
     * ([RainGlassLast.follow]). Nhịp rỗng (không chọn gì / lỗi) không xoá gì.
     */
    fun record(atWallMs: Long, outcomes: List<RainGlassOutcome>) {
        synchronized(lock) {
            outcomes.forEach { last[it.plan.glass] = RainGlassLast.follow(last[it.plan.glass], atWallMs, it) }
        }
    }

    fun of(glass: RainGlass): RainGlassLast? = synchronized(lock) { last[glass] }
}

/**
 * Màu chấm của dòng: xanh = đang đúng việc · vàng = chưa đọc được / chờ / lệnh bật trước tới xe mà xe vẫn báo tắt ·
 * đỏ = lệnh không tới xe · xám = không có việc.
 */
enum class RainStatusTone { OK, WAIT, FAIL, IDLE }

/**
 * Các cụm chữ của dòng tình trạng — `:app` dựng từ tài nguyên (`kachi_rain_st_*`, vi/en), `:core` chỉ CHỌN và GHÉP
 * (spec V8.1 · D11: tầng `launcher/` bắt mọi chữ qua tài nguyên, còn `:core` không có tài nguyên).
 *
 * Chỗ điền: [rain]/[dry] có [RainDefrostStatus.SPEED] (mức gạt), [next]/[carOffAfterOn] có [RainDefrostStatus.TIME] (HH:mm).
 */
data class RainStatusWords(
    val front: String,
    val rear: String,
    val never: String,
    val next: String,
    val rain: String,
    val dry: String,
    val sensorError: String,
    val carOn: String,
    val carOff: String,
    /** Thay [carOff] khi [RainGlassLast.offAfterOnAtWallMs] có giờ — lệnh bật lúc đó đã tới xe mà xe vẫn báo tắt. */
    val carOffAfterOn: String,
    val carError: String,
    val turnOnOk: String,
    val turnOnFail: String,
    val turnOffOk: String,
    val turnOffFail: String,
    val skipped: String,
    val keepOn: String,
    val nothingToDo: String,
    val notOurs: String,
)

/** Một dòng tình trạng của MỘT kính đang chọn. */
data class RainStatusLine(val glass: RainGlass, val text: String, val tone: RainStatusTone)

/**
 * ═══ kachi-automation V8.1 · DÒNG TÌNH TRẠNG từng kính (R-V8.7) ═══════════════════════════════════════════════
 *
 * Owner 30/09: lỗi *"chỉ chọn sau + gương → không work"* có trên 2.83, không biết xe đời nào, không có thời gian test ⇒
 * app phải TỰ cho thấy nguyên nhân bằng **một ảnh chụp màn hình** (CLAUDE.md §11). Một dòng trả lời đủ ba câu tách
 * được "Kachi không chạy" / "không đọc được xe" / "xe không nhận lệnh":
 *  1. cảm biến mưa đọc được không, ra mức mấy;
 *  2. xe báo kính đang bật/tắt, hay không đọc được;
 *  3. Kachi làm gì, lệnh có tới xe không (rc hợp lệ ≠ xe đã bật — sự thật là lần đọc của nhịp sau, spec V8.0; nên
 *     lần đọc ấy được nói ngay trên dòng khi nó trái lệnh bật trước: *"xe báo: vẫn tắt sau lệnh bật 14:02"*, D17).
 *
 * Không in số rc (D15): `CarControlPort.toggle` chỉ trả `Boolean`; lộ rc là đổi đường ghi (R-V8.nf1 cấm). Chi tiết ở
 * dòng log `#seq … ghi=hỏng`.
 */
object RainDefrostStatus {

    /** Chỗ điền mức gạt trong [RainStatusWords.rain]/[RainStatusWords.dry]. */
    const val SPEED = "{n}"

    /** Chỗ điền giờ trong [RainStatusWords.next]. */
    const val TIME = "{t}"

    /** Ngăn giữa các vế — ký hiệu, không phải chữ ⇒ không qua tài nguyên. */
    const val SEP = " · "

    /** Ngăn giữa *"xe báo"* và *"Kachi làm gì"*. */
    const val ARROW = " → "

    /**
     * Một dòng cho MỖI kính đang chọn, theo thứ tự [RainGlass]; không chọn gì ⇒ rỗng.
     *
     * @param lastOf kết quả gần nhất của đúng kính được hỏi ([RainGlassLastResults.of]).
     * @param nextAtWallMs giờ (đồng hồ tường) của nhịp kế nếu biết — chỉ hiện ở dòng *"chưa kiểm"*.
     * @param clock đồng hồ tường → `"HH:mm"` (theo ngôn ngữ app, do `:app` cấp).
     */
    fun lines(
        choice: RainDefrostChoice,
        lastOf: (RainGlass) -> RainGlassLast?,
        nextAtWallMs: Long?,
        words: RainStatusWords,
        clock: (Long) -> String,
    ): List<RainStatusLine> = choice.glasses.map { line(it, lastOf(it), nextAtWallMs, words, clock) }

    /** Dòng của MỘT kính — chỉ nhìn [last] của chính nó. */
    fun line(
        glass: RainGlass,
        last: RainGlassLast?,
        nextAtWallMs: Long?,
        words: RainStatusWords,
        clock: (Long) -> String,
    ): RainStatusLine {
        val name = when (glass) {
            RainGlass.FRONT -> words.front
            RainGlass.REAR -> words.rear
        }
        if (last == null) {
            val next = nextAtWallMs?.let { words.next.replace(TIME, clock(it)) }
            return RainStatusLine(glass, listOfNotNull(name, words.never, next).joinToString(SEP), RainStatusTone.IDLE)
        }
        val plan = last.outcome.plan
        val head = name + SEP + clock(last.atWallMs)
        val speed = plan.rainSpeed
            ?: return RainStatusLine(glass, head + SEP + words.sensorError, RainStatusTone.WAIT)
        val raining = RainDefrostPolicy.isRaining(speed)
        val rain = (if (raining) words.rain else words.dry).replace(SPEED, speed.toString())
        val read = plan.read
            ?: return RainStatusLine(glass, head + SEP + rain + SEP + words.carError, RainStatusTone.WAIT)
        val offAfterOn = last.offAfterOnAtWallMs
        val car = when {
            read -> words.carOn
            offAfterOn != null -> words.carOffAfterOn.replace(TIME, clock(offAfterOn))
            else -> words.carOff
        }
        val (verdict, sentTone) = verdict(plan.action, raining, read, last.outcome.writeOk, words)
        // Lệnh bật trước tới xe mà xe vẫn báo tắt ⇒ không được xanh dù lệnh lần này cũng tới xe (D17); đỏ giữ đỏ.
        val tone = if (offAfterOn != null && sentTone == RainStatusTone.OK) RainStatusTone.WAIT else sentTone
        return RainStatusLine(glass, head + SEP + rain + SEP + car + ARROW + verdict, tone)
    }

    private fun verdict(
        action: RainDefrostAction,
        raining: Boolean,
        read: Boolean,
        writeOk: Boolean?,
        w: RainStatusWords,
    ): Pair<String, RainStatusTone> = when (action) {
        RainDefrostAction.TurnOn -> sent(writeOk, w.turnOnOk, w.turnOnFail, w.skipped)
        RainDefrostAction.TurnOff -> sent(writeOk, w.turnOffOk, w.turnOffFail, w.skipped)
        // Mưa + kính tắt không bao giờ ra Leave (luật V3 — `RainDefrostOwner.step` luôn TurnOn) ⇒ ba ca còn lại.
        RainDefrostAction.Leave -> when {
            read && raining -> w.keepOn to RainStatusTone.OK
            // Khô + bật + `owned = false`: chỉ BIẾT "Kachi không quản" — không biết ai bật (người lái, hay Kachi rồi
            // lệnh tắt hỏng / bỏ-tích-lại ô đã nhả chủ quyền). Chữ không được khẳng định nguồn gốc (soát V8.1 Pass 7).
            read -> w.notOurs to RainStatusTone.IDLE
            else -> w.nothingToDo to RainStatusTone.IDLE
        }
    }

    /** Lệnh đã phát: rc hợp lệ ⇒ tới xe · rc hỏng ⇒ không tới xe · không phát (vừa đổi lựa chọn giữa nhịp) ⇒ bỏ. */
    private fun sent(writeOk: Boolean?, ok: String, fail: String, skipped: String): Pair<String, RainStatusTone> =
        when (writeOk) {
            true -> ok to RainStatusTone.OK
            false -> fail to RainStatusTone.FAIL
            null -> skipped to RainStatusTone.WAIT
        }
}
