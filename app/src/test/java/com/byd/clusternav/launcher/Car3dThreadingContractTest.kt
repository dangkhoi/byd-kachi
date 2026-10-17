package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.14 `PERF-SL6-3113` R3/R4 — khoá luật luồng của widget xe 3D (Filament không chạy được off-device).
 *
 * [ĐO log Seal 17/10, 3.1.11] `Engine.create` + gltfio `createAsset`/`loadResources` TRÊN LUỒNG CHÍNH = 788–1509 ms mỗi lượt dựng ⇒ bỏ
 * 62–139 khung. Nay mọi lời gọi Filament ở `Car3dScene.kt`, chạy trên luồng sở hữu car3d-gl ([Car3dOwnerThread]); tay cầm
 * `Car3dRenderer.kt` chỉ XẾP việc dựng/nạp/huỷ (`GL.post`). Mỗi luật ĐỎ khi đưa lượt dựng về luồng chính.
 */
class Car3dThreadingContractTest {

    private fun src(n: String) = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/$n"))
    private val renderer = src("Car3dRenderer.kt")
    private val scene = src("Car3dScene.kt")
    private val widget = src("Car3dWidgetView.kt")
    private fun code(s: String) = s.lines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") }.joinToString("\n")
    private fun body(s: String, head: String, end: String = "\n    }\n") = s.substringAfter(head).substringBefore(end)

    @Test
    fun `createAsset loadResources Engine create chi o Car3dScene - khong file nao khac cua app`() {
        val root = SourceRoots.path("src/main/java")
        val hits = Files.walk(root).use { w ->
            w.filter { it.extension == "kt" || it.extension == "java" }.toList()
                .filter { p -> Regex("""\.createAsset\(|\.loadResources\(|Engine\.create\(|asyncBeginLoad\(""").containsMatchIn(code(Files.readString(p))) }
                .map { it.name }.toSet()
        }
        assertEquals(setOf("Car3dScene.kt"), hits, "lời gọi dựng Filament nằm ngoài lớp sống trên car3d-gl: $hits")
    }

    @Test
    fun `lop Filament tu kiem luong - dung, nap, huy, ham dung`() {
        for (head in listOf("fun load(glb: ByteBuffer): Built? {", "    fun destroy() {", "fun create(medium: Boolean, night: Boolean): Car3dScene {", "    init {")) {
            assertTrue(body(scene, head).trimStart().startsWith("Car3dRenderer.GL.check()"), "`$head` phải mở đầu bằng GL.check() (fail-loud khi gọi nhầm luồng)")
        }
    }

    @Test
    fun `tay cam chi xep viec dung nap huy vao car3d-gl - luong chinh khong cho`() {
        val create = body(renderer, "fun create(tv: TextureView", "\n        }\n")
        assertTrue(create.indexOf("GL.post {") in 0 until create.indexOf("Car3dScene.create("), "Engine + cảnh dựng trong GL.post (không chờ)")
        val load = body(renderer, "fun load(glb: ByteBuffer")
        assertTrue(load.indexOf("GL.post {") in 0 until load.indexOf("sc.load(glb)"), "gltfio nạp trong GL.post (không chờ)")
        assertTrue(load.contains("MAIN.post {") && load.contains("if (dead) return@post"), "kết quả về luồng chính bằng post; đã huỷ ⇒ bỏ")
        val destroy = body(renderer, "fun destroy(onFreed: () -> Unit) {")
        assertTrue(destroy.indexOf("GL.post {") in 0 until destroy.indexOf("s?.destroy()") && destroy.contains("MAIN.post(onFreed)"),
            "Engine huỷ trên car3d-gl (Engine::shutdown đòi đúng luồng — Engine.cpp:606), trả trang sau khi xong")
        assertFalse(Regex("""GL\.call\s*\{[^}]*(Car3dScene\.create|\.load\(glb\)|\.destroy\(\))""").containsMatchIn(code(renderer)), "không chờ đồng bộ một lượt dựng/huỷ")
    }

    @Test
    fun `luong chinh khong cho car3d-gl - cau hoi tu bang tra, lenh va ve xep hang`() {
        // [ĐO máy ảo 2 lõi 17/10] bản nhảy-đồng-bộ-mỗi-câu: dựng neo 201 bước · 234 ms chờ; 2 khung 102 bước · 136 ms chờ.
        val c = code(renderer)
        val calls = Regex("""GL\.call\s*\{""").findAll(c).count()
        assertEquals(2, calls, "chỉ hai callback bề mặt (đổi/huỷ swap chain) được chờ car3d-gl")
        for (cb in listOf("override fun onNativeWindowChanged(", "override fun onDetachedFromSurface()")) assertTrue(body(renderer, cb, "\n    }").contains("GL.call"), cb)
        for (q in listOf("fun visibleBounds()", "fun anchor(", "fun worldBox(", "fun target(", "fun hasPart(", "fun hasHinge(", "fun partNames()"))
            assertTrue(renderer.substringAfter(q).substringBefore("\n").contains("idx?."), "$q phải trả lời từ bảng tra trên luồng chính")
        val cmd = renderer.substringAfter("private fun cmd(").substringBefore("\n")
        assertTrue(cmd.contains("GL.post {") && cmd.contains("drawAgain = true"), "lệnh đổi cảnh xếp hàng, không chờ; khung đang chờ ⇒ vẽ thêm")
        val render = body(renderer, "fun render(frameTimeNanos: Long): Boolean {")
        assertTrue(render.contains("if (drawQueued) return true") && render.contains("GL.post {"), "vẽ xếp hàng + gộp (không bao giờ dồn khung)")
        // Soát Pass 15 (P1): khung ném trên car3d-gl mà không về MAIN ⇒ `drawQueued` kẹt true ⇒ xe đứng hình im lặng mãi.
        assertTrue(render.indexOf("catch (t: Throwable)") in 0 until render.indexOf("MAIN.post {"), "khung ném ⇒ vẫn về MAIN trả drawQueued (coi là khung hụt)")
        val attach = body(renderer, "private fun attach(index: Car3dIndex) {")
        assertTrue(attach.indexOf("idx = index") in 0 until attach.indexOf("ui.attachTo(tv)"), "UiHelper chỉ gắn sau khi nạp xong (callback bề mặt không gặp lượt dựng)")
        assertTrue(body(renderer, "fun load(glb: ByteBuffer").contains("attach(it.index)"), "nạp xong mới gắn bề mặt")
    }

    @Test
    fun `widget khong cham Filament truc tiep va dung so loi cua tien trinh`() {
        val w = code(widget)
        assertFalse(Regex("""Engine\.|Car3dScene\.create|\.createAsset\(""").containsMatchIn(w), "widget chỉ nói chuyện qua tay cầm Car3dRenderer")
        assertTrue(w.contains("r.load(l.glb, l.model.variants) {"), "widget nhận kết quả nạp bằng callback (không chờ)")
        // R4: dựng lại ô (view mới) không được đếm lỗi lại từ 0 ⇒ trần 2 · 8 · 32 s là của tiến trình (Car3dHoldTest khoá phần thuần).
        assertTrue(w.contains("Car3dHold(budget = Car3dHold.Faults.PROCESS)"), "widget dùng sổ lỗi chung của tiến trình")
    }
}
