package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PERF 3.1.5 · owner 14/10 *"car 3D không bao giờ chạy nền, chỉ chạy trên khung hoặc không chạy, để tiết kiệm RAM"* — khoá cổng sống
 * của widget xe 3D (Filament/Android, không chạy được off-device). Mỗi luật ĐỎ khi gỡ bản sửa; số đo ở
 * `docs/diagnostics/perf-315-2026-10-14.md` (PSS khi khuất, điểm ảnh xe sau khi hiện lại, thời gian nạp lại).
 */
class Car3dLiveGateContractTest {

    private fun src(n: String) = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/$n"))
    private val widget = src("Car3dWidgetView.kt")
    private val renderer = src("Car3dRenderer.kt")
    private val gate = widget.substringAfter("private fun syncLive()").substringBefore("\n    }\n")

    @Test
    fun `ca ba nguon kh uat di qua MOT cong - va cong nha Engine khi khuat`() {
        // [ĐO máy ảo 14/10] trước: khuất (rời HOME · ô khác bung R30) chỉ huỷ khung ⇒ Engine + GLB sống ngầm (PSS khuất ≈ lúc hiện).
        for (hook in listOf("override fun onWindowVisibilityChanged(", "override fun onVisibilityAggregated(")) {
            assertTrue(widget.substringAfter(hook).substringBefore("\n").contains("syncLive()"), "$hook phải đi qua syncLive")
        }
        assertTrue(widget.contains("ScreenLit.watch(") && widget.substringAfter("ScreenLit.watch(").substringBefore("\n").contains("syncLive()"),
            "màn tắt ([ĐO] SLEEP không đổi độ hiện cửa sổ) phải đi qua syncLive")
        assertTrue(gate.contains("!windowHidden") && gate.contains("!viewHidden") && gate.contains("!screenOff"), "sống = cửa sổ hiện ∧ view hiện ∧ màn sáng")
        assertTrue(gate.substringBefore("return").contains("stop()"), "khuất ⇒ stop() (huỷ renderer + Engine), không chỉ huỷ khung")
        assertTrue(gate.contains("start()"), "hiện lại ⇒ start() nạp lại từ tệp đệm")
        assertTrue(gate.contains("WidgetFallbacks.shared.shown(this, live)"), "khuất ⇒ nhu cầu HAL của widget về 0")
    }

    @Test
    fun `dung thi tra trang cho he va go nghe man khi roi cua so`() {
        val stop = widget.substringAfter("private fun stop()").substringBefore("\n    }\n")
        assertTrue(stop.contains("it.destroy()") && stop.contains("KachiMem.trim("), "stop: huỷ renderer rồi purge (free không tự trả trang)")
        // [ĐO máy ảo 14/10] màn tắt→sáng: lượt hiện thứ hai (ScreenLit báo 2 lần) gọi applyPaint với `model` CŨ trước khi nạp xong ⇒
        // đánh dấu đã sơn ⇒ xe mới nạp giữ màu trắng mặc định. `model` thuộc đời renderer ⇒ stop phải xoá.
        assertTrue(stop.contains("model = null"), "stop: xoá model cùng renderer")
        val detach = widget.substringAfter("override fun onDetachedFromWindow()").substringBefore("\n    }\n")
        assertTrue(detach.contains("unlit?.invoke()"), "rời cửa sổ ⇒ gỡ nghe màn (không rò receiver)")
    }

    @Test
    fun `dung lai tren be mat co san thi dat co - khong o trong sau khi hien lai`() {
        // [ĐO bytecode filament-android 1.77.3 `UiHelper$TextureViewHandler.<init>`] bề mặt có sẵn ⇒ onSurfaceTextureAvailable(0, 0)
        // ⇒ viewport 0 ⇒ ô trống ([ĐO máy ảo 14/10] 3/4 lượt rời-HOME-rồi-về trước khi vá).
        val ctor = renderer.substringAfter("ui.attachTo(tv)").substringBefore("\n    }\n")
        assertTrue(ctor.contains("tv.isAvailable") && ctor.contains("onResized(tv.width, tv.height)"), "bề mặt có sẵn ⇒ tự đặt cỡ")
    }
}
