package com.byd.clusternav.launcher.testbridge

import android.content.Context
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraCornerOf
import com.byd.clusternav.cameraMirrorOf
import com.byd.clusternav.cameraPlace
import com.byd.clusternav.cameraProjectionChoice
import com.byd.clusternav.cameraRotationOf
import com.byd.clusternav.cameraShapeChoice
import com.byd.clusternav.cameraSize
import com.byd.clusternav.launcher.camera.CameraCamConfig
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
 * ═══ 2.93 · `prefs_set` cho 22 khoá MỚI của bộ chỉnh *Từng camera* (spec `docs/specs/kachi-293-cam.html` R2) ═══════════
 *
 * Tách khỏi [TestBridgePrefsSet] (trần 500 dòng — CLAUDE.md §4.1) và viết THEO LOẠI khoá chứ không 22 nhánh chép tay:
 * tên khoá, camera và miền hợp lệ đều tra ở `:core` [CameraCamConfig] — một chỗ khai. Sáu khoá cũ của hai camera gương
 * (`camera_pos_*` · `camera_rot_*` · `camera_mirror_*`) vẫn ở nhánh riêng của tệp kia, không đi qua đây.
 *
 * Ngoài miền ⇒ `null` ⇒ `bad_prefs_value:` (không kẹp im lặng — cùng luật mọi khoá camera). Vị trí nhận `x,y` (phần
 * nghìn) hoặc `AUTO` (= xoá khoá, về góc mặc định).
 */
internal object TestBridgePerCam {

    /** Khoá [key] có phải một trong 22 khoá mới không. */
    fun owns(key: String): Boolean = key in CameraCamConfig.NEW_KEYS

    /** Ghi; trả giá trị đã áp (`null` = giá trị không hợp lệ ⇒ không ghi gì). */
    fun write(app: Context, key: String, raw: String): String? {
        val (w, field) = locate(key) ?: return null
        val v = raw.trim()
        val up = v.uppercase()
        return when (field) {
            Field.CORNER -> up.takeIf { CameraSignalPolicy.isCorner(it) }?.also { Prefs.setCameraCornerOf(app, w, it) }
            Field.PLACE -> if (up == CameraCamConfig.FOLLOW) {
                Prefs.setCameraPlace(app, w, null); CameraCamConfig.FOLLOW
            } else {
                CameraCamConfig.parsePlace(v)?.also { Prefs.setCameraPlace(app, w, it) }?.encode()
            }
            Field.SIZE -> v.toIntOrNull()?.takeIf { CameraCamConfig.isSizePct(it) }
                ?.also { Prefs.setCameraSize(app, w, it) }?.toString()
            Field.SHAPE -> up.takeIf { CameraCamConfig.isShapeChoice(it) }?.also { Prefs.setCameraShapeChoice(app, w, it) }
            Field.PROJECTION -> up.takeIf { CameraCamConfig.isProjectionChoice(it) }
                ?.also { Prefs.setCameraProjectionChoice(app, w, it) }
            Field.ROTATION -> up.takeIf { CameraSignalPolicy.isRotation(it) }?.also { Prefs.setCameraRotationOf(app, w, it) }
            Field.MIRROR -> TestBridgePrefsSet.bool(v)?.also { Prefs.setCameraMirrorOf(app, w, it) }?.toString()
        }
    }

    /** Đọc LẠI từ nơi lưu bền — giá trị THẬT sau lượt ghi (cùng hợp đồng `read_back`). */
    fun read(app: Context, key: String): String {
        val (w, field) = locate(key) ?: return ""
        return when (field) {
            Field.CORNER -> Prefs.cameraCornerOf(app, w)
            Field.PLACE -> Prefs.cameraPlace(app, w)?.encode() ?: CameraCamConfig.FOLLOW
            Field.SIZE -> Prefs.cameraSize(app, w).toString()
            Field.SHAPE -> Prefs.cameraShapeChoice(app, w)
            Field.PROJECTION -> Prefs.cameraProjectionChoice(app, w)
            Field.ROTATION -> Prefs.cameraRotationOf(app, w)
            Field.MIRROR -> Prefs.cameraMirrorOf(app, w).toString()
        }
    }

    private enum class Field { CORNER, PLACE, SIZE, SHAPE, PROJECTION, ROTATION, MIRROR }

    /** Khoá → (camera, loại) bằng chính các hàm tên khoá của `:core`. Không phải khoá mới ⇒ `null`. */
    private fun locate(key: String): Pair<CameraWhich, Field>? {
        if (!owns(key)) return null
        CameraWhich.ALL.forEach { w ->
            when (key) {
                CameraCamConfig.cornerKey(w) -> return w to Field.CORNER
                CameraCamConfig.placeKey(w) -> return w to Field.PLACE
                CameraCamConfig.sizeKey(w) -> return w to Field.SIZE
                CameraCamConfig.shapeKey(w) -> return w to Field.SHAPE
                CameraCamConfig.projectionKey(w) -> return w to Field.PROJECTION
                CameraCamConfig.rotationKey(w) -> return w to Field.ROTATION
                CameraCamConfig.mirrorKey(w) -> return w to Field.MIRROR
            }
        }
        return null
    }
}
