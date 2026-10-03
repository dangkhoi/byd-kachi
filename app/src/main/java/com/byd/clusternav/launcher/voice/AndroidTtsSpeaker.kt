package com.byd.clusternav.launcher.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LangHost
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.voiceLangOf
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * ═══ V1 pha NÓI · ĐƯỜNG (a) — MÁY ĐỌC CỦA HỆ THỐNG ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R2a**. Rẻ nhất trong ba đường: 0 byte đĩa, 0 byte RAM của Kachi,
 * và trên đầu xe có sẵn gói `vi-VN` thì nó đọc ngay từ bản cài đầu tiên.
 *
 * ## Hỏi giọng của **ngôn ngữ GIỌNG NÓI**, không viết cứng `vi-VN`
 * Ngôn ngữ đi qua [LangHost.voiceLocale] (spec `kachi-i18n-zh-th-ms.html` R6: giao diện EN ⇒ English, mọi tiếng khác
 * ⇒ tiếng Việt) — cùng một chỗ map ngôn ngữ→`Locale` mà mọi chỗ định dạng của launcher dùng
 * (`LauncherLocaleContractTest` canh điều đó). Ba lý do:
 *  1. một chiếc xe đặt English đang nhận câu trả lời **tiếng Anh** (`VoiceReply` theo tiếng giọng nói) — đọc chuỗi
 *     ấy bằng giọng Việt cho ra một thứ không ai nghe ra là tiếng gì;
 *  2. giao diện zh/th/ms nhận câu trả lời **tiếng Việt** — hỏi giọng theo tiếng giao diện ([LangHost.locale]) là đi
 *     tìm giọng Trung/Thái cho một câu tiếng Việt;
 *  3. viết cứng `Locale("vi","VN")` ở đây là dựng **chỗ map thứ hai**, và chỗ thứ hai bao giờ cũng là chỗ lệch.
 * [voiceLang] là lambda của phiên (`VoiceSession.voiceLang`): ở `:wake` nó đọc ảnh chụp ngữ pháp, không phải
 * `Strings.current` (luôn VI ở đó).
 *
 * Đổi ngôn ngữ giữa chuyến: ở tiến trình chính màn chính `recreate()` ⇒ phiên mới ⇒ máy đọc mới; nhưng phiên `:wake`
 * (WAKE/HOLD) được DÙNG LẠI suốt đời tiến trình ⇒ máy đọc phải tự theo. [TtsVoiceLang.sync] chạy ở MỌI cửa đọc/chọn
 * ([available] · [languageStatus]): tiếng của câu này khác tiếng đã đặt ⇒ hỏi + đặt lại, và số `isLanguageAvailable`
 * mà `VoiceSpeakerRouter.probe` đọc cũng là số của tiếng MỚI (soát 2.87 · P2).
 *
 * ## ⚠ VÌ SAO PHẢI KIỂM GIỌNG CHỨ KHÔNG CHỈ KIỂM "CÓ ENGINE KHÔNG"
 * [ĐO] 2026-09-15, máy ảo `emulator-5554`: `pm list packages` có `com.google.android.tts`, `cmd package
 * query-services -a android.intent.action.TTS_SERVICE` trả về đúng một dịch vụ đang bật — **nhưng**
 * `/data/data/com.google.android.tts/app_voices` **rỗng** (`ls -la` chỉ có `.` và `..`), `app_patts` cũng rỗng,
 * và `settings get secure tts_default_synth` = `null`. Tức có engine mà **không có giọng nào**.
 *
 * Ở trạng thái ấy `isLanguageAvailable` trả `LANG_MISSING_DATA` (−1) và `speak()` vẫn trả `SUCCESS`: câu được
 * nhận, không có tiếng nào phát ra, không có lỗi nào ghi lại. Đó là lý do ngưỡng nằm ở
 * [VoiceSpeakerSelector.LANG_AVAILABLE] — xem KDoc ở đó.
 *
 * ## Tiêu điểm âm thanh: **cùng một kiểu** với âm báo của micro
 * `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` + `CONTENT_TYPE_SPEECH`, gương của [VoiceCapture.requestFocus]. Nhạc chỉ
 * **nhỏ xuống** chứ không dừng: một câu xác nhận 2 giây mà làm đứt bài nhạc đang nghe là một cái giá không ai
 * chịu trả hai lần — họ sẽ tắt luôn tính năng.
 */
