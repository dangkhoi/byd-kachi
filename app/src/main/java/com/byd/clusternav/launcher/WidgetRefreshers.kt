package com.byd.clusternav.launcher

import android.view.View
import com.byd.clusternav.R

/**
 * ═══ SỔ ĐĂNG KÝ "ĐỔ GIÁ TRỊ TẠI CHỖ" CHO Ô GIỮA MÀN ═════════════════════════════════════════════════════════
 *
 * Hai đường `view → hàm đổ`, giữ **trên chính view** của ô con:
 *  • [live]/[refresh] — ô **ĐỌC** (widget dựng tay + telemetry), hàm đổ nhận cả gói [WidgetData] (trạng thái xe ·
 *    nhạc · đơn vị · ảnh) vì một ô có thể cần nhiều nguồn cùng lúc (bảng tổng hợp đọc xe **và** nhạc);
 *  • [liveAction]/[refreshAction] — ô **HÀNH ĐỘNG**, chỉ cần [CarStatus] (nó đọc lại giá trị thật của nút).
 *
 * ## ⚠⚠ Vì sao sổ này tồn tại — owner 2026-09-21
 * Nguyên văn: *"widget curated như Áp suất lốp refresh lấy số mới bị GIẬT"*. [WidgetViews.refreshRead] trước đây làm
 * mới một ô ĐỌC bằng cách **dựng lại view rồi thay vào chỗ cũ** (`removeViewAt` + `addView`). Trên xe trạng thái đổi
 * **1 nhịp/giây**, nên mỗi giây một ô curated bị tháo khỏi cây view và gắn lại: khung mới phải đo–đặt–vẽ từ đầu, ô vẽ
 * Canvas mất ảnh xe đang nạp ([CarImageLayer] nạp lại), và mắt thấy đúng một cú **giật**.
 *
 * Ô **HÀNH ĐỘNG** và ô **NHÓM** đã tránh được chuyện này từ trước (chúng đổ chữ tại chỗ qua bảng riêng / `binders`);
 * sổ này mang cùng cách làm sang ô ĐỌC — tức là nó không phát minh cơ chế mới, nó **trải rộng cơ chế đã đúng**.
 *
 * ## ⚠⚠ Vì sao giữ trên VIEW (`setTag`) chứ KHÔNG phải một `WeakHashMap<View, …>`
 * [SOÁT 2026-09-21 · P1] Bản đầu của lượt này dùng `WeakHashMap<View, (WidgetData) -> Unit>` với lý do *"ô bị gỡ
 * khỏi cây là mục tự rụng, không rò Context"*. **Lý do đó sai**, và sai theo cách không nhìn thấy được: mọi hàm đổ
 * đều **bắt chính view** mà nó đổ vào (`fillEnergy` giữ `card`, `fillTyreBoard` giữ `boardView`, `fillSpeed` giữ
 * `number` → `mParent` → root…). Trong `WeakHashMap`, **giá trị được giữ MẠNH**; một giá trị trỏ về khoá của nó làm
 * khoá **không bao giờ** trở nên weakly-reachable ⇒ mục không bao giờ bị dọn. Đây là cảnh báo có sẵn trong tài liệu
 * `java.util.WeakHashMap` (*"value objects must not strongly refer to their own keys"*). Vì [WidgetViews] là `object`,
 * sổ sống cả tiến trình ⇒ **mỗi** ô từng dựng + `Context` của nó bị giữ vĩnh viễn, và launcher dựng lại cây ô ở mọi
 * lượt đổi bố cục / đổi hồ sơ / `recreate` khi đổi giao diện-ngôn ngữ. Trên đầu xe còn 56–94 MB trống thì đó là rò
 * thật, không phải rò lý thuyết.
 *
 * Giữ hàm đổ bằng `setTag(R.id.…)` đảo đúng chiều tham chiếu: **view giữ hàm đổ**, nên cả vòng (view → hàm đổ →
 * view) chết cùng lúc khi ô bị gỡ khỏi cây — không cần ai đi xoá sổ, và cũng không còn bảng dùng chung nào để lo
 * chuyện nhiều luồng. Đây là đúng khuôn [KachiGlass] đã dùng cho trạng thái-theo-view (`kachi_glass_spec`), tức
 * không phải một cơ chế thứ hai của dự án.
 *
 * ## Bất biến (vi phạm là mở lại đúng cú giật qua một cửa khác)
 *  1. Hàm đổ **KHÔNG được dựng View mới** — chỉ `setText` / `set(...)` / `invalidate` trên view đã có. Thứ gì cố
 *     định theo mã khả năng (nhãn · hình · dấu *"chưa kiểm"* · lưới ô con) phải dựng ở hàm dựng, KHÔNG ở hàm đổ.
 *  2. Ô chưa đăng ký ⇒ [refresh] trả `false` ⇒ chỗ gọi **lùi về** đường dựng lại cũ. Nhờ vậy thêm một bộ vẽ mà quên
 *     đăng ký thì ô đó chỉ mất tính mượt, **không bao giờ câm** — đúng hướng suy giảm mà dự án chọn ở mọi cổng khác.
 *  3. Hai khoá tag là **của riêng sổ này**. Đừng đọc/ghi chúng ở tệp khác: cả điểm đăng ký lẫn điểm đổ phải đi qua
 *     bốn hàm dưới đây, nếu không thì "ai đang đổ ô này" lại thành một câu hỏi phải đi tìm.
 */
