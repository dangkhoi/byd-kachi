package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * LƯỚI Ô KHẢ NĂNG (187 ô) — chọn khả năng để đặt vào thanh nút xe; giữ để đưa lên thanh trạng thái (S1 · T2).
 *
 * Chuyển **nguyên văn** từ bảng "Tuỳ biến" cũ (đã xoá ở S1·T5: `addGrid` · `tile` · `applyState` ·
 * `iconRes` + bảng `tiles`). Lý do tách: màn Cài đặt (S1) cần đúng lưới này trong nhóm "Màn hình chính", và bảng cũ
 * đã sát trần 500 dòng.
 *
 * Nhận **cổng vào bằng lambda** như [HomePanels] / [DrawerController] / [LauncherWindows]: lớp này không biết bảng
 * nào đang chứa nó, không chạm prefs, KHÔNG ghi bền — chỉ báo ra qua [onToggle] / [onChipToggle].
 *
 * ## ⚠⚠ RÀNG BUỘC "MỘT LƯỚI = MỘT BẢNG TILES" — bẫy đã trả giá, đừng lặp
 * [tiles] là bảng tra `mã khả năng → view` để [applyState] tô lại nền ô khi bật/tắt. Ràng buộc: **mỗi lưới trên màn
 * phải có một thực thể [CapabilityGridSection] RIÊNG** (hoặc một bộ dựng ô riêng như [TopStripPicker.tile]).
 *
 * [ĐO] 2026-09-11: phiên trước dùng lại hàm dựng-ô của bảng "Tuỳ biến" cũ cho lưới chọn chip thanh trên và sinh
 * **BA lỗi cùng
 * lúc**:
 *  1. sự kiện bấm bên trong ô bắn vào **thanh nút** ⇒ chọn một chip lại thêm một nút vào thanh nút;
 *  2. `tiles[id]` bị **ghi đè** vì cùng một mã khả năng xuất hiện ở cả hai lưới ⇒ chỉ ô cuối được tô;
 *  3. hai chỗ tô nền tranh nhau ⇒ trạng thái hiển thị của ô không còn nói đúng cấu hình.
 *
 * Đúng bẫy **"hai bản sao cùng khoá"** mà dự án đã gặp nhiều lần. Nên: dùng lại lớp này thì cấp thực thể mới; muốn
 * một lưới có hành vi bấm KHÁC thì dựng ô riêng, đừng thêm cờ vào [tile] (xem [TopStripPicker.section]).
 *
 * @param enabledIds các khả năng đang nằm trên thanh nút — dùng để tô ô lúc mở bảng.
 * @param onToggle người dùng bật/tắt một khả năng khỏi **thanh nút**.
 * @param onChipToggle người dùng GIỮ một ô ⇒ đưa/bỏ khả năng đó khỏi **thanh trạng thái** (đi qua [TopStripPicker]).
 * @param chipEnabled khả năng đó có đang ở thanh trạng thái hay không — đọc SAU khi [onChipToggle] chạy, để câu
 *   thông báo nói đúng chiều vừa xảy ra (nếu bộ chọn từ chối vì đã đầy trần thì chiều không đổi).
 */
