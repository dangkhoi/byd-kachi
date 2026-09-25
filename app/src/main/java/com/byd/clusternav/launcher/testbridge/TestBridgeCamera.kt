package com.byd.clusternav.launcher.testbridge

import android.os.Handler
import android.os.Looper

/**
 * Lệnh `camera --es arg left|right|none` — ép MỘT nhịp camera-theo-xi-nhan với xi-nhan GIẢ.
 *
 * Verify overlay E2E off-car: HAL panorama null trên emulator (không có video), nhưng cửa sổ overlay + nhãn
 * trái/phải dựng được ⇒ nhìn thấy đúng bên/đúng lúc = wiring xong. Trên xe chỉnh được bằng `prefs_set`
 * `camera_pos_left`/`camera_pos_right` (góc hiện) và `camera_cam_left`/`camera_cam_right` (cameraId) — không phải
 * sửa wiring. (`camera_lvds_option` đã gỡ cùng mười option LVDS, spec `camera-turn-signal-hal-socket.html` R6.)
 */
internal object TestBridgeCamera {
    fun run(cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        val a = cmd.arg.trim().lowercase()
        val left = a == "left" || a == "trai"
        val right = a == "right" || a == "phai"
        Handler(Looper.getMainLooper()).post { hooks.cameraTick(left, right) }
        reply.ok("arg" to a, "left" to left, "right" to right)
    }
}
