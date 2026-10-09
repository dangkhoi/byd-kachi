package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.98 · R3 · SLOT-ELSEWHERE-TWO-HOMES — DÂY NỐI ════════════════════════════════════════════════════════════════════════
 *
 * Luật thuần + chuỗi lỗi trên bản đọc nguyên văn ở `:core` (`SlotProbeScopeTest`). `:app` không có Robolectric ⇒ bài này canh MÃ
 * (đã bỏ chú thích) — mỗi khẳng định là một mắt xích mà bài thuần không thấy:
 *  - bộ đo ô hỏi sổ chủ màn ảo (`SlotVdOwner.held`) và truyền `SlotProbeScope.otherHomes` vào `SlotPresence.of` ở ĐÚNG phép "ở chỗ
 *    khác" — chỉ khi ô vắng app (lười), vẫn MỘT lệnh `am stack list` mỗi nhịp (0 lệnh mới);
 *  - khoá đo = `"$owner#$slot"` (`SlotProbeScope.ownerOf` đọc ngược) và chủ của một màn chính là `"ws@…"` (`SlotProbeScope.isHome`);
 *    ô 7 (`park`) / dàn dựng (`stage`) KHÔNG phải màn chính ⇒ một màn Kachi giữ nguyên hành vi 2.93 (CLAUDE.md §6);
 *  - đường câu báo của host không đổi (cờ `elsewhere` vẫn chỉ đến từ `SlotLiveness`).
 */
class SlotElsewhereTwoHomesWiringContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    private val probe by lazy { code("SlotLiveProbe.kt") }
    private val owner by lazy { code("SlotVdOwner.kt") }

    @Test
    fun `bo do o bo man ao o cua man chinh KHAC khoi phep o cho khac, 0 lenh moi`() {
        val sweep = SourceRoots.body(probe, "private fun sweep()")
        assertEquals(1, Regex("shell\\(\"am stack list\"\\)").findAll(sweep).count(), "vẫn MỘT lệnh mỗi nhịp")
        assertTrue("val held by lazy(LazyThreadSafetyMode.NONE) { SlotVdOwner.held() }" in sweep, "sổ đọc lười, một lần mỗi nhịp")
        // 2.98 · R12 — ĐỔI GHIM có lý do: một `presence` cho cả ELSEWHERE lẫn GONE (app sập ngay lượt mở); đoản mạch giữ nguyên.
        val away = sweep.substring(sweep.indexOf("val presence = "))
        assertTrue(away.startsWith("val presence = if (alive) SlotPresence.IN_SLOT"), "ô còn app (alive) ⇒ không chạm sổ (đoản mạch)")
        assertTrue("else SlotPresence.of(out, sub.pkg, sub.displayId, SlotProbeScope.otherHomes(held, sub.key, sub.displayId))" in away)
        assertTrue("val away = presence == SlotPresence.ELSEWHERE" in away)
        assertTrue(sweep.indexOf("val held by lazy") < sweep.indexOf("snapshot.forEach"), "một sổ cho cả nhịp, không mỗi ô một lần")
    }

    @Test
    fun `so chu man ao chi doc RAM, cap chu - display`() {
        val held = SourceRoots.body(owner, "fun held()")
        assertTrue("ledger.live().map { SlotProbeScope.Held(it.owner, it.handle.displayId) }" in held)
        assertFalse("free(" in held || "release" in held, "chỉ đọc — không nhả gì")
    }

    @Test
    fun `khoa do va ten chu khop luat thuan - man chinh ws@, o 7 va dan dung khong phai`() {
        val host = code("VdAppHost.kt")
        // 2.98 · R14 — ĐỔI GHIM có lý do: khoá dựng bằng `SlotVdLedger.keyOf` (dùng chung với sổ màn ảo để gỡ bộ đo host cũ) — cùng dạng.
        assertTrue("private val probeKey = SlotVdLedger.keyOf(owner, slot)" in host, "SlotProbeScope.ownerOf đọc ngược đúng dạng khoá này")
        assertTrue("fun keyOf(owner: String, slot: Int) = \"\$owner#\$slot\"" in SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/SlotVdLedger.kt"))
        assertTrue("private val owner: String = \"ws\"," in host, "chủ mặc định = SlotProbeScope.HOME_OWNER")
        assertTrue("private val hostOwner = \"ws@\${System.identityHashCode(this)}\"" in code("WorkspaceView.kt"),
            "mỗi cây workspace một chủ 'ws@…' ⇒ hai màn chính là hai chủ khác nhau")
        assertTrue("private const val OWNER = \"park\"" in code("ParkedApps.kt"))
        assertTrue("const val OWNER = \"stage\"" in code("behind/StagingDisplay.kt"))
        // Chủ đo được truyền nguyên khoá host ở mọi lối watch (⇱ dùng CÙNG probeKey) — không lối nào tự đặt khoá khác.
        val full = code("SlotReturnRun.kt")
        assertEquals(2, Regex("SlotLiveProbe\\.watch\\(probeKey, ").findAll(full).count())
        assertEquals(4, Regex("SlotLiveProbe\\.watch\\(probeKey, ").findAll(host).count())
    }

    @Test
    fun `cau bao cua host khong doi - elsewhere chi tu SlotLiveness`() {
        val actions = code("KachiHomeSlotActions.kt")
        val gone = SourceRoots.body(actions, "override fun onAppGone(index: Int, pkg: String, elsewhere: Boolean)")
        // 2.98 · R18 — ghim TRẢ VỀ nguyên văn 2.93 (R7 đã gỡ).
        assertTrue("if (elsewhere) sayIfStill(index, R.string.kachi_slot_app_elsewhere, pkg)" in gone)
        assertTrue("val elsewhere = sub.liveness.elsewhere" in SourceRoots.body(probe, "private fun sweep()"))
    }
}
