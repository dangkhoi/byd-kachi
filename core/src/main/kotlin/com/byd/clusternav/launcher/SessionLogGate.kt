package com.byd.clusternav.launcher

/**
 * ═══ CỔNG GHI TỆP `usage-*.log` — dòng logcat nào thật sự xuống thẻ (LOG-VOLUME 3.1.10) ═══════════════════════════
 *
 * Thay [LogLineThrottle] ở **đúng một chỗ**: vòng chụp logcat của `KachiLog.startCapture`. [LogLineThrottle] vẫn là
 * công cụ tiết chế TẠI NGUỒN cho từng chỗ gọi (`AdaptiveInk`, `Car3dDotsLayer`, `TyreRawLog`…), không đổi.
 *
 * ## Bệnh [ĐO xe 15–16/10, diagnostics `log-volume-2026-10-16.md`]
 * Lúc đang dẫn đường + bật camera xi-nhan, tệp usage lên **98–120 KB/phút** (trung bình chuyến 36–55 KB/phút)
 * dù đã có bộ tiết chế trùng-nguyên-văn 10 s. Ba nguồn cộng lại ≈ 70 % số byte còn lại:
 *  1. **Số đo đổi liên tục** — cự ly dẫn đường (`seg=120 → 110 → 100…`), tốc độ (`speed is 47.5`), `intValue is 120`
 *     của thư viện BYD, `µs/khung` của đợt vẽ: mỗi dòng là một chuỗi MỚI ⇒ bộ tiết chế nguyên văn không chặn được
 *     dòng nào, ~1–2 dòng/s/loại.
 *  2. **Nhịp tim 10 s** của mọi câu chữ lặp y nguyên (thư viện HAL đọc 1 Hz): sàn ≈ 6 dòng/phút × vài chục câu.
 *  3. **Dòng E lặp y nguyên** của framework (`E/FrameEvents: updateAcquireFence: Did not find frame.`, ~4 KB/phút lúc
 *     camera) — luật cũ cho W/E đi thẳng, kể cả bản sao thứ 500.
 *
 * ## Luật — theo MỨC log, không theo tag/tên gói (không có danh sách ngoại lệ nào)
 *  • **F/A**: luôn ghi.
 *  • **I/W/E**: tiết chế **nguyên văn** như cũ ([repeatMs] = 10 s, hậu tố `[+N lặp]`). Đây là mức của mọi dòng bằng
 *    chứng Kachi tự ghi (`KachiCtl` ctl/ctl-after, `KachiHalEvent`, `KachiHalSignal`, `KachiKey`, WAKE…) ⇒ hành vi
 *    của chúng **không đổi**: mỗi chuỗi khác nhau đi qua ngay. W/E nay cũng vào cổng này: bản sao Y NGUYÊN trong
 *    10 s thành một số đếm trên dòng ghi kế, dòng đầu tiên vẫn ghi ngay và vẫn xả ngay (`KachiLog.mustFlushNow`).
 *  • **D/V** (mức "gỡ lỗi theo nhịp"): khoá theo **khuôn** ([template]: mọi số đứng riêng → `#`), mỗi khuôn nhớ
 *    dòng ghi cuối:
 *     – y hệt dòng ghi cuối ⇒ bỏ trong [debugRepeatMs] (60 s), rồi ghi lại kèm `[+N lặp]`;
 *     – chỉ khác ở **số đo** ([measure]: số có phần thập phân, hoặc số nguyên 2–7 chữ số) ⇒ bỏ trong [measureMs]
 *       (10 s) kể từ lần ghi cuối của khuôn, rồi ghi giá trị HIỆN TẠI kèm `[trước đó: +M tương tự]`;
 *     – khác ở bất cứ chỗ nào khác — số **một chữ số** (trạng thái: `is 3` → `is 1`), số **≥ 8 chữ số** (id), chữ
 *       (`false` → `true`), tên đường — ⇒ **ghi ngay**. Một trạng thái đổi rồi đổi VỀ (A → B → A) cũng ghi ngay,
 *       vì so với dòng ghi cuối của khuôn chứ không so với "đã thấy chuỗi này chưa" — lỗ "quay về giá trị cũ bị
 *       nuốt" của bộ tiết chế nguyên văn không có ở mức D;
 *     – NGOẠI LỆ duy nhất: khuôn đang là **vòng poll nhiều danh tính** (≥ [CYCLE_MIN] thân khác nhau ghi trong 10 s,
 *       vd `getDoorState area is: 1..6`) ⇒ thân đã ghi trong 10 s bị gộp — đúng bằng luật nguyên văn 10 s của 3.1.9,
 *       không tệ hơn (A ↔ B hai thân vẫn coi là lật trạng thái, vẫn ghi).
 *
 * ## Vết gọi / bản ghi nhiều dòng không bao giờ bị cắt giữa
 * [ĐO tệp usage 09-17 → 10-06] `logcat -v time` in **đầu dòng đầy đủ trên MỌI dòng** của một bản ghi nhiều dòng
 * (`I/KachiVoiceGeo(16335): \tat android.location…`) — không có "dòng phụ không mốc" như KDoc cũ của
 * `KachiLog.throttled` giả định. Nên: dòng có **cùng đầu dòng** (mốc ms + mức + tag + pid) với dòng ngay trước, và
 * (dòng trước là W/E/F/A, hoặc thân bắt đầu bằng `\t` / `Caused by:`) là **dòng tiếp** ⇒ luôn ghi; nếu những dòng
 * trước nó trong cùng bản ghi vừa bị bỏ thì chúng được **thả ra trước** (tối đa [MAX_HELD]), số đếm hoàn lại.
 *
 * ## Chi phí (đường nóng)
 * O(độ dài dòng) mỗi dòng như bản cũ (bản cũ đã `substring`): thêm 1 lượt quét ký tự dựng khuôn cho dòng D/V, 1 lượt
 * nữa chỉ khi thân khác dòng ghi cuối. Không luồng, không hẹn giờ, hai bảng LRU có trần [maxKeys]. Gọi từ MỘT luồng
 * chụp mỗi tiến trình; `@Synchronized` chỉ để an toàn nếu có chỗ gọi thứ hai.
 *
 * THUẦN (không Android, đồng hồ truyền vào) ⇒ `SessionLogGateTest` + `SessionLogGateReplayTest` khoá off-device.
 */
