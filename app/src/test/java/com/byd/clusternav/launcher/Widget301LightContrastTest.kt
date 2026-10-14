package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.min

/**
 * 3.1.x LIGHT-CONTRAST — owner 14/10: xe chạy chủ đề SÁNG ban ngày (`chủ đề=sáng`), icon/nút 3.1 "mờ", sai màu.
 * [ĐO máy ảo trước khi sửa, diagnostics `widget-visual-301` §10] bảng SÁNG: đĩa nút chân/nền 1,16:1, glyph/đĩa 1,7–3,3:1; đèn báo
 * chỉ-xem trên thẻ sáng 1,4–2,6:1 (lớp phụ 0,38 của glyph hai tông). Bài này khoá số TÍNH từ chính bảng màu, cùng công thức WCAG:
 *  - nút tròn chỉ-icon ([KachiButtonInks]): glyph ≥ 4,5:1 trên đĩa, đĩa ≥ 3:1 trên nền SÁNG (L 0,30–1,0 — nền mặc định đo
 *    0,45–0,55, thẻ tới trắng tinh); BẬT khác TẮT ở mặt đĩa; mọi chỗ vẽ nút đi qua một đường;
 *  - đĩa/đèn báo của [StateDotPainter] ở bảng sáng: nét chính ≥ 4,5:1, lớp phụ 0,38 (sau nhân alpha) ≥ 3:1.
 */
class Widget301LightContrastTest {

    private val light = KachiPalette.LIGHT
    private fun c(hex: String) = ColorMath.parse(hex)

    /** Nền sáng tiêu biểu: ảnh/nền mặc định 0,30–0,60 (đo 0,45–0,55) tới thẻ trắng. */
    private val lightGrounds = listOf(0.30, 0.45, 0.55, 0.70, 1.0).map { ColorMath.grayOfLuminance(it) } +
        listOf(c(light.card), c(light.surfFrom), c(light.surfTo), c(light.bg))

    @Test
    fun `nut tron sang - glyph 4,5 tren dia, dia 3 tren nen sang, bat khac tat`() {
        val k = KachiButtonInks.of(light)
        assertTrue(KachiButtonInks.lightSurface(light) && !KachiButtonInks.lightSurface(KachiPalette.DARK))
        lightGrounds.forEach { g ->
            val rest = ColorMath.over(k.rest, g)
            val glyph = ColorMath.ratio(c(k.glyph), rest)
            val disc = ColorMath.ratio(rest, g)
            val on = ColorMath.over(k.on, g)
            val glyphOn = ColorMath.ratio(c(k.glyphOn), on)
            val l = "%.3f".format(ColorMath.luminance(g))
            println("LIGHT-BTN nền L=$l glyph/đĩa ${"%.2f".format(glyph)} đĩa/nền ${"%.2f".format(disc)} bật glyph/đĩa ${"%.2f".format(glyphOn)}")
            assertTrue(glyph >= 4.5, "nền L=$l: glyph/đĩa $glyph")
            assertTrue(disc >= 3.0, "nền L=$l: đĩa/nền $disc")
            assertTrue(glyphOn >= 4.5, "nền L=$l: glyph BẬT/đĩa BẬT $glyphOn")
            assertTrue(ColorMath.ratio(on, g) >= 3.0, "nền L=$l: đĩa BẬT/nền")
        }
        // BẬT khác TẮT ở MẶT đĩa: bật mang sắc nhấn (lam rõ), tắt là kính tối trung tính.
        val blue = { x: Int -> (x and 0xff) - ((x ushr 16) and 0xff) }
        assertTrue(blue(k.on) >= 60 && blue(k.rest) == 0, "đĩa BẬT phải lam rõ, TẮT trung tính: on=${Integer.toHexString(k.on)}")
        // Ảnh TỐI dưới nút ở bảng sáng: đĩa không tách được nền (chấp nhận) nhưng glyph vẫn phải đọc được.
        assertTrue(ColorMath.ratio(c(k.glyph), ColorMath.over(k.rest, ColorMath.grayOfLuminance(0.0))) >= 4.5)
    }

    @Test
    fun `moi cho ve nut tron chi-icon di qua KachiButtonInks, khong con ban chep tay`() {
        val dir = "src/main/java/com/byd/clusternav/launcher/"
        mapOf(
            "ActionMatrixCompact.kt" to "private fun barButton(",
            "Car3dTaps.kt" to "private fun button(",
            "Car3dFootRow.kt" to "private fun disc(",
        ).forEach { (f, fn) ->
            val code = SourceRoots.codeOf(dir + f)
            assertTrue(SourceRoots.body(code, fn).contains("KachiButtonInks.background()"), "$f: $fn")
            assertFalse(code.contains("REST_ALPHA = 0x14"), "$f: còn bản chép tay đĩa mực 8 %")
        }
        val foot = SourceRoots.codeOf(dir + "Car3dFootRow.kt")
        assertTrue(foot.contains("setTextColor(KachiButtonInks.glyphTint())"), "−/+ dùng glyph chung")
        assertTrue(SourceRoots.body(foot, "private fun drawRecirc(").contains("ink.glyphOn else ink.glyph"), "lấy gió dùng glyph chung")
    }

