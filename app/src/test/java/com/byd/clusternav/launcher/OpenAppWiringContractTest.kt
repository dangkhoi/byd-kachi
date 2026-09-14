package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá DÂY NỐI của gói 1 ("Mở app cho đúng") — những bất biến mà test đơn vị không chạm tới được vì chúng nằm ở
 * chỗ ghép giữa Activity và View Android (dự án không dùng Robolectric).
 *
 * Ba nhóm:
 *  - **P-bug2**: kênh nhúng phải được gắn NGUYÊN KHỐI (không gán rời từng field, không gọi `render` trần ở nhánh
 *    dò-kênh-thành-công — chính chỗ đã sinh ra lỗi app-không-hiện-lúc-mở).
 *  - **U3**: có đường mở app TOÀN MÀN và nó KHÔNG ghi vào trạng thái ô.
 *  - **U2**: đường đưa app đang chạy về ô dùng đặt-lại-khung, còn đường đặt-mới giữ nguyên; `reflow` KHÔNG bị đổi
 *    (nó cố ý mở lại để nâng cửa sổ lên trước launcher — đổi mù sẽ làm app tụt sau launcher).
 */
class OpenAppWiringContractTest {

    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }

    /**
     * F4 (2026-09-14): khối "nối kênh sau khi dò xanh" đã **dời** khỏi [KachiHomeActivity] sang
     * `KachiHomeWiring.bringUpShellChannel` (trần 500 dòng) — cùng các bước, cùng thứ tự, chỉ khác chỗ đứng.
     * Bài canh đi theo mã, không đi theo tệp cũ.
     */
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }

    /**
     * Glue intent theo-ô — **đã dời** khỏi màn chính sang [KachiHomeSlots] ở soát Pass 2 (2026-09-14) để màn chính
     * về dưới trần 500 dòng (CLAUDE.md §4.1). Chỉ đổi CHỖ KHAI: tính chất mà hai bài dưới canh (đường API đi trước
     * đường shell · mở toàn màn không chạm ô) giữ nguyên từng dòng, nên bài canh chỉ đổi tệp nó đọc.
     */
    private val slots by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeSlots.kt") }
    private val view by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt") }
    private val windows by lazy { code("src/main/java/com/byd/clusternav/launcher/LauncherWindows.kt") }
    private val drawer by lazy { code("src/main/java/com/byd/clusternav/launcher/DrawerController.kt") }
    private val opener by lazy { code("src/main/java/com/byd/clusternav/launcher/AppOpener.kt") }
    private val strip by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt") }

    /**
     * Đọc source rồi **bỏ mọi chú thích** trước khi quét: test này canh **CODE**, không canh văn xuôi.
     * Hai lý do: (a) câu giải thích thường nhắc chính tên hàm đang bị cấm gọi ⇒ quét thô sẽ báo sai;
     * (b) chặn kiểu "đạt test" bằng cách viết token vào comment thay vì nối dây thật.
     * (Hạn chế đã biết: chuỗi ký tự chứa `//` cũng bị cắt — các file ở đây không có.)
     */
    private fun code(relative: String): String = SourceRoots.text(relative)
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    // ── P-bug2 ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `WorkspaceView phoi ham gan kenh nguyen khoi va KHONG con setter cong khai cho tung field`() {
        assertTrue(view.contains("fun applyEmbedSeam("), "phải có applyEmbedSeam")
        // Nếu 4 field này công khai lại thì bên ngoài có thể gán rời → sai thứ tự mà không có gì báo lỗi.
        listOf("shell", "registerVd", "unregisterVd", "inputClient").forEach { f ->
            assertTrue(view.contains("private var $f"), "$f phải private")
            assertFalse(Regex("""(?m)^\s{4}var\s+$f\b""").containsMatchIn(view), "$f KHÔNG được công khai")
        }
    }

    @Test
    fun `applyEmbedSeam gan DU 4 thu TRUOC khi dung lai o`() {
        val body = SourceRoots.body(view, "fun applyEmbedSeam(")
        val fields = listOf("this.registerVd", "this.unregisterVd", "this.inputClient", "this.shell")
        fields.forEach { assertTrue(body.contains(it), "applyEmbedSeam phải gán $it") }
        val renderAt = body.indexOf("renderInternal(")
        assertTrue(renderAt > 0, "phải gọi renderInternal")
        fields.forEach { assertTrue(body.indexOf(it) < renderAt, "$it phải được gán TRƯỚC renderInternal") }
        assertTrue(body.contains("embedChanged = !had"), "phải khai báo năng lực nhúng vừa đổi")
    }

    @Test
    fun `nhanh do-kenh-thanh-cong goi applyEmbedSeam va KHONG goi render tran`() {
        val block = SourceRoots.body(wiring, "internal fun Activity.bringUpShellChannel(")
        assertFalse(activity.contains("dadb.probe()"), "màn chính không còn tự dò — lượt dò đầu do [ShellChannelGate] hẹn (F4)")
        assertTrue(block.contains("workspace.applyEmbedSeam("), "phải gọi applyEmbedSeam")
        assertFalse(
            block.contains("workspace.render("),
            "KHÔNG được gọi workspace.render(...) ở đây — đó chính là lỗi P-bug2 (render diff theo nội dung ⇒ bỏ qua ô App)",
        )
    }

    @Test
    fun `render cua WorkspaceView di qua bo quyet dinh THUAN o core`() {
        assertTrue(view.contains("WorkspaceRenderPlanner.decide("))
        assertFalse(view.contains("private fun sameContent("), "luật cũ phải bị gỡ khỏi view (đã chuyển sang :core)")
    }

    // ── U3 ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `duong mo toan man dung khung null va KHONG dat che do cua so nho`() {
        assertTrue(opener.contains("setLaunchBounds(null)"), "phải đặt khung null (= toàn màn theo tài liệu Android)")
        assertFalse(opener.contains("setLaunchWindowingMode"), "KHÔNG được đặt chế độ freeform ở đường mở-thường")
        assertTrue(opener.contains("FreeformLaunch.fullscreenCmd("), "vẫn giữ lưới an toàn bằng công thức shell đã proven")
    }

    @Test
    fun `mo toan man KHONG ghi trang thai o va KHONG ghi so vi tri o`() {
        val fn = SourceRoots.body(slots, "fun openAppFullscreen(")
        assertTrue(fn.contains("appOpener.openByIntent("), "phải gọi đường API")
        assertTrue(fn.contains("touchRecentApp("), "phải ghi nhận app gần đây")
        listOf("viewModel.assignApp", "viewModel.setPreset", ".place(", "windows.placeApp").forEach {
            assertFalse(fn.contains(it), "mở toàn màn KHÔNG được gọi $it (không gắn ô, không đổi bố cục)")
        }
    }

    @Test
    fun `duong API di TRUOC duong shell (theo so do), va shell chay tren thread NEN`() {
        val fn = SourceRoots.body(slots, "fun openAppFullscreen(")
        val intentAt = fn.indexOf("appOpener.openByIntent(")
        val shellAt = fn.indexOf("appOpener.openByShell(")
        assertTrue(intentAt > 0 && shellAt > 0, "phải có cả hai đường")
        assertTrue(
            intentAt < shellAt,
            "đường API phải đi TRƯỚC — [ĐO] nó tốt bằng-hoặc-hơn đường shell ở cả app sạch lẫn app từng nằm trong ô",
        )
        assertTrue(
            fn.contains("submitBg { appOpener.openByShell("),
            "đường shell phải đi qua cửa nền submitBg (dadb chặn — không được chạy trên thread chính)",
        )
    }

    @Test
    fun `co loi vao Ung dung tren thanh tren, noi toi che do mo-thuong cua ngan keo`() {
        assertTrue(strip.contains("onOpenAppList"))
        assertTrue(strip.contains("R.string.kachi_pill_apps"), "thanh trên phải có nút Ứng dụng")
        assertTrue(activity.contains("onOpenAppList = { drawerController.openAppList() }"))
        assertTrue(drawer.contains("fun openAppList()"), "ngăn kéo phải có chế độ mở-thường")
        assertTrue(drawer.contains("AppDrawer.Mode.OPEN_APP"))
        assertTrue(drawer.contains("recentApps = recentApps()"), "chế độ mở-thường phải nhận danh sách gần đây")
    }

    // ── U2 ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `dua app dang chay ve o thi DAT LAI KHUNG, dat app moi thi giu duong cu`() {
        val fn = SourceRoots.body(windows, "fun placeApp(")
        assertTrue(fn.contains("launcher.moveToSlot("), "đường không-làm-mới phải dùng moveToSlot")
        assertTrue(fn.contains("am force-stop"), "đường đặt-mới vẫn dừng hẳn app trước")
        assertTrue(fn.contains("launcher.openInSlot("), "đường đặt-mới vẫn mở vào ô")
    }

    @Test
    fun `reflow KHONG bi doi sang dat-lai-khung — no can mo lai de nang cua so len truoc launcher`() {
        val fn = SourceRoots.body(windows, "fun reflow()")
        assertTrue(fn.contains("launcher.openInSlot("), "reflow vẫn dùng openInSlot")
        assertFalse(fn.contains("moveToSlot"), "reflow KHÔNG được dùng moveToSlot (mất thứ tự lớp)")
    }

    // ── Bản vá senior review Pass 1 (2026-09-10) ─────────────────────────────────────────────────

    @Test
    fun `kenh shell phai Volatile — ghi tren thread nen, doc tren thread chinh`() {
        assertTrue(
            Regex("""@Volatile\s+private\s+var\s+shell\b""").containsMatchIn(activity),
            "shell được GHI trên thread nền (nhánh dò dadb) và ĐỌC trên thread chính (lưới an toàn U3) ⇒ phải @Volatile, " +
                "không thì thread chính có thể thấy mãi null và đường shell biến mất im lặng",
        )
    }

    @Test
    fun `huy activity phai DONG ngan keo — no co the la cua so overlay rieng`() {
        val fn = SourceRoots.body(activity, "override fun onDestroy()")
        assertTrue(fn.contains("drawerController.close()"), "ngăn kéo overlay không chết cùng activity ⇒ phải đóng tay")
        val closeAt = fn.indexOf("drawerController.close()")
        val shutdownAt = fn.indexOf("winExec.shutdownNow()")
        assertTrue(shutdownAt > 0 && closeAt < shutdownAt, "đóng ngăn kéo TRƯỚC khi tắt hàng đợi lệnh")
    }
}
