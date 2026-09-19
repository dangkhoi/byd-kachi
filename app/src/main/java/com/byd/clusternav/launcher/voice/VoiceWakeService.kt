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
import android.os.SystemClock
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
 * ## ⚠ Service này chạy ở TIẾN TRÌNH RIÊNG `:wake` (manifest `android:process`, xem [PROCESS_SUFFIX])
 * Cùng lẽ với `:tts` của 1.79: bộ nghe cầm `KeywordSpotter` của sherpa-onnx — **cùng** `libonnxruntime.so` mà
 * [ĐO tombstone xe 2026-09-18] đã một lần SIGSEGV và giết cả tiến trình launcher (⇒ a11y unbind ⇒ phím gán chết).
 * SIGSEGV native không bắt được bằng `runCatching` trong cùng tiến trình ⇒ ranh giới tiến trình là bản vá duy
 * nhất khả thi. Hai hệ quả phải nhớ:
 *  • **[sync] gọi TỪ tiến trình launcher**, nên nó chỉ được dùng `startForegroundService`/`stopService` (nền tảng
 *    tự dựng `:wake`) — KHÔNG được chạm trực tiếp vào [listener].
 *  • **[VoiceSingleFlight] KHÔNG còn bắc qua hai tiến trình** (nó là state tĩnh trong một tiến trình). Nên việc
 *    nhường micro cho phiên lệnh nay đi theo **THỜI GIAN**, không theo chốt — xem KDoc [fireWake].
 *
 * ⚠ Lá chắn CPU CHÍNH nằm ở [VoiceWakeController]/[VoiceLoadGuard] bên trong listener; service chỉ lo vòng đời.
 */
class VoiceWakeService : Service() {

    private var listener: VoiceWakeListener? = null
    private val main = Handler(Looper.getMainLooper())

    /**
     * Mốc **hết hạn** của lượt nhường micro (`elapsedRealtime`); `0` = không nhường. Xem KDoc [fireWake].
     *
     * ## ⚠ Vì sao lượt nhường cần một MỐC, không chỉ cần một hẹn giờ (soát 2026-09-19)
     * Lượt nhường được biểu diễn bằng `listening = false` — mà `listening` cũng là **cổng màn-sáng** và cũng bị
     * [onStartCommand] đặt lại mỗi lần service được start. Nên nếu chỉ có hẹn giờ thì **hai đường chẳng liên
     * quan** vẫn lặng lẽ **huỷ** lượt nhường giữa lúc phiên lệnh đang ghi:
     *  • `SCREEN_ON` trong 18 s (người lái bấm nút nguồn, hay màn vừa hết giờ rồi được chạm) ⇒ [onScreen] bật nghe.
     *  • một cú `sync()` bất kỳ (gạt công tắc, boot, tải xong model) ⇒ [onStartCommand] bật nghe.
     * Cả hai đều dẫn tới đúng triệu chứng mà lượt tách `:wake` sinh ra để chặn: hai tiến trình cùng UID cùng mở
     * `AudioRecord`, và người lái thấy *"gọi được nhưng nó không nghe mình nói gì"*.
     *
     * Mốc này **tự hết hạn**, nên nó không tạo được ngõ cụt: quá hạn thì mọi đường bật nghe lại chạy như thường,
     * kể cả khi [resumeTask] đã bị bỏ (màn tối đúng lúc hết hạn ⇒ `SCREEN_ON` sau đó bật lại bình thường).
     *
     * ⚠ Khai **TRƯỚC** [resumeTask] có chủ ý: thứ tự khởi tạo property trong Kotlin là thứ tự KHAI, và dự án đã
     * cắn lỗi "callback chạm field chưa gán" hai lần (`AndroidTtsSpeaker`). Mọi cờ mà một callback có thể chạm
     * phải khai trước cái callback đó.
     */
    private var handoffUntil = 0L

    /**
     * Nghe LẠI sau khi đã nhường micro cho một phiên lệnh — xem KDoc [fireWake].
     *
     * Xoá mốc nhường **trước** khi kiểm hai cổng: hết hạn là hết hạn, dù lượt này có bật nghe lại được hay không
     * (màn có thể đã tối) — nếu không thì một lần màn tối đúng lúc hết hạn sẽ khoá [handoffUntil] lại mãi.
     */
    private val resumeTask = Runnable {
        handoffUntil = 0L
        if (enabled() && screenOn()) {
            ensureListener()
            listener?.setListening(true)
            Log.i(TAG, "nghe lại sau khi nhường micro cho phiên lệnh")
        }
    }

