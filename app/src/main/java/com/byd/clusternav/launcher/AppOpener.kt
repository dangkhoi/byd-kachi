package com.byd.clusternav.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent

/**
 * Mở app kiểu **THƯỜNG (toàn màn)** — U3. Đây là đường mở app KHÔNG liên quan ô: không ghi trạng thái workspace,
 * không đổi bố cục đã lưu, không ghi sổ vị trí ô (app không thuộc ô nào).
 *
 * Hai đường, thứ tự do **SỐ ĐO** quyết định (không phải suy luận):
 *
 * | App | [openByShell] (`am start --windowingMode 1`) | [openByIntent] (`setLaunchBounds(null)`) |
 * |-----|---------------------------------------------|------------------------------------------|
 * | chưa từng thu nhỏ (Chrome/Calendar) | 1920×1080 — đúng | **1920×1080 — đúng** |
 * | từng nằm trong ô (Clock) | **1132×768 — quay về khung ô CŨ, SAI** | **phủ hết màn — đúng** |
 *
 * ⇒ **[openByIntent] là đường CHÍNH** (tốt bằng-hoặc-hơn ở cả hai ca), [openByShell] chỉ là lưới an toàn khi không
 * lấy được ý-định khởi chạy. Bản thiết kế đầu tiên chọn ngược lại vì SUY LUẬN rằng "task từng là cửa sổ nhỏ sẽ quay
 * lại dạng cũ nếu chỉ gọi ý-định thường, nên phải ép chế độ cửa sổ 1"; phép đo trên máy ảo (Android 10, màn
 * 1920×1080 @240dpi — cùng hình học với xe) **BÁC** suy luận đó: ép chế độ cửa sổ 1 KHÔNG xoá được khung cũ mà
 * Android đã nhớ, còn khung `null` thì phủ hết màn.
 *
 * **Hợp đồng luồng**: [openByIntent] mở activity ⇒ gọi trên thread CHÍNH. [openByShell] chạy lệnh chặn qua dadb ⇒
 * gọi trên thread NỀN. [KachiHomeActivity] tuân theo đúng như vậy.
 *
 * ⚠ Còn tồn tại (đã đo, chờ xe): app từng nằm trong ô thì cửa sổ phủ hết màn nhưng **lệch xuống ~36px** vì task vẫn
 * ở chế độ cửa sổ-nhỏ-đã-phóng-to (thanh tiêu đề cửa sổ). Android 10 không có lệnh nào đưa task đang ở chế độ đó về
 * hẳn toàn màn (`am stack move-task` cần id chồng cố định; `am task resize` cũng cho cùng kết quả lệch). Trên xe,
 * app trong ô chạy trên **màn ảo riêng** nên không tích khung cũ ở màn chính ⇒ dự kiến không gặp; **verify trên xe**.
 */
class AppOpener(private val activity: Activity) {

    /**
     * Đường CHÍNH — API chuẩn: ý-định khởi chạy + **khung `null`** (tài liệu Android: `null` = *toàn màn*), KHÔNG
     * đặt chế độ cửa-sổ-nhỏ như [IntentAppLauncher]. Gọi trên thread CHÍNH. Trả false nếu app không có ý-định
     * khởi chạy (⇒ caller thử [openByShell]).
     */
    fun openByIntent(pkg: String): Boolean {
        val intent = activity.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } ?: return false
        // Ô 7 (2.89-thử1, spec 287 §4.6d): app đang ĐỖ có task trên một màn ảo ẩn. [ĐO nguồn A10 r47] không nêu display thì
        // display ưu tiên = display của task tìm lại được (`TaskLaunchParamsModifier.java:313-317`, `ActivityStarter.java:1480-1485`)
        // ⇒ [SUY] task chỉ lên đỉnh màn ảo ẩn, người dùng không thấy gì. Nêu display 0 ⇒ nhánh `reparentToDisplay` (`ActivityStarter
        // .java:2164-2170`) đưa task ra màn chính — cùng kiểu `am start --display 0` (K7) [ĐO xe 05/10]: app relaunch, đúng ý "toàn màn".
        // Pass 3 · whole-r2-1: MỘT cửa chung với giọng nói / dẫn theo lịch ([ParkedApps.launchOptions]).
        val base = { ActivityOptions.makeBasic().setLaunchBounds(null) }
        val parked = ParkedApps.launchOptions(pkg, base)
        val opts = parked ?: base()
        // PARK-2a: task đã dời ra display 0 ⇒ màn ảo đỗ TRỐNG ⇒ quên ngay (lượt mở vào ô kế đi đường thường, không khung đen).
        return runCatching { activity.startActivity(intent, opts.toBundle()); true }.getOrDefault(false)
            .also { opened -> if (opened && parked != null) ParkedApps.launched(pkg) }
    }

    /**
     * Lưới an toàn — công thức shell đã proven trên xe ([FreeformLaunch.fullscreenCmd]). Chạy lệnh CHẶN qua dadb ⇒
     * gọi trên thread NỀN. Dùng khi [openByIntent] thất bại (ROM chặn mở activity từ launcher, app không có
     * ý-định khởi chạy chuẩn…).
     */
    fun openByShell(pkg: String, sh: (String) -> String): Boolean = runCatching {
        val comp = FreeformLaunch.parseComponent(sh(FreeformLaunch.resolveCmd(pkg)))
        if (comp == null) {
            false
        } else {
            val out = sh(FreeformLaunch.fullscreenCmd(comp))
            !out.contains("Error", true) && !out.contains("Exception", true)
        }
    }.getOrDefault(false)
}
