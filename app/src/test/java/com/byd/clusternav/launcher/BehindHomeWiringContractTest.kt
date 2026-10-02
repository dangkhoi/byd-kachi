package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * V1 (phần nhóm A, spec shortcuts-autostart §5.4) — bài canh TĨNH cho mảnh BEHIND-HOME + đặt tạm. Đọc thân hàm bằng
 * [SourceRoots.body] (đếm ngoặc, nổ nếu mốc không có), mã đã bỏ chú thích ([SourceRoots.codeOf]).
 *
 * Mỗi bài khoá một điều đã ĐO hoặc owner chốt:
 *  - O2-sai che HOME [ĐO 06 bước 6] ⇒ `move-task` chỉ ở MỘT chỗ, sau lượt đọc lại kiểm "A không ở đỉnh";
 *  - Kachi bị giết khi app đang sau nhà ⇒ app nổi lên che HOME [ĐO impl-probe/e6] ⇒ dấu bền TRƯỚC lệnh + lượt trả lại
 *    ở đầu chuỗi SẴN;
 *  - đính chính owner 01/10: đặt vào ô lúc chạy là TẠM ⇒ không ghi bền, không giết app cũ.
 */
class BehindHomeWiringContractTest {

    private val seq by lazy { SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/behind/BehindHomeSequence.kt") }
    private val vm by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt") }
    private val host by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/VdAppHost.kt") }
    private val slots by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeSlots.kt") }
    private val render by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeRender.kt") }
    private val early by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/EarlyShellChannel.kt") }
    private val anchor by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/behind/BehindAnchorActivity.kt") }
    private val recovery by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/behind/BehindHomeRecovery.kt") }

    private fun order(src: String, vararg marks: String) {
        var at = -1
        for (m in marks) {
            val i = src.indexOf(m, at + 1)
            assertTrue(i > at, "thứ tự sai/thiếu: '$m' phải đứng sau mốc trước trong:\n$src")
            at = i
        }
    }

    /** Mã đã bỏ chú thích — cùng luật [SourceRoots.codeOf], cho tệp tìm được bằng quét cây. */
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `move-task chi duoc dung o MOT cho, va chi chay sau doc lai + dau ben`() {
        // Phạm vi = tính năng LAUNCHER (`…/launcher/…` của cả hai module). Đường chiếu-cụm (`modules/clustercast`) có
        // move-task riêng đã chạy ngoài hiện trường từ trước — không thuộc mảnh này (CLAUDE.md §6).
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") && "/launcher/" in it.toString() }.toList() }
        }.map { it.fileName.toString() to code(it.toFile().readText()) }
        assertTrue(all.size > 100, "quét được quá ít tệp launcher (${all.size}) — đường dẫn sai thì bài này là test giả")
        val builders = all.filter { "stack move-task" in it.second }.map { it.first }.distinct()
        assertEquals(listOf("BehindHomePlan.kt"), builders, "chuỗi `am stack move-task` chỉ được dựng ở BehindHomePlan")
        val callers = all.filter { "BehindHomePlan.moveTaskCmd(" in it.second }.map { it.first }.distinct()
        assertEquals(listOf("BehindHomeSequence.kt"), callers, "chỉ chuỗi thi hành được bắn move-task")
        val fn = SourceRoots.body(seq, "private fun moveBehind(")
        order(fn, "anchor.start()", "BehindHomePlan.pickAnchor(", "BehindHomePlan.checkEvict(", "anchor.markBehind(",
            "BehindHomePlan.moveTaskCmd(", "BehindHomePlan.verifyMoved(", "finish()")
    }

