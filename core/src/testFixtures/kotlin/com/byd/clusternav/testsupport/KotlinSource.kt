package com.byd.clusternav.testsupport

/**
 * Tiện ích quét **source Kotlin dạng văn bản** cho các contract test (những chỗ chạm API Android nên không
 * chạy được trên android.jar stub của JVM — xem `NavSourceDwellWiringContractTest`).
 *
 * (Ghi chú cách đọc: bên dưới viết `OPEN` thay cho cặp ký tự mở comment khối và `CLOSE` thay cho cặp đóng.
 * Viết thẳng ký tự thật vào KDoc này sẽ tự đóng/mở chính nó — Kotlin cho comment khối LỒNG NHAU nên một dấu
 * lạc chỗ làm hỏng cả file. Đó cũng chính là họ lỗi mà lớp này sinh ra để xử lý.)
 *
 * ── VÌ SAO CÓ FILE NÀY (sửa 08-23 vòng 3) ────────────────────────────────────────────────────────────────
 * `stripComments` trước đây bị **chép ở 2 nơi** (`HudManeuverEncodingTest`, `NavSourceDwellWiringContractTest`)
 * và cả hai bản đều là regex ngây thơ: bỏ `OPEN … CLOSE` bằng `.*?` (DOT_MATCHES_ALL), rồi bỏ `//` tới hết
 * dòng. Hai lỗi, cùng hướng **FAIL-OPEN** — tức guard lặng lẽ THÔI gác, đúng loại lỗi CLAUDE.md §8/§10 cảnh
 * báo (test xanh không có nghĩa là nó còn đang kiểm cái gì). Đo thật bằng probe 08-23:
 * ```
 * input : val url = "https://x/y"; ScreenCaptureSignal.arrowPkg()
 * output: val url = "https:                              ← NUỐT luôn lời gọi BỊ CẤM
 * out.contains("ScreenCaptureSignal") = false            ← guard mù, test XANH oan
 * ```
 * Cùng cơ chế với chuỗi chứa `OPEN` trong string literal: nó mở một comment khối giả rồi ăn tới `CLOSE` thật
 * ở tận đâu đó phía dưới. Vì các test này tồn tại để CẤM một lời gọi (`ScreenCaptureSignal`,
 * `SourceArbiter.shouldFeed`, `NavViewIdSource.publish`…), xoá nhầm phần thi hành là **bỏ lọt đúng thứ cần bắt**.
 *
 * ⚠ Đây là code TEST-ONLY (testFixtures), không vào APK.
 */
object KotlinSource {

    /**
     * Bỏ comment dòng và comment khối, **giữ nguyên mọi string literal**.
     *
     * Quét MỘT LƯỢT theo trạng thái thay vì regex, vì "cái gì là comment" phụ thuộc ngữ cảnh mà regex không
     * mang được: `//` nằm trong `"https://…"` KHÔNG phải comment, còn ba dấu nháy trong một comment thì không
     * mở raw-string nào. Xử đúng 4 dạng của Kotlin:
     *  • raw string ba-nháy (dạng phổ biến nhất ở đây — mọi regex trong repo đều viết bằng nó);
     *  • string thường có escape `\"`;
     *  • char literal có escape;
     *  • comment khối **LỒNG NHAU** (Kotlin cho phép, Java thì không; regex `.*?` cắt ở dấu đóng ĐẦU TIÊN rồi
     *    coi phần đuôi là code).
     *
     * Thứ tự nhánh là thứ tự quét trái→phải, nên một comment mở trước sẽ nuốt mọi dấu nháy bên trong nó, và
     * một string mở trước sẽ nuốt mọi `//` bên trong nó — đúng ngữ nghĩa của lexer.
     */
    fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        val n = src.length
        while (i < n) {
            when {
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3)
                    val stop = if (end < 0) n else end + 3
                    out.append(src, i, stop)
                    i = stop
                }
                src[i] == '"' -> i = copyQuoted(src, i, '"', out)
                src[i] == '\'' -> i = copyQuoted(src, i, '\'', out)
                src.startsWith(LINE_OPEN, i) -> {
                    while (i < n && src[i] != '\n') i++          // giữ lại '\n' để số dòng không dồn cục
                }
                src.startsWith(BLOCK_OPEN, i) -> {
                    var depth = 0
                    while (i < n) {
                        if (src.startsWith(BLOCK_OPEN, i)) { depth++; i += 2 }
                        else if (src.startsWith(BLOCK_CLOSE, i)) { depth--; i += 2; if (depth == 0) break }
                        else i++
                    }
                }
                else -> { out.append(src[i]); i++ }
            }
        }
        return out.toString()
    }

    /** Chép nguyên một literal mở bằng [quote] (kể cả escape), trả về chỉ số NGAY SAU literal đó. */
    private fun copyQuoted(src: String, start: Int, quote: Char, out: StringBuilder): Int {
        val n = src.length
        var i = start
        out.append(src[i]); i++
        while (i < n) {
            val c = src[i]
            out.append(c); i++
            if (c == '\\' && i < n) { out.append(src[i]); i++ }   // escape: ký tự kế KHÔNG đóng literal
            else if (c == quote) break
            else if (c == '\n') break                             // literal thường không qua dòng ⇒ tự cắt
        }
        return i
    }

    // Ghép từ ký tự để KDoc/So sánh không phải chứa cặp mở-đóng thật (xem ghi chú đầu file).
    private const val LINE_OPEN = "//"
    private val BLOCK_OPEN = "/" + "*"
    private val BLOCK_CLOSE = "*" + "/"
}
