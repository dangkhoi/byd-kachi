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

    /**
     * Một ô trong lưới app: gói (để tra hàng "Gần đây"), nhãn, **cách lấy** icon, việc làm khi chạm.
     *
     * ## ⚠ [SOÁT OCR 2026-09-16 · P2] Icon là một HÀM, không phải một [Drawable] đã dựng sẵn
     * `ri.loadIcon(pm)` mở tài nguyên + bung drawable (icon thích ứng trên API 29+ là **hai** lớp). Bản cũ gọi nó
     * cho MỌI app ngay trong [load], mà [load] chạy trong `init` của [AppDrawer] — tức trên luồng vẽ, ngay trong
     * cú chạm mở ngăn kéo. Trên đầu xe 60+ gói, đó là launcher đứng hình đúng lúc người dùng vừa bấm. Để nó ở
     * dạng hàm thì [tile] gọi được trên luồng nền rồi gắn vào [ImageView] qua `post` — khung đầu của ngăn kéo
     * không còn chờ một lượt bung icon nào.
     */
    class Item(val pkg: String, val label: String, val icon: () -> Drawable?, val onTap: () -> Unit)

    /** Mọi app có màn khởi chạy, sắp theo nhãn. NHÃN đọc ngay (rẻ), ICON để dành cho luồng nền — xem [Item]. */
    fun load(): List<Item> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return PackageQueries.queryActivities(pm, intent)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                val icon: () -> Drawable? = { ri.loadIcon(pm) }
                Triple(pkg, ri.loadLabel(pm).toString(), icon)
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
            byPkg[pkg]?.let { Item(it.pkg, it.label, copyOf(it.icon), it.onTap) }
        }
    }

    /** Bản sao độc lập, tính LƯỜI đúng như bản gốc — phép sao chỉ chạy khi luồng nền thật sự bung icon. */
    private fun copyOf(icon: () -> Drawable?): () -> Drawable? = { copyDrawable(icon()) }

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
                layoutParams = LinearLayout.LayoutParams(dpi(context, Sp.ICON_XL), dpi(context, Sp.ICON_XL))
                // Bung icon trên luồng nền rồi gắn về luồng vẽ — xem KDoc [Item]. Ô giữ nguyên KÍCH THƯỚC từ
                // `layoutParams` nên icon về muộn KHÔNG làm lưới nhảy; hỏng/không có icon ⇒ ô trống, không ném.
                //
                // ⚠ [SOÁT 1.69 · P2] Gửi qua [MAIN] chứ KHÔNG phải `View.post` của chính ô này. Ngay lúc khối
                // dưới được xếp hàng, ô còn **chưa có cha** (nó vừa được dựng, `addView` vào lưới xảy ra sau, và
                // cả lưới chỉ gắn vào cửa sổ ở cuối lượt dựng ngăn kéo). `View.post` trên một view **chưa gắn**
                // không đi qua Handler mà đẩy vào `mRunQueue` của view — một hàng đợi KHÔNG đồng bộ, được luồng
                // vẽ rút ra ở `dispatchAttachedToWindow`. Ghi vào nó từ luồng nền là đua với chính lượt gắn ấy:
                // nhẹ thì mất icon, nặng thì ném giữa lượt dựng ngăn kéo. Handler của main looper thì an toàn
                // đa luồng theo hợp đồng, và `setImageDrawable` vẫn chạy trên đúng luồng vẽ.
                val view = this
                ICONS.execute {
                    val d = runCatching { item.icon() }.getOrNull() ?: return@execute
                    MAIN.post { view.setImageDrawable(d) }
                }
            })
            addView(TextView(context).apply {
                text = item.label; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
            })
        }

    private companion object {
        /**
         * Luồng bung icon — **daemon**, hai luồng, dùng chung cho mọi lần mở ngăn kéo.
         *
         * Hai chứ không nhiều hơn: mỗi lượt là một chuyến IPC sang `PackageManager` rồi một lượt bung drawable,
         * nên thêm luồng chỉ thêm tranh chấp binder. Daemon để nó không bao giờ giữ tiến trình sống (cùng khuôn
         * với `SlotLiveProbe.io` / `PhotoWidgetView`).
         */
        private val ICONS: java.util.concurrent.ExecutorService =
            java.util.concurrent.Executors.newFixedThreadPool(2) { r ->
                Thread(r, "kachi-drawer-icons").apply { isDaemon = true }
            }

        /**
         * Đường về luồng vẽ cho icon bung xong — xem ⚠ ở [tile] về vì sao không dùng `View.post`.
         *
         * `by lazy` chứ không khởi tạo sớm: một `Handler` dựng trong thân `companion` là lời gọi khung **lúc nạp
         * lớp**, nên mọi bài kiểm JVM lỡ chạm tới lớp này sẽ chết ở `<clinit>` ("not mocked") thay vì ở chỗ nó
         * thật sự cần Android. Lười thì nó chỉ dựng đúng lúc có một icon thật để gắn.
         */
        private val MAIN: android.os.Handler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    }
}