    @Test
    fun `dat tam KHONG ghi ben va dung lop tam`() {
        val fn = SourceRoots.body(vm, "fun placeTemporary(")
        listOf("persist(", "mutate", "repository.").forEach { assertFalse(fn.contains(it), "placeTemporary không được '$it'") }
        assertTrue(fn.contains("overlay.place("), "phải ghi vào lớp tạm")
        val revert = SourceRoots.body(vm, "fun revertTemporary(")
        listOf("persist(", "mutate", "repository.").forEach { assertFalse(revert.contains(it), "revertTemporary không được '$it'") }
        val glue = SourceRoots.body(slots, "fun placeTemporary(")
        assertTrue(glue.contains("viewModel.placeTemporary(") && !glue.contains("viewModel.assignApp("),
            "lối đặt tạm của màn chính đi lớp tạm, không đi đường LƯU")
    }

    @Test
    fun `doi app tai cho KHONG force-stop app cu, mo app moi bang dung than golden roi moi bao day`() {
        val fn = SourceRoots.body(host, "fun swapApp(")
        assertFalse(fn.contains("force-stop"), "đặt tạm không được giết app cũ (owner: app cũ ra sau, không đè home)")
        order(fn, "SlotLiveProbe.unwatch(probeKey)", "launchInto(displayId, newPkg, sh)", "onLaunched(displayId, old, newPkg)")
        val launch = SourceRoots.body(host, "private fun maybeLaunch()")
        assertTrue(launch.contains("launchInto(displayId, p, sh)"), "ô mới và đặt tạm dùng CHUNG một thân mở (DRY, byte golden)")
    }

    @Test
    fun `man chinh ve bo cuc DANG HIEN va chi doi tai cho o co moc moi`() {
        val fn = SourceRoots.body(render, "internal fun KachiHomeActivity.render(state: HomeUiState) {")
        assertTrue(fn.contains("workspace.render(state.effectiveWorkspace, state.carStatus, WorkspaceRenderPlanner.swapCandidates(prev?.swapNonce, state.swapNonce))"))
        assertTrue(fn.contains("windows.reconcileLocations(state.effectiveWorkspace.slots)"))
    }

    @Test
    fun `luot tra lai chay o dau chuoi SAN, truoc kiem phim, dung mot lan`() {
        val chain = SourceRoots.body(early, "private fun readyChain(")
        order(chain, "interactive(app) != true", "BehindHomeRecovery.onReady(app)", "KeyReady.prepare(app)")
        assertEquals(1, Regex(Regex.escape("BehindHomeRecovery.onReady(")).findAll(early).count())
        val ready = SourceRoots.body(recovery, "fun onReady(app: Context) {")
        // Một lượt mỗi TIẾN TRÌNH, chốt đặt TRƯỚC khi đọc dấu (chuỗi SẴN chạy lại mỗi lần màn bật — cùng tiến trình mà app
        // có dấu ở trước màn nhà là người lái tự mở nó, không phải Kachi chết).
        order(ready, "ranThisProcess.compareAndSet(false, true)) return", ".read().isEmpty()) return", "BehindHomeRunner.execute(", "run(app)")
        assertTrue(ready.contains("if (!measured) ranThisProcess.set(false)"), "không đọc được ⇒ lượt sau của cùng tiến trình đo lại")
        val fn = SourceRoots.body(recovery, "private fun run(app: Context): Boolean {")
        order(fn, "if (marks.isEmpty()) return", "LocalDeviceShell.run(", "BehindMarks.surfaced(", "AccessibilityRebind.GO_HOME_UNLESS_CAMERA")
    }

