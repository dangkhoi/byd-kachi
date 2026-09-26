package com.byd.clusternav.launcher.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.byd.clusternav.Prefs
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ CLOSE-3 (2026-09-26) — LỐI VÀO phiên nghe của tiến trình CHÍNH: giao cho `:wake` khi "Hey Kachi" bật ═══════
 *
 * Quyết định ở `:core` ([VoiceEntryRoute]); lớp này chỉ làm ba việc mà chỉ tầng Android làm được:
 *  1. **Đọc sự thật**: `Prefs.wakeEnabled` + tiến trình `:wake` có đang chạy không ([VoiceWakeService.isProcessAlive]
 *     — `runningAppProcesses`, không phải cờ RAM; CLAUDE.md §5).
 *  2. **Gửi** `listenNow` (cùng đường mà phím vô-lăng `AssistantLauncher` và "Hey Kachi" đã dùng — R7 overlay độc lập).
 *  3. **Chờ ack** rồi **lùi**: `:wake` báo đã nhận bằng broadcast nội bộ [ACTION_LISTEN_ACK] (chỉ trong gói —
 *     `setPackage` + `RECEIVER_NOT_EXPORTED`). Không ack trong hạn ⇒ `onFallback()` mở phiên in-process như cũ.
 *
 * ## Vì sao cần ack, không chỉ "startForegroundService không ném"
 * `startForegroundService` trả về **trước** khi `:wake` chạy `onStartCommand`; nó chỉ ném khi bị từ chối ngay.
 * Tiến trình có thể không lên (ROM lạ, LMK giữa chừng) mà không có callback nào về tiến trình gửi. Không có ack thì
 * ca ấy là **nút mic bấm không ra gì** — đúng thứ trace-den-tan-cung cấm. Ack = một broadcast trong gói, rẻ hơn
 * một `bindService` bắc qua hai tiến trình và không mở giao diện IPC mới nào (KDoc [VoiceWakeService] cố ý giữ
 * `sync`/`listenNow` là toàn bộ giao diện — ack là một chiều báo-nhận, không phải một kênh lệnh).
 *
 * ## Khe đã biết (ghi rõ, chưa vá)
 * Ack tới **sau** hạn ⇒ in-process đã mở, `:wake` cũng mở ⇒ hai phiên trong vài giây (hai `AudioRecord`). Tiến trình
 * chính không cắt được phiên bên kia mà không mở thêm một kênh lệnh; hạn [VoiceEntryRoute.ACK_COLD_MS] đặt rộng để
 * ca này chỉ xảy ra khi `:wake` thật sự hỏng. 🚗 Đọc log `KachiVoiceEntry` khi kiểm CLOSE-3 để chốt hạn.
 *
 * ## [SOÁT 2.68 · P1] Vì sao `VoiceSession.stopped` phải tồn tại vì lớp này
 * Trước CLOSE-3 mọi lối vào mở phiên NGAY trong cùng một lượt luồng vẽ, nên không có khe nào để `start()` chạy sau
 * `onDestroy`. Đường lùi ở đây hẹn **1,5–4 s** rồi mới gọi `start(local = true)`: người lái bấm mic, `:wake` dựng lạnh
 * chậm, trong khe đó màn bị dựng lại (đổi chủ đề / ngôn ngữ / low-memory) ⇒ lượt lùi nổ **sau** `stop()` và mở lại
 * đúng ba thứ mà KDoc `VoiceSession.stop` nói phải chết theo màn: một cửa sổ `TYPE_APPLICATION_OVERLAY` phủ toàn màn
 * ăn mọi cú chạm, một `AudioRecord` đang mở, và một `VoiceDispatcher` trỏ vào activity đã huỷ. Cờ `cancelled` KHÔNG
 * đủ — `start()` tự đặt nó về `false` ở dòng đầu ⇒ phải là một cờ RIÊNG, chỉ bật một chiều.
 */
