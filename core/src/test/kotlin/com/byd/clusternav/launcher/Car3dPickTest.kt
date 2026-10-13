package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R25 — chạm thẳng vào bộ phận xe 3D ([Car3dPick]): tâm chiếu của mỗi bộ phận chọn CHÍNH nó ở nhiều góc xoay, phía
 * khuất không chọn được, khoảng trống không chọn gì, đích nhỏ trong đích lớn thắng, đích mảnh vẫn được nới ≥ minPx.
 * Hộp thế giới theo khung chung (sườn TRÁI +X, đầu xe +Z) dựng từ số đo Seal sau phép đặt (`Car3dSealTest`).
 */
class Car3dPickTest {

    private val bounds = Car3dBounds(V3.ZERO, V3(1.07, 0.74, 2.39))
    private fun box(id: String, cx: Double, cy: Double, cz: Double, hx: Double, hy: Double, hz: Double) =
        Car3dPick.Target(id, V3(cx - hx, cy - hy, cz - hz), V3(cx + hx, cy + hy, cz + hz), V3(cx, cy, cz))

    // Kính trái trước NẰM TRONG tấm cửa trái trước (đích cửa chỉ-xem lớn hơn); đèn pha trái mảnh ở đầu xe.
    private val winLf = box("win-lf", 0.80, 0.40, 0.30, 0.05, 0.20, 0.45)
    private val doorLf = box("door-lf", 0.85, 0.05, 0.35, 0.08, 0.55, 0.60)
    private val winRf = box("win-rf", -0.80, 0.40, 0.30, 0.05, 0.20, 0.45)
    private val winLr = box("win-lr", 0.78, 0.42, -0.70, 0.05, 0.18, 0.40)
    private val headl = box("headl", 0.56, -0.02, 2.05, 0.06, 0.03, 0.05)
    private val targets = listOf(winLf, doorLf, winRf, winLr, headl)
    private val w = 1566; private val h = 826; private val min = 96f

    private fun cam(yaw: Double, pitch: Double = 16.0) = Car3dCamera(V3.ZERO, Car3dMath.frameDistance(bounds, 30.0, w, h, 0.6), yaw, pitch, 30.0)

    @Test
    fun `tam chieu cua moi bo phan chon chinh no o nhieu goc xoay, phia khuat khong chon`() {
        for (yaw in listOf(30.0, 40.0, 60.0, 90.0)) {
            val c = cam(yaw)
            for (t in listOf(winLf, winLr) + if (yaw <= 60.0) listOf(headl) else emptyList()) {
                val r = Car3dPick.rect(c, t, w, h, min)!!
                assertEquals(t.id, Car3dPick.pick(c, targets, bounds, w, h, r.cx, r.cy, min), "yaw $yaw ${t.id}")
            }
            // Kính PHẢI ở phía khuất: tâm chiếu của nó không chọn kính phải.
            val rr = Car3dPick.rect(c, winRf, w, h, min)!!
            assertTrue(Car3dPick.pick(c, targets, bounds, w, h, rr.cx, rr.cy, min) != "win-rf", "yaw $yaw")
        }
        // Quay ra sau-phải: kính phải thấy, kính trái khuất.
        val back = cam(-60.0)
        val r = Car3dPick.rect(back, winRf, w, h, min)!!
        assertEquals("win-rf", Car3dPick.pick(back, targets, bounds, w, h, r.cx, r.cy, min))
    }

    @Test
    fun `luoi yaw x pitch - kinh trai chon chinh no, kinh phai khuat khong bao gio bi chon`() {
        for (yaw in listOf(20.0, 50.0, 90.0, 130.0)) for (pitch in listOf(Car3dOrbit.PITCH_MIN, 0.0, 16.0, 40.0, Car3dOrbit.PITCH_MAX)) {
            val c = cam(yaw, pitch)
            // Kính SAU-trái ở yaw 20° gần như nằm dọc tia nhìn (lướt) ⇒ được coi là khuất ở pitch cao — chỉ đòi từ 50°.
            for (t in listOf(winLf) + if (yaw >= 50.0) listOf(winLr) else emptyList()) {
                val r = Car3dPick.rect(c, t, w, h, min)!!
                assertEquals(t.id, Car3dPick.pick(c, targets, bounds, w, h, r.cx, r.cy, min), "yaw $yaw pitch $pitch ${t.id}")
            }
            val rr = Car3dPick.rect(c, winRf, w, h, min)!!
            assertTrue(Car3dPick.pick(c, targets, bounds, w, h, rr.cx, rr.cy, min) != "win-rf", "yaw $yaw pitch $pitch")
            assertNull(Car3dPick.pick(c, targets, bounds, w, h, 3f, 3f, min), "góc trống yaw $yaw pitch $pitch")
        }
    }

    @Test
    fun `dich nho trong dich lon thang, ngoai kinh trong cua la cua, khoang trong khong chon gi`() {
        val c = cam(90.0)
        val door = Car3dPick.rect(c, doorLf, w, h, min)!!
        val win = Car3dPick.rect(c, winLf, w, h, min)!!
        assertEquals("win-lf", Car3dPick.pick(c, targets, bounds, w, h, win.cx, win.cy, min))
        assertEquals("door-lf", Car3dPick.pick(c, targets, bounds, w, h, door.cx, door.b - 2f, min))
        assertNull(Car3dPick.pick(c, targets, bounds, w, h, 5f, 5f, min))
        assertNull(Car3dPick.pick(c, targets, bounds, w, h, w / 2f, h - 5f, min))
    }

