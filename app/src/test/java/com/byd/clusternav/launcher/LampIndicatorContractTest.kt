package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 POLISH — owner 13/10 *"phân biệt ra dạng icon cho hiểu, với cả cái đèn xinhan có nhấp nháy theo xe không?"*.
 *
 * Khoá (1) HÌNH: nút = icon trong đĩa tông (vòng chỉ khi bật/chưa biết), chỉ-xem = đèn báo trần (không đĩa, không vòng) sáng màu
 * loại đèn; (2) TƯƠNG PHẢN ≥ 3:1 (WCAG 1.4.11) của nét icon ở Tối + Sáng trên ảnh xe (cùng ba mức sáng ảnh đo ở
 * [CarDotWiringContractTest]); (3) NHỊP xi-nhan: chỉ khi có đèn đang nháy + bảng đang hiện, hẹn đúng MỘT lần vẽ lại ở mép pha
 * (không vòng hoạt hoạ lúc nghỉ — luật hiệu năng owner 08/10); (4) số giả vehicleTest bật được xi-nhan.
 */
class LampIndicatorContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher/"
    private val painter = SourceRoots.codeOf(dir + "StateDotPainter.kt")
    private val board = SourceRoots.codeOf(dir + "CarIconBoardView.kt")

    @Test
    fun `nut la dia tong, chi-xem la den bao tran`() {
        val button = SourceRoots.body(painter, "private fun drawButton(")
        assertTrue(button.contains("inks.shadow") && button.contains("inks.tonal") && button.contains("canvas.drawCircle(cx, cy, r, fill)"), "nút = đĩa tông đặc trên nền tối")
        assertTrue(button.contains("if (kind != CarDots.Kind.CLOSED)"), "vòng chỉ khi bật/chưa biết")
        val lamp = SourceRoots.body(painter, "fun drawLamp(")
        assertFalse(lamp.contains("inks.backing") || lamp.contains("ring"), "đèn báo không đĩa nền, không vòng")
        assertTrue(lamp.contains("inks.lampEdge") && lamp.contains("inks.lampTone(lamp)"), "viền tương phản + màu loại đèn (theo bảng)")
        // Chữ ký bảng 3D (Car3dDotsLayer) giữ nguyên.
        assertTrue(painter.contains("tappable: Boolean = true,"))
        assertTrue(board.contains("LampTone.of(s.tile.cell?.id)?.argb"), "màu theo mã đọc (dữ liệu :core)")
    }

    @Test
    fun `net icon dat 3 tren 1 tren anh xe o bang toi va sang`() {
        listOf(KachiPalette.DARK to true, KachiPalette.LIGHT to false).forEach { (p, night) ->
            val k = StateDotPainter.Inks.of(p, night)
            listOf(MEDIAN_WORST, P95_WORST, PEAK).forEach { l ->
                val photo = ColorMath.grayOfLuminance(l)
                val shade = ColorMath.over(k.lampEdge, photo)
                val face = ColorMath.over(k.tonal, ColorMath.over(k.shadow, photo))
                val checks = LampTone.Tone.entries.associate { "đèn báo ${it.name}" to ColorMath.ratio(k.lampTone(it.argb), shade) } + mapOf(
                    "đèn báo tắt" to ColorMath.ratio(ColorMath.over(k.lampOff, shade), shade),
                    "đèn báo chưa biết" to ColorMath.ratio(ColorMath.over(k.lampUnknown, shade), shade),
                    "đèn báo nhấn (không màu loại)" to ColorMath.ratio(k.lampTone(null), shade),
                    "nút tắt" to ColorMath.ratio(ColorMath.over(k.closed, face), face),
                    "nút bật" to ColorMath.ratio(k.accent, face),
                    "nút chưa biết" to ColorMath.ratio(ColorMath.over(k.unknown, face), face),
                )
                checks.forEach { (what, r) -> assertTrue(r >= 3.0, "${if (night) "Tối" else "Sáng"} ảnh L=$l: $what = ${"%.2f".format(r)} < 3:1") }
            }
        }
    }

    @Test
    fun `xi-nhan nhay tai cho chi khi bat va dang hien, khong vong luc nghi`() {
        val blink = SourceRoots.body(board, "private fun blinkKind(")
        assertTrue(blink.contains("LampTone.blinks(t.cell?.id)") && blink.contains("blinkSince.remove(t)"), "tắt/chưa biết ⇒ xoá mốc ngay")
        val sched = SourceRoots.body(board, "private fun scheduleBlink(")
        assertTrue(sched.contains("blinkSince.isEmpty()") && sched.contains("isShown") && sched.contains("windowVisibility != View.VISIBLE"))
        assertTrue(sched.contains("postInvalidateDelayed(") && !sched.contains("postInvalidateOnAnimation"), "hẹn đúng mép pha, không mỗi khung")
        assertTrue(sched.contains("if (at == blinkAt) return"), "không chồng hẹn")
        assertTrue(board.contains("blinkSince.clear()"), "tách khỏi cửa sổ ⇒ dừng")
        assertFalse(board.contains("ValueAnimator") || board.contains("ObjectAnimator"), "không bộ hoạt hoạ chạy nền")
    }

    @Test
    fun `so gia vehicleTest bat duoc xi-nhan`() {
        val dummy = SourceRoots.codeOf("src/vehicleTest/java/com/byd/clusternav/DummyCarReceiver.kt")
        assertTrue(dummy.contains("getBooleanExtra(\"turn_l\"") && dummy.contains("getBooleanExtra(\"turn_r\""))
        // 3.01 TURN (owner 13/10 "làm luôn"): xi-nhan giả đi qua hộp socket `LampLive` — đúng dây xi-nhan thật trên xe.
        assertTrue(dummy.contains("LampLive.shared.publish(turnL, turnR)"))
    }

    private companion object {
        const val MEDIAN_WORST = 0.0395
        const val P95_WORST = 0.4695
        const val PEAK = 0.598
    }
}
