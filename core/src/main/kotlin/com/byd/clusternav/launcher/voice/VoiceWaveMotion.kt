package com.byd.clusternav.launcher.voice

import kotlin.math.abs

/**
 * ═══ 3.0 R5 VOICE-OVERLAY-FLICKER-SLOW — luật CHUYỂN ĐỘNG của vòng sóng trên tấm chữ giọng nói ═══════════════════════
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car. `VoiceWaveView` (`:app`) chỉ vẽ theo hai hàm ở đây.
 *
 * ## Bệnh — [ĐO máy ảo 10/10, `docs/diagnostics/voice-300-r5-overlay-bisect-2026-10-10.md`]
 * Tới 2.99, `onDraw` tự hẹn khung kế **vô điều kiện** khi đang nghe (`if (listening || …) postInvalidateOnAnimation()`, có từ
 * 2.16) ⇒ tấm chữ — một cửa sổ PHỦ TOÀN MÀN trong suốt — vẽ lại 60 khung/giây suốt phiên (tới 8 s nghe + 5 s nghe nối), kể cả
 * khi mức âm đứng yên. Đo một phiên ~11 s ở `:wake`: luồng `RenderThread` ăn **3 780 ms CPU**, mọi luồng khác cộng lại ~220 ms.
 * Trên đầu xe (SoC yếu, video đang chạy bên dưới) đó là phần CPU tranh với VAD + giải mã ⇒ khung hình giật + xử lý chậm.
 *
 * ## Luật — chỉ vẽ khi CÓ gì thay đổi THẤY ĐƯỢC
 * Mức âm tới 5 lần/giây (khúc 200 ms của `VoiceCapture`). Mỗi khung nội suy [EASE] về đích; còn cách đích > [EPS] thì hẹn khung
 * kế, tới đích thì DỪNG — khung kế chỉ có khi mức âm mới lệch > [EPS] hoặc đổi pha nghe. [EPS] = 1 % mức ⇒ bán kính lệch
 * < 0,3 px ở cỡ vòng hiện hành: nhìn không khác, nhưng im lặng / mức đều là 0 khung.
 */
object VoiceWaveMotion {

    /** Phần đường còn lại đi được mỗi khung — giữ đúng hệ số của bản ≤ 2.99 (độ mượt không đổi). */
    const val EASE = 0.25f

    /** Lệch mức (0..1) nhỏ hơn ngần này coi như đã tới — dưới ngưỡng thấy được của một điểm ảnh. */
    const val EPS = 0.01f

    /** Mức vẽ ở khung kế; vào trong [EPS] của đích thì CHỐT đúng đích (phần dư không thấy được, và nhờ đó ồn nền lệch < [EPS] không gây khung nào). */
    fun step(level: Float, goal: Float): Float {
        val next = level + (goal - level) * EASE
        return if (abs(goal - next) <= EPS) goal else next
    }

    /** Còn phải vẽ khung nữa không (mức đang vẽ chưa tới đích). */
    fun moving(level: Float, goal: Float): Boolean = abs(goal - level) > EPS
}
