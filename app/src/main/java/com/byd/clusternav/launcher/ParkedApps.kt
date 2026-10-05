package com.byd.clusternav.launcher

import android.app.ActivityOptions
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bề mặt vẽ KHÔNG AI XEM cho một màn ảo của Kachi: `ImageReader` + một luồng riêng lấy-rồi-bỏ từng khung (không có người
 * nhận thì hàng đệm đầy và bên vẽ của màn ảo nghẽn — KDoc `StagingDisplay`). MỘT bản cho hai chỗ dùng (CLAUDE.md §4.1 DRY):
 * màn ảo dàn dựng ẩn (`StagingDisplay`, cỡ display 0) và màn ảo ĐỖ của ô 7 ([ParkedApps], cỡ chính màn ảo đỗ).
 */
internal class OffscreenSink private constructor(private val reader: ImageReader, private val thread: HandlerThread) {

    val surface: Surface get() = reader.surface

    /** Đóng mặt vẽ rồi luồng. Gọi SAU khi màn ảo đã rời mặt vẽ này (nhả / đổi mặt vẽ). Gọi lại vô hại. */
    fun close() {
        runCatching { reader.close() }
        thread.quitSafely()
    }

    companion object {
        /** [w]×[h] RGBA_8888, 2 khung (cùng thông số `StagingDisplay` trước khi tách). Ném `RuntimeException` ⇒ bên gọi lùi. */
        fun open(w: Int, h: Int, threadName: String): OffscreenSink {
            val t = HandlerThread(threadName).apply { start() }
            val r = try {
                ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            } catch (e: RuntimeException) {
                t.quitSafely()
                throw e
            }
            r.setOnImageAvailableListener({ rr -> runCatching { rr.acquireLatestImage()?.close() } }, Handler(t.looper))
            return OffscreenSink(r, t)
        }
    }
}

/**
 * ═══ Ô 7 — ĐỖ ẨN app của ô (2.89-thử1 · bản THỬ, spec `kachi-287-look-and-keys.html` §4.6d, backlog `SLOT-PARK-HIDDEN`) ═══
 *
 * Owner 05/10 trên xe: *"sao ko giả lập 1 ô số 7 gì đó, để nhét các app chạy nền vào đó"* · *"thử cho nó vào nền đi xem nào?"*.
 * [ĐO xe 05/10] (Seal DL3, 2.88): giữ chỗ BEHIND-HOME ném NPE trong system_server ⇒ mọi lượt đẩy ra sau màn nhà hỏng; dời
 * YouTube từ màn ảo ô sang display 0 ⇒ activity RELAUNCH ⇒ dừng phát hẳn.
 *
 * ## Cơ chế — app KHÔNG rời màn ảo của nó
 * Đỗ = `VirtualDisplay.setSurface(<ImageReader CÙNG cỡ>)`; nhận lại vào ô = `setSurface(<mặt vẽ ô>)`. [ĐO nguồn] A10 r47
 * `services/.../display/VirtualDisplayAdapter.java:291-301` (A12 r34 `:320-330`): đổi mặt vẽ khác-null → khác-null KHÔNG phát
 * `DISPLAY_DEVICE_EVENT_CHANGED`, không đổi cỡ / mật độ; ON/OFF của màn ảo là `mIsDisplayOn` (A10 `:237` · `:316-322` · `:396`)
 * — không đi theo mặt vẽ. Đổi CỠ thì có phát (`resizeLocked` A10 `:303-314`) ⇒ khớp phép đo relaunch. [SUY] app không nhận
 * đổi cấu hình nào ⇒ không relaunch, nhạc chạy tiếp — 🚗 chờ owner thử (§4.6d).
 *
 * ## Bốn câu CLAUDE.md §4 — lượt đỗ / nhận lại chạy **0 lệnh shell**
 *  1. display = đúng màn ảo Kachi tạo và đã đăng ký cổng sở hữu ([SlotVdOwner]; id ≥ 1 BẤT KỲ — sau khởi động nguội màn ảo ô đầu
 *     tiên CHÍNH LÀ display 1 [ĐO xe 15/09], B4); không bao giờ display 0 hay màn ảo cụm (id dò live) — review 2.89 Pass 3 · whole-r2-4;
 *  2. app = app đang ở chính màn ảo đó (không lệnh nào nhắm app);
 *  3. loại stack = không đụng (0 `am` / `wm`);
 *  4. hoàn tác = nhận lại vào ô (đổi mặt vẽ ngược) · nhả màn ảo (trần [ParkLedger.CAP] · Kachi chết ⇒ cờ 256
 *     DESTROY_CONTENT_ON_REMOVAL kết thúc activity trên đó). KHÔNG trạng thái hệ thống bền nào (§5).
 *
 * Màn ảo đỗ vẫn do [SlotVdOwner] cầm (chủ [OWNER], khoá âm dải RIÊNG) ⇒ `releaseOwner` của cây workspace (màn Kachi huỷ /
 * dựng lại) KHÔNG nhả nó; `adopt` của ô thật không đụng nó. Luồng chính.
 */
