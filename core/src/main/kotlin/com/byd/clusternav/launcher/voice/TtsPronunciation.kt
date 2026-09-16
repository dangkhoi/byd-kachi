package com.byd.clusternav.launcher.voice

/**
 * ═══ V1 pha NÓI · CHỮ LATIN → ÂM VIỆT, **NGAY TRƯỚC CỬA MÁY ĐỌC** ════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` **§9**. Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car.
 *
 * ## Bài toán ĐÃ ĐO, không phải phòng xa
 * [ĐO owner nghe mẫu 2026-09-16] Piper `vi_VN-vais1000` **đánh vần từng chữ cái** mọi từ Latin nó không biết:
 * *"Kachi"* ra **"K-A-cê-hát-i"**. Bộ phiên âm của Piper là espeak-ng với giọng `vi`, nên một chuỗi không đọc
 * được bằng luật chính tả tiếng Việt sẽ rơi xuống đường *"đọc tên từng ký tự"*. Mọi tên app, mọi đơn vị
 * (`km/h` · `kWh` · `PM2.5`), mọi chữ viết tắt (`SOC` · `SOH` · `VIN`) trong một câu phản hồi đều mắc **cùng**
 * một bệnh — và câu phản hồi nào cũng có ít nhất một trong số đó.
 *
 * ## Vì sao sửa Ở ĐÂY mà không sửa trong [VoiceReply]
 * Chuỗi của [VoiceReply] còn ba chỗ dùng khác, và cả ba đều **phải giữ nguyên văn**:
 *  1. **tấm chữ** (`VoiceOverlay`) — người lái liếc màn thấy *"Ka-chi"* thay vì *"Kachi"* là một lỗi hiển thị;
 *  2. **nhật ký** (`VoiceUtteranceLog`, cầu kiểm thử) — chẩn đoán phải đọc đúng chữ mà máy đã dựng;
 *  3. **tra clip đọc sẵn** (gói giọng clone, spec `kachi-voice-clone.html`) — bảng clip khoá theo **chuỗi gốc**;
 *     tra bằng chuỗi đã phiên âm là trượt toàn bộ gói, và trượt **im lặng**.
 * ⇒ Phép đổi này là việc của **cửa ra tiếng**, không phải của tầng dựng câu. Nó chạy đúng một lần, ở đúng một
 * chỗ ([VoiceSpeakerRouter.route], ngay trước `speak`), và không ai khác nhìn thấy kết quả của nó.
 *
 * ## Chỉ có DỮ LIỆU, không có nhánh `if` theo tên app (CLAUDE.md §7)
 * [PHRASES] là một bảng đóng `chữ viết → cách đọc`; thuật toán chỉ làm ba việc: khớp **dài nhất trước**, kiểm
 * **ranh giới từ**, rồi tra bảng. Từ Latin **lạ** (app mới cài, hãng mới) không rơi về đánh vần: nó đi tiếp qua
 * [VoiceAppPhonetics.SYLLABLES] (bảng âm tiếng Anh → âm Việt đã có sẵn — tệp này gần như là **ảnh ngược** của
 * nó) rồi tới [LETTERS] (đánh vần **bằng tên chữ cái tiếng Việt**, đúng cho mọi chữ viết tắt).
 *
 * ## Tất định + **luỹ đẳng**
 * Cùng chuỗi vào ⇒ cùng chuỗi ra; và `normalise(normalise(s)) == normalise(s)` (bài canh
 * `TtsPronunciationTest.chuan hoa hai lan bang mot lan`). Điều đó **không** miễn phí: nó là lý do bảng có mấy
 * dòng trông như đồng nhất (`"số vin" to "số vin"`) — xem chú thích tại chỗ.
 */
object TtsPronunciation {

