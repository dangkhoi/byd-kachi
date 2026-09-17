package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ U7 · R6 — DẤU *"CHƯA KIỂM TRÊN XE"* ĐẢO CHIỀU ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-icon-set-v2.html` §3 **R6**.
 *
 * ## Bệnh nó chữa — [ĐO] ảnh lưới ngăn kéo 2026-09-12/13
 * Mức bằng chứng của bộ đăng ký hôm nay: **chỉ 21/195 mã ở mức [EvidenceTier.PROVEN]**. Vì dấu được vẽ cho mọi mã
 * *không* PROVEN, cái lưới thực tế hiện ra là **gần như MỌI ô đều mang một chấm hổ phách sáng ở góc icon**. Một dấu
 * mà 9/10 ô đều có thì nó không còn là *dấu* — nó thành **hoa văn nền**, đúng họ bệnh mà U6 đã phải chữa cho icon
 * (*"quá ba ô cùng một hình thì hình thành hoa văn"*). Tệ hơn: màu hổ phách là màu CẢNH BÁO của bảng màu, nên cả
 * trang đọc như một trang toàn lỗi.
 *
 * ## Cách chữa: giữ dấu, HẠ giọng nó, rồi nói MỘT câu cho cả nhóm
 *  1. Chấm đổi từ **hổ phách sáng** sang **[KachiTheme.MUT2] (mực mờ nhất)** và nhỏ hơn một bậc ⇒ vẫn phân biệt
 *     được ô đã kiểm / chưa kiểm khi soi từng ô, nhưng không còn hét lên khi nhìn cả trang.
 *  2. Số lượng chuyển thành **một dòng ở đầu mỗi nhóm** (*"N mục chưa kiểm trên xe"*) — chỗ duy nhất nói được
 *     con số, và nói một lần thay vì 19 lần.
 *
 * Giữ nguyên **nguồn sự thật** ([EvidenceTier.needsBadge]) — lượt này chỉ đổi cách NÓI, không đổi cách TÍNH.
 *
 * ## Vì sao là một tệp riêng, không phải hàm trong [AppDrawer]
 * Có **hai** bề mặt bày đúng những ô ấy ([AppDrawer] cho ô giữa màn · [TopStripPicker] cho chip thanh trên).
 * Bản cũ chỉ [AppDrawer] có chấm, [TopStripPicker] thì không — tức cùng một mã, hai màn nói hai điều khác nhau về
 * độ tin cậy của nó. Đây đúng bẫy "hai bản sao của một quyết định" mà [CapabilityPicker] đã phải gom
 * (`unitPrefs` từng có 4 bản sao). Một nơi quyết định hình dạng của dấu; hai màn gọi vào.
 */
object PickerBadge {

    /**
     * Đổi trạng thái chọn của một icon đã dựng bằng [icon] (ô bộ chọn bật/tắt tại chỗ) — tìm lại `ImageView` dù nó
     * đứng một mình hay nằm trong khung kèm chấm.
     */
    fun retint(view: View, iconDp: Int, selected: Boolean) {
        val img = view as? ImageView ?: (view as? FrameLayout)?.getChildAt(0) as? ImageView ?: return
        KachiIcons.tint(img, iconDp, selected)
    }

    /**
     * Đường kính chấm, tính từ cỡ icon: **1/5 cạnh icon**, không phải một con số tự chọn.
     *
     * Suy ra thay vì tự chọn để hai bề mặt có cỡ icon khác nhau ([KachiSpace.ICON_XL] ở ngăn kéo,
     * [KachiSpace.ICON_L] ở bộ chọn chip) vẫn ra **cùng một tỉ lệ thị giác**. Trần của R6 là *"chấm không quá 8%
     * diện tích ô"*: ô hẹp nhất là icon 32dp ⇒ chấm 6.4dp, diện tích π·3.2² ≈ 32dp² trên ô ≥ 32×32 = 1024dp²,
     * tức **≈ 3%** — còn cách trần một quãng rộng, và quãng đó không đổi khi cỡ icon đổi.
     */
    private const val DOT_RATIO = 5

    private fun dotPx(ctx: Context, iconDp: Int): Int {
        // Phép chia làm NGOÀI lời gọi dpi(): `SpacingScaleContractTest` soi mọi số trần nằm trong đối số của
        // `dpi(...)` (đúng luật — số dp phải tới từ thang [KachiSpace]). [DOT_RATIO] không phải một số dp, nó là
        // một TỈ LỆ, nên chỗ của nó là ở đây chứ không phải trong thang khoảng cách.
        val dp = iconDp / DOT_RATIO
        return dpi(ctx, dp)
    }

    /**
     * Icon của một ô chọn, kèm chấm *"chưa kiểm trên xe"* **dán vào góc icon** khi [needsBadge].
     *
     * ⚠ [KIỂM TOÁN UX mục 6] Chấm phải dán vào **góc ICON**, không phải góc THẺ: ô nhóm rộng ~583px mà icon chỉ
     * 66px và nằm giữa ô ⇒ chấm ở góc thẻ cách icon ~268px, và ô chưa chọn thì không có nền thẻ để cái góc đó
     * thuộc về, nên chấm trông như một hạt bụi trên màn.
     */
    fun icon(ctx: Context, res: Int, needsBadge: Boolean, iconDp: Int, selected: Boolean = false): View {
        val size = dpi(ctx, iconDp)
        val img = ImageView(ctx).apply {
            // P2 · AC2.6: tint theo hợp đồng cỡ/chủ đề/trạng thái ([KachiIcons.tint]) thay vì INK đơn sắc ở mọi nơi.
            if (res != 0) { setImageResource(res); KachiIcons.tint(this, iconDp, selected) }
        }
        if (!needsBadge) return img.apply { layoutParams = LinearLayout.LayoutParams(size, size) }
        val d = dotPx(ctx, iconDp)
        return FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            addView(img, FrameLayout.LayoutParams(size, size))
            addView(dot(ctx), FrameLayout.LayoutParams(d, d, Gravity.TOP or Gravity.END))
        }
    }

    /**
     * CHÍNH cái chấm — hình tròn, màu [KachiTheme.MUT2]. **Cỡ do bên gọi đặt bằng `LayoutParams`**, vì mỗi bề mặt
     * có một ô khác cỡ (icon 32/44dp ở bộ chọn · ô nút 84×86dp ở thanh nút).
     *
     * ## U10 — vì sao hàm này phải tồn tại thay vì mỗi nơi tự vẽ một hình tròn
     * R6 hạ chấm từ hổ phách xuống [KachiTheme.MUT2] cho **bộ chọn**, nhưng [ControlTileFactory.withBadge] (thanh
     * nút + ô giữa màn) vẫn tự vẽ một chấm hổ phách của riêng nó ⇒ **cùng một sự thật** (*"mã này chưa kiểm trên
     * xe"*) nói bằng **hai giọng** ở hai bề mặt cạnh nhau, và giọng to hơn lại là giọng đã bị bác. Đúng bẫy "hai
     * bản sao của một quyết định" mà [PickerBadge] sinh ra để gom. Nay màu + hình chỉ còn MỘT chỗ quyết định.
     */
    fun dot(ctx: Context): View = View(ctx).apply {
        // MUT2 = mực mờ nhất của bảng màu. KHÔNG dùng AMBER: xem KDoc lớp — hổ phách là màu cảnh báo, mà dấu này
        // hiện trên 9/10 ô nên cả trang đọc thành "toàn lỗi".
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(KachiTheme.MUT2)) }
    }

    /**
     * Câu *"N mục chưa kiểm trên xe"* cho một khối ô — `null` khi **không có mục nào** chưa kiểm.
     *
     * Trả `null` (chứ không phải chuỗi *"0 mục…"*) là cố ý, cùng luật với vòng kiểm quyền và với
     * [CapabilityPicker.groupHint]: **đủ thì im lặng**. Một dòng nói "0" là một dòng chiếm chỗ mà không nói gì.
     */
    fun unverifiedNote(ctx: Context, picks: List<CapabilityPick>): String? {
        val n = CapabilityPicker.unverifiedCount(picks)
        if (n <= 0) return null
        return ctx.resources.getQuantityString(R.plurals.kachi_picker_unverified_n, n, n)
    }
}
