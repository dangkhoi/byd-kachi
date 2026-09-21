package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import com.byd.clusternav.testsupport.Wcag.fmt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar

/**
 * ═══ VISUAL-REFRESH P1b · R8 — BÀI CANH CHỌN MÀU: mọi lựa chọn × mọi tông × hai bảng ═══════════════════════════
 *
 * AC8.5: *"với mỗi lựa chọn, test sinh bảng tương phản mực/nền; màu nào kéo chữ xuống < 4,5:1 thì app tự đậm hoá
 * nền hoặc đổi mực"*. Bài này chạy **cùng phép suy** mà `KachiTheme.applyTheme` dùng ([derive]) trên **mọi** tổ hợp
 * (9 màu nhấn × 3 tông × 2 bảng, ô *theo ảnh nền* thử với 4 màu trội cực đoan) và đo **sau khi đã tự chỉnh**; bảng
 * ghi ra `docs/diagnostics/visual-refresh-2026-09-16/contrast-table-colors.md` (sinh bằng máy — AC6.6).
 */
class ColorChoiceContractTest {

    /** Bốn ảnh nền "khó" cho ô *theo ảnh nền*: trắng tinh · đen tuyền · đỏ rực · xám không sắc. */
    private val artSeeds = listOf(
        "trắng" to intArrayOf(ColorMath.parse("#ffffff")),
        "đen" to intArrayOf(ColorMath.parse("#000000")),
        "đỏ rực" to intArrayOf(ColorMath.parse("#ff2020")),
        "xám" to intArrayOf(ColorMath.parse("#808080")),
    )

    private fun variants(): List<Triple<String, KachiPalette, KachiPalette>> {
        val out = ArrayList<Triple<String, KachiPalette, KachiPalette>>()
        listOf("TỐI" to KachiPalette.DARK, "SÁNG" to KachiPalette.LIGHT).forEach { (name, base) ->
            val dark = base === KachiPalette.DARK
            AccentChoice.values().forEach { a ->
                CardTone.values().forEach { t ->
                    if (a == AccentChoice.FROM_ART) artSeeds.forEach { (why, seed) ->
                        out += Triple("$name · $a($why) · $t", base, base.derive(ColorChoice(a, t), seed, dark))
                    } else out += Triple("$name · $a · $t", base, base.derive(ColorChoice(a, t), null, dark))
                }
            }
        }
        return out
    }

    /** Các cặp chữ/nền mà mọi lựa chọn phải giữ ≥ 4.5:1 — cùng danh sách với `ThemePaletteContractTest.textOn` + P1. */
    private fun pairs(p: KachiPalette): List<Triple<String, Int, Int>> {
        fun c(s: String) = ColorMath.parse(s)
        val bg = c(p.bg); val tile = c(p.tile)
        val out = ArrayList<Triple<String, Int, Int>>()
        out += Triple("ON_ACCENT trên gradFrom", c(p.onAccent), c(p.gradFrom))
        out += Triple("ON_ACCENT trên gradTo", c(p.onAccent), c(p.gradTo))
        out += Triple("INK_ON_ACCENT trên tileOnFrom+tile", c(p.inkOnAccent), ColorMath.over(c(p.tileOnFrom), tile))
        out += Triple("INK_ON_ACCENT trên tileOnTo+tile", c(p.inkOnAccent), ColorMath.over(c(p.tileOnTo), tile))
        out += Triple("INK trên surfOnFrom+bg", c(p.ink), ColorMath.over(c(p.surfOnFrom), bg))
        out += Triple("INK trên surfOnTo+bg", c(p.ink), ColorMath.over(c(p.surfOnTo), bg))
        listOf("tile" to tile, "card" to c(p.card), "surfFrom" to c(p.surfFrom), "surfTo" to c(p.surfTo)).forEach { (n, g) ->
            out += Triple("ACCENT_INK trên $n", c(p.accentInk), g)
        }
        // Tông thẻ dịch các bề mặt ⇒ mọi mực phải còn đọc được trên chúng (cả hai đầu gradient).
        val inks = listOf("ink" to p.ink, "ink2" to p.ink2, "mut" to p.mut, "mut2" to p.mut2, "icon" to p.icon, "accentInk" to p.accentInk)
        listOf("surfFrom" to p.surfFrom, "surfTo" to p.surfTo, "slot" to p.slot, "slotTo" to p.slotTo, "tile" to p.tile,
            "cell" to p.cell, "card" to p.card, "fieldSunken" to p.fieldSunken).forEach { (gn, g) ->
            inks.forEach { (inName, ink) -> out += Triple("$inName trên $gn", c(ink), ColorMath.over(c(g), bg)) }
        }
        return out
    }