    /**
     * `chữ viết (thường)` → `cách đọc`. Khoá viết **thường** (phép khớp hạ chữ cả hai vế), giá trị viết **đúng
     * chính tả tiếng Việt có dấu** — đó là thứ espeak-ng đọc được.
     *
     * Thứ tự khai **không** quyết định gì (luật là *dài nhất trước*), nhưng vẫn nhóm theo nguồn để người sau biết
     * dòng nào sinh ra từ đâu: mỗi dòng dưới đây hoặc là owner đọc mẫu, hoặc là một chuỗi **đã quét thấy** trong
     * nhãn/đơn vị của bốn bộ đăng ký (xem §9 của spec, mục *"quét chuỗi thật"*).
     *
     * ## ⚠ Gạch nối hay dấu cách giữa hai âm tiết — **đo, và phải đo LẶP LẠI**
     * Owner đọc mẫu bằng gạch nối (*"Ka-chi"*). espeak-ng phiên âm khác nhau cho hai cách viết ấy, nên câu hỏi
     * *"viết thế nào"* là một câu hỏi **đo được** — nhưng chỉ đo được khi đo nhiều lần: Piper VITS có **nhiễu lấy
     * mẫu** (`noise_scale` 0.667), mỗi lượt sinh ra một đoạn tiếng khác nhau ⇒ một lượt đo **n=1 không kết luận
     * được gì**. Lượt n=1 ngày 2026-09-17 từng chọn `"Ka chi"`; lượt **n=5** cùng ngày bác lại:
     * [ĐO host 2026-09-17, n=5, vòng lặp Piper→ASR đang ship — `scratchpad/repeat/repeat.tsv`]
     *  • `"Kachi"` (để nguyên) → ASR nghe **"AC HI"** 5/5 — đúng bệnh đánh vần;
     *  • `"Ka-chi"` → **"KACHI"** 2/5 và giữ được cả hai âm ở **5/5** lượt;
     *  • `"Ka chi"` → *"CÀI" / "CÁ TRI" / "CÀ CHI"* — **mất âm đầu**, không lượt nào đúng.
     * ⇒ Bảng này giữ đúng chữ owner đọc mẫu. Hai chỗ **lệch** khỏi bản đọc mẫu đều có số đo n=5 đứng sau và được
     * ghi ngay tại dòng: [LETTERS] nối bằng dấu cách, và `pm2.5`. Trọng tài là bộ nhận dạng đang ship — đúng luật
     * chọn bản của `kachi-voice-clone.html` §4.6 (*"lấy bản ASR đọc lại đúng nguyên văn"*).
     *
     * ⚠ Mức bằng chứng: ASR là **trọng tài thay thế**, không phải tai người. Nó trả lời đúng một câu hỏi —
     *   *"chuỗi này còn bị đọc thành từng chữ cái rời không"* — và câu ấy thì nó trả lời rất rõ.
     */
    val PHRASES: Map<String, String> = linkedMapOf(
        // ── (1) Tên riêng — owner đọc mẫu 2026-09-16 ────────────────────────────────────────────────────────
        "kachi" to "Ka-chi",
        "youtube music" to "Du-túp Mu-dích",
        "youtube" to "Du-túp",
        "google maps" to "Gu-gồ Máp",
        "google map" to "Gu-gồ Máp",
        "google" to "Gu-gồ",
        "waze" to "Quây",
        "carplay" to "Ca-plây",
        "android auto" to "An-đroi Ô-tô",
        "zalo" to "Za-lô",
        "spotify" to "Spô-ti-phai",
        "vietmap" to "Việt-máp",
        "tiktok" to "Tíc-tóc",
        "netflix" to "Nét-phlích",
        "ok" to "ô-kê",

        // ── (2) Viết tắt trên xe — owner đọc mẫu; hai dòng đầu là NHÃN THẬT của `TelemetryRegistry` ──────────
        // `t("soc", "Pin (SOC)", …)` và `t("soh_oem", "Sức khỏe pin (SOH)", …)`. Đổi lẻ `SOC`→*"pin"* thì câu ra
        // *"Pin (pin)"* — đọc lên là *"pin pin"*. Cả cụm mới là đơn vị đọc đúng, và luật dài-nhất-trước lấy nó.
        "pin (soc)" to "pin",
        "sức khỏe pin (soh)" to "sức khoẻ pin",
        "soc" to "pin",
        "soh" to "sức khoẻ pin",
        // `t("vin", "Số VIN", …)`. Dòng dưới trông đồng nhất nhưng **bắt buộc**: không có nó thì lượt chuẩn hoá
        // thứ hai lại thấy `vin` và cho ra *"số số vin"* ⇒ vỡ tính luỹ đẳng. Dài-nhất-trước giữ cụm lại nguyên.
        "số vin" to "số vin",
        "vin" to "số vin",

        // ── (3) Đơn vị — owner đọc mẫu + đơn vị THẬT quét từ `TelemetryRegistry` ─────────────────────────────
        "kwh/100km" to "ki-lô-oát giờ trên một trăm ki-lô-mét",
        "km/h" to "ki-lô-mét trên giờ",
        "kwh" to "ki-lô-oát giờ",
        "kw" to "ki-lô-oát",
        "kpa" to "ki-lô-pa-xcan",
        "km" to "ki-lô-mét",
        "nm" to "niu-tơn mét",
        "rpm" to "vòng trên phút",
        "µg/m³" to "mi-crô-gam trên mét khối",
        // Owner đọc mẫu *"pê-em"*. [ĐO host 2026-09-17, n=5 — `scratchpad/repeat/repeat.tsv`]: viết *"pê-em"*
        // thì ASR nghe lại đúng **1/5** lượt (*"TEN"/"KEM"/"K"*) — **tệ hơn cả để nguyên** `PM2.5` (2/5).
        // Cùng bộ tên chữ cái tiếng Việt nhưng viết rời *"pê mờ"* thì đúng **3/5**. Giữ nguyên Ý của owner
        // (đọc bằng tên chữ cái Việt), lấy CÁCH VIẾT mà số đo chọn. Owner muốn đổi lại thì sửa đúng dòng này.
        "pm2.5" to "pê mờ hai chấm năm",
        "°c" to "độ",
        "độ c" to "độ",
        // ⚠ [SOÁT chuỗi-lời-đáp 2026-09-17] `°` TRẦN (không có `c` đi sau) là đơn vị THẬT của **5 datum**:
        // `steering_deg` · `slope_deg` · `gps_lat` · `gps_lon` · `gps_heading` (đếm bằng máy trên
        // `core/build/catalog/registry.json`). Thiếu dòng này thì *"Góc vô-lăng 12°"* ra đúng chuỗi `12°` và
        // espeak đọc ký hiệu độ theo luật riêng của nó. Luật dài-nhất-trước vẫn để `°c` thắng `°`.
        "°" to "độ",
        "%" to "phần trăm",
        "min" to "phút",
        "h" to "giờ",
        "m" to "mét",
        "v" to "vôn",

        // ── (4) Chuỗi Latin còn lại quét thấy trong nhãn/bậc THẬT của `ControlRegistry`/`TelemetryRegistry` ──
        // (`"Điều hòa AUTO"` · `args = listOf("Tắt", "Auto", …)` · `"Chế độ lái: Eco"` · `"Camera 360"` ·
        //  `"Ion âm"` · `"Odo tổng"` · `"Chìa Bluetooth"` · `"Chế độ drift"`).
        // ⚠ `auto` PHẢI có dòng riêng: không có nó thì đường lùi âm tiết trả về *"ô tô"* (âm của *Android Auto*)
        //    cho một bậc đèn/điều hoà — đúng nghĩa ngược.
        "auto" to "au-tô",
        "eco" to "ê-cô",
        "camera" to "ca-mê-ra",
        "bluetooth" to "blu-tút",
        "ion" to "i-on",
        "odo" to "ô-đô",
        "drift" to "đờ-ríp",
        // ⚠ [SOÁT chuỗi-lời-đáp 2026-09-17] `cell` có trong nhãn của **5 datum** (`cell_temp_high/low/avg` ·
        // `cell_v_high/low` — đếm bằng máy trên registry). Không có dòng này nó đi xuống đường âm-tiếng-Anh rồi
        // đường đánh vần, tức *"xê e e-lờ e-lờ"* cho một từ mà người Việt vẫn đọc thẳng.
        // ⚠ Mức bằng chứng: **[ĐOÁN]** — *"xeo"* là cách người Việt quen đọc, CHƯA chạy vòng lặp Piper→ASR để
        // chốt giữa *"xeo"* và *"seo"* (mọi dòng khác của bảng này đều có n=5 đứng sau). Chốt bằng:
        // `python3 scripts/voice/stream-matrix.py` … hoặc đúng quy trình n=5 đã ghi ở KDoc [PHRASES].
        "cell" to "xeo",
    )

