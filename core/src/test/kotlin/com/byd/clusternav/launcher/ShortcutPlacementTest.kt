package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceGrammar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F1 · C9 (spec shortcuts-autostart R1.1–R1.3 · §4.7) — CHỖ ĐỨNG của lối tắt trong các bộ đăng ký và bảng phạm vi khoá.
 *
 * Bài canh chung (hồ sơ · chia sẻ · danh mục · nhãn EN) đã tự quét các mục mới; bài này chốt những tính chất RIÊNG mà
 * bài chung không hỏi: khối thanh nút đặt được nhưng KHÔNG gọi bằng lời, widget `w_apps` không lọt vào hai bộ chọn chip /
 * nút, khoá `app_shortcuts` đi đúng đường của một khoá BỐ CỤC theo hồ sơ.
 */
class ShortcutPlacementTest {

    private val id = LauncherActions.SHORTCUTS

    @Test
    fun `khoi loi tat dat duoc len thanh nut, o khoi Launcher`() {
        assertEquals(CapabilityKind.LAUNCHER, CapabilityCatalog.kindOf(id))
        assertNotNull(LauncherActions.byId(id))
        assertTrue(id in CapabilityPicker.launcherPicks().map { it.id }, "bộ chọn nút thanh xe phải bày khối này")
        assertTrue(id in DockSelection.apply(DockConfig(), setOf(id)).enabled, "Áp dụng ở bộ chọn ⇒ mã vào được thanh")
        assertFalse(CapabilityCatalog.isWrite(id), "không phải lệnh xuống xe")
        assertFalse(TopStripConfig.isChippable(id), "chip 24dp không vẽ được một hàng icon")
        // Khối trên thanh + widget trong ô KHÔNG kéo theo datum nào — `null` = "đọc hết" = cổng H1 tắt im lặng.
        val d = CarDataDemand.of(
            HomeUiState(
                workspace = WorkspaceState(slots = List(WorkspaceState.SLOT_CAP) { if (it == 0) SlotContent.Widget(listOf("w_apps")) else SlotContent.Empty }),
                dock = DockConfig(enabled = listOf(id)),
                topStrip = TopStripConfig(ids = emptyList()),
            ),
        )
        assertNotNull(d, "nhu cầu về null ⇒ đọc lại cả bảng datum mỗi nhịp")
        assertTrue(d!!.isEmpty(), "lối tắt không bày một số nào của xe")
    }

    /** Khối Launcher đứng TRƯỚC 187 ô trong bộ chọn — nó phải còn là MỘT hàng (lý do con số ghim của bài cũ). */
    @Test
    fun `khoi Launcher van vua mot hang cua bo chon`() {
        assertTrue(
            CapabilityPicker.launcherPicks().size <= CapabilityPicker.COLS,
            "khối Launcher ${CapabilityPicker.launcherPicks().size} ô > ${CapabilityPicker.COLS} cột ⇒ đẩy lưới xuống một hàng",
        )
    }

    @Test
    fun `khoi loi tat KHONG goi bang loi - khong vao tu vung giong noi`() {
        assertFalse(LauncherActions.ALL.any { it.id == id }, "ALL là nguồn từ vựng giọng nói")
        assertFalse(VoiceGrammar.terms().any { it.id == id }, "\"mở lối tắt ứng dụng\" không có việc nào để thi hành")
    }

    @Test
    fun `widget w_apps - LOCAL, tu lo noi dung, khong doc xe`() {
        val w = WidgetRegistry.byId("w_apps")
        assertNotNull(w)
        assertEquals(WidgetKind.LOCAL, w!!.kind)
        assertTrue(WorkspaceRenderPlanner.selfDriven("w_apps"), "nhịp trạng thái xe 1 Hz không được dựng lại lưới icon (R1.3)")
        assertEquals(emptySet<String>(), CarDataDemand.CURATED["w_apps"], "nhu cầu dữ liệu xe RỖNG")
    }

    @Test
    fun `widget w_apps KHONG lot vao bo chon chip thanh tren va bo chon nut thanh xe`() {
        assertFalse(TopStripConfig.choices().any { it.id == "w_apps" }, "bộ chọn chip")
        val dockPicker = CapabilityPicker.launcherPicks() + CapabilityPicker.groupPicks() +
            CapabilityCatalog.byDomain().flatMap { CapabilityPicker.singlesOf(it.second) }
        assertFalse(dockPicker.any { it.id == "w_apps" }, "bộ chọn nút thanh xe")
    }

    @Test
    fun `hai be mat lot tat khong thanh cap nhan trung`() {
        val block = LauncherActions.byId(id)!!
        val widget = WidgetRegistry.byId("w_apps")!!
        assertFalse(block.label == widget.label || block.labelEn == widget.labelEn)
        assertFalse(block.label in CapabilityCatalog.collidingLabels())
    }

    @Test
    fun `khoa app_shortcuts - bo cuc theo ho so, chuoi, di theo ban chia se, co muc Cai dat`() {
        assertEquals(ProfileScope.Scope.PROFILE, ProfileScope.scopeOf("app_shortcuts"))
        assertTrue("app_shortcuts" in ProfileScope.LAUNCHER_LAYOUT_SUFFIXES)
        assertEquals(PrefType.STRING, ProfileScopeLauncher.DECLARED_TYPES["app_shortcuts"])
        assertTrue(ProfileSharePolicy.shareable("app_shortcuts"), "chỉ tên gói + kiểu mở — không vị trí")
        val e = SettingsCatalog.ENTRIES.single { it.prefKey == "app_shortcuts" }
        assertEquals(SettingsGroup.BARS, e.group)
        assertFalse(e.labelEn.isNullOrBlank())
    }

    @Test
    fun `chuoi ma hoa di qua kenh xuat-nhap ho so - khong tab, khong xuong dong, dung kieu`() {
        val enc = AppShortcutCodec.encode(listOf(AppShortcut("com.a", ShortcutMode.Slot(2)), AppShortcut("com.b", ShortcutMode.Background)))
        assertFalse(enc.contains('\t') || enc.contains('\n'))
        val checked = ProfileScopeLauncher.check(mapOf("app_shortcuts" to enc, "preset" to "THREE"))
        assertEquals(emptyList<String>(), checked.dropped)
        assertEquals(listOf("app_shortcuts"), ProfileScopeLauncher.check(mapOf("app_shortcuts" to true)).dropped, "sai kiểu ⇒ bỏ")
    }
}
