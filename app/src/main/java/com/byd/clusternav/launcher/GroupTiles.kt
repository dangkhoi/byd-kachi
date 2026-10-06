package com.byd.clusternav.launcher

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

// ⚠ [SOÁT G1] Tách khỏi `GroupTileViews.kt` vì tệp đó đã **521 dòng** (trần dự án là 500) sau khi lượt soát thêm
// KDoc cho `rowsOf` và `boardFooter`. Đường cắt chọn ở đây vì `GroupTiles` là **cửa vào** (chỗ gọi dùng đúng ba hàm
// `build`/`mini`), còn `GroupTileView` là bộ vẽ — hai vai khác nhau, không phải cắt bừa cho vừa số dòng.
//
// ⚠⚠ `GroupTileWiringContractTest` quét **CẢ HAI** tệp (`tiles` = nối nội dung) — nếu chỉ quét một tệp thì mọi
// `assertFalse(...)` (không tra dữ liệu · không ngưỡng · không chép mã thành viên) sẽ **thôi phủ** phần tách ra,
// tức tách tệp trở thành cách lách bài canh. Đó đúng họ lỗi "bài quét vùng sai" mà dự án đã trả giá.

/**
 * ═══ G1 · T3 — BA BỘ VẼ Ô NHÓM (STRIP · BOARD · CARD) ════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-capability-groups.html` §4.3. Cùng lối chia việc với W4: **quyết định** ở `:core`
 * ([GroupBoard], test off-car) — **vẽ** ở đây.
 *
 * ## Ba luật của tệp này (có test canh cả ba)
 *  1. **KHÔNG tự tra dữ liệu**: mọi số/nhãn/sắc thái đến từ [GroupBoardModel]. Lớp này không gọi [TelemetryReadout]
 *     và không gọi [UnitFormat] — mỗi bề mặt tự đổi đơn vị chính là bệnh RW0 đã dọn ([ĐO] bảng lốp từng ghi `°C`
 *     trong khi người dùng chọn `°F`, vì ô vẽ tự ghép ký hiệu đơn vị).
 *  2. **KHÔNG chép danh sách thành viên**: [GroupBoard] hỏi [CapabilityGroups]. Ở đây không có một mã `tyre_p_*` /
 *     `window_*` / `door_*` nào viết tay.
 *  3. **KHÔNG có ngưỡng**: non/căng/lệch của lốp và mức bụi thuộc `:core`. Viết lại ở đây là tạo ngưỡng thứ tư —
 *     lỗi dự án đã gặp thật (widget lốp cũ có `t[i] < 2.2` viết tại chỗ, lệch với `:core`).
 *
 * ## OQ1 — chạm ô con CHỈ để xem
 * Ô con của dải STRIP **không** gắn chạm; lệnh chỉ đi từ **hàng nút phía dưới**. Hai lý do (spec §7·OQ1): ô con nhỏ
 * (dải 10 cửa trên khung 640dp còn ~64dp mỗi ô, dưới xa mức 48dp cho một đích chạm an toàn), và lệnh kính/cửa
 * **không hoàn lại nhanh được**. Cùng lập luận đã dùng để quyết "chip thanh trên không nhận nút" ([TopStripConfig]).
 *
 * ## Số khoảng cách trong tệp này là TẠM
 * T5 sẽ đưa mọi `dp(...)` về thang `KachiSpace`. Các hằng được gom vào [GroupTileView.Companion] **để T5 chỉ phải
 * sửa một khối**, thay vì rải số tại chỗ như 115 lời gọi hiện có.
 */
object GroupTiles {

    /**
     * Ô nhóm cho ô giữa màn.
     *
     * @param tyreBoard cổng vào dựng **bảng 4 bánh** — truyền từ [WidgetViews] (nơi đang giữ bộ dựng đó) theo đúng
     *   lối "cổng vào bằng lambda" của [LauncherWindows] / [DrawerController] / [HomePanels]. Lý do KHÔNG dựng lại
     *   bảng lốp ở đây: nó đã có ([TyreBoardView] + [TyreBoard]), và bản thứ hai sẽ mang theo ngưỡng thứ hai.
     */
    fun build(ctx: Context, id: String, data: WidgetData, tyreBoard: (Context, WidgetData) -> View): View {
        val tile = GroupTileView(ctx)
        if (!tile.bind(id, data, tyreBoard)) return fallback(ctx, id)
        // 2.93 GROUPBOARD-1ROW — khung thấp (bảng đầy đủ tràn / ô vẽ dưới cao tối thiểu) ⇒ dạng tóm tắt GIÃN theo khung
        // (GroupFitFrame); khung đủ ⇒ y bảng 2.92. Nhịp xe đổ tại chỗ vào cả hai — không dựng lại view nào.
        val mini = mini(ctx, id, data)
        return WidgetRefreshers.live(GroupFitFrame(ctx, tile, FitGridLayout.single(ctx, mini))) { d ->
            tile.refresh(d); WidgetRefreshers.refresh(mini, d)
        }
    }

