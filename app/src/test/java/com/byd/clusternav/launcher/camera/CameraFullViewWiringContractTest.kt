package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.92 CAMERA-FULL-VIEW — bài canh DÂY NỐI của `:app` (kiểu hình · áp ngay · nút Khung thô) ═════════════════
 *
 * Spec `docs/specs/kachi-292-camera-full-view.html` R1 · R6 · R8. Toán (vừa khung, κ, bộ uniform theo kiểu) đã có test
 * bằng số ở `:core` (`CameraViewPlanTest`, `CameraDewarpKappaTest`); ở đây canh những mắt xích mà gỡ đi thì build vẫn xanh
 * và không bài `:core` nào đỏ (CLAUDE.md §8 — bẫy `CastShell.evictVd`: hàm viết cẩn thận mà không ai gọi):
 *
 *  1. controller quy kiểu theo **đường vẽ** TRƯỚC khi dựng phiên (TV không có shader ⇒ Thẳng rộng thành Gương cầu) và
 *     đưa CÙNG một kiểu xuống cả ba chỗ dùng (vùng cắt · uniform GL · tỉ lệ TV);
 *  2. đổi kiểu/thu phóng khi khung đang hiện ⇒ dựng lại NGAY, theo đường *đổi bên* (không chạm HOLD), không hiện thì
 *     không bật khung bất ngờ;
 *  3. hai nút *📷 Khung thô* đi đúng đường xem thử → chụp → ghi → ĐÓNG, chỉ đọc cấu hình người lái;
 *  4. bản sao vào Thư viện ảnh qua `MediaStore` (ngoài cây `DiagStorageCap` dọn).
 */
class CameraFullViewWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }
    // 2.93: lượt đọc pref + quy kiểu của MỘT phiên dời sang `CameraSessionSpec` (spec kachi-293-cam §4.2).
    private val spec by lazy { app("launcher/camera/CameraSessionSpec.kt") }
    private val perCam by lazy { app("PrefsCameraPerCam.kt") }
    private val bridge by lazy { app("launcher/ClusterNavBridgeAutomation.kt") }
    private val raw by lazy { app("launcher/camera/CameraRawCapture.kt") }
    private val files by lazy { app("launcher/camera/CameraFrameFiles.kt") }
    private val diag by lazy { app("modules/clustercast/DiagActivity.kt") }

    /** Lời gọi [call] kèm ĐỦ danh sách đối số (khớp ngoặc) — `SourceRoots.body` cắt THÂN hàm, không cắt lời gọi. */
    private fun args(src: String, call: String): String {
        val at = src.indexOf(call)
        require(at >= 0) { "không tìm thấy '$call' — bài đang quét vùng không tồn tại" }
        var depth = 0
        for (i in src.indexOf('(', at) until src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return src.substring(at, i + 1)
            }
        }
        error("lời gọi '$call' không đóng ngoặc")
    }

    /** (1) R6 — kiểu đã QUY theo đường vẽ, đọc đường vẽ trước, và cả ba chỗ dùng nhận đúng biến ấy. */
    @Test fun `controller quy kieu theo duong ve roi dua cung mot kieu xuong ca ba cho`() {
        val open = SourceRoots.body(controller, "private fun openSession(")
        // 2.93: đọc + quy ở `CameraSessionSpec.read` (MỘT lượt mỗi phiên, cho cả bốn camera) — controller nhận kết quả.
        assertTrue("CameraSessionSpec.read(appCtx, which)" in open, "mỗi phiên phải đọc cấu hình của ĐÚNG camera đang mở")
        assertTrue("val mode = s.mode" in open, "controller dùng kiểu ĐÃ quy của lượt đọc phiên")
        val read = SourceRoots.body(spec, "fun read(ctx: Context, which: CameraWhich): CameraSessionSpec?")
        val render = read.indexOf("Prefs.cameraRender(ctx)")
        val quantize = read.indexOf("CameraViewMode.effective(")
        assertTrue(render >= 0 && quantize > render, "phải đọc đường vẽ TRƯỚC rồi quy kiểu theo nó (TV không có shader)")
        assertTrue("Prefs.cameraProjectionOf(ctx, which)" in read, "kiểu hình đọc từ pref (riêng camera → chung) mỗi lượt dựng")
        assertTrue(", cameraProjection(ctx))" in SourceRoots.body(perCam, "fun Prefs.cameraProjectionOf("),
            "*Theo chung* của kiểu riêng phải lùi về ĐÚNG pref chung `camera_projection`")
        val crops = args(read, "CameraViewPlan.crops(")
        assertTrue("mode = mode" in crops, "vùng cắt khung/nội dung phải theo kiểu ĐÃ quy")
        val gl = args(open, "Prefs.cameraGlUniforms(")
        assertTrue("mode = mode" in gl && "crops = crops" in gl, "uniform GL phải theo kiểu ĐÃ quy + đúng vùng cắt vừa suy")
        assertTrue("CameraViewPlan.tvScale(mode," in open, "tỉ lệ ma trận TV phải theo kiểu ĐÃ quy")
        assertTrue("letterbox = CameraViewMode.fullView(mode)" in open, "lớp video phải biết kiểu trọn dải để vừa khung")
        assertTrue("(chọn \$asked)" in open, "log phải nói cả kiểu người lái chọn khi đường vẽ ép kiểu khác (R6)")
    }

    /** (2) R1 — áp ngay: hai setter của cầu Cài đặt cùng gọi một cửa, cửa ấy chỉ chạm khi controller đã dựng. */
    @Test fun `doi kieu hoac thu phong thi dung lai khung dang hien, khong cham HOLD`() {
        // 2.93 wave 2C · PREFS-SET-CAM-GLOBAL-REAPPLY: hai setter của cầu giao THẲNG cho cửa chung `CameraReapply` — CÙNG hàm
        // `prefs_set` gọi; luật "ghi · nắn đủ · chỉ dựng lại khi đổi" sống ở đó (bài `CameraWave2CWiringContractTest`). Ý bài
        // giữ nguyên: setter ⇒ áp lại khung đang hiện; cửa áp chỉ chạm khi controller đã dựng.
        assertTrue("CameraReapply.setProjection(app, v)" in SourceRoots.body(bridge, "fun ClusterNavBridge.setCameraProjection("),
            "chip kiểu hình phải đi cửa áp lại DÙNG CHUNG với prefs_set")
        assertTrue("CameraReapply.setZoom(app, v)" in SourceRoots.body(bridge, "fun ClusterNavBridge.setCameraZoom("),
            "thanh thu phóng phải đi cửa áp lại DÙNG CHUNG với prefs_set")
        val door = app("launcher/camera/CameraReapply.kt")
        listOf("fun setProjection(ctx: Context, v: String): Boolean", "fun setZoom(ctx: Context, v: Int): Boolean").forEach { sig ->
            assertTrue("if (changed) anyShowing(ctx)" in SourceRoots.body(door, sig), "$sig phải áp lại khung đang hiện (chỉ khi đổi)")
        }
        val reapply = SourceRoots.body(door, "fun anyShowing(ctx: Context)")
        assertTrue("cameraSignalCreated" in reapply, "không được DỰNG controller chỉ vì một lần chạm chip")
        assertTrue("cameraSignal.reapplyIfShowing()" in reapply, "phải gọi đúng cửa áp lại của controller")

        val body = SourceRoots.body(controller, "fun reapplyIfShowing(")
        // 2.93: "đang hiện gì" là sự thật của CỬA SỔ (`showing` — xi-nhan hay theo yêu cầu), không còn là `turn`.
        assertTrue("val which = showing ?: return" in body, "không hiện gì ⇒ không bật khung bất ngờ")
        assertTrue("closeSession(keepPano = true)" in body && "openSession(which)" in body,
            "dựng lại theo đường ĐỔI CAMERA đã chạy hiện trường (dỡ phần cứng + cửa sổ, giữ panorama)")
        listOf("hold.", "stop()", "endBlinker()", "dropBlinker()", "od.").forEach {
            assertFalse(it in body, "áp lại KHÔNG được chạm máy trạng thái (HOLD / theo yêu cầu) — overlay chớp giữa chuyến: $it")
        }
    }

    /** (3) R8 — nút Chẩn đoán → xem thử → chờ khung → chụp → ghi + Thư viện → ĐÓNG; chỉ đọc cấu hình người lái. */
    @Test fun `nut khung tho di dung duong xem thu, ghi hai cho, dong lai va chi doc`() {
        assertTrue("captureRaw(left = true)" in diag && "captureRaw(left = false)" in diag, "đủ hai nút trái/phải")
        assertTrue("CameraRawCapture.capture(" in SourceRoots.body(diag, "private fun captureRaw("), "nút phải gọi đường chụp")

        assertTrue("Prefs.cameraSignalEnabled(" in raw, "công tắc xi-nhan TẮT ⇒ báo chữ, không chụp")
        assertTrue("previewSide(left)" in raw, "mở bên ấy bằng ĐÚNG lượt xem thử của Cài đặt")
        assertTrue("grabRawFrame(" in raw, "GL ⇒ chụp khung THÔ qua FBO (khung đang hiện có thể đã nắn)")
        assertTrue("CameraFrameFiles.savePng(" in raw && "CameraFrameFiles.copyToPictures(" in raw,
            "ghi PNG vào kachi-logs VÀ chép vào Thư viện ảnh")
        // Soát Opus 06/10: đóng + chụp theo SỐ HIỆU PHIÊN — xi-nhan thật / lượt khác thay phiên thì không đóng nhầm,
        // không chụp nhầm bên (spec R8 "đóng nếu chính nút đã mở").
        assertTrue("onMain { c.previewSide(left); c.sessionSeq() }" in raw, "số hiệu đọc CÙNG nhịp main với lượt mở")
        assertTrue(
            Regex("""finally\s*\{\s*onMain\s*\{\s*c\.endPreview\(if \(seq != NO_SEQ\) seq else c\.sessionSeq\(\)\)""")
                .containsMatchIn(raw),
            "lượt xem thử phải ĐÓNG trong finally — kể cả khi lượt mở ném/hết giờ — và chỉ đúng phiên đã mở",
        )
        assertTrue(raw.indexOf("var seq = NO_SEQ") in 0 until raw.indexOf("try {"), "số hiệu khai TRƯỚC try ⇒ finally luôn chạy")
        val grab = SourceRoots.body(raw, "private fun grab(")
        assertEquals(2, Regex("""c\.sessionSeq\(\) == seq""").findAll(grab).count(), "chụp CÙNG nhịp main với phép so phiên (GL lẫn TV)")
        val end = SourceRoots.body(controller, "fun endPreview(seq: Long)")
        // 2.93: dỡ = hạ PHẦN XI-NHAN (`endBlinker`) — camera theo yêu cầu đang bật (nếu có) quay lại, không bị tắt theo.
        assertTrue("openedSessions == seq" in end && "endBlinker()" in end, "endPreview chỉ dỡ khi phiên vẫn là phiên của nút")
        assertTrue("openedSessions++" in SourceRoots.body(controller, "private fun openSession("), "mỗi lượt dựng = một số hiệu mới")
        // Hai lượt chạm liền tay (trái rồi phải) xếp hàng trên MỘT luồng — không giành một phiên camera.
        assertTrue("ThreadPoolExecutor(0, 1, IDLE_S" in raw && "worker.execute" in raw, "lượt chụp phải xếp hàng, luồng tự tắt khi rảnh")
        assertFalse("Thread({" in raw, "không dựng luồng rời mỗi lượt chạm")
        listOf("Prefs.set", ".edit()", "putString(", "putInt(", "putBoolean(").forEach {
            assertFalse(it in raw, "nút chẩn đoán không được đổi cấu hình người lái: $it")
        }
    }

    /**
     * (3b) Soát Opus 06/10 [P2] — "đã có khung" phải là khung THẬT ở mọi đường TextureView:
     *  • `isAvailable` chỉ nói `SurfaceTexture` đã có; layer chưa nhận buffer ⇒ `getBitmap` trả bitmap nguyên số 0
     *    ([ĐO AOSP r47] `LayerDrawable.cpp:145`, `Readback.cpp:184-188`, `TextureView.java:574-590`) ⇒ phải dò điểm ảnh;
     *  • GL đã RƠI về TextureView (`glFellBack=1`) không có bộ đếm `frames=` ⇒ phải đi đường TextureView, không chờ khung GL.
     */
    @Test fun `nut khung tho cho khung that, ke ca duong TV va GL da roi`() {
        val wait = SourceRoots.body(raw, "private fun awaitFrame(")
        assertTrue("hasPixels(probe)" in wait, "TextureView: đòi điểm ảnh thật, không tin isAvailable")
        assertFalse("shot.available" in wait, "isAvailable KHÔNG phải 'đã có khung'")
        assertTrue("drawsInShader(shot)" in wait, "GL chỉ chờ bộ đếm khi luồng vẽ THẬT đang chạy")
        val shader = SourceRoots.body(raw, "private fun drawsInShader(")
        assertTrue("CameraVideoLayer.GL_FELL_BACK !in shot.glStats" in shader, "GL đã rơi ⇒ đường TextureView")
        assertTrue("if (b != null && !hasPixels(b))" in SourceRoots.body(raw, "private fun grab("),
            "bitmap rỗng (layer chưa có buffer) không được ghi thành 'khung thô'")
        val layer = app("launcher/camera/CameraVideoLayer.kt")
        assertTrue("const val GL_FELL_BACK = \"glFellBack=1\"" in layer && "GL_FELL_BACK else renderer?.stats()" in layer,
            "một chỗ khai dấu 'đã rơi' — glStats và nút chụp đọc CÙNG hằng")
    }

    /** (4) R8 — bản sao Thư viện: MediaStore ảnh, thư mục `Pictures/Kachi`, không cần quyền (API 29+). */
    @Test fun `ban sao khung tho vao Thu vien anh qua MediaStore`() {
        val copy = SourceRoots.body(files, "fun copyToPictures(")
        assertTrue("MediaStore.Images.Media.EXTERNAL_CONTENT_URI" in copy, "chèn vào bộ sưu tập ẢNH")
        assertTrue("MediaStore.MediaColumns.RELATIVE_PATH" in copy && "Environment.DIRECTORY_PICTURES" in copy,
            "đặt dưới Pictures/ — ngoài cây getExternalFilesDir mà DiagStorageCap dọn")
        assertTrue("resolver.delete(uri, null, null)" in copy, "ghi hỏng ⇒ xoá mục rỗng (không để ảnh 0 byte)")
        assertTrue("\"Kachi\"" in files, "thư mục con Pictures/Kachi")
    }
}
