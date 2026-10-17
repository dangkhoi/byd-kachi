package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 3.1.12 · CAR3D-SEAT-ICON-OVERLAP — icon ghế nhường chấm có việc ([Car3dDeconflict]). Owner 17/10 (3.1.11 trên xe): *"2 icon
 * sưởi, mát ghế nó đè lên kính trước, làm kính trước hơi khó nhấn"*. Bài đầu dựng lại ĐÚNG khung của xe từ log `Car3d` 17/10
 * (neo thế giới + hộp xe + khung px + 240 dpi) — mỗi bài ĐỎ khi gỡ bản sửa (lý do trong thông báo).
 */
class Car3dDeconflictTest {

    // ── Khung xe thật 17/10 11:05 (log `nạp byd-avm-eke … hộp … neo …`, `dots (frame px 1192x882)`) ─────────────────────────
    private val bounds = Car3dBounds(V3.ZERO, V3(1.0743, 0.7374, 2.3913))
    private val fw = 1192; private val fh = 882
    private val anchors = mapOf(
        "win-lf" to V3(0.71, 0.44, 0.24), "door-lf" to V3(0.85, -0.14, 0.35), "win-rf" to V3(-0.74, 0.44, 0.26),
        "door-rf" to V3(-0.85, -0.14, 0.35), "win-lr" to V3(0.72, 0.46, -0.67), "win-rr" to V3(-0.74, 0.46, -0.69),
        "trunk" to V3(0.0, 0.25, -2.11), "headl" to V3(0.56, -0.02, 2.05), "drl" to V3(0.66, -0.29, 2.14), "readl" to V3(0.0, 0.74, -0.35),
    )
    /** Điểm có việc (chấm) — kính, cốp, đèn; cửa chỉ xem (không chấm). */
    private val actionable = listOf("win-lf", "win-rf", "win-lr", "win-rr", "trunk", "headl", "drl", "readl")
    private val density = 1.5f                                   // 240 dpi (`applicationDensity - 240`)
    private val touch = (48 * density).toInt()                   // [KachiSpace.TOUCH] → ô chạm chấm
    private val disc = touch * 70 / 100                          // `Car3dHeroLayer.SEAT_PCT`
    private val gap = disc / 8                                   // `Car3dHeroLayer.GAP_DIV`
    private val cw = 2f * disc + gap
    private fun cam(yaw: Double, pitch: Double) = Car3dMath.camera(bounds, 30.0, 0.6, fw, fh, Car3dOrbit(yaw, 0.0, pitch = pitch))!!

    /** Vùng cứng như `Car3dTaps.project`: ô chạm quanh mỗi chấm có việc đang hiện (quay về camera, trong khung). */
    private fun dotZones(c: Car3dCamera): Car3dDeconflict.Zones = Car3dDeconflict.Zones(2).also { z ->
        for (id in actionable) {
            val a = anchors.getValue(id); val p = c.project(a, fw, fh) ?: continue
            if (!c.facing(a, Car3dMath.outward(a, bounds)) || p.x !in 0f..fw.toFloat() || p.y !in 0f..fh.toFloat()) continue
            z.addHard(p.x - touch / 2f, p.y - touch / 2f, p.x + touch / 2f, p.y + touch / 2f)
        }
    }

    private fun place(cx: Float, cy: Float, z: Car3dDeconflict.Zones, out: FloatArray = FloatArray(2)) =
        Car3dDeconflict.place(cx, cy, cw, disc.toFloat(), fw.toFloat(), fh.toFloat(), z, disc / 4f, disc * 3f, out) to out

