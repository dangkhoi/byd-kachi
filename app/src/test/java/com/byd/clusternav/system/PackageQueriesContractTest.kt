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

    /**
     * D3(a) — cùng cửa cho `resolveActivity` / `getPackageInfo` (cả hai có overload `*Flags` từ API 33).
     *
     * ⚠ 2026-09-13: danh sách ngoại lệ nay **RỖNG**. Ngoại lệ duy nhất từng có là `MainActivity.kt:83` — nó tồn
     * tại vì tệp đó bị wiring test ghim source nên không sửa được (spec IA v2 §2); màn cũ đã gỡ (S3 · R1) nên
     * lý do đó biến mất cùng nó. Giữ danh sách (và bài canh chống-rữa bên dưới) để lần sau ai cần một ngoại lệ
     * thì phải VIẾT RA lý do, thay vì thêm im lặng.
     *
     * ⚠ Khuôn bắt theo **hàm**, KHÔNG theo tên biến nhận: bản đầu chỉ khớp `packageManager.`/`pm.` nên một chỗ gọi
     * viết `val manager = ctx.packageManager; manager.getPackageInfo(…)` sẽ lọt — đúng kiểu "chỗ gọi thứ tám viết
     * ngày mai" mà bài này đi chặn. Hai lookbehind trừ ra hai thứ hợp lệ: chính helper (`PackageQueries.…`) và
     * `Intent.resolveActivity(pm)` — API của **Intent**, không phải PackageManager, không thuộc phạm vi
     * (`VietMapWidgetBridge.kt:170` đang dùng).
     */
    @Test
    fun `KHONG file nao ngoai helper goi thang resolveActivity hay getPackageInfo cua PackageManager`() {
        val direct = Regex("""(?<!PackageQueries)(?<![Ii]ntent)\.(resolveActivity|getPackageInfo)\(""")
        val allowed = emptySet<String>()
        val offenders = kotlinSources().filter { (name, code) -> direct.containsMatchIn(code) && name !in allowed }.map { it.first }
        assertEquals(emptyList<String>(), offenders, "phải đi qua PackageQueries.resolveActivity / packageInfo")
        assertTrue(
            allowed.all { name -> kotlinSources().any { (n, code) -> n == name && direct.containsMatchIn(code) } },
            "mỗi ngoại lệ phải còn THẬT trong cây — hết thì bỏ khỏi allowed cho khỏi rữa",
        )
    }

    @Test
    fun `resolveActivity va packageInfo cua helper deu co cho goi that`() {
        val src = kotlinSources()
        assertTrue(src.any { (_, code) -> code.contains("PackageQueries.resolveActivity(") }, "resolveActivity chưa ai gọi")
        assertTrue(src.count { (_, code) -> code.contains("PackageQueries.packageInfo(") } >= 5, "packageInfo phải thay ≥5 chỗ gọi cũ")
    }

    /** Helper viết ra mà không ai gọi là mã chết (CLAUDE.md §8). Đếm chỗ gọi thật, ngoài chính nó. */
    @Test
    fun `helper phai co du 6 cho goi that`() {
        // D2b đếm 7. Hai chỗ biến mất 2026-09-13 cùng màn ClusterNav cũ (`MainActivity`, `CastAutostart`) —
        // danh sách app "chiếu được" / "tự chiếu" nay chỉ còn một bản, trong cầu (S3 · R1/R3).
        // +1 từ 2026-09-14 (V1 · R6): `VoiceTextConsole` dựng bảng *nhãn app → tên gói* cho bộ phân tích câu lệnh.
        // Nó KHÔNG dùng lại `AppDrawerApps.load()` vì hàm đó nạp cả **icon** của từng app (`ri.loadIcon`) — công
        // việc nặng nhất của ngăn kéo — trong khi ở đây chỉ cần hai chuỗi. Đi qua đúng helper này là đủ để giữ
        // tính chất mà bài canh bảo vệ: một cửa duy nhất tới `PackageManager`.
        val callers = kotlinSources().filter { (_, code) -> code.contains("PackageQueries.queryActivities(") }.map { it.first }
        assertEquals(6, callers.size, "mọi chỗ gọi phải đi qua helper; thấy: $callers")
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
                    // '/' cứng: trên Windows `relativize` trả '\\' ⇒ allow-list so tên tệp sẽ trượt (CLAUDE.md §5 cross-platform).
                    root.relativize(file.toPath()).toString().replace('\\', '/') to code
                }
                .toList()
        }
}
