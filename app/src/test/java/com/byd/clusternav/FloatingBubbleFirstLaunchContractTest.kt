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

    // ══ Công tắc CHIẾU CỤM là cổng vào của dịch vụ này ═══════════════════════════════════════════════════════
    //
    // ⚠ Ba phép dưới đây CHUYỂN VỀ ĐÂY 2026-09-13 từ `CastEnableToggleContractTest` (bài đó bị xoá cùng màn
    // ClusterNav cũ — S3 · R4 — vì phần lớn của nó đọc source `MainActivity`). Ba phép này thì KHÔNG dính gì tới
    // màn cũ: chúng canh chính dịch vụ bong bóng, và chúng **quan trọng hơn** kể từ đợt này, vì
    // `KachiHomeWiring.ensureCastBubble` nay gọi `startForegroundService` ở `onResume` của **màn chính**
    // (tức mỗi lần bấm Home), chứ không còn là mỗi lần mở một màn cấu hình. Cổng "người dùng đã tắt Cast thì
    // đứng xuống" là thứ duy nhất chặn việc đó dựng bong bóng cho người đã tắt tính năng.

    @Test
    fun `ca hai loi vao vong doi deu dung xuong khi cong tac Cast TAT`() {
        val onCreate = functionBody("override fun onCreate()")
        val onStart = functionBody("override fun onStartCommand")
        assertTrue(onCreate.contains("if (!castEnabledNow()) { stopSelf()")) {
            "onCreate phải đứng xuống khi Cast tắt — nếu không, `ensureCastBubble` ở mỗi lần bấm Home sẽ dựng " +
                "bong bóng cho người đã tắt tính năng"
        }
        assertTrue(onStart.contains("if (!castEnabledNow()) { stopSelf(startId)")) {
            "onStartCommand phải đứng xuống khi Cast tắt (đây là lời gọi LẶP LẠI: mỗi `startForegroundService`)"
        }
    }

    @Test
    fun `cong tac doc MOI moi lan va hong thi coi nhu TAT`() {
        val helper = functionBody("private fun castEnabledNow()")
        assertTrue(helper.contains("prefs.castEnabled()")) { "phải đọc cờ bền, không cache trong RAM (CLAUDE.md §5)" }
        assertTrue(helper.contains("getOrDefault(false)")) {
            "đọc hỏng KHÔNG được im lặng BẬT chiếu — đây là tính năng opt-in"
        }
    }

    @Test
    fun `cong tac Cast mac dinh TAT trong prefs`() {
        val runtime = app("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt")
            .toFile().readText()
        assertTrue(runtime.contains("getBoolean(\"cast_enabled\", false)")) { "mặc định phải là TẮT (opt-in)" }
        assertTrue(runtime.contains("putBoolean(\"cast_enabled\"")) { "phải có đường ghi cờ bền" }
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
