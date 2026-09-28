package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.testbridge.TestBridgeWritableKeys
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VẠCH CHUẨN KHOẢNG CÁCH — bài canh DÂY NỐI (2026-09-28) ═══════════════════════════════════════════════════════
 *
 * Quyết định *vạch ở đâu* là code thuần và đã có [CameraGuideTest] ở `:core`. Bài này canh thứ mà code thuần không
 * chạm tới: **vạch có thật sự tới được màn hình không**. Đúng cái bẫy CLAUDE.md §8 — `CastShell.evictVd` từng viết
 * xong, có KDoc, compile sạch và **chưa từng được gọi lần nào**.
 *
 * Bốn mắt xích phải liền: pref → `CameraSignalController` → `CameraOverlayView.show(guide=)` → `addView`. Đứt một
 * mắt là người lái chọn nấc, Cài đặt báo xong, và trên hình không có gì.
 */
class CameraGuideWiringContractTest {

    private fun app(rel: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$rel")

    @Test fun `bo dieu khien DOC pref roi truyen xuong overlay`() {
        val c = app("launcher/camera/CameraSignalController.kt")
        assertTrue("CameraGuide.positionFor(Prefs.cameraGuide(appCtx, left = isLeft))" in c,
            "bộ điều khiển phải đọc pref theo ĐÚNG BÊN đang hiện — đọc nhầm bên là canh trái ra vạch phải")
        assertTrue("guide = CameraGuide.positionFor" in c, "giá trị đọc được phải đi vào tham số `guide` của show()")
    }

    @Test fun `overlay VE that, nam tren video va duoi nhan`() {
        val v = app("launcher/camera/CameraOverlayView.kt")
        assertTrue("CameraGuideLineView(ctx).apply { position = pos }" in v, "không có addView ⇒ vạch không bao giờ hiện")
        val iVideo = v.indexOf("addView(child, videoLp(st, g))")
        val iGuide = v.indexOf("CameraGuideLineView(ctx)")
        val iLabel = v.indexOf("labelFor(ctx, side)")
        assertTrue(iVideo in 1 until iGuide, "vạch phải thêm SAU video — thêm trước là video vẽ đè lên vạch")
        assertTrue(iGuide < iLabel, "vạch phải thêm TRƯỚC nhãn — nhãn là thứ duy nhất cho biết đang hiện đúng bên")
    }

    /** Lớp vẽ nằm TRÊN video nên nếu nó ăn chạm thì mọi thao tác lên overlay chết theo. */
    @Test fun `lop ve KHONG an cham`() {
        val g = app("launcher/camera/CameraGuideLineView.kt")
        assertTrue("isClickable = false" in g && "isFocusable = false" in g, "phải tự tắt nhận sự kiện")
        assertFalse("setOnTouchListener" in g || "onTouchEvent" in g, "cài listener là chặn chạm xuyên xuống lớp dưới")
        assertTrue("halo" in g, "vạch trắng trơn biến mất trên nền sáng — phải có viền tương phản")
    }

    /** Đổi nấc mà không thấy hình thì không canh được: setter phải tự bật xem thử đúng bên vừa đổi. */
    @Test fun `doi nac thi XEM THU ngay ben do`() {
        val b = SourceRoots.body(app("launcher/ClusterNavBridgeAutomation.kt"), "fun ClusterNavBridge.setCameraGuide(")
        assertTrue("Prefs.setCameraGuide(app, left = left, v = v)" in b, "phải ghi pref")
        assertTrue("cameraSignal.previewSide(left)" in b, "phải xem thử ĐÚNG bên vừa đổi")
        assertTrue("runCatching" in b, "xem thử hỏng không được kéo theo cú ghi pref đã thành công")
    }

    /** Cầu kiểm thử: hai khoá phải ghi/đọc được bằng adb, và giá trị lạ bị TỪ CHỐI chứ không ghi bừa. */
    @Test fun `cau kiem thu ghi va doc duoc, chan gia tri la`() {
        val t = app("launcher/testbridge/TestBridgePrefsSet.kt")
        listOf("camera_guide_left", "camera_guide_right").forEach {
            assertTrue("\"$it\"" in t, "khoá $it chưa có nhánh ghi ⇒ `prefs_set` báo ok rồi không làm gì")
            assertTrue(it in TestBridgeWritableKeys.ALL, "khoá $it chưa có trong danh sách trắng")
        }
        assertTrue("CameraGuide.isValue(it)" in t, "giá trị lạ phải bị chặn ngay cửa, không ghi vào pref")
    }

    @Test fun `chu co o ca hai ngon ngu va duoc dung`() {
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        val settings = app("launcher/SettingsSectionsCamera.kt")
        listOf(
            "kachi_camera_guide_sub", "kachi_camera_guide_row_left", "kachi_camera_guide_row_right",
            "kachi_camera_guide_off",
        ).forEach { k ->
            assertTrue("\"$k\"" in vi, "thiếu VI $k"); assertTrue("\"$k\"" in en, "thiếu EN $k")
            assertTrue("R.string.$k" in settings, "chữ $k không ai dùng ⇒ mồ côi")
        }
    }
}
