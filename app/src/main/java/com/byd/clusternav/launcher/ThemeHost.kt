package com.byd.clusternav.launcher

import java.util.Calendar

/**
 * ═══ T1 — CHỦ SỞ HỮU DUY NHẤT của việc ÁP bảng màu ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-and-light-theme.html` T1, mục 4.
 *
 * ## Vì sao có tệp riêng cho hai dòng mã
 * Phiên S1 đo được rằng `HomeUiState.themeMode` **không có ai đọc để VẼ** (chỉ nạp và ghi lại chính nó) và kết luận
 * đúng rằng nút gạt chủ đề sẽ là **nút chết** (`kachi-settings-screen.html` §4.5). Đây là chỗ đóng lỗ hổng đó — mắt
 * xích cuối của đường một chiều:
 *
 * `WorkspacePrefs` (lưu bền) → `HomeUiState.themeMode` (nguồn sự thật) → `HomeViewModel.setThemeMode` (intent) →
 * **[sync]** (đọc để vẽ) → [KachiTheme.palette] → mọi view dựng sau đó.
 *
 * Đặt riêng vì nó là **một nơi ghi duy nhất**, và một nơi-ghi-duy-nhất có tên riêng thì kiểm được bằng máy:
 * [com.byd.clusternav.launcher.ThemePaletteContractTest] đòi cả `app/src/main` chỉ có **ĐÚNG MỘT** lời gọi
 * `KachiTheme.applyTheme`. Cho phép chỗ thứ hai là dựng lại bẫy "hai bản sao cùng khoá" mà dự án đã sập ba lần
 * (`customLayout` · `unitPrefs` · `wallpaper`) — lần này còn khó thấy hơn vì bản sao nằm trong một `object` toàn cục.
 *
 * ⚠ [KachiTheme.palette] **KHÔNG phải trạng thái thứ hai**: nó là *hình chiếu lúc vẽ* của `themeMode`, không bao giờ
 * được đọc để quyết định logic, và không có đường ghi nào khác ngoài đây.
 */
internal object ThemeHost {

    /**
     * Áp bảng màu cho [state]; trả về `true` nếu bảng **ĐỔI** (chỗ gọi dùng để quyết định dựng lại màn).
     *
     * Giờ lấy tại chỗ vì chỉ [ThemeMode.AUTO] cần nó, và AUTO cần giờ **lúc vẽ** chứ không phải lúc nạp cấu hình.
     * [now] mở ra để test off-car ép được ca 6h/18h mà không phải chờ đến giờ đó.
     */
    fun sync(state: HomeUiState, now: Calendar = Calendar.getInstance()): Boolean =
        KachiTheme.applyTheme(state.themeMode, now.get(Calendar.HOUR_OF_DAY))
}
