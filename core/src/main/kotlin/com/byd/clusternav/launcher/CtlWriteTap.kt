package com.byd.clusternav.launcher

/**
 * ═══ 3.01 POLISH2 — CHỖ NGHE lệnh ghi xe cho XE GIẢ của máy ảo (CHỈ bản `vehicleTest` cắm được) ═══════════════════════════
 *
 * Owner 13/10 trên máy ảo: *"đèn ban ngày không tắt được"* — logcat có `tap drl → drl TẮT` + `setDayTimeLightState [2]`, nhưng số
 * giả (`DummyCarReceiver`, đèn mặc định SÁNG) đè lại BẬT mỗi nhịp đổ số ⇒ máy ảo không cư xử như xe. Chỗ cắm này cho xe giả
 * NHẠI lệnh vừa gửi (đèn · kính · cốp) để máy ảo đổi trạng thái như xe thật.
 *
 * Móc ở đường ghi DUY NHẤT ([ControlWriteFlow.write] — mọi ô · giọng nói · gói · cầu đều qua `HalBindingTable.write`), sau lượt
 * gửi, chỉ cho lượt LỆNH (không cho lượt nhả, không cho lệnh bị cổng có-mặt từ chối). Mặc định `null` ⇒ không làm gì; chỉ mã ở
 * `app/src/vehicleTest/` được gán (`DummyCarTapContractTest` khoá) ⇒ bản release/debug không thể có tiếng vọng giả.
 */
object CtlWriteTap {

    /** Người nghe (định nghĩa nút, tham số chính đã gửi); `null` = không cắm (mọi bản phát hành). */
    @Volatile
    var echo: ((ControlDef, Int) -> Unit)? = null
        private set

    fun set(f: ((ControlDef, Int) -> Unit)?) { echo = f }

    /** Gọi từ [ControlWriteFlow.write]; người nghe ném ⇒ nuốt (chẩn đoán máy ảo không được làm hỏng lệnh ghi). */
    fun sent(def: ControlDef, primary: Int) {
        val f = echo ?: return
        runCatching { f(def, primary) }
    }
}