internal object WidgetRefreshers {

    /**
     * Bọc hàm đổ trong một lớp riêng thay vì đặt thẳng lambda vào tag: `getTag` trả `Any?`, nên `as?` xuống một
     * kiểu hàm đã bị xoá generic (`(WidgetData) -> Unit`) là một phép ép **không kiểm được lúc chạy** — hai sổ sẽ
     * nhận nhầm nhau nếu ai đó dùng sai khoá. Hai lớp riêng làm phép ép ấy thành thật.
     */
    private class ValueFill(val fn: (WidgetData) -> Unit)

    private class ActionFill(val fn: (CarStatus) -> Unit)

    /**
     * Đăng ký đường đổ giá trị cho ô [view] rồi trả lại **chính nó**, để bộ vẽ `return` thẳng một dòng.
     *
     * ⚠ Cố ý **KHÔNG** tự gọi [fill] một lượt mồi: mọi bộ vẽ đã có sẵn dữ liệu của lượt dựng (nó vừa nhận
     * `car`/`data` làm tham số), nên bắt nó dựng thêm một gói [WidgetData] chỉ để mồi lần đầu là thêm một đối tượng
     * rác cho mỗi ô mỗi lượt. Bộ vẽ tự gọi hàm đổ của nó **trước** khi đăng ký.
     */
    fun <V : View> live(view: V, fill: (WidgetData) -> Unit): V {
        view.setTag(R.id.kachi_widget_fill, ValueFill(fill))
        return view
    }

    /** Đổ lại giá trị cho ô [view]. `false` = ô chưa có đường đổ tại chỗ ⇒ chỗ gọi lùi về dựng lại cả ô con. */
    fun refresh(view: View, data: WidgetData): Boolean {
        val fill = view.getTag(R.id.kachi_widget_fill) as? ValueFill ?: return false
        fill.fn(data)
        return true
    }

    /** Như [live] nhưng cho ô HÀNH ĐỘNG: nó chỉ cần đọc lại trạng thái nút, không cần cả gói dữ liệu render. */
    fun liveAction(view: View, fill: (CarStatus) -> Unit) {
        view.setTag(R.id.kachi_widget_action_fill, ActionFill(fill))
    }

    /** Đọc lại giá trị thật của ô HÀNH ĐỘNG [view]. `false` = ô không có đường đọc (vd gói lệnh: không có số nào). */
    fun refreshAction(view: View, car: CarStatus): Boolean {
        val fill = view.getTag(R.id.kachi_widget_action_fill) as? ActionFill ?: return false
        fill.fn(car)
        return true
    }
}
