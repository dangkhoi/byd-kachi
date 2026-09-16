package com.byd.clusternav.launcher.voice

/**
 * ═══ H7 · MÔ HÌNH LẪN ÂM — **DỮ LIỆU**, không phải nhánh `if` ════════════════════════════════════════════════
 *
 * Owner 2026-09-16: *"cần phải xử lý sai chính tả kiểu này nữa nhé, làm 1 bộ data sai chính tả luôn, anh em nói
 * đâu phải lúc nào cũng đúng, cốp với cấp khác gì nhau đâu"*.
 *
 * ## Vì sao một BẢNG LẪN ÂM, không phải thêm bí danh vào [VoiceSynonyms]
 * [ĐO giọng thật 2026-09-16] — 5 bản thu (owner · con gái · ba giọng Nam: chậm/nhanh/có nhạc), giải mã bằng
 * đúng mô hình đang ship (`zipformer-vi`), căn theo mốc *"câu N"*: lỗi áp đảo **không** phải nghe sai cả câu mà
 * là **một âm tiết trượt một nấc** — `cốp`→`cấp`, `pin`→`bên/biên`, `nhiệt`→`mật`, `hết`→`máy`, `lái`→`đá/gái`.
 * Chép từng chuỗi ấy vào [VoiceSynonyms] là dựng một từ vựng **theo lỗi của một mô hình**: nó dài ra mãi, và
 * cái sai ấy sống lâu hơn chính mô hình (đúng lý do §APP_TARGETS đã loại 97/101 dòng). Bảng dưới nói **quy
 * luật** (phụ âm đầu · vần · thanh) nên một cặp chưa ai gặp vẫn được đỡ, còn danh sách [OBSERVED] chỉ để ghi
 * *"đã thấy ở đâu"* — tức bằng chứng, không phải cơ chế (CLAUDE.md §2).
 *
 * ## Một phép chuẩn hoá DUY NHẤT
 * Nền của mọi phép so là [VoiceLexicon.deaccent] — y hệt thứ [VoiceGrammar] dùng để dựng từ vựng. Thanh điệu
 * là **thông tin thêm** đọc từ chính chữ gốc ([Token.raw]), không phải một cách chuẩn hoá thứ hai: nó chỉ làm
 * phép so **chặt hơn** (0,15 thay vì 0), không bao giờ nới ra.
 *
 * ⚠ Tệp này **không** quyết định câu nào được nhận — nó chỉ trả về giá của một phép thay. Ngưỡng, biên an
 * toàn và luật *"nhập nhằng thì HỎI, không đoán"* nằm ở [VoicePhoneticMatch].
 */
internal object VoicePhoneticConfusions {

    /** Sáu thanh của tiếng Việt. `NGANG` = không dấu. */
    enum class Tone { NGANG, HUYEN, SAC, HOI, NGA, NANG }

    /**
     * Một âm tiết ở dạng so khớp được.
     *
     * @property base bản **đã bỏ dấu** ([VoiceLexicon.deaccent]) — cùng dạng với [VoiceTerm.words].
     * @property tone thanh điệu, hoặc `null` khi **không biết**. Cụm lấy từ nhãn có dấu của bộ đăng ký thì biết;
     *   cụm lấy từ bí danh không dấu của [VoiceSynonyms] thì không. `null` ⇒ thanh **không** được tính vào giá —
     *   không biết thì không phạt.
     */
    data class Syl(val base: String, val tone: Tone?)

    // ── BẢNG GIÁ ─────────────────────────────────────────────────────────────────────────────────

    /** Thay một âm tiết bằng một âm tiết **có trong bảng lẫn** (hoặc cặp đã quan sát). */
    const val SUB_CONFUSED: Double = 0.30

    /** Cùng chữ, **khác mỗi thanh** và là một cặp thanh có trong [TONE_PAIRS]. */
    const val SUB_TONE: Double = 0.15

    /** Hai âm tiết không liên quan — đắt bằng đúng "một từ sai". */
    const val SUB_OTHER: Double = 1.00

    /** Thêm/bớt một âm tiết. Đắt có chủ ý: xem KDoc [VoicePhoneticMatch] về ca *"mở cửa"* → *"mở khóa cửa"*. */
    const val GAP: Double = 0.80

    /** Thêm/bớt một **tiếng đệm** ([VoiceLexicon.FILLERS]) — người ta thêm bớt chúng tuỳ hứng. */
    const val GAP_FILLER: Double = 0.40

