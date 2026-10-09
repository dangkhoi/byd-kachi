package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.98 · R18 (SLOT-ESCAPE-VD-RETURN) — DÂY NỐI ═════════════════════════════════════════════════════════════════════════
 *
 * Luật thuần ở `:core` (`EscapeReturnGuardTest` trên log nguyên mẫu máy ảo · `EscapeReturnBreakerTest` · `EscapeReturnWireTest` ·
 * `LegacyFreeformUndoTest`); `:app` không có Robolectric ⇒ bài này canh MÃ (đã bỏ chú thích). Mỗi khẳng định là một mắt xích:
 *  - host ô báo bảng TRƯỚC lệnh mở (app thoát ~1–3 s sau `am start`) và gỡ ở mọi đường thôi giữ (CLAUDE.md §8: hàm mới có chỗ gọi);
 *  - daemon đọc khung điều khiển, gắn/gỡ client, chỉ tra bảng tên hàm theo id; mọi lỗi đi vào cầu chì;
 *  - cầu chì bền chặn bản cài; DL5 tắt; R7 không còn trên đường chạy; đường trả R7 chạy lúc kênh lên.
 */
class SlotEscapeReturnWiringContractTest {

    private fun code(path: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$path")

    @Test
    fun `host o bao bang truoc lenh mo va go o moi duong thoi giu`() {
        val host = code("launcher/VdAppHost.kt")
        val launch = SourceRoots.body(host, "private fun launchInto(")
        val allow = launch.indexOf("SlotEscapeReturn.allow(context, inputClient, probeKey, displayId, p)")
        assertTrue(allow >= 0, "launchInto phải báo bảng")
        assertTrue(allow < launch.indexOf("sh(\"am force-stop \$p\")"), "báo TRƯỚC force-stop/am start")
        assertTrue(allow < launch.indexOf("SlotReturnRun.bringBackMarked("), "báo TRƯỚC cả đường K8/app đang sống")
        assertTrue("SlotEscapeReturn.allow(context, inputClient, probeKey, p.lease.displayId, p.pkg)" in SourceRoots.body(host, "private fun unpark("))
        assertTrue("SlotEscapeReturn.allow(context, inputClient, probeKey, v.display.displayId, shownPkg)" in SourceRoots.body(host, "fun adoptShown("))
        for (fn in listOf("fun release()", "fun park(", "fun relinquish(", "private fun onAppClosed(")) {
            assertTrue("SlotEscapeReturn.revoke(probeKey)" in SourceRoots.body(host, fn), "$fn phải gỡ bảng")
        }
        assertFalse("SlotEscape." in host.replace("SlotEscapeReturn.", ""), "R7 không còn trong host")
        // [ĐO máy ảo 09/10 22:52] màn chính dựng lại ⇒ `slot-taken` nhả màn ảo host cũ trước khi host cũ revoke ⇒ bảng còn màn chết.
        // Gốc của bảng là MÀN ẢO ⇒ gỡ ở chỗ duy nhất mọi đường nhả đi qua.
        val owner = code("launcher/SlotVdOwner.kt")
        assertTrue("SlotEscapeReturn.revokeDisplay(displayId)" in SourceRoots.body(owner, "internal fun free()"))
    }

    @Test
    fun `kenh daemon - khung dieu khien, chieu nguoc, the he cong`() {
        val main = code("system/inputd/InputDaemonMain.kt")
        val serve = SourceRoots.body(main, "private fun serve(")
        assertTrue("InputWireProtocol.controlLength(buf)" in serve)
        assertTrue("EscapeReturnDaemon.onConfig(String(body, Charsets.UTF_8))" in serve)
        val tcp = SourceRoots.body(main, "private fun serveTcp(")
        assertTrue(tcp.indexOf("handshake(input, token)") < tcp.indexOf("EscapeReturnDaemon.attach("), "chỉ client đã qua bắt tay nhận báo cáo")
        assertTrue("EscapeReturnDaemon.detach()" in tcp, "client rớt ⇒ daemon quên bảng (§5)")
        val client = code("system/inputd/InputDaemonClient.kt")
        assertTrue("fun setControl(frame: ByteArray?, wantsDaemon: Boolean)" in client)
        assertTrue("control?.let { f -> senderExecutor.execute" in SourceRoots.body(client, "private fun tryConnect("), "nối lại ⇒ gửi lại bảng")
        val container = code("AppContainer.kt")
        assertTrue("onReport = { line -> com.byd.clusternav.launcher.escape.SlotEscapeReturn.onReport(app, line) }" in container)
    }

    @Test
    fun `daemon - api chi theo id, moi loi vao cau chi, nghe mot lan`() {
        val d = code("system/inputd/EscapeReturnDaemon.kt")
        assertTrue("EscapeReturnWire.decodeConfig(text)" in d)
        assertFalse("getMethod(text" in d || "getMethod(c." in d, "không phản chiếu tên đọc từ socket")
        val failed = SourceRoots.body(d, "private fun onFailed(")
        assertTrue("EscapeReturnGuard.decide(e, sys.stacks(), sys.windowingMode(e.requestedDisplay))" in failed, "đọc hệ chỉ sau precheck")
        val order = listOf("EscapeReturnGuard.precheck(", "EscapeReturnGuard.decide(", "sys.move(", "EscapeReturnGuard.verify(", "breaker.onAfter(")
        order.zipWithNext().forEach { (a, b) -> assertTrue(failed.indexOf(a) in 0 until failed.indexOf(b), "$a trước $b") }
        assertTrue("breaker.onMoveThrew(pkg, t.javaClass.name" in failed, "lệnh ném ⇒ cầu chì phân loại theo tên lớp")
        val binder = code("system/inputd/TaskStackBinder.kt")
        assertTrue("getDeclaredField(api.failedTxnField)" in binder, "mã giao dịch đọc lúc chạy, không ghi cứng số")
        assertTrue("getMethod(api.moveStack" in binder && "getMethod(api.stackInfos)" in binder)
        assertTrue("throw e.targetException ?: e" in binder, "NPE phía system_server phải tới cầu chì đúng tên lớp")
        // Soát Pass 12: [P2] client gửi bảng có api sau khi đã ngắt bền ⇒ phát lại báo cáo bền (Kachi mới ghi được dấu) · [P3] mọi việc
        // trên luồng daemon qua lưới bắt cuối — ngoại lệ lọt là Android giết cả tiến trình bơm chạm.
        val cfg = SourceRoots.body(d, "fun onConfig(")
        assertTrue(cfg.indexOf("breaker.persisted?.let(::report)") in 0 until cfg.indexOf("report(EscapeReport.Off(\"tripped:"), "phát lại bền TRƯỚC báo tắt")
        assertTrue("fun onConfig(text: String) = guarded(" in d && "{ f -> guarded(\"event\") { onFailed(f) } }" in d)
        assertTrue("catch (e: Exception)" in SourceRoots.body(d, "private fun guarded("))
    }

    @Test
    fun `cau chi ben, DL5 tat, R7 go, duong tra R7 khi kenh len`() {
        val ret = code("launcher/escape/SlotEscapeReturn.kt")
        assertTrue("EscapeReturnBreaker.persistActive(prefs(ctx).getString(TRIP_KEY, null), BuildConfig.VERSION_CODE)" in ret)
        assertTrue("if (r is EscapeReport.Tripped && r.persist)" in SourceRoots.body(ret, "fun onReport("))
        assertTrue("if (!api.usableOn(Build.VERSION.SDK_INT)) return null" in SourceRoots.body(ret, "private fun api("))
        val profile = code("modules/clustercast/ClusterProfile.kt")
        assertTrue("escapeReturn = escapeReturnFor(5)," in profile, "DL5 (Android 12) chưa đo ⇒ tắt")
        assertTrue("fun escapeReturnFor(diLink: Int): EscapeReturnApi? = if (diLink >= 5) null else EscapeReturnApi.ANDROID_10_R47" in profile)
        assertTrue("escapeReturn = escapeReturnFor(diLink)," in profile, "chuỗi override (parse) cũng theo đời DiLink")
        for (gone in listOf("launcher/escape/SlotEscape.kt", "launcher/escape/SlotEscapeHome.kt", "launcher/escape/EscapeCoverOverlay.kt",
            "launcher/escape/EscapeMarkerStore.kt", "modules/navaccess/A11yOverlayPort.kt")) {
            assertFalse(SourceRoots.exists("src/main/java/com/byd/clusternav/$gone"), "$gone phải gỡ (R7 SUPERSEDED)")
        }
        val windows = code("launcher/LauncherWindows.kt")
        assertTrue("if (reason == \"shell-up\") submit { LegacyFreeformCleanup.runOnce(activity, s) }" in SourceRoots.body(windows, "fun sweepFloating("))
        val probe = code("launcher/SlotLiveProbe.kt")
        assertFalse("grace" in probe || "burst" in probe.lowercase(), "nhịp ân hạn + burst chỉ phục vụ R7 ⇒ gỡ")
        assertEquals(0, Regex("SlotEscape::shade").findAll(code("launcher/SettingsDialogs.kt")).count())
    }
}
