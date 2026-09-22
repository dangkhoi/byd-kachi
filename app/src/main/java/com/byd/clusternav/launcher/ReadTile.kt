package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

// ⚠ [KIỂM TOÁN 2026-09-12] Tách khỏi `ControlTileFactory.kt` vì tệp đó vượt trần 500 dòng sau khi thêm cỡ ô
// [TileSize.GROUP] và nhánh ô HẸP. Đường cắt: đây là **ô đã dựng xong** (một tay cầm để đổ số về sau), còn
// `ControlTileFactory` là **bộ dựng** — hai vai khác nhau, không phải cắt bừa cho vừa số dòng.
//
// ⚠⚠ [WP2 · 2026-09-20] Nay **cả bộ dựng ô ĐỌC** cũng về đây ([readTileOf]) — xem KDoc của nó. `ControlTileFactory`
// giữ `fun readTile(` làm cửa vào (một dòng uỷ quyền) nên mọi chỗ gọi không đổi, nhưng bài canh cắt vùng theo tệp
// thì phải trỏ sang đây.

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
    private val iconView: DatumIconView? = null,
    private val iconName: String = "",
    private val maxLevel: Int = 0,
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
        // Icon: mức (chấm) + active/inactive theo giá trị xe. maxLevel≥1 ⇒ mức = số nguyên trong giá trị (0/1/2);
        // maxLevel=0 ⇒ active theo onOff. Chưa đọc được ⇒ inactive/level 0 (không bịa).
        iconView?.let { iv ->
            if (maxLevel >= 1) {
                val lvl = v?.takeIf { it.available }?.let { levelFrom(it) } ?: 0
                iv.set(iconName, maxLevel, lvl, active = lvl > 0)
            } else {
                iv.set(iconName, 0, 0, active = v?.onOff == true)
            }
        }
    }

    /** Mức hiện tại từ một [TelemetryView] có mức: lấy CHỮ SỐ đầu trong `display` ("Mức 2"→2, "2"→2), kẹp 0..maxLevel. */
    private fun levelFrom(v: TelemetryView): Int =
        Regex("\\d+").find(v.display)?.value?.toIntOrNull()?.coerceIn(0, maxLevel) ?: (if (v.onOff == true) 1 else 0)

    private companion object {
        /** Độ mờ của số chưa đọc được — cùng giá trị bản cũ dùng cho cả ô, nên dấu gạch trông y như trước. */
        const val DIM = 0.5f
    }
}

/**
 * ═══ BỘ DỰNG ô CHỈ-XEM — tách khỏi `ControlTileFactory.kt` ngày 2026-09-20 (WP2) ══════════════════════════════
 *
 * Lý do tách: tệp kia đã **539 dòng** (quá trần 500 của CLAUDE.md §4.1) TRƯỚC khi WP2 thêm một dòng nào. Đường cắt
 * theo VAI và về **đúng chỗ**: tệp này vốn đã sở hữu [ReadTile] (ô đã dựng + cách đổ số), nên bộ dựng ra nó thuộc
 * về đây; `ControlTileFactory` ở lại đúng vai *"ô HÀNH ĐỘNG"*.
 *
 * ## ⚠ Vì sao nhận [badge] bằng lambda chứ không tự vẽ chấm
 * Chấm *"chưa kiểm trên xe"* phải có **đúng một** hàm vẽ trên cả ba bề mặt (U10 — bộ chọn · thanh nút · đầu ô
 * nhóm), và hàm đó là `ControlTileFactory.withBadge` (bài canh
 * `CapabilityTileWiringContractTest.cham chua kiem tren xe cua thanh nut dung CHUNG cach ve voi bo chon` đòi nó nằm
 * ở đó và gọi `PickerBadge.dot`). Dựng một bản sao ở đây là mở bản thứ tư của cùng một quyết định — đúng bẫy
 * hai-bản-sao. Nên chỗ gọi chuyền hàm vẽ vào.
 *
 * ⚠⚠ Bài canh *"ô ĐỌC không gắn chạm"* đã đổi đường dẫn sang tệp này trong CÙNG lượt tách; để nguyên đường cũ thì
 * `SourceRoots.body` `require` hỏng và bài NỔ — đúng ý đồ của nó (nổ còn hơn âm thầm quét cả tệp).
 */
