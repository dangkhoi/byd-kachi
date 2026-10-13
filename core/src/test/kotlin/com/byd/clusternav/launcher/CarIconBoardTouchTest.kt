package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.hypot

/**
 * 3.01 TAPFIX — owner 13/10 trên máy ảo: *"thử nhấn mấy cái nút trên emulator thấy nhảy loạn, nhấn nút này nó xanh nút
 * khác"*. [ĐO logcat `KachiCtl` emulator-5556 18:44] ~20 chạm trên bảng Đèn chỉ ra `drl` / `headl` / `readl`.
 *
 * Gốc [ĐO bằng bản chép thuật toán cũ, công cụ ngoài repo — bảng ở handoff `kachi-301-stage-7-TAPFIX-done.md`]: R24 `CarIconBoard.zones` chia vùng chạm k-d CHỈ cho
 * 3 điểm có nút và nở ra cả khung ⇒ ở ô Đèn thật 267×329 (cả Seal lẫn ảnh mặc định) tâm của MỌI điểm chỉ-xem nằm trong vùng
 * một đèn có nút: cốt + sương mù trước ⇒ `drl`, xi-nhan hai bên + đèn hông + sương mù sau ⇒ `readl`.
 *
 * Khoá: (1) tâm mỗi điểm thuộc chính nó; (2) tâm điểm chỉ-xem KHÔNG BAO GIỜ thuộc điểm có nút (đúng lỗi trên); (3) điểm giữa
 * hai điểm thuộc điểm gần hơn; (4) ngoài tầm mọi điểm ⇒ không ai; (5) ô trợ năng không chồng.
 */
class CarIconBoardTouchTest {

    private val lights = GroupIconBoard.of(GroupBoard.of(CapabilityGroups.LIGHTS, CarStatus()))!!
    private val arts = listOf(CarArtCatalog.DEFAULT, CarArtCatalog.SEAL_TOP)

    /** Ô Đèn thật [ĐO máy ảo 13/10]: 267×329 px, mật độ 1,5 ⇒ điểm 20 dp = 30 px, đích chạm 48 dp = 72 px. Thêm ô 2×3. */
    private val boards = listOf(Triple(267, 329, 30f), Triple(267, 312, 30f))
    private val touch = 72
    private val reach = SpotTouch.reachPx(touch)

    private fun layout(a: CarArt, w: Int, h: Int, disc: Float): Pair<List<CarIconBoard.Spot>, List<Pair<Float, Float>>> {
        val s = minOf((w - disc) / a.aspect, h - disc); val fw = s * a.aspect
        val spots = CarIconBoard.place(lights, a)!!
        return spots to spots.map { CarIconBoard.centerIn(it, (w - fw) / 2f, (h - s) / 2f, fw, s) }
    }

    private fun each(block: (String, List<CarIconBoard.Spot>, List<Pair<Float, Float>>, Int, Int) -> Unit) =
        arts.forEach { a -> boards.forEach { (w, h, d) -> val (sp, pts) = layout(a, w, h, d); block("${a.key} ${w}x$h", sp, pts, w, h) } }

    @Test fun `tam moi diem thuoc chinh no - ca diem chi-xem`() = each { k, spots, pts, _, _ ->
        pts.forEachIndexed { i, (x, y) ->
            assertEquals(i, SpotTouch.owner(pts, x, y, reach), "$k: tâm ${spots[i].tile.icon} không thuộc chính nó")
        }
    }

    @Test fun `cham diem chi-xem khong bao gio ra lenh den ben canh`() = each { k, spots, pts, _, _ ->
        val ro = spots.indices.filter { spots[it].tile.action == null }
        assertTrue(ro.size >= 5, "$k: bảng Đèn phải có các điểm chỉ-xem (sương mù, xi-nhan, cốt, hông…)")
        ro.forEach { i ->
            val (x, y) = pts[i]
            // Lệch khỏi tâm tới gần mép đĩa 20 dp (ngón tay không trúng tâm) vẫn thuộc điểm chỉ-xem đó.
            listOf(0f to 0f, 6f to 0f, -6f to 0f, 0f to 6f, 0f to -6f).forEach { (dx, dy) ->
                val o = SpotTouch.owner(pts, x + dx, y + dy, reach)
                assertEquals(null, o?.let { spots[it].tile.action }, "$k: chạm ${spots[i].tile.icon} (+$dx,+$dy) ra lệnh ${o?.let { spots[it].tile.action?.id }}")
            }
        }
    }

    @Test fun `diem giua hai diem thuoc diem gan hon - suong mu va ban ngay`() = arts.forEach { a ->
        val (spots, pts) = layout(a, 267, 329, 30f)
        val fog = spots.indexOfFirst { it.tile.icon == "ic-car-front-fog" }
        val drl = spots.indexOfFirst { it.tile.icon == "ic-car-front-drl" }
        val (fx, fy) = pts[fog]; val (dx, dy) = pts[drl]
        fun at(t: Float) = SpotTouch.owner(pts, fx + (dx - fx) * t, fy + (dy - fy) * t, reach)
        assertEquals(fog, at(0.4f), "${a.key}: 40 % từ sương mù ⇒ sương mù (chỉ xem)")
        assertEquals(drl, at(0.6f), "${a.key}: 60 % ⇒ đèn ban ngày (có nút)")
    }

    @Test fun `vung la Voronoi theo tam, ngoai tam thi khong ai`() = each { k, _, pts, w, h ->
        for (x in 0 until w step 3) for (y in 0 until h step 3) {
            val o = SpotTouch.owner(pts, x.toFloat(), y.toFloat(), reach)
            val near = pts.minOf { (px, py) -> hypot(px - x, py - y) }
            if (near > reach) { assertNull(o, "$k ($x,$y) cách mọi điểm ${near}px > tầm"); continue }
            val (ox, oy) = pts[o!!]
            assertEquals(near, hypot(ox - x, oy - y), 1e-3f, "$k ($x,$y) không về điểm gần nhất")
        }
    }

    @Test fun `60 dp tren diem cao nhat thi khong ai`() = each { k, _, pts, _, _ ->
        val (tx, ty) = pts.minByOrNull { it.second }!!
        assertNull(SpotTouch.owner(pts, tx, ty - 90f, reach), "$k: 60 dp (90 px @1,5) trên điểm cao nhất vẫn bắn")
    }

    @Test fun `hoa thi chi so nho, rong thi null`() {
        assertEquals(0, SpotTouch.owner(listOf(0f to 0f, 10f to 0f), 5f, 0f, 100f))
        assertNull(SpotTouch.owner(emptyList(), 5f, 5f, 100f))
        assertNull(SpotTouch.owner(listOf(0f to 0f), 50f, 0f, 49f))
    }

    @Test fun `o tro nang rieng khong chong, chua tam, khong qua dich cham`() = each { k, _, pts, w, h ->
        val z = pts.indices.map { SpotTouch.ownSquare(pts, it, touch.toFloat(), w, h) }
        z.forEachIndexed { i, r ->
            val (x, y) = pts[i]
            assertTrue(x >= r.left && x < r.right && y >= r.top && y < r.bottom, "$k: ô $i không chứa tâm: $r")
            assertTrue(r.width <= touch && r.height <= touch, "$k: ô $i quá đích chạm: $r")
        }
        for (i in z.indices) for (j in i + 1 until z.size) {
            val a = z[i]; val b = z[j]
            assertTrue(a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top, "$k: ô $i/$j chồng: $a $b")
        }
    }
}
