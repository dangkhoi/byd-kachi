package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.TextureView
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 3.01 R25 — WIDGET XE 3D (owner 13/10: *"làm 1 cái widget to, cho xe 3D chiếu nghiêng, hiện hết cả cửa và đèn,
 * cho xoay tới lui luôn"*) ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Mô hình 3D THẬT của xe (lưới AVM của BYD — [Car3dModelSource]), ¾ trước-trái ([Car3dModel.yawDeg]); vuốt ngang xoay quanh
 * trục đứng (có giới hạn + quán tính — [Car3dOrbit]); điểm chạm tại kính/cửa/cốp/đèn theo bảng ràng buộc ([Car3dCatalog]);
 * hai nút trên đầu = mở hết · đóng hết kính (icon chân bảng Kính 2D). Cửa mở ⇒ hiện bản cánh cửa mở; đèn sáng ⇒ bản sáng.
 *
 * ## Chi phí (memory perf-every-fix)
 *  • Chỉ vẽ khi CÓ THAY ĐỔI (nạp xong, đổi cỡ, đang vuốt/quán tính, biến thể cửa/đèn đổi, đổi ngày/đêm) — rảnh = 0 khung;
 *    trần [FRAME_NS] (~24 khung/giây) khi xoay. Số xe đổi chỉ vẽ lại LỚP CHẤM (View thường), không vẽ lại Filament.
 *  • Chỉ sống khi gắn cửa sổ; rời cửa sổ ⇒ trả hết GPU ([Car3dRenderer.destroy]). Nạp/đổi định dạng mô hình trên luồng nền.
 *  • KHÔNG BAO GIỜ lùi bảng 2D (3.1.8, owner 15/10): không vẽ được ⇒ khung trống, vĩnh viễn ⇒ dòng "không khả dụng", thoáng qua ⇒ chờ/thử lại ([Car3dHold]).
 *
 * ## Ghi — không đường thứ hai
 * Mỗi việc là một ô nút THẬT ([MatrixKit.tile] → [ControlTileFactory], cùng cổng chạm `TileSafetyGate` cho cốp) gắn `GONE`;
 * điểm chạm/nút chỉ `set`/`performClick` ô đó. Logic chạm ở [Car3dTaps] (cùng [WindowCycle] với bảng Kính 2D).
 */
@SuppressLint("ViewConstructor")
internal class Car3dWidgetView(context: Context, data: WidgetData) : FrameLayout(context) {

    private var latest = data
    private val texture = TextureView(context)
    private val dots = Car3dDotsLayer(context)
    private val backdrop = Car3dBackdropView(context)
    /** Nút "Xong" của chế độ căn ảnh nền (thay hàng nút kính khi đang căn). */
    private val doneAlign = android.widget.TextView(context)
    private val taps = Car3dTaps(this, MatrixKit(context, data), dots)
    /** R28 — chân: nhiệt · 3 nút kính · gió · lấy gió ([Car3dFootRow]); trên xe: icon ghế + số lốp ([Car3dHeroLayer]). */
    private val bar = Car3dFootRow(context, data, taps.buttons())
    private val hero = Car3dHeroLayer(context, data)
    private var renderer: Car3dRenderer? = null
    private var model: Car3dModel? = null
    private var placement: Car3dOrient.Placement? = null
    private val anchors = HashMap<String, V3>()          // id điểm chạm → điểm THẾ GIỚI
    private var orbit = Car3dOrbit(0.0, 0.0)
    /** Vì sao đang không vẽ + có thử lại không (3.1.8 `CAR3D-FALLBACK-STICKY`); [note] = dòng "không khả dụng" khi vĩnh viễn. */
    private val hold = Car3dHold(); private val note = android.widget.TextView(context)
    private var shown = false
    private var night = KachiTheme.night
    private var gen = 0
    private val main = Handler(Looper.getMainLooper())
    /** Thử dựng lại (hẹn sau lỗi · [Car3dSeat] đánh thức — có thể giữa lời gọi của view khác ⇒ [wake] sang lượt sau). */
    private val retry = Runnable { if (isAttachedToWindow && liveNow() && width > 0) start() }; private val wake: () -> Unit = { main.post(retry) }
    private var paints: List<Car3dPaint.Preset> = emptyList()
    private var paintApplied: String? = null
    /** Xi-nhan đang nháy: mã ô đọc → mốc thấy BẬT (`uptimeMillis`). Rỗng = không hẹn nhịp nào. */
    private val blinkSince = HashMap<String, Long>()
    private val blinkTick = Runnable { if (applyVariants()) requestFrames(1) }
    // ⚠ Khai TRƯỚC `init` (init gọi fill → retargetGlass): khai sau thì còn null lúc dựng ⇒ HOME sập [ĐO máy ảo 13/10 20:52].
    /** Kính tụt theo mức: id điểm kính → mẫu tên bộ phận kính · mức đang vẽ (0..1) · hoạt cảnh (từ, tới, mốc ns). */
    private val glassOf = HashMap<String, String>()
    private val glassAt = HashMap<String, Float>()
    private val glassAnim = HashMap<String, Triple<Float, Float, Long>>()
    /** Cửa/cốp: chỉ số biến thể → mức mở đang vẽ (0..1) · hoạt cảnh (từ, tới, mốc ns). */
    private val swingAt = HashMap<Int, Float>()
    private val swingAnim = HashMap<Int, Triple<Float, Float, Long>>()

    init {
        // Khung 3D chừa chân cho hàng nút (không đè bánh/cản — đo máy ảo 13/10). Lớp chấm trùng ĐÚNG khung 3D (cùng phép chiếu).
        val foot = dpi(context, Sp.TOUCH) + 2 * dpi(context, Sp.S)
        addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)); Car3dInk.declare(this, backdrop)   // R29 T29-6: mặt khung 3D
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also { it.bottomMargin = foot })
        addView(dots, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also { it.bottomMargin = foot })
        addView(hero, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also { it.bottomMargin = foot })
        // Chân widget (owner 13/10, R28): cả hàng canh giữa khung ([Car3dFootRow] tự canh + tự ẩn bộ chỉnh khi hẹp).
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dpi(context, Sp.TOUCH), Gravity.BOTTOM).also { it.bottomMargin = dpi(context, Sp.S) })
        contentDescription = context.getString(R.string.kcar3d_title)
        doneAlign.apply {
            text = context.getString(R.string.kachi_done); gravity = Gravity.CENTER; visibility = GONE; setTextColor(KachiTheme.c(KachiTheme.ON_ACCENT)); minWidth = dpi(context, Sp.TOUCH) * 2
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dpi(context, Sp.TOUCH) / 2f; setColor(KachiTheme.c(KachiTheme.ACCENT)) }
            setOnClickListener { Car3dBackdropChoice.save?.invoke(backdrop.backdrop); Car3dBackdropChoice.aligning = false }
        }
        addView(doneAlign, LayoutParams(LayoutParams.WRAP_CONTENT, dpi(context, Sp.TOUCH), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .also { it.bottomMargin = dpi(context, Sp.S) })
        addView(note.apply { text = context.getString(R.string.kcar3d_unavailable); gravity = Gravity.CENTER; visibility = GONE; setTextColor(KachiTheme.c(KachiTheme.MUT)) }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        taps.changed = { guarded("tap") { if (applyVariants() or retargetGlass() or (renderer?.glow(taps.glowing()) == true)) requestFrames(1) } }
        fill(data)
    }

    // ── Vòng đời ───────────────────────────────────────────────────────────────────────────────────────────────────
    private val paintChanged: () -> Unit = { main.post { if (applyPaint()) requestFrames(1); hero.reproject() } }   // R28: + công tắc số lốp
    private val backdropChanged: () -> Unit = { main.post(::syncBackdrop) }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); TileSafetyGate.listen(taps.refused); Car3dPaintChoice.listen(paintChanged); WidgetFallbacks.shared.attach(this, WIDGET_ID, rendering = hold.why == null)
        unlit = com.byd.clusternav.system.ScreenLit.watch(context) { screenOff = !com.byd.clusternav.system.ScreenLit.read(context); syncLive() }
            .also { screenOff = !com.byd.clusternav.system.ScreenLit.read(context) }
        Car3dBackdropChoice.listen(backdropChanged); syncBackdrop(); if (width > 0 && liveNow()) start()
    }

    /** POLISH2: dựng khi biết cỡ ô THẬT (ô < `minSlotDp` ⇒ chờ, ô lớn lại ⇒ dựng). ĐỒNG BỘ, trước lượt vẽ đầu — [ĐO máy ảo 13/10] dựng trễ
     *  một nhịp ⇒ UiHelper gắn sau khi bề mặt TextureView đã có ⇒ không nhận cỡ ⇒ camera không đặt. Ô nhỏ thì hoãn một nhịp (giữa bố cục). */
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || !isAttachedToWindow || !liveNow()) return   // khuất ⇒ không dựng (cổng [syncLive])
        if (slotFits()) start() else main.post { if (isAttachedToWindow) start() }
    }

    private fun slotFits(): Boolean = Car3dDevice.slotFits(this, WIDGET_ID)
    override fun onDetachedFromWindow() {
        TileSafetyGate.unlisten(taps.refused); Car3dPaintChoice.unlisten(paintChanged); Car3dBackdropChoice.unlisten(backdropChanged); WidgetFallbacks.shared.detach(this)
        unlit?.invoke(); unlit = null; stop(); Car3dSeat.shared.leave(this); main.removeCallbacks(retry); windowHidden = false; viewHidden = false; screenOff = false; super.onDetachedFromWindow()
    }

    /** Ảnh nền theo lựa chọn (hồ sơ); đang căn ⇒ giữ bản nháp, đổi hàng nút thành "Xong". */
    private fun syncBackdrop() {
        val aligning = Car3dBackdropChoice.aligning && Car3dBackdropChoice.current != null
        if (!aligning || backdrop.backdrop == null) backdrop.backdrop = Car3dBackdropChoice.current
        bar.visibility = if (aligning || hold.why != null) GONE else VISIBLE   // không vẽ ⇒ khung trống (3.1.8)
        doneAlign.visibility = if (aligning) VISIBLE else GONE
    }
    /** 3.1.5 [ĐO máy ảo 14/10]: tắt/bật màn KHÔNG gọi [onVisibilityAggregated] mà gọi hàm này (cửa sổ khuất/hiện) — cùng cổng [syncLive]. */
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); windowHidden = visibility != VISIBLE; syncLive() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); viewHidden = !isVisible; syncLive() }
    private var windowHidden = false; private var viewHidden = false; private var screenOff = false; private var unlit: (() -> Unit)? = null
    private fun liveNow() = !windowHidden && !viewHidden && !screenOff
    /** PERF 3.1.5 · owner 14/10 *"car 3D không bao giờ chạy nền"* — MỘT cổng: khuất (ô khác bung R30 · màn chính rời · màn tắt) ⇒ [stop] nhả
     *  Engine/GPU/GLB + nhu cầu HAL về 0 ([WidgetFallbacks.shown]); hiện lại ⇒ [start] nạp lại từ tệp đệm (trước: khuất chỉ huỷ khung).
     *  Màn tắt: [ĐO máy ảo 14/10] `SLEEP` không đổi độ hiện của cửa sổ ⇒ nghe thêm [com.byd.clusternav.system.ScreenLit] (cùng nguồn vòng HAL). */
    private fun syncLive() {
        val live = liveNow()
        shown = live; WidgetFallbacks.shared.shown(this, live)
        if (!live) { if (renderer != null) stop() else { cancelFrames(); main.removeCallbacks(blinkTick); endGesture() }; return }
        if (renderer == null) { if (isAttachedToWindow && width > 0) start() } else { applyPaint(); applyVariants(); requestFrames(FRAMES_AFTER_WAKE) }
    }

    private fun start() {
        if (!hold.canStart()) return
        if (width > 0 && !slotFits()) return park(Car3dHold.Why.SLOT, "slot ${width}x$height px < minSlotDp ${WidgetRegistry.byId(WIDGET_ID)?.minSlotDp} dp")
        if (renderer != null) return
        // Tối đa MỘT Engine; người mới (đang hiện) thắng (3.1.4, đo xe 14/10) — bên thua CHỜ, [Car3dSeat] đánh thức khi chỗ trống.
        if (!Car3dSeat.shared.claim(this, ::liveNow, wake) { park(Car3dHold.Why.SEAT, "a newer 3D widget took the engine") })
            return park(Car3dHold.Why.SEAT, "a shown 3D widget holds the engine")
        if (!Car3dDevice.capable(context)) return park(Car3dHold.Why.INCAPABLE, "low-RAM device or GLES < 3.0")
        if (!Car3dRenderer.init()) return park(Car3dHold.Why.NO_LIBS, "Filament native libs failed to load")
        hold.started(); live3d(true)
        night = KachiTheme.night
        val r = runCatching { Car3dRenderer.create(texture, medium = Car3dDevice.mediumTier(context), night = night) { _, _ -> requestFrames(FRAMES_AFTER_LOAD) } }
            .getOrElse { return park(Car3dHold.Why.FAULT, "Filament init failed: $it") }
        renderer = r
        val my = ++gen
        val t0 = System.nanoTime()
        Thread({
            val loaded = runCatching { Car3dModelSource.load(context.applicationContext) }.onFailure { Log.w(TAG, "nạp", it) }
            main.post {
                if (my != gen || renderer !== r) return@post
                // Ranh giới widget: lỗi Java/Filament bất kỳ khi dựng cảnh ⇒ khung trống + thử lại có lùi dần, KHÔNG bao giờ để HOME sập.
                runCatching { onLoaded(r, loaded, t0) }.onFailure { Log.e(TAG, "dựng cảnh 3D hỏng", it); park(Car3dHold.Why.FAULT, "3D scene setup failed: $it") }
            }
        }, "car3d-load").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun stop() {
        gen++
        cancelFrames()
        main.removeCallbacks(blinkTick); blinkSince.clear(); paintApplied = null
        endGesture()
        renderer?.let { runCatching { it.destroy() }.onFailure { e -> Log.w(TAG, "destroy", e) } }
        renderer = null; model = null; placement = null; anchors.clear(); glassOf.clear(); glassAt.clear(); glassAnim.clear(); swingAt.clear(); swingAnim.clear()
        posed.clear(); Car3dSeat.shared.release(this)
        Log.i(TAG, "dừng — tổng $framesTotal khung đã vẽ"); com.byd.clusternav.launcher.perf.KachiMem.trim("car3d dừng")   // PERF 3.1.5: trả trang Engine vừa nhả cho hệ
    }

    private fun onLoaded(r: Car3dRenderer, res: Result<Car3dModelSource.Loaded?>, t0: Long) {
        // Nạp NÉM = lỗi lúc chạy (đĩa đầy, đọc hỏng) ⇒ thử lại; `null` = đời xe không có mô hình ⇒ không khả thi ([Car3dModelSource.load]).
        val l = res.getOrElse { return park(Car3dHold.Why.FAULT, "model load failed: $it") } ?: return park(Car3dHold.Why.NO_MODEL, "no 3D model for this car")
        val t1 = System.nanoTime()
        if (!r.load(l.glb)) {
            // [ĐO máy ảo 15/10, 3.1.6] tệp đệm dở (4 MB / 29 MB) ⇒ gltfio từ chối ⇒ trước: 2D tới khi dựng lại ô. Bỏ đệm ⇒ lượt thử lại dựng từ lưới.
            if (l.origin.startsWith(CACHE_ORIGIN)) Thread({ Car3dModelSource.invalidate(context.applicationContext) }, "car3d-purge").start()
            return park(Car3dHold.Why.FAULT, "gltfio could not load the model (${l.origin})")
        }
        val m = l.model; model = m
        paints = l.paints
        applyPaint()
        applyVariants()   // ẩn bản cửa-mở / đèn-sáng TRƯỚC khi đo hộp bao (hộp chỉ tính bộ phận đang hiện)
        val b = r.visibleBounds() ?: return park(Car3dHold.Why.EMPTY_MODEL, "empty model")
        val pl = Car3dOrient.place(b.center, b.half, r.anchor(m.front), r.anchor(m.rear), m.frontSign)
        val found = ArrayList<String>(); val missing = ArrayList<String>()
        for (h in m.hotspots) {
            val a = r.anchor(h.anchor)
            if (a == null) { missing += h.id; continue }
            anchors[h.id] = pl.apply(a); found += h.id
        }
        // Đích chạm = hộp THẾ GIỚI của bộ phận neo ([Car3dPick]); điểm vẫn chấm khi hình 3D không tự nói trạng thái.
        val boxes = HashMap<String, Car3dPick.Target>()
        for (h in m.hotspots) {
            val a = anchors[h.id] ?: continue
            val shown = h.shownBy?.let(r::hasPart) == true
            boxes[h.id] = (if (shown) target(r, pl, h, a, posed = false) else null) ?: Car3dPick.Target(h.id, a, a, a)
            if (shown && r.hasHinge(h.shownBy!!)) posed[h.id] = h to a
        }
        glassOf.clear()
        m.hotspots.filter { it.group == Car3dCatalog.GROUPS.first() && it.id in boxes && it.shownBy?.let(r::hasPart) == true && it.target in DOOR_KEYS }
            .forEach { h -> glassOf[h.id] = "${h.shownBy}*" }
        r.place(pl.matrix())
        placement = pl
        orbit = Car3dOrbit(m.yawDeg, 0.0, pitch = m.pitchDeg)
        taps.bind(m, anchors.keys, boxes) { h -> r.worldBox(pl, h.anchor) }; hero.zones = taps.zones; taps.seatsLog = hero::where   // 3.1.12: icon ghế nhường chấm
        // R28 — điểm ghế (từ neo cửa + kính trước SẴN CÓ) + điểm chạm đất 4 bánh (hộp hợp bộ phận `wheel*`, đo hình học).
        val seats = Car3dHero.Seat.values().mapNotNull { s -> val k = if (s.side == Car3dHero.LEFT) "lf" else "rf"
            anchors["door-$k"]?.let { d -> anchors["win-$k"]?.let { g -> s to Car3dHero.seatPoint(d, g) } } }.toMap()
        hero.bind(seats, worldBox(r, pl, Car3dAnchor.Node(WHEELS))?.let { (lo, hi) -> Car3dHero.wheels(lo, hi) } ?: emptyMap())
        hero.bringToFront(); bar.bringToFront(); doneAlign.bringToFront()   // nút ghế/chân TRÊN các nút trợ năng bộ phận ([Car3dTaps] gắn sau)
        Log.i(TAG, "pick targets ${boxes.size} · sliding glass ${glassOf.keys}")
        Log.i(TAG, "nạp ${m.id} (${l.origin}) · nguồn ${(t1 - t0) / 1_000_000} ms · gltfio ${(System.nanoTime() - t1) / 1_000_000} ms · " +
            "${r.partNames().size} bộ phận · chấm có: $found · chấm thiếu: $missing · hộp ${pl.half} · neo " +
            anchors.entries.joinToString { (k, v) -> "$k(%.2f,%.2f,%.2f)".format(v.x, v.y, v.z) })
        applyVariants()
        fill(latest)
        requestFrames(FRAMES_AFTER_LOAD)
    }
    private fun target(r: Car3dRenderer, pl: Car3dOrient.Placement, h: Car3dHotspot, a: V3, posed: Boolean) = r.target(pl, h, a, posed)
    private fun worldBox(r: Car3dRenderer, pl: Car3dOrient.Placement, a: Car3dAnchor) = r.worldBox(pl, a)
    /** Điểm có bộ phận quay bản lề (nắp cốp): đích chạm đi theo tư thế hiện tại — chạm nắp ĐANG giơ lên thì đóng (owner 13/10). */
    private val posed = HashMap<String, Pair<Car3dHotspot, V3>>()
    private fun repose(r: Car3dRenderer) {
        val pl = placement ?: return
        posed.values.forEach { (h, a) -> target(r, pl, h, a, posed = true)?.let(taps::retarget) }
        taps.redot { h -> r.worldBox(pl, h.anchor, posed = true) }   // chấm đi theo cửa/nắp/kính ĐANG vẽ
    }
    /** Không vẽ vì [w] ([Car3dHold]): nhả Engine, khung trống (nền), nhu cầu HAL về 0; thôi hẳn (vĩnh viễn / quá trần lỗi) ⇒ dòng [note]; lỗi ⇒ hẹn [retry]. */
    private fun park(w: Car3dHold.Why, detail: String) {
        val n = hold.park(w)
        if (n.log) Log.w(TAG, "3D không vẽ [$w]: $detail · " + (n.retryMs?.let { "thử lại sau $it ms" } ?: if (n.gaveUp) "thôi thử (quá ${Car3dHold.MAX_FAULTS} lỗi)" else if (w.permanent) "không khả thi" else "chờ"))
        if (renderer != null) stop() else Car3dSeat.shared.release(this); if (!hold.canStart()) Car3dSeat.shared.leave(this)
        live3d(false); taps.hide(); note.visibility = if (!hold.canStart()) VISIBLE else GONE   // Pass 8: quá trần lỗi cũng là "không khả dụng" (owner: coi như không khả thi)
        main.removeCallbacks(retry); n.retryMs?.let { main.postDelayed(retry, it) }
    }
    /** Lớp vẽ (khung 3D · chấm · ghế/lốp) + hàng chân theo trạng thái vẽ; sổ nhu cầu HAL ([WidgetFallbacks.rendering]). */
    private fun live3d(on: Boolean) {
        val v = if (on) VISIBLE else INVISIBLE; texture.visibility = v; dots.visibility = v; hero.visibility = v; if (on) note.visibility = GONE
        syncBackdrop(); WidgetFallbacks.shared.rendering(this, on)
    }
    // ── Dữ liệu xe (nhịp poll) ─────────────────────────────────────────────────────────────────────────────────────

    /** Đổ trạng thái xe TẠI CHỖ: chấm, mô tả trợ năng, biến thể cửa/đèn, ô nút ẩn. Không dựng view mới. */
    fun fill(d: WidgetData) {
        latest = d
        taps.fill(d)
        if (hold.why != null) return   // đang chờ/không khả thi: hero/chân ẩn — [onLoaded] đổ lại `latest`; lỗi lặp không đốt lượt thử
        guarded("fill") { hero.fill(d); bar.fill(d)   // Pass 3: lớp hero/chân (R28) cũng trong ranh giới — lỗi ⇒ khung trống + thử lại, không lên nhịp poll HOME
            var redraw = applyVariants() or applyPaint() or retargetGlass() or (renderer?.glow(taps.glowing()) == true)
            if (KachiTheme.night != night) { night = KachiTheme.night; renderer?.night(night); redraw = true }
            if (redraw) requestFrames(1)
        }
    }

    /**
     * Ranh giới widget cho MỌI đường chạm Filament ngoài khung/nạp (nhịp đổ số · cú chạm): lỗi Java ⇒ [park] (thử lại có lùi dần), không để lọt lên
     * nhịp poll của HOME (soát Pass 2: chỉ [onFrame] và [onLoaded] có ranh giới).
     */
    private inline fun guarded(what: String, block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.e(TAG, "$what 3D hỏng", e); park(Car3dHold.Why.FAULT, "3D $what failed: $e") }
    }

    /**
     * Biến thể cửa/đèn theo số đọc (cùng [CarStatus] của HOME — gồm cả xi-nhan thật qua `TurnSignalLive`). Xi-nhan
     * ([LampTone.blinks]) đang BẬT ⇒ nháy TẠI CHỖ ~1,5 Hz như bảng Đèn 2D ([LampTone.litAt]): mỗi mép pha hẹn ĐÚNG MỘT lượt
     * ([blinkTick] → một khung Filament), chỉ khi widget đang hiện; TẮT ⇒ xoá mốc + bỏ hẹn ngay (không vòng lặp rảnh).
     */
    private fun applyVariants(): Boolean {
        val r = renderer ?: return false
        val m = model ?: return false
        val now = android.os.SystemClock.uptimeMillis()
        val seen = HashSet<String>()
        val open = m.variants.map { v ->
            val on = taps.active(v.group, v.read)
            if (!on || !LampTone.blinks(v.read)) return@map on
            seen += v.read
            LampTone.litAt(now - blinkSince.getOrPut(v.read) { now })
        }
        if (seen != blinkSince.keys) Log.i(TAG, "blink ${blinkSince.keys} -> $seen")
        blinkSince.keys.retainAll(seen)
        main.removeCallbacks(blinkTick)
        if (blinkSince.isNotEmpty() && shown) main.postDelayed(blinkTick, blinkSince.values.minOf { LampTone.nextEdgeIn(now - it) })
        var started = false
        val shownOpen = open.mapIndexed { i, on ->
            if (!isSwing(m.variants[i])) return@mapIndexed on
            val to = if (on) 1f else 0f
            val cur = swingAt[i]
            when {
                cur == null || !r.hasHinge(m.variants[i].closed) -> { swingAt[i] = to; swingAnim.remove(i); r.swing(m.variants[i].closed, to); repose(r) }
                (swingAnim[i]?.second ?: cur) != to -> { swingAnim[i] = Triple(cur, to, System.nanoTime()); started = true }
            }
            // Đang diễn hoạt ⇒ hiện bản ĐÓNG đang quay theo bản lề; tới đích "mở" mới đổi sang đúng bản MỞ của tệp.
            // Không có bản MỞ trong tệp (nắp cốp cắt từ thân — [Car3dLid]) ⇒ giữ bản đóng ĐÃ QUAY hết góc.
            swingAnim[i] == null && (swingAt[i] ?: 0f) >= 1f && r.hasPart(m.variants[i].open)
        }
        val changed = runCatching { r.showByName { name -> Car3dNames.visibleAll(m.variants, shownOpen, name) } }
            .getOrElse { Log.e(TAG, "biến thể 3D hỏng", it); false }
        return changed || started
    }

    /** Biến thể có hình MỞ (cửa · cốp) ⇒ diễn hoạt theo bản lề thay vì đổi bản ngay (owner 13/10 "đang thấy giật phát ra luôn"). */
    private fun isSwing(v: Car3dVariant) = v.open.contains(OPEN_TAG)

    /** Một bước hoạt cảnh cửa/cốp. `true` = còn chạy. Xong một cái ⇒ áp lại biến thể (đổi sang bản mở/đóng của tệp). */
    private fun stepSwing(r: Car3dRenderer, now: Long): Boolean {
        val m = model ?: return false
        var done = false
        val it = swingAnim.entries.iterator()
        while (it.hasNext()) {
            val (i, a) = it.next()
            val t = ((now - a.third) / (SWING_MS * 1_000_000.0)).coerceIn(0.0, 1.0).toFloat()
            val v = a.first + (a.second - a.first) * (t * t * (3f - 2f * t))
            swingAt[i] = v; r.swing(m.variants[i].closed, v)
            if (t >= 1f) { it.remove(); done = true }
        }
        if (swingAnim.isNotEmpty() || done) repose(r)
        if (done) applyVariants()
        return swingAnim.isNotEmpty()
    }

    /** Mức đích của từng kính khác mức đang vẽ ⇒ bắt đầu hoạt cảnh ≤ [GLASS_MS] (dừng hẳn khi tới — rảnh 0 khung). */
    private fun retargetGlass(): Boolean {
        var started = false
        val now = System.nanoTime()
        for (id in glassOf.keys) {
            val to = (taps.level(id) ?: 0) / WindowCycle.FULL.toFloat()
            val cur = glassAt[id]
            if (cur == null) { glassAt[id] = to; renderer?.let { it.slide(glassOf.getValue(id), to); repose(it) }; started = true; continue }
            val goal = glassAnim[id]?.second ?: cur
            if (goal != to) { glassAnim[id] = Triple(cur, to, now); started = true }
        }
        return started
    }

    /** Một bước hoạt cảnh kính (gọi mỗi khung). `true` = còn chạy. */
    private fun stepGlass(r: Car3dRenderer, now: Long): Boolean {
        val moved = glassAnim.isNotEmpty()
        val it = glassAnim.entries.iterator()
        while (it.hasNext()) {
            val (id, a) = it.next()
            val t = ((now - a.third) / (GLASS_MS * 1_000_000.0)).coerceIn(0.0, 1.0).toFloat()
            val e = t * t * (3f - 2f * t)   // ease in-out
            val v = a.first + (a.second - a.first) * e
            glassAt[id] = v; r.slide(glassOf.getValue(id), v)
            if (t >= 1f) it.remove()
        }
        if (moved) repose(r)
        return glassAnim.isNotEmpty()
    }

    /** Màu sơn đang chọn ([Car3dPaintChoice], theo hồ sơ) khác màu đã áp ⇒ đổi tham số vật liệu sơn. */
    private fun applyPaint(): Boolean {
        val r = renderer ?: return false
        if (model == null) return false
        val p = Car3dPaint.pick(paints, Car3dPaintChoice.current) ?: return false
        if (p.name == paintApplied) return false
        paintApplied = p.name; backdrop.paint = p.argb()   // R29 T29-6: màu sơn vào nền đo của lớp phủ (đổi ⇒ một lượt giải)
        Log.i(TAG, "paint ${p.name}")
        return runCatching { r.paint(p.linear) }.getOrElse { Log.e(TAG, "paint", it); false }
    }

    // ── Chạm: vuốt ngang = xoay; chạm-thả gần điểm = điểm đó ([Car3dTaps]) ───────────────────────────────────────

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f; private var dragging = false
    private var tracker: VelocityTracker? = null
    private var barGesture = false; private var heroGesture = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (placement == null) return false
        // [ĐO máy ảo 13/10] bản đầu chỉ nhả cú NHẤN cho thanh nút rồi chặn cú THẢ ⇒ nút nhận CANCEL, không bao giờ bấm được.
        // Cả cử chỉ bắt đầu trên thanh nút thuộc về thanh nút.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) { barGesture = inBar(ev) || (doneAlign.visibility == VISIBLE && inView(doneAlign, ev)); heroGesture = hero.owns(ev.x, ev.y) && !taps.claims(ev.x, ev.y); downX = ev.x; downY = ev.y }
        // R28: chạm icon ghế = nút; kéo quá ngưỡng từ trên icon ⇒ cử chỉ thành XOAY (giành lại, icon nhận CANCEL) — không có vùng chết.
        if (heroGesture && ev.actionMasked == MotionEvent.ACTION_MOVE && kotlin.math.hypot(ev.x - downX, ev.y - downY) > slop) { heroGesture = false; dragging = true; lastX = ev.x; lastY = ev.y; parent?.requestDisallowInterceptTouchEvent(true); return true }
        return !barGesture && !heroGesture   // mọi cú chạm khác trên khung xe do view này quyết (vuốt hay chạm điểm)
    }

    private fun inBar(ev: MotionEvent) = bar.visibility == VISIBLE && inView(bar, ev)
    private fun inView(v: View, ev: MotionEvent) = ev.x >= v.left && ev.x < v.right && ev.y >= v.top && ev.y < v.bottom

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (placement == null) return super.onTouchEvent(ev)
        // Chế độ căn ảnh nền: mọi cú chạm trong khung là của ảnh (kéo/chụm), không xoay xe, không bấm bộ phận.
        if (doneAlign.visibility == VISIBLE) {
            parent?.requestDisallowInterceptTouchEvent(true); backdrop.align(ev); return true
        }
        val vt = tracker ?: VelocityTracker.obtain().also { tracker = it }
        vt.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastX = ev.x; lastY = ev.y; dragging = false; orbit = orbit.copy(velocity = 0.0, pitchVelocity = 0.0)
                // Cử chỉ bắt đầu trong khung 3D thuộc về khung 3D (xoay 2 trục) — vùng làm việc không cuộn giành.
                parent?.requestDisallowInterceptTouchEvent(true)
                taps.down(ev.x, ev.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && kotlin.math.hypot(ev.x - downX, ev.y - downY) > slop) { dragging = true; taps.cancel() }
                if (dragging) { orbit = orbit.drag(ev.x - lastX, ev.y - lastY, width, height); lastX = ev.x; lastY = ev.y; requestFrames(1) }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) { vt.computeCurrentVelocity(1000); orbit = orbit.fling(vt.xVelocity, vt.yVelocity, width, height); requestFrames(1) }
                else if (taps.up(ev.x, ev.y)) playSoundEffect(SoundEffectConstants.CLICK)
                dragging = false; vt.recycle(); tracker = null
            }
            MotionEvent.ACTION_CANCEL -> endGesture()
        }
        return true
    }

    /**
     * Bỏ cử chỉ đang dở (CANCEL · ẩn · rời cửa sổ). Soát Pass 2: rời cửa sổ/ẩn giữa lúc vuốt thì không có UP ⇒ `dragging` kẹt
     * `true` ⇒ sau khi gắn lại [frame] tự hẹn khung MÃI (~24 khung/giây khi rảnh — trái "rảnh = 0 khung").
     */
    private fun endGesture() { taps.cancel(); dragging = false; tracker?.recycle(); tracker = null }

    // ── Khung ──────────────────────────────────────────────────────────────────────────────────────────────────────

    private val frameCb = Choreographer.FrameCallback { onFrame(it) }
    private var posted = false
    private var pending = 0
    private var misses = 0
    private var lastNs = 0L
    private var framesTotal = 0L
    private var burst = 0
    private var burstNs = 0L

    private fun requestFrames(n: Int) { pending = maxOf(pending, n); post() }

    private fun post() {
        if (posted || renderer == null || !shown || placement == null) return
        posted = true; Choreographer.getInstance().postFrameCallback(frameCb)
    }

    private fun cancelFrames() { if (posted) Choreographer.getInstance().removeFrameCallback(frameCb); posted = false }

    private fun onFrame(t: Long) {
        posted = false
        runCatching { frame(t) }.onFailure { Log.e(TAG, "khung 3D hỏng", it); park(Car3dHold.Why.FAULT, "3D frame failed: $it") }
    }

    private fun frame(t: Long) {
        val r = renderer ?: return
        if (lastNs != 0L && t - lastNs < FRAME_NS) { post(); return }
        val w0 = System.nanoTime()
        if (orbit.moving) orbit = orbit.step(if (lastNs == 0L) 1.0 / 24 else ((t - lastNs) / 1e9).coerceAtMost(0.1))
        val sliding = stepGlass(r, System.nanoTime()) or stepSwing(r, System.nanoTime())
        val cam = camera(r)
        if (cam != null) r.setCamera(cam)
        if (r.render(t)) {
            lastNs = t; framesTotal++; burst++; misses = 0; burstNs += System.nanoTime() - w0
            if (pending > 0) pending--
            cam?.let { c -> placement?.let { taps.project(c, anchors, it.bounds, texture.width, texture.height); hero.project(c, texture.width, texture.height); taps.logOnce(); groundShadow(c, it.bounds) } }
        } else if (++misses > MAX_MISSES) { pending = 0; misses = 0 }
        if (orbit.moving || pending > 0 || dragging || sliding) post()
        else {
            if (burst > 0) { Log.d(TAG, "đợt vẽ $burst khung (tổng $framesTotal) · luồng chính TB ${burstNs / burst / 1000} µs/khung"); taps.dump("dots yaw=${orbit.yaw.toInt()} pitch=${orbit.pitch.toInt()}"); Car3dInk.settle(this) }   // R29: xoay DỪNG
            burst = 0; burstNs = 0L; lastNs = 0L
        }
    }

    /** Bóng đất: hình chiếu bầu dục đáy xe lên khung ([Car3dMath.groundRect]) — lớp nền vẽ, chỉ đổi khi camera đổi. */
    private fun groundShadow(c: Car3dCamera, b: Car3dBounds) = Car3dMath.groundRect(c, b, texture.width, texture.height)?.let { (l, t, rr, bb) -> backdrop.shadow(l, t, rr, bb) }
    private fun camera(r: Car3dRenderer): Car3dCamera? = placement?.let { pl -> model?.let { m -> Car3dMath.camera(pl.bounds, m.fovDeg, m.zoom, r.width, r.height, orbit) } }

    private companion object {
        const val TAG = "Car3d"
        const val WIDGET_ID = "w_car3d"
        /** ~24 khung/giây (41,7 ms) — trần khi đang xoay/quán tính. */
        const val FRAME_NS = 41_666_666L
        const val FRAMES_AFTER_LOAD = 3
        const val FRAMES_AFTER_WAKE = 6
        const val MAX_MISSES = 48
        /** Kính tụt/kéo lên trong 560 ms (owner 13/10 "kính chạy hơi nhanh, giảm 1/2": 280 → 560) — rồi đứng yên, 0 khung. */
        const val GLASS_MS = 560L
        /** Cửa/cốp mở/đóng trong 900 ms (owner: 800–1000 ms), êm hai đầu. */
        const val SWING_MS = 900L
        const val OPEN_TAG = "open"
        /** Tiền tố [Car3dModelSource.Loaded.origin] khi nạp từ tệp đệm. */
        const val CACHE_ORIGIN = "cache:"
        val DOOR_KEYS = setOf("lf", "rf", "lr", "rr")
        const val WHEELS = "wheel*"
    }
}
