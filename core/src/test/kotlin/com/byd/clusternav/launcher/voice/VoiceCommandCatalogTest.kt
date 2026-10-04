package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.Domain
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.TelemetryRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R3 · DANH SÁCH CÂU NÓI ĐƯỢC **KHÔNG ĐƯỢC NÓI DỐI** ══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-274-ux-voice-camera.html` R3.
 *
 * ## Bài canh mà năm bản sao trước đây KHÔNG có
 * Trước [VoiceCommandCatalog], tri thức *"câu nào nói được"* nằm ở 5 chỗ (xem KDoc tệp đó) và **không chỗ nào**
 * kiểm rằng câu bày ra thật sự phân tích được. Một bảng chép tay vì thế có thể quảng cáo *"bật đèn viền"* sau khi
 * owner đã gỡ nút đó, và không gì đỏ. Ở đây mỗi câu hiện trên màn bị đem qua đúng bộ phân tích mà xe dùng
 * ([VoiceIntentParser.parseOne]) và phải ra **đúng ý định mà cột "Kachi làm gì" đang hứa** — ở CẢ hai thứ tiếng.
 *
 * ## Tự dọn [Strings.current]
 * [VoiceCommandCatalog.groups] mặc định đọc [Strings.current] cho tiêu đề + cột *"làm gì"* (câu nói thì luôn tiếng
 * Việt — spec `kachi-i18n-zh-th-ms.html` R5), và vài bài cũ lật nó; mỗi bài tự trả nó về [Lang.VI] — `var` toàn cục
 * rò từ bài này sang bài khác là một họ lỗi rất khó lần ra (khuôn `LangCoverageTest`).
 */
class VoiceCommandCatalogTest {

    @AfterEach
    fun `dọn`() {
        Strings.current = Lang.VI
    }

    private val profiles = listOf("Vợ", "Mặc định")
    private val apps = listOf("YouTube", "Zalo")
    private val places = listOf("Nhà", "Công ty")

    private fun groups(confirmIds: Set<String> = emptySet()) =
        VoiceCommandCatalog.groups(profiles, apps, places, confirmIds)

    // ══ 1 · MỌI câu bày ra đều phân tích được, ra đúng ý định đã hứa ═════════════════════════════════════

    @Test
    fun `moi cau vi du deu parse ra dung y dinh da hua — VI va EN`() {
        listOf(Lang.VI, Lang.EN).forEach { lang ->
            Strings.current = lang
            val misses = ArrayList<String>()
            groups().forEach { g ->
                g.examples.forEach { e ->
                    val got = VoiceIntentParser.parseOne(e.phrase, profiles, apps, places)
                    if (!same(got, e.intent)) misses.add("$lang · ${g.id} · \"${e.phrase}\" → $got (chờ ${e.intent})")
                }
            }
            assertTrue(
                misses.isEmpty(),
                "câu bày ra mà bộ phân tích KHÔNG hiểu (hoặc hiểu thành việc khác) — ${misses.size} câu:\n" +
                    misses.joinToString("\n"),
            )
        }
    }

    /** Cột *"Kachi làm gì"* phải là câu THẬT của ý định phân tích ra — không phải một câu mô tả gần đúng. */
    @Test
    fun `cot Kachi lam gi la cau that cua y dinh phan tich ra`() {
        listOf(Lang.VI, Lang.EN).forEach { lang ->
            Strings.current = lang
            groups().forEach { g ->
                // Điểm đến là từ vựng MỞ (chỗ giữ chỗ đi qua bộ nhận dạng tự do) ⇒ chỉ so LOẠI, không so chuỗi.
                if (g.id == "nav") return@forEach
                g.examples.forEach { e ->
                    val got = VoiceIntentParser.parseOne(e.phrase, profiles, apps, places)
                    assertEquals(VoiceReply.preview(got), e.does, "$lang · ${g.id} · \"${e.phrase}\"")
                }
            }
        }
    }

    /**
     * spec `kachi-i18n-zh-th-ms.html` R5/OQ3 — ở **mọi** tiếng giao diện (kể cả EN): cột câu nói là ĐÚNG bộ câu tiếng
     * Việt (bộ nhận dạng chỉ nghe tiếng Việt), mọi câu parse ra đúng ý định đã hứa, còn tiêu đề nhóm + cột *"Kachi
     * làm gì"* theo tiếng màn. Trước bản này giao diện zh ghép *"bật &lt;nhãn chữ Hán&gt;"* — câu không parse được — và
     * bài canh chỉ lặp VI/EN nên không thấy. Chạy cả hai đường gọi: `lang` tường minh và mặc định `Strings.current`.
     */
    @Test
    fun `moi tieng giao dien - cau noi tieng Viet parse dung, tieu de va cot lam gi theo tieng man`() {
        val viPhrases = VoiceCommandCatalog.groups(profiles, apps, places, lang = Lang.VI)
            .map { g -> g.id to g.examples.map { it.phrase } }
        Lang.entries.forEach { lang ->
            val explicit = VoiceCommandCatalog.groups(profiles, apps, places, lang = lang)
            Strings.current = lang
            val byCurrent = groups()
            Strings.current = Lang.VI
            assertEquals(explicit, byCurrent, "$lang: mặc định phải = tiếng màn (Strings.current)")
            assertEquals(viPhrases, explicit.map { g -> g.id to g.examples.map { it.phrase } }, "$lang: câu nói phải là đúng bộ câu tiếng Việt")
            val misses = ArrayList<String>()
            explicit.forEach { g ->
                g.examples.forEach { e ->
                    val got = VoiceIntentParser.parseOne(e.phrase, profiles, apps, places)
                    if (!same(got, e.intent)) misses.add("$lang · ${g.id} · \"${e.phrase}\" → $got (chờ ${e.intent})")
                    // Điểm đến là chỗ giữ chỗ (từ vựng mở) ⇒ cột "làm gì" dịch chỗ giữ chỗ, không so chuỗi.
                    if (g.id != "nav") assertEquals(VoiceReply.preview(got, lang), e.does, "$lang · ${g.id} · \"${e.phrase}\"")
                }
                g.domain?.let { assertEquals(it.labelIn(lang), g.title, "$lang · tiêu đề nhóm ${g.id} phải theo tiếng màn") }
            }
            assertTrue(misses.isEmpty(), "$lang: câu bày ra mà bộ phân tích không hiểu — ${misses.size}:\n" + misses.joinToString("\n"))
            // Cột "làm gì" của câu dẫn đường: chỗ giữ chỗ theo tiếng màn (VI y nguyên câu cũ).
            val nav = explicit.first { it.id == "nav" }.examples.first { it.intent is VoiceIntent.Nav }
            assertEquals(VoiceReply.preview(VoiceIntent.Nav(Strings.t("điểm đến", "a place", lang)), lang), nav.does, "$lang · nav")
        }
    }

    /** Câu nằm đúng nhóm: ý định phân tích ra phải thuộc chính miền xe mà tiêu đề nhóm đang nói. */
    @Test
    fun `cau cua nhom mien xe deu thuoc dung mien do`() {
        val misses = ArrayList<String>()
        groups().filter { it.domain != null }.forEach { g ->
            g.examples.forEach { e ->
                val got = VoiceIntentParser.parseOne(e.phrase, profiles, apps, places)
                if (domainOf(got) != g.domain) misses.add("${g.id} · \"${e.phrase}\" → ${domainOf(got)}")
            }
        }
        assertTrue(misses.isEmpty(), "câu đứng sai nhóm ⇒ người dùng mở nhóm Lốp mà thấy câu về đèn:\n" + misses.joinToString("\n"))
    }

    // ══ 2 · Sinh từ bộ đăng ký: thêm một dòng là tự có câu ══════════════════════════════════════════════

    @Test
    fun `moi nut, datum, goi lenh va hanh dong launcher deu co it nhat mot cau`() {
        val ids = groups().flatMap { g -> g.examples.map { it.intent } }.mapNotNull { keyOf(it) }.toSet()
        listOf(
            ControlRegistry.ALL.map { it.id },
            VoiceTelemetry.SPOKEN.map { it.id },   // 2.88: trừ 13 mã lốp thô — [VoiceTelemetry]
            ActionMacros.ALL.map { it.id },
            LauncherActions.ALL.map { it.id },
        ).flatten().let { want ->
            assertEquals(emptyList<String>(), (want - ids).sorted(), "mã trong bộ đăng ký mà KHÔNG có câu nào gọi được")
        }
    }

    @Test
    fun `cau vi du khong rong, khong trung trong mot nhom, va nhom rong thi khong hien`() {
        listOf(Lang.VI, Lang.EN).forEach { lang ->
            Strings.current = lang
            groups().forEach { g ->
                assertTrue(g.title.isNotBlank(), "$lang · nhóm ${g.id} không có tiêu đề")
                assertTrue(g.examples.isNotEmpty(), "$lang · nhóm ${g.id} rỗng mà vẫn hiện")
                val phrases = g.examples.map { it.phrase }
                assertEquals(phrases.size, phrases.distinct().size, "$lang · nhóm ${g.id} có câu TRÙNG: $phrases")
                g.examples.forEach {
                    assertTrue(it.phrase.isNotBlank(), "$lang · câu rỗng ở nhóm ${g.id}")
                    assertTrue(it.does.isNotBlank(), "$lang · câu \"${it.phrase}\" không nói được nó làm gì")
                    // Dấu ngoặc của nhãn (*"Pin (SOC)"*) là thứ để ĐỌC BẰNG MẮT, không ai đọc nó ra thành tiếng.
                    assertTrue(!it.phrase.contains('('), "$lang · câu \"${it.phrase}\" còn dấu ngoặc của nhãn")
                }
            }
        }
        Strings.current = Lang.VI
        // Danh sách ĐỘNG rỗng ⇒ nhóm tương ứng tự vắng (không quảng cáo thứ máy này chưa có).
        val bare = VoiceCommandCatalog.groups().map { it.id }
        assertTrue("profile" !in bare, "chưa có hồ sơ nào mà vẫn bày nhóm hồ sơ")
        assertTrue(bare.any { it.startsWith("dom_") }, "nhóm miền xe phải có mặt kể cả khi chưa nạp hồ sơ/app")
    }

    @Test
    fun `danh sach dong dung du lieu THAT cua may nay`() {
        val all = groups().flatMap { g -> g.examples.map { it.phrase } }
        assertTrue(all.any { it.contains("Vợ") }, "tên hồ sơ thật phải xuất hiện, không phải chữ mẫu")
        assertTrue(all.any { it.contains("YouTube") }, "app đã cài phải xuất hiện")
        assertTrue(all.any { it.contains("Nhà") }, "nhãn sổ địa chỉ phải xuất hiện")
        // Đúng ba nguồn ⇒ đổi hồ sơ/app/sổ thì danh sách đổi theo, không giữ bản chụp cũ.
        val other = VoiceCommandCatalog.groups(listOf("Bố"), listOf("Zing MP3"), listOf("Trường"))
            .flatMap { g -> g.examples.map { it.phrase } }
        assertTrue(other.none { it.contains("Vợ") || it.contains("YouTube") || it.contains("Nhà") })
    }

    // ══ 3 · Hai bảng phụ bị canh HAI CHIỀU (không thể thành chỗ lén thêm một câu lệnh) ══════════════════

    @Test
    fun `bang dong tu tieng Anh khop VoiceGrammar VERBS hai chieu`() {
        VoiceCommandCatalog.EN_VERBS.forEach { (verb, form) ->
            val words = VoiceLexicon.tokenize(form).map { it.norm }
            val hit = VoiceGrammar.VERBS.firstOrNull { it.first == words }
            assertTrue(hit != null, "dạng Anh \"$form\" KHÔNG có trong VoiceGrammar.VERBS ⇒ nói ra mà máy không hiểu")
            assertEquals(verb, hit!!.second, "\"$form\" map về động từ khác trong ngữ pháp")
        }
        assertEquals(
            VoiceVerb.entries.toSet(), VoiceCommandCatalog.EN_VERBS.keys,
            "mỗi VoiceVerb phải có đúng một dạng Anh — thiếu một cái là danh sách EN rơi về tiếng Việt lặng lẽ",
        )
    }

    @Test
    fun `cum danh dau ho so va menh de chi o deu la cum bo phan tich THAT SU doc duoc`() {
        listOf(Lang.VI, Lang.EN).forEach { lang ->
            Strings.current = lang
            val phrase = groups().first { it.id == "profile" }.examples.first().phrase
            val words = VoiceLexicon.tokenize(phrase).map { it.norm }
            assertTrue(
                VoiceProfileNames.MARKERS.any { m -> words.windowed(m.size).any { it == m } },
                "$lang · câu đổi hồ sơ phải mang một cụm đánh dấu của VoiceProfileNames.MARKERS: \"$phrase\"",
            )
            // Mệnh đề chỉ ô phải có mặt ở CẢ hai thứ tiếng — thiếu nó thì *"đưa app vào ô số N"* (đúng câu owner
            // yêu cầu 2026-09-14) không được quảng cáo ở đâu cả.
            val slot = groups().first { it.id == "apps" }.examples
                .firstOrNull { (it.intent as? VoiceIntent.OpenApp)?.slot != null }
            assertTrue(slot != null, "$lang · nhóm app không có ví dụ nào nêu số ô")
        }
    }

    /**
     * ⚠ [SOÁT 2.74] Hai phép chọn CHỮ của họ NHẠC, cả hai canh trên bảng THẬT của bộ phân tích:
     *  1. Từ chỉ nhạc ([VoiceCommandCatalog.MEDIA_NOUN_VI]) phải là một mục CÓ THẬT của
     *     [VoiceSynonyms.MEDIA_WORDS] **và** có dạng có dấu — nếu không, nó là một từ vựng thứ hai khai lén.
     *  2. Câu tự đứng một mình (*bài tiếp theo* · *bài trước*) **không được** kết thúc bằng một từ chỉ đối tượng.
     *     Bản đầu lấy "dạng nhiều khoảng trắng nhất" ra *"quay lại bài"*: parser nhận, nhưng người đọc sẽ tự nối
     *     thêm *"…bài hát trước"* và câu nối thêm thì không parse được ⇒ danh sách dạy sai.
     */
    @Test
    fun `cau mau nhac dung tu THAT cua bang va khong bo lung ve doi tuong`() {
        val key = VoiceCommandCatalog.MEDIA_NOUN_VI
        assertTrue(key in VoiceSynonyms.MEDIA_WORDS, "\"$key\" không có trong VoiceSynonyms.MEDIA_WORDS")
        val accented = SherpaSpokenWords.ACCENTED[key]
        assertTrue(!accented.isNullOrBlank(), "\"$key\" chưa có dạng CÓ DẤU ở SherpaSpokenWords.ACCENTED")
        Strings.current = Lang.VI
        val media = groups().first { it.id == "media" }.examples
        assertTrue(
            media.any { it.phrase.endsWith(accented!!) },
            "câu mẫu nhạc phải dùng đúng từ đã chọn (\"$accented\"): ${media.map { it.phrase }}",
        )
        val standalone = media.filter {
            (it.intent as? VoiceIntent.Media)?.op in setOf(VoiceMediaOp.NEXT, VoiceMediaOp.PREV)
        }
        assertEquals(2, standalone.size, "phải có đúng hai câu tự đứng một mình (bài tiếp/bài trước)")
        val dangling = standalone.map { it.phrase }
            .filter { VoiceLexicon.tokenize(it).last().norm in VoiceSynonyms.MEDIA_WORDS }
        assertEquals(emptyList<String>(), dangling, "câu tự đứng một mình mà bỏ lửng vế đối tượng")
    }

    // ══ 4 · Cờ "sẽ hỏi lại" thật sự nối dây (tập rỗng ⇒ không hỏi gì) ══════════════════════════════════

    @Test
    fun `co se-hoi-lai theo dung tap ma nguoi dung da tich`() {
        assertTrue(groups().none { g -> g.examples.any { it.asksFirst } }, "mặc định (tập rỗng) KHÔNG câu nào hỏi lại")
        val id = VoiceRiskTable.askableIds().first()
        val on = groups(setOf(id)).flatMap { it.examples }.filter { it.asksFirst }
        assertTrue(on.isNotEmpty(), "tích mã $id mà không câu nào báo là sẽ hỏi lại ⇒ cờ chết")
        assertTrue(
            on.all { VoiceRiskTable.confirmId(it.intent) == id },
            "chỉ ĐÚNG mã được tích mới hỏi lại, các câu khác không: ${on.map { it.phrase }}",
        )
    }

    // ── Tiện ích ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Hai ý định có cùng **việc** không.
     *
     * So theo **mã**, và với nút thì có đường lùi theo **nhãn**: hai nút trùng nhãn thì câu dựng từ nhãn KHÔNG
     * phân biệt nổi chúng — đó là giới hạn của chính cái nhãn, không phải lỗi bộ phân tích (cùng lời thừa nhận
     * ở `VoiceGrammarCoverageTest.moi nut trong ControlRegistry deu co it nhat mot cau nhan dung`).
     */
    private fun same(got: VoiceIntent, want: VoiceIntent): Boolean = got::class == want::class && when {
        // ⚠ [SOÁT 2.74] **Giá trị cũng phải khớp**, không chỉ mã: bản đầu chỉ so `id`, nên lật cực của một nút
        // (*"tắt X"* gửi 1) vẫn xanh ở đây — chỉ bài `cot Kachi lam gi…` bắt được, tức bài mang tên *"parse ra
        // đúng ý định đã hứa"* lại KHÔNG canh phần "đúng". [ĐO] lật `val on` ở `VoiceCommandCatalog` thì bài này
        // đỏ ngay sau khi thêm hai phép so dưới.
        got is VoiceIntent.Control && want is VoiceIntent.Control ->
            (got.value == want.value && got.relative == want.relative) &&
                (got.id == want.id ||
                    ControlRegistry.byId(got.id)?.label == ControlRegistry.byId(want.id)?.label)
        // Hai ca mà [VoiceCommandCatalog.keyOf] cố ý gộp cả họ (xem KDoc ở đó) nhưng bài canh thì soi CHẶT hơn:
        // bố cục so đúng preset, còn điểm đến là từ vựng mở nên chỉ so LOẠI.
        got is VoiceIntent.Layout && want is VoiceIntent.Layout -> got.preset == want.preset
        got is VoiceIntent.Nav -> true
        else -> keyOf(got) != null && keyOf(got) == keyOf(want)
    }

    private fun keyOf(i: VoiceIntent): String? = VoiceCommandCatalog.keyOf(i)

    private fun domainOf(i: VoiceIntent): Domain? = when (i) {
        is VoiceIntent.Control -> ControlRegistry.byId(i.id)?.domain
        is VoiceIntent.Read -> TelemetryRegistry.byId(i.datumId)?.domain
        is VoiceIntent.Macro -> ActionMacros.byId(i.id)?.domain
        else -> null
    }
}
