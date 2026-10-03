package com.byd.clusternav.launcher.voice

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.R
import com.byd.clusternav.launcher.LangHost

/**
 * ═══ FIX286 · VK2 — chế độ HOLD của `:wake`: giữ mô hình nạp sẵn cho phím vô-lăng gán Kachi nghe ═══════════════════
 *
 * Spec `docs/specs/kachi-286-field-fixes.html` R-VK (phương án S2 B′ — phiên điều phối chọn). Tách khỏi
 * `VoiceWakeService.kt` (492 dòng, trần 500 — global §4.1) theo VAI *"chế độ + nạp sẵn + thông báo"*; service chỉ còn
 * vòng đời và rẽ nhánh theo [VoiceWakeMode].
 *
 * HOLD = `wake TẮT ∧ có phím gán Kachi nghe`: FGS sống (thông báo chữ riêng), **không** mở micro, **không** dựng bộ
 * nghe câu gọi, mô hình nạp sẵn ở `:wake` qua [VoiceEngine.preload] (`inWake = true`: chỉ cổng RAM, luồng nền ưu tiên
 * thấp), đứng xuống = KEEP. Lần bấm phím sau đó dùng bản có sẵn — tái dùng đúng đường "mô hình đã nằm ở `:wake`" mà
 * wake-BẬT đã chạy trên xe ([ĐO xe 26/09] *"sẵn sàng nghe sau 216 ms"*, `logcat-stream-2.70.txt:4639`; §6: không dựng
 * cơ chế thứ hai). Ca phím + HOLD trên xe anh em: [CHƯA BIẾT] tới OC-VK1.
 *
 * ⚠ Tệp này chạy ở `:wake` ⇒ cùng ràng buộc với `VoiceWakeService.kt`: không `AppContainer`, không `Prefs.set*` (trừ
 * cầu chì), không READER tự ghi (`Prefs.voiceKeyBindings`) — bài canh `VoiceWakeIsolationContractTest` liệt kê tệp này.
 */
internal object VoiceWakeHold {

    private const val TAG = "WakeSvc"

    /** Kênh thông báo — dùng chung cho mọi chế độ (một FGS, một kênh; chữ đổi theo chế độ). */
    private const val CHANNEL = "kachi_wake"

    /**
     * Chế độ của `:wake`, quyết từ ẢNH CHỤP mà tiến trình chính ghi (`VoiceWakePrefsMain.publish`), KHÔNG từ cache
     * `SharedPreferences` của `:wake` — [ĐO AOSP r47 `ContextImpl.java:447-474`] cache ấy không bao giờ nạp lại, nên
     * `:wake` đang HOLD sẽ không thấy người dùng vừa bật "Hey Kachi". Cầu chì false-accept là tệp marker ⇒ đọc tươi.
     * Ảnh chụp chưa có (máy vừa nâng cấp, chưa mở màn chính) ⇒ hành vi 2.85 ([VoiceWakePrefs.mode]).
     */
    fun modeInWake(ctx: Context): VoiceWakeMode =
        VoiceGrammarSnapshotStore.read(ctx).wake.mode(
            ownWakeEffective = Prefs.wakeEnabled(ctx),
            fuseTripped = Prefs.wakeServiceDisabled(ctx),
        )

    /** Prefs tươi cho dispatcher của phiên `:wake` (VK4) — đọc TỆP mỗi lần gọi, không cache. */
    fun prefs(ctx: Context): VoiceWakePrefs = VoiceGrammarSnapshotStore.read(ctx).wake

    /**
     * Vào HOLD: nạp sẵn mô hình ở `:wake`. An toàn khi gọi lặp (mỗi `sync` từ `onResume` màn chính): đã nạp ⇒
     * [VoiceEngine.recognizer] trả ngay; đang có lượt nạp ⇒ `preloading` bỏ qua. Không bao giờ chặn luồng gọi.
     */
    fun holdModel(ctx: Context) {
        if (VoiceEngine.loaded()) return   // đã nằm sẵn — mỗi `sync`/LISTEN_NOW đi qua đây, không lặp log
        Log.i(TAG, "HOLD — giữ mô hình cho phím vô-lăng (không micro, không bộ nghe câu gọi) · đang nạp=${VoiceEngine.loading()}")
        VoiceEngine.preload(ctx, inWake = true)
    }