    /** Đang trong lượt nhường micro cho phiên lệnh? (mốc tự hết hạn — xem [handoffUntil]) */
    private fun handoffActive(): Boolean =
        handoffUntil > 0L && SystemClock.elapsedRealtime() < handoffUntil

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
        // Model KWS vừa tải xong ⇒ phải DỰNG LẠI bộ nghe: luồng cũ đã chốt "không có model" cho cả vòng đời của
        // nó (xem KDoc [EXTRA_RELOAD]). `stop()` join ≤ 700 ms — chấp nhận được trên luồng main của một tiến
        // trình nền không có UI.
        if (intent?.getBooleanExtra(EXTRA_RELOAD, false) == true) {
            Log.i(TAG, "dựng lại bộ nghe để nạp model KWS mới")
            stopListening()
        }
        ensureListener()
        // Màn đang tắt thì luồng đỗ, chờ SCREEN_ON. Và nếu đang nhường micro cho một phiên lệnh thì **vẫn đỗ**:
        // một cú `sync()` (gạt công tắc / boot / tải xong model) không được phép giành lại mic giữa lúc người lái
        // đang nói — xem KDoc [handoffUntil]. [resumeTask] sẽ bật lại khi hết hạn.
        listener?.setListening(screenOn() && !handoffActive())
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
        // Màn TẮT luôn thi hành NGAY (nhả mic — không có lý do gì để chờ). Màn SÁNG thì phải tôn trọng lượt
        // nhường micro đang chạy: bật nghe lại ở đây là giành mic với phiên lệnh đang ghi (xem [handoffUntil]).
        if (on && handoffActive()) {
            Log.i(TAG, "màn sáng nhưng đang nhường micro cho phiên lệnh — chưa nghe lại")
            return
        }
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
     * Wake nổ: **nhả micro**, đưa Kachi lên + mở phiên nghe lệnh (đúng đường nút mic — `singleTask` ⇒ `onNewIntent`).
     *
     * ## Nhường micro cho phiên lệnh — cross-process thì phải theo THỜI GIAN
     * Tới 1.77 cả bộ nghe câu gọi lẫn phiên lệnh sống trong MỘT tiến trình, nên [VoiceSingleFlight] (state tĩnh)
     * đủ để bảo đảm một-mic: bộ nghe thấy `yieldRequested()` và nhả trong một khung. Từ lượt tách `:wake` thì có
     * **hai bản** [VoiceSingleFlight] — mỗi tiến trình một bản — và chúng không thấy nhau. Nếu `:wake` cứ giữ
     * `AudioRecord` thì phiên lệnh vừa được mở ra sẽ giành mic với chính nó.
     *
     * ⇒ Ở đây làm đúng hai việc, theo đúng thứ tự:
     *  1. `setListening(false)` **TRƯỚC** `startActivity`. ⚠ Đọc cho đúng cơ chế: tới dòng này phần cứng mic **đã**
     *     được nhả rồi — `inner()` gọi `rec.stop()/release()` trong `finally` và `runOuter()` nhả chốt
     *     [VoiceSingleFlight], **cả hai trước khi** `onWake` được gọi. Việc của dòng này là chặn **lượt xin mic KẾ
     *     TIẾP**: không có nó, luồng nghe chỉ nghỉ `REARM_MS` (4 s) rồi giành mic lại ngay giữa lúc người lái đang
     *     nói. Tức nó **kéo dài** lượt nhường 4 s → [WAKE_HANDOFF_MS], chứ không phải nó đi nhả mic. (Đừng "tối ưu"
     *     bỏ `nap(REARM_MS)` vì tưởng dòng này làm việc nhả — nó không.)
     *  2. Ghi mốc [handoffUntil] + hẹn nghe lại sau [WAKE_HANDOFF_MS] — **một mốc THỜI GIAN, không IPC**. Cố ý không
     *     chờ tín hiệu "phiên lệnh xong": một cổng chờ tín hiệu từ tiến trình khác là một cổng có thể **không bao
     *     giờ mở** (đúng ca `fireWake` bị nền tảng chặn ở dưới), và khi ấy "Hey Kachi" chết im tới lần nổ máy sau.
     *     [WAKE_HANDOFF_MS] = 18 s ⇒ đủ cho một lượt hỏi-lại + một câu trả lời đọc xong (trần nghe 8 s + đọc).
     *     Mốc ấy còn để **hai đường chẳng liên quan** (`SCREEN_ON` · một cú `sync()`) không huỷ được lượt nhường —
     *     xem KDoc [handoffUntil].
     *
     * ⚠ **[SUY] Android 10 chặn start-activity từ nền**, và một foreground-service **không** phải một miễn trừ.
     * Miễn trừ mà Kachi dựa vào là `SYSTEM_ALERT_WINDOW` (đã có, và bắt buộc phải có vì tấm chữ voice/bóng cast
     * là cửa sổ overlay) và ca "app đang là HOME/đang hiện". Nếu bị chặn thì hệ **không ném** — chỉ một dòng
     * logcat của nền tảng — nên `runCatching` ở đây không phát hiện được. Đây là mục **phải đo trên xe** (V-oncar
     * của spec), không phải thứ tự nhận là chạy. Chính vì thế bước (2) là một hẹn giờ vô điều kiện: bị chặn thì
     * bộ nghe vẫn tự sống lại.
     */
    private fun fireWake() {
        // (1) Chặn lượt xin mic kế tiếp TRƯỚC khi phiên lệnh mở ra — xem KDoc (mic đã nhả từ trước, đây là kéo dài).
        listener?.setListening(false)
        // (2) Ghi mốc nhường NGAY, trước cả `startActivity`: một `SCREEN_ON`/`sync()` xen vào đúng khe này cũng
        //     phải thấy "đang nhường" (xem KDoc [handoffUntil]).
        handoffUntil = SystemClock.elapsedRealtime() + WAKE_HANDOFF_MS
        runCatching {
            startActivity(
                Intent(this, KachiHomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_START_VOICE, true),
            )
        }.onFailure { Log.w(TAG, "fireWake mở activity lỗi", it) }
        // (3) Hẹn nghe lại — vô điều kiện, kể cả khi (2) bị nền tảng chặn im lặng.
        main.removeCallbacks(resumeTask)
        main.postDelayed(resumeTask, WAKE_HANDOFF_MS)
    }