class VoiceEntry(
    private val ctx: Context,
    /** Bốn đường Activity mà `:wake` trả về qua intent — xem [VoiceHomeActions]. */
    val home: VoiceHomeActions,
    private val wakeEnabled: () -> Boolean = { runCatching { Prefs.wakeEnabled(ctx) }.getOrDefault(false) },
    private val wakeAlive: () -> Boolean = { VoiceWakeService.isProcessAlive(ctx) },
    private val dispatch: () -> Boolean = { VoiceWakeService.listenNow(ctx) },
    private val ui: Handler = Handler(Looper.getMainLooper()),
) {

    /** Context của TIẾN TRÌNH cho vòng đăng-ký ack (xem [register]) — không giữ Activity qua một lượt hẹn giờ. */
    private val appCtx: Context = ctx.applicationContext ?: ctx

    /**
     * Thử giao lối vào cho `:wake`. Trả `true` ⇒ **đã giao** (chỗ gọi KHÔNG mở phiên in-process); [onFallback] sẽ
     * chạy trên luồng vẽ nếu `:wake` không ack trong hạn. Trả `false` ⇒ chỗ gọi mở in-process ngay (wake tắt, hoặc
     * gửi hỏng — đã log).
     */
    fun tryWake(onFallback: () -> Unit): Boolean {
        val plan = VoiceEntryRoute.decide(wakeEnabled(), wakeAlive())
        if (plan.route != VoiceEntryRoute.Route.WAKE_PROCESS) return false
        val acked = AtomicBoolean(false)
        val registered = AtomicBoolean(false)
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action != ACTION_LISTEN_ACK) return
                acked.set(true)
                unregister(this, registered)
                Log.i(TAG, "lối vào → `:wake` đã ack (hạn ${plan.ackTimeoutMs} ms) — không dựng recognizer ở tiến trình chính")
            }
        }
        register(rx, registered)
        val dispatched = dispatch()
        if (!dispatched) {
            unregister(rx, registered)
            Log.w(TAG, "listenNow gửi hỏng ⇒ mở phiên in-process")
            return false
        }
        ui.postDelayed({
            unregister(rx, registered)
            if (VoiceEntryRoute.afterDispatch(dispatched, acked.get()) == VoiceEntryRoute.Route.IN_PROCESS) {
                Log.w(TAG, "`:wake` không ack trong ${plan.ackTimeoutMs} ms ⇒ lùi về phiên in-process")
                onFallback()
            }
        }, plan.ackTimeoutMs)
        return true
    }

    /**
     * [SOÁT 2.68 · P2] Đăng ký trên **applicationContext**, không phải Activity: receiver này sống 1,5–4 s, và một
     * lượt dựng lại màn trong khe đó để lại *"Activity has leaked IntentReceiver"* + `unregisterReceiver` ném (chỉ
     * còn bị `runCatching` nuốt). Ack là tin của TIẾN TRÌNH, không của màn, nên app context là chỗ đúng của nó.
     *
     * [SOÁT 2.68 · P1 · ĐO `:app:lintRelease`] Bản đầu rẽ nhánh `SDK_INT >= 33` và gọi `registerReceiver(rx, f)`
     * trần ở nhánh dưới ⇒ **lint ERROR** `UnspecifiedRegisterReceiverFlag` (action `com.byd.launcher.LISTEN_ACK` là
     * broadcast **của app**, không phải của hệ thống như `ACTION_PACKAGE_*` ở `VietMapProviderCatalog`, nên cờ export
     * là **bắt buộc ở MỌI nhánh**) ⇒ `lintRelease` đỏ = không ra được bản release. Dùng `ContextCompat.registerReceiver`
     * (androidx.core 1.19.0 đã có trong `:app`; [ĐO Context7 `core/api/1.19.0-rc01.txt`] chữ ký 4 tham số + hằng
     * `RECEIVER_NOT_EXPORTED = 0x4`): một đường cho mọi API, cờ luôn khai, không còn nhánh nào để quên.
     */
    private fun register(rx: BroadcastReceiver, flag: AtomicBoolean) {
        val f = IntentFilter(ACTION_LISTEN_ACK)
        runCatching {
            ContextCompat.registerReceiver(appCtx, rx, f, ContextCompat.RECEIVER_NOT_EXPORTED)
            flag.set(true)
        }.onFailure { Log.w(TAG, "đăng ký receiver ack hỏng — vẫn gửi, hết hạn sẽ lùi in-process", it) }
    }

    private fun unregister(rx: BroadcastReceiver, flag: AtomicBoolean) {
        if (flag.compareAndSet(true, false)) runCatching { appCtx.unregisterReceiver(rx) }
    }

    companion object {
        private const val TAG = "KachiVoiceEntry"

        /** `:wake` → tiến trình chính: "đã nhận LISTEN_NOW". Chỉ trong gói (`setPackage`), không phải API cho ai khác. */
        const val ACTION_LISTEN_ACK = "com.byd.launcher.LISTEN_ACK"

        /** Gọi từ `VoiceWakeService.onStartCommand` ngay khi nhận `ACTION_LISTEN_NOW`. */
        fun ack(ctx: Context) {
            runCatching { ctx.sendBroadcast(Intent(ACTION_LISTEN_ACK).setPackage(ctx.packageName)) }
                .onFailure { Log.w(TAG, "gửi ack hỏng", it) }
        }
    }
}

/**
 * Bốn đường Activity mà phiên trong `:wake` không tự làm được — **CÙNG** lambda mà `VoiceWiring.dispatcher` của
 * phiên in-process dùng (`KachiHomeWiring.voiceSession` truyền đúng bốn cái đó vào đây). `:wake` gửi
 * [VoiceHomeAction.id] qua extra; `KachiHomeWiring.startVoiceIfRequested` gọi [perform].
 */
class VoiceHomeActions(
    val openAppList: () -> Unit,
    val openSettings: () -> Unit,
    val openPermissions: () -> Unit,
    val switchProfile: (String) -> Unit,
) {
    /** `false` ⇒ thiếu tham số (đổi hồ sơ không tên) — không đoán, có log ở chỗ gọi. */
    fun perform(action: VoiceHomeAction, arg: String?): Boolean = when (action) {
        VoiceHomeAction.APP_LIST -> { openAppList(); true }
        VoiceHomeAction.SETTINGS -> { openSettings(); true }
        VoiceHomeAction.PERMISSIONS -> { openPermissions(); true }
        VoiceHomeAction.SWITCH_PROFILE -> arg?.takeIf { it.isNotBlank() }?.let { switchProfile(it); true } ?: false
    }
}
