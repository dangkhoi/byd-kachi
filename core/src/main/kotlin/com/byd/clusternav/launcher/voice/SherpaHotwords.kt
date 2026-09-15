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
