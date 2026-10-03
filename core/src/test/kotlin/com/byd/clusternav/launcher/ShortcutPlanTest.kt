package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ShortcutPlan.Exclusion
import com.byd.clusternav.launcher.ShortcutPlan.Input
import com.byd.clusternav.launcher.ShortcutPlan.Reason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * C3 (spec shortcuts-autostart §4.4.3, R1.5) — ĐÚNG một ca cho mỗi dòng của bảng 15 dòng (+ dòng 0 "chưa cài").
 * Tên ca bắt đầu bằng số dòng để bảng trong spec và bài này đối chiếu được từng dòng.
 *
 * FIX286 · R-SC (spec `kachi-286-field-fixes.html` §3.10, lỗi xe owner 03/10): dòng 3 THAY khẳng định (ô widget ⇒ đặt tạm,
 * không còn toàn màn); dòng 4 · 9 · 12 thêm khẳng định theo phép đo sống/chết lúc chạm ([SlotPresence]) — ca `GONE` của
 * mỗi dòng đỏ ở bản trước FIX286 (bản đó không nhìn phép đo). Không ca cũ nào bị nới: mọi khẳng định cũ còn nguyên ở
 * nhánh `IN_SLOT`/`UNKNOWN`, trừ dòng 3 mà owner đổi ý.
 */
class ShortcutPlanTest {

    private val b = "com.google.android.deskclock"
    private val a = "vn.vietmap.live"
    private val slots3 = listOf(SlotContent.App(a), SlotContent.Widget("w_media"), SlotContent.Empty)

    private fun slot(n: Int) = AppShortcut(b, ShortcutMode.Slot(n))
    private val full = AppShortcut(b, ShortcutMode.Full)
    private val bg = AppShortcut(b, ShortcutMode.Background)

    private fun go(sc: AppShortcut, slots: List<SlotContent> = slots3, usable: Boolean = true, f: (Input) -> Input = { it }) =
        ShortcutPlan.decide(f(Input(sc, slots, slotCount = 3, usable = usable)))

    @Test fun `00 chua cai - moi kieu deu tu choi`() {
        listOf(slot(1), full, bg).forEach {
            assertEquals(ShortcutAction.Refuse(Reason.NOT_INSTALLED), go(it) { i -> i.copy(installed = false) })
        }
    }

    @Test fun `01 o n - chua co kenh thi hoi quyen`() = assertEquals(ShortcutAction.Prompt, go(slot(1), usable = false))

    @Test fun `02 o n - n vuot so o thi mo toan man kem ly do`() =
        assertEquals(ShortcutAction.OpenFull(Reason.SLOT_ABSENT), go(slot(4)))

    /**
     * FIX286 · R-SC1 — owner 03/10: *"khi chọn app trên shortcut thì nó đạp widget ra để thay app vào đấy … đưa gmaps vào ô 1
     * chạy ngon lành mới đúng"*. Bản trước: `OpenFull(SLOT_WIDGET)`. Nay ô widget (Kachi hay bên thứ ba) đi CÙNG đường ô trống:
     * đặt TẠM, không đẩy ai (widget không phải app để ra sau màn nhà), và không phép đo nào kéo nó về toàn màn.
     */
    @Test fun `03 o n - o la widget thi dat TAM de len widget, khong day ai, khong bao gio mo toan man`() {
        assertEquals(ShortcutAction.PlaceTemp(1, evict = null), go(slot(2)))
        val aw = listOf(SlotContent.AppWidget(7, "p/.W"), SlotContent.Empty, SlotContent.Empty)
        assertEquals(ShortcutAction.PlaceTemp(0, evict = null), go(slot(1), aw))
        SlotPresence.entries.forEach { p ->
            assertEquals(ShortcutAction.PlaceTemp(0, evict = null), go(slot(1), aw) { it.copy(presence = p) }, "đo $p")
        }
        assertEquals(-1, ShortcutPlan.presenceSlot(Input(slot(1), aw, slotCount = 3, usable = true)), "B không ở ô ⇒ 0 lệnh đo")
    }

    /**
     * FIX286 · R-SC2 — owner 03/10: *"tắt gmaps … bấm lại icon gmaps ở shortcut, chỉ hiện icon gmaps … thay vì mở lại gmaps
     * lên ô 1"*. Bố cục chỉ nói B ĐƯỢC XẾP ở ô 1; còn sống hay không là việc của `am stack list` lúc chạm. Bản trước trả
     * `Noop(0)` với mọi phép đo ⇒ ca `GONE` đỏ. Chưa đo (`UNKNOWN`) ⇒ KHÔNG mở lại: mở lại = `am force-stop` B.
     */
    @Test fun `04 o n - o da la B - song thi nhay vien, da dong thi mo lai vao o, song cho khac thi khong dung`() {
        val s = listOf(SlotContent.App(b), SlotContent.Empty, SlotContent.Empty)
        assertEquals(0, ShortcutPlan.presenceSlot(Input(slot(1), s, slotCount = 3, usable = true)), "dòng 4 phải ĐO trước khi quyết")
        assertEquals(ShortcutAction.Noop(highlight = 0), go(slot(1), s) { it.copy(presence = SlotPresence.IN_SLOT) })
        assertEquals(ShortcutAction.Reopen(0), go(slot(1), s) { it.copy(presence = SlotPresence.GONE) })
        assertEquals(ShortcutAction.Noop(highlight = 0, reason = Reason.RUNNING), go(slot(1), s) { it.copy(presence = SlotPresence.ELSEWHERE) })
        assertEquals(ShortcutAction.Noop(highlight = 0), go(slot(1), s), "chưa đo ⇒ hành vi trước FIX286, không force-stop")
    }

