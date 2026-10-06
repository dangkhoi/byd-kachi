package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.byd.clusternav.launcher.camera.CameraDemand
import com.byd.clusternav.launcher.camera.CameraDemandDispatch

/**
 * Lệnh `camera --es name left|right|none` (extra là `name` = `TestBridgeCommand.EXTRA_ARG`, KHÔNG phải `arg`) — ép MỘT nhịp camera-theo-xi-nhan với xi-nhan GIẢ.
 *
 * Verify overlay E2E off-car: HAL panorama null trên emulator (không có video), nhưng cửa sổ overlay + nhãn
 * trái/phải dựng được ⇒ nhìn thấy đúng bên/đúng lúc = wiring xong. Trên xe chỉnh được bằng `prefs_set`
 * `camera_pos_left`/`camera_pos_right` (góc hiện) và `camera_cam_left`/`camera_cam_right` (cameraId) — không phải
 * sửa wiring. (`camera_lvds_option` đã gỡ cùng mười option LVDS, spec `camera-turn-signal-hal-socket.html` R6.)
 *
 * 2.93 · CAMERA-ON-DEMAND (spec `kachi-293-cam.html` §6 V-emu): đối số dạng `<động từ>:<camera>` — `demand:rear` (như
 * bấm NÚT: bật/tắt) · `open:left` / `close:left` (mở chắc chắn / như NÓI *"tắt camera trái"*) · `demand:off` (*Tắt camera*)
 * — đi ĐÚNG đường thi hành của phím vật lý / thanh nút / giọng nói ([CameraDemandDispatch.fireForResult]), không đường thứ
 * hai. Lời đáp mang `outcome` (wave 2B: `opened` · `closed` · `nothing_to_close` · `unreachable`) — CÙNG kết quả mà câu trả
 * lời giọng nói đọc từ đó. Đối số không có `:` ⇒ nghĩa cũ y nguyên (xi-nhan giả). Kết hợp `camera_synth on` để có hình.
 */
internal object TestBridgeCamera {
    fun run(app: Context, cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        val a = cmd.arg.trim().lowercase()
        CameraDemand.parseBridge(a)?.let { op ->
            CameraDemandDispatch.fireForResult(app, op) { o ->
                reply.ok(
                    "arg" to a,
                    "demand" to CameraDemand.encode(op),
                    "sent" to (o != CameraDemand.Outcome.UNREACHABLE),
                    "outcome" to o.name.lowercase(),
                )
            }
            return
        }
        val left = a == "left" || a == "trai"
        val right = a == "right" || a == "phai"
        Handler(Looper.getMainLooper()).post { hooks.cameraTick(left, right) }
        reply.ok("arg" to a, "left" to left, "right" to right)
    }
}
