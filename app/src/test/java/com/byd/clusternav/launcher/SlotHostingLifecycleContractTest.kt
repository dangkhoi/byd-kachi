package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ H2 — BÀI CANH VÒNG ĐỜI MÀN ẢO CỦA Ô + KÊNH IM LẶNG ══════════════════════════════════════════════════════
 *
 * Hai lỗi [ĐO] 2026-09-14 (`docs/diagnostics/waze-into-slot-research-2026-09-14.md` §5, đo lại trên
 * `emulator-5554` cùng ngày):
 *
 *  1. **Rò màn ảo** — `dumpsys display` có **4** thiết bị `kachi-slot-*` cho **2** ô App (2 cái cũ ở `state OFF`),
 *     cùng lúc `dumpsys activity activities` có **4** `KachiHomeActivity` (3 mang cờ `f` = đang kết thúc). Gốc:
 *     đường giải phóng DUY NHẤT là `View.onDetachedFromWindow`, mà một màn "đang kết thúc" giữ cây view bao lâu
 *     tuỳ hệ điều hành ⇒ màn ảo của nó sống chồng lên màn ảo của màn Kachi mới.
 *  2. **Khung đóng băng** — app trên màn ảo chết thì `SurfaceView` giữ khung hình cuối ⇒ ô trông còn sống.
 *
 * Bài này là **quét mã nguồn** (không dựng được View/VirtualDisplay trong JVM thuần; phần thuần đã có bộ đếm
 * riêng ở `:core` — [SlotVdLedgerTest], [SlotLivenessTest]). Mọi phép quét đi qua [SourceRoots.codeOf] để **bỏ
 * chú thích trước khi kiểm** — nếu không thì viết tên hàm vào một dòng chú thích là test xanh, tức test tự lừa mình.
 */
class SlotHostingLifecycleContractTest {

    private val host by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/VdAppHost.kt") }
    private val workspace by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt") }
    private val activity by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val probe by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SlotLiveProbe.kt") }

    // ══ (1) RÒ MÀN ẢO ═══════════════════════════════════════════════════════════════════════════════════════

    /**
     * **Luật quét toàn cây**: tệp nào gọi `createVirtualDisplay` thì CHÍNH tệp đó phải có đường giải phóng —
     * giao tay cầm cho [SlotVdOwner] (chủ theo ô) *và* một hàm `release()` gọi `SlotVdOwner.release`.
     *
     * Viết thành phép quét thay vì assert vào đúng `VdAppHost.kt`, vì bệnh cần chặn là **tệp thứ hai** mai này
     * tạo màn ảo rồi quên nhả — đúng hình dạng lỗi đã đo.
     */
    @Test
    fun `moi noi tao VirtualDisplay deu phai giao tay cam cho SlotVdOwner va co duong nha`() {
        val creators = kotlinSources().filter { "createVirtualDisplay" in code(it) }
        assertTrue(creators.isNotEmpty(), "không quét được tệp nào — đường dẫn source sai thì bài này là test giả")
        val offenders = creators.filterNot { f ->
            val src = code(f)
            "SlotVdOwner.adopt(" in src && "SlotVdOwner.release(" in src && "fun release()" in src
        }.map { it.fileName.toString() }
        assertEquals(
            emptyList<String>(), offenders,
            "tệp tạo VirtualDisplay mà không có cặp adopt/release ⇒ màn ảo sống lâu hơn ô (đã đo: 4 VD cho 2 ô)",
        )
    }

    @Test
    fun `VdAppHost nha man ao bang release idempotent — khong chi dua vao onDetachedFromWindow`() {
        val release = SourceRoots.body(host, "fun release()")
        assertTrue("if (released) return" in release, "gọi lần hai phải là no-op (WorkspaceView nhả rồi view mới tháo)")
        assertTrue("SlotVdOwner.release(owner, slot)" in release, "phải trả màn ảo về chủ sở hữu theo ô")
        assertTrue("SlotLiveProbe.unwatch(probeKey)" in release, "nhả ô thì thôi đo — không để nhịp đo mồ côi")
        val detached = SourceRoots.body(host, "override fun onDetachedFromWindow()")
        assertTrue("release()" in detached, "đường cũ (tháo view) vẫn phải là lưới an toàn")
    }

