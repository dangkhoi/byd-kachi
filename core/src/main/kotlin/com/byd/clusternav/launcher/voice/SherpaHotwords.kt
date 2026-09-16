package com.byd.clusternav.launcher.voice

/**
 * ═══ V2 pha NGHE · HOTWORDS (contextual biasing) — KÉO GIẢI MÃ TỰ DO VỀ TẬP LỆNH ĐÓNG ═══════════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html` §Design. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Vì sao biasing thay cho "ngữ pháp đóng" của Vosk
 * Gốc bệnh Vosk trên xe là **ngữ pháp FST cứng** + mô hình 32 MB: nói cả câu ra một từ (README model WER 15,7%).
 * sherpa-onnx giải mã **tự do** (nghe được câu bất kỳ) rồi dùng **hotwords** kéo nhẹ về đúng nhãn control khi
 * người lái nói gần đúng — [ĐO] off-car: score 3.0 sửa `pin`/`tắt`/`âm lượng` mà KHÔNG chèn nhầm lệnh vào câu
 * thường ("hôm nay trời đẹp quá" giữ nguyên). Xem `docs/diagnostics/vn-stt-sherpa-emulator-eval-2026-09-14.md`.
 *
 * ## Vì sao HOA + có dấu
 * Mô hình VN của sherpa xuất **CHỮ HOA CÓ DẤU** (tokens.txt: `▁ĐÈN`, `▁PIN`…). Hotwords phải cùng bảng chữ thì
 * bộ mã hoá BPE (`modelingUnit=bpe` + `bpeVocab`) mới khớp được — [ĐO] hotword thường/không dấu bị native bỏ
 * ("Failed to encode some hotwords"). Nên hàm này **viết hoa** và giữ nguyên dấu.
 *
 * ## Vì sao lọc, không đổ nguyên danh sách
 *  • Bỏ token đơn ký tự / rỗng: một hotword một chữ cái kéo lệch mọi câu.
 *  • Bỏ **token** có chữ số (tên app, `PM2.5`, `12V`, số ô): mô hình VN **không phát ra** được token đó nên
 *    biasing vô nghĩa, còn làm rối — tên app do [VoiceIntentParser] khớp nhãn lo, không phải hotword.
 *  • Khử trùng, giữ thứ tự xuất hiện (ổn định cho test + nhật ký).
 *
 * ## ⚠ Dấu câu là **chỗ ngắt**, KHÔNG phải cớ để bỏ cả cụm ([ĐO] 2026-09-15)
 * Bản đầu viết `else -> return null`: gặp một dấu câu là bỏ nguyên nhãn. Đếm trên danh mục thật
 * (`docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L3): **12/64** nhãn `ControlRegistry` và **38/123**
 * nhãn `TelemetryRegistry` — tức **50 nhãn** — chưa bao giờ thành hotword, gồm cả *"Pin (SOC)"*,
 * *"Kính trước-trái"*, *"Khoá / mở khoá"*, *"Áp lốp trước-trái"*. Hậu quả đo được: `xem pin` nghe ra *"xem tin"*,
 * `mở kính trước trái` ra *"mở kín trước trái"* — hai câu hay dùng nhất lại là hai câu không được bias.
 *
 * Nay dấu câu được xử đúng bản chất của nó:
 *  • dấu **liệt kê / ngoặc** (`/ , ; ( ) [ ] | · + – —`) tách nhãn thành **nhiều** hotword: *"Khoá / mở khoá"* ⇒
 *    `KHOÁ` + `MỞ KHOÁ`; *"Pin (SOC)"* ⇒ `PIN` + `SOC`; *"Mở cửa + đèn đọc"* ⇒ `MỞ CỬA` + `ĐÈN ĐỌC`;
 *  • dấu **trong từ** (gạch nối, chấm, `%`) chỉ là ngắt từ: *"Kính trước-trái"* ⇒ `KÍNH TRƯỚC TRÁI`;
 *  • chữ số bỏ theo **token**, không bỏ cả cụm: *"Bụi mịn PM2.5"* ⇒ `BỤI MỊN`, *"Ắc-quy 12V"* ⇒ `ẮC QUY`.
 *
 * ⇒ MỌI nhãn của 4 bộ đăng ký sinh được ít nhất một hotword; `SherpaBiasingCoverageTest` khoá điều đó bằng máy.
 */
object SherpaHotwords {

    /**
     * Sinh nội dung tệp hotwords từ danh sách cụm control **có dấu** (ví dụ các cụm trong [VoicePhraseSet]).
     *
     * @param phrases cụm đã có dấu, bất kỳ hoa/thường; mỗi cụm một dòng ở kết quả.
     * @return nội dung tệp (mỗi hotword một dòng, HOA có dấu, đã lọc + khử trùng); rỗng nếu không cụm nào hợp lệ.
     */
    fun fileContent(phrases: Iterable<String>): String {
        val seen = LinkedHashSet<String>()
        for (raw in phrases) seen.addAll(phrasesOf(raw))
        return if (seen.isEmpty()) "" else seen.joinToString("\n") + "\n"
    }

