package com.byd.clusternav.launcher.voice

/**
 * ═══ H3(b) · ĐỌC TÊN APP THEO ÂM VIỆT — SINH TỪ **NHÃN**, KHÔNG TỪ DANH SÁCH TAY ═════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car.
 *
 * ## Bài toán ĐÃ ĐO, không phải phòng xa
 * [ĐO] `docs/diagnostics/voice-mishear-2026-09-16.md` §3: loại ý định `app` đúng **12,8 %** — thấp nhất bảng.
 * §5 nói rõ cơ chế: mô hình `zipformer-vi` là mô hình **tiếng Việt**, nên người lái đọc tên thương hiệu theo âm
 * Việt (*"gu gồ máp"*, *"du túp"*, *"za lô"*, *"chát gi pi ti"*) và mô hình in ra đúng những chữ ấy — trong khi
 * từ vựng của Kachi chỉ có **nhãn hệ thống** do `PackageManager` cấp (*"Google Maps"*, *"ChatGPT"*).
 *
 * [VoiceSynonyms.APP_TARGETS] chữa được **bảy** app có trong bảng đích. Nhưng owner 2026-09-16 hỏi thẳng:
 * *"mở app chatgpt → có mở được không, có lấy được các app đang có trong xe để mở không?"* — tức yêu cầu là
 * **mọi app đã cài**, kể cả app chưa ai kịp khai. Chép tay thì không bao giờ đuổi kịp, và đó đúng là thứ
 * CLAUDE.md §7 cấm (*"không hardcode tên gói / tên app"*).
 *
 * ## Cách làm: một BẢNG ÂM TIẾT hữu hạn, viết tay — không phải một bộ luật phiên âm
 * Phiên âm tự động EN→VI là một bài toán mở; đoán sai một âm là đẻ ra một bí danh không ai đọc như vậy, mà cái
 * sai ấy **im lặng** (nó chỉ làm câu của người khác khớp nhầm). Nên ở đây chỉ có **dữ liệu**: [SYLLABLES] là một
 * bảng đóng `âm tiếng Anh → cách người Việt đọc`, và thuật toán chỉ làm hai việc — cắt nhãn thành âm tiết, rồi
 * tra bảng.
 *
 * ## Luật *"phủ hết hoặc không gì cả"*
 * Nhãn chỉ ra được cách đọc khi **mọi** âm tiết của nó có trong bảng. Phủ một nửa (vd *"VTV Go"* → `vtv` không
 * biết) thì trả **rỗng**, không trả một chuỗi nửa Việt nửa Anh: một bí danh nửa vời vừa không ai đọc như thế,
 * vừa là một cụm lạ nằm trong từ vựng. Nhãn thuần Việt (*"Cài đặt"*, *"Ứng dụng của tôi"*) vì thế tự trả rỗng —
 * không cần một danh sách loại trừ nào.
 *
 * ## Tất định
 * Cùng nhãn vào ⇒ cùng danh sách ra, cùng thứ tự (âm tiết trái→phải, biến thể theo thứ tự khai trong bảng).
 * Đó là điều kiện để bài kiểm so bằng `assertEquals` và để bản đồ nhãn→gói không đổi giữa hai lượt dựng.
 */
object VoiceAppPhonetics {