    /** Alpha thật của một nét [stroke] (alpha trong vector) tô mực [ink] với hệ số [boost] — cùng công thức bộ lọc ma trận. */
    private fun stroke(ink: Int, stroke: Double, boost: Float): Int =
        ColorMath.withAlpha(ink, (min(1.0, boost * (ink ushr 24) / 255.0 * stroke) * 255).toInt())

    @Test
    fun `dia nut va den bao bang sang - net chinh 4,5, lop phu 3`() {
        val k = StateDotPainter.Inks.of(light, night = false)
        assertEquals(StateDotPainter.LIGHT_GLYPH_BOOST, k.glyphBoost)
        assertEquals(1f, StateDotPainter.Inks.of(KachiPalette.DARK, night = true).glyphBoost, "bảng tối giữ setTint như cũ")
        // Ảnh xe (ba mức đo của CarDotWiringContractTest) + nền sáng của thẻ/nền mặc định.
        val grounds = listOf(0.0395, 0.4695, 0.598).map { ColorMath.grayOfLuminance(it) } + lightGrounds
        grounds.forEach { g ->
            val l = "%.3f".format(ColorMath.luminance(g))
            val face = ColorMath.over(k.tonal, ColorMath.over(k.shadow, g))
            listOf("tắt" to k.closed, "bật" to k.accent, "chưa biết" to k.unknown).forEach { (what, ink) ->
                val main = ColorMath.ratio(ColorMath.over(stroke(ink, 1.0, k.glyphBoost), face), face)
                val minor = ColorMath.ratio(ColorMath.over(stroke(ink, SECONDARY, k.glyphBoost), face), face)
                assertTrue(main >= 4.5, "nút $what nền L=$l: nét chính $main")
                assertTrue(minor >= 3.0, "nút $what nền L=$l: lớp phụ $minor")
            }
            val edge = ColorMath.over(k.lampEdge, g)
            val lamps = LampTone.Tone.entries.map { it.name to k.lampTone(it.argb) } +
                listOf("tắt" to k.lampOff, "chưa biết" to k.lampUnknown, "không màu loại" to k.lampTone(null))
            lamps.forEach { (what, ink) ->
                val main = ColorMath.ratio(ColorMath.over(stroke(ink, 1.0, k.glyphBoost), edge), edge)
                val minor = ColorMath.ratio(ColorMath.over(stroke(ink, SECONDARY, k.glyphBoost), edge), edge)
                assertTrue(main >= 4.5, "đèn báo $what nền L=$l: nét chính $main")
                assertTrue(minor >= 3.0, "đèn báo $what nền L=$l: lớp phụ $minor")
            }
        }
        // Đèn báo TẮT vs BẬT (bảng sáng): khác MÀU — tắt là mực trung tính, bật là màu loại.
        assertTrue(LampTone.Tone.entries.all { k.lampTone(it.argb) != k.lampOff })
    }

    /**
     * Soát KachiDialog (3.1.1 IA): mọi chữ/glyph trên mặt hộp (`surfFrom`→`surfTo`) và trên nút của nó ≥ 4,5:1 ở CẢ hai bảng; ô
     * chọn TẮT (`fieldSunken`) phải tách khỏi mặt hộp ≥ 3:1 ở bảng sáng (ô rỗng — chỉ mặt ô nói "đây là ô chọn").
     */
    @Test
    fun `hop thoai - chu va nut dat 4,5, o chon tat tach mat hop`() {
        listOf("TỐI" to KachiPalette.DARK, "SÁNG" to light).forEach { (name, p) ->
            val faces = listOf(c(p.surfFrom), c(p.surfTo))
            val ctl = c(dialogControlFill(p))
            faces.forEach { f ->
                val control = ColorMath.over(ctl, f)
                val pairs = mapOf(
                    "INK/mặt" to ColorMath.ratio(c(p.ink), f), "MUT/mặt" to ColorMath.ratio(c(p.mut), f),
                    "ACCENT_INK/mặt" to ColorMath.ratio(c(p.accentInk), f),
                    "INK/nút phụ" to ColorMath.ratio(c(p.ink), control),
                    "RED/nút nguy hiểm" to ColorMath.ratio(c(p.red), ColorMath.over(c(p.redSoft), f)),
                    "ON_ACCENT/nút chính" to minOf(ColorMath.ratio(c(p.onAccent), c(p.gradFrom)), ColorMath.ratio(c(p.onAccent), c(p.gradTo))),
                )
                println("DIALOG $name " + pairs.entries.joinToString { "${it.key} ${"%.2f".format(it.value)}" } +
                    " · ô chọn tắt/mặt ${"%.2f".format(ColorMath.ratio(ColorMath.over(c(p.fieldSunken), f), f))}")
                pairs.forEach { (what, r) -> assertTrue(r >= 4.5, "$name $what $r") }
                val box = ColorMath.ratio(ColorMath.over(c(dialogBoxOff(p)), f), f)
                println("DIALOG $name ô chọn tắt (sau) /mặt ${"%.2f".format(box)}")
                assertTrue(box >= 3.0, "$name ô chọn TẮT/mặt hộp $box")
            }
        }
    }

    private companion object {
        /** Lớp phụ của glyph hai tông (`gen-car.py`: PHỤ 0.38). */
        const val SECONDARY = 0.38
    }
}
