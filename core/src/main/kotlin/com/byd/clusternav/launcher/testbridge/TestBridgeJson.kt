package com.byd.clusternav.launcher.testbridge

/**
 * ═══ T-BRIDGE · BỘ GHI JSON TỐI GIẢN (thuần Kotlin) ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` R4. Cầu kiểm thử trả kết quả bằng **JSON** vì đầu đọc là một script
 * `adb` chứ không phải mắt người: `jq '.ok'` phải chạy được, và hai lượt đo phải so được bằng `diff`.
 *
 * ## Vì sao KHÔNG dùng `org.json`
 * `org.json` là lớp của **nền tảng Android** — nghĩa là mọi câu hỏi *"JSON sinh ra có đúng khuôn không"* chỉ trả
 * lời được trên máy/emulator. [ĐO] `app/build.gradle.kts` đã phải kéo `org.json:json` vào riêng cho test vì
 * `android.jar` chỉ có bản ném `Stub!`. Bộ ghi ở đây là ~40 dòng thuần, chạy trong `:core:test` off-device, nên
 * khuôn JSON được khoá bằng bài kiểm chứ không bằng một lượt cắm dây.
 *
 * Nó **chỉ ghi**, không đọc: cầu kiểm thử không bao giờ phải phân tích JSON của ai (đầu vào là extra của Intent).
 * Viết thêm bộ đọc là viết mã không ai gọi — đúng hình dạng `CastShell.evictVd` mà CLAUDE.md §8 cấm.
 */
object TestBridgeJson {

    /**
     * Một mảnh JSON **đã dựng sẵn** — nhúng nguyên văn, không bọc dấu nháy.
     *
     * Cần vì [obj] nhận `Any?`: không có nhãn này thì một đối tượng con dựng trước sẽ bị coi là chuỗi và đi qua
     * [escape] ⇒ JSON lồng biến thành một chuỗi đầy `\"`, tức đầu đọc `jq` không vào được nữa.
     */
    @JvmInline
    value class Raw(val json: String)

    /** `{"k":v,…}` — thứ tự giữ NGUYÊN thứ tự truyền vào (đầu ra phải so `diff` được giữa hai lượt đo). */
    fun obj(fields: List<Pair<String, Any?>>): String =
        fields.joinToString(",", "{", "}") { (k, v) -> escape(k) + ":" + value(v) }

    /** Tiện cho chỗ gọi ngắn. */
    fun obj(vararg fields: Pair<String, Any?>): String = obj(fields.toList())

    /** `[v,…]`. */
    fun arr(items: List<Any?>): String = items.joinToString(",", "[", "]") { value(it) }

    /**
     * Một giá trị.
     *
     * Số/bool đi thẳng; [Raw] nhúng nguyên; **mọi thứ còn lại** qua `toString()` rồi bọc chuỗi. Mặc định "bọc
     * chuỗi" chứ không ném: dữ liệu ở đây đến từ trạng thái máy (tên gói, chuỗi dumpsys, câu trả lời của trợ lý),
     * và một lượt đo hỏng vì bộ ghi ném giữa chừng thì mất luôn phần đã đo được.
     */
    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Raw -> v.json
        is Boolean -> v.toString()
        is Int, is Long -> v.toString()
        is Double, is Float -> if (v.toString().let { it == "NaN" || it.contains("Infinity") }) "null" else v.toString()
        else -> escape(v.toString())
    }

    /**
     * Chuỗi JSON đúng RFC 8259: thoát `"` `\` và **mọi** ký tự điều khiển < 0x20.
     *
     * Ký tự điều khiển là ca thật, không phải lý thuyết: đầu ra của `dumpsys`/`logcat` có `\n` và `\t`, và một
     * dấu xuống dòng thô trong chuỗi JSON làm `jq` báo lỗi cú pháp — tức cả lượt đo thành vô dụng.
     */
    fun escape(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        s.forEach { c ->
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                // Locale.ROOT bắt buộc: `"%04x".format(x)` dùng locale MẶC ĐỊNH, ở locale chữ số không
                // phải Latin (vd ar-SA nu=arab) sẽ sinh ra chữ số Ả Rập ⇒ JSON hỏng, `jq` bỏ cả lượt đo.
                // Phần còn lại của repo (NavParse, ArrowClassifier) đã luôn nêu Locale tường minh.
                c < ' ' -> sb.append("\\u").append(String.format(java.util.Locale.ROOT, "%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