    // ── THANH ĐIỆU ───────────────────────────────────────────────────────────────────────────────

    private val TONE_OF: Map<Char, Tone> = mapOf(
        '̀' to Tone.HUYEN, '́' to Tone.SAC, '̃' to Tone.NGA,
        '̉' to Tone.HOI, '̣' to Tone.NANG,
    )

    /**
     * Cặp thanh mà bộ nhận dạng thật sự lẫn.
     *
     * `hỏi`↔`ngã` là cặp **chính tả** người Việt lẫn nhiều nhất (và giọng Nam gộp hẳn hai thanh này);
     * `sắc`↔`nặng` cùng đường nét ngắn-gấp; `ngang`↔`huyền` là cặp **nhẹ** — hai thanh bằng, chỉ khác cao độ,
     * nên nó hay xuất hiện khi nói nhanh.
     */
    private val TONE_PAIRS: Set<Pair<Tone, Tone>> = symmetric(
        listOf(Tone.HOI to Tone.NGA, Tone.SAC to Tone.NANG, Tone.NGANG to Tone.HUYEN),
    )

    /** Thanh của một chữ **có dấu**; chữ đã bỏ dấu luôn ra [Tone.NGANG] ⇒ chỗ gọi phải truyền chữ GỐC. */
    fun toneOf(word: String): Tone {
        val nfd = java.text.Normalizer.normalize(word.lowercase(), java.text.Normalizer.Form.NFD)
        nfd.forEach { ch -> TONE_OF[ch]?.let { return it } }
        return Tone.NGANG
    }

    /** Chữ gốc → âm tiết so khớp được (bỏ dấu để lấy [Syl.base], giữ thanh làm thông tin thêm). */
    fun syl(word: String): Syl = Syl(VoiceLexicon.deaccent(word), toneOf(word))

    // ── BA NHÓM LẪN ÂM ───────────────────────────────────────────────────────────────────────────

    /**
     * **Phụ âm đầu.** `v`↔`d`↔`gi` (giọng Nam gộp cả ba), `s`↔`x`, `tr`↔`ch` (giọng Bắc gộp), `r`↔`g`,
     * `l`↔`n` (lẫn vùng đồng bằng Bắc Bộ), `d`↔`r`.
     *
     * `d`↔`r` vào bảng ngày 2026-09-16 vì **[ĐO ×2]**: `dừng nhạc` → *"rừng nhạc"* ở lượt máy ảo 09-15
     * (`emulator-voice-e2e-2026-09-15.md` §3 L3, ca `w09` — cũng là một trong ba phép đo đẻ ra tệp hotword, xem
     * KDoc [SherpaSpokenWords]) và **đo lại** tối 09-16 sau khi VAD cắt đuôi làm lệch nhẹ tệp WAV ấy. Đã quét lại
     * 4 486 câu `variants.tsv` + bộ ca âm tính sau khi thêm: **0 câu đổi kết quả** ⇒ cặp này không nới cửa cho ai.
     */
    private val INITIALS: Set<Pair<String, String>> = symmetric(
        chain("v", "d", "gi") + chain("s", "x") + chain("tr", "ch") + chain("r", "g") + chain("l", "n") +
            chain("d", "r"),
    )

    /** **Phụ âm cuối.** `t`↔`c` · `n`↔`ng` · `p`↔`c` — ba cặp mà giọng Nam gần như không phân biệt. */
    private val FINALS: Set<Pair<String, String>> = symmetric(
        listOf("t" to "c", "n" to "ng", "p" to "c"),
    )

