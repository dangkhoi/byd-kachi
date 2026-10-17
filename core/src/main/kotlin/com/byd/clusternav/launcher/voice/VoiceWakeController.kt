package com.byd.clusternav.launcher.voice

/**
 * ═══ BỘ NÃO của bộ nghe "Hey Kachi" — quyết định MỖI NHỊP, thuần (cấm `android.*`) ════════════════════════════
 *
 * Đây là phần "làm rất khéo" mà owner lo: nó gom [VoiceLoadGuard] + [VoiceWakeGate] + cooldown + cầu chì
 * false-accept thành **một máy quyết định kiểm được off-car**. Lớp Android ở `:app` chỉ là vòng lặp mỏng: đọc
 * micro → tính RMS + đọc `/proc/loadavg` → hỏi [onFrame]; nếu bảo `RUN_KWS` thì chạy KWS trên khung → báo kết
 * quả về [onKwsResult]. Mọi lý lẽ chống-hang-CPU nằm ở đây, nơi bơm được số giả để chứng minh.
 *
 * ## Bốn tầng cắt CPU (đắt dần)
 *  1. **Load-guard** — hệ nóng ⇒ `SUSPENDED`: nhả mic, thôi cả RMS lẫn KWS (lá chắn chính vì nghe nền).
 *  2. **Cooldown** — vừa nổ wake xong ⇒ `IDLE` một lúc (phiên lệnh đang chạy; tránh tự nghe lại chime/tiếng mình).
 *  3. **Cổng năng lượng** — im ⇒ `IDLE` (chỉ toán RMS); có giọng ⇒ mới xét tầng 4.
 *  4. **Ngân sách CPU suy diễn** — [VoiceWakeCpuBudget]: tính bằng **ms CPU đo được** của từng lượt gọi engine
 *     (không bằng khung) ⇒ tỉ lệ CPU của phần nơ-ron có **chặn trên chứng minh được** kể cả khi cabin ồn liên tục
 *     trên ngưỡng (nhạc). Hết ngân sách ⇒ `OBSERVE` (engine chỉ gom tiếng, không suy diễn). Xem [onInferenceCost].
 *
 * ## Cầu chì false-accept (OQ5, owner: tự tắt + báo)
 * Wake nổ quá [maxWakesPerWindow] lần trong [wakeWindowMs] ⇒ `FUSED`: coi như đang nghe nhầm loạn xạ, **tự
 * đình chỉ** (mọi `onFrame` sau trả `SUSPENDED`); `:app` tắt công tắc + báo user. Cùng triết lý cầu chì của
 * [VoiceSingleFlight] — lớp cuối không cần biết nguyên nhân.
 */
