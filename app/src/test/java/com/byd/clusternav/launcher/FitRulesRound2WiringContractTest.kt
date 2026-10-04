package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L5 WIDGET-FIT-ALL — soát vòng 2 (2.87): chỗ NỐI của bốn bản vá vào các quyết định thuần đã test ở `FitRulesRound2Test`
 * (`:core`). Dự án không dùng Robolectric nên cây view thật không dựng được trong JVM; bài này khoá rằng tầng vẽ đi
 * qua đúng các quyết định đó, và các lỗi soát vòng 2 không quay lại:
 *  - [P1] trạng thái cắt đọc lúc đổ tại chỗ có thể CHƯA BIẾT (`TextView` WRAP vừa `nullLayouts()`) ⇒ `due` không chốt,
 *    lượt đo kế quyết; lượt thưa để ô còn cắt ⇒ nhận số đo thật; số bị bẻ đôi qua hai dòng là cắt;
 *  - [P2] dấu chữ không phụ thuộc hiện/ẩn của nhãn (bộ áp sở hữu) ⇒ chụp lúc đo dò = dạng đang hiện;
 *  - [P3] weight chỉ ghi khi bộ áp sở hữu; ngân sách `…` chỉ cho chữ tự do được KHAI (tên bài/nghệ sĩ).
 */
