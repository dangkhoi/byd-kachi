package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.testbridge.TestBridgeWritableKeys
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · CAMERA-PER-CAM-CONFIG — bài canh DÂY NỐI của `:app` (spec `docs/specs/kachi-293-cam.html` R2 · §4.3 · §4.5) ═══
 *
 * Tên khoá · miền · *"theo chung"* · phép đặt chỗ đã có bài chạy thật ở `:core` (`CameraCamConfigTest`,
 * `CameraPlacementTest`). Ở đây canh những mắt xích chỉ nhìn được trong mã Android:
 *  1. hai camera GƯƠNG đi qua ĐÚNG hàm đọc/ghi 2.35/2.71/2.76 (di trú khoá đơn 2.67 + mặc định hồ sơ xe) — đọc lại bằng
 *     `getString` thô là Seal thấy ↺90 thay vì 0 sau nâng cấp, và không một bài `:core` nào đỏ;
 *  2. đường đặt chỗ riêng của overlay CHỈ chạy khi đã kéo/đổi cỡ (xe không chạm Cài đặt ⇒ đường 2.73–2.92 nguyên văn);
 *  3. bộ chỉnh ghi xong áp NGAY đúng camera đang hiện, không dựng controller chỉ vì mở Cài đặt;
 *  4. `prefs_set` ghi/đọc lại đủ 22 khoá mới, mỗi loại một phép kiểm `:core`, sai miền ⇒ từ chối.
 */
class CameraPerCamPrefsWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val prefs by lazy { app("PrefsCameraPerCam.kt") }
    private val bridge by lazy { app("launcher/ClusterNavBridgeCameraPerCam.kt") }
    private val section by lazy { app("launcher/SettingsSectionsCameraPerCam.kt") }
    private val overlay by lazy { app("launcher/camera/CameraOverlayView.kt") }
    private val layout by lazy { app("launcher/camera/CameraOverlayLayout.kt") }
    private val perCamSet by lazy { app("launcher/testbridge/TestBridgePerCam.kt") }

    /** (1) Camera gương: ĐÚNG hàm cũ, TRƯỚC mọi lượt đọc khoá mới; camera giữa: khoá mới + phép kiểm `:core`. */
    @Test fun `camera guong di qua ham cu, camera giua kiem bang core`() {
        mapOf(
            "fun Prefs.cameraCornerOf(" to ("if (w.side) return cameraPos(ctx, left = w == CameraWhich.LEFT)" to "CameraSignalPolicy.isCorner(raw)"),
            "fun Prefs.cameraRotationOf(" to ("if (w.side) return cameraRotation(ctx, left = w == CameraWhich.LEFT)" to "CameraSignalPolicy.isRotation(raw)"),
        ).forEach { (sig, pair) ->
            val (legacy, check) = pair
            val body = SourceRoots.body(prefs, sig)
            assertTrue(body.indexOf(legacy) in 0 until body.indexOf("getString("), "$sig: camera gương phải rẽ về hàm cũ TRƯỚC")
            assertTrue(check in body, "$sig: giá trị lạ trên đĩa ⇒ mặc định (`:core`)")
        }
        assertTrue("if (w.side) cameraMirror(ctx, left = w == CameraWhich.LEFT)" in SourceRoots.body(prefs, "fun Prefs.cameraMirrorOf("))
        // Ghi: camera gương đi qua ĐÚNG hàm ghi cũ (cùng khoá, cùng nơi lưu).
        assertTrue("if (w.side) setCameraRotation(ctx, left = w == CameraWhich.LEFT, v = v)" in SourceRoots.body(prefs, "fun Prefs.setCameraRotationOf("))
        assertTrue("if (w.side) setCameraMirror(ctx, left = w == CameraWhich.LEFT, v = v)" in SourceRoots.body(prefs, "fun Prefs.setCameraMirrorOf("))
        // Miền + *theo chung* tra ở `:core`, không con số nào ở đây.
        assertTrue("CameraCamConfig.isSizePct(raw)" in SourceRoots.body(prefs, "fun Prefs.cameraSize("))
        assertTrue("CameraCamConfig.parsePlace(" in SourceRoots.body(prefs, "fun Prefs.cameraPlace("))
        assertTrue("CameraCamConfig.effectiveShape(" in SourceRoots.body(prefs, "fun Prefs.cameraShapeOf("))
        assertTrue("CameraCamConfig.effectiveProjection(" in SourceRoots.body(prefs, "fun Prefs.cameraProjectionOf("))
        // Một nơi lưu: `clusternav_prefs` qua `autoPrefs` (ProfileScope xếp loại đúng tệp này) — không mở cửa thứ hai.
        assertFalse("getSharedPreferences" in prefs, "không mở tệp prefs thứ hai cho cùng dữ liệu")
        assertTrue("e.remove(CameraCamConfig.placeKey(w))" in SourceRoots.body(prefs, "fun Prefs.setCameraPlace("),
            "*Đặt lại vị trí* = XOÁ khoá (về góc mặc định ⇒ đường 2.73), không ghi một toạ độ giả")
    }

    /** (2) Overlay: đường riêng CHỈ khi đã kéo/đổi cỡ; cùng loại cửa sổ + cờ không nhận chạm của 2.73. */
    @Test fun `overlay di duong rieng chi khi da keo hoac doi co`() {
        assertTrue("class CameraOverlayPlace private constructor(" in layout, "chỉ dựng qua `of` — không lách được phép `custom`")
        assertTrue("if (CameraPlacement.custom(place, sizePct)) CameraOverlayPlace(place, sizePct) else null" in layout,
            "chưa kéo + 100 % ⇒ null ⇒ đường 2.73–2.92 nguyên văn (CLAUDE.md §6)")
        val geo = SourceRoots.body(overlay, "private fun geometry(st: Live): Geo")
        assertTrue(geo.indexOf("st.place?.let") in 0 until geo.indexOf("if (st.onCluster)"), "đường riêng rẽ TRƯỚC hai đường cũ")
        assertTrue("place: CameraOverlayPlace? = null," in overlay, "mặc định tham số = hành vi cũ")
        val custom = SourceRoots.body(layout, "internal fun customOverlayGeo(")
        assertTrue("CameraPlacement.cluster(" in custom && "CameraPlacement.main(" in custom, "toán ở `:core` (có test bằng số)")
        assertTrue("CameraClusterBand.band(displayW, displayH, band)" in custom, "cụm: CÙNG dải theo hồ sơ xe với đường cũ")
        assertTrue("WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE" in custom,
            "cửa sổ không nhận chạm — Cài đặt bên dưới vẫn bấm được lúc Xem thử")
        assertTrue("TYPE_APPLICATION_OVERLAY" in custom)
        val show = SourceRoots.body(app("launcher/camera/CameraSignalController.kt"), "private fun openSession(")
        assertTrue("place = CameraOverlayPlace.of(s.place, s.sizePct)," in show, "controller phải chuyển chỗ + cỡ của ĐÚNG camera")
    }

    /** (3) Cầu Cài đặt: áp ngay CHỈ khi đúng camera ấy đang hiện; không dựng controller; chọn góc = bỏ vị trí kéo. */
    @Test fun `cau cai dat ap ngay dung camera, khong dung controller`() {
        // 2.93 wave 2B · D2 — Cài đặt và `prefs_set` đi CÙNG một cửa ([CameraReapply.ifShowing]); luật giữ nguyên ý.
        val reapply = SourceRoots.body(bridge, "private fun ClusterNavBridge.reapplyIf(w: CameraWhich)")
        assertTrue("CameraReapply.ifShowing(app, w)" in reapply, "cầu Cài đặt phải đi cửa áp lại DÙNG CHUNG với prefs_set")
        val door = SourceRoots.body(app("launcher/camera/CameraReapply.kt"), "fun ifShowing(ctx: Context, which: CameraWhich)")
        assertTrue("c.cameraSignalCreated && c.cameraSignal.showingCamera() == which" in door, "camera khác đang hiện ⇒ để yên; không dựng controller")
        assertTrue("c.cameraSignal.reapplyIfShowing()" in door, "cùng cửa áp lại của 2.92 (dỡ + dựng, không chạm HOLD)")
        listOf(
            "fun ClusterNavBridge.setCameraCorner(", "fun ClusterNavBridge.setCameraPlace(", "fun ClusterNavBridge.setCameraSize(",
            "fun ClusterNavBridge.setCameraShapeChoice(", "fun ClusterNavBridge.setCameraProjectionChoice(",
            "fun ClusterNavBridge.setCameraRotationOf(", "fun ClusterNavBridge.setCameraMirrorOf(",
        ).forEach { assertTrue("reapplyIf(w)" in SourceRoots.body(bridge, it), "$it phải áp ngay nếu camera ấy đang hiện") }
        val corner = SourceRoots.body(bridge, "fun ClusterNavBridge.setCameraCorner(")
        assertTrue("Prefs.setCameraPlace(app, w, null)" in corner, "chọn góc = về góc ấy (bỏ vị trí kéo-thả)")
        listOf("fun ClusterNavBridge.cameraDemanded(", "fun ClusterNavBridge.cameraShowing(", "fun ClusterNavBridge.onCameraDemandChanged(").forEach {
            assertTrue("if (c.cameraSignalCreated)" in SourceRoots.body(bridge, it), "$it: mở Cài đặt không được DỰNG controller")
        }
        val model = SourceRoots.body(bridge, "fun ClusterNavBridge.cameraPlacementModel(")
        assertTrue("CameraSessionSpec.read(app, w)" in model, "ô kéo-thả lấy tỉ lệ khung từ CÙNG lượt đọc của phiên camera")
        assertTrue("CameraDefaults.band(app)" in model && "CameraPlacement.mainRegion(" in model, "cùng vùng cho phép với overlay")
        assertTrue("CameraDemandDispatch.fire(app, CameraDemand.Op.Toggle(w))" in SourceRoots.body(bridge, "fun ClusterNavBridge.toggleCameraDemand("),
            "nút Xem thử = CÙNG đường của nút thanh nút / phím vật lý")
    }

    /** (3b) Bộ chỉnh: nút Xem thử theo trạng thái THẬT, nghe theo vòng đời cửa sổ (gỡ khi rời), ô kéo-thả đọc lại prefs. */
    @Test fun `bo chinh tung camera - xem thu theo su that, nghe co go`() {
        val build = SourceRoots.body(section, "    fun build(")
        assertTrue("override fun onViewAttachedToWindow(v: View) { listen(); paintPreview(); bindPlacement() }" in build)
        assertTrue("override fun onViewDetachedFromWindow(v: View) { unlisten(); unlisten = {} }" in build, "rời cửa sổ ⇒ gỡ người nghe")
        // wave 2B: luật "đang mở VÀ đang hiện" nay là MỘT hàm `:core` (`CameraDemand.isOn`) dùng chung với ô thanh nút/widget.
        assertTrue("CameraDemand.isOn(which, bridge.cameraDemanded(), bridge.cameraShowing())" in SourceRoots.body(section, "private fun paintPreview("),
            "chữ nút theo controller (đang mở VÀ đang hiện — cùng luật toggle), không theo cờ màn")
        val listen = SourceRoots.body(section, "private fun listen(")
        assertTrue(listen.indexOf("unlisten()") in 0 until listen.indexOf("bridge.onCameraDemandChanged"), "gỡ lượt nghe cũ TRƯỚC")
        val ed = SourceRoots.body(section, "private fun rebuild(")
        assertTrue("bridge.toggleCameraDemand(w)" in ed, "nút Xem thử nối đường camera chung")
        assertTrue("bridge.setCameraPlace(w, p)" in ed && "bridge.setCameraPlace(w, null)" in ed, "kéo-thả + Về góc mặc định")
        assertTrue("CameraCamConfig.SIZE_POSITIONS" in ed && "CameraCamConfig.sizeAt(pos)" in ed, "miền cỡ ở `:core`")
        assertTrue("(listOf(CameraCamConfig.FOLLOW) + CameraSignalPolicy.SHAPES)" in ed && "(listOf(CameraCamConfig.FOLLOW) + CameraViewMode.MODES)" in ed,
            "chip đầu = *Theo chung*, phần còn lại SINH từ `:core`")
        assertFalse("Prefs." in section, "bộ chỉnh 100 % qua cầu (N2) — không chạm Prefs trực tiếp")
    }

    /** (4) `prefs_set`: đủ 22 khoá mới vào danh sách trắng, một nhánh THEO LOẠI, mỗi loại một phép kiểm `:core`. */
    @Test fun `prefs_set ghi va doc lai du 22 khoa moi, sai mien thi tu choi`() {
        assertEquals(22, CameraCamConfig.NEW_KEYS.size)
        CameraCamConfig.NEW_KEYS.forEach { assertTrue(it in TestBridgeWritableKeys.ALL, "$it thiếu trong danh sách trắng") }
        assertTrue("fun owns(key: String): Boolean = key in CameraCamConfig.NEW_KEYS" in perCamSet)
        val set = app("launcher/testbridge/TestBridgePrefsSet.kt")
        assertTrue("else -> if (TestBridgePerCam.owns(cmd.key)) TestBridgePerCam.write(app, cmd.key, raw)" in set, "nhánh ghi")
        assertTrue("else -> if (TestBridgePerCam.owns(key)) TestBridgePerCam.read(app, key) else \"\"" in set, "read_back")
        val write = SourceRoots.body(perCamSet, "fun write(app: Context, key: String, raw: String): String?")
        listOf(
            "CameraSignalPolicy.isCorner(it)", "CameraCamConfig.parsePlace(v)", "CameraCamConfig.isSizePct(it)",
            "CameraCamConfig.isShapeChoice(it)", "CameraCamConfig.isProjectionChoice(it)", "CameraSignalPolicy.isRotation(it)",
            "TestBridgePrefsSet.bool(v)",
        ).forEach { assertTrue(it in write, "prefs_set thiếu phép kiểm $it (sai miền phải bị từ chối, không kẹp im lặng)") }
        assertTrue("Prefs.setCameraPlace(app, w, null); CameraCamConfig.FOLLOW" in write, "`AUTO` = xoá vị trí kéo-thả")
        val locate = SourceRoots.body(perCamSet, "private fun locate(key: String)")
        listOf("cornerKey", "placeKey", "sizeKey", "shapeKey", "projectionKey", "rotationKey", "mirrorKey").forEach {
            assertTrue("CameraCamConfig.$it(w) ->" in locate, "khoá → camera tra bằng hàm tên khoá `:core` ($it)")
        }
        assertFalse(Regex(""""camera_[a-z_]+"""").containsMatchIn(perCamSet), "không chép trần tên khoá nào")
    }
}
