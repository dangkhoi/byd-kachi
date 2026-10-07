package com.byd.clusternav.launcher.camera

import android.content.Context
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraCamId
import com.byd.clusternav.cameraCirclePct
import com.byd.clusternav.cameraCornerOf
import com.byd.clusternav.cameraMirrorOf
import com.byd.clusternav.cameraOnCluster
import com.byd.clusternav.cameraPano
import com.byd.clusternav.cameraPlace
import com.byd.clusternav.cameraProjectionOf
import com.byd.clusternav.cameraRender
import com.byd.clusternav.cameraRotationDegOf
import com.byd.clusternav.cameraShapeOf
import com.byd.clusternav.cameraSize
import com.byd.clusternav.cameraSpan
import com.byd.clusternav.cameraStrip
import com.byd.clusternav.cameraView
import com.byd.clusternav.cameraZoom
import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView

/**
 * ═══ 2.93 · MỌI THỨ một phiên camera cần, ĐỌC TỪ PREFS cho MỘT camera — một cửa cho controller lẫn bộ chỉnh Cài đặt ═══
 *
 * Spec `docs/specs/kachi-293-cam.html` §4.2. Tách khỏi `CameraSignalController.openSession` (2.76–2.92) vì hai lý do:
 *  1. **bốn camera, không còn hai bên**: camera xi-nhan (trái/phải) và camera theo yêu cầu (sau·trái·phải·trước) đi
 *     CÙNG một lượt đọc theo [CameraWhich] — một camera một cấu hình, bất kể ai mở nó (owner *"từng camera"*);
 *  2. **DRY**: bộ chỉnh *Từng camera* trong Cài đặt cần đúng tỉ lệ khung để vẽ ô kéo-thả ⇒ đọc chung đường này, không
 *     dựng bản sao thứ hai của phép suy vùng cắt (hai bản sao là hai bản sẽ lệch — CLAUDE.md §4.1).
 *
 * Giá trị là **bất biến theo phiên** (cùng luật 2.76: đổi giữa hai khung không có tác dụng) — controller đọc MỘT lần lúc
 * mở phiên. Camera GƯƠNG: đúng lượt đọc 2.92 từng pref (góc nhìn · camera số · dải · bề rộng — `camera_*_left/right`),
 * nên xe không chạm Cài đặt *Từng camera* thì không đổi một pixel (bài `CameraSessionSpecTest` + máy ảo).
 *
 * ## Camera GIỮA (sau/trước) lấy NGUỒN của bên trái
 * Bốn dải nằm trong CÙNG một luồng ghép 4-in-1 ([ĐO] 27/09 Seal + 28/09 SL6). Nguồn đã dò được của camera gương trái (góc
 * nhìn + camera số — khối *Nếu camera không hiện*) chính là luồng ấy ⇒ dùng lại, chỉ thay DẢI bằng [CameraWhich.strip] và
 * lấy TRỌN dải (vệt hẹp `NARROW` neo theo mép một bên — vô nghĩa cho camera giữa). ⚠ Bên trái đặt *Nguyên khung*
 * (`camera_pano_left = NONE`, camera đơn) thì camera giữa vẫn cắt dải — [CHƯA BIẾT] đời xe nào như vậy; ghi ở spec OQ.
 */
