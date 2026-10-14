package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import com.byd.clusternav.testsupport.Wcag.argb
import com.byd.clusternav.testsupport.Wcag.fmt
import com.byd.clusternav.testsupport.Wcag.over
import com.byd.clusternav.testsupport.Wcag.ratio
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.99 · R2 — NGÀY TRÊN THANH TRÊN ĐỌC ĐƯỢC NHƯ CHIP (spec `docs/specs/kachi-299-plan.html`) ═══════════════════════════
 *
 * Owner 10/10: *"cái ngày tháng năm trên header dùng màu gì khác với mấy chip … khi trùng màu nền nó khó nhìn lắm, chìm hẳn"*.
 * [ĐO mã] 2.98: ngày = `KachiTheme.MUT` + CAPTION, chip trung tính = `chipInk(NEUTRAL)` = `INK2` + BODY. Trên thanh `BAR_TOP` đã
 * trộn (độ đục R-OP `KachiChrome.fade`) MUT kém INK2 khoảng 1,5×: vd bảng TỐI trên ảnh nền xám giữa ở 100 % — MUT 4,19 · INK2 6,21;
 * 80 % — 3,47 · 5,15; bảng SÁNG trên ảnh nền đen ở 80 % — 3,64 · 6,71 (bảng đầy đủ: [dump] — chạy tay khi cần số).
 *
 * Luật khoá ở đây: (1) ngày dùng CHÍNH biểu thức mực + cỡ của chip trung tính (đổi màu chip ⇒ ngày đổi theo, không lệch lại);
 * (2) trên nền chính của bảng, mọi nấc độ đục 0–100 % ⇒ ≥ 4,5 : 1.
 *
 * Giới hạn [ĐO bảng màu] — ghi ra, KHÔNG giấu: bảng TỐI + ảnh nền gần trắng thì thanh `#99141b30` (60 % đục) chỉ còn `#727682`
 * ⇒ INK2 2,86 : 1 ngay ở 100 % (chip cũng vậy). Ngày nay ngang chip, nhưng ca đó cần lớp che / mực theo độ chói ảnh — việc
 * riêng, owner quyết (backlog).
 */
class HeaderDateContrastContractTest {

    private val strip = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt")
    private val chipInk = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/TopStripChipInk.kt")

    @Test
    fun `ngay dung CHINH muc va co cua chip trung tinh`() {
        val date = strip.lines().single { it.trimStart().startsWith("dateText = TextView(activity)") }
        // 3.1.5 R29 T29-7: vai mực chip trung tính, tô qua bộ giải mực thích ứng (AdaptiveContrastContractTest).
        assertTrue(date.contains("AdaptiveInk.text(this, chipInk(ChipTone.NEUTRAL))"), "ngày phải dùng mực chip trung tính: $date")
        assertTrue(date.contains("KachiType.apply(this, KachiType.BODY)"), "ngày cùng cỡ chữ chip: $date")
        assertFalse(Regex("KachiTheme\\.MUT2?\\b").containsMatchIn(date), "MUT/MUT2 là mực chìm — không dùng cho ngày")
        assertTrue(date.contains("themed {"), "đổi Sáng/Tối tại chỗ phải tô lại (QA 2.87 [P2])")
        // Chip trung tính dựng cùng cỡ BODY, và vai NEUTRAL là INK2 (đo bên dưới).
        assertTrue(SourceRoots.body(strip, "private fun chip(").contains("KachiType.apply(this, KachiType.BODY)"))
        assertTrue(chipInk.contains("ChipTone.NEUTRAL -> KachiTheme.INK2"), "vai mực của chip trung tính đổi ⇒ đo lại bảng dưới")
    }

    @Test
    fun `ngay tren thanh tren nen chinh cua bang dat 4_5 o moi nac do duc`() {
        val bad = mutableListOf<String>()
        palettes.forEach { (n, p) ->
            (0..100 step 5).forEach { pct ->
                val bar = over(faded(p.barTop, pct / 100.0), p.bg)
                val r = ratio(p.ink2, bar)
                if (r < 4.5) bad += "$n $pct% ${fmt(r)}"
            }
        }
        assertEquals(emptyList<String>(), bad, "ngày (INK2) trên thanh dưới 4.5:1: $bad")
    }

    /** Ngày mới không bao giờ tệ hơn MUT cũ ở nơi thanh còn đục (≥ 60 %) trên ba nền đại diện: trắng · xám giữa · đen. */
    @Test
    fun `ngay moi khong kem MUT cu khi thanh con duc`() {
        val bad = mutableListOf<String>()
        palettes.forEach { (n, p) ->
            listOf("#ffffff", "#808080", "#000000").forEach { wall ->
                listOf(1.0, 0.8, 0.6).forEach { f ->
                    val bar = over(faded(p.barTop, f), wall)
                    if (ratio(p.ink2, bar) < ratio(p.mut, bar)) bad += "$n $wall f=$f"
                }
            }
        }
        assertEquals(emptyList<String>(), bad)
    }

    /** In bảng số đo (không khẳng định) — để cập nhật KDoc/spec khi bảng màu đổi. */
    @Test
    fun dump() {
        palettes.forEach { (n, p) ->
            listOf("#ffffff", "#808080", "#000000", p.bg).forEach { wall ->
                listOf(1.0, 0.8, 0.6, 0.4, 0.2, 0.0).forEach { f ->
                    val bar = over(faded(p.barTop, f), wall)
                    println("HEADER-DATE $n wall=$wall f=$f bar=$bar ink2=${fmt(ratio(p.ink2, bar))} mut=${fmt(ratio(p.mut, bar))}")
                }
            }
        }
    }

    private val palettes = listOf("TỐI" to KachiPalette.DARK, "SÁNG" to KachiPalette.LIGHT)

    /** Nền thanh sau `KachiChrome.fade`: alpha của mã nhân [f] (cùng phép `ChromeOpacity.alphaByte` × alpha gốc khi vẽ). */
    private fun faded(hex: String, f: Double): String {
        val c = argb(hex)
        val a = (c[0] * f).toInt().coerceIn(0, 255)
        return String.format("#%02x%02x%02x%02x", a, c[1], c[2], c[3])
    }
}
