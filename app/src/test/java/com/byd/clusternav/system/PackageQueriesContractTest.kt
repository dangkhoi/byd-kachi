package com.byd.clusternav.system

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá **MỘT CỬA** cho `PackageManager.queryIntentActivities` (backlog D2b).
 *
 * Nợ gốc: 7 chỗ hỏi cùng một câu, mỗi chỗ tự gọi bản `(Intent, Int)` đã **deprecated ở API 33**. Ghi chú backlog
 * nói đúng cái nguy: *"đổi một chỗ là lệch bốn"* (thực tế là bảy). Bài này canh để nợ đó không mọc lại: rẽ nhánh
 * SDK sống ở đúng [PackageQueries], và không tệp nào khác được gọi thẳng API kia.
 */
class PackageQueriesContractTest {

    private val helperPath = "src/main/java/com/byd/clusternav/system/PackageQueries.kt"

    private val helper by lazy { SourceRoots.codeOf(helperPath) }

    @Test
    fun `re nhanh SDK 33 nam dung mot cho, dung ResolveInfoFlags of`() {
        // [ĐO] javap trên android.jar của compileSdk 37: `ResolveInfoFlags of(long)` ⇒ phải `.toLong()`, không
        // truyền Int. Context7 (/websites/developer_android · PackageManager.ResolveInfoFlags) xác nhận cùng chữ ký.
        assertTrue(helper.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU"),
            "rẽ nhánh phải theo SDK_INT (minSdk 29 ⇒ hai đường cùng tồn tại)")
        assertTrue(helper.contains("PackageManager.ResolveInfoFlags.of(flags.toLong())"),
            "đường mới phải dùng ResolveInfoFlags.of(long) — of() nhận long, không nhận Int")
        // ⚠ [ĐO] 2026-09-13: stub `(Intent, int)` của `compileSdk = 37` KHÔNG mang `@Deprecated` (bỏ `@Suppress` đi
        // biên dịch vẫn sạch), nên dòng này KHÔNG canh "chặn cảnh báo" — nó canh **dấu hiệu cho người đọc** rằng
        // đây là đường cũ, và phòng cho ngày Google gắn chú thích thật. Xem KDoc [PackageQueries].
        assertTrue(helper.contains("@Suppress(\"DEPRECATION\")"),
            "đường cũ phải được đánh dấu rõ là đường deprecated-theo-tài-liệu, không im lặng")
    }

    /**
     * ⚠ Quét **toàn bộ** cây source của 3 module, không phải một danh sách tệp chép tay: danh sách chép tay thì
     * chỗ gọi thứ tám viết ngày mai sẽ không ai bắt — đúng cái bệnh bài này đi chữa.
     */
    @Test
    fun `KHONG file nao ngoai helper duoc goi thang queryIntentActivities`() {
        val offenders = kotlinSources().filter { (_, code) -> code.contains("queryIntentActivities(") }.map { it.first }
        assertEquals(emptyList<String>(), offenders,
            "phải đi qua PackageQueries.queryActivities(pm, intent) — API (Intent, Int) deprecated từ API 33")
    }

    /** Helper viết ra mà không ai gọi là mã chết (CLAUDE.md §8). Đếm chỗ gọi thật, ngoài chính nó. */
    @Test
    fun `helper phai co du 7 cho goi that`() {
        val callers = kotlinSources().filter { (_, code) -> code.contains("PackageQueries.queryActivities(") }.map { it.first }
        assertEquals(7, callers.size, "7 chỗ gọi của D2b phải đều đi qua helper; thấy: $callers")
    }

    /**
     * Mọi tệp `.kt` của 3 module (trừ chính helper) → `tên tương đối` tới **mã đã bỏ chú thích**.
     *
     * Bỏ chú thích là bắt buộc: KDoc của helper lẫn của chỗ gọi đều NHẮC tên API đang bị cấm gọi, nên quét thô sẽ
     * báo sai — cùng lý do [SourceRoots.codeOf] tồn tại.
     */
    private fun kotlinSources(): List<Pair<String, String>> =
        SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") && it.name != "PackageQueries.kt" }
                .map { file ->
                    val code = file.readText()
                        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
                        .lines().joinToString("\n") { it.substringBefore("//") }
                    root.relativize(file.toPath()).toString() to code
                }
                .toList()
        }
}
