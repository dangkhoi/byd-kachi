package com.byd.clusternav.testsupport

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText

/**
 * ═══ BỘ QUÉT LỜI GỌI DỊCH trong mã nguồn Kotlin (TEST-ONLY) ═════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-zh-th-ms.html` §4.2 (a). Tìm mọi lời gọi `Strings.t` · `Strings.pick` · `Strings.f` ·
 * `Strings.fIn` (`:core`, kể cả bí danh `CoreStrings`) · `Lang.t` · `Lang.f` (`:app`, kể cả `ClusterNavLang`) · và
 * hàm bọc cục bộ `s(…)` của `KeyCtlTarget.kt`, rồi lấy ra HAI chữ nguồn (vi, en) — chính là khoá bảng dịch ZH/TH/MS.
 *
 * ## Vì sao một bộ lex theo trạng thái, không phải regex trên văn bản thô
 * Cùng lý do [KotlinSource]: `"//"` trong chuỗi không phải chú thích, dấu `)` trong chuỗi không đóng lời gọi, và
 * `${…}` trong chuỗi chứa cả ngoặc lẫn chuỗi lồng. Regex ngây thơ sẽ cắt sai đối số ⇒ bài canh MÙ (xanh oan) đúng
 * chỗ cần bắt. Bộ lex ở đây là bản chuyển từ `scan.py`/`scanlib.py` của đợt kiểm kê 2026-10-03 (cùng kết quả đếm).
 *
 * ## Phân loại đối số ([ArgKind])
 *  • [ArgKind.LIT] — một chữ `"…"` (hoặc `"""…"""`) KHÔNG có `$`, hoặc nhiều chữ như thế nối bằng `+` (cả xuống
 *    dòng). [Arg.value] = nội dung đã giải escape — đúng chuỗi lúc chạy, tức đúng khoá bảng dịch.
 *  • [ArgKind.TEMPLATE] — chữ có `$x`/`${…}`: khoá đổi theo giá trị ⇒ không bao giờ dịch được (phải dùng `Strings.f`).
 *  • [ArgKind.EXPR] — mọi thứ khác (biến, lời gọi, `if`…): dữ liệu lúc chạy, phải được duyệt bằng đối tượng thật.
 *
 * Giới hạn đã biết: dấu `,` trong `<A, B>` của kiểu tổng quát sẽ bị coi là ranh giới đối số (chưa gặp trong các
 * lời gọi dịch). Trường hợp đó rơi về [ArgKind.EXPR] ⇒ bài canh ĐỎ chứ không im (fail-closed).
 */
object I18nCallScanner {

    /** Một hàm dịch: [pattern] khớp tới `(`; hai chữ nguồn ở vị trí đối số [viIndex] và [viIndex] + 1. */
    class Fn(val name: String, val pattern: Regex, val viIndex: Int, val onlyFileName: String? = null)

    private const val NOT_AFTER = """(?<![\w.$])"""
    private const val CORE_Q = """(?:com\.byd\.clusternav\.launcher\.)?(?:Core)?Strings"""
    private const val APP_Q = """(?:com\.byd\.clusternav\.)?(?:ClusterNav)?Lang"""

    val FNS: List<Fn> = listOf(
        Fn("Strings.t", Regex("""$NOT_AFTER$CORE_Q\.t\s*\("""), 0),
        Fn("Strings.pick", Regex("""$NOT_AFTER$CORE_Q\.pick\s*\("""), 0),
        Fn("Strings.f", Regex("""$NOT_AFTER$CORE_Q\.f\s*\("""), 0),
        Fn("Strings.fIn", Regex("""$NOT_AFTER$CORE_Q\.fIn\s*\("""), 1),
        Fn("Lang.t", Regex("""$NOT_AFTER$APP_Q\.t\s*\("""), 0),
        Fn("Lang.f", Regex("""$NOT_AFTER$APP_Q\.f\s*\("""), 0),
        Fn("s", Regex("""$NOT_AFTER\bs\s*\("""), 0, onlyFileName = "KeyCtlTarget.kt"),
    )

    enum class ArgKind { LIT, TEMPLATE, EXPR }

    /** @property text mã nguồn của đối số (đã bỏ tên `x =` nếu có) · @property value chuỗi lúc chạy khi [kind] = LIT. */
    data class Arg(val kind: ArgKind, val text: String, val value: String?)

