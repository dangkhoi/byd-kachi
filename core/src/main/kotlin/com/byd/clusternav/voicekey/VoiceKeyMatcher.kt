package com.byd.clusternav.voicekey

/**
 * LOGIC THUẦN (không Android): một sự kiện phím vật lý có (a) kích hoạt mở app đích không, (b) mở đích NÀO,
 * và (c) có "nuốt" (consume) sự kiện đó không.
 *
 * Rework 1.19 (owner 2026-08-14): **BỎ khái niệm cử chỉ (Nhấn/Nhấn-giữ) + mốc thời-gian-giữ.** Trên xe này
 * nút phát KEYCODE KHÁC NHAU cho nhấn-ngắn vs nhấn-giữ, nên phân biệt bằng thời gian giữ là thừa và gây lỗi
 * (giữ > 500ms từng làm PRESS từ chối bắn → phím lọt → hệ thống mở nhầm trợ lý Gemini). Giờ đơn giản:
 * đúng keycode đã cấu hình → **BẮN 1 lần** (trên DOWN đầu của mỗi lần nhấn) + **NUỐT trọn** phím đó
 * (DOWN/UP/repeat); keycode khác hoặc tính năng tắt → **pass-through hoàn toàn** (giữ chức năng gốc).
 *
 * F3 (owner 2026-08-24): đổi từ "so với MỘT mã" sang **tra DANH SÁCH gán** (nhiều phím → nhiều app).
 * Máy trạng thái chống-bắn-lặp GIỮ NGUYÊN (`CLAUDE.md §6` — không đảo đường đang chạy tốt ngoài hiện
 * trường), chỉ khoá theo **(keyCode, downTime)** thay vì downTime đơn lẻ: hai phím đã gán được bấm gần
 * nhau có thể trùng `downTime` ở độ phân giải ms, mà khoá chung một ô thì phím thứ hai bị nuốt mất lần bắn.
 *
 * 2.88 · KEY-SOURCE-SPLIT tầng 2 (spec `kachi-288-key-source-split` R3, §4.2): núm bệ giữa và nút âm lượng vô-lăng ra
 * CÙNG mã 291/292 [ĐO xe owner 04/10], nên một dòng gán có thể kèm NGUỒN. Matcher nay NHỚ QUYẾT ĐỊNH của từng lần nhấn
 * (mã phím → downTime, nuốt?, nguồn) thay vì chỉ downTime đã bắn:
 *  - mã KHÔNG có dòng gán theo nguồn ⇒ quyết định y như 2.87, hàm tra nguồn KHÔNG được gọi (không HAL);
 *  - mã CÓ dòng gán theo nguồn ⇒ gọi hàm tra nguồn đúng MỘT lần, trên DOWN đầu của lần nhấn, rồi chọn
 *    (mã, nguồn) → (mã, không nguồn) → để phím đi tiếp;
 *  - DOWN lặp / UP / OTHER của cùng lần nhấn DÙNG LẠI quyết định của DOWN đầu (không đọc lại; DOWN đã nuốt thì UP cũng
 *    nuốt, DOWN đã để đi thì UP cũng để đi — không UP mồ côi sang hệ thống);
 *  - UP/OTHER không thấy DOWN nào ⇒ nuốt khi và chỉ khi có dòng (mã, không nguồn) — đúng hành vi 2.87 cho dòng thường.
 *
 * CÓ TRẠNG THÁI (mỗi lần nhấn chỉ bắn 1 lần). Gọi tuần tự từ 1 luồng (onKeyEvent main).
 */
enum class VoiceKeyAction { DOWN, UP, OTHER }

/**
 * Cấu hình tối thiểu: bật/tắt + **danh sách gán** (mã phím [+ nguồn] → đích).
 *
 * Ý nghĩa của chuỗi đích (package name hay sentinel) do tầng app quyết định, KHÔNG ở `:core` — ở đây nó chỉ
 * là dữ liệu đi kèm được chuyển tiếp nguyên vẹn ra [VoiceKeyDecision.targetSpec].
 */
data class VoiceKeyConfig(val enabled: Boolean, val bindings: List<VoiceKeyBinding>) {
    /** Tra đích — tất định vì `VoiceKeyBindings` cưỡng chế mỗi (mã phím, nguồn) chỉ gán MỘT đích. */
    fun targetFor(keyCode: Int, source: KeySourceKind? = null): String? = VoiceKeyBindings.targetFor(bindings, keyCode, source)
}

/**
 * @property fire true → tầng app phóng intent mở [targetSpec].
 * @property consume true → [android.accessibilityservice.AccessibilityService.onKeyEvent] trả true (chặn hệ
 *   thống xử lý phím). Chỉ true cho phím CÓ dòng gán khớp.
 * @property targetSpec đích của phím vừa bắn. **Bất biến: `fire` ⟺ `targetSpec != null`** — tầng app không
 *   phải tra lại danh sách (tra hai lần = hai kết quả nếu prefs đổi giữa chừng).
 * @property source nút vật lý đã dùng để chọn dòng (2.88); `null` = không biết / không tra.
 * @property reason lý do nguồn cho nhật ký (R6, xem [KeySourceLookup.reason]); `null` ⇔ lần nhấn này KHÔNG tra nguồn
 *   (mã không có dòng gán theo nguồn — đường 2.87).
 */