object ParkedApps {

    private const val TAG = "KachiPark"

    /** Chủ của màn ảo đỗ ở [SlotVdOwner] — tách khỏi chủ cây ô (`ws@…`) và chủ dàn dựng ẩn (`stage`). */
    private const val OWNER = "park"

    /**
     * "Ô" âm, dải RIÊNG: `SlotVdLedger.adopt` nhả mọi màn ảo CÙNG chỉ số ô bất kể chủ ⇒ không được trùng ô thật (0…5) hay
     * khoá dàn dựng ẩn (`StagingDisplay` -1, -2, … — một lượt mỗi chuyến / lối tắt, không bao giờ chạm một triệu).
     */
    private val keys = AtomicInteger(-1_000_000)

    /** Một app đang đỗ: màn ảo ([lease], tên [name]) giữ NGUYÊN cỡ [width]×[height] lúc đỗ. */
    class Parked internal constructor(
        val pkg: String,
        val name: String,
        val lease: VdLease,
        val width: Int,
        val height: Int,
        internal val key: Int,
        internal val sink: OffscreenSink,
    )

    private val ledger = ParkLedger<Parked>()

    /**
     * ĐỖ app [pkg] đang ở màn ảo [lease] (tên [name], cỡ [width]×[height]): mặt vẽ → bề mặt ẩn cùng cỡ, màn ảo sang chủ
     * [OWNER]. Vượt trần ⇒ nhả bản đỗ cũ nhất KHÔNG thuộc [protect] (app sắp được nhận lại ở cùng lượt). `false` = chưa đỗ
     * (không dựng được bề mặt ẩn / hệ từ chối đổi mặt vẽ) ⇒ bên gọi giữ host như cũ.
     */
    fun park(pkg: String, name: String, lease: VdLease, width: Int, height: Int, protect: Set<String> = emptySet()): Boolean {
        val sink = try {
            OffscreenSink.open(width, height, "kachi-park")
        } catch (e: RuntimeException) {
            Log.w(TAG, "đỗ $pkg: không dựng được bề mặt ẩn ${width}x$height", e)
            return false
        }
        try {
            lease.vd.surface = sink.surface
        } catch (e: RuntimeException) {
            sink.close()
            Log.w(TAG, "đỗ $pkg: đổi mặt vẽ display ${lease.displayId} hỏng", e)
            return false
        }
        enter(pkg, name, lease, width, height, sink, protect, via = "")
        return true
    }

    /**
     * A2 · 2.89 (backlog `TRIP-MUSIC-IN-SLOT` (2)) — nhận vào ô 7 một màn ảo ĐÃ vẽ vào bề mặt ẩn [sink] (màn ảo dàn dựng ẩn của
     * chuyến lên xe — `StagingDisplay.park`, app [pkg] vừa mở lên đó bằng `HiddenPark`): KHÔNG đổi mặt vẽ, KHÔNG lệnh nào; chỉ
     * chuyển chủ + ghi sổ. Từ đây app được đối xử y như app đỗ từ ô: đặt vào ô ⇒ [claim] (đổi mặt vẽ, không relaunch), mở toàn
     * màn ⇒ [forget], trần [ParkLedger.CAP] ⇒ nhả cũ nhất. Gọi được từ luồng `kachi-behind`: sổ và [SlotVdOwner] đều có khoá.
     */
    internal fun adoptHidden(pkg: String, name: String, lease: VdLease, width: Int, height: Int, sink: OffscreenSink): Boolean {
        if (pkg.isBlank() || width <= 0 || height <= 0) return false
        enter(pkg, name, lease, width, height, sink, emptySet(), via = " via=trip")
        return true
    }

