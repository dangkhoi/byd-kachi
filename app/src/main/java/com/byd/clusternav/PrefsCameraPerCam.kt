package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.launcher.camera.CameraCamConfig
import com.byd.clusternav.launcher.camera.CameraSignalPolicy
import com.byd.clusternav.launcher.camera.CameraWhich

/**
 * ═══ 2.93 · CAMERA-PER-CAM-CONFIG — đọc/ghi cấu hình RIÊNG từng camera (spec `docs/specs/kachi-293-cam.html` R2) ═══════
 *
 * Hàm mở rộng của [Prefs], **cùng tệp `clusternav_prefs`** qua `autoPrefs` (không mở cửa thứ hai vào cùng chỗ lưu — KDoc
 * [PrefsAutomation]). Tên khoá, miền hợp lệ và phép *"theo chung"* ở `:core` [CameraCamConfig] — không một literal khoá
 * hay con số nào viết ở tệp này.
 *
 * ## Hai camera GƯƠNG đi qua đúng hàm 2.35/2.71/2.76 cũ
 * Góc ([Prefs.cameraPos]) · xoay ([Prefs.cameraRotation] — kèm lượt di trú khoá đơn 2.67 và mặc định hồ sơ xe) · lật
 * ([Prefs.cameraMirror]) của trái/phải KHÔNG được đọc lại bằng `getString` thô ở đây: làm thế là bỏ qua di trú + mặc định
 * theo hồ sơ xe, tức Seal sẽ thấy ↺90 thay vì 0 (bài `CameraPerCamPrefsWiringContractTest`).
 */

/** Góc mặc định (`"TL"`/`"TR"`) của camera [w]. Lạ trên đĩa ⇒ [CameraCamConfig.defaultCorner]. */
fun Prefs.cameraCornerOf(ctx: Context, w: CameraWhich): String {
    if (w.side) return cameraPos(ctx, left = w == CameraWhich.LEFT)
    val fallback = CameraCamConfig.defaultCorner(w)
    val raw = autoPrefs(ctx).getString(CameraCamConfig.cornerKey(w), fallback) ?: fallback
    return if (CameraSignalPolicy.isCorner(raw)) raw else fallback
}

/**
 * Xem [cameraCornerOf]. Chỉ ghi ĐÚNG khoá góc (cầu kiểm thử `prefs_set` ghi một khoá là một khoá); việc *"chọn góc thì bỏ
 * vị trí kéo-thả"* của chip Cài đặt nằm ở cầu Cài đặt (`ClusterNavBridge.setCameraCornerOf`), không giấu ở đây.
 */
fun Prefs.setCameraCornerOf(ctx: Context, w: CameraWhich, v: String) =
    autoPrefs(ctx).edit().putString(CameraCamConfig.cornerKey(w), v).apply()

/** Vị trí kéo-thả của camera [w]; `null` = theo góc mặc định ([CameraCamConfig.parsePlace]). */
fun Prefs.cameraPlace(ctx: Context, w: CameraWhich): CameraCamConfig.Place? =
    CameraCamConfig.parsePlace(autoPrefs(ctx).getString(CameraCamConfig.placeKey(w), null))

/** Xem [cameraPlace]. `null` = xoá khoá (về góc mặc định — nút *Đặt lại vị trí*). */
fun Prefs.setCameraPlace(ctx: Context, w: CameraWhich, p: CameraCamConfig.Place?) {
    val e = autoPrefs(ctx).edit()
    if (p == null) e.remove(CameraCamConfig.placeKey(w)) else e.putString(CameraCamConfig.placeKey(w), p.encode())
    e.apply()
}

/** Cỡ `%` của camera [w]; ngoài miền ⇒ [CameraCamConfig.SIZE_DEFAULT]. */
fun Prefs.cameraSize(ctx: Context, w: CameraWhich): Int {
    val raw = autoPrefs(ctx).getInt(CameraCamConfig.sizeKey(w), CameraCamConfig.SIZE_DEFAULT)
    return if (CameraCamConfig.isSizePct(raw)) raw else CameraCamConfig.SIZE_DEFAULT
}

/** Xem [cameraSize]. */
fun Prefs.setCameraSize(ctx: Context, w: CameraWhich, v: Int) =
    autoPrefs(ctx).edit().putInt(CameraCamConfig.sizeKey(w), v).apply()

