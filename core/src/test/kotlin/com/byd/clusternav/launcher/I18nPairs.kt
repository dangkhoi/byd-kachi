package com.byd.clusternav.launcher

import com.byd.clusternav.comfort.Pm25Filter
import com.byd.clusternav.launcher.voice.VoiceFeatureGone
import com.byd.clusternav.launcher.voice.VoicePlaces
import com.byd.clusternav.launcher.voice.VoiceRiskTable
import com.byd.clusternav.testsupport.I18nCallScanner
import com.byd.clusternav.testsupport.I18nScripts
import java.nio.file.Files
import java.nio.file.Path

/**
 * ═══ DANH SÁCH CẶP (vi, en) CẦN DỊCH SANG ZH/TH/MS — một nguồn cho cả bài xuất lẫn bài phủ ═════════════════════
 *
 * Spec `docs/specs/kachi-i18n-zh-th-ms.html` §4.2: (a) quét nguồn lấy mọi cặp chữ CỐ ĐỊNH ở lời gọi dịch
 * ([I18nCallScanner]); (b) duyệt registry LÚC CHẠY — phần lớn chữ Anh của registry là đối số THEO VỊ TRÍ (hàm `t()`
 * của `TelemetryRegistry`, `SettingsEntry`, enum…), regex trên `labelEn =` sẽ bỏ sót gần hết, nên phải đọc đối tượng
 * thật. Cặp từ hai nguồn gộp theo khoá `(vi, en)` — đúng khoá bảng dịch ([I18nCatalog]).
 *
 * Định dạng tệp xuất (người dịch đang làm theo — không đổi): `vi⇥en⇥kind⇥where⇥cap`, thoát như bảng dịch.
 */
internal object I18nPairs {

    /** Loại cặp. Thứ tự = ưu tiên khi một cặp xuất hiện ở nhiều nơi (loại có trần chữ đứng trước). */
    enum class Kind(val code: String) {
        SHORT("short"), ARGS("args"), LABEL("label"), SUB("sub"), DESC("desc"), DATA("data"), INLINE("inline"), VOICE("voice")
    }

    data class Row(val vi: String, val en: String, val kind: Kind, val where: String, val cap: String = "")

    /** Ba tiếng dịch bằng bảng. */
    val TRANSLATED: List<Lang> = listOf(Lang.ZH, Lang.TH, Lang.MS)

    /**
     * Trần nhãn NGẮN (chip thanh trên, hàng nút của ô nhóm) — spec §4.5: zh ≤ 6 ký tự, th/ms ≤ [LangCoverageFixtures.SHORT_CAP]
     * ký tự HIỂN THỊ (dấu ghép chữ Thái không tính — xem [displayLength]).
     */
    val SHORT_CAPS: String = "zh6,th${LangCoverageFixtures.SHORT_CAP},ms${LangCoverageFixtures.SHORT_CAP}"

    /**
     * Trần viết tắt góc bánh của bảng lốp (bản EN là 2 chữ `FL`, bài EN canh ≤ 3). [SUY] ô bánh hẹp hơn chip;
     * số này chưa đo trên máy ảo — T6 chụp màn rồi chỉnh tại đây nếu cần.
     */
    const val TYRE_CAPS: String = "zh3,th6,ms4"

    // ── (a) Quét nguồn ──────────────────────────────────────────────────────────────────────────────────────────

    /** Mọi lời gọi dịch trong `app/` + `core/` (đọc tệp MỘT lần cho cả lượt chạy). */
    val calls: List<I18nCallScanner.Call> by lazy { I18nCallScanner.scanRepo() }

    /** Cặp chữ cố định từ lời gọi dịch. Chữ ở `…/voice/…` mang loại [Kind.VOICE]. */
    fun scanned(): List<Row> = calls.filter { it.isLiteralPair && it.en!!.value!!.isNotBlank() }.map {
        Row(it.vi!!.value!!, it.en!!.value!!, if ("/voice/" in it.path) Kind.VOICE else Kind.INLINE, it.where)
    }

    // ── (b) Dữ liệu lúc chạy ────────────────────────────────────────────────────────────────────────────────────

