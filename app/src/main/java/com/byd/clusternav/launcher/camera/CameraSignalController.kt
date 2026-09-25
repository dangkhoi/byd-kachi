package com.byd.clusternav.launcher.camera

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraSignalEnabled
import com.byd.clusternav.cameraPos
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
    private var current: Turn = Turn.NONE

    // Nguồn xi-nhan THẬT = HAL helper (app_process uid shell) publish socket 19322 → [HalSignalClient] subscribe.
    // [ĐO xe 2026-09-25] register listener dưới uid APP bị SecurityException BYDAUTO_LIGHT_GET (perm signature);
    // chạy dưới uid shell (mô hình kinex) thì OK. getLightStatus poll trả 0 trên trim này ⇒ không dùng poll.
    @Volatile private var started = false
    private val signal by lazy { HalSignalClient() }
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "kachi-camera-hal").apply { isDaemon = true }
    }

    fun tick() {
        if (!Prefs.cameraSignalEnabled(appCtx)) { if (current != Turn.NONE) stop(); return }
        ensureSignal()
        // FGS nhịp: gọi tick(false,false) — CHỈ để HOLD tự HẾT HẠN khi không còn sự kiện ON. Sự kiện xi-nhan ON
        // đến từ socket ([HalSignalClient] onTurn → tick(l,r) refresh lastOnMs). KHÔNG đọc evtLeft/evtRight sticky ở
        // đây: [ĐO xe 2026-09-25] xi-nhan nhấp nháy → nếu tài xế tắt đúng pha ON, evt kẹt true ⇒ FGS refresh HOLD
        // mãi ⇒ camera KHÔNG tắt (bug lúc-bị-lúc-không). Để HOLD tự hết theo mốc ON gần nhất là đường tin cậy.
        tick(false, false)
    }

    /** Khởi HAL helper (uid shell) + socket client MỘT lần, trên thread NỀN (ensure() chặn: push jar + shell). */
    private fun ensureSignal() {
        if (started) return
        started = true
        bg.execute {
            runCatching { HalHelperLauncher.ensure(appCtx) }.onFailure { Log.w(PanoramaHal.TAG, "HAL helper ensure lỗi: ${it.message}") }
            runCatching { signal.start { l, r -> tick(l, r) } }.onFailure { Log.w(PanoramaHal.TAG, "HAL signal start lỗi: ${it.message}") }
        }
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
        // overlay/hal/avm là op WindowManager + View ⇒ PHẢI main thread. tick(l,r) có thể được gọi từ LUỒNG ĐỌC
        // socket ([HalSignalClient]) ⇒ marshal về main. tick() no-arg (FGS) cũng đi qua đây, main-post là no-op-an-toàn.
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            main.post { tickMain(left, right) }; return
        }
        tickMain(left, right)
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun tickMain(left: Boolean?, right: Boolean?) {
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
                // Góc hiện overlay = pref TỪNG BÊN (`camera_pos_left/right`, mặc định trái→TL / phải→TR). KHÔNG suy
                // từ `side`: owner chốt xi-nhan trái vẫn được hiện ở góc trên-phải (spec R4).
                val corner = Prefs.cameraPos(appCtx, left = turn == Turn.LEFT)
                // cameraId: đọc pref TỪNG BÊN (SL6/xe khác tự chọn cam nào lên — owner 2026-09-25 "cho chọn cam như cũ").
                // cameraId: picker TỪNG BÊN. defId = view.cameraId (Seal fisheye = id 1). SL6 fisheye = id 0
                // (ảnh owner: cam 0 ra 4-in-1) ⇒ SL6 chọn id 0 trong Cài đặt. crop [0.25-0.35]/[0.65-0.75] là VÙNG
                // GƯƠNG của ẢNH FISHEYE — đúng cho CẢ id 0 (SL6) lẫn id 1 (Seal), nên LUÔN áp view.crop (đừng gate
                // theo camId: gate camId==defId từng chặn SL6-chọn-id-0 khỏi crop = REGRESSION 2.42→2.44 khi đổi id 0→1).
                val defId = view.cameraId
                val camId = Prefs.cameraCamId(appCtx, left = turn == Turn.LEFT, defId)
                val crop = view.crop
                Log.i(PanoramaHal.TAG, "xi-nhan $turn → camera ${view.name} camId=$camId (def=$defId) crop=${crop != null} overlay $side góc=$corner")
                // Bật panorama HAL (best-effort — vài ROM cần WORK_ON để camera stack sống) rồi ĐỔ frame AVMCamera
                // vào Surface của overlay (RE kinex `b1/RunnableC0170d`: đây mới là đường có HÌNH, LVDS thụ động ra đen).
                hal.open(view)
                overlay.show(
                    corner = corner,
                    side = side,
                    onCluster = Prefs.cameraOnCluster(appCtx),
                    crop = crop,
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
