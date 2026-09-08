package com.byd.clusternav.body

import android.content.Context
import android.util.Log
import com.byd.clusternav.modules.hal.BydHal

/**
 * ĐIỀU KHIỂN CỬA SỔ / CỐP qua HAL `BYDAutoBodyworkDevice` — đi ĐƯỜNG REFLECTION SẴN CÓ của ClusterNav
 * ([BydHal.device] + [BydHal.callNamedInt]) như [com.byd.clusternav.comfort.Pm25FilterApplier], KHÔNG raw
 * feature-id (windows/trunk là method-TÊN — xem [Bodywork] + RE doc
 * `docs/diagnostics/bodywork-window-trunk-RE-2026-09-06.md`).
 *
 * ── An toàn (degrade-safe) ───────────────────────────────────────────────────────────────────────
 * Mỗi thao tác chạy trên THREAD NỀN (HAL + reflection off main), toàn bộ bọc `runCatching`. Off-car / không
 * có HAL → `device()` trả null → log rồi return, KHÔNG ném, KHÔNG crash (bộ test off-car dựa vào điều này).
 * Mỗi lời gọi có [Log] tag "Bodywork" (method/args/rc) để owner xác minh trên xe bằng `logcat -s Bodywork`.
 *
 * ⚠ KHÔNG UI, KHÔNG layout, CHƯA wire vào MainActivity ở phiên này. Gate an toàn (mở cửa/cốp khi đang chạy =
 * rủi ro → chặn theo tốc độ / số P) là **quyết định của owner** khi ráp UI sau. [windowPermitted] có sẵn để
 * đọc quyền điều khiển của xe trước khi ghi.
 */
object BodyworkControl {

    const val TAG = "Bodywork"

    /** Mở 1 cửa sổ ([Bodywork.WINDOW_LF]..[Bodywork.WINDOW_RR]). Nền, degrade-safe. */
    fun openWindow(ctx: Context, window: Int) = setWindow(ctx, window, open = true)

    /** Đóng 1 cửa sổ. Nền, degrade-safe. */
    fun closeWindow(ctx: Context, window: Int) = setWindow(ctx, window, open = false)

    /** Mở/đóng CẢ 4 cửa qua `setAllWindowState(lf,rf,lr,rr)` (mỗi arg cùng state). Nền, degrade-safe. */
    fun setAllWindows(ctx: Context, open: Boolean) {
        val state = Bodywork.windowState(open)
        run(ctx, "set-all-windows") { dev ->
            val rc = BydHal.callNamedInt(dev, Bodywork.METHOD_SET_ALL_WINDOWS, state, state, state, state)
            Log.i(TAG, "${Bodywork.METHOD_SET_ALL_WINDOWS}(state=$state ×4) $rc")
        }
    }

    /** Mở cốp (`setHetchDoorStatus(HATCH_OPEN)`). Nền, degrade-safe. */
    fun openTrunk(ctx: Context) = setHatch(ctx, open = true)

    /** Đóng cốp (`setHetchDoorStatus(HATCH_CLOSE)`). Nền, degrade-safe. */
    fun closeTrunk(ctx: Context) = setHatch(ctx, open = false)

    /**
     * Đọc quyền điều khiển cửa sổ (`getWindowPermitState()`) → log kết quả (nền). Xe có thể cấm điều khiển khi
     * đang chạy / khoá trẻ em; UI sau này nên đọc trước khi ghi. Degrade-safe (off-car → "null").
     */
    fun windowPermitted(ctx: Context) {
        run(ctx, "window-permit") { dev ->
            val v = BydHal.callGetter(dev, Bodywork.METHOD_GET_WINDOW_PERMIT) ?: "null"
            Log.i(TAG, "${Bodywork.METHOD_GET_WINDOW_PERMIT}()=$v")
        }
    }

    private fun setWindow(ctx: Context, window: Int, open: Boolean) {
        if (!Bodywork.isWindow(window)) {
            Log.w(TAG, "window không hợp lệ: $window (cần ${Bodywork.WINDOW_LF}..${Bodywork.WINDOW_RR})")
            return
        }
        val state = Bodywork.windowState(open)
        run(ctx, "window-$window") { dev ->
            val rc = BydHal.callNamedInt(dev, Bodywork.METHOD_SET_WINDOW, window, state)
            Log.i(TAG, "${Bodywork.METHOD_SET_WINDOW}(window=$window, state=$state) $rc")
        }
    }

    private fun setHatch(ctx: Context, open: Boolean) {
        val status = Bodywork.hatchStatus(open)
        run(ctx, "hatch") { dev ->
            val rc = BydHal.callNamedInt(dev, Bodywork.METHOD_SET_HATCH, status)
            Log.i(TAG, "${Bodywork.METHOD_SET_HATCH}(status=$status) $rc")
        }
    }

    /**
     * Resolve BODYWORK device trên THREAD NỀN rồi chạy [block]; off-car / không có HAL → log + bỏ qua. Toàn bộ
     * degrade-safe (KHÔNG ném). [threadName] chỉ để đặt tên thread + log ngữ cảnh.
     */
    private fun run(ctx: Context, threadName: String, block: (Any) -> Unit) {
        val app = ctx.applicationContext
        Thread({
            runCatching {
                val dev = BydHal.device(BydHal.BODYWORK, BydHal.systemBypassContext(), BydHal.bypass(app))
                if (dev == null) {
                    Log.i(TAG, "BodyworkDevice null (off-car / no HAL) — bỏ $threadName")
                    return@runCatching
                }
                block(dev)
            }.onFailure { Log.w(TAG, "$threadName thất bại (degrade-safe, bỏ qua)", it) }
        }, "bodywork-$threadName").start()
    }
}
