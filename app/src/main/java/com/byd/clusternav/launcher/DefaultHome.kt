package com.byd.clusternav.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.byd.clusternav.system.PackageQueries

/**
 * ═══ S5 — "Kachi có đang là màn hình chính không" (ĐỌC thuần, KHÔNG shell) + LỐI VÀO HOME ═══════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` §9 (S5) · `kachi-hal187-cast-remediation.html` §9 (2026-09-15, alias).
 * **Một chỗ duy nhất** cho mọi câu về HOME:
 *  • [component] — component HOME của Kachi. Từ 2026-09-15 đây là **alias** `KachiHome` (manifest `activity-alias`,
 *    `enabled=false` sẵn), KHÔNG phải `KachiHomeActivity`. Chuỗi này là thứ `cmd package set-home-activity` nhận; giữ ở
 *    một nơi nên đường Cài đặt ([ClusterNavBridge.setDefaultHome]) và khởi động nguội ([com.byd.clusternav.KachiAutostart])
 *    không bao giờ ghép khác nhau.
 *  • [launchComponent] — component để **KHỞI CHẠY** Kachi bằng `am start -n` ([KachiHomeActivity], LUÔN bật). ⚠ KHÔNG
 *    dùng [component] cho việc này: alias HOME xuất xưởng `enabled=false`, mà `am start -n` vào một component đang tắt
 *    trả `Error: Activity class … does not exist` ⇒ sau boot/OTA launcher KHÔNG lên (người chưa bấm "Đặt làm màn hình
 *    chính" thì alias vẫn tắt). Hai câu hỏi khác nhau: *"đặt ai làm HOME"* (alias) ≠ *"khởi chạy Kachi"* (activity).
 *  • [enableHomeEntry] — bật alias. Vì sao HOME phải TẮT SẴN: [ĐO on-car 2026-09-15] BYD
 *    packageinstaller chặn GUI-install app có HOME đang bật; owner [ĐO] DuDu "cài vào là app bình thường, chọn làm
 *    launcher mới hiện hộp chọn" ⇒ bật HOME lúc runtime là đường DuDu đi. Bật alias = hệ thống có ứng viên home mới ⇒
 *    hộp chọn launcher (nếu ROM hiện) — và `set-home-activity` vẫn là fallback tất định sau đó.
 *  • [currentPackage]/[isCurrent] — hệ thống ĐANG chọn ai cho ý-định HOME. Đọc qua [PackageQueries.resolveActivity]
 *    (API `PackageManager`, **mọi app đọc được, không cần dadb** — cùng ràng buộc C4 mà `PermissionPreflight` giữ).
 *
 * ⚠ [isCurrent] trả `null` khi **không phân giải được** (ROM lạ / lỗi đọc) — KHÔNG kết luận là "chưa phải" (cùng luật
 * "đọc không được ⇒ null, không ⇒ false" của `PermissionPreflight`, để không đi xin đặt lại HOME dựa trên một lần đọc
 * hỏng). So theo **gói** chứ không theo lớp, nên alias hay activity đều đúng.
 */
object DefaultHome {

    /** Lớp alias HOME (`com.byd.clusternav.launcher.KachiHome`) — cùng gói Java với [KachiHomeActivity], khớp manifest. */
    val HOME_ALIAS_CLASS: String = KachiHomeActivity::class.java.name.substringBeforeLast('.') + ".KachiHome"

    /** Component HOME của Kachi: `"<applicationId>/<lớp alias KachiHome>"` — đích của `set-home-activity`. */
    fun component(ctx: Context): String = "${ctx.packageName}/$HOME_ALIAS_CLASS"

    /**
     * Component để KHỞI CHẠY Kachi (`am start -n <đây>`): `"<applicationId>/<lớp KachiHomeActivity>"`.
     *
     * ⚠ [SOÁT 2026-09-15 · P1] Tách khỏi [component] có chủ đích. Alias HOME xuất xưởng `enabled=false`; `am start`
     * vào component đang tắt trả `Error: Activity class {…} does not exist` ⇒ nếu `KachiAutostart` dùng [component]
     * thì sau mỗi boot/`MY_PACKAGE_REPLACED` của người dùng CHƯA bấm "Đặt làm màn hình chính", launcher không được
     * đưa lên (ô không mount lại). [KachiHomeActivity] luôn bật (MAIN+LAUNCHER) nên đây là đích khởi chạy đúng.
     */
    fun launchComponent(ctx: Context): String = "${ctx.packageName}/${KachiHomeActivity::class.java.name}"

    private fun alias(ctx: Context) = ComponentName(ctx.packageName, HOME_ALIAS_CLASS)

    private fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /**
     * Bật lối vào HOME (alias). Idempotent; `DONT_KILL_APP` để không giết launcher đang chạy. `false` = không bật được
     * (ném) — caller nói đúng việc, không giả vờ đã bật. Đây là bước ĐẦU của "Đặt làm màn hình chính".
     */
    fun enableHomeEntry(ctx: Context): Boolean = runCatching {
        ctx.packageManager.setComponentEnabledSetting(
            alias(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
        )
        true
    }.getOrDefault(false)

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
