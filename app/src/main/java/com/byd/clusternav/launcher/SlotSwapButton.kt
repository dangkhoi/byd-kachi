package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Nút ⇄ (đổi app/widget) của một ô — **một bộ dựng cho cả hai đường hiện ô**.
 *
 * Owner 2026-09-12: *"nút chỉ nổi trên khung để đổi, không được ảnh hưởng layout/size khung; CHỈ 1 nút, canh GIỮA
 * trên cùng, KHÔNG thanh nền, KHÔNG nút ✕."* Ô có hai đường vẽ tuỳ lúc mở app: **nhúng** ([WorkspaceView.slotHead],
 * view nằm trong ô) hoặc **freeform + lớp phủ** ([OverlayHeads], cửa sổ `TYPE_APPLICATION_OVERLAY` đặt lên trên
 * cửa sổ app khi kênh dadb có). Trước 2026-09-13 mỗi đường tự vẽ một kiểu: đường nhúng đã là 1 nút ⇄, đường phủ vẫn
 * là thanh cũ (nền đục + chấm + tên + ⇄ + ✕) ⇒ owner thấy "lúc 1 icon lúc 2 icon" trên cùng máy ảo — không phải
 * lỗi ngẫu nhiên, mà là đường nào thắng lúc mở. Một bộ dựng ở đây ⇒ hai đường không thể lệch nhau nữa
 * (`SlotHeadParityContractTest` khoá).
 *
 * Hình (2026-09-14, owner: "kín đáo, nhỏ gọn, không khung viền"): CHỈ icon `ic-swap` [Sp.ICON_S] tô [KachiTheme.MUT],
 * không nền, không viền. Bản trước (tròn ICON_L + scrim + viền STROKE) nổi như một nút bấm giữa ô. Đích chạm vẫn
 * TOUCH×SLOT_HEAD_CLEAR ở [centered]; ở [strip] dải nền màu KHUNG Ô vẫn giữ vì nó che caption của cửa sổ freeform
 * (chức năng, không phải trang trí).
 *
 * FIX286 (owner 03/10): ô TRỐNG nay trong suốt ⇒ ⇄ của nó nằm thẳng trên ảnh nền. Phương án A (không nền, tô màu theo
 * độ chói đo dưới nút) [ĐO máy ảo 03/10] KHÔNG đạt 3:1 trên ảnh nhiều chi tiết — không một màu đơn nào đạt trên cả
 * điểm sáng lẫn tối của cùng một vùng 30 px. ⇒ **Phương án B** (OQ8, chốt 2026-10-03 — phiên điều phối, owner có thể
 * đổi): [centered] `disc = true` đặt icon lên một **đĩa kính** [Sp.SWAP_DISC] (cùng [KachiGlass] NEUTRAL như thẻ,
 * không viền); icon giữ `MUT` như mọi ⇄ khác — tương phản do hợp đồng lớp che kính bảo đảm, không đo theo từng ảnh.
 * Lòng ô vẫn trong suốt. Mọi nút có mô tả trợ năng.
 */
object SlotSwapButton {

    /**
     * Nút ⇄ KÍN ĐÁO — owner 2026-09-14: *"cái nút switch app trên khung làm kín đáo, nhỏ gọn, không cần khung viền,
     * border gì"*. Trước: icon 32dp trên nền oval SCRIM_BTN + viền STROKE trắng — nổi như một nút bấm giữa ô. Nay:
     * **chỉ icon** [Sp.ICON_S] tô màu MUT (mờ), không nền, không viền. Đích chạm KHÔNG đổi (khung TOUCH×SLOT_HEAD_CLEAR
     * ở [centered]) — nhỏ là hình, không phải chỗ bấm.
     */
    fun build(context: Context, onTap: () -> Unit): ImageView = ImageView(context).apply {
        val r = KachiTheme.iconRes("ic-swap")
        if (r != 0) { setImageResource(r); setColorFilter(Color.parseColor(KachiTheme.MUT)) }
        scaleType = ImageView.ScaleType.FIT_CENTER
        background = null
        setOnClickListener { onTap() }
    }

