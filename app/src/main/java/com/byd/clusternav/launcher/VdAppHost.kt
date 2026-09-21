package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.system.inputd.GestureFallback
import com.byd.clusternav.system.inputd.InputDaemonClient
import com.byd.clusternav.system.inputd.SlotTouchMapper
import com.byd.clusternav.system.inputd.TouchRouter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
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
    // ⚠ `@Volatile` (soát OCR): GHI trên luồng vẽ ([release] / [onDetachedFromWindow]), ĐỌC trên luồng mở app của
    // [maybeLaunch]. Thiếu nó thì luồng nền có thể mãi thấy `false` và vẫn bắn `am start` cho một màn ảo đã nhả.
    @Volatile private var released = false

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

    private companion object {
        const val TAG = "VdAppHost"

        /**
         * ⚠ [SOÁT OCR] MỘT luồng dùng chung cho đường LÙI của chạm — trước 1.69 mỗi `ACTION_DOWN`/`ACTION_UP`
         * dựng **một `Thread` mới** (hai luồng mỗi cú chạm), mỗi luồng chạy một lệnh dadb CHẶN. Cuộn một danh
         * sách trong ô là hàng chục luồng sinh-và-chết trong vài giây, tất cả xếp hàng sau CÙNG một chủ
         * `ShellTransport` — thêm luồng không làm nhanh hơn, chỉ làm mọi bản chụp luồng trên xe khó đọc.
         *
         * Hàng đợi **có trần** + [ThreadPoolExecutor.DiscardPolicy]: khi kênh shell nghẽn, bỏ cú chạm MỚI là
         * đúng — giữ nó lại chỉ để thi hành muộn vài giây thì app trong ô nhận một cú chạm ở chỗ người dùng đã
         * rời mắt từ lâu. Điều tuyệt đối KHÔNG được làm là chặn luồng vẽ (nên không có `CallerRunsPolicy`).
         */
        val TOUCH_FALLBACK: ThreadPoolExecutor = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
            { r -> Thread(r, "kachi-slot-tap").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardPolicy(),
        )
    }

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
                    // 256 = DESTROY_CONTENT_ON_REMOVAL (dọn khi gỡ). Shell mở app lên VD vẫn được.
                    // ⚠⚠ KHÔNG dùng FLAG_PRESENTATION (2) để chống xoay dọc: [ĐO 2026-09-15 emulator+owner] màn ảo ô
                    // mang PRESENTATION khiến app "tự relaunch" (như YouTube: Shell→WatchWhileActivity) coi màn ô là
                    // ĐÍCH KHÔNG HỢP LỆ ⇒ nhảy về display 0, để lại ô **ĐEN THUI** — tệ hơn lỗi dọc ban đầu. Chống
                    // xoay nay làm bằng `wm set-fix-to-user-rotation` qua shell SAU khi tạo VD (xem [maybeLaunch]) —
                    // khoá hướng mà KHÔNG đổi cờ hiển thị nên không đổi đường composite (app vẫn vẽ vào ô như cũ).
                    val name = "kachi-slot-$slot-${System.currentTimeMillis()}"
                    val created = dm.createVirtualDisplay(name, w, ht, slotDensity(w, ht), h.surface, 8 or 256)
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
                    resize(w, ht)
                }
            }
            override fun surfaceDestroyed(h: SurfaceHolder) { vd?.surface = null }
        })
    }

    /**
     * ═══ V3 · R15 — ĐỔI CỠ màn ảo của ô, **một đường duy nhất** ═══════════════════════════════════════════
     *
     * ## Bệnh nó chữa — [ĐO xe 2026-09-16] `carlog-0916/slot-insets-bug.png`
     * Lúc khởi động, khi thanh trên/dưới của ROM còn hiện, ô được đo theo khung **đã bị co**: màn ảo dựng đúng
     * cỡ hụt ấy, app trong ô (YouTube) bố trí theo nó, và khi thanh ẩn đi thì ô rộng ra nhưng ảnh trong ô vẫn
     * giữ khung cũ ⇒ **dải xám ở trên**. Bấm Home lần nữa (lúc thanh đã ẩn) thì đúng.
     *
     * ## Vì sao là một hàm có TÊN, không phải ba dòng trong `surfaceChanged`
     * Từ 1.66 có **hai** thời điểm biết được cỡ ô đã đổi: `surfaceChanged` (đường cũ) và lượt bố trí lại do
     * inset đổi ([WorkspaceView] — đường mới). Hai chỗ tự viết ba dòng giống nhau là bản sao thứ hai của một
     * phép tính có **đòn bẩy mật độ** bên trong; một bên quên [slotDensity] là ô đó hiện chữ to gấp rưỡi ô bên
     * cạnh mà không ai hiểu vì sao.
     *
     * **Trùng cỡ ⇒ không làm gì**: đây là điều khiến đường mới không phải "cơ chế thứ hai" mà chỉ là một cái
     * kích thêm — gọi thừa bao nhiêu lần cũng vô hại, và `VirtualDisplay.resize` thì KHÔNG rẻ (nó đẩy một lượt
     * đổi cấu hình vào app đang chạy trong ô).
     */
    fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (w == dispW && h == dispH) return
        val v = vd ?: return
        Log.i(TAG, "[slot-resize] ô $slot ${dispW}x$dispH → ${w}x$h")
        runCatching { v.resize(w, h, slotDensity(w, h)) }
            .onFailure { Log.w(TAG, "[slot-resize] ô $slot hỏng", it) }
        // B4: giữ cỡ VD đồng bộ để map toạ độ chạm đúng sau resize.
        dispW = w; dispH = h
    }

    /**
     * R5 · ĐÒN BẨY MẬT ĐỘ (generic, spec §4.4). Mật độ thật đặt cho màn ảo của ô = [SlotDensity.forTablet] theo
     * **cạnh ngắn** của ô, để `smallestScreenWidthDp ≥ 600` ⇒ app có layout tablet không đòi portrait ⇒ không rơi
     * size-compat (bug "YouTube co dải dọc giữa ô khi play", owner 2026-09-15). Truyền NGAY lúc
     * `createVirtualDisplay`/`resize` (không qua `wm density` shell ⇒ không ghi `display_settings.xml`, không cần
     * đường trả lại — CLAUDE §5). Không hỏi tên gói, không hỏi to/bé; ô đã ≥600dp thì giữ [densityDpi] nguyên.
     * Kỳ vọng: app đầy khung; video 16:9 chỉ full-pixel ở fullscreen player. Log một dòng để đo trên xe.
     */
    private fun slotDensity(w: Int, h: Int): Int {
        val short = minOf(w, h)
        val dpi = SlotDensity.forTablet(short, densityDpi)
        Log.i(
            TAG,
            "[slot-density] slot=$slot short=$short dpi=$densityDpi→$dpi" +
                " (%.1fdp→%.1fdp)".format(SlotDensity.dpOf(short, densityDpi), SlotDensity.dpOf(short, dpi)),
        )
        return dpi
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
            if (released) return@Thread
            sh("am force-stop $p")
            Thread.sleep(1000)     // đợi force-stop XONG hẳn → am start mở task MỚI trên VD, không tái dùng task fullscreen ở display 0 (bug gmail nhảy fullscreen)
            if (released) return@Thread
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
            if (!released && !appRunning(p, sh)) { Log.i(TAG, "ô $slot: app $p chưa lên sau boot — thử mở lại 1 lần"); sh(cmd) }
            // H2·2: từ đây mới bắt đầu ĐO "còn task trên màn ảo không". [SlotLiveness] không kết luận chết trước
            // khi thấy sống ít nhất một nhịp ⇒ ca "app chưa bao giờ vào được ô" (H1/Waze) KHÔNG bị nhận nhầm.
            post { if (!released) SlotLiveProbe.watch(probeKey, p, displayId, sh) { onAppClosed() } }
        }.start()
    }

    /** App đang có tiến trình chưa — `pidof` rỗng ⇒ chưa lên (dùng cho retry mở-lại trên cold boot, #12). */
    private fun appRunning(pkg: String, sh: (String) -> String): Boolean =
        runCatching { sh("pidof $pkg").trim().isNotEmpty() }.getOrDefault(false)

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
        val m = SlotTouchMapper.toDisplay(e.x.toInt(), e.y.toInt(), width, height, dispW, dispH)
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
        if (cmd != null) runCatching { TOUCH_FALLBACK.execute { runCatching { sh(cmd) } } }
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
        // Ô đang bị nhả giữa một cử chỉ ⇒ bỏ luôn, đừng bắn lệnh chạm cho một màn ảo sắp biến mất.
        gesture.reset()
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
