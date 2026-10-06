package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.A11yLifecycleHeal
import com.byd.clusternav.Prefs
import com.byd.clusternav.a11yTatMayAt

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

    /**
     * 2.93 · TEST-MODE-ACC-OFF — mốc tắt máy gần nhất: claim bền của lớp 1 (`Prefs.a11yTatMayAt`, ghi mỗi lần BYD giết +
     * Android dựng lại launcher lúc màn TẮT) hoặc — chưa kịp ghi ở luồng nền — mốc bật của CHÍNH tiến trình này nếu nó bật
     * lúc màn tắt ([A11yLifecycleHeal.pendingTatMayAt]): lệnh tới ngay sau lượt dựng lại không lọt qua khe đó. Cửa sổ mở
     * TRƯỚC mốc này ⇒ đóng ([TestBridgeWindow.closedByIgnitionOff]).
     */
    private fun tatMayAt(ctx: Context): Long = maxOf(Prefs.a11yTatMayAt(ctx), A11yLifecycleHeal.pendingTatMayAt())

    /** Cầu kiểm thử có đang mở không. Mọi nhánh lệnh đều hỏi hàm này TRƯỚC. */
    fun isOn(ctx: Context): Boolean =
        TestBridgeWindow.isOn(stored(ctx), bootId(), SystemClock.elapsedRealtime(), tatMayAt(ctx))

    /** Số phút còn lại (0 = đang tắt) — màn Cài đặt và JSON trả về cùng đọc con số này. */
    fun remainingMinutes(ctx: Context): Int =
        TestBridgeWindow.remainingMinutes(stored(ctx), bootId(), SystemClock.elapsedRealtime(), tatMayAt(ctx))

    /** Mở một cửa sổ mới **tính từ bây giờ** (bật lại khi đang bật = gia hạn, đúng thứ người test muốn). */
    fun enable(ctx: Context) {
        val value = TestBridgeWindow.encode(bootId(), SystemClock.elapsedRealtime())
        sp(ctx).edit().putString(KEY_UNTIL, value).apply()
    }

    /**
     * Danh tính lần nổ máy — `/proc/sys/kernel/random/boot_id` (đổi mỗi lần boot, app thường đọc được). Không đọc được
     * ⇒ chuỗi rỗng ⇒ cửa sổ luôn ĐÓNG (an toàn về phía tắt). [ĐO] xe DiLink3 14/09: đọc được, dạng UUID. ⚠ Tắt máy BYD
     * KHÔNG khởi động lại máy ⇒ chuỗi này không đổi qua lần tắt máy — phần đó do [tatMayAt] lo.
     */
    private fun bootId(): String = runCatching { java.io.File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("")

    /** Đóng ngay. XOÁ khoá chứ không ghi một giá trị "đã tắt": đọc lại không phải phân biệt hai cách nói "không". */
    fun disable(ctx: Context) {
        sp(ctx).edit().remove(KEY_UNTIL).apply()
    }

    /**
     * 2.93 · TEST-MODE-ACC-OFF (senior review Pass 1, [P2]) — tiến trình NÀY bật lúc màn TẮT (hoặc không hỏi được màn) ⇒ làm
     * lần đóng thành BỀN: còn khoá mà [isOn] đã nói ĐÓNG (cửa sổ mở TRƯỚC lúc bật — [tatMayAt] đã gồm mốc bật này) ⇒ XOÁ khoá,
     * `commit()` (luồng nền).
     *
     * Vì sao [tatMayAt] một mình chưa đủ: claim bền của lớp 1 có cổng *"cùng một lần tắt máy"*
     * (`AccessibilityHealGates.tatMayMayRun` — trong 10 phút mà chưa có lượt mở-xe nào ⇒ KHÔNG ghi claim mới), còn mốc RAM
     * [A11yLifecycleHeal.pendingTatMayAt] chết theo tiến trình ⇒ một tiến trình SAU bật lúc màn SÁNG (nổ máy lại trong 60
     * phút) sẽ thấy cửa sổ MỞ lại. Chỗ gọi DUY NHẤT: luồng nền của [A11yLifecycleHeal.install] (không đọc prefs trên luồng
     * chính); bài canh `TestBridgeIgnitionOffWiringContractTest`.
     */
    fun closeAfterScreenOffStart(ctx: Context) {
        if (stored(ctx) == null || isOn(ctx)) return
        val ok = sp(ctx).edit().remove(KEY_UNTIL).commit()
        Log.i(TestBridgeReply.TAG, "test-mode: tiến trình bật lúc màn tắt ⇒ đóng bền cửa sổ cũ (ghi=${if (ok) "ok" else "HỎNG"})")
    }
}
