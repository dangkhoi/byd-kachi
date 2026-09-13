package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B3.20 — khoá dây nối chip cảnh báo/camera VietMap trên cụm (nguồn → quyết định → owner → overlay), gate
 * `Prefs.showAlertChip` (default OFF), + toggle UI ở cả hai layout. Quét VĂN BẢN NGUỒN (như các wiring test
 * khác) — không dựng Android runtime.
 */
class AlertChipWiringContractTest {

    private val listener = SourceRoots.text("src/main/java/com/byd/clusternav/NavNotificationListener.kt")
    private val owner = SourceRoots.text("src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt")
    private val overlay = SourceRoots.text("src/main/java/com/byd/clusternav/speedbadge/SpeedBadgeOverlay.kt")
    /** Công tắc chip nay ở cầu Kachi — `BadgePlacementController` + màn cũ đã gỡ 2026-09-13 (S3 · R3). */
    private val bridge = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt")
    private val section = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/SettingsSectionsNav.kt")
    private val prefs = SourceRoots.text("src/main/java/com/byd/clusternav/Prefs.kt")

    @Test
    fun `listener nuoi chip qua RoadAlertChipDecision, gate showAlertChip`() {
        assertTrue(listener.contains("RoadAlertChipDecision.decide("), "listener phải dùng quyết định thuần :core")
        assertTrue(listener.contains("Prefs.showAlertChip(applicationContext)"), "phải gate theo toggle (default OFF)")
        assertTrue(
            listener.contains("speedSignOwner.setRoadAlertChip("),
            "listener phải đẩy quyết định xuống owner (cả nhánh show lẫn ẩn)",
        )
        // nhánh ẩn khi toggle off — không để chip kẹt trên cụm khi user tắt
        assertTrue(listener.contains("setRoadAlertChip(false"), "toggle off ⇒ ẩn chip")
    }

    @Test
    fun `owner uy quyen xuong overlay setAlert`() {
        assertTrue(owner.contains("fun setRoadAlertChip("), "owner phải có API chip")
        assertTrue(owner.contains("badgeOverlay.setAlert("), "owner uỷ quyền xuống overlay")
        assertTrue(owner.contains("fun onAlertChipEnabledChanged("), "owner phải có hook toggle")
        assertTrue(owner.contains("badgeOverlay.applyAlertChipEnabled("), "hook toggle → applyAlertChipEnabled")
    }

    @Test
    fun `overlay gate + teardown chip`() {
        assertTrue(overlay.contains("fun setAlert("), "overlay phải có API công khai setAlert")
        // doSetAlert VÀ applyAlertChipEnabled đều phải gate BẰNG CẢ master badge LẪN showAlertChip ⇒ đếm ≥ 2
        // (gỡ khỏi 1 trong 2 chỗ ⇒ đỏ; không bị mù như kiểm "tồn tại đâu đó").
        val gateHits = Regex("""Prefs\.badgeEnabled\(appContext\)\s*\|\|\s*!Prefs\.showAlertChip\(appContext\)""")
            .findAll(overlay).count()
        assertTrue(
            gateHits >= 2,
            "gate (master AND showAlertChip) phải có ở CẢ doSetAlert và applyAlertChipEnabled — thấy $gateHits",
        )
        assertTrue(overlay.contains("teardownAlert()"), "teardown() phải gỡ luôn chip (không rò cửa sổ)")
        // chip là cửa sổ RIÊNG (không đụng badge chính / upcoming)
        assertTrue(overlay.contains("private var alertChipView: AlertChipView?"), "chip có view/cửa sổ riêng")
    }

    @Test
    fun `toggle UI co that va di qua cau`() {
        // Tới 2026-09-13 bài này canh hai biến thể `activity_main.xml` + `BadgePlacementController` (bài học F3
        // P0: xe render bản `layout-w960dp`). Màn cũ đã gỡ ⇒ công tắc chỉ còn MỘT bề mặt: nhóm *Dẫn đường* của
        // Kachi Settings, dựng bằng mã (không XML) và ghi qua cầu — nên bài canh đúng hai mắt xích đó.
        assertTrue(section.contains("bridge.setAlertChip("), "section Dẫn đường phải có ô tick gọi cầu")
        assertTrue(bridge.contains("Prefs.setShowAlertChip("), "cầu phải lưu prefs")
        assertTrue(bridge.contains("onAlertChipEnabledChanged("), "cầu phải re-evaluate overlay")
    }

    @Test
    fun `prefs showAlertChip default OFF`() {
        assertTrue(
            Regex("""getBoolean\(K_SHOW_ALERT_CHIP,\s*false\)""").containsMatchIn(prefs),
            "showAlertChip phải default false (opt-in, không phá bố trí badge)",
        )
    }
}