    /**
     * Tên **chữ cái tiếng Việt** — đường lùi cuối cho một chữ viết tắt lạ (`MCU` → *"em xê u"*, `HUD` →
     * *"hát u dê"*, `EV` → *"e vê"*).
     *
     * Đây đúng là việc Piper đang làm sai: nó cũng đánh vần, nhưng đánh vần bằng **tên chữ cái tiếng Anh** đọc
     * qua bộ âm tiếng Việt (*"cê-hát"* cho `ch`). Bảng này nói ra tên chữ cái mà người Việt thật sự đọc.
     *
     * ⚠ Nối bằng **dấu cách**, không bằng gạch nối — [ĐO host 2026-09-17, n=5 — `scratchpad/repeat/repeat.tsv`]:
     * *"Tầm hoạt động **e-vê**"* → ASR nghe *"ELI"/"ERICON"* **0/5**, còn *"Tầm hoạt động **e vê**"* → *"EV"*
     * **5/5**. Từng chữ cái là một **tiếng rời**, và espeak chỉ đọc đúng như thế khi chúng được viết rời.
     */
    val LETTERS: Map<Char, String> = linkedMapOf(
        'a' to "a", 'b' to "bê", 'c' to "xê", 'd' to "dê", 'e' to "e", 'f' to "ép", 'g' to "giê",
        'h' to "hát", 'i' to "i", 'j' to "gi", 'k' to "ca", 'l' to "e-lờ", 'm' to "em", 'n' to "en",
        'o' to "o", 'p' to "pê", 'q' to "quy", 'r' to "e-rờ", 's' to "ét", 't' to "tê", 'u' to "u",
        'v' to "vê", 'w' to "vê-kép", 'x' to "ích", 'y' to "i-dài", 'z' to "dét",
    )

