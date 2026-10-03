package com.byd.clusternav.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ NỘI DUNG TĨNH của một ô: thẻ app · widget chết · nền widget (+ nhật ký ô trống) ════════════════════════════════════
 *
 * Tách khỏi `WorkspaceView.kt` ở lượt soát 1.66 (trần 500 dòng — CLAUDE.md §4.1) và tách **theo vai**, không theo
 * số dòng: tệp kia là một `ViewGroup` — nó đo, bố trí, dựng/huỷ màn ảo của ô và nghe inset. Bốn hàm dựng thẻ dưới đây
 * không làm gì trong số đó; chúng chỉ **dựng một view con** từ dữ liệu đã có, không giữ trạng thái nào, và không
 * hàm nào gọi ngược lên vòng đời của sân khấu. (FIX286: `emptyAdd` — "＋ Mở ứng dụng" của ô trống — đã gỡ vì khung
 * trống nay trong suốt; [EmptySlotLog] cuối tệp là nhật ký, không dựng view.)
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

/**
 * FIX286 · ES6 — dòng `[slot-empty]` cho `usage-*.log`: bản trên xe có khung trống trong suốt chưa, ⇄ của nó vẽ kiểu
 * nào (OQ8 · phương án B: `⇄ trên đĩa kính` — owner đổi lại phương án thì dòng này phải đổi theo, để nhật ký chụp từ
 * xe nói đúng bản đang chạy), đang có bao nhiêu ô trống, chủ đề nào (owner/anh em chụp màn hình gửi về — CLAUDE.md
 * §9/§11). KHÔNG ghi trường ảnh nền: [ĐO máy ảo 02/10] ở lượt dựng đầu sau khi tiến trình bật, ảnh nền còn đang nạp
 * trên luồng nền ⇒ một trường `ảnh=` ở dòng này sẽ báo `off` dù màn sắp hiện ảnh.
 *
 * Gọi ở MỌI lượt render theo state THẬT ([WorkspaceView] `renderInternal` — cả lượt không đổi cấu trúc: bố cục toàn ô
 * trống giống hệt state rỗng của `init` nên lượt đầu không dựng lại gì) + `setCustomLayout` đổi số ô + `restyle`; không
 * mỗi ô, và KHÔNG ở `rebuild()` trần — [ĐO máy ảo 03/10] `init` gọi nó với state rỗng trước lượt render đầu ⇒ dòng
 * "3 ô trống" giả ở mỗi lần tiến trình bật. Nhịp render 1 Hz trên xe ⇒ khử trùng ở đây là bắt buộc. Chỉ ghi
 * khi chữ ký (tập ô trống · chủ đề) ĐỔI so với dòng trước — `restyle` chạy ở mỗi lượt đổi chủ đề, ghi lặp cùng một
 * dòng chỉ làm loãng nhật ký. Lượt đầu của tiến trình không có ô trống ⇒ không ghi (không có gì để nói).
 */
internal object EmptySlotLog {
    private var last: String? = null

    fun note(slots: List<SlotContent>, shown: Int) {
        val empty = (0 until shown).filter { slots.getOrElse(it) { SlotContent.Empty } is SlotContent.Empty }
        val sig = "${empty.joinToString(",")}|${KachiTheme.night}"
        if (sig == last || (last == null && empty.isEmpty())) return
        last = sig
        android.util.Log.i(
            "KachiWorkspace",
            "[slot-empty] ${empty.size} ô trống" +
                (if (empty.isEmpty()) "" else " (ô ${empty.joinToString(",") { "${it + 1}" }}) trong suốt · ⇄ trên đĩa kính") +
                " · chủ đề=${if (KachiTheme.night) "tối" else "sáng"}",
        )
    }
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

/**
 * [tapHint] — ô KHÔNG có bộ chiếu (chưa có kênh shell và ROM không cho ActivityView). PROFILE-SWITCH-SLOTS R-B1 bỏ việc
 * tự mở app thành cửa sổ nổi; READY-AT-HOME R1.3 bỏ nốt đường chạm-để-mở-nổi ⇒ dòng chữ nói TÌNH TRẠNG KÊNH thay cho
 * "Chạm để mở": "Đang kết nối…" / "Cần cấp quyền" / "Chưa có kênh điều khiển" (luật `ShellReadinessPolicy.tileHint`),
 * tự đổi khi trạng thái kênh đổi ([ShellAccessUi.tileHint]). Chạm ô đi `onAppOpen` → `LauncherWindows.placeApp` →
 * chờ kênh hoặc thẻ xin quyền. Kênh lên ⇒ `applyEmbedSeam` dựng lại ô với bộ chiếu ⇒ thẻ dựng lại không còn dòng này.
 */
internal fun WorkspaceView.appCard(pkg: String, tapHint: Boolean = false): View {
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
    if (tapHint) col.addView(ShellAccessUi.tileHint(context))
    return col
}
