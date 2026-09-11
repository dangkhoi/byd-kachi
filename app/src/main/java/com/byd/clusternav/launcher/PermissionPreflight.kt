package com.byd.clusternav.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.byd.clusternav.NavNotificationListener
import com.byd.clusternav.modules.navaccess.NavAccessibilityService

/**
 * VÒNG KIỂM QUYỀN — phần chạm Android (P8). Quyết định nằm ở `:core` ([LauncherRequirements]); file này chỉ làm
 * hai việc: **đọc trạng thái thật** và **cấp lại** những gì tự cấp được.
 *
 * Spec: `docs/specs/kachi-permission-preflight.html`.
 *
 * ## Hai nguyên tắc bám chặt
 * 1. **KHÔNG mở phiên kênh shell chỉ để ĐỌC** (ràng buộc C4). Ba quyền kiểu ADB đọc được bằng cách đọc thẳng cấu
 *    hình hệ thống — mọi app đọc được, không cần shell. Chỉ mở kênh khi thật sự phải CẤP. Code cũ đã ghi bài học
 *    này: mở phiên thừa mỗi lần mở app là tốn.
 * 2. **Đọc không được ⇒ trả `null`**, KHÔNG trả `false`. Một số ROM thiếu API; đoán là thiếu rồi đi xin lại sẽ tạo
 *    nhiễu đúng lúc đang test trên xe — trái hẳn mục đích của việc này.
 */
object PermissionPreflight {

    private const val TAG = "Preflight"

    /**
     * Đọc trạng thái 6 điều kiện → báo cáo. **Không** cấp gì, **không** mở kênh shell.
     *
     * @param shellUsable kênh shell có dùng được không — chỗ gọi truyền vào (activity đã biết, khỏi dò lại).
     *   `null` = chưa dò xong ⇒ mục đó thành "chưa đọc được", không bị coi là thiếu.
     */
    fun check(ctx: Context, shellUsable: Boolean?): PermissionReport =
        LauncherRequirements.check { req ->
            when (req.id) {
                LauncherRequirements.NOTIFICATION_LISTENER.id -> notificationListenerGranted(ctx)
                LauncherRequirements.ACCESSIBILITY.id -> accessibilityGranted(ctx)
                LauncherRequirements.OVERLAY.id -> overlayGranted(ctx)
                LauncherRequirements.FREEFORM.id -> freeformEnabled(ctx)
                LauncherRequirements.SHELL_CHANNEL.id -> shellUsable
                LauncherRequirements.DEFAULT_HOME.id -> isDefaultHome(ctx)
                else -> null
            }
        }

    /**
     * Cấp lại những mục **tự cấp được** đang thiếu. Chạy trên thread NỀN (có mở kênh shell) — chỗ gọi chịu trách
     * nhiệm việc đó.
     *
     * Mỗi lệnh độc lập: một lệnh hỏng KHÔNG chặn lệnh sau (xe từ chối một quyền là chuyện thường).
     *
     * @param sh hàm chạy lệnh qua kênh shell; trả chuỗi kết quả.
     * @return số mục đã thử cấp.
     */
    fun selfGrant(report: PermissionReport, sh: (String) -> String): Int {
        val todo = report.selfFixable
        if (todo.isEmpty()) return 0
        todo.forEach { req ->
            val cmd = grantCommand(req) ?: return@forEach
            val rc = runCatching { sh(cmd) }.getOrElse { "lỗi: ${it.javaClass.simpleName}" }
            Log.i(TAG, "tự cấp ${req.id}: $rc")
        }
        return todo.size
    }

    /**
     * Lệnh cấp cho một mục. Dùng **đúng công thức đã proven trên xe** từ bản 1.13 (`cmd notification allow_listener`,
     * append vào danh sách trợ năng, `appops set … allow`) — không phát minh lệnh mới.
     */
    private fun grantCommand(req: LauncherRequirement): String? = when (req.id) {
        LauncherRequirements.NOTIFICATION_LISTENER.id ->
            "cmd notification allow_listener ${flat(NavNotificationListener::class.java.name)}"
        LauncherRequirements.OVERLAY.id -> "appops set $PKG SYSTEM_ALERT_WINDOW allow"
        // ⚠ FREEFORM cố ý KHÔNG có lệnh ở đây. Cờ cửa sổ tự do là **trạng thái BỀN**, và dự án có luật
        // **một-nơi-ghi-duy-nhất** (`FreeformSeedPolicy` ở `:core system/`); phía launcher KHÔNG được ghi trực tiếp.
        // Guard `PersistentWindowStateWriterGuardTest` đã bắt đúng lúc tôi viết lệnh thô vào đây.
        // Cách đúng: gọi lại đường gieo đã có (xem `KachiHomeActivity.runPreflight`), đường mà `KachiAutostart`
        // vẫn đang dùng ⇒ vẫn chỉ MỘT nơi ghi.
        LauncherRequirements.FREEFORM.id -> null
        // Trợ năng: phải ĐỌC-SỬA-GHI danh sách (append, không ghi đè — ghi đè sẽ tắt trợ năng của app khác).
        LauncherRequirements.ACCESSIBILITY.id -> null   // đọc-sửa-ghi, xem [accessibilityGrantCommands]
        else -> null
    }

