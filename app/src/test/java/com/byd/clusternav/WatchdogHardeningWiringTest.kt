package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B1 (2026-09-25, `kachi-closeout-hardening` R3 — BG-11/BG-14/F6/F7): các cổng THUẦN ở `:core` (có test riêng:
 * `AccessibilityHealGatesTest`, `InstalledPackageGateTest`, `Pm25PollBackoffTest`) phải THẬT SỰ được nối vào đường chạy
 * trên xe. `:app` không có Robolectric (`android.util.Log` ném trong JVM) nên khoá dây bằng đọc mã như
 * [AccessibilityForceBindTest] / [VoiceKeyAdbApprovalWiringTest]; helper [SourceRoots.body] nổ nếu mốc không tồn tại.
 */
class WatchdogHardeningWiringTest {

    private fun code(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$rel")

    @Test
    fun `grantAccessibility hoi AccessibilityManager TRUOC khi mo phien dadb`() {
        val src = code("NavConnect.kt")
        val body = SourceRoots.body(src, "private fun doGrantResult(app: Context): GrantResult")
        assertTrue(body.contains("AccessibilityHealGates.grantOrSkip("), "đường grant phải đi qua cổng thuần")
        assertTrue(body.contains("boundPerAccessibilityManager(app)"), "cổng nhận kết quả binder (Boolean?), không cờ RAM")
        assertEquals(0, Regex("LocalDeviceShell\\.session\\(").findAll(body).count(), "doGrantResult KHÔNG được tự mở phiên dadb — chỉ nhánh shell của cổng mới mở")
        val shell = SourceRoots.body(src, "private fun grantViaShell(app: Context, myGen: Int): GrantResult")
        assertTrue(shell.contains("LocalDeviceShell.session("), "nhánh shell giữ nguyên đường dadb đầy đủ")
        assertTrue(shell.contains("forceRebindIfNeeded(keyPair, sh)"), "nhánh shell vẫn verify dumpsys + toggle ép rebind (fix 1.78)")
    }

    @Test
    fun `boundPerAccessibilityManager tra null khi binder khong hoi duoc, KHONG roi ve co RAM`() {
        // Hàm một-biểu-thức `= runCatching {…}.getOrNull()`: `body()` chỉ trả khối `{…}`, nên cắt tới hàm kế tiếp.
        val src = code("NavConnect.kt")
        val at = src.indexOf("fun boundPerAccessibilityManager(ctx: Context): Boolean?")
        assertTrue(at >= 0, "phải có boundPerAccessibilityManager trả Boolean?")
        val fn = src.substring(at, src.indexOf("fun reconnect(", at))
        assertTrue(fn.contains(".getOrNull()"), "ném ⇒ null (đi shell), không phải connected-flag")
        assertEquals(0, Regex("NavAccessibilitySource\\.connected").findAll(fn).count(), "không đọc cờ kẹt để quyết định bỏ shell")
    }

    @Test
    fun `alarm 60 s chi heal khi FGS keep-alive KHONG song`() {
        val rx = SourceRoots.body(code("RebindReceiver.kt"), "override fun onReceive(context: Context, intent: Intent?)")
        assertTrue(rx.contains("AccessibilityHealGates.alarmShouldHeal("), "receiver phải hỏi cổng thuần")
        assertTrue(rx.contains("VoiceKeyKeepAliveService.inProcessWatchdogAlive"), "sự thật FGS lấy từ cờ tĩnh sống theo tiến trình")
        // Đường heal vẫn còn (không bỏ fix on-car 1.78): grant vẫn được gọi trong nhánh true.
        assertTrue(rx.contains("NavConnect.grantAccessibility(context.applicationContext)"))
        // Alarm KHÔNG bị cancel (lưới cho ca tiến trình chết).
        assertEquals(0, Regex("\\.cancel\\(").findAll(code("RebindReceiver.kt")).count())
    }

    @Test
    fun `co inProcessWatchdogAlive bat khi watchdog chay va tat khi FGS dung`() {
        val src = code("VoiceKeyKeepAliveService.kt")
        val start = SourceRoots.body(src, "override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int")
        assertTrue(start.contains("inProcessWatchdogAlive = true"))
        assertTrue(start.contains("inProcessWatchdogAlive = false"), "nhánh phím-thoại OFF phải hạ cờ")
        val destroy = SourceRoots.body(src, "override fun onDestroy()")
        assertTrue(destroy.contains("inProcessWatchdogAlive = false"))
    }

    @Test
    fun `VmOverlayPosition applyOnOpen gate goi VietMap co cai TRUOC ResendGate`() {
        val fn = SourceRoots.body(code("VmOverlayPosition.kt"), "fun applyOnOpen(ctx: Context)")
        val i = fn.indexOf("vietMapInstalled(app)")
        val j = fn.indexOf("gate.shouldSend(")
        assertTrue(i in 0 until j, "kiểm 'có cài' phải đứng TRƯỚC ResendGate (không ghi nhận lượt gửi hụt)")
        val src = code("VmOverlayPosition.kt")
        assertTrue(src.contains("InstalledPackageGate(PKG_TTL_MS)") && src.contains("PackageQueries.packageInfo("), "dùng cổng thuần + cửa PackageManager chung (DRY)")
    }

    @Test
    fun `Pm25 poll dung Pm25PollBackoff va dem lai tu 0 khi doc duoc`() {
        val fn = SourceRoots.body(code("comfort/Pm25FilterApplier.kt"), "private fun startPollLoop(app: Context)")
        assertTrue(fn.contains("Pm25PollBackoff.nextIntervalMs(consecutiveInvalid, POLL_INTERVAL_MS)"))
        assertTrue(fn.contains("consecutiveInvalid = 0"), "đọc được ⇒ về 45 s ngay (không khoá vĩnh viễn)")
        assertTrue(fn.contains("var consecutiveInvalid = 0"), "bộ đếm là cục bộ của vòng ⇒ bật lại công tắc là đếm lại")
    }
}
