package com.byd.clusternav.launcher.voice

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.SettingsRows

/**
 * WP9 / T9 — hàng *Giọng Kachi bé (clip OTA)* trong nhóm Giọng nói, tách khỏi [VoiceModelSettings] khi tệp đó
 * chạm trần 500 dòng (CLAUDE.md §4.1). Cùng khuôn lớp-hàng với [VoiceConfirmSettings]: dựng từ chính `build()`
 * của [VoiceModelSettings] nên trang Cài đặt vẫn có một khối "Giọng nói" liền mạch.
 *
 * ## Sẵn sàng ĐO bằng tệp đã BUNG — và phép đó thuộc [VoiceModelStore], không phải hàng này
 * Gói clip tải về là MỘT `.zip` ([KachiClipVoiceCatalog.OTA_PACK]); sau khi [VoiceModelStore] bung nó, tệp `.zip`
 * (thành viên duy nhất của pack) bị xoá ⇒ phép mặc định *"mọi tệp trong `files` còn trên đĩa"* sẽ báo *"chưa cài"*
 * cho một gói đã lắp đủ. Chỗ chữa nằm ở [VoiceModelStore.isReady] (nó hỏi
 * [KachiClipVoiceCatalog.EXTRACTED_FILES] cho gói archive), **không** ở đây: chép phép kiểm vào tầng vẽ là bản sao
 * thứ hai của một luật lưu bền, và bản sao ấy sẽ lệch ở đúng lần ai đó sửa một bên — đúng bẫy `unitPrefs` (4 bản)
 * mà dự án đã trả giá. Nhờ delegate, `install()` cũng giữ được đường thoát sớm của nó (xem KDoc `isReady`).
 */
internal class ClipVoiceRow(
    private val context: Context,
    private val rows: SettingsRows,
    private val background: (() -> Unit) -> Unit = { block -> Thread(block, "KachiClipVoice").start() },
) {

    private val pack = KachiClipVoiceCatalog.OTA_PACK

    fun build(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_clip_voice_title)))
        val status = rows.note(statusText()) as TextView
        body.addView(status)

        val action = rows.button(actionLabel()) {} as TextView
        action.setOnClickListener {
            if (ready()) remove(status, action) else install(status, action)
        }
        body.addView(action)
    }

    private fun install(status: TextView, action: TextView) {
        action.isEnabled = false
        action.text = context.getString(R.string.kachi_voice_model_working)
        background {
            VoiceModelStore.install(context, pack) { step ->
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
            VoiceModelStore.remove(context, pack)
            status.post { status.text = statusText() }
            action.post { action.isEnabled = true; action.text = actionLabel() }
        }
    }

    /** Sẵn sàng = hai bảng tra đã bung có mặt — hỏi [VoiceModelStore] (một nguồn sự thật, xem KDoc lớp). */
    private fun ready(): Boolean = VoiceModelStore.isReady(context, pack)

    private fun actionLabel(): String = context.getString(
        if (ready()) R.string.kachi_voice_tts_remove else R.string.kachi_voice_tts_get,
    )

    private fun statusText(): String = if (!ready()) {
        context.getString(R.string.kachi_clip_voice_absent, mb(pack.totalBytes))
    } else {
        context.getString(R.string.kachi_clip_voice_ready, mb(VoiceModelStore.sizeOnDisk(context, pack)))
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

    private fun mb(bytes: Long): String = "${(bytes + HALF_MB) / MB} MB"

    private companion object {
        const val MB = 1024L * 1024L
        const val HALF_MB = MB / 2
    }
}