    /**
     * ÂM tiếng Anh → cách đọc tiếng Việt. Khai theo **âm**, không theo tên app: nhờ vậy `chat` + `gpt` phục vụ
     * *ChatGPT*, `net` + `flix` phục vụ *Netflix*, và một app mới ghép từ cùng bộ âm là tự nói được.
     *
     * Nhiều biến thể ⇒ nhiều cách đọc (người miền Nam nói *"gia lô"*, miền Bắc *"da lô"* — `apps.tsv` ghi cả ba).
     * Thứ tự trong danh sách là thứ tự ưu tiên, và nó **ổn định** (xem KDoc lớp).
     *
     * ⚠ Viết **có dấu**: đây là chữ mà mô hình in ra. Tầng so khớp tự bỏ dấu ([VoiceLexicon.deaccent]) nên có dấu
     * không làm hỏng gì, còn thiếu dấu thì khai với bộ nhận dạng một chuỗi mà không giọng nào phát ra như thế.
     */
    val SYLLABLES: Map<String, List<String>> = linkedMapOf(
        // ── tên thương hiệu đọc trọn (đặt trước để luật khớp-dài-nhất lấy chúng) ──
        "youtube" to listOf("du túp", "iu túp"),
        "vietmap" to listOf("việt máp"),
        "spotify" to listOf("spô ti phai", "pô ti phai"),
        "google" to listOf("gu gồ"),
        "android" to listOf("an đroi"),
        "zalo" to listOf("za lô", "gia lô"),
        "waze" to listOf("quây"),
        "zing" to listOf("zing"),
        "mp3" to listOf("em pê ba"),
        "gpt" to listOf("gi pi ti"),
        "music" to listOf("miu dích"),
        "mobile" to listOf("mô bai"),
        "photo" to listOf("phô tô"),
        "drive" to listOf("đờ rai"),
        "store" to listOf("sờ to"),
        "chat" to listOf("chát"),
        "maps" to listOf("mép", "máp"),
        "auto" to listOf("ô tô"),
        "play" to listOf("plây"),
        "face" to listOf("phây"),
        "book" to listOf("búc"),
        "flix" to listOf("phờ lích"),
        "mail" to listOf("meo"),
        "live" to listOf("lai"),
        "mini" to listOf("mi ni"),
        "plus" to listOf("plớt"),
        "viet" to listOf("việt"),
        "tube" to listOf("túp"),
        "map" to listOf("máp"),
        "car" to listOf("ca"),
        "net" to listOf("nét"),
        "tik" to listOf("tích"),
        "tok" to listOf("tốc"),
        "you" to listOf("du", "iu"),
        "pro" to listOf("prô"),
        "app" to listOf("áp"),
        "cast" to listOf("cát"),
        "tv" to listOf("ti vi"),
        "go" to listOf("gâu"),
        "gi" to listOf("gi"),
        "g" to listOf("gi"),
        "p" to listOf("pi"),
        "t" to listOf("ti"),
    )

    /** Trần số cách đọc cho một nhãn — nhân chéo biến thể có thể nở, mà mỗi cách đọc là một khoá trong bản đồ. */
    private const val MAX_FORMS = 8

    /** Trần số âm tiết của một nhãn: dài hơn thì đó không còn là một cái tên, và tra bảng cũng vô ích. */
    private const val MAX_SYLLABLES = 8

    /**
     * Các cách đọc **có dấu** của [label], hoặc **rỗng** khi nhãn không phủ hết bằng [SYLLABLES].
     *
     * Kết quả gồm cả chính nhãn viết thường (mô hình hay in ra dạng thường), đặt **cuối** để cách đọc Việt được
     * xét trước. Đã khử trùng, giữ thứ tự.
     */
    fun spokenForms(label: String): List<String> {
        val chunks = chunks(label)
        if (chunks.isEmpty() || chunks.size > MAX_SYLLABLES) return emptyList()
        val perChunk = ArrayList<List<String>>(chunks.size)
        for (c in chunks) {
            val hit = split(c) ?: return emptyList()
            if (perChunk.size + hit.size > MAX_SYLLABLES) return emptyList()
            perChunk.addAll(hit)
        }
        val out = LinkedHashSet<String>(MAX_FORMS + 1)
        cross(perChunk).forEach { if (out.size < MAX_FORMS) out.add(it) }
        out.add(label.lowercase())
        // Cùng luật ≤ 2 ký tự của [VoiceSynonyms.APP_TARGETS] §1: một khoá hai chữ cái khớp vào quá nhiều câu.
        return out.filter { it.length >= MIN_FORM_LEN }
    }

    /** Cách đọc ngắn hơn ngần này thì khớp bừa — xem luật loại ở KDoc [VoiceSynonyms.APP_TARGETS]. */
    private const val MIN_FORM_LEN = 3

    /**
     * Nhãn → các mảnh chữ-số liền nhau, đã tách **CamelCase**: `"ChatGPT"` → `["Chat", "GPT"]` ·
     * `"YouTube Music"` → `["You", "Tube", "Music"]` · `"Zing MP3"` → `["Zing", "MP3"]`.
     *
     * ⚠ Chữ số **không** bị tách khỏi chữ cái đứng trước: `MP3` là một âm (*"em pê ba"*), còn cắt thành `MP` + `3`
     * thì không mảnh nào tra được bảng và cả nhãn rơi vào "không biết đọc".
     */
    private fun chunks(label: String): List<String> {
        val words = label.split(NON_WORD).filter { it.isNotBlank() }
        val out = ArrayList<String>(words.size + 2)
        words.forEach { w -> splitCamel(w, out) }
        return out
    }