    @Test fun `05 o n - B dang o o khac thi chi nhay vien o do`() {
        val s = listOf(SlotContent.App(a), SlotContent.Empty, SlotContent.App(b))
        assertEquals(ShortcutAction.Highlight(2, Reason.IN_OTHER_SLOT), go(slot(2), s))
    }

    @Test fun `06 o n - o trong thi dat tam, khong day ai`() = assertEquals(ShortcutAction.PlaceTemp(2, evict = null), go(slot(3)))

    @Test fun `07 o n - o co app A thi dat tam va day A ra sau nha`() =
        assertEquals(ShortcutAction.PlaceTemp(0, evict = a), go(slot(1)))

    @Test fun `08 toan man - B khong o o nao thi mo bang Intent`() = assertEquals(ShortcutAction.OpenFull(null), go(full))

    /**
     * FIX286 · R-SC2 (cùng gốc dòng 4): B được xếp ở ô m mà KHÔNG còn task nào ⇒ K7 không có gì để tách (bản trước:
     * `DetachToFull` ⇒ chuỗi đọc thấy "đã đóng", B không mở) ⇒ mở bằng Intent như dòng 8. Ca `GONE` đỏ ở bản trước.
     */
    @Test fun `09 toan man - B o o m, co kenh thi tach ra toan man, B da dong thi mo bang Intent`() {
        val s = listOf(SlotContent.App(b), SlotContent.Empty, SlotContent.Empty)
        assertEquals(0, ShortcutPlan.presenceSlot(Input(full, s, slotCount = 3, usable = true)))
        assertEquals(ShortcutAction.DetachToFull(0, byIntent = false), go(full, s))
        assertEquals(ShortcutAction.DetachToFull(0, byIntent = true), go(full, s) { it.copy(fullByIntent = true) })
        listOf(SlotPresence.IN_SLOT, SlotPresence.ELSEWHERE).forEach { p ->
            assertEquals(ShortcutAction.DetachToFull(0, byIntent = false), go(full, s) { it.copy(presence = p) }, "đo $p")
        }
        assertEquals(ShortcutAction.OpenFull(null), go(full, s) { it.copy(presence = SlotPresence.GONE) })
    }

    @Test fun `10 toan man - B o o m, khong kenh - theo ket qua T-M2`() {
        val s = listOf(SlotContent.App(b), SlotContent.Empty, SlotContent.Empty)
        assertEquals(ShortcutAction.Prompt, go(full, s, usable = false))
        assertEquals(ShortcutAction.DetachToFull(0, byIntent = true), go(full, s, usable = false) { it.copy(fullByIntent = true) })
        assertEquals(-1, ShortcutPlan.presenceSlot(Input(full, s, slotCount = 3, usable = false)), "không kênh ⇒ không đo")
    }

    @Test fun `11 chay ngam - chua co kenh thi hoi quyen`() = assertEquals(ShortcutAction.Prompt, go(bg, usable = false))

    /**
     * FIX286 · R-SC2 — "kiểm tương tự dòng 12": B được xếp ở ô m mà đo thấy KHÔNG còn task ⇒ mở lại vào CHÍNH ô m (bố cục nói
     * B sống ở đó; chạy B sau màn nhà sẽ để ô m treo thẻ "đã đóng" trong khi B đang chạy chỗ khác). Ca `GONE` đỏ ở bản trước.
     */
    @Test fun `12 chay ngam - B o mot o con song hoac dang chay thi khong lam gi, B trong o da dong thi mo lai vao o do`() {
        val s = listOf(SlotContent.Empty, SlotContent.App(b), SlotContent.Empty)
        assertEquals(1, ShortcutPlan.presenceSlot(Input(bg, s, slotCount = 3, usable = true)))
        assertEquals(ShortcutAction.Noop(highlight = 1, reason = Reason.RUNNING), go(bg, s))
        listOf(SlotPresence.IN_SLOT, SlotPresence.ELSEWHERE).forEach { p ->
            assertEquals(ShortcutAction.Noop(highlight = 1, reason = Reason.RUNNING), go(bg, s) { it.copy(presence = p) }, "đo $p")
        }
        assertEquals(ShortcutAction.Reopen(1), go(bg, s) { it.copy(presence = SlotPresence.GONE) })
        assertEquals(ShortcutAction.Noop(highlight = -1, reason = Reason.RUNNING), go(bg) { it.copy(running = true, hasLiveStage = true) })
        assertEquals(-1, ShortcutPlan.presenceSlot(Input(bg, slots3, slotCount = 3, usable = true)), "B không ở ô ⇒ 0 lệnh đo")
        assertEquals(ShortcutAction.StartBehind, go(bg) { it.copy(presence = SlotPresence.GONE, hasLiveStage = true) },
            "GONE chỉ có nghĩa với B ĐANG ở ô — B không ở ô nào thì đi dòng 15 như cũ")
    }

