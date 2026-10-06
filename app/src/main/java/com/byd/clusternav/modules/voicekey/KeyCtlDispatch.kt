package com.byd.clusternav.modules.voicekey

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.byd.clusternav.AppContainer
import com.byd.clusternav.launcher.CarControlPort
import com.byd.clusternav.launcher.CarStatus
import com.byd.clusternav.launcher.ControlDef
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.ControlTileWrite
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.KeyCtlPlan
import com.byd.clusternav.launcher.KeyCtlTargets
import com.byd.clusternav.launcher.KeyCtlThrottle
import com.byd.clusternav.launcher.MacroExec
import com.byd.clusternav.launcher.VoiceControlDispatch

/**
 * ═══ FIX286 · R-KC — phím vật lý gán một NÚT XE (`ctl:<nút>:<việc>`) ═══════════════════════════════════════════
 *
 * Lối vào DUY NHẤT: [AssistantLauncher.launch] (chỗ phát đích của mọi dòng gán phím) ⇐
 * `NavAccessibilityService.onKeyEvent` ⇐ `VoiceKeyMatcher` (bắn một lần mỗi lần nhấn, nuốt cả DOWN/UP/lặp — phím mất
 * chức năng gốc, owner 03/10 *"overwrite chức năng cũ Ok"*).
 *
 * ## Không chặn `onKeyEvent` — [ĐO nguồn AOSP]
 * `KeyEventDispatcher.java:51` (r47 · r34) `ON_KEY_EVENT_TIMEOUT_MILLIS = 500`; quá hạn mà dịch vụ chưa trả lời thì
 * `:262-270` coi phím là KHÔNG xử lý và chuyển tiếp cho app (`FLAG_PASS_TO_USER`) ⇒ phím lọt. Đọc mốc + ghi vì vậy
 * xuống làn nền [ControlTileWrite.LANE] — CÙNG làn với cú chạm ô đơn ⇒ phím và ô xếp một hàng theo thứ tự bấm. Ngoại lệ
 * (Pass 5): nhánh rời-AUTO hai lệnh (`VoiceClimateStep`) + lượt đọc lại 300 ms chạy luồng riêng, KHÔNG giữ làn.
 *
 * ## Không có đường ghi thứ hai (spec KC2)
 * Thi hành = [VoiceControlDispatch.run] — chính đường của câu nói *"tăng gió"* · *"mở cốp"*: cốp chỉ MỞ khi đứng yên
 * (đọc tốc độ TƯƠI), nấc đáy gió là AUTO, *"xe này không có"*, đọc lại xác nhận, ghi qua `actByKind` →
 * `AppContainer.carControl` (`WakeOnWriteControl` → `HalBindingTable.write`). Câu trả lời của nó (`VoiceReply`) hiện
 * thành TOAST ngắn — không đọc thành tiếng (phím là thao tác không nhìn màn; tiếng chen nhạc/dẫn đường khi vặn núm).
 */
object KeyCtlDispatch {
    private const val TAG = "KeyCtl"

    private val main = Handler(Looper.getMainLooper())
    private val throttle = KeyCtlThrottle()
    @Volatile private var lastToast: Toast? = null
    @Volatile private var runner: KeyCtlRunner? = null

    /**
     * Một lần nhấn (đã qua `VoiceKeyMatcher`) của phím gán [spec]. Luồng chính, trong `onKeyEvent` ⇒ trả ngay.
     * `false` = mã đích hỏng/không còn (đã báo).
     */
    fun fire(ctx: Context, spec: String): Boolean {
        val app = ctx.applicationContext
        val t = KeyCtlTargets.decode(spec)
        if (t == null) {
            Log.w(TAG, "mã đích không hợp lệ / nút không còn: $spec")
            toast(app, KeyCtlPlan.invalidReply(spec))
            return false
        }
        when (val s = throttle.press(t, SystemClock.uptimeMillis())) {
            is KeyCtlThrottle.Step.Fire -> submit(app, s, "ngay")
            is KeyCtlThrottle.Step.Hold -> if (s.schedule) {
                Log.i(TAG, "gộp (chống dồn) ${t.controlId} tới ${s.flushAtMs}")
                main.postAtTime({
                    throttle.flush(t.controlId, SystemClock.uptimeMillis())?.let { submit(app, it, "gộp") }
                }, s.flushAtMs)
            }
            KeyCtlThrottle.Step.Drop -> Log.i(TAG, "bỏ (chống bấm kép) $spec")
        }
        return true
    }

    private fun submit(app: Context, f: KeyCtlThrottle.Step.Fire, why: String) {
        Log.i(TAG, "phím → ${f.target.spec} ×${f.count} ($why)")
        MacroExec.submitSerial(ControlTileWrite.LANE) {
            // 2.87 · R-FL2 — nhật ký ghi cả hành động ĐÃ GIẢI + nguồn trạng thái (CAR/MEMORY) của Đảo/Kế tiếp (§11).
            runCatching { runner(app).run(f) }
                .onSuccess { o -> o?.let { Log.i(TAG, "${f.target.spec} ⇒ $it") } }
                .onFailure { Log.w(TAG, "thi hành ${f.target.spec} hỏng", it) }
        }
    }

