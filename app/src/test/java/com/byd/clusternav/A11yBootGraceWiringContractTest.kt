package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ READY-AT-HOME (02/10) — DÂY NỐI CỦA ÂN HẠN KHỞI ĐỘNG (lớp 2 mở rộng) ═══════════════════════════════════════════
 *
 * Luật thuần ở `A11yBootGraceGatesTest` (`:core`). Bài này khoá phần chỉ Android có: AI gọi luật, ở ĐÂU, theo THỨ TỰ nào —
 * và rằng lượt này KHÔNG có lối vào nào ngoài lúc tiến trình bật (không phải mỗi lần mở Cài đặt / màn bật / nút), nên
 * ngoài ân hạn luật 2.83 "đang chạy thì không tự khởi động lại launcher" giữ nguyên.
 *
 * Lỗi được khoá [ĐO E2E máy ảo 02/10 ca 1]: giết kiểu BYD lúc màn bật ⇒ tiến trình dựng lại `tương tác=true` ⇒ không lớp
 * nào được phép leo ⇒ `nấc NONE, KHÔNG leo` (pha RUNNING) ⇒ phím chết ~70 s.
 *
 * Vùng quét cắt bằng [SourceRoots.body] trên mã đã bỏ chú thích ([SourceRoots.codeOf]) — không `substringAfter/Before`.
 */
class A11yBootGraceWiringContractTest {

    private val heal = SourceRoots.codeOf("src/main/java/com/byd/clusternav/A11yLifecycleHeal.kt")
    private val nav = SourceRoots.codeOf("src/main/java/com/byd/clusternav/NavConnect.kt")
    private val prefs = SourceRoots.codeOf("src/main/java/com/byd/clusternav/PrefsA11yLifecycle.kt")

    private fun order(src: String, vararg tokens: String) {
        val at = tokens.map { t -> src.indexOf(t).also { assertTrue(it >= 0, "thiếu '$t' trong vùng quét") } }
        assertEquals(at.sorted(), at, "thứ tự sai: ${tokens.joinToString(" → ")}")
    }

    /**
     * Mọi tệp mã đã bỏ chú thích bằng bộ quét DÙNG CHUNG [KotlinSource.stripComments] (giữ nguyên string literal). Review
     * lượt 3 [P3]: bản tự chép trước đây cắt mỗi dòng ở `//` đầu tiên ⇒ một chuỗi `"https://…"` nuốt luôn phần mã phía sau
     * trên cùng dòng ⇒ bài đếm lối vào im lặng THÔI gác [ĐO phá thử 02/10: thêm `HealPhase.KHOI_DONG` sau một chuỗi
     * `"https://x"` ở KeyReady.kt — bản cũ vẫn xanh].
     */
    private fun mainFiles(): List<Pair<String, String>> = SourceRoots.moduleSourceRoots().flatMap { root ->
        Files.walk(root).use { s ->
            s.filter { it.toString().endsWith(".kt") }
                .map { it.fileName.toString() to KotlinSource.stripComments(it.toFile().readText()) }.toList()
        }
    }

    // ─── Lối vào DUY NHẤT: lúc tiến trình bật, xếp SAU lớp 1 ──────────────────────────────────────────────────

    @Test
    fun `luot an han khoi dong xep SAU lop 1, bao dang xet truoc khi xep, nha trong finally`() {
        val install = SourceRoots.body(heal, "fun install(ctx: Context) {")
        order(
            install,
            "val interactive = interactive(app)",
            "bootGraceBusy.set(interactive == true)",
            "onProcessStart(app, interactive, startedAt)",
            "submit(\"ân hạn khởi động\") { try { onBootGrace(app, interactive, startedAt) } finally { bootGraceBusy.set(false) } }",
        )
    }

