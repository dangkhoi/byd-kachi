package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá DÂY NỐI của lỗi B (PROFILE-SWITCH-SLOTS — app nổi sót sau khi đổi hồ sơ) ở chỗ ghép Activity/View Android mà
 * test đơn vị `:core` không chạm tới (dự án không dùng Robolectric). Hành vi thật của lượt dọn (chuỗi lệnh, guard, dấu)
 * đã khoá ở `:core` `FloatingOrphanSweepTest`/`FloatingOrphanPlanTest` bằng dump `am stack list` nguyên văn; ở đây chỉ
 * khoá: ai GỌI nó, ở đâu, theo thứ tự nào.
 *
 *  - R-B1: `reflow` không tự mở/đóng app nào (nguồn app mồ côi đo được ở máy ảo 01/10 và xe 29/09).
 *  - R-B2 → READY-AT-HOME R1.3: chạm tay KHÔNG còn mở cửa sổ nổi (không quyền thì không dùng được app).
 *  - R-B3: dọn ở đúng hai mốc — gỡ app (evict) và kênh shell vừa lên — chỉ khi có kênh, trên luồng nền.
 *  - R-B4: dấu theo XE, tệp `clusternav_state` sẵn có, khai lý do ở danh mục.
 *
 * Mọi vùng cắt bằng [SourceRoots.body] (nổ nếu mốc không còn), trên mã đã bỏ chú thích ([SourceRoots.codeOf]).
 */
class FloatingWiringContractTest {

    private val windows by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/LauncherWindows.kt") }
    private val activity by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val view by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt") }
    private val cards by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WorkspaceViewCards.kt") }
    private val store by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/FloatingLedgerStore.kt") }

    @Test
    fun `R-B1 reflow KHONG mo, KHONG dong, KHONG doi khung app nao - chi dung lai nut noi`() {
        val fn = SourceRoots.body(windows, "fun reflow()")
        listOf("openInSlot", "closeSlot", "moveToSlot", "submit", "shell()", "am start").forEach {
            assertFalse(fn.contains(it), "reflow không được còn '$it' — đường tự mở là nguồn app mồ côi (R-B1)")
        }
        assertTrue(fn.contains("updateOverlayHeads()"), "reflow vẫn phải dựng lại nút ⇄ nổi theo khung mới")
        assertTrue(fn.contains("if (embedding()) return"), "nhúng thì không có nút nổi để dựng")
    }

    /**
     * ⚠ READY-AT-HOME R1.3 (owner 2026-10-01 *"không có quyền, không dùng đc app"*) — bài này ĐỔI có chủ đích, theo
     * hướng CHẶT hơn: bản R-B2 đòi chạm tay vẫn MỞ cửa sổ nổi (ghi dấu trước). Nay chạm ô chưa có bộ chiếu KHÔNG mở cửa
     * sổ nổi ở ca nào ⇒ cũng không còn gì để ghi dấu; lượt dọn [sweepFloating] vẫn giữ cho cửa sổ do bản cũ để lại sau
     * nâng cấp. Khoá đầy đủ: `ReadyAtHomeWiringContractTest.placeApp…`; luật thuần `ShellReadinessPolicyTest`.
     */
    @Test
    fun `R1_3 cham tay KHONG mo cua so noi - khong con lenh mo nen khong con dau phai ghi`() {
        val fn = SourceRoots.body(windows, "fun placeApp(")
        listOf("floatingLedger.markOpened(pkg)", "launcher.openInSlot(", "launcher.moveToSlot(", "submit {").forEach {
            assertFalse(fn.contains(it), "placeApp không được còn '$it' (READY-AT-HOME R1.3)")
        }
        assertTrue(fn.indexOf("if (embedding()) return") in 0 until fn.indexOf("ShellAccessUi.slotTap"), "có bộ chiếu ⇒ không làm gì")
        assertTrue(windows.contains("FloatingOrphanSweep(floatingLedger"), "lượt dọn cửa sổ nổi của bản cũ vẫn phải còn")
    }

    @Test
    fun `R-B3 moc evict - don sau khi go app, chi khi co app bi go`() {
        val fn = SourceRoots.body(windows, "fun reconcileLocations(")
        val evict = fn.indexOf("r.evict.forEach")
        val sweep = fn.indexOf("if (r.evict.isNotEmpty()) sweepFloating(\"evict\")")
        assertTrue(evict >= 0 && sweep > evict, "dọn phải chạy SAU vòng gỡ, chỉ khi evict khác rỗng")
    }

