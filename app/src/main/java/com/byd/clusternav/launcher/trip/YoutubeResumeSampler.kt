package com.byd.clusternav.launcher.trip

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.tripConfig
import com.byd.clusternav.system.FreeformSeedStore
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nơi ghi bền bài phát tiếp ([YoutubeResume.KEY]) — tệp THEO XE `clusternav_state` (cùng chỗ sổ chuyến [TripLedgerStore]),
 * khai `ProfileScope.DEVICE_KEYS` + `SettingsCatalog.NOT_SETTINGS` qua [TripGate.DEVICE_KEYS]. Phạm vi xe: KDoc [YoutubeResume].
 *
 * `commit()` ĐỒNG BỘ (luồng nền): BYD giết Kachi ngay lúc tắt máy ([ĐO xe 29/09]) — đúng lúc mẫu cuối cùng là mẫu cần nhất.
 */
internal class YoutubeResumeStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FreeformSeedStore.PREF, Context.MODE_PRIVATE)

    fun read(): YoutubeResume.Saved? = YoutubeResume.decode(prefs.getString(YoutubeResume.KEY, null))

    @SuppressLint("ApplySharedPref")   // commit() đồng bộ là cố ý — xem KDoc lớp
    fun write(s: YoutubeResume.Saved): Boolean = prefs.edit().putString(YoutubeResume.KEY, YoutubeResume.encode(s)).commit()
}

/**
 * ═══ 2.94 · R3 — BÊN LƯU bài YouTube đang phát (luồng `kachi-yt-resume`) ═══════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-294-plan.html` R3 · §4.3. Mỗi [YoutubeResume.SAMPLE_EVERY_MS] trong suốt đời tiến trình launcher
 * (cài ở `KachiApplication.onCreate`, chỉ tiến trình chính): một lượt RẺ, dừng ở cổng sớm nhất —
 *  1. đang giữ ([hold]: chuyến lên xe vừa mở lại bài, chưa tua) ⇒ bỏ: không để mẫu ở giây 0 đè vị trí cần tua tới;
 *  2. không gì đang phát (`AudioManager.isMusicActive`, không binder phiên nào) ⇒ bỏ;
 *  3. hồ sơ đang dùng không cần phát tiếp ([YoutubeResume.wanted]) ⇒ bỏ — không dùng tính năng thì không ghi gì;
 *  4. đọc phiên qua CHÍNH [MediaBridge.lives] (không đường `MediaSessionManager` thứ hai) ⇒ app đích ĐANG PHÁT có tiêu đề
 *     ([YoutubeResume.sample]) ⇒ ghi.
 * 0 lệnh shell, 0 giao diện. Nhật ký: một dòng khi đổi bài, không thì tối đa mỗi [YoutubeResume.LOG_EVERY_MS] — ASCII,
 * KHÔNG tiêu đề (cùng luật dòng `KachiTrip`: không ghi nội dung người dùng nghe).
 *
 * Cờ [hold] nằm trong RAM là đúng chỗ (CLAUDE.md §5 chỉ cấm quyết định trạng thái HỆ THỐNG bằng cờ RAM): nó chỉ chặn chính
 * bên lưu của tiến trình này; tiến trình chết thì bên lưu chết theo.
 */
internal object YoutubeResumeSampler {

    private const val TAG = "KachiYtResume"

