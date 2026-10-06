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
        assertTrue(Regex("""\bcol\(ctx\)""").findAll(views).count() >= 3, "đồng hồ · tốc độ · trạng thái xe")
        assertTrue(Regex("""WidgetViews\.col\(ctx\)""").findAll(telemetry).count() >= 5, "vòng · số · huy hiệu · dải · thẻ chữ")
        assertTrue(media.contains("WidgetViews.col(ctx)"), "widget nhạc")
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
        assertTrue("val (padXDp, padYDp) = defaultPaddingDips(d)" in size)
        assertTrue("((w - paddingLeft - paddingRight).coerceAtLeast(0) / d).toInt() + padXDp" in size, "bề ngang khai = nội dung + bù")
        assertTrue("((h - paddingTop - paddingBottom).coerceAtLeast(0) / d).toInt() + padYDp" in size, "bề cao khai = nội dung + bù")
        assertTrue("runCatching { updateAppWidgetSize(Bundle(), wDp, hDp, wDp, hDp) }" in size)
        val def = SourceRoots.body(host, "private fun defaultPaddingDips(d: Float): Pair<Int, Int>")
        assertTrue("AppWidgetHostView.getDefaultPaddingForWidget(context, provider, null)" in def, "bù đúng lề framework tự trừ")
        assertTrue("((r.left + r.right) / d).toInt() to ((r.top + r.bottom) / d).toInt()" in def, "cùng phép (int)(px/density) của framework")
        // Một lề cho mọi widget: khối dọc dựng sẵn cũng Sp.S (= KachiSpace.S).
        assertTrue("val p = dpi(ctx, Sp.S); setPadding(p, p, p, p)" in SourceRoots.body(views, "internal fun col(ctx: Context): LinearLayout"))
    }
}