    /** Tách một từ ở ranh giới **thường→HOA** và **HOA HOA→thường** (`"GPTChat"` ⇒ `GPT` + `Chat`). */
    private fun splitCamel(word: String, out: MutableList<String>) {
        var start = 0
        for (i in 1 until word.length) {
            val prev = word[i - 1]
            val cur = word[i]
            val boundary = (prev.isLowerCase() && cur.isUpperCase()) ||
                (prev.isUpperCase() && cur.isUpperCase() && i + 1 < word.length && word[i + 1].isLowerCase())
            if (boundary) { out.add(word.substring(start, i)); start = i }
        }
        if (start < word.length) out.add(word.substring(start))
    }

    /**
     * Một mảnh → dãy **danh sách cách đọc** của từng âm tiết, hoặc `null` nếu có chỗ không tra được.
     *
     * Khớp **tham lam, dài nhất trước** từ trái sang — cùng luật *"dãy dài nhất thắng"* của [VoiceGrammar], nên
     * `"vietmap"` ra một âm (*"việt máp"*) chứ không ra `viet` + `map` rời rạc.
     */
    private fun split(chunk: String): List<List<String>>? {
        val s = chunk.lowercase()
        val out = ArrayList<List<String>>(2)
        var i = 0
        while (i < s.length) {
            var hit: List<String>? = null
            var len = 0
            for (end in s.length downTo i + 1) {
                val cand = SYLLABLES[s.substring(i, end)]
                if (cand != null) { hit = cand; len = end - i; break }
            }
            if (hit == null) return null
            out.add(hit)
            i += len
        }
        return out
    }

    /**
     * Trong các khoá cùng trỏ về **một gói**, khoá nào là **nhãn THẬT** (chữ người dùng nhìn thấy)?
     *
     * ## Vì sao cần — [ĐO máy ảo 2026-09-16], năm ca `t73`–`t78`
     * Bản đồ nhãn→gói của `VoiceWiring.withPhonetics` cố ý chứa **cả** nhãn thật lẫn các dạng đọc sinh ra từ nó,
     * vì tầng hiểu cần khớp được cả hai. Nhưng khi câu *"mở du túp"* khớp bằng dạng ĐỌC, ý định mang theo đúng
     * chuỗi đã khớp, nên Kachi **đọc to**: *"Mở ứng dụng du túp"*. Owner đã chốt Kachi nói phản hồi thành tiếng
     * (D-C3), nên đây không còn là chuyện thẩm mỹ: người lái nghe máy gọi tên app bằng một chuỗi phiên âm mà
     * chính họ không viết bao giờ, và không có cách nào biết máy đã mở **đúng** app hay chưa.
     *
     * ## Phép chọn KHÔNG dựa vào thứ tự chèn
     * Cách rẻ nhất là *"lấy khoá đầu tiên trỏ về gói ấy"* — đúng với `withPhonetics` (nhãn thật chèn trước,
     * `putIfAbsent` không đè), nhưng nó đúng nhờ một tính chất nằm ở **tệp khác**. Hàm công khai thì không được
     * đúng kiểu đó (cùng lý do KDoc [SherpaHotwords.dropPrefixes] nêu). Ở đây hỏi thẳng: khoá nào **không phải**
     * dạng đọc sinh ra từ một khoá khác trong cùng nhóm. Nhóm chỉ vài phần tử nên phép so đôi một là miễn phí.
     *
     * @return nhãn thật, hoặc [candidates] đầu tiên khi không phân biệt được (không bao giờ trả rỗng cho danh
     *   sách không rỗng — chỗ gọi luôn có một cái tên để đọc).
     */
    fun canonicalLabel(candidates: List<String>): String? {
        if (candidates.size <= 1) return candidates.firstOrNull()
        val generated = candidates.flatMap { spokenForms(it) }.toSet()
        return candidates.firstOrNull { it !in generated } ?: candidates.first()
    }

    /** Nhân chéo các biến thể, trái→phải, có trần [MAX_FORMS]. */
    private fun cross(parts: List<List<String>>): List<String> {
        var acc = listOf("")
        parts.forEach { options ->
            val next = ArrayList<String>(minOf(acc.size * options.size, MAX_FORMS))
            acc.forEach { head ->
                options.forEach { o -> if (next.size < MAX_FORMS) next.add(if (head.isEmpty()) o else "$head $o") }
            }
            acc = next
        }
        return acc.filter { it.isNotEmpty() }
    }

    /** Mọi thứ không phải chữ/số là ngắt từ — cùng lệ với [VoiceLexicon.tokenize]. */
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
}
