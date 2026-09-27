package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.76 · R3 — lùi CHANNEL → PANO: bài canh DÂY NỐI (máy trạng thái đã test thuần ở `CameraChannelFallbackTest`) ═══
 *
 * Mắt xích nào gỡ đi thì build vẫn xanh và không bài `:core` nào đỏ (CLAUDE.md §8): `AvmCamera` phải NÓI kênh bị từ
 * chối; controller phải HỎI máy ở đúng hai chỗ (sau HAL, sau ngân sách khung đầu); phép lùi phải đi ĐÚNG đường dỡ
 * `closeSession(keepPano = true)` rồi `openSession(forcedPano = true)`; dấu cho Cài đặt phải được ghi/xoá; và
 * `camera_frame` phải mang ghi chú anamorphic.
 */
class CameraChannelFallbackWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }
    private val avm by lazy { app("launcher/camera/AvmCamera.kt") }
    private val frameCmd by lazy { app("launcher/testbridge/TestBridgeCameraFrame.kt") }
    private val shot by lazy { app("launcher/camera/CameraFrameShot.kt") }

    @Test fun `AvmCamera noi kenh don bi tu choi, dat lai moi luot open`() {
        val open = SourceRoots.body(avm, "    fun open(")
        assertTrue("channelRefused = false" in open, "đặt lại ở đầu MỖI lượt mở — không mang dấu của phiên trước")
        assertTrue("channelRefused = !surfaceOk && CameraProfileDefaults.isChannel(halMode)" in open,
            "chỉ kênh ĐƠN (1..4) bị từ chối mới tính; VIEW_DEFAULT=0 hay AUTO thì không")
        assertTrue("var channelRefused: Boolean = false" in avm && "private set" in avm, "chỉ AvmCamera được ghi")
        // [P1 · SOÁT Opus 2026-09-27] Ca THỨ HAI của *"xin kênh đơn mà không nhận được"*: lớp AVMCamera của một trim
        // khác **không có** `addPreviewSurface(Surface,int)` ⇒ nhánh đo ở trên bị bỏ, hàm rơi về đường một-tham-số
        // (khung GHÉP) mà `channelRefused` vẫn `false` ⇒ R3 không bao giờ bắn. Phép gán phải nằm SAU nhánh ấy.
        assertTrue("if (add == null && CameraProfileDefaults.isChannel(halMode)) {" in open,
            "không có hàm để xin kênh cũng là một lần bị từ chối — nếu không, khung ghép đi tiếp dưới nhãn một kênh")
        val iOneArg = open.indexOf("m(\"addPreviewSurface\", Surface::class.java)?.invoke(obj, surface)")
        val iNoMethod = open.indexOf("if (add == null && CameraProfileDefaults.isChannel(halMode)) {")
        assertTrue(iOneArg in 0..<iNoMethod, "phải đứng SAU đường dự phòng một-tham-số: chỉ lúc ấy mới biết khung là ghép")
        // Đường 2.73 (dò 0..3) không đổi một byte — bài `CameraSpanShapeWiringContractTest` đã ghim, nhắc lại vế cốt lõi.
        assertTrue("            for (mode in 0..3) {" in avm)
    }

    @Test fun `controller hoi may lui o dung hai cho va lam dung mot duong`() {
        val open = SourceRoots.body(controller, "private fun openSession(")
        assertTrue("fallback.onSessionStart(forcedPano)" in open, "mỗi phiên phải báo cho máy: phiên xi-nhan đặt lại cờ, phiên lùi giữ")
        assertTrue("fallback.onHalResult(channel, avm.channelRefused) == CameraChannelFallback.Action.REBUILD_PANO" in open,
            "sau HAL: hỏi máy bằng đúng sự thật AvmCamera đưa")
        assertTrue("fallbackToPano(turn, session, \"rc=false\")" in open)
        assertTrue("main.postDelayed(firstFrameCheck, CameraChannelFallback.FIRST_FRAME_BUDGET_MS)" in open,
            "ngân sách khung đầu lấy từ `:core`, chỉ hẹn khi phiên là một kênh")
        assertTrue("if (channel) {" in open, "PANO không hẹn gì")
        // [P2 · SOÁT Opus 2026-09-27] Phiên do phép lùi mở KHÔNG được truyền lại `camera_hal_mode` đã ghim: kênh ấy
        // vừa chết. Ở nhánh `no-frame` (HAL nhận mà không đẩy khung) nó sẽ lại được nhận, lại không có khung, và
        // `fellBack` đã chốt ⇒ không còn đường phục hồi nào (🚗 CAM-F3 hứa *"hình khung ghép lên"*).
        assertTrue("val halMode = if (forcedPano) CameraSignalPolicy.HAL_MODE_AUTO else CameraSignalPolicy.channelFor(" in open,
            "lượt dựng do phép lùi đi đường 2.73 (dò 0..3 ⇒ khung ghép), không đọc pref kênh")
        val iForced = open.indexOf("val halMode = if (forcedPano)")
        val iPref = open.indexOf("halModePref = Prefs.cameraHalMode(appCtx),")
        assertTrue(iForced in 0..<iPref, "pref kênh chỉ được đọc ở nhánh KHÔNG bị ép PANO")
        val check = controller.substringAfter("private val firstFrameCheck = Runnable {").substringBefore("\n    }")
        assertTrue("CameraChannelFallback.framesOf(overlay.glStats())" in check, "số khung đọc từ chuỗi stats của luồng vẽ")
        assertTrue("firstFrameSession != sessionId" in check, "hẹn giờ của phiên CŨ không được lùi phiên MỚI")
        assertTrue("fallbackToPano(current, sessionId, \"no-frame\")" in check)
        // Phép lùi: post về main, kiểm phiên, log to, ghi dấu, dỡ đúng đường đổi bên, dựng lại forcedPano.
        val fb = SourceRoots.body(controller, "private fun fallbackToPano(")
        assertTrue("main.post {" in fb, "gọi từ trong callback SurfaceTexture ⇒ phải post, không tái nhập hide()")
        assertTrue("if (session != sessionId || current != turn) return@post" in fb)
        assertTrue("Log.w(" in fb && "LÙI VỀ TOÀN CẢNH" in fb, "một dòng log TO, đọc được trên xe")
        assertTrue("Prefs.setCameraChannelFallback(appCtx, reason)" in fb, "dấu cho Cài đặt")
        val iClose = fb.indexOf("closeSession(keepPano = true)")
        val iOpen = fb.indexOf("openSession(turn, forcedPano = true)")
        assertTrue(iClose in 0..<iOpen, "dỡ phiên cũ (HAL trước, cửa sổ sau) RỒI mới dựng — đúng đường đổi bên 27/09")
        assertEquals(1, Regex("""openSession\(turn, forcedPano = true\)""").findAll(controller).count(), "chỉ phép lùi mới ép PANO")
        // Phiên xi-nhan mới xoá dấu; `stop()` huỷ hẹn giờ khung đầu.
        assertTrue("if (!forcedPano) Prefs.setCameraChannelFallback(appCtx, \"\")" in open)
        assertTrue("main.removeCallbacks(firstFrameCheck)" in SourceRoots.body(controller, "private fun stop("))
        // Không rebuild ở chỗ khác: `tickMain` chỉ có một lối vào openSession.
        assertTrue("if (turn == Turn.NONE) stop() else openSession(turn, forcedPano = false)" in SourceRoots.body(controller, "private fun tickMain("))
    }

    /**
     * [P1 · SOÁT Opus 2026-09-27] Đường GL tự rơi về `TextureView` 2.73 ⇒ `glStats` **không được** trả một bộ đếm.
     *
     * Nếu nó trả `frames=0` của một luồng vẽ chưa từng chạy thì ngân sách khung đầu lùi một phiên **đang có hình** và
     * in dấu `no-frame` trong khi bệnh là EGL — trái chính KDoc `CameraChannelFallback.onFirstFrameBudget`
     * (*"không lùi theo một số liệu không có"*) và trái §2.3 doc làn `camera-ia-profile.md`.
     */
    @Test fun `duong GL da roi thi glStats khong dua ra bo dem`() {
        val layer = app("launcher/camera/CameraVideoLayer.kt")
        assertTrue("private val fellBack: BooleanArray? = null," in layer, "cờ đã-rơi phải đi vào thực thể, không chỉ là biến trong hàm")
        assertTrue("CameraVideoLayer(tv, renderer, null, fellBack)" in layer, "nhánh GL truyền đúng ô cờ mà hai callback dùng chung")
        assertTrue(
            "fun glStats(): String = if (fellBack?.get(0) == true) \"glFellBack=1\" else renderer?.stats().orEmpty()" in layer,
            "đã rơi ⇒ chuỗi KHÔNG có `frames=` (⇒ framesOf = null = không đếm được), và KHÔNG rỗng (còn đọc được lý do)",
        )
        // Hợp đồng số: `framesOf` của dấu ấy phải là `null` — chữ `frames=` là điều kiện của regex ở `:core`.
        assertEquals(null, CameraChannelFallback.framesOf("glFellBack=1"))
        assertEquals(0L, CameraChannelFallback.framesOf("frames=0 busySkip=0"), "chuỗi thật của luồng vẽ ĐANG chạy vẫn đếm được")
        assertEquals(CameraChannelFallback.Action.NONE, CameraChannelFallback().onFirstFrameBudget(channel = true, frames = null))
    }

    @Test fun `camera_frame mang ghi chu anamorphic khi nguon la mot kenh`() {
        assertTrue("val channel: Boolean = false" in shot, "CameraFrameShot phải nói phiên đang treo là một kênh hay khung ghép")
        assertTrue("channel = s?.channel == true," in SourceRoots.body(controller, "    fun grabFrame("), "lấy từ quyết định lúc dựng (Shown), không tra pref")
        assertTrue("\"note\" to com.byd.clusternav.launcher.camera.CameraSignalPolicy.frameNote(shot.channel)" in frameCmd,
            "lời đáp phải mang note; chuỗi do `:core` dựng")
        assertFalse("\"anamorphic" in frameCmd, "chuỗi không viết ở `:app`")
        assertEquals("anamorphic x4", CameraSignalPolicy.frameNote(true))
    }
}