/** Mã chip hình khung riêng ([CameraCamConfig.FOLLOW] hoặc mã hình) — đúng thứ hàng chip đang sáng. */
fun Prefs.cameraShapeChoice(ctx: Context, w: CameraWhich): String =
    CameraCamConfig.shapeChoice(autoPrefs(ctx).getString(CameraCamConfig.shapeKey(w), null))

/** Xem [cameraShapeChoice]. */
fun Prefs.setCameraShapeChoice(ctx: Context, w: CameraWhich, v: String) =
    autoPrefs(ctx).edit().putString(CameraCamConfig.shapeKey(w), v).apply()

/** Hình khung HIỆU LỰC của camera [w]: riêng thắng, [CameraCamConfig.FOLLOW] ⇒ [Prefs.cameraShape] (chung). */
fun Prefs.cameraShapeOf(ctx: Context, w: CameraWhich): String =
    CameraCamConfig.effectiveShape(autoPrefs(ctx).getString(CameraCamConfig.shapeKey(w), null), cameraShape(ctx))

/** Mã chip kiểu hình riêng ([CameraCamConfig.FOLLOW] hoặc mã kiểu). */
fun Prefs.cameraProjectionChoice(ctx: Context, w: CameraWhich): String =
    CameraCamConfig.projectionChoice(autoPrefs(ctx).getString(CameraCamConfig.projectionKey(w), null))

/** Xem [cameraProjectionChoice]. */
fun Prefs.setCameraProjectionChoice(ctx: Context, w: CameraWhich, v: String) =
    autoPrefs(ctx).edit().putString(CameraCamConfig.projectionKey(w), v).apply()

/**
 * Kiểu hình HIỆU LỰC của camera [w]: riêng thắng; [CameraCamConfig.FOLLOW] ⇒ [Prefs.cameraProjection] (chung — kèm phép
 * thừa kế *Nắn hình* TẮT đời 2.91 ⇒ *Gương cầu*).
 */
fun Prefs.cameraProjectionOf(ctx: Context, w: CameraWhich): String =
    CameraCamConfig.effectiveProjection(autoPrefs(ctx).getString(CameraCamConfig.projectionKey(w), null), cameraProjection(ctx))

/** Mã xoay của camera [w]. Gương ⇒ [Prefs.cameraRotation] (di trú + mặc định hồ sơ xe); giữa ⇒ khoá mới, mặc định không xoay. */
fun Prefs.cameraRotationOf(ctx: Context, w: CameraWhich): String {
    if (w.side) return cameraRotation(ctx, left = w == CameraWhich.LEFT)
    val fallback = CameraCamConfig.CENTRE_ROTATION_DEFAULT
    val raw = autoPrefs(ctx).getString(CameraCamConfig.rotationKey(w), fallback) ?: fallback
    return if (CameraSignalPolicy.isRotation(raw)) raw else fallback
}

/** Xem [cameraRotationOf]. Gương ⇒ đúng hàm ghi cũ ([Prefs.setCameraRotation]). */
fun Prefs.setCameraRotationOf(ctx: Context, w: CameraWhich, v: String) {
    if (w.side) setCameraRotation(ctx, left = w == CameraWhich.LEFT, v = v)
    else autoPrefs(ctx).edit().putString(CameraCamConfig.rotationKey(w), v).apply()
}

/** Góc xoay (độ) của camera [w] — mã đã kiểm ở [cameraRotationOf], phép đổi độ ở `:core`. */
fun Prefs.cameraRotationDegOf(ctx: Context, w: CameraWhich): Int =
    CameraSignalPolicy.rotationDegrees(cameraRotationOf(ctx, w), left = w == CameraWhich.LEFT)

/** Lật gương của camera [w]. Gương ⇒ [Prefs.cameraMirror]; giữa ⇒ khoá mới, mặc định TẮT. */
fun Prefs.cameraMirrorOf(ctx: Context, w: CameraWhich): Boolean =
    if (w.side) cameraMirror(ctx, left = w == CameraWhich.LEFT)
    else autoPrefs(ctx).getBoolean(CameraCamConfig.mirrorKey(w), false)

/** Xem [cameraMirrorOf]. */
fun Prefs.setCameraMirrorOf(ctx: Context, w: CameraWhich, v: Boolean) {
    if (w.side) setCameraMirror(ctx, left = w == CameraWhich.LEFT, v = v)
    else autoPrefs(ctx).edit().putBoolean(CameraCamConfig.mirrorKey(w), v).apply()
}