    @Test
    fun `R-B3 sweepFloating - chi khi co kenh, tren luong nen, held doc luc chay, khong force-stop`() {
        val fn = SourceRoots.body(windows, "fun sweepFloating(")
        val gate = fn.indexOf("val s = shell() ?: return")
        val bg = fn.indexOf("submit {")
        val held = fn.indexOf("state().slots")
        val run = fn.indexOf("floatingSweep.run(s, held, reason)")
        assertTrue(gate >= 0 && bg > gate && held > bg && run > held, "thứ tự: cổng kênh → luồng nền → đọc held → chạy")
        listOf("embedding()", "force-stop", "fullscreenCmd", "closeSlot", "am stack").forEach {
            assertFalse(fn.contains(it), "sweepFloating không được có '$it' (lệnh dựng ở :core FloatingOrphanPlan)")
        }
    }

    @Test
    fun `R-B3 moc shell-up - don NGAY SAU khi gan kenh trong onSeam`() {
        val fn = SourceRoots.body(activity, "bringUpShellChannel(dadb, seam, workspace, viewModel, container) { s ->")
        val shell = fn.indexOf("shell = s")
        val sweep = fn.indexOf("windows.sweepFloating(\"shell-up\")")
        assertTrue(shell >= 0 && sweep > shell, "dọn phải đứng SAU `shell = s` (sweepFloating đọc shell())")
    }

    @Test
    fun `chi mot cho dung lenh go - khong tep nao cua man nha tu viet am stack remove`() {
        val dir = SourceRoots.path("src/main/java/com/byd/clusternav/launcher")
        val offenders = java.nio.file.Files.walk(dir).use { s ->
            s.filter { it.toString().endsWith(".kt") }
                .filter { stripComments(it.toFile().readText()).contains("stack remove") }
                .map { it.fileName.toString() }.toList()
        }
        assertEquals(emptyList<String>(), offenders, "lệnh gỡ chỉ được dựng ở :core FloatingOrphanPlan.removeCmd")
    }

    /** ĐÚNG bộ bỏ chú thích của [SourceRoots.codeOf] ([KotlinSource.stripComments]) — KDoc được phép NHẮC lệnh, mã thì không được VIẾT nó. */
    private fun stripComments(src: String): String = KotlinSource.stripComments(src)

    @Test
    fun `R-B4 dau theo XE - tep clusternav_state, commit dong bo, khai ly do o danh muc`() {
        assertTrue(store.contains("getSharedPreferences(FreeformSeedStore.PREF"), "dùng lại tệp theo xe sẵn có, không mở tệp thứ ba")
        assertTrue(store.contains(".commit()"), "dấu phải nằm trên đĩa trước lệnh mở (commit đồng bộ)")
        assertFalse(store.contains(".apply()"))
        assertEquals("kachi_floating_opened", FloatingLedgerStore.KEY)
        assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf(FloatingLedgerStore.KEY), "theo XE, không theo hồ sơ")
        assertTrue(FloatingLedgerStore.KEY in SettingsCatalog.NOT_SETTINGS, "trạng thái máy — không phải dòng cài đặt")
    }

    /**
     * READY-AT-HOME §4.9 — chữ trên ô chưa có bộ chiếu đổi từ "Chạm để mở" (chạm không còn mở nổi) sang TÌNH TRẠNG KÊNH,
     * tự đổi theo trạng thái. Điều kiện hiện vẫn y R-B1: ô KHÔNG có bộ chiếu (không kênh shell, không ActivityView).
     */
    @Test
    fun `R1_3 the o App khong bo chieu thi noi tinh trang kenh, du vi va en`() {
        assertTrue(
            view.contains("appCard(content.pkg, tapHint = shell == null && !SlotAppHost.embeddingUsable(context))"),
            "gợi ý chỉ hiện khi ô KHÔNG có bộ chiếu (không kênh shell, không ActivityView)",
        )
        val fn = SourceRoots.body(cards, "internal fun WorkspaceView.appCard(")
        assertTrue(fn.contains("if (tapHint) col.addView(ShellAccessUi.tileHint(context))"))
        assertFalse(fn.contains("kachi_slot_tap_to_open"))
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        assertTrue(vi.contains("<string name=\"kachi_slot_connecting\">Đang kết nối…</string>"))
        assertTrue(vi.contains("<string name=\"kachi_slot_needs_access\">Cần cấp quyền</string>"))
        assertTrue(en.contains("<string name=\"kachi_slot_connecting\">Connecting…</string>"))
        assertTrue(en.contains("<string name=\"kachi_slot_needs_access\">Permission needed</string>"))
    }
}
