package com.byd.clusternav.core

/**
 * Gộp CSV thuần cho khoá toàn cục BYD `settings global byd_float_app_list`.
 *
 * ⚠ 2.89 · B2 (VM-PREREQ-TRUTH) — khoá này KHÔNG làm điều 1.35 từng gán cho nó ("gói vắng mặt bị chặn bằng hộp
 * *Hệ thống IVI không hỗ trợ hoạt động này*"). Nguồn ROM nói ngược lại:
 *  • [ĐO nguồn ROM 2602030, theo vắng mặt] không chỗ nào ở system/product đọc `byd_float_app_list` — 0 lần trong
 *    framework.jar, services.jar, mọi dex của 150 APK + SystemUI của product, và grep thô system.img/product.img (bản cũ
 *    2511080 cũng 0). Chưa trích vendor.img ⇒ vendor [CHƯA BIẾT].
 *  • [ĐO nguồn ROM] cổng vẽ nổi là AOSP thường (`PhoneWindowManager.checkAddPermission` → appop SYSTEM_ALERT_WINDOW).
 *  • [ĐO nguồn ROM + smali VietMap] hộp thoại là `UnsupportActivity` của CarSetting, chỉ tới được bằng phân giải ý-định —
 *    với VietMap là lời xin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` của chính nó ở mỗi `MainActivity.onCreate` khi CHƯA
 *    được miễn pin. Cách chữa là miễn pin theo sự thật (`com.byd.clusternav.system.AppPrereqPlan`), không phải khoá này.
 * Vẫn ghi (vô hại; công thức "8hare" cho Gemini mà khoá này đến từ có thể có tác dụng ở vendor chưa đọc) — nhưng bên gọi
 * không được gán tác dụng nào cho nó.
 *
 * Hai bên gọi trên máy nối thêm vào danh sách qua uid-shell dadb và KHÔNG được đè gói đã có:
 *  • [com.byd.clusternav.modules.voicekey.AssistantLauncher] — Google + Gemini + chính mình, cho trợ lý.
 *  • [com.byd.clusternav.VietMapAutostart] — VietMap, khi bật bóng trên cụm (chỉ ghi khi đọc thấy còn VẮNG).
 *
 * Tách/trim/bỏ "null"/nối/khử trùng/ghép giống hệt ở hai nơi nên nằm MỘT chỗ — thuần (không Android), test off-car theo
 * ranh giới `:core` ([CoreBoundary]).
 */
object FloatAppList {

    /**
     * Gộp [add] vào danh sách CSV [current], giữ nguyên mục của OEM / app khác.
     *
     * Đúng công thức AssistantLauncher: tách [current] theo dấu phẩy, trim, bỏ mục rỗng và chữ `"null"` (thứ `settings get`
     * in khi khoá chưa đặt), nối [add] nguyên văn, khử trùng giữ lần xuất hiện ĐẦU, ghép lại bằng dấu phẩy. Thứ tự = cũ
     * trước, mới sau; gói đã có ⇒ không đổi gì (chạy lại vô hại).
     */
    fun merge(current: String, add: List<String>): String =
        (entries(current) + add)
            .distinct()
            .joinToString(",")

    /** [pkg] đã có trong danh sách vừa đọc (`settings get`) chưa — sự thật đọc TRƯỚC mọi lần ghi (CLAUDE.md §5). */
    fun contains(current: String, pkg: String): Boolean = pkg in entries(current)

    private fun entries(current: String): List<String> =
        current.split(',').map { it.trim() }.filter { it.isNotEmpty() && it != "null" }
}