internal class CameraSessionSpec(
    val which: CameraWhich,
    val view: CamView,
    /** Camera số mặc định của góc nhìn (`view.cameraId`) — in ra log để biết người lái đã đổi hay chưa. */
    val defId: Int,
    val camId: Int,
    /** Dải ép bằng nguồn ghép (`null` = góc nhìn đã mang vùng cắt dựng sẵn — hai góc Gương). */
    val panoStrip: Int?,
    val streamW: Int,
    val streamH: Int,
    /** Dải HIỆU LỰC (quyết vùng cắt + tâm quang). */
    val effStrip: Int,
    val render: String,
    /** Kiểu người lái chọn (riêng camera, rồi tới chung) — TRƯỚC khi quy theo đường vẽ. */
    val asked: String,
    /** Kiểu THẬT SỰ vẽ được trên [render] ([CameraViewMode.effective]). */
    val mode: String,
    val zoom: Int,
    val span: String,
    /** Hình khung hiệu lực (riêng camera, rồi tới chung) — chưa quy theo display. */
    val shape: String,
    val crops: CameraViewPlan.Crops,
    val rot: Int,
    val mirror: Boolean,
    val corner: String,
    val place: CameraCamConfig.Place?,
    val sizePct: Int,
    val onCluster: Boolean,
) {
    /** Bên dùng cho các pref THEO BÊN của nguồn (camera giữa ⇒ bên trái — KDoc lớp). */
    val sourceLeft: Boolean get() = which != CameraWhich.RIGHT

    companion object {
        /**
         * Đọc trọn bộ cho camera [which]. `null` chỉ khi không suy ra được góc nhìn nào (tên lạ VÀ không có mặc định —
         * lưới an toàn cuối, cùng chỗ `return` của 2.92).
         */
        fun read(ctx: Context, which: CameraWhich): CameraSessionSpec? {
            val left = which != CameraWhich.RIGHT
            // GÓC NHÌN = pref TỪNG BÊN (2026-09-28). Chưa chọn ⇒ `Prefs.cameraView` trả đúng tên mặc định cũ.
            val view = CameraSignalPolicy.viewOf(Prefs.cameraView(ctx, left = left))
                ?: CameraSignalPolicy.defaultView(if (left) CameraSignalPolicy.Turn.LEFT else CameraSignalPolicy.Turn.RIGHT)
                ?: return null
            val defId = view.cameraId
            val camId = Prefs.cameraCamId(ctx, left = left, defId)
            // NGUỒN giải MỘT lần rồi dùng chung cho cả ba chỗ (vùng cắt · cỡ ảnh · tâm quang) — 2026-09-28.
            val panoStrip = if (which.side) {
                CameraPanoCrop.panoStripFor(view, Prefs.cameraPano(ctx, left = left), left = left)
            } else {
                // Camera giữa: góc Gương đã mang vùng cắt ⇒ dải đi qua `strip`; góc khác ⇒ ép dải của chính camera.
                if (view.crop != null) null else which.strip
            }
            val effStrip = if (which.side) panoStrip ?: Prefs.cameraStrip(ctx, left = left) else which.strip
            val span = if (which.side) Prefs.cameraSpan(ctx) else CameraSignalPolicy.SPAN_STRIP
            val render = Prefs.cameraRender(ctx)   // CLOSE-14: mã lưu bền, đọc mỗi lượt dựng (theo hồ sơ xe)
            val asked = Prefs.cameraProjectionOf(ctx, which)   // R6: TV không có shader ⇒ Thẳng rộng hiện như Gương cầu
            // 2.94 R1: sau/trước ⇒ *Thẳng rộng* = *Nắn thẳng* (theo VAI camera, trước khi quy theo đường vẽ).
            val mode = CameraViewMode.effective(CameraViewMode.forCamera(asked, which), render)
            val shape = Prefs.cameraShapeOf(ctx, which)
            val crops = CameraViewPlan.crops(
                mode = mode,
                view = view,
                left = left,
                strip = effStrip,
                panoStrip = panoStrip,
                span = span,
                shape = shape,
                circlePct = Prefs.cameraCirclePct(ctx),
            )
            return CameraSessionSpec(
                which = which,
                view = view,
                defId = defId,
                camId = camId,
                panoStrip = panoStrip,
                streamW = CameraPanoCrop.streamW(view, panoStrip),
                streamH = CameraPanoCrop.streamH(view, panoStrip),
                effStrip = effStrip,
                render = render,
                asked = asked,
                mode = mode,
                zoom = Prefs.cameraZoom(ctx),   // MỘT lượt đọc cho uniform GL lẫn tỉ lệ TV/đường rơi (soát 06/10 [P3])
                span = span,
                shape = shape,
                crops = crops,
                // R7 · 2.71 · 2.93 — xoay/lật theo CAMERA (gương: đúng khoá `camera_rot_*`/`camera_mirror_*` theo bên).
                rot = Prefs.cameraRotationDegOf(ctx, which),
                mirror = Prefs.cameraMirrorOf(ctx, which),
                corner = Prefs.cameraCornerOf(ctx, which),
                place = Prefs.cameraPlace(ctx, which),
                sizePct = Prefs.cameraSize(ctx, which),
                onCluster = Prefs.cameraOnCluster(ctx),
            )
        }
    }
}
