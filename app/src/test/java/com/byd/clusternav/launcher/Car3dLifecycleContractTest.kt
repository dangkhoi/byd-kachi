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
        // 3.1.10: dọn qua `Car3dCache.stale` = mọi tệp của widget (`owned`: .glb.part / .tmp của lần dựng bị giết) TRỪ bản nhớ khoang.
        assertTrue(source.contains("Car3dCache.stale("), "dọn cả .glb.part / .tmp của lần dựng bị giết")
        assertTrue(source.contains("Car3dCabin.apply(mesh, lid, cabinMemo(ctx))"), "lượt khoang đi qua bản nhớ theo khoá đầu vào")
        val alloc = source.substringAfter("Car3dGlb.write(").substringBefore("force()")
        assertTrue(alloc.indexOf("Car3dCache.roomFor(") in 0 until alloc.indexOf("FileChannel.MapMode.READ_WRITE"),
            "kiểm chỗ trống TRƯỚC khi ánh xạ ghi tệp thưa (đĩa đầy = SIGBUS)")
        assertTrue(widget.contains("Car3dModelSource.invalidate("), "tệp đệm gltfio từ chối ⇒ bỏ, lượt thử lại dựng từ lưới")
    }

    @Test
    fun `moi duong cham Filament co ranh gioi va cu chi do duoc bo khi roi`() {
        val fill = widget.substringAfter("    fun fill(d: WidgetData) {").substringBefore("\n    }\n")
        assertTrue(fill.contains("guarded(\"fill\")"), "nhịp đổ số chạm Filament ⇒ phải qua ranh giới (park + thử lại)")
        assertTrue(widget.contains("taps.changed = { guarded("), "cú chạm chạm Filament ⇒ ranh giới")
        val stop = widget.substringAfter("    private fun stop() {").substringBefore("\n    }\n")
        assertTrue(stop.contains("endGesture()"), "rời cửa sổ giữa lúc vuốt ⇒ dragging kẹt ⇒ vẽ mãi khi rảnh")
    }

    @Test
    fun `mot widget 3D mot luc, o nho thi cho, tra cho khi dung va khi thoi ve`() {
        // Soát O-3D-O1 (POLISH2): hai ô `w_car3d` ⇒ hai Engine + hai lượt dựng đệm; bố cục cũ ô nhỏ ⇒ dựng Filament trong ô chật.
        val start = widget.substringAfter("    private fun start() {").substringBefore("\n    }\n")
        assertTrue(src("Car3dDevice.kt").contains("Car3dSeat.fits(id") && start.indexOf("slotFits()") in 0 until start.indexOf("Car3dRenderer.create("), "cổng cỡ ô TRƯỚC khi dựng Engine")
        assertTrue(start.indexOf("Car3dSeat.shared.claim(this, ::liveNow, wake)") in 0 until start.indexOf("Car3dRenderer.create("), "xin chỗ (kèm độ hiện + hàm đánh thức) TRƯỚC khi dựng Engine")
        val stop = widget.substringAfter("    private fun stop() {").substringBefore("\n    }\n")
        val park = widget.substringAfter("    private fun park(w: Car3dHold.Why, detail: String) {").substringBefore("\n    }\n")
        assertTrue(stop.contains("Car3dSeat.shared.release(this)") && park.contains("Car3dSeat.shared.release(this)"), "trả chỗ khi dừng/thôi vẽ")
        assertTrue(widget.substringAfter("override fun onDetachedFromWindow()").substringBefore("\n    }\n").contains("Car3dSeat.shared.leave(this)"),
            "rời cửa sổ ⇒ ra khỏi hàng chờ (không đánh thức view chết)")
    }

    @Test
    fun `318 khong bao gio lui bang 2D - moi duong hong qua park, chi ly do vinh vien moi chot`() {
        // Owner 15/10 (3.1.6) *"có nhiều lúc đã lên 3D rồi, nhưng lại bị trả về 2D"* → *"không bao giờ lùi về 2D … không chạy thì bỏ qua"*.
        // [ĐO máy ảo 15/10, 3.1.6] tệp đệm dở ⇒ `lùi bảng 2D: gltfio …` kẹt qua tắt/bật màn; hai ô w_car3d ⇒ ô chính 2D kẹt cả khi bung.
        val code = widget.lines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") }.joinToString("\n")
        for (gone in listOf("fallBack(", "fallen", "GroupTiles", "removeAllViews()", "lùi bảng 2D"))
            assertFalse(code.contains(gone), "widget 3D không còn đường đổi sang bảng 2D: thấy `$gone`")
        assertFalse(src("WidgetViews.kt").substringAfter("private fun car3d(").substringBefore("\n\n").contains("GroupTiles"), "WidgetViews không đưa bản lùi 2D")
        // Thoáng qua (ô nhỏ · thua chỗ · lỗi) vs vĩnh viễn (máy · thư viện · không mô hình · mô hình rỗng) — phân loại ở :core Car3dHold (Car3dHoldTest).
        val parks = Regex("""park\(Car3dHold\.Why\.(\w+)""").findAll(code).map { it.groupValues[1] }.toSet()
        assertTrue(parks == setOf("SLOT", "SEAT", "INCAPABLE", "NO_LIBS", "FAULT", "NO_MODEL", "EMPTY_MODEL"), "mọi lý do đi qua park: $parks")
        val onLoaded = widget.substringAfter("private fun onLoaded(").substringBefore("\n    }\n")
        assertTrue(onLoaded.contains("res.getOrElse { return park(Car3dHold.Why.FAULT"), "nạp NÉM (đĩa đầy, đọc hỏng) = thoáng qua ⇒ thử lại, không chốt 'không mô hình'")
        assertTrue(source.substringAfter("private fun loadLocked(").substringBefore("\n    }\n").contains("failure?.let { throw it }"),
            "Car3dModelSource không được nuốt lỗi lúc chạy thành null (null = đời xe không có mô hình)")
        val start = widget.substringAfter("    private fun start() {").substringBefore("\n    }\n")
        assertTrue(start.trimStart().startsWith("if (!hold.canStart()) return"), "chỉ lý do vĩnh viễn / quá trần lỗi mới chặn dựng lại")
        val park = widget.substringAfter("    private fun park(w: Car3dHold.Why, detail: String) {").substringBefore("\n    }\n")
        assertTrue(park.contains("main.postDelayed(retry, it)") && park.contains("live3d(false)") && park.contains("taps.hide()"), "park: khung trống + hẹn thử lại")
        // Soát Pass 8 [P3]: thôi hẳn (vĩnh viễn HOẶC quá trần lỗi) ⇒ dòng "không khả dụng" + ra khỏi hàng chờ Engine — không khung trống câm.
        assertTrue(park.contains("note.visibility = if (!hold.canStart()) VISIBLE") && park.contains("if (!hold.canStart()) Car3dSeat.shared.leave(this)"),
            "quá trần lỗi cũng bày dòng không khả dụng và rời hàng chờ (owner: coi như không khả thi)")
        assertTrue(widget.substringAfter("private fun live3d(on: Boolean)").substringBefore("\n    }\n").contains("WidgetFallbacks.shared.rendering(this, on)"),
            "không vẽ ⇒ nhu cầu HAL về 0")
    }
}
