package com.byd.clusternav.launcher.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.91 VOICE-APP-NAMES · A4 — LƯỢT DẠY CHẠY Ở TIẾN TRÌNH ĐANG GIỮ MÔ HÌNH (R-nf1) ═══════════════════════════════
 *
 * Spec §4.4 (sơ đồ trình tự). Không bao giờ nạp bản mô hình thứ hai chỉ để dạy (+74–110 MB, ~15 s): hỏi
 * [VoiceEntryRoute.decide] với CÙNG hai nguồn mà [VoiceEntry] hỏi.
 *  • `IN_PROCESS` ⇒ chạy [VoiceTeachSession] tại tiến trình chính.
 *  • `WAKE_PROCESS` ⇒ gửi [ACTION_TEACH_LISTEN] (nonce) sang `VoiceWakeService`; `:wake` ack ngay (cùng khuôn
 *    `ACTION_LISTEN_ACK`), chạy lượt dạy bằng [VoiceTeachSession] CỦA NÓ, rồi gửi [ACTION_TEACH_EVENT] (nonce · trạng
 *    thái · mức micro · chữ · mã lỗi). Không ack trong hạn ⇒ lùi về tiến trình chính (cùng mã).
 * Mọi broadcast: `setPackage(gói mình)` + receiver `RECEIVER_NOT_EXPORTED` ⇒ chữ người dùng nói không rời gói, không
 * vào logcat (R-nf3). `:wake` ở chế độ OFF (không giữ mô hình) ⇒ KHÔNG ack ⇒ chính tự làm.
 */
