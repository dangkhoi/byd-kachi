package com.byd.clusternav.launcher.voice

/**
 * ═══ XIN BỘ NGHE NỀN NHƯỜNG MICRO — chờ CÓ TRẦN, thuần (cấm `android.*`) ══════════════════════════════════════
 *
 * ## Ca hỏng nó chữa ([SOÁT 2026-09-18], giao điểm "Hey Kachi" × phiên lệnh)
 * Bộ nghe wake **giữ chốt micro suốt thời gian màn sáng** (nó phải giữ: một `AudioRecord` mở liên tục). Mọi lối
 * vào phiên lệnh — nút mic · ô thanh nút · phím vô-lăng · cầu kiểm thử — đi qua [VoiceSingleFlight.acquire] và vì
 * thế chỉ nhận `Busy("wake")`: **bật một tính năng mặc định TẮT làm chết tính năng chính**, im lặng. Đây đúng họ
 * lỗi giao-điểm mà dự án đã trả giá vài lần (hai tính năng đều đúng khi đứng riêng).
 *
 * ## Vì sao chờ ở đây là an toàn (và vì sao nó KHÔNG thể treo)
 *  • Số nhịp chờ là **hằng** (`waitMs / stepMs`) ⇒ không có vòng nào phụ thuộc điều kiện bên ngoài để kết thúc.
 *  • Chỗ gọi ([VoiceCapture.listen]) vốn đã **chặn** trên luồng nền, đúng chỗ được phép chờ.
 *  • Bộ nghe hỏi [VoiceSingleFlight.yieldRequested] **mỗi khung** (~100 ms) rồi nhả trong một khung +
 *    `stop`/`release` ⇒ trần 600 ms rộng gấp nhiều lần thời gian thật cần.
 *  • Hết trần ⇒ trả `false` và chỗ gọi cư xử **y như trước** (một dòng nhật ký rồi rút).
 *
 * Thuần + [sleep] bơm được ⇒ kiểm được off-car cả ba ca (nhường kịp · không nhường · cầu chì) mà không cần thiết
 * bị và không cần chờ thật.
 */
object VoiceMicPreempt {

    /** Trần chờ tổng — xem KDoc lớp. */
    const val WAIT_MS = 600L

    /** Một nhịp hỏi lại. */
    const val STEP_MS = 30L

    /** Ngủ [ms]; trả `false` nếu bị interrupt (chỗ gọi phải thôi chờ ngay). */
    fun sleepQuiet(ms: Long): Boolean = try {
        Thread.sleep(ms); true
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt(); false
    }

    /**
     * Xin chủ hiện tại nhường micro rồi chờ tối đa [waitMs] để cầm được chốt cho [label].
     *
     * @return `true` = đã cầm chốt (chỗ gọi **phải** nhả trong `finally`); `false` = không (chốt KHÔNG được cầm).
     */
    fun preempt(
        label: String,
        waitMs: Long = WAIT_MS,
        stepMs: Long = STEP_MS,
        sleep: (Long) -> Boolean = ::sleepQuiet,
    ): Boolean {
        VoiceSingleFlight.requestYield()
        var waited = 0L
        while (waited < waitMs) {
            if (!sleep(stepMs)) return false
            waited += stepMs
            when (VoiceSingleFlight.acquire(label)) {
                VoiceSingleFlight.Grant.Ok -> return true
                // Cầu chì trong lúc chờ ⇒ thôi; chỗ gọi báo đúng lý do cũ, không nhân đôi dòng nhật ký.
                is VoiceSingleFlight.Grant.Fused -> return false
                is VoiceSingleFlight.Grant.Busy -> Unit // chưa nhả xong — chờ nhịp nữa
            }
        }
        return false
    }
}
