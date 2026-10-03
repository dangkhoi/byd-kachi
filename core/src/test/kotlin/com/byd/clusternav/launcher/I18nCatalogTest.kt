package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * ═══ CƠ CHẾ BẢNG DỊCH ZH/TH/MS + Strings.t/pick/f ════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-zh-th-ms.html` §4.2 + R-nf1 (*"VI và EN không đổi một byte"*). Bài này khoá:
 *  1. định dạng tệp (thoát, chú thích, dòng chỉ-en, dòng hỏng, trùng khoá) — người dịch đang làm theo định dạng ấy;
 *  2. thứ tự tra: cặp → chỉ-en → **tiếng Anh** (không bao giờ tiếng Việt) cho ZH/TH/MS;
 *  3. phép thay `{n}` (đổi thứ tự, đối số chứa `{1}`, `null`);
 *  4. VI/EN của `t`/`pick`/`f` y từng byte cho MỌI cặp chữ quét được trong mã;
 *  5. lựa chọn ngôn ngữ (mã đĩa, AUTO giữ luật cũ — OQ1) và ngôn ngữ giọng nói (R6).
 */
class I18nCatalogTest {

    @AfterEach
    fun `dọn`() {
        Strings.current = Lang.VI
    }

    // ── 1 · Định dạng tệp ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parse doc dung escape, bo chu thich va dong trang, nhan dong chi-en`() {
        val t = I18nCatalog.parse(
            "\uFEFF# đầu tệp\n" +
                "\n" +
                "   \n" +
                "Mở\tOpen\t打开\r\n" +
                "a\\tb\tline\\none\tx\\\\y\\tz\\nw\n" +
                "\tCancel\t取消\n" +
                "# Đóng\tClose\t关\n" +
                "giá \\$\tprice\t价格 \\q\n",
        )
        assertEquals(emptyList<String>(), t.problems)
        assertEquals(emptyList<String>(), t.duplicates)
        assertEquals("打开", t.pairs["Mở" to "Open"], "CRLF phải được cắt \\r")
        assertEquals("x\\y\tz\nw", t.pairs["a\tb" to "line\none"], "\\t \\n \\\\ giải cả ba ô")
        assertEquals("取消", t.enOnly["Cancel"])
        assertNull(t.pairs["Đóng" to "Close"], "dòng mở bằng # là chú thích")
        assertEquals("价格 \\q", t.pairs["giá \\$" to "price"], "gạch chéo ngược khác giữ nguyên chữ")
        assertEquals(4, t.size)
    }

    @Test
    fun `parse gom dong hong va trung khoa, khong nem`() {
        val t = I18nCatalog.parse(
            "chỉ hai\tô\n" +
                "a\tb\tc\td\n" +
                "x\t\tbản dịch\n" +
                "x\ty\t\n" +
                "Mở\tOpen\t打开\n" +
                "Mở\tOpen\t开\n" +
                "\tOpen\t开启\n" +
                "\tOpen\t开\n",
        )
        assertEquals(4, t.problems.size, "2 ô · 4 ô · en rỗng · bản dịch rỗng: ${t.problems}")
        assertTrue(t.problems.first().startsWith("1:"), "lỗi phải nêu số dòng: ${t.problems}")
        assertEquals(2, t.duplicates.size, t.duplicates.toString())
        assertEquals("打开", t.lookup("Mở", "Open"), "trùng khoá giữ dòng ĐẦU")
        assertEquals("开启", t.lookup("Khác", "Open"))
    }

    @Test
    fun `escape la nguoc cua unescape`() {
        listOf("a\tb", "x\ny", "c:\\dir", "\\t thật", "", "plain").forEach {
            assertEquals(it, I18nCatalog.unescape(I18nCatalog.escape(it)), "khứ hồi hỏng: «$it»")
        }
        assertEquals("a\\tb\\nc\\\\d", I18nCatalog.escape("a\tb\nc\\d"))
    }

    // ── 2 · Thứ tự tra ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cap dung truoc dong chi-en, roi null`() {
        val t = I18nCatalog.parse("Mở\tOpen\t打开\nĐang mở\tOpen\t已打开\n\tOpen\t开\n")
        assertEquals("打开", t.lookup("Mở", "Open"), "từ đồng hình: LỆNH")
        assertEquals("已打开", t.lookup("Đang mở", "Open"), "từ đồng hình: TRẠNG THÁI")
        assertEquals("开", t.lookup("Hé", "Open"), "cặp chưa có ⇒ dòng chỉ-en")
        assertNull(t.lookup("Đóng", "Close"))
    }

    @Test
    fun `ZH TH MS thieu dong thi lui ve tieng Anh, khong ve tieng Viet`() {
        val vi = "§ chuỗi thử không bao giờ có trong bảng"
        val en = "§ probe string that is never cataloged"
        for (lang in I18nPairs.TRANSLATED) {
            assertEquals(en, Strings.t(vi, en, lang), "$lang: t")
            assertEquals(en, Strings.pick(vi, en, lang), "$lang: pick có bản Anh")
            assertEquals(vi, Strings.pick(vi, null, lang), "$lang: pick KHÔNG có bản Anh ⇒ tiếng Việt (như EN)")
            assertEquals(vi, Strings.pick(vi, "  ", lang), "$lang: bản Anh trắng = chưa dịch")
            assertEquals("§ 3 probes", Strings.fIn(lang, "§ {0} mẫu thử", "§ {0} probes", 3), "$lang: f lùi về MẪU Anh")
        }
        assertNull(I18nCatalog.lookup(Lang.VI, "Mở", "Open"), "VI/EN không có bảng")
        assertNull(I18nCatalog.lookup(Lang.EN, "Mở", "Open"))
    }

    @Test
    fun `bang that nam tren classpath, doc duoc, khong loi dinh dang`() {
        for (lang in I18nPairs.TRANSLATED) {
            assertNotNull(javaClass.getResource(I18nCatalog.resourcePath(lang)), "thiếu ${I18nCatalog.resourcePath(lang)}")
            val t = I18nCatalog.table(lang)
            assertEquals(emptyList<String>(), t.problems, "$lang: dòng hỏng")
            assertEquals(emptyList<String>(), t.duplicates, "$lang: trùng khoá")
            assertTrue(t === I18nCatalog.table(lang), "$lang: nạp LẠI mỗi lần tra (phải nạp một lần)")
        }
        assertEquals(0, I18nCatalog.table(Lang.VI).size)
    }

    @Test
    fun `tra song song tu nhieu luong cho cung mot ket qua`() {
        val pool = Executors.newFixedThreadPool(8)
        try {
            val jobs = (1..64).map { i ->
                Callable { Strings.t("Bật", "On", I18nPairs.TRANSLATED[i % 3]) to I18nCatalog.table(I18nPairs.TRANSLATED[i % 3]) }
            }
            val results = pool.invokeAll(jobs).map { it.get() }
            I18nPairs.TRANSLATED.forEachIndexed { k, lang ->
                val mine = results.filterIndexed { i, _ -> (i + 1) % 3 == k }
                assertEquals(1, mine.map { System.identityHashCode(it.second) }.distinct().size, "$lang: hai bảng khác nhau")
                assertEquals(1, mine.map { it.first }.distinct().size, "$lang: kết quả lệch giữa các luồng")
            }
        } finally {
            pool.shutdownNow()
        }
    }

    // ── 3 · Phép thay {n} ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `fill thay mot luot, cho doi thu tu, giu cho khong co doi so`() {
        assertEquals("b a", Strings.fill("{1} {0}", arrayOf<Any?>("a", "b")), "đổi thứ tự")
        assertEquals("x {1}!", Strings.fill("{0}!", arrayOf<Any?>("x {1}", "KHÔNG")), "đối số chứa {1} không bị thay tiếp")
        assertEquals("null · 3 · 2.5", Strings.fill("{0} · {1} · {2}", arrayOf<Any?>(null, 3, 2.5)), "như \"\$x\" của Kotlin")
        assertEquals("{5} {a} {} {0", Strings.fill("{5} {a} {} {0", arrayOf<Any?>("z")), "chỗ không hợp lệ giữ nguyên")
        assertEquals("{0} giây", Strings.fill("{0} giây", emptyArray<Any?>()), "không đối số ⇒ giữ mẫu")
        assertEquals("1{0}", Strings.fill("{0}{1}", arrayOf<Any?>(1, "{0}")))
    }

    @Test
    fun `f doc Strings current, fIn doc ngon ngu truyen vao`() {
        Strings.current = Lang.EN
        assertEquals("by 3", Strings.f("{0} nấc", "by {0}", 3))
        assertEquals("3 nấc", Strings.fIn(Lang.VI, "{0} nấc", "by {0}", 3))
        Strings.current = Lang.VI
        assertEquals("3 nấc", Strings.f("{0} nấc", "by {0}", 3))
        assertEquals("by 3", Strings.fIn(Lang.EN, "{0} nấc", "by {0}", 3))
    }

    // ── 4 · VI/EN y từng byte ───────────────────────────────────────────────────────────────────────────────────

    /**
     * MỌI cặp chữ cố định trong mã (bộ quét) — `t`/`pick` trả đúng chữ nguồn ở VI/EN, `f` không có đối số trả đúng
     * mẫu. Đây là chứng cứ máy cho R-nf1 trên toàn bộ ~300 lời gọi, không chỉ vài mẫu chọn tay.
     */
    @Test
    fun `VI va EN cua t pick f giu nguyen chu nguon cho moi cap quet duoc`() {
        val literal = I18nPairs.calls.filter { it.isLiteralPair }
        assertTrue(literal.size >= 250, "quét được ${literal.size} cặp — quá ít")
        literal.forEach { c ->
            val vi = c.vi!!.value!!
            val en = c.en!!.value!!
            assertEquals(vi, Strings.t(vi, en, Lang.VI), c.where)
            assertEquals(en, Strings.t(vi, en, Lang.EN), c.where)
            assertEquals(vi, Strings.pick(vi, en, Lang.VI), c.where)
            assertEquals(en.ifBlank { vi }, Strings.pick(vi, en, Lang.EN), c.where)
            assertEquals(vi, Strings.fIn(Lang.VI, vi, en), c.where)
            assertEquals(en, Strings.fIn(Lang.EN, vi, en), c.where)
        }
    }

    /** Dữ liệu registry: `labelIn`/`displayLabel` ở VI/EN y như luật cũ (`en` trống ⇒ `vi`). */
    @Test
    fun `VI va EN cua nhan registry giu nguyen luat cu`() {
        LangCoverageFixtures.localizedRows().forEach { row ->
            assertEquals(row.label, row.labelIn(Lang.VI))
            assertEquals(row.labelEn?.takeIf { it.isNotBlank() } ?: row.label, row.labelIn(Lang.EN))
        }
        (TelemetryRegistry.ALL.map { Triple(it.shortLabel, it.shortEn, it.labelEn) to it::shortLabelIn } +
            ControlRegistry.ALL.map { Triple(it.shortLabel, it.shortEn, it.labelEn) to it::shortLabelIn }).forEach { (f, fn) ->
            val (short, shortEn, labelEn) = f
            assertEquals(short, fn(Lang.VI))
            assertEquals(shortEn?.takeIf { it.isNotBlank() } ?: labelEn?.takeIf { it.isNotBlank() } ?: short, fn(Lang.EN))
        }
        ControlRegistry.ALL.forEach { c ->
            assertEquals(c.args, c.argsIn(Lang.VI))
            assertEquals(if (c.argsEn.size == c.args.size && c.argsEn.isNotEmpty()) c.argsEn else c.args, c.argsIn(Lang.EN))
            // ZH/TH/MS: lệch số phần tử ⇒ tiếng Việt cả danh sách (như EN); đủ ⇒ từng phần tử lùi về bản Anh.
            I18nPairs.TRANSLATED.forEach { l -> assertEquals(c.args.size, c.argsIn(l).size, "${c.id} $l") }
        }
    }

    // ── 5 · Lựa chọn ngôn ngữ ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `ma dia va giai nghia LangMode`() {
        assertEquals(listOf("auto", "vi", "en", "zh", "th", "ms"), LangMode.entries.map { it.code })
        assertEquals(listOf("vi", "en", "zh", "th", "ms"), Lang.entries.map { it.code })
        assertEquals(Lang.ZH, LangMode.ZH.resolve("vi"), "chọn tường minh thắng locale máy")
        assertEquals(Lang.TH, LangMode.TH.resolve(null))
        assertEquals(Lang.MS, LangMode.MS.resolve("en"))
        LangMode.entries.filter { it != LangMode.AUTO }.forEach { assertEquals(it.code, it.resolve("xx").code) }
        assertEquals(LangMode.ZH, LangMode.of("zh"))
        assertEquals(LangMode.AUTO, LangMode.of("zh-CN"), "mã lạ ⇒ AUTO (tương thích ngược)")
    }

    /** OQ1 (owner 2026-10-03): "Theo xe" KHÔNG tự chọn ZH/TH/MS — máy vi ⇒ VI, còn lại ⇒ EN, như trước. */
    @Test
    fun `AUTO giu luat cu - may zh th ms ra tieng Anh`() {
        assertEquals(Lang.VI, LangMode.AUTO.resolve("vi"))
        listOf("zh", "th", "ms", "en", "ja", "", null).forEach {
            assertEquals(Lang.EN, LangMode.AUTO.resolve(it), "AUTO với locale máy '$it'")
        }
    }

    @Test
    fun `nhan bo chon viet bang chinh tieng do`() {
        assertEquals("简体中文", LangMode.ZH.label())
        assertEquals("ไทย", LangMode.TH.label())
        assertEquals("Bahasa Melayu", LangMode.MS.label())
        assertEquals("Tiếng Việt", LangMode.VI.label())
        assertEquals("English", LangMode.EN.label())
        assertEquals("Theo xe", LangMode.AUTO.label())
        assertEquals("By car", I18nPairs.inLang(Lang.EN) { LangMode.AUTO.label() })
        assertEquals(LangMode.entries.size, LangMode.entries.map { it.label() }.toSet().size, "hai mục cùng nhãn")
    }

    /** R6: giọng nói = EN nếu giao diện EN, còn lại VI (một gói nhận dạng tiếng Việt duy nhất). */
    @Test
    fun `ngon ngu giong noi suy thuan tu giao dien`() {
        assertEquals(Lang.EN, voiceLangOf(Lang.EN))
        listOf(Lang.VI, Lang.ZH, Lang.TH, Lang.MS).forEach {
            assertEquals(Lang.VI, voiceLangOf(it), "$it")
            assertEquals(Lang.VI, it.voice)
        }
        assertEquals(Lang.EN, Lang.EN.voice)
    }

    @Test
    fun `do dai hien thi khong tinh dau ghep chu Thai`() {
        assertEquals(2, I18nPairs.displayLength("น้ำ"), "น + ้ (dấu) + ำ")
        assertEquals(4, I18nPairs.displayLength("简体中文"))
        assertEquals(7, I18nPairs.displayLength("Tyre FL"))
        assertEquals(6, I18nPairs.capFor(I18nPairs.SHORT_CAPS, Lang.ZH))
        assertEquals(LangCoverageFixtures.SHORT_CAP, I18nPairs.capFor(I18nPairs.SHORT_CAPS, Lang.TH))
        assertNull(I18nPairs.capFor("", Lang.MS))
    }
}
