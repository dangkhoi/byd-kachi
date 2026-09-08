package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloatingBubbleFirstLaunchContractTest {
    private val source by lazy {
        app("src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt").toFile().readText()
    }

    @Test
    fun `foreground starts before overlay gate in both lifecycle entries`() {
        val onCreate = functionBody("override fun onCreate()")
        val onStart = functionBody("override fun onStartCommand")
        assertTrue(onCreate.indexOf("startForegroundOnce()") in 0 until onCreate.indexOf("requestOverlayIfMissing()"))
        assertTrue(onStart.indexOf("startForegroundOnce()") in 0 until onStart.indexOf("requestOverlayIfMissing()"))
    }

    @Test
    fun `overlay settings request is one shot per service instance`() {
        assertTrue(source.contains("private var overlayRequested = false"))
        val gate = functionBody("private fun requestOverlayIfMissing()")
        assertTrue(gate.contains("if (!overlayRequested)"))
        assertTrue(gate.indexOf("overlayRequested = true") < gate.indexOf("CastBubbleControl.requestOverlay(this)"))
    }

    @Test
    fun `early stop teardown guards both late initialized collaborators`() {
        val destroy = functionBody("override fun onDestroy()")
        assertTrue(destroy.contains("if (::gestureHandler.isInitialized) gestureHandler.shutdown()"))
        assertTrue(destroy.contains("if (::renderer.isInitialized) renderer.clearViews()"))
    }

    private fun functionBody(signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "missing $signature" }
        val next = source.indexOf("\n    override fun ", start + signature.length).takeIf { it >= 0 }
            ?: source.indexOf("\n    private fun ", start + signature.length).takeIf { it >= 0 }
            ?: source.length
        return source.substring(start, next)
    }

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }
}
