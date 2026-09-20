package com.byd.clusternav.launcher.voice

/**
 * ═══ B1 (1.70) · MÁY TRẠNG THÁI TƯỜNG MINH của MỘT phiên nghe — thuần :core, thay chùm 6 cờ race ════════════
 *
 * Owner 2026-09-17: *"voice là function quan trọng nhất nhì, chưa bao giờ ổn khi test thực tế… làm sâu, đảm bảo
 * nói cái nghe liền, thực hiện liền, feedback lại kết quả liền"*.
 *
 * ## Vì sao đưa LUẬT CHUYỂN xuống :core, không đưa cả cơ chế
 * `VoiceSession` (ở `:app`) trước đây mang **sáu** cờ rời — `running` · `capturing` · `confirmOpen` · `cancelled`
 * · `generation` · `pendingConfirm` — mỗi cờ là một ô nhớ mà nhiều luồng (vẽ · nghe nền · engine đọc) cùng
 * chạm, và ba lượt soát trước đã phải vá đúng các cờ này ba lần (KDoc `generation`/`confirmOpen`/`micOpen`).
 * Sáu boolean độc lập cho **2⁶ = 64** tổ hợp, mà chỉ một nhúm là hợp lệ; phần còn lại là các khe race không tên.
 *
 * Cái đưa xuống `:core` được là **luật**: *"phiên đang ở pha nào, và từ pha đó được sang pha nào"* — thuần, không
 * `android`, không luồng, nên **kiểm cạn off-car** (thứ mà test trên chùm cờ không làm được: chúng cần thiết bị
 * để dựng ra một race). Cơ chế còn lại (mở `AudioRecord`, `Handler`, overlay) ở lại `:app` — nhưng nay đọc MỘT
 * `AtomicReference<VoiceTurnPhase>` thay vì suy từ sáu cờ.
 *
 * ## Các pha, và một chuyến đi điển hình
 * ```
 * IDLE ──start──▶ LISTENING ──(có tiếng, hết câu)──▶ DECODING ──▶ EXECUTING
 *                    │                                               │
 *                    │(im lặng / hết trần / huỷ)                     ├─(cần xác nhận)─▶ CONFIRMING ─▶ EXECUTING
 *                    ▼                                               ├─(không hiểu)───▶ CLARIFYING ─▶ LISTENING
 *                  CLOSING ◀───────────────────────────────────────┴─(xong)─────────▶ FOLLOW_UP ─▶ LISTENING
 *                    │                                                                     │
 *                    ▼                                              (hết giờ / hết quỹ) ────┘
 *                  IDLE
 * ```
 * Ba pha nối (CONFIRMING · CLARIFYING · FOLLOW_UP) đều **mở lại một lượt nghe** ⇒ quay về LISTENING; đó là chỗ
 * vòng lặp hoang sinh ra, nên [canOpenMic] là cổng CỨNG: chỉ LISTENING mới được mở mic.
 *
 * ## Bất biến chống-loop (OQ5 — owner)
 *  • **Chỉ MỘT pha mở được mic** ([canOpenMic] = LISTENING). CONFIRMING/CLARIFYING/FOLLOW_UP phải chuyển **về**
 *    LISTENING trước, và mỗi lần chuyển ấy tiêu một suất của trần lượt nối (đếm ở `VoiceSession`, `:app`).
 *  • **CLOSING là hố hút**: từ CLOSING chỉ về IDLE. Mọi việc nền về muộn thấy CLOSING/IDLE thì rút, không vẽ.
 *  • Mọi chuyển KHÔNG hợp lệ ⇒ [next] trả `null` (chỗ gọi giữ nguyên pha) — không có "chuyển lén".
 */
enum class VoiceTurnPhase {
    /** Chưa có phiên. Trạng thái nghỉ. */
    IDLE,

    /** Micro đang mở, đang nghe một lượt (chính · hỏi-lại · hội-thoại · xác-nhận đều là LISTENING). */
    LISTENING,