    /**
     * **Nguyên âm** — khai bằng chữ CÓ DẤU (đó là chỗ người đọc nhận ra chúng), rồi nở + bỏ dấu.
     *
     * ## ⚠⚠ Vì sao chỉ giữ cặp mà **vế dài hơn có ≥ 2 chữ cái**
     * [ĐO nguyên mẫu host 2026-09-16] `ô`↔`â` sau khi bỏ dấu là `o`↔`a` — tức **hai nguyên âm khác hẳn nhau**,
     * không còn là một dấu mũ trượt nấc. Bật nó lên thì *"bật cái đó"* (`cai do`) khớp *"Cài đặt"* (`cai dat`)
     * với giá 0,15 và máy **mở Cài đặt** cho một câu cố ý không phải lệnh — đúng ca mà
     * `VoiceIntentParserTest` đang khoá. `ê`↔`e` thì bỏ dấu xong **trùng hệt** ⇒ đã miễn phí (giá 0), không
     * cần khai. Chỉ `ươ`↔`ơ` = `uo`↔`o` sống sót: nó là một **vần**, không phải một chữ cái, nên nó vẫn mang
     * đúng nghĩa *"trượt một nấc"* sau khi bỏ dấu.
     *
     * Cặp `ô`↔`â` THẬT đo được (*"cốp"*→*"cấp"*) không mất đi: nó đi đường [OBSERVED], nơi mỗi dòng có chỗ
     * quan sát kèm theo — đo được thì nhận, suy ra thì không (CLAUDE.md §2).
     */
    private val VOWELS: Set<Pair<String, String>> = symmetric(
        (chain("ô", "â", "o") + chain("ê", "e") + chain("ươ", "ơ"))
            .map { VoiceLexicon.deaccent(it.first) to VoiceLexicon.deaccent(it.second) }
            .filter { it.first != it.second && maxOf(it.first.length, it.second.length) >= 2 },
    )

    // ── CẶP ĐÃ QUAN SÁT ──────────────────────────────────────────────────────────────────────────

    /** Chỗ đã **nhìn thấy** một cặp — để người sau phân biệt phép đo với suy luận (CLAUDE.md §2). */
    enum class Seen {
        /** Bản thu GIỌNG THẬT 2026-09-16 (`owner-rec/segments-*-shipping.json`) — bằng chứng mạnh nhất. */
        REC,

        /** Corpus tổng hợp trên host (`docs/diagnostics/voice-mishear-2026-09-16.md` §5 · `aliases-proposed.tsv`). */
        HOST,
    }

    /**
     * @property right chữ người ta NÓI · [heard] chữ mô hình IN RA · [seen] chỗ quan sát · [note] câu/mốc cụ thể.
     */
    data class Observed(val right: String, val heard: String, val seen: Seen, val note: String)