    @Test
    fun `host da nha thi KHONG duoc tao them man ao moi`() {
        val changed = SourceRoots.body(host, "override fun surfaceChanged(")
        assertTrue(
            "if (released) return" in changed,
            "host đã nhả mà mặt vẽ đổi cỡ lần nữa ⇒ tạo một `kachi-slot-*` không ai cầm",
        )
        assertTrue("SlotVdOwner.adopt(owner, slot, name, VdLease(created, id, unregisterVd))" in changed,
            "màn ảo vừa tạo phải được giao cho chủ theo ô NGAY (chủ mới nhận ô ⇒ màn ảo cũ của ô đó bị nhả)")
    }

    @Test
    fun `moi duong thay-dong-dung lai o deu nha man ao TRUOC khi thao view`() {
        val perSlot = SourceRoots.body(workspace, "is WorkspaceRenderPlan.PerSlot ->")
        assertTrue(
            perSlot.indexOf("releaseSlotHost(i)") in 0 until perSlot.indexOf("removeView(slotViews[i])"),
            "ô đổi nội dung: phải nhả màn ảo TRƯỚC removeView (removeView chỉ tháo khi cây view đang gắn cửa sổ)",
        )
        val rebuild = SourceRoots.body(workspace, "private fun rebuild()")
        assertTrue(
            rebuild.indexOf("releaseAppHosts()") in 0 until rebuild.indexOf("removeAllViews()"),
            "dựng lại tất cả (đổi bố cục / đổi hồ sơ) phải nhả màn ảo trước khi xoá sạch con",
        )
        val detached = SourceRoots.body(workspace, "override fun onDetachedFromWindow()")
        assertTrue("releaseAppHosts()" in detached, "workspace rời cửa sổ ⇒ nhả mọi màn ảo của cây này")
        val all = SourceRoots.body(workspace, "fun releaseAppHosts()")
        assertTrue("SlotVdOwner.releaseOwner(hostOwner)" in all,
            "phải quét nốt theo CHỦ: host đã rời cây view thì không ô nào trỏ tới nó nữa, nhưng màn ảo vẫn sống")
    }

    @Test
    fun `man chinh huy thi nha man ao tuong minh`() {
        val destroy = SourceRoots.body(activity, "override fun onDestroy()")
        assertTrue(
            "workspace.releaseAppHosts()" in destroy,
            "đo được: màn Kachi 'đang kết thúc' giữ view ⇒ màn ảo sống tiếp; onDestroy phải nhả tường minh",
        )
    }

    @Test
    fun `moi o co danh tinh rieng — khoa so huu la chu x o`() {
        assertTrue("private val hostOwner = " in workspace, "cây workspace phải có danh tính chủ sở hữu")
        assertTrue(
            "slot = index, owner = hostOwner" in workspace,
            "host phải biết mình giữ ô NÀO của chủ NÀO — nếu không, bất biến một-màn-ảo-mỗi-ô không thực thi được",
        )
    }

    // ══ (2) KHUNG ĐÓNG BĂNG — KÊNH IM LẶNG PHẢI NÓI ════════════════════════════════════════════════════════

    /**
     * ⚠ L6 (owner 03/10, ảnh xe: ô Google Maps đã đóng hiện icon to + *"App đã đóng — chạm để mở lại"*): *"Khi app bị tắt
     * thì trả về transparent luôn, không cần giữ icon và yêu cầu mở app như này nhé"*. Ghim CŨ (thẻ `kachi_slot_app_closed`
     * + `setOnClickListener { reopen() }`) ĐẢO có chủ ý: giấu mặt vẽ vẫn bắt buộc (khung đóng băng), nhưng "NÓI ra" nay là
     * BÁO LÊN màn chính ([onGone] → `SlotRevertPlan` `APP_DIED`: app LƯU ⇒ ô trong suốt · app đặt tạm ⇒ ô về nội dung LƯU).
     * Mở lại vẫn có đường: lối tắt *Ô n* (R-SC2 / đặt tạm vào khung trống) · ⇄ · khởi động lại.
     */
    @Test
    fun `app trong o chet thi giau mat ve va BAO len luat hoan o, khong the cham-de-mo-lai`() {
        val closed = SourceRoots.body(host, "private fun onAppClosed()")
        assertTrue("surface.visibility = GONE" in closed, "phải giấu mặt vẽ — nếu không, khung cuối đóng băng vẫn nằm đó")
        assertTrue("pkg?.let(onGone)" in closed, "phải báo lên màn chính (luật hoàn ô) — thiếu là ô kẹt khung trống không ai dọn")
        assertTrue("if (released || dead) return" in closed && "dead = true" in closed, "báo MỘT lần")
        assertFalse("kachi_slot_app_closed" in closed || "TextView" in closed || "setOnClickListener" in closed,
            "owner 03/10: không còn thẻ icon + 'chạm để mở lại'")
        assertFalse("kachi_slot_app_closed" in host, "thẻ cũ gỡ hẳn")
        val reopen = SourceRoots.body(host, "private fun reopen()")
        assertTrue("surface.visibility = VISIBLE" in reopen && "maybeLaunch()" in reopen, "mở lại phải trả mặt vẽ + mở app")
        assertTrue("launched = false" in reopen, "không hạ cờ đã-mở thì maybeLaunch() trả về ngay ⇒ nút chết")
    }

