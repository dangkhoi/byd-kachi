package com.byd.clusternav.launcher.testbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ T-BRIDGE · BÀI CANH BỘ GHI JSON ═════════════════════════════════════════════════════════════════════════
 *
 * Đầu ra của cầu kiểm thử được đọc bằng `jq`, nên một dấu xuống dòng thô hay một dấu nháy chưa thoát không phải
 * lỗi thẩm mỹ: nó làm **cả lượt đo** thành vô dụng. Bài này khoá đúng ba ca đó.
 */
class TestBridgeJsonTest {

    @Test
    fun `thu tu truong giu nguyen de hai luot do so diff duoc`() {
        assertEquals(
            """{"ok":true,"cmd":"state","ms":12}""",
            TestBridgeJson.obj("ok" to true, "cmd" to "state", "ms" to 12),
        )
    }

    @Test
    fun `chuoi co dau nhay, gach cheo va xuong dong deu duoc thoat`() {
        val json = TestBridgeJson.obj("a" to "nói \"vâng\"", "b" to "c:\\x", "c" to "hai\ndòng\ttab")
        assertEquals("""{"a":"nói \"vâng\"","b":"c:\\x","c":"hai\ndòng\ttab"}""", json)
        // Chốt thật sự: không còn ký tự điều khiển THÔ nào lọt ra ngoài.
        assertFalse(json.any { it < ' ' }, "ký tự điều khiển thô trong JSON ⇒ jq báo lỗi cú pháp")
    }

    @Test
    fun `ky tu dieu khien la cung thanh dang u00xx`() {
        assertTrue(TestBridgeJson.obj("a" to "x\u0001y").contains("\\u0001"))
    }

    @Test
    fun `Raw nhung nguyen van, khong bi boc thanh chuoi`() {
        val inner = TestBridgeJson.obj("n" to 1)
        assertEquals("""{"slot":{"n":1}}""", TestBridgeJson.obj("slot" to TestBridgeJson.Raw(inner)))
    }

    @Test
    fun `null va mang rong ra dung dang JSON`() {
        assertEquals("""{"x":null}""", TestBridgeJson.obj("x" to null))
        assertEquals("[]", TestBridgeJson.arr(emptyList()))
        assertEquals("""["a","b"]""", TestBridgeJson.arr(listOf("a", "b")))
    }

    /** Số thực không biểu diễn được (NaN/∞) ⇒ `null`, không phải một chuỗi `NaN` làm hỏng cú pháp. */
    @Test
    fun `NaN va vo cuc ra null chu khong pha cu phap`() {
        assertEquals("""{"x":null,"y":null}""", TestBridgeJson.obj("x" to Double.NaN, "y" to Double.POSITIVE_INFINITY))
    }

    @Test
    fun `kieu la di qua toString roi boc chuoi, khong nem`() {
        assertEquals("""{"x":"A"}""", TestBridgeJson.obj("x" to 'A'))
    }
}
