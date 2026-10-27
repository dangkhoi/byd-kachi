package com.byd.clusternav.automation

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TELEGRAM-REMOTE (spec `kachi-telegram-remote.html` R2/R3/R4) — khoá DÂY NỐI phía `:app` của luật ưu tiên.
 *
 * Quét SOURCE (dự án không dựng Activity/Service trong JVM thuần — không Robolectric). Logic thuần đã có bài
 * red-when-reverted ở `:core` (`PendingNavPolicyTest`); bài này chỉ canh các bất biến NỐI DÂY mà một lượt sửa
 * vô ý dễ phá:
 *  1. `AutomationService` gọi `applyPending` TRƯỚC `tick` và BỎ `tick` khi pending bắn (short-circuit — R2);
 *  2. `applyPending` đóng dấu `NavAutomationFired` (overwrite lịch — R3) rồi `PendingNavStore.consume` (một-lần — R4);
 *  3. `anyEnabled` giữ vòng FGS sống khi có pending (nếu không, owner chỉ-Telegram sẽ không bao giờ được đọc).
 */
class PendingNavWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$rel")
    private val service by lazy { code("automation/AutomationService.kt") }
    private val applier by lazy { code("automation/ScheduledNavApplier.kt") }

    @Test
    fun `FGS goi applyPending truoc tick va short-circuit`() {
        val i = service.indexOf("applyPending")
        val j = service.indexOf("ScheduledNavApplier.tick(")
        assertTrue(i in 0 until j, "applyPending phải đứng TRƯỚC tick trong vòng FGS (ưu tiên pending)")
        assertTrue(service.contains("if (pending == ScheduledNavApplier.PendingOutcome.NONE)"),
            "chỉ NONE mới chạy lịch: FIRED ⇒ bỏ nhịp (R2), WAITING ⇒ lịch không chen trước điểm đến chủ động (một lượt đọc kho)")
    }

    @Test
    fun `applyPending overwrite lich roi consume`() {
        assertTrue(applier.contains("ScheduledNavPolicy.blocked(TripStart.currentId(app))"), "điểm đến chủ động thắng lịch của CHUYẾN (R3)")
        assertTrue(applier.contains("PendingOutcome.WAITING") && !service.contains("PendingNavStore.read(app) != null }.getOrDefault(false)\n                            if"),
            "chờ GPS trả WAITING; vòng FGS không đọc kho lần hai")
        assertTrue(applier.contains("PendingNavStore.consume("), "bắn xong ⇒ consume (một-lần, R4)")
        assertTrue(applier.contains("launchPlace("), "bắn qua launchPlace — cùng cửa với lịch, không đường ghi thứ hai")
        assertTrue(applier.contains("isStale("), "điểm đến chờ quá hạn ⇒ bỏ (P2-14)")
    }

    @Test
    fun `GPS bat buoc + token khong lot log + offset ben`() {
        val svc = code("telegram/TelegramBridgeService.kt")
        val api = code("telegram/TelegramApi.kt")
        assertTrue(svc.contains("PendingNavStore.write(app, dest, requireGps = true)"), "điểm đến từ Telegram: GPS bắt buộc (owner 26/10)")
        assertTrue(svc.contains("setTelegramOffset("), "offset lưu bền, at-most-once (P2-10)")
        assertTrue(svc.contains("isInteractive") || svc.contains("interactive("), "không poll khi màn tắt (P1-8)")
        // P0-1: không throwable nào bị log (message = url+token); không in url/base() ra log.
        assertTrue(!Regex("""Log\.[wei]\([^)]*,\s*\w+\s*\)""").containsMatchIn(api) ||
            !api.contains("base()"), "TelegramApi không được log throwable (token trong message URL)")
        assertTrue(!api.contains("conn.inputStream.bufferedReader"), "đọc body qua responseCode-gated, không inputStream ném ở 4xx (P0-1)")
    }

    @Test
    fun `anyEnabled giu vong song khi co pending`() {
        assertTrue(service.contains("PendingNavStore.read(app) != null"),
            "có pending ⇒ anyEnabled true ⇒ vòng FGS sống để nhịp 60s đọc (owner có thể chỉ dùng Telegram)")
    }
}
