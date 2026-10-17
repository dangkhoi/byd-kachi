package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.14 PERF-SL6-3113 R3 — bảng tra của tài sản xe 3D trên luồng chính (chép logic hỏi/đổi cảnh của bộ vẽ cũ, nay không JNI).
 * Khoá các luật cũ của bộ vẽ (3.1.5): hộp đang hiện chỉ tính bộ phận hiện; tư thế = bản lề, KHÔNG theo kính trượt; kính theo TÊN;
 * nhóm diễn hoạt (`extras.grp`) khớp như tên; ẩn/hiện chỉ báo thay đổi thật; đèn khoang không lặp lệnh.
 */
class Car3dIndexTest {

    private fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) = floatArrayOf(x0, y0, z0, x1, y1, z1)
    private val hinge = Car3dHinge.Hinge(axis = V3(0.0, 1.0, 0.0), angle = Math.PI / 2, pivot = V3(0.0, 0.0, 0.0), rms = 0.0)
    private fun idx() = Car3dIndex(listOf(
        Car3dIndex.Part(1, "body_paint", null, box(-1f, 0f, -2f, 1f, 1f, 2f), null, null, "paint", null),
        Car3dIndex.Part(2, "doorlf_open", null, box(-3f, 0f, 0f, -1f, 1f, 1f), null, null, "open", null),
        Car3dIndex.Part(3, "glass_lf", null, box(-1f, 1f, 0f, -0.9f, 1.5f, 1f), null, null, null, null),
        Car3dIndex.Part(4, "body_cut", "trunk", box(-1f, 0.5f, 1.5f, 1f, 1f, 2f), null, hinge, "paint", null),
        Car3dIndex.Part(5, "inside", null, box(-0.5f, 0.2f, -1f, 0.5f, 0.8f, 1f), null, null, null, floatArrayOf(1f, 0.8f, 0.6f)),
    ), Car3dBounds(V3(0.0, 0.5, 0.0), V3(3.0, 0.75, 2.0)))

    @Test
    fun `hop dang hien bo qua bo phan an`() {
        val i = idx()
        assertEquals(mapOf(2 to false), i.show { n -> if (n == "doorlf_open") false else null })
        val b = i.visibleBounds()!!
        assertEquals(-1.0, b.center.x - b.half.x, 1e-6, "bản cửa mở đang ẩn không nới hộp (3,7 m thay vì 1,86 m — ĐO 13/10)")
        assertTrue(i.show { n -> if (n == "doorlf_open") false else null }.isEmpty(), "không đổi ⇒ không lệnh")
    }

    @Test
    fun `nhom dien hoat khop nhu ten - ban le quay hop, kinh truot thi khong`() {
        val i = idx()
        assertTrue(i.hasPart("trunk*") && i.hasHinge("trunk*"), "nắp cốp cắt từ thân tìm qua nhóm")
        assertFalse(i.hasHinge("glass*"))
        val rest = i.box(Car3dAnchor.Node("glass_lf"))!!
        assertEquals(1, i.slide("glass_lf", 1f).size, "kính trượt theo tên")
        assertEquals(rest, i.box(Car3dAnchor.Node("glass_lf"), posed = true), "kính trượt KHÔNG dời hộp/đích (3.1.5: chấm ở lại ô kính)")
        val before = i.box(Car3dAnchor.Node("trunk*"), posed = true)
        assertEquals(1, i.swing("trunk*", 1f).size)
        assertTrue(before != i.box(Car3dAnchor.Node("trunk*"), posed = true), "nắp quay ⇒ đích chạm đi theo")
        assertEquals(before, i.box(Car3dAnchor.Node("trunk*")), "hộp nghỉ không đổi")
        assertTrue(i.swing("glass*", 1f).isEmpty(), "không bản lề ⇒ không lệnh")
    }

    @Test
    fun `son theo vat lieu nut, den khoang khong lap`() {
        val i = idx()
        assertEquals(listOf(1, 2, 4), i.paintTargets(), "thân + cánh cửa mở + phần tách cùng nhận màu")
        assertEquals(setOf(5), i.glow(true)!!.keys)
        assertNull(i.glow(true), "cùng trạng thái ⇒ không lệnh")
        assertEquals(0f, i.glow(false)!!.getValue(5)[0])
    }

    @Test
    fun `thieu extras thi lui hop Filament va hop tai san`() {
        val i = Car3dIndex(listOf(Car3dIndex.Part(7, "x", null, null, box(0f, 0f, 0f, 2f, 2f, 2f), null, null, null)), Car3dBounds(V3.ZERO, V3(9.0, 9.0, 9.0)))
        assertEquals(V3(1.0, 1.0, 1.0), i.anchor(Car3dAnchor.Node("x", ty = 0.5)))
        assertEquals(9.0, i.visibleBounds()!!.half.x, "không hộp riêng ⇒ hộp tài sản")
    }

    /** Bảng "nút ↔ biến thể" dựng sẵn cho đúng kết quả của [Car3dNames.visibleAll] (luật gộp mọi biến thể) ở mọi tổ hợp trạng thái. */
    @Test
    fun `an hien theo bang bien the dung sẵn khop visibleAll`() {
        val parts = listOf("doorlf", "doorlf_open", "doorlf_lamp_turnleft_openon", "lamp_turnleft_on", "lamp_turnleft", "body")
            .mapIndexed { i, n -> Car3dIndex.Part(i, n, null, null, null, null, null, null) }
        val variants = listOf(Car3dVariant("doorlf*", "doorlf*open*", "door", "d"), Car3dVariant("*turnleft*", "*turnleft*on", "lamp", "t"))
        for (a in listOf(false, true)) for (b in listOf(false, true)) {
            val open = listOf(a, b)
            val viaTable = Car3dIndex(parts, null).showVariants(variants, open)
            val viaNames = Car3dIndex(parts, null).show { n -> Car3dNames.visibleAll(variants, open, n) }
            assertEquals(viaNames, viaTable, "cửa=$a xi-nhan=$b")
        }
        val i = Car3dIndex(parts, null)
        i.showVariants(variants, listOf(false, false))
        assertTrue(i.showVariants(variants, listOf(false, false)).isEmpty(), "không đổi ⇒ không lệnh")
    }
}
