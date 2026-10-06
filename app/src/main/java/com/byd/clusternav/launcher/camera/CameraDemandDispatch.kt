package com.byd.clusternav.launcher.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.byd.clusternav.AppContainer
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.camera.CameraDemand.Outcome
import com.byd.clusternav.launcher.voice.VoiceGrammarSnapshotStore
import com.byd.clusternav.launcher.voice.VoiceWakeService

/**
 * ═══ 2.93 · CAMERA-ON-DEMAND — MỘT đường thi hành cho mọi lối vào (spec `docs/specs/kachi-293-cam.html` §4.4) ═══════
 *
 * Owner 06/10: *"trigger từ bind phím vật lý, hoặc widget action button, hoặc voice"*. Bốn lối vào, một đường:
 *  • **phím vật lý** — `AssistantLauncher.launch` (dịch vụ Hỗ trợ, tiến trình CHÍNH) nhận mã `cam:<camera>`/`cam:off`
 *    ([fireKey]);
 *  • **nút trên thanh nút / ô widget lưới** — `KachiHomeWiring.controlDock` · `cameraDemandTile` nhận mã việc `launcher_cam_*`
 *    ([tap]);
 *  • **giọng nói** — `VoiceDispatcher.runLauncher` → `VoiceCameraTurn` (phiên ở tiến trình chính HOẶC `:wake`) ([fireForResult]);
 *  • **cầu kiểm thử** — `camera --es name demand:<camera>` (QA máy ảo) ([fireForResult]).
 *
 * ## Camera sống ở tiến trình CHÍNH — `:wake` đi cầu broadcast CÓ THỨ TỰ trong gói, đọc `resultCode`
 * [CameraSignalController] là MỘT cho cả tiến trình chính ([AppContainer.cameraSignal]). Phiên giọng nói ở `:wake` (phím
 * vô-lăng *Kachi nghe* · Hey Kachi) không chạm được nó ⇒ gửi `sendOrderedBroadcast(setPackage)` tới receiver đăng ký
 * `RECEIVER_NOT_EXPORTED` trong tiến trình chính (khuôn đã chạy của `ControlSentRelay`, 52 ms trên máy ảo — một chiều);
 * receiver áp lệnh rồi đặt `resultCode` = [Outcome.code] ⇒ phiên `:wake` NÓI ĐÚNG việc đã xảy ra (wave 2B · D1/OQ6: *"tắt
 * camera"* trần mà không có gì để tắt thì rơi về Camera 360 — `VoiceCameraTurn`). KHÔNG dùng `VoiceWakeHomeRelay`: cầu đó
 * đưa `KachiHomeActivity` LÊN, sai cho một overlay nổi trên app đang xem. Tiến trình chính không sống ⇒
 * [CameraDemand.withoutMain] (TẮT = không có gì để tắt · MỞ = không tới được) — không ✓ giả. 2.93 wave 2C ·
 * CAM-WAKE-COLD-MAIN: tiến trình chính ĐANG khởi động (sống nhưng receiver chưa đăng ký ⇒ mã khởi đầu về ngay) ⇒ gửi lại
 * MỘT lần trong cùng hạn chờ — luật thuần + bài ở `:core` [CameraWakeAsk]. Soát senior wave 2B/2C [P2]: lệnh mang hạn tổng;
 * receiver tới SAU hạn (luồng chính bận) thì BỎ — câu ✗ đã nói không được có hiệu lực muộn ([CameraWakeAsk.expired]).
 *
 * ## Phạm vi (CLAUDE.md §4) · hoàn tác (§5)
 * Chỉ đổi trạng thái RAM của controller + mở/đóng đúng luồng `AVMCamera`/overlay mà camera xi-nhan đã dùng (cùng đường dỡ
 * có bài canh). Không lệnh `am`/`wm`/`service call` nào, không pref nào, không state ngoài tiến trình ⇒ chết theo tiến trình.
 * Lệnh nút xe (Camera 360) KHÔNG bao giờ bắn từ đây — đường ấy là của làn nút xe giọng nói.
 */
object CameraDemandDispatch {

    private const val TAG = "KachiCamDemand"

    /** `:wake` → chính: một lệnh camera đã mã hoá ([CameraDemand.encode]). Chỉ trong gói (`setPackage`). */
    const val ACTION = "com.byd.launcher.CAMERA_DEMAND"
    private const val EXTRA_OP = "op"

