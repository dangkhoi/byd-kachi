package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Widget NHẠC — ảnh bìa + tên bài + nghệ sĩ + thanh tiến trình + ba nút điều khiển.
 *
 * ## Vì sao tách khỏi [WidgetViews] (T5)
 * [WidgetViews] chạm trần **500 dòng** của dự án khi T5 thêm chú thích cho việc nới đích chạm nút nhạc. Cắt theo
 * đường này vì widget nhạc là bộ vẽ **tự chứa** duy nhất còn lại trong tệp đó: nó là bề mặt duy nhất có
 * **nút bấm** (`data.onMedia`), trong khi mọi bộ vẽ còn lại chỉ hiện số. Bộ dựng chung (`col`/`tv`) vẫn dùng lại
 * từ [WidgetViews] để nhạc không lệch phông với các widget khác.
 */
object MediaWidgetView {

        fun build(ctx: Context, data: WidgetData): View = WidgetViews.col(ctx).apply {
            val m = data.media
            val art = ImageView(ctx).apply {
                background = KachiTheme.gradient(ctx, Sp.RADIUS_L, "#f59e0b", "#ef4444")
                if (m?.albumArt != null) setImageBitmap(m.albumArt)
            }
            addView(art, LinearLayout.LayoutParams(dpi(ctx, Sp.ART), dpi(ctx, Sp.ART)).also { it.bottomMargin = dpi(ctx, Sp.S) })
            addView(WidgetViews.tv(ctx, m?.title ?: "—", 15f, KachiTheme.INK, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            addView(WidgetViews.tv(ctx, m?.artist ?: "", 12.5f, KachiTheme.MUT).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            val frac = m?.let { if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) else 0f } ?: 0f
            val prog = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply { cornerRadius = dpi(ctx, Sp.RADIUS_PILL).toFloat(); setColor(c("#29FFFFFF")) }
                addView(View(ctx).apply { background = GradientDrawable().apply { cornerRadius = dpi(ctx, Sp.RADIUS_PILL).toFloat(); setColor(c(KachiTheme.ACCENT)) } },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, frac.coerceAtLeast(0.001f)))
                addView(View(ctx), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (1f - frac).coerceAtLeast(0.001f)))
            }
            addView(prog, LinearLayout.LayoutParams(dpi(ctx, Sp.PROGRESS_W), dpi(ctx, Sp.BAR_THIN)).also { it.topMargin = dpi(ctx, Sp.SLOT_GAP) })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dpi(ctx, Sp.M), 0, 0)
                fun mbtn(icon: String, action: String) = ImageView(ctx).apply {
                    val r = KachiTheme.iconRes(icon); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
                    // Lề trong giữ GLYPH ở cỡ cũ (48 − 2×12 = 24dp) trong khi VÙNG CHẠM là 48dp. Không có lề này thì
                    // ImageView kéo hình đầy khung ⇒ nút nhạc to gấp đôi, tức là "sửa đích chạm" hoá ra đổi cả bố cục.
                    val p = dpi(ctx, Sp.M); setPadding(p, p, p, p)
                    setOnClickListener { data.onMedia(action) }
                }
                val playing = m?.playing == true
                // T5 — đích chạm nút nhạc 22dp → Sp.TOUCH (48dp). Đây là đích chạm NHỎ NHẤT của launcher trước T5
                // (diện tích chỉ 1/4,7 mức tối thiểu) mà lại nằm ở widget hay dùng nhất. Glyph giữ ~24dp bằng lề
                // trong của `mbtn`, nên nhìn gần như không đổi — chỉ VÙNG CHẠM to ra. Khe giữa hai nút hạ XL→S vì
                // bản thân vùng chạm đã tách chúng ra.
                addView(mbtn("ic-prev", "prev"), LinearLayout.LayoutParams(dpi(ctx, Sp.TOUCH), dpi(ctx, Sp.TOUCH)).also { it.marginEnd = dpi(ctx, Sp.S) })
                addView(mbtn("ic-play", if (playing) "pause" else "play"), LinearLayout.LayoutParams(dpi(ctx, Sp.TOUCH), dpi(ctx, Sp.TOUCH)).also { it.marginEnd = dpi(ctx, Sp.S) })
                addView(mbtn("ic-next", "next"), LinearLayout.LayoutParams(dpi(ctx, Sp.TOUCH), dpi(ctx, Sp.TOUCH)))
            })
        }
}