    /**
     * Các cặp **đã đo**, mỗi dòng kèm chỗ quan sát.
     *
     * Đây là DỮ LIỆU, không phải cơ chế: xoá hết bảng này thì ba nhóm quy luật ở trên vẫn chạy, chỉ là mất
     * những cặp mà quy luật không phủ (vd `nhiệt`→`mật` đổi cả phụ âm đầu lẫn vần).
     */
    val OBSERVED: List<Observed> = listOf(
        Observed("cốp", "cấp", Seen.REC, "miennam-a #39 · b #26 — «mở cốp sau»"),
        Observed("cốp", "góc", Seen.REC, "owner #59 — «mở cốp sau» → MỞ GÓC SAU"),
        Observed("đọc", "độc", Seen.REC, "miennam-a #10 · c #9 — «bật đèn đọc» (bỏ dấu xong trùng ⇒ giá 0)"),
        Observed("pin", "bên", Seen.REC, "miennam-c #24 — «xem pin» → XEM BÊN"),
        Observed("pin", "biên", Seen.REC, "owner #39 · #57 — «pin còn bao nhiêu» → BIÊN CÒN BAO NHIÊU"),
        Observed("pin", "bin", Seen.REC, "bé #14"),
        Observed("pin", "phim", Seen.REC, "owner #38 — «xem pin» → XEM PHIM"),
        Observed("pin", "tin", Seen.HOST, "emulator-voice-e2e-2026-09-15 §3 L3 · mishear §5 «tin còn bao nhiêu» ×4"),
        // ⚠ `pin`↔`binh` (owner có nêu) **KHÔNG** vào bảng: [ĐO quét corpus 2026-09-16] `scripts/voice/data/
        // variants.tsv` có 5 dòng *"kiểm tra bình"* / *"bình còn bao nhiêu"* mà người soạn gắn cho **`volt_12v`**
        // (ắc-quy 12V), không phải `soc`. Nhận cặp này là trả lời sai datum — đúng lý do [VoiceSynonyms] §3 đã
        // từ chối bí danh một từ `"bình"` từ trước. Đường chữa đúng cho nó là một bí danh của `volt_12v`.
        Observed("dừng", "dần", Seen.REC, "miennam-c #34 — «dừng nhạc» → DẦN NHẠC"),
        // ⚠ Cặp này phải có mặt **ở đây** chứ không chỉ ở nhóm `d`↔`r` của [INITIALS]: `dừng` là cụm MỘT âm tiết,
        // mà cụm một âm tiết chỉ đi qua [nearMiss] (cặp đã ĐO), không đi qua quy luật. Đo hai lần, hai lượt khác
        // nhau ⇒ đúng điều kiện lên bảng này.
        Observed("dừng", "rừng", Seen.HOST, "emulator-voice-e2e-2026-09-15 §3 L3 (w09) · đo lại 2026-09-16 sau VAD trim"),
        Observed("quây", "quay", Seen.REC, "cả 5 bản — «mở Waze»; đã có sẵn ở VoiceSynonyms.APP_TARGETS"),
        Observed("kính", "kiếng", Seen.HOST, "nouns.tsv vùng nam; đã có bí danh riêng"),
        Observed("kính", "kiêng", Seen.REC, "bé #8 — «hạ kính trước trái» → HẠT KIÊNG TRƯỚC KHI"),
        Observed("kính", "kim", Seen.REC, "bé #51 — HẠ KIM TRƯỚC TRÁI"),
        Observed("kính", "kín", Seen.HOST, "emulator-voice-e2e-2026-09-15 §3 L3 — «mở kính trước trái»"),
        Observed("sưởi", "sửi", Seen.HOST, "mishear §5 · ghế sưởi"),
        Observed("sưởi", "sữa", Seen.HOST, "mishear §5"),
        Observed("nhiệt", "mật", Seen.REC, "owner #51 — «nhiệt độ …» → TÁM MẬT ĐỘ"),
        Observed("mở", "ở", Seen.REC, "miennam-c #31 — «mở cốp sau» → Ở CẤP SAU"),
        Observed("mở", "mã", Seen.REC, "owner #50 — «mở máy lạnh» → BẢY MÃ MÁY LẠNH"),
        Observed("mở", "mỡ", Seen.REC, "bé #3 — CÂU BẢY MỠ NHIỆT ĐỘ"),
        Observed("hết", "máy", Seen.REC, "owner #37 — «đóng hết kính» → ĐÓNG MÁY KÍNH (nói nhanh)"),
        Observed("đèn", "đang", Seen.HOST, "mishear §5 — «đèn đọc sách» → đang đọc sách"),
        Observed("bật", "bằng", Seen.REC, "owner #58 — «bật ghế sưởi» → BẰNG GHẾ SƯỞI"),
        Observed("xem", "xe", Seen.REC, "bé #36 · #53 — «xem áp suất lốp…» → XE ÁP SUẤT"),
        Observed("xem", "sẽ", Seen.REC, "owner #40 — SẼ ÁP SUẤT LỐP TRƯỚC TRÁI"),
        Observed("hạ", "hạt", Seen.REC, "bé #8"),
        Observed("lái", "gái", Seen.REC, "owner #45 — «chế độ lái thể thao» → CHẾ ĐỘ GÁI THỂ THAO"),
        Observed("lái", "đá", Seen.REC, "bé #38 — CHẾ ĐỘ ĐÁ THỂ THAO"),
        Observed("giảm", "mầm", Seen.REC, "miennam-b #16 — «giảm âm lượng» → MẦM LƯỢNG"),
    )

    /**
     * [OBSERVED] ở dạng tra cứu: đã bỏ dấu, **đối xứng hai chiều**, bỏ cặp trùng nhau sau khi bỏ dấu.
     *
     * Cặp tự triệt tiêu (`đọc`/`độc` → `doc`/`doc`) không mất gì: chúng đã bằng nhau ⇒ giá 0, rẻ hơn 0,30.
     */
    private val OBSERVED_PAIRS: Set<Pair<String, String>> = symmetric(
        OBSERVED.map { VoiceLexicon.deaccent(it.right) to VoiceLexicon.deaccent(it.heard) },
    ).filterTo(HashSet()) { it.first != it.second }

    // ── GIÁ MỘT PHÉP THAY ────────────────────────────────────────────────────────────────────────