    @Test
    fun `pha KHOI_DONG chi co MOT loi vao - khong phai moi lan mo Cai dat hay man bat`() {
        val all = mainFiles()
        val uses = all.sumOf { (_, src) -> Regex("HealPhase\\.KHOI_DONG").findAll(src).count() }
        val inHeal = Regex("HealPhase\\.KHOI_DONG").findAll(heal).count()
        val inGates = all.filter { it.first == "AccessibilityHealGates.kt" || it.first == "A11yBindJournal.kt" }
            .sumOf { (_, src) -> Regex("HealPhase\\.KHOI_DONG").findAll(src).count() }
        assertEquals(1, inHeal, "app chỉ được nói 'KHOI_DONG' ở đúng một chỗ: onBootGrace")
        assertEquals(uses, inHeal + inGates, "không tệp nào khác (KeyReady, NavConnect, Cài đặt, watchdog) được tự đặt pha có quyền leo")
        assertTrue(SourceRoots.body(heal, "private fun onBootGrace(").contains("healIfStuck(app, HealPhase.KHOI_DONG, screenOnAt = startedAt)"))
        val calls = all.sumOf { (_, src) -> Regex("(?<!fun )onBootGrace\\(app").findAll(src).count() }
        assertEquals(1, calls, "onBootGrace chỉ được gọi từ install (một lượt mỗi tiến trình)")
        assertTrue(SourceRoots.body(heal, "fun install(ctx: Context) {").contains("onBootGrace(app, interactive, startedAt)"))
    }

    @Test
    fun `cong vao doc moc tien trinh TRUOC roi moi ghi, hoi luat thuan, roi an han, roi moi do`() {
        val fn = SourceRoots.body(heal, "private fun onBootGrace(")
        order(
            fn,
            "val prevProcStart = Prefs.a11yProcStartAt(app)",
            "Prefs.setA11yProcStartAt(app, startedAt)",
            "val escalatedAt = Prefs.a11yEscalatedAt(app)",
            "val ownChild = AccessibilityHealGates.ownHealChild(bornFromOwnHeal.get(), escalatedAt, Prefs.a11yScoredFor(app), startedAt)",
            "if (!AccessibilityHealGates.bootGraceMayRun(interactive, prevProcStart, ownChild, startedAt)) {",
            "if (!AccessibilityHealGates.withinBootGrace(startedAt, now)) {",
            "healIfStuck(app, HealPhase.KHOI_DONG, screenOnAt = startedAt)",
        )
        assertEquals(1, Regex("Prefs\\.a11yProcStartAt\\(app\\)").findAll(fn).count(),
            "mốc tiến trình trước chỉ đọc MỘT lần, TRƯỚC khi ghi — đọc sau là luôn thấy chính mình")
        listOf("LocalDeviceShell", "forceStopRebindCommand", "escalateOnLifecycle", "NavConnect.").forEach {
            assertFalse(fn.contains(it), "'$it' trong onBootGrace — đo/leo chỉ qua healIfStuck (DRY, cùng đường lớp 1/2)")
        }
        val setter = SourceRoots.body(prefs, "fun Prefs.setA11yProcStartAt(")
        assertTrue(setter.contains(".commit()") && !setter.contains(".apply()"), "mốc tiến trình ghi đồng bộ (CLAUDE.md §5)")
    }

    // ─── Màn TẮT giữa lượt ⇒ trao LỚP 1 (senior review lượt 1 — khoá lỗi E2E C6) ────────────────────────────────

    /**
     * [ĐO E2E máy ảo 02/10 C6] lượt khởi động thấy KẸT, màn tắt ở +2 s ⇒ `pha đã qua → bỏ` ⇒ không lớp nào nhận ⇒ phím chết
     * 112 s tới khi bấm tay. Bài này đỏ khi: lời gọi trao bị nuốt; trao mà không claim lớp 1 TRƯỚC khi đo (tiến trình con
     * dựng lại lúc màn tắt sẽ mở lượt tắt-máy thứ hai); claim hỏng mà vẫn đo; hoặc nhánh "pha qua" của [healIfStuck] thôi
     * báo CẮT.
     */
    @Test
    fun `man tat giua luot an han thi trao lop 1 sau khi claim`() {
        val boot = SourceRoots.body(heal, "private fun onBootGrace(")
        assertTrue(boot.contains("if (healIfStuck(app, HealPhase.KHOI_DONG, screenOnAt = startedAt)) handOffToTatMay(app, startedAt)"),
            "kết quả CẮT của lượt khởi động phải gác lời gọi trao lớp 1")
        val calls = mainFiles().sumOf { (_, src) -> Regex("(?<!fun )handOffToTatMay\\(app").findAll(src).count() }
        assertEquals(1, calls, "chỉ onBootGrace được trao — một lần mỗi tiến trình")
        val fn = SourceRoots.body(heal, "private fun handOffToTatMay(")
        order(
            fn,
            "if (!AccessibilityHealGates.bootGraceHandsOffToTatMay(interactive(app), startedAt, Prefs.a11yTatMayAt(app), Prefs.a11yMoXeAt(app), now)) {",
            "if (!Prefs.setA11yTatMayAt(app, now)) {",
            "healIfStuck(app, HealPhase.TAT_MAY, screenOnAt = -1L)",
        )
        listOf("LocalDeviceShell", "forceStopRebindCommand", "escalateOnLifecycle", "NavConnect.").forEach {
            assertFalse(fn.contains(it), "'$it' trong lượt trao — đo/leo chỉ qua healIfStuck (DRY, cùng đường lớp 1)")
        }
        val h = SourceRoots.body(heal, "private fun healIfStuck(")
        val wait = h.indexOf("if (!waitInPhase(app, phase, screenOnAt, AccessibilityHealGates.STUCK_CONFIRM_GAP_MS)) {")
        assertTrue(wait >= 0)
        assertEquals(h.indexOf("return true", wait), h.indexOf("return", wait), "nhánh chờ bị pha cắt phải báo CẮT (true)")
        assertTrue(h.contains("return r != NavConnect.GrantResult.RESTARTING && !stillInPhase(app, phase, screenOnAt)"),
            "cổng cuối của đường leo nói pha qua ⇒ cũng là CẮT")
    }

