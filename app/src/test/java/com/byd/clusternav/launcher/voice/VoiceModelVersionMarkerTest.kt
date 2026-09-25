package com.byd.clusternav.launcher.voice

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Hardening 2026-09-25 · audit F6 [P2] — `.version` ghi hỏng im ⇒ `needsUpdate()` true mãi ⇒ tải lại 61 MB mỗi lần
 * bấm. Khoá: marker ghi + đọc lại; ghi không được ⇒ `false`; và `install` ghi nó TRONG staging TRƯỚC `renameTo`
 * (gói hoặc đủ tệp kể cả `.version`, hoặc không tồn tại).
 */
class VoiceModelVersionMarkerTest {

    @Test fun `ghi duoc thi doc lai dung so`(@TempDir dir: File) {
        assertTrue(VoiceModelStore.writeVersionMarker(dir, 3))
        assertEquals("3", File(dir, ".version").readText())
    }

    @Test fun `thu muc khong ghi duoc thi false, khong nem`(@TempDir dir: File) {
        val ro = File(dir, "ro").apply { mkdirs() }
        // Thư mục không tồn tại: writeText ném FileNotFoundException ⇒ false (ca "thẻ rút / staging đã bay").
        assertFalse(VoiceModelStore.writeVersionMarker(File(dir, "missing/sub"), 3))
        if (ro.setWritable(false, false) && !ro.canWrite()) {
            try {
                assertFalse(VoiceModelStore.writeVersionMarker(ro, 3), "thư mục chỉ-đọc ⇒ false")
            } finally { ro.setWritable(true, false) }
        }
    }

    @Test fun `install ghi marker trong staging TRUOC renameTo va huy goi khi ghi hong`() {
        val cwd = File(System.getProperty("user.dir"))
        val rel = "app/src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt"
        // Bỏ comment `//` để một dòng bị comment-out không làm bài canh xanh giả (thử-làm-đỏ 2026-09-25).
        val src = listOf(cwd.resolve(rel), cwd.resolve("../$rel"), cwd.resolve(rel.removePrefix("app/"))).first { it.isFile }
            .readText().lines().joinToString("\n") { it.substringBefore("//") }
        val body = src.substringAfter("onStep(Step.Extracting)").substringBefore("staging.deleteRecursively()")
        val marker = body.indexOf("writeVersionMarker(out, pack.version)")
        val rename = body.indexOf("out.renameTo(dest)")
        assertTrue(marker in 0 until rename, "marker phải ghi vào `out` (staging) TRƯỚC renameTo")
        assertTrue(body.substringAfter("writeVersionMarker(out, pack.version)").substringBefore("val dest").contains("out.deleteRecursively()"), "ghi hỏng ⇒ huỷ gói dựng dở")
        assertTrue(body.substringAfter("writeVersionMarker(out, pack.version)").substringBefore("val dest").contains("Step.Failed"), "…và nói ra, không im")
        assertFalse(src.contains("File(dest, VERSION_FILE).writeText"), "không còn ghi `.version` SAU rename (đường im lặng cũ)")
    }
}
