package com.byd.clusternav.navigation.screencapture

/**
 * B3.13 — chọn cửa sổ APP DẪN ĐƯỜNG từ danh sách window a11y (`getWindows()` / `getWindowsOnAllDisplays()`),
 * **BẤT KỂ foreground/focus**, để đọc nav app khi nó KHÔNG phải app trên cùng — chính là cách OpenBYD đọc
 * được Waze lúc không active (`BydAccessibilityService.rootNodeForPackages` duyệt mọi window mọi display).
 *
 * THUẦN (không Android): lớp `:app` map `AccessibilityWindowInfo` → [WinInfo] ở biên, ở đây chỉ quyết định.
 *
 * Lọc: CHỈ [TYPE_APPLICATION] (loại overlay/hệ-thống → đồng thời sửa **B3.7** nhiễu overlay nổi WazeMod) +
 * `pkg` thuộc nav + bounds không rỗng. Xếp hạng: **DIỆN TÍCH lớn nhất trước** (app dẫn thật phủ nhiều hơn cửa
 * sổ nổi nhỏ), focus chỉ là tiebreak — nên window Waze KHÔNG focus vẫn được chọn nếu nó là nav-window lớn nhất.
 */
object NavWindowPicker {

    /** Trùng `AccessibilityWindowInfo.TYPE_APPLICATION` (API). Overlay/system/IME KHÔNG phải app dẫn. */
    const val TYPE_APPLICATION = 1

    /**
     * Một cửa sổ a11y đã rút gọn. [type] = `AccessibilityWindowInfo.getType()`; [bounds] = `getBoundsInScreen`
     * (không gian màn); [displayId] = display chứa window (0 chính, 1 cụm…); [focused] = `isFocused`.
     */
    data class WinInfo(
        val pkg: String,
        val type: Int,
        val bounds: CropRect,
        val displayId: Int,
        val focused: Boolean,
    )

    /** Kết quả: nav app nào + vùng window + display. Caller dùng để mở gate + đặt pkg + biết chụp display nào. */
    data class Pick(val pkg: String, val bounds: CropRect, val displayId: Int)

    /**
     * Chọn window nav tốt nhất. null nếu không có window TYPE_APPLICATION của nav pkg nào (⇒ gate không mở
     * theo đường window-enum; caller vẫn có thể dựa nguồn khác).
     */
    /**
     * Xếp hạng MỌI cửa sổ nav ứng viên, tốt nhất trước — để caller thử lần lượt thay vì cược vào một cái.
     *
     * VÌ SAO (08-22): [pick] chọn theo DIỆN TÍCH, không có khái niệm "app nào đang DẪN". Ca thật: người dùng
     * cài cả Waze zin lẫn WazeMod; chỉ MỘT bản dẫn tại một thời điểm (bản kia nằm im). Nếu bản đứng im lại có
     * cửa sổ to hơn thì nó thắng — rồi đọc view-id trả rỗng, mà `CaptureForegroundSource` đã bị lái sang nó.
     * Có danh sách xếp hạng thì caller thử tiếp ứng viên sau; **"đọc được dữ liệu dẫn đường" mới là bằng
     * chứng đang dẫn**, diện tích chỉ là phỏng đoán.
     */
    fun rank(windows: List<WinInfo>, navPkgs: Set<String>): List<Pick> =
        windows
            .filter { it.type == TYPE_APPLICATION && it.pkg in navPkgs && !it.bounds.isEmpty() }
            .sortedWith(
                compareByDescending<WinInfo> { area(it.bounds) }
                    .thenByDescending { it.focused }
                    // chốt cuối TẤT ĐỊNH: không bao giờ để kết quả phụ thuộc thứ tự duyệt của hệ thống
                    .thenBy { it.pkg },
            )
            .map { Pick(it.pkg, it.bounds, it.displayId) }
            // `getWindows()` và `getWindowsOnAllDisplays()` trả TRÙNG cùng một cửa sổ (đo 08-22: ranked ra
            // [wazemod, wazemod]) → khử trùng để không đọc lại y hệt một app hai lần mỗi nhịp.
            .distinct()

    fun pick(windows: List<WinInfo>, navPkgs: Set<String>): Pick? {
        var best: WinInfo? = null
        for (w in windows) {
            if (w.type != TYPE_APPLICATION) continue          // loại overlay/system (B3.7)
            if (w.pkg !in navPkgs) continue
            if (w.bounds.isEmpty()) continue
            val b = best
            if (b == null || better(w, b)) best = w
        }
        val chosen = best ?: return null
        return Pick(chosen.pkg, chosen.bounds, chosen.displayId)
    }

    /** Diện tích lớn hơn thắng (app dẫn thật > overlay nổi); bằng nhau thì focused thắng. */
    private fun better(a: WinInfo, b: WinInfo): Boolean {
        val aa = area(a.bounds)
        val ba = area(b.bounds)
        if (aa != ba) return aa > ba
        return a.focused && !b.focused
    }

    private fun area(r: CropRect): Long = r.width.toLong() * r.height.toLong()
}