    /** Đã chốt câu, đang giải mã / phân tích ý định. Micro đã đóng. */
    DECODING,

    /** Đang thi hành ý định (bắn lệnh xe / mở app / …) và đọc phản hồi. */
    EXECUTING,

    /** Đang chờ người lái trả lời "đồng ý/huỷ" cho một việc rủi ro. Có thể mở một lượt nghe ngắn. */
    CONFIRMING,

    /** Câu không hiểu, đang hỏi lại. Sẽ mở một lượt nghe. */
    CLARIFYING,

    /** Đã trả lời xong, giữ mic mở chờ câu tiếp (hội thoại). Sẽ mở một lượt nghe. */
    FOLLOW_UP,

    /** Đang đóng (tấm chữ nán lại rồi biến). Chỉ về IDLE. */
    CLOSING,
}

/**
 * Máy chuyển pha — thuần, không trạng thái (mọi hàm nhận pha hiện tại, trả pha mới hoặc `null`).
 *
 * `VoiceSession` giữ `AtomicReference<VoiceTurnPhase>` và gọi các hàm ở đây; giá trị `null` nghĩa là chuyển
 * không hợp lệ ⇒ giữ nguyên pha (không ném — một chuyển sai là lỗi lập trình cần thấy trong test, không phải
 * một crash trên xe).
 */
object VoiceTurnMachine {

    /** Các chuyển hợp lệ: pha → tập pha đích. Bảng DỮ LIỆU, không phải chuỗi `when` rải khắp nơi. */
    private val EDGES: Map<VoiceTurnPhase, Set<VoiceTurnPhase>> = mapOf(
        VoiceTurnPhase.IDLE to setOf(VoiceTurnPhase.LISTENING, VoiceTurnPhase.CLOSING),
        // Nghe xong: có tiếng ⇒ DECODING; im lặng/hết trần/huỷ ⇒ CLOSING.
        VoiceTurnPhase.LISTENING to setOf(VoiceTurnPhase.DECODING, VoiceTurnPhase.CLOSING),
        // Giải mã xong: hiểu ⇒ EXECUTING; không hiểu ⇒ CLARIFYING; hỏng/huỷ ⇒ CLOSING.
        VoiceTurnPhase.DECODING to setOf(
            VoiceTurnPhase.EXECUTING, VoiceTurnPhase.CLARIFYING, VoiceTurnPhase.CLOSING,
        ),
        // Thi hành xong: cần xác nhận ⇒ CONFIRMING; mở hội thoại ⇒ FOLLOW_UP; xong hẳn ⇒ CLOSING.
        //
        // ⚠⚠ [ĐO xe 2026-09-20 · `oncar-1.84-session-2026-09-20.md` §5] **CLARIFYING phải có ở đây**, và thiếu nó
        // là gốc của lỗi *"phiên thoại biến mất"*. Sơ đồ ở KDoc lớp (nhánh *"(không hiểu)"* mọc ra từ EXECUTING) và
        // chính chỗ gọi (`VoiceSessionTurns.askAgain`, chú thích *"B1: EXECUTING → CLARIFYING"*) đều nói cạnh này
        // tồn tại; chỉ **bảng dữ liệu** này bỏ sót nó. Cạnh này KHÔNG phải một đường lùi cho tiện: cổng hỏi-lại
        // (`clarifyAsk`) nằm **sau** `go(DECODING); go(EXECUTING)` trong `VoiceSession.execute` — tức lúc quyết định
        // *"câu này không hiểu"* thì phiên đã ở EXECUTING, nên EXECUTING mới là pha XUẤT PHÁT thật của lượt hỏi lại.
        //
        // Hậu quả khi thiếu (đo được trong nhật ký xe, theo đúng thứ tự): `askAgain` bị từ chối ⇒ pha kẹt ở
        // EXECUTING ⇒ `listenAgain` xin LISTENING cũng bị từ chối ⇒ lượt trả lời gọi `execute` lần hai và in ra
        // `pha: chuyển KHÔNG hợp lệ EXECUTING ⇒ DECODING` — dòng owner bắt được. Tức **cả ba** mốc pha của một lượt
        // hỏi-lại đều trượt, và pha thôi mô tả sự thật kể từ câu không-hiểu đầu tiên của mỗi phiên.
        //
        // (DECODING → CLARIFYING ở trên vẫn giữ: nó là đường của một bản sau muốn hỏi lại **trước** khi thi hành.)
        VoiceTurnPhase.EXECUTING to setOf(
            VoiceTurnPhase.CONFIRMING, VoiceTurnPhase.CLARIFYING, VoiceTurnPhase.FOLLOW_UP, VoiceTurnPhase.CLOSING,
        ),
        // Xác nhận xong: đồng ý ⇒ EXECUTING (chạy việc); từ chối/hết giờ ⇒ CLOSING.
        VoiceTurnPhase.CONFIRMING to setOf(
            VoiceTurnPhase.LISTENING, VoiceTurnPhase.EXECUTING, VoiceTurnPhase.CLOSING,
        ),
        // Hỏi lại: mở lượt nghe ⇒ LISTENING; bỏ cuộc ⇒ CLOSING.
        VoiceTurnPhase.CLARIFYING to setOf(VoiceTurnPhase.LISTENING, VoiceTurnPhase.CLOSING),
        // Hội thoại: mở lượt nghe ⇒ LISTENING; hết giờ/hết quỹ ⇒ CLOSING.
        VoiceTurnPhase.FOLLOW_UP to setOf(VoiceTurnPhase.LISTENING, VoiceTurnPhase.CLOSING),
        // Hố hút.
        VoiceTurnPhase.CLOSING to setOf(VoiceTurnPhase.IDLE),
    )