class AndroidTtsSpeaker(
    ctx: Context,
    /** Ngôn ngữ GIỌNG NÓI để chọn giọng (xem KDoc lớp); mặc định suy từ `Strings.current` (tiến trình chính). */
    voiceLang: () -> Lang = { voiceLangOf(Strings.current) },
) : VoiceSpeaker {

    override val kind: VoiceSpeakerKind = VoiceSpeakerKind.ANDROID_TTS

    private val app = ctx.applicationContext

    /** Đã dựng xong dịch vụ chưa (`onInit` == SUCCESS) — dựng là **bất đồng bộ**, xem KDoc [VoiceSpeaker.available]. */
    private val inited = AtomicBoolean(false)

    /** Dịch vụ đã bị [shutdown] chưa — chốt để `onInit` về muộn không bật lại một máy đọc đã nhả. */
    private val dead = AtomicBoolean(false)

    /**
     * Tiếng đã đặt cho engine + câu trả lời của nền tảng cho tiếng ấy (`null` = chưa biết: chưa `onInit` xong, hoặc
     * hỏi hỏng) — đi theo tiếng giọng nói của TỪNG câu, xem KDoc lớp. ⚠ Khai TRƯỚC [tts] (cùng lý do [inited]).
     */
    private val voice = TtsVoiceLang { LangHost.voiceLocale(runCatching { voiceLang() }.getOrDefault(Lang.VI)) }

    private val focus = AtomicReference<AudioFocusRequest?>(null)

    /** Số thứ tự câu — [UtteranceProgressListener] chỉ được nhả tiêu điểm của **đúng câu nó vừa đọc**. */
    private val seq = AtomicInteger(0)

    /**
     * Nhả tiêu điểm khi câu đã đọc xong — **hoặc hỏng**.
     *
     * Bỏ `onError` là bỏ đúng ca người ta sẽ gặp trên xe: engine chết giữa câu ⇒ không ai nhả tiêu điểm ⇒ nhạc
     * của cả xe **kẹt ở mức nhỏ** cho tới khi có app khác xin tiêu điểm. Một lỗi không kêu, không log, và người
     * dùng chỉ biết là "xe tự nhiên bé tiếng".
     *
     * ⚠ Khai **TRƯỚC** [tts]: `onInit` chạm vào nó, và thứ tự khởi tạo trường trong Kotlin là thứ tự viết. Khai
     * sau thì một `onInit` về sớm sẽ đọc một tham chiếu chưa gán (`null` ở bytecode, `NullPointerException` ở
     * tầng Kotlin) — hỏng đúng ở máy chậm, không bao giờ hỏng ở máy soạn thảo.
     */
    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished(utteranceId)

        // Nền tảng gọi bản 2 tham số; bản 1 tham số vẫn phải cài vì lớp cha khai nó `abstract`.
        // ⚠ Thông điệp của `@Deprecated` viết bằng TIẾNG ANH có chủ ý: `LauncherI18nContractTest` quét chuỗi
        // tiếng Việt viết cứng ở `:app` và nó KHÔNG coi `@Deprecated(` là một lời gọi chẩn đoán (khác `Log.*`).
        @Deprecated("Platform calls the 2-arg overload; kept because the superclass declares it abstract.")
        override fun onError(utteranceId: String?) = finished(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "đọc hỏng ($utteranceId, mã $errorCode)")
            finished(utteranceId)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    /**
     * ═══ OQ4 · MỐC **ĐỌC XONG** — nhả tiêu điểm, rồi báo cho chỗ đang chờ ═══════════════════════════
     *
     * Bốn sự kiện của nền tảng (`onDone` · hai `onError` · `onStop`) đều là *"câu này hết đời"*, và cả bốn đều
     * phải mở cổng cho [VoiceSession.confirm] — nếu chỉ `onDone` mở cổng thì một câu hỏi bị engine từ chối giữa
     * chừng sẽ **không bao giờ mở micro**, tức cổng xác nhận chết im đúng lúc cần nó nhất.
     *
     * `getAndSet(null)` ⇒ gọi lại **nhiều nhất một lần** (vế (1) của hợp đồng [VoiceSpeaker.speak]): nền tảng
     * bắn `onStop` rồi `onDone` cho cùng một câu trên vài ROM.
     */
    private fun finished(utteranceId: String?) {
        abandonIfCurrent(utteranceId)
        if (utteranceId != null && utteranceId != idOf(seq.get())) return
        pending.getAndSet(null)?.let { runCatching { it() } }
    }

    /** Việc phải làm khi câu MỚI NHẤT đọc xong; `null` = không ai chờ. Xem [finished]. */
    private val pending = AtomicReference<(() -> Unit)?>(null)

    /**
     * Nhả tiêu điểm — **chỉ khi** sự kiện thuộc về câu MỚI NHẤT (đó là điều KDoc [seq] hứa).
     *
     * [speak] dùng `QUEUE_FLUSH`, nên nói hai câu sát nhau thì câu cũ bắn `onStop(interrupted = true)` **sau khi**
     * câu mới đã xin tiêu điểm xong. Nhả vô điều kiện ở đó là giật mất tiêu điểm của chính câu đang đọc: nhạc
     * bật to trở lại ngay giữa câu xác nhận, còn tiêu điểm thì không ai nhả nữa cho tới câu sau.
     *
     * `utteranceId` rỗng (vài ROM không trả lại id) ⇒ vẫn nhả: thà mất một lượt ducking còn hơn giữ tiêu điểm của
     * cả xe mãi mãi — cùng cách chọn đã ghi ở KDoc [listener].
     */
    private fun abandonIfCurrent(utteranceId: String?) {
        if (utteranceId != null && utteranceId != idOf(seq.get())) return
        abandonFocus()
    }

    private fun idOf(n: Int): String = ID_PREFIX + n

    /**
     * ⚠ [SOÁT OCR 2026-09-16] `onInit` về TRƯỚC khi hàm dựng gán xong [tts] — có thật, và im lặng.
     *
     * [tts] được dựng bằng `TextToSpeech(app) { onInit(it) }`: người nghe đã nằm trong tay nền tảng NGAY trong
     * lời gọi hàm dựng, nên nếu dịch vụ đã nối sẵn thì `onInit` chạy **trước** khi giá trị trả về kịp vào field.
     * Bản cũ gặp `tts == null` là `return` — không đặt [inited], không hẹn lại lần nào ⇒ đường máy đọc hệ thống
     * chết CẢ PHIÊN mà không một dòng log nào nói vì sao. Ghi lại lượt đã lỡ rồi chạy nốt ở câu hỏi kế
     * ([available] — cửa mà [speakInternal] và [VoiceSpeakerSelector] đều phải đi qua).
     *
     * ⚠⚠ Khai TRƯỚC [tts], không sau: thứ tự khởi tạo field của Kotlin chạy theo thứ tự KHAI, nên nếu ô này nằm
     * dưới [tts] thì đúng lượt `onInit` đồng bộ ấy sẽ đọc phải một tham chiếu còn `null` ⇒ NPE bị `runCatching`
     * của [tts] nuốt ⇒ mất luôn cả máy đọc. Đây là cùng lý do mà [inited]/[dead]/[voice] đứng trên đó.
     */
    private val initMissed = AtomicBoolean(false)

    private val tts: TextToSpeech? = runCatching {
        TextToSpeech(app) { status -> onInit(status) }
    }.onFailure { Log.w(TAG, "không dựng được máy đọc của hệ thống", it) }.getOrNull()

    private fun onInit(status: Int) {
        if (dead.get()) { shutdown(); return }
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "onInit trả $status — không có máy đọc dùng được")
            return
        }
        val engine = tts
        if (engine == null) { initMissed.set(true); return }
        configure(engine)
    }

    /** Chạy nốt lượt cấu hình đã lỡ vì [tts] chưa kịp gán — xem KDoc [initMissed]. */
    private fun catchUpInit() {
        if (!initMissed.compareAndSet(true, false)) return
        val engine = tts ?: return
        if (dead.get()) return
        configure(engine)
    }

    /**
     * Cấu hình CHUNG (không phụ thuộc tiếng) một lần, rồi lượt đặt tiếng đầu tiên ([follow]) — RỒI MỚI mở cổng [inited].
     *
     * ⚠ Soát vòng 2 [P3] — thứ tự là để KHÔNG có vòng khoá: `onInit` chạy trên luồng chính khi ĐANG giữ `mStartLock` của
     * `TextToSpeech` [ĐO nguồn android-10.0.0_r47 `TextToSpeech.java:2220-2228` (onPostExecute `synchronized(mStartLock)`) →
     * `:832-838` dispatchOnInit; `isLanguageAvailable`/`setLanguage` đi `runAction` `:756-766` cũng `synchronized(mStartLock)`;
     * android-12.0.0_r34 cùng cấu trúc]. Bản trước mở [inited] TRƯỚC [follow]: trong khe đó một luồng phiên gọi [available]
     * ⇒ giữ khoá của [TtsVoiceLang.sync] rồi chờ `mStartLock` (luồng chính giữ), còn luồng chính vào `voice.sync` chờ khoá kia
     * ⇒ treo luồng chính vĩnh viễn (ANR). Nay [follow] xong (khoá `sync` đã nhả) mới mở cổng ⇒ không luồng nào khác vào được
     * `voice.sync` trong lúc `onInit` còn giữ `mStartLock`.
     */
    private fun configure(engine: TextToSpeech) {
        runCatching {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            engine.setOnUtteranceProgressListener(listener)
        }.onFailure { Log.w(TAG, "không cấu hình được máy đọc", it); return }
        follow(engine)
        inited.set(true)
    }

    /**
     * Đưa engine về tiếng giọng nói của câu NÀY ([TtsVoiceLang.sync] — một phép so khi không đổi). `true` = tiếng ấy có
     * giọng thật. Chỉ gọi khi dịch vụ đã dựng xong: từ [configure] (lượt đầu, TRƯỚC khi mở [inited]) hoặc qua [ready].
     */
    private fun follow(engine: TextToSpeech): Boolean {
        val r = voice.sync(EnginePort(engine))
        if (r.changed) {
            if (r.usable) Log.i(TAG, "máy đọc hệ thống sẵn sàng (${r.locale}, isLanguageAvailable=${r.status})")
            else Log.i(TAG, "máy đọc hệ thống KHÔNG có giọng ${r.locale} (isLanguageAvailable=${r.status})")
        }
        return r.usable
    }

    private class EnginePort(private val e: TextToSpeech) : TtsVoiceLang.Port {
        override fun isLanguageAvailable(locale: Locale): Int = e.isLanguageAvailable(locale)
        override fun setLanguage(locale: Locale) { e.language = locale }
    }

    /** Engine đã dựng + cấu hình chung xong và chưa nhả — điều kiện để hỏi/đặt tiếng. */
    private fun ready(): TextToSpeech? {
        catchUpInit()
        return tts?.takeIf { inited.get() && !dead.get() }
    }

    /**
     * Số thô của nền tảng cho tiếng giọng nói HIỆN TẠI — cầu kiểm thử đọc để báo cáo, và [VoiceSpeakerSelector] đọc để
     * chọn (qua `VoiceSpeakerRouter.probe`). Tiếng đổi từ lần đặt trước ⇒ đặt lại TRƯỚC khi trả số (soát 2.87 · P2).
     */
    fun languageStatus(): Int? {
        ready()?.let { follow(it) }
        return voice.status()
    }

    /** Tên gói engine đang dùng — chỉ để báo cáo trên cầu kiểm thử. */
    fun engineName(): String? = runCatching { tts?.defaultEngine }.getOrNull()

    override fun available(): Boolean = ready()?.let { follow(it) } ?: false

    override fun speak(text: String): Boolean = speakInternal(text, null)

    /**
     * OQ4 — đọc rồi báo *"xong"*. Câu không nhận được ⇒ gọi [onDone] **ngay** (vế (1) của hợp đồng).
     *
     * Dùng một ô nhớ duy nhất ([pending]) chứ không một hàng đợi: `speak` luôn `QUEUE_FLUSH`, nên tại một lúc
     * chỉ có **một** câu sống. Câu trước bị đè thì việc chờ của nó cũng hết nghĩa — và [speakInternal] bắn nó
     * ngay để chỗ gọi cũ không treo (nó có hạn chờ riêng, nhưng chờ hết hạn cho một việc đã biết là vô nghĩa).
     */
    override fun speak(text: String, onDone: () -> Unit): Boolean = speakInternal(text, onDone)

    @Suppress("ReturnCount")
    private fun speakInternal(text: String, onDone: (() -> Unit)?): Boolean {
        fun fail(): Boolean { onDone?.let { runCatching { it() } }; return false }
        if (!available() || text.isBlank()) return fail()
        val engine = tts ?: return fail()
        // ⚠ [SOÁT Pass 4 · P2] Tăng số thứ tự TRƯỚC khi đặt việc chờ mới, không sau.
        // Đặt trước rồi mới tăng là để hở một khe: một sự kiện về muộn của câu CŨ (`onStop`/`onDone` của nó) rơi
        // vào khe ấy vẫn khớp `idOf(seq.get())` ⇒ [finished] vớ đúng việc chờ **của câu mới** và bắn ngay — tức
        // micro mở trong lúc câu hỏi xác nhận còn đang đọc, đúng hazard mà OQ4 sinh ra để chặn.
        val id = idOf(seq.incrementAndGet())
        // Câu cũ (nếu có ai chờ) sắp bị `QUEUE_FLUSH` đè ⇒ đóng sổ cho nó.
        pending.getAndSet(onDone)?.let { runCatching { it() } }
        // Xin tiêu điểm TRƯỚC khi đẩy câu: xin sau thì vài trăm ms đầu của câu đọc chồng lên nhạc đang phát ở
        // nguyên âm lượng — đúng khúc mang mấy từ quan trọng nhất ("Đã đặt…").
        requestFocus()
        val rc = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }.onFailure { Log.w(TAG, "speak ném", it) }.getOrDefault(TextToSpeech.ERROR)
        if (rc != TextToSpeech.SUCCESS) {
            Log.w(TAG, "máy đọc từ chối câu (rc=$rc)")
            abandonFocus()
            // Câu không vào hàng đợi ⇒ sẽ KHÔNG có sự kiện nào của nền tảng bắn về ⇒ phải tự đóng sổ ở đây,
            // nếu không thì cổng xác nhận chờ mãi một câu chưa bao giờ bắt đầu.
            return fail()
        }
        return true
    }

    override fun stop() {
        runCatching { tts?.stop() }
        abandonFocus()
        // `stop()` trên vài ROM KHÔNG bắn `onStop` cho câu đang đọc ⇒ tự đóng sổ. Gọi thừa vô hại: `getAndSet`
        // bảo đảm nhiều nhất một lần (vế (1) của hợp đồng).
        pending.getAndSet(null)?.let { runCatching { it() } }
    }

    override fun shutdown() {
        dead.set(true)
        inited.set(false)
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        abandonFocus()
        pending.getAndSet(null)?.let { runCatching { it() } }
    }

    // ── tiêu điểm âm thanh (gương của VoiceCapture) ──────────────────────────────────────────────

    private fun audio(): AudioManager? = app.getSystemService(AudioManager::class.java)

    private fun requestFocus() {
        if (focus.get() != null) return
        val am = audio() ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .build()
        val ok = runCatching { am.requestAudioFocus(req) }.getOrNull() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // Không xin được vẫn ĐỌC: vài ROM xe từ chối tiêu điểm cho app không phải media. Câu xác nhận 2 giây
        // chồng lên nhạc còn hơn im lặng — nhưng phải ghi lại, vì nó là dấu hiệu của một ROM khác thường.
        if (ok) focus.set(req) else Log.i(TAG, "ROM từ chối tiêu điểm âm thanh — vẫn đọc, không ducking")
    }

    private fun abandonFocus() {
        val req = focus.getAndSet(null) ?: return
        val am = audio() ?: return
        runCatching { am.abandonAudioFocusRequest(req) }
    }

    private companion object {
        const val TAG = "KachiVoiceTts"

        /** Tiền tố của `utteranceId` — chỉ Kachi dùng, để [abandonIfCurrent] nhận ra câu của chính mình. */
        const val ID_PREFIX = "kachi-"
    }
}
