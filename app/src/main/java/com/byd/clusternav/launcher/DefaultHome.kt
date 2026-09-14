package com.byd.clusternav.launcher

import android.content.Context
import android.content.Intent
import com.byd.clusternav.system.PackageQueries

/**
 * ═══ S5 — "Kachi có đang là màn hình chính không" (ĐỌC thuần, KHÔNG shell) ═════════════════════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` §9 (S5). **Một chỗ duy nhất** trả lời hai câu đọc-được-không-cần-shell:
 *  • [component] — component HOME của Kachi (`pkg/KachiHomeActivity`). Đây là chuỗi mà `cmd package set-home-activity`
 *    nhận; giữ ở một nơi nên đường Cài đặt ([ClusterNavBridge.setDefaultHome]) và đường khởi động nguội
 *    ([com.byd.clusternav.KachiAutostart]) không bao giờ ghép khác nhau.
 *  • [currentPackage]/[isCurrent] — hệ thống ĐANG chọn ai cho ý-định HOME. Đọc qua [PackageQueries.resolveActivity]
 *    (API `PackageManager`, **mọi app đọc được, không cần dadb** — cùng ràng buộc C4 mà `PermissionPreflight` giữ).
 *
 * ⚠ [isCurrent] trả `null` khi **không phân giải được** (ROM lạ / lỗi đọc) — KHÔNG kết luận là "chưa phải" (cùng luật
 * "đọc không được ⇒ null, không ⇒ false" của `PermissionPreflight`, để không đi xin đặt lại HOME dựa trên một lần đọc
 * hỏng).
 */
object DefaultHome {

    /** Component HOME của Kachi: `"<applicationId>/<lớp KachiHomeActivity đầy đủ>"`. */
    fun component(ctx: Context): String = "${ctx.packageName}/${KachiHomeActivity::class.java.name}"

    private fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** Gói đang là màn hình chính, hoặc `null` nếu không phân giải được (cho dòng "hệ thống đang dùng <gói>"). */
    fun currentPackage(ctx: Context): String? = runCatching {
        PackageQueries.resolveActivity(ctx.packageManager, homeIntent())?.activityInfo?.packageName
    }.getOrNull()

    /** Kachi có đang là màn hình chính không; `null` = không đọc được (không kết luận là thiếu). */
    fun isCurrent(ctx: Context): Boolean? = runCatching {
        PackageQueries.resolveActivity(ctx.packageManager, homeIntent())
            ?.activityInfo?.packageName?.let { it == ctx.packageName }
    }.getOrNull()
}
