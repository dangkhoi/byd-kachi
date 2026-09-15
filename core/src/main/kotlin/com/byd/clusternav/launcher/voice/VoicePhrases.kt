package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.TelemetryRegistry

/**
 * Kết quả dựng ngữ pháp — [entries] là thứ đem đi nhận dạng, phần còn lại là **số đo để nói ra**.
 *
 * @property entries mục ngữ pháp, thứ tự ổn định (cụm dài trước, rồi từ đơn, cuối cùng là `[unk]`).
 * @property phrasesKept số **cụm nhiều từ** dựng được từ nhãn (đo độ phủ của mô hình lên danh mục xe).
 * @property phrasesDropped cụm bị loại vì mô hình không có ít nhất một từ trong đó — giữ nguyên văn để nhật ký
 *   nói được *"mất cái gì"*, không phải một con số trơ.
 * @property wordsUnknown từ (đã bỏ dấu) mà mô hình **không có cách viết nào** — đây là danh sách đáng đọc nhất:
 *   mỗi mục là một chỗ người lái nói mà máy không thể nghe ra, dù nói đúng.
 */
data class VoicePhraseSet(
    val entries: List<String>,
    val phrasesKept: Int,
    val phrasesDropped: List<String>,
    val wordsUnknown: List<String>,
) {
    /** Mảng JSON cho `Recognizer(model, 16000f, grammarJson)`. */
    fun json(): String = entries.joinToString(",", "[", "]") { "\"" + escape(it) + "\"" }

    /** Một dòng cho nhật ký — **không dịch** (dòng cho người phát triển, cùng lệ `PermissionReport.logLine`). */
    fun logLine(): String =
        "ngữ pháp: ${entries.size} mục (cụm giữ $phrasesKept, bỏ ${phrasesDropped.size}, " +
            "từ mô hình không có ${wordsUnknown.size})"

    private companion object {
        /** Chỉ `"` và `\` mới phải thoát: chữ Việt đi thẳng dạng UTF-8, JSON nhận (RFC 8259 §7). */
        fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}

/**
 * ═══ V1 pha NGHE · DỰNG NGỮ PHÁP CHO BỘ NHẬN DẠNG — **SINH TỪ DANH MỤC + TỪ ĐIỂN MÔ HÌNH** ═══════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R10**. Thuần Kotlin (`:core`) ⇒ kiểm off-car bằng đúng tệp từ điển
 * thật rút ra từ mô hình ([VoskWordList]).
 *
 * ## Bài toán: hai bên nói hai kiểu chữ
 * Tầng chữ của V1 làm việc trên chữ **đã bỏ dấu** ([VoiceLexicon.deaccent]) — và nó đúng, vì nguồn chữ là bàn
 * phím xe. Nhưng mô hình nhận dạng thì **[ĐO] dùng chữ CÓ DẤU**: từ điển 19.529 mục của `vosk-model-small-vn-0.4`
 * có `đèn` · `bật` · `tắt` · `nhiệt` · `độ` (kiểm 2026-09-14). Khai ngữ pháp bằng chữ không dấu là bảo bộ giải mã
 * *"chỉ được phép nghe ra `den`"* trong khi người lái nói `đèn` — hai chuỗi âm vị khác nhau (tiếng Việt mã hoá
 * thanh điệu vào âm vị), tức tự tay làm hỏng phần nhận dạng rồi đổ cho mô hình.
 *
 * ## Cách giải: KHÔNG chép tay bảng chữ có dấu — **tra ngược qua chính từ điển mô hình**
 * Chép một bảng *"bat → bật"* là dựng **bản sao thứ hai** của từ vựng, đúng họ lỗi mà cả [VoiceGrammar] lẫn
 * [VoiceSynonyms] sinh ra để chặn (`unitPrefs` ×4, `customLayout` ×2). Thay vào đó: gom từ điển mô hình theo
 * **dạng đã bỏ dấu**, rồi với mỗi từ trong từ vựng của Kachi, hỏi từ điển *"anh viết từ này thế nào"*.
 *
 * Hệ quả cố ý và có lợi: một từ bỏ dấu ra `bat` thì **mọi** cách viết của mô hình (`bạt` · `bát` · `bắt` · `bật`)
 * đều được nhận. Nghe sai thanh điệu — điểm yếu lớn nhất của một mô hình 32 MB — không còn làm hỏng câu lệnh, vì
 * [VoiceIntentParser] bỏ dấu lại ở đầu bên kia. Đây là CLAUDE.md §7 đúng nghĩa: khác biệt giữa các mô hình lộ ra
 * qua **đo đạc**, không qua một nhánh `if` cho từng mô hình.
 *
 * ## Hai loại mục, hai việc khác nhau
 * Vosk dựng một **LM bigram** từ danh sách này (`recognizer.cc:336` `opts.ngram_order = 2`), nên:
 *  • **cụm nhiều từ** (nhãn nút/datum/gói/app/hồ sơ) cho LM biết *thứ tự* — *"đèn đọc"* phải nối được với nhau;
 *  • **từ đơn** (động từ · từ đồng nghĩa · số · liên từ · từ xác nhận) chỉ cần có mặt trong từ vựng là đủ, vì
 *    chúng ghép với nhau theo vô số thứ tự mà bigram tự lo.
 * Nổ tổ hợp bị chặn ở đúng chỗ này: từ đơn **không** nhân chéo, chỉ cụm nhãn mới nhân — mà nhãn thì đã có dấu
 * sẵn nên gần như không phải nhân gì.
 *
 * ## `[unk]` bắt buộc có mặt
 * Không có nó, bộ giải mã **phải** ép mọi tiếng động thành một câu lệnh — ho một cái là xe mở khoá. Có nó thì
 * phần ngoài từ vựng rơi vào `[unk]` và [VoiceIntentParser] trả [VoiceUnknownReason.NO_VERB] như một câu gõ sai.
 * [ĐO] từ điển mô hình có sẵn mục `[unk]`.
 */
object VoicePhrases {

    /** Mục "ngoài từ vựng" của Kaldi/Vosk — xem KDoc lớp về vì sao nó là mục BẮT BUỘC. */
    const val UNK = "[unk]"

    /**
     * Trần số cách viết cho MỘT dạng bỏ dấu.
     *
     * [ĐO] `mot` có 8 cách viết trong từ điển, `bay` có 9. Không chặn thì một từ đệm như `toi` kéo vào hàng chục
     * mục mà không mục nào là câu lệnh. Sắp xếp trước khi cắt ⇒ kết quả **tất định** (cùng vào, cùng ra) — điều
     * kiện để bài canh đếm được số mục.
     */
    private const val MAX_SPELLINGS = 8

    /**
     * Dựng ngữ pháp.
     *
     * @param vocabulary từ điển mô hình (chữ có dấu, đã thường hoá). Rỗng ⇒ trả [VoicePhraseSet] rỗng kèm toàn bộ
     *   từ vựng vào [VoicePhraseSet.wordsUnknown] — chỗ gọi phải coi đó là *"mô hình chưa sẵn sàng"*, KHÔNG được
     *   lặng lẽ dựng một ngữ pháp trống rồi bật mic (bộ giải mã sẽ ép mọi thứ thành rác).
     * @param profiles tên hồ sơ tài xế đang có — **động**, người dùng tự đặt.
     * @param apps nhãn ứng dụng đã cài — **động**, và là lý do không tên gói nào được viết cứng (CLAUDE.md §7).
     */
    fun build(
        vocabulary: Set<String>,
        profiles: List<String> = emptyList(),
        apps: List<String> = emptyList(),
        installed: Set<String> = emptySet(),
        places: List<String> = emptyList(),
    ): VoicePhraseSet {
        val spellings = index(vocabulary)
        val phrases = LinkedHashSet<String>()
        val dropped = ArrayList<String>()
        val singles = LinkedHashSet<String>()
        val unknown = LinkedHashSet<String>()

        // ── (1) Cụm NHIỀU TỪ dựng từ nhãn: nhãn đã CÓ DẤU sẵn, chỉ cần soi xem mô hình có đủ từ không ──
        labelPhrases(profiles, apps, installed, places).forEach { raw ->
            val words = words(raw)
            if (words.isEmpty()) return@forEach
            val mapped = words.map { w -> resolve(w, vocabulary, spellings) }
            if (mapped.any { it == null }) {
                // Ghi lý do bằng NGUYÊN VĂN nhãn: một dòng "bỏ 37 cụm" không ai lần lại được là bỏ cái gì.
                dropped.add(raw)
                // Từ nào đọc được thì vẫn cho vào từ vựng đơn — nửa nhãn nhận ra được vẫn hơn không gì.
                words.forEachIndexed { i, w -> if (mapped[i] != null) singles.add(mapped[i]!!) else unknown.add(deaccent(w)) }
                return@forEach
            }
            val phrase = mapped.joinToString(" ") { it!! }
            if (words.size > 1) phrases.add(phrase) else singles.add(phrase)
            // ⚠ Thêm MỌI cách viết của từng từ, không chỉ cách viết đã chọn cho cụm.
            //
            // [ĐO] [VoiceSynonyms] khai bằng chữ **không dấu** (đó là hợp đồng của nó — cùng dạng mà
            // [VoiceLexicon.deaccent] trả về). Nên cụm `"khoa xe"` khớp thẳng vào từ `khoa` (= khoa/ngành) của
            // mô hình, và nếu chỉ có mình nó trong ngữ pháp thì bộ giải mã **chỉ được phép** nghe ra thanh
            // ngang — trong khi người lái nói `khoá`. Cho cả họ `khoa · khoá · khóa · khoả …` vào dạng TỪ ĐƠN
            // thì bigram tự ghép lại, còn [VoiceIntentParser] bỏ dấu ở đầu bên kia nên mọi thanh đều ra cùng
            // một ý định. Sai thanh điệu là điểm yếu lớn nhất của một mô hình 32 MB; đây là chỗ vô hiệu hoá nó.
            words.forEach { w -> spellings[deaccent(w)]?.let(singles::addAll) }
        }

        // ── (2) TỪ ĐƠN: mọi từ trong từ vựng KHÔNG DẤU của Kachi, nở ra mọi cách viết mà mô hình biết ──
        deaccentedVocabulary().forEach { token ->
            val found = spellings[token].orEmpty()
            if (found.isEmpty()) unknown.add(token) else singles.addAll(found)
        }

        // Thứ tự: cụm trước (chúng mang cấu trúc), rồi từ đơn, cuối là `[unk]`. Ổn định ⇒ đếm được.
        val entries = ArrayList<String>(phrases.size + singles.size + 1)
        entries.addAll(phrases)
        singles.filterNot { it in phrases }.forEach { entries.add(it) }
        entries.add(UNK)
        return VoicePhraseSet(entries, phrases.size, dropped.sorted(), unknown.sorted())
    }

    /**
     * Cách viết của mô hình cho một từ [w] của Kachi.
     *
     * Thứ tự thử: **đúng y nguyên** (nhãn có dấu khớp thẳng) → tra theo dạng bỏ dấu (lấy cách viết đầu, đã sắp
     * xếp ⇒ tất định). `null` = mô hình không biết từ này dưới bất kỳ dấu nào.
     */
    private fun resolve(w: String, vocabulary: Set<String>, spellings: Map<String, List<String>>): String? {
        val lower = w.lowercase()
        if (lower in vocabulary) return lower
        return spellings[deaccent(lower)]?.firstOrNull()
    }

    /**
     * Từ điển mô hình gom theo dạng **bỏ dấu**: `"bat" → ["bat", "bát", "bạt", "bắt", "bật", …]`.
     *
     * Sắp xếp + cắt ở [MAX_SPELLINGS] ⇒ tất định và có trần. Bỏ mọi mục không phải từ thật (`<eps>`, `!SIL`,
     * `#0`…): chúng là ký hiệu nội bộ của FST, đưa vào ngữ pháp là khai một "từ" mà không âm nào phát ra được.
     */
    private fun index(vocabulary: Set<String>): Map<String, List<String>> {
        val out = HashMap<String, MutableList<String>>(vocabulary.size)
        vocabulary.forEach { raw ->
            if (raw.isEmpty() || raw == UNK) return@forEach
            if (!raw[0].isLetterOrDigit()) return@forEach
            out.getOrPut(deaccent(raw)) { ArrayList(2) }.add(raw)
        }
        return out.mapValues { (_, v) -> v.sorted().take(MAX_SPELLINGS) }
    }

    /**
     * MỌI nhãn có thể trở thành một cụm — **sinh từ chính bộ đăng ký**, không chép tay một chữ nào.
     *
     * Đi lại 4 bộ đăng ký thay vì đọc [VoiceGrammar.terms] vì `terms()` trả chữ **đã bỏ dấu** (nó phục vụ tầng
     * so khớp), còn ở đây cần đúng bản gốc có dấu. Cùng NGUỒN, hai cách trình bày — không phải hai bản sao.
     *
     * `args`/`argsEn` của nút SELECT cũng vào đây: *"chỉnh chế độ đèn pha sang **auto**"* không nói được nếu
     * *"auto"* không có trong ngữ pháp.
     */
    private fun labelPhrases(
        profiles: List<String>,
        apps: List<String>,
        installed: Set<String>,
        places: List<String>,
    ): List<String> {
        val out = ArrayList<String>(1024)
        ControlRegistry.ALL.forEach { c ->
            out.add(c.label); c.labelEn?.let(out::add); c.short?.let(out::add); c.shortEn?.let(out::add)
            out.addAll(c.args); out.addAll(c.argsEn)
            VoiceSynonyms.CONTROL[c.id]?.let(out::addAll)
        }
        TelemetryRegistry.ALL.forEach { t ->
            out.add(t.label); t.labelEn?.let(out::add); t.short?.let(out::add); t.shortEn?.let(out::add)
            VoiceSynonyms.TELEMETRY[t.id]?.let(out::addAll)
        }
        ActionMacros.ALL.forEach { m -> out.add(m.label); m.labelEn?.let(out::add) }
        LauncherActions.ALL.forEach { a -> out.add(a.label); a.labelEn?.let(out::add) }
        out.addAll(VoiceSynonyms.MEDIA_WORDS)
        out.addAll(VoiceSynonyms.NAV_WORDS)
        out.addAll(profiles)
        out.addAll(apps)
        // V1.1 — tên APP ĐÍCH (*"…bằng YouTube Music"*), **chỉ khi app ấy có trên máy**.
        //
        // Cùng luật với danh sách app ở trên: ngữ pháp chỉ khai thứ gọi được. Khai *"spotify"* trên một chiếc xe
        // không cài Spotify là mở thêm một đường cho bộ giải mã nghe nhầm vào một app không tồn tại — mà nó
        // KHÔNG đổi lại được gì, vì câu ấy rồi cũng chỉ nhận được câu trả lời "chưa cài".
        VoiceAppTargets.ALL.filter { it.packageIn(installed) != null }.forEach { out.addAll(it.spoken) }
        // Sổ địa chỉ (spec `kachi-voice-addresses.html` R6) — nhãn ĐÃ LƯU + cách nói dựng sẵn của nhãn chuẩn
        // tương ứng. CÙNG luật với hai dòng trên: chỉ khai thứ gọi được thật; sổ trống ⇒ không khai gì.
        out.addAll(VoicePlaces.spokenPhrases(places))
        return out
    }

    /**
     * Toàn bộ từ vựng **không dấu** mà tầng chữ đang dùng — động từ · liên từ · số · từ đệm · cụm hỏi · xác nhận.
     *
     * Mỗi nguồn ở đây đã có **đúng một** nơi khai (xem từng hằng); lớp này chỉ gom lại, không khai thêm từ nào.
     * Thiếu một nguồn thì câu dùng nó **không bao giờ** nghe ra được — mà lỗi ấy im lặng, nên có bài canh đếm.
     */
    private fun deaccentedVocabulary(): Set<String> {
        val out = LinkedHashSet<String>(256)
        VoiceGrammar.VERBS.forEach { (words, _) -> out.addAll(words) }
        out.addAll(VoiceIntentParser.CONNECTORS)
        out.addAll(VoiceLexicon.NUMBER_WORDS)
        out.addAll(VoiceLexicon.FILLERS)
        VoiceLexicon.READ_TAILS.forEach { out.addAll(it) }
        // V1.1 — *"…vào ô số hai"*. Thiếu một từ ở đây thì câu gõ được mà **không nói được**, im lặng.
        out.addAll(VoiceLexicon.SLOT_WORDS)
        out.addAll(VoiceLexicon.BY_APP_MARKERS)
        VoiceLexicon.CONFIRM_YES.forEach { out.addAll(it) }
        VoiceLexicon.CONFIRM_NO.forEach { out.addAll(it) }
        return out
    }

    private fun words(phrase: String): List<String> = VoiceLexicon.tokenize(phrase).map { it.raw }

    private fun deaccent(s: String): String = VoiceLexicon.deaccent(s)
}