    /**
     * Một lời gọi dịch.
     *
     * @property path đường dẫn tương đối từ gốc repo (dấu `/`).
     * @property site khoá ổn định cho danh sách cho-phép: `<module>:<đường sau com/byd/clusternav/>` (vd
     *   `core:launcher/Lang.kt`) — không chứa số dòng, nên sửa tệp không làm danh sách mục.
     * @property symbol tên khai báo `fun`/`val`/`var` gần nhất PHÍA TRƯỚC lời gọi (ngữ cảnh cho người đọc + khoá).
     * @property args số đối số SAU hai chữ nguồn (với `f`/`fIn`: số giá trị thay vào `{0}`…; với `t`: `lang` nếu có).
     */
    data class Call(
        val path: String,
        val site: String,
        val line: Int,
        val fn: String,
        val symbol: String,
        val vi: Arg?,
        val en: Arg?,
        val args: Int = 0,
    ) {
        val where: String get() = "$path:$line"
        val isLiteralPair: Boolean get() = vi?.kind == ArgKind.LIT && en?.kind == ArgKind.LIT
        val hasTemplate: Boolean get() = vi?.kind == ArgKind.TEMPLATE || en?.kind == ArgKind.TEMPLATE
    }

    /** Hai cây nguồn được quét (tương đối từ gốc repo). */
    val SOURCE_DIRS: List<String> = listOf("app/src/main/java", "core/src/main/kotlin")

    /**
     * Gốc repo: `-Dclusternav.root` (Gradle đặt cho `:core:test`) hoặc đi ngược từ thư mục làm việc tới chỗ có
     * `settings.gradle.kts`.
     */
    fun repoRoot(): Path {
        System.getProperty("clusternav.root")?.let { p -> Paths.get(p).takeIf { Files.isDirectory(it) }?.let { return it } }
        var dir: Path? = Paths.get("").toAbsolutePath()
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) return dir
            dir = dir.parent
        }
        error("không tìm thấy gốc repo (settings.gradle.kts) từ ${Paths.get("").toAbsolutePath()}")
    }

    /** Quét cả [SOURCE_DIRS]. Nổ nếu một cây không tồn tại (quét rỗng = bài canh xanh giả). */
    fun scanRepo(root: Path = repoRoot()): List<Call> = SOURCE_DIRS.flatMap { dir ->
        val base = root.resolve(dir)
        require(Files.isDirectory(base)) { "không thấy cây nguồn $base — bài canh sẽ quét rỗng" }
        Files.walk(base).use { s -> s.filter { it.extension == "kt" }.sorted().toList() }.flatMap { file ->
            scanSource(root.relativize(file).invariantSeparatorsPathString, file.readText())
        }
    }

    /** Quét MỘT tệp. [path] tương đối từ gốc repo (để dựng [Call.site]). Thuần — test tự dựng nguồn rồi gọi. */
    fun scanSource(path: String, src: String): List<Call> {
        // Token lồng trong `${…}` của chuỗi cũng được lex: lời gọi dịch nằm trong một biểu thức template vẫn là mã
        // chạy thật, bỏ qua nó là bài canh mù đúng chỗ (fail-closed: thấy hết, phân loại sau).
        val toks = KotlinLexer.tokenizeDeep(src)
        val ends = HashMap<Int, Int>(toks.size * 2).apply { toks.forEach { put(it.start, it.end) } }
        val byStart = toks.associateBy { it.start }
        val inTok = BooleanArray(src.length)
        for (t in toks) for (i in t.start until t.end) inTok[i] = true
        for (r in toks.flatMap { KotlinLexer.templateExprRanges(src, it) }) for (i in r) inTok[i] = false
        for (t in toks.filter { it.nested }) for (i in t.start until t.end) inTok[i] = true
        val codeOnly = String(CharArray(src.length) { i -> if (inTok[i]) ' ' else src[i] })
        val fileName = path.substringAfterLast('/')
        val out = ArrayList<Call>()
        for (fn in FNS) {
            if (fn.onlyFileName != null && fn.onlyFileName != fileName) continue
            for (m in fn.pattern.findAll(src)) {
                if (inTok[m.range.first]) continue
                val before = codeOnly.substring(maxOf(0, m.range.first - 4), m.range.first)
                if (before.endsWith("fun ")) continue                       // định nghĩa, không phải lời gọi
                val open = m.range.last
                val close = KotlinLexer.matchClose(src, open, ends)
                if (close < 0) continue
                val args = KotlinLexer.splitArgs(src, open + 1, close, ends)
                val vi = args.getOrNull(fn.viIndex)?.let { classify(src, it.first, it.last + 1, byStart) }
                val en = args.getOrNull(fn.viIndex + 1)?.let { classify(src, it.first, it.last + 1, byStart) }
                out.add(
                    Call(
                        path, siteOf(path), lineOf(src, m.range.first), fn.name, symbolBefore(codeOnly, m.range.first),
                        vi, en, maxOf(0, args.size - fn.viIndex - 2),
                    ),
                )
            }
        }
        return out.sortedBy { it.line }
    }

    /** `core/src/main/kotlin/com/byd/clusternav/launcher/Lang.kt` → `core:launcher/Lang.kt`. */
    fun siteOf(path: String): String {
        val module = path.substringBefore('/')
        val inner = path.substringAfter("com/byd/clusternav/", path)
        return "$module:$inner"
    }

    private fun lineOf(src: String, pos: Int): Int {
        var n = 1
        for (i in 0 until pos) if (src[i] == '\n') n++
        return n
    }

    private val DECL = Regex("""\b(?:fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)""")

    private fun symbolBefore(codeOnly: String, pos: Int): String =
        DECL.findAll(codeOnly.substring(0, pos)).lastOrNull()?.groupValues?.get(1) ?: "<top>"

    private val NAMED = Regex("""^(\w+)\s*=(?!=)\s*""")

    /** Phân loại đối số trong [start, end) — xem KDoc lớp. */
    private fun classify(src: String, start: Int, end: Int, byStart: Map<Int, KotlinLexer.Tok>): Arg {
        var s = start
        while (s < end && src[s].isWhitespace()) s++
        var e = end
        while (e > s && src[e - 1].isWhitespace()) e--
        NAMED.find(src.substring(s, e))?.let { s += it.value.length }
        val text = src.substring(s, e)
        // Chuỗi: STRING (ws/chú thích '+' ws/chú thích STRING)*
        val parts = ArrayList<KotlinLexer.Tok>()
        var i = s
        var expectString = true
        while (i < e) {
            val c = src[i]
            if (c.isWhitespace()) { i++; continue }
            val tok = byStart[i]
            when {
                tok != null && tok.kind == KotlinLexer.Kind.COMMENT -> i = tok.end
                expectString && tok != null && (tok.kind == KotlinLexer.Kind.STR || tok.kind == KotlinLexer.Kind.RAW) -> {
                    if (tok.end > e) return Arg(ArgKind.EXPR, text, null)
                    parts.add(tok); i = tok.end; expectString = false
                }
                !expectString && c == '+' -> { i++; expectString = true }
                else -> return Arg(ArgKind.EXPR, text, null)
            }
        }
        if (parts.isEmpty() || expectString) return Arg(ArgKind.EXPR, text, null)
        if (parts.any { KotlinLexer.hasTemplate(src, it) }) return Arg(ArgKind.TEMPLATE, text, null)
        return Arg(ArgKind.LIT, text, parts.joinToString("") { KotlinLexer.decode(src, it) })
    }
}

