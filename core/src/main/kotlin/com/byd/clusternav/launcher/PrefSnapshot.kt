package com.byd.clusternav.launcher

/**
 * ═══ S4 · R5 — CHỤP–ÁP: mã hoá **một tệp SharedPreferences** thành một chuỗi, giữ nguyên KIỂU ═════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. Spec `docs/specs/kachi-profiles-are-everything.html` §4.
 *
 * ## Vì sao phải giữ kiểu, không lưu tất cả thành chuỗi
 * `SharedPreferences` **phân biệt kiểu ở tầng đọc**: `getBoolean("cast_enabled", …)` trên một giá trị đã ghi bằng
 * `putString` **ném** `ClassCastException` — không phải trả mặc định, mà ném. Mà chỗ đọc là **dịch vụ đang chạy trên
 * xe** (`FloatingBubbleService`, `NavAccessibilityService`), không phải màn Cài đặt. Nghĩa là một lượt chụp–áp làm
 * mất kiểu sẽ không hỏng ở chỗ đổi hồ sơ; nó hỏng ở lần dịch vụ đọc khoá đó tiếp theo — tức **trên đường**, và với
 * một vết crash không trỏ về nguyên nhân.
 *
 * ## Vì sao tự viết chứ không JSON
 * `:core` không có thư viện JSON (module JVM thuần, không phụ thuộc), và JSON vẫn không giữ được thứ đắt nhất ở đây:
 * phân biệt `Int` với `Long` với `Float`, và phân biệt `Set<String>` với `String`. Dạng có **thẻ kiểu một ký tự** ở
 * đầu mỗi dòng giải quyết đúng bài đó, đọc được bằng mắt khi cần cứu dữ liệu tay (cùng lý do với
 * [WorkspaceGrid.encode]).
 *
 * ## Dạng lưu
 * ```
 * b|cast_enabled|true
 * i|badge_size_dp|120
 * s|autostart_package|com.byd.androidauto
 * S|voicekey_bindings|a,b,c
 * E|tap_rong|
 * n|thiếu_giá_trị|
 * ```
 * Mỗi dòng: `<thẻ kiểu>|<khoá>|<giá trị>` — thẻ: `b` bool · `i` int · `l` long · `f` float · `s` chuỗi · `S` tập ·
 * `E` tập rỗng · `n` null. Khoá và giá trị đều đi qua [esc] nên **không bao giờ** chứa ký tự ngăn
 * cấu trúc — đây là bài học đã trả giá ở sổ cảnh: tên có `|` làm bản ghi ra 8 trường ⇒ mất dữ liệu **im lặng**.
 */
object PrefSnapshot {

    /** Ngăn giữa các bản ghi. */
    private const val REC = "\n"

    /** Ngăn giữa ba trường của một bản ghi. */
    private const val FLD = '|'

    /** Ngăn giữa các phần tử của `Set<String>`. */
    private const val ITEM = ','

    private const val T_BOOL = 'b'
    private const val T_INT = 'i'
    private const val T_LONG = 'l'
    private const val T_FLOAT = 'f'
    private const val T_STRING = 's'
    private const val T_SET = 'S'

    /**
     * Tập RỖNG có thẻ riêng — **không** phải "tập có thân rỗng".
     *
     * ⚠ Đây là ca đã làm bài canh đỏ ngay lần chạy đầu: `emptySet()` và `setOf("")` đều nối ra thân `""`, nên một
     * thẻ duy nhất thì hai giá trị KHÁC NHAU đọc về thành một. Và cả hai đều đạt được thật ở
     * `SharedPreferences.getStringSet` — `voicekey_custom_buttons` rỗng (chưa học nút nào) so với một mục rỗng do
     * chuỗi hỏng. Một thẻ nữa rẻ hơn hẳn việc đoán.
     */
    private const val T_SET_EMPTY = 'E'
    private const val T_NULL = 'n'

    /**
     * Chụp [values] thành một chuỗi. Khoá được **sắp xếp** để hai lần chụp cùng nội dung ra cùng một chuỗi — nhờ vậy
     * so sánh "có gì đổi không" là so chuỗi, và bài test không phụ thuộc thứ tự lặp của `Map`.
     *
     * Giá trị **kiểu lạ** (không thuộc 6 kiểu mà `SharedPreferences` biết) bị **bỏ**, không ném: nguồn là
     * `sp.all`, và một kiểu lạ ở đó nghĩa là dữ liệu đã hỏng từ trước — làm sập lượt đổi hồ sơ vì nó là phản ứng
     * quá tay.
     */
    fun encode(values: Map<String, Any?>): String = values.entries
        .sortedBy { it.key }
        .mapNotNull { (key, value) -> line(key, value) }
        .joinToString(REC)