    /**
     * FIX286 · VK3 (S1) — nhả mô hình sau khi đứng xuống đã quyết, **KHÔNG CHẶN** luồng chính: [VoiceEngine.tryRelease]
     * kiểm lại — dưới khoá dựng — rằng phiên của tiến trình vẫn là phiên [epoch] lúc quyết và đã IDLE (một LISTEN_NOW
     * chen vào ⇒ không nhả dưới chân nó). Cấm `VoiceEngine.release()` ở đây (chờ cả lượt nạp 9–34 s trên luồng chính)
     * và cấm chuyển nó sang luồng nền trần (phiên B lấy mô hình rồi bị nhả ⇒ "không nghe thấy" — KDoc `ModelHolder`).
     *
     * @return `true` = xong (đã nhả / không có gì để nhả / nhả lỗi đã log) ⇒ chỗ gọi đứng xuống tiếp; `false` = bận
     *   (đang nạp · đang giải mã · phiên đã đổi) ⇒ hỏi lại nhịp sau.
     */
    fun releaseModel(epoch: Int): Boolean {
        val r = runCatching {
            VoiceEngine.tryRelease { VoiceWakeSessions.epoch() == epoch && VoiceWakeSessions.phase() == VoiceTurnPhase.IDLE }
        }.onFailure { Log.w(TAG, "nhả recognizer lỗi", it) }.getOrNull()
        val done = r != ModelHolder.Release.BUSY && r != ModelHolder.Release.SKIPPED
        Log.i(TAG, "nhả mô hình (không chặn): ${r ?: "lỗi"}" + if (done) "" else " — hỏi lại nhịp sau")
        return done
    }

    /**
     * spec `kachi-i18n-zh-th-ms.html` R9 — Context TÀI NGUYÊN theo tiếng GIAO DIỆN người dùng, cho chữ của chính `:wake`
     * (thông báo FGS dưới · toast cầu chì ở `VoiceWakeService.autoDisable`). `:wake` không có Activity nên không đi qua
     * `LangHost.wrap`: tài nguyên của nó theo locale MÁY — có `values-zh-rCN` rồi thì xe đặt tiếng Trung + người dùng
     * chọn English sẽ ra thông báo tiếng Trung. Tiếng đọc từ ảnh chụp ngữ pháp (tiến trình chính ghi, đã giải nghĩa);
     * KHÔNG ghi `Strings.current`, KHÔNG gọi `WorkspacePrefs.langMode()` (tự ghi khi migrate). Ảnh chụp chưa mang tiếng
     * ⇒ [ctx] nguyên vẹn = hành vi cũ. Chỉ dùng cho `getString` — dịch vụ hệ thống vẫn xin qua [ctx].
     */
    fun uiRes(ctx: Context): Context =
        VoiceGrammarSnapshotStore.read(ctx).uiLang?.let { LangHost.localized(ctx, it) } ?: ctx

    /** Thông báo FGS theo chế độ: WAKE giữ chữ cũ ("Hey Kachi / Đang nghe câu gọi"); HOLD/OFF nói đúng việc phím. */
    fun notification(ctx: Context, mode: VoiceWakeMode): Notification {
        // minSdk 29 ⇒ kênh thông báo luôn có (lint ObsoleteSdkInt của hai nhánh `SDK_INT >= O` cũ — dọn cùng lượt dời tệp).
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val res = uiRes(ctx)   // i18n R9 — chữ theo tiếng người dùng chọn, không theo locale máy
        nm.createNotificationChannel(NotificationChannel(CHANNEL, res.getString(R.string.kachi_wake_notif_title), NotificationManager.IMPORTANCE_MIN))
        val b = Notification.Builder(ctx, CHANNEL)
        val wake = mode == VoiceWakeMode.WAKE
        return b.setContentTitle(res.getString(if (wake) R.string.kachi_wake_notif_title else R.string.kachi_wake_hold_notif_title))
            .setContentText(res.getString(if (wake) R.string.kachi_wake_notif_text else R.string.kachi_wake_hold_notif_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
    }
}

/**
 * Một chỗ đọc `runningAppProcesses` (từ API 21 chỉ trả tiến trình của CHÍNH gói). Lỗi ⇒ `false` = coi như lạnh. Dời
 * nguyên văn khỏi companion của `VoiceWakeService` (trần 500 dòng); `isProcessAlive`/`isMainProcessAlive` vẫn ở đó.
 */
internal fun processAlive(ctx: Context, name: String): Boolean = runCatching {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    am.runningAppProcesses?.any { it.processName == name } == true
}.getOrDefault(false)

/**
 * Cùng khuôn `VoiceKeyKeepAliveService.startForegroundOnce` (7 FGS khác) — `false` ⇒ chỗ gọi `stopSelf`. Dời khỏi
 * `VoiceWakeService.kt` (trần 500 dòng) cùng lượt VK2; chữ thông báo theo chế độ hiện hành ([VoiceWakeHold.notification]).
 */
internal fun VoiceWakeService.startForegroundOnce(): Boolean = runCatching {
    startForeground(VoiceWakeService.NOTIF_ID, VoiceWakeHold.notification(this, VoiceWakeHold.modeInWake(this)))
    true
}.getOrElse { Log.e("WakeSvc", "startForeground bị từ chối", it); false }
