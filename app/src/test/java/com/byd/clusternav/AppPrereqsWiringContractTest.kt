package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.89 · B2 VM-PREREQ-TRUTH — khoá ĐƯỜNG NỐI của điều kiện nền theo sự thật (`:core` `AppPrereqPlan` + `:app`
 * [AppPrereqs]) và của luật chờ bóng VietMap (`:core` `VietMapBubbleWait`). `:app` không có Robolectric ⇒ đọc SOURCE đã
 * bỏ chú thích ([SourceRoots.codeOf]) — nhắc tên trong chú thích không thoả được bài nào.
 *
 * Lỗi xe khoá ở đây (05/10): (1) hộp "Hệ thống IVI không hỗ trợ hoạt động này" mỗi lần VietMap khởi động sau khi cài lại
 * — gốc [ĐO nguồn ROM + smali] là lời xin miễn pin của VietMap, còn Kachi chỉ thêm miễn pin MỘT lần sau cờ
 * `doze_whitelist_applied` (đặt cả khi lệnh hỏng) ⇒ không bao giờ thêm lại; (2) bóng không lên khi mạng chậm — 2.88 hết
 * 25 s vẫn hạ VietMap xuống nền lúc còn ở màn chờ.
 */
class AppPrereqsWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val prereqs by lazy { code("src/main/java/com/byd/clusternav/AppPrereqs.kt") }
    private val early by lazy { code("src/main/java/com/byd/clusternav/EarlyShellChannel.kt") }
    private val autostart by lazy { code("src/main/java/com/byd/clusternav/VietMapAutostart.kt") }
    private val runtime by lazy { code("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt") }
    private val diag by lazy { code("src/main/java/com/byd/clusternav/modules/clustercast/DiagActivity.kt") }
    private val clusterDiag by lazy { code("src/main/java/com/byd/clusternav/modules/clustercast/ClusterDiag.kt") }
    private val ops by lazy { code("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinatorOps.kt") }

    private fun order(src: String, vararg tokens: String) {
        var at = -1
        tokens.forEachIndexed { k, t ->
            val i = src.indexOf(t, at + 1)
            assertTrue(i > at, "thiếu hoặc sai thứ tự: `$t` phải đứng sau `${tokens.getOrNull(k - 1)}`\n$src")
            at = i
        }
    }

    /** Toàn bộ mã sản phẩm (đã bỏ chú thích) của `:app` + `:core` + `:car-integration`. */
    private val allMain: List<Pair<String, String>> by lazy {
        SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s ->
                s.filter { it.toString().endsWith(".kt") || it.toString().endsWith(".java") }.toList()
            }.map { p ->
                val rel = root.relativize(p).toString()
                rel to KotlinSource.stripComments(p.toFile().readText())
            }
        }
    }

    // ── Ba lối vào, MỘT hàm quyết ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `chuoi SAN goi AppPrereqs sau cong man sang va truoc chuyen len xe`() {
        order(SourceRoots.body(early, "private fun readyChain("), "WakeEpochPolicy.shouldRun(prev, epoch, ShellReadiness.isUp(), interactive(app))", "AppPrereqs.onReady(app)", "TripStart.onReady(app)")
        assertEquals(1, Regex(Regex.escape("AppPrereqs.onReady(")).findAll(early).count())
    }

    @Test
    fun `VietMapAutostart chua dieu kien nen TRUOC cong som va truoc chong-loop`() {
        val fn = SourceRoots.body(autostart, "fun runNow(ctx: Context, returnToSelfPkg: String?)")
        order(
            fn,
            "getLaunchIntentForPackage(PKG)",
            "AppPrereqs.ensure(app, PKG, AppPrereqPlan.Role.AUTOSTART_PASS)",
            "if (!castDefault && !silentReason) return",
            "tryBeginRun()",
            "LocalDeviceShell.sessionResult(",
        )
    }

    @Test
    fun `luot mo chieu cum di qua CHINH shell cua coordinator, khong con co mot-lan`() {
        assertTrue(
            runtime.contains("appPrereqs = { sh -> com.byd.clusternav.AppPrereqs.ensureForCastOpen(app, prefs, sh) }"),
            "SimpleCastRuntime phải nối móc mở chiếu vào AppPrereqs",
        )
        val body = SourceRoots.body(ops, "internal fun SimpleCastCoordinator.openProjectionBody()")
        assertTrue(body.contains("appPrereqs(shell)"))
        assertFalse(body.contains("deviceidle"), "không còn lệnh `+vn.vietmap.live` ghi cứng trong lượt mở chiếu")
        // Review 2.89 Pass 3 · vietmap-dock-r2-6 — ĐỔI GHIM có lý do: điều kiện nền chạy SAU khi `projection.open` thành công (30/16/35
        // không còn chờ nó; trùng với lúc màn ảo cụm đang được dựng), và KHÔNG chờ khoá (lượt nền đang đọc CÙNG sự thật).
        val opened = body.indexOf("val ok = projection.open(")
        assertTrue(opened in 0 until body.indexOf("appPrereqs(shell)"), "điều kiện nền SAU lượt mở projection: $body")
        assertTrue(body.indexOf("if (!ok) {") < body.indexOf("appPrereqs(shell)"), "chỉ khi mở thành công")
        val castOpen = SourceRoots.body(prereqs, "fun ensureForCastOpen(")
        assertTrue(castOpen.contains("lock.tryLock()"), castOpen)
        assertFalse(castOpen.contains("TimeUnit") || castOpen.contains("lock.withLock"), "không chờ khoá")
    }

    @Test
    fun `AppPrereqs dung MOT ham quyet cua core cho ca ba loi vao, phien nen qua cong thi hanh`() {
        assertEquals(2, Regex(Regex.escape("AppPrereqPlan.ensureAll(")).findAll(prereqs).count(), "phạm vi + mở chiếu")
        assertTrue(prereqs.contains("LocalDeviceShell.sessionResult(keys, LocalShellRetry.BACKGROUND_READ_CAP)"))
        assertTrue(prereqs.contains("CastAutoStartPkgs.of(castPrefs)"), "app tự chiếu của hồ sơ vào phạm vi")
        assertTrue(prereqs.contains("listOf(VietMapAutostart.PKG to Role.CAST_OPEN)"), "giữ phạm vi đường mở chiếu cũ")
        assertFalse(prereqs.contains("Prefs.set"), "AppPrereqs không ghi cờ nào")
        // Không `if (gói == …)` — gói chỉ vào lệnh qua bảng phạm vi (`AppPrereqPlan.targets`).
        assertFalse(Regex("""==\s*"[a-z]+\.[a-z.]+"""").containsMatchIn(prereqs), "không so tên gói cứng")
    }

    // ── Không quyết bằng cờ (CLAUDE.md §5) ─────────────────────────────────────────────────────────────────

    @Test
    fun `khong con cho nao doc hoac ghi hai co mot-lan`() {
        val hits = allMain.filter { (_, src) ->
            listOf("dozeWhitelistApplied", "DozeWhitelistApplied", "vmFloatWhitelistApplied", "VmFloatWhitelistApplied")
                .any { src.contains(it) }
        }.map { it.first }
        assertEquals(emptyList<String>(), hits)
        // Chuỗi khoá cũ chỉ còn ở bảng xếp loại phạm vi hồ sơ (khoá đời cũ còn nằm trên máy).
        val keyHits = allMain.filter { (_, src) ->
            src.contains("\"doze_whitelist_applied\"") || src.contains("\"vm_float_whitelist_applied\"")
        }.map { it.first.substringAfterLast('/') }.toSet()
        assertEquals(setOf("ProfileScope.kt", "ProfileScopeCluster.kt"), keyHits)
    }

    @Test
    fun `man Chan doan in SU THAT, khong in co`() {
        assertFalse(diag.contains("dozeWhitelist="), "dòng in CỜ đã bỏ")
        val fn = SourceRoots.body(diag, "private fun refreshPrereqs()")
        order(fn, "Thread(", "AppPrereqs.diagnose(applicationContext)", "VietMapAutostart.lastBubbleWait", "runOnUiThread")
        assertTrue(SourceRoots.body(diag, "private fun refresh()").contains("refreshPrereqs()"))
        assertTrue(clusterDiag.contains("DozeWhitelistRead.READ"), "ClusterDiag chụp danh sách miễn pin thật")
        assertTrue(clusterDiag.contains("UnsupportActivity"), "ClusterDiag chụp dấu vết hộp 'IVI không hỗ trợ'")
        // Review 2.89 Pass 3 · vietmap-dock-r2-5: dòng `START … from uid` là `Slog.i` ⇒ bộ đệm SYSTEM (r47 `Slog.java:51-52`).
        assertTrue(clusterDiag.contains("logcat -d -b main -b system -b events | grep -E 'REQUEST_IGNORE_BATTERY_OPTIMIZATIONS|UnsupportActivity'"))
    }

    // ── Pass 2 · vietmap-dock-r1-2 — đường TRẢ LẠI (dấu bền trước lệnh ghi, lượt ready trả dấu đã rời phạm vi) ─────────────

    @Test
    fun `moi luot ghi deu mang dau ben, chi luot ready tra lai`() {
        listOf("ensureAll(targets, { cmd -> sh.execute(cmd)", "PrefsMarks(app))").forEach {
            assertTrue(SourceRoots.body(prereqs, "fun ensureForCastOpen(").contains(it), "lượt mở chiếu thiếu `$it`")
        }
        val scope = SourceRoots.body(prereqs, "private fun runScope(")
        order(scope, "val marks = PrefsMarks(app)", "if (extra.isEmpty()) lock.withLock { AppPrereqPlan.stale(f, marks) }",
            "AppPrereqPlan.ensureAll(targets, run, marks)", "AppPrereqPlan.revertStale(f, marks, run)")
        val marks = SourceRoots.body(prereqs, "private class PrefsMarks(app: Context) : AppPrereqPlan.Marks {")
        assertTrue(marks.contains(".commit()") && !marks.contains(".apply()"), "dấu phải chạm đĩa TRƯỚC lệnh ghi: $marks")
        assertTrue(marks.contains("AppPrereqPlan.markKey(p)"))
        assertTrue(prereqs.contains("private const val MARKS_FILE = \"app_prereq_marks\""))
    }

    // ── byd_float_app_list: vẫn ghi, đọc trước, không gán tác dụng ─────────────────────────────────────────

    @Test
    fun `byd_float_app_list doc truoc, chi ghi khi vang, appops do AppPrereqs lo`() {
        val fn = SourceRoots.body(autostart, "fun runNow(ctx: Context, returnToSelfPkg: String?)")
        order(fn, "if (Prefs.vmBubbleEnabled(app))", "settings get global byd_float_app_list",
            // Pass 2 · vietmap-dock-r1-6: đọc hỏng KHÔNG phải "rỗng" ⇒ không ghi đè danh sách chung (mục Gemini).
            "if (!read.ok)", "return@runCatching", "val curFloat = read.output.trim()",
            "FloatAppList.contains(curFloat, PKG)", "settings put global byd_float_app_list")
        assertFalse(autostart.contains("appops set"), "quyền vẽ nổi đi qua AppPrereqs (đọc sự thật), không ghi mù ở đây")
    }

    // ── Chờ bóng (owner 05/10: mạng chậm, hạ xuống thì bóng không lên) ────────────────────────────────────

    @Test
    fun `chi ha nen khi luat cho phep, doc lai service bong MOT lan sau khi ha`() {
        val fn = SourceRoots.body(autostart, "fun runNow(ctx: Context, returnToSelfPkg: String?)")
        order(
            fn,
            "monkey -p \$PKG",   // nhánh cast-default
            "monkey -p \$PKG",   // nhánh nền: mở MỘT lần rồi mới chờ
            "val wait = awaitBubble(",
            // Pass 2 · whole-r1-1: hạ hay không do luật thuần quyết (STILL_SPLASH + nổ máy + chuyến còn chờ ⇒ vẫn về HOME).
            "tripPendingThisIgnition(app)",
            // Pass 3 · vietmap-dock-r2-1: + miễn pin chưa chứng minh ⇒ USER_LEFT/NEVER_FOREGROUND lúc nổ máy cũng về HOME.
            "val goBack = VietMapBubbleWait.backgroundAfter(wait.outcome, bootPath, tripPending, dialogExpected)",
            "if (goBack)",
            "HomeActivityCmd.GO_HOME",
            "bubbleAfterBackground(sh)",
            "record(",
        )
        assertTrue(fn.contains("if (goBack && bubbleOn) bubbleAfterBackground(sh) else null"))
        assertFalse(fn.contains("if (wait.outcome.background)"), "không quyết hạ nền ngoài luật backgroundAfter")
        val trip = SourceRoots.body(autostart, "private fun tripPendingThisIgnition(app: Context): Boolean")
        assertTrue(trip.contains("!WorkspacePrefs(app).tripConfig().empty") && trip.contains("TripStart.now(app) != TripGate.Now.SHOWN"), trip)
        // Không vòng mở lại: đúng HAI lệnh mở VietMap (nhánh cast-default · nhánh nền), không lệnh nào trong vòng chờ.
        assertEquals(2, Regex(Regex.escape("monkey -p \$PKG")).findAll(autostart).count())
        val wait = SourceRoots.body(autostart, "private fun awaitBubble(")
        assertTrue(wait.contains("VietMapBubbleWait.next("), "luật chờ ở :core, không chép lại")
        assertFalse(wait.contains("monkey"), "vòng chờ không bao giờ mở lại VietMap")
        // Pass 2 · vietmap-dock-r1-1: quyết theo DISPLAY 0 (dòng tổng = màn GIỮ TIÊU ĐIỂM, fixture 13), đồng hồ đơn điệu (r1-3).
        assertTrue(wait.contains("VietMapBubbleWait.topOnDefaultDisplay(sh(RESUMED_GREP).output)"), "đọc display 0")
        assertFalse(wait.contains("VietMapBubbleWait.topResumed("), "dòng tổng không còn quyết USER_LEFT/READY")
        assertTrue(autostart.contains("private const val RESUMED_GREP = VietMapBubbleWait.PER_DISPLAY_GREP"))
        assertEquals(2, Regex(Regex.escape("SystemClock.elapsedRealtime()")).findAll(wait).count(), "startMs + nowMs đơn điệu")
        assertFalse(wait.contains("System.currentTimeMillis()"), "giờ tường chỉ cho Chẩn đoán (Pass 2 · vietmap-dock-r1-3)")
        assertFalse(autostart.contains("isInMapActivity"), "bộ đọc cũ (giả định màn chờ khác tên activity — sai) đã gỡ")
        val recheck = SourceRoots.body(autostart, "private fun bubbleAfterBackground(")
        assertEquals(1, Regex(Regex.escape("SERVICES_DUMP")).findAll(recheck).count(), "đọc lại đúng MỘT lần")
    }

    /**
     * Review 2.89 Pass 3 · vietmap-dock-r2-1 — kết quả lượt AUTOSTART_PASS KHÔNG bị vứt: miễn pin chưa chứng minh CÓ ⇒ hộp "IVI
     * không hỗ trợ" là chuyện đã biết ⇒ luật nhận `dialogExpected` (cả cổng đọc sổ chuyến). Thử ĐỎ: trả lời gọi về
     * `AppPrereqs.ensure(app, PKG, AppPrereqPlan.Role.AUTOSTART_PASS)` trần.
     */
    @Test
    fun `Pass 3 - mien pin chua chung minh thi hop IVI la chuyen da biet`() {
        val fn = SourceRoots.body(autostart, "fun runNow(ctx: Context, returnToSelfPkg: String?)")
        order(fn, "val prereq = AppPrereqs.ensure(app, PKG, AppPrereqPlan.Role.AUTOSTART_PASS)",
            "val dialogExpected = (prereq?.after?.dozeExempt ?: Truth.UNKNOWN) != Truth.YES",
            "VietMapBubbleWait.mayGoHome(wait.outcome, dialogExpected) && tripPendingThisIgnition(app)",
            "VietMapBubbleWait.backgroundAfter(wait.outcome, bootPath, tripPending, dialogExpected)")
    }

    /**
     * Review 2.89 Pass 3 · vietmap-dock-r2-3 — service bóng ĐÃ chạy trước `monkey` (FGS sống lâu hơn activity) không phải bằng chứng
     * cho lượt Dart mới: đọc MỘT lần trước lệnh mở, vòng chờ chỉ nhận khi `lastActivity` mới hơn lượt mở ([VietMapBubbleWait.freshBubble],
     * phép đọc thuần + fixture ở `VietMapBubbleWaitTest`). Thử ĐỎ: bỏ `bubbleBefore` (truyền `false`).
     */
    @Test
    fun `Pass 3 - service bong da chay truoc khong la bang chung`() {
        val fn = SourceRoots.body(autostart, "fun runNow(ctx: Context, returnToSelfPkg: String?)")
        order(fn, "val bubbleBefore = if (!bubbleOn || !running) false", "hasBubbleService(sh(SERVICES_DUMP).output) }.getOrNull()",
            "monkey -p \$PKG", "val wait = awaitBubble(sh, needBubble = bubbleOn, bubbleBefore = bubbleBefore)")
        val wait = SourceRoots.body(autostart, "private fun awaitBubble(")
        assertTrue(wait.contains("VietMapBubbleWait.freshBubble(hasBubbleService(dump), bubbleBefore,"), wait)
        assertTrue(wait.contains("VietMapBubbleWait.serviceLastActivityAgoMs(dump, BUBBLE_SERVICE), nowMs - startMs)"), wait)
    }

    @Test
    fun `ham moi deu co cho goi`() {
        mapOf(
            "AppPrereqs.onReady(" to "EarlyShellChannel.kt",
            "AppPrereqs.ensure(" to "VietMapAutostart.kt",
            "AppPrereqs.ensureForCastOpen(" to "SimpleCastRuntime.kt",
            "AppPrereqs.diagnose(" to "DiagActivity.kt",
            "VietMapAutostart.castDefault(" to "AppPrereqs.kt",
            "CastAutoStartPkgs.of(" to "AppPrereqs.kt",
            "FloatAppList.contains(" to "VietMapAutostart.kt",
            "VietMapBubbleWait.next(" to "VietMapAutostart.kt",
            "AppPrereqPlan.targets(" to "AppPrereqs.kt",
            "AppPrereqPlan.read(" to "AppPrereqs.kt",
            // Review 2.89 Pass 2
            "VietMapBubbleWait.topOnDefaultDisplay(" to "VietMapAutostart.kt",
            "VietMapBubbleWait.backgroundAfter(" to "VietMapAutostart.kt",
            // Review 2.89 Pass 3
            "VietMapBubbleWait.mayGoHome(" to "VietMapAutostart.kt",
            "VietMapBubbleWait.freshBubble(" to "VietMapAutostart.kt",
            "VietMapBubbleWait.serviceLastActivityAgoMs(" to "VietMapAutostart.kt",
            "AppPrereqPlan.stale(" to "AppPrereqs.kt",
            "AppPrereqPlan.revertStale(" to "AppPrereqs.kt",
            "AppPrereqPlan.markKey(" to "AppPrereqs.kt",
            "themeGapRetryMs()" to "BubbleAutostart.kt",
            "slotDisplayConfig(intent.pkg)" to "SimpleCastCoordinatorIntents.kt",
            "ClusterRectLayout.oneToOne(" to "CastSessionPin.kt",
        ).forEach { (call, file) ->
            val callers = allMain.filter { it.second.contains(call) }.map { it.first.substringAfterLast('/') }
            assertTrue(file in callers, "`$call` phải được gọi ở $file — thấy ở $callers")
        }
    }
}