    @Test
    fun `nhip do thua ca hai chieu — mot lenh cho moi o, khong poll day, khong ket luan som`() {
        assertTrue("SlotLiveness.PROBE_PERIOD_MS" in probe, "chu kỳ đo phải lấy từ luật thuần ở :core (5 s)")
        val sweep = SourceRoots.body(probe, "private fun sweep()")
        assertEquals(1, Regex("shell\\(\"am stack list\"\\)").findAll(sweep).count(),
            "đúng MỘT lệnh `am stack list` cho mọi ô mỗi nhịp (4 ô tự hẹn giờ riêng = 4 lệnh cho cùng một dữ liệu)")
        assertTrue("if (out.isNullOrBlank()) return@execute" in sweep,
            "đọc không được ⇒ bỏ nhịp, KHÔNG kết luận app đã chết (shell hỏng không phải cái chết của app)")
        val tick = SourceRoots.body(probe, "override fun run()")
        assertTrue("if (subs.isEmpty()) { ticking = false; return }" in tick,
            "không ô nào theo dõi ⇒ tắt hẳn nhịp (màn chính chỉ có widget thì không đốt một lệnh nào)")
    }

    /**
     * [SOÁT Pass H2 · P2] **Màn khuất ⇒ ngưng nhịp đo.**
     *
     * Mọi lệnh shell của app xếp hàng trên MỘT chủ (`ShellTransport`), nên một nhịp đo mỗi 5 s là một chỗ trong
     * cùng hàng đợi với lệnh đặt cửa sổ. Người dùng mở một app toàn màn thì màn Kachi **không chết** (view còn
     * gắn, ô còn đăng ký) ⇒ không ngưng là đốt một lượt dadb mỗi 5 giây suốt chuyến đi.
     *
     * Và phải đếm số màn đang hiện, không dùng cờ trần: thứ tự vòng đời là `A.onPause → B.onStart → A.onStop`,
     * nên một cờ sẽ bị màn CŨ tắt sau khi màn MỚI đã bật — nhịp đo đứng im vĩnh viễn.
     */
    @Test
    fun `man khuat thi ngung nhip do, va dem theo so man dang hien`() {
        val tick = SourceRoots.body(probe, "override fun run()")
        assertTrue("if (paused) { ticking = false; return }" in tick, "màn khuất ⇒ ticker phải dừng hẳn")
        assertTrue("visible.decrementAndGet()" in SourceRoots.body(probe, "fun pause()"),
            "phải ĐẾM màn đang hiện: cờ trần bị `A.onStop` tắt sau `B.onStart` ⇒ nhịp đo chết vĩnh viễn")
        assertTrue("SlotLiveProbe.pause()" in SourceRoots.body(activity, "override fun onStop()"),
            "màn chính khuất phải ngưng nhịp đo")
        assertTrue("SlotLiveProbe.resume()" in SourceRoots.body(activity, "override fun onStart()"),
            "màn chính hiện lại phải đo tiếp — ngưng mà không có đường bật lại là tắt hẳn tính năng")
    }

    /** L6 — nhãn ô chết đã GỠ (owner 03/10) ⇒ khoá ở CẢ năm tệp phải đi cùng (khoá mồ côi = bản dịch của câu đã bỏ). */
    @Test
    fun `nhan app-da-dong da go o ca nam tieng`() {
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { f ->
            assertFalse("\"kachi_slot_app_closed\"" in SourceRoots.text("src/main/res/$f/strings_kachi.xml"), "$f còn nhãn ô chết")
        }
    }

