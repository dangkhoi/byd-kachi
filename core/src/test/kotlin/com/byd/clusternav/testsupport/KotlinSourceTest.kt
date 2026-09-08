package com.byd.clusternav.testsupport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test cho chính công cụ mà các contract test dựa vào để gác (CLAUDE.md §10).
 *
 * VÌ SAO đáng có test riêng: [KotlinSource.stripComments] là bước tiền xử lý của mọi guard kiểu "lời gọi X
 * KHÔNG được xuất hiện trong phần thi hành". Nếu nó xoá nhầm phần thi hành thì guard **fail-open** — test vẫn
 * xanh trong khi lời gọi bị cấm đang nằm đó. Bản regex cũ (2 bản chép, cả hai giống nhau) mắc đúng lỗi này;
 * ba test đầu dưới đây là các ca ĐÃ ĐO khiến bản cũ ĐỎ.
 */
class KotlinSourceTest {

    /**
     * KHOÁ lỗi FAIL-OPEN đã đo: `//` bên trong một string literal KHÔNG phải comment.
     * Bản regex cũ cho ra `val url = "https:` — nuốt luôn lời gọi bị cấm đứng sau trên cùng dòng.
     */
    @Test
    fun `dau gach doi trong string literal KHONG phai comment`() {
        val src = """val url = "https://x/y"; ScreenCaptureSignal.arrowPkg()"""
        val out = KotlinSource.stripComments(src)
        assertTrue(out.contains("ScreenCaptureSignal.arrowPkg()"), "guard phải còn thấy lời gọi bị cấm")
        assertTrue(out.contains("https://x/y"), "string literal giữ nguyên")
    }

    /** KHOÁ: dấu MỞ comment khối nằm trong string literal không được mở một khối giả rồi ăn tới dấu ĐÓNG thật phía dưới. */
    @Test
    fun `mo comment khoi trong string literal khong duoc an phan thi hanh`() {
        val src = "val glob = \"/*\"\nSourceArbiter.shouldFeed(a, b, c)\n/* comment THẬT */\nval x = 1"
        val out = KotlinSource.stripComments(src)
        assertTrue(out.contains("SourceArbiter.shouldFeed(a, b, c)"), "phần thi hành phải còn")
        assertTrue(out.contains("val x = 1"), "code sau comment thật phải còn")
        assertFalse(out.contains("comment THẬT"), "comment thật vẫn phải bị bỏ")
    }

    /** KHOÁ: Kotlin cho phép comment khối LỒNG NHAU; regex `.*?` cắt ở dấu đóng ĐẦU TIÊN rồi coi đuôi là code. */
    @Test
    fun `comment khoi LONG NHAU bi bo tron ven`() {
        val out = KotlinSource.stripComments("a /* ngoài /* trong */ vẫn trong */ b")
        assertEquals("a  b", out)
    }

    /** Raw string ba-nháy là dạng viết regex của cả repo — nội dung bên trong tuyệt đối không bị đụng. */
    @Test
    fun `raw string giu nguyen ke ca khi chua dau comment`() {
        val src = "val re = Regex(\"\"\"//[^\\n]*\"\"\")\nNavViewIdSource.publish(x)"
        val out = KotlinSource.stripComments(src)
        assertTrue(out.contains("NavViewIdSource.publish(x)"), "lời gọi sau raw string phải còn")
        assertTrue(out.contains("//[^\\n]*"), "nội dung raw string giữ nguyên")
    }

    /** Hành vi CƠ BẢN không đổi so với bản regex cũ: comment thật vẫn bị bỏ, code vẫn còn. */
    @Test
    fun `van bo comment dong va comment khoi thuong`() {
        val src = "val a = 1 // giải thích ScreenCaptureSignal\n/* khối\n nhiều dòng NavViewIdSource */\nval b = 2"
        val out = KotlinSource.stripComments(src)
        assertFalse(out.contains("ScreenCaptureSignal"), "tên trong comment dòng không được lọt")
        assertFalse(out.contains("NavViewIdSource"), "tên trong comment khối không được lọt")
        assertTrue(out.contains("val a = 1"))
        assertTrue(out.contains("val b = 2"))
    }

    /** Escape `\"` không đóng string — nếu đóng sớm thì phần sau bị coi là code/comment sai. */
    @Test
    fun `escape trong string khong dong literal som`() {
        val out = KotlinSource.stripComments("""val s = "a\"//b"; NavApps.ALL""")
        assertTrue(out.contains("NavApps.ALL"), "code sau string có escape phải còn")
        assertTrue(out.contains("""a\"//b"""), "nội dung string giữ nguyên")
    }

    /** Degrade-safe: chuỗi rỗng / comment không đóng không được ném. */
    @Test
    fun `khong nem voi input khuyet`() {
        assertEquals("", KotlinSource.stripComments(""))
        assertEquals("a ", KotlinSource.stripComments("a /* chưa đóng"))
        assertEquals("a ", KotlinSource.stripComments("a // chưa xuống dòng"))
    }
}
