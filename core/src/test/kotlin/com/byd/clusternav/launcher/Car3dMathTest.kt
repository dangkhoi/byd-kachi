package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R25 — khoá toán của widget xe 3D: chấm chạm phải nằm ĐÚNG chỗ Filament vẽ (cùng camera), góc xoay có giới hạn
 * (spec R25), quán tính tắt dần và dừng ở biên, chấm phía khuất bị ẩn, mô hình được quay về khung chung bằng ĐO.
 */
class Car3dMathTest {

    private val cam = Car3dCamera(V3.ZERO, distance = 10.0, yawDeg = 0.0, pitchDeg = 0.0, fovDeg = 60.0)

    @Test
    fun `tam xe chieu vao giua khung, diem sau lung camera khong chieu`() {
        val p = cam.project(V3.ZERO, 800, 400)!!
        assertEquals(400f, p.x, 0.01f); assertEquals(200f, p.y, 0.01f)
        assertNull(cam.project(V3(0.0, 0.0, 20.0), 800, 400))   // camera ở z = +10 nhìn về −z ⇒ z = 20 sau lưng
    }

    @Test
    fun `nhin thang mui xe, ben trai xe (+X) nam ben PHAI man hinh, Y len thi len`() {
        // Đứng trước mũi xe nhìn vào: sườn TRÁI xe ở bên PHẢI người nhìn — đúng ảnh tham chiếu ¾ trước-trái của owner.
        val left = cam.project(V3(1.0, 0.0, 0.0), 800, 400)!!
        val up = cam.project(V3(0.0, 1.0, 0.0), 800, 400)!!
        assertTrue(left.x > 400f); assertTrue(up.y < 200f)
        // Biên khung đúng góc nhìn: điểm ở cạnh trên của nón 60° dọc chiếu vào y = 0.
        val edge = cam.project(V3(0.0, 10.0 * Math.tan(Math.toRadians(30.0)), 0.0), 800, 400)!!
        assertEquals(0f, edge.y, 0.5f)
    }

    @Test
    fun `cham phia khuat than xe bi an`() {
        val front = Car3dCamera(V3.ZERO, 10.0, 0.0, 10.0, 30.0)
        assertTrue(front.facing(V3(0.0, 0.0, 2.0), V3(0.0, 0.0, 1.0)))
        assertFalse(front.facing(V3(0.0, 0.0, -2.0), V3(0.0, 0.0, -1.0)))
        assertTrue(front.facing(V3(0.0, 1.0, 0.0), V3.ZERO))   // tâm nóc: luôn thấy
        val leftSide = Car3dCamera(V3.ZERO, 10.0, 90.0, 10.0, 30.0)   // yaw 90 ⇒ camera bên trái xe (+X)
        assertTrue(leftSide.facing(V3(1.0, 0.0, 0.0), Car3dMath.outward(V3(1.0, 0.0, 0.0), V3.ZERO)))
        assertFalse(leftSide.facing(V3(-1.0, 0.0, 0.0), Car3dMath.outward(V3(-1.0, 0.0, 0.0), V3.ZERO)))
    }

    @Test
    fun `xe vua khung theo goc hep hon`() {
        val b = Car3dBounds(V3.ZERO, V3(1.0, 1.0, 1.0))
        val wide = Car3dMath.frameDistance(b, 30.0, 1600, 800, 1.0)
        val tall = Car3dMath.frameDistance(b, 30.0, 400, 800, 1.0)
        assertTrue(tall > wide)   // ô hẹp-cao ⇒ góc ngang hẹp ⇒ lùi xa hơn để không cắt mũi/đuôi
        assertEquals(Math.sqrt(3.0) / Math.sin(Math.toRadians(15.0)), wide, 1e-6)
    }

    @Test
    fun `xoay co gioi han, quan tinh tat dan va dung o bien`() {
        var o = Car3dOrbit(40.0, 0.0, -120.0, 200.0)
        o = o.drag(-400f, 800)   // kéo sang TRÁI nửa khung ⇒ +100°
        assertEquals(140.0, o.yaw, 1e-9)
        assertEquals(200.0, o.drag(-8000f, 800).yaw, 1e-9)   // kẹp biên
        o = Car3dOrbit(0.0, 0.0, -120.0, 200.0).fling(-800f, 800)
        assertTrue(o.moving)
        repeat(200) { o = o.step(1.0 / 24) }
        assertFalse(o.moving)
        assertTrue(o.yaw in 0.0..200.0)
        val hit = Car3dOrbit(195.0, 300.0, -120.0, 200.0).step(0.1)
        assertEquals(200.0, hit.yaw, 1e-9); assertFalse(hit.moving)
    }

