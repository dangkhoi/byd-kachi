package com.byd.clusternav.launcher

/**
 * ═══ UX4 · CÚ GHI CỦA MỘT BƯỚC TƯƠNG ĐỐI — **CHỖ CHỜ 400 ms KHÔNG ĐƯỢC LÀ LUỒNG VẼ** ═════════════════════════
 *
 * Vai tách khỏi [VoiceDispatcher] ngày 2026-09-27 vì trần 500 dòng (CLAUDE.md §4.1), và tách theo **VAI**: tệp kia
 * trả lời *"câu vừa nói là việc gì, đi đường nào"*, tệp này chỉ trả lời **một** câu hỏi — *"[ClimateAuto.StepPlan]
 * này gồm mấy lệnh, và luồng nào được phép chờ giữa chúng"*.
 *
 * ## Bệnh nó chữa ([SOÁT 2.74 · P2] — an toàn khi đang lái)
 * Bản đầu của đường giọng nói cho UX4 chạy cả chuỗi **trên luồng gọi**, mà `VoiceSession` gọi [VoiceDispatcher] trên
 * luồng VẼ ⇒ câu *"tăng gió"* lúc xe đang AUTO đóng băng giao diện **~400 ms** ([ActionMacros.DEFAULT_GAP_MS]) giữa
 * hai lệnh. Bốn trăm ms đứng hình trên một cái xe đang lăn bánh đúng là loại lỗi mà CLAUDE.md mở đầu bằng.
 *
 * Cú **chạm** ô −/+ không bị, và nó không hề chờ ít hơn: `ControlTileFactory.nudge` chờ **đúng cùng nhịp ấy** nhưng
 * ở trong làn nền tuần tự của `ControlTileWrite` (`writer.submit`). Nghĩa là bản vá này không phát minh cơ chế mới —
 * nó chỉ đưa câu nói về đúng chỗ đứng mà ngón tay đã đứng từ đầu.
 *
 * ## Vì sao CHỈ nhánh rời-AUTO xuống nền, không phải cả ba
 *  • rời AUTO ([ClimateAuto.StepIntent.LeaveAuto]) ⇒ `auto != null` **và** `level != null` ⇒ HAI lệnh ⇒ **có** nhịp chờ;
 *  • `EnableAuto` (`level == null`) và `SetLevel` (`auto == null`) ⇒ **một** lệnh ⇒ không chờ một ms nào, nên chúng
 *    chạy **thẳng** trên luồng gọi, giữ y nguyên hành vi lẫn thứ tự của bản trước.
 *
 * Ba nhánh một lệnh không có gì để chờ, nên đẩy chúng xuống nền chỉ là đổi luồng của một đường đã chạy hiện trường
 * mà không được gì (CLAUDE.md §6). Từ 2.76 (VOICE-WRITE-LANE) thứ tự các vế của một câu ghép **không còn** phụ thuộc
 * vào việc nhánh nào đồng bộ: `VoiceDispatcher.runFrom` chỉ chạy vế kế tiếp khi [done] của vế này đã được gọi — với
 * nhánh rời-AUTO là **sau** nhịp 400 ms — nên *"tăng gió rồi tắt điều hoà"* ghi đúng `[auto OFF, mức +1, (nhịp), AC
 * OFF]` (bài `VoiceWriteLaneDispatchTest`). Trước đó (2.74–2.75) vế 2 có thể ghi vào giữa nhịp chờ — review Pass 1 [P2].
 *
 * Câu trả lời cho người lái quay về luồng vẽ qua [onUi] **trước khi** nói — cùng lẽ [VoiceReadback]: bảng
 * [ControlTileState] và câu nói đều thuộc luồng ấy.
 */