    @Test
    fun `xe that 17-10 goc mac dinh - cum ghe lai dang de cham kinh truoc, sau khi dat thi nhuong o cham`() {
        val c = cam(40.0, 16.0)
        // Khung dựng lại khớp log xe (±2 px) cho các chấm KHÔNG bị dời: win-lr 739,373 · headl 443,535 · drl 444,588 · readl 628,328.
        for ((id, x, y) in listOf(Triple("win-lr", 739, 373), Triple("headl", 443, 535), Triple("drl", 444, 588), Triple("readl", 628, 328))) {
            val p = c.project(anchors.getValue(id), fw, fh)!!
            assertTrue(abs(p.x - x) <= 2 && abs(p.y - y) <= 2, "$id dựng lại $p ≠ log $x,$y")
        }
        // Bằng chứng gốc: neo win-lf chiếu (656, 403) nhưng log ghi chấm (656, 367) — luật cũ dời chấm LÊN né icon ghế.
        val win = c.project(anchors.getValue("win-lf"), fw, fh)!!
        assertEquals(656f, win.x, 2f); assertEquals(403f, win.y, 2f)
        val seat = c.project(Car3dHero.seatPoint(anchors.getValue("door-lf"), anchors.getValue("win-lf")), fw, fh)!!
        val z = dotZones(c)
        assertTrue(Car3dDeconflict.overlap(z.hard, z.nHard, seat.x, seat.y, cw, disc.toFloat()) > 0f, "fixture: cụm ở điểm ghế ĐÈ ô chạm kính trước")
        val (free, at) = place(seat.x, seat.y, z)
        assertTrue(free, "còn chỗ trống quanh ghế ⇒ không đè chấm nào")
        assertEquals(0f, Car3dDeconflict.overlap(z.hard, z.nHard, at[0], at[1], cw, disc.toFloat()), "cụm không đè ô chạm của chấm")
        assertTrue(hypot(at[0] - seat.x, at[1] - seat.y) <= disc * 3f + 1e-3f, "vẫn ở quanh ghế (≤ 3 đĩa): ${at.toList()} vs $seat")
        // Ưu tiên chạm: tâm chấm kính trước (và cả ô 48 dp quanh nó) thuộc CHẤM.
        assertTrue(Car3dDeconflict.claimed(win.x, win.y, z)); assertTrue(Car3dDeconflict.claimed(win.x + touch / 2f - 1, win.y - touch / 2f + 1, z))
    }

    @Test
    fun `quet moi goc xoay - hai cum ghe khong bao gio de cham co viec, khong de nhau, nam tron trong khung`() {
        var checked = 0
        for (yaw in -180..180 step 10) for (pitch in listOf(-5.0, 6.0, 16.0, 30.0, 45.0, 60.0)) {
            val c = cam(yaw.toDouble(), pitch)
            val z = dotZones(c); val base = z.nHard
            val placed = ArrayList<FloatArray>()
            for (s in Car3dHero.Seat.values()) {
                val k = if (s.side == Car3dHero.LEFT) "lf" else "rf"
                val sp = Car3dHero.seatPoint(anchors.getValue("door-$k"), anchors.getValue("win-$k"))
                if (!Car3dHero.seatVisible(c, sp, s.side)) continue
                val p = c.project(sp, fw, fh) ?: continue
                if (p.x !in 0f..fw.toFloat() || p.y !in 0f..fh.toFloat()) continue
                val (free, at) = place(p.x, p.y, z)
                assertTrue(free, "yaw $yaw pitch $pitch ${s.name}: không tìm được chỗ trống")
                assertTrue(at[0] - cw / 2 >= -1e-3f && at[0] + cw / 2 <= fw + 1e-3f && at[1] - disc / 2f >= -1e-3f && at[1] + disc / 2f <= fh + 1e-3f)
                z.addHard(at[0] - cw / 2, at[1] - disc / 2f, at[0] + cw / 2, at[1] + disc / 2f)
                placed += at.copyOf(); checked++
            }
            z.truncateHard(base)
            if (placed.size == 2) assertTrue(abs(placed[0][0] - placed[1][0]) >= cw - 1e-3f || abs(placed[0][1] - placed[1][1]) >= disc - 1e-3f,
                "yaw $yaw pitch $pitch: hai cụm đè nhau")
        }
        assertTrue(checked > 100, "quét đủ góc có ghế hiện: $checked")
    }