    /** Dài nhất của [PHRASES] — trần cho vòng thử *dài nhất trước*, tính một lần. */
    private val MAX_KEY = PHRASES.keys.maxOf { it.length }

    /** Chữ viết tắt dài hơn ngần này thì đánh vần ra một câu không ai nghe hết — để nguyên còn hơn. */
    private const val MAX_ACRONYM = 5

    /**
     * Chuỗi [text] → chuỗi **đọc được**. Chỉ dùng cho lời gọi `speak`; mọi bề mặt khác giữ [text] gốc.
     *
     * Quét **một lượt** trái→phải. Tại mỗi vị trí, theo đúng thứ tự:
     *  1. [PHRASES] khớp **dài nhất trước** (nên `"YouTube Music"` thắng `"YouTube"`, `"km/h"` thắng `"km"`);
     *  2. nếu đang đứng ở đầu một **từ Latin** lạ ⇒ [soundOut] (âm tiếng Anh) rồi [spellOut] (tên chữ cái);
     *  3. không khớp gì ⇒ chép nguyên ký tự.
     * Phần đã chép ra **không bao giờ được quét lại** trong cùng lượt, nên một cách đọc không thể sinh ra một
     * lượt thay thứ hai.
     */
    fun normalise(text: String): String {
        if (text.isEmpty()) return text
        val hay = lower(text)
        val out = StringBuilder(text.length + PAD)
        var i = 0
        while (i < text.length) {
            val key = phraseAt(hay, i)
            if (key != null) {
                append(out, PHRASES.getValue(key))
                i += key.length
                continue
            }
            val end = latinTokenEnd(hay, i)
            if (end > i) {
                val token = text.substring(i, end)
                val said = soundOut(token) ?: spellOut(token)
                append(out, said ?: token)
                i = end
                continue
            }
            out.append(text[i])
            i++
        }
        // Một cách đọc chèn sau số/chữ tự thêm một dấu cách (xem [append]); chỗ vốn đã có dấu cách thì thành hai.
        // Máy đọc không phát ra khoảng lặng vì thế, nhưng chuỗi ra phải **so sánh được** với chính nó ở lượt sau.
        return SPACES.replace(out, " ")
    }

    /** Hạ chữ **từng ký tự** — giữ nguyên độ dài, để chỉ số của [hay] và của chuỗi gốc luôn trùng nhau. */
    private fun lower(s: String): String = buildString(s.length) { s.forEach { append(it.lowercaseChar()) } }

