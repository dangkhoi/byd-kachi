package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/**
 * ═══ 3.1 · R3 — NHẤN GIỮ nút −/+ thì lặp bước (spec `kachi-301-cast-steps-dialog-ia.html` §3 R3) ═══════════════════════
 *
 * Chỉnh mép 200 px bằng bước 20 là 10 chạm trên màn xe; giữ nút thì sau [FIRST_DELAY_MS] lặp mỗi [REPEAT_MS] một bước.
 *
 * ## Vì sao gắn TỪ NGOÀI, không sửa `SettingsRows.stepperRow`
 * `SettingsRows.kt` sát trần 500 dòng và hàng −/+ dùng ở nhiều nhóm (cỡ biển báo, ghế, tỉ lệ…) — nhấn giữ chỉ có nghĩa ở
 * khung chiếu (dải 0–2880 px). Tệp này nhận hàng ĐÃ dựng ([SettingsRows.Stepper.view]) và tìm hai nút theo hợp đồng của nó:
 * con BẤM ĐƯỢC (`isClickable`) thứ nhất = −, cuối = + (nhãn và ô số không bấm được). Không đúng 2 con bấm được ⇒ KHÔNG gắn
 * (chạm đơn vẫn chạy như cũ) — `SettingsRepeatTouchContractTest` canh thứ tự đó ở `stepperRow`.
 *
 * ## Không có cú bấm thừa khi nhả
 * Đã lặp ít nhất một lần thì lúc nhả tay ta nuốt `ACTION_UP` và đưa `ACTION_CANCEL` cho chính view (xoá trạng thái nhấn) —
 * không thì `View.onTouchEvent` phát thêm một `performClick` = thêm một bước người lái không muốn. Ngón trượt khỏi nút / trang
 * cuộn giành chạm (`ACTION_CANCEL`) ⇒ dừng lặp ngay. Chạm ngắn (< [FIRST_DELAY_MS]) đi đúng đường click cũ.
 *
 * Hiệu năng (memory perf-every-fix): 0 luồng, 0 nhịp nền — chỉ một `postDelayed` trên luồng chính TRONG LÚC ngón tay đang giữ.
 */
object SettingsRepeatTouch {

    /** Giữ bao lâu thì bắt đầu lặp — ngắn hơn long-press mặc định (500 ms) một chút để cảm giác "ăn" ngay. */
    const val FIRST_DELAY_MS = 400L

    /** Nhịp lặp (R3: "mỗi ~150 ms một bước"). */
    const val REPEAT_MS = 150L

    /**
     * Gắn nhấn-giữ cho hai nút của một hàng −/+ đã dựng. [onMinus]/[onPlus] là CÙNG hành động của chạm đơn. Trả `false` (và
     * không gắn gì) khi hàng không đúng hình dạng mong đợi.
     */
    fun attachToStepper(row: View, onMinus: () -> Unit, onPlus: () -> Unit): Boolean {
        val group = row as? ViewGroup ?: return false
        val buttons = (0 until group.childCount).map { group.getChildAt(it) }.filter { it.isClickable }
        if (buttons.size != 2) return false
        attach(buttons[0], onMinus)
        attach(buttons[1], onPlus)
        return true
    }

    @SuppressLint("ClickableViewAccessibility")   // chạm đơn vẫn đi `performClick` của view (đường a11y không đổi)
    fun attach(button: View, step: () -> Unit) {
        var repeated = false
        val tick = object : Runnable {
            override fun run() {
                // Review 3.1 Pass 2 [P3]: nút đã rời cửa sổ (trang/hộp dựng lại giữa lúc giữ) ⇒ dừng — nhịp đã xếp trên Handler
                // không tự huỷ theo view, mà mỗi bước có thể là một lệnh `am task resize`.
                if (!button.isAttachedToWindow || !button.isEnabled || !button.isPressed) return
                repeated = true
                step()
                button.postDelayed(this, REPEAT_MS)
            }
        }
        button.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeated = false
                    v.removeCallbacks(tick)
                    v.postDelayed(tick, FIRST_DELAY_MS)
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ev.x < 0 || ev.y < 0 || ev.x > v.width || ev.y > v.height) v.removeCallbacks(tick)
                    false
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(tick)
                    if (repeated) cancelClick(v, ev) else false
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(tick)
                    false
                }
                else -> false
            }
        }
    }

    /** Nuốt `ACTION_UP` sau khi đã lặp: đưa `ACTION_CANCEL` cho view để nó bỏ trạng thái nhấn mà không phát click. */
    private fun cancelClick(v: View, up: MotionEvent): Boolean {
        val cancel = MotionEvent.obtain(up)
        cancel.action = MotionEvent.ACTION_CANCEL
        v.onTouchEvent(cancel)
        cancel.recycle()
        return true
    }
}
