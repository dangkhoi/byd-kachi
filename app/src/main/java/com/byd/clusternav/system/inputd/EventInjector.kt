package com.byd.clusternav.system.inputd

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent

/**
 * Bơm [TouchFrame] thành `MotionEvent` vào ĐÚNG display đích bằng HIDDEN API qua PHẢN CHIẾU (chạy trong tiến trình
 * uid-2000 shell → có `INJECT_EVENTS` + thoát hidden-API enforcement):
 *   • `InputManager.getInstance()` (Android ≤ 12) hoặc `InputManagerGlobal.getInstance()` (Android 13+) → instance.
 *   • `injectInputEvent(InputEvent, int mode = ASYNC)`.
 *   • `MotionEvent.setDisplayId(int)` để đóng sự kiện vào ĐÚNG VirtualDisplay của ô (không phải display chính).
 *
 * Ghép DOWN/UP theo `downTime` per-display để thành MỘT tap đúng (khác `input -d tap` cũ bắn 2 lần → double-fire),
 * và cho MOVE mượt. Mọi call phản chiếu bọc `runCatching` → hỏng 1 sự kiện KHÔNG làm sập daemon.
 */
class EventInjector(private val log: (String) -> Unit = {}) {

    /** `InputManager.INJECT_INPUT_EVENT_MODE_ASYNC` = 0 (không chờ finish → độ trễ thấp). */
    private val injectModeAsync = 0

    /** downTime theo từng display, để ghép cặp DOWN→MOVE→UP thành một cử chỉ. */
    private val downTimes = HashMap<Int, Long>()

    private val inputManager: Any? = resolveInputManager()
    private val injectMethod = inputManager?.let { im ->
        runCatching {
            im.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        }.getOrNull()
    }
    private val setDisplayId = runCatching {
        MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }.getOrNull()

    fun inject(frame: TouchFrame) {
        val now = SystemClock.uptimeMillis()
        val downTime = when (frame.action) {
            MotionEvent.ACTION_DOWN -> now.also { downTimes[frame.displayId] = it }
            else -> downTimes[frame.displayId] ?: now
        }
        val event = MotionEvent.obtain(downTime, now, frame.action, frame.x.toFloat(), frame.y.toFloat(), 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        runCatching { setDisplayId?.invoke(event, frame.displayId) }
            .onFailure { log("setDisplayId failed: ${it.message}") }
        runCatching { injectMethod?.invoke(inputManager, event, injectModeAsync) }
            .onFailure { log("inject failed: ${it.message}") }
        event.recycle()
        if (frame.action == MotionEvent.ACTION_UP || frame.action == MotionEvent.ACTION_CANCEL) {
            downTimes.remove(frame.displayId)
        }
    }

    private fun resolveInputManager(): Any? {
        // Android ≤ 12: InputManager.getInstance()  (đường của BYD DiLink / Android 10).
        runCatching {
            return Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null)
        }
        // Android 13+: InputManagerGlobal.getInstance()  (best-effort cho nền tảng mới).
        runCatching {
            return Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null)
        }
        log("no InputManager instance resolvable")
        return null
    }
}
