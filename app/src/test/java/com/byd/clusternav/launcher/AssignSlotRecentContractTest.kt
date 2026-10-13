package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ kachi-301 **R27** — bộ chọn của Ô (`Mode.ASSIGN_SLOT`) có hàng "Gần đây" TRÊN CÙNG, chạm = đặt app vào ô ═══════
 *
 * Owner 13/10: *"khi chọn widget cũng thêm section các ứng dụng hay dùng như nhấn nút ứng dụng nhé, trên cùng"*.
 * Bài khoá bốn điều: (1) Ô nhận danh sách gần đây từ `DrawerController.open`; (2) hàng đứng TRƯỚC mọi mục khác của
 * chế độ gán ô; (3) chạm dùng LẠI đường `onPickApp` của bảng (ô gần đây chép `onTap` từ [AppDrawerApps.load]) — không
 * có đường chạm thứ hai; (4) MỘT chỗ dựng cho cả hai chế độ, và các chế độ chọn khác không có hàng này.
 */
class AssignSlotRecentContractTest {

    private fun code(f: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$f")

    private val drawer by lazy { code("AppDrawer.kt") }
    private val recentFile by lazy { code("AppDrawerRecent.kt") }
    private val appsFile by lazy { code("AppDrawerApps.kt") }
    private val controller by lazy { code("DrawerController.kt") }

    /** Thân hàm `fun name(` tới hàm kế tiếp (đủ cho phép so thứ tự trong một hàm). */
    private fun fnBody(src: String, name: String): String {
        val start = src.indexOf("fun $name(")
        assertTrue(start >= 0, "không thấy fun $name(")
        val next = src.indexOf("\n    fun ", start + 1).let { if (it < 0) src.length else it }
        return src.substring(start, next)
    }

    /** Khối `else if (assign) { … } else if (pick)` trong `init` của ngăn kéo. */
    private val assignBlock by lazy {
        val a = drawer.indexOf("} else if (assign) {")
        val b = drawer.indexOf("} else if (pick) {", a)
        assertTrue(a >= 0 && b > a, "không tìm thấy khối chế độ gán ô")
        drawer.substring(a, b)
    }

    @Test
    fun `o gan nhan danh sach gan day tu DrawerController open`() {
        val open = fnBody(controller, "open")
        assertTrue(open.contains("recentApps = recentApps()"), "R27: DrawerController.open phải chuyền recentApps cho bộ chọn của Ô")
        assertTrue(open.contains("onPickApp = { pkg -> onPickApp(index, pkg) }"), "chạm app ở bộ chọn Ô phải về onPickApp(index, pkg)")
    }

    @Test
    fun `hang gan day dung TRUOC moi muc khac cua che do gan o`() {
        val recent = assignBlock.indexOf("recentSection(")
        assertTrue(recent >= 0, "R27: chế độ gán ô phải dựng hàng Gần đây")
        listOf(
            "R.string.kachi_drawer_section_apps", "R.string.kachi_drawer_section_appwidgets",
            "groupSection(", "cameraSection(", "addWidgetGrid(", "singlesSection(",
        ).forEach { later ->
            val at = assignBlock.indexOf(later)
            assertTrue(at > recent, "R27: '$later' phải đứng SAU hàng Gần đây (hàng này TRÊN CÙNG)")
        }
    }

    @Test
    fun `cham o gan day dung lai duong onPickApp cua bang - khong co duong thu hai`() {
        // Hàng gần đây lấy ô từ AppDrawerApps.recent trên CHÍNH danh sách `all` của lưới Ứng dụng.
        assertTrue(assignBlock.contains("recentSection(body, apps, all, recentApps, COLS_APP)"))
        assertTrue(assignBlock.contains("apps.grid(body, all, cols = COLS_APP)"), "lưới Ứng dụng và hàng Gần đây phải dùng chung `all`")
        assertTrue(recentFile.contains("apps.recent(all, recentApps)"))
        // Ô gần đây chép NGUYÊN onTap của ô gốc (load() ⇒ onPickApp(pkg)).
        assertTrue(appsFile.contains("Item(it.pkg, it.label, copyOf(it.icon), it.onTap)"), "AppDrawerApps.recent phải chép onTap gốc")
        assertTrue(appsFile.contains(".map { (pkg, label, icon) -> Item(pkg, label, icon) { onPickApp(pkg) } }"))
        // Tệp hàng gần đây không được tự dựng ô / tự gọi đường chạm nào.
        listOf("onPickApp", "onOpenApp", "Item(", "setOnClickListener", "assignApp").forEach {
            assertFalse(recentFile.contains(it), "AppDrawerRecent.kt không được có '$it' — đường chạm thứ hai")
        }
    }

    @Test
    fun `mot cho dung cho hai che do, cac che do chon khac khong co hang nay`() {
        assertEquals(2, Regex("""recentSection\(body""").findAll(drawer).count(), "đúng 2 lời gọi: OPEN_APP + ASSIGN_SLOT")
        assertFalse(drawer.contains("kachi_drawer_section_recent"), "nhãn Gần đây chỉ dựng ở AppDrawerRecent.kt (một nguồn)")
        assertTrue(recentFile.contains("R.string.kachi_drawer_section_recent"))
        assertTrue(recentFile.contains("if (recent.isEmpty()) return false"), "rỗng ⇒ không bày mục, kể cả tiêu đề")
        assertEquals(2, Regex("""recentSection\(body, apps, all, recentApps, COLS_APP\)""").findAll(drawer).count(), "cùng số cột lưới app ở cả hai chế độ")
        // PICK_DOCK / PICK_SHORTCUTS / PICK_TRIP không có hàng gần đây.
        val dockBlock = drawer.substring(drawer.indexOf("if (dock) {"), drawer.indexOf("} else if (assign) {"))
        val pickBlock = drawer.substring(drawer.indexOf("} else if (pick) {"), drawer.indexOf("} else {", drawer.indexOf("} else if (pick) {")))
        assertFalse(dockBlock.contains("recentSection("))
        assertFalse(pickBlock.contains("recentSection("))
        listOf("openDockPicker", "openShortcutPicker", "openTripPicker").forEach { fn ->
            if (controller.contains("fun $fn(")) assertFalse(fnBody(controller, fn).contains("recentApps"), "$fn không nhận recentApps")
        }
    }
}