    /** Hạn của bên gửi (`SystemClock.elapsedRealtime`, chung mọi tiến trình) — receiver bỏ lệnh tới SAU nó ([CameraWakeAsk.expired]). */
    private const val EXTRA_DEADLINE = "deadline"

    /**
     * Hạn chờ `resultCode` của cầu `:wake` → chính. [SUY] đường một chiều cùng khuôn tới sau 52 ms (máy ảo); receiver làm
     * việc đồng bộ trên luồng chính (≤ ba lệnh HAL panorama + một `addView`) — 2 s dư nhiều lần mà vẫn NGẮN hơn hẳn lưới an
     * toàn của lượt nói (`VoiceSession.TURN_SETTLE_MS` = 4 s), còn chỗ cho đường Camera 360 chạy sau đó. 🚗 Đo trên xe:
     * `logcat -s KachiCamDemand` — dòng *"kết quả … sau N ms"* (`:wake`, đo từ lúc gửi) + dòng *"nhận từ `:wake`"* (chính).
     * Wave 2C: là trần TỔNG cho cả lượt gửi lại ([CameraWakeAsk]) — không nới.
     */
    const val RESULT_TIMEOUT_MS = 2_000L

    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Chạy [block] trên luồng chính — NGAY nếu đang ở đó (để lời đáp/đi tiếp không trễ một nhịp vô cớ). */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /**
     * Thi hành [op] rồi báo [onResult] ĐÚNG MỘT LẦN, trên luồng chính, với điều THẬT SỰ đã xảy ra ([Outcome]). Tiến trình
     * chính ⇒ áp thẳng ([applyHere]); tiến trình khác ⇒ broadcast có thứ tự + hạn [RESULT_TIMEOUT_MS] ([sendForResult]).
     */
    fun fireForResult(ctx: Context, op: CameraDemand.Op, onResult: (Outcome) -> Unit) {
        val app = ctx.applicationContext ?: ctx
        if (VoiceGrammarSnapshotStore.isMainProcess(app)) {
            onMain { onResult(applyHere(app, op)) }
            return
        }
        if (!VoiceWakeService.isMainProcessAlive(app)) {
            val o = CameraDemand.withoutMain(op)
            Log.w(TAG, "${CameraDemand.encode(op)}: tiến trình chính không sống ⇒ $o (camera theo yêu cầu chỉ sống ở đó)")
            onMain { onResult(o) }
            return
        }
        sendForResult(app, op, onResult)
    }

    /**
     * Lối không cần kết quả (phím vật lý · nút thanh nút · ô widget · nút *Xem thử*): CÙNG đường [fireForResult]. `false` =
     * chắc chắn KHÔNG tới được controller (tiến trình khác và tiến trình chính không sống) — để chỗ gọi nói thật.
     */
    fun fire(ctx: Context, op: CameraDemand.Op): Boolean {
        val app = ctx.applicationContext ?: ctx
        if (!VoiceGrammarSnapshotStore.isMainProcess(app) && !VoiceWakeService.isMainProcessAlive(app)) {
            Log.w(TAG, "${CameraDemand.encode(op)}: tiến trình chính không sống ⇒ không có camera nào để mở")
            return false
        }
        fireForResult(app, op) { o -> if (o == Outcome.UNREACHABLE) Log.w(TAG, "${CameraDemand.encode(op)}: không tới được controller") }
        return true
    }

    /**
     * Tiến trình CHÍNH, **luồng chính**: áp [op] lên controller rồi đọc kết quả từ trạng thái TRƯỚC/SAU. `demand` trên luồng
     * chính chạy ĐỒNG BỘ (chỉ tự đẩy về main khi ở luồng khác — KDoc `CameraSignalController.demand`), nên hai lượt đọc
     * [CameraSignalController.demanded] kẹp đúng một lượt áp. Hai chỗ gọi: [fireForResult] (qua [onMain]) và receiver cầu.
     */
    private fun applyHere(app: Context, op: CameraDemand.Op): Outcome {
        val c = AppContainer.get(app).cameraSignal
        val before = c.demanded()
        c.demand(op)
        return CameraDemand.outcome(op, before, c.demanded())
    }