    // ══ (3) PROFILE-SWITCH-SLOTS (2026-10-01) — lỗi A: ô đen khi đổi hồ sơ khác cạnh thanh nút ═══════════════════

    /**
     * R-A2 — **nhả trước khi mở thì KHÔNG `am force-stop`.** [ĐO máy ảo 2026-10-01] lượt A→B: `Force stopping
     * com.google.android.youtube` dù YouTube **chưa từng** được mở trong ô (ô bị nhả trước khi có mặt vẽ). `launched` bật
     * ở `maybeLaunch` ngay trước luồng mở app ⇒ đọc nó TRƯỚC dòng `launched = false` cuối `release()` là đủ biết.
     */
    @Test
    fun `nha truoc khi mo app thi KHONG force-stop app cua nguoi dung`() {
        val release = SourceRoots.body(host, "fun release()")
        val read = release.indexOf("val wasLaunched = launched")
        val reset = release.indexOf("launched = false")
        assertTrue(read >= 0, "release() phải chụp cờ đã-mở: $release")
        assertTrue(reset > read, "phải đọc cờ TRƯỚC khi hạ nó — đọc sau thì luôn là false (hoặc luôn force-stop)")
        assertEquals(1, Regex("""am force-stop""").findAll(release).count(), "đúng một lệnh dừng app trong release()")
        assertTrue(
            Regex("""if \([^)\n]*\bwasLaunched\b[^)\n]*\)[^\n]*am force-stop""").containsMatchIn(release),
            "lệnh `am force-stop` phải nằm sau điều kiện có `wasLaunched` (R-A2): $release",
        )
        val launch = SourceRoots.body(host, "private fun maybeLaunch()")
        assertTrue(launch.indexOf("launched = true") in 0 until launch.indexOf("Thread {"),
            "`launched` phải bật TRƯỚC luồng mở app — nếu không, wasLaunched = false mà app vẫn được mở")
    }

    /**
     * R-A4 — **lưới: tháo-gắn lại cây ô thì DỰNG LẠI ô, không đen câm.** Gốc lỗi A đã sửa ở `DockAreaLayout`
     * (`DockAreaLayoutContractTest`); lưới này cho mọi đường tháo-gắn chưa biết. Dựng lại đi qua nhánh SẴN CÓ
     * (`renderInternal(…, embedChanged = true)` — P-bug2), không hồi sinh host đã nhả (H2).
     */
    @Test
    fun `gan lai cua so ma o App da bi nha thi dung lai o — khong hoi sinh host`() {
        val attached = SourceRoots.body(workspace, "override fun onAttachedToWindow()")
        assertTrue("super.onAttachedToWindow()" in attached, "phải gọi lớp cha")
        assertTrue("post {" in attached, "dựng lại SAU lượt gắn (post), không sửa danh sách con giữa lúc đang duyệt nó")
        assertTrue("isAttachedToWindow && SlotHostHeal.anyReleased(slotViews)" in attached,
            "chỉ dựng lại khi CÒN gắn và thật sự có ô cầm host đã nhả — không đốt một lượt dựng lại vô cớ")
        assertTrue("renderInternal(displayed, displayedStatus, embedChanged = true)" in attached,
            "đi qua đường dựng-lại-ô-App sẵn có, không viết đường thứ hai")
        val heal = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SlotHostHeal.kt")
        assertTrue("as? VdAppHost)?.isReleased == true" in heal, "phải hỏi đúng host của ô")
        assertTrue("val isReleased: Boolean get() = released" in host, "cờ đọc-chỉ phản chiếu đúng `released`")
        assertFalse(Regex("""(?m)^\s*released\s*=\s*false""").containsMatchIn(host),
            "host đã nhả KHÔNG được hồi sinh (H2: màn ảo của ô có thể đã về tay chủ khác)")
    }

    /** Cả cây `:app`: bệnh cần chặn là **tệp thứ hai** mai này tạo màn ảo rồi quên nhả. */
    private fun kotlinSources(): List<Path> =
        Files.walk(SourceRoots.path("src/main/java/com/byd/clusternav")).use { s ->
            s.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.sorted().toList()
        }

    /** MÃ đã bỏ chú thích — KDoc dự án viết tiếng Việt và nhắc chính tên hàm đang canh, quét thô sẽ báo sai. */
    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }
}
