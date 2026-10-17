package com.byd.clusternav.launcher.voice

/**
 * ═══ NGÂN SÁCH CPU của bộ nghe "Hey Kachi" — tính bằng **thời gian suy diễn đo được**, không bằng khung ════════
 *
 * VOICE-WAKE-CPU-BUDGET (2026-10-15). [ĐO xe 15/10 tối, `docs/diagnostics/oncar-perf-2026-10-15-evening.md` §A]
 * nhạc YouTube đang phát + "Hey Kachi" BẬT ⇒ `:wake` TB ≈ 105 % một lõi, đỉnh 263 %; 97/102 lượt giải mã ra chữ.
 * Gốc [SUY mã, khớp số đo]: trần cũ của [VoiceWakeController] đếm **khung 100 ms** (40 khung / 10 s). Đúng cho KWS
 * dòng cũ (mỗi khung rẻ), sai cho engine ASR cửa-sổ: 5 khung = MỘT lượt giải mã 0,7–2,9 s tường × 4 luồng. Đếm
 * khung thì không nói gì về CPU.
 *
 * ## Cơ chế: xô token tính bằng **ms CPU**
 *  • Xô nạp đều [duty] ms CPU cho mỗi ms tường (mặc định [DUTY] = 25 % một lõi), đầy ở [burstMs].
 *  • [mayRun] = xô còn dương. Sau MỖI lượt gọi engine, chỗ gọi [charge] đúng số ms CPU vừa tiêu (đo bằng
 *    `Process.getElapsedCpuTime()` quanh `feed` — gồm cả các luồng onnxruntime, không chỉ luồng nghe).
 *  • Xô được phép âm **một** lượt (không đoán trước giá một lượt giải mã) ⇒ phải trả nợ xong mới chạy tiếp ⇒ lượt
 *    giải mã tự giãn ra đúng theo giá thật của nó: đắt gấp đôi thì thưa gấp đôi. Không có hằng số "mấy giây một lượt".
 *
 * ## Chặn trên chứng minh được (khoá ở `VoiceWakeCpuBudgetTest`)
 * Trên MỌI khoảng dài `T` ms: `Σ CPU ≤ burstMs + duty × T + (giá một lượt lớn nhất)`. Hệ quả:
 *  • dài hạn (nhạc phát cả chuyến): ≤ [DUTY] một lõi;
 *  • một lượt gọi sau ≥ [BURST_WINDOW_MS] yên lặng được chạy liền một chuỗi ≈ `burstMs` ms CPU — đủ cho trọn một câu
 *    gọi (2–3 lượt giải mã) mà không bị giãn. Đây là chỗ giữ độ nhạy: câu gọi đầu tiên sau một quãng yên không bao giờ
 *    bị ngân sách cắt; chỉ nguồn ồn **kéo dài** (nhạc) mới bị giãn.
 *
 * Thuần (cấm `android.*`): đồng hồ do chỗ gọi đưa vào ⇒ bơm số giả để chứng minh off-car.
 */
class VoiceWakeCpuBudget(
    private val duty: Double = DUTY,
    private val burstMs: Double = DUTY * BURST_WINDOW_MS,
) {
    init {
        require(duty > 0.0 && duty.isFinite()) { "duty phải > 0" }
        require(burstMs > 0.0 && burstMs.isFinite()) { "burstMs phải > 0" }
    }

    private var credit = burstMs
    private var lastMs = UNSET

    /** Tổng ms CPU đã tính (cho dòng nhật ký định kỳ / lệnh đo) — không ảnh hưởng quyết định. */
    var chargedMs: Long = 0L
        private set

    /** Còn ngân sách để gọi engine lúc [nowMs] không. */
    fun mayRun(nowMs: Long): Boolean {
        refill(nowMs)
        return credit > 0.0
    }

    /** Trừ [cpuMs] ms CPU vừa tiêu (âm/NaN coi như 0 — đồng hồ CPU không đi lùi; số rác không được *nạp* xô). */
    fun charge(cpuMs: Long, nowMs: Long) {
        refill(nowMs)
        val c = cpuMs.coerceAtLeast(0L)
        credit -= c
        chargedMs += c
    }

    /** Số dư hiện tại (ms CPU) — chỉ để nhật ký/đo. */
    fun creditMs(): Double = credit

    fun reset() {
        credit = burstMs
        lastMs = UNSET
        chargedMs = 0L
    }

    private fun refill(nowMs: Long) {
        if (lastMs == UNSET) { lastMs = nowMs; return }
        val dt = nowMs - lastMs
        if (dt <= 0L) return            // đồng hồ đứng/lùi ⇒ không nạp (không bao giờ nạp nhờ đồng hồ lạ)
        credit = minOf(burstMs, credit + dt * duty)
        lastMs = nowMs
    }

    companion object {
        private const val UNSET = Long.MIN_VALUE

        /**
         * **HẰNG SỐ NGÂN SÁCH DUY NHẤT** — phần một lõi mà suy diễn wake được dùng, trung bình dài hạn.
         *
         * 25 %: [ĐO xe 15/10] một lượt giải mã ASR ≈ 1,35 s CPU (105 % TB ở nhịp 1 lượt / 1,29 s) ⇒ dưới nhạc liên tục
         * còn ~1 lượt / 5,4 s (trước: ~1 lượt / 1,3 s, 2,6 lõi đỉnh). Hạ xuống = mát hơn, bắt câu gọi *trong lúc nhạc
         * có lời* kém hơn; không có giá trị nào làm nó vô hiệu. Câu gọi trong cabin yên không bị ảnh hưởng (xem [burstMs]).
         */
        const val DUTY = 0.25

        /**
         * Cửa sổ trung bình: xô đầy = [DUTY] × 16 s = 4 000 ms CPU ≈ 3 lượt giải mã xe — trọn một câu gọi.
         *
         * WAKE-DECODE-COST (3.1.12): [ĐO xe 17/10] giá thật 2,2–4,4 s/lượt (xô chỉ ~1 lượt) — gốc là worker onnxruntime quay
         * chờ sau `Run()`, đã bỏ bằng [SherpaModelCatalog.ORT_SESSION_CONFIG] ([ĐO host] CPU/lượt × 0,37). [SUY] giá xe còn
         * ≈ 0,8–1,6 s ⇒ xô 4 000 ms lại phủ 2,5–5 lượt ⇒ GIỮ [DUTY]/cửa sổ, không nới xô. 🚗 chốt bằng dòng `ngan_sach` chuyến sau;
         * nếu giá xe vẫn > 2 s/lượt thì cỡ xô theo giá đo (giữ DUTY 0,25 dài hạn).
         */
        const val BURST_WINDOW_MS = 16_000L
    }
}
