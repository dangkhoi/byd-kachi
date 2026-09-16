package com.byd.clusternav.launcher.voice

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.BuildConfig
import com.byd.clusternav.R
import com.byd.clusternav.launcher.SettingsDeps
import com.byd.clusternav.launcher.SettingsRows
import com.byd.clusternav.launcher.setVoicePreferOffline
import com.byd.clusternav.launcher.setVoiceSpeakReplies
import com.byd.clusternav.launcher.voicePreferOffline
import com.byd.clusternav.launcher.voiceSpeakReplies

/**
 * ═══ KHỐI **GIỌNG NÓI** TRONG CÀI ĐẶT — cái TAI, cái MIỆNG, và hai công tắc ══════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R9** (mô hình nghe) + `kachi-voice-feedback.html` **R4 · T8 · T9**
 * (gói đọc + công tắc). Nằm ở *Cài đặt › Hệ thống & quyền › Nâng cao*, ngay trên ô *"Gõ lệnh chữ"* — cùng chỗ,
 * vì đó là hai nửa của một việc: lắp cái tai và cái miệng, rồi thử cái đầu.
 *
 * ## Vì sao bốn hàng ở CÙNG một lớp, không phải bốn chỗ
 * Chúng trả lời cùng một câu hỏi của người dùng (*"Kachi nghe/nói thế nào trên xe này"*) và chúng **phụ thuộc
 * nhau**: *"Ưu tiên giọng offline"* chỉ có nghĩa khi hàng *Giọng đọc offline* đã báo "đã cài". Tách ra hai lớp
 * thì câu mô tả của công tắc phải nói về một hàng mà nó không nhìn thấy — và câu ấy sẽ lệch ở lần đổi đầu tiên.
 *
 * ## Vì sao là một cú bấm của NGƯỜI DÙNG, không tự tải lúc mở app
 * 61 MB (gói đọc) + 266 MB (mô hình nghe) qua mạng 4G của xe là tiền của người ta và là băng thông mà app dẫn
 * đường đang cần. Tự tải nền cũng là thứ không ai đoán được đang xảy ra (và trên xe thì "đang xảy ra" có thể là
 * đang chạy 80 km/h). Một hàng nói rõ cỡ tệp, một nút, một thanh tiến trình.
 *
 * ## Tiến trình phải HIỆN, và phải hiện đúng bước nào
 * Ba bước dài khác nhau về bản chất: **tải** (phụ thuộc mạng, có phần trăm), **kiểm** (băm hàng chục MB, vài
 * giây, im), **hoàn tất** (đổi tên thư mục). Gộp cả ba vào một chữ *"Đang cài…"* thì một lần kiểm băm chậm
 * trông y hệt một lần treo — và người dùng sẽ bấm lại, tức tải lại từ đầu.
 *
 * ## ⚠ Gói ĐỌC: nút *Tải* hôm nay đi vào một địa chỉ CHƯA CÓ ASSET
 * Xem TODO(owner) ở KDoc [SherpaTtsCatalog]: 13 tệp còn chờ owner đăng lên GitHub Release. Tới lúc đó nút *Tải*
 * sẽ báo lỗi fail-safe (404 / sha không khớp) **nói rõ tệp nào**, còn đường **side-load USB** chạy ngay — đó là
 * lý do câu mô tả của hàng nói cả hai đường thay vì chỉ mời bấm.
 */
