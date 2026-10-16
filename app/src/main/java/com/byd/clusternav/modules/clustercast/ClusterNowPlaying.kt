package com.byd.clusternav.modules.clustercast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Prefs
import com.byd.clusternav.clearNowPlayingMarker
import com.byd.clusternav.comfort.SeatAutoOffGuard
import com.byd.clusternav.launcher.CtlJournalStore
import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.modules.clustercast.nowplaying.ClusterNowPlayingPlan
import com.byd.clusternav.modules.clustercast.nowplaying.NowPlayingMedia
import com.byd.clusternav.modules.clustercast.nowplaying.NowPlayingOwner
import com.byd.clusternav.modules.clustercast.nowplaying.NowPlayingSink
import com.byd.clusternav.modules.clustercast.nowplaying.NowPlayingStatus
import com.byd.clusternav.modules.hal.BydHal
import com.byd.clusternav.modules.hal.BydHalContentPush
import com.byd.clusternav.nowPlayingEnabled
import com.byd.clusternav.nowPlayingMarker
import com.byd.clusternav.saveNowPlayingMarker
import com.byd.clusternav.setNowPlayingEnabled

/**
 * ═══ 3.1.11 · CLUSTER-NOW-PLAYING — phần CHẠM HỆ THỐNG: nghe phiên nhạc → [ClusterNowPlayingPlan] → ghi HAL cụm ═══════════
 *
 * Doc `docs/diagnostics/cluster-now-playing-2026-10-16.md` §4.A + §7. Tính năng NGỦ: công tắc mặc định TẮT.
 *  • **TẮT ⇒ 0 việc**: không luồng, không bộ nghe phiên, không HAL. [install] chỉ đọc prefs; luồng `kachi-np` chỉ dựng khi
 *    công tắc BẬT hoặc còn dấu bền cần trả.
 *  • **BẬT**: một bộ nghe phiên ([MediaBridge.watchAll], cơ chế bám/gỡ có sẵn) — mỗi sự kiện đi qua plan, plan nuốt sự kiện trùng;
 *    ~3 lượt ghi binder mỗi bài; một lượt hẹn DUY NHẤT khi tạm dừng (không nhịp).
 *  • **Ghi**: đường HAL chuẩn có cache từ chối ([BydHalContentPush.pushMusicInt]/[BydHalContentPush.pushMusicBytes]) trên `BYDAutoInstrumentDevice` (device 1007) qua
 *    bypass-context — cùng công thức đường nav. Mỗi lượt để một dòng `np …` ở nhật ký lệnh ghi xe ([CtlJournalStore], logcat
 *    `KachiCtl` + `ctllog` của cầu kiểm thử): id, giá trị, rc, đọc lại.
 *
 * CLAUDE.md §4 — bốn câu: (1) display nào: KHÔNG display (tín hiệu HAL, không `am`/`wm`); (2) app nào: không đụng app — chỉ
 * ĐỌC phiên; (3) stack nào: không lệnh stack; (4) hoàn tác: ghi lại nguồn cũ (dấu bền ghi TRƯỚC, dọn ở mọi lối, trả lúc khởi
 * động nếu tiến trình chết giữa chừng — [ClusterNowPlayingPlan.restoreAtStartup]).
 */
object ClusterNowPlaying {

    private const val TAG = "KachiNowPlaying"

    @Volatile private var app: Context? = null
    @Volatile private var handler: Handler? = null
    @Volatile private var plan: ClusterNowPlayingPlan? = null
    @Volatile private var unwatch: (() -> Unit)? = null
    @Volatile private var noAccess = false
    private var shutdownRx: BroadcastReceiver? = null
    private var device: Any? = null
    private val owners = HashMap<String, Boolean>()
    private val pauseCheck = Runnable { plan?.onPauseCheck(SystemClock.elapsedRealtime()) }

    /** `KachiApplication.onCreate` (tiến trình chính). TẮT và không dấu ⇒ không dựng gì. */
    fun install(ctx: Context) {
        val a = ctx.applicationContext
        app = a
        val marker = Prefs.nowPlayingMarker(a)
        val on = Prefs.nowPlayingEnabled(a)
        if (marker == null && !on) return
        post(a) {
            ClusterNowPlayingPlan.restoreAtStartup(marker, sink(a))
            if (on) start(a)
        }
    }

    /** Công tắc ở Cài đặt / cầu kiểm thử — ghi pref rồi bật/tắt bộ nghe trên luồng `kachi-np`. */
    fun setEnabled(ctx: Context, on: Boolean) {
        val a = ctx.applicationContext
        app = a
        Prefs.setNowPlayingEnabled(a, on)
        if (!on && handler == null) return                   // chưa từng bật trong tiến trình này ⇒ không có gì để trả
        post(a) { if (on) start(a) else stopWatching() }
    }

    fun enabled(ctx: Context): Boolean = Prefs.nowPlayingEnabled(ctx.applicationContext)

    /** Trạng thái cho dòng mô tả + cầu kiểm thử. */
    fun status(ctx: Context): NowPlayingStatus {
        plan?.status?.let { s ->
            // `noAccess` chỉ đúng khi đang BẬT mà không bám được phiên (gỡ ở [stopWatching] trước khi plan về OFF).
            return if (noAccess) s.copy(kind = NowPlayingStatus.Kind.NO_ACCESS) else s
        }
        val supported = ClusterProfile.resolveCached(ctx.applicationContext).nowPlaying != null
        return NowPlayingStatus(if (supported) NowPlayingStatus.Kind.OFF else NowPlayingStatus.Kind.UNSUPPORTED)
    }

