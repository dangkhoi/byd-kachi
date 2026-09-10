package com.byd.clusternav.comfort

import android.content.Context
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Prefs

/**
 * TỰ LẤY GIÓ TRONG KHI NỔ MÁY (W3) — spec `docs/specs/kachi-unified-capability-tile.html` §4.5.
 *
 * Bệnh nó chữa: xe **quên** chế độ lấy gió mỗi lần khởi động, nên đi trong phố mặc định hít gió ngoài (khói, bụi).
 * Owner muốn một ô tick "nổ máy thì tự lấy gió trong".
 *
 * ## Bám ĐÚNG mẫu đã chạy, không phát minh đường mới
 * Cùng hình dạng với [SeatComfortApplier] / [Pm25FilterApplier]: khoá lưu bền mặc định TẮT ([Prefs.recircOnStartEnabled])
 * → [applyOnStart] gọi từ `BootSetupService` → làm việc trên **thread nền** sau một khoảng trễ để HAL kịp sẵn sàng.
 *
 * ## Đường ghi: DÙNG LẠI, không viết lại lệnh HAL
 * Lệnh đi qua [AppContainer.carControl] (tức `CarControlAdapter` → `HalBindingTable` → `BydHalGateway`) thay vì gọi
 * `BydHal` trực tiếp như hai bộ áp dụng cũ. Lý do: mã `"recirc"` và feature-id của nó **đã khai một chỗ** trong
 * `ControlRegistry`; gọi HAL trực tiếp ở đây là nhân bản kiến thức binding, để rồi hai chỗ lệch nhau khi trim đổi.
 *
 * ## ⚠ KHÔNG HỨA nó chạy được
 * `ControlDef("recirc")` ở mức [com.byd.clusternav.launcher.EvidenceTier.OVERDRIVE] — đọc từ mã nguồn dự án khác,
 * **CHƯA kiểm trên xe owner** (khác ghế mát / lọc bụi = đã chạy thật). Vì vậy:
 *  • UI phải mang dấu "chưa kiểm trên xe" (spec R10);
 *  • thất bại chỉ ghi log, **không** được kéo sập các bước khởi động khác;
 *  • [applyOnStart] trả `Unit` — không có đường báo "đã lấy gió thành công" cho người dùng, vì off-car không phân
 *    biệt được "xe từ chối" với "chưa lên xe".
 */
object RecircApplier {

    const val TAG = "RecircOnStart"

    /** Mã nút trong `ControlRegistry` — một nguồn sự thật cho cả UI lẫn bộ áp dụng này. */
    const val CONTROL_ID = "recirc"

    /** Trễ sau khi khởi động, khớp [SeatComfortApplier.START_DELAY_MS] để cabin/HAL sẵn sàng trước khi ghi. */
    const val START_DELAY_MS = 5_000L

    /**
     * Gọi lúc nổ máy / mở app. KHÔNG làm gì nếu công tắc TẮT (mặc định) ⇒ máy mới cài không bao giờ tự đụng HAL.
     * Chạy trên thread nền vì có ngủ chờ + ghi HAL (blocking).
     */
    fun applyOnStart(ctx: Context) {
        if (!Prefs.recircOnStartEnabled(ctx)) return
        val app = ctx.applicationContext
        Thread({
            runCatching { Thread.sleep(START_DELAY_MS) }
            applyNow(app)
        }, "recirc-on-start").start()
    }

    /**
     * Người dùng vừa BẬT ô tick → áp ngay, không chờ lần nổ máy sau (nền, degrade-safe).
     * KHÔNG gate theo công tắc: chỗ gọi vừa đặt nó về true.
     */
    fun applyNowAsync(ctx: Context) {
        val app = ctx.applicationContext
        Thread({ applyNow(app) }, "recirc-apply-now").start()
    }

    /**
     * Bật chế độ lấy gió trong MỘT lần. Mọi lỗi bị bắt tại đây (degrade-safe) — chuỗi khởi động gọi nó không được
     * chết vì HAL từ chối.
     *
     * @return `true` nếu HAL nhận lệnh; `false` nếu off-car / xe từ chối / chưa provision. Trả về để test dùng, chỗ
     *   gọi trong luồng khởi động bỏ qua.
     */
    fun applyNow(ctx: Context): Boolean = runCatching {
        val ok = AppContainer.get(ctx.applicationContext).carControl.toggle(CONTROL_ID, true)
        if (ok) Log.i(TAG, "đã bật lấy gió trong")
        else Log.w(TAG, "HAL không nhận lệnh lấy gió trong (off-car, hoặc trim chưa provision — mã chưa kiểm trên xe)")
        ok
    }.getOrElse {
        Log.w(TAG, "lỗi khi bật lấy gió trong: ${it.javaClass.simpleName}")
        false
    }
}
