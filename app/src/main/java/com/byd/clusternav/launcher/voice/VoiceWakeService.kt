package com.byd.clusternav.launcher.voice

import android.app.ActivityManager
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
import com.byd.clusternav.launcher.perf.KachiMem

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
 * Wake nổ ⇒ mở PHIÊN NGHE của chính `:wake` với overlay độc lập (R7, [fireWake]); việc cần Activity (ngăn kéo ·
 * Cài đặt · quyền · đổi hồ sơ · gắn ô · bố cục) trả về `KachiHomeActivity` bằng extra `EXTRA_VOICE_HOME_ACTION`
 * (`VoiceWakeSessionFactory.kt` — bộ dây; `VoiceWakeHomeRelay` — chờ kết quả thật cho hai việc có Boolean, 2.69).
 * Phiên do [VoiceWakeSessions] sở hữu ở mức TIẾN TRÌNH (2.69) — service chỉ lo vòng đời.
 * CLOSE-3 (2026-09-26): khi wake BẬT, **nút mic màn chính + `EXTRA_START_VOICE` cũng đi [listenNow]** (`VoiceEntry`)
 * — một mô hình ASR cho cả máy; service **ack** bằng `VoiceEntry.ack` để tiến trình chính không mở phiên thứ hai.
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
     * R7 (2026-09-23) — phiên nghe RIÊNG của `:wake`, mở overlay ĐỘC LẬP (KHÔNG kéo `KachiHomeActivity` lên đè
     * app đang xem). Owner: *"chỉ cần overlay lên, đừng mở Kachi đè hết"*. Dùng `applicationContext` + overlay
     * `TYPE_APPLICATION_OVERLAY` (đường bóng cast/VietMap đã proven từ nền). Lambda service-an-toàn: điều khiển
     * xe/nav/nhạc chạy thẳng (không cần Activity); mở-app dùng `startActivity(NEW_TASK)` (launch app ĐÍCH, không
     * phải Kachi); mở Cài đặt/ngăn kéo/đổi hồ sơ MỚI đưa Kachi lên (hành động tường minh, hiếm).
     *
     * [SOÁT 2.68 · Pass 2 · P2] **Dựng lại được**, không phải `lazy` một-lần: đứng xuống gọi `stop()` lên phiên, và
     * `VoiceSession.stop()` nhả HẲN đường ra tiếng rồi khoá vĩnh viễn mọi `start()` sau đó (cờ `stopped`) ⇒ ai `stop()`
     * cũng phải **bỏ tham chiếu**, lượt gọi sau dựng phiên mới (recognizer do `VoiceEngine` quản, không nằm trong phiên).
     *
     * [2.69 · VOICE-WAKE-SESSION-OWNER] Phiên **không còn là trường của instance service**: chủ sở hữu là
     * [VoiceWakeSessions] (object, mức TIẾN TRÌNH). Hai instance service (nhánh "nói nốt" ở [onDestroy] + `listenNow`
     * ngay sau) từng mỗi bên một phiên ⇒ chồng tiếng. Getter này = đường "Hey Kachi": dùng lại / dựng mới, **không**
     * cắt phiên đang chạy; đường phím-thoại ([ACTION_LISTEN_NOW]) đi [VoiceWakeSessions.preempt]. Mọi lượt gọi ở luồng
     * CHÍNH (`onStartCommand`/`main.post`); thân dựng ở `VoiceWakeSessionFactory.kt` ([buildSession]).
     */
    private val voiceSession: VoiceSession get() = VoiceWakeSessions.acquire { buildSession() }

    /** Mốc bắt đầu chờ đứng xuống (`elapsedRealtime`), `0` = không chờ — xem [standDownTask]. */
    private var standDownSince = 0L

    /** Số hiệu phiên mà lượt chờ này đang đếm (`-1` = chưa đọc lần nào) — xem KDoc [VoiceWakeSessions.epoch]. */
    private var standDownEpoch = -1

    /** `startId` của lượt start gần nhất — `stopSelf(id)` để một LISTEN_NOW tới SAU mốc quyết định không bị stop nhầm. */
    private var lastStartId = 0

    /**
     * ═══ BG-20 (2026-09-25 · wake) — ĐỨNG XUỐNG sau phiên nghe headless khi "Hey Kachi" TẮT ═══════════════════
     *
     * [SUY, inventory BG-20 + audit RAM §1.1] `ACTION_LISTEN_NOW` với wake OFF mở phiên lệnh trong `:wake` — phiên
     * ấy nạp recognizer int8 (≈ 85–105 MB resident) — rồi nhánh cũ **không có** `stopSelf`: FGS + mô hình treo ở
     * tiến trình thứ ba tới khi người lái mở lại màn Kachi (`onResume` → `sync()` với wake OFF). Trên xe còn
     * 56–94 MB trống, đó là một bản mô hình thừa nằm đúng lúc không ai dùng.
     *
     * Cách đứng xuống — theo trạng thái THẬT của phiên, không theo hẹn giờ mù: hỏi `phase` của [VoiceSession] mỗi
     * [VoiceWakeStandDown.POLL_MS]; `IDLE` (phiên đã tự đóng: trả lời xong + nán, hoặc huỷ) ⇒ [VoiceEngine.release]
     * (chờ lượt giải mã đang chạy — khoá dùng/nhả) + bỏ foreground + `stopSelf`. Wake được BẬT giữa chừng ⇒ thôi
     * (vòng đời thường sở hữu service). Kẹt quá [VoiceWakeStandDown.MAX_WAIT_MS] ⇒ vẫn đứng xuống, có log — một
     * phiên không bao giờ về IDLE là lỗi, và FGS treo mãi không phải cách che nó. Quyết định thuần ở
     * [VoiceWakeStandDown.decide] (test off-device).
     */
    private val standDownTask = object : Runnable {
        override fun run() {
            val phase = VoiceWakeSessions.phase()
            // [SOÁT 2.69 · P2] Phiên của tiến trình đã ĐỔI kể từ lượt trước ⇒ đếm lại từ đầu. Trần `MAX_WAIT_MS` là
            // trần cho MỘT phiên về IDLE, không phải cho một chuỗi phiên nối đuôi — mà từ 2.69 `release()` chạm phiên
            // của cả TIẾN TRÌNH, nên một hẹn còn sót của instance service đã chết sẽ cắt đúng phiên người lái đang
            // nói. Xem KDoc `VoiceWakeSessions.epoch`.
            val epoch = VoiceWakeSessions.epoch()
            if (epoch != standDownEpoch) {
                if (standDownEpoch != -1) Log.i(TAG, "phiên đã đổi ($standDownEpoch ⇒ $epoch) — đếm lại hạn chờ đứng xuống")
                standDownEpoch = epoch
                standDownSince = SystemClock.elapsedRealtime()
            }
            val waited = if (standDownSince == 0L) 0L else SystemClock.elapsedRealtime() - standDownSince
            when (VoiceWakeStandDown.decide(enabled(), phase, waited)) {
                VoiceWakeStandDown.Decision.KEEP -> standDownSince = 0L
                VoiceWakeStandDown.Decision.WAIT -> main.postDelayed(this, VoiceWakeStandDown.POLL_MS)
                VoiceWakeStandDown.Decision.STAND_DOWN -> {
                    if (phase != VoiceTurnPhase.IDLE) Log.w(TAG, "phiên headless kẹt ở $phase quá ${waited / 1000} s — vẫn đứng xuống")
                    else Log.i(TAG, "phiên headless xong, wake OFF — nhả recognizer + đứng xuống (BG-20)")
                    standDownSince = 0L
                    // Phiên kẹt ⇒ `stop()` để nó thôi (overlay/loa). `stop()` là MỘT CHIỀU (nhả TTS + khoá `start`
                    // vĩnh viễn) ⇒ chủ sở hữu bỏ luôn tham chiếu: lượt LISTEN_NOW nào tới sau (kể cả khi nó huỷ được
                    // lượt `stopSelf` này và dùng lại instance service) phải dựng phiên MỚI, không gọi lên xác cũ.
                    VoiceWakeSessions.release()
                    runCatching { VoiceEngine.release() }.onFailure { Log.w(TAG, "nhả recognizer lỗi", it) }
                    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                    stopSelf(lastStartId)
                }
            }
        }
    }

    private fun scheduleStandDown() {
        main.removeCallbacks(standDownTask)
        standDownSince = SystemClock.elapsedRealtime()
        main.postDelayed(standDownTask, VoiceWakeStandDown.POLL_MS)
    }

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
        // CLOSE-4 — mốc pha "phiên lệnh đã xong" (lượt nhường micro hết hạn ⇒ phiên nghe/đọc của lượt wake vừa rồi
        // đã đóng): trả lại rác giải mã của lượt đó TRƯỚC khi mở mic lại. Đây là mốc pha thưa nhất mà vẫn phủ được
        // mỗi lượt wake (một lần / [WAKE_HANDOFF_MS] = 18 s), và nó ở NGOÀI đường audio — không có khung nào bị
        // `madvise` chen vào (CLAUDE.md §6: không đảo hỏng đường đang chạy tốt).
        KachiMem.trim("phiên lệnh xong")
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
        // ═══ CLOSE-4 · WAKE-MALLOPT — tiến trình NÀY tự trả page free ngay, không chờ tick decay ══════════════
        // Zygote đặt `M_DECAY_TIME 1` (1000 ms) cho mọi app, mà decay chỉ chạy theo tick sự kiện malloc — và `:wake`
        // là tiến trình im lặng nhất của Kachi (nạp 74 MB mô hình rồi gần như không cấp phát gì nữa) ⇒ nó là tiến
        // trình **ít có khả năng tới tick nhất**, tức chính nơi rác nạp ở lại resident. Đặt decay = 0 ngay khi tiến
        // trình sinh ra là cách rẻ nhất: từ đó MỌI đường free (kể cả đường không ai nhớ gọi trim) tự madvise.
        // Thiếu `libkachimem.so` ⇒ `false` và không có gì đổi (xem [KachiMem]). Xem doc
        // `docs/diagnostics/offcar-2026-09-26/wake-mallopt-ndk.md`.
        Log.i(TAG, "mallopt(M_DECAY_TIME,0) = ${KachiMem.decayNow()} · lib=${KachiMem.available()}")
        registerReceiver(screenRx, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
    }

    /**
     * ⚠ **[startForeground] phải gọi TRƯỚC mọi đường thoát sớm.**
     *
     * `sync()` gọi `startForegroundService()`; nền tảng cho service một khoảng ân hạn để lên foreground, quá hạn là
     * `RemoteServiceException` — tức **sập app**. [ĐO AOSP android-10.0.0_r47
     * `services/core/java/com/android/server/am/ActiveServices.java:102-109`:
     * `static final int SERVICE_START_FOREGROUND_TIMEOUT = 10*1000;`] — **10 giây**, không phải 5 giây như tài liệu
     * Google viết (bản KDoc trước ghi 5 s theo tài liệu; sửa theo source vì CLAUDE.md §3). Bản đầu `return` khi công
     * tắc đã tắt *trước khi* lên foreground:
     * ca ấy có thật (công tắc bị tắt trong khe giữa `sync()` và `onStartCommand`, hoặc một lượt `START_STICKY`
     * dựng lại service sau khi người dùng đã tắt). Lên foreground rồi `stopSelf()` ngay thì thông báo chỉ nhấp
     * một nhịp — đổi một nhịp nhấp lấy việc không sập là đổi đúng.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // P2#6 (2026-09-25 · wake) — cùng khuôn 7 FGS khác: `startForeground` bị từ chối (ROM lạ / kênh thông báo
        // hỏng) thì đứng xuống có log, không để nền tảng giết tiến trình bằng RemoteServiceException sau 5 s.
        if (!startForegroundOnce()) { stopSelf(startId); return START_NOT_STICKY }
        lastStartId = startId
        // ACTION_LISTEN_NOW (owner 2026-09-25): phím-thoại / nút mic khi app khác đang fullscreen ⇒ mở phiên nghe
        // HEADLESS (overlay TYPE_APPLICATION_OVERLAY nổi trên app đang xem), KHÔNG kéo KachiHomeActivity lên đè.
        // Chạy được cả khi "Hey Kachi" TẮT (đây là phiên nghe một-lượt, không cần bộ nghe câu gọi). Không bật
        // listener wake; wake OFF ⇒ sau khi phiên xong service ĐỨNG XUỐNG (BG-20, xem [standDownTask]).
        if (intent?.action == ACTION_LISTEN_NOW) {
            Log.i(TAG, "LISTEN_NOW — mở phiên nghe headless (overlay, không kéo launcher)")
            // 2.69 — bấm phím = muốn nói NGAY: phiên cũ đang đọc/nán bị cắt (chủ sở hữu mức tiến trình, xem [voiceSession]).
            runCatching { main.post { VoiceWakeSessions.preempt { buildSession() }.start() } }.onFailure { Log.w(TAG, "LISTEN_NOW lỗi", it) }
            // CLOSE-3 — báo tiến trình chính "đã nhận": nó đang chờ ack để KHÔNG mở phiên in-process (`VoiceEntry.tryWake`).
            VoiceEntry.ack(this)
            if (!enabled()) { scheduleStandDown(); return START_NOT_STICKY }
        }
        // Wake BẬT ⇒ vòng đời thường sở hữu service: một lượt chờ đứng xuống còn treo (LISTEN_NOW trước đó) phải bỏ.
        main.removeCallbacks(standDownTask); standDownSince = 0L
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

    /** @return `true` khi luồng nghe đã chết thật (hoặc không có) — xem [VoiceWakeListener.stop]. */
    private fun stopListening(): Boolean {
        val dead = listener?.stop() ?: true
        listener = null
        return dead
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
        // (2) Ghi mốc nhường NGAY.
        handoffUntil = SystemClock.elapsedRealtime() + WAKE_HANDOFF_MS
        // (3) R7 — mở PHIÊN NGHE của chính `:wake` với overlay ĐỘC LẬP, KHÔNG kéo KachiHomeActivity lên đè app
        //     đang xem (owner 2026-09-23). Overlay `TYPE_APPLICATION_OVERLAY` nổi trên mọi thứ; điều khiển/nav/nhạc
        //     chạy thẳng từ service. Đường Activity (nút mic/"Nói với xe") KHÔNG đổi.
        runCatching { main.post { voiceSession.start() } }
            .onFailure { Log.w(TAG, "fireWake mở phiên nghe lỗi", it) }
        // (4) Hẹn nghe lại — vô điều kiện, kể cả khi overlay bị nền tảng chặn im lặng.
        main.removeCallbacks(resumeTask)
        main.postDelayed(resumeTask, WAKE_HANDOFF_MS)
    }

    // `buildSession()` — bộ dây của phiên `:wake` (R7 · §8.2 (A) · 2.69 relay) là hàm mở rộng ở `VoiceWakeSessionFactory.kt`
    // (tách theo VAI, trần 500 dòng).

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
        val listenerDead = stopListening()
        val sessionActive = VoiceWakeSessions.isRunning()
        if (!sessionActive) {
            main.removeCallbacks(standDownTask); standDownSince = 0L
            // [SOÁT 2.68 · Pass 3 · P2] Phiên IDLE vẫn giữ `TextToSpeech`: CHỈ `stop()` nhả (`speaker.shutdown()` — KDoc
            // [VoiceSession.stop]: một TTS chưa shutdown giữ kết nối dịch vụ + tiêu điểm âm thanh sống lâu hơn cả thứ nó
            // phục vụ, đây là service vừa chết). `stop()` một chiều ⇒ chủ sở hữu bỏ luôn tham chiếu, cùng khuôn [standDownTask].
            VoiceWakeSessions.release()
            // `:wake` chỉ chứa service này: recognizer 74 MB còn nằm trong một tiến trình rỗng là RAM thừa tới khi LMK dọn.
            // Nhả ngay — CHỈ khi luồng nghe đã chết thật (khoá dùng/nhả `VoiceEngine` là lưới thứ hai); còn sống thì để cái chết của tiến trình lo, có log.
            if (listenerDead) runCatching { VoiceEngine.release() }
            else Log.i(TAG, "onDestroy: luồng nghe chưa chết — không nhả recognizer")
        } else {
            // Phiên headless (R7/LISTEN_NOW) đang chạy — ca thật: phím-thoại ngoài Kachi rồi mở Kachi ⇒ `onResume` →
            // `sync()` với wake OFF → `stopService` ĐÚNG lúc người lái đang nói. KHÔNG cắt (owner: nói nốt): phiên sống
            // bằng `applicationContext` (overlay/loa/mic không thuộc service). Giữ lượt chờ đứng xuống để nhả recognizer
            // + `stop()` phiên khi nó xong; service đã chết nên `stopForeground`/`stopSelf` trong đó là no-op.
            Log.i(TAG, "onDestroy giữa phiên headless — để phiên nói nốt, nhả recognizer khi phiên xong")
            if (standDownSince == 0L) scheduleStandDown()
        }
        runCatching { unregisterReceiver(screenRx) }
        super.onDestroy()
    }

    /** Cùng khuôn `VoiceKeyKeepAliveService.startForegroundOnce` (7 FGS khác) — `false` ⇒ chỗ gọi `stopSelf`. */
    private fun startForegroundOnce(): Boolean = runCatching {
        startForeground(NOTIF_ID, buildNotification())
        true
    }.getOrElse { Log.e(TAG, "startForeground bị từ chối", it); false }

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
                // P2#6: `startForegroundService` ném được (`IllegalStateException` khi bị coi là nền trên ROM lạ,
                // `SecurityException`) — một công tắc trong Cài đặt không được làm sập launcher.
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                }.onFailure { Log.w(TAG, "sync: không start được VoiceWakeService", it) }
            } else {
                runCatching { ctx.stopService(i) }.onFailure { Log.w(TAG, "sync: stopService lỗi", it) }
            }
        }

        /** Action của [listenNow]. */
        const val ACTION_LISTEN_NOW = "com.byd.launcher.LISTEN_NOW"

        /**
         * Mở PHIÊN NGHE HEADLESS (owner 2026-09-25) — overlay voice nổi lên TRÊN app đang xem, KHÔNG kéo
         * [KachiHomeActivity] lên. Dùng cho phím-thoại/nút mic khi app khác đang fullscreen. Chạy được cả khi
         * "Hey Kachi" TẮT (service lên foreground, mở phiên, rồi nhàn nếu wake off). Overlay là
         * `TYPE_APPLICATION_OVERLAY` nên không cần Activity — điều khiển/nav/nhạc chạy thẳng từ service context.
         */
        fun listenNow(ctx: Context): Boolean {
            val i = Intent(ctx, VoiceWakeService::class.java).setAction(ACTION_LISTEN_NOW)
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                true
            }.onFailure { Log.w(TAG, "listenNow: không start được VoiceWakeService", it) }.getOrDefault(false)
        }

        /**
         * CLOSE-3 — tiến trình `:wake` có đang chạy không (đo bằng `runningAppProcesses` — từ API 21 chỉ trả tiến
         * trình của CHÍNH gói, đủ cho câu hỏi này). Lỗi/không đọc được ⇒ `false` = coi như dựng lạnh (chờ lâu hơn).
         */
        fun isProcessAlive(ctx: Context): Boolean = processAlive(ctx, ctx.packageName + PROCESS_SUFFIX)

        /**
         * [SOÁT 2.69 · P1] Tiến trình **CHÍNH** (launcher) có đang chạy không — tên tiến trình chính = `applicationId`
         * (`AndroidManifest.xml` không khai `android:process` cho `<application>`; chỉ `:wake` và `:tts` có hậu tố).
         * `VoiceWakeHomeRelay` đọc để chọn hạn chờ ack: chết ⇒ `startActivity` phải dựng lạnh cả launcher, 1,5 s là
         * từ chối oan (xem [VoiceHomeRelay.ackTimeoutMs]).
         */
        fun isMainProcessAlive(ctx: Context): Boolean = processAlive(ctx, ctx.packageName)

        /** Một chỗ đọc `runningAppProcesses` (từ API 21 chỉ trả tiến trình của CHÍNH gói). Lỗi ⇒ `false` = coi như lạnh. */
        private fun processAlive(ctx: Context, name: String): Boolean = runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.any { it.processName == name } == true
        }.getOrDefault(false)
    }
}
