package com.byd.clusternav.launcher.voice

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.SettingsDeps
import com.byd.clusternav.launcher.SettingsRows
import com.byd.clusternav.launcher.setVoiceAskAloud
import com.byd.clusternav.launcher.setVoiceConfirmIds
import com.byd.clusternav.launcher.setVoiceMicSource
import com.byd.clusternav.launcher.voiceAskAloud
import com.byd.clusternav.launcher.voiceConfirmIds
import com.byd.clusternav.launcher.voiceMicSource

/**
 * ═══ V3 · R7 — MỤC *"HỎI XÁC NHẬN TRƯỚC KHI CHẠY"* (+ nguồn micro) ══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` **R7 · R1**. Nằm ngay dưới khối *Giọng nói* của
 * [VoiceModelSettings] (cùng nhóm *Hệ thống & quyền › Nâng cao*).
 *
 * ## Vì sao mục này tồn tại — owner 2026-09-16
 * *"cái nào nguy hiểm lái xe mới hỏi, chứ mở cửa hỏi làm gì? cần document lại cái nào cần đồng ý để tôi chọn"*,
 * và bảng B trả lời B1–B12 = **chạy luôn**, B13 = *"muốn có mục trong Cài đặt để tự bật/tắt hỏi cho từng nút"*.
 * ⇒ Mặc định **không tích gì** ([VoiceRiskTable.of] với tập rỗng trả [VoiceRisk.NORMAL] cho mọi việc).
 *
 * ## Vì sao danh sách SINH ra, không chép tay
 * [VoiceRiskTable.askableIds] sinh từ chính bảng lý do + tập gói lệnh; nhãn tra từ `ControlRegistry`/
 * `ActionMacros`. Thêm một dòng vào bảng lý do là mục này tự có hàng mới, đúng chữ đang hiện trên nút
 * (CLAUDE.md §7). Chép tay 9 hàng là dựng bản sao thứ hai của một danh sách sẽ còn đổi.
 *
 * ## Vì sao *"Đọc to câu hỏi"* đứng ở đây chứ không ở khối hai công tắc đọc
 * Nó chỉ có nghĩa khi **đã tích ít nhất một việc**: không tích gì thì không có câu hỏi nào để đọc. Đặt nó cạnh
 * *"Đọc phản hồi bằng giọng"* sẽ làm hai công tắc trông như một cặp, trong khi cái này phụ thuộc mục ở trên.
 */
class VoiceConfirmSettings(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    fun build(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_voice_confirm_sec)))
        body.addView(rows.note(context.getString(R.string.kachi_voice_confirm_note)))
        // Đọc MỘT lần lúc dựng trang rồi sửa trên bản sao: mỗi ô tích ghi lại **cả tập**, nên hai ô bấm liền
        // nhau phải cùng nhìn vào một bản. Đọc lại prefs trong từng lambda là để lượt ghi sau xoá lượt trước.
        val picked = deps.bridge.voiceConfirmIds().toMutableSet()
        VoiceRiskTable.askableIds().forEach { id ->
            val (label, why) = VoiceRiskTable.askableLabel(id) ?: return@forEach
            body.addView(rows.checkRow(on = id in picked, title = label, sub = why) { on ->
                if (on) picked.add(id) else picked.remove(id)
                deps.bridge.setVoiceConfirmIds(picked.toSet())
            })
        }
        body.addView(rows.checkRow(
            on = deps.bridge.voiceAskAloud(),
            title = context.getString(R.string.kachi_voice_ask_aloud_title),
            sub = context.getString(R.string.kachi_voice_ask_aloud_sub),
        ) { on -> deps.bridge.setVoiceAskAloud(on) })
        micSource(body)
    }

    /**
     * R1 — nguồn micro. Một hàng chip bốn lựa chọn, không phải một công tắc: [ĐO xe 2026-09-16] nguồn
     * `VOICE_RECOGNITION` cho tiếng **gần câm** trên ROM này còn `MIC` thì tốt, nhưng ca *"đang lái + đang mở
     * nhạc"* thì **[CHƯA BIẾT]** — và chỉ đo được trên đường. Hàng này để đo mà không phải build lại APK.
     */
    private fun micSource(body: LinearLayout) {
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_voice_mic_source),
            VoiceMicSource.CHOICES.map { it.toString() to sourceLabel(it) },
            deps.bridge.voiceMicSource().toString(),
        ) { code -> code.toIntOrNull()?.let { deps.bridge.setVoiceMicSource(it) } })
        body.addView(rows.note(context.getString(R.string.kachi_voice_mic_source_note)))
    }

    /** Nhãn chip: *"Tự chọn"* đã dịch, ba nguồn kia giữ **tên hằng Android** (grep được giữa hai máy đo). */
    private fun sourceLabel(source: Int): String =
        if (source == VoiceMicSource.PREF_AUTO) {
            context.getString(R.string.kachi_voice_mic_source_auto)
        } else {
            VoiceMicSource.sourceName(source)
        }
}
