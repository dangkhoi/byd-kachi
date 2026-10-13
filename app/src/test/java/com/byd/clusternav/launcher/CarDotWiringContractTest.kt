package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 DOT — owner 13/10: *"thay vì vẽ hẳn 1 cái gạch màu xanh, mình vẽ chấm tròn thôi, như bọn kinex, kimlauncher thấy
 * nó đẹp hơn"*. Khoá: (1) bảng kính + bảng cửa cùng đi qua MỘT bộ tô chấm ([StateDotPainter]) với trạng thái từ `:core`
 * ([CarDots]); (2) lớp "kính hạ" cũ (khoang tối cắt mặt nạ, nét mép kính, đường bao mảnh) không quay lại; (3) chấm giữ
 * ≥ 3:1 trên ảnh xe ở cả bảng Tối lẫn Sáng, đo theo độ chói sơn xe thật quanh tâm chấm (diagnostics
 * `widget-visual-301-2026-10-13.md` §9: p95 xấu nhất 0,4695 = ảnh mặc định cửa trước-phải, trung vị xấu nhất 0,0395 = Seal
 * sau-phải, đỉnh 0,598).
 */
class CarDotWiringContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher/"
    private fun code(f: String) = SourceRoots.codeOf(dir + f)

    @Test
    fun `bang kinh va bang cua dung chung bo to cham`() {
        val levels = code("CarLevelsLayer.kt")
        assertTrue(levels.contains("dots.draw(") && levels.contains("CarDots.ofCell(") && levels.contains("CarDots.center("))
        val door = code("DoorBoardView.kt")
        assertTrue(door.contains("dots.draw(") && door.contains("CarDots.ofPart("), "bảng cửa cùng ngôn ngữ chấm")
        // Owner 13/10 "đẹp, bỏ số luôn em, vì cái chấm nó có thể hiện tương đối % rồi": không nhãn % cạnh xe; số chỉ còn
        // trong contentDescription của vùng chạm.
        val compact = code("ActionMatrixCompact.kt")
        assertFalse(compact.contains("kit.value(") || compact.contains("TextView") || compact.contains("anchorY("), "còn nhãn % trên màn")
        assertTrue(compact.contains("describeZones()") && compact.contains("?.value"), "số chính xác phải ở contentDescription")
    }

    @Test
    fun `lop kinh ha cu khong quay lai`() {
        val levels = code("CarLevelsLayer.kt")
        listOf("clipPath", "clipRect", "LinearGradient", "drawLine(", "drawPath(", "fillTo", "Paint.Style.STROKE").forEach {
            assertFalse(levels.contains(it), "CarLevelsLayer còn dấu lớp 'kính hạ' (owner 13/10 bỏ gạch xanh): $it")
        }
        assertFalse(code("DoorBoardView.kt").contains("CORE_RATIO"), "chấm quầng+lõi riêng của bảng cửa đã gộp vào bộ tô chung")
    }

    @Test
    fun `co cham 10 den 12 dp theo khung`() {
        val d = 1.5f
        assertEquals(10f * d, StateDotPainter.diameterPx(20f, d), 1e-3f)
        assertEquals(12f * d, StateDotPainter.diameterPx(2000f, d), 1e-3f)
        assertEquals(120f * StateDotPainter.RATIO, StateDotPainter.diameterPx(120f, d), 1e-3f)
    }

    @Test
    fun `cham tren anh xe dat 3 tren 1 o bang toi va sang`() {
        listOf(KachiPalette.DARK to true, KachiPalette.LIGHT to false).forEach { (p, night) ->
            val k = StateDotPainter.Inks.of(p, night)
            listOf(MEDIAN_WORST, P95_WORST, PEAK).forEach { l ->
                val bg = ColorMath.over(k.backing, ColorMath.grayOfLuminance(l))
                val checks = mapOf(
                    "mở (chấm đặc nhấn)" to ColorMath.ratio(k.accent, bg),
                    "đóng (vòng)" to ColorMath.ratio(k.closed, bg),
                    "chưa đọc (vòng đứt)" to ColorMath.ratio(k.unknown, bg),
                )
                checks.forEach { (what, r) -> assertTrue(r >= 3.0, "${if (night) "Tối" else "Sáng"} sơn L=$l: $what = $r < 3:1") }
            }
        }
    }

    private companion object {
        const val MEDIAN_WORST = 0.0395
        const val P95_WORST = 0.4695
        const val PEAK = 0.598
    }
}
