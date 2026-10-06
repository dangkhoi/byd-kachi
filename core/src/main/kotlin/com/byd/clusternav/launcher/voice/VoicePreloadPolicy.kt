package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings

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
     * đó ⇒ nạp sẵn ở chính là **bản thứ hai** (≈ 85–110 MB) trên một đầu xe còn 56–94 MB trống.
     *
     * ⚠ [FIX286 · VK7 — sửa KDoc sai, ĐO mã 02/10] Bản 2.66 viết tiếp *"Wake TẮT ⇒ chính là nơi duy nhất nghe ⇒ giữ
     * nạp sẵn"* — sai NGAY khi viết: từ 2.62 phím vô-lăng gán Kachi nghe mở phiên ở `:wake` bất kể wake, nên người
     * dùng phím (wake TẮT, mặc định) có mô hình nạp sẵn ở tiến trình chính mà phím thì nạp nguội ở `:wake` mỗi lần bấm.
     * Từ 2.86 tham số là [VoiceWakeMode.modelInWake] (`wakeEnabled ∨ keyHold`): mô hình ở `:wake` (WAKE/HOLD) ⇒ chính
     * KHÔNG nạp sẵn; không (OFF) ⇒ giữ nạp sẵn như V3 R4 (15 s lần bấm đầu) — tiến trình chính khi ấy là nơi nghe của
     * nút mic màn.
     *
     * Đây vẫn là quyết định **hoãn**: mô hình ở `:wake` mà nút mic màn lùi in-process (`:wake` không ack) thì lần ấy
     * vẫn nạp như cũ.
     */
    fun shouldPreloadInMain(modelInWake: Boolean): Boolean = !modelInWake

    /**
     * ═══ [Senior review FIX286 Pass 2 · P2] — chiều NGƯỢC của [shouldPreloadInMain]: tiến trình chính có nên TRẢ bản
     * mô hình nó đang giữ không ═══
     *
     * [ĐO mã 03/10] [shouldPreloadInMain] chỉ quyết lúc NẠP. Chế độ chuyển OFF → HOLD/WAKE **giữa đời tiến trình** (gán
     * phím Kachi nghe · bật "Nhận nút vật lý" · bật "Hey Kachi" · đổi sang hồ sơ có phím) thì bản đã nạp sẵn ở chính
     * nằm lại, còn `:wake` nạp bản của nó ⇒ HAI bản (≈ 2 × 85–110 MB) tới lần BYD giết Kachi khi tắt máy — trên đầu xe
     * còn 56–94 MB trống, đúng thứ VK1 hứa tránh ("một bản mô hình cho cả máy"). Chiều HOLD/WAKE → OFF đã có (D-VK4).
     *
     * Nhả khi mô hình ĐÃ ở `:wake` **và** không có phiên nghe nào đang chạy trong tiến trình chính — nhả dưới chân một
     * phiên đang mở là giải mã trả rỗng ⇒ người lái nói xong nhận *"không nghe thấy"* (KDoc `ModelHolder`). Bận ⇒ không
     * nhả lượt này; hỏi lại theo [shouldRetryHandOver].
     */
    fun shouldHandOverToWake(modelInWake: Boolean, sessionRunning: Boolean): Boolean = modelInWake && !sessionRunning

    /**
     * ═══ [Senior review FIX286 Pass 3 · P2] — lượt trả bản KHÔNG xong thì có hỏi lại không ═══
     *
     * [ĐO mã 03/10] Bản Pass 2 thử MỘT lần rồi thôi (*"`sync` ở lượt `onResume` kế hỏi lại"*), để lọt hai ca thật:
     *  (a) chế độ đổi đúng lúc tiến trình chính ĐANG NẠP (nạp sẵn 9–34 s trên xe, hoặc phiên in-process đang dựng) — cổng
     *      vào chỉ xét "đã nạp" nên thoát ngay, bản vừa nạp xong nằm lại;
     *  (b) nói *"đổi sang hồ sơ <có phím>"* từ phiên in-process của chính (chế độ OFF) ⇒ lượt trả bản chạy GIỮA phiên ⇒
     *      [ModelHolder.Release.SKIPPED]. Tấm chữ là overlay, Activity không pause ⇒ không `onResume` nào tới khi người
     *      lái rời rồi quay lại màn chính — cả chuyến ngồi trong app dẫn đường là hai bản cả chuyến.
     *
     * Hỏi lại khi lượt vừa rồi BẬN (đang nạp/giải mã) hoặc BỎ (phiên đang chạy), chế độ VẪN ở `:wake` (đổi ngược về OFF
     * ⇒ thôi, chính giữ bản của nó), và chưa quá [maxWaitMs] — cùng trần [VoiceWakeStandDown.MAX_WAIT_MS]: một lượt nạp
     * và một phiên nghe luôn kết thúc trước đó. RELEASED/EMPTY ⇒ xong.
     */
    fun shouldRetryHandOver(
        last: ModelHolder.Release,
        modelInWake: Boolean,
        waitedMs: Long,
        maxWaitMs: Long = VoiceWakeStandDown.MAX_WAIT_MS,
    ): Boolean = (last == ModelHolder.Release.BUSY || last == ModelHolder.Release.SKIPPED) && modelInWake && waitedMs < maxWaitMs

    /**
     * MÃ lý do của quyết định RAM ([shouldPreload]) — kèm số MB để câu dịch điền vào. Câu theo ngôn ngữ ở [PreloadSkip.text].
     *
     * QA 2.87 [P2] — trước đây trả thẳng một câu tiếng Việt và câu ấy được nhét vào ghi chú Cài đặt đã dịch
     * (`kachi_voice_model_preload_skipped`) ⇒ máy EN/ZH/TH/MS thấy nửa câu tiếng Việt. Nay ghi chú giữ MÃ, dịch lúc HIỆN.
     */
    fun skip(availMemBytes: Long, lowMemory: Boolean, modelBytes: Long): PreloadSkip = when {
        lowMemory -> PreloadSkip(PreloadSkip.Code.LOW_MEMORY)
        modelBytes <= 0 -> PreloadSkip(PreloadSkip.Code.UNKNOWN_SIZE)
        else -> PreloadSkip(
            PreloadSkip.Code.NOT_ENOUGH_RAM,
            availMb = availMemBytes / MB,
            needMb = (modelBytes + headroomBytes(modelBytes)) / MB,
            modelMb = modelBytes / MB,
            headroomMb = headroomBytes(modelBytes) / MB,
        )
    }

    /** Lý do bỏ nạp sẵn khi [shouldPreloadInMain] = false — hiện ở ghi chú Cài đặt (`VoiceEngine.lastPreloadSkip`). */
    val WAKE_OWNS_MODEL = PreloadSkip(PreloadSkip.Code.WAKE_OWNS_MODEL)

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