    /**
     * @param description mô tả trợ năng của nút (FIX286 · ES4 — trước đây 0 chỗ có): [describe]. Đặt trên khung CHẠM
     *   (thứ nhận cú bấm), để trình đọc màn hình và uiautomator tìm được nút theo mô tả.
     * @param disc FIX286 · OQ8 phương án B — nút nằm trên ô TRONG SUỐT (ô trống) ⇒ icon đặt trên một đĩa kính
     *   [Sp.SWAP_DISC] ([KachiGlass] NEUTRAL: ảnh mờ + lớp che theo độ chói đo được + bề mặt 80 % — cùng hợp đồng
     *   `MUT ≥ 4.5:1` trên MỌI độ chói ảnh như thẻ kính, bài `ColorChoiceContractTest.lop che kinh du…`). Ô có nội
     *   dung không cần (nút nằm trên bề mặt ô). Đĩa là view RIÊNG phía sau icon: icon vẫn `background = null`, khung
     *   ô trống vẫn không mang tag kính (bẫy P1b) — tag kính nằm trên đĩa, lượt `KachiGlass.refresh` khi ảnh đổi
     *   dựng lại đúng đĩa (đường có sẵn, không đường mới).
     */
    fun centered(context: Context, description: String, disc: Boolean = false, onTap: () -> Unit): FrameLayout = FrameLayout(context).apply {
        // Đích chạm: hình VẼ 20dp nhưng vùng CHẠM là khung TOUCH×SLOT_HEAD_CLEAR quanh nó — soát ảnh
        // v2 [ĐO] ⌀48px = 32dp < 48dp. Khung trong suốt nhận chạm, nút bên trong không nhận (clickable=false) để một
        // cú chạm không rơi vào hai lớp.
        val visual = build(context) {}.apply { isClickable = false; isFocusable = false }
        val hit = FrameLayout(context).apply {
            isClickable = true
            contentDescription = description
            setOnClickListener { onTap() }
            if (disc) {
                val d = View(context).apply { isClickable = false; isFocusable = false }
                KachiGlass.apply(d, Sp.SWAP_DISC / 2, SurfaceTone.NEUTRAL, fade = false)   // bán kính = nửa cạnh ⇒ đĩa tròn; NÚT, không mờ theo R-OP
                addView(d, FrameLayout.LayoutParams(KachiTheme.dpi(context, Sp.SWAP_DISC), KachiTheme.dpi(context, Sp.SWAP_DISC), Gravity.CENTER))
            }
            addView(
                visual,
                // Hình 20dp ([Sp.ICON_S]) canh giữa theo cả hai chiều trong khung chạm — nhỏ gọn, không viền (owner 2026-09-14).
                FrameLayout.LayoutParams(KachiTheme.dpi(context, Sp.ICON_S), KachiTheme.dpi(context, Sp.ICON_S), Gravity.CENTER),
            )
        }
        addView(
            hit,
            // ⇄ ở giữa mép trên ô (owner 2026-09-25: nút switch app phải nằm giữa, không lệch phải).
            FrameLayout.LayoutParams(KachiTheme.dpi(context, Sp.TOUCH), KachiTheme.dpi(context, Sp.SLOT_HEAD_CLEAR), Gravity.TOP or Gravity.CENTER_HORIZONTAL),
        )
    }

    /**
     * Biến thể cho đường **freeform + lớp phủ** ([OverlayHeads]): cùng nút ⇄ giữa, nhưng khung là một DẢI màu khung ô
     * ([KachiTheme.CELL], bo góc trên) trải hết bề rộng ô.
     *
     * Vì sao có nền ở đây trong khi owner nói "không background": [ĐO 2026-09-13, máy ảo] khi app chạy cửa sổ freeform,
     * **hệ thống tự vẽ caption** (thanh xám + nút ▭ ✕) ở mép trên cửa sổ app; nút ⇄ không nền để caption lộ ra ⇒ mắt
     * vẫn thấy "2 icon" (⇄ của ta + ✕ của hệ). Dải này che caption — nó là **khung ô** kéo lên trên cửa sổ app, không
     * phải thanh tiêu đề (không tên, không chấm, không ✕). Đường nhúng không có caption nên dùng [centered] (trong suốt).
     * [CHƯA BIẾT] ROM xe có vẽ caption không — nếu không, có thể đổi OverlayHeads sang [centered]; đo ở buổi test xe.
     */
    fun strip(context: Context, description: String, onTap: () -> Unit): FrameLayout = centered(context, description, onTap = onTap).apply {
        val r = KachiTheme.dpi(context, Sp.RADIUS_L).toFloat()
        background = GradientDrawable().apply {
            setColor(Color.parseColor(KachiTheme.CELL))
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }

    /** Bề cao khung = [Sp.SLOT_HEAD_CLEAR]. */
    fun overlayHeightPx(context: Context): Int = KachiTheme.dpi(context, Sp.SLOT_HEAD_CLEAR)

    /**
     * FIX286 · ES4 — mô tả trợ năng của ⇄ ô [index] (0-based; người đọc thấy số 1-based như mọi chỗ khác nói "ô N").
     * Ô trống: *"Chọn ứng dụng cho ô N"* — khi khung trong suốt, đây là thứ DUY NHẤT còn nói ô ấy là chỗ đặt app;
     * ô có nội dung: *"Đổi ứng dụng ô N"*.
     */
    fun describe(context: Context, index: Int, empty: Boolean): String =
        context.getString(if (empty) R.string.kachi_slot_swap_empty else R.string.kachi_slot_swap, index + 1)

}
