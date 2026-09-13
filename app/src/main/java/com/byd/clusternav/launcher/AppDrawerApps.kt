package com.byd.clusternav.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.system.PackageQueries
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * PHẦN **DANH SÁCH ỨNG DỤNG** của ngăn kéo — tách khỏi [AppDrawer] (T6).
 *
 * Lý do tách là trần **500 dòng** của dự án (CLAUDE.md §4.1): [AppDrawer] đã 501 dòng TRƯỚC khi nhận chế độ thứ ba
 * (chọn nút cho thanh nút xe). Ranh giới cắt chọn ở đây vì phần này là thứ **duy nhất** trong ngăn kéo không nói về
 * khả năng của xe: nó hỏi [android.content.pm.PackageManager], dựng ô icon-ứng-dụng, và không tham gia vào đường
 * chọn-nhiều (`toggleSelection`) của các lưới ô khả năng.
 *
 * Lưới app cố ý giữ **số cột riêng** (`AppDrawer.COLS_APP`) — xem KDoc [CapabilityPicker.COLS]: ô app là icon nhỏ,
 * khác loại với ô khả năng. Nhưng **khe và chiều cao hàng** vẫn đi qua [CapabilityTileGrid] như mọi lưới khác: một
 * vùng cuộn phải có một nhịp.
 */
class AppDrawerApps(private val context: Context, private val onPickApp: (String) -> Unit) {

    /** Một ô trong lưới app: gói (để tra hàng "Gần đây"), nhãn, icon, việc làm khi chạm. */
    class Item(val pkg: String, val label: String, val iconDrawable: Drawable?, val onTap: () -> Unit)

    /** Mọi app có màn khởi chạy, sắp theo nhãn. */
    fun load(): List<Item> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return PackageQueries.queryActivities(pm, intent)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                Triple(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm))
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
            .map { (pkg, label, icon) -> Item(pkg, label, icon) { onPickApp(pkg) } }
    }

    /** Các app gần đây (theo thứ tự [recentApps]) lọc xuống những app THẬT còn cài — app đã gỡ tự rụng khỏi hàng. */
    fun recent(all: List<Item>, recentApps: List<String>): List<Item> {
        if (recentApps.isEmpty()) return emptyList()
        val byPkg = all.associateBy { it.pkg }
        // Cùng một app xuất hiện ở CẢ "Gần đây" LẪN "Tất cả" ⇒ phải NHÂN BẢN icon. Một Drawable chỉ giữ ĐÚNG MỘT
        // callback (`setImageDrawable` gán view làm callback) và một bộ bounds/state; dùng chung cho 2 ImageView thì
        // view gắn sau chiếm callback ⇒ view trước có thể không vẽ lại / lệch trạng thái.
        return recentApps.mapNotNull { pkg ->
            byPkg[pkg]?.let { Item(it.pkg, it.label, copyDrawable(it.iconDrawable), it.onTap) }
        }
    }

    /** Bản sao độc lập của [d] (chia sẻ constant-state nên rẻ). Không sao chép được → dùng lại bản gốc. */
    private fun copyDrawable(d: Drawable?): Drawable? =
        d?.let { runCatching { it.constantState?.newDrawable(context.resources) }.getOrNull() ?: it }

    /** Xếp [items] thành lưới [cols] cột — khe/chiều cao hàng lấy từ [CapabilityTileGrid] (R5). */
    fun grid(parent: LinearLayout, items: List<Item>, cols: Int) =
        CapabilityTileGrid.rows(context, parent, items.size, cols) { i -> tile(items[i]) }

    private fun tile(item: Item): View =
        LinearLayout(context).apply {
            // Căn DỌC-TRÊN như ô khả năng (không CENTER): ô cao `MATCH_PARENT` theo hàng, căn giữa dọc sẽ làm icon
            // của ô nhãn ngắn tụt xuống lệch với ô cùng hàng.
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
            setOnClickListener { item.onTap() }
            addView(ImageView(context).apply {
                if (item.iconDrawable != null) setImageDrawable(item.iconDrawable)
                layoutParams = LinearLayout.LayoutParams(dpi(context, Sp.ICON_XL), dpi(context, Sp.ICON_XL))
            })
            addView(TextView(context).apply {
                text = item.label; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
            })
        }
}
