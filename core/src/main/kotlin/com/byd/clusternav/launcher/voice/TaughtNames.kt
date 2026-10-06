package com.byd.clusternav.launcher.voice

/**
 * ═══ 2.91 VOICE-APP-NAMES · C1 — TÊN APP NGƯỜI LÁI TỰ DẠY (dữ liệu + định dạng lưu) ════════════════════════════
 *
 * Spec `docs/specs/kachi-290-voice-app-names.html` §4.5 · R5. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Lưu CHỮ mô hình in ra, không lưu tiếng (spec §2.1 · OQ1)
 * Bộ hiểu lệnh làm việc trên chuỗi chữ; thứ cần nhớ là *chuỗi mô hình thật sự in ra cho giọng + mic của người đó*
 * (vd Netflix ⇒ *"nep leag"*), không phải cách đọc ta đoán. [accented] là chữ thường CÓ DẤU — đúng thứ mô hình in —
 * và là nguồn sự thật cho hotword (HOA có dấu mới mã hoá được BPE). [norm] luôn **tính lại** từ [accented].
 *
 * Tên mang theo [label] lúc dạy CHỈ để hiển thị khi app đã gỡ (tên gắn với GÓI, không với nhãn).
 */
data class TaughtName(
    val pkg: String,
    val source: TaughtSource,
    val accented: String,
    val label: String,
) {
    /** Dãy từ đã bỏ dấu — cùng phép tách của [VoiceLexicon.tokenize] mà bộ phân tích dùng. */
    val words: List<String> get() = VoiceLexicon.tokenize(accented).map { it.norm }

    /** [words] nối bằng một dấu cách — khoá so trùng. */
    val norm: String get() = words.joinToString(" ")

    /** Chữ để HIỆN cho một tên của app đã gỡ: nhãn lúc dạy, rỗng ⇒ [fallback] (thường là tên gói). */
    fun shownLabel(fallback: String): String = label.ifBlank { fallback }
}

/** Nguồn của một tên: [SPEECH] = chữ mô hình in ra (vào hotword) · [TYPED] = người dùng gõ (chỉ khớp chữ, OQ3). */
enum class TaughtSource(val code: String) { SPEECH("S"), TYPED("T") }

/**
 * Định dạng TSV có phiên bản của khoá hồ sơ `voice_app_names`:
 * ```
 * kachi-names	v1
 * <gói>	<S|T>	<dạng có dấu>	<dạng chuẩn hoá>	<nhãn lúc dạy>
 * ```
 * Giải mã **không ném** (khuôn [VoiceGrammarSnapshot.decode]): dòng hỏng ⇒ bỏ dòng đó; header/phiên bản lạ ⇒ rỗng +
 * [Decoded.readOnly] = `true` — dữ liệu từ một bản Kachi MỚI hơn không bao giờ bị bản này ghi đè (hạ cấp/nhập chéo).
 * Lý do bỏ ([Decoded.problem]) chỉ nói số dòng/độ dài, KHÔNG nhắc lại chữ người dùng (R-nf3).
 */
object TaughtNamesCodec {
    const val HEADER = "kachi-names"
    const val VERSION = "v1"

    /** Trần (spec OQ4): 4 tên/app = 3 giọng + 1 gõ; 120 tên/hồ sơ (tính cả tên của app đã gỡ). */
    const val MAX_PER_APP = 4
    const val MAX_SPEECH_PER_APP = 3
    const val MAX_TYPED_PER_APP = 1
    const val MAX_PER_PROFILE = 120

    private const val SEP = '\t'
    private val PKG = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    private val BREAKS = Regex("[\\t\\r\\n]+")
    private val SPACES = Regex("\\s+")

    /** Khử ký tự ngăn cột/dòng ở cửa vào + gom khoảng trắng. */
    fun clean(raw: String): String = raw.replace(BREAKS, " ").replace(SPACES, " ").trim()

    /** Dạng có dấu chuẩn: [clean] + chữ thường (mô hình in HOA, tầng nghe hạ thường — một dạng cho cả hai nguồn). */
    fun cleanAccented(raw: String): String = clean(raw).lowercase()

    fun validPackage(pkg: String): Boolean = pkg.length <= MAX_PKG_LEN && PKG.matches(pkg)

    data class Decoded(val names: List<TaughtName>, val readOnly: Boolean, val problem: String?)

    fun encode(names: List<TaughtName>): String = buildString {
        append(HEADER).append(SEP).append(VERSION).append('\n')
        names.forEach { n ->
            append(n.pkg).append(SEP).append(n.source.code).append(SEP).append(cleanAccented(n.accented)).append(SEP)
                .append(n.norm).append(SEP).append(clean(n.label)).append('\n')
        }
    }

