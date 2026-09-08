package com.byd.clusternav.offcar

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal fun testProjectRoot(): Path =
    Path.of(System.getProperty("clusternav.root")).toAbsolutePath().normalize()

class DependencyBoundaryTest {
    @Test
    fun `planner project dependency is only vehicle contracts`() {
        val root = testProjectRoot()
        val plannerBuild = Files.readString(root.resolve("offcar-planner/build.gradle.kts"))
        val projectDependencies = Regex("project\\(\"([^\"]+)\"\\)")
            .findAll(plannerBuild).map { it.groupValues[1] }.toSet()

        assertEquals(setOf(":vehicle-contracts"), projectDependencies)
        assertTrue(Files.readString(root.resolve("core/build.gradle.kts")).contains("api(project(\":vehicle-contracts\"))"))
        assertFalse(Files.readString(root.resolve("core/build.gradle.kts")).contains(":offcar-planner"))
        assertTrue(Files.readString(root.resolve("settings.gradle.kts")).contains("include(\":offcar-planner\")"))
        listOf("app/build.gradle.kts", "core/build.gradle.kts", "car-integration/build.gradle.kts").forEach { path ->
            assertFalse(Files.readString(root.resolve(path)).contains("project(\":offcar-planner\")"), path)
        }
    }

    @Test
    fun `planner test classpath excludes runtime core integration and transport libraries`() {
        val classpath = System.getProperty("java.class.path").lowercase()
        listOf("/core/build/", "car-integration", "dadb", "android.jar").forEach { token ->
            assertFalse(classpath.contains(token), "planner classpath contains $token")
        }
    }

    @Test
    fun `runtime consumers do not reference planner packages IDs or report JSON`() {
        val root = testProjectRoot()
        val consumerRoots = listOf(
            root.resolve("app/src/main"),
            root.resolve("car-integration/src/main"),
            root.resolve("core/src/main"),
        )
        val forbidden = Regex(
            "com\\.byd\\.clusternav\\.offcar|OFFCAR-|m[1-4]-(nav-hud|hud-road|cluster-sign|hud-sign)-plan\\.json|candidate-report\\.html|traceability\\.json",
        )
        val textExtensions = setOf("kt", "java", "xml", "kts", "json", "properties")
        consumerRoots.forEach { sourceRoot ->
            Files.walk(sourceRoot).use { paths ->
                paths.filter {
                    Files.isRegularFile(it) && it.fileName.toString().substringAfterLast('.', "") in textExtensions
                }.forEach { path ->
                    assertFalse(forbidden.containsMatchIn(Files.readString(path)), "planner reference found in $path")
                }
            }
        }
    }
}
