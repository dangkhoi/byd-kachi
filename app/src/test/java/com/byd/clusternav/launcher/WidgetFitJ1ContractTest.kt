package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ J1 (2.87, QA2 04/10 + soát vòng 4) — chỗ NỐI của nhãn ngắn, luật chữ tên, luật giá trị, đo lười ═══════════════════
 *
 * Luật thuần đã khoá ở `:core` (`FitLabelsTest` — chữ tên + phân biệt; `FitRulesRound4Test` — giá trị không `…`, cờ kẹt;
 * `GridFitWindowWidgetTest` J1 — hộp đo từ nhật ký QA2, đích chạm trước loại nhãn; `GridFitTest` — oracle phủ dự phòng +
 * nhãn ngắn; `WorkspacePhotoSourceTest` — khởi động không dựng ô hai lần). Dự án không dùng Robolectric ⇒ bài này khoá
 * rằng tầng vẽ ĐI QUA các luật đó (CLAUDE.md §8: hàm mới có chỗ gọi thật):
 *  1. [P2 QA2] ô nút khai nhãn ĐẦY + nhãn NGẮN (đã dịch 5 tiếng) lên chính `TextView`; bộ áp đổi chữ + chỗ `…` tại chỗ,
 *     nhãn đầy vào mô tả trợ năng; dấu chữ của ô băm bản đầy;
 *  2. [P2 QA2] luật chữ tên chạy SAU vòng kiểm lại, trên bố cục thật, hỏi `FitLabels.choose`;
 *  3. [P2 QA2] lưới không đọc được ⇒ giá trị tự co (`FitValues.valuePx` — QA3 thay `FitRules.valuePx`), ở lượt khớp VÀ ở đổ tại chỗ;
 *  4. [P3 soát 4] dạng PHỤ (dự phòng · nhãn ngắn · chỉ-icon) đo lười — chỉ khi dạng chính không cho lưới đọc được + chạm.
 */
