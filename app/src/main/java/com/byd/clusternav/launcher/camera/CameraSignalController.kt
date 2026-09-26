package com.byd.clusternav.launcher.camera

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraSignalEnabled
import com.byd.clusternav.cameraPos
import com.byd.clusternav.cameraOnCluster
import com.byd.clusternav.cameraCamId
import com.byd.clusternav.cameraRotation
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
    // BG-15 + [SOÁT Pass 1 · 2026-09-25]: MỘT controller dùng chung ⇒ `tick()` chạy CẢ trên main (HOME) lẫn luồng
    // vòng `AutomationService`. Cờ phải đổi bằng **một phép nguyên tử**: `if (started) … started = true` cho hai
    // luồng đi qua cùng lúc ⇒ hai lượt `start` xếp hàng (hoặc một `stop` lọt giữa) ⇒ cờ nói khác sự thật của socket.
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)
    private val signal by lazy { HalSignalClient() }
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "kachi-camera-hal").apply { isDaemon = true }
    }

    /**
     * Đồng bộ với công tắc — gọi từ `AutomationService` (mỗi nhịp 60 s + ngay khi `sync`) và từ HOME khi state đổi.
     * Bật ⇒ đảm bảo helper + socket đang nghe; tắt ⇒ dừng luồng socket (BG-15) và đóng overlay (trên main).
     *
     * Sự kiện xi-nhan ON đến từ socket ([HalSignalClient] onTurn → tick(l,r) refresh mốc ON). Ở đây KHÔNG đọc
     * evtLeft/evtRight sticky: [ĐO xe 2026-09-25] xi-nhan nhấp nháy → nếu tài xế tắt đúng pha ON, evt kẹt true ⇒
     * refresh HOLD mãi ⇒ camera KHÔNG tắt (bug lúc-bị-lúc-không). Để HOLD tự hết theo mốc ON gần nhất là đường tin
     * cậy — và mốc hết hạn được HẸN đúng lúc bằng [expiry] (BG-13), không cần vòng 250 ms nào gọi vào đây nữa.
     */
    fun tick() {
        if (Prefs.cameraSignalEnabled(appCtx)) ensureSignal() else release()
        tick(null, null)
    }

    /** Khởi HAL helper (uid shell) + socket client MỘT lần, trên thread NỀN (ensure() chặn: push jar + shell). */
    private fun ensureSignal() {
        if (!started.compareAndSet(false, true)) return
        bg.execute {
            runCatching { HalHelperLauncher.ensure(appCtx) }.onFailure { Log.w(PanoramaHal.TAG, "HAL helper ensure lỗi: ${it.message}") }
            runCatching { signal.start { l, r -> tick(l, r) } }.onFailure { Log.w(PanoramaHal.TAG, "HAL signal start lỗi: ${it.message}") }
        }
    }

    /**
     * BG-15 (2026-09-25): dừng luồng socket `KachiHalSignal` khi không còn ai cần (công tắc TẮT / automation hết
     * việc). Trước đây không có đường này ⇒ mỗi controller một luồng sống tới khi xe tắt máy. Idempotent; đi qua
     * [bg] để KHÔNG vượt mặt một `start` đang xếp hàng (executor một luồng giữ thứ tự). Bật lại ⇒ [ensureSignal]
     * dựng lại (helper `ensure` idempotent: dò cổng trước khi khởi).
     */
    fun release() {
        if (!started.compareAndSet(true, false)) return
        bg.execute { runCatching { signal.stop() }.onFailure { Log.w(PanoramaHal.TAG, "HAL signal stop lỗi: ${it.message}") } }
    }

    /**
     * Một nhịp với trạng thái xi-nhan cho sẵn (từ socket, test bridge, hoặc null = không có tin mới).
     *
     * ⚠ [ĐO xe 2026-09-24] Xi-nhan NHẤP NHÁY (~1.5Hz, sáng/tắt ~340ms). Nhìn pha TẮT mà đóng thì camera nháy theo
     * đèn. Luật giữ nằm ở [CameraHold] (thuần, test với đồng hồ giả): bên nào ON trong [HOLD_MS] coi như ĐANG bật;
     * chỉ đóng khi không thấy ON quá [HOLD_MS]. Mốc hết hạn được hẹn bằng [expiry] sau MỖI nhịp (BG-13) — trước
     * 2026-09-25 cần vòng automation 250 ms gọi `tick(false,false)` chỉ để HOLD hết hạn.
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
        val turn = hold.observe(left, right, now)
        // BG-13: hẹn ĐÚNG mốc HOLD hết hạn gần nhất (đặt lại mỗi nhịp — sự kiện ON mới đẩy mốc lùi). Không có bên
        // nào đang giữ ⇒ không hẹn gì. Handler main ⇒ [expiry] cũng chạy trên main.
        main.removeCallbacks(expiry)
        hold.expiresInMs(now)?.let { main.postDelayed(expiry, it) }
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
                // R7 (owner 2026-09-26): vùng gương crop từ fisheye là dải DỌC ⇒ căng vào ô vuông thì NGANG; xoay
                // theo pref `camera_rotation` (mặc định theo bên: trái ↺ −90 / phải ↻ +90). Tính ở `:core`, overlay
                // chỉ nhận số độ.
                val rot = CameraSignalPolicy.rotationDegrees(Prefs.cameraRotation(appCtx), turn)
                Log.i(PanoramaHal.TAG, "xi-nhan $turn → camera ${view.name} camId=$camId (def=$defId) crop=${crop != null} overlay $side góc=$corner rot=$rot")
                // Bật panorama HAL (best-effort — vài ROM cần WORK_ON để camera stack sống) rồi ĐỔ frame AVMCamera
                // vào Surface của overlay (RE kinex `b1/RunnableC0170d`: đây mới là đường có HÌNH, LVDS thụ động ra đen).
                hal.open(view)
                overlay.show(
                    corner = corner,
                    side = side,
                    onCluster = Prefs.cameraOnCluster(appCtx),
                    crop = crop,
                    rotationDeg = rot,
                ) { surface -> runCatching { avm.open(camId, surface) } }
            }
        }
    }

    /** Đồng hồ ĐƠN ĐIỆU cho HOLD (test override được). */
    internal var clockMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }
    private val hold = CameraHold(HOLD_MS)
    /** Hẹn hết hạn HOLD (BG-13): `observe(null,null)` đúng lúc mốc ON cuối + HOLD_MS trôi qua ⇒ đóng camera. */
    private val expiry = Runnable { tickMain(null, null) }

    private fun stop() {
        current = Turn.NONE   // [P1 fix] reset để bật lại KHỚP lượt rẽ sau (không kẹt current cũ → return sớm)
        hold.reset()
        main.removeCallbacks(expiry)
        runCatching { avm.close() }
        hal.close()
        overlay.hide()
    }

    companion object {
        const val LIGHT_DEVICE = "android.hardware.bydauto.light.BYDAutoLightDevice"

        /** Giữ camera qua pha TẮT của nháy — giá trị + lý do ở [CameraHold.HOLD_MS]. */
        const val HOLD_MS = CameraHold.HOLD_MS
    }
}
