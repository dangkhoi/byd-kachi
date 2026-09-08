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
 * Float/overlay-whitelist wiring. The BYD IVI refuses an overlay from any package NOT in the global CSV
 * `byd_float_app_list` (the "Hệ thống IVI không hỗ trợ hoạt động này" toast). The modded VietMap draws the
 * cluster bubble, so [VietMapAutostart] must append it to that list AND grant SYSTEM_ALERT_WINDOW over the
 * dadb uid-shell — the same proven recipe [com.byd.clusternav.modules.voicekey.AssistantLauncher] already
 * uses for Google/Gemini, with the CSV merge shared via `com.byd.clusternav.core.FloatAppList`.
 *
 * Runtime needs Android (Context, dadb, appops), and `:app` has no Robolectric — so, like
 * [VoiceKeyAdbApprovalWiringTest], this locks the wiring by reading the source. Comments are stripped first
 * (via the shared [KotlinSource]) so a mention in a comment can never satisfy a contract.
 */
class VmFloatWhitelistWiringTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun read(relative: String) = app("src/main/java/com/byd/clusternav/$relative").toFile().readText()

    private val autostart by lazy { KotlinSource.stripComments(read("VietMapAutostart.kt")) }
    private val assistant by lazy { KotlinSource.stripComments(read("modules/voicekey/AssistantLauncher.kt")) }

    /** The float-whitelist recipe block, scoped from its gate so ordering assertions stay local to it. */
    private val recipe by lazy {
        val start = autostart.indexOf("Prefs.vmBubbleEnabled(app) && !Prefs.vmFloatWhitelistApplied(app)")
        assertTrue(start >= 0, "recipe gate (bubble enabled + one-time flag) không tìm thấy trong VietMapAutostart")
        // End at the next statement after the recipe so the recipe's own runCatching/.onFailure is the ONLY
        // one in scope (the outer runNow-level .onFailure must not leak into ordering assertions).
        val end = autostart.indexOf("val foreground = running", start)
        assertTrue(end > start, "không tìm thấy mốc kết thúc recipe (val foreground = running) sau gate")
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
        // Đã EXTRACT thật (không còn bản merge inline chép tay ở AssistantLauncher): chữ ký inline cũ biến mất.
        assertFalse(
            assistant.contains("filter { it.isNotEmpty() && it != \"null\" }"),
            "merge inline cũ ở AssistantLauncher phải được thay bằng FloatAppList.merge, không còn chép tay",
        )
    }

    @Test
    fun `VietMapAutostart ghi byd_float_app_list + cap SYSTEM_ALERT_WINDOW cho VietMap`() {
        assertTrue(
            recipe.contains("settings put global byd_float_app_list"),
            "phải ghi lại danh sách float toàn cục byd_float_app_list",
        )
        assertTrue(
            recipe.contains("appops set \$PKG SYSTEM_ALERT_WINDOW allow"),
            "bóng VietMap cần CẢ membership list LẪN quyền SYSTEM_ALERT_WINDOW",
        )
        // $PKG buộc = vn.vietmap.live ⇒ appops nhắm đúng gói VietMap (grep-confirm target của EXIT).
        assertTrue(
            autostart.contains("const val PKG = NavApps.VIETMAP_LIVE"),
            "PKG phải trỏ NavApps.VIETMAP_LIVE để appops set đúng gói",
        )
        assertEquals("vn.vietmap.live", NavApps.VIETMAP_LIVE)
    }

    @Test
    fun `recipe bi cong bang bubble bat + co mot-lan chua set`() {
        // Gate đứng TRƯỚC mọi lệnh ghi — không có gate thì recipe chạy mỗi autostart (spam Settings/appops).
        val gate = autostart.indexOf("Prefs.vmBubbleEnabled(app) && !Prefs.vmFloatWhitelistApplied(app)")
        val put = autostart.indexOf("settings put global byd_float_app_list")
        assertTrue(gate in 0 until put, "cổng bubble + cờ một-lần phải đứng TRƯỚC lệnh ghi byd_float_app_list")
    }

    @Test
    fun `co mot-lan chi set khi thanh cong, va recipe degrade-safe (khong chan launch)`() {
        // runCatching bọc toàn recipe ⇒ hỏng KHÔNG ném ra ngoài ⇒ launch phía dưới vẫn chạy (degrade-safe).
        assertTrue(
            recipe.contains("runCatching {") && recipe.contains(".onFailure"),
            "recipe phải bọc runCatching + onFailure để một lần hỏng không chặn autostart/launch",
        )
        // Cờ set NẰM TRONG runCatching, SAU 2 lệnh ghi ⇒ hỏng giữa chừng thì cờ KHÔNG set ⇒ lần sau thử lại.
        val put = recipe.indexOf("settings put global byd_float_app_list")
        val appops = recipe.indexOf("appops set \$PKG SYSTEM_ALERT_WINDOW allow")
        val setFlag = recipe.indexOf("Prefs.setVmFloatWhitelistApplied(app, true)")
        val onFailure = recipe.indexOf(".onFailure")
        assertTrue(setFlag > put && setFlag > appops, "cờ một-lần phải set SAU khi cả 2 lệnh ghi đã phát")
        assertTrue(setFlag in 0 until onFailure, "cờ phải nằm TRONG nhánh thành công (trước .onFailure), không set khi hỏng")
    }
}
