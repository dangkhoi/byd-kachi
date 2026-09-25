package com.byd.clusternav.launcher.voice

/**
 * ═══ H6 (PERF 2026-09-16) — CÓ nên nạp sẵn mô hình nghe không, quyết bằng RAM CÒN LẠI ════════════════════════
 *
 * [ĐO xe 2026-09-16]: đầu xe DL3 còn **56–94 MB** trống, `memFactor=1`, trong khi RSS của Kachi đã là **537 MB**
 * (native heap 477 MB = mô hình fp32 266 MB + onnxruntime). Nạp sẵn (`VoiceRecognizer.preload`) nạp **thêm** cả
 * mô hình vào lúc launcher vừa dựng xong màn chính — tức đúng lúc máy đang căng nhất, cho một tính năng người
 * dùng **có thể không bấm tới** trong cả chuyến đi.
 *
 * Phép đổi chác của nạp-sẵn: bỏ ra RAM ngay để lần bấm mic ĐẦU không phải chờ ~15 s. Nó đáng khi còn RAM, và
 * **phản tác dụng** khi không: hệ thống phải đẩy thứ khác ra (kể cả app dẫn đường đang chạy), rồi có khi chính
 * mô hình vừa nạp bị thu hồi — trả tiền mà không mua được gì.
 *
 * ## Vì sao NGƯỠNG tính theo cỡ mô hình, không phải một con số cố định
 * Dự án có hai gói: fp32 ≈266 MB và int8 ≈74 MB. Một ngưỡng cứng kiểu *"còn <200 MB thì thôi"* sẽ **chặn nhầm**
 * gói int8 (74 MB nạp thoải mái trong 150 MB trống) và **cho qua nhầm** gói fp32 trên một máy còn đúng 210 MB.
 * Hỏi đúng câu: *còn đủ chỗ cho CHÍNH gói này cộng một khoảng thở không?*
 *
 * Đây là quyết định **hoãn**, không phải quyết định **tắt**: từ chối nạp sẵn thì lần bấm mic đầu vẫn nạp bình
 * thường (đường cũ của 1.65). Không có tính năng nào biến mất — chỉ có một lượt nạp không xảy ra khi nó gây hại.
 *
 * THUẦN ⇒ test off-device; số RAM do `:app` đọc từ `ActivityManager.MemoryInfo`.
 */
object VoicePreloadPolicy {

    /**
     * Có nên nạp sẵn không.
     *
     * @param availMemBytes `ActivityManager.MemoryInfo.availMem`.
     * @param lowMemory `ActivityManager.MemoryInfo.lowMemory` — hệ thống đã tự nhận là đang thiếu.
     * @param modelBytes cỡ gói đang chọn ([VoicePack.totalBytes]); `0` = chưa biết ⇒ KHÔNG chặn (fail-open, cùng
     *   luật với [com.byd.clusternav.launcher.CarDataDemand]: thiếu số liệu thì giữ hành vi cũ, đừng tự tắt).
     */
    fun shouldPreload(availMemBytes: Long, lowMemory: Boolean, modelBytes: Long): Boolean {
        if (lowMemory) return false
        if (modelBytes <= 0) return true
        return availMemBytes >= modelBytes + headroomBytes(modelBytes)
    }

    /**
     * ═══ 2026-09-25 · wake — MỘT mô hình cho cả máy: tiến trình CHÍNH có nên nạp sẵn không? ═══════════════
     *
     * [SUY, audit RAM 2026-09-25 §1.1] Khi "Hey Kachi" BẬT, `:wake` **cũng** nạp đúng mô hình int8 74 MB này
     * (`VoiceWakeAsr` → `VoiceEngine.recognizer` của tiến trình `:wake`) và phiên lệnh R7/LISTEN_NOW chạy ở
     * đó ⇒ nạp sẵn ở chính là **bản thứ hai** (≈ 85–110 MB) trên một đầu xe còn 56–94 MB trống. Wake TẮT ⇒ chính là
     * nơi duy nhất nghe ⇒ giữ nạp sẵn (spec V3 R4: 15 s lần bấm đầu).
     *
     * Đây vẫn là quyết định **hoãn**: wake BẬT mà người lái bấm nút mic trên màn chính thì lần đầu vẫn nạp như cũ.
     */
    fun shouldPreloadInMain(wakeEnabled: Boolean): Boolean = !wakeEnabled

    /** Câu GIẢI THÍCH cho log (owner đọc log trên xe) — nói rõ vì sao bỏ qua, không im lặng. */
    fun reason(availMemBytes: Long, lowMemory: Boolean, modelBytes: Long): String = when {
        lowMemory -> "hệ thống báo thiếu bộ nhớ (lowMemory=true)"
        modelBytes <= 0 -> "chưa biết cỡ mô hình ⇒ nạp như cũ"
        else -> "còn ${availMemBytes / MB} MB, cần ${(modelBytes + headroomBytes(modelBytes)) / MB} MB " +
            "(mô hình ${modelBytes / MB} MB + thở ${headroomBytes(modelBytes) / MB} MB)"
    }

    /** Lý do bỏ nạp sẵn khi [shouldPreloadInMain] = false — hiện ở ghi chú Cài đặt (`VoiceEngine.lastPreloadSkip`). */
    const val REASON_WAKE_OWNS_MODEL = "\"Hey Kachi\" đang bật ⇒ mô hình sống ở tiến trình nghe câu gọi, không nạp bản thứ hai"

    private const val MB = 1024L * 1024L

    /**
     * Khoảng thở cho phần còn lại của hệ thống sau khi mô hình đã vào RAM.
     *
     * 96 MB: [ĐO xe 2026-09-16] máy chạy ở 56–94 MB trống với `memFactor=1` — tức dải mà LMK bắt đầu cân nhắc
     * giết tiến trình nền. Chừa đúng bằng dải ấy để lượt nạp sẵn KHÔNG phải là thứ đẩy máy vào dải đó.
     */
    const val HEADROOM_BYTES = 96L * 1024L * 1024L

    /**
     * Khoảng thở THEO CỠ GÓI = max([HEADROOM_BYTES], 1,5 × gói).
     *
     * [SUY, audit RAM 2026-09-25 §4.2, ORT 1.28.2 `session_state.cc:1591,1750,667-673`] lúc nạp, ORT giữ đồng thời
     * `ModelProto` (≈ cỡ tệp) + bản OrtValue chép ra, rồi mới giải phóng ⇒ **đỉnh transient ≈ +71…+126 MB** trên
     * mức ổn định với gói int8 74 MB. Sàn 96 MB nhỏ hơn đỉnh ấy ⇒ policy cho qua mà lượt nạp vẫn chạm LMK. 1,5 ×
     * gói phủ đỉnh cho int8 (111 MB) lẫn fp32 (399 MB); gói nhỏ vẫn được sàn 96 MB.
     */
    fun headroomBytes(modelBytes: Long): Long = maxOf(HEADROOM_BYTES, modelBytes.coerceAtLeast(0) * 3 / 2)
}
