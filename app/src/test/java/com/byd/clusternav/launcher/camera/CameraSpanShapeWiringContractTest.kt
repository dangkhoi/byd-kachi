package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.testbridge.TestBridgeCommands
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R8-A · VÙNG GƯƠNG · HÌNH KHUNG · MÓC ĐO HAL — bài canh DÂY NỐI của `:app` ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-274-ux-voice-camera.html` R8 · RE `diagnostics/electro-camera-RE-2026-09-26.md` §5 K4/K6/K10
 * · §6.1 · §6.3-C1. Hình học đã test bằng số ở `:core` ([CameraPanoCropTest]); ở đây canh **năm mắt xích mà gỡ đi thì
 * build vẫn xanh và không bài `:core` nào đỏ** (CLAUDE.md §8 — đúng cái bẫy `CastShell.evictVd`):
 *
 *  1. controller **suy ra** crop từ `:core` thay vì lấy hằng `view.crop`, và truyền hình khung + kênh HAL xuống;
 *  2. `AvmCamera`: vòng dò `0..3` của 2.73 **còn nguyên từng byte** khi pref vắng; nhánh đo đọc `rc` thật;
 *  3. `rmPreviewSurface` gọi **SAU** `stopPreview` + `close`, đúng tên đã xác minh trong firmware;
 *  4. overlay bo TRÒN bằng **đúng** `ViewOutlineProvider` của 2.73 (không thêm cơ chế cắt thứ hai);
 *  5. bốn hàng chip + sáu khoá `prefs_set` (có `read_back`) — thiếu chúng thì owner không dò được gì trên xe.
 */
class CameraSpanShapeWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val overlay by lazy { app("launcher/camera/CameraOverlayView.kt") }
    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }
    private val avm by lazy { app("launcher/camera/AvmCamera.kt") }
    private val settings by lazy { app("launcher/SettingsSectionsCar.kt") }
    private val prefs by lazy { app("PrefsAutomation.kt") }
    private val prefsSet by lazy { app("launcher/testbridge/TestBridgePrefsSet.kt") }
    private val bridge by lazy { app("launcher/ClusterNavBridgeAutomation.kt") }
    private val vi by lazy { SourceRoots.text("src/main/res/values/strings_kachi.xml") }
    private val en by lazy { SourceRoots.text("src/main/res/values-en/strings_kachi.xml") }

    // ══ (1) CONTROLLER — crop SUY RA ở `:core`, hình khung + kênh HAL đi xuống đúng chỗ ═════════════════════

    /**
     * Crop phải đến từ [CameraPanoCrop.cropFor] với **cả bốn** pref, và hằng `view.crop` **không còn** được đọc thẳng:
     * còn `val crop = view.crop` thì bốn hàng chip là bốn nút không làm gì, mà không test nào đỏ.
     */
    @Test fun `controller suy ra crop tu core voi ca bon pref`() {
        assertTrue("CameraPanoCrop.cropFor(" in controller, "crop phải do `:core` suy ra (có test bằng số)")
        assertTrue("val crop = view.crop" !in controller, "hằng crop của enum không được đọc thẳng nữa (chip sẽ vô tác dụng)")
        listOf(
            "Prefs.cameraSpan(appCtx)",
            "Prefs.cameraShape(appCtx)",
            "Prefs.cameraStrip(appCtx, left = turn == Turn.LEFT)",
            "Prefs.cameraCirclePct(appCtx)",
        ).forEach { assertTrue(it in controller, "thiếu lượt đọc pref: $it") }
        // Chỉ số dải đọc theo ĐÚNG BÊN xi-nhan (hai khoá độc lập, y khuôn camera_rot_left/right).
        assertTrue("strip = Prefs.cameraStrip(appCtx, left = turn == Turn.LEFT)" in controller)
        // Hình khung đi tiếp xuống tầng vẽ; kênh HAL đi tiếp xuống tầng mở camera.
        assertTrue("shape = shape," in controller, "hình khung phải vào overlay.show(shape = …)")
        assertTrue("avm.open(camId, surface, halMode)" in controller, "kênh HAL phải vào AvmCamera.open")
        assertTrue("val halMode = Prefs.cameraHalMode(appCtx)" in controller)
        // Một dòng log đủ để đọc lại quyết định trên xe (CLAUDE.md §11: app tự chụp, owner không gõ adb).
        assertTrue("vùng=\$span" in controller && "hình=\$shape" in controller && "halMode=\$halMode" in controller,
            "dòng log của controller phải nói vùng/hình/kênh — đó là thứ owner đọc lại khi chốt dải")
        assertTrue("rot=\$rot\")" in controller, "dòng log vẫn kết bằng rot= (hợp đồng của bài R7)")
    }

    /** `cam_sort` được ghi MỘT dòng lúc bật tính năng, trên thread nền, và **không gate** gì (CLAUDE.md §3). */
    @Test fun `cam_sort duoc ghi log mot dong va khong gate gi`() {
        val ensure = SourceRoots.body(controller, "private fun ensureSignal(")
        assertTrue("logCamSort()" in ensure, "phải gọi trong lượt bật (thread nền) — getprop + reflection, không trên main")
        val body = SourceRoots.body(controller, "private fun logCamSort(")
        assertTrue("AvmCamera.systemProp(AvmCamera.PROP_CAM_SORT)" in body, "đọc getprop qua cửa duy nhất của `:app`")
        assertTrue("CameraSignalPolicy.camSortIds(raw)" in body, "phân tích chuỗi ở `:core` (có test với chuỗi thật của xe)")
        assertTrue("Log.i(" in body, "chỉ GHI LOG")
        // Không có nhánh nào rẽ theo cam_sort: biến nó thành cổng là tự tắt tính năng trên trim mà ROM viết khác.
        assertTrue("camSortId" !in controller.substringAfter("private fun tickMain("), "cam_sort KHÔNG được gate đường mở camera")
    }

    // ══ (2)+(3) AvmCamera — đường 2.73 nguyên vẹn, móc đo đọc rc, rmPreviewSurface xuống CUỐI ═══════════════

    /**
     * Vòng dò `0..3` của 2.73 phải còn **nguyên văn** — ba dòng dưới đây là đường đang chạy ngoài hiện trường
     * (CLAUDE.md §6). Và nó chỉ chạy khi pref = AUTO, tức pref vắng ⇒ **không một lời gọi HAL nào đổi**.
     */
    @Test fun `vong do 0 3 cua 2 73 con nguyen van va chi chay khi pref AUTO`() {
        assertTrue("            for (mode in 0..3) {" in avm, "vòng dò phải giữ đúng thứ tự 0..3 của 2.73")
        assertTrue(
            "                if (runCatching { add.invoke(obj, surface, mode); true }.getOrDefault(false)) {" in avm,
            "lời gọi HAL trong vòng dò phải giữ nguyên từng byte (đổi là đổi đường đang chạy trên xe)",
        )
        assertTrue("if (add != null && !surfaceOk && halMode < CameraSignalPolicy.HAL_MODE_MIN) {" in avm,
            "vòng dò chỉ chạy khi pref = AUTO (hoặc rác) — cổng phải đọc từ hằng `:core`, không chép số")
        assertTrue("halMode: Int = CameraSignalPolicy.HAL_MODE_AUTO" in avm, "mặc định tham số = đường 2.73")
        // Nhánh ĐO: đúng một lời gọi, và ĐỌC giá trị trả về (vòng dò thì bỏ qua rc ⇒ không đo được gì).
        // [SOÁT Opus 2026-09-27] Miền hợp lệ phải kiểm ở **tầng thi hành** (CLAUDE.md §4: *"guard cứng đặt ở tầng
        // thi hành"*), không chỉ ở chỗ đọc prefs: đây là lời gọi ĐỔI trạng thái camera của xe. Ngoài miền ⇒ rơi xuống
        // nhánh một-tham-số, tức hành vi an toàn.
        assertTrue(
            "if (add != null && CameraSignalPolicy.isHalMode(halMode) && halMode >= CameraSignalPolicy.HAL_MODE_MIN) {" in avm,
            "nhánh ĐO phải tự kiểm miền kênh HAL, không tin chỗ gọi",
        )
        // Và cổng của bước 3 khi dỡ — mặc định TẮT ⇒ chuỗi dỡ y 2.73 (xem KDoc `AvmCamera.rmOnClose`).
        assertTrue("var rmOnClose: Boolean = false" in avm, "móc ĐO `rmPreviewSurface` phải mặc định TẮT")
        assertTrue("if (rmOnClose) added?.let" in avm, "và lời gọi phải nằm sau cổng ấy")
        assertTrue("add.invoke(obj, surface, halMode) as? Boolean ?: true" in avm, "nhánh đo phải đọc rc, không bỏ qua")
        assertTrue("rc=\$rc" in avm, "log phải in rc để lượt đo không bị đọc thành \"kênh n chạy\" khi ảnh tới từ đường dự phòng")
    }

    /**
     * `close()`: hai bước 2.73 trước, `rmPreviewSurface` **sau** — kiểm bằng **vị trí** trong thân hàm, không chỉ
     * bằng `contains` (một `contains` không thấy được thứ tự, mà thứ tự đúng là điều CLAUDE.md §6 bắt giữ).
     */
    @Test fun `close giu thu tu 2 73 roi moi rmPreviewSurface`() {
        val body = SourceRoots.body(avm, "fun close(")
        val stop = body.indexOf("\"stopPreview\"")
        val close = body.indexOf("\"close\"")
        val rm = body.indexOf("\"rmPreviewSurface\"")
        assertTrue(stop >= 0 && stop < close, "stopPreview phải đứng trước close (thứ tự 2.73)")
        assertTrue(close >= 0 && close < rm, "rmPreviewSurface phải đứng SAU close — đường mới xuống cuối, không đảo đường đã chạy")
        // Tên đã xác minh trong firmware: `removePreviewSurface` KHÔNG tồn tại ở đâu cả ⇒ không được thử tên thứ hai.
        assertTrue("removePreviewSurface" !in avm, "tên này không có trong SDK/framework BYD — đừng đoán thêm tên")
        assertTrue("Surface::class.java, Integer.TYPE" in body, "chữ ký (Surface, int) — IDiLinkAVMCamera.java:38")
        assertTrue("runCatching {" in body, "có thể ném khi camera đã đóng ⇒ phải bọc, và đó là ca BÌNH THƯỜNG")
        assertTrue("Log.d(" in body, "log mức DEBUG (không làm ồn nhật ký mỗi lượt xi-nhan)")
        // Cặp (surface, mode) phải là cặp HAL đã NHẬN, không phải một hằng đoán.
        assertTrue("added?.let { (surface, mode) ->" in body)
        assertTrue("added = surface to mode" in avm, "mode dùng khi dỡ phải là mode HAL đã nhận ở lượt mở")
    }

    // ══ (4) OVERLAY — bo TRÒN bằng đúng cơ chế bo góc của 2.73 ══════════════════════════════════════════════

    /**
     * Một [android.view.ViewOutlineProvider] duy nhất, hai nhánh: `setOval` cho hình tròn, `setRoundRect` cho chữ
     * nhật — **cùng** `clipToOutline` của 2.73. Mở một cơ chế cắt thứ hai (mask bitmap, `canvas.clipPath`) là thêm
     * một đường vẽ nữa vào đúng chỗ đang bị nghi là nguồn giật (CLOSE-14).
     */
    @Test fun `hinh tron dung dung ViewOutlineProvider cua 2 73`() {
        val body = SourceRoots.body(overlay, "private fun View.roundOutline(")
        assertTrue("outline.setOval(0, 0, v.width, v.height)" in body, "hình tròn = setOval trên chính outline đó")
        assertTrue("outline.setRoundRect(0, 0, v.width, v.height, radius)" in body, "chữ nhật bo góc 2.73 phải còn nguyên")
        assertTrue("clipToOutline = true" in body)
        assertEquals(1, Regex("""object : ViewOutlineProvider\(\)""").findAll(overlay).count(), "chỉ MỘT provider, hai nhánh")
        assertTrue("clipPath" !in overlay && "BitmapShader" !in overlay, "không mở cơ chế cắt thứ hai")
        // Nền đục cũng phải thành hình tròn, nếu không bốn góc đen lộ ra ngoài vòng bo.
        assertTrue("if (round) GradientDrawable.OVAL else GradientDrawable.RECTANGLE" in overlay)
        assertTrue("val round = shape == CameraSignalPolicy.SHAPE_ROUND" in overlay, "mã lạ ⇒ chữ nhật (mặc định 2.73)")
        assertTrue("shape: String = CameraSignalPolicy.SHAPE_RECT" in overlay, "mặc định tham số = hành vi 2.73")
        assertTrue("roundOutline(radius, round)" in overlay, "cờ phải thật sự đi vào provider")
    }

    // ══ (5) CÀI ĐẶT · PREFS · CẦU KIỂM THỬ ═════════════════════════════════════════════════════════════════

    /** Bốn hàng chip, mọi mã lấy từ hằng `:core`, không một chuỗi/số nào chép vào tệp Cài đặt. */
    @Test fun `cai dat co bon hang chip lay ma tu core`() {
        val body = SourceRoots.body(settings, "private fun cameraSignal(")
        listOf("SPAN_NARROW", "SPAN_STRIP", "SHAPE_RECT", "SHAPE_ROUND").forEach {
            assertTrue("CameraSignalPolicy.$it to " in body, "chip $it phải lấy mã từ hằng `:core`")
        }
        assertTrue("CameraPanoCrop.STRIPS_ALL.map" in body, "chip dải phải SINH từ `:core` (0..3), không viết tay bốn chip")
        assertTrue("CameraSignalPolicy.HAL_MODES.map" in body, "chip kênh HAL phải sinh từ miền hằng BYD ở `:core`")
        assertTrue("CameraSignalPolicy.HAL_MODE_AUTO" in body, "chip đầu = tự dò = đường 2.73")
        // Mã lưu bền không được chép trần vào Cài đặt (bẫy hai-bản-sao mà `ProfileNames` đã trả giá).
        listOf("\"NARROW\"", "\"STRIP\"", "\"RECT\"", "\"ROUND\"").forEach {
            assertTrue(it !in settings, "mã $it bị chép trần — dùng hằng CameraSignalPolicy")
        }
        // Bốn hàng, bốn cặp getter/setter qua cầu (Cài đặt không ghi Prefs thẳng).
        listOf(
            "bridge.cameraSpan()" to "bridge.setCameraSpan(v)",
            "bridge.cameraShape()" to "bridge.setCameraShape(v)",
            "bridge.cameraStripLeft()" to "bridge.setCameraStrip(left = true, v = it)",
            "bridge.cameraStripRight()" to "bridge.setCameraStrip(left = false, v = it)",
            "bridge.cameraHalMode()" to "bridge.setCameraHalMode(it)",
        ).forEach { (get, set) ->
            assertTrue(get in body, "hàng chip thiếu getter $get")
            assertTrue(set in body, "hàng chip thiếu setter $set")
        }
        listOf("cameraSpan", "cameraShape", "cameraStripLeft", "cameraStripRight", "cameraHalMode").forEach {
            assertTrue("fun ClusterNavBridge.$it(" in bridge, "cầu thiếu $it")
        }
    }

    /** Chữ của bốn hàng có ở CẢ hai ngôn ngữ, và nhãn hình tròn nói THẲNG là **chưa nắn méo**. */
    @Test fun `chu cua bon hang co o ca hai ngon ngu`() {
        val keys = listOf(
            "kachi_camera_span_sub", "kachi_camera_span_row", "kachi_camera_span_narrow", "kachi_camera_span_strip",
            "kachi_camera_strip_sub", "kachi_camera_strip_left", "kachi_camera_strip_right",
            "kachi_camera_shape_sub", "kachi_camera_shape_row", "kachi_camera_shape_rect", "kachi_camera_shape_round",
            "kachi_camera_hal_sub", "kachi_camera_hal_row", "kachi_camera_hal_auto",
        )
        keys.forEach { k ->
            assertTrue("\"$k\"" in vi, "thiếu chữ tiếng Việt cho $k")
            assertTrue("\"$k\"" in en, "thiếu chữ tiếng Anh cho $k")
            assertTrue("R.string.$k" in settings, "chữ $k không được dùng ⇒ tài nguyên mồ côi")
        }
        assertTrue("méo" in vi.substringAfter("kachi_camera_shape_sub").take(160),
            "nhãn hình tròn phải nói rõ là CHƯA nắn méo — owner không được hiểu là đã nắn (CLAUDE.md §2)")
    }

    /** Năm pref: mặc định + phép kiểm lấy từ `:core`, device-scope, và cả sáu khoá `prefs_set` có `read_back`. */
    @Test fun `nam pref mac dinh core, device scope, sau khoa vao danh sach trang`() {
        mapOf(
            "fun Prefs.cameraSpan(" to listOf("CameraSignalPolicy.defaultSpan()", "CameraSignalPolicy.isSpan(raw)"),
            "fun Prefs.cameraShape(" to listOf("CameraSignalPolicy.defaultShape()", "CameraSignalPolicy.isShape(raw)"),
            "fun Prefs.cameraStrip(" to listOf("CameraPanoCrop.defaultStrip(left)", "CameraPanoCrop.isStrip(raw)"),
            "fun Prefs.cameraCirclePct(" to listOf("CameraSignalPolicy.CIRCLE_PCT_DEFAULT", "CameraSignalPolicy.isCirclePct(raw)"),
            "fun Prefs.cameraHalMode(" to listOf("CameraSignalPolicy.HAL_MODE_AUTO", "CameraSignalPolicy.isHalMode(raw)"),
        ).forEach { (sig, needles) ->
            val body = SourceRoots.body(prefs, sig)
            needles.forEach { assertTrue(it in body, "$sig thiếu $it (mặc định/phép kiểm phải ở `:core`)") }
            assertTrue("autoPrefs(ctx)" in body, "$sig phải device-scope: cách HAL ghép ảnh là chuyện của XE")
        }
        val keys = mapOf(
            "camera_span" to "Prefs.cameraSpan(app)",
            "camera_shape" to "Prefs.cameraShape(app)",
            "camera_strip_left" to "Prefs.cameraStrip(app, left = true).toString()",
            "camera_strip_right" to "Prefs.cameraStrip(app, left = false).toString()",
            "camera_circle_scale" to "Prefs.cameraCirclePct(app).toString()",
            "camera_hal_mode" to "Prefs.cameraHalMode(app).toString()",
        )
        keys.forEach { (key, readBack) ->
            assertTrue(key in TestBridgeCommands.WRITABLE_PREFS_KEYS, "dò trên xe cần prefs_set $key")
            assertTrue("\"$key\" ->" in prefsSet, "prefs_set thiếu nhánh ghi cho $key")
            assertTrue("\"$key\" -> $readBack" in prefsSet, "read_back của $key phải đọc lại từ nơi lưu bền")
            assertTrue("\"$key\"" in prefs, "tên khoá phải khai ở PrefsAutomation")
        }
        // Giá trị ngoài dải bị TỪ CHỐI (bad_prefs_value), không kẹp im lặng — một lượt dò bị kẹp là một kết luận sai.
        listOf("CameraSignalPolicy.isSpan(it)", "CameraSignalPolicy.isShape(it)", "CameraPanoCrop.isStrip(it)",
            "CameraSignalPolicy.isCirclePct(it)", "CameraSignalPolicy.isHalMode(it)").forEach {
            assertTrue(it in prefsSet, "prefs_set thiếu phép kiểm $it")
        }
    }
}
