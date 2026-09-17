package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** P1b · R8 — lưu bền lựa chọn màu: đi vòng tròn, rác ⇒ mặc định, nhãn đủ và khác nhau. */
class ColorChoiceTest {

    @Test
    fun `encode decode di vong tron cho moi to hop`() {
        AccentChoice.values().forEach { a ->
            CardTone.values().forEach { t ->
                val c = ColorChoice(a, t, "pearl")
                assertEquals(c, ColorChoice.decode(c.encode()), c.encode())
            }
        }
    }

    @Test
    fun `thieu hoac rac thi ve mac dinh tung phan, khong sap`() {
        assertEquals(ColorChoice.DEFAULT, ColorChoice.decode(null))
        assertEquals(ColorChoice.DEFAULT, ColorChoice.decode(""))
        assertEquals(ColorChoice.DEFAULT, ColorChoice.decode("garbage"))
        assertEquals(ColorChoice(AccentChoice.TEAL, CardTone.NEUTRAL), ColorChoice.decode("TEAL;???"))
        assertEquals(ColorChoice(AccentChoice.KACHI_BLUE, CardTone.WARM), ColorChoice.decode("nope;WARM"))
    }

    @Test
    fun `ma mau son khong duoc pha dinh dang`() {
        val c = ColorChoice(paint = "a;b")
        assertEquals("a b", ColorChoice.decode(c.encode()).paint)
    }

    @Test
    fun `nhan tung lua chon khong rong va khong trung`() {
        listOf(Lang.VI, Lang.EN).forEach { lang ->
            val a = AccentChoice.values().map { Strings.t(it.label(), it.label(), lang) }
            assertEquals(a.size, a.distinct().size, "nhãn màu nhấn trùng nhau ($lang)")
            assertTrue(a.all { it.isNotBlank() })
            val t = CardTone.values().map { it.label() }
            assertEquals(t.size, t.distinct().size)
        }
        assertEquals(9, AccentChoice.values().size, "AC8.1: 8 ô chọn nhanh + 1 ô theo ảnh nền")
        assertEquals(3, CardTone.values().size, "AC8.2: trung tính · ấm · lạnh")
    }
}
