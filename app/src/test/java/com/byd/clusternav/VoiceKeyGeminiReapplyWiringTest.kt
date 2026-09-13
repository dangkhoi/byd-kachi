package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F4e (owner 2026-08-25): *"mở app lên, nhấn hold mic không work, xoá record binding gemini đi add lại thì
 * nó mới active lại assistant default rồi mới work; nút rotation mở kiki thì OK ngay"*.
 *
 * Gốc [ĐO]: đích Gemini đi `keyevent 231` (route tới TRỢ LÝ HỆ THỐNG) ⇒ chỉ ra Gemini khi
 * `AssistantLauncher.setSystemAssistant` đã đặt trợ lý = Google/Gemini; mà từ F3 recipe đó CHỈ chạy ở nút
 * "Thêm" khi cấu hình ĐỔI. Mở app / sau reboot thì chưa set ⇒ 231 route sai. Kiki đi `getLaunchIntentForPackage`
 * (mở app THẲNG) nên không phụ thuộc trợ lý hệ thống ⇒ OK ngay (khớp owner).
 *
 * Quét source vì đường này cần Context thật (không Robolectric ở :app) — cùng khuôn VoiceKeyAdbApprovalWiringTest.
 *
 * ⚠ 2026-09-13 (S3 · R1): màn ClusterNav cũ bị gỡ, nên đường re-apply "lúc mở app" (`MainActivity.onCreate`)
 * chuyển thành **một việc do người dùng bấm**: nút *Kiểm tra / Sửa ngay* ở nhóm *Phím vô-lăng*
 * (`ClusterNavBridge.checkFix` → `reapplyGeminiAssistant`). Lý do đổi chỗ nằm ở KDoc của hàm đó: Kachi là
 * launcher, `onCreate` của nó chạy mỗi lần bấm Home, mà recipe này đi dadb chờ tới ~31 s.
 */
class VoiceKeyGeminiReapplyWiringTest {

    private fun read(relative: String): String {
        val current = Path.of(System.getProperty("user.dir"))
        val base = if (Files.exists(current.resolve("src"))) current else current.resolve("app")
        return base.resolve("src/main/java/com/byd/clusternav/$relative").toFile().readText()
    }

    private val keys by lazy { read("launcher/ClusterNavBridgeKeys.kt") }

    /** Thân hàm re-apply (cửa sổ ~900 ký tự từ chỗ định nghĩa — hàm này ~10 dòng). */
    private val reapplyBody by lazy {
        val start = keys.indexOf("internal fun ClusterNavBridge.reapplyGeminiAssistant()")
        assertTrue(start >= 0, "thiếu reapplyGeminiAssistant — đường re-apply trợ lý đã mất")
        keys.substring(start, minOf(start + 900, keys.length))
    }

    @Test
    fun `re-apply duoc GOI tu nut Kiem tra Sua ngay, khong chi dinh nghia`() {
        // Lời gọi phải là CÂU LỆNH đứng riêng trong `checkFix` (không phải chuỗi trong comment, không phải dòng
        // định nghĩa). `checkFix` là nút "Kiểm tra / Sửa ngay" — bề mặt duy nhất còn lại của đường re-apply.
        val body = com.byd.clusternav.testsupport.SourceRoots.body(
            com.byd.clusternav.testsupport.KotlinSource.stripComments(keys),
            "fun ClusterNavBridge.checkFix(",
        )
        assertTrue(
            Regex("""(?m)^\s*reapplyGeminiAssistant\(\)\s*$""").containsMatchIn(body),
            "reapplyGeminiAssistant() phải được GỌI như một câu lệnh trong checkFix — nếu chỉ định nghĩa mà " +
                "không gọi thì bug 'hold mic không work sau reboot' vẫn còn",
        )
    }

    @Test
    fun `re-apply CHI khi co binding Gemini, chay setSystemAssistant NEN`() {
        assertTrue(reapplyBody.contains("hasGeminiBinding("),
            "cổng an toàn: CHỈ re-apply khi có binding TRỎ Gemini — owner chỉ dùng Kiki thì KHÔNG đụng dadb")
        assertTrue(reapplyBody.contains("setSystemAssistant("),
            "re-apply = chạy đúng recipe đặt trợ lý hệ thống = Google/Gemini (cùng hàm nút Thêm dùng)")
        assertTrue(reapplyBody.contains("Thread("),
            "chạy NỀN — recipe dadb (AWAIT_ADB_APPROVAL) chờ tới ~31s, chạy trên luồng vẽ là ANR")
    }

    /**
     * F4e boot (owner 08-25: *"kể cả khởi động nền hay full app đều enable service gemini"*). Boot headless
     * đi `BootSetupService` — không mở màn nào, nên chính nó phải đặt
     * Gemini làm trợ lý, nhưng với retry **NONE** (owner KHÔNG ở màn hình lúc boot để bấm cấp quyền ⇒ không
     * được chờ ~31s kẻo treo boot — F6).
     */
    @Test
    fun `boot headless cung re-apply Gemini voi retry NONE`() {
        val boot = read("BootSetupService.kt")
        assertTrue(boot.contains("hasGeminiBinding("),
            "boot phải gate theo binding Gemini (owner chỉ Kiki thì KHÔNG đụng dadb lúc boot)")
        assertTrue(
            Regex("""setSystemAssistant\([^)]*LocalShellRetry\.NONE""").containsMatchIn(boot),
            "boot headless phải đặt trợ lý = Gemini với retry NONE (boot không mở màn nào; NONE = không chờ " +
                "~31s vì owner không ở màn hình bấm cấp quyền — treo boot là F6)",
        )
    }
}
