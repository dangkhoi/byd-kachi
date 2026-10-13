package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 3.01 TAPFIX — bảng Kính. Owner 13/10: *"widget kính cũng bị vậy luôn đó, click lên xa trên top, tức là nó không đúng vùng,
 * chứ ko chỉ là vùng chọn rộng đâu"*. Gốc: [GroupActionMatrix] chia khung thành phần tư/dải theo neo CHUNG nở tới mép ô
 * (vùng kính trước bắt đầu từ mép TRÊN ô) — vùng không gắn với chỗ chấm vẽ. Nay chủ cú chạm = chấm có tâm VẼ gần nhất trong
 * tầm ([SpotTouch]); tâm tính bằng CÙNG phép đặt khung ([SpotTouch.fitCenter]) + [CarDots.center] mà `CarLevelsLayer.centerOf`
 * dùng để vẽ.
 *
 * Khoá trên CẢ HAI ảnh (mặc định đứng · Seal xoay+cắt) và cỡ ô thật (2×3 owner 267×312 [ĐO], ô cao, ô vuông lớn, ô rộng-thấp
 * cốp xuống chân): (1) tâm mỗi chấm thuộc chính nó; (2) chạm 60 dp phía trên chấm cao nhất ⇒ không ai; (3) mép trên ô ⇒ không ai.
 */
class CarDotTouchTest {

    private val windows = GroupActionMatrix.of(GroupBoard.of(CapabilityGroups.WINDOWS, CarStatus()))!!
    private val arts = listOf(CarArtCatalog.DEFAULT, CarArtCatalog.SEAL_TOP)
    private val base = GroupActionMatrix.CompactMetrics(touchPx = 72, gap = 6, minCarPx = 108, bars = 2, barPx = 72, ends = 1)
    /** Mật độ máy ảo 1,5 ⇒ 60 dp = 90 px. */
    private val sixtyDp = 90f
    private val boards = listOf(Triple(267, 312, true), Triple(267, 700, true), Triple(400, 400, true), Triple(870, 170, false))

    /** Tâm px của các chấm như `ActionMatrixCompact.onLayout` → `CarLevelsLayer.fit` → `centerOf`. */
    private fun centres(art: CarArt, w: Int, h: Int, onCar: Boolean): List<Pair<CarPart, Pair<Float, Float>>> {
        val m = if (onCar) base else base.copy(ends = 0, bars = 3)
        val f = GroupActionMatrix.compactForm(w, h, m) ?: error("${art.key} ${w}x$h không vừa")
        var (aw, ah) = GroupActionMatrix.compactCarArea(f, w, h, m)
        var ax = 0; var ay = 0
        fun fit() = SpotTouch.fitCenter(ax + m.gap.toFloat(), ay.toFloat(), ax + aw - m.gap.toFloat(), (ay + ah).toFloat(), art.aspect)
        val c0 = fit()
        if (f == GroupActionMatrix.CompactForm.STACK) {
            val need = maxOf(c0[3].toInt(), (2 + m.ends) * m.touchPx)
            if (need < ah) { ay = (h - need - m.touchPx - m.gap) / 2; ah = need }
        } else {
            val need = maxOf(c0[2].toInt() + 2 * m.gap, 2 * m.touchPx)
            if (need < aw) { ax += (aw - need) / 2; aw = need }
        }
        val c = fit()
        val parts = windows.placed.map { it.place!! } + if (onCar) windows.onCar.map { it.first } else emptyList()
        return parts.map { p -> val a = CarDots.center(p, art); p to ((c[0] + a.x * c[2]) to (c[1] + a.y * c[3])) }
    }

    @Test fun `tam cham thuoc chinh no, xa phia tren thi khong ai`() {
        var n = 0
        arts.forEach { art ->
            boards.forEach { (w, h, onCar) ->
                val k = "${art.key} ${w}x$h"
                val cs = centres(art, w, h, onCar)
                val pts = cs.map { it.second }
                val reach = SpotTouch.reachPx(base.touchPx)
                pts.forEachIndexed { i, (x, y) -> assertEquals(i, SpotTouch.owner(pts, x, y, reach), "$k: tâm ${cs[i].first}"); n++ }
                val (tx, ty) = pts.minByOrNull { it.second }!!
                assertNull(SpotTouch.owner(pts, tx, ty - sixtyDp, reach), "$k: 60 dp trên chấm cao nhất ($tx,$ty) vẫn bắn")
                if (ty - sixtyDp >= 0f) pts.forEach { (x, _) -> assertNull(SpotTouch.owner(pts, x, 0f, reach), "$k: mép trên ô x=$x vẫn bắn") }
            }
        }
        assertEquals(arts.size * (3 * 5 + 4), n)
    }
}
