package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
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
 *
 * ## ⚠⚠ 2026-09-21 — khung dựng MỘT LẦN, nội dung đổ TẠI CHỖ
 * Trước lượt này ô nhạc cũng bị [WidgetViews.refreshRead] **dựng lại** mỗi nhịp (nó là ô ĐỌC theo bảng tra khả
 * năng), nên ba nút transport bị tháo/gắn liên tục — đúng cái làm **mất cú bấm** mà ràng buộc C5 dựng ra để chặn,
 * chỉ là trên một bề mặt khác. Nay: ảnh bìa · tên bài · nghệ sĩ · tiến trình · hành động của nút Play đều đổi qua
 * [fill], còn ba nút thì dựng một lần và không bao giờ bị chạm tới.
 *
 * ⚠ Ô này **không đọc gì từ xe** (chỉ [WidgetData.media]) và `CarDataDemandRendererContractTest` khoá đúng điều đó
 * bằng cách quét tệp này — kể cả chú thích. Đừng nhắc trạng thái xe ở đây.
 */
object MediaWidgetView {

    fun build(ctx: Context, data: WidgetData): View {
        val art = ImageView(ctx).apply {
            background = KachiTheme.gradient(ctx, Sp.RADIUS_L, KachiTheme.ORANGE, KachiTheme.ART_TO)
        }
        // Tên bài + nghệ sĩ dài vô hạn theo thiết kế ⇒ KHAI là chữ tự do cho phép khớp (`…` sau ngân sách, không kéo cỡ
        // cả ô) — giá trị/chú thích các ô khác không khai nên phải hiện trọn (soát vòng 2, P3).
        val title = FitScale.markFree(WidgetViews.tv(ctx, "", 15f, KachiTheme.INK, true)
            .apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        val artist = FitScale.markFree(WidgetViews.tv(ctx, "", 12.5f, KachiTheme.MUT)
            .apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        // Thanh tiến trình = hai ô chia theo `weight`. Đổi tiến trình = đổi weight rồi đặt lại `layoutParams` (nó tự
        // `requestLayout`) — KHÔNG dựng lại thanh, vì dựng lại thanh là dựng lại cả ô.
        val done = View(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = dpi(ctx, Sp.RADIUS_PILL).toFloat(); setColor(c(KachiTheme.ACCENT))
            }
        }
        val rest = View(ctx)
        val prog = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dpi(ctx, Sp.RADIUS_PILL).toFloat(); setColor(c(KachiTheme.OVERLAY))
            }
            addView(done, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.001f))
            addView(rest, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.999f))
        }
        fun mbtn(icon: String) = ImageView(ctx).apply {
            val r = KachiTheme.iconRes(icon); if (r != 0) { setImageResource(r); setColorFilter(c(KachiTheme.INK)) }
            // Lề trong giữ GLYPH ở cỡ cũ (48 − 2×12 = 24dp) trong khi VÙNG CHẠM là 48dp. Không có lề này thì
            // ImageView kéo hình đầy khung ⇒ nút nhạc to gấp đôi, tức là "sửa đích chạm" hoá ra đổi cả bố cục.
            val p = dpi(ctx, Sp.M); setPadding(p, p, p, p)
        }
        val play = mbtn("ic-play")
        // T5 — đích chạm nút nhạc 22dp → Sp.TOUCH (48dp). Đây là đích chạm NHỎ NHẤT của launcher trước T5 (diện tích chỉ
        // 1/4,7 mức tối thiểu) mà lại nằm ở widget hay dùng nhất. Glyph giữ ~24dp bằng lề trong của `mbtn`, nên nhìn gần như
        // không đổi — chỉ VÙNG CHẠM to ra. Khe giữa hai nút Sp.S vì bản thân vùng chạm đã tách chúng ra.
        val prev = mbtn("ic-prev").apply { setOnClickListener { data.onMedia("prev") } }
        val next = mbtn("ic-next").apply { setOnClickListener { data.onMedia("next") } }
        // 2.93 WF-MEDIA-SMALL — khung tự xếp (MediaFitLayout, luật `:core MediaFit`): khung nhỏ bỏ ẢNH → nghệ sĩ → tiến
        // trình → tên trước, GIỮ ba nút ≥ 48 dp; khung một hàng rộng xếp ngang. Cùng các con, cùng lề trong Sp.S như `col`.
        val root = MediaFitLayout(ctx, art, title, artist, prog, prev, play, next, MediaFitLayout.box(title, artist) { dpi(ctx, it) })
        fun weigh(v: View, w: Float) {
            v.layoutParams = (v.layoutParams as LinearLayout.LayoutParams).also { it.weight = w }
        }
        fun fillMedia(d: WidgetData) {
            val m = d.media
            art.setImageBitmap(m?.albumArt)
            title.text = m?.title ?: "—"
            artist.text = m?.artist ?: ""
            val frac = m?.let { if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) else 0f } ?: 0f
            weigh(done, frac.coerceAtLeast(0.001f))
            weigh(rest, (1f - frac).coerceAtLeast(0.001f))
            // Nút giữa đổi Ý NGHĨA theo phiên nhạc (đang phát ⇒ tạm dừng). Hình giữ `ic-play` như bản cũ — đổi hình
            // là một quyết định thẩm mỹ, không thuộc lượt vá này.
            play.setOnClickListener { d.onMedia(if (m?.playing == true) "pause" else "play") }
        }
        fillMedia(data)
        return WidgetRefreshers.live(root, ::fillMedia)
    }
}
