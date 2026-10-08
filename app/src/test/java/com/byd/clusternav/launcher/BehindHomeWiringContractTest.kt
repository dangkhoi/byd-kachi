package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
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

    /** Mã đã bỏ chú thích — ĐÚNG bộ quét của [SourceRoots.codeOf] ([KotlinSource.stripComments]), cho tệp tìm được bằng quét cây. */
    private fun code(text: String) = KotlinSource.stripComments(text)

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
        // Review 2.89 Pass 3 · whole-r2-2 — ĐỔI GHIM có lý do: + cờ đổi hồ sơ (lượt dựng lại do đổi hồ sơ nhả app rời ô như 2.88).
        assertTrue(fn.contains("workspace.render(state.effectiveWorkspace, state.carStatus, WorkspaceRenderPlanner.swapCandidates(prev?.swapNonce, state.swapNonce),\n        profileSwitch = prev != null && prev.activeProfile != state.activeProfile)"), fn)
        assertTrue(fn.contains("windows.reconcileLocations(state.effectiveWorkspace.slots)"))
    }

    @Test
    fun `luot tra lai chay o dau chuoi SAN, truoc kiem phim, dung mot lan`() {
        val chain = SourceRoots.body(early, "private fun readyChain(")
        order(chain, "WakeEpochPolicy.shouldRun(prev, epoch, ShellReadiness.isUp(), interactive(app))", "BehindHomeRecovery.onReady(app)", "KeyReady.prepare(app)")
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
     * 2.93 · BEHIND-FELL-UNREAD-K12 (spec `docs/specs/kachi-293-slot.html` R4) — bằng chứng "lượt chờ đã THẤY X trước màn nhà" chỉ
     * được dùng thay bản đọc lại hỏng ở chuỗi màn ảo ẨN (giữa hai lượt chỉ có gỡ che). Chuỗi qua ô sống (`startBehind`) còn K3 +
     * cả lượt đẩy chen giữa ⇒ bản đọc lúc chờ đã cũ ⇒ không truyền (giữ `UNREAD`, 0 K12). Hành vi khoá ở `BehindHomeHiddenStageTest`.
     */
    @Test
    fun `bang chung luot cho chi dung o chuoi man ao an`() {
        assertTrue(SourceRoots.body(seq, "fun startBehindHidden(").contains("afterStage(tag, x, waited, out, homeWasTop, fellSeen = w.seen)"))
        val visible = SourceRoots.body(seq, "fun startBehind(x: String")
        assertTrue(visible.contains("afterStage(tag, x, waited, evict(stage.vd, x, stage.pkg), homeWasTop)") && !visible.contains("fellSeen"),
            "chuỗi qua ô sống không được dùng bản đọc lúc chờ")
        val wait = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/behind/StageWait.kt")
        assertTrue(wait.contains("return StageWaited(waited, fell = true, seen = r)"), "bằng chứng = chính bản đọc thấy X rơi")
    }

    /**
     * 2.93 · BEHIND-MARKS-BOOT (spec `docs/specs/kachi-293-slot.html` R5) — MỘT kho cho mọi bên đọc/ghi dấu, và kho đọc theo
     * lần khởi động: dấu của đời máy trước không tới được lượt trả lại (K12) hay K8 về ô. Khoá = khoá của sổ chuyến
     * (`TripStart.bootKey`, không phép đọc `BOOT_COUNT` thứ hai). Luật thuần ở `BehindMarksBootTest`.
     */
    @Test
    fun `kho dau doc ghi theo lan khoi dong may, mot khoa voi so chuyen`() {
        val store = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/behind/BehindMarksStore.kt")
        assertTrue(store.contains("private val boot: String by lazy { TripStart.bootKey(app) }"))
        assertTrue(SourceRoots.body(store, "fun read()").contains("BehindMarks.forBoot(prefs.getString(KEY, null), boot)"))
        assertTrue(SourceRoots.body(store, "fun write(").contains("BehindMarks.encode(marks, boot)"))
        assertFalse(store.contains("BehindMarks.decode("), "kho không được đọc dấu bỏ qua lần khởi động")
        // Mọi chỗ đọc/ghi dấu ở :app đi qua kho (không ai đọc khoá prefs trực tiếp).
        val all = SourceRoots.moduleSourceRoots().filter { "app" in it.toString() }.flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.map { it.fileName.toString() to code(it.toFile().readText()) }
        assertTrue(all.size > 100, "quét được quá ít tệp :app (${all.size}) — đường dẫn sai thì bài này là test giả")
        val direct = all.filter { (name, src) -> name != "BehindMarksStore.kt" && src.contains("BehindMarks.decode(") }.map { it.first }
        assertEquals(emptyList<String>(), direct, "đọc dấu thẳng bằng decode ⇒ lách luật lần khởi động")
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
        order(full, "when (out.back) {", "null, SlotReturn.Back.IN_SLOT -> SlotLiveProbe.watch(", "SlotReturn.Back.GONE -> onClosed(false)", "else -> reopen()")
        // Đổi app tại chỗ (đặt tạm) ⇒ trạng thái toàn màn của app CŨ bị bỏ TRƯỚC khi mở app mới (thẻ cũ không phủ app mới,
        // K8 không kéo app cũ đè lên, nhả ô vẫn dừng app mới).
        order(SourceRoots.body(host, "fun swapApp("), "SlotLiveProbe.unwatch(probeKey)", "full.reset()", "launchInto(displayId, newPkg, sh)")
        order(SourceRoots.body(run, "fun reset() {"), "task = null", "host.removeView(")
        // Nhả ô khi app của nó đang toàn màn (người dùng đang thấy trên display 0) ⇒ KHÔNG force-stop app đó (2.97 · R5: không
        // force-stop app nào khi nhả ô).
        assertFalse(SourceRoots.body(host, "fun release()").contains("force-stop"))
        order(SourceRoots.body(run, "fun bringBack(vd: Int, pkg: String, sh: (String) -> String) {"),
            "SlotReturnRun.bringBack(", "SlotReturn.Back.KEEP) return@bringBack", "SlotReturn.Back.IN_SLOT -> SlotLiveProbe.watch(",
            "SlotReturn.Back.GONE -> onClosed(false)", "else -> reopen()")
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