    /**
     * Giá thay âm tiết [a] (nghe được) bằng [b] (trong từ vựng).
     *
     * Thứ tự xét là một phần của hợp đồng: **trùng chữ** trước (thanh chỉ phạt khi biết cả hai), rồi **cặp đã
     * đo**, cuối cùng mới tới quy luật. Đảo lại thì một cặp đã đo (vd `nhiệt`/`mật`, đổi cả đầu lẫn vần) rơi
     * vào nhánh quy luật và bị tính 1,00 — tức phép đo thua suy luận.
     */
    @Suppress("ReturnCount")
    fun cost(a: Syl, b: Syl): Double {
        if (a.base == b.base) {
            val ta = a.tone
            val tb = b.tone
            if (ta == null || tb == null || ta == tb) return 0.0
            return if ((ta to tb) in TONE_PAIRS) SUB_TONE else SUB_CONFUSED
        }
        if ((a.base to b.base) in OBSERVED_PAIRS) return SUB_CONFUSED
        val x = parts(a.base)
        val y = parts(b.base)
        var diff = 0
        var listed = false
        if (x.onset != y.onset) { diff++; listed = (x.onset to y.onset) in INITIALS }
        if (x.nucleus != y.nucleus) { diff++; listed = (x.nucleus to y.nucleus) in VOWELS }
        if (x.coda != y.coda) { diff++; listed = (x.coda to y.coda) in FINALS }
        // Đúng MỘT thành phần lệch, và cái lệch ấy có tên trong bảng. Hai thành phần cùng lệch là một âm tiết
        // khác hẳn — nhận nó là mở cửa cho mọi từ đời thường chui vào từ vựng của xe.
        return if (diff == 1 && listed) SUB_CONFUSED else SUB_OTHER
    }

    /**
     * Cặp [a]/[b] có phải **trùng chữ** hoặc một cặp ĐÃ ĐO không — cổng riêng cho cụm **một âm tiết**.
     *
     * ## Vì sao cụm một âm tiết phải chặt hơn
     * [ĐO nguyên mẫu host] từ vựng có hàng chục cụm một âm tiết (*"kính"*, *"pin"*, *"gió"*, *"quạt"*…). Cho
     * quy luật chạy tự do ở đó thì `vợ` → `gió` (v↔gi), `đó` → `gió` (d↔gi) — tức một tiếng đời thường bất kỳ
     * cũng cướp được một nút của xe. Đây đúng là luật §3 mà [VoiceSynonyms] đã viết ra cho bí danh một từ,
     * chỉ là áp cho tầng lẫn âm.
     */
    fun nearMiss(a: Syl, b: Syl): Boolean = a.base == b.base || (a.base to b.base) in OBSERVED_PAIRS

    // ── Tách âm tiết ─────────────────────────────────────────────────────────────────────────────

    /** Ba thành phần của một âm tiết đã bỏ dấu. */
    data class Parts(val onset: String, val nucleus: String, val coda: String)

    /** Dài trước ngắn — `ngh` phải được thử trước `ng`, `gi` trước `g`, nếu không `"gia"` tách thành `g`+`ia`. */
    private val ONSETS = listOf(
        "ngh", "ng", "nh", "ch", "gh", "gi", "kh", "ph", "qu", "th", "tr",
        "b", "c", "d", "g", "h", "k", "l", "m", "n", "p", "q", "r", "s", "t", "v", "x",
    )

    private val CODAS = listOf("nh", "ng", "ch", "c", "m", "n", "p", "t")

    /** Tách `"trang"` → (`tr`, `a`, `ng`). Luôn chừa lại phần vần **không rỗng** (`"ba"` → (`b`, `a`, ``)). */
    fun parts(base: String): Parts {
        val onset = ONSETS.firstOrNull { base.startsWith(it) && base.length > it.length } ?: ""
        val rest = base.substring(onset.length)
        val coda = CODAS.firstOrNull { rest.endsWith(it) && rest.length > it.length } ?: ""
        return Parts(onset, rest.substring(0, rest.length - coda.length), coda)
    }

    // ── Tiện ích khai bảng ───────────────────────────────────────────────────────────────────────

    /** Quan hệ đối xứng thì khai MỘT chiều — viết tay hai chiều là mời một chiều bị quên. */
    private fun <T> symmetric(pairs: List<Pair<T, T>>): Set<Pair<T, T>> =
        pairs.flatMapTo(HashSet()) { listOf(it, it.second to it.first) }

    /** Nhóm `a↔b↔c` ⇒ mọi cặp trong nhóm. */
    private fun chain(vararg items: String): List<Pair<String, String>> =
        items.indices.flatMap { i -> (i + 1 until items.size).map { j -> items[i] to items[j] } }
}
