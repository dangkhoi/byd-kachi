package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.launcher.SettingsCatalog
import com.byd.clusternav.launcher.SettingsGroup
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ UX-OVERHAUL · WP6 — DÂY NỐI của công tắc *"Hiện nút nổi chiếu cụm"* ════════════════════════════════════════
 *
 * Quyết định thuần đã có `BubblePresenceTest` (`:core`) canh. Bài này canh **phần Android không kiểm được off-car
 * bằng cách chạy** (dịch vụ nổi cần thiết bị/Robolectric) bằng cách đọc chính mã nguồn — cùng khuôn
 * [BubbleGestureContractTest] và `FloatingBubbleFirstLaunchContractTest`.
 *
 * Thứ nó bảo vệ là **lằn ranh owner đặt ra**: *"ẩn thì không dựng bubble, nhưng cast vẫn bật được qua cách khác"*.
 * Ba tính năng KHÁC sống trong cùng dịch vụ nổi (tự-chiếu khi nổ máy · nhịp giữ-cụm · nhịp áp lại bong bóng
 * VietMap), nên cách "ẩn" dễ nghĩ nhất — `stopSelf()` — sẽ tắt luôn cả ba mà không có lỗi nào, không log nào.
 */
class BubbleVisibilityWiringContractTest {

    private fun code(rel: String): String = SourceRoots.codeOf(rel)

    private val service by lazy { code("src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt") }
    private val bridge by lazy { code("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeCast.kt") }
    private val section by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsCast.kt") }
    private val prefs by lazy {
        code("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt")
    }

    // ── 1 · Chuỗi Cài đặt → cầu → prefs → dịch vụ ───────────────────────────────────────────────

    @Test
    fun `hang Cai dat di qua cau, cau ghi dung khoa ben`() {
        assertTrue(
            section.contains("bridge.setCastBubbleVisible(") && section.contains("bridge.castBubbleVisible()"),
            "hàng Cài đặt phải ĐỌC và GHI qua cầu (spec IA v2 R2/N2 — section không gọi thẳng prefs)",
        )
        assertTrue(
            SourceRoots.body(bridge, "fun ClusterNavBridge.setCastBubbleVisible(").contains("prefs.setBubbleVisible("),
            "cầu phải ghi cờ BỀN, không giữ trong RAM",
        )
        assertTrue(prefs.contains("getBoolean(\"cast_bubble_visible\", true)"), "khoá thật + mặc định HIỆN")
        assertTrue(prefs.contains("putBoolean(\"cast_bubble_visible\""), "phải có đường ghi")
    }

    /**
     * Khoá phải có CHỦ trong danh mục, đúng nhóm *Chiếu cụm*, và là khoá **theo XE**.
     *
     * Không có mục nào nhận ⇒ `SettingsCoverageContractTest` sẽ đỏ, nhưng nó không nói được *"đúng nhóm nào"* —
     * mà một công tắc của nút nổi lạc sang nhóm khác là thứ owner phải đi tìm trên xe.
     */
    @Test
    fun `khoa co chu trong danh muc, dung nhom, theo XE`() {
        assertEquals(SettingsGroup.CAST, SettingsCatalog.groupOf("cast_bubble_visible"))
        assertEquals("simple_cast_prefs", SettingsCatalog.CLUSTERNAV_KEYS["cast_bubble_visible"])
        assertEquals(
            com.byd.clusternav.launcher.ProfileScope.Scope.DEVICE,
            com.byd.clusternav.launcher.ProfileScope.scopeOf("cast_bubble_visible"),
            "cùng phạm vi với `cast_enabled` mà nó phụ thuộc — hai khoá của một tính năng không được lệch phạm vi",
        )
    }

    // ── 2 · ⚠⚠ Ẩn nút nổi KHÔNG được tắt ba tính năng đi cùng dịch vụ ───────────────────────────

    /**
     * Nhánh *"đã tắt"* phải **giữ dịch vụ sống**. Đây là bài quan trọng nhất của WP6: `stopSelf()` ở đây sẽ giết
     * driver DUY NHẤT của tự-chiếu-khi-nổ-máy (R1) + nhịp `repinEscapedCastApps` + nhịp `VmOverlayPosition`, và
     * **không có gì báo lỗi** — owner chỉ thấy "tự chiếu tự nhiên không chạy nữa".
     */
    @Test
    fun `nhanh an KHONG dung dich vu xuong va KHONG xin quyen`() {
        val onCreate = SourceRoots.body(service, "override fun onCreate()")
        val hidden = "BubblePresence.NEEDS_OVERLAY_PERMISSION"
        assertTrue(
            onCreate.contains("if (presence == $hidden) { requestOverlayIfMissing(); stopSelf(); return }"),
            "CHỈ nhánh thiếu quyền được đứng xuống — nhánh HIDDEN phải đi tiếp",
        )
        // Và bốn thứ còn lại của dịch vụ phải nằm SAU cổng đó, tức vẫn chạy khi nút nổi bị ẩn.
        listOf("coordinator.addStateListener(stateListener)", "handler.post(refresh)", "pipGuard.block(coordinator)", "autostart.dispatch(coordinator)")
            .forEach {
                assertTrue(onCreate.indexOf(it) > onCreate.indexOf(hidden)) {
                    "'$it' phải chạy kể cả khi nút nổi bị ẩn — nó không phải một phần của cái cửa sổ nổi"
                }
            }
    }

