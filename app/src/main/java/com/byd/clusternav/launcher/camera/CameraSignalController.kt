package com.byd.clusternav.launcher.camera

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraSignalEnabled
import com.byd.clusternav.cameraLvdsOption
import com.byd.clusternav.cameraOnCluster
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
    private val gw by lazy { com.byd.clusternav.launcher.BydHalGateway(appCtx.applicationContext) }
    private var current: Turn = Turn.NONE

    /**
     * Một nhịp — **tự đọc xi-nhan trực tiếp** qua HAL (findings 2026-09-23 mục 6: `carStatus.lights` LUÔN null vì
     * datum xi-nhan không nằm trong tập poll của HOME). `BYDAutoLightDevice.getLightStatus(4/5)` [ĐO
     * `HalReadTables:33` LIGHT_LEFT_TURN=4 · LIGHT_RIGHT_TURN=5]. Giá trị ≠ 0/rỗng ⇒ đang bật.
     */
    fun tick() {
        if (!Prefs.cameraSignalEnabled(appCtx)) { if (current != Turn.NONE) stop(); return }
        tick(readTurn(4), readTurn(5))
    }

    /** Đọc một đèn xi-nhan (type 4=trái/5=phải). null (off-car/không đọc được) ⇒ coi như tắt. */
    private fun readTurn(type: Int): Boolean? {
        val raw = gw.getter(LIGHT_DEVICE, "getLightStatus", type) ?: return null
        return raw.trim().toIntOrNull()?.let { it != 0 } ?: false
    }

    /** Một nhịp với trạng thái xi-nhan cho sẵn (cho test/off-car). null = coi như tắt. */
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
                val opt = Prefs.cameraLvdsOption(appCtx)   // phương án thử (runbook A–J) qua pref
                Log.i(PanoramaHal.TAG, "xi-nhan $turn → camera ${view.name} overlay $side opt=$opt")
                overlay.show(side, onCluster = Prefs.cameraOnCluster(appCtx) || opt.contains("G"), option = opt)   // pref cụm / opt G
                hal.open(view, opt)
            }
        }
    }

    private fun stop() {
        hal.close()
        overlay.hide()
    }

    companion object {
        const val LIGHT_DEVICE = "android.hardware.bydauto.light.BYDAutoLightDevice"
    }
}
