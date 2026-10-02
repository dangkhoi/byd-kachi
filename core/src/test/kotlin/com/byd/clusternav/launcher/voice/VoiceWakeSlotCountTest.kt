package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.GridFrame
import com.byd.clusternav.launcher.GridLayout
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.LayoutPreset
import com.byd.clusternav.launcher.WorkspaceState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VOICE-WAKE-SLOTCOUNT (2026-10-02) — phần THUẦN ═══════════════════════════════════════════════════════════
 *
 * [ĐO ảnh owner 02/10, 2.85] bố cục tự vẽ 6 khung, Cài đặt lối tắt hiện Ô1…Ô6, mà câu *"mở YouTube vào ô 6"* trả
 * *"✗ Mở ứng dụng YouTube vào ô 6 — bố cục hiện chỉ có 3 ô"*. Gốc [ĐO nguồn]: phiên `:wake` dựng `VoiceDispatcher` với
 * `state = { grammar().homeState() }`, và `homeState()` chỉ mang hồ sơ + sổ địa chỉ ⇒ bố cục = mặc định `THREE`.
 *
 * Bài này khoá bốn mảnh thuần của bản vá:
 *  1. tiền đề lỗi — `homeState()` LUÔN ra 3 ô dù màn thật có mấy ô (nên `:wake` không được dùng nó cho số ô);
 *  2. luật dải ô duy nhất [VoiceSlotPlace.decide] (Activity và phiên in-process cùng gọi);
 *  3. lời đáp Activity → `:wake` mang SỐ Ô THẬT ([VoiceHomeRelay.ackOf] / [VoiceHomeRelay.slotOutcome]);
 *  4. trần ô của tầng NGHE = trần ô thật ([WorkspaceState.SLOT_CAP]), không phải trần bố cục sẵn (4).
 */
class VoiceWakeSlotCountTest {

    private val six = GridLayout((0 until 6).map { GridFrame(it * 2, 0, 2, 3) })

