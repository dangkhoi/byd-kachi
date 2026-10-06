package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2B — bài canh DÂY NỐI của bốn việc theo sau camera theo yêu cầu (spec `docs/specs/kachi-293-cam.html`) ═════
 *
 *  • OQ4 CAMERA-DOCK-ACTIVE-STATE — ô camera trên thanh nút (và ô widget) SÁNG theo trạng thái thật, nghe controller theo
 *    vòng đời cửa sổ, KHÔNG vòng hỏi; ô launcher khác giữ đúng hành vi bấm-một-phát cũ;
 *  • D2 — `prefs_set` một khoá camera áp ngay nếu đúng camera ấy đang hiện, CÙNG cửa với Cài đặt;
 *  • D6 — đổi hồ sơ áp cấu hình mới cho camera THEO YÊU CẦU đang hiện, KHÔNG dỡ camera xi-nhan đang giữ (điểm mù khi rẽ).
 *
 * Luật thuần đã có bài chạy thật ở `:core` (`CameraDemandOutcomeTest.o sang…`, `CameraCamConfigTest.khoa nao…`,
 * `CameraWidgetTileTest`); ở đây canh những mắt xích mà gỡ đi thì build vẫn xanh (CLAUDE.md §8 — bẫy `CastShell.evictVd`).
 */
