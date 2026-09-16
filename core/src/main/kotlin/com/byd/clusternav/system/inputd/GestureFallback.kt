package com.byd.clusternav.system.inputd

/**
 * ═══ ĐƯỜNG LÙI THEO CỬ CHỈ — gom DOWN…MOVE…UP thành ĐÚNG MỘT lệnh `input` ════════════════════════════════════
 *
 * PURE JVM (:core, không một `import android` nào) ⇒ test off-device.
 *
 * ## Bệnh nó chữa — [ĐO xe 2026-09-16] `docs/diagnostics/oncar-trace-2026-09-16b/README.md` §9.1
 * Owner: *"mở YouTube lên, không lướt để scroll được, nó chỉ nhận tap, mà tap thì lại không chính xác lắm"* —
 * và *"CPU về ổn nhưng vẫn không scroll được đâu, đừng assumption do CPU lag"* (CPU Kachi 0–7 % lúc đo).
 * Phép đo chốt: `adb shell input swipe 1000 850 1000 350 400` vào ô ⇒ app nhận **một cú tap** (mở luôn video).
 *
 * Gốc đọc từ mã (`VdAppHost.onTouchEvent` trước 1.69): daemon bơm chạm là đường **DUY NHẤT** mang MOVE, mà trên
 * xe daemon **không lên** (`I/Kachi/InputDaemonClient: daemon did not come up…` ở MỌI lần mở app). Đường lùi khi
 * đó bắn `input -d <id> tap x y` cho **cả** DOWN **lẫn** UP — tức **hai** cú tap cho một cú chạm (đúng triệu
 * chứng *"tap không chính xác"*) — còn MOVE thì rơi thẳng xuống đất (không có nhánh lùi nào).
 *
 * ## Máy trạng thái (một ngón, một cử chỉ)
 * ```
 *   IDLE ──DOWN(x0,y0,t0)──▶ TRACKING ──MOVE(x,y)──▶ TRACKING
 *     ▲                          │
 *     ├───────CANCEL─────────────┤
 *     └───────UP(x1,y1,t1) ⇒ PHÁT ĐÚNG MỘT LỆNH
 * ```
 * Tại UP, ba nhánh — theo thứ tự, nhánh đầu khớp là dừng:
 *  1. quãng (x0,y0)→(x1,y1) **> [touchSlopPx]** ⇒ `input -d <d> swipe x0 y0 x1 y1 <max(60, t1−t0)>`
 *  2. còn lại mà giữ **> [longPressTimeoutMs]** ⇒ `input -d <d> swipe x y x y <t1−t0>` (giữ tại chỗ; `input` không
 *     có lệnh "long press" nào khác — swipe cùng điểm đầu-cuối chính là cách `input` diễn đạt một cú giữ)
 *  3. còn lại ⇒ `input -d <d> tap x y` **MỘT lần**
 *
 * ## Ba quyết định có thể bị hỏi lại (ghi ra để người sau không phải đoán)
 *  • **Ngưỡng đo trên quãng ĐẦU→CUỐI, không phải quãng đã đi.** Chuẩn Android là *"vượt slop một lần là cuộn"*,
 *    nhưng lệnh phát ra ở đây chỉ mang được hai điểm: một cử chỉ đi xa rồi quay về chỗ cũ mà báo là `swipe
 *    x0 y0 x0 y0` thì chính `input` lại diễn giải thành một cú giữ — tức nói dối theo một kiểu khác. Đo đúng cái
 *    lệnh sẽ nói ra là cách duy nhất để lệnh không mâu thuẫn với phép đo.
 *  • **Toạ độ lấy ở điểm DOWN cho tap/giữ.** Đây là chỗ ngón tay thật sự nhấn; trong phạm vi slop thì UP chỉ khác
 *    vài pixel do rung tay, và lấy DOWN thì lệnh không phụ thuộc vào cái rung đó.
 *  • **Ngón thứ hai bị BỎ QUA, không huỷ cử chỉ.** `input` không diễn đạt được đa điểm chạm; huỷ luôn thì một cú
 *    chạm hờ của bàn tay cầm vô-lăng sẽ nuốt mất cú vuốt thật của ngón kia.
 *
 * ⚠ Toạ độ đưa vào đây phải là toạ độ **ĐÃ MAP** sang hệ màn ảo ([SlotTouchMapper.toDisplay]) — đường lùi cũ dùng
 * `x,y` thô của view, và đó là phần thứ hai của triệu chứng *"tap không chính xác"*.
 *
 * Mọi chuỗi lệnh dựng ở [TouchRouter] (một chỗ duy nhất, golden-lock byte) — lớp này chỉ giữ **quyết định**.
 */
