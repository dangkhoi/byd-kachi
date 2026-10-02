package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ShortcutPlan.Exclusion
import com.byd.clusternav.launcher.ShortcutPlan.Input
import com.byd.clusternav.launcher.ShortcutPlan.Reason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * C3 (spec shortcuts-autostart §4.4.3, R1.5) — ĐÚNG một ca cho mỗi dòng của bảng 15 dòng (+ dòng 0 "chưa cài").
 * Tên ca bắt đầu bằng số dòng để bảng trong spec và bài này đối chiếu được từng dòng.
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

    @Test fun `03 o n - o la widget thi mo toan man, khong de widget`() {
        assertEquals(ShortcutAction.OpenFull(Reason.SLOT_WIDGET), go(slot(2)))
        val aw = listOf(SlotContent.AppWidget(7, "p/.W"), SlotContent.Empty, SlotContent.Empty)
        assertEquals(ShortcutAction.OpenFull(Reason.SLOT_WIDGET), go(slot(1), aw))
    }

    @Test fun `04 o n - o da la B thi khong lam gi, nhay vien`() {
        val s = listOf(SlotContent.App(b), SlotContent.Empty, SlotContent.Empty)
        assertEquals(ShortcutAction.Noop(highlight = 0), go(slot(1), s))
    }

    @Test fun `05 o n - B dang o o khac thi chi nhay vien o do`() {
        val s = listOf(SlotContent.App(a), SlotContent.Empty, SlotContent.App(b))
        assertEquals(ShortcutAction.Highlight(2, Reason.IN_OTHER_SLOT), go(slot(2), s))
    }

    @Test fun `06 o n - o trong thi dat tam, khong day ai`() = assertEquals(ShortcutAction.PlaceTemp(2, evict = null), go(slot(3)))

    @Test fun `07 o n - o co app A thi dat tam va day A ra sau nha`() =
        assertEquals(ShortcutAction.PlaceTemp(0, evict = a), go(slot(1)))

    @Test fun `08 toan man - B khong o o nao thi mo bang Intent`() = assertEquals(ShortcutAction.OpenFull(null), go(full))

    @Test fun `09 toan man - B o o m, co kenh thi tach ra toan man`() {
        val s = listOf(SlotContent.App(b), SlotContent.Empty, SlotContent.Empty)
        assertEquals(ShortcutAction.DetachToFull(0, byIntent = false), go(full, s))
        assertEquals(ShortcutAction.DetachToFull(0, byIntent = true), go(full, s) { it.copy(fullByIntent = true) })
    }

    @Test fun `10 toan man - B o o m, khong kenh - theo ket qua T-M2`() {
        val s = listOf(SlotContent.App(b), SlotContent.Empty, SlotContent.Empty)
        assertEquals(ShortcutAction.Prompt, go(full, s, usable = false))
        assertEquals(ShortcutAction.DetachToFull(0, byIntent = true), go(full, s, usable = false) { it.copy(fullByIntent = true) })
    }

    @Test fun `11 chay ngam - chua co kenh thi hoi quyen`() = assertEquals(ShortcutAction.Prompt, go(bg, usable = false))

    @Test fun `12 chay ngam - B o mot o hoac dang chay thi khong lam gi`() {
        val s = listOf(SlotContent.Empty, SlotContent.App(b), SlotContent.Empty)
        assertEquals(ShortcutAction.Noop(highlight = 1, reason = Reason.RUNNING), go(bg, s))
        assertEquals(ShortcutAction.Noop(highlight = -1, reason = Reason.RUNNING), go(bg) { it.copy(running = true, hasLiveStage = true) })
    }

    @Test fun `13 chay ngam - app he thong, dang chieu cum, chinh Kachi thi tu choi`() {
        assertEquals(ShortcutAction.Refuse(Reason.SYSTEM_APP), go(bg) { it.copy(exclusion = Exclusion.SYSTEM_APP, hasLiveStage = true) })
        assertEquals(ShortcutAction.Refuse(Reason.CAST), go(bg) { it.copy(exclusion = Exclusion.CAST, hasLiveStage = true) })
        assertEquals(ShortcutAction.Refuse(Reason.SELF), go(bg) { it.copy(exclusion = Exclusion.SELF, hasLiveStage = true) })
    }

    @Test fun `14 chay ngam - khong co o song nao thi tu choi NO_STAGE, khong lui ve O5`() =
        assertEquals(ShortcutAction.Refuse(Reason.NO_STAGE), go(bg) { it.copy(hasLiveStage = false) })

    @Test fun `15 chay ngam - con lai thi chay sau man nha`() =
        assertEquals(ShortcutAction.StartBehind, go(bg) { it.copy(hasLiveStage = true) })

    @Test fun `o an (ngoai so o dang hien) khong tinh la B dang o o`() {
        // Ô 4 tồn tại trong trạng thái (SLOT_CAP = 6) nhưng bố cục 3 ô không hiện nó ⇒ B coi như không ở ô nào.
        val s = listOf(SlotContent.Empty, SlotContent.Empty, SlotContent.Empty, SlotContent.App(b))
        assertEquals(ShortcutAction.OpenFull(null), go(full, s))
    }
}
