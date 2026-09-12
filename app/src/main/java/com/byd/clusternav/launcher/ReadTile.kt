package com.byd.clusternav.launcher

import android.view.View
import android.widget.TextView

// ⚠ [KIỂM TOÁN 2026-09-12] Tách khỏi `ControlTileFactory.kt` vì tệp đó vượt trần 500 dòng sau khi thêm cỡ ô
// [TileSize.GROUP] và nhánh ô HẸP. Đường cắt: đây là **ô đã dựng xong** (một tay cầm để đổ số về sau), còn
// `ControlTileFactory` là **bộ dựng** — hai vai khác nhau, không phải cắt bừa cho vừa số dòng.
//
// ⚠⚠ `CapabilityTileWiringContractTest` soi `fun readTile(` (hàm DỰNG, vẫn ở tệp cũ) nên phép kiểm đó không đổi;
// `ReadTile.bind` thì không bài nào cắt vùng theo tệp, nên tách chỗ này không làm bài canh nào thôi phủ.

/**
 * Một ô ĐỌC đã dựng: [view] để gắn vào vùng, [bind] để đổ/đổi số **mà không dựng lại view**.
 *
 * Chưa đọc được (`null` hoặc [TelemetryView.available] = false) ⇒ dấu gạch ngang + mờ 50% — KHÔNG bịa số
 * (off-car là ca thường, không phải ca lỗi).
 */
class ReadTile internal constructor(
    val view: View,
    private val content: View,
    private val value: TextView,
    private val unit: TextView,
) {
    fun bind(v: TelemetryView?) {
        value.text = v?.display ?: TelemetryView.PLACEHOLDER
        val u = v?.unit ?: ""
        unit.text = u
        unit.visibility = if (u.isEmpty()) View.GONE else View.VISIBLE
        // ⚠⚠ [KIỂM TOÁN UX mục 2] Làm mờ **GIÁ TRỊ**, KHÔNG làm mờ cả ô.
        //
        // `content.alpha = 0.5f` kéo cả **nhãn** xuống theo, và off-car là ca THƯỜNG (mọi field null) nên hậu quả là
        // người dùng không đọc được ô đó đang là cái gì — đúng lỗi đã đo ở ô con của nhóm (2.33:1). Nhãn trả lời
        // *"ô này là cái gì"*, câu đó không phụ thuộc việc xe đã trả số hay chưa.
        content.alpha = 1f
        val dim = if (v?.available == true) 1f else DIM
        value.alpha = dim
        unit.alpha = dim
    }

    private companion object {
        /** Độ mờ của số chưa đọc được — cùng giá trị bản cũ dùng cho cả ô, nên dấu gạch trông y như trước. */
        const val DIM = 0.5f
    }
}