    @Test
    fun `dich manh duoc noi toi minPx, dich sau lung camera thi bo`() {
        val r = Car3dPick.rect(cam(40.0), headl, w, h, min)!!
        assertTrue(r.r - r.l >= min - 0.01f && r.b - r.t >= min - 0.01f, "$r")
        val behind = Car3dPick.Target("x", V3(0.0, 0.0, 99.0), V3(0.1, 0.1, 99.1), V3(0.0, 0.0, 99.0))
        assertNull(Car3dPick.rect(cam(0.0), behind, w, h, min))
    }

    @Test
    fun `cham giua nap cop ra cop - dai den duoi chi-xem nam tren nap khong cuop cu cham`() {
        // POLISH2 [ĐO máy ảo 13/10]: chạm giữa nắp cốp (nhìn từ sau) ra `tap tail → chỉ xem` vì hộp đèn đuôi nhỏ hơn hộp nắp.
        val trunk = box("trunk", 0.0, 0.25, -2.11, 0.62, 0.25, 0.25)
        val tail = box("tail", 0.0, 0.17, -2.08, 0.86, 0.06, 0.10)
        val c = cam(180.0, 20.0)
        val t = Car3dPick.rect(c, tail, w, h, min)!!
        val acts = setOf("trunk")
        assertEquals("trunk", Car3dPick.pick(c, listOf(trunk, tail), bounds, w, h, t.cx, t.cy, min) { it in acts })
        val far = Car3dPick.rect(c, tail, w, h, min)!!.let { it.l + 4f }
        assertEquals("tail", Car3dPick.pick(c, listOf(trunk, tail), bounds, w, h, far, t.cy, min) { it in acts }, "ngoài nắp ⇒ đèn (chỉ xem)")
    }

    /** Tâm phần giao của hai chữ nhật; `null` = không chồng nhau ở góc này. */
    private fun overlap(a: Car3dPick.Rect, b: Car3dPick.Rect): Pair<Float, Float>? {
        val l = maxOf(a.l, b.l); val r = minOf(a.r, b.r); val t = maxOf(a.t, b.t); val bb = minOf(a.b, b.b)
        return if (l < r && t < bb) (l + r) / 2 to (t + bb) / 2 else null
    }

    @Test
    fun `soat Pass 3 P2 - cham cua sau (chi xem) chong len hop nap cop MO khong duoc bam o cop`() {
        // Nắp cốp đã mở: hộp theo tư thế vươn cao, chữ nhật chiếu chồng lên phần đuôi cửa sau ở góc sau-¾-trái. Cửa là đích chỉ xem;
        // bản POLISH2 cho đích có việc thắng hễ cùng chứa điểm ⇒ chạm cửa gửi `performClick` ô cốp (lệnh ghi xe không ai nhắm).
        val lidOpen = box("trunk", 0.0, 0.70, -1.85, 0.62, 0.65, 0.75)
        val doorLr = box("door-lr", 0.85, 0.05, -0.70, 0.08, 0.55, 0.60)
        val acts = setOf("trunk")
        var checked = 0
        for (yaw in listOf(95.0, 100.0, 105.0, 110.0, 120.0)) for (pitch in listOf(5.0, 20.0)) {
            val c = cam(yaw, pitch)
            val door = Car3dPick.rect(c, doorLr, w, h, min)!!; val lid = Car3dPick.rect(c, lidOpen, w, h, min)!!
            val (x, y) = overlap(door, lid) ?: continue
            if (lid.contains(door.cx, door.cy)) continue   // cửa "nằm trên" nắp thì nhường là đúng luật — không phải ca này
            checked++
            assertEquals("door-lr", Car3dPick.pick(c, listOf(lidOpen, doorLr), bounds, w, h, x, y, min) { it in acts }, "yaw $yaw pitch $pitch")
        }
        assertTrue(checked > 0, "phải có ít nhất một góc hai chữ nhật chồng nhau mà tâm cửa ngoài nắp")
    }

    @Test
    fun `soat Pass 3 P2 - den doc (hop kinh cabin) chong len tam cua khong cuop cu cham cua, kinh van thang cua`() {
        // Đèn đọc: hộp = cả kính cabin (`body_glass`); neo ở kính sườn trái (neo giữa xe thì pháp tuyến elip suy biến ⇒ "khuất").
        val readl = Car3dPick.Target("readl", V3(-0.82, 0.20, -1.00), V3(0.82, 0.64, 1.20), V3(0.80, 0.42, 0.10))
        val acts = setOf("readl", "win-lf")
        val c = cam(90.0, 16.0)
        val door = Car3dPick.rect(c, doorLf, w, h, min)!!; val glass = Car3dPick.rect(c, readl, w, h, min)!!
        assertTrue(!glass.contains(door.cx, door.cy), "tâm cửa dưới kính")
        // Điểm trên tấm cửa nằm trong chữ nhật kính (mép trên cửa) nhưng ngoài kính cửa ⇒ CỬA (chỉ xem), không phải đèn đọc.
        val p = (1..9).map { door.l + (door.r - door.l) * it / 10 to door.t + (door.b - door.t) * 0.2f }.first { (x, y) ->
            glass.contains(x, y) && Car3dPick.rect(c, winLf, w, h, min)!!.contains(x, y).not()
        }
        assertEquals("door-lf", Car3dPick.pick(c, targets + readl, bounds, w, h, p.first, p.second, min) { it in acts })
        // Kính cửa (có việc, nhỏ hơn) vẫn thắng đèn đọc (có việc, to hơn) — nhỏ nhất thắng giữa hai đích cùng loại.
        val win = Car3dPick.rect(c, winLf, w, h, min)!!
        assertEquals("win-lf", Car3dPick.pick(c, targets + readl, bounds, w, h, win.cx, win.cy, min) { it in acts })
    }
}
