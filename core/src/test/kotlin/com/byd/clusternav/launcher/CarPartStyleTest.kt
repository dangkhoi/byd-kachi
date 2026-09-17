package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VISUAL-REFRESH P3 · AC6.3 — MỌI cặp (bộ phận × tone × available × sơn) tra ra ĐÚNG MỘT kết quả ══════════════
 *
 * Spec `docs/specs/kachi-visual-refresh.html` §4.4 luật (a)–(c). Bài này duyệt **toàn bộ tích Đề-các** chứ không
 * chọn vài cặp tiêu biểu: một nhánh `else` im lặng chỉ lộ ra ở đúng cặp không ai nghĩ tới.
 */
class CarPartStyleTest {

    private val roles = CarPartRole.values()
    private val tones = GroupTone.values()
    private val paints = CarPaint.values()

    @Test
    fun `moi cap vai x tone x available x son deu tra ra mot ket qua`() {
        var n = 0
        roles.forEach { r -> tones.forEach { t -> paints.forEach { p -> listOf(true, false).forEach { a ->
            assertNotNull(CarPartStyle.look(r, t, a, p)); n++
        } } } }
        assertEquals(roles.size * tones.size * paints.size * 2, n)
    }

    /** Chưa đọc được ⇒ nét DIM, KHÔNG tô (không bịa trạng thái đóng); mảnh trang trí thì ẩn hẳn. */
    @Test
    fun `chua doc duoc thi net DIM khong to, trang tri an`() {
        roles.forEach { r -> tones.forEach { t ->
            val look = CarPartStyle.look(r, t, available = false)
            if (r.decor) assertFalse(look.visible, "$r chưa đọc được phải ẨN")
            else assertEquals(PartLook(CarInk.NONE, CarInk.DIM), look, "$r chưa đọc được phải là nét DIM không tô")
        } }
    }

    /** Luật (c): ALERT là thứ đậm nhất — tô hoặc nét ĐỎ với mọi bộ phận mang dữ liệu (sơn không đỏ). */
    @Test
    fun `ALERT luon do voi moi bo phan mang du lieu`() {
        roles.filterNot { it.decor || it == CarPartRole.PAINT }.forEach { r ->
            val look = CarPartStyle.look(r, GroupTone.ALERT, available = true, paint = CarPaint.PEARL)
            assertTrue(look.fill == CarInk.RED || look.fill == CarInk.RED_SOFT || look.stroke == CarInk.RED, "$r ALERT không đỏ: $look")
        }
    }

    /**
     * Sơn ĐỎ: ALERT đổi sang hổ phách + viền tĩnh, còn NEUTRAL/ACTIVE/WARN không đổi so với sơn khác.
     *
     * ⚠ [SOÁT 2026-09-17] Bản đầu của bài này đòi `PartLook(AMBER, AMBER, outline = true)` cho **mọi** vai — kể cả
     * [CarPartRole.HIGHLIGHT], vai **chỉ có NÉT** mà path của nó là CHÍNH thân xe ⇒ bài canh khoá đúng cái lỗi
     * "tô kín thân xe bằng hổ phách". Nay hỏi theo hình dạng: vai nào vốn không tô thì đổi màu vẫn không tô.
     */
    @Test
    fun `son do doi ALERT sang ho phach co vien, cac tone khac giu nguyen`() {
        roles.filterNot { it.decor || it == CarPartRole.PAINT }.forEach { r ->
            val pearl = CarPartStyle.look(r, GroupTone.ALERT, true, CarPaint.PEARL)
            val red = CarPartStyle.look(r, GroupTone.ALERT, true, CarPaint.RED)
            val wantFill = if (pearl.fill == CarInk.NONE) CarInk.NONE else CarInk.AMBER
            assertEquals(PartLook(wantFill, CarInk.AMBER, outline = true), red, "$r ALERT trên sơn đỏ")
            listOf(GroupTone.NEUTRAL, GroupTone.ACTIVE, GroupTone.WARN).forEach { t ->
                assertEquals(CarPartStyle.look(r, t, true, CarPaint.PEARL), CarPartStyle.look(r, t, true, CarPaint.RED), "$r $t lệch theo sơn")
            }
        }
    }

    /**
     * Vai CHỈ-CÓ-NÉT không bao giờ được tô, ở **mọi** tone × **mọi** màu sơn.
     *
     * [ĐO 2026-09-17] `HIGHLIGHT` dùng lại path THÂN xe (`CarFramesGenerated.HIGHLIGHT_REF`), nên một `fill` khác
     * `NONE` ở đó = tô kín chiếc xe. Đây là bài canh đo NGUYÊN NHÂN (hình dạng cách tô), không đo hiện tượng.
     */
    @Test
    fun `vai chi co net khong bao gio to, moi tone moi mau son`() {
        CarPaint.values().forEach { paint ->
            tones.forEach { t ->
                val look = CarPartStyle.look(CarPartRole.HIGHLIGHT, t, true, paint)
                assertEquals(CarInk.NONE, look.fill, "HIGHLIGHT $t sơn $paint tô kín thân xe")
            }
            assertEquals(CarInk.NONE, CarPartStyle.look(CarPartRole.HIGHLIGHT, GroupTone.ALERT, false, paint).fill)
        }
    }

    /** Thân xe = chuyển sắc màu sơn + nét partLine ở mọi tone (thân không mang dữ liệu, nó là chỗ dữ liệu đứng lên). */
    @Test
    fun `than xe luon la son va vien partLine`() {
        tones.forEach { t -> assertEquals(PartLook(CarInk.PAINT, CarInk.PART_LINE), CarPartStyle.look(CarPartRole.PAINT, t, true)) }
    }

    @Test
    fun `ten vai trong SVG tra ra dung vai, ten la thi null`() {
        roles.forEach { assertEquals(it, CarPartRole.of(it.svgRole)) }
        assertEquals(null, CarPartRole.of("chrome"))
        assertEquals(roles.size, roles.map { it.svgRole }.toSet().size, "hai vai trùng tên SVG")
    }

    @Test
    fun `mau son - mac dinh ngoc trai, chuoi la ve mac dinh, san vien 3`() {
        assertEquals(CarPaint.PEARL, CarPaint.of(""))
        assertEquals(CarPaint.PEARL, CarPaint.of(null))
        assertEquals(CarPaint.PEARL, CarPaint.of("hồng"))
        assertEquals(CarPaint.RED, CarPaint.of(" red "))
        assertTrue(CarPaint.outlineNeeded(2.99))
        assertFalse(CarPaint.outlineNeeded(3.0))
        assertEquals(paints.size, paints.map { it.id }.toSet().size)
    }

    @Test
    fun `tone lop - non cang la ALERT, lech la WARN, con lai NEUTRAL`() {
        assertEquals(GroupTone.ALERT, CarPartStyle.tyreTone(TyreStatus.LOW))
        assertEquals(GroupTone.ALERT, CarPartStyle.tyreTone(TyreStatus.HIGH))
        assertEquals(GroupTone.WARN, CarPartStyle.tyreTone(TyreStatus.UNEVEN))
        assertEquals(GroupTone.NEUTRAL, CarPartStyle.tyreTone(TyreStatus.OK))
        assertEquals(GroupTone.NEUTRAL, CarPartStyle.tyreTone(TyreStatus.UNKNOWN))
    }
}
