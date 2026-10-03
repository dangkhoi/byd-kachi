package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotHeadRest.Kind
import com.byd.clusternav.launcher.SlotHeadRest.Projector
import com.byd.clusternav.launcher.SlotHeadRest.Rest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.87 · R-AH3 — bảng nghỉ của nút ⇄ ([SlotHeadRest]), ĐỦ 5 loại ô × 3 đường chiếu × công tắc × TalkBack = 60 ô.
 *
 * Khoá ba điều mà mắt thường không thấy khi thử trên máy ảo (máy ảo không có ActivityView, không bật TalkBack):
 *  1. đường ActivityView / không-bộ-chiếu của ô App LUÔN hiện — ở đó Kachi không thấy cú chạm hoặc có bản ⇄ thứ hai;
 *  2. TalkBack bật ⇒ mọi ô luôn hiện (view `INVISIBLE` rơi khỏi cây trợ năng);
 *  3. ô TRỐNG ẩn (owner 03/10 chốt, ngược khuyến nghị kiểm kê) — đổi ý thì phải đổi ở đây, có chủ đích.
 */
class SlotHeadRestTest {

    /** Bảng mong đợi viết TAY (không suy từ chính hàm đang thử): khi công tắc BẬT và TalkBack TẮT. */
    private fun expectedWhenOn(kind: Kind, projector: Projector): Rest = when (kind) {
        Kind.EMPTY, Kind.WIDGET, Kind.APPWIDGET_LIVE, Kind.APPWIDGET_DEAD -> Rest.AUTO_HIDE
        Kind.APP -> when (projector) {
            Projector.VD -> Rest.AUTO_HIDE
            Projector.ACTIVITY_VIEW -> Rest.ALWAYS
            Projector.NONE -> Rest.ALWAYS
        }
    }

    @Test
    fun `du bang 60 o`() {
        var cells = 0
        for (kind in Kind.values()) for (projector in Projector.values()) for (on in listOf(true, false)) for (te in listOf(true, false)) {
            val want = if (on && !te) expectedWhenOn(kind, projector) else Rest.ALWAYS
            assertEquals(want, SlotHeadRest.rest(kind, projector, on, te), "$kind · $projector · bật=$on · TalkBack=$te")
            cells++
        }
        assertEquals(5 * 3 * 2 * 2, cells, "bảng phải phủ đủ mọi tổ hợp — thêm loại ô/đường chiếu mới ⇒ sửa bảng tay ở trên")
    }

    @Test
    fun `o trong an — owner 03-10 chot, nham dai vao man thi lo ra`() {
        Projector.values().forEach { assertEquals(Rest.AUTO_HIDE, SlotHeadRest.rest(Kind.EMPTY, it, true, false)) }
    }

    @Test
    fun `duong khong thay cham hoac co ban sao thu hai thi luon hien`() {
        assertEquals(Rest.ALWAYS, SlotHeadRest.rest(Kind.APP, Projector.ACTIVITY_VIEW, true, false), "ActivityView nuốt chạm")
        assertEquals(Rest.ALWAYS, SlotHeadRest.rest(Kind.APP, Projector.NONE, true, false), "OverlayHeads có bản ⇄ thứ hai")
        assertEquals(Rest.AUTO_HIDE, SlotHeadRest.rest(Kind.APP, Projector.VD, true, false), "màn ảo: VdAppHost nhận mọi chạm")
    }

    @Test
    fun `talkback hoac cong tac tat thi y nhu 2-86`() {
        Kind.values().forEach { k ->
            Projector.values().forEach { p ->
                assertEquals(Rest.ALWAYS, SlotHeadRest.rest(k, p, autoHideEnabled = false, touchExploration = false))
                assertEquals(Rest.ALWAYS, SlotHeadRest.rest(k, p, autoHideEnabled = true, touchExploration = true))
            }
        }
    }

    @Test
    fun `kindOf du bon nhanh cua makeSlot`() {
        assertEquals(Kind.EMPTY, SlotHeadRest.kindOf(SlotContent.Empty, liveHost = false))
        assertEquals(Kind.WIDGET, SlotHeadRest.kindOf(SlotContent.Widget("w_climate"), liveHost = false))
        assertEquals(Kind.APPWIDGET_LIVE, SlotHeadRest.kindOf(SlotContent.AppWidget(7, "a/.B"), liveHost = true))
        assertEquals(Kind.APPWIDGET_DEAD, SlotHeadRest.kindOf(SlotContent.AppWidget(7, "a/.B"), liveHost = false))
        assertEquals(Kind.APP, SlotHeadRest.kindOf(SlotContent.App("com.x"), liveHost = true))
    }

    @Test
    fun `hen gio trong khoang owner noi vai giay`() {
        // L6 (owner 03/10, kèm nút chạy nền / tắt): *"Nút cũng tự hide sau 3s"* ⇒ chốt ĐÚNG 3 s (trước: khoảng 3–5 s, spec 4 s).
        assertEquals(3_000L, SlotHeadRest.HIDE_AFTER_MS, "owner 03/10: \"Nút cũng tự hide sau 3s\"")
        assertTrue(SlotHeadRest.FADE_IN_MS in 1L..SlotHeadRest.FADE_OUT_MS, "hiện nhanh hơn (hoặc bằng) lúc mờ đi")
        assertTrue(SlotHeadRest.FADE_OUT_MS < SlotHeadRest.HIDE_AFTER_MS)
    }
}
