package com.byd.clusternav.system

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Hardening 2026-09-25 · audit F23 [P2] — handler ngoại lệ chưa bắt: ghi vết rồi **chuyển tiếp** (không nuốt,
 * không tự restart). Khoá: prev được gọi đúng 1 lần với đúng (thread, lỗi) — kể cả khi ghi vết ném; cài hai lần
 * không bọc chồng. Luật bật StrictMode ([StrictModeGate]) khoá cùng đây. Wiring vào `KachiApplication` canh ở
 * `:app` (`KachiApplicationHardeningWiringTest`).
 */
class KachiCrashHandlerTest {

    private var original: Thread.UncaughtExceptionHandler? = null

    @BeforeEach fun save() { original = Thread.getDefaultUncaughtExceptionHandler() }
    @AfterEach fun restore() { Thread.setDefaultUncaughtExceptionHandler(original) }

    private class Prev : Thread.UncaughtExceptionHandler {
        val calls = mutableListOf<Pair<Thread, Throwable>>()
        override fun uncaughtException(t: Thread, e: Throwable) { calls += t to e }
    }

    @Test fun `ghi vet roi chuyen tiep dung mot lan, ghi vet nem cung khong che loi goc`() {
        val prev = Prev()
        val recorded = mutableListOf<String>()
        val h = KachiCrashHandler(prev) { t, e -> recorded += "${t.name}:${e.message}"; error("sink chết") }
        val t = Thread("update-download")
        val e = IllegalStateException("rename adb keypair thất bại")
        h.uncaughtException(t, e)
        assertEquals(listOf("update-download:rename adb keypair thất bại"), recorded)
        assertEquals(1, prev.calls.size, "phải chuyển tiếp cho handler cũ đúng một lần")
        assertSame(t, prev.calls[0].first); assertSame(e, prev.calls[0].second)
    }

    @Test fun `prev null thi khong nem`() {
        KachiCrashHandler(null) { _, _ -> }.uncaughtException(Thread("x"), RuntimeException("y"))
    }

    @Test fun `install boc handler hien co va idempotent`() {
        val prev = Prev()
        Thread.setDefaultUncaughtExceptionHandler(prev)
        val first = KachiCrashHandler.install { _, _ -> }
        val second = KachiCrashHandler.install { _, _ -> }
        assertSame(first, second, "cài lần hai không được bọc chồng")
        assertSame(first, Thread.getDefaultUncaughtExceptionHandler())
        first.uncaughtException(Thread("z"), RuntimeException("w"))
        assertEquals(1, prev.calls.size, "handler cũ (Android: in stack + kill) vẫn phải nhận")
    }

    @Test fun `StrictMode chi bat o build type debug`() {
        assertTrue(StrictModeGate.enabled(isDebuggable = true, buildType = "debug"))
        assertFalse(StrictModeGate.enabled(isDebuggable = true, buildType = "vehicleTest"), "vehicleTest chạy trên xe thật — không bật")
        assertFalse(StrictModeGate.enabled(isDebuggable = false, buildType = "release"))
        assertFalse(StrictModeGate.enabled(isDebuggable = false, buildType = "debug"), "cờ debuggable là điều kiện cần")
    }
}
