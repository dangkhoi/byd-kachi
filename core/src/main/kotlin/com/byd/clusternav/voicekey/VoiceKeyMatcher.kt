package com.byd.clusternav.voicekey

/**
 * LOGIC THUẦN (không Android): một sự kiện phím vật lý có (a) kích hoạt mở app đích không, (b) mở đích NÀO,
 * và (c) có "nuốt" (consume) sự kiện đó không.
 *
 * Rework 1.19 (owner 2026-08-14): **BỎ khái niệm cử chỉ (Nhấn/Nhấn-giữ) + mốc thời-gian-giữ.** Trên xe này
 * nút phát KEYCODE KHÁC NHAU cho nhấn-ngắn vs nhấn-giữ, nên phân biệt bằng thời gian giữ là thừa và gây lỗi
 * (giữ > 500ms từng làm PRESS từ chối bắn → phím lọt → hệ thống mở nhầm trợ lý Gemini). Giờ đơn giản:
 * đúng keycode đã cấu hình → **BẮN 1 lần** (trên DOWN đầu của mỗi lần nhấn) + **NUỐT trọn** phím đó
 * (DOWN/UP/repeat); keycode khác hoặc tính năng tắt → **pass-through hoàn toàn** (giữ chức năng gốc).
 *
 * F3 (owner 2026-08-24): đổi từ "so với MỘT mã" sang **tra DANH SÁCH gán** (nhiều phím → nhiều app).
 * Máy trạng thái chống-bắn-lặp GIỮ NGUYÊN (`CLAUDE.md §6` — không đảo đường đang chạy tốt ngoài hiện
 * trường), chỉ khoá theo **(keyCode, downTime)** thay vì downTime đơn lẻ: hai phím đã gán được bấm gần
 * nhau có thể trùng `downTime` ở độ phân giải ms, mà khoá chung một ô thì phím thứ hai bị nuốt mất lần bắn.
 *
 * CÓ TRẠNG THÁI (mỗi lần nhấn chỉ bắn 1 lần). Gọi tuần tự từ 1 luồng (onKeyEvent main).
 */
enum class VoiceKeyAction { DOWN, UP, OTHER }

/**
 * Cấu hình tối thiểu: bật/tắt + **danh sách gán** (mã phím → đích).
 *
 * Ý nghĩa của chuỗi đích (package name hay sentinel) do tầng app quyết định, KHÔNG ở `:core` — ở đây nó chỉ
 * là dữ liệu đi kèm được chuyển tiếp nguyên vẹn ra [VoiceKeyDecision.targetSpec].
 */
data class VoiceKeyConfig(val enabled: Boolean, val bindings: List<VoiceKeyBinding>) {
    /** Tra đích — tất định vì `VoiceKeyBindings` cưỡng chế mỗi mã phím chỉ gán MỘT đích. */
    fun targetFor(keyCode: Int): String? = VoiceKeyBindings.targetFor(bindings, keyCode)
}

/**
 * @property fire true → tầng app phóng intent mở [targetSpec].
 * @property consume true → [android.accessibilityservice.AccessibilityService.onKeyEvent] trả true (chặn hệ
 *   thống xử lý phím). Chỉ true cho phím CÓ trong danh sách gán.
 * @property targetSpec đích của phím vừa bắn. **Bất biến: `fire` ⟺ `targetSpec != null`** — tầng app không
 *   phải tra lại danh sách (tra hai lần = hai kết quả nếu prefs đổi giữa chừng).
 */
data class VoiceKeyDecision(val fire: Boolean, val consume: Boolean, val targetSpec: String? = null) {
    companion object { val IGNORE = VoiceKeyDecision(fire = false, consume = false) }
}

class VoiceKeyMatcher {

    // (keyCode → downTime của lần nhấn đã bắn) — chống bắn lặp (DOWN-repeat rồi UP trong cùng một lần nhấn).
    // Kích thước bị chặn bởi số mã phím ĐÃ GÁN từng được bấm (phím không gán không bao giờ tới được đây).
    private val firedDownTime = HashMap<Int, Long>()

    fun onKey(cfg: VoiceKeyConfig, action: VoiceKeyAction, keyCode: Int, downTimeMs: Long): VoiceKeyDecision {
        if (!cfg.enabled) return VoiceKeyDecision.IGNORE
        val target = cfg.targetFor(keyCode) ?: return VoiceKeyDecision.IGNORE
        return when (action) {
            VoiceKeyAction.DOWN -> {
                val fire = firedDownTime[keyCode] != downTimeMs   // bắn 1 lần cho mỗi lần nhấn (theo downTime)
                if (fire) firedDownTime[keyCode] = downTimeMs
                VoiceKeyDecision(fire = fire, consume = true, targetSpec = if (fire) target else null)
            }
            // Nuốt UP/OTHER của phím đã gán để phím không lọt xuống hệ thống (khỏi mở nhầm trợ lý mặc định).
            VoiceKeyAction.UP, VoiceKeyAction.OTHER -> VoiceKeyDecision(fire = false, consume = true)
        }
    }

    /** Reset trạng thái (gọi khi service (re)connect) để một lần nhấn dở dang không dính sang phiên mới. */
    fun reset() { firedDownTime.clear() }
}