    /**
     * Một lối ghi sổ cho [park] + [adoptHidden]: khoá âm mới → chuyển chủ màn ảo → sổ (nhả bản bị đẩy ra) → một dòng nhật ký
     * ([via] = đuôi ASCII cho nhật ký: `""` = đỗ từ ô, `" via=trip"` = màn ảo ẩn của chuyến lên xe).
     */
    private fun enter(pkg: String, name: String, lease: VdLease, width: Int, height: Int, sink: OffscreenSink, protect: Set<String>, via: String) {
        val key = keys.getAndDecrement()
        SlotVdOwner.move(OWNER, key, name, lease)
        ledger.park(pkg, Parked(pkg, name, lease, width, height, key, sink), protect).forEach { drop(it.handle, it.why.name) }
        Log.i(TAG, "đỗ $pkg display=${lease.displayId} ${width}x$height ⇒ ô 7 = ${ledger.pkgs()}$via")
    }

    /** A2 · 2.89 — màn ảo đỗ của [pkg] (`null` = không đỗ): link của bước nhạc đi vào CHÍNH màn ảo đó (`TripMusicPlan.ViewRoute.Parked`). */
    fun vdOf(pkg: String): Int? = ledger.peek(pkg)?.lease?.displayId

    /** Lấy RA bản đỗ của [pkg] (`null` = không đỗ) — chỉ [claim] gọi, và [attach] ngay (một màn ảo, một chủ). */
    private fun take(pkg: String): Parked? = ledger.take(pkg)?.also {
        Log.i(TAG, "nhận lại $pkg display=${it.lease.displayId} ${it.width}x${it.height} ⇒ ô 7 = ${ledger.pkgs()}")
    }

    /**
     * Gắn màn ảo đỗ [p] vào mặt vẽ [s] của ô [slot] (chủ [owner]): đổi mặt vẽ → đóng bề mặt ẩn → [SlotVdOwner] chuyển màn ảo
     * về ô (màn ảo cũ của ô, nếu còn, bị nhả — bất biến một-màn-ảo-mỗi-ô). `false` = hệ từ chối đổi mặt vẽ ⇒ màn ảo đã NHẢ ở
     * đây (không để một màn ảo vẽ vào bề mặt đã đóng), bên gọi mở app vào màn ảo MỚI như đường thường.
     */
    private fun attach(p: Parked, s: Surface, owner: String, slot: Int): Boolean {
        try {
            p.lease.vd.surface = s
        } catch (e: RuntimeException) {
            Log.w(TAG, "nhận lại ${p.pkg}: đổi mặt vẽ hỏng — nhả màn ảo đỗ", e)
            drop(p, "attach-failed")
            return false
        }
        p.sink.close()
        SlotVdOwner.move(owner, slot, p.name, p.lease)
        return true
    }

    /**
     * Kết quả [claim]. [parked] khác `null` = đã LẤY RA và GẮN vào mặt vẽ ô (host nhận màn ảo) · [wait] = mặt vẽ đang đổi cỡ,
     * chờ lượt `surfaceChanged` kế (KHÔNG tạo màn ảo) · cả hai rỗng = đường thường. [pinned] = mặt vẽ ghim cỡ màn ảo đỗ.
     */
    class Claim internal constructor(val parked: Parked?, val wait: Boolean, val pinned: Boolean)

