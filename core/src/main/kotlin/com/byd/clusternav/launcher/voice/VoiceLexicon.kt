package com.byd.clusternav.launcher.voice

/**
 * ═══ V1 · CHUẨN HOÁ CHỮ + ĐỌC SỐ ═════════════════════════════════════════════════════════════════════════════
 *
 * Tầng "chữ thô → chuỗi từ so khớp được" — tách khỏi [VoiceGrammar] (vốn nói về *từ vựng của xe*) vì đây là việc
 * của **tiếng Việt**, không phải của xe: nó đúng y như vậy dù bộ đăng ký có đổi hay không.
 *
 * Thuần Kotlin (`:core`, cấm `android.*`). `java.text.Normalizer` là JVM, không phải Android — cùng lệ với
 * `java.util.Locale` mà `TelemetryReadout` đang dùng.
 */
object VoiceLexicon {

    /**
     * Một từ trong câu: [raw] giữ NGUYÊN VĂN (để trả lại tên bài hát/điểm đến đúng chữ hoa và dấu), [norm] là bản
     * đã bỏ dấu + chữ thường (để so khớp).
     *
     * ## Vì sao phải giữ cả hai chứ không chuẩn hoá một lần rồi quên bản gốc
     * [ĐO] mẫu câu Kiki §7(c): *"Mở bài Nồng nàn Hà Nội"*, *"Chỉ đường đến chợ Bến Thành"*. Phần đuôi của những câu
     * đó là **dữ liệu của người dùng**, phải đi tiếp nguyên văn tới app nhạc/dẫn đường. Trả về `"nong nan ha noi"`
     * là tự tay làm hỏng chính thứ mình vừa nhận.
     */
    data class Token(val raw: String, val norm: String)

    /** Ký tự được coi là ngắt từ (mọi thứ không phải chữ/số). Dấu `%` giữ lại vì nó là ĐƠN VỊ, không phải dấu câu. */
    private val SPLIT = Regex("[^\\p{L}\\p{N}%]+")

    /** Cắt câu thành từ, giữ song song bản gốc và bản chuẩn hoá. Từ rỗng bị loại. */
    fun tokenize(text: String): List<Token> =
        text.split(SPLIT).filter { it.isNotBlank() }.map { Token(it, deaccent(it)) }

