package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotHeadActions.Button
import com.byd.clusternav.launcher.SlotHeadRest.Kind
import com.byd.clusternav.launcher.SlotHeadRest.Projector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * L6 · (c) — nút đầu ô ([SlotHeadActions]): ĐỦ bảng 5 loại ô × 3 đường chiếu × kênh = 30 ô.
 *
 * Khoá ba điều owner / luật dự án đòi:
 *  1. ô app: [chạy nền] [⇄] [tắt]; ô widget: [⇄] [tắt]; ô trống: [⇄] (owner 03/10);
 *  2. KHÔNG có nút chết — thiếu kênh / không màn ảo ⇒ chỉ ⇄;
 *  3. widget tắt được KHÔNG cần kênh (chỉ lớp tạm), kể cả widget bên thứ ba đã chết.
 *
 * L8 — ĐỔI GHIM có lý do (owner 03/10: nút chạy nền phải làm được ở MỌI ô app): bỏ điều kiện "ô có app LƯU khác" (D-L6-1) —
 * lớp che của Kachi trong màn ảo ô đứng trước app (`BehindHomeSequence.evictCovered`), không cần app B nào. Bảng 60 ô cũ
 * (thêm trục chạy-nền-được) còn 30 ô.
 *
 * Soát 2.87 · P3 — ĐỔI GHIM có lý do: thêm trục `behind` (BEHIND-HOME còn dùng được trong tiến trình — một phép đo
 * `ANCHOR_IN_FRONT` tắt nó tới lần khởi động sau, mọi lượt *chạy nền* sau đó trả `DISABLED`, 0 lệnh) ⇒ 60 ô; `behind = false`
 * thì ô app mất nút *chạy nền* (luật "không nút chết"), *tắt* giữ nguyên.
 */
class SlotHeadActionsTest {

    /** Bảng mong đợi viết TAY. */
    private fun expected(kind: Kind, projector: Projector, channel: Boolean, behind: Boolean): List<Button> = when (kind) {
        Kind.EMPTY -> listOf(Button.SWAP)
        Kind.WIDGET, Kind.APPWIDGET_LIVE, Kind.APPWIDGET_DEAD -> listOf(Button.SWAP, Button.CLOSE)
        Kind.APP -> when {
            projector != Projector.VD -> listOf(Button.SWAP)
            !channel -> listOf(Button.SWAP)
            !behind -> listOf(Button.SWAP, Button.CLOSE)                     // P3: BEHIND-HOME đã tắt ⇒ không nút chạy nền chết
            else -> listOf(Button.BACKGROUND, Button.SWAP, Button.CLOSE)   // L8: MỌI ô app có kênh + màn ảo
        }
    }

    @Test
    fun `du bang 60 o`() {
        var cells = 0
        for (kind in Kind.values()) for (p in Projector.values()) for (ch in listOf(true, false)) for (bh in listOf(true, false)) {
            assertEquals(expected(kind, p, ch, bh), SlotHeadActions.of(kind, p, ch, bh), "$kind · $p · kênh=$ch · behind=$bh")
            cells++
        }
        assertEquals(5 * 3 * 2 * 2, cells)
    }

    @Test
    fun `thu tu trai sang phai - chay nen, doi, tat - nut doi o giua`() {
        assertEquals(listOf(Button.BACKGROUND, Button.SWAP, Button.CLOSE), SlotHeadActions.of(Kind.APP, Projector.VD, true, true))
    }

    /** P3 — sau `ANCHOR_IN_FRONT` (BEHIND-HOME tắt cả tiến trình) nút chạy nền phải BIẾN MẤT, không ở lại toast "không chạy nền được". */
    @Test
    fun `BEHIND-HOME da tat thi o app mat nut chay nen, giu nut tat`() {
        assertEquals(listOf(Button.SWAP, Button.CLOSE), SlotHeadActions.of(Kind.APP, Projector.VD, channel = true, behind = false))
        assertEquals(setOf(Button.BACKGROUND, Button.CLOSE), SlotHeadActions.possible(Kind.APP, Projector.VD), "vẫn DỰNG nút (BEHIND-HOME có thể bật lại ở tiến trình sau)")
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
