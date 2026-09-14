package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.os.SystemClock

/**
 * ═══ T-BRIDGE · CÔNG TẮC "CHẾ ĐỘ KIỂM THỬ QUA ADB" ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` R2. Chỗ **duy nhất** đọc/ghi công tắc; quyết định *"còn hiệu lực
 * không"* thì nằm ở [TestBridgeWindow] (`:core`, kiểm off-device).
 *
 * ## Vì sao một tệp prefs RIÊNG, không nhét vào `kachi_workspace`
 * `kachi_workspace` là tệp **theo hồ sơ**: nó bị chụp–áp khi đổi hồ sơ, bị nhân bản khi tạo hồ sơ, bị dọn khi xoá
 * hồ sơ. Một cửa điều khiển mở-60-phút mà đi theo những đường ấy là đúng thứ mà cửa sổ thời gian sinh ra để
 * chặn: đổi hồ sơ xong cửa lại mở, hoặc một ảnh chụp từ tuần trước bật nó lên trên đường cao tốc. Tệp riêng thì
 * **không có đường nào** chép nó đi đâu — và `WorkspacePrefs` giữ nguyên tính chất "một cửa duy nhất vào tệp
 * chính" (KDoc `WorkspacePrefsProfile` nói vì sao cửa thứ hai là lỗi).
 *
 * ## Chỉ BẬT được bằng tay, trên màn Cài đặt
 * Không có đường bật bằng broadcast, bằng intent, bằng lệnh shell — có chủ ý. Người bật phải là người **đang
 * ngồi trong xe**: đó là lớp bảo vệ duy nhất mà một receiver `exported` còn lại (một receiver không đọc được uid
 * của bên gửi). Thêm một lệnh `enable` dù có mã xác nhận cũng là mở một cửa mà chủ xe không biết mình đã mở.
 */
object TestBridgeStore {

    /** Tên tệp prefs — khai ở [com.byd.clusternav.launcher.SettingsCatalog.PREFS_FILES] kèm lý do. */
    private const val PREFS_FILE = "kachi_test_bridge"

    /** Khoá DUY NHẤT: `"<mốc nổ máy>:<hạn dùng>"` — xem [TestBridgeWindow]. */
    private const val KEY_UNTIL = "test_bridge_until"

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun stored(ctx: Context): String? = sp(ctx).getString(KEY_UNTIL, null)

    /** Cầu kiểm thử có đang mở không. Mọi nhánh lệnh đều hỏi hàm này TRƯỚC. */
    fun isOn(ctx: Context): Boolean =
        TestBridgeWindow.isOn(stored(ctx), System.currentTimeMillis(), SystemClock.elapsedRealtime())

    /** Số phút còn lại (0 = đang tắt) — màn Cài đặt và JSON trả về cùng đọc con số này. */
    fun remainingMinutes(ctx: Context): Int =
        TestBridgeWindow.remainingMinutes(stored(ctx), System.currentTimeMillis(), SystemClock.elapsedRealtime())

    /** Mở một cửa sổ mới **tính từ bây giờ** (bật lại khi đang bật = gia hạn, đúng thứ người test muốn). */
    fun enable(ctx: Context) {
        val value = TestBridgeWindow.encode(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        sp(ctx).edit().putString(KEY_UNTIL, value).apply()
    }

    /** Đóng ngay. XOÁ khoá chứ không ghi một giá trị "đã tắt": đọc lại không phải phân biệt hai cách nói "không". */
    fun disable(ctx: Context) {
        sp(ctx).edit().remove(KEY_UNTIL).apply()
    }
}
