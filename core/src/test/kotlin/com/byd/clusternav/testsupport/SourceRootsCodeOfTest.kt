package com.byd.clusternav.testsupport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · TEST-CODEOF-STRIP — [SourceRoots.codeOf] (fixture DÙNG CHUNG của ~150 bài canh tĩnh) không được FAIL-OPEN ═══
 *
 * [ĐO phá thử 02/10, spec READY-AT-HOME §10 Pass 7] bản cũ cắt mỗi dòng ở `//` đầu tiên ⇒ `"https://x"` nuốt phần MÃ phía
 * sau trên cùng dòng; regex khối `.*?` ⇒ chuỗi MIME `audio/` + sao mở một comment khối giả nuốt tới dấu đóng thật ở dưới.
 * Hai ca đều làm `contains(...)`/`assertFalse(...)` của bài canh im lặng thôi gác. Mẫu dò `codeof/fail-open-probe.kt.txt`
 * dựng đủ các ca; thử ĐỎ: trả `codeOf` về bản cũ ⇒ hai bài đầu đỏ.
 */
class SourceRootsCodeOfTest {

    private val probe by lazy { SourceRoots.codeOf("src/test/resources/codeof/fail-open-probe.kt.txt") }

    @Test
    fun `chuoi chua dau gach doi khong nuot ma phia sau`() {
        assertTrue(probe.contains("HealPhase.KHOI_DONG"), "ca READY-AT-HOME: mã sau \"https://x\" phải còn:\n$probe")
        assertTrue(probe.contains("\"https://x\""), "string literal giữ nguyên")
    }

    @Test
    fun `chuoi MIME khong mo comment khoi gia`() {
        assertTrue(probe.contains("TokenAfterMime()"), "mã sau \"audio/…\" bị nuốt như một comment khối:\n$probe")
        assertTrue(probe.contains("RealCodeToken") && probe.contains("AfterNestedToken"))
    }

    @Test
    fun `chu thich that van bi bo, chuoi tho van la du lieu`() {
        listOf("CommentOnlyToken", "LineCommentToken", "NestedCommentToken").forEach {
            assertFalse(probe.contains(it), "$it nằm trong chú thích — codeOf phải bỏ")
        }
        assertTrue(probe.contains("GlslInsideString"), "`//` trong chuỗi thô ba-nháy là dữ liệu, không phải chú thích")
    }

    @Test
    fun `codeOf la DUNG bo quet chung, khong ban sao thu hai`() {
        val text = SourceRoots.text("src/test/resources/codeof/fail-open-probe.kt.txt")
        assertEquals(KotlinSource.stripComments(text), probe)
    }
}
