package com.byd.clusternav.launcher.testbridge

/**
 * ═══ CẦU KIỂM THỬ · KHI NÀO ĐƯỢC CHỐT LỜI ĐÁP CỦA `say` ══════════════════════════════════════════════════════
 *
 * Thuần Kotlin ⇒ sống ở `:core` (luật phân tầng `LayeringRulesTest`) và khoá được bằng bài canh off-device
 * (`TestBridgeSettleTest`). Phần bấm giờ (`Handler`) nằm ở `KachiTestBridge.runSay` bên `:app`; ở đây chỉ có
 * **luật quyết định**.
 *
 * ## Bệnh nó chữa — [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L4
 * Bản trước nán lại đúng `GRACE_MS = 700` ms rồi chốt. Hai nhánh chạy ở **luồng nền** vì thế luôn trả về
 * `replies: []`:
 *  • gói lệnh (`MacroRunner` có `Thread.sleep` giữa các bước): `đóng hết kính` → `replies: []` (ms=703) ·
 *    `rời xe` → `replies: []`;
 *  • dẫn đường cần toạ độ (geocode ở luồng nền): `dẫn đường đến Bitexco --ez auto_confirm true` → chỉ thấy dòng
 *    TẠM *"… đang tra điểm đến…"*, câu trả lời CUỐI không bao giờ vào được lời đáp.
 *
 * Tức mọi script đo (kể cả bộ E2E) **mù** với kết quả thật của đúng hai nhánh dễ hỏng nhất. Trần cả lượt thì tận
 * `KachiTestBridge.CAP_MS` = 20 s, nên chỗ hụt là **nhịp chờ**, không phải trần.
 *
 * ## Luật mới: chờ theo VIỆC, không theo một hằng số
 * Không có đường móc *"dispatcher đã xong"* nào ở API công khai (`VoiceDispatcher` không phơi sự kiện kết thúc —
 * cùng lý do `listen` trả lời ngay, xem KDoc `runListen`), nên thứ quan sát được là **số dòng trả lời**. Luật:
 *  1. đủ số dòng so với số ý định đã phân tích **và** không có dòng mới trong [QUIET_MS] ⇒ chốt (đường nhanh:
 *     lệnh thường vẫn trả về trong ~0,3 s, không chậm hơn bản cũ);
 *  2. dòng mới nhất còn là dòng **TẠM** (kết bằng `…`, xem `VoiceFeedbackPhrase`) ⇒ còn việc đang chạy, chờ tiếp
 *     tới [MAX_PENDING_MS];
 *  3. trần cho mọi ca khác là [MAX_MS] — vẫn thấp hơn hẳn trần lượt, nên hết giờ ở đây là LỜI ĐÁP (có bao nhiêu
 *     nói bấy nhiêu), không phải `timeout`.
 */
object TestBridgeSettle {

    /** Lặng bao lâu thì coi là "đã xong" — đủ dài để một gói lệnh nối bước, đủ ngắn để không làm chậm 67 ca. */
    const val QUIET_MS = 300L

    /** Trần chờ thường (gói lệnh chạy nền). Xem KDoc lớp về vì sao 700 ms là không đủ. */
    const val MAX_MS = 5_000L

    /**
     * Trần chờ khi còn một dòng TẠM chưa được thay: nhánh dẫn đường phải chờ geocode qua mạng.
     * Vẫn dưới `KachiTestBridge.CAP_MS` (20 s) để trần lượt không bao giờ bị nhánh này chạm tới.
     */
    const val MAX_PENDING_MS = 12_000L

    /** Nhịp hỏi lại. Nhỏ hơn [QUIET_MS] vài lần ⇒ sai số chốt không đáng kể so với một lượt đo. */
    const val POLL_MS = 50L

    /** Dấu của một dòng TẠM — cùng ký tự mà `VoiceFeedbackPhrase` dùng để **không đọc** dòng đó lên. */
    const val INTERIM_SUFFIX = "…"

    /** Dòng này có phải dòng TẠM không (*"đang tra điểm đến…"*). Chuỗi rỗng ⇒ không phải. */
    fun interim(line: String): Boolean = line.trimEnd().endsWith(INTERIM_SUFFIX)

    /**
     * Đã đến lúc chốt chưa.
     *
     * @param elapsedMs từ lúc `execute()` trả về.
     * @param sinceChangeMs từ dòng (trả lời hoặc câu hỏi) gần nhất; bằng [elapsedMs] nếu chưa có dòng nào.
     * @param answered số dòng đã nhận = `replies` + `needs_confirm`.
     * @param expected số ý định đã phân tích — mỗi vế câu ghép là một ý định.
     * @param lastInterim dòng mới nhất còn là dòng TẠM.
     */
    fun done(elapsedMs: Long, sinceChangeMs: Long, answered: Int, expected: Int, lastInterim: Boolean): Boolean {
        val ceiling = if (lastInterim) MAX_PENDING_MS else MAX_MS
        if (elapsedMs >= ceiling) return true
        if (lastInterim) return false
        if (answered < expected) return false
        return sinceChangeMs >= QUIET_MS
    }
}
