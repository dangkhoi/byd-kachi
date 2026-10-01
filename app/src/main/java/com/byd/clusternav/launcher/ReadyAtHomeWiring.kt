package com.byd.clusternav.launcher

import android.app.Activity
import android.os.Handler
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.byd.clusternav.ShellReadiness
import com.byd.clusternav.carexec.ShellReadinessState

/**
 * ═══ READY-AT-HOME §4.7/§4.9 — NỐI DÂY phía màn chính (tách khỏi `KachiHomeActivity`, trần 500 dòng) ════════════
 *
 * Ba việc, đều chỉ là LỐI VÀO THÊM — F4 và thân `bringUpShellChannel` không đổi (CLAUDE.md §6):
 *  1. Kênh vừa lên ở tầng tiến trình ([ShellReadiness]) ⇒ [ShellChannelGate.adopt] trên luồng chính (adopt tự kiểm đủ
 *     điều kiện: khung đã vẽ, màn hiện, màn tương tác, chưa nhận, không có lượt F4 đang bay).
 *  2. Trạng thái kênh đổi / HOME lấy lại tiêu điểm ⇒ thẻ xin quyền ([ShellAccessUi]) tự hiện hoặc tự biến mất.
 *  3. Vòng đời: gỡ bên nghe + quên màn ở `ON_DESTROY` (không giữ Activity chết trong một object sống suốt tiến trình).
 */
internal fun Activity.wireReadyAtHome(owner: LifecycleOwner, gate: ShellChannelGate, root: FrameLayout, handler: Handler) {
    val activity = this
    ShellAccessUi.attach(activity, root, gate)
    // Review lượt 3 [P3]: vẽ theo trạng thái MỚI NHẤT lúc chạy trên luồng chính, không theo giá trị chụp lúc báo — hai lần
    // chuyển từ hai luồng khác nhau có thể gọi bên nghe ngược thứ tự (`ShellReadiness.apply` gọi bên nghe ngoài khoá) ⇒
    // màn chính vẽ trạng thái cũ (thẻ "cần quyền" khi kênh đã lên, hoặc ngược lại) tới lần đổi kế.
    val onState: (ShellReadinessState) -> Unit = { _ ->
        handler.post {
            if (!activity.isDestroyed) {
                gate.adopt()
                ShellAccessUi.onState(ShellReadiness.state(), root.hasWindowFocus())
            }
        }
    }
    ShellReadiness.addListener(onState)
    val focus = ViewTreeObserver.OnWindowFocusChangeListener { has -> if (has) ShellAccessUi.onFocus(true) }
    root.viewTreeObserver.addOnWindowFocusChangeListener(focus)
    owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        // Màn bật lại sau lượt tắt máy: cửa sổ HOME có thể GIỮ tiêu điểm suốt lúc màn tắt ⇒ không có sự kiện "lấy lại
        // tiêu điểm" nào ⇒ soi lại thẻ ở mỗi lần HOME hiện (cùng hẹn 2,5 s như khi lấy lại tiêu điểm).
        override fun onStart(owner: LifecycleOwner) { ShellAccessUi.onFocus(true) }

        override fun onDestroy(owner: LifecycleOwner) {
            ShellReadiness.removeListener(onState)
            if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            ShellAccessUi.detach(activity)
        }
    })
}
