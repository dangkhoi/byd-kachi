package com.byd.clusternav.launcher.escape

/**
 * ═══ 2.98 · R16 — "có giao diện Kachi nào đang mở cần nằm TRÊN app freeform không" (thuần, `:core`, một luồng — luồng chính) ═══
 *
 * Hai nguồn: BẢNG của màn nhà (ngăn kéo · Cài đặt · sửa bố cục — `LauncherWindows` báo bật/tắt) và HỘP THOẠI/cửa sổ con của màn
 * nhà (`SlotEscape.shade` báo từng cái hiện/đóng — đếm, vì hộp thoại có thể chồng nhau: bộ chọn hồ sơ → "Quản lý hồ sơ…").
 *
 * Vì sao phải biết: cửa sổ hộp thoại thuộc task Kachi ⇒ Android xếp nó cùng task màn nhà, DƯỚI cửa sổ app freeform đang được quản
 * (spec 2.98 OQ6 [P3] — [SUY từ AOSP Q: cửa sổ con/ứng dụng xếp theo task cha] + [ĐO máy ảo] bảng Cài đặt bị app vẽ đè trước Pass 4).
 * Bên dùng ([SlotEscapeHome]) chỉ hành động ở CẠNH: [Edge.OPENED] (từ không có gì mở ⇒ có) = gỡ che + đẩy app xuống dưới màn nhà;
 * [Edge.CLOSED] (về không còn gì) = đối chiếu + đưa app lên lại. Giữa hai cạnh không lệnh nào.
 */
class EscapeShade {

    enum class Edge { OPENED, CLOSED }

    private var panels = false
    private var dialogs = 0

    /** Có bảng hoặc hộp thoại Kachi đang mở. */
    val open: Boolean get() = panels || dialogs > 0

    /** Bảng mở/đóng (gọi lặp cùng giá trị vô hại). */
    fun panels(open: Boolean): Edge? = edge { panels = open }

    /** Một hộp thoại hiện ([shown] = `true`) / đóng. Đóng nhiều hơn đã hiện (báo lặp) ⇒ kẹp 0, không âm. */
    fun dialog(shown: Boolean): Edge? = edge { dialogs = if (shown) dialogs + 1 else maxOf(0, dialogs - 1) }

    private inline fun edge(change: () -> Unit): Edge? {
        val before = open
        change()
        return when {
            !before && open -> Edge.OPENED
            before && !open -> Edge.CLOSED
            else -> null
        }
    }
}
