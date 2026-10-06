package com.byd.clusternav.launcher

import com.byd.clusternav.AppContainer
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraCornerOf
import com.byd.clusternav.cameraMirrorOf
import com.byd.clusternav.cameraPlace
import com.byd.clusternav.cameraProjectionChoice
import com.byd.clusternav.cameraRotationOf
import com.byd.clusternav.cameraShapeChoice
import com.byd.clusternav.cameraSize
import com.byd.clusternav.launcher.camera.CameraCamConfig
import com.byd.clusternav.launcher.camera.CameraClusterBand
import com.byd.clusternav.launcher.camera.CameraDefaults
import com.byd.clusternav.launcher.camera.CameraDemand
import com.byd.clusternav.launcher.camera.CameraDemandDispatch
import com.byd.clusternav.launcher.camera.CameraPlacement
import com.byd.clusternav.launcher.camera.CameraReapply
import com.byd.clusternav.launcher.camera.CameraSessionSpec
import com.byd.clusternav.launcher.camera.CameraSignalPolicy
import com.byd.clusternav.launcher.camera.CameraWhich
import com.byd.clusternav.setCameraCornerOf
import com.byd.clusternav.setCameraMirrorOf
import com.byd.clusternav.setCameraPlace
import com.byd.clusternav.setCameraProjectionChoice
import com.byd.clusternav.setCameraRotationOf
import com.byd.clusternav.setCameraShapeChoice
import com.byd.clusternav.setCameraSize

/**
 * ═══ 2.93 · CẦU Cài đặt ↔ bộ chỉnh *Từng camera* + nút *Xem thử* (spec `docs/specs/kachi-293-cam.html` R1/R2) ═══════
 *
 * Tách tệp vì [ClusterNavBridge] (+ `ClusterNavBridgeAutomation.kt`) đã dày; cùng khuôn mọi khối cầu: màn Cài đặt KHÔNG chạm
 * `Prefs.` trực tiếp (N2), mỗi hàm ghi xong thì **áp ngay** nếu đúng camera ấy đang hiện ([reapplyIf]) — không hiện ⇒ lượt
 * mở sau tự đọc. Không `AutomationService.sync`: đây là *cách hiện*, không phải công tắc bật động cơ nền.
 */

/** Góc mặc định của camera [w] (`"TL"`/`"TR"`). */
fun ClusterNavBridge.cameraCorner(w: CameraWhich): String = Prefs.cameraCornerOf(app, w)

/** Chọn góc ⇒ camera VỀ góc ấy: ghi góc + bỏ vị trí kéo-thả (hai khoá, một cú chạm — đúng thứ người lái vừa nhìn thấy). */
fun ClusterNavBridge.setCameraCorner(w: CameraWhich, v: String) {
    Prefs.setCameraCornerOf(app, w, v)
    Prefs.setCameraPlace(app, w, null)
    reapplyIf(w)
}

/** Vị trí kéo-thả (`null` = theo góc mặc định). */
fun ClusterNavBridge.cameraPlace(w: CameraWhich): CameraCamConfig.Place? = Prefs.cameraPlace(app, w)

/** Xem [cameraPlace]; `null` = *Đặt lại vị trí* (về góc mặc định). */
fun ClusterNavBridge.setCameraPlace(w: CameraWhich, p: CameraCamConfig.Place?) {
    Prefs.setCameraPlace(app, w, p)
    reapplyIf(w)
}

/** Cỡ `%` của camera [w]. */
fun ClusterNavBridge.cameraSize(w: CameraWhich): Int = Prefs.cameraSize(app, w)

/** Xem [cameraSize]. Chỉ dựng lại khi số đổi (thanh kéo thả đúng nấc cũ ⇒ không chớp — soát 2.92 [P3]). */
fun ClusterNavBridge.setCameraSize(w: CameraWhich, v: Int) {
    if (Prefs.cameraSize(app, w) == v) return
    Prefs.setCameraSize(app, w, v)
    reapplyIf(w)
}

/** Mã chip hình khung riêng ([CameraCamConfig.FOLLOW] = theo chung). */
fun ClusterNavBridge.cameraShapeChoice(w: CameraWhich): String = Prefs.cameraShapeChoice(app, w)

/** Xem [cameraShapeChoice]. */
fun ClusterNavBridge.setCameraShapeChoice(w: CameraWhich, v: String) {
    if (Prefs.cameraShapeChoice(app, w) == v) return
    Prefs.setCameraShapeChoice(app, w, v)
    reapplyIf(w)
}

/** Mã chip kiểu hình riêng ([CameraCamConfig.FOLLOW] = theo chung). */
fun ClusterNavBridge.cameraProjectionChoice(w: CameraWhich): String = Prefs.cameraProjectionChoice(app, w)

