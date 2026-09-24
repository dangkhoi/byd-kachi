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
     * ## ⚠⚠ ĐỔI 2026-09-16 (owner chốt) — nhận MỌI từ đồng nghĩa *"có"*, kể cả từ một âm tiết
     * Bản 1.49–1.65 cố ý chỉ nhận 5 cụm và **bỏ** `"ừ"`/`"vâng"` với lý do *"bỏ dấu ra `u`/`vang`, trùng tiếng
     * đệm và trùng chữ vàng"*. Lý do ấy bị **[ĐO xe 2026-09-16] bác**: người lái trả lời hộp xác nhận bằng đúng
     * chữ *"ừ"*, Kachi im lặng bỏ qua, và cái im lặng ấy đọc ra thành *"tính năng giọng nói hỏng"* — tức phép
     * phòng xa đã làm hỏng đúng cổng nó định bảo vệ, ở ca thường gặp nhất. Một hộp không nhận câu trả lời tự
     * nhiên thì người ta thôi dùng cả tính năng, chứ không đổi cách nói.
     *
     * Hai chốt an toàn **không đổi** và chúng mới là thứ giữ cổng này:
     *  1. [confirmAnswer] chỉ nhận khi **cả câu** đúng bằng một cụm — một tiếng ậm ừ lẫn trong câu dài vẫn là
     *     `null` (⇒ KHÔNG), không phải "đồng ý";
     *  2. lượt nghe xác nhận chỉ kéo `CONFIRM_LISTEN_MS`, và mặc định của mọi đường thoát vẫn là **KHÔNG**.
     *
     * ⚠ **Va chạm đã biết, chưa chốt** (báo owner 2026-09-16): `"đúng"` bỏ dấu ra `dung`, **trùng** `"dừng"`
     * (*dừng nhạc · dừng chiếu cụm*) và `"dùng"`. Trong lượt nghe xác nhận, một tiếng *"dừng"* (ý người lái là
     * **thôi**) sẽ đọc thành ĐỒNG Ý. Cách chữa rẻ nhất là bỏ `"đúng"` đứng một mình và giữ `"đúng rồi"` — nhưng
     * đó là một cụm owner đã liệt kê tên, nên **quyết định thuộc owner**, không phải chỗ này tự cắt (backlog
     * `V-CONFIRM-DUNG`).
     */
    val CONFIRM_YES: List<List<String>> = listOf(
        listOf("dong", "y"), listOf("xac", "nhan"), listOf("dung", "roi"), listOf("lam", "di"),
        listOf("u"), listOf("vang"), listOf("co"), listOf("duoc"),  // KHÔNG có "dung" một mình: trùng "dừng" (V-CONFIRM-DUNG, chốt 2026-09-16)
        listOf("ok"), listOf("yes"), listOf("confirm"),
    )

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
        val raw = tokenize(text).map { it.norm }
        // ⚠⚠ Xét bản CHƯA lọc tiếng đệm TRƯỚC. `"ừ"` vừa là câu ĐỒNG Ý (owner chốt 2026-09-16, KDoc [CONFIRM_YES])
        // vừa là tiếng đệm mở đầu câu ([ĐO xe 2026-09-16] `"ừ bật đèn đọc"`) — hai vai khác nhau của cùng một
        // chữ. Lọc trước rồi mới so thì chữ ấy biến mất và hộp xác nhận **im lặng bỏ qua** đúng câu trả lời tự
        // nhiên nhất, tức làm hỏng lại chính cái owner vừa bắt sửa. Thứ tự này giữ CẢ HAI vai và **không đổi
        // nghĩa** của một câu nào: bản đã lọc vẫn được xét ngay sau đó, y như trước.
        if (raw.isNotEmpty()) {
            if (CONFIRM_YES.any { it == raw }) return true
            if (CONFIRM_NO.any { it == raw }) return false
        }
        val t = raw.filterNot { it in FILLERS }
        if (t.isEmpty()) return null
        if (CONFIRM_YES.any { it == t }) return true
        if (CONFIRM_NO.any { it == t }) return false
        return null
    }

    /**
     * Chuỗi [text] **không mang một lượt nói thật nào**: rỗng, hoặc chỉ toàn [FILLERS].
     *
     * Sinh ra cho ca [ĐO xe 2026-09-16] mô hình *ảo giác* trong lúc gần như im lặng — nhật ký thật ghi `"ừ"` 102
     * lần và `"ừm"` 102 lần liên tiếp. Mỗi chuỗi như thế mà được coi là một lượt nói sẽ **mở lại** một vòng hội
     * thoại, tức một vòng lặp chạy mãi trong lúc người ta đang lái.
     *
     * ⚠⚠ **Chỗ gọi PHẢI hỏi [confirmAnswer] TRƯỚC.** Một tiếng *"ừ"* đứng một mình là câu ĐỒNG Ý hợp lệ khi (và
     * chỉ khi) đang có hộp xác nhận chờ; hàm này không biết điều đó và cố ý không biết — nó chỉ trả lời câu hỏi
     * *"chuỗi này có chữ nào mang nghĩa không"*. Đảo thứ tự hai phép hỏi là bịt mất cổng xác nhận.
     */
    fun isFillerOnly(text: String): Boolean {
        val t = tokenize(text)
        return t.isEmpty() || t.all { it.norm in FILLERS }
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
        // Tiếng Việt hàng NGHÌN: "<đơn vị> nghìn/ngàn [phần 1..999]" = 1000..9999. Phần sau nghìn đọc lại bằng
        // readNumber (đệ quy — phần đó KHÔNG bắt đầu bằng "nghìn" nên không vòng vô hạn). "một nghìn tám trăm chín
        // mươi tám"→1898 · "hai ngàn"→2000 (findings 2026-09-24 số nhà hàng nghìn).
        if (t.getOrNull(i + 1)?.norm == "nghin" || t.getOrNull(i + 1)?.norm == "ngan") {
            var value = unit * 1000
            var consumed = 2
            val a = t.getOrNull(i + 2)?.norm
            if (a == "linh" || a == "le") {                    // "hai nghìn lẻ năm" = 2005
                val ones = t.getOrNull(i + 3)?.norm?.let { VI_AFTER_TEN[it] ?: VI_UNITS[it] }
                if (ones != null) { value += ones; consumed += 2 } else consumed += 1
            } else if (a != null) {
                val rest = readNumber(t, i + 2)
                if (rest != null && rest.value in 1..999) { value += rest.value; consumed += rest.consumed }
            }
            return Num(value, consumed)
        }
        // Tiếng Việt hàng TRĂM: "<đơn vị> trăm [lẻ/linh <đơn vị>] | [<đơn vị> mươi [<đơn vị>]]" = 100..999.
        // "bảy trăm hai mươi"→720 · "bảy trăm lẻ năm"→705 · "một trăm"→100 (findings 2026-09-24 số nhà).
        if (t.getOrNull(i + 1)?.norm == "tram") {
            var value = unit * 100
            var consumed = 2                                   // "<đơn vị> trăm"
            val a = t.getOrNull(i + 2)?.norm                   // lẻ/linh HOẶC hàng chục
            if (a == "linh" || a == "le") {                    // "trăm lẻ năm" = 705
                val ones = t.getOrNull(i + 3)?.norm?.let { VI_AFTER_TEN[it] ?: VI_UNITS[it] }
                if (ones != null) { value += ones; consumed += 2 }
                else consumed += 1                             // "một trăm lẻ" cụt ⇒ = 100
            } else if (a != null) {
                // phần sau trăm là một số 0..99 — đọc lại bằng chính readNumber (đệ quy một tầng, không vòng vô hạn
                // vì phần sau KHÔNG bắt đầu bằng "<đơn vị> trăm").
                val rest = readNumber(t, i + 2)
                if (rest != null && rest.value in 1..99) { value += rest.value; consumed += rest.consumed }
            }
            return Num(value, consumed)
        }
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
        // [ĐO xe 2026-09-16] nhật ký thật trên DL3 bản 1.68: `"ừ bật đèn đọc"` — tiếng ậm ừ mở đầu **lọt vào
        // chuỗi chữ**, và vì `u` không phải tiếng đệm nên cả câu rơi vào NO_VERB: một lệnh nói đúng, hiểu sai.
        // ⚠ `u` cũng là một cụm của [CONFIRM_YES]; hai vai ấy chỉ sống chung được nhờ thứ tự xét ở
        // [confirmAnswer] (bản chưa lọc trước) — đọc KDoc ở đó trước khi đụng vào một trong hai danh sách.
        "u", "um",
        // Courtesy đơn (owner on-car 2026-09-22 + golden dataset): "ê", "với" mở/đệm câu. KHÔNG thêm "a" (là đầu
        // cụm "a c"=điều hoà), "cho"/"tôi"/"đi"/"nhé" (nghĩa khác) — xử qua cụm ở [stripCourtesy].
        "e", "voi",
    )

    /**
     * Cụm LỊCH SỰ ở ĐẦU câu (đã bỏ dấu, dài trước ngắn) — "làm ơn bật cốp" / "cho tôi bật cốp" / "cho mình xem
     * pin". [ĐO golden dataset 2026-09-22] đây là nhóm FAIL lớn nhất: động từ không còn ở vị trí 0 ⇒ NO_VERB.
     */
    private val LEAD_COURTESY: List<List<String>> = listOf(
        listOf("lam", "on"), listOf("lam", "phuc"),
        listOf("cho", "toi"), listOf("cho", "minh"), listOf("cho", "anh"), listOf("cho", "em"),
        listOf("giup", "toi"), listOf("giup", "minh"), listOf("phien", "ban"),
    ).sortedByDescending { it.size }

    /**
     * Cụm LỊCH SỰ/đệm ở CUỐI câu — "... hộ tôi" / "... giúp mình" / "... một chút" / "... đi" / "... nhé".
     * Cắt ở đuôi để phần lệnh phía trước parse sạch. Dài trước ngắn.
     */
    private val TAIL_COURTESY: List<List<String>> = listOf(
        listOf("ho", "toi"), listOf("ho", "minh"), listOf("giup", "toi"), listOf("giup", "minh"),
        listOf("gium", "toi"), listOf("gium", "minh"), listOf("dum", "toi"), listOf("dum", "minh"),
        listOf("dum", "cai"), listOf("gium", "cai"), listOf("giup", "cai"),
        listOf("mot", "chut"), listOf("mot", "ti"), listOf("mot", "xiu"), listOf("cai", "nao"),
        listOf("nhe"), listOf("nha"), listOf("di"), listOf("gium"), listOf("dum"), listOf("voi"),
        listOf("lai"), listOf("cai"),
    ).sortedByDescending { it.size }

    /**
     * Cắt cụm lịch sự ĐẦU + CUỐI (courtesy) rồi mới đưa vào [dropLeadingFillers]. Trả nguyên nếu không có gì để cắt.
     *
     * ⚠ CẨN TRỌNG: chỉ cắt ĐUÔI khi phần còn lại vẫn ≥ 1 từ (không nuốt cả câu), và không cắt nếu đuôi trùng một
     * đối tượng có nghĩa — vd "lái" (ghế lái) cắt ở cuối "sưởi ghế lái" sẽ hỏng. Nên "lai"/"cai" chỉ cắt khi
     * TRƯỚC nó vẫn còn cụm đủ dài. Ở đây giữ luật đơn giản: cắt tối đa MỘT cụm mỗi đầu, và không cắt "lai"/"cai"
     * nếu ngay trước chúng là "ghe" (ghế lái) hoặc câu chỉ còn ≤ 2 từ.
     */
    fun stripCourtesy(t: List<Token>): List<Token> {
        var out = t
        // Đầu câu.
        LEAD_COURTESY.firstOrNull { p -> out.size > p.size && phraseAt(out, 0, p) }
            ?.let { out = out.subList(it.size, out.size) }
        // Cuối câu.
        TAIL_COURTESY.firstOrNull { p ->
            out.size > p.size && phraseAt(out, out.size - p.size, p) &&
                // "lai"/"cai" ở cuối chỉ cắt khi câu còn dài (>3 từ) và trước nó KHÔNG phải "ghe" (ghế lái).
                !((p == listOf("lai") || p == listOf("cai")) &&
                    (out.size - p.size <= 3 || out.getOrNull(out.size - p.size - 1)?.norm == "ghe"))
        }?.let { out = out.subList(0, out.size - it.size) }
        return out
    }

    /**
     * Bỏ các [FILLERS] **ở ĐẦU** dãy, dừng ở từ thật đầu tiên (giữa câu thì không đụng — *"bật hay tắt"* phải còn
     * nguyên để [confirmAnswer]/câu hỏi chọn đọc được).
     *
     * ## Vì sao nó ở đây chứ không là hai bản private
     * `VoiceIntentParser` gọi phép này 8 lần, `VoiceMediaNavParse` 2 lần — sau lượt tách tệp của WP9 nó thành **hai
     * bản sao y hệt nhau**. Luật ba dòng thì bản sao rẻ, nhưng nó là bản sao của một luật **ảnh hưởng mọi câu nói**:
     * ai đó siết nó một bên (vd bỏ cả từ đệm ở cuối) thì hai bộ dựng ý-định cắt câu khác nhau, và cái lệch ấy im
     * lặng. Chỗ đúng là cạnh [FILLERS] — dữ liệu và phép dùng dữ liệu ở cùng một nơi, và không bộ phân tích nào
     * phải phụ thuộc vào bộ kia.
     */
    fun dropLeadingFillers(t: List<Token>): List<Token> {
        var i = 0
        while (i < t.size && t[i].norm in FILLERS) i++
        return if (i == 0) t else t.subList(i, t.size)
    }

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
        // [ĐO golden 2026-09-22] Nam Bộ hỏi tình trạng: "pin sao rồi" · "mức xăng sao rồi".
        listOf("sao", "roi"), listOf("the", "nay"), listOf("nhu", "the", "nao"),
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
     * là một tên app đã biết, và tên ấy đứng ở CUỐI câu** ([VoiceTailClause.appAfterMarker]) — nếu không thì
     * một điểm đến như *"cầu Bằng Lăng"* sẽ bị cắt đôi.
     *
     * ## `dung` (= *"dùng"*) — thêm 2026-09-18, và vì sao nó an toàn dù trùng ba từ
     * Owner nói cả *"…**bằng** VietMap"* lẫn *"**dùng** VietMap dẫn đường"*; nợ này đã ghi ở backlog (*app-hint
     * "dùng google map" chưa bắt*). Bỏ dấu thì `dung` trùng **ba** từ khác nhau: *"dừng"* (một động từ PAUSE của
     * [VoiceGrammar.VERBS]), *"đừng"* và *"đúng"*. Nó vào được đây **chỉ vì** ba cổng của
     * [VoiceTailClause.appAfterMarker] đứng chắn: phần sau cụm đánh dấu phải (a) **chạm cuối câu**, (b) khớp
     * **một tên app đã biết**, (c) dài ≤ [VoiceAppTargets.LONGEST_SPOKEN] từ. Nghĩa là `dung` chỉ có nghĩa
     * *"dùng"* trong đúng hình dạng `… dùng <tên app>` kết thúc câu — *"dừng nhạc"*, *"tạm dừng"*, *"đừng mở
     * youtube"* đều không có hình dạng đó và [ĐO] không đổi một ý định nào (`VoiceLogCases0918Test`).
     */
    val BY_APP_MARKERS: Set<String> = setOf("bang", "tren", "voi", "qua", "dung", "with", "on", "using")
}
