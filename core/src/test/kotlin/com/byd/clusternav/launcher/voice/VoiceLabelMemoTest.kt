package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * VOICE-SESSION-READY-CACHE — khoá [ĐO xe 15/10]: bảng nhãn app dựng lại MỖI phiên (2,2 s + cảnh báo trùng lặp lại).
 * Red-proof: cho [VoiceLabelMemo.of] luôn gọi `build` ⇒ bài 1 đỏ; bỏ so chữ ký ⇒ bài 2 đỏ.
 */
class VoiceLabelMemoTest {
    @Test fun `cung chu ky thi KHONG dung lai bang`() {
        val m = VoiceLabelMemo<String>()
        var builds = 0
        val sig = listOf("vn.vietmap.live|.Main|/data/app/vm-1/base.apk|2131|vi")
        assertEquals("A" to true, m.of(sig) { builds++; "A" })
        repeat(5) { assertEquals("A" to false, m.of(sig.toList()) { builds++; "B" }) }
        assertEquals(1, builds, "6 phiên liền, không app nào đổi ⇒ dựng đúng 1 lần")
    }

    @Test fun `cai go cap nhat app hoac doi ngon ngu thi dung lai`() {
        val m = VoiceLabelMemo<Int>()
        var builds = 0
        val base = listOf("a|.A|/data/app/a-1/base.apk|1|vi", "b|.B|/data/app/b-1/base.apk|2|vi")
        m.of(base) { ++builds }
        for (sig in listOf(
            base + "c|.C|/data/app/c-1/base.apk|3|vi",                       // cài thêm
            base.take(1),                                                      // gỡ
            listOf(base[0], "b|.B|/data/app/b-2/base.apk|2|vi"),               // cập nhật (đường APK đổi)
            base.map { it.replace("|vi", "|en") },                             // đổi ngôn ngữ
        )) {
            val (_, rebuilt) = m.of(sig) { ++builds }
            assertTrue(rebuilt, "chữ ký $sig phải dựng lại")
        }
        assertEquals(5, builds)
        m.clear(); assertFalse(m.of(base) { ++builds }.second.not(), "clear ⇒ dựng lại")
    }
}