    /**
     * ═══ PARK-1 — nhận lại: CỠ mặt vẽ TRƯỚC, gắn màn ảo SAU (luồng chính; `surfaceChanged` của host CHƯA có màn ảo) ═══
     *
     * [ĐO nguồn A10 r47 native] SurfaceFlinger đọc cỡ đích của màn ảo MỘT lần, lúc dựng `VirtualDisplaySurface` khi màn ảo
     * đổi mặt vẽ (`VirtualDisplaySurface.cpp:88-92`), chỉ đổi lại khi màn ảo đổi CỠ (`resizeBuffers` `:288-292`) — màn ảo đỗ
     * không bao giờ đổi cỡ; đệm xếp với `SCALING_MODE_FREEZE` (`:264-267`), cỡ báo ra = cỡ đã chốt (`:529-533`). [SUY, chưa
     * thấy trên xe] gắn lúc mặt vẽ ô còn cỡ ô ⇒ khung đứng / đen (HWC) hoặc cắt góc + đen (GLES), không tự lành.
     * ⇒ Cỡ ô ≠ cỡ màn ảo: `setFixedSize` ĐỒNG BỘ (A10 r47 `SurfaceView.java:1051-1056` → `requestLayout`; lượt sau
     * `:566-569` lấy cỡ đã ghim, `:761-770` gọi lại `surfaceChanged`) + khung giữa ô, CHƯA lấy ra ([wait]). Đúng cỡ ⇒ lấy ra
     * + gắn. Bản đỗ bị nhả (trần) / gắn hỏng giữa hai lượt ⇒ [unfit] trả mặt vẽ về cỡ ô rồi đường thường — không rò gì. Bảng
     * quyết thuần: [SlotParkPlan.claim] (review 2.89 Pass 3 · whole-r2-6).
     */
    fun claim(host: View, sv: SurfaceView, pkg: String?, owner: String, slot: Int, w: Int, h: Int, pinned: Boolean): Claim {
        // Review 2.89 Pass 3 · whole-r2-6: QUYẾT ở `:core` (`SlotParkPlan.claim` / `lost`, bảng test), ở đây chỉ THI HÀNH bước.
        val p = pkg?.let(ledger::peek)
        return when (SlotParkPlan.claim(p?.width, p?.height, w, h, host.width, host.height, pinned)) {
            SlotParkPlan.ClaimStep.FIT_WAIT -> {
                fit(host, sv, p!!, w, h)
                Claim(null, wait = true, pinned = true)
            }
            SlotParkPlan.ClaimStep.ATTACH -> {
                val taken = take(p!!.pkg)
                if (taken != null && attach(taken, sv.holder.surface, owner, slot)) Claim(taken, wait = false, pinned = pinned)
                else lostClaim(SlotParkPlan.lost(pinned, w, h, host.width, host.height), host, sv)
            }
            else -> lostClaim(SlotParkPlan.lost(pinned, w, h, host.width, host.height), host, sv)
        }
    }

    /** Thi hành một bước "bản đỗ không còn" của [SlotParkPlan.lost]. */
    private fun lostClaim(step: SlotParkPlan.ClaimStep, host: View, sv: SurfaceView): Claim = when (step) {
        SlotParkPlan.ClaimStep.UNFIT_GOLDEN -> { unfit(host, sv); GOLDEN }
        SlotParkPlan.ClaimStep.UNFIT_WAIT -> { unfit(host, sv); Claim(null, wait = true, pinned = false) }
        else -> GOLDEN
    }

    private val GOLDEN = Claim(null, wait = false, pinned = false)

    /** Khung đang áp lên mặt vẽ (giữ ở `tag` của chính nó — không giữ view ở bộ nhớ tĩnh): bản đỗ + bộ nghe bố trí của host. */
    private class Fit(val p: Parked, val listener: View.OnLayoutChangeListener)

