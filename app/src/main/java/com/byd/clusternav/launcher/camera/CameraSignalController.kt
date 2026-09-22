package com.byd.clusternav.launcher.camera

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraSignalEnabled
import com.byd.clusternav.launcher.camera.CameraSignalPolicy.Turn

/**
 * ═══ CAMERA THEO XI-NHAN · điều phối (`:app`) ═══════════════════════════════════════════════════════════════
 *
 * Mỗi nhịp nhận trạng thái xi-nhan (từ `CarStatus.lights`) → nếu bật tính năng (pref, mặc định TẮT) và bên xi-nhan
 * ĐỔI thì: mở camera view tương ứng ([PanoramaHal]) + hiện overlay bên đó ([CameraOverlayView]); hết xi-nhan ⇒
 * đóng. Chỉ ĐỔI khi khác nhịp trước (không dựng lại overlay mỗi nhịp — cùng lẽ RainDefrostOwner).
 *
 * ⚠ Off-car: PanoramaHal no-op (device null) nhưng overlay vẫn dựng (SurfaceView đen) — đo được wiring. Tín hiệu
 *   video thật = on-car (runbook camera-panorama).
 */
class CameraSignalController(private val appCtx: Context) {

    private val hal by lazy { PanoramaHal(appCtx) }
    private val overlay by lazy { CameraOverlayView(appCtx) }
    private var current: Turn = Turn.NONE

    /** Một nhịp. [left]/[right] = trạng thái xi-nhan đọc từ xe (null = chưa đọc được ⇒ coi như tắt). */
    fun tick(left: Boolean?, right: Boolean?) {
        if (!Prefs.cameraSignalEnabled(appCtx)) { if (current != Turn.NONE) stop(); return }
        val turn = CameraSignalPolicy.turnOf(left == true, right == true)
        if (turn == current) return   // không đổi ⇒ giữ nguyên (không dựng lại)
        current = turn
        when (turn) {
            Turn.NONE -> stop()
            else -> {
                val view = CameraSignalPolicy.defaultView(turn) ?: return
                val side = CameraSignalPolicy.defaultSide(turn) ?: return
                Log.i(PanoramaHal.TAG, "xi-nhan $turn → camera ${view.name} overlay $side")
                overlay.show(side)
                hal.open(view)
            }
        }
    }

    private fun stop() {
        hal.close()
        overlay.hide()
    }
}
