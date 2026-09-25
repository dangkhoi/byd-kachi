package com.byd.clusternav.launcher.camera

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.BydHalGateway
import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView

/**
 * ═══ Cầu HAL cho camera panorama — gọi `BYDAutoPanoramaDevice` qua reflection proven ═════════════════════════
 *
 * RE `docs/diagnostics/camera-panorama-RE-2026-09-22.md`: HAL `android.hardware.bydauto.panorama.
 * BYDAutoPanoramaDevice` (họ `BYDAuto*Device`) — dùng [BydHalGateway.namedInt] (đường ghi proven, KHÔNG mở
 * reflection mới). Method/hằng lấy từ jadx-tmap BYDAutoPanoramaDevice.java (giá trị THẬT).
 *
 * ⚠ Off-car: `namedInt` trả `null` (device null) ⇒ mọi hàm no-op an toàn, trả false. Xe mới bật thật.
 * ⚠ Đây là ĐƯỜNG ĐIỀU KHIỂN (bật view nào). Tín hiệu video đổ vào SurfaceView của overlay là cơ chế LVDS phần
 *   cứng — xem [CameraOverlayView]. Hai chuyện tách bạch.
 */
class PanoramaHal(private val ctx: Context) {

    private val gw by lazy { BydHalGateway(ctx.applicationContext) }

    /**
     * Bật hệ panorama + chọn view. [option] là tổ hợp cờ thử: "C"=setLVDS trước · "D"=FULL_SCREEN thay WIDGET ·
     * "H"=chờ workState ON trước setOutput. Trả rc (null=off-car/từ chối).
     *
     * ⚠ Pref `camera_lvds_option` từng nuôi tham số này đã **gỡ hẳn** (spec `camera-turn-signal-hal-socket.html`
     * R6): [ĐO xe 2026-09-25] đường có HÌNH là AVMCamera đổ frame vào Surface, không phải LVDS thụ động. Mọi chỗ
     * gọi nay dùng mặc định `"A"`; tham số ở lại để một lượt thử trên xe không phải sửa lại thân hàm.
     */
    fun open(view: CamView, option: String = "A"): Boolean {
        if (option.contains("C")) gw.namedInt(FQN, "setLVDSState", intArrayOf(LVDS_PANORMA_RF_VIEW))   // C: LVDS trước
        val op = gw.namedInt(FQN, "setPanoOperation", intArrayOf(WORK_ON))
        if (option.contains("H")) gw.namedInt(FQN, "getPanoWorkState", intArrayOf())                    // H: đọc chờ ON
        val mode = if (option.contains("D")) DISPLAY_MODE_FULL_SCREEN else DISPLAY_MODE_WIDGET           // D: full-screen
        gw.namedInt(FQN, "setDisplayMode", intArrayOf(mode))
        val out = gw.namedInt(FQN, "setPanoOutputState", intArrayOf(view.outputState))
        Log.i(TAG, "open(${view.name}) opt=$option op=$op mode=$mode out=$out")
        return out != null
    }

    /** Tắt panorama (setPanoOperation OFF). */
    fun close(): Boolean {
        val op = gw.namedInt(FQN, "setPanoOperation", intArrayOf(WORK_OFF))
        Log.i(TAG, "close op=$op")
        return op != null
    }

    /** Đọc trạng thái hiện (cho chẩn đoán/runbook). null = off-car. */
    fun workState(): Long? = gw.namedInt(FQN, "getPanoWorkState", intArrayOf())
    fun outputState(): Long? = gw.namedInt(FQN, "getPanoOutputState", intArrayOf())

    /** Định tuyến tín hiệu LVDS (option B của runbook — nếu output-state không đổ vào layer app). */
    fun setLvds(state: Int): Boolean = gw.namedInt(FQN, "setLVDSState", intArrayOf(state)) != null

    companion object {
        const val TAG = "KachiCamera"
        const val FQN = "android.hardware.bydauto.panorama.BYDAutoPanoramaDevice"
        // [ĐO jadx-tmap BYDAutoPanoramaDevice] — giá trị THẬT.
        const val WORK_ON = 1            // FUNCATION_ON
        const val WORK_OFF = 0           // FUNCATION_OFF
        const val DISPLAY_MODE_WIDGET = 3
        const val DISPLAY_MODE_FULL_SCREEN = 1
        const val LVDS_PANORMA_RF_VIEW = 1
    }
}