    // ── 1 · tiền đề ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `homeState cua wake LUON la bo cuc mac dinh 3 o - khong phai man that`() {
        val st = VoiceGrammarSnapshot(profiles = listOf("Mặc định", "Vợ"), activeProfile = "Vợ").homeState()
        assertEquals(3, VoiceSlotPlace.slotCountOf(st), "homeState() không mang bố cục — đây là gốc lỗi 'chỉ có 3 ô'")
        assertEquals(null, st.customLayout)
        // Ba trường mà ảnh chụp MANG THẬT vẫn đúng (phần đã vá ở §8.2 (A), không đổi).
        assertEquals("Vợ", st.activeProfile)
        assertEquals(listOf("Mặc định", "Vợ"), st.profiles)
    }

    @Test
    fun `so o that lay tu bo cuc dang hieu luc - tu ve 6 khung la 6, preset la so cua preset`() {
        assertEquals(6, VoiceSlotPlace.slotCountOf(HomeUiState(customLayout = six)))
        assertEquals(3, VoiceSlotPlace.slotCountOf(HomeUiState(workspace = WorkspaceState(LayoutPreset.THREE))))
        assertEquals(4, VoiceSlotPlace.slotCountOf(HomeUiState(workspace = WorkspaceState(LayoutPreset.QUAD))))
    }

    // ── 2 · luật dải ô ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `decide - trong dai thi giao cho ben thi hanh, ngoai dai thi KHONG goi va tra so o that`() {
        var calls = 0
        assertEquals(SlotPlaceOutcome.Placed, VoiceSlotPlace.decide(5, 6) { calls++; true }, "ô 6 (0-based 5) của bố cục 6 ô")
        assertEquals(SlotPlaceOutcome.Failed, VoiceSlotPlace.decide(0, 6) { calls++; false })
        assertEquals(2, calls)
        assertEquals(SlotPlaceOutcome.OutOfRange(6), VoiceSlotPlace.decide(6, 6) { calls++; true }, "ô 7 của bố cục 6 ô")
        assertEquals(SlotPlaceOutcome.OutOfRange(3), VoiceSlotPlace.decide(3, 3) { calls++; true }, "ô 4 của bố cục 3 ô")
        assertEquals(SlotPlaceOutcome.OutOfRange(3), VoiceSlotPlace.decide(-1, 3) { calls++; true }, "ô 0 (nói 'ô số không')")
        assertEquals(2, calls, "ngoài dải thì không có gì để làm — không được chạm bên thi hành")
    }

    // ── 3 · lời đáp mang số ô thật ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `ack Activity - wake giu nguyen ket qua, ke ca SO O THAT khi ngoai dai`() {
        listOf(SlotPlaceOutcome.Placed, SlotPlaceOutcome.Failed, SlotPlaceOutcome.OutOfRange(6), SlotPlaceOutcome.OutOfRange(1))
            .forEach { o -> assertEquals(o, VoiceHomeRelay.slotOutcome(VoiceHomeRelay.ackOf(o)), "khứ hồi hỏng: $o") }
        assertEquals(VoiceHomeRelay.Ack(done = false, outOfRangeSlots = 6), VoiceHomeRelay.ackOf(SlotPlaceOutcome.OutOfRange(6)))
    }

    /**
     * [SOÁT lượt 1 · 02/10] Tiền đề của cổng `slot < 0` trong `VoiceWakeHomeRelay.performSlot`: bộ phân tích KHÔNG BAO
     * GIỜ ra ô ≤ 0 (`VoiceTailClause.slotAt` bỏ `n.value <= 0`; mọi nguồn `OpenApp.slot` đều qua hàm ấy) ⇒ `slot - 1 ≥ 0`
     * luôn ⇒ cổng đó chỉ là phòng thủ, và không có câu nào mà `:wake` và in-process trả lời khác nhau vì nó. Bản đầu
     * của lượt này ghi một ca lệch *"vào ô số không"* [P3] — ca ấy không tồn tại: câu ra `OpenApp` KHÔNG có ô.
     */
    @Test
    fun `o so khong khong bao gio thanh lenh gan o - wake va in-process khong lech`() {
        listOf("mở youtube vào ô số không", "mở youtube vào ô không", "mở youtube ô số 0").forEach { t ->
            val opens = VoiceIntentParser.parse(t, emptyList(), listOf("YouTube")).filterIsInstance<VoiceIntent.OpenApp>()
            assertTrue(opens.isNotEmpty(), "«$t» phải vẫn hiểu là mở YouTube")
            assertTrue(opens.all { it.slot == null }, "«$t» không được ra một ô ≤ 0: $opens")
        }
    }

    @Test
    fun `khong ack trong han thi la tu choi that - khong lac quan, khong bia so o`() {
        assertEquals(SlotPlaceOutcome.Failed, VoiceHomeRelay.slotOutcome(null))
        // Ack của bản Activity CŨ (chưa có extra số ô) ⇒ `outOfRangeSlots = 0` ⇒ từ chối thường, không bịa "0 ô".
        assertEquals(SlotPlaceOutcome.Failed, VoiceHomeRelay.slotOutcome(VoiceHomeRelay.Ack(done = false)))
        assertEquals(SlotPlaceOutcome.Placed, VoiceHomeRelay.slotOutcome(VoiceHomeRelay.Ack(done = true)))
    }

    // ── 4 · tầng NGHE theo trần ô thật ──────────────────────────────────────────────────────────────────────

    @Test
    fun `tran o cua tang nghe la tran o that - bo cuc tu ve 6 khung`() {
        assertEquals(WorkspaceState.SLOT_CAP, VoiceSlotPhrases.MAX_SLOT, "trần ô giọng nói lệch trần ô màn chính")
        val hot = SherpaBiasing.hotwordsFile().trimEnd().split("\n")
        assertTrue("VÀO Ô SỐ SÁU" in hot, "ô 6 của bố cục tự vẽ phải được bias như ô 2")
        assertTrue("VÀO Ô SỐ NĂM" in hot)
        assertFalse(hot.any { it.contains("Ô SỐ BẢY") }, "không bố cục nào có ô 7 ⇒ không bias")
    }

    @Test
    fun `duoi rung chu o van ra o 5 va o 6`() {
        fun slotOf(text: String) = (VoiceIntentParser.parse(text, emptyList(), listOf("YouTube")).single() as VoiceIntent.OpenApp).slot
        assertEquals(6, slotOf("mở youtube sáu"), "đuôi rụng chữ 'vào ô' — trước bản vá bị kẹp ở 4 ⇒ mở toàn màn")
        assertEquals(5, slotOf("mở youtube số năm"))
        assertEquals(null, slotOf("mở youtube bảy"), "7 vẫn quá trần mọi bố cục ⇒ chứng cứ yếu, không nhận")
        assertEquals(6, slotOf("mở youtube vào ô số sáu"))
    }
}
