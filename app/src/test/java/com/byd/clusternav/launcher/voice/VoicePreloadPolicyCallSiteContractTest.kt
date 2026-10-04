package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ Soát vòng 4 [P3] — CLAUDE.md §8: mọi hàm công khai của `VoicePreloadPolicy` phải có chỗ gọi PRODUCTION ═══
 *
 * `VoicePreloadPolicy.reason()` sống sót qua QA 2.87 chỉ nhờ test gọi: KDoc nói nó là câu log, nhưng log của
 * `VoiceRecognizer.preload` gọi thẳng `PreloadSkip.text(Lang.VI)` — hai đường tới cùng một câu, sửa một đường thì log xe
 * không đổi. Bài này quét source production đã BỎ CHÚ THÍCH (`app` + `core` + `car-integration`) và đòi mỗi `fun` của object
 * có ÍT NHẤT một lời gọi: `VoicePreloadPolicy.<tên>(` ở tệp khác, hoặc `<tên>(` (không phải dòng khai báo) trong chính tệp
 * policy — hàm nội bộ như `headroomBytes`. Tên trần ở tệp khác KHÔNG tính: `KeySourceLog.reason` / `VoiceRiskTable.reason`
 * trùng tên sẽ che mất đúng ca cần bắt.
 */
class VoicePreloadPolicyCallSiteContractTest {

    private val file = "VoicePreloadPolicy.kt"

    private val policy by lazy {
        KotlinSource.stripComments(SourceRoots.text("src/main/java/com/byd/clusternav/launcher/voice/$file"))
    }

    private val others by lazy {
        SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.extension == "kt" || it.extension == "java" }.toList() }
        }.filter { it.fileName.toString() != file }.joinToString("\n") { KotlinSource.stripComments(it.readText()) }
    }

    @Test
    fun `moi ham cua VoicePreloadPolicy co cho goi production`() {
        val start = policy.indexOf("object VoicePreloadPolicy {")
        assertTrue(start >= 0, "không thấy object VoicePreloadPolicy")
        val end = policy.indexOf("\n}\n", start)
        val body = policy.substring(start, if (end < 0) policy.length else end)
        // Soát vòng 5 [P3]: bản vòng 4 chỉ đọc `\n    fun tên(` ⇒ `internal fun reason(`, `@JvmStatic fun reason(`, `fun <T> x(`
        // lọt lưới mà sàn `>= 5` vẫn xanh. Nay đọc mọi dạng khai báo, và SỐ khai báo đọc được phải bằng SỐ từ khoá `fun` trong
        // object — dạng nào regex chưa hiểu thì bài ĐỎ (không lặng lẽ bỏ qua).
        val names = DECL.findAll(body).map { it.groupValues[1] }.toList()
        assertTrue(names.size >= 6, "đọc thiếu hàm của policy: $names")
        assertTrue(names.size == Regex("""\bfun\b""").findAll(body).count(),
            "có khai báo `fun` regex không đọc được trong VoicePreloadPolicy — mở rộng DECL: $names")
        names.forEach { name ->
            val outside = Regex("""\bVoicePreloadPolicy\.$name\(""").findAll(others).count()
            val inside = Regex("""\b$name\(""").findAll(policy).count() - DECL.findAll(policy).count { it.groupValues[1] == name }
            assertTrue(outside + inside >= 1, "`VoicePreloadPolicy.$name` không có chỗ gọi production (CLAUDE.md §8) — gỡ hàm hoặc nối dây")
        }
    }

    @Test
    fun `regex khai bao doc moi dang - chu thich, bo ngu, tham so kieu, ham mo rong`() {
        val src = """
            |object X {
            |    fun a(x: Int) = x
            |    internal fun b() {}
            |    @JvmStatic fun c() {}
            |    @Suppress("UNUSED")
            |    private inline fun <T> d(t: T) = t
            |    fun String.e(): Int = length
            |    suspend fun f () {}
            |}
        """.trimMargin()
        assertTrue(DECL.findAll(src).map { it.groupValues[1] }.toList() == listOf("a", "b", "c", "d", "e", "f"))
    }

    private companion object {
        /** Một khai báo hàm: chú thích (cả dòng trước), bổ ngữ, `<T>`, kiểu nhận (`String.`); nhóm 1 = tên. */
        val DECL = Regex(
            """(?m)^[ \t]*(?:@[\w.]+(?:\([^)]*\))?\s+)*""" +
                """(?:(?:public|internal|private|protected|inline|suspend|operator|infix|tailrec|external|override|open|final)\s+)*""" +
                """fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)\s*[(<]""",
        )
    }
}
