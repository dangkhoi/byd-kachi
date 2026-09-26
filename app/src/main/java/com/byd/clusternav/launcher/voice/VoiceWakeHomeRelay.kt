package com.byd.clusternav.launcher.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.byd.clusternav.launcher.EXTRA_VOICE_HOME_ACTION
import com.byd.clusternav.launcher.EXTRA_VOICE_HOME_ARG
import com.byd.clusternav.launcher.EXTRA_VOICE_HOME_DEADLINE
import com.byd.clusternav.launcher.EXTRA_VOICE_HOME_NONCE
import com.byd.clusternav.launcher.KachiHomeActivity
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ VOICE-WAKE-SLOT-LAYOUT (2.69) — `:wake` giao việc cần Activity và (khi cần) CHỜ KẾT QUẢ THẬT ═══════════════════
 *
 * Trước 2.69 `buildSession` để `assignAppToSlot`/`onLayout` ở mặc định `false` ⇒ wake ON thì nút mic (đi `:wake`) mất
 * *"mở YouTube vào ô 2"* / *"đổi bố cục 2 cột"* — người lái nghe lời từ chối thật cho một việc in-process làm được.
 * Hai lambda ấy trả `Boolean` **đồng bộ**; việc nằm ở `KachiHomeActivity`. Đây là cầu: intent + nonce + hạn → Activity
 * thi hành (chỉ khi chưa quá hạn) → broadcast ack → `:wake` trả đúng Boolean ấy. Hết hạn ⇒ `false` (**từ chối thật**,
 * KDoc `VoiceWiring.dispatcher` cấm lạc quan) và Activity cũng **không làm** (`VoiceHomeRelay.expired`) — hai bên
 * không bao giờ lệch nhau.
 *
 * ## Luồng: [perform] CHẶN luồng gọi, và luồng gọi là MAIN của `:wake`
 * [ĐO source, `VoiceSession.kt:285` `post { execute(sentence) }` (`post` = chạy thẳng nếu đã ở main, không thì `ui.post`)
 * → `VoiceSession.kt:361` `d.execute(intents)` đồng bộ] hai lambda này chạy trên luồng main của `:wake`. Chặn nó
 * ≤ [VoiceHomeRelay.ackTimeoutMs] chấp nhận được: tiến trình không có UI ngoài tấm chữ đang
 * đứng chờ chính câu trả lời này; `say` chỉ chạy **sau** khi lambda trả về. Hệ quả bắt buộc: receiver ack **không được**
 * nhận trên main (main đang đứng chờ ⇒ ack không bao giờ tới ⇒ luôn hết hạn) ⇒ đăng ký với `Handler` của một
 * `HandlerThread` riêng ([ackThread]) — [ĐO javap `core-1.19.0.aar`] `ContextCompat.registerReceiver(Context,
 * BroadcastReceiver, IntentFilter, String, Handler, int)` có sẵn; cờ export vẫn khai (lint `UnspecifiedRegisterReceiverFlag`).
 *
 * ## Ack là broadcast trong gói, không phải kênh lệnh
 * Cùng khuôn `VoiceEntry.ACTION_LISTEN_ACK`: `setPackage` + `RECEIVER_NOT_EXPORTED`, một chiều báo-kết-quả, kèm nonce
 * để ack của lượt trước (về muộn) không được tính cho lượt sau.
 *
 * ## Chặn main bao lâu thì ANR? — [ĐO AOSP android-10.0.0_r47]
 * Trần SỚM NHẤT là **5 s** của input, vì tấm chữ là cửa sổ nhận chạm:
 * `wm/ActivityTaskManagerService.java` → `public static final int KEY_DISPATCHING_TIMEOUT_MS = 5 * 1000;`. Ba trần
 * còn lại rộng hơn nhiều: `am/ActiveServices.java:102-109` → `SERVICE_START_FOREGROUND_TIMEOUT = 10*1000` (grace của
 * `startForegroundService` — **10 s**, không phải 5 s như tài liệu Google viết) và `SERVICE_TIMEOUT = 20*1000`;
 * `am/ActivityManagerService.java` → `BROADCAST_FG_TIMEOUT = 10*1000` (receiver `SCREEN_ON`/`SCREEN_OFF` của service
 * chạy trên main — chậm ≤ hạn chờ, không ANR). ⇒ hạn chờ ack **phải < 5 s**; [VoiceHomeRelay.ackTimeoutMs] trả tối đa
 * 4 s (`VoiceEntryRoute.ACK_COLD_MS`), còn 1 s lề. Đừng nới quá 4 s ở đây — nới là đổi ANR-lề lấy một lượt chờ.
 *
 * ⚠ Android 10 chặn start-activity từ nền, và một **foreground-service KHÔNG phải miễn trừ**. Miễn trừ Kachi dựa vào
 * là `SYSTEM_ALERT_WINDOW` — [ĐO AOSP android-10.0.0_r47 `services/core/java/com/android/server/wm/ActivityStarter.java`
 * `shouldAbortBackgroundActivityStart`: nhánh *"don't abort if the callingUid has SYSTEM_ALERT_WINDOW permission"*;
 * danh sách miễn trừ ở đó **không có** foreground-service] — cùng đường `openHome` cũ của bốn việc
 * APP_LIST/SETTINGS/PERMISSIONS/SWITCH_PROFILE. Bị chặn ⇒ không ack ⇒ hết hạn ⇒ `false` có log — không im.
 */
