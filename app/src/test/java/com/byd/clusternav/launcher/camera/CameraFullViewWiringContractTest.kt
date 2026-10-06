package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
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
        val render = open.indexOf("Prefs.cameraRender(appCtx)")
        val quantize = open.indexOf("CameraViewMode.effective(")
        assertTrue(render >= 0 && quantize > render, "phải đọc đường vẽ TRƯỚC rồi quy kiểu theo nó (TV không có shader)")
        assertTrue("Prefs.cameraProjection(appCtx)" in open, "kiểu hình đọc từ pref mỗi lượt dựng")
        val crops = args(open, "CameraViewPlan.crops(")
        assertTrue("mode = mode" in crops, "vùng cắt khung/nội dung phải theo kiểu ĐÃ quy")
        val gl = args(open, "Prefs.cameraGlUniforms(")
        assertTrue("mode = mode" in gl && "crops = crops" in gl, "uniform GL phải theo kiểu ĐÃ quy + đúng vùng cắt vừa suy")
        assertTrue("CameraViewPlan.tvScale(mode," in open, "tỉ lệ ma trận TV phải theo kiểu ĐÃ quy")
        assertTrue("letterbox = CameraViewMode.fullView(mode)" in open, "lớp video phải biết kiểu trọn dải để vừa khung")
        assertTrue("(chọn \$asked)" in open, "log phải nói cả kiểu người lái chọn khi đường vẽ ép kiểu khác (R6)")
    }

    /** (2) R1 — áp ngay: hai setter của cầu Cài đặt cùng gọi một cửa, cửa ấy chỉ chạm khi controller đã dựng. */
    @Test fun `doi kieu hoac thu phong thi dung lai khung dang hien, khong cham HOLD`() {
        listOf("fun ClusterNavBridge.setCameraProjection(", "fun ClusterNavBridge.setCameraZoom(").forEach { sig ->
            assertTrue("reapplyCamera()" in SourceRoots.body(bridge, sig), "$sig phải áp lại khung đang hiện")
        }
        val reapply = SourceRoots.body(bridge, "private fun ClusterNavBridge.reapplyCamera(")
        assertTrue("cameraSignalCreated" in reapply, "không được DỰNG controller chỉ vì một lần chạm chip")
        assertTrue("cameraSignal.reapplyIfShowing()" in reapply, "phải gọi đúng cửa áp lại của controller")

        val body = SourceRoots.body(controller, "fun reapplyIfShowing(")
        assertTrue("if (turn == Turn.NONE) return" in body, "không hiện gì ⇒ không bật khung bất ngờ")
        assertTrue("closeSession(keepPano = true)" in body && "openSession(turn)" in body,
            "dựng lại theo đường ĐỔI BÊN đã chạy hiện trường (dỡ phần cứng + cửa sổ, giữ panorama)")
        assertFalse("hold." in body || "stop()" in body, "áp lại KHÔNG được chạm máy trạng thái HOLD (overlay chớp giữa chuyến)")
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
        assertTrue(Regex("""finally\s*\{\s*onMain\s*\{\s*c\.endPreview\(\)""").containsMatchIn(raw),
            "lượt xem thử phải ĐÓNG trong finally — kể cả khi chụp hỏng/hết giờ")
        listOf("Prefs.set", ".edit()", "putString(", "putInt(", "putBoolean(").forEach {
            assertFalse(it in raw, "nút chẩn đoán không được đổi cấu hình người lái: $it")
        }
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