    @Test
    fun `mac dinh la chinh bang goc — khong lech mot byte`() {
        assertSame(KachiPalette.DARK, KachiPalette.DARK.derive(ColorChoice.DEFAULT, null, true))
        assertSame(KachiPalette.LIGHT, KachiPalette.LIGHT.derive(ColorChoice.DEFAULT, null, false))
        // Và *theo ảnh nền* khi CHƯA có ảnh cũng là bảng gốc (AC8.1: ô đó không được làm màn trắng tay).
        assertSame(KachiPalette.DARK, KachiPalette.DARK.derive(ColorChoice(AccentChoice.FROM_ART), null, true))
    }

    @Test
    fun `moi lua chon giu moi cap chu nen tren san 4_5`() {
        val bad = mutableListOf<String>()
        variants().forEach { (name, _, p) ->
            pairs(p).forEach { (pair, ink, ground) ->
                val r = ColorMath.ratio(ink, ground)
                if (r < 4.5) bad += "$name: $pair = ${fmt(r)}"
            }
        }
        assertEquals(emptyList<String>(), bad, "AC8.5 hỏng — lựa chọn màu kéo chữ xuống dưới sàn: $bad")
    }

    @Test
    fun `mau nhan khac nhau thi nhin ra khac nhau`() {
        listOf(true, false).forEach { dark ->
            val base = if (dark) KachiPalette.DARK else KachiPalette.LIGHT
            val accents = AccentChoice.values().filter { it != AccentChoice.FROM_ART }
                .map { it to ColorMath.parse(base.derive(ColorChoice(it), null, dark).accent) }
            accents.forEach { (a, c) ->
                accents.forEach { (b, d) ->
                    if (a != b) {
                        val far = ColorMath.hueDistance(c, d) >= 20.0 || kotlin.math.abs(ColorMath.hsl(c)[1] - ColorMath.hsl(d)[1]) >= 0.3
                        assertTrue(far, "${if (dark) "TỐI" else "SÁNG"}: $a và $b cho màu nhấn gần như nhau (${ColorMath.hex(c)} / ${ColorMath.hex(d)})")
                    }
                }
            }
        }
    }

    @Test
    fun `tong the thuc su dich be mat, va van tren san`() {
        listOf(KachiPalette.DARK to true, KachiPalette.LIGHT to false).forEach { (base, dark) ->
            val warm = base.derive(ColorChoice(tone = CardTone.WARM), null, dark)
            val cool = base.derive(ColorChoice(tone = CardTone.COOL), null, dark)
            assertTrue(warm.surfFrom != base.surfFrom && cool.surfFrom != base.surfFrom, "tông phải đổi surfFrom")
            assertTrue(warm.surfFrom != cool.surfFrom, "ấm và lạnh phải khác nhau")
            // Ấm ⇒ kênh đỏ tăng so với lam; lạnh ⇒ ngược lại.
            val w = ColorMath.parse(warm.surfFrom); val c = ColorMath.parse(cool.surfFrom)
            assertTrue(ColorMath.red(w) - ColorMath.blue(w) > ColorMath.red(c) - ColorMath.blue(c))
            assertEquals(base.accent, warm.accent, "tông thẻ không được chạm màu nhấn")
        }
    }

