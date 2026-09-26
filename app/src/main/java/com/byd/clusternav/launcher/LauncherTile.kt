package com.byd.clusternav.launcher

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.dpi

/**
 * ═══ Ô **HÀNH ĐỘNG CỦA CHÍNH LAUNCHER** — tách khỏi `ControlTileFactory.kt` ngày 2026-09-26 (UX4) ═════════════
 *
 * Lý do tách: tệp kia đã **497/500 dòng** (trần CLAUDE.md §4.1, có bài canh
 * `ControlStateUxContractTest.moi tep cua luot WP2 duoi tran 500 dong`) TRƯỚC khi UX4 thêm một dòng nào.
 *
 * Đường cắt theo **VAI**, không theo số dòng — và đây đúng là vai duy nhất trong tệp ấy **không nói về cái xe**:
 * `ControlTileFactory` mặc áo cho **khả năng của XE** (mã nó dựng đều có một dòng trong [ControlRegistry], đều đi
 * qua [CarControlPort], đều có thể mang dấu *"chưa kiểm trên xe"*), còn ô này mặc áo cho **việc của chính
 * launcher** (*Ứng dụng* · *Cài đặt* · *Nói với xe*): không có dòng registry nào, không chạm cổng xe, tier luôn
 * [EvidenceTier.PROVEN]. Cùng lệ đã dùng khi [readTileOf] rời sang `ReadTile.kt` ở WP2.
 *
 * ⚠ Không đổi một dòng hành vi nào lúc tách: cùng package, cùng thứ tự dựng view, cùng 220 ms nháy sáng. Ba tính
 * chất mà `LauncherActionTileWiringContractTest` canh (không `control()`, không `withBadge(`, cú bấm ra `onTap()`)
 * vẫn đo được ở cả hai đầu — cửa vào `ControlTileFactory.launcherTile` giữ nguyên.
 */
internal fun launcherTileOf(
    ctx: Context,
    size: TileSize,
    pick: CapabilityPick,
    icons: Boolean,
    /** Nền + mực của một ô BẤM (`active` = đang nhấn). Chuyền vào để không có bản sao thứ hai của phép tô màu. */
    dress: (LinearLayout, ImageView, TextView, Boolean) -> Unit,
    onTap: () -> Unit,
): View {
    val tile = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        val p = dpi(ctx, size.padDp); setPadding(p, p, p, p)
    }
    val r = KachiIcons.res(pick.icon, size.iconDp)
    val icon = ImageView(ctx).apply { if (r != 0) setImageResource(r) }
    if (icons) tile.addView(icon, LinearLayout.LayoutParams(dpi(ctx, size.iconDp), dpi(ctx, size.iconDp)))
    val label = TextView(ctx).apply {
        text = pick.displayLabel; setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp)
        gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    }
    tile.addView(reserveTwoLines(label))
    dress(tile, icon, label, false)
    tile.setOnClickListener {
        dress(tile, icon, label, true)
        onTap()
        tile.postDelayed({ dress(tile, icon, label, false) }, TAP_FLASH_MS)   // nháy sáng momentary
    }
    return tile
}

/**
 * Thời gian nháy sáng của một ô bấm-một-phát. Dùng lại **đúng con số** của `ControlTileFactory.tileButton` để hai
 * ô cạnh nhau trên cùng một thanh không nháy hai nhịp khác nhau.
 */
private const val TAP_FLASH_MS = 220L
