package com.byd.clusternav.testsupport

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Giải đường dẫn source cho các test quét source, không phụ thuộc module nào đang chạy.
 *
 * Đặt ở namespace trung lập, không nằm trong feature nào. Trước 2026-07-27 nó sống trong package của
 * Cluster Cast, nên test của Navigation phải import một lớp mang tên Cast chỉ để đọc file — chính là kiểu
 * "gọi ngang feature" mà quy tắc Q3 cấm, và trớ trêu là nó xảy ra ngay trong test đang canh quy tắc đó.
 *
 * Từ 2026-07-27 code Cast nằm ở hai module (:core thuần và :app cho phần biết-thiết-bị), và Gradle đặt
 * working directory theo module. Trước đó mọi test giả định gốc là app/, nên vừa dời file là chúng chết
 * hàng loạt mà không phải vì logic sai. Helper này thử mọi gốc hợp lý, kể cả chuyển java/ ↔ kotlin/.
 */
object SourceRoots {

    fun path(relative: String): Path {
        val kotlin = relative.replace("main/java/", "main/kotlin/").replace("test/java/", "test/kotlin/")
        val candidates = listOf(
            "app/$relative", "../app/$relative", relative,
            "core/$kotlin", "../core/$kotlin",
            "car-integration/$kotlin", "../car-integration/$kotlin",
            "app/$kotlin", "../app/$kotlin",
        ).map(Paths::get)
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("không tìm thấy $relative; đã thử: ${candidates.joinToString()}")
    }

    fun text(relative: String): String = path(relative).toFile().readText()

    fun exists(relative: String): Boolean =
        runCatching { path(relative) }.isSuccess

    /** Gốc chứa source Kotlin của cả hai module, dùng cho test cần quét toàn bộ cây. */
    fun moduleSourceRoots(): List<Path> = listOf(
        "app/src/main/java", "../app/src/main/java",
        "core/src/main/kotlin", "../core/src/main/kotlin",
        "car-integration/src/main/kotlin", "../car-integration/src/main/kotlin",
    ).map(Paths::get).filter(Files::exists)

    /**
     * Nội dung tệp đã **bỏ chú thích** — dùng cho test canh CODE (không canh văn xuôi).
     *
     * Hai lý do: (a) câu giải thích thường nhắc chính tên hàm đang bị cấm gọi ⇒ quét thô sẽ báo sai; (b) chặn kiểu
     * "đạt test" bằng cách viết token vào chú thích thay vì nối dây thật.
     */
    fun codeOf(relative: String): String = text(relative)
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }

    /**
     * Cắt đúng THÂN của một hàm/khối theo **đếm ngoặc**, và **NỔ nếu mốc không tồn tại**.
     *
     * ## ⚠ Vì sao helper này phải là hạ tầng dùng chung
     * Lượt soát 2026-09-11 [ĐO] ít nhất 5 bài canh đang cắt vùng bằng `substringAfter(...).substringBefore(...)` với
     * mốc kết **không nằm sau** mốc đầu (ví dụ mốc `init {` ở dòng 74 trong khi hàm cần soi ở dòng 167). Kotlin trả
     * NGUYÊN phần còn lại ⇒ bài đó quét tới hết tệp ⇒ assert `contains` gần như **không thể đỏ**: nó chỉ còn hỏi
     * "chuỗi này có ở đâu đó trong tệp không". Ba bài trong số đó đã chứng minh là không bắt được mutation thật.
     *
     * Hàm này xử lý cả hàm một-biểu-thức (`fun f() = X(...)`) — cái bẫy làm hai bài khác cắt sang thân hàm KẾ TIẾP.
     */
    fun body(src: String, signature: String): String {
        val at = src.indexOf(signature)
        require(at >= 0) { "không tìm thấy '$signature' — test đang quét vùng KHÔNG tồn tại (quét tràn = test giả)" }

        // (a) Mốc TỰ KẾT bằng '{' (vd `runOnUiThread {`, `if (x) {`) ⇒ thân chính là khối đó.
        val sig = signature.trimEnd()
        if (sig.endsWith("{")) return braces(src, src.indexOf('{', at + sig.length - 1), signature)

        // (b) Vượt DANH SÁCH THAM SỐ: `fun f(a: Int, b: Boolean = false)` có '=' ngay trong tham số. Bỏ qua bước này
        // là tưởng mọi hàm có tham số mặc định đều là hàm một-biểu-thức — [ĐO] 12 bài canh đỏ oan vì đúng lỗi đó.
        var cur = at + signature.length
        val paren = src.indexOf('(', at)
        val firstBrace = src.indexOf('{', at)
        if (paren >= at && (firstBrace < 0 || paren < firstBrace)) {
            var d = 0
            var i = paren
            while (i < src.length) {
                if (src[i] == '(') d++
                if (src[i] == ')' && --d == 0) break
                i++
            }
            cur = i + 1
        }

        // (c) '{' (thân khối) hay '=' ĐỨNG RIÊNG (thân biểu thức)? `!=`/`==`/`>=` KHÔNG phải gán — bỏ qua bước này
        // thì một dòng `if (gen != x)` trong thân hàm bị đọc thành "hàm một-biểu-thức".
        val b = src.indexOf('{', cur)
        val e = Regex("(?<![=!<>+\\-*/%])=(?!=)").find(src, cur)?.range?.first ?: -1
        if (b >= 0 && (e < 0 || b < e)) return braces(src, b, signature)
        require(e >= 0) { "'$signature' không có thân đọc được" }

        // Thân BIỂU THỨC: từ '=' tới khai báo thành viên kế tiếp (đủ cho quy ước thụt lề 4 khoảng của dự án).
        val rest = src.substring(e)
        val stop = Regex("\\n\\s{0,4}(fun |private |internal |public |override |val |var |companion |})")
            .find(rest, 1)?.range?.first ?: rest.length
        return rest.substring(0, stop)
    }

    private fun braces(src: String, open: Int, signature: String): String {
        require(open >= 0) { "'$signature' không có khối '{' để đọc" }
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return src.substring(open, i + 1)
            }
        }
        error("thân '$signature' không đóng ngoặc")
    }
}
