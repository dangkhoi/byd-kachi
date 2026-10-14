package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Soát Pass 2 R25 — khoá các đường sống/chết của widget xe 3D mà test off-device không chạy được (Filament/Android):
 * mỗi luật dưới đây là một lỗi ĐỎ khi gỡ bản sửa (lý do ghi trong thông báo).
 */
class Car3dLifecycleContractTest {

    private fun src(n: String) = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/$n"))
    private val renderer = src("Car3dRenderer.kt")
    private val widget = src("Car3dWidgetView.kt")
    private val source = src("Car3dModelSource.kt")

    @Test
    fun `be mat dung lai cung co thi van xin khung - tat bat man khong mat xe`() {
        // [ĐO máy ảo 14/10, 2/2] tắt/bật màn ⇒ swap chain mới, cùng cỡ ⇒ không onResized ⇒ xe không vẽ tới khi vuốt.
        val body = renderer.substringAfter("override fun onNativeWindowChanged(").substringBefore("override fun onDetachedFromSurface")
        assertTrue(body.contains("createSwapChain(") && body.indexOf("onSize(") > body.indexOf("createSwapChain("), "swap chain mới ⇒ xin khung")
    }

    @Test
    fun `cua so hien lai thi xin mot dot khung - tat bat man khong goi onVisibilityAggregated`() {
        // [ĐO máy ảo 14/10, 3/3 sau vá] tắt/bật màn: không vis-aggregated, không đổi bề mặt; chỉ có onWindowVisibilityChanged.
        // PERF 3.1.5: hai sự kiện độ hiện đi qua MỘT cổng [syncLive] — hiện lại (renderer còn) ⇒ xin khung; khuất ⇒ nhả (bài `Car3dLiveGateContractTest`).
        val body = widget.substringAfter("override fun onWindowVisibilityChanged(").substringBefore("override fun ") +
            widget.substringAfter("private fun syncLive()").substringBefore("\n    }\n")
        assertTrue(body.contains("VISIBLE") && body.contains("syncLive()") && body.contains("requestFrames("), "cửa sổ hiện ⇒ xin khung")
    }

    @Test
    fun `hop tu the cua kinh khong theo phan kinh da tut - cham va dich o lai o kinh`() {
        // 3.1.5 [ĐO máy ảo 14/10] `win-lr` 100 % ⇒ chấm y 412 px trên da cửa (cửa 466): hộp tư thế cộng độ trượt kính ⇒ đích chạm
        // + chấm đi xuống theo phần kính đã khuất trong cửa. Hộp tư thế chỉ gồm bản lề.
        val body = renderer.substringAfter("private fun nodeBox(").substringBefore("\n    }\n")
        assertFalse(body.contains("slideOf"), "nodeBox không được cộng độ trượt kính")
        assertTrue(body.contains("swingOf"), "vẫn theo bản lề (cửa/nắp mở)")
    }

    @Test
    fun `khong goi Box set - native nSet abort ca HOME`() {
        // [ĐO máy ảo 13/10 19:43] Box.set → static native nSet ⇒ "JNI DETECTED ERROR … invalid jobject" ⇒ tiến trình chết.
        // nUnionSelf / nTranslateTo cùng dạng static-native-trả-jobject ⇒ cấm luôn. Đọc hộp bằng getter/getAxisAlignedBoundingBox.
        val code = (renderer + widget).lines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") }.joinToString("\n")
        assertFalse(Regex("""\bbox\.set\(|Box\([^)]*\)\.set\(|\.unionSelf\(|\.translateTo\(""").containsMatchIn(code))
    }

    @Test
    fun `engine huy ca khi dung hong giua chung va khi huy hong giua chung`() {
        assertTrue(renderer.contains("private constructor("), "chỉ dựng qua create() — không Engine mồ côi khi hàm dựng ném")
        val create = renderer.substringAfter("fun create(").substringBefore("\n        }")
        assertTrue(create.contains("catch (t: Throwable)") && create.contains("e.destroy()"), "dựng hỏng ⇒ huỷ Engine")
        val destroy = renderer.substringAfter("    fun destroy() {").substringBefore("\n    }\n")
        assertTrue(Regex("""finally \{\s*engine\.destroy\(\)""").containsMatchIn(destroy), "Engine.destroy trong finally")
        assertTrue(widget.contains("Car3dRenderer.create("), "widget dựng qua create()")
    }

    @Test
    fun `nap dung mot luong mot luc va don rac va kiem cho trong`() {
        assertTrue(source.contains("fun load(ctx: Context): Loaded? = synchronized(lock)"), "single-flight: hai luồng cùng ghi car3d-src.tmp ⇒ SIGBUS")
        assertTrue(source.contains("Car3dCache.owned("), "dọn cả .glb.part / .tmp của lần dựng bị giết")
        val alloc = source.substringAfter("Car3dGlb.write(").substringBefore("force()")
        assertTrue(alloc.indexOf("Car3dCache.roomFor(") in 0 until alloc.indexOf("FileChannel.MapMode.READ_WRITE"),
            "kiểm chỗ trống TRƯỚC khi ánh xạ ghi tệp thưa (đĩa đầy = SIGBUS)")
        assertTrue(widget.contains("Car3dModelSource.invalidate("), "tệp đệm gltfio từ chối ⇒ bỏ, không lùi 2D vĩnh viễn")
    }

    @Test
    fun `moi duong cham Filament co ranh gioi va cu chi do duoc bo khi roi`() {
        val fill = widget.substringAfter("    fun fill(d: WidgetData) {").substringBefore("\n    }\n")
        assertTrue(fill.contains("guarded(\"fill\")"), "nhịp đổ số chạm Filament ⇒ phải qua ranh giới lùi 2D")
        assertTrue(widget.contains("taps.changed = { guarded("), "cú chạm chạm Filament ⇒ ranh giới")
        val stop = widget.substringAfter("    private fun stop() {").substringBefore("\n    }\n")
        assertTrue(stop.contains("endGesture()"), "rời cửa sổ giữa lúc vuốt ⇒ dragging kẹt ⇒ vẽ mãi khi rảnh")
    }

    @Test
    fun `mot widget 3D mot luc, o nho thi 2D, tra cho khi dung va khi lui`() {
        // Soát O-3D-O1 (POLISH2): hai ô `w_car3d` ⇒ hai Engine + hai lượt dựng đệm; bố cục cũ ô nhỏ ⇒ dựng Filament trong ô chật.
        val start = widget.substringAfter("    private fun start() {").substringBefore("\n    }\n")
        assertTrue(src("Car3dDevice.kt").contains("Car3dSeat.fits(id") && start.indexOf("slotFits()") in 0 until start.indexOf("Car3dRenderer.create("), "cổng cỡ ô TRƯỚC khi dựng Engine")
        assertTrue(start.indexOf("Car3dSeat.shared.claim(this)") in 0 until start.indexOf("Car3dRenderer.create("), "xin chỗ TRƯỚC khi dựng Engine")
        val stop = widget.substringAfter("    private fun stop() {").substringBefore("\n    }\n")
        val fall = widget.substringAfter("    private fun fallBack(why: String) {").substringBefore("\n    }\n")
        assertTrue(stop.contains("Car3dSeat.shared.release(this)") && fall.contains("Car3dSeat.shared.release(this)"), "trả chỗ khi dừng/lùi 2D")
    }
}
