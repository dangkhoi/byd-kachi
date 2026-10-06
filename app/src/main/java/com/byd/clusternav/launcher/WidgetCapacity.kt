package com.byd.clusternav.launcher

import android.content.Context

/**
 * ═══ 2.93 `WIDGET-CAPACITY-HINT` — bộ chọn widget nói "ô này chỉ vừa N mục còn đọc được chữ" ════════════════════════════
 *
 * Bệnh [ĐO máy ảo QA1–QA3 04/10]: khung 2×1 (301×123 / 301×148 px) không vừa 6 nút có nhãn đọc được ở MỌI tiếng, mà bộ
 * chọn để người dùng đặt cả 6 rồi lưới tự rơi về nhãn ngắn / cắt đầu / chỉ-icon — sức chứa đã có (`GridFit.capacity`) nhưng
 * chỉ nằm trong dòng `WidgetFit … cap=`. R-WF3 (spec 287 §4.5): "khung quá nhỏ thì đổi dạng … HOẶC báo sức chứa".
 *
 * Cách đo (không gõ số dp, không đoán): dựng BẢN NHÁP lưới bằng CHÍNH bộ dựng ô (`WidgetViews.buildGrid` — cùng ô nén,
 * cùng nhãn đã dịch) cho các mục đang chọn, đo ở cỡ khung THẬT của ô (`WorkspaceView` đã đo), lấy kết quả lượt khớp. Bản
 * nháp không bao giờ gắn cửa sổ ⇒ không ô nào đăng ký nghe (đăng ký nằm ở `onAttachedToWindow`), không I/O ảnh. Dữ liệu xe
 * rỗng ("—") ⇒ ô số hẹp hơn lúc có số thật một chút [SUY] — nhãn (thứ quyết sức chứa của ô nút) không đổi.
 */
object WidgetCapacity {   // public: kiểu [Hint] nằm trong chữ ký hàm dựng của `AppDrawer` (lớp public)

    /** [fits] = mọi mục đang chọn vừa với nhãn đọc được + ô ≥ 48 dp; [capacity] = số mục tối đa như thế (0 = không nổi một). */
    data class Hint(val fits: Boolean, val capacity: Int)

    fun of(ctx: Context, ids: List<String>, w: Int, h: Int): Hint? {
        if (ids.size < 2 || w <= 0 || h <= 0) return null
        val grid = WidgetViews.buildGrid(ctx, ids, WidgetData()) as? FitGridLayout ?: return null
        return grid.measureFit(w, h)?.let { (ok, cap) -> Hint(ok, cap) }
    }

    /**
     * Có nói không, và nói số nào: `null` = vừa / không đo được / sức chứa ≥ số đang chọn (không nói câu tự mâu thuẫn);
     * `0` = không nổi một mục; `n` = "chỉ vừa n mục". Thuần — bài `Widget293WiringContractTest` (ca "suc chua o").
     */
    fun say(hint: Hint?, selected: Int): Int? = when {
        hint == null || hint.fits || hint.capacity >= selected -> null
        else -> hint.capacity.coerceAtLeast(0)
    }

    /** Câu cho thanh đáy bộ chọn theo [say]; `null` = không nói gì. */
    fun text(ctx: Context, hint: Hint?, selected: Int): String? = when (val n = say(hint, selected)) {
        null -> null
        0 -> ctx.getString(com.byd.clusternav.R.string.kachi_drawer_fit_none)
        else -> ctx.getString(com.byd.clusternav.R.string.kachi_drawer_fit_hint, n, selected)
    }
}
