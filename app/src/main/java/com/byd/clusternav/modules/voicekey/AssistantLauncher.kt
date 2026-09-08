package com.byd.clusternav.modules.voicekey

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import com.byd.clusternav.AdbKeys
import com.byd.clusternav.Lang
import com.byd.clusternav.Prefs
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellFailure
import com.byd.clusternav.carexec.LocalShellResult
import com.byd.clusternav.carexec.LocalShellRetry
import com.byd.clusternav.core.FloatAppList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mở đích của "Nút vật lý → mở app". Rework 1.19: đích = 1 STRING — hoặc **package name** (mở thẳng app),
 * hoặc 1 trong 2 **sentinel** đặc biệt. Gọi từ [com.byd.clusternav.modules.navaccess.NavAccessibilityService]
 * (không có Activity context) → cần FLAG_ACTIVITY_NEW_TASK.
 *
 * BỎ kiểu đoán ACTION_ASSIST theo enum trợ lý (thủ phạm 1.18: chọn Kiki nhưng mở Gemini vì intent chung
 * dính trợ lý mặc định). Chọn app trực tiếp → launch-intent của đúng package → không lạc app khác.
 */
object AssistantLauncher {
    private const val TAG = "VoiceKeyLauncher"

    // Chống self-loop [P2 review]: nếu capture keycode == 231 (preset VOICE_ASSIST) + target Gemini, emit 231 có thể
    // tự kích lại onKeyEvent(231) → storm. Debounce = tối đa 1 emit / khoảng này (cùng tinh thần 8hare 800ms).
    private const val VOICE_ASSIST_DEBOUNCE_MS = 1500L
    @Volatile private var lastVoiceAssistEmitMs = 0L

    /** Đơn-luồng cho vòng chờ cấp quyền adb (F2): xem [launchViaVoiceAssistKey]. */
    private val voiceAssistInFlight = AtomicBoolean(false)

    /** Đã nói cho owner biết "đang bận" trong vòng chờ hiện tại chưa — mỗi vòng đúng MỘT lần. */
    private val voiceAssistBusyNoticed = AtomicBoolean(false)

    // Nguồn CHÂN LÝ DUY NHẤT của 2 sentinel = Prefs (nơi lưu/migrate spec + nơi MainActivity dựng dropdown).
    // Delegate compile-time const → không thể lệch literal giữa producer (Prefs/MainActivity) và consumer (đây).
    /** Trợ lý mặc định hệ thống (ghim đầu list). */
    const val TARGET_ASSIST = Prefs.VK_TARGET_ASSIST

    /** Nhận dạng giọng nói — RecognizerIntent (ghim đầu list). */
    const val TARGET_RECOGNIZER = Prefs.VK_TARGET_RECOGNIZER

    /** Trợ lý hệ thống qua phím cứng: phát KEYCODE_VOICE_ASSIST (231) qua dadb — như app 8hare. */
    const val TARGET_GEMINI_KEY = Prefs.VK_TARGET_GEMINI_KEY

    private const val PKG_BARD = "com.google.android.apps.bard"                 // app Gemini
    private const val PKG_GSA  = "com.google.android.googlequicksearchbox"      // app Google (host voice service)
    private const val GSA_ASSIST = "$PKG_GSA/com.google.android.voiceinteraction.GsaVoiceInteractionService"
    private const val GSA_RECOG  = "$PKG_GSA/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"

    /** Spec này = "muốn Gemini/Google dạng ASSISTANT (nói được)" → phải đi keyevent 231, KHÔNG mở app home.
     *  Gồm: sentinel 231, VÀ khi user lỡ chọn thẳng app Gemini/Google (mở home vô dụng cho voice-key). */
    fun isGeminiVoiceSpec(spec: String): Boolean = spec == TARGET_GEMINI_KEY || spec == PKG_BARD || spec == PKG_GSA

    /**
     * Có ít nhất MỘT binding phím trỏ Gemini không? Dùng để gate việc re-apply trợ lý hệ thống lúc mở app
     * (`MainActivity`) và lúc boot (`BootSetupService`) — owner CHỈ dùng Kiki / app thường thì trả false ⇒
     * KHÔNG đụng dadb. Đọc lỗi ⇒ false (degrade-safe, không đoán).
     */
    fun hasGeminiBinding(ctx: Context): Boolean = runCatching {
        Prefs.voiceKeyBindings(ctx).any { isGeminiVoiceSpec(it.targetSpec) }
    }.getOrDefault(false)