    /**
     * Như [fileContent] nhưng **chỉ giữ CỤM ≥ 2 từ tiếng Việt** — tệp hotwords thật của bản ship
     * (spec `kachi-voice-hotword-phrases.html` R2).
     *
     * Một luật lọc, có phép đo (2026-09-16, host, 25 WAV, chi tiết ở KDoc [SherpaPhraseHotwords]): **bỏ dòng một
     * từ** — thêm 39 động từ rời vào tệp toàn cụm kéo 21/25 xuống 17/25 (bằng không hotword); danh từ rời (`NHẠC`)
     * cộng điểm cho cả đường sai (*"rừng nhạc"*). Cơ chế: khớp trọn hotword ⇒ đồ thị về gốc.
     *
     * ⚠ Cố ý KHÔNG lọc theo "dòng ASCII thuần" để loại nhãn tiếng Anh: *"xem pin"* cũng là ASCII thuần mà là câu
     * tiếng Việt hay dùng nhất (bản đầu của hàm này đã làm rụng đúng cụm ấy — bài canh bắt được). Nhãn EN không
     * vào tệp vì [SherpaPhraseHotwords] **không lấy** `labelEn`/`shortEn`/`argsEn` ngay từ nguồn.
     */
    fun phraseFile(phrases: Iterable<String>, appNames: Iterable<String> = emptyList()): String {
        val seen = LinkedHashSet<String>()
        for (raw in phrases) phrasesOf(raw).filterTo(seen) { isPhrase(it) }
        // Thứ tự hai bộ lọc có ý nghĩa: bỏ dòng mở đầu bằng tên app TRƯỚC, rồi mới bỏ tiền tố — làm ngược lại
        // thì một dòng ngắn bị bỏ vì là tiền tố của một dòng mà ngay sau đó cũng bị bỏ, tức mất cả hai.
        val kept = dropPrefixes(dropAppNameLeading(seen, appNames))
        return if (kept.isEmpty()) "" else kept.joinToString("\n") + "\n"
    }

    /** Cụm ≥ 2 từ. */
    fun isPhrase(hotword: String): Boolean = ' ' in hotword

    /**
     * Bỏ dòng là **tiền tố theo từ** của một dòng khác (*"CHẾ ĐỘ LÁI"* khi đã có *"CHẾ ĐỘ LÁI THỂ THAO"*).
     *
     * [ĐO] 2026-09-16 host, tệp Kotlin sinh 2057 dòng: `chế độ lái thể thao` nghe ra *"CHẾ ĐỘ LÁI"* (mất đuôi);
     * bỏ 155 dòng tiền tố ⇒ nghe đúng, 20/25 → 21/25. Cùng cơ chế với luật cấm từ rời: khớp trọn *"CHẾ ĐỘ LÁI"*
     * là đồ thị về gốc, phần *"THỂ THAO"* không còn đường cộng điểm. Cụm ngắn mất bias khi nói một mình — đổi lấy
     * cụm dài nghe đúng; cụm dài mới là thứ hay sai (từ đơn mô hình tự nghe được).
     *
     * Cách tìm: xếp thứ tự từ điển ⇒ **mọi** dòng nhận `x` làm tiền tố (theo ký tự) nằm LIỀN NHAU ngay sau `x`
     * (một dòng khác `x` ở vị trí sớm hơn thì đã khác `x` ở một ký tự *bên trong* `x`, nên nó lớn hơn cả khối ấy).
     * Nên chỉ cần quét khối liền sau `x` tới khi hết, tìm một dòng bắt đầu bằng `"$x "`.
     *
     * ⚠ Cố ý KHÔNG dừng ở **một** phần tử kế tiếp. Làm thế là ngầm dựa vào *"dấu cách nhỏ hơn mọi ký tự khác có
     * thể đứng ở đó"* — đúng với tệp hiện nay ([SherpaHotwords.normalize] chỉ sinh chữ cái + một dấu cách, và chữ
     * cái Unicode đều ≥ `A`), nhưng đó là điều kiện của **chỗ gọi**, không phải của hàm này. Hàm công khai thì
     * không được đúng nhờ một giả định nằm ở file khác. Chi phí: vẫn `O(n log n)` + tổng kích thước các khối
     * cùng tiền tố ([ĐO] tệp ship 1902 dòng, kết quả không đổi một dòng nào).
     *
     * Giữ thứ tự xuất hiện ở kết quả để tệp `diff` được giữa hai lượt đo.
     */
    fun dropPrefixes(lines: Collection<String>): List<String> {
        val sorted = lines.sorted()
        val prefixes = HashSet<String>()
        for (i in sorted.indices) {
            val x = sorted[i]
            var j = i + 1
            while (j < sorted.size && sorted[j].startsWith(x)) {
                if (sorted[j].startsWith("$x ")) { prefixes.add(x); break }
                j++
            }
        }
        return lines.filterNot { it in prefixes }
    }

