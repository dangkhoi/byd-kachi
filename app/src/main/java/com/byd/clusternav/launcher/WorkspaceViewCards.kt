package com.byd.clusternav.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ NỘI DUNG TĨNH của một ô: thẻ app · ô trống · widget chết · nền widget ════════════════════════════════════
 *
 * Tách khỏi `WorkspaceView.kt` ở lượt soát 1.66 (trần 500 dòng — CLAUDE.md §4.1) và tách **theo vai**, không theo
 * số dòng: tệp kia là một `ViewGroup` — nó đo, bố trí, dựng/huỷ màn ảo của ô và nghe inset. Năm hàm dưới đây
 * không làm gì trong số đó; chúng chỉ **dựng một view con** từ dữ liệu đã có, không giữ trạng thái nào, và không
 * hàm nào gọi ngược lên vòng đời của sân khấu.
 *
 * ⚠ Không đổi một dòng hành vi nào lúc tách: cùng package, cùng tên, cùng chữ ký — là hàm mở rộng của chính
 * [WorkspaceView] (khuôn `WorkspacePrefsProfile.kt` / `ClusterNavBridgeKeys.kt`), nên chỗ gọi không đổi ký tự nào.
 */

internal fun WorkspaceView.placeholder(text: String) = TextView(context).apply {
    this.text = text
    setTextColor(Color.parseColor(KachiTheme.MUT))
    KachiType.apply(this, KachiType.BODY)
    gravity = Gravity.CENTER
}

/** Ô trống: viền đứt + dấu ＋ to + nhãn — rõ là "chỗ thêm app". */
internal fun WorkspaceView.emptyAdd(): View = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
    addView(TextView(context).apply {
        // [type scale] ngoại lệ: `＋` là KÝ HIỆU trang trí (dấu "thêm vào đây"), không phải chữ — cỡ của nó là
        // hình học của ô trống, không phải một bậc chữ. Trần 32f = DISPLAY(28) sẽ nhỏ đi thấy rõ.
        text = "＋"; setTextColor(Color.parseColor(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f); gravity = Gravity.CENTER   // [type scale] glyph trang trí, ngoài 5 bậc
    })
    addView(TextView(context).apply {
        text = context.getString(R.string.kachi_slot_open_app); setTextColor(Color.parseColor(KachiTheme.MUT)); KachiType.apply(this, KachiType.BODY)
        gravity = Gravity.CENTER; setPadding(0, dp(Sp.XS), 0, 0)
    })
}

/** Thẻ app trong ô: icon + tên thật (PackageManager). Trên xe app THẬT mở freeform vào ô; off-car hiện thẻ này. */
/**
 * T4 — nền tối cố định phía sau widget bên thứ ba. Xem KDoc `KachiPalette.widgetBacking` về **vì sao không theo
 * chủ đề**; ở đây chỉ là một lớp tô, cố ý KHÔNG có viền (viền của ô đã do chính ô vẽ).
 */
internal fun WorkspaceView.appWidgetBacking(): View = View(context).apply {
    background = GradientDrawable().apply {
        cornerRadius = dp(Sp.RADIUS_L).toFloat()
        setColor(Color.parseColor(KachiTheme.WIDGET_BACKING))
    }
}

/**
 * T4 — thẻ hiện khi id widget đã CHẾT (app cung cấp bị gỡ / bị tắt).
 *
 * Bắt buộc phải có: `AppWidgetHost.createView` với id đã chết trả về một view **rỗng không báo lỗi**, nên nếu
 * không chặn thì ô đó thành ô trống y như chưa gán gì — người dùng chỉ thấy widget của mình biến mất. Thẻ này nói
 * **app nào** (nhờ provider được lưu cùng id) và chạm được để chọn lại.
 */
internal fun WorkspaceView.deadWidgetCard(content: SlotContent.AppWidget): View =
    TextView(context).apply {
        text = context.getString(R.string.kachi_appwidget_dead, appWidgetName?.invoke(content) ?: content.provider)
        setTextColor(Color.parseColor(KachiTheme.MUT)); KachiType.apply(this, KachiType.BODY)
        gravity = Gravity.CENTER
        setPadding(dp(Sp.L), dp(Sp.SLOT_HEAD_CLEAR), dp(Sp.L), dp(Sp.L))
    }

internal fun WorkspaceView.appCard(pkg: String): View {
    val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    val pm = context.packageManager
    try {
        col.addView(ImageView(context).apply {
            setImageDrawable(pm.getApplicationIcon(pkg))
            layoutParams = LinearLayout.LayoutParams(dp(Sp.ICON_XXL), dp(Sp.ICON_XXL))
        })
        col.addView(TextView(context).apply {
            text = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
            setTextColor(Color.parseColor(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
            gravity = Gravity.CENTER; setPadding(0, dp(Sp.S), 0, 0)
        })
    } catch (e: Exception) {
        col.addView(placeholder("▣  $pkg"))
    }
    return col
}
