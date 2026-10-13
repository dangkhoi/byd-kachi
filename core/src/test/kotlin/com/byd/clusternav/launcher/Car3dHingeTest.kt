package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * 3.01 R25 — bản lề suy từ cặp đóng/mở ([Car3dHinge]): khớp đúng trục/góc/điểm của một phép quay đã biết (cửa quay quanh
 * trục ĐỨNG tại mép trước, nắp cốp quay quanh trục NGANG ở mép mái), s = 0 giữ nguyên, s = 1 trùng bản mở; biến dạng
 * không cứng ⇒ không có bản lề (đổi bản ngay, không bịa đường đi); chuỗi `extras` đọc lại được.
 */
class Car3dHingeTest {

    private fun pts(): List<V3> = (0 until 60).map { i -> V3(0.2 + (i % 5) * 0.2, 0.3 + (i / 5 % 4) * 0.25, -0.85 - (i / 20) * 0.05) }

    @Test
    fun `cua quay quanh truc dung tai mep truoc - khop dung, s=0 giu nguyen, s=1 trung ban mo`() {
        val truth = Car3dHinge.Hinge(V3(0.0, 1.0, 0.0), Math.toRadians(-62.0), V3(0.97, 0.8, -0.85), 0.0)
        val closed = pts(); val open = closed.map { truth.apply(it, 1f) }
        val h = Car3dHinge.fit(closed, open)!!
        assertTrue(abs(abs(h.axis.y) - 1.0) < 1e-6, "${h.axis}")
        assertEquals(62.0, Math.toDegrees(abs(h.angle)), 1e-4)
        assertTrue(h.rms < 1e-6)
        // Điểm trên trục: x, z của bản lề (trục đứng — y tuỳ ý).
        assertEquals(0.97, h.pivot.x, 1e-6); assertEquals(-0.85, h.pivot.z, 1e-6)
        closed.forEachIndexed { i, p ->
            assertTrue((h.apply(p, 0f) - p).length() < 1e-6)
            assertTrue((h.apply(p, 1f) - open[i]).length() < 1e-6)
        }
        // Nửa đường: điểm giữ khoảng cách tới trục (quay, không trượt thẳng xuyên thân).
        val mid = h.apply(closed[7], 0.5f)
        fun r(p: V3) = Math.hypot(p.x - 0.97, p.z + 0.85)
        assertEquals(r(closed[7]), r(mid), 1e-6)
        assertEquals(h.axis.x, Car3dHinge.parse(""""bbox":[1,2,3,4,5,6],"hinge":${h.json()}""")!!.axis.x, 1e-6)
    }

    @Test
    fun `nap cop quay quanh truc ngang, bien dang khong cung thi khong co ban le`() {
        val truth = Car3dHinge.Hinge(V3(0.0, 0.0, 1.0), Math.toRadians(70.0), V3(-1.6, 1.3, 0.0), 0.0)
        val closed = pts(); val open = closed.map { truth.apply(it, 1f) }
        val h = Car3dHinge.fit(closed, open)!!
        assertTrue(abs(abs(h.axis.z) - 1.0) < 1e-6); assertEquals(70.0, Math.toDegrees(abs(h.angle)), 1e-4)
        val warped = open.mapIndexed { i, p -> if (i % 2 == 0) p + V3(0.0, 0.05, 0.0) else p }
        assertNull(Car3dHinge.fit(closed, warped))
        assertNull(Car3dHinge.fit(closed, closed), "không quay")
        assertNull(Car3dHinge.fit(closed.take(2), open.take(2)))
    }
}
