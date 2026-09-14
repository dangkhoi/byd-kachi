package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ S4 · R12 (b) — Ô LOẠI **LAUNCHER** TRÊN THANH NÚT XE: DÂY NỐI ═══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` **R12**. Phần THUẦN (`kindOf`/`pick`/`isChippable`/
 * `DockSelection.apply`) đã có bài chạy thật ở `:core`; bài này canh ba chỗ mà test đơn vị không tới được vì
 * chúng nằm trong View Android và trong composition-root:
 *  1. thanh nút có nhánh riêng cho loại này (gộp vào WRITE ⇒ ô **không hiện mà cũng không báo**),
 *  2. ô đó KHÔNG đi qua cổng điều khiển xe và KHÔNG mang dấu "chưa kiểm",
 *  3. cú bấm đi về **đúng hai đường mà thanh trên đang dùng** — không mở đường thứ hai (R12).
 *
 * Quét source (bỏ chú thích trước khi kiểm) theo đúng lệ [CapabilityTileWiringContractTest]: viết tên hàm vào
 * comment không được tính là đã nối dây.
 */
class LauncherActionTileWiringContractTest {

    private fun code(relative: String): String =
        SourceRoots.text(relative)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private val dock by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlDockView.kt") }
    private val factory by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt") }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val strip by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt") }
    private val drawer by lazy { code("src/main/java/com/byd/clusternav/launcher/AppDrawer.kt") }

    // ══ (1) Thanh nút dựng được ô loại LAUNCHER ═══════════════════════════════════════════════════════════

    @Test
    fun `thanh nut co nhanh rieng cho hanh dong launcher`() {
        assertTrue(
            dock.contains("CapabilityKind.LAUNCHER"),
            "thiếu nhánh này thì mã launcher rơi vào `null ->` và ô **không được thêm vào thanh** mà cũng không " +
                "báo gì — đúng lỗi 'bật vào thanh rồi tưởng hỏng' đã phải vá cho gói lệnh ở W2",
        )
        val fn = SourceRoots.body(dock, "private fun rebuild()")
        assertTrue(fn.contains("tiles.launcherTile("), "phải dùng BỘ DỰNG Ô DÙNG CHUNG, không tự dựng ô thứ hai")
        assertTrue(fn.contains("onLauncherAction(id)"), "cú bấm phải đẩy RA NGOÀI qua callback (view thuần)")
    }

    /**
     * Thanh nút **không được biết** `launcher_apps` nghĩa là gì.
     *
     * Biết nghĩa = nó tự có một đường thứ hai tới ngăn kéo, và đường ấy sẽ lệch với thanh trên đúng lúc ai đó sửa
     * một bên. Chỗ dịch mã → việc nằm ở [Activity.controlDock] (một bản duy nhất).
     */
    @Test
    fun `thanh nut khong tu biet ma launcher nghia la gi`() {
        listOf("LauncherActions.APPS", "LauncherActions.SETTINGS", "launcher_apps", "launcher_settings")
            .forEach { assertFalse(dock.contains(it), "ControlDockView không được nhắc '$it' — nó là view thuần") }
        assertFalse(dock.contains("openAppList("), "thanh nút không được tự mở ngăn kéo")
        assertFalse(dock.contains("openSettings("), "thanh nút không được tự mở Cài đặt")
    }

    // ══ (2) Ô launcher: không chạm xe, không dấu chưa-kiểm ════════════════════════════════════════════════

    @Test
    fun `o launcher KHONG di qua cong dieu khien xe va KHONG mang dau chua kiem`() {
        val fn = SourceRoots.body(factory, "fun launcherTile(")
        assertFalse(
            fn.contains("control()"),
            "mã launcher không có dòng nào trong ControlRegistry ⇒ bắn `press` xuống cổng xe là gửi một lệnh " +
                "không tồn tại tới phần cứng",
        )
        assertFalse(
            fn.contains("withBadge("),
            "tier luôn PROVEN (mở ngăn kéo / mở Cài đặt là đường dùng hằng ngày) ⇒ dán dấu 'chưa kiểm trên xe' " +
                "lên đây là nói sai, và làm dấu đó mất giá trị ở chỗ nó đúng",
        )
        assertTrue(fn.contains("onTap()"), "cú bấm phải đi ra callback")
        assertTrue(fn.contains("applyBg("), "vẫn là một cái NÚT: nháy nền như ô BUTTON, không phải ô chỉ-xem")
    }

    // ══ (3) Nối về ĐÚNG đường của thanh trên ══════════════════════════════════════════════════════════════

    @Test
    fun `cu bam noi ve dung hai duong ma thanh tren dang dung`() {
        val fn = SourceRoots.body(wiring, "internal fun Activity.controlDock(")
        assertTrue(fn.contains("LauncherActions.APPS -> openAppList()"), "mã Ứng dụng phải mở ngăn kéo")
        assertTrue(fn.contains("LauncherActions.SETTINGS -> openSettings()"), "mã Cài đặt phải mở màn Cài đặt")
        assertTrue(fn.contains("else -> Unit"), "mã launcher lạ ⇒ không làm gì; mở nhầm một màn còn khó hiểu hơn")

        // Và Activity truyền vào ĐÚNG hai biểu thức mà thanh trên đang dùng — so từng chữ, vì đây chính là chỗ một
        // "đường thứ hai" (vd `startActivity(...)` riêng cho Cài đặt) sẽ len vào mà không ai thấy.
        assertTrue(
            activity.contains("controlDock(container.carControl, { drawerController.openAppList() }, " +
                "{ panels.openSettings() })"),
            "thanh nút phải nhận CHÍNH hai lambda của thanh trên",
        )
        assertTrue(strip.contains("onOpenAppList"), "tiền đề: thanh trên vẫn có lối Ứng dụng")
        assertEquals(
            2, Regex(Regex.escape("drawerController.openAppList()")).findAll(activity).count(),
            "đúng hai chỗ gọi: thanh trên + thanh nút. Nhiều hơn là đã mọc một đường thứ ba",
        )
    }

    // ══ (4) Bộ chọn nút bày khối Launcher, và lấy từ :core ════════════════════════════════════════════════

    @Test
    fun `bo chon nut bay khoi Launcher lay tu core`() {
        val init = SourceRoots.body(drawer, "    init {")
        val dockBranch = init.substringAfter("if (dock) {").substringBefore("} else if (assign) {")
        assertTrue(
            dockBranch.contains("CapabilityPicker.launcherPicks()"),
            "khối Launcher phải lấy từ `:core` — chép hai mã ra tầng vẽ là bản sao thứ hai của cùng một quyết định",
        )
        listOf("CapabilityPicker.LAUNCHER_TITLE", "CapabilityPicker.LAUNCHER_NOTE").forEach {
            assertTrue(dockBranch.contains(it), "chữ của khối cũng phải từ `:core` (song ngữ), không gõ tại chỗ")
        }
        // Chế độ gán-ô KHÔNG có khối này: ô giữa màn là khung lớn nhất của HOME, dùng nó để mở ngăn kéo là đổi chỗ
        // đắt lấy việc rẻ — và ngăn kéo đã mở được từ thanh trên.
        val assignBranch = init.substringAfter("} else if (assign) {").substringBefore("} else {")
        assertFalse(assignBranch.contains("launcherPicks()"), "chế độ gán ô không bày hành động launcher")
    }

    /** Khối Launcher chỉ có **2 ô** nên nó đứng ĐẦU mà không đẩy mục Nhóm xuống theo nghĩa có thật (§4.2). */
    @Test
    fun `khoi Launcher dung TRUOC muc Nhom o che do chon nut`() {
        val init = SourceRoots.body(drawer, "    init {")
        val dockBranch = init.substringAfter("if (dock) {").substringBefore("} else if (assign) {")
        val launcherAt = dockBranch.indexOf("CapabilityPicker.launcherPicks()")
        val groupAt = dockBranch.indexOf("groupSection(body)")
        assertTrue(launcherAt in 0 until groupAt, "để nó ở cuối thì phải cuộn qua trọn 187 ô mới đặt được nút Ứng dụng")
        assertEquals(2, LauncherActions.ALL.size, "khối này cố ý NHỎ — thêm mục thì phải xét lại chỗ đứng của nó")
    }
}
