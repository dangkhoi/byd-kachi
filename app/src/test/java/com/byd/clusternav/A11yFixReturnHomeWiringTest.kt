package com.byd.clusternav

import com.byd.clusternav.launcher.HomeActivityCmd
import com.byd.clusternav.modules.navaccess.AccessibilityRebind
import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.readText

/**
 * Khoá ĐƯỜNG DÂY của quyết định owner 2.83-B ("nút sửa phải trả về màn nhà, không để app ô mồ côi freeform"):
 * lệnh tách rời mà `NavConnect` bắn đi PHẢI mang đuôi đo-rồi-mới-Home của [AccessibilityRebind], và KHÔNG có
 * đường nào khác trong app bắn Home mù sau lượt chữa.
 *
 * Vì sao phải chặn "Home mù": lượt tự chữa lúc mở xe chạy đúng lúc tài xế hay lùi xe; camera lùi của BYD là một
 * activity (`com.byd.avc/.AutoVideoActivity`), Home mù sẽ che nó. Cổng đo nằm TRONG lệnh tách rời (tiến trình
 * Kotlin lúc đó đã chết) — logic và fixture thật ở `ForceStopReturnHomeTest` (:core).
 *
 * Ngoại lệ CÓ CHỦ Ý (owner chốt 29/09): lượt người dùng TỰ BẤM "Sửa ngay" luôn về màn nhà — vẫn do hàm dựng lệnh
 * của :core gắn (`AccessibilityRebind.HomeTail.ALWAYS`), không phải `escalateIfStuck` tự bấm Home. Bài
 * `bam tay LUON ve man nha…` khoá chuỗi cờ `userAsked`; lượt TỰ ĐỘNG vẫn đo-rồi-mới-Home.
 */
class A11yFixReturnHomeWiringTest {

    private val navConnect = SourceRoots.codeOf("src/main/java/com/byd/clusternav/NavConnect.kt")
    private val escalate = SourceRoots.body(navConnect, "private fun escalateIfStuck(")

    @Test
    fun `lenh tu giet di qua ham dung lenh CO duoi tra man nha`() {
        assertTrue(
            escalate.contains("AccessibilityRebind.forceStopRebindCommand("),
            "lệnh chữa phải dựng bằng hàm của :core — nơi DUY NHẤT gắn đuôi đo-rồi-Home",
        )
        assertTrue(escalate.contains("sh(cmd)"), "lệnh dựng xong phải được bắn đi nguyên vẹn")
        val built = AccessibilityRebind.forceStopRebindCommand("", "com.byd.launcher",
            "com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService")
        assertTrue(
            built.contains(AccessibilityRebind.RETURN_HOME_IF_ORPHANED) && built.contains(HomeActivityCmd.GO_HOME),
            "lệnh thật mà NavConnect nhận phải mang đuôi trả màn nhà (hàm mới có call site — CLAUDE.md §8)",
        )
    }

    /**
     * Owner chốt 29/09: *"nút tự chữa đó phải trả về home, ko để app mồ côi"* — [ĐO máy ảo E2E 2.83 ca 5c] "Sửa ngay"
     * với Maps toàn màn nằm dưới ⇒ kết thúc ở Maps. Bấm tay ⇒ Home VÔ ĐIỀU KIỆN; lớp 1/2 ⇒ vẫn đo-rồi-mới-Home. Bài
     * này lần theo CẢ chuỗi cờ `userAsked` từ nút tới hàm dựng lệnh — đứt một mắt là nút lại kết thúc ở app khác
     * (hoặc tệ hơn: lượt TỰ ĐỘNG bấm Home mù lên app người lái vừa mở).
     */
    @Test
    fun `bam tay LUON ve man nha, lop 1-2 van do roi moi Home`() {
        assertTrue(
            escalate.contains("homeTail = AccessibilityRebind.homeTailFor(userAsked)"),
            "đuôi về nhà phải do AI YÊU CẦU quyết định (tham số của hàm dựng lệnh), không nhân bản chuỗi lệnh",
        )
        val detailed = SourceRoots.body(navConnect, "fun grantAccessibilityDetailed(")
        assertTrue(detailed.contains("doGrantResultWithTimeout(app, userAsked = reset)"), "nút / công tắc (reset=true) = bấm tay")
        val timeout = SourceRoots.body(navConnect, "private fun doGrantResultWithTimeout(")
        assertTrue(timeout.contains("doGrantResult(app, userAsked)"))
        val grant = SourceRoots.body(navConnect, "private fun doGrantResult(app: Context, userAsked: Boolean): GrantResult")
        assertTrue(grant.contains("grantViaShell(app, myGen, userAsked)"))
        val shell = SourceRoots.body(navConnect, "private fun grantViaShell(app: Context, myGen: Int, userAsked: Boolean): GrantResult")
        assertTrue(shell.contains("escalateIfStuck(app, sh, userAsked)"))
        val lifecycle = SourceRoots.body(navConnect, "internal fun escalateOnLifecycle(")
        assertTrue(
            lifecycle.contains("escalateIfStuck(app, sh, userAsked = false, phase = phase, fireGate = fireGate)"),
            "lớp 1/2 KHÔNG phải bấm tay ⇒ homeTailFor(false) = đo-rồi-mới-Home, không đá người lái khỏi app họ vừa mở",
        )
        val keys = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeKeys.kt")
        assertTrue(
            SourceRoots.body(keys, "fun ClusterNavBridge.checkFix(").contains("NavConnect.grantAccessibilityDetailed(app, reset = true)"),
            "nút 'Kiểm tra / Sửa ngay' phải là lượt bấm tay",
        )
        val keepAlive = SourceRoots.codeOf("src/main/java/com/byd/clusternav/VoiceKeyKeepAliveService.kt")
        assertFalse(keepAlive.contains("reset = true"), "watchdog 30 s KHÔNG được mạo danh bấm tay (Home mù lúc đang lái)")
    }

    @Test
    fun `duong chua KHONG tu bam Home mu sau khi ban lenh`() {
        for (token in listOf("GO_HOME", "category.HOME", "KEYCODE_HOME", "keyevent 3", "HOME_FRONT")) {
            assertFalse(
                escalate.contains(token),
                "`$token` trong escalateIfStuck = Home KHÔNG qua cổng đo ⇒ có thể che camera lùi hoặc app người dùng vừa mở",
            )
        }
    }

    @Test
    fun `chuoi Home he thong chi nam o MOT cho`() {
        // DRY (CLAUDE.md global §4.1): chuỗi từng nằm rải ở VietMapAutostart; nay mọi đường đọc HomeActivityCmd.GO_HOME.
        val literal = "\"" + HomeActivityCmd.GO_HOME + "\""
        val owners = SourceRoots.moduleSourceRoots()
            .flatMap { root -> Files.walk(root).use { s -> s.filter { it.extension == "kt" }.toList() } }
            .filter { KotlinSource.stripComments(it.readText()).contains(literal) }
            .map { it.fileName.toString() }
            .distinct()
        assertEquals(listOf("HomeActivityCmd.kt"), owners, "literal Home hệ thống chỉ được khai ở HomeActivityCmd")
        assertTrue(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/VietMapAutostart.kt").contains("sh(HomeActivityCmd.GO_HOME)"),
            "đường trả nền VietMap lúc boot giữ nguyên hành vi (cùng byte), chỉ đổi sang hằng dùng chung",
        )
    }
}
