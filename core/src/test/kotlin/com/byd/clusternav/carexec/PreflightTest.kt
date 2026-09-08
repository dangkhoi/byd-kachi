package com.byd.clusternav.carexec

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tiền kiểm để buổi trên xe chỉ còn việc chạy và đánh dấu.
 *
 * Mọi thứ có thể phát hiện off-car thì phải phát hiện ở đây. Thời gian trên xe bị giới hạn bởi những thứ
 * không ai điều khiển được — hôm 26/7 mất cả phiên vì ACC standby — nên "thiếu một cờ CLI" không được
 * phép là phát hiện tại chỗ.
 */
class PreflightTest {

    private val cliSource: String by lazy {
        listOf(
            "car-integration/src/main/kotlin/com/byd/clusternav/carexec/CarExecCli.kt",
            "src/main/kotlin/com/byd/clusternav/carexec/CarExecCli.kt",
            "../car-integration/src/main/kotlin/com/byd/clusternav/carexec/CarExecCli.kt",
        ).map(Paths::get).first(Files::exists).toFile().readText()
    }

    @Test
    fun `moi placeholder deu co co CLI de truyen gia tri`() {
        CarExecCatalog.placeholders.forEach { placeholder ->
            val name = placeholder.removeSurrounding("{", "}")
            val flag = when (name) {
                "pkg" -> "--pkg"; "comp" -> "--comp"; "display" -> "--display"
                "taskId" -> "--task"; "svc" -> "--svc"
                else -> "--$name"
            }
            assertTrue(cliSource.contains("\"$flag\""), "CLI thiếu cờ $flag cho placeholder $placeholder")
        }
    }

    @Test
    fun `moi kich ban chay duoc voi bo tham so mot phien binh thuong`() {
        // Bộ này đúng bằng những gì người vận hành biết khi ngồi trong xe: tên app, component, display,
        // taskId, service, bốn cạnh và dpi.
        val sessionValues = mapOf(
            CarExecCatalog.PLACEHOLDER_PACKAGE to "vn.vietmap.live",
            CarExecCatalog.PLACEHOLDER_COMPONENT to "vn.vietmap.live/.MainActivity",
            CarExecCatalog.PLACEHOLDER_DISPLAY to "1",
            CarExecCatalog.PLACEHOLDER_TASK to "26",
            CarExecCatalog.PLACEHOLDER_SERVICE to "AutoContainer",
            CarExecCatalog.PLACEHOLDER_LEFT to "0",
            CarExecCatalog.PLACEHOLDER_TOP to "0",
            CarExecCatalog.PLACEHOLDER_RIGHT to "1920",
            CarExecCatalog.PLACEHOLDER_BOTTOM to "720",
            CarExecCatalog.PLACEHOLDER_DPI to "320",
            // Hai tham số này chỉ biết SAU khi khám phá trên xe: tên khoá/action mà bên vẽ biển đang nghe,
            // và số km/h muốn ghi. Vẫn phải nằm trong bộ tham số của phiên, vì tới lúc chạy step ghi thì
            // người vận hành đã có chúng — và CLI phải có cờ để truyền vào.
            CarExecCatalog.PLACEHOLDER_KEY to "com.byd.example.SPEED_LIMIT",
            CarExecCatalog.PLACEHOLDER_VALUE to "60",
        )
        CarExecScenarios.all.forEach { scenario ->
            assertEquals(
                emptyList<String>(),
                CarExecCommands.missingPlaceholders(scenario, sessionValues),
                "${scenario.id} còn thiếu tham số mà một phiên bình thường không cấp được",
            )
        }
    }

    @Test
    fun `plan khong gui lenh nao`() {
        val text = CarExecCommands.planScenario("cast.cold-first", emptyMap())
        assertTrue(text.contains("KHÔNG chạy gì"), text.lines().firstOrNull())
        assertTrue(text.contains("CÒN THIẾU tham số"), "thiếu tham số thì phải nói ra")
    }

    @Test
    fun `so verdict co san header de lan ghi dau tien khong loi`() {
        val path = listOf(
            "docs/refactor-car-execution/verdicts.tsv",
            "../docs/refactor-car-execution/verdicts.tsv",
        ).map(Paths::get).firstOrNull(Files::exists)
        assertTrue(path != null, "file ledger phải tồn tại sẵn trong repo")
        assertTrue(path!!.toFile().readText().startsWith(VerdictEntry.HEADER), "header sai định dạng")
    }
}