    @Test
    fun `applyTheme nhan lua chon va chi doi bang khi dau vao doi`() {
        val night = ThemeMode.NIGHT
        KachiTheme.applyTheme(night, 12, ColorChoice.DEFAULT, null)
        assertSame(KachiPalette.DARK, KachiTheme.palette)
        assertTrue(KachiTheme.applyTheme(night, 12, ColorChoice(AccentChoice.TEAL), null), "đổi màu nhấn ⇒ bảng đổi")
        val teal = KachiTheme.palette
        assertTrue(!KachiTheme.applyTheme(night, 12, ColorChoice(AccentChoice.TEAL), null), "cùng đầu vào ⇒ không đổi")
        assertSame(teal, KachiTheme.palette, "cùng đầu vào ⇒ cùng thực thể (không suy lại mỗi nhịp)")
        // Màu trội của ảnh chỉ có nghĩa với ô THEO ẢNH NỀN — đổi ảnh khi đang chọn TEAL không được dựng lại màn.
        assertTrue(!KachiTheme.applyTheme(night, 12, ColorChoice(AccentChoice.TEAL), intArrayOf(ColorMath.parse("#ff2020"))))
        assertTrue(KachiTheme.applyTheme(night, 12, ColorChoice(AccentChoice.FROM_ART), intArrayOf(ColorMath.parse("#ff2020"))))
        assertTrue(KachiTheme.applyTheme(night, 12, ColorChoice.DEFAULT, null), "về mặc định ⇒ đổi lại")
        assertSame(KachiPalette.DARK, KachiTheme.palette)
        // ThemeHost đưa cả colorChoice vào — cùng một chỗ áp.
        val state = HomeUiState(themeMode = night, colorChoice = ColorChoice(AccentChoice.AMBER))
        assertTrue(ThemeHost.sync(state, Calendar.getInstance(), null))
        assertTrue(!ThemeHost.sync(state, Calendar.getInstance(), null))
        KachiTheme.applyTheme(night, 12, ColorChoice.DEFAULT, null)
    }

    /**
     * §4.10 mục (5) trên **mọi lựa chọn màu**, không chỉ bảng gốc.
     *
     * ⚠ [SOÁT 2026-09-17] `WallGlassContractTest.lop che du cho moi do choi…` chỉ chạy với [ColorChoice.DEFAULT],
     * nhưng `CardTone` nhuộm đúng bốn vai mà lớp che đo (`surf*OverArt` · `slot`/`slotTo`) và màu nhấn đổi
     * `surfOn*`/`ink` ⇒ sàn 4.5 của thẻ kính chưa được chứng minh cho 54 bảng còn lại. Bài này đóng lỗ đó bằng
     * CHÍNH hợp đồng mực của `KachiGlass.veilInks` + cặp bề mặt của `KachiTheme.surfacePair(…, overArtwork = true)`.
     */
    @Test
    fun `lop che kinh du cho moi lua chon mau, moi do choi anh`() {
        val bad = mutableListOf<String>()
        variants().forEach { (name, _, p) ->
            val veil = ColorMath.parse(p.bg)
            fun inksOf(tone: SurfaceTone) = if (tone == SurfaceTone.ACTIVE) intArrayOf(ColorMath.parse(p.ink))
            else intArrayOf(ColorMath.parse(p.mut), ColorMath.parse(p.mut2), ColorMath.parse(p.ink))
            val pairs = mapOf(
                SurfaceTone.NEUTRAL to intArrayOf(ColorMath.parse(p.surfFromOverArt), ColorMath.parse(p.surfToOverArt)),
                SurfaceTone.WELL to intArrayOf(ColorMath.scaleAlpha(ColorMath.parse(p.slot), 0.8), ColorMath.scaleAlpha(ColorMath.parse(p.slotTo), 0.8)),
                SurfaceTone.ACTIVE to intArrayOf(ColorMath.scaleAlpha(ColorMath.parse(p.surfOnFrom), 0.8), ColorMath.scaleAlpha(ColorMath.parse(p.surfOnTo), 0.8)),
            )
            pairs.forEach { (tone, surfaces) ->
                val inks = inksOf(tone)
                var l = 0.0
                while (l <= 1.0) {
                    val a = GlassVeil.alphaFor(l, veil, surfaces, inks)
                    val worst = GlassVeil.worstRatio(a, ColorMath.grayOfLuminance(l), veil, surfaces, inks)
                    if (worst < 4.5) bad += "$name $tone l=${"%.2f".format(l)} alpha=${"%.2f".format(a)} worst=${"%.2f".format(worst)}"
                    l += 0.05
                }
            }
        }
        assertEquals(emptyList<String>(), bad, "lớp che thẻ kính tới trần vẫn không đủ: $bad")
    }

