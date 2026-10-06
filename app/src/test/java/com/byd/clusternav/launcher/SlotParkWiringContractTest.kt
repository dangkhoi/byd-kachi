package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ Ô 7 — ĐỖ ẨN (2.89-thử1 · spec `kachi-287-look-and-keys.html` §4.6d) — DÂY NỐI ═══════════════════════════════════════
 *
 * Phần thuần ở `:core` (`SlotParkTest`). `:app` không có Robolectric ⇒ bài này canh MÃ (đã bỏ chú thích) — mỗi khẳng định là
 * một mắt xích mà bài thuần không thấy:
 *  - hàm mới có chỗ gọi production thật (CLAUDE.md §8): nút *chạy nền* → `park()`; lượt dựng lại ô → `parkLeaving` TRƯỚC
 *    `releaseSlotHost`; mặt vẽ ô mới → `ParkedApps.take` → `unpark` TRƯỚC khi tạo màn ảo mới;
 *  - đường đỗ / nhận lại chạy **0 lệnh shell** (owner: không đổi trạng thái hệ thống; [ĐO xe 05/10] đổi display = relaunch):
 *    không `am`, không `wm`, không `force-stop`, không `launchInto`; 2.91 · F2: nhận lại vào ô KHÁC cỡ đổi cỡ màn ảo bằng API
 *    (`VdAppHost.resize`, cùng display) — không phải lệnh shell;
 *  - màn ảo đỗ đổi CHỦ (không nhả) ở `SlotVdOwner`, nhả màn ảo TRƯỚC khi đóng bề mặt ẩn;
 *  - đường cũ (lớp che + BEHIND-HOME · đổi-tại-chỗ `swapApp`) GIỮ biên dịch nhưng không còn chỗ gọi ở hai đường A/B.
 */
class SlotParkWiringContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    private val host by lazy { code("VdAppHost.kt") }
    private val parked by lazy { code("ParkedApps.kt") }
    private val actions by lazy { code("KachiHomeSlotActions.kt") }
    private val swap by lazy { code("WorkspaceViewSwap.kt") }
    private val workspace by lazy { code("WorkspaceView.kt") }

    private fun order(src: String, vararg parts: String) {
        var at = -1
        parts.forEach { p ->
            val i = src.indexOf(p, at + 1)
            assertTrue(i > at, "thứ tự sai / thiếu '$p' trong: ${src.take(600)}")
            at = i
        }
    }

    /** Không lệnh shell / không đổi cỡ / không mở app trong đường đỗ + nhận lại. */
    private val forbidden = listOf("\"am ", "\"wm ", "force-stop", "forceStopCmd", "launchInto", "maybeLaunch", "resize(", "sh(", "shell(")

    @Test
    fun `A - nut chay nen la DO, roi moi luat hoan o`() {
        assertTrue("Button.BACKGROUND -> park(index)" in SourceRoots.body(actions, "override fun onAction("))
        val fn = SourceRoots.body(actions, "private fun park(")
        order(fn, "if (index in busy) return", "workspace().hostAt(index)?.park() == true", "Log.i(TAG,",
            "if (parked) revert(index, Event.APP_BACKGROUND, pkg) else say(R.string.kachi_sc_bg_failed_why, pkg, PARK_NOT_READY)",
            "workspace().heads.refreshAll()")
        listOf("toBack(", "allowOrPrompt", "isSystem").forEach { assertFalse(it in fn, "'$it' — đỗ không đi qua BEHIND-HOME / kênh: $fn") }
        assertTrue("ShellAccessUi.usableNow() && hostLive, behind = true" in SourceRoots.body(actions, "override fun buttons("),
            "nút chạy nền không phụ thuộc BEHIND-HOME nữa (ô 7 không đi qua nó)")
    }

    @Test
    fun `B - luot dung lai o, do app cu TRUOC khi nha, dat tam khong con doi-tai-cho`() {
        assertTrue("is WorkspaceRenderPlan.PerSlot -> (plan.rebuild + plan.swap).sorted().forEach { i ->" in workspace,
            "ô đặt tạm (`plan.swap`) dựng lại như mọi ô khác — không còn lọc qua `swapInPlace`")
        val perSlot = SourceRoots.body(workspace, "(plan.rebuild + plan.swap).sorted().forEach {")
        order(perSlot, "parkLeaving(i, oc, nc, s.slots, profileSwitch)", "releaseSlotHost(i)", "removeView(slotViews[i])", "makeSlot(i, nc)")
        assertFalse("swapInPlace(" in workspace, "đặt tạm không còn đổi app TẠI CHỖ (app cũ ở lại DƯỚI app mới khi BEHIND-HOME hỏng)")
        val leave = SourceRoots.body(swap, "internal fun WorkspaceView.parkLeaving(")
        order(leave, "SlotParkPlan.leave(old, new, next, i, profileSwitch) != SlotParkPlan.Leave.PARK", "return",
            "hostAt(i)?.takeIf { !it.isReleased } ?: return", "host.park(protect = SlotParkPlan.shown(next))")
        forbidden.forEach { assertFalse(it in leave, "'$it' trong parkLeaving") }
    }

    @Test
    fun `C - mat ve o moi nhan lai man ao do TRUOC khi tao man ao moi, 0 lenh`() {
        val changed = SourceRoots.body(host, "override fun surfaceChanged(")
        order(changed, "if (released) return",
            "val c = ParkedApps.claim(surface, pkg, owner, slot, w, ht)",
            "if (c != null) { unpark(c.parked); if (c.resize) resize(w, ht); return }", "dm.createVirtualDisplay(")
        val unpark = SourceRoots.body(host, "private fun unpark(")
        order(unpark, "vd = p.lease.vd", "launched = true",
            "SlotLiveProbe.watch(probeKey, p.pkg, p.lease.displayId, sh, onMissing = ::reopen) { onAppClosed() }")
        assertFalse("attach(" in unpark, "gắn nằm trong ParkedApps.claim, không ở unpark")
        forbidden.forEach { assertFalse(it in unpark, "'$it' trong unpark — nhận lại không được mở lại / đổi cỡ app") }
        // Màn ảo đỗ mà app đã RỜI nó (mở toàn màn ở display 0 · chết lúc đỗ) ⇒ không để khung đen vĩnh viễn: nhịp đo chung
        // (một `am stack list` cho mọi ô, đọc-chỉ) kết luận "trống" ⇒ `reopen` = mở như đường thường. Đó là chỗ DUY NHẤT
        // đường nhận lại có thể dẫn tới `force-stop` + `am start`, và chỉ khi app KHÔNG còn trên màn ảo.
        assertEquals(1, Regex("""::reopen""").findAll(unpark).count())
        // 2.91 · F2 — ĐỔI GHIM có lý do: bỏ ghim cỡ (nguồn viền đen [ĐO máy ảo QA 05/10]); màn ảo nhận lại đổi cỡ qua đường DUY NHẤT.
        val resize = SourceRoots.body(host, "fun resize(")
        order(resize, "if (w <= 0 || h <= 0) return", "if (w == dispW && h == dispH) return",
            "val keep = SlotParkPlan.resizeDensity(keepDpi, dispDpi)",
            "VdAppHostResize.apply(slot, v, dispW, dispH, w, h) { keep ?: slotDensity(w, h) }?.let { dispDpi = it }", "dispW = w; dispH = h")
        // 2.91 · F2b — phần THI HÀNH tách nguyên văn sang `VdAppHostResize` (trần 500 dòng): dòng `[slot-resize]` TRƯỚC, mật độ + đổi
        // cỡ SAU, cùng một rào lỗi — đúng thứ tự trước khi tách.
        order(SourceRoots.body(code("VdAppHostResize.kt"), "fun apply("), "Log.i(TAG, \"[slot-resize] ô \$slot",
            "runCatching { dpi().also { v.resize(w, h, it) } }", ".onFailure { Log.w(TAG, \"[slot-resize] ô \$slot hỏng\", it) }")
        assertFalse(".resize(w, h, " in host, "VdAppHost không tự gọi VirtualDisplay.resize — một đường qua VdAppHostResize")
        assertFalse("pinned" in host, "không còn cờ ghim cỡ màn ảo nhận lại")
    }

    @Test
    fun `C2 - nhip do chung, man ao nhan lai TRONG thi goi onMissing, doc hong thi khong ket luan`() {
        val probe = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SlotLiveProbe.kt")
        assertTrue("val liveness = SlotLiveness(adopted = onMissing != null)" in probe, "chỉ màn ảo nhận lại mới bỏ luật 'chưa thấy sống'")
        val sweep = SourceRoots.body(probe, "private fun sweep()")
        assertEquals(1, Regex("shell\\(\"am stack list\"\\)").findAll(sweep).count(), "không thêm lệnh nào — vẫn MỘT lệnh mỗi nhịp")
        order(sweep, "if (out.isNullOrBlank()) return@execute", "val readable = \"Stack id=\" in out",
            "if (sub.onMissing != null && !readable) return@forEach", "if (sub.liveness.observe(alive))",
            "val missing = sub.onMissing?.takeIf { sub.liveness.missing }", "if (missing != null) missing() else sub.onDead()")
        val reopen = SourceRoots.body(host, "private fun reopen()")
        assertTrue("maybeLaunch()" in reopen, "màn ảo trống ⇒ mở app như đường thường vào CHÍNH màn ảo đó")
        // PARK-2b: màn ảo nhận lại được đo NGAY (không chờ nhịp đang lùi tới 15 s), vẫn một chuỗi nhịp.
        order(SourceRoots.body(probe, "fun watch("), "subs.add(Sub(key, pkg, displayId, shell, onDead, onMissing))", "start()",
            "if (onMissing != null) kick()")
        order(SourceRoots.body(probe, "private fun kick()"), "unchangedSweeps = 0", "if (paused) return", "ui.removeCallbacks(tick)",
            "ticking = true", "ui.post(tick)")
    }

    @Test
    fun `C3 - mo toan man app dang do phai neu display 0, roi QUEN ban do (PARK-2a)`() {
        val opener = code("AppOpener.kt")
        // Review 2.89 Pass 3 · whole-r2-1 — ĐỔI GHIM có lý do: nêu display 0 + quên bản đỗ đi qua MỘT cửa chung với giọng nói / dẫn
        // theo lịch (`ParkedApps.launchOptions` / `launched`).
        order(SourceRoots.body(opener, "fun openByIntent("), "val base = { ActivityOptions.makeBasic().setLaunchBounds(null) }",
            "val parked = ParkedApps.launchOptions(pkg, base)", "val opts = parked ?: base()",
            "activity.startActivity(intent, opts.toBundle())", "if (opened && parked != null) ParkedApps.launched(pkg)")
        assertTrue("fun has(pkg: String): Boolean = ledger.has(pkg)" in parked)
        assertTrue("if (pkg != null && has(pkg)) base().setLaunchDisplayId(Display.DEFAULT_DISPLAY) else null" in
            SourceRoots.body(parked, "fun launchOptions("))
        order(SourceRoots.body(parked, "fun launched(pkg: String)"), "Looper.myLooper() == Looper.getMainLooper()", "forget(pkg)",
            "main.post { forget(pkg) }")
        // Lấy ra NGAY (lượt mở vào ô kế đi đường thường), nhả màn ảo SAU một biên an toàn (cờ 256 kết thúc activity còn trên đó).
        order(SourceRoots.body(parked, "fun forget("), "val p = ledger.take(pkg) ?: return",
            "main.postDelayed({ drop(p, \"fullscreen\") }, FORGET_DELAY_MS)")
        assertTrue("private const val FORGET_DELAY_MS = 1_500L" in parked)
    }

    /**
     * 2.91 · F2 — ĐỔI GHIM có lý do (thay PARK-1 ghim cỡ + khung viền): QUYẾT ở `:core` (`SlotParkPlan.claim`, bảng ở `SlotParkTest`);
     * ở đây canh THI HÀNH — gắn (đổi mặt vẽ) TRƯỚC, host đổi cỡ SAU (KDoc `ParkedApps.claim`, [ĐO nguồn A10]); không còn ghim/khung.
     * Thử ĐỎ: bỏ `if (c.resize) resize(w, ht)` ⇒ lại viền đen.
     */
    @Test
    fun `F2 - nhan lai khac co - gan roi doi co man ao theo o, khong ghim, khong khung`() {
        val claim = SourceRoots.body(parked, "fun claim(")
        // Review 2.91 Pass 1 [P3] — ĐỔI GHIM có lý do: lấy ra TRƯỚC, quyết theo CHÍNH bản đã lấy (`adoptHidden` ghi sổ từ luồng
        // `kachi-behind`; xem-rồi-lấy là hai lượt khoá ⇒ bản cùng gói bị thay giữa chừng thì bước tính theo cỡ bản cũ).
        order(claim, "val taken = pkg?.let { take(it) } ?: return null", "SlotParkPlan.claim(taken.width, taken.height, w, h)",
            "if (!attach(taken, sv.holder.surface, owner, slot)) return null",
            "val resize = step == SlotParkPlan.ClaimStep.ATTACH_RESIZE", "return Claim(taken, resize)")
        assertFalse("peek" in claim, "nhận lại không xem-rồi-mới-lấy (hai lượt khoá): $claim")
        listOf("setFixedSize", "letterbox", "fun fit(", "fun unfit(", "fun place(", "Gravity", "FrameLayout").forEach {
            assertFalse(it in parked, "'$it' — 2.91 bỏ ghim cỡ + khung viền (nguồn viền đen)")
        }
        assertFalse(".resize(" in parked, "ParkedApps không tự đổi cỡ — host đổi qua `VdAppHost.resize` (một đường, có đòn bẩy mật độ)")
        assertTrue("private fun take(" in parked && "private fun attach(" in parked)
        listOf("ParkedApps.take", "ParkedApps.attach").forEach { assertFalse(it in host, "'$it' ngoài claim") }
        assertTrue("SlotTouchMapper.toDisplay((e.x - surface.left).toInt(), (e.y - surface.top).toInt(), surface.width, surface.height, dispW, dispH)" in host,
            "chạm map theo mặt vẽ ↔ cỡ màn ảo (dispW/dispH cập nhật trong resize)")
    }

    /**
     * 2.91 · F2b — khoá quyết định điều phối 06/10 (nhạc của app đỗ chạy tiếp): đường NHẬN LẠI không bao giờ đổi MẬT ĐỘ màn ảo; đường
     * golden (mở mới) giữ y nguyên mật độ theo ô. [ĐO nguồn r47 `ActivityRecord.java:3377`] đổi mật độ luôn dựng lại activity không
     * khai `density`. Luật thuần + bảng ở `:core` (`SlotParkTest › F2b`); ở đây canh dây nối. Thử ĐỎ: bỏ `keepDpi = true` ở `unpark`
     * (lượt đổi cỡ sau gắn lại tính mật độ theo ô), hoặc cho `slotDensity(` vào `unpark`.
     */
    @Test
    fun `F2b - nhan lai khong bao gio doi mat do, duong golden giu mat do theo o`() {
        val unpark = SourceRoots.body(host, "private fun unpark(")
        order(unpark, "dispW = p.width; dispH = p.height", "dispDpi = p.densityDpi; keepDpi = true")
        assertFalse("slotDensity(" in unpark, "nhận lại không tính mật độ theo ô")
        assertEquals(1, Regex("""\bkeepDpi = true""").findAll(host).count(), "chỉ đường nhận lại bật giữ mật độ")
        assertTrue("private var dispDpi = 0; private var keepDpi = false" in host, "host mới (mở mới) mặc định KHÔNG giữ")
        // Nhận lại khác cỡ: gắn → unpark (bật giữ) → resize — resize gặp `keepDpi` nên đặt lại ĐÚNG mật độ màn ảo đỗ.
        order(SourceRoots.body(host, "override fun surfaceChanged("), "val c = ParkedApps.claim(surface, pkg, owner, slot, w, ht)",
            "if (c != null) { unpark(c.parked); if (c.resize) resize(w, ht); return }")
        val resize = SourceRoots.body(host, "fun resize(")
        order(resize, "val keep = SlotParkPlan.resizeDensity(keepDpi, dispDpi)",
            "if (keep != null) Log.i(TAG, \"[slot-density] keep \$keep on unpark", "{ keep ?: slotDensity(w, h) }")
        // Đường golden y nguyên: tạo màn ảo bằng mật độ theo ô, chỉ GHI LẠI nó.
        order(SourceRoots.body(host, "override fun surfaceChanged("), "val dpi = slotDensity(w, ht)",
            "dm.createVirtualDisplay(name, w, ht, dpi, h.surface, 8 or 256)", "dispW = w; dispH = ht; vdName = name; dispDpi = dpi")
        // Mật độ của màn ảo đỗ đi cùng nó qua ô 7 (đỗ từ ô · màn ảo ẩn của chuyến).
        assertTrue("val densityDpi: Int," in parked, "bản đỗ mang mật độ")
        assertTrue("ParkedApps.adoptHidden(pkg, n, l, width, height, densityDpi, s)" in
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/behind/StagingDisplay.kt"), "màn ảo ẩn của chuyến trao cả mật độ")
    }

    @Test
    fun `do - host tha moi thu NHU da nha nhung khong force-stop, khong nha man ao`() {
        val park = SourceRoots.body(host, "fun park(")
        order(park, "SlotParkPlan.parkable(released, launched, true, p, dead, full.isDetached, SlotLiveProbe.watching(probeKey))",
            "ParkedApps.park(p, name, VdLease(v, id, unregisterVd), dispW, dispH, dispDpi, protect)", "SlotLiveProbe.unwatch(probeKey)",
            "released = true; vd = null; vdDisplayId = null; pkg = null; launched = false", "surface.visibility = INVISIBLE")
        forbidden.forEach { assertFalse(it in park, "'$it' trong park") }
        assertFalse("SlotVdOwner.release(" in park, "đỗ KHÔNG nhả màn ảo — DESTROY_CONTENT_ON_REMOVAL sẽ kết thúc app")
        // `release()` sau khi đỗ phải là no-op (released = true) ⇒ không force-stop app vừa đỗ.
        assertTrue(SourceRoots.body(host, "fun release()").contains("if (released) return"))
        assertTrue("dispW = w; dispH = ht; vdName = name" in host, "tên màn ảo (khoá đổi chủ) ghi ngay lúc tạo")
    }

    @Test
    fun `so o 7 - doi chu khong nha, nha man ao truoc khi dong be mat an, khong lenh shell`() {
        // A2 · 2.89 — ĐỔI GHIM có lý do (DRY): ghi sổ của `park` và `adoptHidden` (màn ảo ẩn của chuyến lên xe) đi MỘT lối
        // `enter`; thứ tự giữ: đổi mặt vẽ → chuyển chủ (không nhả) → sổ → nhả bản bị đẩy ra.
        order(SourceRoots.body(parked, "fun park("), "OffscreenSink.open(width, height, \"kachi-park\")", "lease.vd.surface = sink.surface",
            "enter(pkg, name, lease, width, height, densityDpi, sink, protect, ")
        order(SourceRoots.body(parked, "private fun enter("),
            "SlotVdOwner.move(OWNER, key, name, lease)", "ledger.park(pkg, Parked(pkg, name, lease, width, height, densityDpi, key, sink), protect)",
            "drop(it.handle, it.why.name)")
        val adopt = SourceRoots.body(parked, "internal fun adoptHidden(")
        assertTrue("enter(pkg, name, lease, width, height, densityDpi, sink, emptySet()" in adopt && "surface" !in adopt, "nhận màn ảo đã ẩn: không đổi mặt vẽ")
        assertTrue("densityDpi <= 0) return false" in adopt, "F2b: màn ảo ẩn không rõ mật độ thì không nhận vào ô 7 (không giữ được)")
        order(SourceRoots.body(parked, "fun attach("), "p.lease.vd.surface = s", "drop(p, \"attach-failed\")", "return false",
            "p.sink.close()", "SlotVdOwner.move(owner, slot, p.name, p.lease)")
        order(SourceRoots.body(parked, "private fun drop("), "SlotVdOwner.release(OWNER, p.key)", "p.sink.close()")
        forbidden.forEach { assertFalse(it in parked, "'$it' trong ParkedApps") }
        assertFalse(".resize(" in parked || "createVirtualDisplay" in parked, "ô 7 không tạo / tự đổi cỡ màn ảo nào (đổi cỡ: host)")
        assertTrue("private val keys = AtomicInteger(-1_000_000)" in parked, "dải khoá riêng — không trùng ô thật / dàn dựng ẩn")
        val move = SourceRoots.body(code("SlotVdOwner.kt"), "fun move(")
        assertTrue("ledger.adopt(owner, slot, name, lease)" in move, "đổi chủ = adopt cùng tên (chỉ đổi khoá, không free)")
        assertFalse("lease.free()" in move)
    }

    @Test
    fun `mot be mat an cho hai cho dung (DRY)`() {
        val staging = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/behind/StagingDisplay.kt")
        assertTrue("OffscreenSink.open(m.widthPixels, m.heightPixels, \"kachi-stage\")" in staging)
        assertFalse("ImageReader" in staging, "StagingDisplay không còn tự dựng ImageReader")
        assertEquals(1, Regex("""ImageReader\.newInstance\(""").findAll(parked).count(), "đúng một chỗ dựng ImageReader")
        order(SourceRoots.body(parked, "fun close()"), "reader.close()", "thread.quitSafely()")
    }

    @Test
    fun `duong cu giu bien dich nhung khong con cho goi o A va B`() {
        assertTrue("fun swapApp(" in host, "VdAppHost.swapApp giữ (bản sau quyết)")
        assertTrue("internal fun WorkspaceView.swapInPlace(" in swap)
        assertTrue("private fun backgroundCovered(" in actions && "toBack(index, stage.vd, pkg) { r ->" in actions,
            "chuỗi lớp che + BEHIND-HOME giữ nguyên thân")
        assertEquals(0, Regex("""\bbackgroundCovered\(index\)""").findAll(actions).count(), "không chỗ gọi")
        assertEquals(0, Regex("""\bswapInPlace\(""").findAll(workspace).count())
    }

    /**
     * Review 2.89 Pass 3 · whole-r2-5 — ĐỔI GHIM có lý do: khoá BẤT BIẾN (không lùi dưới bản thử 190 / 2.89 đã báo owner — CLAUDE.md
     * §9), không khoá con số nhất thời: bước REL-2.89 bump ≥ 191 / "2.89" không được làm đỏ `:app`.
     */
    @Test
    fun `phien ban khong lui duoi ban thu 190, 2_89`() {
        val gradle = SourceRoots.text("build.gradle.kts")
        val code = Regex("""versionCode = (\d+)""").find(gradle)?.groupValues?.get(1)?.toInt()
        val name = Regex("""versionName = "([^"]+)"""").find(gradle)?.groupValues?.get(1)
        assertTrue(code != null && code >= 190, "versionCode = $code")
        val num = com.byd.clusternav.UpdateChecker.numericPrefix(name ?: "")
        val cmp = num.zip(listOf(2, 89)).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a.compareTo(b) } ?: num.size.compareTo(2)
        assertTrue(cmp >= 0, "versionName = $name (phần số $num) không được dưới 2.89")
    }

    /**
     * Review 2.89 Pass 3 · whole-r2-1 — giọng nói (`VoiceWiring` › `VoiceTargetDispatch`) và dẫn theo lịch (`ScheduledNavApplier`) mở
     * app qua `VoiceAppIntents.fire`: app đang ĐỖ phải nêu display 0 qua CÙNG cửa với `AppOpener` (không thì dẫn đường bắt đầu trên
     * màn ảo ẩn), rồi quên bản đỗ. Thử ĐỎ: trả `fire` về `ctx.startActivity(intent)` trơn.
     */
    @Test
    fun `giong noi va dan theo lich mo app dang do ra display 0 qua cung mot cua`() {
        val voice = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceAppIntents.kt")
        val fire = SourceRoots.body(voice, "private fun fire(ctx: Context, intent: Intent?): Boolean")
        order(fire, "val parked = ParkedApps.launchOptions(pkg)", "if (parked != null) ctx.startActivity(intent, parked.toBundle()) else ctx.startActivity(intent)",
            "if (ok && parked != null && pkg != null) ParkedApps.launched(pkg)")
        assertFalse("setLaunchDisplayId" in voice, "không chép luật nêu display — một cửa ở ParkedApps")
        assertEquals(1, Regex("""setLaunchDisplayId\(""").findAll(parked).count(), "đúng một chỗ nêu display 0")
        assertFalse("setLaunchDisplayId" in code("AppOpener.kt"))
    }

    /**
     * Review 2.89 Pass 3 · whole-r2-2 — lượt dựng lại do ĐỔI HỒ SƠ (cùng bố cục ⇒ PerSlot) nhả app rời ô như 2.88, cùng kết cục với
     * đổi hồ sơ khác bố cục (RebuildAll). Luật thuần ở `SlotParkTest`. Thử ĐỎ: bỏ `profileSwitch = …` ở `KachiHomeRender`.
     */
    @Test
    fun `doi ho so nha app roi o nhu 2_88, khong do o 7`() {
        val render = SourceRoots.body(code("KachiHomeRender.kt"), "internal fun KachiHomeActivity.render(state: HomeUiState) {")
        assertTrue("profileSwitch = prev != null && prev.activeProfile != state.activeProfile)" in render, render)
        assertTrue("renderInternal(s, status, embedChanged = false, swap = swap, profileSwitch = profileSwitch)" in workspace)
    }
}
