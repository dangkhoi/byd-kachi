package com.byd.clusternav.voicekey

/**
 * ═══ QA 2.87 [P3] — "Học phím" phải nuốt TRỌN lần nhấn đã học, không chỉ DOWN ═══════════════════════════════════════════
 *
 * [ĐO máy ảo QA 2.87 `l7/learn88-orphan-up.log`] học phím 88 (MEDIA_PREVIOUS): `02:36:21.524 learned voice keycode=88` →
 * `02:36:21.716 MediaSessionService Sending KeyEvent { action=ACTION_UP, keyCode=KEYCODE_MEDIA_PREVIOUS … } to … youtube.music`
 * → hệ thống BẬT tiến trình YT Music (`MediaButtonReceiver`) và phiên phát thành active. Cơ chế [ĐO mã]: lượt học tắt cờ học
 * NGAY trên DOWN rồi trả `true`; UP cùng lần nhấn tới khi cờ đã tắt ⇒ đi đường thường ⇒ 88 chưa gán ⇒ matcher trả IGNORE ⇒
 * `super.onKeyEvent` ⇒ hệ thống nhận một UP mồ côi và giao cho app media. Phím âm lượng thì vô hại; phím media đổi trạng thái phát.
 *
 * Luật: sau khi học xong một DOWN, mọi sự kiện CÙNG lần nhấn đó — cùng `keyCode` VÀ cùng `downTime` (DOWN lặp khi giữ, UP) —
 * đều bị nuốt; UP kết thúc lần nhấn và xoá dấu. DOWN của lần nhấn MỚI (downTime khác) hoặc phím khác ⇒ không đụng tới (đường
 * thường). Cùng khoá `(keyCode, downTime)` của [VoiceKeyMatcher]; xoá ở UP vì cùng lý do (ROM có thể TÁI DÙNG `downTime` cho
 * lần nhấn sau — giữ dấu là nuốt nhầm lần nhấn ấy).
 *
 * Thuần, CÓ TRẠNG THÁI, gọi tuần tự từ MỘT luồng (`onKeyEvent` trên main).
 */
class KeyLearnTail {

    private var keyCode = NONE
    private var downTimeMs = 0L

    /** Vừa học xong DOWN này (cờ học đã tắt) ⇒ phần còn lại của lần nhấn phải bị nuốt. */
    fun learned(keyCode: Int, downTimeMs: Long) {
        this.keyCode = keyCode
        this.downTimeMs = downTimeMs
    }

    /**
     * `true` ⇒ sự kiện thuộc lần nhấn vừa học ⇒ nuốt (không tới matcher, không tới hệ thống). [action] UP kết thúc lần nhấn
     * (xoá dấu); DOWN của một lần nhấn khác cùng mã (downTime khác) cũng xoá dấu — lần nhấn cũ chắc chắn đã hết.
     */
    fun swallow(action: VoiceKeyAction, keyCode: Int, downTimeMs: Long): Boolean {
        if (this.keyCode == NONE || keyCode != this.keyCode) return false
        if (downTimeMs != this.downTimeMs) {
            if (action == VoiceKeyAction.DOWN) reset()
            return false
        }
        if (action == VoiceKeyAction.UP) reset()
        return true
    }

    /** Service (re)connect — một lần nhấn dở dang không dính sang phiên mới. */
    fun reset() {
        keyCode = NONE
        downTimeMs = 0L
    }

    private companion object {
        const val NONE = Int.MIN_VALUE
    }
}