    /** Dây nối một chiều: chip/ô ở Cài đặt → intent → ViewModel → persist/load → ThemeHost. Thiếu một mắt là nút chết. */
    @Test
    fun `chon mau noi day mot chieu tu Cai dat toi bang mau`() {
        val sections = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt")
        assertTrue("rows.swatchRow(" in sections && "deps.onColorChoice(" in sections, "Cài đặt phải có hàng ô màu + intent")
        assertTrue("CardTone.values()" in sections, "Cài đặt phải có hàng tông thẻ")
        val wiring = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt")
        assertTrue("viewModel.setColorChoice(" in wiring, "intent phải tới ViewModel")
        val vm = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt")
        assertTrue("fun setColorChoice(" in vm && "copy(colorChoice = " in vm)
        val repo = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt")
        assertTrue("prefs.setColorChoice(state.colorChoice)" in repo, "persist phải ghi màu")
        assertTrue("colorChoice = prefs.colorChoice()" in repo, "load phải nạp màu (đổi hồ sơ ⇒ đổi màu, AC8.4)")
        val host = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ThemeHost.kt")
        assertTrue("state.colorChoice" in host, "ThemeHost là người đọc-để-vẽ duy nhất")
    }

    /** Sinh bảng đo cho tài liệu — cùng lý do `SurfaceContrastContractTest.sinh bang do tuong phan cua tai lieu`. */
    @Test
    fun `sinh bang do tuong phan cho moi lua chon mau`() {
        val out = StringBuilder()
        out.append("# Bảng đo tương phản — P1b · R8 chọn màu (AC8.5)\n\n")
        out.append("> SINH BẰNG MÁY từ `KachiPalette` + `KachiPaletteDerive` bởi `ColorChoiceContractTest`. **Không sửa tay.**\n")
        out.append("> Mỗi dòng: mực TỆ NHẤT trong mọi cặp chữ/nền của lựa chọn đó, SAU khi `ContrastGuard` đã tự chỉnh.\n\n")
        out.append("| Bảng · màu nhấn · tông | accent | gradFrom→To | ON_ACCENT | Cặp tệ nhất | Đo được | Kết |\n|---|---|---|---|---|---|---|\n")
        variants().forEach { (name, _, p) ->
            val worst = pairs(p).minByOrNull { ColorMath.ratio(it.second, it.third) }!!
            val r = ColorMath.ratio(worst.second, worst.third)
            out.append("| $name | `${p.accent}` | `${p.gradFrom}`→`${p.gradTo}` | `${p.onAccent}` | ${worst.first} | **${fmt(r)}** | ${if (r >= 4.5) "✅" else "❌"} |\n")
        }
        val root = generateSequence(
            SourceRoots.path("src/main/java/com/byd/clusternav/launcher/KachiPalette.kt").toAbsolutePath().toFile(),
        ) { it.parentFile }.firstOrNull { java.io.File(it, "docs/diagnostics").isDirectory }
            ?: error("không tìm thấy gốc kho")
        val target = java.io.File(root, "docs/diagnostics/visual-refresh-2026-09-16/contrast-table-colors.md")
        target.parentFile?.mkdirs()
        target.writeText(out.toString())
        assertTrue(target.length() > 0)
    }
}