internal class VoiceWakeHomeRelay(
    private val ctx: Context,
    /**
     * [SOÁT 2.69 · P1] Tiến trình CHÍNH có đang sống không — hạn chờ ack là một phép **ĐO**, không phải hằng cố
     * định (CLAUDE.md §5: không quyết định bằng cờ RAM). Chết ⇒ `startActivity` phải dựng lạnh cả launcher ⇒
     * 1,5 s là từ chối oan; xem [VoiceHomeRelay.ackTimeoutMs].
     */
    private val mainAlive: () -> Boolean = { VoiceWakeService.isMainProcessAlive(ctx) },
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) {

    /** Bốn việc cũ (mở màn X): gửi và thôi — kết quả là màn hiện lên, không có Boolean nào để trả. */
    fun send(action: VoiceHomeAction, arg: String?) {
        launch(action, arg, nonce = null, deadline = 0L)
    }

    /**
     * Việc có kết quả ([VoiceHomeAction.awaitsResult]): gửi kèm nonce + hạn, chờ ack ≤ [VoiceHomeRelay.ackTimeoutMs]
     * (1,5 s tiến trình chính đang sống / 4 s phải dựng lạnh) trên luồng gọi.
     * @return `true` CHỈ khi Activity đã thi hành và báo `true` trong hạn. Mọi đường khác (gửi hỏng · không đăng ký
     * được receiver · hết hạn · Activity báo `false`) ⇒ `false`, có log.
     */
    fun perform(action: VoiceHomeAction, arg: String?): Boolean {
        // [SOÁT 2.69 · P2] KHÔNG `require`/`throw`: `VoiceSession.execute` chạy trong một `post` tới luồng main
        // (`VoiceSession.kt:285`) — NGOÀI `try/catch` của `runSession` ⇒ một ngoại lệ ở đây là `:wake` **sập**, và
        // một launcher không được chết vì tính năng phụ. Ca này không xảy ra hôm nay (dispatcher ở main), nhưng
        // cái giá của việc nhầm là cả tiến trình, nên cổng là một dòng log + từ chối.
        if (Looper.myLooper() == ackThread.looper) {
            Log.w(TAG, "perform() bị gọi trên chính luồng ack — ack sẽ không bao giờ tới, từ chối ngay ${action.id}")
            return false
        }
        val warm = mainAlive()
        val timeoutMs = VoiceHomeRelay.ackTimeoutMs(warm)
        val nonce = UUID.randomUUID().toString()
        val deadline = now() + timeoutMs
        val latch = CountDownLatch(1)
        val done = AtomicBoolean(false)
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action != VoiceEntry.ACTION_HOME_ACTION_ACK) return
                if (i.getStringExtra(EXTRA_VOICE_HOME_NONCE) != nonce) return   // ack của lượt khác
                done.set(i.getBooleanExtra(VoiceEntry.EXTRA_HOME_ACTION_DONE, false))
                latch.countDown()
            }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                ctx, rx, IntentFilter(VoiceEntry.ACTION_HOME_ACTION_ACK),
                null, Handler(ackThread.looper), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { Log.w(TAG, "không đăng ký được receiver ack — không gửi việc ${action.id} (không nghe được kết quả thì không hứa)", it) }
            .isSuccess
        if (!registered) return false
        try {
            if (!launch(action, arg, nonce, deadline)) return false
            val acked = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            // 🚗 MỘT dòng để chốt hạn trên xe (§Reviewer Log Pass 4 để lại đúng phép đo này): thời gian ack THẬT + hạn đang dùng
            // + trạng thái tiến trình chính. `deadline - timeoutMs` = mốc bắt đầu, không cần thêm biến.
            val ms = now() - (deadline - timeoutMs)
            val who = if (warm) "tiến trình chính sống" else "tiến trình chính lạnh"
            if (acked) Log.i(TAG, "${action.id}: Activity ack sau $ms ms (hạn $timeoutMs ms, $who) ⇒ ${done.get()}")
            else Log.w(TAG, "Activity không ack ${action.id} trong $timeoutMs ms ($who) ⇒ từ chối thật (Activity cũng không làm — quá hạn)")
            return acked && done.get()
        } finally {
            runCatching { ctx.unregisterReceiver(rx) }
        }
    }

    private fun launch(action: VoiceHomeAction, arg: String?, nonce: String?, deadline: Long): Boolean = runCatching {
        val i = Intent(ctx, KachiHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_VOICE_HOME_ACTION, action.id).putExtra(EXTRA_VOICE_HOME_ARG, arg)
        if (nonce != null) i.putExtra(EXTRA_VOICE_HOME_NONCE, nonce).putExtra(EXTRA_VOICE_HOME_DEADLINE, deadline)
        ctx.startActivity(i)
        true
    }.onFailure { Log.w(TAG, "không mở được KachiHomeActivity cho ${action.id}", it) }.getOrDefault(false)

    private companion object {
        const val TAG = "KachiHomeRelay"

        /**
         * [SOÁT 2.69 · P1] MỘT luồng nhận ack cho cả tiến trình `:wake`, **không** một luồng mỗi relay.
         *
         * Relay được dựng trong `buildSession()` ⇒ mỗi phiên một relay, và mỗi phiên-có-lệnh-gắn-ô một
         * `HandlerThread` **không ai `quit()`** (phiên bị `preempt` cắt ⇒ relay mồ côi, luồng vẫn sống tới khi tiến
         * trình chết). Cùng họ lỗi "đường sống lâu hơn thứ nó phục vụ" mà `resumeTask`/`overlay`/`TextToSpeech` đã
         * phải vá. `by lazy` ở mức companion: chỉ dựng khi có lệnh gắn-ô/bố cục đầu tiên, rồi dùng lại mãi — cố ý
         * KHÔNG `quit()` (một luồng đỗ trong `Looper.loop()` tốn ~0 CPU, còn dựng-hủy theo phiên là chỗ vừa rò).
         */
        val ackThread: HandlerThread by lazy { HandlerThread("KachiHomeAck").apply { start() } }
    }
}