    /**
     * Lỗi E2E (5) [ĐO máy ảo 02/10 `c5a-trip-generic`]: màn nhà có thể là `…KachiHomeActivity` trong stack `standard`. Mọi
     * phép "màn nhà Kachi có ở trước không" của BEHIND-HOME + chuyến lấy CÙNG một nguồn hai dạng (`DefaultHome.shownComponents`),
     * không ghép tay, không chỉ alias.
     */
    @Test
    fun `nhan man nha Kachi ca hai dang - mot nguon DefaultHome shownComponents`() {
        val home = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/DefaultHome.kt")
        assertTrue(
            home.contains("fun shownComponents(ctx: Context): List<String> = listOf(component(ctx), launchComponent(ctx))"),
            "hai dạng: alias HOME + activity thật (task dựng bằng `am start -n`)",
        )
        val runner = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/behind/BehindHomeRunner.kt")
        assertTrue(runner.contains("homeComps = DefaultHome.shownComponents(app)"), "runner phải truyền cả hai dạng màn nhà")
        val trip = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/trip/TripStart.kt")
        assertTrue(trip.contains("DefaultHome.shownComponents(app)") && !trip.contains("DefaultHome.component(app)"), "chuyến không được chỉ nhận alias")
        listOf("BehindHomePlan.homeOnTop(before, homeComps)", "BehindHomePlan.homeOnTop(r2, homeComps)").forEach {
            assertTrue(seq.contains(it), "chuỗi đọc 'HOME ở đỉnh' bằng cả hai dạng: $it")
        }
    }

    /**
     * R1.8 (T-M6 [ĐO máy ảo 02/10]: K8 đưa task từ display 0 ẩn về ô, pid giữ, 0 tiêu điểm display 0 — 5/5 app). Móc vào
     * ĐÚNG thân mở app của ô, NGAY TRƯỚC `am force-stop`; không dấu ⇒ 0 lệnh shell (một lần đọc prefs) ⇒ chuỗi golden giữ byte.
     */
    @Test
    fun `R1-8 mo o - app Kachi da day ra sau man nha ve bang K8 truoc force-stop, khong dau thi 0 lenh`() {
        val launch = SourceRoots.body(host, "private fun launchInto(")
        order(launch, "if (released) return", "SlotReturnRun.bringBackMarked(context, displayId, p, sh)", "inputClient?.ensureStarted()",
            "SlotLiveProbe.watch(", "return", "sh(\"am force-stop \$p\")")
        // Phá thử M7 (lượt 1 LỌT): chèn thêm một force-stop TRƯỚC móc vẫn qua được bài thứ tự ⇒ khoá cả SỐ lệnh: thân mở app
        // có đúng MỘT `am force-stop`, và nó đứng SAU móc R1.8.
        assertEquals(1, Regex("am force-stop").findAll(launch).count(), "đúng một lệnh dừng app trong thân mở ô: $launch")
        assertTrue(launch.indexOf("am force-stop") > launch.indexOf("SlotReturnRun.bringBackMarked("), "force-stop chỉ SAU móc R1.8")
        val run = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SlotReturnRun.kt")
        val marked = SourceRoots.body(run, "fun bringBackMarked(ctx: Context, vd: Int, pkg: String, sh: (String) -> String): Boolean {")
        order(marked, "store.read()", "if (pkg !in marks.values) return false", "seq(sh).bringBackMarked(", "SlotReturn.Back.IN_SLOT) return false", "store.remove(")
        // Review lượt 6 [P2]: K8 (R1.8 + về ô từ toàn màn) chỉ khi màn nhà Kachi ở đỉnh display 0 — cổng ở `:core`
        // (`SlotReturnTest`), ở đây khoá rằng hai đường thi hành trao cho nó ĐÚNG các dạng màn nhà (không danh sách rỗng ⇒
        // cổng không bao giờ mở, cũng không danh sách khác ⇒ đọc nhầm đỉnh).
        assertTrue(marked.contains("seq(sh).bringBackMarked(vd, pkg, marks, DefaultHome.shownComponents(ctx))"), marked)
        val back = SourceRoots.body(run, "fun bringBack(host: View, vd: Int, sh: (String) -> String, taskId: Int, done: (SlotReturn.Back) -> Unit) {")
        order(back, "val homes = DefaultHome.shownComponents(host.context)", "BehindHomeRunner.execute(", "seq(sh).bringBack(vd, taskId, homes)")
    }