    private fun line(key: String, value: Any?): String? {
        val k = esc(key)
        return when (value) {
            null -> "$T_NULL$FLD$k$FLD"
            is Boolean -> "$T_BOOL$FLD$k$FLD$value"
            is Int -> "$T_INT$FLD$k$FLD$value"
            is Long -> "$T_LONG$FLD$k$FLD$value"
            is Float -> "$T_FLOAT$FLD$k$FLD$value"
            is String -> "$T_STRING$FLD$k$FLD${esc(value)}"
            is Set<*> ->
                if (value.isEmpty()) {
                    "$T_SET_EMPTY$FLD$k$FLD"
                } else {
                    "$T_SET$FLD$k$FLD${value.joinToString(ITEM.toString()) { esc(it?.toString() ?: "") }}"
                }
            else -> null
        }
    }

    /**
     * Giải mã. **Tự chữa, không bao giờ ném**: dòng sai cấu trúc / thẻ kiểu lạ / số không đọc được ⇒ **bỏ đúng dòng
     * đó**, giữ các dòng còn lại.
     *
     * Vì sao không `require`: chuỗi này đến từ đĩa của một chiếc xe đang chạy và **sửa tay được** (dạng cố ý đọc
     * được). Ném ở đây nghĩa là một ký tự sai làm launcher sập lúc đổi hồ sơ — cùng lập luận đã ghi ở KDoc
     * [SceneBook] về việc *"cố ý không có `init { require(...) }`"*.
     *
     * Giữ cả mục **giá trị `null`**: ở `SharedPreferences` một khoá có mặt-với-giá-trị-null khác hẳn khoá **vắng
     * mặt** (`getString` trả `null` ở cả hai, nhưng `contains` thì không) — và lượt áp cần biết để **xoá** khoá đó ở
     * hồ sơ mới thay vì để nguyên giá trị của hồ sơ cũ.
     */
    fun decode(raw: String?): Map<String, Any?> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Any?>()
        for (line in raw.split(REC)) {
            if (line.isEmpty()) continue
            val f = line.split(FLD)
            if (f.size != 3) continue
            val tag = f[0].singleOrNull() ?: continue
            val key = unesc(f[1])
            if (key.isEmpty()) continue
            val body = f[2]
            val value: Any? = when (tag) {
                T_NULL -> null
                T_BOOL -> body.toBooleanStrictOrNull() ?: continue
                T_INT -> body.toIntOrNull() ?: continue
                T_LONG -> body.toLongOrNull() ?: continue
                T_FLOAT -> body.toFloatOrNull() ?: continue
                T_STRING -> unesc(body)
                T_SET_EMPTY -> emptySet<String>()
                T_SET -> body.split(ITEM).mapTo(LinkedHashSet()) { unesc(it) }
                else -> continue
            }
            out[key] = value
        }
        return out
    }

    /**
     * Làm an toàn một mẩu chuỗi trước khi nhúng vào dạng lưu.
     *
     * ⚠ `\\` phải đi **ĐẦU TIÊN** lúc mã hoá và **CUỐI CÙNG** lúc giải mã. Đổi thứ tự thì một giá trị chứa đúng hai
     * ký tự `\` + `n` sẽ đi ra rồi về thành một dấu xuống dòng thật ⇒ vỡ cấu trúc bản ghi. Đây là lỗi kinh điển của
     * mọi phép escape viết tay, nên có bài canh riêng cho đúng ca đó.
     */
    private fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace(FLD.toString(), "\\p")
        .replace(ITEM.toString(), "\\c")

    /** Nghịch đảo của [esc] — quét MỘT lượt trái→phải để không giải mã chồng (xem KDoc [esc]). */
    private fun unesc(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i == s.lastIndex) {
                sb.append(c)
                i++
                continue
            }
            when (val next = s[i + 1]) {
                '\\' -> sb.append('\\')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                'p' -> sb.append(FLD)
                'c' -> sb.append(ITEM)
                // Dãy thoát lạ ⇒ giữ NGUYÊN VĂN cả hai ký tự: dữ liệu sửa tay có thể có `\x` thật, nuốt dấu `\`
                // ở đó là lặng lẽ đổi giá trị của người dùng.
                else -> sb.append('\\').append(next)
            }
            i += 2
        }
        return sb.toString()
    }
}
