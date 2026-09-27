package com.byd.clusternav.launcher

import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ Ô của ngăn kéo — widget dựng tay · mục khả năng (huy hiệu loại + chấm chưa-kiểm) ═══
 *
 * Tách THUẦN khỏi `AppDrawer.kt` (545 dòng → trần 500, L6-debt 2026-09-27): thân hàm giữ nguyên byte, chỉ đổi `private fun`
 * thành hàm mở rộng `internal` của [AppDrawer] cùng package (cùng khuôn `AppDrawerApps`). Trạng thái chọn vẫn là của
 * [AppDrawer] (`selected` · `widgetTiles` · `cap`); đường bật/tắt DUY NHẤT vẫn là [AppDrawer.toggleSelection].
 */

// ── Widget grid (toggle) — khe/đồng cao lấy từ [CapabilityTileGrid] như mọi lưới khác (R5) ──
internal fun AppDrawer.addWidgetGrid(parent: LinearLayout, cols: Int) =
    CapabilityTileGrid.rows(context, parent, widgets.size, cols) { i -> widgetTile(widgets[i]) }

internal fun AppDrawer.widgetTile(def: WidgetDef): View {
    val tile = LinearLayout(context).apply {
        // Căn NGANG-giữa nhưng DỌC-TRÊN (không CENTER cả hai): ô cao MATCH_PARENT theo hàng, căn giữa dọc làm
        // icon của ô nhãn ngắn tụt xuống lệch với ô nhãn hai dòng cùng hàng.
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
        addView(ImageView(context).apply {
            val r = KachiIcons.res(def.icon, Sp.ICON_XL); if (r != 0) { setImageResource(r); KachiIcons.tint(this, Sp.ICON_XL, false) }
            layoutParams = LinearLayout.LayoutParams(dpi(context, Sp.ICON_XL), dpi(context, Sp.ICON_XL))
        })
        addView(TextView(context).apply {
            text = def.displayLabel; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
        })
        setOnClickListener { toggleSelection(def.id) }
    }
    widgetTiles[def.id] = tile
    applyTileState(def.id)
    return tile
}


// ── Telemetry pick grid (registry-driven; cùng cơ chế chọn với curated) ──
internal fun AppDrawer.addPickGrid(parent: LinearLayout, picks: List<CapabilityPick>, cols: Int) =
    CapabilityTileGrid.rows(context, parent, picks.size, cols) { i -> pickTile(picks[i]) }

/**
 * 1.95 (owner 2026-09-22): huy hiệu LOẠI nhỏ có màu ở đầu ô picker — trả lời ngay *"ô này XEM hay BẤM"*.
 * READ = xanh (thông tin) · WRITE/LAUNCHER = cam (bấm được) · nhóm/thẻ = trung tính. Nhãn từ `pick.kindLabel`
 * (một nguồn ở `:core`), màu quyết ở đây (màu là chuyện trình bày). Cỡ nhỏ, chỉ icon+chữ ngắn — không tốn chỗ.
 */
internal fun AppDrawer.kindPill(pick: CapabilityPick): View {
    val group = pick.group || pick.curated
    val read = pick.kind == CapabilityKind.READ
    val fill = when {
        group -> KachiTheme.MUT2
        read -> KachiTheme.CYAN
        else -> KachiTheme.AMBER
    }
    // Nhóm/thẻ hiếm gặp ⇒ giữ chữ ngắn. READ/WRITE (đại đa số) dùng ICON: mắt = thông tin · nút = hành động.
    if (group) return TextView(context).apply {
        text = pick.kindLabel
        setTextColor(c(KachiTheme.INK_ON_ACCENT))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)   // [type scale] huy hiệu loại — nhỏ hơn dòng phụ 10sp
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        background = KachiTheme.pill(context, fill)
        setPadding(dpi(context, Sp.XS), dpi(context, Sp.HAIRLINE), dpi(context, Sp.XS), dpi(context, Sp.HAIRLINE))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dpi(context, Sp.XS) }
    }
    val sz = dpi(context, Sp.ICON_S)
    return android.widget.ImageView(context).apply {
        setImageResource(if (read) R.drawable.ic_kind_view else R.drawable.ic_kind_act)
        background = KachiTheme.pill(context, fill)
        val pad = dpi(context, Sp.HAIRLINE)
        setPadding(pad, pad, pad, pad)
        layoutParams = LinearLayout.LayoutParams(sz, sz).apply { topMargin = dpi(context, Sp.XS) }
        contentDescription = pick.kindLabel
    }
}

internal fun AppDrawer.pickTile(pick: CapabilityPick): View {
    val inner = LinearLayout(context).apply {
        // ⚠ [R5] Căn NGANG-giữa nhưng DỌC-TRÊN — cùng luật với lưới trong Cài đặt: ô cao `MATCH_PARENT` theo
        // hàng, nếu căn giữa dọc thì ô có dòng phụ đẩy icon/nhãn xuống ~10px lệch với ô cùng hàng.
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
        // R1 (owner 2026-09-22): icon một-nguồn — datum-trạng-thái dùng icon của control ⇒ picker khớp header/ô.
        val iconName = CapabilityDots.iconOverride(pick.id) ?: pick.icon
        addView(PickerBadge.icon(context, KachiIcons.res(iconName, Sp.ICON_XL), pick.needsBadge, Sp.ICON_XL))
        // 1.95 (owner): huy hiệu LOẠI nhỏ có màu — phân biệt ngay Xem / Bấm / Nhóm / Thẻ mà không tốn chỗ.
        addView(kindPill(pick))
        addView(TextView(context).apply {
            // Nhãn = TÊN của khả năng, không mang gợi ý loại (U6): loại xuống dòng phụ bên dưới. [ĐO] ảnh
            // 2026-09-12 owner đọc được "Charge target · view" trên lưới — thuật ngữ nội bộ lọt vào tên.
            text = pick.displayLabel; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)   // [type scale] ô lưới mật độ cao, ngoài 5 bậc
            gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
        })
        // Dòng phụ nói ô này GỒM GÌ (nhóm) và/hoặc thuộc LOẠI gì khi tên bị trùng (U6 — `displaySub`). Mục rời
        // tên không trùng vẫn để rỗng: [ĐO] 88/123 datum, thêm một dòng cho tất cả là làm chật đúng chỗ đang chật.
        // ⚠ T5 (thang khoảng cách/cỡ chữ): 10sp là số TÔI TỰ CHỌN — nhãn ở trên là 11.5sp, dòng phụ phải nhỏ hơn
        // để đọc ra thứ bậc. Mọi dpi() ở đây là số ĐÃ dùng sẵn trong chính ô này, không thêm số mới.
        if (pick.displaySub.isNotEmpty()) addView(TextView(context).apply {
            text = pick.displaySub; setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)   // [type scale] ô lưới mật độ cao, ngoài 5 bậc
            gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setPadding(dpi(context, Sp.XS), dpi(context, Sp.XS), dpi(context, Sp.XS), 0)
        })
        setOnClickListener { toggleSelection(pick.id) }
    }
    widgetTiles[pick.id] = inner
    applyTileState(pick.id)
    return inner
}
