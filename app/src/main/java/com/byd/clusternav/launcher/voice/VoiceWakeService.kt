package com.byd.clusternav.launcher.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import com.byd.clusternav.Prefs
import com.byd.clusternav.R
import com.byd.clusternav.launcher.EXTRA_START_VOICE
import com.byd.clusternav.launcher.KachiHomeActivity

/**
 * ═══ "Hey Kachi" — FOREGROUND-SERVICE micro nền (owner: nghe cả khi launcher KHÔNG hiện) ══════════════════════
 *
 * Android 10 bắt buộc **foreground-service** để thu micro khi app không ở tiền cảnh. Service này SỞ HỮU
 * [VoiceWakeListener] và quyết định **khi nào cho nghe**:
 *  • **Gate màn-sáng**: chỉ nghe khi màn hình SÁNG (xe đang dùng, xuyên mọi app); màn TẮT (xe ngủ/tắt) ⇒ luồng
 *    nghe **đỗ** (nhả mic, 0 % CPU — không huỷ luồng, xem KDoc [VoiceWakeListener]). Đăng ký `SCREEN_ON`/`SCREEN_OFF`.
 *  • **Công tắc**: chỉ chạy khi `Prefs.wakeEnabled` (mặc định TẮT). Owner tắt ⇒ [stopSelf].
 *  • **Cầu chì false-accept** (từ [VoiceWakeController]): nghe nhầm quá nhiều ⇒ [onAutoDisable] TẮT công tắc +
 *    báo + dừng — không để vòng wake loạn xạ.
 *
 * Wake nổ ⇒ mở [KachiHomeActivity] kèm extra `EXTRA_START_VOICE` (đưa Kachi lên + `voice.start()`), tái dùng
 * đúng đường nút mic. (v1: đưa launcher lên tiền cảnh; nghe-xuyên-app-không-đưa-lên là tinh chỉnh sau — cần xe.)
 *
 * ⚠ Lá chắn CPU CHÍNH nằm ở [VoiceWakeController]/[VoiceLoadGuard] bên trong listener; service chỉ lo vòng đời.
 */
class VoiceWakeService : Service() {

    private var listener: VoiceWakeListener? = null
    private val main = Handler(Looper.getMainLooper())

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_ON -> onScreen(true)
                Intent.ACTION_SCREEN_OFF -> onScreen(false)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenRx, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
    }

    /**
     * ⚠ **[startForeground] phải gọi TRƯỚC mọi đường thoát sớm.**
     *
     * `sync()` gọi `startForegroundService()`; nền tảng cho service **5 giây** để lên foreground, quá hạn là
     * `RemoteServiceException` — tức **sập app**. Bản đầu `return` khi công tắc đã tắt *trước khi* lên foreground:
     * ca ấy có thật (công tắc bị tắt trong khe giữa `sync()` và `onStartCommand`, hoặc một lượt `START_STICKY`
     * dựng lại service sau khi người dùng đã tắt). Lên foreground rồi `stopSelf()` ngay thì thông báo chỉ nhấp
     * một nhịp — đổi một nhịp nhấp lấy việc không sập là đổi đúng.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        // START_STICKY dựng lại (intent == null) cũng đi qua đây ⇒ công tắc được đọc lại mỗi lần, không tin cờ cũ.
        if (!enabled()) { stopListening(); stopSelf(); return START_NOT_STICKY }
        ensureListener()
        listener?.setListening(screenOn()) // màn đang tắt thì luồng đỗ, chờ SCREEN_ON
        return START_STICKY
    }

    /**
     * Gate màn-sáng — **không** dựng/huỷ luồng, chỉ bật/tắt việc nghe.
     *
     * Luồng nghe đỗ trong `wait()` khi màn tắt: 0 % CPU, mic đã nhả, mà không có khe hở nào cho hai luồng cùng
     * tồn tại (xem KDoc [VoiceWakeListener] — bật/tắt màn nhanh tay từng để lại luồng mồ côi giữ chốt micro).
     */
    private fun onScreen(on: Boolean) {
        if (!enabled()) { stopListening(); stopSelf(); return }
        ensureListener()
        listener?.setListening(on)
        Log.i(TAG, if (on) "wake listening bật (màn sáng)" else "wake listening tạm nghỉ (màn tắt)")
    }

    private fun ensureListener() {
        if (listener?.isRunning() == true) return
        listener = VoiceWakeListener(
            ctx = applicationContext,
            onWake = { main.post { fireWake() } },
            onAutoDisable = { main.post { autoDisable() } },
        ).also { it.start() }
    }

    private fun stopListening() {
        listener?.stop(); listener = null
    }

    /**
     * Wake nổ: đưa Kachi lên + mở phiên nghe lệnh (đúng đường nút mic — `singleTask` ⇒ `onNewIntent`).
     *
     * ⚠ **[SUY] Android 10 chặn start-activity từ nền**, và một foreground-service **không** phải một miễn trừ.
     * Miễn trừ mà Kachi dựa vào là `SYSTEM_ALERT_WINDOW` (đã có, và bắt buộc phải có vì tấm chữ voice/bóng cast
     * là cửa sổ overlay) và ca "app đang là HOME/đang hiện". Nếu bị chặn thì hệ **không ném** — chỉ một dòng
     * logcat của nền tảng — nên `runCatching` ở đây không phát hiện được. Đây là mục **phải đo trên xe** (V-oncar
     * của spec), không phải thứ tự nhận là chạy.
     */
    private fun fireWake() {
        runCatching {
            startActivity(
                Intent(this, KachiHomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_START_VOICE, true),
            )
        }.onFailure { Log.w(TAG, "fireWake mở activity lỗi", it) }
    }

    /** Cầu chì false-accept: tắt công tắc + báo + dừng service (owner OQ5). */
    private fun autoDisable() {
        runCatching { Prefs.setWakeEnabled(this, false) }
        runCatching { Toast.makeText(this, getString(R.string.kachi_wake_auto_off), Toast.LENGTH_LONG).show() }
        stopListening(); stopSelf()
    }

    override fun onDestroy() {
        stopListening()
        runCatching { unregisterReceiver(screenRx) }
        super.onDestroy()
    }

    private fun enabled(): Boolean = runCatching { Prefs.wakeEnabled(this) }.getOrDefault(false)
    private fun screenOn(): Boolean = runCatching {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    }.getOrDefault(true)

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.kachi_wake_notif_title), NotificationManager.IMPORTANCE_MIN))
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle(getString(R.string.kachi_wake_notif_title))
            .setContentText(getString(R.string.kachi_wake_notif_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WakeSvc"
        private const val CHANNEL = "kachi_wake"
        private const val NOTIF_ID = 4801

        /** Bật/tắt service theo công tắc — gọi từ UI + boot. Tắt công tắc ⇒ service tự stopSelf ở onStartCommand. */
        fun sync(ctx: Context) {
            val i = Intent(ctx, VoiceWakeService::class.java)
            if (runCatching { Prefs.wakeEnabled(ctx) }.getOrDefault(false)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
            } else {
                runCatching { ctx.stopService(i) }
            }
        }
    }
}
