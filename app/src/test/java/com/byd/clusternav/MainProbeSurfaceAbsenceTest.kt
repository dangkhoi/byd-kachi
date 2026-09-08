package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainProbeSurfaceAbsenceTest {
    private val manifest by lazy { app("src/main/AndroidManifest.xml").toFile().readText() }
    private val receiver by lazy { app("src/main/java/com/byd/clusternav/RebindReceiver.kt").toFile().readText() }

    @Test
    fun `main receiver is private and exposes no test action`() {
        val declaration = Regex(
            """<receiver\s+android:name="\.RebindReceiver"\s+android:exported="([^"]+)"[\s\S]*?</receiver>"""
        ).find(manifest) ?: error("RebindReceiver declaration missing")
        assertTrue(declaration.groupValues[1] == "false", "main RebindReceiver must be exported=false")
        assertFalse(declaration.value.contains("com.byd.clusternav.TEST_"))
    }

    @Test
    fun `main source has no mass raw id or free form HAL handler`() {
        listOf(
            "com.byd.clusternav.TEST_",
            "TEST_ADAS_MASS",
            "TEST_HAL_WRITE",
            "getIntExtra(\"id\"",
            "getStringExtra(\"name\"",
            "featureIdsMatching(",
            "hal.setInt(",
        ).forEach { forbidden ->
            assertFalse(receiver.contains(forbidden), "main RebindReceiver retains forbidden probe token: $forbidden")
        }
    }

    @Test
    fun `boot package replacement and watchdog behavior remain declared and wired`() {
        listOf(
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "com.byd.clusternav.REBIND_WATCHDOG",
        ).forEach { action -> assertTrue(manifest.contains(action), "missing manifest action $action") }
        listOf(
            "Intent.ACTION_BOOT_COMPLETED",
            "Intent.ACTION_LOCKED_BOOT_COMPLETED",
            "Intent.ACTION_MY_PACKAGE_REPLACED",
            "rebind(context)",
            "scheduleWatchdog(context)",
            "castBootWork(context",
        ).forEach { token -> assertTrue(receiver.contains(token), "missing receiver behavior $token") }
    }

    @Test
    fun `main and release contain no T10 vehicle probe component or action`() {
        val forbidden = listOf(
            "HudSignProbeReceiver",
            "HudSignProbeActivity",
            "com.byd.clusternav.vehicleTest.T10_",
        )
        listOf(app("src/main"), app("src/release")).filter(Files::exists).forEach { sourceRoot ->
            Files.walk(sourceRoot).use { paths ->
                paths.filter(Files::isRegularFile)
                    .filter {
                        it.toString().endsWith(".kt") ||
                            it.toString().endsWith(".java") ||
                            it.toString().endsWith(".xml")
                    }
                    .forEach { file ->
                        val content = Files.readString(file)
                        forbidden.forEach { token ->
                            assertFalse(content.contains(token), "$file exposes forbidden T10 token $token")
                        }
                    }
            }
        }
    }

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }
}