    @Test fun `13 chay ngam - app he thong, dang chieu cum, chinh Kachi thi tu choi`() {
        assertEquals(ShortcutAction.Refuse(Reason.SYSTEM_APP), go(bg) { it.copy(exclusion = Exclusion.SYSTEM_APP, hasLiveStage = true) })
        assertEquals(ShortcutAction.Refuse(Reason.CAST), go(bg) { it.copy(exclusion = Exclusion.CAST, hasLiveStage = true) })
        assertEquals(ShortcutAction.Refuse(Reason.SELF), go(bg) { it.copy(exclusion = Exclusion.SELF, hasLiveStage = true) })
    }

    /**
     * L8 — ĐỔI GHIM có lý do (owner 03/10 + OQ-L4-1): dòng 14 thôi từ chối `NO_STAGE` — không ô app sống ⇒ màn ảo ẨN của Kachi
     * (cùng chuỗi chuyến lên xe, L4 · D2(a)). Vẫn KHÔNG lùi về O5 (mở toàn màn) và các dòng trước (kênh, đang chạy, loại trừ
     * R0.6) vẫn thắng; ô sống vẫn thắng màn ảo ẩn (dòng 15 — CLAUDE.md §6: đường mới đứng cuối).
     */
    @Test fun `14 chay ngam - khong co o song nao thi qua man ao an, khong lui ve O5`() {
        assertEquals(ShortcutAction.StartBehindHidden, go(bg) { it.copy(hasLiveStage = false) })
        assertEquals(ShortcutAction.StartBehind, go(bg) { it.copy(hasLiveStage = true) }, "ô sống LUÔN trước")
        assertEquals(ShortcutAction.Prompt, go(bg, usable = false), "không kênh ⇒ hỏi quyền, không dàn")
        assertEquals(ShortcutAction.Refuse(Reason.SYSTEM_APP), go(bg) { it.copy(exclusion = Exclusion.SYSTEM_APP) }, "R0.6 thắng")
        assertEquals(ShortcutAction.Noop(highlight = -1, reason = Reason.RUNNING), go(bg) { it.copy(running = true) })
    }

    @Test fun `15 chay ngam - con lai thi chay sau man nha`() =
        assertEquals(ShortcutAction.StartBehind, go(bg) { it.copy(hasLiveStage = true) })

    /** FIX286 · R-SC2 — phép đo chỉ chạy khi KẾT QUẢ phụ thuộc nó (dòng 4/9/12); mọi dòng khác ⇒ `-1`, 0 lệnh shell. */
    @Test fun `presenceSlot - chi do khi ket qua phu thuoc phep do`() {
        val bAt0 = listOf(SlotContent.App(b), SlotContent.Empty, SlotContent.Empty)
        fun ps(sc: AppShortcut, s: List<SlotContent>, usable: Boolean = true, installed: Boolean = true) =
            ShortcutPlan.presenceSlot(Input(sc, s, slotCount = 3, usable = usable, installed = installed))
        assertEquals(-1, ps(slot(1), bAt0, installed = false), "dòng 0")
        assertEquals(-1, ps(slot(1), bAt0, usable = false), "dòng 1")
        assertEquals(-1, ps(slot(4), bAt0), "dòng 2 — n vượt số ô ⇒ toàn màn bất kể sống chết")
        assertEquals(-1, ps(slot(2), bAt0), "dòng 5 — B ở ô khác ⇒ chỉ nháy ô đó")
        assertEquals(-1, ps(slot(1), slots3), "dòng 6/7 — B không ở ô nào")
        assertEquals(-1, ps(full, slots3), "dòng 8")
        assertEquals(-1, ps(bg, bAt0, usable = false), "dòng 11")
        val hidden = listOf(SlotContent.Empty, SlotContent.Empty, SlotContent.Empty, SlotContent.App(b))
        assertEquals(-1, ps(slot(1), hidden), "ô ẩn không tính là B đang ở ô")
    }

    @Test fun `o an (ngoai so o dang hien) khong tinh la B dang o o`() {
        // Ô 4 tồn tại trong trạng thái (SLOT_CAP = 6) nhưng bố cục 3 ô không hiện nó ⇒ B coi như không ở ô nào.
        val s = listOf(SlotContent.Empty, SlotContent.Empty, SlotContent.Empty, SlotContent.App(b))
        assertEquals(ShortcutAction.OpenFull(null), go(full, s))
    }
}