    /**
     * Khoá dài nhất của [PHRASES] khớp tại [i] **và** đứng trọn một từ; `null` nếu không có.
     *
     * ## Hai vế ranh giới KHÔNG đối xứng, và đó là chủ ý
     *  • **Trái**: chặn khi ký tự trước là **chữ cái** (mọi chữ cái Unicode, kể cả chữ Việt có dấu) ⇒ `"ok"` không
     *    bao giờ khớp vào giữa một từ. Chữ **số** thì KHÔNG chặn: `"50km"` phải đọc ra *"50 ki-lô-mét"*.
     *  • **Phải**: chặn khi ký tự sau là chữ cái **hoặc chữ số** ⇒ `"km"` không khớp trong `"km2"`, `"PM2.5"`
     *    không khớp trong `"PM2.55"`. Một chữ số dính đuôi nghĩa là đang đứng trước một ký hiệu KHÁC.
     */
    private fun phraseAt(hay: String, i: Int): String? {
        if (i > 0 && hay[i - 1].isLetter()) return null
        val max = minOf(MAX_KEY, hay.length - i)
        for (len in max downTo 1) {
            val end = i + len
            if (end < hay.length && (hay[end].isLetter() || hay[end].isDigit())) continue
            val cand = hay.substring(i, end)
            if (PHRASES.containsKey(cand)) return cand
        }
        return null
    }

    /**
     * Cuối của **từ Latin** bắt đầu tại [i] (chữ cái ASCII, rồi chữ cái/số ASCII); `i` nếu chỗ này không phải
     * đầu một từ Latin.
     *
     * Ký tự trước phải **không** là chữ cái — nhờ đó mọi từ tiếng Việt **có dấu** tự nằm ngoài tầm: trong
     * *"Ắc-quy"* thì `c` đứng sau `Ắ` (một chữ cái) nên không mở được từ nào.
     */
    private fun latinTokenEnd(hay: String, i: Int): Int {
        if (hay[i] !in 'a'..'z') return i
        if (i > 0 && hay[i - 1].isLetter()) return i
        var j = i
        while (j < hay.length && (hay[j] in 'a'..'z' || hay[j].isDigit())) j++
        return j
    }

    /**
     * Cách đọc theo **âm tiếng Anh** ([VoiceAppPhonetics]), hoặc `null` khi bảng âm không phủ hết từ.
     *
     * Dùng lại bảng của tầng NGHE thay vì chép một bảng thứ hai: hai bảng cùng nội dung là hai bảng sẽ lệch nhau
     * (CLAUDE.md §4.1 DRY). Luật *"phủ hết hoặc không gì cả"* của [VoiceAppPhonetics.spokenForms] chính là thứ
     * giữ cho một từ tiếng Việt viết không dấu (*"pin"* · *"cao"* · *"trong"*) **không** bị đụng tới.
     */
    internal fun soundOut(token: String): String? {
        val forms = VoiceAppPhonetics.spokenForms(token)
        val first = forms.firstOrNull() ?: return null
        // Phần tử cuối của `spokenForms` luôn là chính nhãn viết thường ⇒ không phải một cách đọc.
        return if (first.equals(token, ignoreCase = true)) null else first
    }

    /**
     * Đánh vần một **chữ viết tắt** (toàn chữ HOA ASCII, 2…[MAX_ACRONYM] chữ) bằng [LETTERS]; `null` nếu không
     * phải dạng đó.
     *
     * Chỉ nhận chữ HOA: *"pin"* viết thường là một **từ**, đánh vần nó ra *"pê-i-en"* là biến một câu đang đúng
     * thành một câu vô nghĩa.
     */
    internal fun spellOut(token: String): String? {
        if (token.length !in 2..MAX_ACRONYM) return null
        if (!token.all { it in 'A'..'Z' }) return null
        val named = token.map { LETTERS[it.lowercaseChar()] ?: return null }
        return named.joinToString(" ")
    }

    /**
     * Nối [piece] vào [out], tự chèn một dấu cách khi hai bên dính nhau bằng chữ/số.
     *
     * Ca vào: khoá mở đầu bằng ký hiệu (`"%"` · `"°c"` · `"µg/m³"`) đứng ngay sau một con số — `"46%"` phải ra
     * *"46 phần trăm"*, không phải *"46phần trăm"* (espeak đọc dính thành một từ lạ).
     */
    private fun append(out: StringBuilder, piece: String) {
        if (piece.isEmpty()) return
        val prev = out.lastOrNull()
        if (prev != null && prev.isLetterOrDigit() && piece[0].isLetterOrDigit()) out.append(' ')
        out.append(piece)
    }

    /** Dôi chỗ cho các cách đọc dài hơn chữ viết (*"km"* → *"ki-lô-mét"*) — chỉ để `StringBuilder` bớt nở lại. */
    private const val PAD = 24

    private val SPACES = Regex(" {2,}")
}