/**
 * ═══ QA 2.87 [P2] — lý do bỏ nạp sẵn là MỘT MÃ, câu dịch lúc HIỆN ═══════════════════════════════════════════════════
 *
 * [ĐO máy ảo QA 2.87 `{en,zh,th,ms} · g09-p00.xml` (bằng chứng phiên, ngoài repo)] ghi chú *"Lần khởi động vừa rồi Kachi bỏ qua bước nạp sẵn mô hình (%1$s)…"*
 * đã dịch đủ 5 tiếng, nhưng `%1$s` là một câu tiếng Việt viết cứng ở `:core` ⇒ máy tiếng Anh/Trung/Thái/Mã Lai thấy nửa câu tiếng
 * Việt — đúng ở cấu hình thường ngày của owner ("Hey Kachi" bật / phím vô-lăng gán Kachi nghe). Nay `VoiceEngine.lastPreloadSkip`
 * giữ MÃ + số; [text] chọn câu theo ngôn ngữ ĐANG HIỆN (đổi ngôn ngữ sau lượt bỏ qua vẫn ra đúng tiếng). Bản tiếng Việt giữ đúng
 * từng byte của câu cũ (log trên xe đọc như trước — `VoicePreloadPolicyTest`).
 */
data class PreloadSkip(
    val code: Code,
    val availMb: Long = 0,
    val needMb: Long = 0,
    val modelMb: Long = 0,
    val headroomMb: Long = 0,
) {
    enum class Code {
        /** Mô hình ở `:wake` ("Hey Kachi" BẬT hoặc phím vô-lăng gán Kachi nghe) — [VoicePreloadPolicy.shouldPreloadInMain]. */
        WAKE_OWNS_MODEL,

        /** `ActivityManager.MemoryInfo.lowMemory` = true. */
        LOW_MEMORY,

        /** Chưa biết cỡ gói ⇒ nạp như cũ (fail-open — [VoicePreloadPolicy.shouldPreload] không chặn ca này). */
        UNKNOWN_SIZE,

        /** Còn ít RAM hơn cỡ gói + khoảng thở. */
        NOT_ENOUGH_RAM,
    }

    /**
     * Câu theo [lang] — VI/EN viết ở đây, ZH/TH/MS tra bảng dịch theo cặp (vi, en). MỘT nguồn cho cả hai nơi hiện: ghi chú Cài
     * đặt (ngôn ngữ đang hiện) và dòng log `nạp sẵn: BỎ QUA — …` của `VoiceRecognizer.preload` (luôn `text(Lang.VI)` — owner đọc
     * log trên xe; byte như câu cũ, `VoicePreloadPolicyTest`). Soát vòng 4 [P3]: bỏ `VoicePreloadPolicy.reason()` — chỉ còn test
     * gọi, KDoc của nó nói là câu log trong khi log gọi thẳng hàm này (hai đường tới cùng một câu).
     */
    fun text(lang: Lang = Strings.current): String = when (code) {
        Code.WAKE_OWNS_MODEL -> Strings.t(
            "\"Hey Kachi\" đang bật hoặc phím vô-lăng gán Kachi nghe ⇒ mô hình sống ở tiến trình :wake, không nạp bản thứ hai",
            "\"Hey Kachi\" is on or a steering-wheel key is set to Kachi listening ⇒ the model lives in the :wake process, no second copy is loaded",
            lang,
        )
        Code.LOW_MEMORY -> Strings.t("hệ thống báo thiếu bộ nhớ (lowMemory=true)", "the system reports low memory (lowMemory=true)", lang)
        Code.UNKNOWN_SIZE -> Strings.t("chưa biết cỡ mô hình ⇒ nạp như cũ", "model size unknown ⇒ loads as before", lang)
        Code.NOT_ENOUGH_RAM -> Strings.fIn(
            lang,
            "còn {0} MB, cần {1} MB (mô hình {2} MB + thở {3} MB)",
            "{0} MB free, {1} MB needed (model {2} MB + headroom {3} MB)",
            availMb, needMb, modelMb, headroomMb,
        )
    }
}