    /**
     * H3 — bỏ dòng **mở đầu bằng một TÊN APP đứng trọn vẹn** (*"YOUTUBE MUSIC"*, *"GU GỒ MÁP …"*).
     *
     * ## Bẫy `YOUTUBE MUSIC`, và vì sao nó khác luật tiền tố
     * Cùng cơ chế nguồn với [dropPrefixes] (sherpa-onnx `csrc/context-graph.cc` `ForwardOneStep`: khớp trọn một
     * hotword ⇒ đồ thị **về gốc**), nhưng chỗ đau khác: câu về app gần như luôn còn **mệnh đề đuôi** —
     * *"mở youtube music **vào ô số hai**"*. Tên app khớp trọn ở đầu câu là phần đuôi mất sạch đường cộng điểm,
     * mà đuôi ấy lại là thứ quyết định app đi vào ô nào. [dropPrefixes] không bắt được ca này: *"YOUTUBE MUSIC"*
     * chỉ là tiền tố của một dòng khác khi dòng ấy **có mặt trong tệp**, còn *"vào ô số hai"* thì không bao giờ
     * là hotword (số ô bị luật bỏ-token-chữ-số loại từ đầu).
     *
     * ⇒ Cụm tên app chỉ được bias khi nó đứng **sau một động từ** (*"MỞ YOUTUBE MUSIC"*): lúc đó đường đúng vẫn
     * ăn trọn điểm mà đồ thị chỉ về gốc ở cuối cụm động-từ-+-tên, không phải ngay ở chữ đầu câu.
     *
     * ⚠ Tên app **không** được viết cứng trong tệp này (CLAUDE.md §7): chỗ gọi cấp danh sách — xem
     * [SherpaPhraseHotwords.appNames]. Danh sách rỗng ⇒ hàm trả nguyên đầu vào.
     *
     * @param appNames tên app ở dạng đã chuẩn hoá hay chưa đều được — hàm tự đưa qua [normalize].
     */
    fun dropAppNameLeading(lines: Collection<String>, appNames: Iterable<String>): List<String> {
        val names = appNames.flatMap { phrasesOf(it) }.toSet()
        if (names.isEmpty()) return lines.toList()
        return lines.filterNot { line -> names.any { line == it || line.startsWith("$it ") } }
    }

    /**
     * Một nhãn → **các** hotword của nó (0, 1 hay nhiều), theo đúng ba luật ở KDoc lớp.
     *
     * Trả về danh sách đã khử trùng **trong phạm vi một nhãn**, giữ thứ tự xuất hiện.
     */
    fun phrasesOf(raw: String): List<String> {
        val out = ArrayList<String>(2)
        var start = 0
        for (i in raw.indices) {
            if (raw[i] in ALT_SEPARATORS) {
                normalize(raw.substring(start, i))?.let { if (it !in out) out.add(it) }
                start = i + 1
            }
        }
        normalize(raw.substring(start))?.let { if (it !in out) out.add(it) }
        return out
    }

    /**
     * Chuẩn hoá **một đoạn** (đã tách ở [ALT_SEPARATORS]) thành hotword, hoặc `null` nếu không dùng được.
     *
     * Giữ chữ cái (kể cả có dấu tiếng Việt); mọi ký tự khác là **ngắt từ**; token nào có chữ số thì bỏ **token
     * đó** (xem KDoc lớp); viết HOA; từ chối nếu sau khi lọc còn rỗng hay quá ngắn.
     */
    fun normalize(raw: String): String? {
        val words = ArrayList<String>(4)
        val word = StringBuilder()
        var hasDigit = false
        fun flush() {
            if (!hasDigit && word.isNotEmpty()) words.add(word.toString())
            word.setLength(0)
            hasDigit = false
        }
        for (c in raw) {
            when {
                c.isLetter() -> word.append(c)
                // Chữ số dính vào một từ (`PM2`, `12V`, `360`) ⇒ bỏ đúng từ đó, phần còn lại của nhãn vẫn dùng được.
                c.isDigit() -> hasDigit = true
                else -> flush() // khoảng trắng, gạch nối, chấm, `%`… đều chỉ là ngắt từ
            }
        }
        flush()
        val cleaned = words.joinToString(" ")
        if (cleaned.length < MIN_LEN) return null
        // Một token đơn (không khoảng trắng) mà quá ngắn cũng bỏ.
        if (!cleaned.contains(' ') && cleaned.length < MIN_SINGLE_TOKEN_LEN) return null
        return cleaned.uppercase()
    }

    /**
     * Dấu **liệt kê / ngoặc**: mỗi bên là một cách gọi riêng ⇒ tách thành nhiều hotword (xem KDoc lớp).
     *
     * Cố ý KHÔNG có gạch nối `-` và dấu chấm: trong nhãn của dự án chúng nằm **trong** một cách gọi
     * (*"Kính trước-trái"*, *"PM2.5"*), tách ra là đẻ ra hotword `TRÁI` đứng một mình.
     */
    private const val ALT_SEPARATORS = "/,;()[]|·+–—"

    private const val MIN_LEN = 2
    private const val MIN_SINGLE_TOKEN_LEN = 2
}