class SessionLogGate(
    private val repeatMs: Long = REPEAT_MS,
    private val debugRepeatMs: Long = DEBUG_REPEAT_MS,
    private val measureMs: Long = MEASURE_MS,
    private val maxKeys: Int = MAX_KEYS,
) {

    /** Mức I/W/E — khoá nguyên văn. */
    private class Exact(var writtenAtMs: Long, var suppressed: Int)

    /** Mức D/V — khoá khuôn; nhớ dòng ghi cuối của khuôn và hai số đếm từ lần ghi đó. */
    private class Tpl(var lastBody: String, var lastMeasure: String?, var writtenAtMs: Long) {
        var repeats = 0
        var similar = 0

        /** Vòng [RING] thân đã ghi gần nhất của khuôn + mốc ghi — nhận ra nhịp poll nhiều danh tính (xem [cycling]). */
        private val ringBody = arrayOfNulls<String>(RING)
        private val ringAt = LongArray(RING)

        init { remember(lastBody, writtenAtMs) }

        fun remember(body: String, nowMs: Long) {
            var oldest = 0
            for (k in 0 until RING) {
                if (ringBody[k] == body) { ringAt[k] = nowMs; return }
                if (ringBody[k] == null) { oldest = k; break }
                if (ringAt[k] < ringAt[oldest]) oldest = k
            }
            ringBody[oldest] = body; ringAt[oldest] = nowMs
        }

        /**
         * Thân này đã ghi trong [windowMs] **và** khuôn đang có ≥ [CYCLE_MIN] thân khác nhau ghi trong [windowMs] ⇒ đây
         * là một vòng poll nhiều danh tính (cửa 1..6, lốp 1..4 — mỗi lượt đọc một dòng cùng khuôn), không phải đổi
         * trạng thái. Hai thân luân phiên (A ↔ B) KHÔNG đủ: đó có thể là trạng thái lật thật ⇒ vẫn ghi.
         * Tệ nhất = đúng bộ tiết chế nguyên văn 10 s của 3.1.9, không bao giờ tệ hơn.
         */
        fun cycling(body: String, nowMs: Long, windowMs: Long): Boolean {
            var hit = false
            var recent = 0
            for (k in 0 until RING) {
                val b = ringBody[k] ?: continue
                val age = nowMs - ringAt[k]
                if (age in 0 until windowMs) { recent++; if (b == body) hit = true }
            }
            return hit && recent >= CYCLE_MIN
        }
    }

    private fun <V> lru(): LinkedHashMap<String, V> = object : LinkedHashMap<String, V>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > maxKeys
    }

    private val exact = lru<Exact>()
    private val templates = lru<Tpl>()

    /** Đầu dòng + mức của dòng nguồn ngay trước (nhận dòng tiếp). */
    private var prevHeader: String? = null
    private var prevSeverity = ' '

    /** Dòng vừa bị bỏ của bản ghi đang mở (cùng đầu dòng) — thả ra nếu bản ghi hoá ra có dòng tiếp. */
    private val held = ArrayList<Held>(MAX_HELD)

    /** Một dòng đang giữ + số đếm nó đã cộng (để hoàn lại khi thả): [UNDO_EXACT] · [UNDO_REPEAT] · [UNDO_SIMILAR]. */
    private class Held(val line: String, val exact: Exact?, val tpl: Tpl?, val undo: Int)

    /**
     * Dòng (hoặc nhiều dòng nối bằng `\n` khi thả dòng đang giữ) cần ghi xuống tệp, hoặc `null` = bỏ.
     * Dòng không phải đầu bản ghi `logcat -v time` (dòng mốc `--------- beginning of main`, dòng hỏng) đi thẳng.
     */
    @Synchronized
    fun offer(line: String, nowMs: Long): String? {
        if (!isRecordHead(line)) {
            prevHeader = null; held.clear()
            return line
        }
        val sev = line[SEVERITY_COL]
        val headerEnd = line.indexOf("): ", SEVERITY_COL)
        val header = if (headerEnd < 0) null else line.substring(0, headerEnd)
        val continuation = header != null && header == prevHeader &&
            (prevSeverity in ALWAYS_COHESIVE || line.startsWith("\t", headerEnd + 3) || line.startsWith("Caused by:", headerEnd + 3))
        if (header == null || header != prevHeader) held.clear()
        prevHeader = header
        prevSeverity = sev

        if (continuation) return release(line)
        if (sev == 'F' || sev == 'A') { held.clear(); return line }

        val body = line.substring(SEVERITY_COL)
        val out = if (sev == 'D' || sev == 'V') debug(line, body, nowMs) else exactGate(line, body, nowMs)
        if (out != null) held.clear()
        return out
    }

    private fun release(line: String): String {
        if (held.isEmpty()) return line
        val sb = StringBuilder()
        for (h in held) {
            when (h.undo) {
                UNDO_EXACT -> h.exact?.let { it.suppressed-- }
                UNDO_REPEAT -> h.tpl?.let { it.repeats-- }
                else -> h.tpl?.let { it.similar-- }
            }
            sb.append(h.line).append('\n')
        }
        held.clear()
        return sb.append(line).toString()
    }

    private fun hold(h: Held) {
        if (held.size < MAX_HELD) held.add(h)
    }

    private fun expired(writtenAtMs: Long, nowMs: Long, windowMs: Long): Boolean {
        val elapsed = nowMs - writtenAtMs
        // Đồng hồ LÙI (đầu xe chỉnh giờ giữa phiên) ⇒ coi như hết cửa sổ: một cú nhảy giờ không bao giờ khoá dòng mãi.
        return elapsed < 0 || elapsed >= windowMs
    }

    private fun exactGate(line: String, body: String, nowMs: Long): String? {
        val slot = exact[body]
        if (slot == null) { exact[body] = Exact(nowMs, 0); return line }
        if (!expired(slot.writtenAtMs, nowMs, repeatMs)) {
            slot.suppressed++
            hold(Held(line, slot, null, UNDO_EXACT))
            return null
        }
        val n = slot.suppressed
        slot.writtenAtMs = nowMs; slot.suppressed = 0
        return line + LogLineThrottle.repeatSuffix(n)
    }

    private fun debug(line: String, body: String, nowMs: Long): String? {
        val key = template(body)
        val slot = templates[key]
        if (slot == null) { templates[key] = Tpl(body, null, nowMs); return line }
        if (body == slot.lastBody) {
            if (!expired(slot.writtenAtMs, nowMs, debugRepeatMs)) {
                slot.repeats++
                hold(Held(line, null, slot, UNDO_REPEAT))
                return null
            }
            val suffix = debugSuffix(slot.repeats, slot.similar, sameBody = true)
            slot.writtenAtMs = nowMs; slot.repeats = 0; slot.similar = 0
            slot.remember(body, nowMs)
            return line + suffix
        }
        val lastMeasure = slot.lastMeasure ?: measure(slot.lastBody).also { slot.lastMeasure = it }
        val nowMeasure = measure(body)
        if (nowMeasure == lastMeasure && !expired(slot.writtenAtMs, nowMs, measureMs)) {
            slot.similar++
            hold(Held(line, null, slot, UNDO_SIMILAR))
            return null
        }
        if (slot.cycling(body, nowMs, repeatMs)) {
            slot.repeats++
            hold(Held(line, null, slot, UNDO_REPEAT))
            return null
        }
        val suffix = debugSuffix(slot.repeats, slot.similar, sameBody = false)
        slot.lastBody = body; slot.lastMeasure = nowMeasure
        slot.writtenAtMs = nowMs; slot.repeats = 0; slot.similar = 0
        slot.remember(body, nowMs)
        return line + suffix
    }

    companion object {
        /** I/W/E: cửa sổ trùng nguyên văn — giữ đúng [LogLineThrottle.WINDOW_MS] (hành vi dòng bằng chứng không đổi). */
        const val REPEAT_MS = LogLineThrottle.WINDOW_MS

        /**
         * D/V: dòng y hệt dòng ghi cuối của khuôn hiện lại sau tối đa 60 s. Không mất dữ kiện: đổi giá trị đi qua
         * ngay, số lần lặp nằm trên dòng ghi kế; nhịp "còn sống" đã có `KachiPerf` mỗi 60 s.
         */
        const val DEBUG_REPEAT_MS = 60_000L

        /** D/V: số đo (cự ly, tốc độ, µs…) của cùng một khuôn ghi tối đa một lần mỗi 10 s — giá trị hiện tại. */
        const val MEASURE_MS = 10_000L

        /** Trần mỗi bảng LRU — như [LogLineThrottle.MAX_KEYS]. */
        const val MAX_KEYS = LogLineThrottle.MAX_KEYS

        /** Cỡ vòng thân-đã-ghi mỗi khuôn D/V (≥ số danh tính của vòng poll dài nhất đo được: cửa 6, đèn 4, lốp 4). */
        const val RING = 8

        /** Số thân khác nhau tối thiểu trong cửa sổ để coi là vòng poll nhiều danh tính (2 = có thể là lật trạng thái). */
        const val CYCLE_MIN = 3

        /** Số dòng vừa bỏ tối đa được giữ lại để thả ra khi bản ghi hoá ra nhiều dòng. */
        const val MAX_HELD = 8

        /** Cột ký tự mức trong `logcat -v time` (`09-16 09:01:53.708 W/Tag(pid): …`). */
        const val SEVERITY_COL = 19

        private const val UNDO_EXACT = 0
        private const val UNDO_REPEAT = 1
        private const val UNDO_SIMILAR = 2

        private const val SEVERITIES = "VDIWEFA"

        /** Mức mà mọi dòng cùng đầu dòng theo sau đều thuộc cùng một bản ghi (vết gọi của `Log.w(msg, e)`…). */
        private const val ALWAYS_COHESIVE = "WEFA"

        /** Đầu bản ghi `logcat -v time`: ký tự mức ở [SEVERITY_COL] và ngay sau là `/`. */
        fun isRecordHead(line: String): Boolean =
            line.length > SEVERITY_COL + 1 && SEVERITIES.indexOf(line[SEVERITY_COL]) >= 0 && line[SEVERITY_COL + 1] == '/'

        /** Khuôn: MỌI số thập phân đứng riêng (không dính chữ/số) → `#`; token chữ-số (`43f01018`, `0x095…`) giữ. */
        fun template(s: String): String = mask(s, measuresOnly = false)

        /**
         * Phần "không phải số đo": chỉ số có phần thập phân hoặc số nguyên 2–7 chữ số → `#`. Số một chữ số (trạng
         * thái) và ≥ 8 chữ số (id) giữ nguyên ⇒ hai dòng cùng [measure] chỉ khác nhau ở số đo.
         */
        fun measure(s: String): String = mask(s, measuresOnly = true)

        private fun mask(s: String, measuresOnly: Boolean): String {
            val n = s.length
            val sb = StringBuilder(n)
            var i = 0
            while (i < n) {
                val c = s[i]
                if (c in '0'..'9' && (i == 0 || !s[i - 1].isLetterOrDigit())) {
                    var j = i
                    while (j < n && s[j] in '0'..'9') j++
                    val intDigits = j - i
                    var decimal = false
                    if (j + 1 < n && (s[j] == '.' || s[j] == ',') && s[j + 1] in '0'..'9') {
                        decimal = true
                        j++
                        while (j < n && s[j] in '0'..'9') j++
                    }
                    if (j < n && s[j].isLetterOrDigit()) {
                        // Token chữ-số (id hex, `5981KB`, `200ms`): giữ nguyên cả token — an toàn: khác ⇒ ghi.
                        while (j < n && s[j].isLetterOrDigit()) j++
                        sb.append(s, i, j)
                    } else if (!measuresOnly || decimal || intDigits in 2..7) {
                        sb.append('#')
                    } else {
                        sb.append(s, i, j)
                    }
                    i = j
                    continue
                }
                sb.append(c)
                i++
            }
            return sb.toString()
        }

        /** Hậu tố D/V: cùng thân ⇒ `[+N lặp · +M tương tự]`; thân mới ⇒ các số đếm thuộc dòng TRƯỚC: `[trước đó: …]`. */
        fun debugSuffix(repeats: Int, similar: Int, sameBody: Boolean): String {
            if (repeats <= 0 && similar <= 0) return ""
            val parts = buildString {
                if (repeats > 0) append("+").append(repeats).append(" lặp")
                if (similar > 0) { if (repeats > 0) append(" · "); append("+").append(similar).append(" tương tự") }
            }
            return if (sameBody) "   [$parts]" else "   [trước đó: $parts]"
        }
    }
}
