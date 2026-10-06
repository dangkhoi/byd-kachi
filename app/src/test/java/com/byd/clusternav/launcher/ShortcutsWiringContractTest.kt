package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F1 · V1 (nhóm B — Shortcuts UI, spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R1.2–R1.6 · §4.4) — bài canh
 * TĨNH nối dây giao diện lối tắt. Thân hàm cắt bằng [SourceRoots.body] (đếm ngoặc, nổ nếu mốc không có) trên mã đã bỏ
 * chú thích ([SourceRoots.codeOf]) — không `substringAfter/Before`.
 *
 * Mỗi bài khoá một điều owner chốt hoặc một luật dự án:
 *  - chạm ⇒ bảng quyết định THUẦN ở `:core` (không tự quyết ở tầng view);
 *  - kiểu cần kênh gọi `ShellAccessUi.allowOrPrompt` NGAY TRƯỚC khi thi hành (READY-AT-HOME R1.3/R0.8);
 *  - đính chính owner 01/10: lối tắt *Ô n* là TẠM ⇒ không `assignApp`, không ghi bền, không giết app cũ;
 *  - T-M2 [ĐO máy ảo 02/10]: Intent từ HOME KHÔNG tách app khỏi màn ảo (4/4) ⇒ `DetachToFull` đi K7 qua kênh (hỏi quyền
 *    trước), không bao giờ qua đường Intent; về ô bằng K8 khi màn nhà hiện lại;
 *  - một danh sách, hai bề mặt, một view; bên nghe gắn/gỡ theo vòng đời view (không rò Activity);
 *  - Cài đặt chỉ ghi qua ViewModel (`GridSeamGuardTest`), phép sửa là hàm thuần [ShortcutSelection].
 */
class ShortcutsWiringContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val glue by lazy { code("KachiHomeShortcuts.kt") }
    private val view by lazy { code("ShortcutIconsView.kt") }
    private val dock by lazy { code("ControlDockView.kt") }
    private val widgets by lazy { code("WidgetViews.kt") }
    private val settings by lazy { code("SettingsSectionsShortcuts.kt") }
    private val drawer by lazy { code("AppDrawer.kt") }
    private val drawerCtl by lazy { code("DrawerController.kt") }
    private val vm by lazy { code("HomeViewModel.kt") }

    private fun order(src: String, vararg marks: String) {
        var at = -1
        for (m in marks) {
            val i = src.indexOf(m, at + 1)
            assertTrue(i > at, "thứ tự sai/thiếu: '$m' phải đứng sau mốc trước trong:\n$src")
            at = i
        }
    }

    private val act by lazy { SourceRoots.body(glue, "private fun act(") }

    // ── R1.5 · chạm ⇒ bảng ở :core ⇒ đường đã có ─────────────────────────────────────────────────────────────

    /**
     * FIX286 · R-SC2 đổi chân bài này (không nới): bảng thuần vẫn là chỗ quyết DUY NHẤT, nhưng nay nằm ở `decideAndAct` sau
     * phép đo — `onShortcut` không được tự gọi `ShortcutPlan.decide` (quyết trước khi đo là đúng lỗi owner 03/10).
     */
    @Test
    fun `cham loi tat di qua bang quyet dinh thuan o core, voi su that do duoc`() {
        val fn = SourceRoots.body(glue, "override fun onShortcut(sc: AppShortcut)")
        order(fn, "ShellAccessUi.usableNow()", "ShortcutPlan.presenceSlot(input)",
            "if (probe < 0) return decideAndAct(sc, input, count, stages)",
            "slots().presence(probe, sc.pkg) { measured -> decideAndAct(sc, input.copy(presence = measured), count, stages) }")
        assertFalse(fn.contains("ShortcutPlan.decide("), "onShortcut quyết TRƯỚC khi đo ⇒ dòng 4/9/12 lại quyết bằng bố cục")
        order(SourceRoots.body(glue, "private fun decideAndAct("), "ShortcutPlan.decide(input)", "act(sc, action")
        assertTrue(fn.contains("workspace().stagingCandidates(shown, count)"), "ô dàn dựng đọc từ cây view thật (A5)")
        assertTrue(fn.contains("BehindHomePlan.stagingSlot(stages, sc.pkg) != null"), "CÙNG bộ chọn ô mà runner dùng")
        assertTrue(fn.contains("EffectiveLayout.slotCount("), "số ô ĐANG hiện, không đoán từ preset")
        assertTrue(fn.contains("st.effectiveWorkspace.slots"), "ô đang hiện = lớp lưu + lớp tạm")
        assertTrue(fn.contains("fullByIntent = false"), "T-M2 [ĐO]: Intent từ HOME không kéo được app ra khỏi ô (am_new_intent tại chỗ, 4/4)")
    }

    @Test
    fun `kieu can kenh goi allowOrPrompt NGAY TRUOC khi thi hanh, Prompt chi nhac`() {
        order(SourceRoots.body(act, "is ShortcutAction.PlaceTemp -> {"), "ShellAccessUi.allowOrPrompt(activity)", "return", "slots().placeTemporary(")
        order(SourceRoots.body(act, "ShortcutAction.StartBehind -> {"), "ShellAccessUi.allowOrPrompt(activity)", "return", "slots().startBehind(")
        assertTrue(act.contains("ShortcutAction.Prompt -> ShellAccessUi.allowOrPrompt(activity)"))
    }

    @Test
    fun `dat tam la TAM - khong assignApp, khong ghi ben, khong lenh shell nao o lop keo`() {
        listOf("assignApp", "persist", "WorkspacePrefs", "workspaceRepository", "force-stop", "\"am ", "\"pidof", "shell(")
            .forEach { assertFalse(glue.contains(it), "KachiHomeShortcuts không được chạm '$it' — đặt tạm đi qua slots.placeTemporary") }
        assertTrue(SourceRoots.body(act, "is ShortcutAction.PlaceTemp -> {").contains("slots().placeTemporary(action.slot, sc.pkg)"))
    }

    @Test
    fun `toan man di DUONG ngan keo Ung dung (Intent), khong can kenh`() {
        val fn = SourceRoots.body(act, "is ShortcutAction.OpenFull -> {")
        assertTrue(fn.contains("slots().openAppFullscreen(sc.pkg)"))
        assertFalse(fn.contains("allowOrPrompt"), "Toàn màn KHÔNG cần kênh (R1.5) — hỏi quyền ở đây là chặn oan")
    }

    /**
     * T-M2 [ĐO máy ảo 02/10, `behind-home-…/finish/tm2`]: Intent từ HOME (đường ngăn kéo) với app đang ở ô ⇒ `am_new_intent`
     * TẠI CHỖ, không ra toàn màn (VietMap · YT Music · Đồng hồ · Kiki); K7 qua kênh ⇒ ra toàn màn, pid giữ. Bài khoá: nhánh
     * này hỏi kênh TRƯỚC rồi đi đúng đường K7 của host ô — không bao giờ đi đường Intent (đã đo là không ăn), không lệnh
     * shell tại lớp keo; ô chưa sẵn ⇒ chỉ ra chỗ app đang ở.
     */
    @Test
    fun `DetachToFull - hoi kenh truoc, di K7 cua host o, khong bao gio qua Intent`() {
        val fn = SourceRoots.body(act, "is ShortcutAction.DetachToFull -> {")
        order(fn, "ShellAccessUi.allowOrPrompt(activity)", "return", "slots().detachToFull(action.slot)")
        listOf("openAppFullscreen", "startBehind", "placeTemporary").forEach {
            assertFalse(fn.contains(it), "'$it' ở nhánh tách-ra-toàn-màn: T-M2 đo Intent KHÔNG tách được app khỏi ô")
        }
        assertTrue(fn.contains("R.string.kachi_sc_full_failed"), "không tách được ⇒ nói ra (chuỗi tài nguyên)")
        assertTrue(fn.contains("if (!started) { workspace().flashSlot(action.slot)"), "ô chưa sẵn ⇒ chỉ ra chỗ app đang ở")
        val slots = code("KachiHomeSlots.kt")
        val glue = SourceRoots.body(slots, "fun detachToFull(index: Int, done: (Boolean) -> Unit)")
        assertTrue(glue.contains("ClusterProfile.resolveCached(app).cameraSignature") && glue.contains("DefaultHome.shownComponents(app)"),
            "rào K7: dấu hiệu camera theo đời xe (ClusterProfile) + cả hai dạng màn nhà Kachi")
    }

    /**
     * FIX286 · R-SC2 — lỗi xe owner 03/10 (*"tắt gmaps … bấm lại icon gmaps ở shortcut, chỉ hiện icon gmaps"*). Khoá ba mắt
     * xích: đo bằng `am stack list` trên luồng nền (một lệnh, không ở lớp keo), không đo được ⇒ UNKNOWN (bảng giữ hành vi cũ,
     * không force-stop); mở lại đi CHÍNH đường thẻ "App đã đóng" của host (nhịp đo cũ thôi trước, rồi `reopen()`), hỏi kênh
     * trước; lượt mở đang chạy dở ⇒ không chồng lệnh.
     */
    @Test
    fun `Reopen - do bang am stack list o luong nen, mo lai qua duong the da dong, hoi kenh truoc`() {
        val slotsSrc = code("KachiHomeSlots.kt")
        val presence = SourceRoots.body(slotsSrc, "fun presence(index: Int, pkg: String, done: (SlotPresence) -> Unit)")
        order(presence, "workspace().hostAt(index)?.stage()", "stage.pkg != pkg", "done(SlotPresence.UNKNOWN); return",
            "submitBg {", "sh(BehindHomePlan.LIST_CMD)", "SlotPresence.of(out, pkg, stage.vd)", "mainHandler.post { done(p) }",
            "if (!accepted) done(SlotPresence.UNKNOWN)")
        assertEquals(1, Regex("\\bsh\\(").findAll(presence).count(), "đúng MỘT lệnh đọc mỗi lần chạm, không lệnh ghi nào")
        val fn = SourceRoots.body(act, "is ShortcutAction.Reopen -> {")
        order(fn, "ShellAccessUi.allowOrPrompt(activity)", "return", "val ok = slots().reviveInSlot(action.slot, sc.pkg)",
            "if (!ok) workspace().flashSlot(action.slot)")
        listOf("placeTemporary", "openAppFullscreen", "startBehind", "detachToFull").forEach {
            assertFalse(fn.contains(it), "'$it' ở nhánh mở-lại: app đã đóng TRONG ô thì mở lại vào chính ô đó")
        }
        assertTrue(SourceRoots.body(slotsSrc, "fun reviveInSlot(index: Int, pkg: String)").contains("workspace().hostAt(index)?.reviveInSlot(pkg) ?: false"))
        val host = code("VdAppHost.kt")
        val revive = SourceRoots.body(host, "fun reviveInSlot(expect: String)")
        // ⚠ L6 (owner 03/10): thẻ "đã đóng" (`closedCard`) gỡ — dấu "nhịp đo đã báo app rời ô" nay là cờ `dead` (đặt ở
        // `onAppClosed`, hạ ở `reopen`/`swapApp`). Cùng rào: chưa báo chết, chưa đo, không toàn màn ⇒ lượt mở đang chạy dở.
        assertTrue(revive.contains("!dead && !full.isDetached && !SlotLiveProbe.watching(probeKey)"),
            "lượt mở đang chạy dở (chưa vào nhịp đo, chưa báo chết, không toàn màn) ⇒ không mở chồng — force-stop giữa lượt mở")
        assertTrue(revive.contains("if (released || !launched || pkg != expect || busy) return false"))
        order(revive, "SlotLiveProbe.unwatch(probeKey)", "full.reset()", "reopen()")
    }

    /** FIX286 · R-SC1 — ô widget không còn lý do "đang là widget ⇒ mở toàn màn" ở bất kỳ tầng nào (bảng · keo · chuỗi). */
    @Test
    fun `o widget khong con nhanh mo toan man`() {
        assertFalse(glue.contains("SLOT_WIDGET") || glue.contains("kachi_sc_reason_widget"))
        assertFalse(ShortcutPlan.Reason.entries.any { it.name == "SLOT_WIDGET" })
        listOf("src/main/res/values/strings_kachi.xml", "src/main/res/values-en/strings_kachi.xml").forEach {
            assertFalse(SourceRoots.text(it).contains("kachi_sc_reason_widget"), "$it còn chuỗi lý do cũ")
        }
    }

    /**
     * L8 — ĐỔI GHIM có lý do (owner 03/10, OQ-L4-1): không có ô sống ⇒ màn ảo ẨN (đường cuối, cùng chuỗi chuyến lên xe) thay
     * cho câu từ chối. Câu `kachi_sc_no_stage` chỉ còn khi chuỗi trả `NO_STAGE` (không tạo được màn ảo ẩn).
     */
    @Test
    fun `chay ngam khong co o song - qua man ao an, khong tao duoc thi noi ly do`() {
        val fn = SourceRoots.body(act, "ShortcutAction.StartBehind -> {")
        order(fn, "ShellAccessUi.allowOrPrompt(activity)", "slots().startBehind(", "if (stage == null) slots().startBehindHidden(sc.pkg)")
        order(SourceRoots.body(act, "ShortcutAction.StartBehindHidden -> {"), "ShellAccessUi.allowOrPrompt(activity)", "return",
            "slots().startBehindHidden(sc.pkg) { out -> onBehindDone(sc, out) }")
        assertTrue("BehindHomeSequence.Result.NO_STAGE -> say(activity.getString(R.string.kachi_sc_no_stage))" in
            SourceRoots.body(glue, "private fun onBehindDone("))
        assertFalse("kachi_sc_no_stage" in code("SettingsSectionsShortcuts.kt"),
            "Cài đặt thôi nhắc 'cần ít nhất một ô app' — điều kiện đó không còn")
    }

    // ── R1.2 / R1.3 · một view, hai bề mặt ───────────────────────────────────────────────────────────────────

    @Test
    fun `khoi thanh nut - ma launcher_shortcuts dung khoi icon co be dai tu core`() {
        val rebuild = SourceRoots.body(dock, "private fun rebuild()")
        assertTrue(rebuild.contains("if (id == LauncherActions.SHORTCUTS) addView(shortcutStrip())"))
        val strip = SourceRoots.body(dock, "private fun shortcutStrip()")
        // 2.89 · B3 DOCK-SCALE — đổi chân CÓ CHỦ Ý (không nới): khối dựng bằng `ui` (Context co/giãn của thanh; ở 100 % `ui`
        // LÀ `context` ⇒ y hệt 2.88) để icon + khe co theo cỡ thanh như mọi ô khác.
        assertTrue(strip.contains("ShortcutIconsView(ui, grid = false)"))
        assertTrue(strip.contains("shortcutStripLength(ui, ShortcutHub.items().size)"), "bề dài theo số app (R1.2)")
        assertTrue(strip.contains("vertical = v"), "thanh dọc ⇒ khối cao ra")
        val viewRebuild = SourceRoots.body(view, "private fun rebuild()")
        assertTrue(viewRebuild.contains("shortcutStripLength(context, items.size)"), "đổi danh sách ⇒ đặt lại bề dài")
        // MỘT phép bề dài: số khe từ `:core`, số dp từ thang (`KachiBars`) — E7: 1/4/8 app = 1/4/8 × 52 dp + 2 × 4 dp.
        // B3 — khe = `shortcutSlotPx` = max(SHORTCUT_CELL, 48 dp THẬT): ở 100 % đúng 52 dp như cũ; thanh co ⇒ khe không dưới
        // đích chạm. Vẫn MỘT phép cho cả `cellPx()` lẫn bề dài (bài `ShortcutGridFitWiringContractTest` canh `cellPx`).
        assertTrue(SourceRoots.body(view, "internal fun shortcutStripLength(ctx: Context, n: Int)")
            .contains("ShortcutStrip.cells(n) * shortcutSlotPx(ctx) + 2 * dpi(ctx, Bars.SHORTCUT_PAD)"))
        assertTrue(SourceRoots.body(view, "internal fun shortcutSlotPx(ctx: Context)")
            .contains("maxOf(dpi(ctx, Bars.SHORTCUT_CELL), DockScaleContext.touchFloorPx(ctx))"))
        assertEquals(52, KachiBars.SHORTCUT_CELL, "owner 01/10: icon 52 dp")
        assertEquals(KachiSpace.XS, KachiBars.SHORTCUT_PAD)
        // 2.87 R-SI1 đổi chân (không nới): lưới widget KHÔNG còn khe cố định `SHORTCUT_GRID_CELL` (64 dp) để so — cỡ icon +
        // khe nay khớp theo khung thật; đích chạm của lưới (icon + khe) khoá ở `ShortcutGridFitWiringContractTest`.
        // Khe của khối thanh nút vẫn cố định nên vế của nó giữ nguyên.
        assertTrue(KachiBars.SHORTCUT_CELL >= KachiSpace.TOUCH, "đích chạm ≥ 48 dp")
    }

    @Test
    fun `widget w_apps dung CUNG view, o to va o nen`() {
        // ĐỔI GHIM 2.93 wave 2A (SHORTCUT-SCROLL-REBUILD): ô to và ô nền đều mang `scrollKey` để giữ cuộn qua lượt dựng view MỚI.
        assertTrue(SourceRoots.body(widgets, "fun build(").contains("\"w_apps\" -> ShortcutIconsView(ctx, grid = true, scrollKey = scrollKey)"))
        assertTrue(SourceRoots.body(widgets, "private fun mini(").contains("\"w_apps\"   -> ShortcutIconsView(ctx, grid = true, compact = true, scrollKey = scrollKey)"))
    }

    @Test
    fun `ben nghe gan khi view gan, go khi view thao - khong ro Activity`() {
        val on = SourceRoots.body(view, "override fun onAttachedToWindow()")
        listOf("ShortcutHub.addListener(onList)", "ShellReadiness.addListener(onReady)", "registerPackages()", "rebuild()")
            .forEach { assertTrue(on.contains(it), "gắn: thiếu '$it'") }
        val off = SourceRoots.body(view, "override fun onDetachedFromWindow()")
        listOf("ShortcutHub.removeListener(onList)", "ShellReadiness.removeListener(onReady)", "unregisterPackages()")
            .forEach { assertTrue(off.contains(it), "tháo: thiếu '$it'") }
        val hub = code("ShortcutHub.kt")
        assertTrue(hub.contains("WeakHashMap<Activity, WeakReference<Host>>"), "bảng chủ giữ YẾU cả hai đầu")
    }

    @Test
    fun `icon mo theo kenh va theo app da go, cham ve chu cua man, ten app la contentDescription`() {
        val dim = SourceRoots.body(view, "private fun paintDim()")
        assertTrue(dim.contains("ShellAccessUi.usableNow()") && dim.contains("cell.sc.mode.needsChannel"))
        assertTrue(dim.contains("!cell.installed ->"))
        assertTrue(SourceRoots.body(view, "private fun cell(sc: AppShortcut)").contains("ShortcutHub.tap(context, sc)"))
        assertTrue(SourceRoots.body(view, "private fun load(gen: Int)").contains("cell.view.contentDescription = it"))
    }

    /**
     * 2.93 QA máy ảo F1 [ĐO 07/10]: R1 nạp lại TẠI CHỖ ⇒ app trong dải bị gỡ (hình chung, đã [KachiIcons.tint]) rồi cài lại
     * nhận icon thật trên CÙNG view — bộ lọc màu còn ⇒ icon xám (trắng đọc 184 = 0,72 × 255) tới lần dựng lại. Khoá: gỡ tint
     * NGAY TRƯỚC khi gắn icon thật; `untint` xoá cả bộ lọc (gốc F1: `ImageView` áp lại bộ lọc lên drawable mới) lẫn cờ (cờ còn
     * thì một lượt `refit` sau này tô lại — dải hôm nay không qua `FitScale`, cờ là vệ sinh hợp đồng).
     */
    @Test
    fun `app cai lai nhan icon that - go tint cua hinh chung truoc khi gan`() {
        val load = SourceRoots.body(view, "private fun load(gen: Int)")
        order(load, "if (icon != null) {", "KachiIcons.untint(cell.view)", "cell.view.setImageDrawable(icon)", "else genericIcon(cell.view)")
        val untint = SourceRoots.body(code("KachiIcons.kt"), "fun untint(img: ImageView)")
        assertTrue(untint.contains("drawnBy[img]?.tinted = false"), "cờ tint phải rơi — không thì refit tô lại")
        assertTrue(untint.contains("img.colorFilter = null"), "bộ lọc màu phải gỡ")
    }

    @Test
    fun `chu nhan cham dang ky theo Activity, danh sach day o moi render`() {
        assertTrue(glue.contains("init { ShortcutHub.bind(activity, this) }"))
        assertTrue(SourceRoots.body(code("KachiHomeRender.kt"), "internal fun KachiHomeActivity.render(state: HomeUiState)").contains("shortcuts.publish(state)"))
        val act = code("KachiHomeActivity.kt")
        assertTrue(act.contains("KachiHomeShortcuts(this, viewModel, { slots }, { workspace }, { drawerController })"))
        assertTrue(act.contains("shortcuts = shortcuts,"), "trang Cài đặt nhận cổng lối tắt")
    }

    // ── R1.4 · Cài đặt + ngăn kéo chọn app ───────────────────────────────────────────────────────────────────

    @Test
    fun `Cai dat chon app qua ngan keo, sua bang ham thuan, ghi qua ViewModel`() {
        assertTrue(SourceRoots.body(glue, "override fun openPicker(").contains("drawer().openShortcutPicker(selected, onApply)"))
        assertTrue(SourceRoots.body(glue, "override fun save(").contains("viewModel.setAppShortcuts(items)"))
        assertTrue(SourceRoots.body(settings, "private fun openPicker()").contains("ShortcutSelection.apply(items, picked)"))
        assertTrue(settings.contains("ShortcutSelection.setMode(") && settings.contains("ShortcutSelection.move("))
        assertTrue(SourceRoots.body(settings, "private fun save(").contains("port.save(next)"))
        assertTrue(settings.contains("ShortcutSelection.slotChips(count, sc.mode)"), "chip Ô theo số ô của bố cục ĐANG dùng")
        val set = SourceRoots.body(vm, "fun setAppShortcuts(items: List<AppShortcut>)")
        order(set, "AppShortcutCodec.sanitize(items)", "_uiState.update", "repository.setAppShortcuts(clean)")
        assertEquals(1, Regex("repository\\.setAppShortcuts\\(").findAll(vm).count(), "một đường ghi")
    }

    @Test
    fun `ngan keo chon loi tat - che do rieng, dung CHUNG duong chon, tran = tran ky thuat AppShortcutCodec MAX`() {
        val open = SourceRoots.body(drawerCtl, "fun openShortcutPicker(")
        // F2 · U6 (02/10, nhóm C): CÙNG bộ chọn phục vụ thêm chế độ PICK_TRIP (app mở khi nổ máy) qua tham số `mode` — ghim
        // đổi từ "thân đặt PICK_SHORTCUTS" sang "mặc định của tham số = PICK_SHORTCUTS + thân chuyển đúng tham số đó";
        // lối tắt (KachiHomeShortcuts.openPicker) không truyền `mode` ⇒ vẫn mở đúng PICK_SHORTCUTS. Độ chặt giữ nguyên.
        assertTrue(drawerCtl.contains("mode: AppDrawer.Mode = AppDrawer.Mode.PICK_SHORTCUTS,"))
        assertTrue(open.contains("mode = mode,"))
        assertTrue(open.contains("onApply(ids.toList()); close()"))
        // 2.92 — ĐỔI GHIM có lý do (owner 06/10 *"không nên giới hạn 8 app trong shortcut app đâu"*, spec 292 R2): bảng
        // chọn lối tắt KHÔNG còn dùng `AppDrawer.MAX` (8 — trần WIDGET mỗi ô giữ nguyên) mà trần KỸ THUẬT của danh sách.
        assertTrue(drawer.contains("Mode.PICK_SHORTCUTS -> AppShortcutCodec.MAX"), "trần bảng = trần danh sách lối tắt")
        assertTrue(drawer.contains("const val MAX = 8"), "trần 8 WIDGET mỗi ô (gán ô) giữ nguyên — không phải lối tắt")
        assertTrue(AppShortcutCodec.MAX >= 200, "trần kỹ thuật ≫ số app có màn khởi chạy (máy ảo 23)")
        assertTrue(drawer.contains("Mode.PICK_SHORTCUTS -> context.getString(R.string.kachi_sc_cap_note, cap)"),
            "câu nhắc trần đọc CHÍNH trần của bảng")
        assertTrue(SourceRoots.body(code("SettingsSectionsShortcuts.kt"), "private fun pickText()")
            .contains("getString(R.string.kachi_sc_pick_n, items.size)"), "nút Cài đặt chỉ còn số đã chọn — hết '(n/8)'")
        assertTrue(SourceRoots.body(drawer, "private fun placeBar()").contains("mode == Mode.PICK_SHORTCUTS) onApply(selected.toSet())"))
        val pick = code("AppDrawerShortcutPick.kt")
        assertTrue(SourceRoots.body(pick, "internal fun AppDrawer.shortcutPickSection(").contains("toggleSelection("),
            "bật/tắt đi ĐÚNG đường có trần + lời nhắc (PickerCapNoticeContractTest)")
        assertFalse(pick.contains("Toast"), "bề mặt phủ không nói bằng Toast")
    }

    @Test
    fun `prefs doc an toan kieu va nap cung luot load`() {
        val prefs = code("WorkspacePrefsShortcuts.kt")
        assertTrue(SourceRoots.body(prefs, "fun WorkspacePrefs.appShortcuts()").contains("sp.stringOrNull(key(K_APP_SHORTCUTS))"))
        assertTrue(SourceRoots.body(prefs, "fun WorkspacePrefs.setAppShortcuts(").contains("AppShortcutCodec.encode(items)"))
        assertTrue(SourceRoots.body(code("PrefsWorkspaceRepository.kt"), "override fun load(): HomeUiState").contains("shortcuts = prefs.appShortcuts()"))
    }

    // ── §8 · hàm mới phải có call site ngoài định nghĩa ──────────────────────────────────────────────────────

    @Test
    fun `moi ham moi deu co call site`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.map { p ->
            p.fileName.toString() to KotlinSource.stripComments(p.toFile().readText())
        }
        assertTrue(all.size > 300, "quét được quá ít tệp (${all.size}) — đường dẫn sai thì bài này là test giả")
        mapOf(
            "ShortcutPlan.decide(" to "KachiHomeShortcuts.kt",
            "AppShortcutCodec.decodeReport(" to "WorkspacePrefsShortcuts.kt",
            "AppShortcutCodec.encode(" to "WorkspacePrefsShortcuts.kt",
            "behind.startBehind(" to "KachiHomeSlots.kt",
            "slots().startBehind(" to "KachiHomeShortcuts.kt",
            "stagingCandidates(" to "KachiHomeShortcuts.kt",
            "hostAt(i)?.stage()" to "WorkspaceViewSwap.kt",
            "SlotLiveProbe.seenAlive(" to "VdAppHost.kt",
            "flashSlot(" to "KachiHomeShortcuts.kt",
            "openShortcutPicker(" to "KachiHomeShortcuts.kt",
            "SettingsShortcutsSection(" to "SettingsSectionsBars.kt",
            "ShellAccessUi.usableNow()" to "ShortcutIconsView.kt",
            "ShortcutSelection.apply(" to "SettingsSectionsShortcuts.kt",
            "shortcutPickSection(" to "AppDrawer.kt",
            "ShortcutHub.publish(" to "KachiHomeShortcuts.kt",
            "prefs.appShortcuts()" to "PrefsWorkspaceRepository.kt",
            // F1 dòng 9 + R1.8 (T-M2/T-M6) — mọi hàm mới của Ô ⇄ TOÀN MÀN có lời gọi production.
            "slots().detachToFull(" to "KachiHomeShortcuts.kt",
            "workspace().detachToFull(" to "KachiHomeSlots.kt",
            "hostAt(i)?.detachToFull(" to "WorkspaceViewSwap.kt",
            "hostAt(i)?.returnFromFull()" to "WorkspaceViewSwap.kt",
            "workspace.returnDetached()" to "KachiHomeActivity.kt",
            "full.detach(" to "VdAppHost.kt",
            "full.bringBack(" to "VdAppHost.kt",
            "full.reset()" to "VdAppHost.kt",
            "SlotReturnRun.detach(" to "SlotReturnRun.kt",
            "SlotReturnRun.bringBack(" to "SlotReturnRun.kt",
            "SlotReturnRun.bringBackMarked(" to "VdAppHost.kt",
            "SlotReturnRun.fullCard(" to "SlotReturnRun.kt",
            "seq(sh).detach(" to "SlotReturnRun.kt",
            "seq(sh).bringBack(" to "SlotReturnRun.kt",
            "seq(sh).bringBackMarked(" to "SlotReturnRun.kt",
            "SlotReturn.guardedDetachCmd(" to "SlotReturn.kt",
            "CameraGuard.onHomeUnlessCamera(" to "SlotReturn.kt",
            "SlotReturn.markedBehind(" to "SlotReturn.kt",
            "SlotReturn.afterK8(" to "SlotReturn.kt",
            "BehindHomePlan.mainTasksOf(" to "BehindHomeSequence.kt",
            // FIX286 · R-SC2 — đo sống/chết lúc chạm + mở lại qua đường thẻ "đã đóng".
            "ShortcutPlan.presenceSlot(" to "KachiHomeShortcuts.kt",
            "slots().presence(" to "KachiHomeShortcuts.kt",
            "slots().reviveInSlot(" to "KachiHomeShortcuts.kt",
            "SlotPresence.of(" to "KachiHomeSlots.kt",
            "hostAt(index)?.reviveInSlot(" to "KachiHomeSlots.kt",
            "SlotLiveProbe.watching(" to "VdAppHost.kt",
        ).forEach { (call, file) ->
            assertTrue(all.any { it.first == file && it.second.contains(call) }, "'$call' phải được gọi trong $file")
        }
    }

    // ── R-nf8 · chữ song ngữ ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chu moi du hai ban, ban EN khong con dau tieng Viet`() {
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        val keys = listOf(
            "kachi_drawer_title_shortcuts", "kachi_drawer_hint_shortcuts", "kachi_sc_cap_note", "kachi_sc_section",
            "kachi_sc_note", "kachi_sc_pick_n", "kachi_sc_mode_slot", "kachi_sc_mode_full", "kachi_sc_mode_bg",
            "kachi_sc_slot_outside", "kachi_sc_order_title", "kachi_sc_order_hint", "kachi_sc_empty",
            "kachi_sc_reason_absent", "kachi_sc_in_slot", "kachi_sc_running", "kachi_sc_no_stage",
            "kachi_sc_refuse_system", "kachi_sc_refuse_self", "kachi_sc_refuse_cast", "kachi_sc_not_installed",
            "kachi_sc_bg_failed", "kachi_sc_full_card", "kachi_sc_full_failed",
        )
        val marks = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ"
        keys.forEach { k ->
            assertTrue(vi.contains("name=\"$k\""), "thiếu $k ở values/")
            val m = Regex("<string name=\"$k\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(en)
            assertTrue(m != null, "thiếu $k ở values-en/")
            assertEquals(emptyList<Char>(), m!!.groupValues[1].lowercase().filter { it in marks }.toList(), "$k bản EN còn dấu")
        }
        assertTrue(SourceRoots.text("src/main/res/values/strings_kachi.xml").contains("bỏ một app để thêm"),
            "câu trần nói cả cách đi tiếp (luật PickerCapNoticeContractTest)")
    }
}