    /** Trần giữ — lỡ chuyến không gọi [release] (lối thoát sớm) thì tự hết. Phủ chờ phiên + quảng cáo + tua ([TripMusicResume]). */
    const val HOLD_MAX_MS = 180_000L

    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "kachi-yt-resume").apply { isDaemon = true } }
    private val started = AtomicBoolean(false)
    private val changePending = AtomicBoolean(false)

    @Volatile private var holdUntil = 0L
    @Volatile private var lastLogAt = 0L
    @Volatile private var bridge: MediaBridge? = null

    /** Một lần mỗi tiến trình (gọi lại = không làm gì). */
    fun install(ctx: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        try {
            exec.scheduleWithFixedDelay(
                { guarded { tick(app) } }, YoutubeResume.SAMPLE_EVERY_MS, YoutubeResume.SAMPLE_EVERY_MS, TimeUnit.MILLISECONDS,
            )
        } catch (e: RejectedExecutionException) {
            started.set(false)
            Log.e(TAG, "không hẹn được bên lưu", e)
            return
        }
        watchChanges(app)
    }

    /**
     * 2.97 · YT-SAVE-ON-CHANGE — nghe sự kiện ĐỔI BÀI của phiên app đích ([YoutubeResume.watchedPackages]) và lưu sau
     * [YoutubeResume.CHANGE_SETTLE_MS] (gộp nhiều lần đổi liền nhau), để mẫu cuối trước khi tắt máy là bài ĐANG nghe chứ không
     * phải bài trước (nhịp 60 s vẫn giữ để cập nhật vị trí). Cùng cổng của [tick] (giữ · đang phát · hồ sơ cần) — sự kiện chỉ
     * làm lượt lưu tới SỚM hơn. Không có quyền đọc thông báo ⇒ chỉ còn nhịp định kỳ như 2.96.
     */
    private fun watchChanges(app: Context) {
        val t = HandlerThread("kachi-yt-watch").apply { isDaemon = true; start() }
        val b = bridge ?: MediaBridge(app).also { bridge = it }
        // Soát Pass 4 [P2]: `null` = không nghe được (chưa có quyền) ⇒ tua muộn KHÔNG BAO GIỜ có sự kiện để bắn ⇒ không cho hẹn.
        watching = b.watchPackages(YoutubeResume.watchedPackages(), Handler(t.looper), onPlayback = { guarded { lateSeek() } }) {
            // R2d: đổi bài (quảng cáo ⇒ video) có thể không kèm đổi trạng thái phát ⇒ xét tua muộn ở đây nữa (rẻ: `late == null` ⇒ thoát).
            guarded { lateSeek() }
            if (!changePending.compareAndSet(false, true)) return@watchPackages
            try {
                exec.schedule({ changePending.set(false); guarded { tick(app) } }, YoutubeResume.CHANGE_SETTLE_MS, TimeUnit.MILLISECONDS)
            } catch (e: RejectedExecutionException) {
                changePending.set(false)
                Log.e(TAG, "không hẹn được lượt lưu khi đổi bài", e)
            }
        } != null
    }

    /**
     * Chuyến lên xe bắt đầu mở lại bài: thôi lưu tới khi [release] (trần [HOLD_MAX_MS]). Soát Pass 4 [P1]: một lượt phát tiếp MỚI
     * bắt đầu ⇒ lượt tua muộn của chuyến TRƯỚC (tiến trình sống qua lần tắt máy) hết nghĩa — bỏ, kẻo nó tua bài của chuyến này về
     * điểm cũ ở lần người lái bấm dừng/phát kế tiếp.
     */
    fun hold() {
        holdUntil = SystemClock.elapsedRealtime() + HOLD_MAX_MS
        synchronized(this) { if (late != null) { late = null; lateHoldUntil = 0L; Log.i(TAG, "late-seek dropped (new resume)") } }
    }

    /** Nhả cờ giữ của CHUYẾN (y 2.96). Cờ giữ của lượt tua muộn ([lateHoldUntil]) là cờ riêng — [lateSeek] tự nhả. */
    fun release() { holdUntil = 0L }

    @Volatile private var late: YoutubeLateSeek.Pending? = null

    /** Cờ giữ RIÊNG của tua muộn — tách khỏi [holdUntil] để [release] của chuyến không nhả nhầm (soát Pass 4 [P2], đua arm → SEEK → release). */
    @Volatile private var lateHoldUntil = 0L

    /** [watchChanges] có nghe được phiên không; không ⇒ [armLateSeek] từ chối (không có sự kiện nào để tua). */
    @Volatile private var watching = false

    /**
     * 2.97 · R2d — lượt phát tiếp chờ phiên hết hạn ([TripMusicResume.finish] `resume-wait-timeout`): giữ điểm tua, tua khi phiên
     * [pkg] THẬT phát đúng [title] (sự kiện phiên ở [watchChanges], không dò định kỳ). Giữ bộ lưu tới lúc đó (không ghi đè điểm
     * phát tiếp bằng giây 0). `false` = không đáng hẹn (không vị trí / không tiêu đề / không nghe được phiên) ⇒ bên gọi nhả như cũ.
     */
    fun armLateSeek(pkg: String, title: String?, seekMs: Long): Boolean {
        if (!YoutubeLateSeek.worth(title, seekMs)) return false
        if (!watching) { Log.i(TAG, "late-seek refused target=$pkg (no session watcher)"); return false }
        val until = SystemClock.elapsedRealtime() + YoutubeLateSeek.MAX_WAIT_MS
        synchronized(this) {
            late = YoutubeLateSeek.Pending(pkg, title.orEmpty(), seekMs, until)
            lateHoldUntil = until
        }
        Log.i(TAG, "late-seek armed target=$pkg seek=$seekMs")
        return true
    }

    /**
     * Một lượt xét tua muộn — gọi khi phiên đích đổi (luồng `kachi-yt-watch`) hoặc từ lượt xét lại duy nhất sau [YoutubeLateSeek.OTHER_GRACE_MS]
     * (luồng `kachi-yt-resume`). Quyết định ngoài khoá; đổi trạng thái trong khoá và chỉ khi [late] vẫn là chính [p] (lượt khác chưa chốt).
     */
    private fun lateSeek() {
        val p = late ?: return
        val b = bridge ?: return
        val now = SystemClock.elapsedRealtime()
        when (YoutubeLateSeek.decide(p, b.lives(), now)) {
            YoutubeLateSeek.Action.WAIT -> Unit
            YoutubeLateSeek.Action.SEEK -> {
                if (!settle(p, lateHold = now + YoutubeLateSeek.AFTER_SEEK_HOLD_MS)) return
                val ok = b.seekPackage(p.pkg, p.seekMs)
                Log.i(TAG, "late-seek fired target=${p.pkg} seek=${p.seekMs} ok=$ok")
            }
            YoutubeLateSeek.Action.CANCEL -> {
                if (settle(p, lateHold = 0L)) Log.i(TAG, "late-seek cancelled target=${p.pkg} (expired or other title)")
            }
            YoutubeLateSeek.Action.OTHER -> {
                if (p.otherSinceMs != 0L) return                         // đã hẹn lượt xét lại
                synchronized(this) { if (late !== p) return; late = p.copy(otherSinceMs = now) }
                try {
                    exec.schedule({ guarded { lateSeek() } }, YoutubeLateSeek.OTHER_GRACE_MS + TimeUnit.SECONDS.toMillis(1), TimeUnit.MILLISECONDS)
                } catch (e: RejectedExecutionException) {
                    Log.e(TAG, "không hẹn được lượt xét lại tua muộn", e)
                }
            }
        }
    }

    /** Chốt lượt tua muộn [p]: xoá + đặt cờ giữ riêng — `false` = lượt khác đã chốt / đã bỏ trước. */
    private fun settle(p: YoutubeLateSeek.Pending, lateHold: Long): Boolean = synchronized(this) {
        if (late !== p) return false
        late = null
        lateHoldUntil = lateHold
        true
    }

    private fun tick(app: Context) {
        if (SystemClock.elapsedRealtime() < maxOf(holdUntil, lateHoldUntil)) return
        if (!musicActive(app)) return
        if (!YoutubeResume.wanted(WorkspacePrefs(app).tripConfig().music)) return
        val b = bridge ?: MediaBridge(app).also { bridge = it }
        val lives = b.lives() ?: return
        val (target, live) = YoutubeResume.pick(lives, YoutubeResume.watchedTargets()) ?: return
        val s = YoutubeResume.sample(target, live, System.currentTimeMillis(), SystemClock.elapsedRealtime()) ?: return
        val store = YoutubeResumeStore(app)
        val prev = store.read()
        val ok = store.write(s)
        if (YoutubeResume.shouldLog(prev, s, lastLogAt)) {
            lastLogAt = s.savedAtMs
            Log.i(TAG, "saved target=${s.target} pos=${s.positionMs} dur=${s.durationMs} titleLen=${s.title.length} ok=$ok")
        }
    }

    private fun musicActive(app: Context): Boolean = try {
        app.getSystemService(AudioManager::class.java)?.isMusicActive == true
    } catch (e: RuntimeException) {
        false
    }

    /** Ranh giới lỗi một lượt nền (cùng mẫu `NlsHeal.guarded`): lọt ngoại lệ ra luồng hẹn giờ là lượt sau không bao giờ chạy. */
    private inline fun guarded(task: () -> Unit) {
        try {
            task()
        } catch (e: RuntimeException) {
            Log.e(TAG, "lượt lưu lỗi", e)
        }
    }
}