    @Test
    fun `nghieng len xuong - keo doc doi goc ngang, keo cheo ca hai, quan tinh hai truc, kep bien`() {
        var o = Car3dOrbit(40.0, 0.0, -120.0, 200.0, pitch = 16.0)
        o = o.drag(0f, 400f, 800, 800)   // kéo XUỐNG nửa khung ⇒ camera lên +45°
        assertEquals(61.0, o.pitch, 1e-9); assertEquals(40.0, o.yaw, 1e-9)
        assertEquals(Car3dOrbit.PITCH_MAX, o.drag(0f, 8000f, 800, 800).pitch, 1e-9)
        assertEquals(Car3dOrbit.PITCH_MIN, o.drag(0f, -8000f, 800, 800).pitch, 1e-9)
        val diag = Car3dOrbit(40.0, 0.0, -120.0, 200.0, pitch = 16.0).drag(-80f, 80f, 800, 800)
        assertEquals(60.0, diag.yaw, 1e-9); assertEquals(25.0, diag.pitch, 1e-9)
        var f = Car3dOrbit(40.0, 0.0, -120.0, 200.0, pitch = 16.0).fling(0f, 600f, 800, 800)
        assertTrue(f.moving)
        repeat(200) { f = f.step(1.0 / 24) }
        assertFalse(f.moving); assertTrue(f.pitch in Car3dOrbit.PITCH_MIN..Car3dOrbit.PITCH_MAX); assertTrue(f.pitch > 16.0)
        // Chạm biên ngẩng ⇒ trục ngẩng dừng, trục xoay vẫn quán tính.
        val hit = Car3dOrbit(40.0, 100.0, -120.0, 200.0, pitch = 64.0, pitchVelocity = 300.0).step(0.1)
        assertEquals(Car3dOrbit.PITCH_MAX, hit.pitch, 1e-9); assertEquals(0.0, hit.pitchVelocity); assertTrue(hit.moving)
    }

    @Test
    fun `quay ve khung chung bang DO hop bao, khong soi guong`() {
        // Mô hình Z-lên, dài theo X, đầu xe về −X (kiểu tệp CAD): neo đầu/đuôi quyết chiều.
        val pl = Car3dOrient.place(V3(0.0, 0.0, 0.7), V3(2.4, 0.95, 0.7), front = V3(-2.3, 0.0, 0.6), rear = V3(2.3, 0.0, 0.8))
        val nose = pl.apply(V3(-2.4, 0.0, 0.7))
        assertEquals(2.4, nose.z, 1e-9)                       // đầu xe → +Z
        assertEquals(0.7, pl.apply(V3(0.0, 0.0, 1.4)).y, 1e-9) // nóc → +Y
        assertEquals(V3(0.95, 0.7, 2.4), pl.half)
        // Giữ chiều tay phải: (X × Y) · Z = +1 — trái không thành phải.
        assertEquals(1.0, pl.rot[0].cross(pl.rot[1]).dot(pl.rot[2]), 1e-9)
        // Ma trận cột-chính cho Filament cho cùng kết quả với apply().
        val m = pl.matrix(); val p = V3(-2.4, 0.3, 1.0)
        val w = pl.apply(p)
        assertEquals(w.z, m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14], 1e-5)
        assertEquals(w.y, m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13], 1e-5)
    }

    @Test
    fun `khong co neo dau duoi thi theo frontSign, glTF chuan giu nguyen`() {
        val pl = Car3dOrient.place(V3(0.0, 0.57, 0.24), V3(1.27, 0.57, 2.18))
        assertEquals(V3(0.0, 0.0, 1.0), pl.apply(V3(0.0, 0.57, 1.24)))
        assertNotNull(pl.bounds)
    }

    @Test
    fun `khoang cach vua hop - moi goc hop xe nam trong khung o moi goc ngang va ngang cao`() {
        // POLISH2 [ĐO máy ảo 13/10]: nhìn từ trên (ngẩng 58°) xe bị cắt ngang ở mép dưới khung 3D.
        val b = Car3dBounds(V3(0.0, 0.0, 0.0), V3(1.07, 0.74, 2.39)); val w = 1566; val h = 700
        for (yaw in listOf(-120.0, 0.0, 40.0, 90.0, 160.0, 200.0)) for (pitch in listOf(-5.0, 16.0, 40.0, 65.0)) {
            val d = maxOf(Car3dMath.frameDistance(b, 30.0, w, h, 0.78), Car3dMath.fitDistance(b, V3.ZERO, yaw, pitch, 30.0, w, h))
            val c = Car3dCamera(V3.ZERO, d, yaw, pitch, 30.0)
            for (i in 0 until 8) {
                val p = c.project(V3(b.half.x * (if (i and 1 == 0) -1 else 1), b.half.y * (if (i and 2 == 0) -1 else 1), b.half.z * (if (i and 4 == 0) -1 else 1)), w, h)!!
                assertTrue(p.x in 0f..w.toFloat() && p.y in 0f..h.toFloat(), "yaw $yaw pitch $pitch góc $i ra ngoài khung: $p")
            }
        }
    }
}
