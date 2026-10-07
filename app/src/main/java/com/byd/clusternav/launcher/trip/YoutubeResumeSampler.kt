package com.byd.clusternav.launcher.trip

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
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
        }
    }

    /** Chuyến lên xe bắt đầu mở lại bài: thôi lưu tới khi [release] (trần [HOLD_MAX_MS]). */
    fun hold() { holdUntil = SystemClock.elapsedRealtime() + HOLD_MAX_MS }

    fun release() { holdUntil = 0L }

    private fun tick(app: Context) {
        if (SystemClock.elapsedRealtime() < holdUntil) return
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
