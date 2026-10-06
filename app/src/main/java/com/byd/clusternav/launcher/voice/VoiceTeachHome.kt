package com.byd.clusternav.launcher.voice

import android.content.Context
import com.byd.clusternav.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * ═══ 2.91 VOICE-APP-NAMES · A6 — LỐI VÀO (b) ngăn kéo + (c) tấm chữ ⇒ trang *Dạy tên app* ════════════════════════
 *
 * Spec R4 · §4.3 bảng *"Ba lối vào"*. Hai lối mới đều đi qua MỘT hành động [VoiceHomeAction.TEACH_APP] (tham số
 * [VoiceTeachHint.Request] mã hoá): `startActivity` về màn chính (singleTask ⇒ `onNewIntent` → `startVoiceIfRequested`
 * → `VoiceHomeActions.perform`) — CÙNG đường mà `:wake` dùng để trả việc cần Activity ([VoiceWakeHomeRelay.send]), nên
 * chạy được ở cả tiến trình chính lẫn `:wake` mà không có kênh thứ hai.
 */
internal object VoiceTeachHome {

    /** Mở trang *Dạy tên app* với yêu cầu [r] (từ tiến trình bất kỳ của gói). */
    fun open(ctx: Context, r: VoiceTeachHint.Request) {
        VoiceWakeHomeRelay(ctx.applicationContext ?: ctx).send(VoiceHomeAction.TEACH_APP, VoiceTeachHint.encode(r))
    }
}

/**
 * Yêu cầu dạy đang chờ màn Cài đặt mở ra (Activity nhận [VoiceHomeAction.TEACH_APP] → ghi ở đây → `HomePanels.openSettings`
 * lấy ra và mở trang). Một ô cho cả tiến trình chính; lấy là xoá (không mở lại trang ở lần mở Cài đặt sau).
 */
internal object VoiceTeachPending {
    private val slot = AtomicReference<VoiceTeachHint.Request?>(null)

    fun offer(r: VoiceTeachHint.Request) { slot.set(r) }

    fun take(): VoiceTeachHint.Request? = slot.getAndSet(null)
}

/**
 * OQ5 — câu ĐỌC *"Nếu đó là tên app, hãy dạy Kachi…"* chỉ đọc [VoiceTeachHint.SPOKEN_TIMES] lần đầu MỖI HỒ SƠ. Đếm trong
 * RAM của tiến trình (sai lệch spec có ghi: không lưu bền — `:wake` không được ghi prefs (R-nf2), và firmware BYD giết
 * cả hai tiến trình mỗi lần tắt máy ⇒ thực tế là "3 lần đầu mỗi chuyến"). Nút trên tấm chữ thì luôn hiện.
 */
internal object VoiceTeachHintCounter {
    private val spoken = ConcurrentHashMap<String, Int>()

    /** `true` = lượt này được đọc đuôi gợi ý (và đã đếm). */
    fun takeSpoken(profile: String): Boolean {
        var allowed = false
        spoken.compute(profile) { _, n -> val c = n ?: 0; if (c < VoiceTeachHint.SPOKEN_TIMES) { allowed = true; c + 1 } else c }
        return allowed
    }
}

/**
 * Lối (c): sau một câu *"mở …"* không hiểu, GẮN nút *"Dạy tên «…»"* lên tấm chữ [VoiceTeachHint.BUTTON_MS] (OQ5).
 * Một dòng gọi trong `VoiceSession.execute` (tệp ấy sát trần 500); chạm ⇒ đóng phiên + mở trang với mẫu đang chờ.
 */
internal fun VoiceSession.armTeachHint(intents: List<VoiceIntent>, heard: String) {
    val pending = VoiceTeachHint.pendingOf(intents, heard) ?: return
    val label = ctx.getString(R.string.kachi_vn_overlay_teach, pending)
    overlay?.arm(label, VoiceTeachHint.BUTTON_MS) {
        close()
        VoiceTeachHome.open(ctx, VoiceTeachHint.Request(sample = pending))
    }
}
