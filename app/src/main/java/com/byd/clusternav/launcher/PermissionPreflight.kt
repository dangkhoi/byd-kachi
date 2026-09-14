package com.byd.clusternav.launcher

import com.byd.clusternav.system.PackageQueries
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.byd.clusternav.NavNotificationListener
import com.byd.clusternav.modules.navaccess.NavAccessibilityService
import java.util.concurrent.atomic.AtomicBoolean

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
     * ⚠ **THÔNG BÁO THIẾU QUYỀN CHỈ NỔ MỘT LẦN MỖI PHIÊN TIẾN TRÌNH** (backlog U8b).
     *
     * [ĐO] máy ảo 2026-09-13: toast *"Kênh điều khiển cửa sổ… / Window control channel…"* nổ **mỗi lần mở Home** và
     * che thanh nút xe ~3 giây (`Toast.LENGTH_LONG`). Trên xe, Home được mở lại rất nhiều lần trong một chuyến (mỗi
     * lần thoát app là một lần), nên một câu đúng lặp N lần thành nhiễu — đúng thứ việc P8 sinh ra để dọn.
     *
     * **Cờ RAM, KHÔNG prefs — có chủ ý.** Thiếu quyền là trạng thái **của phiên tiến trình này**: khởi động lại đầu
     * xe (hoặc launcher bị hệ thống thu hồi) là đúng lúc phải nói lại, vì cấu hình có thể đã đổi. Ghi prefs sẽ làm
     * câu này im vĩnh viễn kể cả sau khi ROM bị cập nhật thu hồi quyền — thà nhiễu một lần mỗi khởi động còn hơn
     * im lặng khi tính năng lõi thật sự hỏng. Đây cũng là lý do §5 CLAUDE.md phân biệt cờ RAM với state ghi ra
     * ngoài: cái này CHẾT theo tiến trình là đúng, không cần đường trả lại.
     *
     * Người dùng vẫn xem được **đầy đủ, bất cứ lúc nào** ở *Cài đặt › Hệ thống & quyền* — trang đó liệt kê từng mục
     * thiếu kèm "mất gì" ([SettingsSections.system], canh bởi `PermissionPreflightWiringContractTest`).
     *
     * [AtomicBoolean] chứ không phải `var`: [runAndReport] chạy trên thread NỀN và hai Activity có thể cùng khởi
     * động (Home + màn ClusterNav cũ) ⇒ `getAndSet` là chốt duy nhất đảm bảo đúng MỘT lượt hiện.
     */
    private val noticeShown = AtomicBoolean(false)

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
                LauncherRequirements.MICROPHONE.id -> micGranted(ctx)
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
        // V1 pha NGHE — quyền RUNTIME, nhưng `pm grant` từ uid shell cấp được mà không cần hộp hỏi quyền. Đây là
        // ĐÚNG đường mà máy ảo cũng dùng (`adb shell pm grant com.byd.launcher android.permission.RECORD_AUDIO`),
        // nên thứ chạy trên bàn và thứ chạy trên xe là một câu lệnh, không phải hai.
        LauncherRequirements.MICROPHONE.id -> "pm grant $PKG android.permission.RECORD_AUDIO"
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
     *
     * [rawList] = **chuỗi THÔ** của `settings get secure enabled_accessibility_services`, truyền y nguyên. Hàm này
     * tự phân biệt ba ca, vì đó chính là chỗ đã hở:
     *  • `null` / rỗng / chỉ khoảng trắng ⇒ **KHÔNG đọc được** (kênh shell lỗi trả chuỗi rỗng thay vì ném);
     *  • `"null"` ⇒ đọc được và danh sách **thật sự rỗng** (đây là cách `settings get` báo "chưa đặt");
     *  • còn lại ⇒ danh sách thật, lọc theo dạng component.
     *
     * ## ⚠ [SOÁT P1-2] Vì sao phải phân biệt rỗng-thật với không-đọc-được
     * [ĐO] `ShellTransport.run` trả **`""`** khi cả hai lượt thử thất bại, và `launcherSeam` trả `""` khi lệnh bị
     * từ chối — **không ném ngoại lệ**. Bản trước lọc `takeIf { it != "null" }` nên `""` đi qua như "danh sách rỗng"
     * ⇒ sinh lệnh ghi danh sách chỉ-có-Kachi ⇒ **xoá trợ năng của mọi app khác**, đúng hậu quả mà KDoc này nói đã
     * chặn. Bộ lọc theo dạng component chặn được việc *chèn rác*, nhưng không chặn được việc *mất bản gốc*.
     *
     * Ca "đọc được chuỗi lạ nhưng không có token nào đúng dạng" cũng bị coi là KHÔNG đọc được: một câu lỗi
     * (`"Error: permission denied"`) lọc ra rỗng, và không có cách nào phân biệt nó với danh sách rỗng thật.
     * Không ghi thì tệ nhất là lần sau vòng kiểm thấy vẫn thiếu và thử lại — thua xa việc xoá của người khác.
     */
    fun accessibilityGrantCommands(rawList: String?, flagOn: Boolean = false): List<String> {
        val comp = flat(NavAccessibilityService::class.java.name)
        val raw = rawList?.trim()
        val emptyForSure = raw == "null"
        val existing = (raw ?: "")
            .split(':').map { it.trim() }
            .filter { COMPONENT_SHAPE.matches(it) }
        // Không đọc được ⇒ tuyệt đối KHÔNG chạm danh sách dùng chung. Bật cờ thì vẫn an toàn (không xoá gì của ai).
        val readable = emptyForSure || existing.isNotEmpty()
        if (!readable) return if (flagOn) emptyList() else listOf(FLAG_ON_CMD)
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
        if (!flagOn) cmds.add(FLAG_ON_CMD)
        return cmds
    }

    /** Lệnh bật cờ trợ năng toàn hệ thống (một chỗ khai, hai chỗ dùng). */
    private const val FLAG_ON_CMD = "settings put secure accessibility_enabled 1"


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

    /**
     * Quyền micro.
     *
     * `checkSelfPermission` là API của `Context`, luôn có từ API 23 ⇒ khác với năm mục kia, ca *"không đọc
     * được"* ở đây không tồn tại. Vẫn bọc `runCatching` cho đồng nhất với cả nhóm (và với ROM đã từng thiếu API
     * ở những chỗ tưởng như chắc chắn — xem [overlayGranted]).
     */
    private fun micGranted(ctx: Context): Boolean? = runCatching {
        ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrNull()

    private fun freeformEnabled(ctx: Context): Boolean? = runCatching {
        Settings.Global.getInt(ctx.contentResolver, "enable_freeform_support", 0) == 1
    }.getOrNull()

    /**
     * Kachi có đang là màn hình chính không. So gói của activity mà hệ thống chọn cho ý-định HOME.
     * Trả `null` nếu không phân giải được (không kết luận là thiếu).
     */
    private fun isDefaultHome(ctx: Context): Boolean? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = PackageQueries.resolveActivity(ctx.packageManager, intent) ?: return null
        res.activityInfo?.packageName == ctx.packageName
    }.getOrNull()

    private fun flat(cls: String) = "$PKG/$cls"

    private const val PKG = "com.byd.launcher"

    /**
     * CHẠY vòng kiểm rồi BÁO — gồm: đọc trạng thái · tự cấp phần tự cấp được · nói ra phần làm mất tính năng lõi.
     *
     * Ở đây (không ở Activity) vì đây là **luật của vòng kiểm**, không phải việc của màn hình: Activity chỉ nên là
     * composition-root. [ĐO] sau vòng vá, `KachiHomeActivity` vượt **trần 500 dòng** của dự án; đây là một trong hai
     * khối tách ra để về đúng trần (khối kia là hình nền → `WallpaperController`).
     *
     * Chạy trên thread NỀN (chỗ gọi lo): mọi lệnh cấp quyền đi qua kênh shell, chặn.
     */
    fun runAndReport(activity: android.app.Activity, shellUsable: Boolean, sh: ((String) -> String)?) {
        val before = check(activity, shellUsable)
        Log.i("Preflight", before.logLine())

        // Tự cấp: chỉ khi CÓ kênh shell và thật sự đang thiếu (đọc thì không cần shell, cấp thì cần).
        if (sh != null && before.selfFixable.isNotEmpty()) {
            runCatching { selfGrant(before, sh) }
            // Trợ năng phải ĐỌC-SỬA-GHI (append, không ghi đè — ghi đè sẽ tắt trợ năng của app khác).
            if (before.selfFixable.any { it.id == LauncherRequirements.ACCESSIBILITY.id }) {
                runCatching {
                    // Truyền chuỗi THÔ: luật "đọc được hay không" nằm ở một chỗ duy nhất trong
                    // PermissionPreflight.accessibilityGrantCommands (xem KDoc — `takeIf { it != "null" }` ở đây
                    // từng để chuỗi rỗng của kênh shell lỗi đi qua như 'danh sách rỗng').
                    val cur = sh(READ_ACCESSIBILITY_CMD)
                    val flagOn = sh(READ_ACCESSIBILITY_FLAG_CMD).trim() == "1"
                    accessibilityGrantCommands(cur, flagOn).forEach { sh(it) }
                }
            }
            Log.i("Preflight", "sau khi tự cấp: " + check(activity, shellUsable).logLine())
        }

        // Chỉ NÓI khi thiếu thứ làm mất TÍNH NĂNG LÕI (app vào ô). Thiếu mục nhỏ mà báo mỗi lần mở là nhiễu —
        // đúng thứ việc này đi dọn. Danh sách đầy đủ nằm trong bảng Tuỳ biến.
        val after = check(activity, shellUsable)
        // [SOÁT P3] Dùng `notice()` của :core thay vì tự ghép chuỗi ở đây. Trước đây `notice()`/`needsUser`/
        // `fixedAtBoot` chỉ có TEST gọi ⇒ chúng là mã chết ở sản phẩm, và tệ hơn: câu chữ người dùng đọc lại nằm ở
        // tầng UI nên hai bên có thể nói khác nhau. Nay một nguồn duy nhất, và mã kia hết chết.
        val msg = after.notice(coreOnly = true)   // chỉ mục làm mất tính năng lõi — mục nhỏ để bảng Tuỳ biến nói
        // U8b: một lần mỗi phiên tiến trình. `getAndSet` chỉ bật cờ khi THẬT SỰ có câu để nói — thiếu quyền xuất
        // hiện muộn (người dùng thu quyền giữa chuyến) vẫn được báo đúng một lần, thay vì bị cờ "đã nói" nuốt mất.
        if (msg != null && !noticeShown.getAndSet(true)) {
            activity.runOnUiThread { runCatching { Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() } }
        }
    }

}
