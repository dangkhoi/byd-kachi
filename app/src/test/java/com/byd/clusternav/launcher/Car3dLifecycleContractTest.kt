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