internal class VoiceClimateStep(
    private val control: () -> CarControlPort,
    /** Đẩy một việc về luồng VẼ — mặc định chạy thẳng để bài kiểm thuần tất định (xem `VoiceDispatcher.onUi`). */
    private val onUi: (() -> Unit) -> Unit,
    /** Chạy một việc CÓ CHỜ trên thread nền — cùng lambda mà `runMacro`/[VoiceReadback] đang dùng. */
    private val background: (() -> Unit) -> Unit,
) {

    /**
     * Thi hành [plan] rồi gọi [done] với *"xe có nhận không"* — **đúng một lần**, trên mọi nhánh.
     *
     * [done] chạy trên luồng gọi ở nhánh một-lệnh, và trên luồng VẼ ([onUi]) ở nhánh rời-AUTO. Cả hai nhánh bọc
     * [runCatching] vì trim thiếu lớp HAL thì [CarControlPort] **ném** (ca thật, `VoiceRelativeStepTest`) — và một
     * ngoại lệ trên luồng nền là một lượt trả lời **không bao giờ tới**, tức im lặng, đúng thứ tệ nhất.
     */
    fun apply(def: ControlDef, plan: ClimateAuto.StepPlan, done: (Boolean) -> Unit) {
        // MỘT lệnh ⇒ không có gì để chờ ⇒ y nguyên đường cũ, kể cả thứ tự các vế trong một câu ghép.
        if (plan.auto == null || plan.level == null) {
            done(runCatching { applyStep(def, plan, gap = {}) }.getOrDefault(false))
            return
        }
        // HAI lệnh: nhịp chờ nằm **trong** lambda nền này, và đó là bất biến `VoiceCommandWiringContractTest` canh.
        background {
            val ok = runCatching {
                applyStep(def, plan, gap = {
                    // [SOÁT Opus 2026-09-27] Lượt chờ bị CẮT ⇒ **trả lại** cờ `interrupt` (`InterruptedException`
                    // xoá nó khi được ném). `runCatching` trần ăn mất cờ ⇒ luồng đã bị yêu cầu dừng vẫn bắn tiếp
                    // lệnh HAL thứ hai. Lệ của repo là trả lại cờ (`VoiceWakeListener`), và nó thành quan trọng
                    // thật ngay khi cú ghi này sang một làn DÙNG LẠI luồng (backlog `VOICE-WRITE-LANE`).
                    runCatching { Thread.sleep(ActionMacros.DEFAULT_GAP_MS) }
                        .onFailure { if (it is InterruptedException) Thread.currentThread().interrupt() }
                })
            }.getOrDefault(false)
            onUi { done(ok) }
        }
    }

    /**
     * Thi hành một [ClimateAuto.StepPlan] bằng **đúng những cửa** mà ô nút dùng (`ControlTileFactory.nudge`).
     * `auto == null` (nút không khai [ControlDef.autoId]) ⇒ còn lại **một** lệnh [CarControlPort.actByKind] y như
     * trước UX4, kể cả bước tương đối trên nút [ControlKind.SELECT] (bảng định tuyến ấy giữ đúng cửa của từng kiểu).
     * Rời AUTO = HAI lệnh và phải **chờ** giữa chúng: bắn liên tiếp thì lệnh sau rơi — đúng lý do
     * `ActionMacros.DEFAULT_GAP_MS` tồn tại.
     *
     * @param gap nhịp chờ giữa hai lệnh. Là **tham số** chứ không viết thẳng `Thread.sleep` ở đây, có chủ ý: chỗ duy
     *   nhất được phép chờ là luồng nền, và một tham số đặt nhịp chờ **ngay tại chỗ gọi** thì đọc nguồn là thấy —
     *   thay vì phải tin rằng cái nhánh có `sleep` không bao giờ bị gọi từ luồng vẽ (đúng loại niềm tin đã sai một
     *   lần ở chính hàm này).
     */
    private fun applyStep(def: ControlDef, plan: ClimateAuto.StepPlan, gap: () -> Unit): Boolean {
        val okAuto = plan.auto?.let { control().toggle(def.autoId, it) } ?: true
        return plan.level?.let {
            if (plan.auto != null) gap()
            control().actByKind(def.id, it) && okAuto
        } ?: okAuto
    }
}
