package com.byd.clusternav.launcher

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Owner 2026-09-14: *"bóng vietmap sao mất chỗ cấu hình vị trí trên cụm rồi"*.
 *
 * [ĐO] gốc: IA v2 (09-12) cho `bubble()` **return sớm** khi `vmBubbleAdjustable()` = false ⇒ chưa bật chiếu (hoặc chưa
 * bật công tắc) thì stepper + khung kéo-thả **không được dựng**, chỉ còn một câu. Người dùng không thấy chỗ cấu hình.
 * Màn cũ (`MainActivity.refreshVmOverlayPanel`) giữ panel, chỉ mờ + khoá + nhắc điều kiện. Bài này khoá hành vi cũ:
 *  1. trong thân `bubble(` không có `return` trước chỗ dựng `VmBubblePlacementView(`;
 *  2. cổng nằm ở `applyBubbleGate()` với ba câu nhắc (công tắc tắt / chưa chiếu / kéo được), có gọi từ callback công tắc;
 *  3. cả hai ngôn ngữ có chuỗi `kachi_bubble_need_toggle`.
 */
class BubblePanelAlwaysVisibleContractTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private val nav by lazy {
        app("src/main/java/com/byd/clusternav/launcher/SettingsSectionsNav.kt").toFile().readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }
    }

    private fun body(fn: String): String {
        val from = nav.indexOf("private fun $fn(")
        assertTrue(from >= 0, "phải có $fn")
        val rest = nav.substring(from + 1)
        val next = rest.indexOf("private fun ").takeIf { it >= 0 } ?: rest.length
        return rest.substring(0, next)
    }

    @Test
    fun `bo chinh vi tri bong bong duoc dung bat ke dang chieu hay chua`() {
        val b = body("bubble")
        val build = b.indexOf("VmBubblePlacementView(")
        assertTrue(build >= 0, "bubble() phải dựng khung kéo-thả")
        assertFalse(b.substring(0, build).contains("return"), "không được return sớm trước khi dựng bộ chỉnh (owner: 'mất chỗ cấu hình')")
        assertTrue(b.contains("applyBubbleGate()"), "cổng chỉnh áp qua applyBubbleGate, không ẩn view")
    }

    @Test
    fun `cong chi mo khoa, khong an, va noi dung dieu kien con thieu`() {
        val g = body("applyBubbleGate")
        assertTrue(g.contains("isEnabled = adjustable") && g.contains("GATE_ALPHA"), "chưa chỉnh được ⇒ khoá + mờ (như màn cũ 0.4f)")
        listOf("kachi_bubble_need_toggle", "kachi_bubble_need_cast", "kachi_bubble_drag_hint").forEach {
            assertTrue(g.contains(it), "câu nhắc $it phải có trong cổng")
        }
        assertTrue(body("bubble").contains("bridge.setVmBubbleEnabled(on); applyBubbleGate()"), "đổi công tắc ⇒ áp cổng ngay")
    }

    /**
     * [SOÁT SENIOR 2026-09-14 · P1] Khoá cổng bằng `stepper.view.isEnabled` là **khoá giả**.
     *
     * [ĐO] AOSP `android-10.0.0_r47`: `View.java:10873–10876` `setEnabled` chỉ đặt cờ cho CHÍNH view đó
     * (`setFlags(… ENABLED_MASK)`, không đệ quy); `ViewGroup.java` không override `setEnabled` và
     * `dispatchTouchEvent` của nó không đọc cờ enabled lần nào; nhánh `DISABLED` ở `View.java:14764–14771` nằm
     * trong `onTouchEvent` của view bị tắt. ⇒ tắt hàng `LinearLayout` thì hai nút −/+ con **vẫn nhận click**,
     * `nudgeBubble` vẫn ghi prefs trong lúc cổng đang "khoá" (mờ 0.4). Bài này ghim: cổng phải đi qua
     * [SettingsRows.Stepper.isEnabled], và setter đó phải chạm ĐÚNG hai nút con.
     */
    @Test
    fun `khoa stepper phai cham hai nut, khong chi cham hang`() {
        val g = body("applyBubbleGate")
        assertFalse(
            g.contains("bubbleX?.view") && g.contains("isEnabled"),
            "khoá qua view của hàng là khoá giả (View.setEnabled không lan xuống con) — dùng Stepper.isEnabled",
        )
        val rowsSrc = app("src/main/java/com/byd/clusternav/launcher/SettingsRows.kt").toFile().readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }
        val setter = rowsSrc.substringAfter("var isEnabled: Boolean", "").substringBefore("}\n")
        assertTrue(
            setter.contains("minus.isEnabled") && setter.contains("plus.isEnabled") && setter.contains("view.isEnabled"),
            "Stepper.isEnabled phải khoá cả hàng lẫn hai nút −/+",
        )
    }

    /**
     * Cổng bám CAST, mà công tắc Cast ở **nhóm khác** của cùng bảng; trang Cài đặt lại được nhớ lại
     * (`SettingsPanel.pages` — đổi nhóm chỉ tháo/gắn view, không dựng lại). Không đọc lại lúc trang hiện ra thì
     * bật Chiếu xong quay về đây vẫn mờ + khoá + câu nhắc sai. Màn cũ né bằng nhịp 1 s; ở đây bằng attach.
     */
    @Test
    fun `cong doc lai moi luot trang hien ra`() {
        val b = body("bubble")
        assertTrue(b.contains("addOnAttachStateChangeListener"), "trang được nhớ lại ⇒ phải áp lại cổng lúc gắn vào cửa sổ")
        assertTrue(b.contains("onViewAttachedToWindow(v: View) = applyBubbleGate()"), "gắn lại ⇒ đọc lại cast/công tắc")
    }

    @Test
    fun `chuoi nhac cong tac co ca hai ngon ngu`() {
        listOf("src/main/res/values/strings_kachi.xml", "src/main/res/values-en/strings_kachi.xml").forEach { f ->
            assertTrue(app(f).toFile().readText().contains("name=\"kachi_bubble_need_toggle\""), "$f thiếu kachi_bubble_need_toggle")
        }
    }
}
