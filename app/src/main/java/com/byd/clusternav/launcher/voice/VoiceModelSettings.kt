package com.byd.clusternav.launcher.voice

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.SettingsRows

/**
 * ═══ V1 pha NGHE · HÀNG "TẢI MÔ HÌNH" TRONG CÀI ĐẶT ══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R9**. Nằm ở *Cài đặt › Hệ thống & quyền › Nâng cao*, ngay trên ô
 * *"Gõ lệnh chữ"* — cùng chỗ, vì đó là hai nửa của một việc: tải cái tai, rồi thử cái đầu.
 *
 * ## Vì sao là một cú bấm của NGƯỜI DÙNG, không tự tải lúc mở app
 * 32 MB qua mạng 4G của xe là tiền của người ta và là băng thông mà app dẫn đường đang cần. Tự tải nền cũng là
 * thứ không ai đoán được đang xảy ra (và trên xe thì "đang xảy ra" có thể là đang chạy 80 km/h). Một hàng nói rõ
 * cỡ tệp, một nút, một thanh tiến trình.
 *
 * ## Tiến trình phải HIỆN, và phải hiện đúng bước nào
 * Ba bước dài khác nhau về bản chất: **tải** (phụ thuộc mạng, có phần trăm), **kiểm** (băm 32 MB, vài giây, im),
 * **giải nén** (53 MB ra đĩa). Gộp cả ba vào một chữ *"Đang cài…"* thì một lần kiểm băm chậm trông y hệt một lần
 * treo — và người dùng sẽ bấm lại, tức tải lại từ đầu.
 */
class VoiceModelSettings(
    private val context: Context,
    private val rows: SettingsRows,
    /** Chạy việc dài trên luồng NỀN — tách ra để đo/kiểm được, mặc định là một Thread. */
    private val background: (() -> Unit) -> Unit = { block -> Thread(block, "KachiVoiceModel").start() },
) {

    fun build(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_voice_model_title)))
        val status = rows.note(statusText()) as TextView
        body.addView(status)

        val action = rows.button(actionLabel()) {} as TextView
        action.setOnClickListener {
            if (VoiceModelStore.isReady(context)) remove(status, action) else install(status, action)
        }
        body.addView(action)
    }

    // ── hành động ────────────────────────────────────────────────────────────────────────────────

    private fun install(status: TextView, action: TextView) {
        action.isEnabled = false
        action.text = context.getString(R.string.kachi_voice_model_working)
        background {
            VoiceModelStore.install(context) { step ->
                // `onStep` tới từ luồng nền ⇒ mọi lần chạm view phải qua `post` (view chỉ đụng được trên luồng vẽ).
                status.post { status.text = stepText(step) }
                if (step is VoiceModelStore.Step.Done || step is VoiceModelStore.Step.Failed) {
                    action.post { action.isEnabled = true; action.text = actionLabel() }
                }
            }
        }
    }

    private fun remove(status: TextView, action: TextView) {
        action.isEnabled = false
        background {
            // Trả mô hình khỏi bộ nhớ TRƯỚC khi xoá tệp: xoá trước thì mã native vẫn giữ các tệp đã mmap và
            // lần cài sau nạp nhầm bản cũ — xem KDoc [VoiceEngine.release].
            VoiceEngine.release()
            VoiceModelStore.remove(context)
            status.post { status.text = statusText() }
            action.post { action.isEnabled = true; action.text = actionLabel() }
        }
    }

    // ── chữ ──────────────────────────────────────────────────────────────────────────────────────

    private fun actionLabel(): String = context.getString(
        if (VoiceModelStore.isReady(context)) R.string.kachi_voice_model_remove else R.string.kachi_voice_model_get,
    )

    private fun statusText(): String =
        if (!VoiceModelStore.isReady(context)) {
            context.getString(R.string.kachi_voice_model_absent, mb(VoiceModelManifest.ZIP_BYTES))
        } else {
            context.getString(
                R.string.kachi_voice_model_ready,
                VoiceModelManifest.ID,
                VoiceModelStore.words(context).size,
                mb(VoiceModelStore.sizeOnDisk(context)),
            )
        }

    private fun stepText(step: VoiceModelStore.Step): String = when (step) {
        is VoiceModelStore.Step.Downloading ->
            if (step.percent < 0) context.getString(R.string.kachi_voice_model_downloading_unknown)
            else context.getString(R.string.kachi_voice_model_downloading, step.percent)
        VoiceModelStore.Step.Verifying -> context.getString(R.string.kachi_voice_model_verifying)
        VoiceModelStore.Step.Extracting -> context.getString(R.string.kachi_voice_model_extracting)
        is VoiceModelStore.Step.Done -> statusText()
        is VoiceModelStore.Step.Failed -> context.getString(R.string.kachi_voice_model_failed, step.reason)
    }

    /** Byte → "32 MB". Một chỗ đổi ⇒ ba câu chữ nói cùng một đơn vị. */
    private fun mb(bytes: Long): String = "${(bytes + HALF_MB) / MB} MB"

    private companion object {
        const val MB = 1024L * 1024L
        const val HALF_MB = MB / 2
    }
}