class GestureFallback(
    /** `ViewConfiguration.get(ctx).scaledTouchSlop` — TIÊM từ chỗ gọi, KHÔNG viết cứng ở `:core` (theo mật độ máy). */
    private val touchSlopPx: Int,
    /** `ViewConfiguration.getLongPressTimeout()` — cùng lý do. */
    private val longPressTimeoutMs: Long,
) {

    companion object {
        /**
         * Gương của `android.view.MotionEvent.ACTION_*`. `:core` là JVM thuần nên **không** import android được;
         * bốn hằng này là hợp đồng nền tảng đã cố định từ API 1, và `GestureFallbackTest` khoá lại từng số một —
         * lệch một số là bài test đỏ ngay, không phải đợi tới lúc lên xe mới thấy cử chỉ câm.
         */
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP = 6

        /**
         * Sàn thời gian của một cú vuốt. `input swipe … 0` bị nền tảng nội suy thành hai sự kiện trùng thời điểm ⇒
         * app nhận một cú nhảy, không phải một cú cuộn. 60 ms ≈ 4 khung hình — đủ để bộ nhận cử chỉ của app thấy
         * có quãng đường, vẫn nhanh hơn mọi cú vuốt người thật làm được.
         */
        const val MIN_SWIPE_MS = 60L
    }

    private var tracking = false
    private var pointer = 0
    private var display = 0
    private var x0 = 0
    private var y0 = 0
    private var t0 = 0L

    /** Đang gom một cử chỉ dở dang không (chỉ để đo/log — quyết định KHÔNG bao giờ đọc cờ này từ ngoài). */
    fun isTracking(): Boolean = tracking

    /** Bỏ cử chỉ đang gom mà KHÔNG phát lệnh (daemon vừa sống lại giữa chừng · ô bị nhả · huỷ). */
    fun reset() {
        tracking = false
    }

    /**
     * Nạp một sự kiện chạm **đã map**. Trả chuỗi lệnh shell **DUY NHẤT** của cử chỉ tại UP, `null` ở mọi lúc khác.
     *
     * @param pointerId id của ngón ở **index 0** của sự kiện (`MotionEvent.getPointerId(0)`); mọi ngón khác bị bỏ.
     * @param timeMs `MotionEvent.getEventTime()` (uptime, đơn điệu) — KHÔNG phải giờ tường.
     */
    fun feed(action: Int, displayId: Int, x: Int, y: Int, timeMs: Long, pointerId: Int = 0): String? {
        when (action) {
            ACTION_DOWN -> {
                tracking = true
                pointer = pointerId
                display = displayId
                x0 = x
                y0 = y
                t0 = timeMs
            }
            ACTION_MOVE -> Unit          // chỉ cần điểm đầu + điểm cuối; giữ MOVE lại không đổi được lệnh phát ra
            ACTION_UP -> {
                if (!tracking) return null
                tracking = false
                // Ngón đầu đã rời trước (POINTER_UP index 0) ⇒ UP cuối cùng là của ngón KHÁC: không có cử chỉ nào
                // của ngón đầu để nói, và nói theo toạ độ ngón kia là bịa ra một cú chạm người dùng không làm.
                if (pointerId != pointer) return null
                return commandFor(x, y, timeMs)
            }
            ACTION_CANCEL -> tracking = false
            // ACTION_POINTER_DOWN / ACTION_POINTER_UP / mọi action lạ: ngón thứ hai bị BỎ QUA, cử chỉ của ngón
            // đầu chạy tiếp (xem KDoc lớp).
            else -> Unit
        }
        return null
    }

    private fun commandFor(x1: Int, y1: Int, t1: Long): String {
        val held = (t1 - t0).coerceAtLeast(0L)
        val dx = (x1 - x0).toLong()
        val dy = (y1 - y0).toLong()
        val slop = touchSlopPx.coerceAtLeast(0).toLong()
        return when {
            dx * dx + dy * dy > slop * slop ->
                TouchRouter.fallbackSwipeCmd(display, x0, y0, x1, y1, maxOf(MIN_SWIPE_MS, held))
            held > longPressTimeoutMs ->
                TouchRouter.fallbackSwipeCmd(display, x0, y0, x0, y0, held)
            else -> TouchRouter.fallbackTapCmd(display, x0, y0)
        }
    }
}