class FitRulesRound2WiringContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val layout by lazy { code("FitGridLayout.kt") }
    private val scale by lazy { code("FitScale.kt") }
    private val probe by lazy { code("FitProbe.kt") }
    private val core by lazy { SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/FitRules.kt") }

    @Test
    fun `P1 - trang thai cat chua biet khong bi chot, luot do ke quyet tren bo cuc that`() {
        val clip = SourceRoots.body(probe, "fun clip(fs: FitScale)")
        assertTrue(clip.contains("tv.layout == null } -> FitRules.Clip.UNKNOWN"), "bố cục null ⇒ CHƯA BIẾT, không phải 'không cắt'")
        assertTrue(clip.indexOf("Clip.UNKNOWN") < clip.indexOf("clipped(fs)"), "xét CHƯA BIẾT trước khi đọc cắt")
        val due = SourceRoots.body(layout, "private fun due(v: View, measured: Boolean)")
        assertTrue(due.contains("{ FitRules.known(FitProbe.clip(fs), measured) }"),
            "đọc trạng thái cắt qua clip (có CHƯA BIẾT), lười — chỉ khi chữ đổi")
        assertFalse(due.contains("FitProbe.clipped("), "đọc clipped thẳng = đọc 'không cắt' khi bố cục null (lỗi vòng 2)")
        val hook = SourceRoots.body(layout, "private fun onContentChanged(")
        assertTrue(hook.contains("due(child, measured = false)"), "đổ tại chỗ = TRƯỚC lượt đo ⇒ được phép CHƯA BIẾT")
        assertTrue(hook.contains("FitRules.Verdict.WAIT -> if (!isLayoutRequested) requestLayout()"),
            "chưa biết ⇒ bảo đảm có lượt đo để xét lại (chính TextView đã xin trong ca thường)")
        // Sau measureAll: chữ không bố cục = không được vẽ ⇒ không cắt — CHƯA BIẾT không được chặn mãi (CLAUDE.md §3).
        assertTrue(SourceRoots.body(layout, "private fun grew()").contains("due(v, measured = true)"))
        // `grew` chạy SAU measureAll ở đường cache ⇒ trạng thái cắt đọc trên bố cục thật.
        val measure = SourceRoots.body(layout, "override fun onMeasure(")
        assertTrue(measure.indexOf("measureAll(f.cellW, f.cellH, force = false)") < measure.indexOf("if (grew())"))
        // :core: Cell.check đi qua nhịp reprobe (FitRulesTest khoá nhịp) và chặn CHƯA BIẾT trước nó.
        val check = SourceRoots.body(core, "fun check(sigSame: Boolean")
        assertTrue(check.contains("if (c == Clip.UNKNOWN) return Verdict.WAIT") && check.contains("reprobe("))
        assertTrue(check.indexOf("Clip.UNKNOWN") < check.indexOf("reprobe("))
    }

    @Test
    fun `P1 - luot thua de o con cat thi nhan so do that, khong ghi dau chu dang cat`() {
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int)")
        assertTrue(refit.contains("it.fresh = raw.takeIf { got !== raw }"), "giữ số đo thật khi settle giữ số cũ")
        assertTrue(refit.contains("it.cell.probed(SystemClock.elapsedRealtime(), kept = got !== raw)"))
        // ĐỔI GHIM (soát vòng 3, P3): `fitted` nhận thêm "chữ đang hiện là chữ đã đo dò" — kẹt chỉ chốt trên chữ ấy.
        assertTrue(
            refit.contains("it.cell.fitted(it.fs?.let { fs -> FitProbe.clipped(fs) } == true, probedContent = probed(it))"),
            "sau kiểm lại",
        )
        assertTrue(refit.contains("it.fresh?.let { n -> it.need = n }"), "nhận số đo thật rồi khớp lại")
        assertTrue(refit.contains("if (adopt.isEmpty()) break"), "dừng khi không còn ô nào phải nhận")
        // `settled` trả CHÍNH số đo mới khi mọi dạng nhận nó — không thì `got !== raw` luôn đúng, `kept` sai.
        assertTrue(SourceRoots.body(layout, "private fun settled(").contains("if (shapes.indices.all { shapes[it] === fresh.shapes[it] }) return fresh"))
        // Đo dò vẫn chỉ ở lượt khớp (không thêm lượt đo dò trong vòng nhận số thật).
        assertEquals(1, Regex("FitProbe\\.need\\(").findAll(refit).count())
    }

    @Test
    fun `P1 - so bi be doi qua hai dong la cat`() {
        val text = SourceRoots.body(probe, "private fun clippedText(")
        assertTrue(text.contains("for (i in 1 until shown) if (FitRules.splitsNumber(l.text, l.getLineStart(i))) return true"))
    }

    @Test
    fun `P2 - dau chu khong phu thuoc hien an cua nhan`() {
        val sig = SourceRoots.body(probe, "fun signature(fs: FitScale)")
        // ĐỔI GHIM (J1): chữ băm theo bản ĐẦY ([FitScale.fullText]) — bản đang hiện (đầy/ngắn) do bộ áp sở hữu, cùng lẽ
        // hiện/ẩn của nhãn; băm `tv.text` thì đo dò dạng nhãn ngắn để ô ở bản ngắn ⇒ dấu không bao giờ khớp.
        assertTrue(sig.contains("FitRules.sigStep(h, fs.fullText(tv).toString(), tv.visibility, fs.isLabel(tv))"))
        assertFalse(sig.contains("+ tv.visibility"), "băm hiện/ẩn của mọi chữ = dấu dạng chỉ-icon ≠ dạng đang hiện")
        assertTrue(SourceRoots.body(scale, "fun isLabel(tv: TextView)").contains(".isLabel"))
        // Dấu chụp ở need() đi qua đúng hàm đó.
        assertTrue(SourceRoots.body(probe, "fun need(child: View").contains("signature(fs)"))
    }

    @Test
    fun `P3 - ngan sach chu tu do chi cho chu duoc khai`() {
        val base = scale.substringAfter("private class Base(").substringBefore("private val bases")
        assertTrue(base.contains("FitRules.freeText(v.getTag(R.id.kachi_fit_free_text) == true"), "KHAI, không suy từ maxLines")
        assertTrue(SourceRoots.body(scale, "fun markFree(tv: TextView)").contains("setTag(R.id.kachi_fit_free_text, true)"))
        // Đúng hai bộ dựng khai: widget nhạc (tên bài + nghệ sĩ) và ô nén khi chỗ gọi xin (`w_media`).
        val media = code("MediaWidgetView.kt")
        assertEquals(2, Regex("FitScale\\.markFree\\(").findAll(media).count(), "tên bài + nghệ sĩ")
        val tele = code("WidgetTelemetry.kt")
        val card = tele.substringAfter("internal class MiniCard(").substringBefore("fun set(")
        assertEquals(2, Regex("if \\(free\\) FitScale\\.markFree\\(this\\)").findAll(card).count(), "số + dòng phụ khi xin")
        assertEquals(2, Regex("FitScale\\.markFree\\(").findAll(tele).count(), "không khai chỗ nào khác (dấu chưa kiểm, số đọc)")
        val mini = SourceRoots.body(code("WidgetViews.kt"), "private fun mini(")
        val free = mini.lines().filter { "free = true" in it }
        assertEquals(1, free.size, "chỉ ô nén nhạc khai chữ tự do: $free")
        assertTrue(free.single().contains("\"w_media\""))
        // Bỏ chú thích (như `SourceRoots.codeOf`): nhắc tên hàm trong KDoc không phải chỗ gọi.
        fun strip(t: String) = t.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }
        val app = SourceRoots.moduleSourceRoots().filter { it.toString().contains("app") }
        val callers = app.flatMap { root ->
            java.nio.file.Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.filter { it.fileName.toString() != "FitScale.kt" && strip(it.toFile().readText()).contains("FitScale.markFree(") }
            .map { it.fileName.toString() }.toSet()
        assertEquals(setOf("MediaWidgetView.kt", "WidgetTelemetry.kt"), callers)
    }
}
