package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Vạch chuẩn khoảng cách. Owner 2026-09-28 trên xe: *"camera tạo cảm giác xe mình rất xa xe bên cạnh, trong khi
 * cách tầm 30cm thôi, nên khó phán đoán"* — sau khi ba đường làm-hình-thật-hơn đều đóng (xem KDoc [CameraGuide]).
 */
class CameraGuideTest {

    private val G = CameraGuide

    @Test
    fun `mac dinh la TAT`() {
        assertEquals(G.OFF, G.defaultValue(), "phần lớn người dùng chưa canh bao giờ; vẽ sẵn một vạch chưa canh " +
            "là tệ hơn không vẽ, vì người lái sẽ tin nó")
        assertNull(G.positionFor(G.defaultValue()), "tắt ⇒ không vẽ gì")
    }

    @Test
    fun `chin nac nam gon trong khung, khong nac nao dinh mep`() {
        (G.STEP_MIN..G.STEP_MAX).forEach { s ->
            val p = G.positionOf(s)
            assertTrue(p > 0f && p < 1f, "nấc $s ra $p — dính mép thì không phân biệt được với viền overlay")
        }
        assertEquals(0.1f, G.positionOf(1), 1e-6f)
        assertEquals(0.9f, G.positionOf(9), 1e-6f)
    }

    @Test
    fun `nac tang thi vach di xuong, don dieu`() {
        val ps = (G.STEP_MIN..G.STEP_MAX).map { G.positionOf(it) }
        assertEquals(ps.sorted(), ps, "nấc lớn hơn phải nằm thấp hơn — nếu không người lái canh kiểu gì cũng loạn")
        assertEquals(ps.distinct(), ps, "hai nấc không được ra cùng một chỗ")
    }

    @Test
    fun `gia tri la thi IM, khong doan`() {
        listOf(null, "", "   ", "rác", "0", "10", "-1", "99").forEach {
            assertNull(G.positionFor(it), "giá trị '$it' lạ ⇒ KHÔNG vẽ; vẽ ở chỗ ngẫu nhiên là nói dối người lái")
        }
        assertFalse(G.isValue("0"), "0 ngoài dải")
        assertFalse(G.isValue("10"), "10 ngoài dải")
    }

    @Test
    fun `doc gia tri chiu hoa thuong va khoang trang`() {
        assertTrue(G.isValue("off"))
        assertTrue(G.isValue(" OFF "))
        assertTrue(G.isValue("5"))
        assertNull(G.stepOf(" off "), "tắt ⇒ không có nấc")
        assertEquals(5, G.stepOf(" 5 "))
    }

    @Test
    fun `tap chon phu dung mot lan TAT va chin nac`() {
        assertEquals(10, G.VALUES.size)
        assertEquals(G.OFF, G.VALUES.first(), "chip TẮT đứng đầu — đường thoát phải ở chỗ dễ thấy nhất")
        assertEquals((1..9).map { it.toString() }, G.VALUES.drop(1))
        G.VALUES.forEach { assertTrue(G.isValue(it), "$it phải tự nhận chính mình") }
    }

    @Test
    fun `nac ngoai dai bi KEP chu khong nem`() {
        assertEquals(G.positionOf(G.STEP_MIN), G.positionOf(-5), 1e-6f)
        assertEquals(G.positionOf(G.STEP_MAX), G.positionOf(999), 1e-6f)
    }
}