    @Test
    fun `nhip 2 giay doc lai cong tac nen gat la thay ngay`() {
        val loop = SourceRoots.body(service, "private val refresh = object : Runnable {")
        assertTrue(loop.contains("syncBubbleWindow()"), "nhịp phải đồng bộ cửa sổ với công tắc")
        assertTrue(
            loop.indexOf("syncBubbleWindow()") < loop.indexOf("refreshBubbleState()"),
            "đồng bộ TRƯỚC khi vẽ nhãn ⇒ cửa sổ vừa dựng có nhãn trạng thái đúng ngay trong cùng nhịp",
        )
        val sync = SourceRoots.body(service, "private fun syncBubbleWindow()")
        assertTrue(sync.contains("BubblePresence.SHOW -> if (bubble == null) showBubble()"), "chỉ dựng khi chưa có")
        assertTrue(sync.contains("BubblePresence.HIDDEN -> hideBubble()"), "tắt ⇒ gỡ cửa sổ")
    }

    /**
     * ⚠ Cầu **KHÔNG** được stop/start dịch vụ: `onCreate` chạy lại nghĩa là bộ tự-chiếu chạy lại ⇒ gạt một công
     * tắc trình bày lại đẩy một app lên cụm trước mặt người lái.
     */
    @Test
    fun `cau khong dung lai dich vu de ap cong tac`() {
        val setter = SourceRoots.body(bridge, "fun ClusterNavBridge.setCastBubbleVisible(")
        assertFalse(setter.contains("stopService"), "dựng lại dịch vụ = chạy lại tự-chiếu giữa chuyến")
        assertFalse(setter.contains("startForegroundService"), "cùng lý do trên")
    }

    // ── 3 · Gỡ cửa sổ phải gỡ CẢ bảng con neo vào nó ────────────────────────────────────────────

    @Test
    fun `hideBubble go ca bang con va nha tham chieu`() {
        val hide = SourceRoots.body(service, "private fun hideBubble()")
        assertTrue(hide.contains("submenu?.dismiss()"), "bảng con đặt theo toạ độ bong bóng — bỏ lại là thẻ lơ lửng")
        assertTrue(
            hide.indexOf("submenu?.dismiss()") < hide.indexOf("val view = bubble ?: return"),
            "phải dẹp bảng con TRƯỚC lượt `return` sớm: cửa sổ đã gỡ mà bảng con còn thì không ai dẹp nó nữa",
        )
        assertTrue(hide.contains("windowManager?.removeView(view)"), "gỡ cửa sổ thật")
        assertTrue(hide.contains("bubble = null") && hide.contains("params = null"), "nhả tham chiếu")
        assertTrue(hide.contains("::renderer.isInitialized"), "onStartCommand có thể tới đây trước khi onCreate xong")
    }

    // ── 4 · Trần 500 dòng cho mọi tệp lượt WP6 chạm tới (CLAUDE.md §4.1) ────────────────────────

    /**
     * `FloatingBubbleService.kt` vào lượt này đang **537 dòng** (nợ có trước). WP6 tách ba khối không thuộc bốn vai
     * mà KDoc của nó tự khai — autostart · PiP · thông báo — nên tệp về dưới trần, và ba tệp mới cũng phải dưới.
     */
    @Test
    fun `moi tep cua luot WP6 duoi tran 500 dong`() {
        listOf(
            "src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt",
            "src/main/java/com/byd/clusternav/modules/clustercast/BubbleAutostart.kt",
            "src/main/java/com/byd/clusternav/modules/clustercast/BubblePipGuard.kt",
            "src/main/java/com/byd/clusternav/modules/clustercast/BubbleForegroundNotice.kt",
            "src/main/java/com/byd/clusternav/modules/clustercast/BubbleRenderer.kt",
            "src/main/java/com/byd/clusternav/launcher/SettingsSectionsCast.kt",
            "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeCast.kt",
        ).forEach {
            val n = SourceRoots.text(it).lines().size
            assertTrue(n <= 500, "$it dài $n dòng — trần là 500")
        }
    }

    /**
     * Lượt tách phải **nối lại đủ**: hàm mới mà không có chỗ gọi là mã chết, còn ở đây tệ hơn — nó là *"tự chiếu
     * khi nổ máy không bao giờ chạy"*, im lặng.
     */
    @Test
    fun `ba khoi tach ra van duoc noi day`() {
        assertTrue(service.contains("BubbleAutostart(applicationContext, handler) { destroyed }"), "driver tự-chiếu")
        assertTrue(service.contains("autostart.detach(coordinator)"), "onDestroy phải gỡ bộ nghe còn treo")
        assertTrue(
            service.contains("startForeground(BubbleForegroundNotice.ID, BubbleForegroundNotice.build(this))"),
            "thông báo FGS — thiếu là RemoteServiceException sau ~5 giây",
        )
    }
}
