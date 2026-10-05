package com.byd.clusternav

import com.byd.clusternav.navigation.NavApps
import com.byd.clusternav.testsupport.KotlinSource
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `byd_float_app_list` + quyền vẽ nổi của bóng VietMap.
 *
 * ĐỔI GHIM 2.89 · B2 VM-PREREQ-TRUTH (có lý do):
 *  • bản 1.35 gán hộp *"Hệ thống IVI không hỗ trợ hoạt động này"* cho việc VẮNG khỏi `byd_float_app_list`. [ĐO nguồn ROM
 *    2602030] system/product không chỗ nào đọc khoá này (vendor [CHƯA BIẾT]); hộp đó là `UnsupportActivity` của
 *    CarSetting, tới bằng lời xin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` của chính VietMap (KDoc `FloatAppList`).
 *  • cờ một-lần `vm_float_whitelist_applied` đã gỡ: nó chặn lại công thức sau lần đầu, kể cả khi VietMap cài lại (ROM xoá
 *    appop theo gói) ⇒ owner phải cấp tay lại. Nay `byd_float_app_list` đọc trước, chỉ ghi khi VẮNG; appop
 *    `SYSTEM_ALERT_WINDOW` đi qua `AppPrereqs` (đọc sự thật → áp phần thiếu → đọc lại) — khoá ở
 *    `AppPrereqsWiringContractTest` + `:core` `AppPrereqPlanTest`.
 *
 * Runtime cần Android (Context, dadb, appops), `:app` không có Robolectric ⇒ khoá bằng SOURCE đã bỏ chú thích
 * ([KotlinSource]) — nhắc trong chú thích không thoả được bài nào.
 */
class VmFloatWhitelistWiringTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun read(relative: String) = app("src/main/java/com/byd/clusternav/$relative").toFile().readText()

    private val autostart by lazy { KotlinSource.stripComments(read("VietMapAutostart.kt")) }
    private val assistant by lazy { KotlinSource.stripComments(read("modules/voicekey/AssistantLauncher.kt")) }

    /** Khối `byd_float_app_list`, cắt từ cổng tới mốc kế tiếp để mọi bài thứ tự chỉ xét trong khối đó. */
    private val recipe by lazy {
        val start = autostart.indexOf("if (Prefs.vmBubbleEnabled(app)) {")
        assertTrue(start >= 0, "cổng bóng của khối byd_float_app_list không tìm thấy trong VietMapAutostart")
        val end = autostart.indexOf("val foreground = running", start)
        assertTrue(end > start, "không tìm thấy mốc kết thúc khối (val foreground = running) sau cổng")
        autostart.substring(start, end)
    }

    @Test
    fun `merge dung chung FloatAppList — dung boi CA HAI caller (DRY)`() {
        assertTrue(
            assistant.contains("FloatAppList.merge(cur, listOf(PKG_GSA, PKG_BARD, app.packageName))"),
            "AssistantLauncher phải gọi helper dùng chung, không tự merge inline",
        )
        assertTrue(
            autostart.contains("FloatAppList.merge(curFloat, listOf(PKG))"),
            "VietMapAutostart phải gọi cùng helper FloatAppList.merge",
        )
        assertFalse(
            assistant.contains("filter { it.isNotEmpty() && it != \"null\" }"),
            "merge inline cũ ở AssistantLauncher phải được thay bằng FloatAppList.merge, không còn chép tay",
        )
    }

    @Test
    fun `doc truoc - chi ghi byd_float_app_list khi VANG, khong co co mot-lan`() {
        val get = recipe.indexOf("settings get global byd_float_app_list")
        val check = recipe.indexOf("FloatAppList.contains(curFloat, PKG)")
        val put = recipe.indexOf("settings put global byd_float_app_list")
        assertTrue(get in 0 until check && check < put, "đọc → kiểm VẮNG → mới ghi:\n$recipe")
        assertFalse(recipe.contains("WhitelistApplied"), "không còn cờ một-lần quyết thay sự thật")
        assertEquals("vn.vietmap.live", NavApps.VIETMAP_LIVE)
        assertTrue(autostart.contains("const val PKG = NavApps.VIETMAP_LIVE"))
    }

    @Test
    fun `appops khong con ghi mu o day - di qua AppPrereqs theo su that`() {
        assertFalse(recipe.contains("appops set"), "quyền vẽ nổi đọc sự thật ở AppPrereqs (vai BUBBLE ⇒ OVERLAY)")
        assertTrue(autostart.contains("AppPrereqs.ensure(app, PKG, AppPrereqPlan.Role.AUTOSTART_PASS)"))
    }

    @Test
    fun `khoi byd_float_app_list degrade-safe (khong chan launch)`() {
        assertTrue(
            recipe.contains("runCatching {") && recipe.contains(".onFailure"),
            "khối phải bọc runCatching + onFailure để một lần hỏng không chặn autostart/launch",
        )
    }
}