internal fun readTileOf(
    ctx: Context,
    size: TileSize,
    pick: CapabilityPick,
    badge: (LinearLayout) -> View,
): ReadTile {
    val content = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        val p = dpi(ctx, size.padDp); setPadding(p, p, p, p)
        background = KachiTheme.surface(ctx, size.radius, domain = pick.domain)
    }
    val r = KachiTheme.iconRes(pick.icon)
    val iconName = CapabilityDots.iconOverride(pick.id) ?: pick.icon
    val maxLevel = CapabilityDots.maxLevel(pick.id)
    val iconView = DatumIconView(ctx).apply { set(iconName, maxLevel, level = 0, active = maxLevel == 0) }
    if (KachiTheme.iconRes(iconName) != 0) content.addView(
        iconView,
        LinearLayout.LayoutParams(dpi(ctx, size.iconDp), dpi(ctx, size.iconDp)).also { it.bottomMargin = dpi(ctx, Sp.XS) },
    )
    val label = TextView(ctx).apply {
        // [ĐO] máy ảo 2026-09-10: một dòng + cắt cuối làm "Áp lốp trước-trái" và "Áp lốp trước-phải" đều thành
        // "Áp lốp trước-t…" ⇒ hai ô trông Y HỆT, người dùng không biết ô nào là bánh nào. Sửa: cho 2 DÒNG.
        // Cố ý KHÔNG bịa quy tắc viết tắt (kiểu bỏ tiền tố / lấy chữ đầu): nhãn đến từ bộ đăng ký với 195 mục
        // đủ kiểu, mọi quy tắc tự nghĩ đều sẽ tạo ra nhãn vô nghĩa ở đâu đó mà không ai kiểm được.
        text = pick.displayLabel
        setTextColor(c(KachiTheme.INK2)); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 1.5f)
        gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    }
    val value = TextView(ctx).apply {
        text = TelemetryView.PLACEHOLDER; setTextColor(c(KachiTheme.INK)); typeface = Typeface.DEFAULT_BOLD
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size.valueSp); gravity = Gravity.CENTER; maxLines = 1
        // ⚠ [SOÁT ĐỘC LẬP 2026-09-12] `maxLines = 1` mà KHÔNG ellipsize ⇒ chữ bị cắt CỨNG, không có "…" — đúng
        // họ lỗi mà chính tệp này đã vá hai lần cho NHÃN ô ([actionTile] và nhãn của ô đọc ngay trên). Từ khi
        // giá trị và đơn vị chia CHUNG một hàng ngang, giá trị dài không còn được cả bề ngang ô nữa nên ca cắt
        // gần hơn trước; có "…" thì người dùng đọc ra là "còn nữa", không đọc ra "số bị sai".
        ellipsize = TextUtils.TruncateAt.END
    }
    val unit = TextView(ctx).apply {
        setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp - 2f)
        gravity = Gravity.CENTER; maxLines = 1; visibility = View.GONE
    }
    content.addView(label)
    // ⚠ [SOÁT UI 2026-09-12] Giá trị + đơn vị trên MỘT hàng ngang (trước đây đơn vị là dòng RIÊNG dưới giá trị).
    // Ở ô THẤP của thanh nút (vd "Mức xăng"), ba dòng dọc (nhãn tối đa 2 dòng + giá trị + đơn vị) tràn khỏi ô ⇒
    // đơn vị "%" bị CẮT ở đáy và trông lạc lõng, trong khi ô kế bên KHÔNG có nút −/+ nên ô này nhìn như hỏng.
    // Gộp một hàng vừa hết cắt vừa đọc "— %" thành một cụm. Giữ 2 TextView riêng để [ReadTile.bind] không đổi.
    content.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        addView(value)
        addView(unit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.marginStart = dpi(ctx, Sp.XS) })
    })
    val outer = if (pick.needsBadge) badge(content) else content
    return ReadTile(outer, content, value, unit, iconView, iconName, maxLevel)
}