    /**
     * Bỏ dấu + chữ thường: `"Nhiệt độ"` → `"nhiet do"`.
     *
     * ## Vì sao bỏ dấu là BẮT BUỘC, không phải tiện tay
     * Chuỗi vào tầng này có ba nguồn khác nhau và **không nguồn nào đảm bảo dấu**: bàn phím xe (người ta gõ nhanh,
     * thường không dấu), tương lai là ASR (bảng token của một mô hình nhỏ hiếm khi phủ đủ 134 tổ hợp dấu tiếng
     * Việt), và nhật ký/kịch bản test. So khớp có dấu thì *"bat den doc"* trượt sạch, mà đó lại là cách gõ phổ biến
     * nhất trên xe.
     *
     * `Normalizer` NFD tách được dấu thanh/dấu mũ, **nhưng không tách `đ`** (nó là một chữ cái riêng, không phải
     * `d` + dấu) ⇒ phải thay tay, không thì *"đèn"* → *"den"* hỏng thành *"đen"* và mọi nhãn có `đ` đều trượt.
     */
    fun deaccent(s: String): String {
        val lower = s.lowercase()
        val nfd = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        for (ch in nfd) {
            when {
                ch == 'đ' -> sb.append('d')
                // Dải dấu kết hợp (combining diacritical marks) — bỏ hẳn.
                ch.code in 0x0300..0x036F -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    // ── SỐ BẰNG CHỮ → SỐ ─────────────────────────────────────────────────────────────────────────

    private val VI_UNITS = mapOf(
        "khong" to 0, "mot" to 1, "hai" to 2, "ba" to 3, "bon" to 4, "nam" to 5,
        "sau" to 6, "bay" to 7, "tam" to 8, "chin" to 9,
    )

    /**
     * Biến thể của hàng đơn vị **khi đứng sau `mươi`** — tiếng Việt đổi từ, không đổi số: 21 = *"hai mươi mốt"*,
     * 24 = *"hai mươi tư"*, 25 = *"hai mươi lăm"*. Thiếu bảng này thì *"đặt nhiệt độ hai mươi tư"* không đọc được,
     * mà 24 °C là mức người ta nói hằng ngày.
     */
    private val VI_AFTER_TEN = mapOf("mot" to 1, "tu" to 4, "lam" to 5, "linh" to 0, "le" to 0)

    /**
     * Hàng đơn vị **chỉ có nghĩa khi đi sau hàng chục** — dùng cho lối nói rút gọn *"hai lăm"* = 25, *"ba mốt"* =
     * 31, *"hai tư"* = 24 (bỏ hẳn chữ *"mươi"*, cách nói thường ngày hơn cả bản đầy đủ).
     *
     * ## [SOÁT P1] Vì sao đây là lỗi phải chữa, không phải "chưa hỗ trợ"
     * Thiếu bảng này thì *"đặt nhiệt độ hai lăm"* đọc ra số **2**, rồi `ControlDef.clamp` kéo nó về `min` = **17 °C**
     * — tức máy **làm sai một việc** và vẫn báo *"✓ Đặt Nhiệt độ = 17"*. Một câu không hiểu được thì người ta nói
     * lại; một câu hiểu SAI thành số lạnh nhất thì không ai kịp nhận ra trước khi xe lạnh ngắt.
     *
     * ⚠ Cố ý **không** nhận `linh`/`le` ở đây: *"hai linh"* không phải một con số (nó là nửa của *"hai linh năm"* =
     * 205), nhận vào sẽ đẻ ra 20 từ một câu chưa nói xong.
     */
    private val VI_TENS_SHORT = mapOf("mot" to 1, "tu" to 4, "lam" to 5)

    /**
     * *"hăm"* = **hai mươi** rút gọn (*"hăm bốn"* = 24, *"hăm lăm"* = 25, *"hăm mốt"* = 21).
     *
     * ⚠ KHÔNG thêm *"băm"* (= ba mươi) vào đây: bỏ dấu xong nó là `"bam"`, trùng hệt **"bấm"** — một từ người ta
     * dùng để *ra lệnh*. Đổi một động từ thành con số là đúng họ lỗi mà [FILLERS] đã phải rút ngắn vì nó.
     */
    private const val HAM = "ham"

    private val EN_UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )

    private val EN_TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    /**
     * Cụm nghĩa là "hết cỡ" / "thấp nhất" — trả [MAX] / [MIN] để chỗ gọi kẹp theo `ControlDef.min/max`.
     *
     * ⚠ Khai bằng **chuỗi từ**, không phải một từ: sau khi bỏ dấu thì *"tối"* (đa) và *"tôi"* (đại từ, nằm trong
     * [FILLERS]) **trùng hệt nhau** = `"toi"`. Chỉ nhận khi có đủ từ thứ hai (`toi da`) thì hai nghĩa mới tách được;
     * bắt một từ là *"bật đèn cho tôi"* biến thành *"bật đèn hết cỡ"*.
     */
    private val MAX_PHRASES = listOf(listOf("toi", "da"), listOf("het", "co"), listOf("max"), listOf("maximum"))
    private val MIN_PHRASES = listOf(listOf("toi", "thieu"), listOf("nho", "nhat"), listOf("min"), listOf("minimum"))

    /** Sentinel: "hết cỡ" — chỗ gọi thay bằng `ControlDef.max`. */
    const val MAX = Int.MAX_VALUE

    /** Sentinel: "thấp nhất" — chỗ gọi thay bằng `ControlDef.min`. */
    const val MIN = Int.MIN_VALUE

    /**
     * MỌI từ có thể tham gia một con số (đã bỏ dấu) — gom từ chính các bảng mà [readNumber] tra.
     *
     * ## Vì sao phải phơi ra, và vì sao là một phép GOM chứ không một danh sách mới
     * Tầng nghe (`VoicePhrases`) phải khai với bộ nhận dạng *"những từ này là số"*, nếu không thì *"đặt nhiệt độ
     * hai mươi hai"* không bao giờ nghe ra được — mà đó là dạng câu hay dùng nhất sau bật/tắt. Chép tay một bảng
     * thứ hai ở tầng nghe là đúng họ lỗi mà [VoiceSynonyms] sinh ra để chặn: thêm *"hăm"* ở đây mà quên bên kia
     * thì nói được khi gõ, không nói được khi nói — **im lặng**, không ai đỏ.
     *
     * ⇒ Gom từ đúng sáu bảng riêng mà [readNumber] đang tra, cộng hai từ hàng chục đứng một mình. Thêm một cách
     * đọc số ở trên là tầng nghe **tự** biết, không phải sửa gì.
     */
    val NUMBER_WORDS: Set<String> = buildSet {
        addAll(VI_UNITS.keys); addAll(VI_AFTER_TEN.keys); addAll(VI_TENS_SHORT.keys)
        addAll(EN_UNITS.keys); addAll(EN_TENS.keys)
        add(HAM); add("muoi")
        MAX_PHRASES.forEach { addAll(it) }; MIN_PHRASES.forEach { addAll(it) }
    }

    /**
     * Cụm ĐỒNG Ý / TỪ CHỐI cho hộp xác nhận ([VoiceRisk.CONFIRM]) — bỏ dấu, khớp NGUYÊN cụm.
     *
     * ## Vì sao câu trả lời cho hộp xác nhận lại ở `:core`
     * Hộp xác nhận là `AlertDialog` của `:app`, nhưng *"đồng ý"* / *"huỷ"* là **tiếng Việt**, cùng loại việc với
     * mọi thứ khác trong tệp này. Để ở `:app` thì tầng nghe và các bài kiểm off-car không chạm được — mà đây đúng
     * là chỗ phải kiểm kỹ: trả lời nhầm một hộp *"mở khoá toàn xe?"* là hậu quả không hoàn lại được.
     *
     * ⚠ Danh sách CỐ Ý ngắn và **không** có từ một âm tiết mơ hồ (`"ok"` thì nhận, `"ừ"`/`"vâng"` bỏ dấu ra `u`/
     * `vang` — trùng tiếng đệm và trùng chữ *"vàng"*). Nghe nhầm một tiếng ậm ừ thành *"đồng ý"* là đúng thứ mà
     * cổng xác nhận sinh ra để chặn; thà hỏi lại còn hơn tự trả lời hộ người lái.
     */
    val CONFIRM_YES: List<List<String>> =
        listOf(listOf("dong", "y"), listOf("xac", "nhan"), listOf("ok"), listOf("yes"), listOf("confirm"))

    /** Cụm TỪ CHỐI — xem KDoc [CONFIRM_YES]. */
    val CONFIRM_NO: List<List<String>> =
        listOf(listOf("huy"), listOf("huy", "bo"), listOf("khong"), listOf("thoi"), listOf("no"), listOf("cancel"))

    /**
     * Câu [text] có phải là câu trả lời cho một hộp xác nhận không: `true` = đồng ý · `false` = huỷ · `null` =
     * không phải câu trả lời (⇒ chỗ gọi để nguyên hộp, KHÔNG đoán).
     *
     * Chỉ nhận khi cả câu **đúng bằng** một cụm: *"đồng ý"* là trả lời, còn *"đồng ý rồi bật đèn"* thì không —
     * một câu dài đứng trước hộp xác nhận nhiều khả năng là người lái đang nói việc khác, và đoán sai ở đây
     * nghĩa là tự bấm "Đồng ý" hộ họ.
     */
    fun confirmAnswer(text: String): Boolean? {
        val t = tokenize(text).map { it.norm }.filterNot { it in FILLERS }
        if (t.isEmpty()) return null
        if (CONFIRM_YES.any { it == t }) return true
        if (CONFIRM_NO.any { it == t }) return false
        return null
    }

    /** Kết quả đọc số: [value] (hoặc [MAX]/[MIN]) và số từ đã ăn. */
    data class Num(val value: Int, val consumed: Int)

    /**
     * Đọc một con số bắt đầu tại [i] trong [t]. Trả `null` nếu chỗ đó không phải số.
     *
     * Nhận: chữ số thuần (`"22"`, `"80%"`), số bằng chữ tiếng Việt tới 99 (*"hai mươi hai"*, *"ba mươi mốt"*,
     * *"mười"*), tiếng Anh tới 99 (*"twenty two"*, `"twenty-two"` đã thành hai từ sau [tokenize]), và các cụm
     * "hết cỡ / tối đa / tối thiểu".
     */
    @Suppress("ReturnCount")
    fun readNumber(t: List<Token>, i: Int): Num? {
        if (i !in t.indices) return null
        val w = t[i].norm

        // "tối đa" / "tối thiểu" / "hết cỡ" — khớp NGUYÊN cụm (xem KDoc [MAX_PHRASES]).
        MAX_PHRASES.firstOrNull { phraseAt(t, i, it) }?.let { return Num(MAX, it.size) }
        MIN_PHRASES.firstOrNull { phraseAt(t, i, it) }?.let { return Num(MIN, it.size) }

        // Chữ số thuần (cho phép hậu tố % hoặc đơn vị dính liền bị bỏ bởi tokenize).
        val digits = w.trimEnd('%')
        digits.toIntOrNull()?.let { return Num(it, 1) }

        // Tiếng Anh.
        EN_UNITS[w]?.let { return Num(it, 1) }
        EN_TENS[w]?.let { tens ->
            val next = t.getOrNull(i + 1)?.norm
            val unit = EN_UNITS[next]
            return if (unit != null && unit < 10) Num(tens + unit, 2) else Num(tens, 1)
        }

        // Tiếng Việt rút gọn: "hăm <đơn vị>" = 21..29 (xem KDoc [HAM]).
        if (w == HAM) {
            val next = t.getOrNull(i + 1)?.norm
            val add = next?.let { VI_AFTER_TEN[it] ?: VI_UNITS[it] }
            return if (add != null) Num(20 + add, 2) else Num(20, 1)
        }

        // Tiếng Việt: "mười" đứng đầu = 10..19.
        if (w == "muoi") {
            val next = t.getOrNull(i + 1)?.norm
            val unit = next?.let { VI_AFTER_TEN[it] ?: VI_UNITS[it] }
            return if (unit != null) Num(10 + unit, 2) else Num(10, 1)
        }

        // Tiếng Việt: "<đơn vị> mươi [<đơn vị>]" = 20..99; "<đơn vị>" đơn lẻ = 0..9.
        val unit = VI_UNITS[w] ?: return null
        if (t.getOrNull(i + 1)?.norm == "muoi") {
            val tail = t.getOrNull(i + 2)?.norm
            val add = tail?.let { VI_AFTER_TEN[it] ?: VI_UNITS[it] }
            return if (add != null) Num(unit * 10 + add, 3) else Num(unit * 10, 2)
        }
        // Rút gọn: "<đơn vị> mốt/tư/lăm" = 21/24/25… (xem KDoc [VI_TENS_SHORT]). Chỉ áp khi hàng chục ≥ 2 vì
        // *"một lăm"* không ai nói (số 15 là *"mười lăm"*, đã bắt ở nhánh trên).
        if (unit >= 2) {
            VI_TENS_SHORT[t.getOrNull(i + 1)?.norm]?.let { return Num(unit * 10 + it, 2) }
        }
        return Num(unit, 1)
    }

    /** Cụm [words] có nằm đúng tại vị trí [i] của [t] không. */
    fun phraseAt(t: List<Token>, i: Int, words: List<String>): Boolean =
        words.indices.all { k -> t.getOrNull(i + k)?.norm == words[k] }

    /**
     * Từ ĐỆM bỏ qua ở đầu câu và ngay sau động từ.
     *
     * ## ⚠⚠ Danh sách này phải NGẮN, và mỗi từ phải qua được bài canh tiền tố
     * Bỏ dấu xong thì tiếng Việt **đụng nhau rất nhiều**: `"cái"` = `"cài"` (⇒ nuốt mất *"cài đặt"*), `"của"` =
     * `"cửa"` (⇒ nuốt mất *"cửa sổ trời"*), `"thể"` = `"thế"` = `"the"` (⇒ nuốt mất *"thể thao"*), `"tối"` =
     * `"tôi"`. Một từ đệm trùng **tiền tố** của một cụm trong từ vựng sẽ làm cụm đó **không bao giờ khớp được
     * nữa** — im lặng, không ai đỏ. Vì thế `VoiceGrammarCoverageTest.khong tu dem nao la tien to cua mot cum
     * trong tu vung` quét bằng máy: thêm một từ đệm ăn mất một nhãn ⇒ test ĐỎ ngay.
     */
    val FILLERS: Set<String> = setOf(
        "kachi", "oi", "hay", "giup", "gium", "vui", "long", "please", "just",
    )

    /**
     * Cụm HỎI — gặp là chuyển cả câu thành lệnh ĐỌC, cắt cụm này ra rồi đọc phần còn lại
     * (`VoiceIntentParser.askAt`).
     *
     * [ĐO] mẫu câu Kiki §7(c) #31/#38/#41 đều có hình dạng `<X> hôm nay/bao nhiêu/thế nào?` — người Việt hỏi xe
     * bằng cụm hỏi chứ không bằng động từ đứng đầu. Không có bảng này thì *"pin còn bao nhiêu"* rơi vào NO_VERB.
     *
     * ⚠ Tên cũ là *"đuôi"* vì bản đầu chỉ nhận ở CUỐI câu — nay nhận ở **bất kỳ đâu** (*"còn bao nhiêu pin"* cũng
     * là câu hỏi). Giữ nguyên tên hằng để khỏi đụng các chỗ đang đọc nó; ý nghĩa thì đọc ở đây.
     */
    val READ_TAILS: List<List<String>> = listOf(
        listOf("bao", "nhieu"), listOf("the", "nao"), listOf("ra", "sao"),
        listOf("how", "much"), listOf("how", "many"),
    )

    // ── V1.1 · Ô TRÊN MÀN HÌNH (*"mở YouTube vào ô số 2"*) ───────────────────────────────────────

    /**
     * Từ mở đầu một mệnh đề chỉ Ô: *"…vào **ô** số 2"* / EN *"…in **slot** 2"*.
     *
     * ⚠ `"o"` là một từ **một chữ cái** sau khi bỏ dấu (`ô` → `o`), nên nó CHỈ được tra ở đúng một chỗ: phần đuôi
     * **sau** một tên app đã khớp ([VoiceIntentParser.slotAt]). Quét nó ở giữa câu bất kỳ là mời mọi tiếng
     * *"ờ / ồ / ô"* biến thành một chỉ số ô.
     */
    val SLOT_HEADS: Set<String> = setOf("o", "slot")

    /**
     * Từ đệm giữa [SLOT_HEADS] và con số — *"ô **số** hai"*, *"ô **thứ** hai"*, EN *"slot **number** 2"*.
     *
     * Bỏ qua chúng chứ không bắt buộc có: người ta nói cả *"vào ô 2"* lẫn *"vào ô số 2"*, và bắt một trong hai
     * là làm câu kia câm mà không ai biết vì sao.
     */
    val SLOT_ORDINALS: Set<String> = setOf("so", "thu", "number")

    /**
     * MỌI từ có thể tham gia một mệnh đề chỉ ô — gom để tầng NGHE khai đủ với bộ nhận dạng.
     *
     * Cùng lý do với [NUMBER_WORDS]: thiếu một từ ở đây thì câu *"mở YouTube vào ô số hai"* **gõ được mà không
     * nói được**, và cái thiếu ấy im lặng. `vao`/`in` không tham gia phép đọc số (bộ phân tích bỏ qua chúng như
     * mọi từ lạ) nhưng **phải** có trong ngữ pháp, không thì người nói đúng câu vẫn không được nghe ra.
     */
    val SLOT_WORDS: Set<String> = SLOT_HEADS + SLOT_ORDINALS + setOf("vao", "in", "into")

    /**
     * Cụm đánh dấu *"…**bằng** &lt;app&gt;"* — mở đầu phần CHỌN APP ở cuối một câu nhạc/dẫn đường.
     *
     * ⚠ Cố ý **không** có `"o"`/`"tai"`: chúng quá ngắn và quá thường. Và cụm này chỉ có nghĩa khi **ngay sau nó
     * là một tên app đã biết, và tên ấy đứng ở CUỐI câu** ([VoiceIntentParser.appAfterMarker]) — nếu không thì
     * một điểm đến như *"cầu Bằng Lăng"* sẽ bị cắt đôi.
     */
    val BY_APP_MARKERS: Set<String> = setOf("bang", "tren", "voi", "qua", "with", "on", "using")
}
