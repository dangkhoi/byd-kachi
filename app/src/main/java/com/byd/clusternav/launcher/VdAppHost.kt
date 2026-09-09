package com.byd.clusternav.launcher

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import com.byd.clusternav.system.inputd.InputDaemonClient
import com.byd.clusternav.system.inputd.SlotTouchMapper
import com.byd.clusternav.system.inputd.TouchRouter

/**
 * Dudu-style app projection, done a bit better.
 *
 * Dudu's "PIP" (RE `com.dudu.autoui.ui.activity.launcher.widget.pip.BydPipTextureView`) renders another
 * app **caption-free** inside a slot by creating a plain [VirtualDisplay] (a *secondary* display → the
 * system never draws the freeform caption on it) backed by a view Surface, then launching the app onto
 * that display and forwarding touch — both through a **uid-2000 shell** (Dudu ships an `app_process`
 * daemon; we already have the same privilege over the dadb loopback).
 *
 * Two deltas vs Dudu:
 *  - **SurfaceView** instead of TextureView → SurfaceFlinger composites the display straight to the
 *    slot surface with no extra GPU texture copy (Dudu's TextureView copy is a big part of its lag).
 *  - Launch/touch ride the dadb seam we already own.
 *
 * NO platform signing / ROM change required — works on any BYD (and the emulator) that exposes the
 * uid-2000 shell. On a platform-signed ROM the caption-free + zero-lag path is [SlotAppHost]
 * (ActivityView); this is the sideload path.
 */
