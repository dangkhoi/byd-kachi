package com.byd.clusternav.launcher

import android.app.ActivityOptions
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.SurfaceView
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
 * — không đi theo mặt vẽ. Đổi CỠ thì có phát (`resizeLocked` A10 `:303-314`). [SUY] đỗ / nhận lại CÙNG cỡ: app không nhận đổi
 * cấu hình nào ⇒ nhạc chạy tiếp — 🚗 chờ owner thử (§4.6d). 2.91 · F2: nhận lại vào ô KHÁC cỡ ⇒ màn ảo đổi cỡ theo ô (cùng
 * display, cùng tiến trình — một lượt đổi cấu hình như mọi ô khi inset đổi), xem [claim].
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
     * chuyển chủ + ghi sổ. Từ đây app được đối xử y như app đỗ từ ô: đặt vào ô ⇒ [claim] (đổi mặt vẽ; màn ảo ẩn mang cỡ display 0
     * ⇒ đổi cỡ theo ô — 2.91 · F2, một lượt đổi cấu hình cùng display, KDoc [claim]), mở toàn
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
     * Kết quả [claim]: [parked] = đã LẤY RA và GẮN vào mặt vẽ ô (host nhận màn ảo) · [resize] = cỡ ô ≠ cỡ màn ảo đỗ ⇒ host PHẢI
     * đổi cỡ màn ảo theo ô ngay sau khi nhận ([SlotParkPlan.ClaimStep.ATTACH_RESIZE]). `claim` trả `null` = đường thường.
     */
    class Claim internal constructor(val parked: Parked, val resize: Boolean)

    /**
     * ═══ Nhận lại vào ô (luồng chính; `surfaceChanged` của host CHƯA có màn ảo) — 2.91 · F2, spec `kachi-291-small-fixes.html` §4.2 ═══
     *
     * Bảng quyết thuần: [SlotParkPlan.claim]. Có bản đỗ ⇒ lấy ra + đổi mặt vẽ sang mặt vẽ ô [sv] (đang ở CỠ Ô — 2.91 bỏ ghim
     * `setFixedSize` + khung viền của PARK-1, nguồn của viền đen [ĐO máy ảo QA 05/10]); khác cỡ ⇒ [Claim.resize] để host gọi
     * `VdAppHost.resize` (đường đổi cỡ DUY NHẤT của ô) ngay trong cùng lượt.
     *
     * Vì sao gắn TRƯỚC rồi đổi cỡ SAU (không ngược lại) [ĐO nguồn A10: framework r47, native LineageOS 17.1 = nhánh A10]:
     * `VirtualDisplayAdapter.java:303-314` `resizeLocked` chỉ
     * đặt `PENDING_RESIZE` + hẹn traversal; nếu traversal chạy khi màn ảo còn vẽ vào `ImageReader` cỡ cũ của ô 7 thì SurfaceFlinger đổi
     * cỡ đệm của CHÍNH bề mặt ẩn đó (`SurfaceFlinger.cpp:2732-2733` → `VirtualDisplaySurface.cpp:288-292`). Gắn trước: lượt đổi mặt
     * vẽ dựng lại thiết bị (`SurfaceFlinger.cpp:2709-2721`) và `VirtualDisplaySurface` đọc cỡ mặt vẽ = cỡ ô (`:89-92`); lượt đổi
     * cỡ sau đó đưa nội dung về cùng cỡ — đúng thứ tự `v.surface = h.surface; resize(w, ht)` mà nhánh thường của `surfaceChanged`
     * đã chạy ngoài hiện trường. Cả hai cờ chờ có thể rơi chung một traversal: `performTraversalLocked` áp cỡ TRƯỚC mặt vẽ (`:281-287`).
     * Lấy ra hỏng / gắn hỏng ⇒ `null` (đường thường; [attach] đã nhả màn ảo).
     *
     * Đổi cỡ kéo theo MẬT ĐỘ của ô mới (`VdAppHost.resize` → `SlotDensity.forTablet` theo cạnh ngắn): hai ô có cạnh ngắn hai bên
     * ngưỡng sw600 thì mật độ cũng đổi (vd nền 200 dpi: ô 1129×610 → 162 dpi, ô 1129×804 → 200 dpi). [ĐO nguồn r47
     * `ActivityRecord.java`] activity DỰNG LẠI khi có thay đổi nó không khai trong `configChanges` (`:3291`, `:3377`); đổi cỡ màn
     * chỉ được tính khi vượt ngưỡng tài nguyên của chính app (`:3398-3412`), mật độ thì luôn tính ⇒ ca có đổi mật độ dễ dựng lại
     * activity hơn ca chỉ đổi cỡ (cùng tiến trình, task không dời). Nhạc có còn hay không: 🚗 OC-291-2 / OQ1.
     */
    fun claim(sv: SurfaceView, pkg: String?, owner: String, slot: Int, w: Int, h: Int): Claim? {
        // Review 2.91 Pass 1 [P3]: LẤY RA trước rồi mới quyết theo CHÍNH bản sẽ gắn. Sổ có khoá, nhưng `adoptHidden` ghi từ luồng
        // `kachi-behind`: xem rồi mới lấy là HAI lượt khoá — bản đỗ cùng gói bị thay giữa hai lượt (SAME_PKG) thì bước tính theo cỡ
        // bản CŨ, có thể bỏ đổi cỡ cho bản MỚI (cỡ display 0) ⇒ lại lệch khung.
        val taken = pkg?.let { take(it) } ?: return null
        val step = SlotParkPlan.claim(taken.width, taken.height, w, h)
        if (!attach(taken, sv.holder.surface, owner, slot)) return null
        val resize = step == SlotParkPlan.ClaimStep.ATTACH_RESIZE
        if (resize) Log.i(TAG, "nhận lại ${taken.pkg}: ô ${w}x$h ≠ màn ảo ${taken.width}x${taken.height} ⇒ đổi cỡ màn ảo theo ô (cùng display)")
        return Claim(taken, resize)
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
