package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar
import kotlin.math.abs

/**
 * ═══ 2.88 · R-OP — ÁP ĐÚNG SỐ NGƯỜI CHỌN, ở mọi nền (thay bài quét sàn đọc được `ChromeOpacityContrastContractTest`) ══
 *
 * Owner 04/10: *"Cho trong suốt lên 100% luôn, tùy user chọn, có thay kéo từ 0-100%"*. 2.87 giữ nền đục hơn bậc người
 * chọn ở chỗ nền phía sau làm chữ hụt 4.5:1 (bài quét cũ chấm "0 ca hụt ở chỗ hôm nay đạt"); trên ảnh sáng thanh kéo vì
 * thế nói một đằng, mắt thấy một nẻo. 2.88 bỏ sàn: độ đục được VẼ = gốc × hệ số, ở mọi bảng màu, mọi ảnh. Bài này khoá
 * ba điều thay cho bài quét cũ, đi qua ĐÚNG đường sản phẩm (`ThemeHost.sync` → [KachiChrome]) với vai màu thật của bảng:
 *  1. vị trí 0 (trong suốt 0 %) = hôm nay TỪNG BYTE, mọi vai nền mờ;
 *  2. mọi vị trí: alpha vẽ ra = `gốc × f` (sai số làm tròn `modulateAlpha` ≤ 2/255), không đục hơn gốc, đơn điệu, vị trí
 *     cuối = 0 — và hệ số KHÔNG phụ thuộc bảng màu/ảnh nền (không còn "mức cần để đọc" nào kéo lên);
 *  3. lớp kính trên ảnh: lớp che là đúng lớp che hôm nay (bộ giải chữ trên bề mặt 100 %) × f, không thêm sàn theo vùng ảnh.
 * Đọc được ở mức trong cao trên ảnh sáng là lựa chọn của người dùng — ghi chú dưới thanh kéo nói thẳng điều đó.
 * `Drawable`/`Paint` không dựng được ngoài thiết bị ⇒ phép NHÂN là [ChromeOpacity.drawnAlpha] (phép `modulateAlpha` r47),
 * còn việc năm chỗ dựng gọi đúng phép đó do `KachiChromeContractTest` ghim nguồn.
 */
class ChromeOpacityExactContractTest {

    @AfterEach
    fun reset() {
        KachiTheme.applyTheme(ThemeMode.NIGHT, 12, ColorChoice.DEFAULT, null)
        KachiChrome.apply(ChromeOpacity.DEFAULT)
    }