    @Suppress("ReturnCount")
    fun decode(raw: String?): Decoded {
        if (raw.isNullOrBlank()) return Decoded(emptyList(), readOnly = false, problem = null)
        val lines = raw.split('\n')
        val head = lines[0].split(SEP)
        if (head.getOrNull(0) != HEADER) return Decoded(emptyList(), readOnly = true, problem = "header lạ (${lines[0].length} ký tự)")
        if (head.getOrNull(1) != VERSION) return Decoded(emptyList(), readOnly = true, problem = "phiên bản lạ (${head.getOrNull(1)?.length ?: 0} ký tự)")
        var out: List<TaughtName> = emptyList()
        var problem: String? = null
        fun note(msg: String) { if (problem == null) problem = msg }
        lines.drop(1).forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            val row = i + 2
            val c = line.split(SEP)
            val source = TaughtSource.entries.firstOrNull { it.code == c.getOrNull(1) }
            val name = TaughtName(
                pkg = c.getOrNull(0).orEmpty().trim(),
                source = source ?: TaughtSource.TYPED,
                accented = cleanAccented(c.getOrNull(2).orEmpty()),
                label = clean(c.getOrNull(4).orEmpty()),
            )
            when {
                c.size < COLUMNS -> note("dòng $row: thiếu cột")
                source == null -> note("dòng $row: nguồn lạ")
                !validPackage(name.pkg) -> note("dòng $row: gói hỏng")
                name.words.isEmpty() -> note("dòng $row: tên rỗng")
                // Tệp hồ sơ NHẬP từ xe/người khác là dữ liệu ngoài: tên phải còn đúng HÌNH DẠNG một cái tên ([TeachSample.shape]
                // — mọi đường dạy đều đã qua nó: 1–4 từ · ≥ 3 chữ cái · ≤ 48 ký tự) ⇒ chuỗi rác/khổng lồ không vào từ vựng + hotword.
                TeachSample.shape(name.accented) !is TeachSample.Sample -> note("dòng $row: tên sai hình dạng")
                else -> {
                    // Cột chuẩn hoá chỉ để đọc tay/so khi nhập; lệch ⇒ dùng bản TÍNH LẠI (một nguồn sự thật).
                    if (c[3].trim() != name.norm) note("dòng $row: cột chuẩn hoá lệch — dùng bản tính lại")
                    when (val r = TaughtNames.add(out, name)) {
                        is TaughtNames.Added -> out = r.names
                        is TaughtNames.Refused -> note("dòng $row: bỏ (${r.why.name.lowercase()})")
                    }
                }
            }
        }
        return Decoded(out, readOnly = false, problem = problem)
    }

    private const val COLUMNS = 5
    private const val MAX_PKG_LEN = 255
}

/** Phép sửa danh sách tên — thuần, một chỗ, cho UI · cầu kiểm thử · giải mã (cùng trần, cùng luật trùng). */
object TaughtNames {

    enum class Why { DUPLICATE, FULL_APP, FULL_SOURCE, FULL_PROFILE, EMPTY }

    sealed interface Result
    data class Added(val names: List<TaughtName>) : Result
    data class Refused(val why: Why) : Result

    /** Thêm [n] nếu chưa trùng (cùng gói + cùng [TaughtName.norm]) và còn trần. Không tự xoá gì để lấy chỗ. */
    @Suppress("ReturnCount")
    fun add(list: List<TaughtName>, n: TaughtName): Result {
        if (n.words.isEmpty()) return Refused(Why.EMPTY)
        if (list.any { it.pkg == n.pkg && it.norm == n.norm }) return Refused(Why.DUPLICATE)
        val mine = list.filter { it.pkg == n.pkg }
        if (mine.size >= TaughtNamesCodec.MAX_PER_APP) return Refused(Why.FULL_APP)
        val cap = if (n.source == TaughtSource.SPEECH) TaughtNamesCodec.MAX_SPEECH_PER_APP else TaughtNamesCodec.MAX_TYPED_PER_APP
        if (mine.count { it.source == n.source } >= cap) return Refused(Why.FULL_SOURCE)
        if (list.size >= TaughtNamesCodec.MAX_PER_PROFILE) return Refused(Why.FULL_PROFILE)
        return Added(list + n.copy(accented = TaughtNamesCodec.cleanAccented(n.accented), label = TaughtNamesCodec.clean(n.label)))
    }

    fun remove(list: List<TaughtName>, pkg: String, norm: String): List<TaughtName> =
        list.filterNot { it.pkg == pkg && it.norm == norm }

    fun removeApp(list: List<TaughtName>, pkg: String): List<TaughtName> = list.filterNot { it.pkg == pkg }

    fun of(list: List<TaughtName>, pkg: String): List<TaughtName> = list.filter { it.pkg == pkg }
}
