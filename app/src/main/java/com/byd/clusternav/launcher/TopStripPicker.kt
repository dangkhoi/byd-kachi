package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

/**
 * BỘ CHỌN CHIP cho thanh trạng thái trên (RW0 **vùng thứ ba**).
 *
 * Tách khỏi bảng "Tuỳ biến" cũ (đã xoá ở S1·T5) vì bảng đó vượt **trần 500 dòng** của dự án khi
 * thêm mục này; và đây là một mảng liền
 * mạch (giữ danh sách chip đang chọn + vẽ ô + đổi trạng thái) nên cắt đúng khớp.
 *
 * Giữ **trạng thái đang chọn của phiên mở bảng** để tô ô đúng; nguồn sự thật vẫn là `HomeUiState.topStrip` — lớp này
 * chỉ báo ra qua [onToggle] và KHÔNG ghi bền.
 */
class TopStripPicker(
    private val context: Context,
    initial: TopStripConfig,
    private val onToggle: (String, Boolean) -> Unit,
) {
    private var strip = initial
    private val tiles = HashMap<String, View>()

    fun has(id: String): Boolean = strip.has(id)

    /**
     * RW0 vùng thứ ba — chọn chip cho thanh trạng thái trên.
     *
     * Trước 2026-09-11 thanh trên là **3 chip viết cứng** trong bộ vẽ ⇒ vùng duy nhất người dùng không sửa được, nên
     * RW0 chưa trọn dù hai vùng kia đã xong.
     *
     * **Chỉ nhận mục ĐỌC** — lý do (đích chạm 24dp là quá nhỏ để bắn lệnh xe + thanh trên là dòng trạng thái) ghi ở
     * KDoc [TopStripConfig]. Không phải bỏ sót.
     *
     * ⚠ **Ô ở đây dựng RIÊNG, KHÔNG dùng lại `tile()`** — [ĐO] bản đầu dùng lại thì sinh ba lỗi cùng lúc: sự kiện bấm
     * bên trong `tile()` bắn vào **thanh nút** (chọn chip lại thêm nút vào thanh), `tiles[id]` bị **ghi đè** vì cùng
     * một mã xuất hiện ở cả hai lưới, và hai chỗ tô nền tranh nhau. Đúng bẫy "hai bản sao cùng khoá".
     *
     * ⚠ **Chỉ bày 3 chip dựng sẵn + chip đang chọn** (≤ 7 ô) chứ không bày cả 123 datum: bảng này đã dựng 187 ô đồng
     * bộ trên thread chính (nợ đã ghi), thêm 123 ô nữa là nhân đôi giá mở bảng. Muốn đặt một datum bất kỳ thì
     * **giữ** ô của nó ở danh sách bên dưới — có nói rõ trong câu mô tả.
     */
    fun section(parent: LinearLayout) {
        parent.addView(label("Chip trên thanh trạng thái"))
        parent.addView(TextView(context).apply {
            text = "Tối đa ${TopStripConfig.CAP} thông tin hiện cạnh đồng hồ. Chạm để bật/tắt. " +
                "Muốn đưa một thông tin khác lên đây: GIỮ ô của nó ở danh sách bên dưới. " +
                "Chỉ thông tin XEM — nút bấm đặt ở thanh điều khiển hoặc ô giữa màn (chip quá nhỏ để bấm an toàn)."
            setTextColor(Color.parseColor(KachiTheme.MUT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, 0, 0, dpi(context, 8))
        })
        val shown = TopStripConfig.choices().filter { it.id in TopStripConfig.BUILT_IN || strip.has(it.id) }
        var row: LinearLayout? = null
        shown.forEachIndexed { i, pick ->
            if (i % 5 == 0) {
                row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                parent.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            row!!.addView(tile(pick), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val rem = shown.size % 5
        if (rem != 0) repeat(5 - rem) {
            row!!.addView(View(context), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Ô chọn chip — dựng riêng, sự kiện riêng, bảng riêng (xem cảnh báo ở [section]). */
    private fun tile(pick: CapabilityPick): View {
        val t = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dpi(context, 8), dpi(context, 12), dpi(context, 8), dpi(context, 12))
            addView(ImageView(context).apply {
                val r = iconRes(pick); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(dpi(context, 30), dpi(context, 30))
            })
            addView(TextView(context).apply {
                text = pick.displayLabel; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, 2), dpi(context, 6), dpi(context, 2), 0)
            })
            setOnClickListener { toggle(pick.id) }
        }
        tiles[pick.id] = t
        paint(pick.id)
        return t
    }

    /**
     * Bật/tắt một chip. Luật (trần · chỉ mục đọc) ở `:core`; nếu cấu hình KHÔNG đổi thì **nói ra** thay vì im lặng bỏ
     * qua cú bấm — im lặng làm người dùng tưởng nút hỏng (bài học từ nút bố cục sẵn ở P9).
     */
    fun toggle(id: String) {
        val on = !strip.has(id)
        val next = strip.setEnabled(id, on)
        if (next == strip) {
            Toast.makeText(
                context,
                "Thanh trên chỉ chứa ${TopStripConfig.CAP} chip — bỏ một cái trước",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        strip = next
        onToggle(id, on)
        paint(id)
    }

    private fun paint(id: String) {
        val t = tiles[id] ?: return
        t.background = if (strip.has(id)) GradientDrawable().apply {
            cornerRadius = dpi(context, 14).toFloat()
            setColor(Color.parseColor("#264c7dff")); setStroke(dpi(context, 1), Color.parseColor(KachiTheme.ACCENT))
        } else KachiTheme.card(context, 14f, "#161b24")
    }

    private fun label(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT2)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        letterSpacing = 0.06f; setPadding(0, dpi(context, 14), 0, dpi(context, 6))
    }

    /** Icon của khả năng; chưa map → icon đại diện nhóm (khỏi ô trống icon). */
    private fun iconRes(pick: CapabilityPick): Int {
        val r = KachiTheme.iconRes(pick.icon)
        if (r != 0) return r
        val d = pick.domain ?: return 0
        return KachiTheme.iconRes(WidgetCatalog.iconFor(d))
    }
}