    private fun start(a: Context) {
        if (unwatch != null) return
        val ids = ClusterProfile.resolveCached(a).nowPlaying
        val p = ClusterNowPlayingPlan(ids, sink(a)).also { plan = it }
        p.onSwitch(true, SystemClock.elapsedRealtime())
        if (ids == null) { Log.i(TAG, "np unsupported (ho so doi xe khong co so lieu cum)"); return }
        val h = handler ?: return
        val w = MediaBridge(a).watchAll(h) { evaluate(a) }
        noAccess = w == null
        unwatch = w
        if (w == null) { Log.w(TAG, "np: chua co quyen doc thong bao — khong nghe duoc phien"); return }
        registerShutdown(a)
        Log.i(TAG, "np watching")
    }

    private fun stopWatching() {
        handler?.removeCallbacks(pauseCheck)
        unwatch?.invoke()
        unwatch = null
        noAccess = false
        plan?.onSwitch(false, SystemClock.elapsedRealtime())
        app?.let { a -> shutdownRx?.let { runCatching { a.unregisterReceiver(it) } } }
        shutdownRx = null
        Log.i(TAG, "np stopped")
    }

    private fun evaluate(a: Context) {
        val p = plan ?: return
        val np = MediaBridge(a).nowPlaying()
        val media = np?.let { (pkg, title, playing) -> NowPlayingMedia(pkg, thirdParty(a, pkg), playing, title) }
        val delay = p.onMedia(media, { powerLevel(a) }, SystemClock.elapsedRealtime()) ?: return
        handler?.let { h -> h.removeCallbacks(pauseCheck); h.postDelayed(pauseCheck, delay + PAUSE_SLACK_MS) }
    }

    /** Phép quyết generic ([NowPlayingOwner]) — nhớ theo gói trong tiến trình (cờ cài đặt không đổi khi app đang chạy). */
    private fun thirdParty(a: Context, pkg: String): Boolean = owners.getOrPut(pkg) {
        runCatching {
            val pm = a.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            NowPlayingOwner.thirdParty(
                systemImage = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                updatedSystem = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                platformSigned = pm.checkSignatures("android", pkg) == PackageManager.SIGNATURE_MATCH,
                uid = ai.uid,
            )
        }.getOrDefault(false)
    }

    private fun powerLevel(a: Context): Int? =
        runCatching { SeatAutoOffGuard.raw(AppContainer.get(a).telemetryText("power_level")) }.getOrNull()

    private fun registerShutdown(a: Context) {
        if (shutdownRx != null) return
        val rx = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val pending = goAsync()
                val h = handler
                if (h == null || !h.post { try { plan?.stop(SystemClock.elapsedRealtime()) } finally { pending.finish() } }) pending.finish()
            }
        }
        runCatching { a.registerReceiver(rx, IntentFilter(Intent.ACTION_SHUTDOWN)) }.onSuccess { shutdownRx = rx }
    }

    /**
     * Luồng `kachi-np` dựng LƯỜI (lần `post` đầu) và KHÔNG tự tắt: tắt công tắc chỉ gỡ bộ nghe + hẹn; luồng đỗ ở looper (0 CPU, không
     * nhịp). Cố ý không `quitSafely` ở [stopWatching] — lượt BẬT lại đua với lượt tắt đang chạy trên chính luồng ấy có thể rơi
     * `start` vào looper đã đóng (mất bộ nghe, im lặng). Mặc định TẮT + không dấu ⇒ luồng không bao giờ được dựng (Soát Pass 12).
     */
    private fun post(a: Context, block: () -> Unit) {
        val h = handler ?: synchronized(this) {
            handler ?: Handler(HandlerThread("kachi-np").apply { isDaemon = true; start() }.looper).also { handler = it }
        }
        h.post {
            try {
                block()
            } catch (e: RuntimeException) {
                Log.e(TAG, "np luot loi", e)                    // ranh giới lỗi: lọt ra là luồng chết, lượt sau không chạy
            }
        }
        app = a
    }

    /** Cửa ra HAL + prefs + nhật ký — mọi hàm không ném. */
    private fun sink(a: Context): NowPlayingSink = object : NowPlayingSink {
        private fun dev(): Any? = device ?: runCatching {
            BydHal.device(BydHal.INSTRUMENT, BydHal.systemBypassContext(), BydHal.bypass(a))
        }.getOrNull().also { device = it }

        override fun readInt(id: Int): Int? = runCatching {
            dev()?.let { d -> HalBindingTable.coerceInt(BydHal.readFeature(d, id)) }
        }.getOrNull()

        override fun writeInt(id: Int, value: Int): String = dev()?.let { BydHalContentPush.pushMusicInt(it, id, value) } ?: NO_DEVICE

        override fun writeBytes(id: Int, bytes: ByteArray): String = dev()?.let { BydHalContentPush.pushMusicBytes(it, id, bytes) } ?: NO_DEVICE

        override fun saveMarker(marker: String): Boolean = runCatching { Prefs.saveNowPlayingMarker(a, marker) }.getOrDefault(false)

        override fun clearMarker() { runCatching { Prefs.clearNowPlayingMarker(a) } }

        override fun log(line: String) = CtlJournalStore.journal(a).write(line)
    }

    /** rc khi không có device HAL (off-car / máy ảo) — chữ ASCII cho dòng nhật ký. */
    const val NO_DEVICE = "no-device"

    /** Lượt kiểm sau tạm dừng đến muộn hơn ân hạn một chút (tránh trượt mép `>=`). */
    private const val PAUSE_SLACK_MS = 200L
}