class CapabilityGridSection(
    private val context: Context,
    enabledIds: Collection<String>,
    private val onToggle: (String, Boolean) -> Unit,
    private val onChipToggle: (String) -> Unit,
    private val chipEnabled: (String) -> Boolean,
) {

    private val enabled = HashSet(enabledIds)
    private val tiles = HashMap<String, LinearLayout>()

    fun addGrid(parent: LinearLayout, items: List<CapabilityPick>, cols: Int) {
        var row: LinearLayout? = null
        items.forEachIndexed { i, pick ->
            if (i % cols == 0) {
                row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                parent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            row!!.addView(tile(pick), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val rem = items.size % cols
        if (rem != 0) repeat(cols - rem) { row!!.addView(View(context), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun tile(pick: CapabilityPick): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
            addView(iconWithBadge(iconRes(pick), pick.needsBadge))
            addView(TextView(context).apply {
                text = pick.displayLabel; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
            })
            // Dòng phụ nói ô này GỒM GÌ — chỉ NHÓM có ([CapabilityPick.sub]); mục rời để rỗng nên lưới 187 ô không
            // cao thêm một dòng nào.
            // ⚠ T5: 10sp là số TÔI TỰ CHỌN (nhãn 11.5sp ⇒ dòng phụ phải nhỏ hơn để đọc ra thứ bậc).
            if (pick.sub.isNotEmpty()) addView(TextView(context).apply {
                text = pick.sub; setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.XS), dpi(context, Sp.XS), 0)
            })
            setOnClickListener {
                val now = pick.id !in enabled
                if (now) enabled.add(pick.id) else enabled.remove(pick.id)
                applyState(pick.id); onToggle(pick.id, now)
            }
            // GIỮ = đưa lên/bỏ khỏi thanh trạng thái. Đây là đường đặt **datum bất kỳ** lên thanh trên mà không phải
            // dựng thêm 123 ô cho bảng này (xem [TopStripPicker.section]). Chỉ mục ĐỌC — nút thì nói rõ vì sao không được.
            setOnLongClickListener {
                if (TopStripConfig.isChippable(pick.id)) {
                    onChipToggle(pick.id)
                    val onNow = chipEnabled(pick.id)
                    Toast.makeText(
                        context,
                        context.getString(
                    if (onNow) R.string.kachi_chip_added else R.string.kachi_chip_removed, pick.displayLabel,
                ),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(context, R.string.kachi_chip_button_rejected, Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
        tiles[pick.id] = content
        applyState(pick.id)
        return content
    }

    /**
     * Icon + chấm "chưa kiểm trên xe" **dán vào góc ICON**, không phải góc thẻ.
     *
     * [KIỂM TOÁN UX mục 6] Ở góc thẻ, chấm cách icon hàng trăm pixel (ô rộng gấp nhiều lần icon) nên nó đọc thành
     * một hạt bụi chứ không thành một dấu nói về mục này. Cùng bản vá với [AppDrawer.iconWithBadge] — hai màn chọn
     * phải nói cùng một kiểu, nhưng mỗi màn giữ bộ dựng ô RIÊNG (xem cảnh báo "một lưới = một bảng tiles" ở KDoc
     * lớp), nên đây là hàm riêng chứ không phải chỗ để dùng chung một hàm dựng ô.
     */
    private fun iconWithBadge(res: Int, needsBadge: Boolean): View {
        val size = dpi(context, Sp.ICON_L)
        val img = ImageView(context).apply { if (res != 0) { setImageResource(res); setColorFilter(c(KachiTheme.INK)) } }
        if (!needsBadge) return img.apply { layoutParams = LinearLayout.LayoutParams(size, size) }
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            addView(img, FrameLayout.LayoutParams(size, size))
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(KachiTheme.AMBER)) }
            }, FrameLayout.LayoutParams(dpi(context, Sp.DOT), dpi(context, Sp.DOT), Gravity.TOP or Gravity.END))
        }
    }

    private fun applyState(id: String) {
        val tile = tiles[id] ?: return
        tile.background = if (id in enabled) GradientDrawable().apply {
            cornerRadius = dpi(context, Sp.RADIUS_L).toFloat(); setColor(c(KachiTheme.ACCENT_SOFT)); setStroke(dpi(context, Sp.HAIRLINE), c(KachiTheme.ACCENT))
        } else KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.FIELD)
    }

    /** iconRes theo icon của khả năng; chưa map → icon đại diện nhóm (khỏi ô trống icon). */
    private fun iconRes(pick: CapabilityPick): Int {
        val r = KachiTheme.iconRes(pick.icon)
        if (r != 0) return r
        val d = pick.domain ?: return 0
        return KachiTheme.iconRes(WidgetCatalog.iconFor(d))
    }
}
