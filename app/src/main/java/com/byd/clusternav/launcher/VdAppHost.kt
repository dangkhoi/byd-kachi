package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.system.inputd.InputDaemonClient
import com.byd.clusternav.system.inputd.SlotTouchMapper
import com.byd.clusternav.system.inputd.TouchRouter
import com.byd.clusternav.launcher.KachiSpace as Sp

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
 *
 * ## H2 — hai chỗ hỏng đã vá ở đây (2026-09-14)
 *  1. **Rò màn ảo.** Trước đây `release()` chỉ nằm ở `onDetachedFromWindow`, tức vòng đời một tài nguyên hệ
 *     thống treo vào một sự kiện mà hệ điều hành có quyền hoãn (màn Kachi "đang kết thúc" giữ view hàng phút).
 *     Nay mỗi màn ảo do [SlotVdOwner] cầm theo **ô**, và mọi đường thay/đóng/dựng lại/huỷ đều gọi [release] —
 *     idempotent. Xem KDoc [SlotVdLedger] cho phép đo sinh ra luật này.
 *  2. **Khung đóng băng.** App trên màn ảo chết thì `SurfaceView` giữ khung cuối ⇒ ô trông còn sống. Nay
 *     [SlotLiveProbe] đo 5 s/lần (một lệnh cho mọi ô); mất task ⇒ **giấu mặt vẽ** (khung cuối biến mất, lộ thẻ
 *     icon+tên phía sau) + hiện nhãn *"app đã đóng — chạm để mở lại"*, chạm là mở lại đúng ô này.
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
    /** H2: chỉ số ô — khoá sở hữu màn ảo ở [SlotVdOwner] (bất biến: mỗi ô nhiều nhất MỘT màn ảo sống). */
    private val slot: Int = 0,
    /** H2: chủ (một cây workspace). Hai màn Kachi cùng sống ⇒ hai chủ khác nhau, cùng tranh một ô. */
    private val owner: String = "ws",
) : FrameLayout(context) {

    private val surface = SurfaceView(context)
    private var vd: VirtualDisplay? = null
    private var vdDisplayId: Int? = null
    private var dispW = 0                 // B4: cỡ VirtualDisplay (để map toạ độ ô→display; = cỡ surface nên map đồng nhất)
    private var dispH = 0
    private var pkg: String? = null
    private var shell: ((String) -> String)? = null
    private var launched = false

    /**
     * Đã nhả màn ảo chưa ⇒ mọi lời gọi [release] sau là no-op (một ô bị thay có thể gọi [release] rồi mới tháo
     * view). Khai ở đây, cùng các field khác, vì `init` đọc nó; luật idempotent xem KDoc [release].
     */
    private var released = false

    /** H2: khoá theo dõi ở [SlotLiveProbe] — riêng cho từng chủ×ô để hai màn Kachi không đạp lên nhau. */
    private val probeKey = "$owner#$slot"

    /** H2: thẻ "app đã đóng — chạm để mở lại"; chỉ dựng khi thật sự cần (ô sống thì không tốn view nào). */
    private var closedCard: TextView? = null

    init {
        // Default z-order: the surface composites BEHIND the window, so the slot header (added later,
        // on top) still draws over it. The app fills the slot.
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {}
            override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, ht: Int) {
                val v = vd
                if (v == null) {
                    // Đã nhả ⇒ host này là rác đang chờ tháo: KHÔNG được tạo màn ảo mới (đó đúng là cách một ô
                    // "đã đóng" lại mọc thêm một `kachi-slot-*` không ai cầm).
                    if (released) return
                    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    // 8 = OWN_CONTENT_ONLY (chỉ hiện app đặt lên VD, KHÔNG mirror display 0 → hết "gương đệ quy")
                    // 256 = DESTROY_CONTENT_ON_REMOVAL (dọn khi gỡ). Shell mở app lên VD vẫn được (khác ActivityView bị chặn ở API startActivity, không phải ở cờ này).
                    val name = "kachi-slot-$slot-${System.currentTimeMillis()}"
                    val created = dm.createVirtualDisplay(name, w, ht, densityDpi, h.surface, 8 or 256)
                    vd = created
                    dispW = w; dispH = ht          // B4: VD cỡ = surface cỡ → map toạ độ chạm đồng nhất
                    // B2b: đăng ký display của VD (thuộc LAUNCHER) TRƯỚC maybeLaunch — nếu không, cổng ownership
                    // của launcherSeam sẽ REJECT lệnh `am start --display <vdId>` (fail-safe deny display không chủ).
                    created?.display?.displayId?.let { id ->
                        vdDisplayId = id
                        runCatching { registerVd(id) }
                        // H2·1: giao tay cầm cho chủ sở hữu theo Ô — màn ảo CŨ của chính ô này (kể cả do một màn
                        // Kachi đời trước tạo và chưa kịp chết) được giải phóng NGAY tại đây.
                        SlotVdOwner.adopt(owner, slot, name, VdLease(created, id, unregisterVd))
                    }
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
            // H2·2: từ đây mới bắt đầu ĐO "còn task trên màn ảo không". [SlotLiveness] không kết luận chết trước
            // khi thấy sống ít nhất một nhịp ⇒ ca "app chưa bao giờ vào được ô" (H1/Waze) KHÔNG bị nhận nhầm.
            post { if (!released) SlotLiveProbe.watch(probeKey, p, displayId, sh) { onAppClosed() } }
        }.start()
    }

    private fun resolveComponent(pkg: String, sh: (String) -> String): String? {
        val out = runCatching {
            sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg")
        }.getOrDefault("")
        return out.trim().lines().lastOrNull { it.contains("/") && it.contains(pkg) }
    }

    // ── H2·2 · KÊNH IM LẶNG PHẢI NÓI ────────────────────────────────────────────────────────────────────────

    /**
     * App trong ô đã chết (đo ở [SlotLiveProbe]). Hai việc, đúng thứ tự:
     *  1. **Giấu mặt vẽ** — `SurfaceView` bị GONE ⇒ khung hình cuối (đóng băng) biến mất, lộ thẻ icon+tên mà
     *     [WorkspaceView] đã đặt sẵn phía sau. KHÔNG giải phóng màn ảo: mở lại dùng đúng màn ảo đó (bất biến
     *     một-màn-ảo-mỗi-ô giữ nguyên, không có nhịp tạo/huỷ thừa).
     *  2. **Nói ra** — nhãn "app đã đóng — chạm để mở lại", chạm là mở lại NGAY trong ô này.
     */
    private fun onAppClosed() {
        if (released || closedCard != null) return
        surface.visibility = GONE
        val card = TextView(context).apply {
            text = context.getString(R.string.kachi_slot_app_closed)
            setTextColor(Color.parseColor(KachiTheme.MUT)); KachiType.apply(this, KachiType.BODY)
            // ĐÁY ô, không phải giữa: thẻ icon+tên app do [WorkspaceView.appCard] vẽ nằm CHÍNH GIỮA và nay lộ ra
            // sau khi mặt vẽ bị giấu — [ĐO ảnh 2026-09-14] để `gravity = CENTER` thì câu chữ đè lên icon.
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(dp(Sp.L), dp(Sp.SLOT_HEAD_CLEAR), dp(Sp.L), dp(Sp.L))
            setOnClickListener { reopen() }
        }
        closedCard = card
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Người dùng chạm thẻ "đã đóng" ⇒ mở lại app trên đúng màn ảo của ô (không dựng lại view, không tạo VD mới). */
    private fun reopen() {
        closedCard?.let { removeView(it) }; closedCard = null
        surface.visibility = VISIBLE
        launched = false
        maybeLaunch()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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

    // ── H2·1 · GIẢI PHÓNG MÀN ẢO ────────────────────────────────────────────────────────────────────────────

    /**
     * Nhả màn ảo của ô này + dừng app + thôi đo. **Idempotent** — [WorkspaceView] gọi tường minh TRƯỚC khi tháo
     * view (đường chắc chắn), `onDetachedFromWindow` gọi lại (đường cũ, cho mọi ca tháo view ngoài tầm nhìn).
     *
     * Vì sao không dựa mỗi vào `onDetachedFromWindow`: [ĐO] 2026-09-14 cho thấy màn Kachi mang cờ "đang kết thúc"
     * vẫn giữ cây view (và màn ảo) trong khi màn Kachi mới đã dựng ô của nó ⇒ 4 màn ảo cho 2 ô. Xem [SlotVdLedger].
     */
    fun release() {
        if (released) return
        released = true
        SlotLiveProbe.unwatch(probeKey)
        val p = pkg; val sh = shell
        if (p != null && sh != null) Thread { runCatching { sh("am force-stop $p") } }.start()
        vdDisplayId = null
        SlotVdOwner.release(owner, slot)   // gỡ đăng ký + VirtualDisplay.release() nằm trong VdLease.free()
        vd = null
        launched = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}