    /**
     * `:wake` → chính, CÓ THỨ TỰ: receiver của tiến trình chính đặt `resultCode`; không ai trả lời ⇒ giữ mã khởi đầu
     * ([Outcome.UNREACHABLE]). Hạn TỔNG [RESULT_TIMEOUT_MS] ⇒ [Outcome.UNREACHABLE]; kết quả về MUỘN hơn hạn chỉ ghi log (câu
     * đã nói không rút lại được, làn ghi đã đi tiếp) — hợp đồng *"đúng một lần"*. 2.93 wave 2C · CAM-WAKE-COLD-MAIN: mã khởi
     * đầu về NGAY (tiến trình chính đang khởi động, receiver chưa đăng ký) ⇒ gửi lại MỘT lần trong cùng hạn — luật + cờ một-lần
     * ở `:core` [CameraWakeAsk] (bài `CameraWakeAskTest` chạy thật bằng đồng hồ giả); ở đây chỉ nối Android vào. Soát senior
     * wave 2B/2C [P2]: mỗi lượt gửi mang HẠN TỔNG (`deadline`) — receiver tới sau hạn thì bỏ (câu ✗ đã nói, KDoc
     * [CameraWakeAsk.expired]).
     */
    private fun sendForResult(app: Context, op: CameraDemand.Op, onResult: (Outcome) -> Unit) {
        val code = CameraDemand.encode(op)
        val deadline = SystemClock.elapsedRealtime() + RESULT_TIMEOUT_MS   // ≤ mốc hết hạn của CameraWakeAsk.run (t0 đọc sau dòng này)
        Log.i(TAG, "$code: gửi sang tiến trình chính (chờ ≤ $RESULT_TIMEOUT_MS ms, gửi lại tối đa 1 lần)")
        CameraWakeAsk.run(
            op = op,
            totalMs = RESULT_TIMEOUT_MS,
            now = { SystemClock.elapsedRealtime() },
            schedule = { ms, block -> main.postDelayed({ block() }, ms) },
            mainAlive = { VoiceWakeService.isMainProcessAlive(app) },
            send = { reply -> sendOnce(app, code, deadline, reply) },
            log = { Log.i(TAG, "$code: $it") },   // 🚗 số đo độ trễ cầu: "kết quả … sau N ms"
            onResult = onResult,
        )
    }

