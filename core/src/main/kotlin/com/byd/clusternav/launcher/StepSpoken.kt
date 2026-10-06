package com.byd.clusternav.launcher

/**
 * ═══ 2.93 `A11Y-STEPPER-NAME` — chữ TRỢ NĂNG của ô bước (gió · nhiệt · âm lượng…) — thuần, `:core` ═══════════════════════
 *
 * Bệnh [ĐO máy ảo QA4 04/10]: nút −/+ của ô bước (widget dạng chỉ-icon/dọc và thanh nút xe) không có tên cho trợ năng —
 * TalkBack chỉ đọc `4` (chữ giá trị) và `−`/`+` (glyph), không nói đang chỉnh CÁI GÌ; ở dạng chỉ-icon nhãn tên đã ẩn hẳn.
 *
 * Mỗi lần ô vẽ một giá trị, ba view mang chữ đầy đủ "tên + giá trị" (spec `docs/specs/kachi-293-widget.html` R-W14):
 *  - chữ giá trị: tên trước, rồi giá trị — cùng luật [FitValues.spoken] (`Gió 4`);
 *  - nút −: `Giảm Gió (4)` · nút +: `Tăng Gió (4)` — tiền tố DÙNG LẠI cặp đã dịch của câu nói giọng
 *    (`VoiceReplyPreview`: "Tăng "/"Giảm " — zh 调高/调低, th เพิ่ม/ลด, ms Naikkan/Turunkan), không thêm chữ mới cho người dịch.
 */
object StepSpoken {

    /** Ba câu trợ năng của một ô bước ở giá trị [value] (chữ ĐANG hiện, vd `22°`, `AUTO`). */
    data class Words(val value: String, val down: String, val up: String)

    fun of(name: String, value: String, lang: Lang = Strings.current): Words = Words(
        value = FitValues.spoken(value, emptyList(), listOf(name)),
        down = Strings.t("Giảm ", "Decrease ", lang) + "$name ($value)",
        up = Strings.t("Tăng ", "Increase ", lang) + "$name ($value)",
    )
}