/** Xem [cameraProjectionChoice]. */
fun ClusterNavBridge.setCameraProjectionChoice(w: CameraWhich, v: String) {
    if (Prefs.cameraProjectionChoice(app, w) == v) return
    Prefs.setCameraProjectionChoice(app, w, v)
    reapplyIf(w)
}

/** Mã xoay của camera [w] (gương: khoá 2.71 cũ — `Prefs.cameraRotation`). */
fun ClusterNavBridge.cameraRotationOf(w: CameraWhich): String = Prefs.cameraRotationOf(app, w)

/** Xem [cameraRotationOf]. */
fun ClusterNavBridge.setCameraRotationOf(w: CameraWhich, v: String) {
    if (Prefs.cameraRotationOf(app, w) == v) return
    Prefs.setCameraRotationOf(app, w, v)
    reapplyIf(w)
}

/** Lật gương của camera [w] (gương: khoá 2.76 cũ — `Prefs.cameraMirror`). */
fun ClusterNavBridge.cameraMirrorOf(w: CameraWhich): Boolean = Prefs.cameraMirrorOf(app, w)

/** Xem [cameraMirrorOf]. */
fun ClusterNavBridge.setCameraMirrorOf(w: CameraWhich, v: Boolean) {
    Prefs.setCameraMirrorOf(app, w, v)
    reapplyIf(w)
}

/**
 * Mô hình ô kéo-thả của camera [w]: display ĐÍCH theo `camera_on_cluster` (cụm: cỡ chiếu thật [ClusterNavBridge.clusterSize]
 * + dải theo hồ sơ xe; màn chính: display của chính Cài đặt), tỉ lệ khung từ CÙNG lượt đọc mà phiên camera dùng
 * ([CameraSessionSpec]). `null` khi không đọc được (góc nhìn lạ, lưới an toàn).
 */
fun ClusterNavBridge.cameraPlacementModel(w: CameraWhich): CameraPlacement.Model? {
    val s = CameraSessionSpec.read(app, w) ?: return null
    val (dw, dh) = if (s.onCluster) clusterSize() else app.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    val region = if (s.onCluster) CameraClusterBand.band(dw, dh, CameraDefaults.band(app)) else CameraPlacement.mainRegion(dw, dh)
    val shape = CameraClusterBand.effectiveShape(s.crops.frameShape, s.onCluster)
    return CameraPlacement.Model(
        displayW = dw, displayH = dh, onCluster = s.onCluster, region = region,
        round = shape == CameraSignalPolicy.SHAPE_ROUND, corner = s.corner,
        stream = CameraPlacement.Stream(s.streamW, s.streamH, s.crops.frame, s.rot),
    )
}

/**
 * Camera theo yêu cầu đang bật (`null` = không) — cho nút *Xem thử*. Controller chưa dựng ⇒ `null` mà KHÔNG dựng nó (một
 * lượt mở Cài đặt không được tạo ra thứ nó chỉ đọc).
 */
fun ClusterNavBridge.cameraDemanded(): CameraWhich? {
    val c = AppContainer.get(app)
    return if (c.cameraSignalCreated) c.cameraSignal.demanded() else null
}

/** Camera ĐANG HIỆN (xi-nhan hay theo yêu cầu), `null` = không hiện. Controller chưa dựng ⇒ `null` mà KHÔNG dựng nó. */
fun ClusterNavBridge.cameraShowing(): CameraWhich? {
    val c = AppContainer.get(app)
    return if (c.cameraSignalCreated) c.cameraSignal.showingCamera() else null
}

/** Nút *Xem thử* = CÙNG đường của nút trên thanh nút / phím vật lý (bật/tắt camera [w]). */
fun ClusterNavBridge.toggleCameraDemand(w: CameraWhich): Boolean = CameraDemandDispatch.fire(app, CameraDemand.Op.Toggle(w))

/** Nghe đổi trạng thái theo yêu cầu (phím/giọng nói đổi khi Cài đặt đang mở). Controller chưa dựng ⇒ không nghe (trả hàm rỗng). */
fun ClusterNavBridge.onCameraDemandChanged(l: (CameraWhich?) -> Unit): () -> Unit {
    val c = AppContainer.get(app)
    return if (c.cameraSignalCreated) c.cameraSignal.onDemandChanged(l) else ({})
}

/**
 * Áp ngay khi ĐÚNG camera [w] đang hiện (xi-nhan hay theo yêu cầu); camera khác đang hiện ⇒ để yên (không chớp vô cớ).
 * 2.93 wave 2B · D2 — CÙNG một cửa với `prefs_set` ([CameraReapply.ifShowing]), không bản thứ hai.
 */
private fun ClusterNavBridge.reapplyIf(w: CameraWhich) = CameraReapply.ifShowing(app, w)
