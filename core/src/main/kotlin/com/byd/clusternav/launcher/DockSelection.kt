package com.byd.clusternav.launcher

/**
 * ═══ TỪ "TẬP NGƯỜI DÙNG VỪA CHỐT" SANG `DockConfig.enabled` — PHÉP GẤP THUẦN ════════════════════════════════
 *
 * T6 · R-UI (m). Bộ chọn thanh nút xe (`DrawerController.openDockPicker`) trả về một **TẬP** mã; cấu hình bền lại
 * là một **DANH SÁCH có thứ tự** ([DockConfig.enabled] — thứ tự = thứ tự nút hiện trên thanh). Chỗ nối hai thứ đó
 * là một quyết định thật.
 *
 * ## Vì sao ở `:core` chứ không cạnh bộ chọn
 * Quy tắc Q1 (`LayeringRulesTest.so file thuan con nam trong app chi duoc giam`): tệp **thuần** không được ở `:app`
 * — chốt đó là `0` và chỉ được giảm. Bản nháp đầu của T6 đặt tệp này ở `:app` và bài canh đỏ ngay, đúng việc nó
 * sinh ra để làm. Ở `:core` nó còn nằm cạnh chính [DockConfig] mà nó gấp vào.
 *
 * ## Hai bẫy nó đóng — cả hai đều là lỗi CÓ THẬT của dự án, chỉ đổi bề mặt
 *  1. **Chỉ gửi chiều BẬT.** Cách viết tự nhiên nhất ở chỗ gọi là `selected.forEach { setEnabled(it, true) }` —
 *     và khi đó cấu hình chỉ **lớn lên**: người dùng bỏ tích một ô rồi bấm Áp dụng thì nút vẫn còn trên thanh, không
 *     một lời nào. Đúng họ "bỏ qua im lặng" mà `DockConfig.setEnabled` đã phải nới một lần
 *     (*"mã không phải nút ⇒ return this"*, RW0).
 *  2. **Sắp lại toàn bộ theo thứ tự catalog.** Làm vậy thì mỗi lần mở bộ chọn rồi bấm Áp dụng — **kể cả khi không
 *     đổi gì** — thứ tự nút trên thanh bị xáo lại. Thứ tự là thứ người dùng quen tay; giữ nguyên phần cũ, chỉ nối
 *     phần mới vào cuối.
 */
object DockSelection {

    /**
     * Cấu hình thanh nút sau khi người dùng chốt [selected].
     *
     * Thứ tự: các mã CŨ còn được chọn giữ nguyên chỗ; các mã MỚI nối vào cuối theo thứ tự khai của
     * [CapabilityCatalog.all] (cùng thứ tự mà bộ chọn đang bày ⇒ người dùng thấy chúng xếp như họ vừa nhìn).
     *
     * Mã lạ (không có trong [CapabilityCatalog]) bị [DockConfig.setEnabled] từ chối như trước — ở đây không nhân
     * bản phép kiểm đó, để chỉ có MỘT chỗ trả lời "mã nào vào được thanh".
     */
    fun apply(current: DockConfig, selected: Set<String>): DockConfig {
        var out = current
        // Chiều TẮT trước: mã đang có mà người dùng đã bỏ tích.
        current.enabled.filterNot { it in selected }.forEach { out = out.setEnabled(it, false) }
        // Chiều BẬT sau, theo thứ tự catalog cho phần mới.
        CapabilityCatalog.all().map { it.id }.filter { it in selected && it !in current.enabled }
            .forEach { out = out.setEnabled(it, true) }
        return out
    }
}
