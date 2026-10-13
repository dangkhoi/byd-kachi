package com.byd.clusternav.launcher

/**
 * ═══ 3.01 LAMPLIVE — NHU CẦU helper HAL, đếm theo NGUỒN + nhả sau thời gian ân hạn (thuần `:core`) ═══════════════════════
 *
 * Owner 13/10: *"nối luôn đi, đâu cần camera xi nhan bật mới sáng, tự nối lấy tín hiệu được mà"* — chấp nhận khởi helper uid-shell
 * khi cần. Trước đây MỘT nguồn duy nhất (công tắc camera xi-nhan) quyết `ensure`/`stop`; nay có HAI nguồn cùng cần socket 19322:
 *  - [Source.CAMERA] — công tắc camera xi-nhan BẬT (`CameraSignalController.tick`, đường cũ giữ nguyên chỗ gọi);
 *  - [Source.LAMP_WIDGET] — màn chính đang bày một datum đèn mà helper báo được ([LampSignalDemand.Watch]) VÀ màn xe sáng.
 *
 * Luật (một chỗ, test bằng đồng hồ giả):
 *  - tập nguồn rỗng → khác rỗng ⇒ huỷ hẹn nhả (nếu có); chưa chạy ⇒ [acquire] ĐÚNG MỘT LẦN. Hai nguồn chồng nhau = một lần.
 *  - khác rỗng → rỗng ⇒ HẸN [release] sau [graceMs] (lướt trang / mở Cài đặt / màn chớp tắt không khởi-dừng liên tục).
 *    Trong hạn có nguồn quay lại ⇒ không nhả, không acquire lại.
 *  - Bật/tắt lặp cùng một nguồn = no-op (đếm theo TẬP, không theo số lần gọi — một nguồn gọi `true` hai lần không giữ helper mãi).
 *
 * ⚠ [acquire]/[release] được gọi TRONG khoá ⇒ chúng PHẢI không chặn (bên `:app` chỉ đẩy việc vào executor một luồng của hub — thứ tự
 * đẩy = thứ tự chạy, nên acquire/release không bao giờ đảo nhau). [schedule] trả hàm huỷ; hẹn cũ lỡ chạy sau khi bị huỷ bị chặn bằng
 * số thế hệ.
 */
class HalSignalDemand(
    private val graceMs: Long,
    private val schedule: (delayMs: Long, task: () -> Unit) -> () -> Unit,
    private val acquire: () -> Unit,
    private val release: () -> Unit,
) {
    enum class Source { CAMERA, LAMP_WIDGET }

    private val wanted = mutableSetOf<Source>()
    private var active = false
    private var cancelPending: (() -> Unit)? = null
    private var gen = 0

    /** Nguồn [source] cần (true) / thôi cần (false) helper. Gọi từ luồng nào cũng được; không chặn. */
    @Synchronized
    fun set(source: Source, on: Boolean) {
        val changed = if (on) wanted.add(source) else wanted.remove(source)
        if (!changed) return
        if (wanted.isNotEmpty()) {
            cancelPending?.invoke()
            cancelPending = null
            gen++
            if (!active) { active = true; acquire() }
        } else if (active && cancelPending == null) {
            val g = ++gen
            cancelPending = schedule(graceMs) { expire(g) }
        }
    }

    @Synchronized
    private fun expire(g: Int) {
        if (g != gen || wanted.isNotEmpty() || !active) return
        cancelPending = null
        active = false
        release()
    }

    /** Helper + client đang được giữ (kể cả trong thời gian ân hạn). */
    @get:Synchronized
    val isActive: Boolean get() = active

    /** Bản sao tập nguồn đang cần — test/chẩn đoán. */
    @get:Synchronized
    val sources: Set<Source> get() = wanted.toSet()

    companion object {
        /** Ân hạn mặc định trước khi nhả: 60 s — đủ cho lướt trang/mở Cài đặt rồi quay lại mà không khởi-dừng client. */
        const val GRACE_MS = 60_000L
    }
}

/**
 * Màn chính có đang BÀY một đèn mà helper báo được không — suy từ CHÍNH tập nhu cầu H1 ([CarDataDemand.of]) mà vòng poll dùng, nên
 * bảng Đèn (`g_lights`), xe 3D (`w_car3d`), một ô/chip lẻ `light_*` đều tự tính, không danh sách widget thứ hai.
 */
object LampSignalDemand {

    /** Datum đèn có sự kiện ở helper (thang `type` của [LampLive]) + DRL. `headlight_feedback` KHÔNG có (không sự kiện). */
    val LAMP_IDS: Set<String> = setOf(
        "light_left_turn", "light_right_turn", "light_low_beam", "light_high_beam",
        "light_front_fog", "light_rear_fog", "light_side", "light_drl",
    )

    /**
     * `null` (= "đọc hết": màn chưa công bố / đã `onStop`, hoặc widget lạ) ⇒ KHÔNG cần: khởi một tiến trình uid-shell là tác dụng
     * phụ, chỉ làm khi CHẮC màn đang bày đèn (khác cổng poll — nơi `null` fail-open là hụt hiệu năng, không phải tác dụng phụ).
     */
    fun wants(demand: Set<String>?): Boolean = demand != null && demand.any { it in LAMP_IDS }

    /**
     * Gộp "màn bày đèn" ([demand], mỗi lượt state) với "màn xe sáng" ([screen], mỗi lượt cổng màn của vòng poll) ⇒ gọi [sink] CHỈ
     * khi kết quả ĐỔI. Mặc định màn sáng (giống `ScreenLit`: không hỏi được ⇒ sáng).
     */
    class Watch(private val sink: (Boolean) -> Unit) {
        private var shown = false
        private var lit = true
        private var last = false

        @Synchronized fun demand(d: Set<String>?) { shown = wants(d); push() }

        @Synchronized fun screen(on: Boolean) { lit = on; push() }

        private fun push() {
            val w = shown && lit
            if (w == last) return
            last = w
            sink(w)
        }
    }
}
