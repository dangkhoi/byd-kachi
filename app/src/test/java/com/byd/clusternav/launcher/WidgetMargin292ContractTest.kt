package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.92 · R4 (spec `docs/specs/kachi-292-shortcut-widget.html` §5.2) — owner 06/10 *"margin 2 bên nhiều quá phí … check thêm
 * các widget khác nữa nhé"*. Soát máy ảo 06/10 (ba dáng khung: hẹp cao 301×804 · rộng thấp 1558×123 · to 1558×668): các
 * widget dựng trên khối dọc chung `WidgetViews.col` (đồng hồ · tốc độ · trạng thái xe · nhạc · vòng năng lượng/PM2.5 ·
 * thẻ đọc chung) mất 16 dp lề trong MỖI phía — ở dải rộng thấp đó là 48 px trên 123 px chiều cao, vòng năng lượng còn
 * 63 px. Lề nay 8 dp, cùng mép 8 dp của lưới lối tắt (`KachiBars.SHORTCUT_GRID_GAP`).
 *
 * Bài khoá lề của khối dọc (gỡ ⇒ đỏ) và chốt rằng nó vẫn là MỘT chỗ khai cho mọi widget một cột (không ai chép lề riêng).
 */
class WidgetMargin292ContractTest {

    private val views by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WidgetViews.kt") }

    @Test
    fun `khoi doc widget mot cot - le trong 8 dp, mot cho khai`() {
        val col = SourceRoots.body(views, "internal fun col(ctx: Context): LinearLayout")
        assertTrue(col.contains("val p = dpi(ctx, Sp.S); setPadding(p, p, p, p)"), "lề khối dọc widget = Sp.S (8 dp), không Sp.L")
        assertEquals(KachiSpace.S, KachiBars.SHORTCUT_GRID_GAP, "cùng mép với lưới lối tắt")
        // Mọi widget một cột đi qua CÙNG hàm (đồng hồ · tốc độ · xe ở WidgetViews; vòng/thẻ ở WidgetTelemetry; nhạc).
        val telemetry = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WidgetTelemetry.kt")
        val media = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/MediaWidgetView.kt")
        // 2.93 WIDGET-CAR-STRIP-LAYOUT (đổi ghim có lý do): trạng thái xe nay dựng bằng `CarStateLayout` (hình trên/cạnh chú
        // thích theo `:core CarStripFit`) với CÙNG lề trong Sp.S — không còn đi qua `col`.
        assertTrue(Regex("""\bcol\(ctx\)""").findAll(views).count() >= 2, "đồng hồ · tốc độ")
        assertTrue("CarStateLayout(ctx, art, doorLine, pad = dpi(ctx, Sp.S)" in views, "trạng thái xe: cùng lề 8 dp")
        assertTrue(Regex("""WidgetViews\.col\(ctx\)""").findAll(telemetry).count() >= 5, "vòng · số · huy hiệu · dải · thẻ chữ")
        // 2.93 WF-MEDIA-SMALL (đổi ghim có lý do): widget nhạc tự xếp bằng `MediaFitLayout` (bỏ phần phụ trước, giữ nút) với
        // CÙNG lề trong Sp.S — lề nằm ở `MediaFitLayout.box` (pad = dp(KachiSpace.S)).
        assertTrue(media.contains("MediaFitLayout(ctx, art, title, artist, prog, prev, play, next, MediaFitLayout.box("), "widget nhạc")
        assertTrue("pad = dp(KachiSpace.S)" in SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/MediaFitLayout.kt"))
    }

    /**
     * OQ3 (quyết định điều phối 06/10, backlog APPWIDGET-PADDING-UNIFORM): widget Android của app KHÁC cũng lề đều 8 dp,
     * không còn lề mặc định framework 12/4/12/20 dp (sw720dp, [ĐO framework-res máy ảo]: 18/6/18/30 px — đáy 30 px phí).
     * `AppWidgetHostView.setAppWidget` đặt lại lề mặc định mỗi lần ⇒ ghi đè PHẢI ở chính hàm ấy, sau `super`. Cỡ khai cho
     * nhà cung cấp = vùng nội dung thật + đúng phần lề mặc định framework sẽ tự trừ (gỡ bù ⇒ nhà cung cấp tưởng ô hẹp hơn
     * 8 dp mỗi chiều; bù sai chiều ⇒ tưởng to hơn và bị cắt).
     */
    @Test
    fun `widget ben thu ba - le deu 8 dp, khai co bu dung le mac dinh`() {
        val host = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/AppWidgetSlotHost.kt")
        val set = SourceRoots.body(host, "override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?)")
        assertTrue(set.indexOf("super.setAppWidget(appWidgetId, info)") in 0 until set.indexOf("setPadding(p, p, p, p)"), "lề ta đặt SAU lề mặc định")
        assertTrue("val p = KachiTheme.dpi(context, KachiSpace.S)" in set, "cùng 8 dp với khối dọc widget dựng sẵn")
        val size = SourceRoots.body(host, "override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int)")
        // 2.93 APPWIDGET-SIZE-API31 (đổi ghim có lý do): phép bù dời về `:core AppWidgetSize` (bài vét cạn hai đường API cho
        // nhà cung cấp CÙNG số MIN/MAX) + nhánh API 31+ `List<SizeF>`; đường < 31 vẫn bản 5 tham số, cùng số.
        assertTrue("val (padXPx, padYPx) = defaultPaddingPx()" in size)
        assertTrue("val cw = (w - paddingLeft - paddingRight).coerceAtLeast(0)" in size && "AppWidgetSize.legacyDp(cw, padXPx, d)" in size, "bề ngang khai = nội dung + bù")
        assertTrue("val ch = (h - paddingTop - paddingBottom).coerceAtLeast(0)" in size && "AppWidgetSize.legacyDp(ch, padYPx, d)" in size, "bề cao khai = nội dung + bù")
        assertTrue("runCatching { updateAppWidgetSize(Bundle(), wDp, hDp, wDp, hDp) }" in size)
        assertTrue("Build.VERSION.SDK_INT >= Build.VERSION_CODES.S" in size && "updateAppWidgetSize(Bundle(), listOf(size))" in size, "API 31+")
        val def = SourceRoots.body(host, "private fun defaultPaddingPx(): Pair<Int, Int>")
        assertTrue("AppWidgetHostView.getDefaultPaddingForWidget(context, provider, null)" in def, "bù đúng lề framework tự trừ")
        assertTrue("(r.left + r.right) to (r.top + r.bottom)" in def, "tổng px hai phía — phép chia/làm tròn ở AppWidgetSize")
        // Một lề cho mọi widget: khối dọc dựng sẵn cũng Sp.S (= KachiSpace.S).
        assertTrue("val p = dpi(ctx, Sp.S); setPadding(p, p, p, p)" in SourceRoots.body(views, "internal fun col(ctx: Context): LinearLayout"))
    }
}
