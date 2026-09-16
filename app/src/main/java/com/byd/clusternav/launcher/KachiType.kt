package com.byd.clusternav.launcher

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView

/**
 * ═══ THANG CỠ CHỮ (type scale) ══════════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-design-system.html` §3.1. **Một nguồn duy nhất** cho cỡ chữ của tầng giao diện launcher,
 * cùng vai với [KachiSpace] (khoảng cách) và [KachiTheme] (màu).
 *
 * ## Vì sao tệp này tồn tại — [ĐO] hiện trạng
 * **18 cỡ chữ khác nhau** viết tay bằng `setTextSize(COMPLEX_UNIT_SP, …)` rải rác toàn gói: 9.5 · 10 · 10.5 ·
 * 11.5 · 12 · 12.5 · 13 · 13.5 · 14 · 14.5 · 15 · 16 · 17 · 19 · 20 · 32 … Không có bậc "tiêu đề / nội dung /
 * chú thích" nào, nên chữ chỗ to chỗ nhỏ không theo nhịp — đúng cái owner gọi là *"chữ lộn xộn"*. Cùng bệnh, cùng
 * cách chữa như [KachiSpace] đã làm cho dp.
 *
 * ## Năm bậc — chọn theo VAI TRÒ, không theo số gần nhất
 * Ánh xạ đúng là *vai trò → bậc*, không phải *cỡ cũ → cỡ gần nhất*. Một nhãn hàng 14.5sp và một nhãn hàng 13.5sp
 * đang là HAI cỡ cho CÙNG một vai ("nội dung một hàng") ⇒ cả hai về [BODY]. Tiêu đề nhóm 12–13sp đang NHỎ hơn nội
 * dung ⇒ nó phải lên [SECTION] (**16**, đậm) để ra "đầu mục" — ở 14 thì tỉ số cỡ với [BODY] chỉ 14/13.5 = 1.04,
 * mắt không đọc ra thứ bậc; 16 cho 1.19 (số học từ hai bậc; [ĐO] verify ảnh vòng 2 đo tỉ số chiều cao nét 1.33).
 *
 *  • [DISPLAY] 28 — số/giá trị hero (đồng hồ, giá trị lớn giữa ô).
 *  • [TITLE]   20 — tiêu đề màn / bảng phủ.
 *  • [SECTION] 16 — tiêu đề nhóm (luôn dùng kèm đậm + màu sáng để nổi hơn [BODY]).
 *  • [BODY]  13.5 — nội dung, nhãn hàng, chữ trên nút.
 *  • [CAPTION] 12 — chú thích, dòng phụ, đơn vị.
 *
 * ## KHÔNG thuộc thang này
 * Cỡ chữ VẼ trên Canvas của widget/board (số áp suất lốp, nhãn bộ phận thân xe…) tính theo **tỉ lệ cạnh ô** để bất biến với
 * cỡ ô — đó là kích thước hình học, không phải một bậc chữ giao diện. Chúng khai riêng tại chỗ vẽ, có lý do (xem
 * [TyreBoardView]/[DoorBoardView]).
 *
 * ## Bài canh
 * [TypeScaleContractTest] quét mã nguồn các bề mặt đã áp và **đỏ** khi có `setTextSize(COMPLEX_UNIT_SP, <số>)`
 * viết tại chỗ thay vì đi qua [KachiType]. Ngoại lệ (Canvas) khai tường minh kèm lý do.
 */
object KachiType {

    const val DISPLAY = 28f
    const val TITLE = 20f
    const val SECTION = 16f
    const val BODY = 13.5f
    const val CAPTION = 12f

    /**
     * Đặt cỡ chữ theo một bậc của thang (+ đậm tuỳ chọn). Dùng thay cho `setTextSize` số tay ở mọi bề mặt đã áp
     * design system.
     *
     * ## ⚠ Hàm này sở hữu CẢ cỡ CẢ nét — gọi nó SAU khi đặt typeface là mất typeface đó
     * `bold = false` ghi `Typeface.DEFAULT` **tường minh** (không phải "để nguyên"): một bậc của thang phải ra đúng
     * một hình chữ, nếu không thì hai chỗ cùng gọi [BODY] lại ra hai nét khác nhau tuỳ view đó trước đó bị ai chạm.
     * Đổi lại, thứ tự gọi có nghĩa — `typeface = DEFAULT_BOLD` rồi `apply(tv, BODY)` sẽ **âm thầm** mất đậm. Cách
     * đúng: truyền `bold = true`, đừng đặt typeface riêng. [ĐO] 2026-09-12: 12 chỗ gọi hiện tại đều trên `TextView`
     * vừa tạo và không chỗ nào đặt typeface riêng ⇒ chưa có ca nào bị mất nét, nhưng bẫy thì có thật.
     */
    fun apply(tv: TextView, sp: Float, bold: Boolean = false) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        tv.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}