    /**
     * Ô nhóm trong **ô nén** (người dùng nhét nhiều widget vào cùng một ô ⇒ mỗi ô con còn ~1/4 khung).
     *
     * Cố ý KHÔNG vẽ dải/bảng thu nhỏ: 10 ô con trong một khung 1/4 thì mỗi ô con còn vài chục pixel, chữ không đọc
     * được và người xem không rút ra gì. Thay vào đó hiện **tóm tắt** ([GroupBoardModel.summary] — thuần, test
     * off-car): *"1 cảnh báo"* trả lời đúng câu người lái hỏi, còn số của thành viên đầu tiên thì không.
     *
     * ## Soát vòng 3 (P2) — đổ TẠI CHỖ, không thay view mỗi nhịp
     * Nhóm là mục ĐỌC ⇒ ô chứa nó được làm mới mỗi nhịp trạng thái xe. Bản trước không đăng ký hàm đổ ⇒
     * `WidgetViews.refreshRead` rơi về đường LÙI (tháo + gắn view) MỖI GIÂY ⇒ `FitGridLayout` xoá kết quả khớp, đo dò lại
     * cả lưới + một dòng `WidgetFit` mỗi giây, ô số đứng trước nó mất nhịp nở 1 s. Nay sắc thái (nền + màu icon) và
     * nhãn đổ tại chỗ qua [WidgetRefreshers.live] — chỉ ghi khi đổi thật.
     */
    fun mini(ctx: Context, id: String, data: WidgetData): View {
        val first = GroupBoard.of(id, data.car, data.units) ?: return fallback(ctx, id)
        val domain = CapabilityGroups.byId(id)?.domain
        var shown: GroupTone? = null
        var icon: ImageView? = null
        val label = GroupTileView.text(ctx, "", 10.5f, KachiTheme.MUT).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val p = dpi(ctx, Sp.S); setPadding(p, p, p, p)
            val r = KachiTheme.iconRes(first.icon)
            if (r != 0) addView(
                ImageView(ctx).apply { setImageResource(r) }.also { icon = it },
                LinearLayout.LayoutParams(dpi(ctx, Sp.ICON_S), dpi(ctx, Sp.ICON_S)).also { it.bottomMargin = dpi(ctx, Sp.XS) },
            )
            // Owner 2026-09-23: bỏ dòng tóm tắt ("7 bộ phận"/"2 cảnh báo") dưới ô nhóm — giữ icon + nhãn nhóm.
            addView(label)
        }
        /** Sắc thái (nền + màu icon) và nhãn nhóm của [m] — chỉ ghi khi đổi thật (một cửa cho lượt dựng lẫn lượt đổ). */
        fun paint(m: GroupBoardModel) {
            val tone = GroupTileView.worstTone(m)
            if (tone != shown) {
                shown = tone
                GroupTileView.surfaceOf(root, Sp.RADIUS_M, tone, domain)
                icon?.setColorFilter(c(GroupTileView.tintOf(tone)))
            }
            if (label.text.toString() != m.label) label.text = m.label
        }
        paint(first)
        fun fillGroupMini(d: WidgetData) {
            GroupBoard.of(id, d.car, d.units)?.let(::paint)
        }
        return WidgetRefreshers.live(root, ::fillGroupMini)
    }

    /**
     * Mã nhóm không tra ra model — chỉ xảy ra nếu prefs còn mã của bản cũ. Hiện mã + `"—"` thay vì để trống, cùng lối
     * suy giảm an toàn với [WidgetViews]. Không có gì để đổ (mã lạ không bao giờ thành có model) ⇒ đăng ký hàm đổ RỖNG
     * để `refreshRead` không thay view này mỗi nhịp (soát vòng 3, P2).
     */
    private fun fallback(ctx: Context, id: String): View = WidgetRefreshers.live(
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(GroupTileView.text(ctx, id, 12f, KachiTheme.MUT2))
            addView(GroupTileView.text(ctx, TelemetryView.PLACEHOLDER, 26f, KachiTheme.MUT, bold = true))
        },
    ) { }
}
