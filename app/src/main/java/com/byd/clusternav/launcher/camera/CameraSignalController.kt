package com.byd.clusternav.launcher.camera

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraSignalEnabled
import com.byd.clusternav.cameraLvdsOption
import com.byd.clusternav.cameraOnCluster
import com.byd.clusternav.cameraCamId
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
    private val avm by lazy { AvmCamera() }
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

    /**
     * Một nhịp với trạng thái xi-nhan cho sẵn (cho test/off-car). null = coi như tắt.
     *
     * ⚠ [ĐO xe 2026-09-24] Xi-nhan NHẤP NHÁY (~1.5Hz, sáng/tắt ~340ms). Poll thấy pha TẮT ⇒ đọc 0 dù
     * đang bật ⇒ camera không lên / nhấp nháy. Giữ MỐC lần thấy ON gần nhất mỗi bên; bên nào ON trong [HOLD_MS]
     * (> chu kỳ nháy) thì coi như ĐANG bật. Nhờ đó pha TẮT của nháy không đóng camera; chỉ đóng khi tắt hẳn
     * (không thấy ON quá [HOLD_MS]). Gọi ở nhịp NHANH ([AutomationService.CAMERA_TICK_MS] 250ms < 340ms ⇒ bắt kịp
     * pha ON của nháy).
     */
    fun tick(left: Boolean?, right: Boolean?) {
        if (!Prefs.cameraSignalEnabled(appCtx)) { if (current != Turn.NONE) stop(); return }
        val now = clockMs()
        if (left == true) lastLeftOnMs = now
        if (right == true) lastRightOnMs = now
        val leftHeld = now - lastLeftOnMs <= HOLD_MS
        val rightHeld = now - lastRightOnMs <= HOLD_MS
        val turn = CameraSignalPolicy.turnOf(leftHeld, rightHeld)
        if (turn == current) return   // không đổi ⇒ giữ nguyên (không dựng lại, không nháy theo đèn)
        current = turn
        when (turn) {
            Turn.NONE -> stop()
            else -> {
                val view = CameraSignalPolicy.defaultView(turn) ?: return
                val side = CameraSignalPolicy.defaultSide(turn) ?: return
                val opt = Prefs.cameraLvdsOption(appCtx)   // phương án thử (runbook A–J) qua pref
                // cameraId đổi được trên xe (chưa chắc map — thử): pref camera_cam_left/right, mặc định theo CamView.
                val camId = if (turn == Turn.LEFT) Prefs.cameraCamId(appCtx, left = true, view.cameraId)
                            else Prefs.cameraCamId(appCtx, left = false, view.cameraId)
                Log.i(PanoramaHal.TAG, "xi-nhan $turn → camera ${view.name} camId=$camId overlay $side opt=$opt")
                // Bật panorama HAL (best-effort — vài ROM cần WORK_ON để camera stack sống) rồi ĐỔ frame AVMCamera
                // vào Surface của overlay (RE kinex `b1/RunnableC0170d`: đây mới là đường có HÌNH, LVDS thụ động ra đen).
                hal.open(view, opt)
                overlay.show(
                    side,
                    onCluster = Prefs.cameraOnCluster(appCtx) || opt.contains("G"),
                    option = opt,
                ) { surface -> runCatching { avm.open(camId, surface) } }
            }
        }
    }

    /** Đồng hồ ĐƠN ĐIỆU cho HOLD (test override được). */
    internal var clockMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }
    private var lastLeftOnMs = -HOLD_MS
    private var lastRightOnMs = -HOLD_MS

    private fun stop() {
        current = Turn.NONE   // [P1 fix] reset để bật lại KHỚP lượt rẽ sau (không kẹt current cũ → return sớm)
        lastLeftOnMs = -HOLD_MS; lastRightOnMs = -HOLD_MS
        runCatching { avm.close() }
        hal.close()
        overlay.hide()
    }

    companion object {
        const val LIGHT_DEVICE = "android.hardware.bydauto.light.BYDAutoLightDevice"

        /** Giữ camera qua pha TẮT của nháy: > chu kỳ nháy (~700ms) đủ để một lần nháy không đóng; đủ ngắn để tắt
         *  hẳn xi-nhan thì camera đóng nhanh. [ĐO xe: nháy ~1.5Hz]. */
        const val HOLD_MS = 1_200L
    }
}
