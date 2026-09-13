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
        assertTrue(overlay.contains("SlotSwapButton.strip("), "OverlayHeads phải dựng nút qua SlotSwapButton.strip (dải khung ô che caption + cùng nút ⇄)")
        val slotHead = workspace.substring(workspace.indexOf("private fun slotHead("))
            .let { it.substring(0, it.indexOf("private fun", 10)) }
        assertTrue(slotHead.contains("SlotSwapButton.centered("), "WorkspaceView.slotHead phải dựng nút qua SlotSwapButton.centered")
    }

    @Test
    fun `hinh nut chi khai o mot cho`() {
        // Hình = icon ic-swap + OVAL + viền STROKE. Chỉ SlotSwapButton được khai; hai đường không được tự vẽ lại.
        assertTrue(button.contains("\"ic-swap\"") && button.contains("GradientDrawable.OVAL") && button.contains("Sp.STROKE"))
        listOf("OverlayHeads.kt" to overlay, "WorkspaceView.kt" to workspace).forEach { (name, src) ->
            assertFalse(src.contains("\"ic-swap\""), "$name không được tự vẽ icon ⇄ — dùng SlotSwapButton")
        }
    }

    @Test
    fun `khong con nut dong, ten hay thanh nen o dai dau o`() {
        assertFalse(overlay.contains("\"ic-close\""), "OverlayHeads không còn nút ✕ (owner: chỉ 1 nút ⇄)")
        assertFalse(overlay.contains("HEAD_BG"), "OverlayHeads không còn thanh nền đục (vai `headBg` đã xoá khỏi bảng màu — D2a)")
        assertFalse(overlay.contains("onClose"), "Head không còn callback đóng")
        assertFalse(overlay.contains("TextView"), "OverlayHeads không còn nhãn tên app")
        assertFalse(windows.contains("onClose ="), "LauncherWindows không còn nối nút ✕ vào Head")
    }

    @Test
    fun `dai phu trai het be rong o, cao SLOT_HEAD_CLEAR, nut o giua`() {
        assertTrue(overlay.contains("hd.width, h, hd.left, hd.top"), "dải phủ trải hết bề rộng ô (che caption freeform của hệ)")
        assertTrue(overlay.contains("SlotSwapButton.overlayHeightPx("), "cao lấy từ SlotSwapButton (= SLOT_HEAD_CLEAR)")
        assertFalse(overlay.contains("Sp.HEAD_BAR"), "không còn cao thanh HEAD_BAR (hằng đã xoá khỏi KachiSpace — D2a)")
        assertTrue(overlay.contains("(hd.appTop - hd.top) + caption") && overlay.contains("Sp.CAPTION_COVER"),
            "dải phải phủ HẾT caption của hệ: từ mép trên ô tới mép trên cửa sổ app + CAPTION_COVER ([ĐO] máy ảo caption 42dp)")
        val strip = button.substring(button.indexOf("fun strip("))
        assertTrue(strip.contains("centered(context, onTap)") && strip.contains("KachiTheme.CELL"),
            "strip = centered + nền màu KHUNG Ô (không phải vai nền thanh tiêu đề cũ)")
    }

    @Test
    fun `nut noi an khi bang Cai dat hoac bang ve dang mo`() {
        val activity = code("KachiHomeActivity.kt")
        assertTrue(activity.contains("drawerOpen = { drawerController.isOpen() || panels.settingsOpen() || panels.layoutOpen() }"),
            "OverlayHeads phải coi bảng Cài đặt/bảng vẽ như ngăn kéo: đang mở ⇒ ẩn nút nổi")
        assertTrue(activity.contains("onPanelsChanged = { windows.updateOverlayHeads() }"), "mở/đóng bảng phải kích cập nhật nút nổi")
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