    /**
     * F1 dòng 9 (T-M2 [ĐO]): tách app ra toàn màn thôi đo ô TRƯỚC lệnh (rời ô theo ý người dùng ≠ "app đã đóng"); về ô ở
     * mỗi lần màn nhà hiện lại; K8 không ăn ⇒ đường golden (`reopen()` — force-stop + mở lại), app đã đóng ⇒ thẻ "đã đóng".
     */
    @Test
    fun `o toan man - thoi do truoc K7, ve o khi man nha hien, khong ve duoc thi golden`() {
        val detach = SourceRoots.body(host, "fun detachToFull(sig: String?, homeComps: List<String>, done: (Boolean) -> Unit): Boolean {")
        order(detach, "if (released || !launched) return false", "full.detach(")
        assertTrue(host.contains("SlotFullscreen(this, surface, probeKey, { p -> !released && pkg == p }, ::onAppClosed, ::reopen)"),
            "trạng thái toàn màn nối đúng thẻ 'đã đóng' + đường golden của CHÍNH host")
        assertTrue(SourceRoots.body(host, "fun returnFromFull() {").contains("if (!released) full.bringBack(id, p, sh)"))
        val run = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SlotReturnRun.kt")
        val full = SourceRoots.body(run, "fun detach(vd: Int, pkg: String, sh: (String) -> String, sig: String?, homeComps: List<String>, done: (Boolean) -> Unit) {")
        order(full, "SlotLiveProbe.unwatch(probeKey)", "SlotReturnRun.detach(", "if (task != null) { done(true); return@detach }", "SlotLiveProbe.watch(")
        // Review lượt 4 [P2]: K7 đưa app rời ô rồi nó ẩn (HOME/camera trước lần đọc) ⇒ chuỗi đã về ô bằng K8; bên host chỉ đo
        // lại ô khi app THẬT ở ô (`null`/IN_SLOT), app đóng ⇒ thẻ "đã đóng", K8 không ăn ⇒ golden — không bao giờ ô đen câm.
        order(full, "when (out.back) {", "null, SlotReturn.Back.IN_SLOT -> SlotLiveProbe.watch(", "SlotReturn.Back.GONE -> onClosed()", "else -> reopen()")
        // Đổi app tại chỗ (đặt tạm) ⇒ trạng thái toàn màn của app CŨ bị bỏ TRƯỚC khi mở app mới (thẻ cũ không phủ app mới,
        // K8 không kéo app cũ đè lên, nhả ô vẫn dừng app mới).
        order(SourceRoots.body(host, "fun swapApp("), "SlotLiveProbe.unwatch(probeKey)", "full.reset()", "launchInto(displayId, newPkg, sh)")
        order(SourceRoots.body(run, "fun reset() {"), "task = null", "host.removeView(")
        // Nhả ô khi app của nó đang toàn màn (người dùng đang thấy trên display 0) ⇒ KHÔNG force-stop app đó.
        assertTrue(SourceRoots.body(host, "fun release()").contains("if (wasLaunched && p != null && sh != null && !full.isDetached)"))
        order(SourceRoots.body(run, "fun bringBack(vd: Int, pkg: String, sh: (String) -> String) {"),
            "SlotReturnRun.bringBack(", "SlotReturn.Back.KEEP) return@bringBack", "SlotReturn.Back.IN_SLOT -> SlotLiveProbe.watch(",
            "SlotReturn.Back.GONE -> onClosed()", "else -> reopen()")
        val act = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt")
        assertTrue(SourceRoots.body(act, "override fun onStart() {").contains("workspace.returnDetached()"))
        // Chuỗi K7 tách ô chỉ dựng ở :core (SlotReturn), host + lớp keo không tự viết lệnh display 0.
        listOf(host, slots).forEach { assertFalse(it.contains("--display 0"), "lệnh display 0 phải đi qua SlotReturn (rào camera)") }
    }

    @Test
    fun `giu cho chi tu tat tinh nang khi chinh tien trinh nay mo no`() {
        val fn = SourceRoots.body(anchor, "override fun onCreate(")
        order(fn, "Process.myPid()", "BehindHomeRunner.disable(", "finishAndRemoveTask()")
    }
}