    @Test
    fun `cho goc da trong thi khong doi mot pixel`() {
        val z = Car3dDeconflict.Zones().apply { addHard(0f, 0f, 50f, 50f); addSoft(0f, 0f, 60f, 60f) }
        val out = FloatArray(2)
        assertTrue(Car3dDeconflict.place(500f, 400f, 100f, 50f, 1000f, 800f, z, 10f, 150f, out))
        assertEquals(500f, out[0]); assertEquals(400f, out[1])
    }

    @Test
    fun `vung cung (cham) thang vung mem (vung nhan bo phan) - de mem chu khong de cung`() {
        // Cụm 100×50 ở (500, 400) đè chấm; quanh đó chỉ còn chỗ trong vùng mềm (kính) — chọn đè kính, không đè chấm.
        val z = Car3dDeconflict.Zones().apply { addHard(480f, 380f, 520f, 420f); addSoft(300f, 250f, 700f, 550f) }
        val out = FloatArray(2)
        assertTrue(Car3dDeconflict.place(500f, 400f, 100f, 50f, 1000f, 800f, z, 10f, 150f, out))
        assertEquals(0f, Car3dDeconflict.overlap(z.hard, z.nHard, out[0], out[1], 100f, 50f))
        assertTrue(Car3dDeconflict.overlap(z.soft, z.nSoft, out[0], out[1], 100f, 50f) > 0f, "vùng mềm quá rộng để thoát trong tầm ⇒ chịu đè mềm")
    }

    @Test
    fun `vung mem gan bien - chiu doi them de tra lai vung nhan cua kinh`() {
        // Kính 200×80 quanh điểm ghế; mép dưới cách tâm 40 px. Thoát hẳn kính chỉ tốn ~65 px < phạt đè cả cụm (100 px) ⇒ ra khỏi kính.
        val z = Car3dDeconflict.Zones().apply { addSoft(400f, 360f, 600f, 440f) }
        val out = FloatArray(2)
        assertTrue(Car3dDeconflict.place(500f, 400f, 100f, 50f, 1000f, 800f, z, 5f, 150f, out))
        assertEquals(0f, Car3dDeconflict.overlap(z.soft, z.nSoft, out[0], out[1], 100f, 50f), "ra khỏi vùng nhấn kính: ${out.toList()}")
        assertTrue(hypot(out[0] - 500f, out[1] - 400f) <= 70f)
    }

    @Test
    fun `het cho thi tra tam re nhat trong khung, khong an icon - cham van thang cu cham`() {
        val z = Car3dDeconflict.Zones().apply { addHard(0f, 0f, 400f, 300f) }   // cả khung là ô chạm chấm
        val out = FloatArray(2)
        assertFalse(Car3dDeconflict.place(390f, 290f, 100f, 50f, 400f, 300f, z, 10f, 60f, out))
        assertTrue(out[0] in 50f..350f && out[1] in 25f..275f, "kẹp trong khung: ${out.toList()}")
        assertTrue(Car3dDeconflict.claimed(out[0], out[1], z), "chạm trên icon đè chấm ⇒ thuộc CHẤM")
        assertFalse(Car3dDeconflict.claimed(401f, 10f, z))
    }

    @Test
    fun `vung tam cua cum truoc bo duoc, bo dem no khi vuot suc chua`() {
        val z = Car3dDeconflict.Zones(1)
        repeat(20) { z.addHard(it.toFloat(), 0f, it + 1f, 1f) }
        assertEquals(20, z.nHard); assertTrue(Car3dDeconflict.claimed(19.5f, 0.5f, z))
        z.truncateHard(5); assertEquals(5, z.nHard); assertFalse(Car3dDeconflict.claimed(19.5f, 0.5f, z))
        z.clear(); assertEquals(0, z.nHard); assertEquals(0, z.nSoft)
    }
}
