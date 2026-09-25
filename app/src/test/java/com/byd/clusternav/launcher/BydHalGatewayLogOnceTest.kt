package com.byd.clusternav.launcher

import com.byd.clusternav.modules.hal.BydHal
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Hardening 2026-09-25 · audit F5 [P2] — [BydHal.callGetter]/[BydHal.tryGet] đưa NGUYÊN NHÂN của `null` ra seam
 * `onError` mà KHÔNG đổi giá trị trả về; `BydHalGateway` nối seam ấy vào `HalLogOnce` (luật log-once thuần ở
 * `:core`, canh bởi `HalLogOnceTest`) ở MỌI đường đọc/ghi. Gateway cần `Context` nên wiring canh bằng văn bản.
 */
class BydHalGatewayLogOnceTest {

    /** Device giả: getter ném / getter không có — hai nguyên nhân của `null` phải ra seam. */
    class DeadDevice {
        @Suppress("unused") fun getCurrentSpeed(): Int = throw IllegalStateException("binder dead")
        @Suppress("unused") fun getOk(): Int = 7
    }

    @Test fun `callGetter giu null nhung noi ly do qua onError`() {
        val dev = DeadDevice()
        val errors = mutableListOf<Throwable>()
        assertNull(BydHal.callGetter(dev, "getCurrentSpeed", onError = { errors += it }))
        assertEquals(1, errors.size)
        assertEquals("IllegalStateException: binder dead", BydHal.root(errors[0]), "root phải bóc InvocationTargetException")
        assertNull(BydHal.callGetter(dev, "getMissing", onError = { errors += it }))
        assertTrue(errors[1] is NoSuchMethodException, "ROM thiếu method cũng là một lý do phải nói ra")
        assertEquals("7", BydHal.callGetter(dev, "getOk", onError = { errors += it }))
        assertEquals(2, errors.size, "đường thành công không gọi onError")
        assertNull(BydHal.callGetter(dev, "getCurrentSpeed"), "mặc định (không seam) y như cũ: null, không ném")
    }

    @Test fun `tryGet khong co get 2-arg - null + NoSuchMethodException`() {
        val errors = mutableListOf<Throwable>()
        assertNull(BydHal.tryGet(DeadDevice(), 0x1F701010, onError = { errors += it }))
        assertTrue(errors.single() is NoSuchMethodException)
        assertNull(BydHal.readFeature(DeadDevice(), 0x1F701010), "mặc định y như cũ")
    }

    @Test fun `BydHalGateway noi log-once o moi duong`() {
        val src = source("app/src/main/java/com/byd/clusternav/launcher/BydHalGateway.kt")
        assertTrue(src.contains("BydHal.callGetter(it, method, arg) { t -> warnOnce(deviceFqn, method, t) }"), "getter: seam onError → warnOnce")
        assertTrue(src.contains("BydHal.readFeature(dev, id) { t -> warnOnce("), "featureGet: seam onError → warnOnce")
        assertTrue(src.contains("warnOnceRaw(deviceFqn, method, raw)"), "namedInt: chuỗi lỗi của callNamedInt cũng phải lộ")
        assertTrue(src.contains("warnOnceRaw(deviceFqn, label, raw)"), "featureSet: chuỗi lỗi của setInt cũng phải lộ")
        assertTrue(src.contains("HalLogOnce.forgetDevice(fqn)"), "resolve lại device ⇒ mở khoá log")
        assertTrue(src.contains("HalLogOnce.first(\"$" + "deviceFqn#\$method\")"), "khoá = fqn#method")
        assertTrue(src.contains("BydHal.root(t)"), "dòng W phải mang gốc lỗi, không phải lớp bọc reflection")
        val halPaths = src.substringAfter("class BydHalGateway").substringBefore("override fun settingGet")
        assertFalse(Regex("""\}\.getOrNull\(\)""").containsMatchIn(halPaths), "mọi đường HAL đọc/ghi: getOrNull() ⇒ getOrElse { warnOnce }")
    }

    /** Mã nguồn ĐÃ BỎ comment `//` — để một dòng bị comment-out không còn làm bài canh xanh giả (thử-làm-đỏ 2026-09-25). */
    private fun source(rel: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val text = listOf(cwd.resolve(rel), cwd.resolve("../$rel"), cwd.resolve(rel.removePrefix("app/"))).first { it.isFile }.readText()
        return text.lines().joinToString("\n") { it.substringBefore("//") }
    }
}
