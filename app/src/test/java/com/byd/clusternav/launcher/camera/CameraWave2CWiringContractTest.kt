package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2C — bài canh DÂY NỐI của hai việc camera (spec `docs/specs/kachi-293-wave2c.html` R4 · R5) ═══════════════
 *
 *  • R4 PREFS-SET-CAM-GLOBAL-REAPPLY — `prefs_set camera_projection|camera_zoom` đi ĐÚNG hàm của chip/thanh kéo Cài đặt
 *    (`CameraReapply.setProjection/setZoom`): khung đang hiện dựng lại như Cài đặt; một chỗ GHI duy nhất cho hai khoá chung.
 *  • R5 CAM-D6-SAME-CAMERA-EDGE — lời hẹn "dựng lại lúc xi-nhan nhả" có ĐỦ ba mắt xích ở controller: đặt (đổi hồ sơ), thi hành
 *    (nhả xi-nhan), xoá (phiên mới).
 *
 * Luật thuần đã có bài chạy thật ở `:core` (`CameraViewModeTest.chon Nan thang…`, `CameraDemandProfileReapplyTest`); ở đây
 * canh những mắt xích mà gỡ đi thì build vẫn xanh (CLAUDE.md §8 — bẫy `CastShell.evictVd`). R6 (gửi lại `:wake`) canh ở
 * `CameraDemandWiringContractTest` + `:core` `CameraWakeAskTest`.
 */
class CameraWave2CWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val prefsSet by lazy { app("launcher/testbridge/TestBridgePrefsSet.kt") }
    private val reapply by lazy { app("launcher/camera/CameraReapply.kt") }
    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }

    /** R4 — `prefs_set` hai khoá CHUNG gọi đúng cửa Cài đặt; giá trị hỏng không chạm camera (đi qua `takeIf` trước). */
    @Test fun `prefs_set khoa camera chung di dung cua Cai dat`() {
        val run = SourceRoots.body(prefsSet, "fun run(")
        val proj = run.indexOf("\"camera_projection\" -> raw.trim().uppercase().takeIf { CameraViewMode.isMode(it) }")
        assertTrue(proj >= 0 && run.indexOf("?.let { CameraReapply.setProjection(app, it); it }", proj) > proj,
            "camera_projection: mã hợp lệ ⇒ CÙNG hàm của chip Cài đặt (ghi · nắn đủ · dựng lại nếu đổi)")
        val zoom = run.indexOf("\"camera_zoom\" -> int(raw)?.takeIf { CameraViewMode.isZoomPct(it) }")
        assertTrue(zoom >= 0 && run.indexOf("?.let { CameraReapply.setZoom(app, it); it.toString() }", zoom) > zoom,
            "camera_zoom: số trong miền ⇒ CÙNG hàm của thanh thu phóng Cài đặt")
        // Cửa chung: ghi TRƯỚC, luật nắn-đủ ở `:core`, dựng lại chỉ khi đổi.
        val set = SourceRoots.body(reapply, "fun setProjection(ctx: Context, v: String): Boolean")
        assertTrue(set.indexOf("Prefs.setCameraProjection(ctx, v)") in 0 until set.indexOf("if (changed) anyShowing(ctx)"), "ghi rồi mới áp")
        assertTrue("CameraViewMode.amountOnPick(v, Prefs.cameraDewarpAmount(ctx))" in set, "luật nắn đủ ở `:core` — không bản sao")
        assertTrue(set.indexOf("Prefs.cameraProjection(ctx) != v") in 0 until set.indexOf("Prefs.setCameraProjection(ctx, v)"),
            "so với kiểu ĐÃ QUY trước khi ghi (chạm lại chip đang sáng = không chớp)")
    }

    /** R4 — MỘT chỗ ghi cho hai khoá chung trong toàn mã `:app` (ngoài định nghĩa ở `PrefsCameraDewarp.kt`). */
    @Test fun `mot cho ghi duy nhat cho hai khoa camera chung`() {
        val main = SourceRoots.path("src/main/java")
        val writers = Files.walk(main).use { s ->
            s.filter { it.toString().endsWith(".kt") }.filter { f ->
                val src = SourceRoots.codeOf("src/main/java/" + main.relativize(f).toString().replace('\\', '/'))
                Regex("""(?<!fun )Prefs\.setCamera(Projection|Zoom)\(""").containsMatchIn(src)   // lời GỌI, không phải định nghĩa
            }.map { it.fileName.toString() }.toList()
        }.toSet()
        assertEquals(setOf("CameraReapply.kt"), writers, "Cài đặt và prefs_set phải ghi qua CÙNG một hàm — bản thứ hai sẽ lệch")
    }

    /** R5 — lời hẹn có đủ ba mắt xích; thiếu một thì luật `:core` đúng mà xe vẫn giữ cấu hình hồ sơ cũ. */
    @Test fun `loi hen dung lai luc nha xi nhan co du dat, thi hanh, xoa`() {
        val switchFn = SourceRoots.body(controller, "fun reapplyIfDemandShowing()")
        assertTrue("od.onProfileSwitched(CameraWhich.ofTurn(current), showing)" in switchFn, "ĐẶT hẹn: luật `:core` quyết ngay / hẹn / để yên")
        val end = SourceRoots.body(controller, "private fun endBlinker(")
        assertTrue(end.indexOf("dropBlinker()") in 0 until end.indexOf("od.takeReapplyAtRelease()"), "hạ xi-nhan TRƯỚC rồi mới hỏi lời hẹn")
        assertTrue("od.takeReapplyAtRelease() && showing != null && want() == showing" in end,
            "THI HÀNH chỉ khi camera nên hiện vẫn là camera đang treo (camera khác ⇒ `show` đã tự mở phiên mới)")
        assertTrue("reapplyIfShowing()" in end && "show(want())" in end, "dựng lại theo cửa áp lại; ngược lại đường cũ y nguyên")
        assertTrue("od.sessionOpened()" in SourceRoots.body(controller, "private fun openSession("), "XOÁ hẹn khi phiên mới đọc cấu hình tươi")
        assertEquals(1, Regex("""main\.postDelayed\(""").findAll(controller).count(), "lời hẹn KHÔNG phải hẹn giờ — chỉ mốc HOLD dùng postDelayed")
    }
}
