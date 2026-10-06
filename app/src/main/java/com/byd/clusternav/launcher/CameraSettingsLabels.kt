package com.byd.clusternav.launcher

import android.content.Context
import com.byd.clusternav.R
import com.byd.clusternav.launcher.camera.CameraCamConfig
import com.byd.clusternav.launcher.camera.CameraSignalPolicy
import com.byd.clusternav.launcher.camera.CameraViewMode
import com.byd.clusternav.launcher.camera.CameraWhich

/**
 * ═══ Nhãn chip camera dùng chung cho hai khối Cài đặt (chung · *Từng camera*, 2.93) ════════════════════════════════════
 *
 * MỘT bảng mã `:core` → chuỗi tài nguyên, để hàng *Hình khung chung* và hàng *Hình khung riêng* không bao giờ gọi một mã bằng
 * hai cái tên (DRY — CLAUDE.md §4.1). Mã chưa có chữ ⇒ hiện chính mã (lộ ra để sửa, không im lặng bỏ chip).
 */
internal object CameraSettingsLabels {

    /** Nhãn chip hình khung theo mã `:core`. */
    fun shape(ctx: Context, code: String): String = when (code) {
        CameraSignalPolicy.SHAPE_RECT -> ctx.getString(R.string.kachi_camera_shape_rect)
        CameraSignalPolicy.SHAPE_ROUND -> ctx.getString(R.string.kachi_camera_shape_round)
        CameraSignalPolicy.SHAPE_CLUSTER -> ctx.getString(R.string.kachi_camera_shape_cluster)
        CameraCamConfig.FOLLOW -> ctx.getString(R.string.kachi_camera_follow)
        else -> code
    }

    /** Nhãn chip kiểu hình theo mã `:core`. */
    fun projection(ctx: Context, code: String): String = when (code) {
        CameraViewMode.STRAIGHT -> ctx.getString(R.string.kachi_camera_projection_straight)
        CameraViewMode.WIDE -> ctx.getString(R.string.kachi_camera_projection_wide)
        CameraViewMode.FISHEYE -> ctx.getString(R.string.kachi_camera_projection_fisheye)
        CameraCamConfig.FOLLOW -> ctx.getString(R.string.kachi_camera_follow)
        else -> code
    }

    /** Bốn chip xoay — mã lưu bền của `:core` [CameraSignalPolicy.ROTATIONS], đúng thứ tự. */
    fun rotations(ctx: Context): List<Pair<String, String>> = listOf(
        CameraSignalPolicy.ROTATE_NONE to ctx.getString(R.string.kachi_camera_rot_none),
        CameraSignalPolicy.ROTATE_LEFT to ctx.getString(R.string.kachi_camera_rot_left),
        CameraSignalPolicy.ROTATE_RIGHT to ctx.getString(R.string.kachi_camera_rot_right),
        CameraSignalPolicy.ROTATE_180 to ctx.getString(R.string.kachi_camera_rot_180),
    )

    /** Nhãn NGẮN của một camera (chip chọn camera: *Sau · Trái · Phải · Trước*). */
    fun cameraShort(ctx: Context, w: CameraWhich): String = ctx.getString(
        when (w) {
            CameraWhich.REAR -> R.string.kachi_cam_chip_rear
            CameraWhich.LEFT -> R.string.kachi_cam_chip_left
            CameraWhich.RIGHT -> R.string.kachi_cam_chip_right
            CameraWhich.FRONT -> R.string.kachi_cam_chip_front
        },
    )

    /** Tên đầy đủ của một camera (*Camera sau* …) — cùng chuỗi nhãn của overlay. */
    fun cameraName(ctx: Context, w: CameraWhich): String = ctx.getString(
        when (w) {
            CameraWhich.REAR -> R.string.kachi_camera_rear
            CameraWhich.LEFT -> R.string.kachi_camera_left
            CameraWhich.RIGHT -> R.string.kachi_camera_right
            CameraWhich.FRONT -> R.string.kachi_camera_front
        },
    )
}
