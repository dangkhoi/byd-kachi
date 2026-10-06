package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.90 · R8/R9/R10 — hợp đồng NỐI DÂY (CLAUDE.md §8: hàm mới phải có call site thật). Luật thuần đã khoá ở `:core`
 * (`ClusterLayerPauseTest`, `ClusterThemeLayerPauseTest`, `ClusterOverlayDisplayTest`, `VietMapWidgetRestorePlanTest`); ở đây khoá
 * rằng `:app` thật sự gọi chúng — không có Robolectric nên đọc source qua ranh giới (khuôn `SpeedBadgeLifecycleContractTest`).
 */
class ClusterLayerWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    @Test
    fun `hop dong VM_BUBBLE_VIS - action, goi tuong minh, extra show, khong appop, khong gate Cast ON`() {
        val vis = code("src/main/java/com/byd/clusternav/VmBubbleVisibility.kt")
        assertTrue(vis.contains("const val ACTION = \"com.byd.clusternav.VM_BUBBLE_VIS\""), "action cố định với bản mod")
        assertTrue(vis.contains("const val EXTRA_SHOW = \"show\""), "extra boolean `show`")
        assertTrue(vis.contains("private const val VIETMAP_PKG = \"vn.vietmap.live\""))
        assertTrue(vis.contains("Intent(ACTION).setPackage(VIETMAP_PKG).putExtra(EXTRA_SHOW, show)"), "broadcast tường minh")
        assertTrue(vis.contains("ClusterLayerPause.bubbleWanted(Prefs.vmBubbleHidden(app), ClusterOverlayDisplays.paused)"))
        // Review 2.90 (an toàn hiện trường): "ẩn" KHÔNG được suy từ `vm_bubble_enabled` (mặc định TẮT) — người chưa từng bật
        // công tắc mà nâng cấp lên mod mới sẽ mất bóng. Chỉ cờ `vm_bubble_hidden` (mặc định false) quyết.
        assertFalse(vis.contains("vmBubbleEnabled"), "VmBubbleVisibility không đọc công tắc tự mở VietMap")
        val prefs = code("src/main/java/com/byd/clusternav/PrefsBadge.kt")
        assertTrue(prefs.contains("getBoolean(K_VM_BUBBLE_HIDDEN, false)"), "mặc định HIỆN")
        val exec = code("src/main/java/com/byd/clusternav/modules/clustercast/ClusterLayerExecutor.kt")
        assertTrue(exec.contains("override fun bubbleHiddenByUser(): Boolean = Prefs.vmBubbleHidden(app)"), "TRẢ cụm ⇒ hiện trừ khi đã ẩn")
        assertFalse(vis.contains("appops"), "không ẩn bằng appop (spec §4.3)")
        assertFalse(vis.contains("castOn"), "bóng nằm trên cụm cả khi tắt chiếu — không gate Cast ON")
        val pos = code("src/main/java/com/byd/clusternav/VmOverlayPosition.kt")
        assertTrue(pos.contains("const val ACTION = \"com.byd.clusternav.VM_BUBBLE_POS\""), "VM_BUBBLE_POS không đổi")
    }

    @Test
    fun `cong tac bong - TAT va BAT deu gui VM_BUBBLE_VIS ngay, BAT van tu mo VietMap`() {
        val bridge = code("src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt")
        val fn = bridge.substring(bridge.indexOf("fun setVmBubbleEnabled(on: Boolean)")).substringBefore("\n    }")
        assertTrue(fn.contains("Prefs.setVmBubbleEnabled(app, on)"))
        assertTrue(fn.contains("Prefs.setVmBubbleHidden(app, !on)"), "TẮT ⇒ hidden=true; BẬT ⇒ hidden=false")
        assertTrue(fn.indexOf("setVmBubbleHidden") < fn.indexOf("VmBubbleVisibility.apply"), "ghi cờ trước khi gửi")
        assertTrue(fn.contains("VmBubbleVisibility.apply(app,") && fn.contains("force = true"), "đổi công tắc ⇒ gửi ngay")
        assertTrue(fn.contains("if (on) VietMapAutostartService.startForAppOpen(app)"), "BẬT giữ tự mở VietMap")
    }

    @Test
    fun `giu an - gui lai sau tu mo VietMap, nhip lam tuoi, ap lai ho so, do cum`() {
        assertTrue(code("src/main/java/com/byd/clusternav/VietMapAutostart.kt").contains("VmBubbleVisibility.apply(app, \"sau lượt tự mở VietMap\", force = true)"))
        assertTrue(code("src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt").contains("VmBubbleVisibility.keepHidden(applicationContext)"))
        assertTrue(code("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeReapply.kt").contains("step(\"bubble.vis\")"))
        val rt = code("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt")
        assertTrue(rt.contains("ClusterOverlayDisplays.publishCastDisplay(id)"), "id cụm sống tới lớp phủ trong tiến trình")
        assertTrue(rt.contains("VmBubbleVisibility.apply(app, \"dò cụm id=\$id\")"))
        assertTrue(rt.contains("clusterLayers = com.byd.clusternav.modules.clustercast.ClusterLayerExecutor(app)"), "bộ thi hành dọn/trả nối vào coordinator")
    }

    @Test
    fun `don tra cum - coordinator TRA trong finally, cong theme DON khi FOREIGN hoac BUBBLE`() {
        val ops = code("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinatorOps.kt")
        val guarded = ops.substring(ops.indexOf("internal fun SimpleCastCoordinator.openProjectionGuarded()")).substringBefore("\n}\n")
        assertTrue(guarded.contains("openProjectionBody()") && guarded.contains("finally") && guarded.contains("themeGuard.resumeLayers(liveDisplayId)"))
        assertTrue(code("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinator.kt").contains("openProjectionGuarded()"))
        val guard = code("src/main/java/com/byd/clusternav/modules/clustercast/simplified/ClusterThemeGuard.kt")
        assertTrue(guard.contains("ClusterLayerPause.pausable(first.tasks, first.windows, it, selfPackage)"))
        assertTrue(guard.contains("first = pauseAndReread(op, first, pause)"))
        val exec = code("src/main/java/com/byd/clusternav/modules/clustercast/ClusterLayerExecutor.kt")
        assertTrue(exec.contains("ClusterOverlayDisplays.setPaused(true)") && exec.contains("ClusterOverlayDisplays.setPaused(false)"))
        assertFalse(exec.contains("\"am ") || exec.contains("\"wm "), "dọn lớp của Kachi trong tiến trình — không lệnh am/wm")
        // Review Pass 3 [P2]: chờ luồng chính không nuốt cờ ngắt; lưới an toàn tự TRẢ khi lượt mở kẹt quá trần.
        assertTrue(exec.contains("Thread.currentThread().interrupt()"), "flushMain giữ cờ ngắt của executor")
        assertTrue(exec.contains("main.postDelayed(failsafe, PAUSE_MAX_MS)") && exec.contains("main.removeCallbacks(failsafe)"))
        assertTrue(guard.contains("openScope && !paused"), "chỉ DỌN trong lượt MỞ (có finally TRẢ)")
    }

    @Test
    fun `Cai dat - mod cu noi dung viec can lam`() {
        val st = code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsCastStyle.kt")
        assertTrue(st.contains("bridge.castThemeBubbleOldMod()") && st.contains("R.string.kachi_cast_style_bubble_old_mod"))
    }

    @Test
    fun `display cum cua camera dung chung bo chon - khong VD rieng tu, khong id 0 dau tien`() {
        val cam = code("src/main/java/com/byd/clusternav/launcher/camera/CameraOverlayView.kt")
        assertTrue(cam.contains("ClusterOverlayDisplays.resolve(dm)"))
        assertTrue(cam.contains("if (ClusterOverlayDisplays.paused) return null"), "đang dọn cụm ⇒ camera mới rơi về màn chính")
        assertFalse(cam.contains("dm.displays.firstOrNull { it.displayId != 0 }"), "đường rơi về display bất kỳ ≠ 0 (có thể là ô Kachi) đã gỡ")
    }

    @Test
    fun `widget VietMap tu lanh - luat thuan + dung lai khi goi doi`() {
        val br = code("src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt")
        assertTrue(br.contains("VietMapWidgetRestorePlan.decide("), "restoreBoundViews đi qua luật thuần")
        assertTrue(br.contains("VietMapWidgetRestorePlan.Action.DROP_REBIND"))
        assertTrue(br.contains("if (action != Intent.ACTION_PACKAGE_REMOVED) { restoreBoundViews(); autoBindMissing() }"), "gói VietMap cài lại ⇒ dựng + bind lại")
        assertTrue(br.contains("main.postDelayed(packageSettleRetry, PKG_SETTLE_MS)") && br.contains("main.removeCallbacks(packageSettleRetry)"),
            "thử lành lại một lần sau khi AppWidgetService kịp nhận gói mới")
    }
}
