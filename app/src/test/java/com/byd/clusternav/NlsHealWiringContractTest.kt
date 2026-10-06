package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 R-HUD — BÀI CANH DÂY NỐI (spec `docs/specs/kachi-286-field-fixes.html` §3.9 / §4.7) ═══════════════════════
 *
 * Luật thuần đã khoá ở `:core` (`NlsHealPolicyTest`, `NlsLiveDumpTest`, `LaneSessionClockTest`, `NavHudStatusTest`) và
 * chuỗi lệnh ở `:car-integration` (`NlsHealShellTest`). Bài này canh phần chỉ Android mới có: AI gọi, ở ĐÂU, theo THỨ TỰ
 * nào, phiên NỀN hay HỎI. Mọi vùng cắt bằng [SourceRoots.body] trên mã đã bỏ chú thích (nổ nếu mốc không còn).
 */
class NlsHealWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val heal by lazy { code("src/main/java/com/byd/clusternav/NlsHeal.kt") }
    private val early by lazy { code("src/main/java/com/byd/clusternav/EarlyShellChannel.kt") }
    private val rebind by lazy { code("src/main/java/com/byd/clusternav/RebindReceiver.kt") }
    private val connect by lazy { code("src/main/java/com/byd/clusternav/NavConnect.kt") }
    private val bridge by lazy { code("src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt") }
    private val listener by lazy { code("src/main/java/com/byd/clusternav/NavNotificationListener.kt") }
    private val broadcaster by lazy { code("src/main/java/com/byd/clusternav/ClusterBroadcaster.kt") }
    private val owner by lazy { code("src/main/java/com/byd/clusternav/NavigationHudOwner.kt") }
    private val section by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsNav.kt") }
    private val rows by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsNavHudRows.kt") }

    private fun body(src: String, sig: String) = SourceRoots.body(src, sig)

    private fun order(src: String, vararg tokens: String) {
        var at = -1
        tokens.forEach { t ->
            val i = src.indexOf(t, at + 1)
            assertTrue(i > at, "thiếu hoặc sai thứ tự: `$t` phải đứng sau `${tokens.getOrNull(tokens.indexOf(t) - 1)}`\n$src")
            at = i
        }
    }

    // ── S1: hai lối vào tự động ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chuoi SAN goi NlsHeal sau cong man sang va truoc chuyen len xe`() {
        order(body(early, "private fun readyChain("), "WakeEpochPolicy.shouldRun(prev, epoch, ShellReadiness.isUp(), interactive(app))", "NlsHeal.onReady(app)", "TripStart.onReady(app)")
    }

    @Test
    fun `nhip watchdog 60 s goi NlsHeal chi voi ACTION_WATCHDOG`() {
        val b = body(rebind, "override fun onReceive(")
        assertTrue(Regex("""if\s*\(action == ACTION_WATCHDOG\)\s*NlsHeal\.onWatchdog\(context\)""").containsMatchIn(b), b)
    }

    @Test
    fun `luot tu dong qua cong thuan TRUOC moi phien shell, doc truoc khi ban, phien NEN`() {
        val b = body(heal, "private fun autoPass(")
        order(b, "NlsHealPolicy.step(facts(app)", "NlsHealPolicy.Step.CHECK", "busy.compareAndSet(false, true)", "NlsHealShell.run(")
        assertTrue("LocalShellRetry.BACKGROUND_READ_CAP" in b, "lượt tự động là phiên NỀN (qua cổng READY-AT-HOME)")
        assertTrue("checkFirst = true" in b, "lượt tự động PHẢI đọc dump trước khi ghi (CLAUDE.md §5)")
        assertTrue(Regex("""fireGate = \{ Prefs\.enabled\(app\) && interactive\(app\) == true \}""").containsMatchIn(b),
            "cổng phút chót phải hỏi lại công tắc + màn")
        assertTrue("NlsHealPolicy.record(" in b, "phải ghi sổ (trần + một lần mỗi tiến trình)")
    }

    @Test
    fun `cong doc cong tac Dan duong va quyen thong bao that`() {
        val b = body(heal, "private fun facts(")
        assertTrue("navEnabled = Prefs.enabled(app)" in b, "owner 03/10: kiểm 'enabled' TRƯỚC khi bind")
        assertTrue("granted = PermissionPreflight.notificationListenerGranted(app)" in b, "dùng CHUNG phép đọc của Preflight")
        assertTrue("shellUp = ShellReadiness.isUp()" in b)
    }

    // ── S2: đường bấm tay ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cong tac va nut Ket noi lai di phien HOI va bao ket qua that`() {
        val u = body(heal, "private fun userPass(")
        assertTrue("LocalShellRetry.USER_READ_CAP" in u, "bấm tay = phiên HỎI — không bị cổng nền chặn (2.85)")
        assertTrue("return run.outcome" in u, "trả kết quả THẬT của phiên")
        assertTrue("NlsHeal.userReconnect(ctx, onResult)" in body(connect, "fun reconnect("))
        assertTrue("NlsHeal.userEnsure(ctx, onResult)" in body(connect, "fun ensureConnected("))
        assertFalse("doReconnect" in connect, "lõi cũ in 'xong' cả khi phiên bị chặn — phải gỡ")
        assertFalse("reconnect qua dadb xong" in SourceRoots.text("src/main/java/com/byd/clusternav/NavConnect.kt"))
        assertTrue("LocalShellRetry.USER_READ_CAP" in body(connect, "private fun doSelfGrant("), "cấp quyền từ công tắc cũng là phiên HỎI")
    }

    @Test
    fun `cau goi onDone theo ket qua that, khong true ngay luc bam`() {
        val nav = body(body(bridge, "fun setNavEnabled("), "if (notificationAccessGranted()) {")
        assertTrue("NavConnect.ensureConnected(app) { r ->" in nav && "onDone(r.ok)" in nav, nav)
        assertFalse("onDone(true)" in nav, nav)
        val rec = body(body(bridge, "fun reconnect("), "if (notificationAccessGranted()) {")
        assertTrue("NavConnect.reconnect(app) { r ->" in rec && "onDone(r.ok)" in rec, rec)
        assertFalse("onDone(true)" in rec, rec)
    }

    // ── S4: dòng tình trạng ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `trang Dan duong co dong HUD, doc lai khi trang hien`() {
        assertTrue("hud.build(body)" in body(section, "private fun nav("))
        assertTrue("hud.refresh()" in body(section, "private fun refreshStatus("))
        val b = body(rows, "fun build(")
        order(b, "addOnAttachStateChangeListener", "onShown()", "readNavHudTruth")
        assertTrue("R.string.kachi_nav_hud_note" in b, "câu cố định: HUD chỉ ăn Google Maps")
        assertEquals(3, Regex("""HudWriteRecord\.note\(""").findAll(owner).count(), "ghi nhận CẢ ba nhánh ghi HAL")
    }

    // ── S9 / S10 ────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `S10 GRANTED dat TRUOC cong cong tac`() {
        val b = body(listener, "override fun onListenerConnected(")
        order(b, "connectedAtElapsed =", "NavigationPermission.GRANTED", "if (!Prefs.enabled(applicationContext)) return")
    }

    @Test
    fun `S9 emitLane so moc phien theo dong ho don dieu`() {
        val b = body(broadcaster, "fun emitLane(")
        assertTrue("LaneSessionClock.isBeforeSession(" in b, b)
        assertFalse(Regex("""updatedAt\s*<\s*sessionSelectedAtEpochMs""").containsMatchIn(b), "phép so hai giờ TƯỜNG cũ")
        val mark = body(broadcaster, "private fun markSessionStart(")
        assertTrue("SystemClock.elapsedRealtime()" in mark && "System.currentTimeMillis()" in mark, mark)
    }

    // ── CLAUDE.md §8 + global §4.1 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `moi ham moi co cho goi production`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.name to it.readText() }.toList()
        }
        mapOf(
            "NlsHeal.onReady(" to "NlsHeal.kt", "NlsHeal.onWatchdog(" to "NlsHeal.kt",
            "NlsHeal.userEnsure(" to "NlsHeal.kt", "NlsHeal.userReconnect(" to "NlsHeal.kt",
            "NlsHeal.readForSettings(" to "NlsHeal.kt", "NlsHealShell.run(" to "NlsHealShell.kt",
            "NlsHealShell.read(" to "NlsHealShell.kt", "NlsLiveDump.verdict(" to "NlsLiveDump.kt",
            "NlsHealPolicy.toggleCommand(" to "NlsHealPolicy.kt", "LaneSessionClock.isBeforeSession(" to "LaneSessionClock.kt",
            "HudWriteSummary.of(" to "HudWriteSummary.kt", "NavHudStatus.source(" to "NavHudStatus.kt",
            "HudWriteRecord.note(" to "HudWriteRecord.kt", "readNavHudTruth" to "ClusterNavBridgeHud.kt",
        ).forEach { (call, home) ->
            assertTrue(all.any { (name, src) -> name != home && call in src }, "`$call` không có chỗ gọi ngoài $home (CLAUDE.md §8)")
        }
    }

    @Test
    fun `tep cham toi khong vuot 500 dong`() {
        listOf(
            "src/main/java/com/byd/clusternav/NlsHeal.kt", "src/main/java/com/byd/clusternav/NavConnect.kt",
            "src/main/java/com/byd/clusternav/NavNotificationListener.kt", "src/main/java/com/byd/clusternav/ClusterBroadcaster.kt",
            "src/main/java/com/byd/clusternav/NavigationHudOwner.kt", "src/main/java/com/byd/clusternav/RebindReceiver.kt",
            "src/main/java/com/byd/clusternav/EarlyShellChannel.kt",
            "src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt", "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeHud.kt",
            "src/main/java/com/byd/clusternav/launcher/SettingsSectionsNav.kt", "src/main/java/com/byd/clusternav/launcher/SettingsNavHudRows.kt",
            "src/main/java/com/byd/clusternav/launcher/PermissionPreflight.kt",
        ).forEach { rel ->
            val n = SourceRoots.text(rel).lines().size
            assertTrue(n <= 500, "$rel dài $n dòng — trần là 500 (CLAUDE.md §4.1)")
        }
    }
}