    /** Mọi chữ Anh nằm trong DỮ LIỆU (không phải lời gọi dịch có chữ cố định) mà màn hình/giọng nói chạm tới. */
    fun runtime(): List<Row> {
        val out = ArrayList<Row>()
        fun add(vi: String?, en: String?, kind: Kind, where: String, cap: String = "") {
            if (vi == null || en.isNullOrBlank()) return              // không có bản Anh ⇒ pick() hiện tiếng Việt
            out.add(Row(vi, en, kind, where, cap))
        }
        LangCoverageFixtures.localizedRows().forEach { add(it.label, it.labelEn, Kind.LABEL, idOf(it)) }
        // Nhãn NGẮN thực dùng — cùng bậc lùi với `shortLabelIn` (khoá theo cặp đang HIỆN trên chip).
        TelemetryRegistry.ALL.filter { it.short != null || it.shortEn != null }.forEach {
            add(it.shortLabel, it.shortEn?.takeIf(String::isNotBlank) ?: it.labelEn, Kind.SHORT, "telemetry:${it.id}.short", SHORT_CAPS)
        }
        ControlRegistry.ALL.filter { it.short != null || it.shortEn != null }.forEach {
            add(it.shortLabel, it.shortEn?.takeIf(String::isNotBlank) ?: it.labelEn, Kind.SHORT, "control:${it.id}.short", SHORT_CAPS)
        }
        TyreCorner.values().forEach { add(it.shortLabel, it.shortLabelEn, Kind.SHORT, "TyreCorner.${it.name}.short", TYRE_CAPS) }
        ControlRegistry.ALL.filter { it.argsEn.size == it.args.size }.forEach { c ->
            c.args.indices.forEach { add(c.args[it], c.argsEn[it], Kind.ARGS, "control:${c.id}.args[$it]") }
        }
        CapabilityGroups.ALL.forEach { add(it.sub, it.subEn, Kind.SUB, "group:${it.id}.sub") }
        SettingsCatalog.GROUPS.forEach { add(it.sub, it.subEn, Kind.SUB, "settings-group:${it.id}.sub") }
        LauncherRequirements.ALL.forEach { r ->
            add(r.losesWhatIfMissing, r.losesWhatIfMissingEn, Kind.DATA, "req:${r.id}.loses")
            add(r.userAction, r.userActionEn, Kind.DATA, "req:${r.id}.action")
        }
        CapabilityTestPlan.items().forEach {
            add(it.label, it.labelEn, Kind.LABEL, "captest:${it.id}")
            add(it.descVi, it.descEn, Kind.DESC, "captest:${it.id}.desc")
        }
        TelemetryEnums.ALL.forEach { t ->
            t.entries.forEach { (code, p) -> add(p.first, p.second, Kind.DATA, "enum-table:${t.id}#$code") }
        }
        TopStripConfig.choices().forEach { add(it.label, it.labelEn, Kind.LABEL, "pick:${it.id}") }
        CapabilityCatalog.allIncludingHidden().forEach { add(it.label, it.labelEn, Kind.LABEL, "pick:${it.id}") }
        WidgetCatalog.CURATED.forEach { add(it.label, it.labelEn, Kind.LABEL, "widget-pick:${it.id}") }
        KeyCtlTargets.groups().forEach { add(it.label, it.labelEn, Kind.LABEL, "keyctl-group:${it.id}") }
        VoiceRiskTable.CONTROL_RULES.forEach { add(it.whyVi, it.whyEn, Kind.DATA, "risk:${it.controlId}") }
        VoiceFeatureGone.ALL.forEach { add(it.label, it.labelEn, Kind.VOICE, "gone:${it.words.joinToString(" ")}") }
        listOf(
            Pm25Filter.EXCELLENT, Pm25Filter.GOOD, Pm25Filter.LOW_GRADE,
            Pm25Filter.MIDDLE, Pm25Filter.HEAVY, Pm25Filter.SERIOUS,
        ).forEach { add(Pm25Filter.levelLabelVi(it), Pm25Filter.levelLabelEn(it), Kind.LABEL, "pm25:$it") }
        // Ba chỗ mà chữ VIỆT là một hằng (lời gọi có đối số không-phải-chữ ⇒ quét nguồn không lấy được cặp): đọc
        // chính hàm hiển thị ở hai thứ tiếng, không chép lại chữ (đổi chữ ở mã ⇒ bài này tự theo).
        for ((vi, where) in listOf(VoicePlaces.HOME to "place:HOME", VoicePlaces.WORK to "place:WORK")) {
            add(vi, inLang(Lang.EN) { VoicePlaces.displayLabel(vi) }, Kind.LABEL, where)
        }
        add(
            HomeUiState.DEFAULT_PROFILE, inLang(Lang.EN) { ProfileNames.display(HomeUiState.DEFAULT_PROFILE) },
            Kind.LABEL, "profile:default",
        )
        add(inLang(Lang.VI) { LangMode.AUTO.label() }, inLang(Lang.EN) { LangMode.AUTO.label() }, Kind.LABEL, "langmode:AUTO")
        return out
    }