    // ─── Kiểm phím + ô chờ lượt khởi động như chờ lớp 2 ──────────────────────────────────────────────────────

    @Test
    fun `chuoi kiem phim va o cho luot khoi dong ket luan`() {
        val pending = SourceRoots.body(heal, "internal fun moXePending(app: Context): Boolean")
        assertTrue(pending.contains("bootGraceBusy.get()"),
            "KeyReady/giữ ô phải CHỜ lượt khởi động: cấp quyền chen giữa lúc nó đo là ghi chồng; gắn ô trước lúc nó giết là app ô mở hai lần")
        assertEquals(2, Regex("bootGraceBusy\\.set\\(").findAll(heal).count(), "cờ chỉ đặt ở install và nhả ở finally — không nơi nào khác")
    }

    /**
     * Review lượt 2 [P3]: trước lượt khởi động, "tiến trình bật lúc màn sáng ⇒ không có lượt nào có thể force-stop" nên chỉ
     * [ShellChannelGate.adopt] giữ ô. Nay lượt khởi động chạy ĐÚNG ở tiến trình đó ⇒ khi lượt sớm chậm hơn tiêu điểm + 1,5 s,
     * F4 thắng đua nhận kênh và ô mở app trong lúc lượt khởi động còn đo ⇒ app ô mở hai lần sau force-stop. Mọi lối chạy
     * `onChannelUp` của màn chính phải giữ ô trước (cùng hàm, fail-open ≤ 8 s).
     */
    @Test
    fun `moi loi nhan kenh cua man chinh deu giu o truoc onChannelUp - ca F4`() {
        val gate = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ShellChannelGate.kt")
        val up = SourceRoots.body(SourceRoots.body(gate, "private fun settle("), "is FirstOpenStep.ChannelUp -> {")
        assertTrue(up.contains("submitBg { KeyReady.holdTileIfEscalating(activity.applicationContext); onChannelUp() }"),
            "F4 nhận kênh phải giữ ô như adopt")
        assertTrue(SourceRoots.body(gate, "fun adopt() {").contains("submitBg { KeyReady.holdTileIfEscalating(app); onChannelUp() }"))
        assertEquals(2, Regex("onChannelUp\\(\\)").findAll(gate).count(), "chỉ hai lối chạy onChannelUp (adopt + F4), cả hai đã giữ ô")
    }

    // ─── Leo bằng ĐÚNG đường sẵn có; marker trước khi bắn ────────────────────────────────────────────────────

    @Test
    fun `moc leo ghi hong thi luot tu dong KHONG ban`() {
        val esc = SourceRoots.body(nav, "private fun escalateIfStuck(")
        order(
            esc,
            "!fireGate()",
            "val marked = Prefs.setA11yEscalatedAt(app, now)",
            "if (!AccessibilityHealGates.autoFireAllowed(userAsked, marked)) {",
            "sh(cmd)",
        )
        val guard = esc.indexOf("if (!AccessibilityHealGates.autoFireAllowed(userAsked, marked)) {")
        val ret = esc.indexOf("return GrantResult.NOT_BOUND", guard)
        assertTrue(ret in guard until esc.indexOf("sh(cmd)"), "kết quả cổng phải GÁC lệnh bắn, không chỉ log")
    }

