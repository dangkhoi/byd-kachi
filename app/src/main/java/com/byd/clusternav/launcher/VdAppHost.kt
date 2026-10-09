package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.byd.clusternav.system.inputd.GestureFallback
import com.byd.clusternav.system.inputd.InputDaemonClient
import com.byd.clusternav.system.inputd.SlotTouchMapper
import com.byd.clusternav.system.inputd.TouchRouter
import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.escape.SlotEscape

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
 *     [SlotLiveProbe] đo 5 s/lần (một lệnh cho mọi ô); mất task ⇒ **giấu mặt vẽ** (khung cuối biến mất) rồi BÁO lên
 *     ([onGone]). L6 (owner 03/10, *"trả về transparent luôn, không cần giữ icon và yêu cầu mở app"*): thẻ "app đã đóng —
 *     chạm để mở lại" của H2 đã gỡ — ô đi theo luật hoàn ô `SlotRevertPlan` (trong suốt; ô LƯU widget ⇒ widget về — 04/10).
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
    /**
     * L6 · (a)/(b): app của ô đo là đã rời màn ảo (luồng chính) ⇒ màn chính áp luật hoàn ô; đối số hai = 2.93 · R3 app RA KHỎI ô
     * mà task còn ở display khác (`SlotLiveness.elsewhere`). Mặc định no-op (test).
     */
    private val onGone: (String, Boolean) -> Unit = { _, _ -> },
) : FrameLayout(context) {

    private val surface = SurfaceView(context)
    private var vd: VirtualDisplay? = null
    private var vdDisplayId: Int? = null
    private var dispW = 0                 // B4: cỡ VirtualDisplay (để map toạ độ ô→display; = cỡ surface nên map đồng nhất)
    private var dispH = 0
    /**
     * 2.91 · F2b — mật độ màn ảo đang mang (tạo · đổi cỡ thành công · nhận lại) và [keepDpi]: màn ảo NHẬN LẠI từ ô 7 giữ mật độ app
     * đã dựng theo cho MỌI lượt đổi cỡ về sau của host này ([SlotParkPlan.resizeDensity]); mật độ theo ô chỉ về khi app MỞ MỚI.
     */
    private var dispDpi = 0; private var keepDpi = false
    private var pkg: String? = null
    private var shell: ((String) -> String)? = null
    private var launched = false
    // Ô 7 (2.89-thử1, spec 287 §4.6d): tên màn ảo (khoá [SlotVdOwner] khi đỗ).
    private var vdName: String? = null

    /**
     * Đã nhả màn ảo chưa ⇒ mọi lời gọi [release] sau là no-op (một ô bị thay có thể gọi [release] rồi mới tháo
     * view). Khai ở đây, cùng các field khác, vì `init` đọc nó; luật idempotent xem KDoc [release].
     */
    // ⚠ `@Volatile` (soát OCR): GHI trên luồng vẽ ([release] / [onDetachedFromWindow]), ĐỌC trên luồng mở app của
    // [maybeLaunch]. Thiếu nó thì luồng nền có thể mãi thấy `false` và vẫn bắn `am start` cho một màn ảo đã nhả.
    @Volatile private var released = false

    /** Chỉ ĐỌC — cho lưới dựng-lại-ô của [WorkspaceView] ([SlotHostHeal]). Host đã nhả không bao giờ sống lại (H2). */
    val isReleased: Boolean get() = released

    /** 2.97 · R5 — gói host đang giữ (chưa nhả); `null` = đã nhả / chưa có app. Để [WorkspaceView] quyết ĐỖ khi dựng lại cả. */
    val heldPkg: String? get() = if (released) null else pkg

    /** H2: khoá theo dõi ở [SlotLiveProbe] — riêng cho từng chủ×ô để hai màn Kachi không đạp lên nhau. */
    private val probeKey = "$owner#$slot"

    /**
     * ═══ 1.69 · BỘ GOM CỬ CHỈ cho ĐƯỜNG LÙI ═════════════════════════════════════════════════════════════════
     *
     * Một bộ cho MỘT ô (mỗi ô một màn ảo, mỗi ô một chuỗi DOWN…UP riêng). Hai ngưỡng lấy từ [ViewConfiguration]
     * **tại chỗ gọi** — `:core` không được biết mật độ màn hình của máy nào (CLAUDE.md §7).
     */
    private val gesture = GestureFallback(
        touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop,
        longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong(),
    )

    private companion object { const val TAG = "VdAppHost" }

    /** H2·2 · L6: nhịp đo đã báo app rời màn ảo ([onAppClosed]) — chỉ để KHÔNG báo hai lần / rào lượt mở dở. */
    private var dead = false

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
                    // ô 7 · C · 2.91 F2: app ĐỖ ⇒ lấy ra + gắn màn ảo đỗ (không tạo mới); khác cỡ ô ⇒ đổi cỡ theo ô qua đường DUY NHẤT [resize]
                    val c = ParkedApps.claim(surface, pkg, owner, slot, w, ht)
                    if (c != null) { unpark(c.parked); if (c.resize) resize(w, ht); return }
                    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    // 8 = OWN_CONTENT_ONLY (chỉ hiện app đặt lên VD, KHÔNG mirror display 0 → hết "gương đệ quy")
                    // 256 = DESTROY_CONTENT_ON_REMOVAL (dọn khi gỡ). Shell mở app lên VD vẫn được.
                    // ⚠⚠ KHÔNG dùng FLAG_PRESENTATION (2) để chống xoay dọc: [ĐO 2026-09-15 emulator+owner] màn ảo ô
                    // mang PRESENTATION khiến app "tự relaunch" (như YouTube: Shell→WatchWhileActivity) coi màn ô là
                    // ĐÍCH KHÔNG HỢP LỆ ⇒ nhảy về display 0, để lại ô **ĐEN THUI** — tệ hơn lỗi dọc ban đầu. Chống
                    // xoay nay làm bằng `wm set-fix-to-user-rotation` qua shell SAU khi tạo VD (xem [maybeLaunch]) —
                    // khoá hướng mà KHÔNG đổi cờ hiển thị nên không đổi đường composite (app vẫn vẽ vào ô như cũ).
                    // 2.98 · R4: tên ỔN ĐỊNH theo ô (không dấu thời gian) ⇒ khoá xoay bên dưới dùng lại ĐÚNG một mục bền trong
                    // `/data/system/display_settings.xml` thay vì để lại một mục mới mỗi lần dựng ô — KDoc [SlotVdName].
                    val name = SlotVdName.pick(slot, SlotVdOwner.liveNames())
                    val dpi = slotDensity(w, ht)   // đường golden (mở mới): mật độ theo ô như trước 2.91 — F2b chỉ GHI LẠI nó
                    // lint WrongConstant: 256 = VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL là hằng @hide của
                    // DisplayManager (8 = OWN_CONTENT_ONLY là public). Cờ ẩn dùng CỐ Ý — đường cast đang chạy ngoài
                    // hiện trường (CLAUDE.md §6): KHÔNG đổi giá trị, chỉ tắt cảnh báo tại đúng một dòng.
                    @SuppressLint("WrongConstant")
                    val created = dm.createVirtualDisplay(name, w, ht, dpi, h.surface, 8 or 256)
                    vd = created
                    dispW = w; dispH = ht; vdName = name; dispDpi = dpi   // B4: VD cỡ = surface cỡ → map toạ độ chạm đồng nhất
                    // B2b: đăng ký display của VD (thuộc LAUNCHER) TRƯỚC maybeLaunch — nếu không, cổng ownership
                    // của launcherSeam sẽ REJECT lệnh `am start --display <vdId>` (fail-safe deny display không chủ).
                    created?.display?.displayId?.let { id ->
                        vdDisplayId = id
                        com.byd.clusternav.KachiReadyLog.tile(id, slot)   // READY-AT-HOME §4.10 — mốc "ô có màn ảo"
                        runCatching { registerVd(id) }
                        // H2·1: giao tay cầm cho chủ sở hữu theo Ô — màn ảo CŨ của chính ô này (kể cả do một màn
                        // Kachi đời trước tạo và chưa kịp chết) được giải phóng NGAY tại đây.
                        SlotVdOwner.adopt(owner, slot, name, VdLease(created, id, unregisterVd))
                    }
                    maybeLaunch()
                } else {
                    v.surface = h.surface
                    resize(w, ht)
                }
            }
            override fun surfaceDestroyed(h: SurfaceHolder) { vd?.surface = null }
        })
    }

    /**
     * ═══ V3 · R15 — ĐỔI CỠ màn ảo của ô, **một đường duy nhất** — bệnh đã chữa + vì sao là một hàm có tên: KDoc [VdAppHostResize]
     * (phần thi hành tách sang đó, 2.91 · F2b). **Trùng cỡ ⇒ không làm gì**: gọi thừa vô hại; `VirtualDisplay.resize` thì KHÔNG
     * rẻ (đẩy một lượt đổi cấu hình vào app đang chạy trong ô). 2.91 · F2b: màn ảo nhận lại từ ô 7 ([keepDpi]) chỉ đổi CỠ, GIỮ mật độ.
     */
    fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (w == dispW && h == dispH) return
        val v = vd ?: return
        val keep = SlotParkPlan.resizeDensity(keepDpi, dispDpi)
        if (keep != null) Log.i(TAG, "[slot-density] keep $keep on unpark ô $slot ${dispW}x$dispH → ${w}x$h")
        VdAppHostResize.apply(slot, v, dispW, dispH, w, h) { keep ?: slotDensity(w, h) }?.let { dispDpi = it }
        // B4: giữ cỡ VD đồng bộ để map toạ độ chạm đúng sau resize.
        dispW = w; dispH = h
    }

    /** R5 · ĐÒN BẨY MẬT ĐỘ theo cạnh ngắn của ô (KDoc + dòng nhật ký `[slot-density]`: [VdAppHostResize.slotDensity]). */
    private fun slotDensity(w: Int, h: Int): Int = VdAppHostResize.slotDensity(slot, w, h, densityDpi)

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
        Thread { launchInto(displayId, p, sh) }.start()
    }

    /**
     * Thân MỞ APP vào màn ảo của ô — một bản, hai lối gọi: [maybeLaunch] (ô mới) và [swapApp] (đặt TẠM, giữ màn ảo).
     * Chuỗi lệnh giữ NGUYÊN byte so với trước khi tách (spec shortcuts-autostart A2, CLAUDE.md §6): phân giải → khoá
     * xoay → `am force-stop` app MỚI → ngủ 1 s → `am start --display` → thử lại một lần sau 2 s → bắt đầu đo ô sống.
     * Chặn (ngủ) ⇒ CHỈ gọi trên luồng nền.
     */
    private fun launchInto(displayId: Int, p: String, sh: (String) -> String) {
        // 2.98 · R7 — app này đang được quản dạng freeform ĐÚNG khung ô (đã tự thoát màn ảo) ⇒ không kéo nó về màn ảo, ô để trống dưới cửa sổ.
        if (SlotEscape.claimsLive(context, slot, p, sh)) { post { if (!released && pkg == p) { dead = true; surface.visibility = GONE } }; return }
        // TẤT CẢ lệnh dadb (blocking) chạy TRONG thread nền — KHÔNG gọi trên UI thread (chặn dựng SurfaceView → ô đen).
        val comp = FreeformLaunch.resolveComponent(p, sh) ?: "$p/.MainActivity"
        // B1: built by the pure FreeformLaunch builder (byte-locked by LauncherCommandGoldenTest) instead of
        // an inline string. displayId = this host's OWN VirtualDisplay (a private secondary display for the
        // slot), NOT the cluster. Touch/force-stop lifecycle stays inline (moves to the input daemon in B4).
        val cmd = FreeformLaunch.launchOnDisplayCmd(comp, displayId, windowingMode = 1)
        // ⚠ CHỐNG XOAY DỌC (bug "YouTube co vào giữa", owner 2026-09-15) — khoá màn ảo ô về hướng NGANG gốc
        // TRƯỚC khi mở app, để app đòi portrait cũng không xoay được ô. Làm bằng `wm` qua shell (KHÔNG đổi cờ
        // hiển thị VD ⇒ app vẫn vẽ vào ô, tránh lỗi ĐEN của FLAG_PRESENTATION). Best-effort: ROM thiếu lệnh
        // (`-d` per-display có từ Android 10; nếu vắng thì trả chuỗi lỗi, không ném). Đặt cả hai cho chắc:
        // `set-user-rotation lock 0` ghim giá trị, `set-fix-to-user-rotation enabled` để mọi app không xoay ô.
        runCatching {
            sh("wm set-user-rotation lock -d $displayId 0")           // ghim hướng ô = 0 (ngang gốc)
            sh("wm set-fix-to-user-rotation -d $displayId enabled")   // mọi app KHÔNG xoay được ô
        }
        // ⚠ [SOÁT OCR] Ô có thể đã bị tháo TRONG lúc luồng này chạy (đổi bố cục · đổi hồ sơ · màn huỷ):
        // `release()` đặt `released = true` và nhả `VirtualDisplay`, nhưng KHÔNG cắt được luồng này. Không
        // kiểm ở đây thì `am force-stop` + `am start --display <id>` vẫn bắn cho một màn ảo KHÔNG CÒN TỒN
        // TẠI — tức giết app của người dùng rồi mở lại nó ở một nơi không ai nhìn thấy. Kiểm ở ĐÚNG hai mốc:
        // trước khi giết app, và sau giấc ngủ 1 giây (cửa sổ rộng nhất).
        if (released) return
        // R1.8 (spec shortcuts-autostart §4.2.6, T-M6 [ĐO máy ảo 02/10]): app do CHÍNH Kachi đẩy ra sau màn nhà (dấu bền) ⇒ K8.
        // A4 (spec 289 §A4): app ĐANG sống (task ở chỗ khác ⇒ K8 · chỉ tiến trình ⇒ [cmd] không giết) ⇒ nhạc sống. Nguội ⇒ golden y byte.
        if (SlotReturnRun.bringBackMarked(context, displayId, p, sh) || SlotReturnRun.openLive(context, displayId, p, cmd, sh)) {
            runCatching { inputClient?.ensureStarted() }   // như đường golden: hâm nóng daemon bơm chạm (B4)
            post { if (!released && pkg == p) SlotLiveProbe.watch(probeKey, p, displayId, sh) { onAppClosed(it) } }
            return
        }
        if (released) return   // A4: lượt đọc / K8 vừa rồi tốn thời gian — ô có thể đã bị tháo, KHÔNG giết app cho màn ảo đã nhả
        sh("am force-stop $p")
        Thread.sleep(1000)     // đợi force-stop XONG hẳn → am start mở task MỚI trên VD, không tái dùng task fullscreen ở display 0 (bug gmail nhảy fullscreen)
        if (released) return
        sh(cmd)                // mở ĐÚNG 1 lần trên VD — KHÔNG relaunch/di lần 2 (bỏ vòng retry gây nháy + làm app ô khác nhảy)
        runCatching { inputClient?.ensureStarted() }   // B4: hâm nóng daemon bơm chạm (lifecycle qua queue) — chạm sau mượt; không block
        // #12 (owner 2026-09-21 · [ĐO xe] YouTube ô ĐEN sau NỔ MÁY): trên cold-boot, `am start --display <vd>`
        // đôi khi TRƯỢT (system chưa sẵn / VD vừa dựng) và KHÔNG có gì thử lại ⇒ ô đen, app KHÔNG chạy. Kiểm
        // MỘT lần sau 2s: app chưa có tiến trình ⇒ relaunch ĐÚNG MỘT lần nữa. An toàn — chỉ bắn khi app THẬT
        // SỰ chưa lên (pidof rỗng), nên đường thường (mở được ngay lần đầu) KHÔNG bị nháy. Guard `released`.
        //
        // ⚠ [SOÁT 2026-09-21 · P2] Giấc ngủ này đứng SAU lượt hâm nóng daemon chạm, không trước: nó chạy ở MỌI
        // lần mở app (không chỉ cold-boot), nên đặt trước là dời việc hâm nóng đi 2 giây trên đường thường —
        // một cái giá trả cho mọi người để chữa một ca chỉ xảy ra lúc nổ máy. Lượt ĐO ô sống (`SlotLiveProbe`)
        // thì cố ý vẫn nằm sau: nó chỉ được bắt đầu đếm khi lượt thử-mở-lại đã xong.
        Thread.sleep(2000)
        if (!released && !FreeformLaunch.appRunning(p, sh)) { Log.i(TAG, "ô $slot: app $p chưa lên sau boot — thử mở lại 1 lần"); sh(cmd) }
        // H2·2: từ đây mới bắt đầu ĐO "còn task trên màn ảo không". [SlotLiveness] không kết luận chết trước
        // khi thấy sống ít nhất một nhịp ⇒ ca "app chưa bao giờ vào được ô" (H1/Waze) KHÔNG bị nhận nhầm là chết (2.93 · R3: còn task
        // ở display khác ⇒ kết luận "ra khỏi ô, vẫn mở" — `SlotLiveness.elsewhere`).
        post { if (!released && pkg == p) SlotLiveProbe.watch(probeKey, p, displayId, sh) { onAppClosed(it) } }
    }

    /**
     * ═══ ĐẶT TẠM — đổi app của ô TẠI CHỖ, giữ màn ảo (spec shortcuts-autostart R0.1 · §4.4.5) ═══════════════════════
     *
     * Đường hôm nay (nhả ô + `am force-stop` app cũ) GIỮ cho mọi thao tác LƯU (R1.7). Đây là đường của lượt đặt TẠM
     * (lối tắt · giọng nói): app cũ A KHÔNG bị giết — B mở vào CÙNG màn ảo bằng đúng [launchInto] (B lên đỉnh, A nằm
     * dưới: O1), rồi [onLaunched] (`vd`, A, B) giao cho `BehindHomeRunner.evict` đẩy A ra sau màn nhà.
     *
     * `false` = host không đổi được tại chỗ (chưa có màn ảo / chưa từng mở / đã nhả) ⇒ bên gọi dựng lại ô như cũ.
     * Cùng gói ⇒ `true`, không làm gì (lượt trả ô về app đang hiện — [adoptShown]).
     */
    fun swapApp(newPkg: String, onLaunched: (Int, String, String) -> Unit): Boolean {
        val v = vd ?: return false
        val sh = shell ?: return false
        val old = pkg ?: return false
        if (released || !launched) return false
        if (old == newPkg) return true
        SlotLiveProbe.unwatch(probeKey)                 // thôi đo A TRƯỚC lệnh: A rời đỉnh không phải "app đã đóng"
        dead = false
        full.reset()                                    // A đang toàn màn (dòng 9) không còn là app của ô này
        surface.visibility = VISIBLE
        gesture.reset()
        pkg = newPkg
        val displayId = v.display.displayId
        Thread {
            launchInto(displayId, newPkg, sh)
            if (!released && pkg == newPkg) onLaunched(displayId, old, newPkg)
        }.start()
        return true
    }

    /**
     * B không vào được ô (app tự rơi về display 0) mà A vẫn ở đỉnh màn ảo ⇒ host nhận lại A làm app của ô, KHÔNG lệnh
     * nào (A chưa từng rời màn ảo). Gọi trên luồng chính, trước khi lớp tạm trả ô về A.
     */
    fun adoptShown(shownPkg: String) {
        val v = vd ?: return
        val sh = shell ?: return
        if (released) return
        pkg = shownPkg
        SlotLiveProbe.watch(probeKey, shownPkg, v.display.displayId, sh) { onAppClosed(it) }
    }

    /** F1 dòng 9 — Ô ⇄ TOÀN MÀN (K7 ra, K8 về; T-M2/T-M6 [ĐO]): trạng thái + thẻ + lệnh ở [SlotFullscreen]. */
    private val full = SlotFullscreen(this, surface, probeKey, { p -> !released && pkg == p }, ::onAppClosed, ::reopen) { returnFromFull() }

    /** Kéo app của ô ra toàn màn display 0 (K7 qua rào). `false` = ô chưa sẵn, 0 lệnh. [done] (luồng chính): đã tách được? */
    fun detachToFull(sig: String?, homeComps: List<String>, done: (Boolean) -> Unit): Boolean {
        val id = vd?.display?.displayId ?: return false
        val sh = shell ?: return false
        val p = pkg ?: return false
        if (released || !launched) return false
        full.detach(id, p, sh, sig, homeComps, done)
        return true
    }

    /** Màn nhà hiện lại / chạm thẻ ⇒ K8 đưa app đang toàn màn về ô (không về được ⇒ golden; app đã đóng ⇒ luật hoàn ô). */
    fun returnFromFull() {
        val id = vd?.display?.displayId ?: return
        val sh = shell ?: return
        val p = pkg ?: return
        if (!released) full.bringBack(id, p, sh)
    }

    /**
     * A5 — ô này làm chỗ dàn dựng BEHIND-HOME (R0.3) được không: có màn ảo, đã mở app, chưa nhả; `alive` = [SlotLiveProbe]
     * đã đo thấy app sống. `null` = không có màn ảo / chưa mở / đã nhả. Chọn ô là việc của `BehindHomePlan.stagingSlot`.
     */
    fun stage(): BehindHomePlan.Stage? {
        val id = vd?.display?.displayId ?: return null
        val p = pkg ?: return null
        if (released || !launched) return null
        return BehindHomePlan.Stage(slot, id, p, width.toLong() * height, SlotLiveProbe.seenAlive(probeKey))
    }

    /** Ô 7 · A/B (2.89-thử1, §4.6d) — ĐỖ: màn ảo NGUYÊN, mặt vẽ → bề mặt ẩn ([ParkedApps.park]); host thôi giữ như đã nhả mà
     *  KHÔNG `force-stop`, KHÔNG nhả màn ảo, 0 lệnh. `false` = không đỗ được ([SlotParkPlan.parkable]) ⇒ host giữ nguyên. */
    fun park(protect: Set<String> = emptySet()): Boolean {
        val v = vd; val id = vdDisplayId; val name = vdName; val p = pkg
        if (v == null || id == null || name == null || p == null) return false
        if (!SlotParkPlan.parkable(released, launched, true, p, dead, full.isDetached, SlotLiveProbe.watching(probeKey))) return false
        if (!ParkedApps.park(p, name, VdLease(v, id, unregisterVd), dispW, dispH, dispDpi, protect)) return false
        SlotLiveProbe.unwatch(probeKey); gesture.reset(); full.reset()
        released = true; vd = null; vdDisplayId = null; pkg = null; launched = false
        surface.visibility = INVISIBLE   // khung cuối không đứng lại trên ô trong lúc chờ lượt render dựng lại ô
        return true
    }

    /**
     * Ô 7 · C — màn ảo đỗ [p] ĐÃ gắn ([ParkedApps.claim]): KHÔNG `force-stop`/`am start` (đổi cỡ, nếu cần, do bên gọi qua [resize] —
     * 2.91 · F2b GIỮ mật độ [p] mang, [keepDpi]); app đã rời nó ⇒ mở như thường.
     */
    private fun unpark(p: ParkedApps.Parked) {
        vd = p.lease.vd; vdDisplayId = p.lease.displayId; vdName = p.name; dispW = p.width; dispH = p.height; launched = true
        dispDpi = p.densityDpi; keepDpi = true
        shell?.let { sh -> SlotLiveProbe.watch(probeKey, p.pkg, p.lease.displayId, sh, onMissing = ::reopen) { onAppClosed(it) } }
        inputClient?.let { c -> Thread { runCatching { c.ensureStarted() } }.start() }   // như đường golden (B4)
    }

    // ── H2·2 · KÊNH IM LẶNG PHẢI NÓI ────────────────────────────────────────────────────────────────────────

    /**
     * App trong ô đã rời màn ảo (đo ở [SlotLiveProbe] · hoặc lượt về-ô của [SlotFullscreen] thấy GONE). Hai việc, đúng
     * thứ tự:
     *  1. **Giấu mặt vẽ** — `SurfaceView` bị GONE ⇒ khung hình cuối (đóng băng) biến mất NGAY. KHÔNG giải phóng màn ảo ở
     *     đây: nhả là việc của lượt render sau luật hoàn ô (bất biến một-màn-ảo-mỗi-ô giữ nguyên).
     *  2. **Báo lên** ([onGone]) — L6 (owner 03/10): KHÔNG còn thẻ icon + *"chạm để mở lại"*; màn chính áp `SlotRevertPlan`
     *     (`APP_DIED`): ô LƯU widget ⇒ widget về · mọi ca khác ⇒ ô trong suốt (owner 04/10: không mở lại app LƯU khác).
     */
    private fun onAppClosed(elsewhere: Boolean = false) {
        if (released || dead) return
        dead = true
        surface.visibility = GONE
        pkg?.let { onGone(it, elsewhere) }   // 2.93 · R3: [elsewhere] = app ra khỏi ô, task còn ở display khác (không phải đã đóng)
    }

    /** Mở lại app trên đúng màn ảo của ô (không dựng lại view, không tạo VD mới) — lối tắt R-SC2 · lượt về-ô hỏng. */
    private fun reopen() {
        dead = false
        surface.visibility = VISIBLE
        launched = false
        maybeLaunch()
    }

    /** FIX286 · R-SC2 — app đo là đã đóng ⇒ mở lại vào ô ([reopen]); `false` = 0 lệnh (KDoc `KachiHomeSlots.reviveInSlot`). */
    fun reviveInSlot(expect: String): Boolean {
        val busy = !dead && !full.isDetached && !SlotLiveProbe.watching(probeKey)   // lượt mở đang chạy, chưa đo
        if (released || !launched || pkg != expect || busy) return false
        SlotLiveProbe.unwatch(probeKey); full.reset(); reopen(); return true   // nhịp đo cũ thôi TRƯỚC: không dựng thẻ giữa lượt mở
    }

    /**
     * L6 — app [expect] đã RỜI ô theo luật hoàn ô (chết / bị *tắt*: stack đã gỡ): host thôi giữ nó TRƯỚC lượt render nhả
     * ô ⇒ [release] KHÔNG `am force-stop` (app có thể còn dịch vụ / task ở chỗ khác — tắt là đúng task trên màn ảo, không
     * phải cả gói), không mở lại, không đo nữa. Gói khác [expect] (ô đã đổi app) ⇒ không làm gì. Luồng chính.
     */
    fun relinquish(expect: String) {
        if (pkg != expect) return
        SlotLiveProbe.unwatch(probeKey); full.reset()
        pkg = null; launched = false
    }

    /** L6 — host chưa nhả và đang giữ [p] (kể cả khi chưa mở xong vào màn ảo — [stage] khi đó còn `null`). Luồng chính. */
    fun holds(p: String): Boolean = !released && pkg == p

    /** 2.98 · R7 — giữ [p] mà màn ảo đã trống (app đã thoát ô / đang được quản dạng freeform), không phải lượt mở đang chạy. */
    fun holdsEmpty(p: String): Boolean = holds(p) && dead

    /** A3 · SLOT-CLOSE-SETTLE — lệnh *tắt* [expect] đã gửi ⇒ giấu mặt vẽ NGAY (không khung đứng); gỡ không xong ⇒ hiện lại. Luồng chính. */
    fun closing(expect: String, on: Boolean) { if (!released && !dead && pkg == expect) surface.visibility = if (on) INVISIBLE else VISIBLE }

    /**
     * ═══ ĐƯA CHẠM VÀO MÀN ẢO CỦA Ô — hai đường, và đường lùi nay hiểu CỬ CHỈ (1.69) ══════════════════════════
     *
     * **Đường CHÍNH** = [InputDaemonClient] thường trú trên socket riêng (`injectInputEvent`: mượt, không spawn
     * tiến trình mỗi sự kiện, không đi qua hàng đợi lệnh cửa sổ).
     *
     * **Đường LÙI** (daemon chưa/không lên) = [GestureFallback] gom DOWN…MOVE…UP rồi tại UP bắn **ĐÚNG MỘT**
     * lệnh `input -d …` cho cả cử chỉ.
     *
     * ## Vì sao viết lại — [ĐO xe 2026-09-16] `docs/diagnostics/oncar-trace-2026-09-16b/README.md` §9.1
     * Trên xe daemon **không lên lần nào**, nên đường lùi KHÔNG phải nhánh hiếm — nó là nhánh **duy nhất** đang
     * chạy. Mà bản trước 1.69 bắn `tap` ở **cả** DOWN **lẫn** UP (⇒ hai cú tap cho một cú chạm: *"tap không
     * chính xác"*), dùng toạ độ **thô của view** thay vì toạ độ đã map (⇒ lệch thêm khi ô ≠ cỡ màn ảo), và
     * KHÔNG có nhánh nào cho MOVE (⇒ *"không lướt để scroll được"*, `input swipe` vào ô chỉ thành một cú tap).
     *
     * Ba thứ đó sửa ở đúng ba chỗ: một lệnh mỗi cử chỉ · toạ độ `dx,dy` đã map cho CẢ hai đường · `swipe` khi
     * quãng vượt touch-slop.
     */
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val v = vd ?: return false
        val sh = shell ?: return false
        val displayId = v.display.displayId
        // View→display map. The VD is created at the surface size, so this is the identity today; it only scales
        // if the display size ever diverges from the view size. ⚠ CẢ HAI đường dùng chung toạ độ đã map — đường
        // lùi cũ dùng `e.x/e.y` thô, và đó là nửa thứ hai của triệu chứng "tap lệch".
        val m = SlotTouchMapper.toDisplay((e.x - surface.left).toInt(), (e.y - surface.top).toInt(), surface.width, surface.height, dispW, dispH)
        val dx = m[0]; val dy = m[1]
        val action = e.actionMasked
        val routed = when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP,
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL ->
                runCatching { inputClient?.sendTouch(displayId, action, dx, dy) ?: false }.getOrDefault(false)
            // Ngón thứ hai: daemon không nhận (khung dây chỉ mang một điểm), đường lùi cũng bỏ qua.
            else -> false
        }
        if (!TouchRouter.shouldFallback(routed)) {
            // Daemon vừa nhận sự kiện này ⇒ bỏ cử chỉ đang gom ở đường lùi, nếu không một cử chỉ nửa-daemon
            // nửa-shell sẽ bắn thêm một lệnh THỨ HAI cho cùng một cú chạm.
            gesture.reset()
            return true
        }
        val cmd = gesture.feed(action, displayId, dx, dy, e.eventTime, e.getPointerId(0))
        if (cmd != null) runCatching { VdTouchExec.TOUCH_FALLBACK.execute { runCatching { sh(cmd) } } }
        return true
    }

    // ── H2·1 · GIẢI PHÓNG MÀN ẢO ────────────────────────────────────────────────────────────────────────────

    /**
     * Nhả màn ảo của ô này + dừng app (CHỈ khi chính host này đã mở nó — R-A2) + thôi đo. **Idempotent** —
     * [WorkspaceView] gọi tường minh TRƯỚC khi tháo
     * view (đường chắc chắn), `onDetachedFromWindow` gọi lại (đường cũ, cho mọi ca tháo view ngoài tầm nhìn).
     *
     * Vì sao không dựa mỗi vào `onDetachedFromWindow`: [ĐO] 2026-09-14 cho thấy màn Kachi mang cờ "đang kết thúc"
     * vẫn giữ cây view (và màn ảo) trong khi màn Kachi mới đã dựng ô của nó ⇒ 4 màn ảo cho 2 ô. Xem [SlotVdLedger].
     */
    fun release() {
        if (released) return
        released = true
        // 2.97 · R5 (spec `kachi-297-plan.html`) — nhả ô KHÔNG `am force-stop` app nào nữa. [ĐO log SL6 08/10 15:16:37] đổi hồ sơ ⇒
        // lệnh dừng cả gói ở đây giết YouTube đang hát (và giết được cả app dẫn đường đang chiếu cụm/HUD nếu nó nằm trong ô cũ).
        // Màn ảo ô mang cờ 256 = DESTROY_CONTENT_ON_REMOVAL (xem [maybeLaunch]) ⇒ nhả màn ảo là hệ thống tự đóng cửa sổ app trên
        // nó ([ĐO cùng log 37.223] `finishAllActivitiesLocked … immediately`); tiến trình + dịch vụ nền sống như mọi app Android.
        // Ô đang bị nhả giữa một cử chỉ ⇒ bỏ luôn, đừng bắn lệnh chạm cho một màn ảo sắp biến mất.
        gesture.reset()
        SlotLiveProbe.unwatch(probeKey)
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