/** Bộ lex tối thiểu của Kotlin cho [I18nCallScanner]: chú thích (lồng nhau), chuỗi thường/raw (có `${…}` lồng), char. */
object KotlinLexer {

    enum class Kind { COMMENT, STR, RAW, CHAR }

    /** @property nested token nằm TRONG một biểu thức `${…}` của chuỗi khác. */
    data class Tok(val kind: Kind, val start: Int, val end: Int, val nested: Boolean = false)

    /** [tokenize] + mọi token bên trong các biểu thức `${…}` của chuỗi (đệ quy). */
    fun tokenizeDeep(src: String): List<Tok> {
        val out = ArrayList<Tok>()
        fun walk(from: Int, to: Int, nested: Boolean) {
            for (t in tokenize(src, from, to)) {
                out.add(if (nested) t.copy(nested = true) else t)
                for (r in templateExprRanges(src, t)) walk(r.first, r.last + 1, true)
            }
        }
        walk(0, src.length, false)
        return out
    }

    /** Khoảng mã của từng biểu thức `${…}` (không gồm ngoặc) trong một chuỗi; rỗng với token khác. */
    fun templateExprRanges(src: String, t: Tok): List<IntRange> {
        if (t.kind != Kind.STR && t.kind != Kind.RAW) return emptyList()
        val raw = t.kind == Kind.RAW
        val out = ArrayList<IntRange>()
        var j = t.start + if (raw) 3 else 1
        while (j < t.end) {
            val c = src[j]
            if (!raw && c == '\\') { j += 2; continue }
            if (c == '$' && j + 1 < t.end && src[j + 1] == '{') {
                val after = skipTemplateExpr(src, j + 1)
                out.add((j + 2) until (after - 1))
                j = after
                continue
            }
            j++
        }
        return out
    }