    @Test
    fun `ngoai an han luat 2_83 dang chay giu nguyen - duong grant van la RUNNING`() {
        assertTrue(
            nav.contains("phase: AccessibilityHealGates.HealPhase = AccessibilityHealGates.HealPhase.RUNNING,"),
            "đường grant (watchdog, alarm, B1, KeyReady, nút) mặc định RUNNING — không được thừa hưởng quyền tự leo",
        )
        assertTrue(nav.contains("if (forceRebindIfNeeded(keyPair, sh)) GrantResult.BOUND else escalateIfStuck(app, sh, userAsked)"),
            "đường grant gọi escalateIfStuck KHÔNG truyền pha ⇒ RUNNING ⇒ nấc NONE khi kẹt")
        val keyReady = SourceRoots.codeOf("src/main/java/com/byd/clusternav/KeyReady.kt")
        assertFalse(keyReady.contains("HealPhase"), "chuỗi kiểm phím không được tự chọn pha")
        assertFalse(keyReady.contains("escalateOnLifecycle"), "chuỗi kiểm phím không được mở cửa leo")
        val lifecycle = SourceRoots.body(nav, "internal fun escalateOnLifecycle(")
        assertTrue(lifecycle.contains("if (phase == AccessibilityHealGates.HealPhase.RUNNING) return GrantResult.NOT_BOUND"))
        assertTrue(SourceRoots.body(heal, "private fun stillInPhase(").contains("AccessibilityHealGates.lifecycleFireAllowed(phase, interactive(app), screenOnAt,"),
            "cổng cuối hỏi lại ân hạn ngay trước khi bắn (đo xong mà quá 20 s ⇒ không giết muộn)")
    }

    // ─── Dòng keys= / màn Chẩn đoán nói kết quả thật ─────────────────────────────────────────────────────────

    @Test
    fun `dong keys noi ket qua that cua luot vong doi va cua cham diem`() {
        val fn = SourceRoots.body(heal, "private fun healIfStuck(")
        order(
            fn,
            "AccessibilityHealGates.stuckPersistent(first, second)",
            "KachiReadyLog.keys(\"STUCK(\$note)->ESCALATE\")",
            "NavConnect.escalateOnLifecycle(app, phase)",
            "KachiReadyLog.keys(\"STUCK(\$note)->\$r\")",
        )
        val score = SourceRoots.body(heal, "private fun scoreAfterHeal(")
        order(score, "NavConnect.boundPerAccessibilityManager(app)", "KachiReadyLog.keys(", "A11yBindJournal.afterHealNote(bound)")
    }

    /**
     * [ĐO máy ảo 02/10 01:45:25.824] lượt cấp của KeyReady thua single-flight ⇒ `keys=GRANT->NOT_BOUND` trong khi phím
     * gắn 1,7 s sau. Nhãn NOT_BOUND phải được XÁC NHẬN bằng binder (chỉ đọc) trước khi được coi là sự thật cuối.
     */
    @Test
    fun `KeyReady xac nhan NOT_BOUND bang binder, chi doc`() {
        val keyReady = SourceRoots.codeOf("src/main/java/com/byd/clusternav/KeyReady.kt")
        val prepare = SourceRoots.body(keyReady, "fun prepare(app: Context): String {")
        assertTrue(prepare.contains("{ r -> KachiReadyLog.keys(\"GRANT->\$r\$suffix\"); confirmLateBind(app, r, suffix) }"),
            "kết quả lượt cấp ghi NGAY, rồi mới xác nhận gắn muộn")
        val fn = SourceRoots.body(keyReady, "private fun confirmLateBind(")
        order(fn, "if (r != NavConnect.GrantResult.NOT_BOUND) return", "KeyReadyPlan.LATE_BIND_WATCH_MS",
            "NavConnect.boundPerAccessibilityManager(app) == true", "KachiReadyLog.keys(\"GRANT->BOUND(late)\$suffix\")")
        listOf("grantAccessibility", "LocalDeviceShell", "escalate", "Prefs.set").forEach {
            assertFalse(fn.contains(it), "'$it' trong lượt xác nhận — CHỈ ĐỌC binder, không có đường chữa thứ hai")
        }
        assertTrue(fn.contains("isDaemon = true"), "luồng chờ không được giữ tiến trình")
    }

    @Test
    fun `mot nguon so cho cua so sinh tu luot chua`() {
        assertTrue(heal.contains("private const val AFTER_HEAL_WINDOW_MS = AccessibilityHealGates.OWN_HEAL_WINDOW_MS"),
            "cửa sổ chấm điểm và cửa sổ chặn ân hạn khởi động là MỘT khái niệm — không chép hằng (§4.1 DRY)")
    }
}
