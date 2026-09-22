package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R

/**
 * ═══ NHÓM CÀI ĐẶT **GIỌNG NÓI** (owner 2026-09-21) ═══════════════════════════════════════════════════════════
 *
 * Owner: *"phần voice nên tách thành 1 menu setting riêng, đang chung hệ thống hơi lộn xộn, khó tìm"*.
 *
 * Tách khỏi [SettingsSections.system] (nhóm *Hệ thống & quyền*) thành [SettingsGroup.VOICE] riêng. Ba khối, tất cả
 * là bề mặt NGƯỜI DÙNG (không phải đồ dev — đồ dev như *Gõ lệnh chữ* vẫn ở Hệ thống › Nâng cao, sau cổng test-mode):
 *  1. **Hey Kachi** — công tắc nghe câu gọi rảnh tay (mặc định TẮT; nghe nền tốn CPU/pin).
 *  2. **Nói với xe / giọng đọc** — [voice.VoiceModelSettings]: tải mô hình NGHE, gói giọng ĐỌC Piper,
 *     công tắc đọc phản hồi, hỏi-xác-nhận, nguồn micro. (Nhật ký lượt nói bên trong nó vẫn gác sau test-mode.)
 *
 * KHÔNG mang thêm khoá lưu bền mới: mọi công tắc đi qua `deps.bridge` (theo XE) như trước — chỉ đổi CHỖ ĐỨNG trong
 * cây Cài đặt, không đổi cách lưu.
 */
class SettingsVoiceSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {
    fun build(body: LinearLayout) {
        // "Hey Kachi" wake-word (W-WAKE). Mặc định TẮT. Gạt ⇒ ghi pref (theo XE) + VoiceWakeService.sync.
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_wake_section)))
        body.addView(rows.checkRow(
            on = deps.bridge.wakeEnabled(),
            title = context.getString(R.string.kachi_wake_title),
            sub = context.getString(R.string.kachi_wake_sub),
        ) { on -> deps.bridge.setWakeEnabled(on) })

        // Engine nhận "Hey Kachi" (owner 2026-09-22): ASR no-train (mặc định) vs KWS. ASR nghe "kachi" bằng chính
        // mô hình tiếng Việt nên không cần train/thu mẫu.
        body.addView(rows.checkRow(
            on = deps.bridge.wakeEngineAsr(),
            title = context.getString(R.string.kachi_wake_engine_title),
            sub = context.getString(R.string.kachi_wake_engine_sub),
        ) { on -> deps.bridge.setWakeEngineAsr(on) })

        // Dòng TRẠNG THÁI model câu gọi (owner 2026-09-21: "không có gì để biết đã tải xong chưa").
        // Tự làm mới mỗi 1.5s để thấy % tải + lúc "sẵn sàng". Dừng poll khi view rời cửa sổ.
        val st0 = deps.bridge.wakeModelStatus()
        val wakeStatus = rows.statusRow(KachiTheme.MUT2, st0.second)
        body.addView(wakeStatus.view)
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                val (state, text) = deps.bridge.wakeModelStatus()
                val color = when (state) {
                    WakeModelState.READY -> KachiTheme.GREEN
                    WakeModelState.DOWNLOADING -> KachiTheme.AMBER
                    WakeModelState.NOT_DOWNLOADED -> KachiTheme.MUT2
                }
                wakeStatus.update(color, text)
                if (wakeStatus.view.isAttachedToWindow) h.postDelayed(this, 1500L)
            }
        }
        wakeStatus.view.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) { h.post(tick) }
            override fun onViewDetachedFromWindow(v: android.view.View) { h.removeCallbacks(tick) }
        })

        // Nói với xe: mô hình NGHE + giọng ĐỌC + công tắc + hỏi-xác-nhận + nguồn micro.
        com.byd.clusternav.launcher.voice.VoiceModelSettings(context, rows, deps).build(body)

        // App NHẠC mặc định (owner 2026-09-21) — nói "phát nhạc" không nêu app + không có nhạc đang phát ⇒ dùng cái
        // này; có tên ⇒ app đó; "Tự chọn" (rỗng) ⇒ giữ hành vi cũ (app đang phát / app nhạc đầu tiên đã cài).
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_music_section)))
        body.addView(rows.chipRow(
            label = context.getString(R.string.kachi_music_default_app),
            options = deps.bridge.musicAppChoices().map { it to musicAppLabel(it) },
            current = deps.bridge.musicDefaultApp(),
        ) { key -> deps.bridge.setMusicDefaultApp(key) })
    }

    /** Nhãn app nhạc — rỗng = "Tự chọn"; còn lại là tên thương hiệu (danh từ riêng, VI=EN). */
    private fun musicAppLabel(key: String): String = when (key) {
        "" -> context.getString(R.string.kachi_music_auto)
        "ytmusic" -> "YT Music"
        "youtube" -> "YouTube"
        "spotify" -> "Spotify"
        "zing" -> "Zing MP3"
        else -> key
    }
}
