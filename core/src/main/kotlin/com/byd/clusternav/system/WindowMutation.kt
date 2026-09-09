package com.byd.clusternav.system

import com.byd.clusternav.launcher.FreeformLaunch
import com.byd.clusternav.launcher.SlotRect

/**
 * Mô hình HÓA (typed) một thay đổi cửa sổ/display sắp gửi xuống head unit. Thuần JVM (:core) — KHÔNG
 * android.*, KHÔNG dadb. Mỗi biến thể mang:
 *  - [targetDisplayId]: display mà mutation nhắm tới. `0` = màn chính launcher, `1` = cụm tài xế (cast),
 *    `≥2` = VirtualDisplay của ô (host tạo). [NO_DISPLAY] = KHÔNG nhắm display nào (vd force-stop).
 *  - [priority]: ưu tiên rút trong hàng đợi cửa sổ hợp nhất ([MutationPriority]).
 *  - [render]: chuỗi lệnh shell BYTE-ỔN ĐỊNH. Các biến thể CÓ KIỂU TÁI DÙNG [FreeformLaunch] (công thức đã
 *    proven on-car, byte-locked bởi `LauncherCommandGoldenTest`) — KHÔNG tự bịa chuỗi mới.
 *
 * B2b sẽ validate qua [DisplayOwnershipRegistry] rồi dispatch qua `ShellTransport` (:app). Stage 2a này CHỈ
 * thêm type + test JVM — KHÔNG wire vào bất kỳ lớp runtime nào (zero runtime risk).
 */
sealed class WindowMutation {
    /** Display đích (xem doc lớp). [NO_DISPLAY] nếu mutation không nhắm display. */
    abstract val targetDisplayId: Int

    /** Ưu tiên rút trong hàng đợi hợp nhất. */
    abstract val priority: MutationPriority

    /** Chuỗi lệnh shell BYTE-ỔN ĐỊNH cho mutation này (tái dùng [FreeformLaunch] cho biến thể có kiểu). */
    abstract fun render(): String

    /**
     * Mở [component] ("pkg/cls") trên [displayId] với [windowingMode] (5 = freeform) — đường VdAppHost
     * (kèm category LAUNCHER) / SlotAppHost ([withLauncherCategory] = false).
     * render() = [FreeformLaunch.launchOnDisplayCmd].
     */
    data class LaunchOnDisplay(
        val component: String,
        val displayId: Int,
        val windowingMode: Int,
        val withLauncherCategory: Boolean = true,
        override val priority: MutationPriority = MutationPriority.NORMAL,
    ) : WindowMutation() {
        override val targetDisplayId: Int get() = displayId
        override fun render(): String =
            FreeformLaunch.launchOnDisplayCmd(component, displayId, windowingMode, withLauncherCategory)
    }

    /**
     * Đặt lại khung task [taskId] về `[left,top,right,bottom]` (KHÔNG phải w/h). [targetDisplayId] = display
     * mà task đang SỐNG — caller cấp (từ `AppMover.findTaskIdOnDisplay` proven), vì `am task resize` không
     * tự mang display. render() = [FreeformLaunch.resizeCmd] (index ô không đổi lệnh nên dùng `index = 0`).
     */
    data class ResizeTask(
        val taskId: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        override val targetDisplayId: Int,
        override val priority: MutationPriority = MutationPriority.NORMAL,
    ) : WindowMutation() {
        override fun render(): String =
            FreeformLaunch.resizeCmd(
                taskId,
                SlotRect(index = 0, left = left, top = top, right = right, bottom = bottom),
            )
    }

    /**
     * Đưa [component] về FULLSCREEN trên [displayId] (đóng ô) — công thức R6 cast (SINGLE_TOP 0x20000000).
     * render() = [FreeformLaunch.fullscreenCmd].
     */
    data class Fullscreen(
        val component: String,
        val displayId: Int = FreeformLaunch.MAIN_DISPLAY,
        override val priority: MutationPriority = MutationPriority.NORMAL,
    ) : WindowMutation() {
        override val targetDisplayId: Int get() = displayId
        override fun render(): String = FreeformLaunch.fullscreenCmd(component, displayId)
    }

    /**
     * force-stop [pkg] (process death). KHÔNG nhắm display nào → [targetDisplayId] = [NO_DISPLAY]
     * ⇒ [DisplayOwnershipRegistry.validate] luôn ALLOW cho cả hai nhánh. render() = [FreeformLaunch.forceStopCmd].
     */
    data class ForceStop(
        val pkg: String,
        override val priority: MutationPriority = MutationPriority.NORMAL,
    ) : WindowMutation() {
        override val targetDisplayId: Int get() = NO_DISPLAY
        override fun render(): String = FreeformLaunch.forceStopCmd(pkg)
    }

    /**
     * ESCAPE HATCH: bọc một chuỗi lệnh [cmd] ĐÃ CÓ (vd hàng chục lệnh cast trong `CastShell`) kèm
     * [targetDisplayId] + [priority] mà KHÔNG cần model-hóa lại toàn bộ cast ngay bây giờ (B2b dùng để đưa
     * cast vào hàng đợi + validate ranh giới). render() trả NGUYÊN [cmd] — byte-ổn định vì không biến đổi.
     */
    data class Raw(
        val cmd: String,
        override val targetDisplayId: Int,
        override val priority: MutationPriority = MutationPriority.NORMAL,
    ) : WindowMutation() {
        override fun render(): String = cmd
    }

    companion object {
        /**
         * Sentinel: mutation KHÔNG nhắm display nào (vd [ForceStop]). Không trùng display id hợp lệ (luôn ≥ 0),
         * nên [DisplayOwnershipRegistry] nhận diện được và luôn ALLOW.
         */
        const val NO_DISPLAY: Int = -1
    }
}
