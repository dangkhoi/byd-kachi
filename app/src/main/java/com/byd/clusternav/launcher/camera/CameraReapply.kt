package com.byd.clusternav.launcher.camera

import android.content.Context
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraDewarpAmount
import com.byd.clusternav.cameraProjection
import com.byd.clusternav.cameraZoom
import com.byd.clusternav.setCameraDewarpAmount
import com.byd.clusternav.setCameraProjection
import com.byd.clusternav.setCameraZoom

/**
 * ═══ 2.93 wave 2B · ÁP LẠI camera ĐANG HIỆN khi cấu hình của nó vừa đổi — MỘT cửa cho Cài đặt · `prefs_set` · đổi hồ sơ ═══
 *
 * Spec `docs/specs/kachi-293-cam.html` §10 Pass 1 D2 · D6. Camera theo yêu cầu KHÔNG có hẹn giờ tắt (owner *"không nên
 * timeout"*) ⇒ khung đang treo có thể ở lại cả chuyến; `openSession` chỉ đọc pref lúc MỞ phiên, nên đổi cấu hình mà không
 * dựng lại là *"chỉnh rồi mà không thấy gì đổi"*. Mọi lối dùng ĐÚNG cửa áp lại của controller
 * ([CameraSignalController.reapplyIfShowing] — dỡ + dựng theo đường *đổi camera*, không chạm HOLD, chuỗi dỡ có bài canh):
 *  • [ifShowing] — Cài đặt *Từng camera* (`ClusterNavBridgeCameraPerCam.reapplyIf`) và `prefs_set` 28 khoá camera
 *    (`TestBridgePrefsSet`, D2): chỉ khi ĐÚNG camera của khoá đang hiện (xi-nhan hay theo yêu cầu) — camera khác đang hiện
 *    ⇒ để yên, không chớp vô cớ;
 *  • [setProjection] · [setZoom] — 2.93 wave 2C · PREFS-SET-CAM-GLOBAL-REAPPLY (spec `kachi-293-wave2c.html` R4): hai khoá
 *    CHUNG (`camera_projection` · `camera_zoom`) — chip/thanh kéo Cài đặt (`ClusterNavBridge.setCameraProjection/Zoom`) VÀ
 *    `prefs_set` đi CÙNG một hàm ⇒ cùng luật nắn-đủ ([CameraViewMode.amountOnPick]), cùng phép "chỉ dựng lại khi thứ đang áp
 *    đổi", cùng cửa [anyShowing] (khoá chung áp cho mọi camera theo chung ⇒ khung nào đang hiện cũng dựng lại);
 *  • [ifDemandShowing] — đổi hồ sơ (`ClusterNavBridge.reapplyAll`, D6): chỉ khi khung đang hiện là camera THEO YÊU CẦU.
 *
 * Controller chưa dựng ⇒ chắc chắn không khung nào treo ⇒ không làm gì, và lượt chỉnh này KHÔNG dựng nó (một lượt chỉnh
 * không được tạo ra thứ nó chỉnh — cùng cổng `reapplyCamera` của 2.92, nay là [anyShowing]).
 */
internal object CameraReapply {

    private const val TAG = "KachiCamReapply"

    /**
     * Khoá của camera [which] vừa đổi ⇒ dựng lại khung nếu ĐÚNG camera ấy đang hiện. Lỗi ⇒ ghi log, không ném vào Cài đặt/cầu —
     * soát senior wave 2B/2C [P2]: bản trước lượt gộp D2 đi qua `reapplyCamera` có `runCatching`; gộp làm rơi lớp chắn ⇒ một lượt
     * dựng lại hỏng (Cài đặt gọi trên luồng chính ⇒ `reapplyIfShowing` chạy ĐỒNG BỘ) ném thẳng vào trình nghe chạm của bộ chỉnh
     * *Từng camera*. Cùng lớp chắn của [anyShowing].
     */
    fun ifShowing(ctx: Context, which: CameraWhich) {
        runCatching {
            val c = AppContainer.get(ctx)
            if (c.cameraSignalCreated && c.cameraSignal.showingCamera() == which) c.cameraSignal.reapplyIfShowing()
        }.onFailure { Log.w(TAG, "áp lại camera $which lỗi: ${it.message}") }
    }

    /**
     * Kiểu hình CHUNG [v] (mã [CameraViewMode.MODES] — chỗ gọi đã kiểm): ghi, kèm độ nắn theo luật `:core`, rồi dựng lại khung
     * đang hiện CHỈ khi thứ đang áp thật sự đổi (chạm lại đúng chip đang sáng = không chớp — soát 2.92 [P3]).
     * @return `true` khi kiểu ĐÃ QUY hoặc độ nắn đổi.
     */
    fun setProjection(ctx: Context, v: String): Boolean {
        var changed = Prefs.cameraProjection(ctx) != v   // so với kiểu ĐÃ QUY (đời cũ amount 0 ⇒ Gương cầu)
        Prefs.setCameraProjection(ctx, v)
        CameraViewMode.amountOnPick(v, Prefs.cameraDewarpAmount(ctx))?.let {
            Prefs.setCameraDewarpAmount(ctx, it)
            changed = true
        }
        if (changed) anyShowing(ctx)
        return changed
    }

    /** Thu phóng CHUNG [v] `%` (chỗ gọi đã kiểm miền): ghi rồi dựng lại khung đang hiện chỉ khi số đổi. `true` = đã đổi. */
    fun setZoom(ctx: Context, v: Int): Boolean {
        val changed = Prefs.cameraZoom(ctx) != v
        Prefs.setCameraZoom(ctx, v)
        if (changed) anyShowing(ctx)
        return changed
    }

    /** Khoá CHUNG vừa đổi ⇒ dựng lại khung ĐANG hiện (xi-nhan hay theo yêu cầu). Lỗi ⇒ ghi log, không ném vào Cài đặt/cầu. */
    fun anyShowing(ctx: Context) {
        runCatching {
            val c = AppContainer.get(ctx)
            if (c.cameraSignalCreated) c.cameraSignal.reapplyIfShowing()
        }.onFailure { Log.w(TAG, "áp lại camera lỗi: ${it.message}") }
    }

    /**
     * Hồ sơ vừa đổi ⇒ camera THEO YÊU CẦU đang hiện nhận cấu hình của hồ sơ mới ngay. Camera xi-nhan đang giữ ⇒ để yên
     * (không dỡ camera điểm mù giữa lúc rẽ); 2.93 wave 2C · CAM-D6-SAME-CAMERA-EDGE: xi-nhan giữ ĐÚNG camera theo yêu cầu ⇒
     * hẹn dựng lại lúc xi-nhan nhả — luật ở `:core` [CameraDemand.profileReapply], thi hành ở
     * [CameraSignalController.reapplyIfDemandShowing].
     */
    fun ifDemandShowing(ctx: Context) {
        val c = AppContainer.get(ctx)
        if (c.cameraSignalCreated) c.cameraSignal.reapplyIfDemandShowing()
    }
}