class VdAppHost(
    context: Context,
    private val densityDpi: Int,
    // B2b: đăng ký/gỡ display của VD với DisplayOwnershipRegistry (qua WindowCommandDispatcher) để launcherSeam
    // cho phép lệnh `am start --display <vdId>`. Mặc định no-op (đường không-dispatcher / test).
    private val registerVd: (Int) -> Unit = {},
    private val unregisterVd: (Int) -> Unit = {},
    // B4: input-injection daemon (smooth touch over its OWN socket). null → fallback-only (`input -d`), i.e. the
    // pre-B4 behaviour. Touch data NEVER rides the ShellTransport command queue; only the daemon lifecycle start does.
    private val inputClient: InputDaemonClient? = null,
) : FrameLayout(context) {

    private val surface = SurfaceView(context)
    private var vd: VirtualDisplay? = null
    private var vdDisplayId: Int? = null
    private var dispW = 0                 // B4: cỡ VirtualDisplay (để map toạ độ ô→display; = cỡ surface nên map đồng nhất)
    private var dispH = 0
    private var pkg: String? = null
    private var shell: ((String) -> String)? = null
    private var launched = false

    init {
        // Default z-order: the surface composites BEHIND the window, so the slot header (added later,
        // on top) still draws over it. The app fills the slot.
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {}
            override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, ht: Int) {
                val v = vd
                if (v == null) {
                    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    // 8 = OWN_CONTENT_ONLY (chỉ hiện app đặt lên VD, KHÔNG mirror display 0 → hết "gương đệ quy")
                    // 256 = DESTROY_CONTENT_ON_REMOVAL (dọn khi gỡ). Shell mở app lên VD vẫn được (khác ActivityView bị chặn ở API startActivity, không phải ở cờ này).
                    val created = dm.createVirtualDisplay("kachi-slot-${System.currentTimeMillis()}", w, ht, densityDpi, h.surface, 8 or 256)
                    vd = created
                    dispW = w; dispH = ht          // B4: VD cỡ = surface cỡ → map toạ độ chạm đồng nhất
                    // B2b: đăng ký display của VD (thuộc LAUNCHER) TRƯỚC maybeLaunch — nếu không, cổng ownership
                    // của launcherSeam sẽ REJECT lệnh `am start --display <vdId>` (fail-safe deny display không chủ).
                    created?.display?.displayId?.let { id -> vdDisplayId = id; runCatching { registerVd(id) } }
                    maybeLaunch()
                } else {
                    v.surface = h.surface
                    v.resize(w, ht, densityDpi)
                    dispW = w; dispH = ht          // B4: giữ cỡ VD đồng bộ để map toạ độ đúng sau resize
                }
            }
            override fun surfaceDestroyed(h: SurfaceHolder) { vd?.surface = null }
        })
    }

    /** Bind the package + the uid-2000 shell seam; launches once the surface/VD is ready. */
    fun bind(pkg: String, shell: (String) -> String) {
        this.pkg = pkg; this.shell = shell; maybeLaunch()
    }

    private fun maybeLaunch() {
        val v = vd ?: return
        val p = pkg ?: return
        val sh = shell ?: return
        if (launched) return
        launched = true
        val displayId = v.display.displayId
        Thread {
            // TẤT CẢ lệnh dadb (blocking) chạy TRONG thread nền — KHÔNG gọi trên UI thread (chặn dựng SurfaceView → ô đen).
            val comp = resolveComponent(p, sh) ?: "$p/.MainActivity"
            // B1: built by the pure FreeformLaunch builder (byte-locked by LauncherCommandGoldenTest) instead of
            // an inline string. displayId = this host's OWN VirtualDisplay (a private secondary display for the
            // slot), NOT the cluster. Touch/force-stop lifecycle stays inline (moves to the input daemon in B4).
            val cmd = FreeformLaunch.launchOnDisplayCmd(comp, displayId, windowingMode = 1)
            sh("am force-stop $p")
            Thread.sleep(1000)     // đợi force-stop XONG hẳn → am start mở task MỚI trên VD, không tái dùng task fullscreen ở display 0 (bug gmail nhảy fullscreen)
            sh(cmd)                // mở ĐÚNG 1 lần trên VD — KHÔNG relaunch/di lần 2 (bỏ vòng retry gây nháy + làm app ô khác nhảy)
            runCatching { inputClient?.ensureStarted() }   // B4: hâm nóng daemon bơm chạm (lifecycle qua queue) — chạm sau mượt; không block
        }.start()
    }

    private fun resolveComponent(pkg: String, sh: (String) -> String): String? {
        val out = runCatching {
            sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg")
        }.getOrDefault("")
        return out.trim().lines().lastOrNull { it.contains("/") && it.contains(pkg) }
    }

    /**
     * Forward touch into the VD. PRIMARY = the resident [InputDaemonClient] over its OWN socket
     * (`injectInputEvent`, smooth, no per-event process spawn — the ~75ms/event `input` fork is gone). When the
     * daemon is unavailable/unhealthy the tap FALLS BACK to the pre-B4 `input -d <display> tap x y`
     * ([TouchRouter.fallbackTapCmd]) — BYTE-IDENTICAL to before, so touch never regresses when the daemon is off.
     *
     * DOWN/UP fall back (double-fire preserved on the fallback, exactly as before); the daemon path injects a
     * proper DOWN+UP (one tap, no double-fire). MOVE is daemon-only smoothness — the pre-B4 path had no MOVE
     * handling, so when the daemon is off this is a no-op = identical to today. Touch NEVER rides the command queue.
     */
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val v = vd ?: return false
        val sh = shell ?: return false
        val displayId = v.display.displayId
        val x = e.x.toInt(); val y = e.y.toInt()
        // View→display map. The VD is created at the surface size, so this is the identity today (daemon coords ==
        // fallback coords); it only scales if the display size ever diverges from the view size.
        val m = SlotTouchMapper.toDisplay(x, y, width, height, dispW, dispH)
        val dx = m[0]; val dy = m[1]
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                val routed = runCatching { inputClient?.sendTouch(displayId, e.actionMasked, dx, dy) ?: false }
                    .getOrDefault(false)
                if (TouchRouter.shouldFallback(routed)) {
                    Thread { runCatching { sh(TouchRouter.fallbackTapCmd(displayId, x, y)) } }.start()
                }
            }
            MotionEvent.ACTION_MOVE ->
                // Daemon-only smoothness (drag/scroll); no fallback (pre-B4 had none → no-op when the daemon is off).
                runCatching { inputClient?.sendTouch(displayId, MotionEvent.ACTION_MOVE, dx, dy) }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        val p = pkg; val sh = shell
        if (p != null && sh != null) Thread { runCatching { sh("am force-stop $p") } }.start()
        vdDisplayId?.let { id -> runCatching { unregisterVd(id) } }; vdDisplayId = null
        runCatching { vd?.release() }; vd = null
    }
}