    fun tokenize(src: String, from: Int = 0, to: Int = src.length): List<Tok> {
        val out = ArrayList<Tok>()
        var i = from
        val n = to
        while (i < n) {
            when {
                src.startsWith("//", i) -> {
                    val j = src.indexOf('\n', i).let { if (it < 0 || it > n) n else it }
                    out.add(Tok(Kind.COMMENT, i, j)); i = j
                }
                src.startsWith("/*", i) -> {
                    var depth = 1
                    var j = i + 2
                    while (j < n && depth > 0) {
                        when {
                            src.startsWith("/*", j) -> { depth++; j += 2 }
                            src.startsWith("*/", j) -> { depth--; j += 2 }
                            else -> j++
                        }
                    }
                    out.add(Tok(Kind.COMMENT, i, j)); i = j
                }
                src.startsWith("\"\"\"", i) -> { val j = skipRaw(src, i); out.add(Tok(Kind.RAW, i, j)); i = j }
                src[i] == '"' -> { val j = skipStr(src, i); out.add(Tok(Kind.STR, i, j)); i = j }
                src[i] == '\'' -> {
                    var j = i + 1
                    if (j < n && src[j] == '\\') j += 2 else j++
                    while (j < n && src[j] != '\'' && src[j] != '\n') j++
                    out.add(Tok(Kind.CHAR, i, minOf(n, j + 1))); i = minOf(n, j + 1)
                }
                else -> i++
            }
        }
        return out
    }

    private fun skipTemplateExpr(src: String, open: Int): Int {
        var depth = 1
        var j = open + 1
        while (j < src.length && depth > 0) {
            when {
                src.startsWith("\"\"\"", j) -> { j = skipRaw(src, j); continue }
                src[j] == '"' -> { j = skipStr(src, j); continue }
                src[j] == '{' -> depth++
                src[j] == '}' -> depth--
            }
            j++
        }
        return j
    }

    private fun skipStr(src: String, start: Int): Int {
        var j = start + 1
        while (j < src.length) {
            val c = src[j]
            when {
                c == '\\' -> { j += 2; continue }
                c == '$' && j + 1 < src.length && src[j + 1] == '{' -> { j = skipTemplateExpr(src, j + 1); continue }
                c == '"' -> return j + 1
                c == '\n' -> return j
            }
            j++
        }
        return j
    }

    private fun skipRaw(src: String, start: Int): Int {
        var j = start + 3
        while (j < src.length) {
            if (src[j] == '$' && j + 1 < src.length && src[j + 1] == '{') { j = skipTemplateExpr(src, j + 1); continue }
            if (src.startsWith("\"\"\"", j)) {
                var k = j + 3
                while (k < src.length && src[k] == '"') k++
                return k
            }
            j++
        }
        return j
    }

    /** Vị trí `)` khớp với `(` ở [open], bỏ qua chuỗi/chú thích; -1 nếu không đóng. */
    fun matchClose(src: String, open: Int, tokEnds: Map<Int, Int>): Int {
        var depth = 0
        var j = open
        while (j < src.length) {
            tokEnds[j]?.let { j = it; continue }
            when (src[j]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (--depth == 0) return j
            }
            j++
        }
        return -1
    }

    /** Khoảng [đầu, cuối] của từng đối số ở độ sâu 0 trong (a, b). Bỏ đối số rỗng (dấu phẩy cuối). */
    fun splitArgs(src: String, a: Int, b: Int, tokEnds: Map<Int, Int>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var depth = 0
        var start = a
        var j = a
        while (j < b) {
            tokEnds[j]?.let { j = it; continue }
            when (src[j]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ',' -> if (depth == 0) { out.add(start until j); start = j + 1 }
            }
            j++
        }
        out.add(start until b)
        return out.filter { r -> src.substring(r.first, r.last + 1).isNotBlank() }
    }

    /** Chuỗi có `$tên`/`${…}` (không thoát) không. */
    fun hasTemplate(src: String, t: Tok): Boolean {
        val raw = t.kind == Kind.RAW
        var j = t.start + if (raw) 3 else 1
        val end = t.end - if (raw) 3 else 1
        while (j < end) {
            val c = src[j]
            if (!raw && c == '\\') { j += 2; continue }
            if (c == '$' && j + 1 < t.end && (src[j + 1] == '{' || src[j + 1].isLetter() || src[j + 1] == '_')) return true
            j++
        }
        return false
    }

    /** Nội dung lúc chạy của một chuỗi KHÔNG có template (giải `\n \t \r \b \" \' \\ \$ \uXXXX`). */
    fun decode(src: String, t: Tok): String {
        // Raw: dấu `"` thừa trước `"""` đóng là NỘI DUNG (`"""a""""` = `a"`) ⇒ cắt đúng 3 ký tự mỗi đầu.
        if (t.kind == Kind.RAW) return src.substring(t.start + 3, maxOf(t.start + 3, t.end - 3))
        val body = src.substring(t.start + 1, t.end - 1)
        val sb = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c != '\\' || i + 1 >= body.length) { sb.append(c); i++; continue }
            when (val e = body[i + 1]) {
                'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b')
                'u' -> { sb.append(body.substring(i + 2, i + 6).toInt(16).toChar()); i += 6; continue }
                else -> sb.append(e)
            }
            i += 2
        }
        return sb.toString()
    }
}
