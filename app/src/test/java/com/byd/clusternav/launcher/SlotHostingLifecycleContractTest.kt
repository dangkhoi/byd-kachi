package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `app trong o chet thi giau mat ve va NOI ra, cham la mo lai`() {
        val closed = SourceRoots.body(host, "private fun onAppClosed()")
        assertTrue("surface.visibility = GONE" in closed, "phải giấu mặt vẽ — nếu không, khung cuối đóng băng vẫn nằm đó")
        assertTrue("R.string.kachi_slot_app_closed" in closed, "phải nói ra bằng chuỗi tài nguyên (VI + EN), không viết cứng")
        assertTrue("setOnClickListener { reopen() }" in closed, "chạm phải mở lại được ngay trong ô")
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

    @Test
    fun `nhan app-da-dong co du hai ngon ngu`() {
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        assertTrue("\"kachi_slot_app_closed\"" in vi && "\"kachi_slot_app_closed\"" in en,
            "nhãn ô chết phải có ở CẢ values/ lẫn values-en/")
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
