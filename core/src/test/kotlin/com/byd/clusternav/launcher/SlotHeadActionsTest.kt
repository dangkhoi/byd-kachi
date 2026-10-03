package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotHeadActions.Button
import com.byd.clusternav.launcher.SlotHeadRest.Kind
import com.byd.clusternav.launcher.SlotHeadRest.Projector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * L6 · (c) — nút đầu ô ([SlotHeadActions]): ĐỦ bảng 5 loại ô × 3 đường chiếu × kênh × chạy-nền-được = 60 ô.
 *
 * Khoá ba điều owner / luật dự án đòi:
 *  1. ô app: [chạy nền] [⇄] [tắt]; ô widget: [⇄] [tắt]; ô trống: [⇄] (owner 03/10);
 *  2. KHÔNG có nút chết — thiếu kênh / không màn ảo ⇒ chỉ ⇄; không app LƯU khác để đứng trước ⇒ không *chạy nền*;
 *  3. widget tắt được KHÔNG cần kênh (chỉ lớp tạm), kể cả widget bên thứ ba đã chết.
 */
class SlotHeadActionsTest {

    /** Bảng mong đợi viết TAY. */
    private fun expected(kind: Kind, projector: Projector, channel: Boolean, bg: Boolean): List<Button> = when (kind) {
        Kind.EMPTY -> listOf(Button.SWAP)
        Kind.WIDGET, Kind.APPWIDGET_LIVE, Kind.APPWIDGET_DEAD -> listOf(Button.SWAP, Button.CLOSE)
        Kind.APP -> when {
            projector != Projector.VD -> listOf(Button.SWAP)
            !channel -> listOf(Button.SWAP)
            bg -> listOf(Button.BACKGROUND, Button.SWAP, Button.CLOSE)
            else -> listOf(Button.SWAP, Button.CLOSE)
        }
    }

    @Test
    fun `du bang 60 o`() {
        var cells = 0
        for (kind in Kind.values()) for (p in Projector.values()) for (ch in listOf(true, false)) for (bg in listOf(true, false)) {
            assertEquals(expected(kind, p, ch, bg), SlotHeadActions.of(kind, p, ch, bg), "$kind · $p · kênh=$ch · chạy-nền=$bg")
            cells++
        }
        assertEquals(5 * 3 * 2 * 2, cells)
    }

    @Test
    fun `thu tu trai sang phai - chay nen, doi, tat - nut doi o giua`() {
        assertEquals(listOf(Button.BACKGROUND, Button.SWAP, Button.CLOSE), SlotHeadActions.of(Kind.APP, Projector.VD, true, true))
    }

    @Test
    fun `nut can dung cho moi loai o - khong bao gio dung nut ngoai tap`() {
        assertEquals(emptySet<Button>(), SlotHeadActions.possible(Kind.EMPTY, Projector.VD))
        assertEquals(setOf(Button.BACKGROUND, Button.CLOSE), SlotHeadActions.possible(Kind.APP, Projector.VD))
        assertEquals(emptySet<Button>(), SlotHeadActions.possible(Kind.APP, Projector.ACTIVITY_VIEW), "ROM ký nền tảng: chưa có id màn ảo")
        assertEquals(emptySet<Button>(), SlotHeadActions.possible(Kind.APP, Projector.NONE))
        listOf(Kind.WIDGET, Kind.APPWIDGET_LIVE, Kind.APPWIDGET_DEAD).forEach { k ->
            Projector.values().forEach { p -> assertEquals(setOf(Button.CLOSE), SlotHeadActions.possible(k, p), "$k · $p") }
        }
    }
}