    /** Cầu chì false-accept: [P2] ghi cờ tệp RIÊNG (KHÔNG đụng `clusternav_prefs` chung) + báo + dừng service. */
    private fun autoDisable() {
        runCatching { Prefs.setWakeServiceDisabled(this, true) }
        runCatching { Toast.makeText(this, getString(R.string.kachi_wake_auto_off), Toast.LENGTH_LONG).show() }
        stopListening(); stopSelf()
    }

    override fun onDestroy() {
        // Hẹn giờ sống lâu hơn service = một Runnable chạm [listener] đã nhả (họ lỗi "đường sống lâu hơn thứ nó
        // phục vụ" của dự án). Huỷ TRƯỚC khi dừng bộ nghe.
        main.removeCallbacks(resumeTask)
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

        /**
         * Hậu tố tiến trình của bộ nghe câu gọi — khai **MỘT CHỖ** cho cả `AndroidManifest.xml`
         * (`android:process`) lẫn `KachiApplication.isBackgroundVoiceProcess()`.
         *
         * Chép chuỗi `":wake"` lần thứ hai là mở đúng cái khe mà `:tts` đã phải đóng bằng một bài canh: manifest
         * và mã lệch nhau thì cổng "đừng nạp mô hình NGHE ở tiến trình nền" **im lặng** hết tác dụng, và thứ
         * nhận ra điều đó là chiếc xe hết RAM.
         */
        const val PROCESS_SUFFIX = ":wake"

        /**
         * Nhường micro cho phiên lệnh bao lâu sau khi wake nổ — xem KDoc [fireWake] (handoff theo THỜI GIAN).
         *
         * 18 s = trần nghe một lượt (8 s) + một lượt hỏi-lại + câu trả lời đọc xong, cộng lề. Ngắn hơn thì bộ
         * nghe giành mic ngay giữa câu người lái đang nói; dài hơn thì "Hey Kachi" thứ hai gọi mãi không thấy.
         * 🚗 con số này **chưa đo trên xe** — chốt bằng một lượt nói hai câu liên tiếp.
         */
        const val WAKE_HANDOFF_MS = 18_000L

        /**
         * Extra của [sync]: **dựng lại bộ nghe** để nó nạp lại model KWS.
         *
         * Cần vì `ensureListener()` cố ý không dựng lại khi luồng còn chạy, và luồng chỉ thử nạp model **một lần**
         * cho cả vòng đời của nó (`kwsTried` — thiếu model là trạng thái bền, thử lại mỗi vòng chỉ là đọc đĩa vô
         * ích). Không có cờ này thì gói KWS vừa tải xong chỉ có tác dụng **sau lần nổ máy sau**.
         */
        private const val EXTRA_RELOAD = "reload_model"

        /**
         * Bật/tắt service theo công tắc — gọi từ UI + boot. Tắt công tắc ⇒ service tự stopSelf ở onStartCommand.
         *
         * @param reloadModel `true` ⇒ dựng lại bộ nghe để nạp model KWS mới tải về (xem [EXTRA_RELOAD]).
         *
         * ⚠ Gọi từ tiến trình **launcher**; nền tảng tự dựng `:wake`. Đây là toàn bộ giao diện giữa hai tiến
         * trình — không có IPC nào khác, có chủ ý (xem KDoc lớp).
         */
        fun sync(ctx: Context, reloadModel: Boolean = false) {
            val i = Intent(ctx, VoiceWakeService::class.java).putExtra(EXTRA_RELOAD, reloadModel)
            if (runCatching { Prefs.wakeEnabled(ctx) }.getOrDefault(false)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
            } else {
                runCatching { ctx.stopService(i) }
            }
        }
    }
}
