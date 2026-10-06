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
 *  3. `rmPreviewSurface` gọi **GIỮA** `stopPreview` và `close` (2.75 — [ĐO] xe 27/09 `rc=false` khi gọi sau
 *     `close`), đúng tên đã xác minh trong firmware;
 *  4. overlay bo TRÒN bằng **đúng** `ViewOutlineProvider` của 2.73 (không thêm cơ chế cắt thứ hai);
 *  5. bốn hàng chip + sáu khoá `prefs_set` (có `read_back`) — thiếu chúng thì owner không dò được gì trên xe;
 *  6. (2.75) `camera_dewarp_pan_x` mang **dấu theo bên** — hai camera gương soi gương nhau.
 */
class CameraSpanShapeWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val overlay by lazy { app("launcher/camera/CameraOverlayView.kt") }
    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }
    private val avm by lazy { app("launcher/camera/AvmCamera.kt") }
    // 2.76 · R1: camera tách khỏi `SettingsSectionsCar` sang tệp riêng, hai tầng (người lái / kỹ thuật).
    private val settings by lazy { app("launcher/SettingsSectionsCamera.kt") }
    private val prefs by lazy { app("PrefsAutomation.kt") }
    private val prefsDewarp by lazy { app("PrefsCameraDewarp.kt") }
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
        // 2.92: vùng cắt KHUNG + NỘI DUNG suy ở `:core` CameraViewPlan.crops (gọi đúng CameraPanoCrop.cropFor của hôm nay
        // cho *Nắn thẳng*) — controller chỉ đọc pref và chuyển xuống.
        assertTrue("CameraViewPlan.crops(" in controller, "crop phải do `:core` suy ra (có test bằng số)")
        val plan = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/camera/CameraViewPlan.kt")
        assertTrue("CameraPanoCrop.cropFor(" in plan, "kế hoạch `:core` phải đi qua đúng phép cắt của hôm nay")
        assertTrue("val crop = view.crop" !in controller, "hằng crop của enum không được đọc thẳng nữa (chip sẽ vô tác dụng)")
        listOf(
            "Prefs.cameraSpan(appCtx)",
            "Prefs.cameraShape(appCtx)",
            "Prefs.cameraStrip(appCtx, left = isLeft)",
            "Prefs.cameraCirclePct(appCtx)",
        ).forEach { assertTrue(it in controller, "thiếu lượt đọc pref: $it") }
        // Chỉ số dải đọc theo ĐÚNG BÊN xi-nhan (hai khoá độc lập, y khuôn camera_rot_left/right).
        // 2026-09-28: dải hiệu lực nay là `effStrip` = dải ảnh ghép người lái chọn, lùi về pref dải cũ.
        assertTrue("strip = effStrip" in controller, "cropFor phải nhận DẢI HIỆU LỰC, không phải pref thô")
        assertTrue(
            "val effStrip = panoStrip ?: Prefs.cameraStrip(appCtx, left = isLeft)" in controller,
            "dải hiệu lực phải ưu tiên lựa chọn ảnh ghép rồi mới lùi về pref cũ",
        )
        assertTrue(
            "CameraPanoCrop.panoStripFor(view, Prefs.cameraPano(appCtx, left = isLeft), left = isLeft)" in controller,
            "quyết định 'nguồn có phải ảnh ghép không' phải nằm ở `:core`, không rẽ nhánh trong app",
        )
        // Hình khung đi tiếp xuống tầng vẽ; kênh HAL đi tiếp xuống tầng mở camera.
        assertTrue("shape = shape," in controller, "hình khung phải vào overlay.show(shape = …)")
        // 2.77: **MỘT** nguồn (khung ghép) ⇒ `AvmCamera.open` không còn tham số kênh, và mọi mảnh của nguồn một-kênh
        // phải VẮNG khỏi controller. Thấy lại một trong số chúng = nguồn đã bị dựng lại mà không ai đo lại
        // ([ĐO xe 27/09] một kênh KHÔNG nét hơn: năng lượng cạnh 686 vs 351, chi tiết ngang/dọc 0,30 vs 0,19).
        assertTrue("avm.open(camId, surface)" in controller, "lượt mở camera chỉ còn (camId, surface)")
        listOf(
            "CameraSignalPolicy.channelFor(", "Prefs.cameraHalMode(appCtx)", "Prefs.cameraSource(appCtx)",
            "CameraSignalPolicy.channelActive(", "CameraPanoCrop.contentWidth(", "CameraChannelFallback",
            "fallbackToPano", "channel = channel,", "CameraDefaults.of(appCtx).channel(", "view.channel",
        ).forEach { assertTrue(it !in controller, "`$it` đã gỡ ở 2.77 cùng nguồn *Một camera* — không được đọc lại") }
        // Một dòng log đủ để đọc lại quyết định trên xe (CLAUDE.md §11: app tự chụp, owner không gõ adb).
        assertTrue("vùng=\$span" in controller && "hình=\$shape" in controller,
            "dòng log của controller phải nói vùng/hình — đó là thứ owner đọc lại khi chốt dải")
        assertTrue("halMode" !in controller.substringAfter("Log.i(PanoramaHal.TAG, \"xi-nhan"),
            "dòng log không còn nói kênh HAL (không còn kênh nào để chọn)")
        assertTrue("rot=\$rot lật=\$mirror\")" in controller, "dòng log vẫn kết bằng rot= (hợp đồng của bài R7)")
    }

    /**
     * ═══ `camera_dewarp_pan_x` phải mang dấu theo **BÊN** — [ĐO khung thô xe 27/09 09:58] ═════════════════════
     *
     * Khung `camera_frame` `5120×960` của buổi xe: hai camera gương là **ảnh soi gương của nhau** (thân xe ở mép
     * PHẢI ô gương trái, mép TRÁI ô gương phải; tương quan lật ngang 0,715 · không lật 0,152). Gỡ dòng `left =` ⇒
     * một núm kéo hai khung về hai phía **ngược nhau** mà không một bài `:core` nào đỏ (số học ở
     * [CameraDewarpPrefs.panXSign] vẫn đúng, chỉ là không ai gọi) — đúng khuôn bẫy CLAUDE.md §8.
     */
    @Test fun `pan_x mang dau theo ben, tu controller xuong core`() {
        assertTrue("left = turn == Turn.LEFT," in controller,
            "controller phải nói BÊN cho bộ uniform — hai camera gương soi gương nhau")
        val body = SourceRoots.body(prefsDewarp, "fun Prefs.cameraGlUniforms(")
        // 2.92: dấu suy trong kế hoạch `:core` (CameraViewPlan.gl) — cameraGlUniforms chỉ chuyển BÊN xuống.
        assertTrue("left = left," in body, "cameraGlUniforms phải chuyển BÊN xuống kế hoạch `:core`")
        val plan = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/camera/CameraViewPlan.kt")
        assertTrue("val sign = CameraDewarpPrefs.panXSign(left)" in plan && "panXSign = sign," in plan,
            "dấu phải suy ở `:core` (có test bằng số), không phải một `if` ở `:app`")
        // Tham số nằm ở CHỮ KÝ (ngoài thân hàm) ⇒ đọc trên nguyên tệp. `left` KHÔNG có `=` ⇒ không quên được.
        assertTrue("    left: Boolean,\n" in prefsDewarp, "`left` phải là tham số BẮT BUỘC của cameraGlUniforms")
        assertTrue("left: Boolean =" !in prefsDewarp, "`left` KHÔNG được có mặc định — mặc định là chỗ để quên")
        // `pan_y` KHÔNG lật: hai camera soi gương quanh trục DỌC, nên lên/xuống giống nhau ở hai bên.
        assertTrue("panYSign" !in body && "panYSign" !in controller, "pan_y không có dấu theo bên")
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
    @Test fun `vong do 0 3 cua 2 73 con nguyen van va la duong DUY NHAT`() {
        assertTrue("            for (mode in 0..3) {" in avm, "vòng dò phải giữ đúng thứ tự 0..3 của 2.73")
        assertTrue(
            "                if (runCatching { add.invoke(obj, surface, mode); true }.getOrDefault(false)) {" in avm,
            "lời gọi HAL trong vòng dò phải giữ nguyên từng byte (đổi là đổi đường đang chạy trên xe)",
        )
        // 2.77: không còn nhánh "gọi ĐÚNG một lần với kênh n" nào — nguồn một-kênh đã bỏ, nên vòng dò là đường DUY
        // NHẤT và `open` không nhận tham số kênh. Chữ ký gọn lại cũng là cách bài này chặn việc dựng lại nhánh ấy.
        assertTrue("fun open(cameraId: Int, surface: Surface): Boolean {" in avm, "`open` chỉ còn (cameraId, surface)")
        listOf("halMode", "channelRefused", "CameraProfileDefaults.isChannel", "HAL_MODE").forEach {
            assertTrue(it !in avm, "`$it` đã gỡ ở 2.77 — nhánh kênh đơn không được dựng lại")
        }
        assertEquals(1, Regex("""for \(mode in 0\.\.3\)""").findAll(avm).count(), "đúng MỘT vòng dò")
        // Và cổng của bước 3 khi dỡ — mặc định TẮT ⇒ chuỗi dỡ y 2.73 (xem KDoc `AvmCamera.rmOnClose`).
        assertTrue("var rmOnClose: Boolean = false" in avm, "móc ĐO `rmPreviewSurface` phải mặc định TẮT")
        assertTrue("if (rmOnClose) added?.let" in avm, "và lời gọi phải nằm sau cổng ấy")
    }

    /**
     * `close()`: `stopPreview` → `rmPreviewSurface` (móc ĐO, có cổng) → `close` — kiểm bằng **vị trí** trong thân
     * hàm, không chỉ bằng `contains` (một `contains` không thấy được thứ tự, mà thứ tự đúng là điều phải giữ).
     *
     * ⚠ **Đổi so với 2.74** (bài này trước đây ghim `rm` đứng SAU `close`): [ĐO xe 27/09] bốn lượt dỡ đều ghi
     * `rmPreviewSurface(mode=0) rc=false` — HAL từ chối vì camera đã đóng ⇒ vị trí cũ làm móc đo **không đo được
     * gì**. Vị trí mới cũng là thứ tự Electro dùng (`stop → rm → release`, RE §3.1). CLAUDE.md §6 (*"không đảo
     * đường đã chạy tốt"*) không bị vi phạm: nhánh này **chỉ** chạy khi chế độ kiểm thử đang mở, nên đường của xe
     * lúc chạy bình thường vẫn đúng hai lời gọi `stopPreview` → `close` của 2.73.
     */
    @Test fun `close giu thu tu 2 73 roi moi rmPreviewSurface`() {
        val body = SourceRoots.body(avm, "fun close(")
        val stop = body.indexOf("\"stopPreview\"")
        val close = body.indexOf("getDeclaredMethod(\"close\")")
        val rm = body.indexOf("\"rmPreviewSurface\"")
        assertTrue(stop >= 0 && stop < rm, "stopPreview phải đứng trước rmPreviewSurface (thứ tự 2.73 giữ nguyên)")
        assertTrue(rm >= 0 && rm < close, "rmPreviewSurface phải đứng TRƯỚC close — gọi sau thì HAL từ chối (rc=false)")
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

    /**
     * 2.77 — chỉ **hàng HÌNH KHUNG** còn UI; ba hàng dò (bề rộng · dải · kênh HAL) + hai hàng cameraId đã gỡ.
     *
     * Owner trên xe 27/09: *"bỏ hết phần nâng cao đi"*. Mã lưu bền vẫn **không được** chép trần vào Cài đặt.
     */
    @Test fun `cai dat chi con hang chip hinh khung, lay ma tu core`() {
        val user = SourceRoots.body(settings, "private fun cameraUser(")
        assertTrue("CameraSignalPolicy.SHAPES.map { it to shapeLabel(it) }" in user,
            "chip hình khung SINH từ `:core` SHAPES ⇒ ô CLUSTER của làn L2 tự có chip")
        listOf("SHAPE_RECT", "SHAPE_ROUND", "SHAPE_CLUSTER").forEach {
            assertTrue("CameraSignalPolicy.$it ->" in settings, "nhãn chip $it phải tra theo hằng `:core`")
        }
        assertTrue("bridge.cameraShape()" in user && "bridge.setCameraShape(v)" in user, "hàng chip nối qua cầu")
        // Ba hàng dò + hai hàng cameraId đã gỡ — chúng chỉ còn đường `prefs_set`.
        listOf("CameraPanoCrop.STRIPS_ALL.map", "CameraSignalPolicy.SPAN_NARROW to ", "CameraSignalPolicy.SPAN_STRIP to ",
            "CameraSignalPolicy.HAL_MODES.map", "bridge.setCameraSpan(v)", "bridge.setCameraStrip(",
            "bridge.setCameraCamLeft(", "bridge.setCameraCamRight(").forEach {
            assertTrue(it !in settings, "`$it` đã gỡ khỏi Cài đặt ở 2.77")
        }
        // Mã lưu bền không được chép trần vào Cài đặt (bẫy hai-bản-sao mà `ProfileNames` đã trả giá).
        listOf("\"NARROW\"", "\"STRIP\"", "\"RECT\"", "\"ROUND\"", "\"CLUSTER\"").forEach {
            assertTrue(it !in settings, "mã $it bị chép trần — dùng hằng CameraSignalPolicy")
        }
        // Cầu vẫn còn cửa cho các khoá không-UI (cầu kiểm thử ghi qua `prefs_set`, không qua cầu Cài đặt) —
        // nhưng hai cửa của nguồn một-kênh phải XOÁ.
        listOf("cameraSpan", "cameraShape", "cameraStripLeft", "cameraStripRight").forEach {
            assertTrue("fun ClusterNavBridge.$it(" in bridge, "cầu thiếu $it")
        }
        listOf("cameraSource", "setCameraSource", "cameraHalMode", "setCameraHalMode").forEach {
            assertTrue("fun ClusterNavBridge.$it(" !in bridge, "cửa cầu $it phải xoá cùng nguồn một-kênh")
        }
    }

    /**
     * Chữ của hàng còn lại có ở CẢ hai ngôn ngữ. 2.92 (spec `kachi-292-camera-full-view` §4.7): nhãn KHÔNG còn hứa
     * "tròn = trọn vòng ảnh, chưa nắn méo" — điều đó sai từ khi GL nắn cả khung tròn, và nay việc nắn/không nắn là của
     * hàng *Kiểu hình* — nên nhãn phải chỉ thẳng sang kiểu *Gương cầu* (owner không được hiểu nhầm, CLAUDE.md §2).
     */
    @Test fun `chu cua hang hinh khung co o ca hai ngon ngu`() {
        val keys = listOf(
            "kachi_camera_shape_sub", "kachi_camera_shape_row", "kachi_camera_shape_rect", "kachi_camera_shape_round",
        )
        keys.forEach { k ->
            assertTrue("\"$k\"" in vi, "thiếu chữ tiếng Việt cho $k")
            assertTrue("\"$k\"" in en, "thiếu chữ tiếng Anh cho $k")
            assertTrue("R.string.$k" in settings, "chữ $k không được dùng ⇒ tài nguyên mồ côi")
        }
        val head = vi.substringAfter("\"kachi_camera_shape_sub\">").substringBefore("</string>")
        assertTrue("Gương cầu" in head, "nhãn hình khung phải chỉ sang kiểu «Gương cầu» cho ảnh trọn chưa nắn: $head")
        assertTrue("tròn =" !in head, "nhãn không được hứa hình tròn = chưa nắn (GL nắn cả khung tròn): $head")
    }

    /** Bốn pref: mặc định + phép kiểm lấy từ `:core`, device-scope, và cả năm khoá `prefs_set` có `read_back`. */
    @Test fun `bon pref mac dinh core, device scope, nam khoa vao danh sach trang`() {
        mapOf(
            // 2.76 · R2: mặc định bề rộng theo HỒ SƠ XE (Seal STRIP / chưa đo NARROW), khoá đã đặt thắng.
            "fun Prefs.cameraSpan(" to listOf("CameraDefaults.of(ctx).span", "CameraSignalPolicy.isSpan(raw)"),
            "fun Prefs.cameraShape(" to listOf("CameraSignalPolicy.defaultShape()", "CameraSignalPolicy.isShape(raw)"),
            "fun Prefs.cameraStrip(" to listOf("CameraPanoCrop.defaultStrip(left)", "CameraPanoCrop.isStrip(raw)"),
            "fun Prefs.cameraCirclePct(" to listOf("CameraSignalPolicy.CIRCLE_PCT_DEFAULT", "CameraSignalPolicy.isCirclePct(raw)"),
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
        )
        keys.forEach { (key, readBack) ->
            assertTrue(key in TestBridgeCommands.WRITABLE_PREFS_KEYS, "dò trên xe cần prefs_set $key")
            assertTrue("\"$key\" ->" in prefsSet, "prefs_set thiếu nhánh ghi cho $key")
            assertTrue("\"$key\" -> $readBack" in prefsSet, "read_back của $key phải đọc lại từ nơi lưu bền")
            assertTrue("\"$key\"" in prefs, "tên khoá phải khai ở PrefsAutomation")
        }
        // Giá trị ngoài dải bị TỪ CHỐI (bad_prefs_value), không kẹp im lặng — một lượt dò bị kẹp là một kết luận sai.
        listOf("CameraSignalPolicy.isSpan(it)", "CameraSignalPolicy.isShape(it)", "CameraPanoCrop.isStrip(it)",
            "CameraSignalPolicy.isCirclePct(it)").forEach {
            assertTrue(it in prefsSet, "prefs_set thiếu phép kiểm $it")
        }
        // 2.77: hai nhánh của nguồn một-kênh phải XOÁ khỏi `prefs_set` — ghi được một khoá không ai đọc là báo `ok`
        // rồi không làm gì (tệ hơn một lệnh lỗi).
        listOf("\"camera_source\"", "\"camera_hal_mode\"").forEach {
            assertTrue(it !in prefsSet, "nhánh $it phải xoá khỏi prefs_set")
            assertTrue(it !in prefs, "khoá $it phải xoá khỏi PrefsAutomation")
        }
    }
}
