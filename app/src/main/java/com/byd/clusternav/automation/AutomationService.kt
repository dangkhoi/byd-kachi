package com.byd.clusternav.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Lang
import com.byd.clusternav.Prefs
import com.byd.clusternav.R
import com.byd.clusternav.launcher.automation.NavAutomationBook
import com.byd.clusternav.navAutomationRules
import com.byd.clusternav.rainDefrostEnabled
import com.byd.clusternav.cameraSignalEnabled

/**
 * ═══ MỘT ĐỘNG CƠ NỀN CHO CẢ HAI AUTOMATION ═══════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R4. Foreground service, nhịp [TICK_MS]; mỗi nhịp gọi
 * [ScheduledNavApplier.tick], còn [RainDefrostApplier.tick] chạy mỗi [RAIN_EVERY_TICKS] nhịp (≈5 phút, R1.2).
 *
 * ## Vì sao FGS + `Thread.sleep`, KHÔNG WorkManager / AlarmManager
 * Spec §Quyết định thiết kế: Kachi vốn **thường trú** (nó là HOME, autostart mỗi lần nổ máy), IVI khoá nhiều
 * đường, và nhịp-trong-FGS là công thức **đã chạy thật** trên xe này (`Pm25FilterApplier.startPollLoop`).
 * WorkManager có min-interval 15 phút — quá thô cho một khung giờ 7–9h cần độ phân giải một phút.
 *
 * ## Guard vòng: [running] + [generation] — copy đúng cơ chế đã proven
 * [running] = *"đang có vòng của THẾ HỆ hiện tại"* (chặn khởi trùng khi [sync] gọi nhiều lần: boot + autostart +
 * cú gạt công tắc có thể tới trong cùng một giây). [generation] = **danh tính** vòng; một thread CHỈ sống khi
 * `myGen == generation`. Nhờ token thế hệ, chuỗi bật→tắt→bật (tắt lúc thread đang NGỦ [TICK_MS] rồi bật lại)
 * KHÔNG để thread cũ sống cạnh thread mới, và `finally` của thread cũ KHÔNG xoá cờ của thread mới — đúng bài học
 * `Pm25FilterApplier` đã ghi (chỉ `@Volatile var running` thì thread cũ đọc thấy cờ thread mới vừa bật ⇒ 2 vòng).
 *
 * ## Tự tắt khi không còn việc
 * [anyEnabled] `false` ⇒ service `stopSelf()`. Một FGS thường trú với một thông báo `IMPORTANCE_MIN` mà **không
 * làm gì** là chi phí ròng: nó giữ tiến trình, giữ một dòng trong danh sách thông báo, và làm người đọc log tin
 * rằng automation đang chạy. Công tắc bật lại ⇒ [sync] khởi lại từ đầu.
 */
class AutomationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Hợp đồng `startForegroundService()`: lên foreground trong ~5 s hoặc bị giết. Làm TRƯỚC mọi việc khác
        // (cùng khuôn `BootSetupService`); nền tảng từ chối ⇒ dừng sạch thay vì chết bằng RemoteServiceException.
        if (!startForegroundOnce()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!anyEnabled(applicationContext)) {
            Log.i(TAG, "không automation nào bật — dừng service")
            finish()
            return START_NOT_STICKY
        }
        startLoop(applicationContext)
        // START_STICKY: engine này là một nhịp **dài hạn** (cả chuyến xe), khác `BootSetupService` (việc một lần).
        // Hệ thống thu hồi vì thiếu RAM thì dựng lại là hành vi đúng; `onStartCommand` idempotent nhờ [running].
        return START_STICKY
    }

    override fun onDestroy() {
        // Vô hiệu hoá vòng đang chạy: thread sẽ thoát ở lần kiểm thế hệ kế tiếp thay vì sống lâu hơn service
        // (đúng họ lỗi "đường sống lâu hơn thứ nó phục vụ" — ba họ lỗi lặp lại của repo, §5 project-context).
        synchronized(GUARD) {
            generation++
            running = false
        }
        super.onDestroy()
    }

    private fun finish() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun startForegroundOnce(): Boolean = runCatching {
        startForeground(NOTIFICATION_ID, notification())
        true
    }.getOrElse {
        Log.e(TAG, "startForeground bị từ chối", it)
        false
    }

    private fun notification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                // IMPORTANCE_MIN: đây là một nhịp nền im lặng, không phải một tin cần người lái đọc.
                NotificationChannel(CHANNEL_ID, "Kachi automation", NotificationManager.IMPORTANCE_MIN),
            )
        }
        @Suppress("DEPRECATION")
        return android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Kachi")
            .setContentText(Lang.t("Tự động hoá đang chạy", "Automation running"))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "KachiAutomation"

        /**
         * Nhịp chính. Luật dẫn-theo-lịch cần độ phân giải một phút (R2.3: *"kiểm mỗi ~1 phút"*).
         *
         * BG-13 (2026-09-25): KHÔNG còn nhịp 250 ms khi camera bật. Sự kiện xi-nhan đến qua socket theo thời gian
         * thật; HOLD hết hạn nay được `CameraSignalController` hẹn bằng `postDelayed` đúng mốc (`CameraHold`).
         * Vòng này chỉ còn đồng bộ công tắc mỗi phút (và [sync] gọi thẳng khi công tắc đổi).
         */
        const val TICK_MS = 60_000L

        /**
         * Rule mưa chạy mỗi ngần này nhịp ⇒ ≈5 phút (R1.2).
         *
         * Đếm nhịp thay vì dựng vòng thứ hai: hai vòng là hai thứ phải nhớ dừng lúc huỷ, và [ĐO] lịch sử dự án
         * cho thấy cái thứ hai là cái bị quên (KDoc `PhotoWidgetView` — nhịp sống lâu hơn ô).
         */
        const val RAIN_EVERY_TICKS = 5

        /** Thông báo riêng, KHÔNG dùng lại id của `BootSetupService` (1043) / cast bubble (1042) — ba FGS cùng sống. */
        private const val NOTIFICATION_ID = 1044
        private const val CHANNEL_ID = "kachi_automation"

        /** Khoá của cặp [running]/[generation] — hai cờ phải đổi cùng nhau dưới cùng một khoá. */
        private val GUARD = Any()

        @Volatile private var running = false
        @Volatile private var generation = 0

        /**
         * Có automation nào đang bật không — **cổng duy nhất** trả lời câu đó.
         *
         * Luật dẫn đường phải **có ít nhất một luật đang bật**, không chỉ *"chuỗi luật không rỗng"*: một sổ toàn
         * luật đã tắt thì engine chỉ ngủ rồi dậy vô ích suốt chuyến.
         */
        fun anyEnabled(ctx: Context): Boolean {
            val app = ctx.applicationContext
            // V7 (owner 2026-09-25): công tắc chính BẬT nhưng bỏ tích **cả hai** ô kính = không còn việc gì. Phải
            // xét cả `selection()` ở đây, không chỉ công tắc: nếu không thì FGS thường trú với một thông báo mà
            // `RainDefrostApplier.tick` chỉ trả `Leave` mỗi 5 phút — đúng thứ KDoc lớp này gọi là "chi phí ròng"
            // (giữ tiến trình, chiếm một dòng thông báo, và làm người đọc log tin rằng automation đang chạy).
            // Bỏ tích đi qua `ClusterNavBridge.setRainDefrostFront/Rear`, mà hai hàm đó gọi `sync` ⇒ service tự
            // dừng ngay lượt đó, và tự dựng lại khi tích lại.
            if (Prefs.rainDefrostEnabled(app) && RainDefrostApplier.selection(app).isNotEmpty()) return true
            if (runCatching { Prefs.cameraSignalEnabled(app) }.getOrDefault(false)) return true
            return runCatching {
                NavAutomationBook.decode(Prefs.navAutomationRules(app)).any { it.enabled }
            }.getOrDefault(false)
        }

        /**
         * Đồng bộ service với công tắc — **gọi sau MỌI lượt đổi cấu hình** (bật/tắt sấy-mưa, sửa/xoá luật) và ở
         * đường khởi động.
         *
         * Idempotent: đang chạy mà còn việc ⇒ no-op (guard [running]); hết việc ⇒ service tự `stopSelf` ở
         * [onStartCommand]. Degrade-safe: `startForegroundService` bị nền tảng chặn (hiếm, ROM lạ) ⇒ log rồi bỏ,
         * KHÔNG ném vào luồng vẽ của người đang bấm công tắc.
         */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext
            runCatching {
                // BG-15: công tắc camera đổi ⇒ controller dùng chung phản ứng NGAY (bật: nối socket; tắt: đóng
                // overlay + dừng luồng), không chờ nhịp 60 s. Tắt mà chưa từng dựng ⇒ không dựng chỉ để dừng.
                syncCamera(app)
                if (!anyEnabled(app)) {
                    // TẮT: vô hiệu vòng NGAY (không chờ service chết) rồi mới xin dừng. Thiếu bước này thì thread
                    // đang ngủ còn chạy thêm một nhịp và có thể ghi HAL sau khi người dùng đã tắt công tắc.
                    synchronized(GUARD) {
                        generation++
                        running = false
                    }
                    RainDefrostApplier.reset()
                    app.stopService(Intent(app, AutomationService::class.java))
                    Log.i(TAG, "sync: không còn automation nào bật ⇒ dừng")
                    return
                }
                val intent = Intent(app, AutomationService::class.java)
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
                Log.i(TAG, "sync: có automation bật ⇒ đảm bảo engine đang chạy")
            }.onFailure { Log.w(TAG, "sync thất bại (degrade-safe, thử lại lần sau)", it) }
        }

        /** Xem [sync]. Tách riêng để nhịp vòng và `finally` của vòng cũng đi đúng một đường. */
        private fun syncCamera(app: Context) {
            val container = AppContainer.get(app)
            val enabled = runCatching { Prefs.cameraSignalEnabled(app) }.getOrDefault(false)
            if (!enabled && !container.cameraSignalCreated) return
            runCatching { container.cameraSignal.tick() }.onFailure { Log.w(TAG, "sync camera lỗi", it) }
        }

        /**
         * Khởi vòng nhịp nếu chưa có vòng nào của thế hệ hiện tại.
         *
         * Thread daemon: nó **không** được giữ tiến trình sống lâu hơn cần thiết; FGS mới là thứ giữ tiến trình,
         * và khi FGS chết thì [onDestroy] đã ++thế hệ nên vòng tự thoát.
         */
        private fun startLoop(app: Context) {
            val myGen: Int
            synchronized(GUARD) {
                if (running) return
                running = true
                myGen = ++generation
            }
            Thread({
                var ticks = 0
                var lastNavMs = 0L
                var lastRainMs = 0L
                try {
                    // Nhịp ĐẦU chạy ngay (không ngủ trước): bật công tắc lúc 7h05 mà phải chờ tới 7h06 mới đánh
                    // giá là một phút không giải thích được với người vừa bấm.
                    while (myGen == generation && anyEnabled(app)) {
                        val nowMs = android.os.SystemClock.elapsedRealtime()
                        // Camera theo xi-nhan — đồng bộ công tắc mỗi nhịp (controller DÙNG CHUNG qua AppContainer, BG-15).
                        // Chạy Ở ĐÂY (FGS nền) chứ không ở render của HOME: [ĐO xe 2026-09-24] lái xe thì app bản-đồ
                        // trên tiền cảnh ⇒ HOME stopped ⇒ render KHÔNG chạy ⇒ xi-nhan không lên camera. FGS chạy bất
                        // kể tiền cảnh. Sự kiện ON/OFF + HOLD hết hạn KHÔNG đi qua nhịp này (socket + postDelayed, BG-13).
                        syncCamera(app)
                        // Nav/mưa theo THỜI GIAN TRÔI (giữ mốc elapsed — không phụ thuộc số nhịp).
                        if (nowMs - lastNavMs >= TICK_MS) {
                            lastNavMs = nowMs
                            runCatching { ScheduledNavApplier.tick(app) }.onFailure { Log.w(TAG, "tick nav lỗi", it) }
                        }
                        if (nowMs - lastRainMs >= TICK_MS * RAIN_EVERY_TICKS) {
                            lastRainMs = nowMs
                            runCatching { RainDefrostApplier.tick(app) }.onFailure { Log.w(TAG, "tick mưa lỗi", it) }
                        }
                        ticks++
                        runCatching { Thread.sleep(TICK_MS) }
                        // Kiểm LẠI sau khi ngủ: công tắc có thể đã tắt trong lúc đó. KHÔNG đọc [running] ở đây —
                        // một thread của thế hệ khác có thể vừa bật lại cờ ấy; danh tính thế hệ mới là điều kiện đúng.
                        if (myGen != generation) break
                    }
                } finally {
                    syncCamera(app)   // nhịp cuối: công tắc vừa tắt ⇒ đóng overlay + dừng luồng socket (BG-15)
                    // CHỈ thế hệ hiện tại được nhả cờ — tránh `finally` của thread cũ xoá cờ của thread mới.
                    synchronized(GUARD) { if (myGen == generation) running = false }
                    Log.i(TAG, "vòng automation (gen $myGen) kết thúc sau $ticks nhịp")
                }
            }, "kachi-automation").apply { isDaemon = true }.start()
        }
    }
}
