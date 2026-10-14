package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotExpand.Change
import com.byd.clusternav.launcher.SlotExpand.Why
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * R30 — bung ô ([SlotExpand], spec `kachi-301-cast-steps-dialog-ia.html` §4.8). Khoá: bung ⇒ chỉ ô đó hiện, khung = trọn vùng ô;
 * thu ⇒ khung cũ NGUYÊN; đổi hồ sơ / bố cục ⇒ thu; một ô một lúc; không gì bền (lớp chỉ có MỘT trường trạng thái).
 */
class SlotExpandTest {

    private val w = 1600
    private val h = 560

    /** Bố cục THREE thật (cột trái 1.55 : 1), khe 12 px — cùng nguồn `WorkspaceView.effectiveRects`. */
    private val base = EffectiveLayout.rects(LayoutPreset.THREE, null, w, h, 12)

    @Test
    fun `bung - chi o do hien, khung tron vung o, khung khac giu nguyen`() {
        val x = SlotExpand()
        assertEquals(Change.Expand(2), x.toggle(2, base.size))
        assertEquals(2, x.expanded)
        assertEquals(listOf(false, false, true), base.indices.map(x::visible))
        val r = x.rects(base, w, h)
        assertEquals(SlotRect(base[2].index, 0, 0, w, h), r[2])
        assertEquals(base[0], r[0]); assertEquals(base[1], r[1])   // ô ẩn không đổi cỡ ⇒ màn ảo của chúng không resize
    }

    @Test
    fun `thu - khung ve dung nhu cu, moi o hien`() {
        val x = SlotExpand()
        x.toggle(0, base.size)
        assertEquals(Change.Collapse(0, Why.TAP), x.toggle(0, base.size))
        assertNull(x.expanded)
        assertSame(base, x.rects(base, w, h), "không bung ⇒ trả nguyên bố cục, không sao chép")
        assertTrue(base.indices.all(x::visible))
    }

    @Test
    fun `doi ho so, doi bo cuc, Back, HOME deu thu - goi lai vo hai`() {
        for (why in Why.values()) {
            val x = SlotExpand()
            x.toggle(1, base.size)
            assertEquals(Change.Collapse(1, why), x.clear(why), "$why")
            assertEquals(Change.None, x.clear(why), "$why lần hai")
            assertSame(base, x.rects(base, w, h))
        }
    }

    @Test
    fun `mot o mot luc - o khac dang bung thi tu choi`() {
        val x = SlotExpand()
        x.toggle(0, base.size)
        assertEquals(Change.None, x.toggle(1, base.size))
        assertEquals(0, x.expanded)
        assertEquals(1, x.rects(base, w, h).count { it.width == w && it.height == h })
    }

    @Test
    fun `bo cuc mot o va o ngoai bo cuc - khong lam gi`() {
        val x = SlotExpand()
        assertEquals(Change.None, x.toggle(0, 1))
        assertEquals(Change.None, x.toggle(3, base.size))
        assertEquals(Change.None, x.toggle(-1, base.size))
        assertNull(x.expanded)
    }

    /** Bố cục đổi còn ít ô hơn mà chưa kịp `clear` ⇒ không ném, không bung khung lạ (rào phụ — `WorkspaceView` vẫn `drop`). */
    @Test
    fun `o bung ngoai bo cuc moi - tra nguyen bo cuc`() {
        val x = SlotExpand()
        x.toggle(2, base.size)
        val two = EffectiveLayout.rects(LayoutPreset.TWO_COL, null, w, h, 12)
        assertSame(two, x.rects(two, w, h))
    }

    /** Không gì bền: lớp có ĐÚNG một trường thể hiện (ô đang bung) — không kho, không prefs, không đồng hồ. */
    @Test
    fun `tam - mot truong trang thai duy nhat, doi tuong moi bat dau thuong`() {
        val fields = SlotExpand::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        assertEquals(listOf("expanded"), fields.map { it.name })
        assertNull(SlotExpand().expanded, "tiến trình / cây workspace mới ⇒ bố cục thường")
    }

    @Test
    fun `luat co nut - widget song va o App ve trong khung`() {
        assertTrue(SlotExpand.offered(SlotHeadRest.Kind.WIDGET, SlotHeadRest.Projector.NONE))
        assertTrue(SlotExpand.offered(SlotHeadRest.Kind.APPWIDGET_LIVE, SlotHeadRest.Projector.NONE))
        assertTrue(SlotExpand.offered(SlotHeadRest.Kind.APP, SlotHeadRest.Projector.VD))
        assertTrue(SlotExpand.offered(SlotHeadRest.Kind.APP, SlotHeadRest.Projector.ACTIVITY_VIEW))
        assertFalse(SlotExpand.offered(SlotHeadRest.Kind.APP, SlotHeadRest.Projector.NONE))
        assertFalse(SlotExpand.offered(SlotHeadRest.Kind.APPWIDGET_DEAD, SlotHeadRest.Projector.NONE))
        assertFalse(SlotExpand.offered(SlotHeadRest.Kind.EMPTY, SlotHeadRest.Projector.VD))
    }
}