    /** Chuyển từ [from] sang [to] có hợp lệ không. `CLOSING` đến được từ MỌI pha (huỷ bất cứ lúc nào) TRỪ chính nó. */
    fun canGo(from: VoiceTurnPhase, to: VoiceTurnPhase): Boolean =
        (to == VoiceTurnPhase.CLOSING && from != VoiceTurnPhase.CLOSING) || to in (EDGES[from] ?: emptySet())

    /** Trả [to] nếu chuyển hợp lệ, `null` nếu không (chỗ gọi giữ nguyên pha). */
    fun next(from: VoiceTurnPhase, to: VoiceTurnPhase): VoiceTurnPhase? = if (canGo(from, to)) to else null

    /**
     * ═══ Bất biến chống-loop: CHỈ pha này được mở micro ═══════════════════════════════════════════════════
     *
     * [ĐO xe 2026-09-16] 309 lượt mở micro từ 7 phiên: mỗi câu trả lời (kể cả chuỗi rác "ừ"/"ừm") mở lại micro.
     * Gốc là **không có một chỗ duy nhất** kiểm "được mở mic không". Nay: mọi lượt nghe (chính · hỏi-lại · hội
     * thoại · xác-nhận) phải đưa phiên về [VoiceTurnPhase.LISTENING] TRƯỚC khi chạm `AudioRecord`, và chỉ pha ấy
     * mới cho mở. Ba pha nối phải chuyển-về-LISTENING (tiêu một suất trần lượt nối) chứ không mở thẳng.
     */
    fun canOpenMic(phase: VoiceTurnPhase): Boolean = phase == VoiceTurnPhase.LISTENING

    /** Phiên đã kết thúc chưa (mọi việc nền về muộn thấy pha này thì rút, không vẽ/không thi hành). */
    fun isDone(phase: VoiceTurnPhase): Boolean = phase == VoiceTurnPhase.CLOSING || phase == VoiceTurnPhase.IDLE
}
