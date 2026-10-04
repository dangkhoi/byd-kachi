package com.byd.clusternav.launcher

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c

/**
 * ═══ MÀU CHỮ + ICON của một chip thanh trên theo SẮC THÁI — MỘT chỗ khai ═══════════════════════════════════════
 *
 * Chỗ đọc: [KachiTopStrip] (vẽ — chữ lẫn icon, `applyChipFace` tint icon bằng CHÍNH màu này). Tách khỏi
 * `KachiTopStrip.refreshChips` ở soát 2.87 để bộ giải độ đục nền (R-OP3) đọc CÙNG bảng; 2.88 gỡ bộ giải đó (owner 04/10:
 * độ trong suốt áp đúng số người chọn) nhưng một bảng map đứng riêng, vét cạn, vẫn là chỗ khai đúng. Bảng màu chỉ ở
 * `:app` — `:core` chỉ nói SẮC THÁI ([ChipTone]).
 *
 * `when` VÉT CẠN, không `else` (`TopStripWiringContractTest`): sắc thái mới mà quên map là lỗi biên dịch, không phải
 * chip xám im lặng.
 */
internal fun chipInk(tone: ChipTone): String = when (tone) {
    ChipTone.ENERGY -> KachiTheme.GREEN
    // Trạng thái bật/tắt của datum boolean = MÀU, không phải chữ (owner 2026-09-21). Cả chữ lẫn icon đổi
    // màu vì `applyChipFace` tint icon bằng CHÍNH màu này — đó là thứ làm "icon sáng / icon mờ".
    //
    // Vì sao hai vai này: [KachiTheme.ACCENT_INK] là vai *"màu nhấn dùng làm CHỮ"* — [ĐO] `accent` thuần
    // (`#4c7dff`) làm chữ thì không đạt tương phản, nên bảng màu đã tách riêng vai này và cho nó đi qua
    // `ContrastGuard.fitInk`. [KachiTheme.MUT2] là vai chữ mờ nhất còn đạt sàn tương phản. Dùng lại hai
    // vai có sẵn thay vì thêm vai mới: "mờ" và "nhấn" đã được định nghĩa và đã được bài canh tương phản
    // đo ở CẢ HAI bảng (tối + sáng) — thêm vai mới là thêm hai hex phải tự chứng minh lại.
    ChipTone.ACTIVE -> KachiTheme.ACCENT_INK
    ChipTone.INACTIVE -> KachiTheme.MUT2
    ChipTone.NEUTRAL -> KachiTheme.INK2   // chip trung tính
    // 2.88 · chip lốp: xe báo VÀNG / ĐỎ cho một bánh. Hai vai đã có của bảng màu (theo ngày/đêm, cùng hai vai mà bảng
    // lốp dùng cho bánh cảnh báo) — không thêm hex mới. Bánh bình thường dùng [ChipTone.ENERGY] (xanh của chip pin).
    ChipTone.WARN -> KachiTheme.AMBER
    ChipTone.ALERT -> KachiTheme.RED
}

/**
 * ═══ 2.88 · CHỮ CHIP CÓ ĐOẠN MÀU — mỗi con số của chip lốp một màu ([ChipView.runs]) ═══════════════════════════
 *
 * Không có đoạn nào ⇒ trả CHÍNH chuỗi thường: mọi chip cũ đi đúng đường cũ, không một `Spannable` nào. Có đoạn ⇒ mỗi
 * [ChipRun] một `ForegroundColorSpan` mang màu của sắc thái ấy (cùng bảng [chipInk]); phần ngoài đoạn (nhãn `"Lốp · "`,
 * khe) giữ màu chữ của cả chip (`applyChipFace`). Đoạn hỏng (ngoài chuỗi / rỗng) bị bỏ, không ném.
 */
internal fun chipText(chip: ChipView): CharSequence {
    if (chip.runs.isEmpty()) return chip.text
    val out = SpannableString(chip.text)
    chip.runs.forEach { r ->
        if (r.start < 0 || r.end > chip.text.length || r.start >= r.end) return@forEach
        out.setSpan(ForegroundColorSpan(c(chipInk(r.tone))), r.start, r.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return out
}

/**
 * Khoá *"chữ đang hiện"* của chip — gồm cả MÀU ĐÃ PHÂN GIẢI của từng đoạn (R6): một bánh đổi xanh → đỏ mà con số
 * đứng yên vẫn phải vẽ lại, và đổi giao diện ngày/đêm (hex của [KachiTheme.GREEN]… đổi) cũng thế. Không có đoạn ⇒
 * khoá = chuỗi, đúng phép so của bản trước 2.88.
 */
internal fun chipTextKey(chip: ChipView): String =
    if (chip.runs.isEmpty()) chip.text
    else chip.text + chip.runs.joinToString("") { "|${it.start}-${it.end}:${chipInk(it.tone)}" }

/**
 * Đặt chữ cho [v] chỉ khi [chipTextKey] đổi — giữ luật H5 (PERF 2026-09-16: `setText` cùng chuỗi vẫn dựng lại
 * `Layout` + `requestLayout()`), nay so bằng khoá có màu thay vì chỉ so chuỗi.
 */
internal fun applyChipText(v: TextView, chip: ChipView) {
    val key = chipTextKey(chip)
    if (v.getTag(R.id.kachi_chip_text_key) == key) return
    v.text = chipText(chip)
    v.setTag(R.id.kachi_chip_text_key, key)
}