    /**
     * Trợ năng cần **append** vào danh sách đang có chứ không ghi đè: ghi đè sẽ tắt trợ năng của app khác (kể cả
     * của người khuyết tật đang dùng). Trả về cặp lệnh đọc-rồi-ghi để chỗ gọi thực hiện tuần tự.
     */
    fun accessibilityGrantCommands(current: String?, flagOn: Boolean = false): List<String> {
        val comp = flat(NavAccessibilityService::class.java.name)
        // CHỈ giữ token đúng DẠNG component (`gói/lớp`). Ba lý do, đều là rủi ro thật:
        //  • lệnh đọc có thể trả về "null" hoặc **một câu lỗi** — ghép câu lỗi vào rồi ghi đè cấu hình dùng chung là
        //    cách chắc chắn để **xoá trợ năng của app khác** (kể cả của người khuyết tật đang dùng);
        //  • token chứa khoảng trắng làm `settings put` chỉ nhận phần đầu ⇒ mất phần còn lại — cùng hậu quả;
        //  • lọc theo dạng thì an toàn kể cả khi lệnh đọc đổi định dạng ở ROM khác.
        val existing = (current ?: "")
            .split(':').map { it.trim() }
            .filter { COMPONENT_SHAPE.matches(it) }
        val already = comp in existing
        // Có trong danh sách nhưng CỜ đang tắt thì trợ năng vẫn KHÔNG chạy ⇒ vẫn phải bật cờ.
        // [ĐO] 2026-09-11: chính cờ này bị hệ thống đưa về 0 khi tiến trình chết.
        if (already && flagOn) return emptyList()
        val cmds = ArrayList<String>(2)
        if (!already) {
            val merged = (existing + comp).joinToString(":")
            // Bọc nháy: giá trị là dữ liệu, không phải mã lệnh.
            cmds.add("settings put secure enabled_accessibility_services '$merged'")
        }
        if (!flagOn) cmds.add("settings put secure accessibility_enabled 1")
        return cmds
    }

    /** Dạng component hợp lệ: `gói/lớp`, không khoảng trắng, không ký tự shell. */
    private val COMPONENT_SHAPE = Regex("""[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+""")

    /** Chuỗi đọc cờ trợ năng toàn hệ thống. */
    const val READ_ACCESSIBILITY_FLAG_CMD = "settings get secure accessibility_enabled"

    /** Chuỗi đọc danh sách trợ năng hiện tại (để append). */
    const val READ_ACCESSIBILITY_CMD = "settings get secure enabled_accessibility_services"

    // ── Đọc trạng thái — KHÔNG cần kênh shell ────────────────────────────────────────────────────

    /** `null` nếu không đọc được (ROM lạ). */
    private fun notificationListenerGranted(ctx: Context): Boolean? = runCatching {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        val expected = ComponentName(ctx, NavNotificationListener::class.java)
        flat.split(':').any { ComponentName.unflattenFromString(it.trim()) == expected }
    }.getOrNull()

    /**
     * Trợ năng ĐỦ = component có trong danh sách **VÀ** cờ toàn hệ thống đang bật. Thiếu phép kiểm cờ thì ca
     * "có trong danh sách nhưng cờ = 0" bị báo là ĐỦ trong khi trợ năng **thật sự đang tắt** — và [ĐO] 2026-09-11
     * cho thấy chính cờ đó bị đưa về 0 khi tiến trình chết, nên đây là ca THẬT, không phải giả thuyết.
     */
    private fun accessibilityGranted(ctx: Context): Boolean? = runCatching {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_accessibility_services") ?: return false
        val expected = ComponentName(ctx, NavAccessibilityService::class.java)
        val listed = flat.split(':').any { ComponentName.unflattenFromString(it.trim()) == expected }
        val flagOn = Settings.Secure.getInt(ctx.contentResolver, "accessibility_enabled", 0) == 1
        listed && flagOn
    }.getOrNull()

    /** Bọc `runCatching`: [ĐO] có ROM thiếu hẳn API này (code cũ đã bọc vì lý do đó). */
    private fun overlayGranted(ctx: Context): Boolean? = runCatching { Settings.canDrawOverlays(ctx) }.getOrNull()

    private fun freeformEnabled(ctx: Context): Boolean? = runCatching {
        Settings.Global.getInt(ctx.contentResolver, "enable_freeform_support", 0) == 1
    }.getOrNull()

    /**
     * Kachi có đang là màn hình chính không. So gói của activity mà hệ thống chọn cho ý-định HOME.
     * Trả `null` nếu không phân giải được (không kết luận là thiếu).
     */
    private fun isDefaultHome(ctx: Context): Boolean? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = ctx.packageManager.resolveActivity(intent, 0) ?: return null
        res.activityInfo?.packageName == ctx.packageName
    }.getOrNull()

    private fun flat(cls: String) = "$PKG/$cls"

    private const val PKG = "com.byd.launcher"
}
