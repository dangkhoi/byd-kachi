package com.byd.clusternav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** So sánh version — thuần, test off-device. Khoá logic quyết định "có bản mới". */
class UpdateCheckerTest {
    @Test fun `moi hon`() {
        assertTrue(UpdateChecker.cmp("0.57", "0.56") > 0)
        assertTrue(UpdateChecker.cmp("0.56", "0.9") > 0, "0.56 > 0.9 vì 56 > 9 (không phải so chuỗi)")
        assertTrue(UpdateChecker.cmp("1.0", "0.99") > 0)
        assertTrue(UpdateChecker.cmp("0.56.1", "0.56") > 0)
    }
    @Test fun `bang hoac cu hon`() {
        assertEquals(0, UpdateChecker.cmp("0.56", "0.56"))
        assertEquals(0, UpdateChecker.cmp("0.56", "0.56.0"))
        assertTrue(UpdateChecker.cmp("0.55", "0.56") < 0)
    }

    /** L2 — kênh riêng: repo byd-kachi, tên Kachi-<ver>-release.apk, vẫn nhận tên cũ; tên lạ thì bỏ qua. */
    @Test fun `ten tep phat hanh Kachi va ten cu deu doc ra phien ban`() {
        assertEquals("1.41", UpdateChecker.apkVersion("Kachi-1.41-release.apk"))
        assertEquals("1.38", UpdateChecker.apkVersion("ClusterNav-1.38-release.apk"))
        assertEquals(null, UpdateChecker.apkVersion("Kachi-1.41-debug.apk"))
        assertEquals(null, UpdateChecker.apkVersion("Kachi-1.41-abc123-release.apk"), "tên có lát cắt (collector T10) không phải bản OTA")
        assertEquals(null, UpdateChecker.apkVersion("README.md"))
    }

    @Test fun `kenh cap nhat la repo byd-kachi`() {
        val src = java.nio.file.Path.of(System.getProperty("user.dir")).let { d ->
            val f = d.resolve("src/main/java/com/byd/clusternav/UpdateChecker.kt")
            (if (java.nio.file.Files.exists(f)) f else d.resolve("app").resolve("src/main/java/com/byd/clusternav/UpdateChecker.kt")).toFile().readText()
        }
        assertTrue(src.contains("REPO = \"dangkhoi/byd-kachi\""), "OTA phải dò đúng repo của Kachi (remote origin), không phải byd-launcher")
    }
}
