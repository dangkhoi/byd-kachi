package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Hardening 2026-09-25 · audit F23 [P2] + spec R4(b) — wiring (CLAUDE.md §8) của `KachiApplication.onCreate`:
 * `KachiCrashHandler` cài TRƯỚC cổng tiến trình (mọi tiến trình có vết), ghi vết đồng bộ ra thẻ, không tự
 * kill/restart; StrictMode đi qua `StrictModeGate` (luật thuần ở `:core`, canh bởi `KachiCrashHandlerTest`), chỉ log.
 */
class KachiApplicationHardeningWiringTest {

    private val src by lazy { source("app/src/main/java/com/byd/clusternav/KachiApplication.kt") }

    @Test fun `cai crash handler TRUOC cong tien trinh, ghi vet ra the, khong tu restart`() {
        val body = src.substringAfter("override fun onCreate()")
        val install = body.indexOf("KachiCrashHandler.install")
        val gate = body.indexOf("if (isBackgroundVoiceProcess()) return")
        assertTrue(install in 0 until gate, "install phải nằm TRƯỚC cổng để `:tts`/`:wake` cũng có vết")
        assertTrue(body.contains("KachiLog.writeCrash("), "phải ghi vết đồng bộ ra thẻ, không chỉ Log.e")
        assertFalse(src.contains("Process.killProcess") || src.contains("exitProcess"), "không tự kill/restart — quyết định của owner")
    }

    @Test fun `StrictMode qua StrictModeGate, chi penaltyLog, sau cong tien trinh`() {
        val body = src.substringAfter("override fun onCreate()")
        val gate = body.indexOf("if (isBackgroundVoiceProcess()) return")
        val strict = body.indexOf("StrictModeGate.enabled(BuildConfig.DEBUG, BuildConfig.BUILD_TYPE)")
        assertTrue(strict > gate, "wiring phải đi qua luật thuần và nằm SAU cổng (tiến trình voice nền không cần)")
        assertTrue(body.indexOf("AppContainer.get(this)") > strict, "bật TRƯỚC khi dựng đồ thị để vi phạm lúc dựng cũng lộ")
        assertFalse(src.contains(".penaltyDeath("), "R4(b): chỉ log")
        assertTrue(src.contains("detectLeakedClosableObjects()") && src.contains("detectLeakedRegistrationObjects()"), "VmPolicy theo spec")
    }

    /**
     * Mã nguồn ĐÃ BỎ chú thích — để một dòng bị comment-out không còn làm bài canh xanh giả (thử-làm-đỏ 2026-09-25).
     * 2.93 wave 2C · TEST-STRIP-COPIES: bộ quét có trạng thái dùng chung [KotlinSource.stripComments] thay bản cắt `//` tay
     * (bỏ cả chú thích khối; `//` nằm trong chuỗi không còn cắt mất phần mã phía sau trên cùng dòng).
     */
    private fun source(rel: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val text = listOf(cwd.resolve(rel), cwd.resolve("../$rel"), cwd.resolve(rel.removePrefix("app/"))).first { it.isFile }.readText()
        return KotlinSource.stripComments(text)
    }
}
