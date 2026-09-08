package com.byd.clusternav.modules.voicekey

import android.os.Handler
import android.os.Looper

/**
 * Cầu nối TRONG-TIẾN-TRÌNH cho luồng "Học phím mới": NavAccessibilityService (bắt KeyEvent) → MainActivity
 * (đang mở màn) hiện dialog đặt tên. Service và Activity cùng tiến trình app nên dùng callback trực tiếp —
 * KHÔNG dùng LocalBroadcastManager (đã deprecated). Activity đăng ký [setListener] khi foreground.
 *
 * Nếu service bắt được mã lúc Activity chưa kịp đăng ký → nhớ [pending], giao ngay khi listener được set.
 */
object VoiceKeyLearnBus {
    @Volatile private var listener: ((Int) -> Unit)? = null
    @Volatile private var pending: Int? = null
    private val main = Handler(Looper.getMainLooper())

    fun setListener(l: ((Int) -> Unit)?) {
        listener = l
        val p = pending
        if (l != null && p != null) { pending = null; main.post { l(p) } }
    }

    /** Service gọi khi bắt được keycode trong lúc "học phím". */
    fun publish(code: Int) {
        val l = listener
        if (l != null) main.post { l(code) } else pending = code
    }
}