    /**
     * MỘT lượt broadcast CÓ THỨ TỰ trong gói; [reply] nhận `resultCode` (mã khởi đầu [Outcome.UNREACHABLE] = không receiver
     * nào trả lời — AOSP r47 `BroadcastQueue.java:1093-1133` giao mã của bản ghi cho `resultTo` khi hết receiver) trên luồng
     * chính, hoặc `null` khi gửi hỏng. [deadline] = hạn tổng của lượt hỏi (receiver tới sau nó thì bỏ, giữ mã khởi đầu).
     */
    private fun sendOnce(app: Context, code: String, deadline: Long, reply: (Int?) -> Unit) {
        try {
            app.sendOrderedBroadcast(
                Intent(ACTION).setPackage(app.packageName).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra(EXTRA_OP, code)
                    .putExtra(EXTRA_DEADLINE, deadline),
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, i: Intent?) = reply(resultCode)
                },
                main,
                Outcome.UNREACHABLE.code,
                null,
                null,
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "$code: gửi sang tiến trình chính hỏng", e)
            onMain { reply(null) }
        }
    }

    /** Phím vật lý gán `cam:<camera>` / `cam:off`. Mã hỏng ⇒ `false` + log (tầng gọi để phím đi tiếp như chưa gán). */
    fun fireKey(ctx: Context, spec: String): Boolean {
        val op = CameraDemand.parseKey(spec) ?: run {
            Log.w(TAG, "mã đích phím camera hỏng: «$spec»")
            return false
        }
        return fire(ctx, op)
    }

    /**
     * Cú bấm một ô LAUNCHER (thanh nút · ô widget lưới): việc camera ⇒ toggle camera ấy, *Tắt camera* ⇒ tắt; mã KHÔNG phải
     * việc camera ⇒ `false` và không làm gì (mã launcher tương lai mà bản này chưa biết: im lặng còn hơn mở nhầm).
     */
    fun tap(ctx: Context, launcherId: String): Boolean {
        if (!LauncherActions.isCamera(launcherId)) return false
        return fire(ctx, CameraDemand.tap(LauncherActions.cameraOf(launcherId)))
    }

    // ── 2.93 wave 2B · CAMERA-DOCK-ACTIVE-STATE — ô camera sáng theo trạng thái THẬT, nghe chứ không hỏi vòng ─────────────

    /**
     * Ô camera [which] có đang sáng không (luật [CameraDemand.isOn]). Controller chưa dựng ⇒ chưa camera nào mở ⇒ `false`,
     * và lượt ĐỌC này không dựng nó. Chỉ luồng chính (trạng thái controller là của luồng chính).
     */
    fun isOn(ctx: Context, which: CameraWhich): Boolean {
        val c = AppContainer.get(ctx)
        return c.cameraSignalCreated && CameraDemand.isOn(which, c.cameraSignal.demanded(), c.cameraSignal.showingCamera())
    }

    /**
     * Gắn [onChange] vào người nghe của controller (`onDemandChanged` — đổi camera theo yêu cầu HOẶC camera đang hiện) theo
     * VÒNG ĐỜI CỬA SỔ của [view]: gắn khi vào cửa sổ (kèm một lượt vẽ lại — trạng thái có thể đã đổi lúc ô khuất), GỠ khi
     * rời. Không vòng hỏi nào. Ô chưa từng vào cửa sổ (bản nháp đo sức chứa `WidgetCapacity`) ⇒ không nghe gì.
     *
     * Lượt gắn DỰNG controller nếu chưa có — chỉ ở tiến trình chính có màn (thanh nút · lưới ô · dải xem trước Cài đặt), nơi
     * lượt vẽ đầu của HOME vốn đã dựng nó (`KachiHomeRender` → `cameraSignal.tick()`); controller rỗng không mở luồng/HAL nào.
     */
    fun watchWhileAttached(view: View, onChange: () -> Unit) {
        val watcher = object : View.OnAttachStateChangeListener {
            private var unwatch: (() -> Unit)? = null

            override fun onViewAttachedToWindow(v: View) {
                unwatch?.invoke()
                unwatch = AppContainer.get(v.context).cameraSignal.onDemandChanged { onChange() }
                onChange()
            }

            override fun onViewDetachedFromWindow(v: View) {
                unwatch?.invoke()
                unwatch = null
            }
        }
        view.addOnAttachStateChangeListener(watcher)
        if (view.isAttachedToWindow) watcher.onViewAttachedToWindow(view)   // gắn muộn vào ô đã trong cửa sổ ⇒ nghe ngay
    }

    /**
     * Tiến trình CHÍNH: nhận lệnh từ `:wake`. Một lần mỗi tiến trình (`KachiApplication.onCreate`, sau cổng tiến trình nền).
     * Không truyền `Handler` ⇒ `onReceive` chạy trên LUỒNG CHÍNH — điều [applyHere] cần. Hỏng đăng ký ⇒ log, launcher vẫn
     * chạy (tính năng phụ không được giết `Application.onCreate`).
     */
    fun receiveInMain(ctx: Context) {
        val app = ctx.applicationContext ?: ctx
        try {
            ContextCompat.registerReceiver(app, Receiver(), IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: RuntimeException) {
            Log.w(TAG, "không đăng ký được receiver cầu `:wake` → chính — câu nói về camera qua phím vô-lăng sẽ không mở camera", e)
        }
    }

    private class Receiver : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (c == null || i?.action != ACTION) return
            val op = CameraDemand.decode(i.getStringExtra(EXTRA_OP))
            if (op == null) {
                Log.w(TAG, "bỏ lệnh camera không hợp lệ từ `:wake`: ${i.getStringExtra(EXTRA_OP)}")
                return
            }
            // Soát senior wave 2B/2C [P2]: tới SAU hạn của `:wake` (luồng chính bận — runnable của receiver xếp hàng) ⇒ câu ✗ đã
            // nói ⇒ KHÔNG áp, giữ mã khởi đầu: lệnh MỞ muộn là "mới nhất" và sẽ đè camera xi-nhan bật trong lúc chờ.
            val deadline = i.getLongExtra(EXTRA_DEADLINE, 0L)
            if (CameraWakeAsk.expired(deadline, SystemClock.elapsedRealtime())) {
                Log.w(TAG, "bỏ lệnh từ `:wake` tới sau hạn ${SystemClock.elapsedRealtime() - deadline} ms: ${CameraDemand.encode(op)}")
                return
            }
            val o = applyHere(c.applicationContext ?: c, op)
            if (isOrderedBroadcast) resultCode = o.code
            Log.i(TAG, "nhận từ `:wake`: ${CameraDemand.encode(op)} → $o")
        }
    }
}
