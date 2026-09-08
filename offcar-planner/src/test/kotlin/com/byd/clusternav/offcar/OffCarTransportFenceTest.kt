package com.byd.clusternav.offcar

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OffCarTransportFenceTest {
    @Test
    fun `planner and contracts main sources contain no transport or command execution APIs`() {
        val roots = listOf(
            testProjectRoot().resolve("offcar-planner/src/main"),
            testProjectRoot().resolve("vehicle-contracts/src/main"),
        )
        val forbidden = listOf(
            Regex("\\bProcessBuilder\\b"),
            Regex("Runtime\\s*\\.\\s*getRuntime"),
            Regex("java\\.lang\\.Process"),
            Regex("java\\.net\\.|\\bSocket\\b"),
            Regex("(?i)\\bdadb\\b|\\badb\\b"),
            Regex("\\bCarExec\\b|com\\.byd\\.clusternav\\.carexec"),
            Regex("android\\."),
            Regex("--execute"),
            Regex("\\bDevice\\b"),
        )

        roots.forEach { root ->
            Files.walk(root).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.forEach { path ->
                    val source = Files.readString(path)
                    forbidden.forEach { pattern ->
                        assertFalse(pattern.containsMatchIn(source), "$pattern found in $path")
                    }
                }
            }
        }
    }

    @Test
    fun `compiled planner classes have no forbidden transport symbols or function callbacks`() {
        val classes = listOf(
            OffCarPlannerMain::class.java,
            CandidateScenarioGenerator::class.java,
            FakeVehicleTransport::class.java,
            CommandPlanRenderer::class.java,
        )
        val forbidden = listOf("java/lang/Process", "java/net/Socket", "dadb", "CarExec", "android/")
        classes.forEach { type ->
            val resource = "/${type.name.replace('.', '/')}.class"
            val bytes = type.getResourceAsStream(resource)!!.readBytes().decodeToString()
            forbidden.forEach { token -> assertFalse(bytes.contains(token), "$token found in ${type.name}") }
        }
        listOf(OffCarPlannerMain::class.java, FakeVehicleTransport::class.java).forEach { type ->
            type.declaredMethods.filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
                .flatMap { it.parameterTypes.toList() + it.returnType }.forEach { signatureType ->
                    assertFalse(
                        signatureType.name.startsWith("kotlin.jvm.functions.Function"),
                        "public callback type found in ${type.name}",
                    )
                }
        }
    }

    /**
     * E6b — cùng hiểm hoạ với `ExpansionDeterminismTest`: `OffCarPlannerMain.main` tự suy root bằng cách
     * LEO NGƯỢC từ thư mục làm việc của test worker (`<repo>/offcar-planner`) tới tổ tiên đầu tiên có
     * `settings.gradle.kts` = repo root, rồi ghi thẳng vào `docs/diagnostics/hud-sign-re/` — thư mục CHA
     * của bộ niêm phong (13 file chốt hash từng byte). Thứ duy nhất chặn lời gọi bên dưới ghi vào đó là
     * chính `require(args.isEmpty())` mà test này đang kiểm. Vì vậy phải khoá trên NGUỒN rằng chốt đó là
     * câu lệnh ĐẦU TIÊN, và khoá đó phải chạy TRƯỚC lời gọi: ai gỡ chốt thì test đỏ ở assert nguồn và
     * dừng lại trước khi `main` kịp ghi một byte nào.
     */
    @Test
    fun `entrypoint rejects every command-line option`() {
        val entrypointSource = testProjectRoot()
            .resolve("offcar-planner/src/main/kotlin/com/byd/clusternav/offcar/OffCarPlannerMain.kt")
        assertTrue(Files.isRegularFile(entrypointSource), entrypointSource.toString())
        val guard = "fun main(args: Array<String>) { require(args.isEmpty())"
        assertTrue(
            Files.readString(entrypointSource).replace(Regex("\\s+"), " ").contains(guard),
            "`OffCarPlannerMain.main` phải mở đầu bằng `$guard` — đây là thứ duy nhất giữ lời gọi bên dưới " +
                "khỏi ghi vào `docs/diagnostics/hud-sign-re/`. Xem KDoc E6b, ĐỪNG gỡ assert này.",
        )
        assertThrows(IllegalArgumentException::class.java) {
            OffCarPlannerMain.main(arrayOf("any-option"))
        }
        assertTrue(OffCarPlannerMain::class.java.declaredMethods.none { it.name.contains("execute", true) })
    }
}
