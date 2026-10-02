package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.EffectiveLayout
import com.byd.clusternav.launcher.HomeUiState

/**
 * Kết quả của *"mở X vào ô N"* — đủ để nói ĐÚNG câu: đã đặt · không đặt được · ngoài dải (kèm số ô THẬT).
 *
 * VOICE-WAKE-SLOTCOUNT (2026-10-02). Trước bản này kết quả chỉ là `Boolean`, nên câu *"bố cục hiện chỉ có N ô"* phải
 * do chính `VoiceDispatcher` tính N từ `state()` của nó — mà trong tiến trình `:wake` `state()` là
 * `VoiceGrammarSnapshot.homeState()` (chỉ hồ sơ + sổ địa chỉ; bố cục = mặc định `THREE`, bố cục tự vẽ = `null`).
 * [ĐO ảnh owner 02/10, 2.85] bố cục tự vẽ 6 khung + *"mở YouTube vào ô 6"* ⇒ *"bố cục hiện chỉ có 3 ô"*.
 */
sealed class SlotPlaceOutcome {
    /** Đã đặt app vào ô. */
    object Placed : SlotPlaceOutcome()

    /** Ô có trong bố cục nhưng không đặt được (bên thi hành từ chối · hết hạn chờ · tham số hỏng). */
    object Failed : SlotPlaceOutcome()

    /** Ô không có trong bố cục ĐANG HIỆU LỰC. [slotCount] = số ô thật, đo ở nơi giữ bố cục. */
    data class OutOfRange(val slotCount: Int) : SlotPlaceOutcome()
}

/**
 * Luật DUY NHẤT của *"ô này có trong bố cục không"* cho đường giọng nói.
 *
 * Hai chỗ gọi, cùng một hàm:
 *  • `VoiceDispatcher` ở bề mặt CHUNG tiến trình với màn chính (nút mic khi wake TẮT · ô *Gõ lệnh chữ* · cầu kiểm
 *    thử) — `state()` ở đó là state thật;
 *  • `VoiceHomeActions` (Activity) khi `:wake` chuyển nguyên lệnh sang — cũng state thật.
 * `:wake` KHÔNG gọi hàm này: nó không có bố cục thật để đưa vào.
 */
object VoiceSlotPlace {

    /** Số ô của bố cục đang hiệu lực — cùng nguồn [EffectiveLayout] mà màn chính vẽ theo. */
    fun slotCountOf(st: HomeUiState): Int = EffectiveLayout.slotCount(st.workspace.preset, st.customLayout)

    /**
     * Ô 0-based [slot] ngoài `0 until slotCount` ⇒ [SlotPlaceOutcome.OutOfRange] và **không** gọi [place] (không có
     * gì để làm). Trong dải ⇒ [place] quyết: `true` = [SlotPlaceOutcome.Placed], `false` = [SlotPlaceOutcome.Failed].
     */
    fun decide(slot: Int, slotCount: Int, place: () -> Boolean): SlotPlaceOutcome = when {
        slot !in 0 until slotCount -> SlotPlaceOutcome.OutOfRange(slotCount)
        place() -> SlotPlaceOutcome.Placed
        else -> SlotPlaceOutcome.Failed
    }
}