data class VoiceKeyDecision(
    val fire: Boolean,
    val consume: Boolean,
    val targetSpec: String? = null,
    val source: KeySourceKind? = null,
    val reason: String? = null,
) {
    companion object { val IGNORE = VoiceKeyDecision(fire = false, consume = false) }
}

class VoiceKeyMatcher {

    /** Quyết định của MỘT lần nhấn đang dở (DOWN đầu đã xử lý, chưa thấy UP). */
    private class Press(val downTime: Long, val consume: Boolean, val source: KeySourceKind?, val reason: String?) {
        /** DOWN lặp / UP / OTHER của cùng lần nhấn: không bắn, nuốt y như DOWN đầu. */
        fun again() = VoiceKeyDecision(fire = false, consume = consume, source = source, reason = reason)
    }

    // (keyCode → quyết định của lần nhấn đang dở) — chống bắn lặp + giữ UP/lặp cùng phía với DOWN đầu.
    // Kích thước bị chặn bởi số mã phím ĐÃ GÁN từng được bấm (mã không có dòng gán nào không bao giờ được nhớ; mã có
    // dòng theo nguồn được nhớ cả khi lần nhấn đi tiếp — để UP đi tiếp theo).
    private val presses = HashMap<Int, Press>()

    /**
     * @param source hàm tra nguồn của lần nhấn này — CHỈ được gọi trên DOWN đầu và CHỈ khi mã có dòng gán theo nguồn
     *   ([VoiceKeyBindings.needsSource]). Mặc định = không tra được ⇒ không biết nguồn ⇒ chỉ dòng không nguồn.
     */
    fun onKey(
        cfg: VoiceKeyConfig,
        action: VoiceKeyAction,
        keyCode: Int,
        downTimeMs: Long,
        source: () -> KeySourceLookup = { KeySourceLookup.NO_LOOKUP },
    ): VoiceKeyDecision {
        if (!cfg.enabled) return VoiceKeyDecision.IGNORE
        val press = presses[keyCode]
        return when (action) {
            // Bắn 1 lần cho mỗi lần nhấn (theo downTime). DOWN có downTime khác ⇒ lần nhấn MỚI (UP cũ lạc mất) ⇒ quyết lại.
            VoiceKeyAction.DOWN ->
                if (press != null && press.downTime == downTimeMs) press.again() else begin(cfg, keyCode, downTimeMs, source)
            // #10 (deep-pass 2026-09-23): UP kết thúc lần nhấn ⇒ XOÁ entry. Chống-lặp dựa trên chu kỳ DOWN→UP,
            // KHÔNG chỉ dựa 'downTime khác' — nếu ROM TÁI DÙNG cùng downTimeMs cho lần nhấn mới thì (không xoá)
            // fire=false MÃI ⇒ phím chết trong khi service VẪN bound (status ACTIVE, watchdog không chữa được vì
            // không phải lỗi bind). Xoá ở UP: DOWN của lần nhấn kế luôn khác entry (đã trống) ⇒ fire lại đúng 1 lần.
            VoiceKeyAction.UP -> { presses.remove(keyCode); press?.again() ?: orphan(cfg, keyCode) }
            VoiceKeyAction.OTHER -> press?.again() ?: orphan(cfg, keyCode)
        }
    }

    /** DOWN đầu của một lần nhấn: tra nguồn (nếu cần) → chọn dòng → nhớ quyết định cho phần còn lại của lần nhấn. */
    private fun begin(cfg: VoiceKeyConfig, keyCode: Int, downTimeMs: Long, source: () -> KeySourceLookup): VoiceKeyDecision {
        val lookup = if (VoiceKeyBindings.needsSource(cfg.bindings, keyCode)) source() else null
        val target = cfg.targetFor(keyCode, lookup?.kind)
        if (target == null && lookup == null) {
            // Mã không gán (đường 2.87): không nhớ gì, để phím đi tiếp.
            presses.remove(keyCode)
            return VoiceKeyDecision.IGNORE
        }
        val p = Press(downTimeMs, consume = target != null, source = lookup?.kind, reason = lookup?.reason)
        presses[keyCode] = p
        return VoiceKeyDecision(fire = target != null, consume = p.consume, targetSpec = target, source = p.source, reason = p.reason)
    }

    /** UP/OTHER không thấy DOWN (vd service vừa nối lại): nuốt ⟺ có dòng (mã, không nguồn) — hành vi 2.87. */
    private fun orphan(cfg: VoiceKeyConfig, keyCode: Int): VoiceKeyDecision =
        if (cfg.targetFor(keyCode, null) != null) VoiceKeyDecision(fire = false, consume = true) else VoiceKeyDecision.IGNORE

    /** Reset trạng thái (gọi khi service (re)connect) để một lần nhấn dở dang không dính sang phiên mới. */
    fun reset() { presses.clear() }
}