class VoiceWakeController(
    private val gate: VoiceWakeGate = VoiceWakeGate(),
    private val loadGuard: VoiceLoadGuard = VoiceLoadGuard(),
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val maxWakesPerWindow: Int = DEFAULT_MAX_WAKES,
    private val wakeWindowMs: Long = DEFAULT_WAKE_WINDOW_MS,
    private val fuseRecoveryMs: Long = DEFAULT_FUSE_RECOVERY_MS,
    private val budget: VoiceWakeCpuBudget = VoiceWakeCpuBudget(),
    private val tailMs: Long = DEFAULT_TAIL_MS,
) {
    /**
     * Việc cần làm với khung âm thanh hiện tại. `OBSERVE` = có tiếng nhưng ngân sách đang nợ ⇒ engine **gom** khung
     * (giữ cửa sổ liền mạch cho lượt sau) mà **không** suy diễn.
     */
    enum class Frame { SUSPENDED, IDLE, OBSERVE, RUN_KWS, FINISH }

    /** Kết quả sau khi KWS chấm một khung. */
    enum class Wake { NONE, FIRE, FUSED }

    private var cooldownUntil = 0L
    private val wakeTimes = ArrayDeque<Long>()

    /**
     * Cầu chì false-accept: mốc hết tự-tắt. `0L` = không fuse. **TỰ CLEAR** sau [fuseRecoveryMs] thay vì latch
     * vĩnh viễn — [ĐO xe 2026-09-21] owner nổ được 1 lần rồi thử lại nhiều lần (>maxWakes) làm fuse latch, và
     * fuse cũ chỉ [reset] lúc dựng lại service ⇒ *"kêu hoài không lên"* tới hết chuyến. Nay một phút ồn/thử
     * nhiều chỉ tắt tạm, hết cửa sổ là nghe lại.
     */
    private var fusedUntil = 0L

    /** Nhịp âm thanh: quyết định có chạy KWS không. `:app` gọi với RMS khung + load hiện tại. */
    fun onFrame(rms: Double, load1: Double, nowMs: Long): Frame {
        if (fusedUntil > 0L) {
            if (nowMs < fusedUntil) return Frame.SUSPENDED
            fusedUntil = 0L  // hết cửa sổ hồi phục ⇒ nghe lại (không cần restart service)
        }
        if (!loadGuard.allow(load1)) return Frame.SUSPENDED      // tầng 1: hệ nóng
        if (nowMs < cooldownUntil) { tailPending = false; return Frame.IDLE } // tầng 2: vừa nổ wake
        if (!gate.voiced(rms)) return tail(nowMs)                // tầng 3: im ⇒ chỉ toán RMS (trừ ĐUÔI câu, xem [tail])
        lastVoicedAt = nowMs; tailPending = true
        return if (budget.mayRun(nowMs)) Frame.RUN_KWS else Frame.OBSERVE // tầng 4: ngân sách CPU suy diễn
    }

    private var lastVoicedAt = 0L
    private var tailPending = false

    /**
     * ═══ ĐUÔI CÂU — VOICE-WAKE-TAIL-UNDECODED (owner 2026-10-16, 3.1.9) ═════════════════════════════════════════
     *
     * [ĐO máy ảo 15/10, harness wake] cabin YÊN chỉ 61,4 % câu gọi nổ (1,44 lượt giải mã/tệp): khung im bị tầng 3 cắt
     * TRƯỚC engine, mà engine ASR chỉ giải mã khi gom đủ 500 ms ⇒ âm cuối của câu gọi (đúng chỗ "…chi") không bao giờ
     * được giải mã lại. Sửa ở tầng quyết định, chung cho mọi engine:
     *  • [tailMs] ms im lặng SAU khung có tiếng cuối vẫn tới engine dưới dạng `OBSERVE` (gom, không suy diễn) — cửa sổ
     *    có đủ phần cuối câu + một quãng im ngắn;
     *  • hết quãng ấy ⇒ đúng MỘT `FINISH` (sườn tiếng→im): engine giải mã cửa sổ một lần. Một lượt thêm mỗi đoạn tiếng,
     *    vẫn qua [budget] (nợ ⇒ không FINISH).
     * Ngắn có chủ ý: đuôi im dài làm mô hình nghe kém (KDoc `VoiceVadTrim`: +4 s im ⇒ 22/25 → 6/25).
     */
    private fun tail(nowMs: Long): Frame {
        if (!tailPending) return Frame.IDLE
        if (nowMs - lastVoicedAt < tailMs) return Frame.OBSERVE
        tailPending = false
        return if (budget.mayRun(nowMs)) Frame.FINISH else Frame.IDLE
    }

    /**
     * ═══ TẦNG 4 — chỗ gọi báo **ms CPU thật** của một lượt gọi engine (`feed` hoặc gom ở `OBSERVE`) ═══════════════
     *
     * Thay trần cũ "40 khung / 10 s" (2026-09-18). Trần ấy hứa "≤ 40 %" — đúng cho KWS dòng (mỗi khung rẻ), **sai**
     * cho engine ASR cửa-sổ (VOICE-WAKE-CPU-BUDGET, [ĐO xe 15/10]: 5 khung = một lượt giải mã ~1,35 s CPU ⇒ 105 % TB,
     * đỉnh 263 %). Đếm khung không nói gì về CPU; đo CPU thì nói. Lý lẽ + chặn trên: KDoc [VoiceWakeCpuBudget].
     *
     * Lỗ mà tầng 3 không bịt: nền của [VoiceWakeGate] cố ý chỉ học lúc im (bài canh `giong lien tuc
     * khong tu keo nen len bit minh`) ⇒ nhạc liên tục trên ngưỡng làm `voiced()` đúng mọi khung. Tầng này không hỏi
     * âm thanh, chỉ hỏi tiền ⇒ đúng với mọi phổ âm và mọi engine.
     */
    fun onInferenceCost(cpuMs: Long, nowMs: Long) = budget.charge(cpuMs, nowMs)

    /**
     * WAKE-TAIL-STALE-FINISH (soát Pass 9 [P3]) — listener vừa MỞ micro cho một lượt nghe mới (sau YIELD/SUSPEND/HANDED/lỗi).
     * Đuôi câu của lượt TRƯỚC không còn nghĩa: không xoá thì khung im ĐẦU của lượt này (đã quá [tailMs]) phát một `FINISH`
     * trên cửa sổ cũ. Một phép gán mỗi lần mở micro — không tốn gì trên nhịp khung.
     */
    fun onMicOpened() { tailPending = false }

    /** Tổng ms CPU suy diễn đã tính + số dư hiện tại — cho dòng nhật ký định kỳ của `:app`. */
    fun chargedCpuMs(): Long = budget.chargedMs
    fun budgetCreditMs(): Double = budget.creditMs()

    /**
     * WAKE-WEAK-UNIT (3.1.14) — áp trần ngân sách theo máy (yếu/thường) và trạng thái bận/rảnh của máy, tính bởi
     * [VoiceWakeDuty.limits] từ [VoiceWakeBackoff]. Tầng 4 vẫn là chỗ quyết duy nhất; lớp này chỉ đổi hai tham số của nó.
     */
    fun pace(deviceDuty: Double, busy: Boolean, nowMs: Long) {
        val (d, cap) = VoiceWakeDuty.limits(deviceDuty, busy, budget.burstMs)
        if (d != budget.duty || cap != budget.capMs) budget.retune(d, cap, nowMs)
    }

    /** Trần dài hạn đang áp (nhật ký `ngan_sach`). */
    fun budgetDuty(): Double = budget.duty

    /** KWS báo có/không khớp câu gọi trên khung vừa RUN_KWS. Trả việc cần làm ở cấp phiên. */
    fun onKwsResult(matched: Boolean, nowMs: Long): Wake {
        if (nowMs < fusedUntil || !matched) return Wake.NONE
        cooldownUntil = nowMs + cooldownMs
        wakeTimes.addLast(nowMs)
        while (wakeTimes.isNotEmpty() && nowMs - wakeTimes.first() >= wakeWindowMs) wakeTimes.removeFirst()
        if (wakeTimes.size > maxWakesPerWindow) { fusedUntil = nowMs + fuseRecoveryMs; wakeTimes.clear(); return Wake.FUSED }
        return Wake.FIRE
    }

    fun isFused(): Boolean = fusedUntil > 0L

    /** Bật lại từ đầu (khi user bật công tắc lại sau khi cầu chì đã tắt). */
    fun reset() {
        cooldownUntil = 0L; wakeTimes.clear(); budget.reset(); fusedUntil = 0L; tailPending = false
        gate.reset(); loadGuard.reset()
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 3_000L      // sau khi nổ wake, nghỉ nghe (phiên lệnh đang chạy)
        const val DEFAULT_MAX_WAKES = 6             // > số này trong cửa sổ ⇒ nghi nghe nhầm ⇒ tự tắt (OQ5)
        const val DEFAULT_WAKE_WINDOW_MS = 60_000L
        const val DEFAULT_FUSE_RECOVERY_MS = 60_000L  // cầu chì TỰ tắt sau 1 phút (không latch vĩnh viễn)

        /**
         * Quãng im sau câu được gom trước lượt giải mã cuối — xem [tail]. [ĐO máy ảo 16/10, harness wake, cabin yên 57 câu
         * gọi] không đuôi 61,4 % · 600 ms 80,7 % · **800 ms 86,0 %** (cùng 1,82 lượt/tệp); nổ nhầm 0/45 ở cả hai.
         * ≤ 800 ms để câu gọi ≤ 0,8 s còn trọn trong cửa sổ 1,6 s và đuôi im không dài (KDoc `VoiceVadTrim` §6).
         */
        const val DEFAULT_TAIL_MS = 800L
    }
}
