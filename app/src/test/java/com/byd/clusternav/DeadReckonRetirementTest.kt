package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeadReckonRetirementTest {
    @Test
    fun `active product has exactly two tracks and no DR or mock provider wiring`() {
        val manifest = app("src/main/AndroidManifest.xml").toFile().readText()
        val home = app("src/main/java/com/byd/clusternav/MainActivity.kt").toFile().readText()
        val receiver = app("src/main/java/com/byd/clusternav/RebindReceiver.kt").toFile().readText()
        val prefs = app("src/main/java/com/byd/clusternav/Prefs.kt").toFile().readText()
        listOf(manifest, home, receiver, prefs).forEach { text ->
            assertFalse(text.contains("DeadReckon"))
            assertFalse(text.contains("modules.deadreckon"))
            assertFalse(text.contains("modules.mockloc"))
            assertFalse(text.contains("ACCESS_MOCK_LOCATION"))
        }
        assertFalse(prefs.contains("gpsAuto"))
        val layout = app("src/main/res/layout/activity_main.xml").toFile().readText()
        assertTrue(layout.contains("Navigation + HUD"))
        assertTrue(layout.contains("Cluster Cast"))
    }

    @Test
    fun `nguon da nghi phai bien khoi cay lam viec`() {
        // 2026-07-27, chủ xe quyết: bỏ hẳn Dead Reckon, "đang fail quá, sau này cần làm thì tìm giải pháp
        // mới sau". Bản trước bài kiểm này ĐÒI file phải còn, với lý do "đọc lại được để rollback" — nhưng
        // git đã giữ nguyên lịch sử, nên giữ thêm bản trong cây chỉ để lại 1.096 dòng không ai chạy, không
        // ai kiểm, mà vẫn phải đọc mỗi lần soát kiến trúc. Giờ đảo lại: chúng PHẢI biến mất.
        assertFalse(app("src/main/java/com/byd/clusternav/modules/deadreckon").toFile().exists())
        assertFalse(app("src/main/java/com/byd/clusternav/modules/mockloc").toFile().exists())
    }

    @Test
    fun `no active source outside the retired packages can reach DR or mock injection`() {
        // The five-file check above cannot catch a future caller in some other file, and mock
        // location changes what the head unit believes its position is. Lock the whole tree:
        // comments may mention the retired code, executable references may not.
        val retired = Regex("""(deadreckon|mockloc)""")
        val callSite = Regex(
            """(^|[^\w.])(import\s+com\.byd\.clusternav\.modules\.(deadreckon|mockloc)|""" +
                """MockLoc\s*\.\s*\w+\s*\(|DeadReckon(Module|Service|State)\s*[.(])"""
        )
        val offenders = mutableListOf<String>()
        val root = app("src/main/java/com/byd/clusternav")
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { !retired.containsMatchIn(it.parent.fileName.toString()) }
                .forEach { file ->
                    file.toFile().readText().lineSequence().forEachIndexed { index, line ->
                        val code = line.substringBefore("//").substringBefore("* ")
                        if (callSite.containsMatchIn(code)) offenders += "$file:${index + 1}"
                    }
                }
        }
        assertTrue(offenders.isEmpty(), "Dead Reckon / mock injection reachable from: $offenders")
    }

    @Test
    fun `the shipped manifest requests no location or mock permission`() {
        val manifest = app("src/main/AndroidManifest.xml").toFile().readText()
        listOf(
            "ACCESS_MOCK_LOCATION",
            "ACCESS_FINE_LOCATION",
            "ACCESS_COARSE_LOCATION",
            "ACCESS_BACKGROUND_LOCATION",
            "FOREGROUND_SERVICE_LOCATION",
        ).forEach { permission ->
            assertFalse(manifest.contains(permission), "manifest still requests $permission")
        }
    }

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }
}