    /**
     * Cỡ ô [w]×[h] ≠ cỡ màn ảo đỗ ⇒ KHÔNG đổi cỡ màn ảo (đổi cỡ = relaunch): mặt vẽ [sv] ghim cỡ màn ảo (`setFixedSize`, đồng
     * bộ — PARK-1) và nằm GIỮA khung [host], giữ tỉ lệ ([SlotParkPlan.letterbox]) — tính lại mỗi lượt bố trí của [host]
     * (inset ẩn/hiện đổi cỡ ô). Gọi lại (lượt `surfaceChanged` chen giữa) ⇒ thay bộ nghe cũ, không chồng.
     */
    private fun fit(host: View, sv: SurfaceView, p: Parked, w: Int, h: Int) {
        val box = SlotParkPlan.letterbox(p.width, p.height, w, h)
        sv.holder.setFixedSize(p.width, p.height)
        (sv.tag as? Fit)?.let { host.removeOnLayoutChangeListener(it.listener) }
        val onLayout = View.OnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> sv.post { place(sv, p, r - l, b - t) } }
        sv.tag = Fit(p, onLayout)
        host.addOnLayoutChangeListener(onLayout)
        sv.post { place(sv, p, host.width, host.height) }
        Log.i(TAG, "nhận lại ${p.pkg}: ô ${w}x$h ≠ màn ảo ${p.width}x${p.height} ⇒ khung ${box?.get(0)}x${box?.get(1)} giữa ô, cỡ mặt vẽ trước — gắn ở lượt sau")
    }

    /**
     * Bỏ khung (bản đỗ không còn để gắn): mặt vẽ về cỡ bố trí, phủ cả ô. Đường thường NGAY hay chờ lượt `surfaceChanged` kế do
     * [SlotParkPlan.lost] quyết (host đã đúng cỡ ⇒ không có lượt kế).
     */
    private fun unfit(host: View, sv: SurfaceView) {
        (sv.tag as? Fit)?.let { host.removeOnLayoutChangeListener(it.listener) }
        sv.tag = null
        sv.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        sv.holder.setSizeFromLayout()
        Log.i(TAG, "bỏ khung: bản đỗ không còn ⇒ mặt vẽ về cỡ ô ${host.width}x${host.height}, mở như đường thường")
    }

    /** Đặt khung mặt vẽ theo cỡ ô [w]×[h] hiện tại; trùng khung đang có / khung đã bỏ ⇒ không làm gì (không vòng bố trí). */
    private fun place(sv: SurfaceView, p: Parked, w: Int, h: Int) {
        if (w <= 0 || h <= 0 || (sv.tag as? Fit)?.p !== p) return
        val box = SlotParkPlan.letterbox(p.width, p.height, w, h)
        val lp = if (box == null) FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        else FrameLayout.LayoutParams(box[0], box[1], Gravity.CENTER)
        val cur = sv.layoutParams as? FrameLayout.LayoutParams
        if (cur != null && cur.width == lp.width && cur.height == lp.height && cur.gravity == lp.gravity) return
        sv.layoutParams = lp
    }

    /** [pkg] đang đỗ (task của nó nằm trên một màn ảo ẩn) — `AppOpener` mở toàn màn phải nêu display 0. Luồng chính. */
    fun has(pkg: String): Boolean = ledger.has(pkg)

    /**
     * Review 2.89 Pass 3 · whole-r2-1 — MỘT cửa cho mọi lượt mở app từ TIẾN TRÌNH CHÍNH (`AppOpener.openByIntent` · giọng nói /
     * dẫn theo lịch `VoiceAppIntents.fire`): app đang đỗ ⇒ [base] + nêu display 0 (task tìm lại được không nêu display thì ở yên
     * trên màn ảo đỗ ẩn [ĐO nguồn r47 `TaskLaunchParamsModifier.java:313-317` · `ActivityStarter.java:1480-1485`] ⇒ người lái
     * không thấy gì); không đỗ ⇒ `null` (bên gọi đi đường cũ từng byte). Mở được ⇒ bên gọi gọi [launched]. Tiến trình `:wake`: sổ
     * trống ⇒ luôn `null` (OQ-P3 không đổi).
     */
    fun launchOptions(pkg: String?, base: () -> ActivityOptions = { ActivityOptions.makeBasic() }): ActivityOptions? =
        if (pkg != null && has(pkg)) base().setLaunchDisplayId(Display.DEFAULT_DISPLAY) else null

    /** PARK-2a sau một lượt mở thành công với [launchOptions] khác `null`: [forget] trên luồng chính (gọi từ luồng nào cũng được). */
    fun launched(pkg: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) forget(pkg) else main.post { forget(pkg) }
    }

    /**
     * PARK-2a — [pkg] vừa mở TOÀN MÀN ở display 0 (`AppOpener` nêu display 0 ⇒ task dời khỏi màn ảo đỗ ngay trong lời gọi
     * `startActivity`, [ĐO nguồn] A10 r47 `ActivityStarter.java:2164-2170` gọi từ `:1572`): LẤY RA khỏi sổ NGAY — lượt mở vào
     * ô kế tiếp đi đường thường, không nhận một màn ảo trống rồi chờ nhịp đo — và nhả màn ảo sau [FORGET_DELAY_MS] (biên an
     * toàn: nhả khi còn activity trên đó thì cờ 256 DESTROY_CONTENT_ON_REMOVAL kết thúc nó). Luồng chính.
     */
    fun forget(pkg: String) {
        val p = ledger.take(pkg) ?: return
        Log.i(TAG, "quên $pkg display=${p.lease.displayId} (mở toàn màn) ⇒ ô 7 = ${ledger.pkgs()} · nhả màn ảo sau ${FORGET_DELAY_MS}ms")
        main.postDelayed({ drop(p, "fullscreen") }, FORGET_DELAY_MS)
    }

    private const val FORGET_DELAY_MS = 1_500L
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Gói đang đỗ, cũ nhất trước (nhật ký `KachiSlotLife`). */
    fun summary(): String = ledger.pkgs().toString()

    /** Nhả màn ảo đỗ [p] TRƯỚC (thôi vẽ vào bề mặt ẩn) rồi mới đóng bề mặt ẩn — cùng thứ tự `StagingDisplay.release`. */
    private fun drop(p: Parked, why: String) {
        SlotVdOwner.release(OWNER, p.key)
        p.sink.close()
        Log.i(TAG, "nhả ${p.pkg} display=${p.lease.displayId} ($why) ⇒ ô 7 = ${ledger.pkgs()}")
    }
}
