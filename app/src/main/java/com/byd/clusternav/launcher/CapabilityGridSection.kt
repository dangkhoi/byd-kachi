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
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

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
            setPadding(dpi(context, 8), dpi(context, 12), dpi(context, 8), dpi(context, 12))
            addView(ImageView(context).apply {
                val r = iconRes(pick); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(dpi(context, 34), dpi(context, 34))
            })
            addView(TextView(context).apply {
                text = pick.displayLabel; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, 2), dpi(context, 6), dpi(context, 2), 0)
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
                        if (onNow) "Đã đưa \"${pick.label}\" lên thanh trạng thái" else "Đã bỏ \"${pick.label}\" khỏi thanh trạng thái",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(context, "Nút bấm không đặt được lên thanh trên (chip quá nhỏ để bấm an toàn)", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
        tiles[pick.id] = content
        applyState(pick.id)
        // badge tier "chưa kiểm" (chấm amber góc trên-phải)
        return if (pick.needsBadge) FrameLayout(context).apply {
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(KachiTheme.AMBER)) }
            }, FrameLayout.LayoutParams(dpi(context, 7), dpi(context, 7), Gravity.TOP or Gravity.END).also {
                it.topMargin = dpi(context, 8); it.marginEnd = dpi(context, 8)
            })
        } else content
    }

    private fun applyState(id: String) {
        val tile = tiles[id] ?: return
        tile.background = if (id in enabled) GradientDrawable().apply {
            cornerRadius = dpi(context, 14).toFloat(); setColor(c("#264c7dff")); setStroke(dpi(context, 1), c(KachiTheme.ACCENT))
        } else KachiTheme.card(context, 14f, "#161b24")
    }

    /** iconRes theo icon của khả năng; chưa map → icon đại diện nhóm (khỏi ô trống icon). */
    private fun iconRes(pick: CapabilityPick): Int {
        val r = KachiTheme.iconRes(pick.icon)
        if (r != 0) return r
        val d = pick.domain ?: return 0
        return KachiTheme.iconRes(WidgetCatalog.iconFor(d))
    }
}