internal class VoiceTeachRelay(
    private val ctx: Context,
    private val local: () -> VoiceTeachSession,
    private val modelInWake: () -> Boolean = { runCatching { VoiceWakePrefsMain.mode(ctx).modelInWake }.getOrDefault(false) },
    private val wakeAlive: () -> Boolean = { VoiceWakeService.isProcessAlive(ctx) },
) {
    private val app: Context = ctx.applicationContext ?: ctx
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var current: Pending? = null

    private inner class Pending(val nonce: String, val listener: VoiceTeachSession.Listener) {
        val acked = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val registered = AtomicBoolean(false)
        /** Hạn im lặng của `:wake` — mỗi tin (ack · trạng thái · mức micro) đặt lại, xem [arm]. */
        val watchdog = Runnable { onSilent(this) }
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.getStringExtra(EXTRA_NONCE) != nonce) return
                when (i.action) {
                    ACTION_TEACH_ACK -> { acked.set(true); arm(this@Pending, WAKE_IDLE_MS); Log.i(TAG, "lượt dạy → `:wake` đã ack") }
                    ACTION_TEACH_EVENT -> onEvent(this@Pending, i)
                }
            }
        }
    }

    /** Một lượt dạy. Chỉ một lượt một lúc (lượt cũ chưa xong ⇒ `false`). */
    fun listen(listener: VoiceTeachSession.Listener): Boolean {
        if (current != null || local().isActive()) return false
        val plan = VoiceEntryRoute.decide(modelInWake(), wakeAlive())
        if (plan.route != VoiceEntryRoute.Route.WAKE_PROCESS) return local().start(listener)
        val p = Pending(UUID.randomUUID().toString(), listener)
        current = p
        runCatching {
            val f = IntentFilter().apply { addAction(ACTION_TEACH_ACK); addAction(ACTION_TEACH_EVENT) }
            ContextCompat.registerReceiver(app, p.rx, f, ContextCompat.RECEIVER_NOT_EXPORTED)
            p.registered.set(true)
        }.onFailure { Log.w(TAG, "đăng ký receiver lượt dạy hỏng — lùi về tiến trình chính", it) }
        val sent = p.registered.get() && send(Intent(app, VoiceWakeService::class.java).setAction(ACTION_TEACH_LISTEN).putExtra(EXTRA_NONCE, p.nonce))
        if (!sent) { finish(p); return local().start(listener) }
        ui.postDelayed({
            if (!p.acked.get() && !p.finished.get()) {
                Log.w(TAG, "`:wake` không ack lượt dạy trong ${plan.ackTimeoutMs} ms ⇒ chạy tại tiến trình chính")
                finish(p)
                local().start(listener)
            }
        }, plan.ackTimeoutMs)
        // `:wake` chết/treo giữa chừng ⇒ hộp không treo. Không phải trần CỐ ĐỊNH (bản đầu 25 s): `:wake` vừa dựng lạnh còn phải
        // nạp mô hình (9–34 s trên đầu xe — KDoc `VoiceEngine`), trần cố định ở đó báo lỗi trong khi `:wake` vẫn chạy tiếp và
        // MỞ MICRO không ai chờ. Nay: hạn im lặng, mọi tin của `:wake` đặt lại; đang nạp mô hình ⇒ hạn dài.
        arm(p, plan.ackTimeoutMs + WAKE_IDLE_MS)
        return true
    }

    fun cancel() {
        current?.let { p -> sendCancel(p) }
        local().cancel()
    }

    private fun sendCancel(p: Pending) {
        send(Intent(app, VoiceWakeService::class.java).setAction(ACTION_TEACH_CANCEL).putExtra(EXTRA_NONCE, p.nonce))
    }

    /** Đặt lại hạn im lặng của [p] (luồng vẽ — receiver và mọi `postDelayed` của lớp này đều chạy ở đó). */
    private fun arm(p: Pending, ms: Long) {
        ui.removeCallbacks(p.watchdog)
        if (!p.finished.get()) ui.postDelayed(p.watchdog, ms)
    }

    /** `:wake` im lặng quá hạn ⇒ BẢO nó huỷ (đừng mở micro khi hộp đã thôi chờ), rồi trả lỗi cho hộp dạy. */
    private fun onSilent(p: Pending) {
        if (p.finished.get()) return
        Log.w(TAG, "`:wake` im lặng quá hạn giữa lượt dạy ⇒ huỷ phía `:wake` + báo lỗi")
        sendCancel(p)
        finish(p)
        p.listener.onResult(VoiceTeachSession.Result("", VoiceTeachSession.Error.ENGINE))
    }

    private fun onEvent(p: Pending, i: Intent) {
        if (p.finished.get()) return
        p.acked.set(true)   // một tin của lượt này = `:wake` ĐÃ nhận (ack lạc ⇒ không được lùi về chính, thành hai lượt nghe)
        val state = i.getStringExtra(EXTRA_STATE)?.let { s -> VoiceTeachSession.State.entries.firstOrNull { it.name == s } }
        arm(p, if (state == VoiceTeachSession.State.LOADING_MODEL) WAKE_LOADING_MS else WAKE_IDLE_MS)
        state?.let(p.listener::onState)
        if (i.hasExtra(EXTRA_LEVEL)) p.listener.onLevel(i.getIntExtra(EXTRA_LEVEL, 0))
        if (i.getBooleanExtra(EXTRA_DONE, false)) {
            val err = i.getStringExtra(EXTRA_ERROR)?.let { e -> VoiceTeachSession.Error.entries.firstOrNull { it.name == e } }
            finish(p)
            p.listener.onResult(VoiceTeachSession.Result(i.getStringExtra(EXTRA_HEARD).orEmpty(), err))
        }
    }

    private fun finish(p: Pending) {
        p.finished.set(true)
        ui.removeCallbacks(p.watchdog)
        if (p.registered.compareAndSet(true, false)) runCatching { app.unregisterReceiver(p.rx) }
        if (current === p) current = null
    }

    /** minSdk 29 ⇒ luôn `startForegroundService` (service lên foreground ngay ở `onStartCommand`, cùng đường `listenNow`). */
    private fun send(i: Intent): Boolean = runCatching {
        app.startForegroundService(i)
        true
    }.onFailure { Log.w(TAG, "gửi lượt dạy sang `:wake` hỏng", it) }.getOrDefault(false)

    companion object {
        private const val TAG = "KachiVoiceTeach"
        const val ACTION_TEACH_LISTEN = "com.byd.launcher.TEACH_LISTEN"
        const val ACTION_TEACH_CANCEL = "com.byd.launcher.TEACH_CANCEL"
        const val ACTION_TEACH_ACK = "com.byd.launcher.TEACH_ACK"
        const val ACTION_TEACH_EVENT = "com.byd.launcher.TEACH_EVENT"
        private const val EXTRA_NONCE = "nonce"
        private const val EXTRA_STATE = "state"
        private const val EXTRA_LEVEL = "level"
        private const val EXTRA_DONE = "done"
        private const val EXTRA_HEARD = "heard"
        private const val EXTRA_ERROR = "error"

        /** Hạn im lặng thường của `:wake` (giữa hai tin: nghe tối đa 8 s có mức micro mỗi [LEVEL_EVERY_MS], giải mã 1–4 s). */
        private const val WAKE_IDLE_MS = 15_000L

        /** Hạn im lặng khi `:wake` báo đang nạp mô hình — lượt dựng 9–34 s trên đầu xe (KDoc `VoiceEngine`), cộng lề. */
        private const val WAKE_LOADING_MS = 60_000L
        private const val LEVEL_EVERY_MS = 150L

        /**
         * Lượt dạy của tiến trình `:wake` — một bản cho cả tiến trình (cùng lẽ [VoiceWakeSessions]). Chỉ giữ
         * `applicationContext` (dựng ở [onWakeStart] bằng `service.applicationContext`) ⇒ không giữ Service/Activity nào.
         */
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile private var wakeTeach: VoiceTeachSession? = null

        /**
         * Phía `:wake` — gọi từ `VoiceWakeService.onStartCommand` (MỘT dòng). `true` = intent là của lượt dạy (đã xử lý).
         * Chế độ OFF (mô hình không ở `:wake`) ⇒ KHÔNG ack, không chạy: tiến trình chính hết hạn chờ sẽ tự làm.
         */
        fun onWakeStart(service: Context, intent: Intent?, mode: VoiceWakeMode): Boolean {
            val nonce = intent?.getStringExtra(EXTRA_NONCE) ?: return false
            when (intent.action) {
                ACTION_TEACH_CANCEL -> { wakeTeach?.cancel(); return true }
                ACTION_TEACH_LISTEN -> Unit
                else -> return false
            }
            if (!mode.modelInWake) { Log.i(TAG, "lượt dạy tới `:wake` khi mô hình không ở đây ($mode) — không ack"); return true }
            val app = service.applicationContext ?: service
            fun event(i: Intent.() -> Unit) = runCatching {
                app.sendBroadcast(Intent(ACTION_TEACH_EVENT).setPackage(app.packageName).putExtra(EXTRA_NONCE, nonce).apply(i))
            }.onFailure { Log.w(TAG, "gửi kết quả lượt dạy hỏng", it) }
            runCatching { app.sendBroadcast(Intent(ACTION_TEACH_ACK).setPackage(app.packageName).putExtra(EXTRA_NONCE, nonce)) }
            val grammar = { VoiceGrammarSnapshotStore.read(app) }
            val s = wakeTeach ?: VoiceTeachSession(app, { grammar().profiles }, { VoiceWiring.appsByLabel(app) }, { grammar().placeLabels() })
                .also { wakeTeach = it }
            val started = s.start(object : VoiceTeachSession.Listener {
                override fun onState(state: VoiceTeachSession.State) { event { putExtra(EXTRA_STATE, state.name) } }
                var lastLevel = 0L
                override fun onLevel(rms: Int) {
                    // Mỗi khối âm là một nhịp — nén về ≤ 1 broadcast / [LEVEL_EVERY_MS] cho vạch mức micro của hộp dạy.
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastLevel >= LEVEL_EVERY_MS) { lastLevel = now; event { putExtra(EXTRA_LEVEL, rms) } }
                }
                override fun onResult(result: VoiceTeachSession.Result) {
                    event { putExtra(EXTRA_DONE, true).putExtra(EXTRA_HEARD, result.heard).putExtra(EXTRA_ERROR, result.error?.name) }
                }
            })
            if (!started) event { putExtra(EXTRA_DONE, true).putExtra(EXTRA_ERROR, VoiceTeachSession.Error.BUSY.name) }
            return true
        }
    }
}