    /** Chạy [block] với [Strings.current] = [lang], TRẢ LẠI giá trị cũ dù ném (biến toàn cục — xem `LangCoverageTest`). */
    fun <T> inLang(lang: Lang, block: () -> T): T {
        val before = Strings.current
        Strings.current = lang
        try {
            return block()
        } finally {
            Strings.current = before
        }
    }

    private fun idOf(x: Localized): String = when (x) {
        is TelemetrySpec -> "telemetry:${x.id}"
        is ControlDef -> "control:${x.id}"
        is CapabilityGroup -> "group:${x.id}"
        is WidgetDef -> "widget:${x.id}"
        is ActionMacro -> "macro:${x.id}"
        is SettingsGroup -> "settings-group:${x.id}"
        is SettingsEntry -> "settings:${x.id}"
        is LauncherRequirement -> "req:${x.id}"
        is LauncherActionDef -> "action:${x.id}"
        is Enum<*> -> "${x.declaringJavaClass.simpleName}.${x.name}"
        else -> "${x.javaClass.simpleName}:${x.label}"
    }

    // ── Gộp + xuất ──────────────────────────────────────────────────────────────────────────────────────────────

    /** Một dòng tệp xuất — một cặp (vi, en) duy nhất, gộp mọi nơi dùng. */
    data class ExportRow(val vi: String, val en: String, val kind: Kind, val wheres: List<String>, val cap: String) {
        val where: String get() = if (wheres.size <= MAX_WHERES) wheres.joinToString("; ")
        else wheres.take(MAX_WHERES).joinToString("; ") + "; +${wheres.size - MAX_WHERES}"
    }

    private const val MAX_WHERES = 4

    /** Mọi cặp cần dịch, theo thứ tự gặp (nguồn trước, dữ liệu sau). Trần chữ: lấy trần CHẶT nhất từng tiếng. */
    val pairs: List<ExportRow> by lazy { merge(scanned() + runtime()) }

    fun merge(rows: List<Row>): List<ExportRow> = rows.groupBy { it.vi to it.en }.map { (key, group) ->
        val caps = group.map { it.cap }.filter { it.isNotEmpty() }
        val cap = TRANSLATED.mapNotNull { l -> caps.mapNotNull { capFor(it, l) }.minOrNull()?.let { "${l.code}$it" } }
            .joinToString(",")
        ExportRow(key.first, key.second, group.minOf { it.kind }, group.map { it.where }.distinct(), cap)
    }

    /** Trần chữ của [lang] trong chuỗi `zh6,th14,ms14`; `null` = không trần. */
    fun capFor(cap: String, lang: Lang): Int? =
        cap.split(',').firstOrNull { it.startsWith(lang.code) }?.removePrefix(lang.code)?.toIntOrNull()

    /**
     * Độ dài HIỂN THỊ: số code point trừ dấu ghép không chiếm chỗ (Mn/Me) — chữ Thái chồng dấu thanh/nguyên âm lên
     * phụ âm (`น้ำ` = 3 code point, 2 ô chữ). Chữ Hán/Latin: 1 code point = 1 ký tự.
     */
    fun displayLength(s: String): Int = s.codePoints().filter {
        val t = Character.getType(it)
        t != Character.NON_SPACING_MARK.toInt() && t != Character.ENCLOSING_MARK.toInt()
    }.count().toInt()

    /**
     * Từ Latin được phép đứng nguyên trong bản zh/th — luật chung với bài canh tài nguyên `:app`, khai MỘT chỗ ở
     * [I18nScripts.NAME_WORDS] (testFixtures). T3/T4 thêm tên ở đó, kèm lý do.
     */
    val NAME_WORDS: Map<String, String> get() = I18nScripts.NAME_WORDS

    /** Bản dịch chỉ gồm tên Latin — xem [I18nScripts.nameOnly]. */
    fun nameOnly(value: String): Boolean = I18nScripts.nameOnly(value)

    /**
     * Chữ Anh mà bản ms ĐÚNG LÀ trùng nguyên văn (từ mượn chuẩn của tiếng Mã Lai, cùng chính tả) — khoá theo chữ Anh,
     * kèm lý do. Ngoài danh sách này, `v == en` ở bảng ms là bản chép cột Anh chưa dịch ([audit]). Mục chết (không còn
     * dòng ms nào trùng chữ Anh ấy) làm `I18nCoverageTest` đỏ.
     */
    val MS_SAME_AS_EN: Map<String, String> = mapOf(
        "{0} item" to "\"item\" là từ mượn chuẩn của tiếng Mã Lai (DBP); số nhiều không biến hình",
        "Neutral" to "số N của hộp số — xe bán ở Malaysia ghi \"Neutral\"; \"neutral\" cũng là từ mượn chuẩn",
        "Manual" to "\"manual\" là từ mượn chuẩn (chế độ chỉnh tay); dịch \"Manual\" → \"Manual\"",
        "Odometer" to "\"odometer\" là từ mượn chuẩn, cùng chính tả",
        "Gear" to "\"gear\" là từ mượn chuẩn (hộp số) trong tiếng Mã Lai ô tô",
    )

