package com.byd.clusternav.launcher

import android.widget.TextView

/**
 * 2.93 `WIDGET-CAPACITY-HINT` — câu sức chứa ở thanh đáy bộ chọn widget (chế độ GÁN Ô). Tách khỏi `AppDrawer.kt` (trần 500
 * dòng); hàm mở rộng `internal` cùng package như `AppDrawerTiles.kt`.
 *
 * Đo bằng bản nháp lưới ([WidgetCapacity]) — vài chục ms trên luồng chính ⇒ HOÃN [DELAY_MS] sau cú chạm cuối (ô vừa chạm tô
 * sáng ngay, câu tới sau) và bỏ nếu lựa chọn đã đổi. Không đè câu đang hiện (câu nhắc trần / câu cảnh báo vừa từ chối):
 * câu sức chứa là THÔNG TIN, cùng dáng mực mờ của câu nhắc trần. Chỉ chạy khi chỗ gọi đưa [AppDrawer.fitOf] (gán ô widget).
 */
internal fun AppDrawer.scheduleFitHint(v: TextView) {
    val probe = fitOf ?: return
    (v.tag as? Runnable)?.let { v.removeCallbacks(it) }
    val ids = selected.toList()
    if (ids.size < 2) return
    val run = Runnable {
        // Soát senior 2.93 Pass 1 [P3]: bộ chọn đã đóng trong 180 ms ⇒ không dựng bản nháp lưới (vài chục ms luồng chính) cho ai.
        if (!v.isAttachedToWindow || ids != selected.toList()) return@Runnable
        val text = WidgetCapacity.text(context, probe(ids), ids.size) ?: return@Runnable
        if (v.text.isNullOrEmpty()) v.text = text
    }
    v.tag = run
    v.postDelayed(run, DELAY_MS)
}

/** Hoãn đo sức chứa sau cú chạm cuối (ms) — đủ để chạm liên tiếp không đo mỗi lần, chưa đủ để thấy trễ. */
private const val DELAY_MS = 180L
