package com.byd.clusternav.launcher.testbridge

import android.os.Handler
import android.os.Looper

/**
 * Lệnh `camera --es arg left|right|none` — ép MỘT nhịp camera-theo-xi-nhan với xi-nhan GIẢ.
 *
 * Verify overlay E2E off-car: HAL panorama null trên emulator (không có video), nhưng cửa sổ overlay + nhãn
 * trái/phải dựng được ⇒ nhìn thấy đúng bên/đúng lúc = wiring xong. Trên xe chỉ cần đổi `camera_lvds_option`
 * (pref, runbook A–J) để tìm đúng lệnh HAL — không phải sửa wiring.
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
