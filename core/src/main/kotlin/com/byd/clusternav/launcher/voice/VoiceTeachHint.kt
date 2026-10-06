package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings

/**
 * ═══ 2.91 VOICE-APP-NAMES · C9 — GỢI Ý *"Dạy tên «…»"* sau một câu *"mở …"* không hiểu ═══════════════════════
 *
 * Spec §4.3 lối (c) · §4.10 · OQ5. Điều kiện hiện (thuần, kiểm off-car): ý định CUỐI là [VoiceIntent.Unknown] lý do
 * [VoiceUnknownReason.NO_OBJECT] / [VoiceUnknownReason.MISMATCH], câu mở đầu bằng động từ MỞ/BẬT, phần tên qua được
 * [TeachSample.normalize] (1–4 từ, ≥ [TeachSample.MIN_LETTERS] chữ cái — tên 3 chữ cái vẫn gợi ý: mẫu chờ là lượt 1,
 * hộp dạy đòi thêm một lượt giống hệt, spec §7 OQ4). Trả mẫu đang chờ (dạng có dấu) hoặc `null`.
 */
object VoiceTeachHint {

    /** Nút trên tấm chữ đứng bao lâu (OQ5: luôn hiện 8 s). */
    const val BUTTON_MS = 8_000L

    /** Câu ĐỌC chỉ đọc mấy lần đầu (OQ5). Đếm ở tầng `:app` — xem nhật ký triển khai spec về phạm vi đếm. */
    const val SPOKEN_TIMES = 3

    fun pendingOf(intents: List<VoiceIntent>, heard: String): String? {
        val last = intents.lastOrNull() as? VoiceIntent.Unknown ?: return null
        if (last.reason != VoiceUnknownReason.NO_OBJECT && last.reason != VoiceUnknownReason.MISMATCH) return null
        val t = VoiceLexicon.dropLeadingFillers(VoiceLexicon.stripCourtesy(VoiceLexicon.tokenize(heard)))
        val opens = VoiceGrammar.VERBS.any { (w, v) -> (v == VoiceVerb.OPEN || v == VoiceVerb.ON) && VoiceLexicon.phraseAt(t, 0, w) }
        if (!opens) return null
        return (TeachSample.normalize(heard) as? TeachSample.Sample)?.accented
    }

    /**
     * Tham số của [VoiceHomeAction.TEACH_APP] — hai lối vào, một hành động: mẫu đang chờ (lối c, *"Dạy tên «…»"* trên
     * tấm chữ) hoặc một gói (lối b, nhấn giữ icon ở ngăn kéo). Mã hoá tường minh để Activity không phải đoán.
     */
    data class Request(val sample: String? = null, val pkg: String? = null)

    private const val P_SAMPLE = "s:"
    private const val P_PKG = "p:"

    fun encode(r: Request): String = r.pkg?.let { P_PKG + it } ?: (P_SAMPLE + TaughtNamesCodec.cleanAccented(r.sample.orEmpty()))

    /**
     * Tham số lạ/rỗng ⇒ yêu cầu RỖNG (mở trang, không mẫu, không gói) — không đoán.
     *
     * ⚠ Activity HOME nhận intent của MỌI app trên xe (exported) ⇒ tham số là dữ liệu NGOÀI: mẫu phải qua đúng luật hình
     * dạng của một tên ([TeachSample.shape]: 1–4 từ, ≥ 3 chữ cái, ≤ [TeachSample.MAX_CHARS] ký tự) mới được hiện thành
     * *"Mẫu đang chờ"*; sai hình dạng ⇒ bỏ mẫu. Không có đường nào lưu tên từ đây: lưu vẫn cần người dùng chọn app,
     * nói thêm ≥ 1 lượt, qua cổng an toàn + máy dò hồi quy rồi bấm Lưu.
     */
    fun decode(arg: String?): Request = when {
        arg == null -> Request()
        arg.startsWith(P_PKG) -> Request(pkg = arg.removePrefix(P_PKG).trim().takeIf { TaughtNamesCodec.validPackage(it) })
        arg.startsWith(P_SAMPLE) -> Request(sample = (TeachSample.shape(arg.removePrefix(P_SAMPLE)) as? TeachSample.Sample)?.accented)
        else -> Request()
    }

    /** Đuôi đọc thêm vào câu *"không tìm thấy"* (chỉ [SPOKEN_TIMES] lần đầu). */
    fun spokenTail(lang: Lang): String =
        Strings.t(
            "Nếu đó là tên app, hãy dạy Kachi trong Cài đặt, Giọng nói",
            "If that is an app name, teach Kachi in Settings, Voice",
            lang,
        )
}