class CameraWave2BWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val tile by lazy { app("launcher/LauncherTile.kt") }
    private val dispatch by lazy { app("launcher/camera/CameraDemandDispatch.kt") }
    private val reapply by lazy { app("launcher/camera/CameraReapply.kt") }
    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }

    /** OQ4 — ô camera sáng theo `CameraDemand.isOn`, nghe controller khi vào cửa sổ, gỡ khi rời; ô khác y như cũ. */
    @Test fun `o camera sang theo su that, nghe theo vong doi cua so, khong hoi vong`() {
        val fn = SourceRoots.body(tile, "internal fun launcherTileOf(")
        assertTrue("val camera = LauncherActions.cameraOf(pick.id)" in fn, "chỉ bốn ô camera có trạng thái (mã → camera ở `:core`)")
        val rest = SourceRoots.body(fn, "fun rest()")
        assertTrue("camera != null && CameraDemandDispatch.isOn(ctx, camera)" in rest, "nền nghỉ = sáng khi chạm là TẮT; ô khác luôn tắt")
        assertTrue("dress(tile, icon, label, on)" in rest, "cùng phép tô của ô bấm (không bản sao thứ hai)")
        assertTrue(fn.indexOf("dress(tile, icon, label, true)") in 0 until fn.indexOf("onTap()") &&
            "tile.postDelayed({ rest() }, TAP_FLASH_MS)" in fn, "nháy sáng 220 ms rồi về nền NGHỈ (không về tắt cứng)")
        assertTrue("if (camera != null) CameraDemandDispatch.watchWhileAttached(tile) { rest() }" in fn, "nghe, không hỏi vòng")
        assertEquals(1, Regex("""postDelayed\(""").findAll(tile).count(), "hẹn giờ DUY NHẤT là nhịp nháy sáng của cú bấm")
        val isOn = SourceRoots.body(dispatch, "fun isOn(ctx: Context, which: CameraWhich): Boolean")
        assertTrue("c.cameraSignalCreated && CameraDemand.isOn(which, c.cameraSignal.demanded(), c.cameraSignal.showingCamera())" in isOn,
            "luật sáng ở `:core`; controller chưa dựng ⇒ không sáng và KHÔNG dựng nó")
        val watch = SourceRoots.body(dispatch, "fun watchWhileAttached(view: View, onChange: () -> Unit)")
        val attach = SourceRoots.body(watch, "override fun onViewAttachedToWindow(v: View)")
        assertTrue(attach.indexOf("unwatch?.invoke()") in 0 until attach.indexOf(".cameraSignal.onDemandChanged { onChange() }"),
            "gỡ lượt nghe cũ TRƯỚC (không chồng hai người nghe)")
        assertTrue("onChange()" in attach.substringAfter("onDemandChanged { onChange() }"), "vào lại cửa sổ ⇒ vẽ lại ngay (đổi lúc khuất)")
        assertTrue("unwatch?.invoke()" in SourceRoots.body(watch, "override fun onViewDetachedFromWindow(v: View)"), "rời cửa sổ ⇒ gỡ")
        // Người nghe báo cả khi camera ĐANG HIỆN đổi (xi-nhan chen/hết) — không chỉ khi demand đổi.
        val show = SourceRoots.body(controller, "private fun show(next: CameraWhich?)")
        assertTrue("od.notifyChanged()" in show, "xi-nhan chen/hết đổi thứ đang hiện ⇒ ô vẽ lại đúng")
        // Thanh nút vẫn là view thuần: không biết camera nào, chỉ dựng bằng bộ dựng ô dùng chung.
        val dock = app("launcher/ControlDockView.kt")
        assertFalse("CameraDemandDispatch" in dock || "LauncherActions.CAM_" in dock, "thanh nút không tự biết việc camera")
    }

    /** D2 — `prefs_set` một trong 28 khoá camera ⇒ áp ngay nếu ĐÚNG camera ấy đang hiện, CÙNG cửa với Cài đặt. */
    @Test fun `prefs_set khoa camera ap ngay dung camera dang hien, cung cua Cai dat`() {
        val run = SourceRoots.body(app("launcher/testbridge/TestBridgePrefsSet.kt"), "fun run(")
        val nul = run.indexOf("if (applied == null) {")
        val re = run.indexOf("CameraCamConfig.cameraOf(cmd.key)?.let { CameraReapply.ifShowing(app, it) }")
        val ok = run.indexOf("reply.ok(\"key\" to cmd.key, \"value\" to applied")
        assertTrue(nul in 0 until re && re < ok, "ghi HỢP LỆ rồi mới áp (giá trị hỏng ⇒ không chạm camera), áp trước khi đọc lại")
        val door = SourceRoots.body(reapply, "fun ifShowing(ctx: Context, which: CameraWhich)")
        assertTrue("c.cameraSignalCreated && c.cameraSignal.showingCamera() == which" in door, "camera khác đang hiện ⇒ để yên")
        assertTrue("c.cameraSignal.reapplyIfShowing()" in door, "cửa áp lại của 2.92: dỡ + dựng, không chạm HOLD")
        // Soát senior wave 2B/2C [P2]: Cài đặt gọi cửa này ĐỒNG BỘ trên luồng chính — lỗi dựng lại phải thành log, không ném vào
        // trình nghe chạm (lớp chắn `runCatching` của `reapplyCamera` cũ đã rơi ở lượt gộp D2).
        assertTrue(door.trimStart().removePrefix("{").trimStart().startsWith("runCatching {") && ".onFailure { Log.w(TAG," in door,
            "cửa áp lại theo camera phải chắn lỗi như `anyShowing`")
        assertTrue("CameraReapply.ifShowing(app, w)" in app("launcher/ClusterNavBridgeCameraPerCam.kt"), "Cài đặt cùng một cửa")
    }

    /** D6 — đổi hồ sơ: áp cho camera THEO YÊU CẦU đang hiện; camera xi-nhan đang giữ ⇒ để yên (điểm mù khi rẽ). */
    @Test fun `doi ho so ap cho camera theo yeu cau, khong do camera xi nhan`() {
        val all = SourceRoots.body(app("launcher/ClusterNavBridgeReapply.kt"), "internal fun ClusterNavBridge.reapplyAll()")
        assertTrue("step(\"camera.demand\") { CameraReapply.ifDemandShowing(app) }" in all, "bước áp lại camera của lượt đổi hồ sơ")
        val door = SourceRoots.body(reapply, "fun ifDemandShowing(ctx: Context)")
        assertTrue("if (c.cameraSignalCreated) c.cameraSignal.reapplyIfDemandShowing()" in door, "không dựng controller chỉ để áp")
        val fn = SourceRoots.body(controller, "fun reapplyIfDemandShowing()")
        assertTrue("main.post {" in fn, "trạng thái controller chỉ đọc trên luồng chính (lượt đổi hồ sơ chạy ở luồng nền)")
        // 2.93 wave 2C · CAM-D6-SAME-CAMERA-EDGE: phép "khung đang hiện là camera THEO YÊU CẦU, không phải camera xi-nhan đang
        // giữ" dời về `:core` (`CameraDemand.profileReapply` — bài `CameraDemandProfileReapplyTest` chạy thật, kể cả ca TRÙNG
        // camera ⇒ hẹn lúc nhả). Ý bài giữ nguyên: controller hỏi luật bằng sự thật của nó (bên xi-nhan + khung đang hiện).
        assertTrue("od.onProfileSwitched(CameraWhich.ofTurn(current), showing)" in fn,
            "luật đổi hồ sơ ở `:core`, controller đưa đúng bên xi-nhan đang giữ + camera của phiên đang treo")
        assertTrue("reapplyIfShowing()" in fn, "cùng cửa áp lại (dỡ + dựng)")
        listOf("postDelayed", "hold.reset", "dropBlinker").forEach { assertFalse(it in fn, "không chạm máy trạng thái xi-nhan: $it") }
    }
}
