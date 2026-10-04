package com.byd.clusternav.voicekey

import com.byd.clusternav.launcher.HalGateway
import java.util.concurrent.ExecutorService

/**
 * Kết quả tra nguồn cho MỘT lần nhấn, đúng thứ [VoiceKeyMatcher] cần để chọn dòng gán (R3) và ghi nhật ký (R6).
 *
 * @property kind nút vật lý; `null` = KHÔNG BIẾT (đọc hụt / quá trần / bận / giá trị lạ / phím không đo) ⇒ matcher chỉ
 *   được dùng dòng không nguồn.
 * @property reason chuỗi ASCII ngắn cho dòng log `voice-key … src=<reason>`: `knob` · `wheel` · `?(timeout)` · `?(busy)` ·
 *   `?(value=7)` · `?(read_error:SecurityException)` · `?(not_measured)` · `?(no_lookup)`.
 * @property reading số đọc thô (nếu có lượt tra) — nhật ký tầng 1 dùng lại nó, không đọc HAL lần hai (R-nf2).
 */
data class KeySourceLookup(val kind: KeySourceKind?, val reason: String, val reading: KeySourceReading? = null) {
    companion object {
        /** Không có cách tra nguồn (chỗ gọi không cấp hàm tra) ⇒ không biết nguồn. */
        val NO_LOOKUP = KeySourceLookup(kind = null, reason = unknown("no_lookup"))

        /** Dịch một số đọc sang kết luận cho matcher — CÙNG [KeySourceProbes.verdict] mà hộp Học phím hiện. */
        fun of(reading: KeySourceReading): KeySourceLookup = when (val v = KeySourceProbes.verdict(reading)) {
            is KeySourceVerdict.Source -> KeySourceLookup(v.kind, v.kind.code, reading)
            is KeySourceVerdict.UnknownValue -> KeySourceLookup(null, unknown("value=${v.value}"), reading)
            is KeySourceVerdict.Failed -> KeySourceLookup(null, unknown(KeySourceLog.reason(reading).ifEmpty { v.failure.code }), reading)
            KeySourceVerdict.NotMeasured -> KeySourceLookup(null, unknown("not_measured"), reading)
            // `verdict(null)` mới ra Pending; ở đây reading luôn khác null — giữ nhánh cho `when` đủ và an toàn.
            KeySourceVerdict.Pending -> KeySourceLookup(null, unknown("pending"), reading)
        }

        private fun unknown(why: String) = "?($why)"
    }
}

/**
 * ═══ 2.88 · KEY-SOURCE-SPLIT tầng 2 — TRA NGUỒN ĐỒNG BỘ cho luồng nhận phím (spec `kachi-288-key-source-split` §4.3) ═══
 *
 * Quyết định nuốt/để đi phải có NGAY trong `onKeyEvent` ([ĐO nguồn r47] main looper, framework chờ tối đa 500 ms —
 * `KeyEventDispatcher.java:51`), nên đường gán đọc `AUDIO_VOLUME_CTRL_MODE` đồng bộ. Luật đọc DÙNG LẠI [KeySourceMeter]
 * (không chép lại): bận ⇒ `BUSY` ngay không chồng lượt, trần tính từ lúc GỬI, mọi lỗi thành [KeySourceFailure] — chỉ đổi
 * trần thành [SYNC_BUDGET_MS] (bản sao đầu dò, bảng gốc giữ trần 250 ms của nhật ký).
 *
 * ## Vì sao 100 ms
 * [ĐO xe owner 04/10] một lượt đọc 1–3 ms ⇒ 100 ms ≈ 30× mức đo, ≪ 500 ms của framework (R-nf1). Xấu nhất (HAL treo):
 * MỘT lần nhấn chờ 100 ms trên main, các lần sau ra `busy` ngay cho tới khi lượt treo tự xong.
 *
 * ## Luồng
 * [lookup] và [prime] chỉ được gọi từ MỘT luồng — luồng nhận phím (main) của `NavAccessibilityService` (`onKeyEvent` /
 * `onServiceConnected`). Lượt đọc thật chạy trên [exec] — luồng HAL RIÊNG của đường gán (`kachi-keysrc-sync`), không xếp
 * hàng sau lượt đo của nhật ký tầng 1 (R-nf1). Chỉ ĐỌC HAL (R-nf4).
 *
 * @param exec bộ thi hành HAL một-luồng của đường gán — `null` = đã dừng ⇒ `not_running`.
 * @param clockMs cùng gốc với [KeySample.eventTime] (`SystemClock.uptimeMillis` ở `:app`).
 */
class KeySourceResolver(
    gateway: () -> HalGateway,
    exec: () -> ExecutorService?,
    clockMs: () -> Long,
    probes: List<KeySourceProbeSpec> = KeySourceProbes.ALL,
    budgetMs: Long = SYNC_BUDGET_MS,
) {
    private val meter = KeySourceMeter(gateway, exec, clockMs, probes.map { it.copy(budgetMs = budgetMs) })

    /** Tra nguồn của lần nhấn [sample] — chặn tối đa trần đã cấu hình, không bao giờ ném vì lỗi HAL. */
    fun lookup(sample: KeySample): KeySourceLookup = KeySourceLookup.of(meter.read(sample))

    /** Đọc mồi một lượt (không chờ) — xem [KeySourceMeter.prime]. `true` ⇔ đã gửi. */
    fun prime(): Boolean = meter.prime()

    companion object {
        /** Trần một lượt đọc đồng bộ trên luồng nhận phím (R-nf1). */
        const val SYNC_BUDGET_MS = 100L
    }
}