    /** Dựng MỘT lần cho đời tiến trình — cùng bộ lambda mà phiên giọng nói tiến trình chính dùng (`VoiceWiring`). */
    private fun runner(app: Context): KeyCtlRunner = runner ?: synchronized(this) {
        runner ?: KeyCtlRunner(
            controls = VoiceControlDispatch(
                control = { AppContainer.get(app).carControl },
                state = { HomeUiState(carStatus = AppContainer.get(app).carStatusRepository.status.value) },
                say = { toast(app, it) },
                freshCar = { id -> fresh(app, id) },
                onUi = { block -> main.post(block) },
                background = { block -> Thread(block, "KachiKeyCtl").start() },
            ),
            port = { AppContainer.get(app).carControl },
            say = { toast(app, it) },
        ).also { runner = it }
    }

    /**
     * Đọc TƯƠI một datum (cổng tốc độ của cốp). Màn nhà ở tiền cảnh ⇒ [AppContainer.refreshForRead] như phiên giọng
     * nói. Màn nhà KHÔNG ở tiền cảnh — ca thường gặp nhất của phím (đang ở app dẫn đường) — thì vòng poll ĐÃ DỪNG
     * (`KachiHomeWiring`: `repeatOnLifecycle(STARTED)` → `carStatusRepository.stop()`) và nhu cầu = `null`, nên
     * `refreshForRead` trả `null` và ảnh chụp có thể cũ hàng phút: dùng nó để quyết *"xe đang đứng yên"* là quyết bằng
     * một con số cũ. Khi ấy đọc ĐÚNG MỘT datum qua `CarDataDemand.Holder.withSoloIfIdle` (luật + bài kiểm ở `:core`).
     */
    private fun fresh(app: Context, id: String): CarStatus? = runCatching {
        AppContainer.get(app).readFresh(id)   // 2.93: luật dời nguyên sang `AppContainer.readFresh` (dùng chung với giọng nói)
    }.getOrNull()

    /** Một toast tại một thời điểm: núm vặn sinh nhiều câu liền nhau — xếp hàng thì câu cuối hiện sau cả chục giây. */
    private fun toast(app: Context, text: String) {
        main.post {
            runCatching {
                lastToast?.cancel()
                lastToast = Toast.makeText(app, text, Toast.LENGTH_SHORT).also { it.show() }
            }
        }
    }
}

/**
 * Phần thi hành của [KeyCtlDispatch], tách để bài kiểm `:app` dựng được bằng cổng xe giả (`KeyCtlSafetyTest`): một
 * lần bắn ⇒ [KeyCtlPlan] ⇒ [VoiceControlDispatch.run]. Lượt đọc HAL duy nhất ở đây là trạng thái cho Đảo / Kế tiếp
 * (chỉ nút có readKey). 2.87 · R-FL2: Đảo / Kế tiếp KHÔNG còn ca "đọc không được ⇒ không bắn" — [KeyCtlPlan] lùi về
 * lệnh cuối Kachi đã gửi (`ControlLastSent.shared`, cùng bảng với ô trên màn) và giải ra hành động cụ thể trước khi
 * giao [VoiceControlDispatch.run] (mọi cổng an toàn áp lên hành động đã giải; ghi bảng chỉ khi lệnh thành công).
 * SOÁT vòng 1 · P1: vận tốc cho luật *"trí nhớ cũ + xe chạy ⇒ hướng luôn được phép"* của [KeyCtlPlan] lấy từ CHÍNH nguồn
 * của cổng thi hành ([VoiceControlDispatch.speedKmh]) — chỉ đọc khi Đảo từ trí nhớ ra MỞ cốp.
 *
 * @return kết quả của [KeyCtlPlan] (cho nhật ký), `null` khi nút không còn trong registry.
 */
internal class KeyCtlRunner(
    private val controls: VoiceControlDispatch,
    private val port: () -> CarControlPort,
    private val say: (String) -> Unit,
    private val resolve: (String) -> ControlDef? = ControlRegistry::byId,
) {
    fun run(f: KeyCtlThrottle.Step.Fire): KeyCtlPlan.Outcome? {
        val def = resolve(f.target.controlId) ?: run { say(KeyCtlPlan.invalidReply(f.target.spec)); return null }
        val o = KeyCtlPlan.of(def, f.target, f.count, speedKmh = controls::speedKmh) {
            runCatching { port().readState(def.id) }.getOrNull()
        }
        when (o) {
            is KeyCtlPlan.Outcome.Run -> controls.run(o.intent) {}
            KeyCtlPlan.Outcome.Invalid -> say(KeyCtlPlan.invalidReply(f.target.spec))
        }
        return o
    }
}
