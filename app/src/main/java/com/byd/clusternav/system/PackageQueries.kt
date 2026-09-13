package com.byd.clusternav.system

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/**
 * ═══ MỘT CỬA DUY NHẤT ĐỂ HỎI `PackageManager` (danh sách activity · phân giải activity · thông tin gói) ═══════
 *
 * `PackageManager.queryIntentActivities(Intent, Int)` được **tài liệu Android đánh dấu deprecated từ API 33**
 * (Tiramisu); bản thay thế là `queryIntentActivities(Intent, PackageManager.ResolveInfoFlags)` với
 * `ResolveInfoFlags.of(long)`.
 *
 * ⚠ **Nói đúng mức bằng chứng** (CLAUDE.md §2): đó là deprecation **trong TÀI LIỆU**, chưa phải trong bytecode.
 * [ĐO] 2026-09-13 trên `android.jar` của `compileSdk = 37`: stub của overload `(Intent, int)` **không mang chú
 * thích `@Deprecated`**, và biên dịch lời gọi đó mà **bỏ** `@Suppress` bên dưới thì Kotlin **không cảnh báo gì**
 * (trong khi cùng lượt biên dịch ấy vẫn cảnh báo `FLAG_FULLSCREEN` và `AccessibilityNodeInfo.recycle()` — tức cơ
 * chế cảnh báo có hoạt động). Vậy nên `@Suppress("DEPRECATION")` ở đây là **phòng trước** cho ngày Google gắn chú
 * thích, không phải thứ hôm nay bắt buộc phải có. Lý do chuyển sang API mới vẫn nguyên vẹn: tài liệu đã chỉ đường,
 * và dự án cần chạy 5+ năm.
 *
 * ## [ĐO] chữ ký — đọc thẳng `android.jar` của `compileSdk = 37`, không dựa trí nhớ (CLAUDE.md §3)
 * ```
 * javap -cp $SDK/platforms/android-37.0/android.jar android.content.pm.PackageManager | grep queryIntentActivities
 *   public          java.util.List<ResolveInfo> queryIntentActivities(Intent, PackageManager$ResolveInfoFlags);
 *   public abstract java.util.List<ResolveInfo> queryIntentActivities(Intent, int);
 * javap -cp … 'android.content.pm.PackageManager$ResolveInfoFlags'
 *   public static android.content.pm.PackageManager$ResolveInfoFlags of(long);
 * ```
 * Đối chiếu Context7 (`/websites/developer_android`, trang `PackageManager.ResolveInfoFlags`): `public static
 * PackageManager.ResolveInfoFlags of(long value)`, trả về **không bao giờ null**.
 *
 * ## Vì sao MỘT hàm dùng chung, không rẽ nhánh tại 7 chỗ (CLAUDE.md §6 · §4.1 DRY)
 * `minSdk = 29` nên hai đường phải cùng tồn tại. Bảy chỗ gọi nằm ở bốn package khác nhau (`launcher`,
 * `cast/platform`, `modules/clustercast`) và đều hỏi **cùng một câu**: *"những activity nào bắt intent này"*.
 * Rải rẽ nhánh ra bảy chỗ nghĩa là lần sau nâng `minSdk` phải nhớ đủ bảy — mà bài học của repo này (D2 trong
 * `docs/PROJECT-BACKLOG.md`) đúng là *"đổi một chỗ là lệch bốn"*. Bài canh
 * `PackageQueriesContractTest` khoá lại: **không tệp nào ngoài tệp này được gọi thẳng `queryIntentActivities(`**.
 *
 * Không bọc `runCatching` ở đây — chỗ gọi nào cần chịu lỗi thì tự bọc (và mấy chỗ đó đang bọc sẵn); nuốt lỗi
 * ở tầng dùng chung sẽ biến "ROM từ chối" thành "không có app nào" mà không ai thấy. NGOẠI LỆ DUY NHẤT là
 * [packageInfo] bắt `NameNotFoundException` — ngoại lệ đó KHÔNG phải lỗi, nó chính là câu trả lời "gói chưa
 * cài" của nền tảng (javap bên dưới: chỉ `getPackageInfo` khai `throws`). Ngoại lệ khác vẫn ném lên.
 *
 * ## [ĐO] chữ ký hai API thêm ở D3(a) — cùng `android.jar` compileSdk 37, cùng cách đọc
 * ```
 * javap … android.content.pm.PackageManager | grep -E 'resolveActivity|getPackageInfo'
 *   public          PackageInfo getPackageInfo(String, PackageManager$PackageInfoFlags) throws NameNotFoundException;
 *   public abstract PackageInfo getPackageInfo(String, int)                            throws NameNotFoundException;
 *   public          ResolveInfo resolveActivity(Intent, PackageManager$ResolveInfoFlags);
 *   public abstract ResolveInfo resolveActivity(Intent, int);
 * javap … 'android.content.pm.PackageManager$PackageInfoFlags'
 *   public static android.content.pm.PackageManager$PackageInfoFlags of(long);
 * ```
 * ⇒ `resolveActivity` KHÔNG khai ngoại lệ (chỉ trả `null`), nên [resolveActivity] không cần `try`.
 */
object PackageQueries {

    /**
     * Mọi activity bắt được [intent].
     *
     * @param flags cờ `PackageManager.MATCH_*` / `GET_*`; mặc định `0` = không cờ nào (đúng thứ 7 chỗ gọi đang dùng).
     */
    fun queryActivities(pm: PackageManager, intent: Intent, flags: Int = 0): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            legacy(pm, intent, flags)
        }

    /** Đường API < 33. Tách riêng để `@Suppress("DEPRECATION")` chỉ phủ ĐÚNG lời gọi cũ, không phủ cả hàm trên. */
    @Suppress("DEPRECATION")
    private fun legacy(pm: PackageManager, intent: Intent, flags: Int): List<ResolveInfo> =
        pm.queryIntentActivities(intent, flags)

    /**
     * D3(a) — cùng cửa với [queryActivities]: `resolveActivity` có overload `ResolveInfoFlags` từ API 33.
     * Trả `null` khi không có activity nào nhận [intent] (đúng hợp đồng nền tảng).
     */
    fun resolveActivity(pm: PackageManager, intent: Intent, flags: Int = 0): ResolveInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            legacyResolve(pm, intent, flags)
        }

    /**
     * D3(a) — `getPackageInfo` có overload `PackageInfoFlags` từ API 33. Trả `null` khi gói **không cài**
     * ([PackageManager.NameNotFoundException]) — mọi chỗ gọi cũ đều chỉ hỏi "có cài không / phiên bản gì", nên
     * một giá trị `null` thay cho `runCatching { … }.isSuccess` là đủ và không nuốt ngoại lệ khác.
     */
    fun packageInfo(pm: PackageManager, packageName: String, flags: Int = 0): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            legacyPackageInfo(pm, packageName, flags)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun legacyResolve(pm: PackageManager, intent: Intent, flags: Int): ResolveInfo? =
        pm.resolveActivity(intent, flags)

    @Suppress("DEPRECATION")
    private fun legacyPackageInfo(pm: PackageManager, packageName: String, flags: Int): PackageInfo =
        pm.getPackageInfo(packageName, flags)
}
