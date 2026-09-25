package com.byd.clusternav.launcher

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Owner 2026-09-13: *"cast app xong tự nhiên lại có 2 icon: đổi app và (x) — đã đổi thành còn 1 icon đổi app nằm
 * giữa, không background rồi? … vẫn đang trên emulator thôi, nhưng code không stable"*.
 *
 * [ĐO] gốc: ô app có HAI đường vẽ tuỳ lúc mở — nhúng ([WorkspaceView.slotHead]) đã là 1 nút ⇄ giữa/không nền, còn
 * freeform + lớp phủ ([OverlayHeads]) vẫn dựng thanh cũ (nền đục + chấm + tên + ⇄ + ✕). Đường nào thắng lúc mở là do
 * kênh dadb có kịp hay không ⇒ "không stable". Khoá: cả hai đường PHẢI dựng qua [SlotSwapButton]; không đường nào còn
 * nút ✕, tên hay thanh nền; hình nút chỉ khai ở MỘT chỗ.
 */
class SlotHeadParityContractTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun code(name: String): String =
        app("src/main/java/com/byd/clusternav/launcher/$name").toFile().readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private val overlay by lazy { code("OverlayHeads.kt") }
    private val workspace by lazy { code("WorkspaceView.kt") }
    private val button by lazy { code("SlotSwapButton.kt") }
    private val windows by lazy { code("LauncherWindows.kt") }

    @Test
    fun `ca hai duong deu dung SlotSwapButton centered`() {
        // owner 2026-09-25 (ảnh xe): bỏ thanh trắng — OverlayHeads đổi từ strip (nền che caption) sang centered
        // (trong suốt). CẢ HAI đường (freeform overlay + nhúng slotHead) nay dùng centered, không còn dải trắng.
        assertTrue(overlay.contains("SlotSwapButton.centered("), "OverlayHeads phải dựng nút qua SlotSwapButton.centered (trong suốt, không dải trắng che caption)")
        assertFalse(overlay.contains("SlotSwapButton.strip("), "OverlayHeads KHÔNG được dùng strip (dải trắng — owner 2026-09-25 chốt bỏ)")
        val slotHead = workspace.substring(workspace.indexOf("private fun slotHead("))
            .let { it.substring(0, it.indexOf("private fun", 10)) }
        assertTrue(slotHead.contains("SlotSwapButton.centered("), "WorkspaceView.slotHead phải dựng nút qua SlotSwapButton.centered")
    }

    @Test
    fun `hinh nut chi khai o mot cho`() {
        // Hình = CHỈ icon ic-swap, không nền oval, không viền (owner 2026-09-14: "kín đáo, nhỏ gọn, không khung viền").
        // Chỉ SlotSwapButton được khai; hai đường không được tự vẽ lại.
        assertTrue(button.contains("\"ic-swap\""))
        val build = button.substring(button.indexOf("fun build("), button.indexOf("fun centered("))
        assertFalse(build.contains("GradientDrawable.OVAL") || build.contains("setStroke("), "nút ⇄ không còn nền oval/viền — owner 2026-09-14")
        assertTrue(build.contains("Sp.ICON_S") || centeredIcon(button), "hình nút nhỏ (ICON_S 20dp), đích chạm vẫn TOUCH")
        listOf("OverlayHeads.kt" to overlay, "WorkspaceView.kt" to workspace).forEach { (name, src) ->
            assertFalse(src.contains("\"ic-swap\""), "$name không được tự vẽ icon ⇄ — dùng SlotSwapButton")
        }
    }

    private fun centeredIcon(src: String): Boolean =
        src.substring(src.indexOf("fun centered(")).contains("Sp.ICON_S), KachiTheme.dpi(context, Sp.ICON_S)")

    @Test
    fun `khong con nut dong, ten hay thanh nen o dai dau o`() {
        assertFalse(overlay.contains("\"ic-close\""), "OverlayHeads không còn nút ✕ (owner: chỉ 1 nút ⇄)")
        assertFalse(overlay.contains("HEAD_BG"), "OverlayHeads không còn thanh nền đục (vai `headBg` đã xoá khỏi bảng màu — D2a)")
        assertFalse(overlay.contains("onClose"), "Head không còn callback đóng")
        assertFalse(overlay.contains("TextView"), "OverlayHeads không còn nhãn tên app")
        assertFalse(windows.contains("onClose ="), "LauncherWindows không còn nối nút ✕ vào Head")
    }

    @Test
    fun `nut noi trong suot, cao dung khung nut, khong phu caption`() {
        // owner 2026-09-25: bỏ thanh trắng — OverlayHeads chỉ nút ⇄ trong suốt, cao đúng khung nút (overlayHeightPx),
        // KHÔNG phủ caption (không nhân CAPTION_COVER), không đè app/GMaps.
        assertTrue(overlay.contains("hd.width, minH, hd.left, hd.top"), "cao = minH (khung nút), không phủ caption")
        assertTrue(overlay.contains("SlotSwapButton.overlayHeightPx("), "cao lấy từ SlotSwapButton (= SLOT_HEAD_CLEAR)")
        assertFalse(overlay.contains("Sp.CAPTION_COVER"), "OverlayHeads KHÔNG còn phủ caption (owner 2026-09-25 bỏ thanh trắng)")
        assertFalse(overlay.contains("(hd.appTop - hd.top) + caption"), "không còn tính chiều cao phủ caption")
    }

    @Test
    fun `nut noi an khi bang Cai dat hoac bang ve dang mo`() {
        val activity = code("KachiHomeActivity.kt")
        assertTrue(activity.contains("drawerOpen = { drawerController.isOpen() || panels.settingsOpen() || panels.layoutOpen() }"),
            "OverlayHeads phải coi bảng Cài đặt/bảng vẽ như ngăn kéo: đang mở ⇒ ẩn nút nổi")
        // Soát Pass 2 (2026-09-14): cổng này nay còn kích thêm `topStrip.refreshVoicePill()` (nút mic soi lại điều
        // kiện sau khi màn Cài đặt đóng). Bài canh vì thế đọc ĐÚNG tính chất của nó — cổng có gọi cập nhật nút nổi
        // hay không — thay vì so nguyên một dòng, thứ sẽ đỏ mỗi lần ai đó nối thêm một việc chính đáng vào cùng cổng.
        val onPanels = Regex("""onPanelsChanged = \{([^}]*)\}""").find(activity)?.groupValues?.get(1).orEmpty()
        assertTrue(onPanels.contains("windows.updateOverlayHeads()"), "mở/đóng bảng phải kích cập nhật nút nổi")
        val panels = code("HomePanels.kt")
        assertTrue(panels.split("onPanelsChanged()").size - 1 >= 4, "openSettings/closeSettings/openLayoutEditor/closeLayoutEditor đều báo")
    }
    @Test
    fun `dich cham nut swap la TOUCH, hinh ve van ICON_L`() {
        val centered = button.substring(button.indexOf("fun centered("))
        assertTrue(centered.contains("KachiTheme.dpi(context, Sp.TOUCH), KachiTheme.dpi(context, Sp.SLOT_HEAD_CLEAR)"),
            "khung nhận chạm phải rộng TOUCH (48dp) — soát ảnh v2 đo nút 32dp")
        assertTrue(centered.contains("isClickable = false"), "nút vẽ không tự nhận chạm (tránh hai lớp cùng ăn một cú chạm)")
    }
}