class VoiceModelSettings(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
    /** Chạy việc dài trên luồng NỀN — tách ra để đo/kiểm được, mặc định là một Thread. */
    private val background: (() -> Unit) -> Unit = { block -> Thread(block, "KachiVoiceModel").start() },
) {

    /** Gói giọng ĐỌC — một gói duy nhất hôm nay; đọc từ danh mục `:core`, không viết cứng đường dẫn nào ở đây. */
    private val ttsPack = SherpaTtsCatalog.PIPER_VI_VAIS1000

    fun build(body: LinearLayout) {
        modelRow(body)
        ttsRow(body)
        speakToggles(body)
    }

    // ── Cái TAI: mô hình nhận dạng (R9) ──────────────────────────────────────────────────────────

    private fun modelRow(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_voice_model_title)))
        val status = rows.note(modelStatusText()) as TextView
        body.addView(status)

        val action = rows.button(modelActionLabel()) {} as TextView
        action.setOnClickListener {
            if (VoiceModelStore.isReady(context)) removeModel(status, action) else installModel(status, action)
        }
        body.addView(action)
    }

    private fun installModel(status: TextView, action: TextView) {
        action.isEnabled = false
        action.text = context.getString(R.string.kachi_voice_model_working)
        background {
            VoiceModelStore.install(context) { step ->
                // `onStep` tới từ luồng nền ⇒ mọi lần chạm view phải qua `post` (view chỉ đụng được trên luồng vẽ).
                status.post { status.text = stepText(step) { modelStatusText() } }
                if (step is VoiceModelStore.Step.Done || step is VoiceModelStore.Step.Failed) {
                    action.post { action.isEnabled = true; action.text = modelActionLabel() }
                }
            }
        }
    }

    private fun removeModel(status: TextView, action: TextView) {
        action.isEnabled = false
        background {
            // Trả mô hình khỏi bộ nhớ TRƯỚC khi xoá tệp: xoá trước thì mã native vẫn giữ các tệp đã mmap và
            // lần cài sau nạp nhầm bản cũ — xem KDoc [VoiceEngine.release].
            VoiceEngine.release()
            VoiceModelStore.remove(context)
            status.post { status.text = modelStatusText() }
            action.post { action.isEnabled = true; action.text = modelActionLabel() }
        }
    }

    private fun modelActionLabel(): String = context.getString(
        if (VoiceModelStore.isReady(context)) R.string.kachi_voice_model_remove else R.string.kachi_voice_model_get,
    )

    private fun modelStatusText(): String {
        val model = VoiceModelStore.selected(context)
        return if (!VoiceModelStore.isReady(context)) {
            context.getString(R.string.kachi_voice_model_absent, mb(model.totalBytes))
        } else {
            context.getString(
                R.string.kachi_voice_model_ready,
                model.label,
                model.files.size,
                mb(VoiceModelStore.sizeOnDisk(context)),
            )
        }
    }

    // ── Cái MIỆNG: gói giọng đọc offline (T8) ────────────────────────────────────────────────────

    /**
     * Hàng *Giọng đọc offline* — **cùng khuôn** với hàng mô hình ở trên, và cố ý vậy: hai việc giống hệt nhau
     * (tải nhiều tệp có ghim, gỡ, báo tiến trình) thì không được trông khác nhau trên màn.
     */
    private fun ttsRow(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_voice_tts_title)))
        val status = rows.note(ttsStatusText()) as TextView
        body.addView(status)

        val action = rows.button(ttsActionLabel()) {} as TextView
        action.setOnClickListener {
            if (ttsReady()) removeTts(status, action) else installTts(status, action)
        }
        body.addView(action)
        // ⚠ [SOÁT Pass 4 · P1] Đường dẫn side-load phải sinh từ **dữ liệu thật**, không chép tay vào chuỗi:
        // bản đầu viết cứng một tên gói SAI (`com.byd.clusternav2` — id của app cũ; `applicationId` thật là
        // `com.byd.launcher`) và bỏ mất đoạn `<id gói>`, tức người cầm USB chép đúng theo câu hướng dẫn thì tệp
        // rơi vào một thư mục không ai đọc, rồi nút Tải báo lỗi mạng mà không ai hiểu vì sao.
        body.addView(rows.note(context.getString(
            R.string.kachi_voice_tts_sideload, BuildConfig.APPLICATION_ID, ttsPack.id,
        )))
    }

    private fun installTts(status: TextView, action: TextView) {
        action.isEnabled = false
        action.text = context.getString(R.string.kachi_voice_model_working)
        background {
            VoiceModelStore.install(context, ttsPack) { step ->
                status.post { status.text = stepText(step) { ttsStatusText() } }
                if (step is VoiceModelStore.Step.Done || step is VoiceModelStore.Step.Failed) {
                    action.post { action.isEnabled = true; action.text = ttsActionLabel() }
                }
            }
        }
    }

    /**
     * Gỡ gói đọc.
     *
     * ⚠ KHÔNG gọi [VoiceEngine.release] ở đây (hàng mô hình NGHE thì có): engine đọc offline là một đối tượng
     * khác hẳn, và chủ sở hữu duy nhất của nó là `VoiceSpeakerRouter` của phiên nói — nó tự hỏi lại đĩa ở **mỗi
     * câu** (`SherpaTtsSpeaker.available` đọc `filesPresent`, không tin một cờ nào), nên xoá tệp là lượt nói sau
     * tự lùi về máy đọc của hệ thống. Gọi nhầm `release()` của đường NGHE ở đây là gỡ cái tai khi người ta bảo
     * gỡ cái miệng.
     */
    private fun removeTts(status: TextView, action: TextView) {
        action.isEnabled = false
        background {
            VoiceModelStore.remove(context, ttsPack)
            status.post { status.text = ttsStatusText() }
            action.post { action.isEnabled = true; action.text = ttsActionLabel() }
        }
    }

    private fun ttsReady(): Boolean = VoiceModelStore.isReady(context, ttsPack)

    private fun ttsActionLabel(): String = context.getString(
        if (ttsReady()) R.string.kachi_voice_tts_remove else R.string.kachi_voice_tts_get,
    )

    private fun ttsStatusText(): String = if (!ttsReady()) {
        context.getString(R.string.kachi_voice_tts_absent, mb(ttsPack.totalBytes))
    } else {
        context.getString(
            R.string.kachi_voice_tts_ready,
            ttsPack.label,
            ttsPack.files.size,
            mb(VoiceModelStore.sizeOnDisk(context, ttsPack)),
        )
    }

    // ── Hai công tắc (R4 · T9) ───────────────────────────────────────────────────────────────────

    /**
     * *"Đọc phản hồi bằng giọng"* (mặc định BẬT) + *"Ưu tiên giọng offline"* (mặc định TẮT).
     *
     * Cả hai đi qua `deps.bridge` như mọi khoá THEO XE khác (`voiceMicPill` · `headlessAutostart`): tầng vẽ của
     * launcher **không mở cửa riêng vào nơi lưu bền**, và một ngoại lệ là chỗ ngoại lệ thứ hai bắt đầu.
     */
    private fun speakToggles(body: LinearLayout) {
        body.addView(rows.checkRow(
            on = deps.bridge.voiceSpeakReplies(),
            title = context.getString(R.string.kachi_voice_speak_title),
            sub = context.getString(R.string.kachi_voice_speak_sub),
        ) { on -> deps.bridge.setVoiceSpeakReplies(on) })
        body.addView(rows.checkRow(
            on = deps.bridge.voicePreferOffline(),
            title = context.getString(R.string.kachi_voice_offline_title),
            sub = context.getString(R.string.kachi_voice_offline_sub),
        ) { on -> deps.bridge.setVoicePreferOffline(on) })
    }

    // ── chữ ──────────────────────────────────────────────────────────────────────────────────────

    /** Chữ cho một bước cài; [done] trả câu trạng thái của **đúng hàng** đang chạy (hai hàng, một bộ chữ bước). */
    private fun stepText(step: VoiceModelStore.Step, done: () -> String): String = when (step) {
        is VoiceModelStore.Step.Downloading ->
            if (step.percent < 0) context.getString(R.string.kachi_voice_model_downloading_unknown)
            else context.getString(R.string.kachi_voice_model_downloading, step.percent)
        VoiceModelStore.Step.Verifying -> context.getString(R.string.kachi_voice_model_verifying)
        VoiceModelStore.Step.Extracting -> context.getString(R.string.kachi_voice_model_extracting)
        is VoiceModelStore.Step.Done -> done()
        is VoiceModelStore.Step.Failed -> context.getString(R.string.kachi_voice_model_failed, step.reason)
    }

    /** Byte → "32 MB". Một chỗ đổi ⇒ mọi câu chữ nói cùng một đơn vị. */
    private fun mb(bytes: Long): String = "${(bytes + HALF_MB) / MB} MB"

    private companion object {
        const val MB = 1024L * 1024L
        const val HALF_MB = MB / 2
    }
}