    private val noon: Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12) }

    /** Mọi bảng màu: hai chủ đề × 8 màu nhấn × 3 tông + vài bảng theo ảnh nền (FROM_ART). */
    private fun forEachChoice(block: (String, ThemeMode, ColorChoice, IntArray?) -> Unit) {
        listOf(ThemeMode.NIGHT, ThemeMode.DAY).forEach { mode ->
            AccentChoice.values().forEach { a ->
                CardTone.values().forEach { t ->
                    if (a == AccentChoice.FROM_ART) {
                        listOf(0xFF6E6E92.toInt(), 0xFFE6C84A.toInt(), 0xFF0B3A40.toInt()).forEach { dom ->
                            block("$mode·ẢNH#${"%06X".format(dom and 0xFFFFFF)}·$t", mode, ColorChoice(a, t), intArrayOf(dom))
                        }
                    } else {
                        block("$mode·$a·$t", mode, ColorChoice(a, t), null)
                    }
                }
            }
        }
    }

    /** Áp vị trí thanh kéo [pos] qua ĐÚNG đường sản phẩm; trả hệ số mà năm chỗ dựng sẽ đọc. */
    private fun syncAt(mode: ThemeMode, choice: ColorChoice, art: IntArray?, pos: Int): Double {
        ThemeHost.sync(HomeUiState(themeMode = mode, colorChoice = choice.copy(surfaceOpacity = ChromeOpacity.ofPosition(pos))), noon, art)
        return KachiChrome.fraction
    }

    /**
     * Vai NỀN mờ thật của màn chính (đọc thẳng từ bảng đang áp) — thanh trên (cũng là đầu đậm của hai dải che hình nền,
     * `WallView`) · thanh nút · thẻ · khay · bề mặt kính 80 % · thẻ bánh thường của bảng lốp (`card2`, soát Pass 10).
     */
    private fun fadedRoles(p: KachiPalette): Map<String, Int> = mapOf(
        "barTop" to p.barTop, "bar" to p.bar, "surfFrom" to p.surfFrom, "surfTo" to p.surfTo,
        "slot" to p.slot, "slotTo" to p.slotTo, "surfFromOverArt" to p.surfFromOverArt, "surfToOverArt" to p.surfToOverArt,
        "card2" to p.card2,
    ).mapValues { ColorMath.parse(it.value) }

    /** Khoá (2): hệ số = đúng số trên thanh kéo, giống hệt nhau ở MỌI bảng màu (không sàn theo nền). */
    @Test
    fun `he so ve ra dung so tren thanh keo o moi bang mau`() {
        var n = 0
        forEachChoice { name, mode, choice, art ->
            (0..ChromeOpacity.POSITIONS).forEach { pos ->
                val f = syncAt(mode, choice, art, pos)
                assertEquals(1.0 - pos * ChromeOpacity.STEP / 100.0, f, 1e-12, "$name vị trí $pos")
                n++
            }
        }
        assertTrue(n >= 2 * (8 * 3 + 3 * 3) * 21, "quét thiếu ca: $n")
    }

    /** Khoá (1) + (2) trên vai màu THẬT: vị trí 0 từng byte; còn lại gốc × f, ≤ gốc, đơn điệu, cuối = 0. */
    @Test
    fun `nen mo cua man chinh ve dung goc nhan he so`() {
        var n = 0
        forEachChoice { name, mode, choice, art ->
            syncAt(mode, choice, art, 0)
            val roles = fadedRoles(KachiTheme.palette)
            val prev = HashMap<String, Int>()
            (0..ChromeOpacity.POSITIONS).forEach { pos ->
                val f = syncAt(mode, choice, art, pos)
                roles.forEach { (role, color) ->
                    val base = ColorMath.alpha(color)
                    val drawn = ChromeOpacity.drawnAlpha(base, f)
                    val tag = "$name $role vị trí $pos"
                    if (pos == 0) assertEquals(base, drawn, "$tag: 0 % trong suốt phải đúng byte hôm nay")
                    assertTrue(drawn <= base, "$tag: đục hơn gốc ($drawn > $base)")
                    assertTrue(abs(drawn - base * f) <= 2.0, "$tag: lệch gốc×f quá 2/255 ($drawn vs ${base * f})")
                    assertTrue(drawn <= (prev[role] ?: Int.MAX_VALUE), "$tag: kéo thêm mà nền đục lại")
                    if (pos == ChromeOpacity.POSITIONS) assertEquals(0, drawn, "$tag: 100 % trong suốt ⇒ không tô gì")
                    prev[role] = drawn
                    n++
                }
            }
        }
        assertTrue(n >= 2 * (8 * 3 + 3 * 3) * 21 * 9, "quét thiếu ca: $n")
    }

    /**
     * Khoá (3): thẻ/khay KÍNH trên ảnh — lớp che = lớp che HÔM NAY (bộ giải chữ trên bề mặt 100 %, sàn/trần mặc định) × f,
     * ở mọi độ chói ảnh. Ảnh sáng KHÔNG còn kéo lớp che lên (2.87: tới 100 % đục). Vị trí 0 = byte 2.86.
     */
    @Test
    fun `lop che kinh la lop che hom nay nhan he so, khong san theo anh`() {
        val lums = (0..20).map { it * 0.05 }
        var n = 0
        listOf(ThemeMode.NIGHT, ThemeMode.DAY).forEach { mode ->
            syncAt(mode, ColorChoice.DEFAULT, null, 0)
            val p = KachiTheme.palette
            val bg = ColorMath.parse(p.bg)
            val inks = intArrayOf(ColorMath.parse(p.mut), ColorMath.parse(p.mut2), ColorMath.parse(p.ink))   // = KachiGlass.veilInks(NEUTRAL/WELL)
            val pairs = mapOf(                                                                               // = KachiTheme.surfacePair(tone, true)
                "NEUTRAL" to intArrayOf(ColorMath.parse(p.surfFromOverArt), ColorMath.parse(p.surfToOverArt)),
                "WELL" to intArrayOf(ColorMath.scaleAlpha(ColorMath.parse(p.slot), 0.8), ColorMath.scaleAlpha(ColorMath.parse(p.slotTo), 0.8)),
            )
            pairs.forEach { (tone, pair) ->
                lums.forEach { l ->
                    val todayByte = (GlassVeil.alphaFor(l, bg, pair, inks) * 255).toInt()   // 2.86/2.87 ở 100 %
                    var prev = Int.MAX_VALUE
                    (0..ChromeOpacity.POSITIONS).forEach { pos ->
                        val f = syncAt(mode, ColorChoice.DEFAULT, null, pos)
                        val drawn = ChromeOpacity.drawnAlpha(todayByte, f)
                        val tag = "$mode $tone l=${"%.2f".format(l)} vị trí $pos"
                        if (pos == 0) assertEquals(todayByte, drawn, tag)
                        assertTrue(drawn <= todayByte && drawn <= prev, "$tag: lớp che đục hơn hôm nay / không đơn điệu")
                        assertTrue(abs(drawn - todayByte * f) <= 2.0, "$tag: lớp che không đúng hôm nay × f")
                        prev = drawn
                        n++
                    }
                    assertEquals(0, prev, "$mode $tone l=$l: 100 % trong suốt ⇒ lớp che 0")
                }
            }
        }
        assertEquals(2 * 2 * 21 * 21, n)
    }
}