    /** @param spec package name của app, hoặc [TARGET_ASSIST]/[TARGET_RECOGNIZER]/[TARGET_GEMINI_KEY]. */
    fun launch(ctx: Context, spec: String): Boolean {
        // Gemini/Google chỉ có nghĩa dạng ASSISTANT (voice). Mở app home = vô dụng (bug 1.19). → route keyevent 231.
        if (isGeminiVoiceSpec(spec)) return launchViaVoiceAssistKey(ctx)
        val app = ctx.applicationContext
        val candidates: List<Intent> = when (spec) {
            TARGET_ASSIST -> listOf(Intent(Intent.ACTION_ASSIST), Intent(Intent.ACTION_VOICE_COMMAND))
            TARGET_RECOGNIZER -> listOf(
                Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE),
                Intent(RecognizerIntent.ACTION_WEB_SEARCH),
            )
            else -> buildList {
                // Mở THẲNG app đã chọn: launch-intent của package; fallback ACTION_MAIN+LAUNCHER setPackage.
                app.packageManager.getLaunchIntentForPackage(spec)?.let { add(it) }
                add(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(spec))
            }
        }
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { app.startActivity(intent); true }.getOrDefault(false)
            if (ok) {
                Log.i(TAG, "launched target=$spec via ${intent.getPackage() ?: intent.action}")
                return true
            }
        }
        Log.e(TAG, "no activity handled target=$spec")
        return false
    }

    /**
     * Phát **KEYCODE_VOICE_ASSIST (231)** qua dadb loopback (uid shell) — y như app 8hare bắt phím voice
     * rồi chạy `input keyevent 231`. Sự kiện phím-cứng này được hệ thống route tới **trợ lý hệ thống**
     * (đặt = Google/Gemini bằng [setSystemAssistant]) → mở đúng surface voice (auto-listen), KHÔNG chooser,
     * KHÔNG nhầm intent như ACTION_ASSIST. Chạy nền (dadb), degrade-safe.
     */
    private fun launchViaVoiceAssistKey(ctx: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastVoiceAssistEmitMs < VOICE_ASSIST_DEBOUNCE_MS) {
            Log.i(TAG, "keyevent 231 bỏ qua (debounce ${VOICE_ASSIST_DEBOUNCE_MS}ms — chống self-loop nếu capture==231)")
            return true
        }
        val app = ctx.applicationContext
        // Đơn-luồng (F2): vòng chờ cấp quyền adb dài tới ~31 s, dài hơn hẳn debounce 1.5 s. Không có chốt
        // này thì owner bấm mic vài lần là sinh vài vòng chờ song song → vài phiên dadb → head unit bung
        // vài hộp thoại "Cho phép gỡ lỗi USB" chồng nhau.
        if (!voiceAssistInFlight.compareAndSet(false, true)) {
            Log.i(TAG, "keyevent 231 bỏ qua — đang có một vòng chờ cấp quyền adb chạy dở")
            // KHÔNG cập nhật debounce ở đây: cú bấm này không phát lệnh nào, đẩy mốc debounce chỉ làm phím
            // mic chết thêm 1,5 s sau khi vòng chờ kết thúc.
            // Nói cho owner một lần mỗi vòng: im lặng ~31 s là đúng cái làm owner tưởng app hỏng (F2).
            if (voiceAssistBusyNoticed.compareAndSet(false, true)) {
                toast(app, Lang.t(
                    "Đang thử nối vào xe để mở trợ lý — chờ vài giây…",
                    "Still reaching the head unit to open the assistant — hang on…",
                ))
            }
            return true
        }
        lastVoiceAssistEmitMs = now
        voiceAssistBusyNoticed.set(false)
        // CHẠY NỀN: phiên dadb ~1-2s — KHÔNG block onKeyEvent (nếu block sẽ trễ/ANR phím). Fire-and-forget.
        val worker = Thread {
            try {
                runCatching {
                    val keys = AdbKeys.ensure(app)
                    val result = LocalDeviceShell.sessionResult(
                        keys,
                        // Owner vừa giữ phím mic ⇒ đang đứng trước màn hình ⇒ hộp thoại "Cho phép gỡ lỗi
                        // USB" bung ra là lúc bấm được ngay. Chờ có giãn cách thay vì bỏ (F2).
                        retry = LocalShellRetry.AWAIT_ADB_APPROVAL,
                        onProgress = { attempt, reason, waitMs -> reportProgress(app, attempt, reason, waitMs) },
                    ) { sh ->
                        val r = sh("input keyevent 231")
                        Log.i(TAG, "keyevent 231 (VOICE_ASSIST) exit=${r.exitCode} err=${r.errorOutput.trim().take(80)}")
                        r.exitCode == 0
                    }
                    when (result) {
                        is LocalShellResult.Ok ->
                            Log.i(TAG, "voice-assist key: phiên dadb OK sau ${result.attempts} lần thử")
                        is LocalShellResult.Failed -> {
                            Log.e(
                                TAG,
                                "voice-assist key: hỏng sau ${result.attempts} lần thử — ${result.reason} " +
                                    "(đã phát lệnh=${result.commandDispatched})",
                                result.cause,
                            )
                            // Đã phát được lệnh nghĩa là bắt tay adb ĐÃ XONG — quyền không phải vấn đề, và
                            // `input keyevent 231` có thể đã tới nơi (chỉ kết quả đọc về là hỏng). Bảo owner
                            // "chưa cấp quyền" lúc này là nói sai; nói đúng cái đã biết thôi.
                            toast(app, if (result.commandDispatched) {
                                Lang.t(
                                    "Đã gửi lệnh mở trợ lý nhưng xe không trả lời kịp — thử lại nếu chưa thấy trợ lý.",
                                    "Sent the assistant key but the head unit did not answer in time — retry if nothing opened.",
                                )
                            } else {
                                failureMessage(result.reason)
                            })
                        }
                    }
                }.onFailure { Log.e(TAG, "voice-assist key launch failed: $it") }
            } finally {
                voiceAssistInFlight.set(false)
            }
        }
        // `start()` ném (hết bộ nhớ / hết luồng) SAU khi CAS đã chiếm chốt ⇒ `finally` bên trong runnable
        // không bao giờ chạy ⇒ chốt kẹt `true` vĩnh viễn ⇒ phím mic câm tới khi kill process. Nhả tay ở đây.
        runCatching { worker.start() }.onFailure {
            voiceAssistInFlight.set(false)
            Log.e(TAG, "không khởi được luồng phát keyevent 231: $it")
        }
        return true   // đã nhận lệnh; emit chạy nền
    }

    /**
     * Nói cho owner biết đang chờ cái gì. Chỉ báo ở lần hỏng ĐẦU: vòng chờ tối đa 4 lần, báo mỗi lần sẽ
     * thành 3 toast chồng nhau trên xe đang chạy.
     *
     * Câu chữ nhắc **tích "luôn cho phép"**: mỗi lần thử là một kết nối MỚI, nên nếu owner bấm Cho phép mà
     * không tích ô đó thì quyền chỉ sống với đúng kết nối đang treo — lần thử sau lại hỏi lại.
     */
    private fun reportProgress(ctx: Context, attempt: Int, reason: LocalShellFailure, waitMs: Long) {
        Log.i(TAG, "adb loopback: lần $attempt hỏng vì $reason, chờ ${waitMs}ms rồi thử lại")
        if (attempt != 1) return
        if (reason == LocalShellFailure.AWAITING_APPROVAL || reason == LocalShellFailure.AUTH_REJECTED) {
            toast(
                ctx,
                Lang.t(
                    "Bấm \"Cho phép/Allow\" (tích \"luôn cho phép\") trên hộp thoại gỡ lỗi USB — app đang chờ…",
                    "Tap \"Allow\" (tick \"always allow\") on the USB-debugging dialog — waiting…",
                ),
            )
        }
    }

    /** Lý do hỏng, viết cho owner đọc — thay cho im lặng của bản trước 2026-08-24. */
    private fun failureMessage(reason: LocalShellFailure): String = when (reason) {
        LocalShellFailure.AWAITING_APPROVAL, LocalShellFailure.AUTH_REJECTED -> Lang.t(
            "Chưa được cấp quyền gỡ lỗi USB. Bấm \"Cho phép/Allow\" (nhớ tích \"luôn cho phép\") rồi thử lại.",
            "USB debugging not authorised yet. Tap \"Allow\" (tick \"always allow\") then try again.",
        )
        LocalShellFailure.PORT_CLOSED -> Lang.t(
            "Cổng gỡ lỗi 5555 chưa bật trên xe — trợ lý giọng nói không chạy được.",
            "Debug port 5555 is off on the head unit — the voice assistant cannot run.",
        )
        LocalShellFailure.IO_ERROR, LocalShellFailure.UNKNOWN -> Lang.t(
            "Không nối được vào xe để mở trợ lý. Thử lại sau.",
            "Could not reach the head unit to open the assistant. Try again later.",
        )
    }

    private fun toast(ctx: Context, text: String) {
        val app = ctx.applicationContext
        // Gọi từ thread nền (dịch vụ hỗ trợ không có Looper riêng) → phải đẩy về main looper.
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(app, text, Toast.LENGTH_LONG).show() }
        }
    }

    /**
     * [một lần] Đặt **trợ lý hệ thống = Google/Gemini** — replicate ĐẦY ĐỦ + ĐÚNG THỨ TỰ recipe app 8hare (proven trên xe BYD).
     * Trả **""** nếu OK; ngược lại trả thông báo lỗi (thiếu app / dadb fail) để hiển thị cho owner.
     * 8hare BẮT BUỘC cả Google app (googlequicksearchbox) LẪN Gemini (bard) phải cài — thiếu 1 trong 2 thì recipe vô hiệu
     * (assist route tới GsaVoiceInteractionService không tồn tại). Có `Thread.sleep(300)` giữa clear+set voice_interaction_service.
     */
    fun setSystemAssistant(ctx: Context, retry: LocalShellRetry = LocalShellRetry.AWAIT_ADB_APPROVAL): String {
        val app = ctx.applicationContext
        val pm = app.packageManager
        val missing = listOf(PKG_GSA to "Google (googlequicksearchbox)", PKG_BARD to "Gemini (com.google.android.apps.bard)")
            .filter { runCatching { pm.getPackageInfo(it.first, 0) }.isFailure }
            .map { it.second }
        if (missing.isNotEmpty()) {
            Log.w(TAG, "setSystemAssistant: thiếu app bắt buộc: $missing")
            return "Thiếu app bắt buộc: ${missing.joinToString(", ")}. Cài đủ Google App + Gemini rồi bật lại."
        }
        return runCatching {
            val keys = AdbKeys.ensure(app)
            val result = LocalDeviceShell.sessionResult(
                keys,
                // App-open / chọn-trong-app: owner đang nhìn màn hình ⇒ AWAIT_ADB_APPROVAL (mặc định). Boot
                // headless: owner KHÔNG ở màn hình ⇒ caller truyền NONE (một lần, không chờ ~31s — F6).
                retry = retry,
                onProgress = { attempt, reason, waitMs -> reportProgress(app, attempt, reason, waitMs) },
            ) { sh ->
                sh("settings put secure assistant $GSA_ASSIST")
                sh("settings put secure voice_interaction_service ''")
                Thread.sleep(300)   // như 8hare: để clear settle trước khi set lại (nếu không, set lại có thể bị bỏ qua)
                sh("settings put secure voice_interaction_service $GSA_ASSIST")
                sh("settings put secure voice_recognition_service $GSA_RECOG")
                // byd_float_app_list: APPEND (không clobber app khác) googlequicksearchbox + bard + chính mình.
                val cur = sh("settings get global byd_float_app_list").output.trim()
                val merged = FloatAppList.merge(cur, listOf(PKG_GSA, PKG_BARD, app.packageName))
                sh("settings put global byd_float_app_list $merged")
                sh("appops set $PKG_GSA SYSTEM_ALERT_WINDOW allow")
                sh("appops set $PKG_BARD SYSTEM_ALERT_WINDOW allow")
                Log.i(TAG, "system assistant → Google/Gemini (full 8hare recipe); float_app_list=$merged")
                true
            }
            when (result) {
                is LocalShellResult.Ok -> {
                    Log.i(TAG, "setSystemAssistant OK sau ${result.attempts} lần thử")
                    ""
                }
                is LocalShellResult.Failed -> {
                    Log.e(
                        TAG,
                        "setSystemAssistant hỏng sau ${result.attempts} lần thử — ${result.reason} " +
                            "(đã phát lệnh=${result.commandDispatched})",
                        result.cause,
                    )
                    // Hỏng SAU khi đã phát lệnh = công thức 12 bước chạy dở. Nguy hiểm nhất là khe giữa
                    // `voice_interaction_service ''` và lần đặt lại — dừng đúng chỗ đó là trợ lý hệ thống
                    // đang RỖNG. Owner phải biết để bật lại, không được nghĩ "không có gì thay đổi".
                    if (result.commandDispatched) {
                        failureMessage(result.reason) + " " + Lang.t(
                            "Công thức trợ lý mới chạy được một phần — bật lại công tắc để chạy đủ.",
                            "The assistant recipe was only partly applied — toggle it again to finish.",
                        )
                    } else {
                        failureMessage(result.reason)
                    }
                }
            }
        }.getOrElse { Log.e(TAG, "setSystemAssistant failed: $it"); "Lỗi đặt trợ lý: ${it.message}" }
    }
}
