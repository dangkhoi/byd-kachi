package com.byd.clusternav.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bảo đảm cấu trúc của :core, kiểm bằng test chứ không bằng niềm tin: nếu ai đó thêm Android hay dadb
 * vào module này thì phải fail ở đây, trước khi nó kịp lan vào máy trạng thái.
 */
class CoreIsolationTest {

    @Test
    fun `core classpath khong co Android va khong co dadb`() {
        listOf("android.content.Context", "android.os.Build", "dadb.Dadb").forEach { name ->
            assertFalse(available(name), "$name không được phép có trên classpath của :core")
        }
    }

    @Test
    fun `core la module JVM thuan`() {
        assertTrue(available("java.time.Instant"))
        assertTrue(CoreBoundary.LAYER == "core")
    }

    private fun available(name: String): Boolean =
        runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess
}