class WidgetFitJ1ContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val scale by lazy { code("FitScale.kt") }
    private val probe by lazy { code("FitProbe.kt") }
    private val layout by lazy { code("FitGridLayout.kt") }
    private val names by lazy { code("FitNames.kt") }
    private val factory by lazy { code("ControlTileFactory.kt") }
    private val tele by lazy { code("WidgetTelemetry.kt") }

    @Test
    fun `1 - nhan ngan - bo dung khai hai ban, bo ap doi chu tai cho, dau chu theo ban day`() {
        val tile = SourceRoots.body(factory, "fun actionTile(def: ControlDef)")
        assertTrue(tile.contains("FitScale.named(this, def.displayLabel, def.displayShortLabel)"), "nhãn ĐÃ DỊCH, không nhãn gốc")
        val card = tele.substringAfter("internal class MiniCard(").substringBefore("internal fun miniCard(")
        assertTrue(card.contains("FitScale.named(subView, v.caption, v.captionShort, name = v.captionShort != null)"))
        val mini = SourceRoots.body(tele, "internal fun telemetryMini(")
        assertTrue(mini.contains("TelemetryRegistry.byId(id)?.displayShortLabel"), "nhãn ngắn của datum đã dịch")
        assertTrue(mini.contains("captionShort = if (named) short ?: v.label else null"), "đơn vị KHÔNG phải tên")
        val named = SourceRoots.body(scale, "fun named(tv: TextView")
        listOf("R.id.kachi_fit_full_text", "R.id.kachi_fit_short_text", "R.id.kachi_fit_use_short")
            .forEach { assertTrue(named.contains(it), "named ghi $it") }
        val ids = SourceRoots.text("src/main/res/values/ids.xml")
        listOf("kachi_fit_full_text", "kachi_fit_short_text", "kachi_fit_use_short").forEach { assertTrue(ids.contains(it)) }
        val variant = SourceRoots.body(scale, "fun variant(v: FitLabels.Variant)")
        assertTrue(variant.contains("TextUtils.TruncateAt.START") && variant.contains("tv.maxLines == 1"),
            "`…` ở đầu chỉ chữ MỘT dòng (StaticLayout.java:1078-1103 r47)")
        // Soát vòng 5 [P3] — ĐỔI GHIM có lý do: nhãn đầy ghi lên ô BẤM (`descHost`) THAY cả cây con ⇒ TalkBack mất chữ chọn
        // (`EV`) của nút nhiều lựa chọn và con số của ô đọc. Nay bản đầy nằm trên CHÍNH chữ tên (FitLabels.spoken — FitRulesRound5Test);
        // ô bấm chỉ nhận nhãn ở dạng chỉ-icon (nhãn `GONE`).
        assertTrue(variant.contains("val say = FitLabels.spoken(use, fullText(tv), b.text?.desc)") &&
            variant.contains("tv.contentDescription = say"), "nhãn ngắn ⇒ nhãn đầy làm mô tả của CHÍNH chữ tên")
        assertFalse(variant.contains("descHost.contentDescription"), "variant không ghi mô tả lên ô bấm")
        assertFalse(SourceRoots.body(scale, "private fun desc(dropLabels: Boolean)").contains("usesShort"),
            "mô tả ô bấm không còn theo nhãn ngắn (chỉ theo chỉ-icon)")
        assertTrue(SourceRoots.body(probe, "fun signature(fs: FitScale)").contains("fs.fullText(tv)"))
        listOf("addView(", "removeView", "inflate(").forEach { assertFalse(variant.contains(it)) }
    }

    @Test
    fun `2 - luat chu ten chay tren bo cuc that, sau kiem lai, truoc khi xet ket`() {
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int)")
        val pick = refit.indexOf("FitNames.pick(named, f.cellW, f.cellH, f.shape?.short == true)")
        assertTrue(pick > refit.indexOf("VERIFY_STEPS"), "sau vòng kiểm lại (k đã chốt)")
        assertTrue(pick in 0 until refit.indexOf("it.cell.fitted("), "trước khi xét kẹt / nhận số đo thật")
        assertTrue(SourceRoots.body(names, "fun pick(").contains("FitLabels.choose(tiles)"), "luật ở :core")
        assertTrue(SourceRoots.body(names, "private fun visible(").contains("l.getEllipsisStart(i), l.getEllipsisCount(i)"),
            "chữ THẤY được đọc từ Layout — cùng nguồn AOSP vẽ `…`")
        assertTrue(SourceRoots.body(names, "private fun options(").contains("FitLabels.variants(c.fs.hasShort(), c.fs.oneLine())"))
        listOf("addView(", "removeView", "requestLayout(", "post(", "post {").forEach {
            assertFalse(names.contains(it), "FitNames có '$it' ⇒ dựng lại/đẩy việc ra ngoài lượt đo")
        }
        assertTrue(SourceRoots.body(layout, "private fun applyAll(").contains("if (opt.short) FitLabels.Variant.SHORT else FitLabels.Variant.FULL"))
        assertTrue(SourceRoots.body(layout, "private fun combine(").contains("short = FitProbe.OPTIONS[i].short"))
        assertTrue(SourceRoots.body(probe, "private fun shape(").contains("fs.variant(if (opt.short) FitLabels.Variant.SHORT else FitLabels.Variant.FULL)"),
            "đo dò dạng nhãn ngắn trên CHỮ ngắn")
    }

    @Test
    fun `3 - luat gia tri - luoi khong doc duoc thi gia tri tu co, ca o luot khop lan do tai cho`() {
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int)")
        // QA3 — ĐỔI GHIM có lý do: luật giá trị chạy ở MỌI tầng (cặp giá trị/chú thích chia hàng theo nhu cầu cả khi lưới đọc
        // được — FitValueRow); tầng không đọc được giữ phần co tới sàn như cũ (`if (!legible)` nay nằm TRONG fitValues).
        assertTrue(refit.contains("if (named.fold(false) { any, c -> c.fs.fitValues(floors.textPx, f.legible) || any })"))
        val hook = SourceRoots.body(layout, "private fun onContentChanged(")
        assertTrue(hook.indexOf("items[child]?.fs?.fitValues(FitProbe.Floors.of(context).textPx, legible, tick = true)") in
            0 until hook.indexOf("due(child, measured = false)"),
            "giá trị mới co / chia lại hàng NGAY, trước khi xét đo dò (lưới không đọc được ⇒ đo dò thưa 30 s)")
        val fv = SourceRoots.body(scale, "fun fitValues(floorPx: Float, legible: Boolean, tick: Boolean = false)")
        // QA3 — ĐỔI GHIM có lý do: `FitRules.valuePx` (nội suy tuyến tính + sai số 1px — coi `06:58` 15px là vừa 39px) đã gỡ;
        // nay đo LẠI chữ (đã chừa chữ số) ở chính cỡ thử (FitValues.valuePx + FitValueRow.needOf — FitValuesTest).
        assertTrue(fv.contains("if (!legible) for (b in bases)"), "phần co tới sàn chỉ khi lưới không đọc được")
        assertTrue(fv.contains("FitValues.valuePx((t.px * scale).toFloat(), floorPx, avail, FitValueRow.needOf(tv))"))
        assertTrue(fv.contains("t.auto || b.free || tv in nameSet || tv.maxLines != 1 || tv.ellipsize == null"),
            "chỉ GIÁ TRỊ: không chữ tên, không chữ tự do (tên bài được `…`), không autosize")
        listOf("measure(", "requestLayout(").forEach { assertFalse(fv.contains(it), "fitValues chỉ đọc số đo + đổi cỡ chữ") }
    }

    @Test
    fun `4 - dang phu do luoi - chi khi dang chinh khong cho luoi doc duoc va dat cham`() {
        assertEquals(listOf(false, false, false, true, true, true, true, true), FitProbe.OPTIONS.map { it.secondary })
        assertTrue(SourceRoots.body(probe, "fun need(child: View, fs: FitScale, floors: Floors)").contains("secondary = false"))
        assertTrue(SourceRoots.body(probe, "fun more(").contains("secondary = true"))
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int)")
        assertTrue(refit.contains("if (!(f.legible && f.touchOk) && more(kids, floors)) continue"))
        assertTrue(refit.indexOf("more(kids, floors)") < refit.indexOf("applyAll(kids, f)"), "đo dạng phụ TRƯỚC khi áp")
        assertTrue(SourceRoots.body(layout, "private fun more(").contains("FitProbe.more(v, fs, floors, n, icon)"))
        assertTrue(SourceRoots.body(layout, "private fun combine(").contains("needs.all { it.shapes[i] != null }"),
            "dạng chưa đo không làm ứng viên")
        assertTrue(SourceRoots.body(layout, "private fun settled(").contains("if (n == null || o == null) n else FitRules.settle(o, n, grow)"),
            "chữ đổi ⇒ dạng phụ đo lại lười — rồi gộp bằng settle ở settledMore (soát vòng 5, WidgetFitR5ContractTest)")
    }

    @Test
    fun `ham moi co cho goi that va tep duoi 500 dong`() {
        val app = SourceRoots.moduleSourceRoots().filter { it.toString().contains("app") }
        fun strip(t: String) = t.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }
        fun uses(token: String, except: String): Boolean = app.any { root ->
            java.nio.file.Files.walk(root).use { s ->
                s.filter { it.toString().endsWith(".kt") && it.fileName.toString() != except }
                    .anyMatch { strip(it.toFile().readText()).contains(token) }
            }
        }
        listOf("FitLabels.choose(", "FitLabels.variants(", "FitLabels.visible(", "FitValues.valuePx(")
            .forEach { assertTrue(uses(it, "-"), "$it chưa có chỗ gọi ở :app") }
        assertTrue(uses("FitNames.pick(", "FitNames.kt"))
        assertTrue(uses("FitScale.named(", "FitScale.kt"))
        assertTrue(uses("FitProbe.more(", "FitProbe.kt"))
        assertTrue(uses("FitProbe.cut(", "FitProbe.kt"))
        assertTrue(uses(".fitValues(", "FitScale.kt"))
        assertTrue(uses(".variant(", "FitScale.kt"))
        listOf(
            "src/main/java/com/byd/clusternav/launcher/FitNames.kt",
            "src/main/java/com/byd/clusternav/launcher/FitScale.kt",
            "src/main/java/com/byd/clusternav/launcher/FitProbe.kt",
            "src/main/java/com/byd/clusternav/launcher/FitGridLayout.kt",
            "src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt",
            "src/main/java/com/byd/clusternav/launcher/WidgetTelemetry.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/FitLabels.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/FitRules.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/GridFit.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/IconRepeat.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/WorkspacePhotoSource.kt",
        ).forEach { rel ->
            val n = SourceRoots.text(rel).lines().size
            assertTrue(n <= 500, "$rel dài $n dòng — trần là 500 (CLAUDE.md §4.1)")
        }
    }
}