    const val HEADER = "vi\ten\tkind\twhere\tcap"

    fun tsv(rows: List<ExportRow>): String = buildString {
        append(HEADER).append('\n')
        rows.forEach { p ->
            listOf(p.vi, p.en, p.kind.code, p.where, p.cap).joinTo(this, "\t") { I18nCatalog.escape(it) }
            append('\n')
        }
    }

    /** `core/build/i18n/` dưới gốc repo — chỗ bài xuất ghi `pairs.tsv` và `missing-<code>.tsv`. */
    fun outDir(): Path = I18nCallScanner.repoRoot().resolve("core/build/i18n").also { Files.createDirectories(it) }

    /** Cặp CHƯA có dòng trong bảng của [lang] (tra đúng như lúc chạy: cặp → chỉ-en). */
    fun missing(lang: Lang): List<ExportRow> = pairs.filter { I18nCatalog.lookup(lang, it.vi, it.en) == null }

    /** Ghi `missing-<code>.tsv`, trả đường dẫn (để thông báo lỗi chỉ thẳng tệp cho người dịch). */
    fun writeMissing(lang: Lang): Path =
        outDir().resolve("missing-${lang.code}.tsv").also { Files.writeString(it, tsv(missing(lang))) }

    // ── Soát một bảng (dùng cho `I18nCoverageTest`; tách ra để bài thử-phá chứng minh từng phép soát CÓ bắt) ──────

    /** Kết quả soát một bảng: bốn nhóm lỗi, rỗng hết = đạt. */
    data class Audit(val missing: List<ExportRow>, val quality: List<String>, val orphans: List<String>, val broken: List<String>) {
        val ok: Boolean get() = missing.isEmpty() && quality.isEmpty() && orphans.isEmpty() && broken.isEmpty()
    }

    private val PLACEHOLDER = Regex("""\{\d}""")

    private fun placeholders(s: String): List<String> = PLACEHOLDER.findAll(s).map { it.value }.sorted().toList()

    private fun hasScript(s: String, script: Character.UnicodeScript): Boolean = I18nScripts.hasScript(s, script)

    /** Soát [table] của [lang] với danh sách cặp [rows] — xem KDoc `I18nCoverageTest` về bảy luật. */
    fun audit(lang: Lang, table: I18nCatalog.Table, rows: List<ExportRow> = pairs): Audit {
        val quality = ArrayList<String>()
        val script = when (lang) {
            Lang.ZH -> Character.UnicodeScript.HAN
            Lang.TH -> Character.UnicodeScript.THAI
            else -> null
        }
        for (p in rows) {
            val v = table.lookup(p.vi, p.en) ?: continue
            val at = "«${p.en}» → «$v» (${p.where})"
            if (placeholders(v) != placeholders(p.en)) quality += "chỗ trống {n} lệch bản Anh: $at"
            if (LangCoverageFixtures.hasVietnameseMark(v)) quality += "còn dấu tiếng Việt: $at"
            if (script != null && !hasScript(v, script) && !nameOnly(v)) quality += "không có chữ $script: $at"
            // ms là chữ Latin ⇒ phép "có chữ của tiếng đó" ở trên không áp được; bản chép nguyên cột Anh lọt mọi phép
            // khác. Cùng luật `ban Ma Lai khong trung nguyen van ban Anh` của 5 thư mục tài nguyên (soát 2.87 · P3).
            if (lang == Lang.MS && v == p.en && !nameOnly(v) && p.en !in MS_SAME_AS_EN) quality += "trùng nguyên văn bản Anh: $at"
            capFor(p.cap, lang)?.let { cap ->
                val len = displayLength(v)
                if (len > cap) quality += "dài $len > trần $cap: $at"
            }
        }
        val usedPairs = rows.map { it.vi to it.en }.toSet()
        val usedEn = rows.map { it.en }.toSet()
        val orphans = table.pairs.keys.filter { it !in usedPairs }.map { "«${it.first}» / «${it.second}»" } +
            table.enOnly.keys.filter { it !in usedEn }.map { "(chỉ-en) «$it»" }
        return Audit(
            missing = rows.filter { table.lookup(it.vi, it.en) == null },
            quality = quality,
            orphans = orphans,
            broken = table.problems + table.duplicates.map { "trùng khoá $it" },
        )
    }
}
