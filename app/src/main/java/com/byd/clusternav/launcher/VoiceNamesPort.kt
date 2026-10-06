package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.TaughtName

/**
 * 2.91 VOICE-APP-NAMES · A1 — cổng của trang *Dạy tên app*: đọc tên đã dạy của hồ sơ đang dùng + ghi CẢ danh sách qua
 * ViewModel (tầng UI 0 lần ghi bền — cùng khuôn [ShortcutSettingsPort.save]). Một giao diện thay cho ba lambda để đường
 * nối `homePanels` → [HomePanels] → [SettingsDeps] chỉ thêm MỘT tham số (`KachiHomeWiring.kt` sát trần 500).
 */
interface VoiceNamesPort {
    fun names(): List<TaughtName>

    /** Dữ liệu đang có là của một phiên bản lạ (bản Kachi mới hơn) ⇒ trang chỉ đọc, không ghi đè. */
    fun readOnly(): Boolean

    /** Ghi CẢ danh sách; `false` = không ghi (chỉ đọc). */
    fun save(names: List<TaughtName>): Boolean
}

/** Cổng thật — ba lời gọi ViewModel, không logic. */
internal fun HomeViewModel.voiceNamesPort(): VoiceNamesPort = object : VoiceNamesPort {
    override fun names(): List<TaughtName> = voiceAppNames()
    override fun readOnly(): Boolean = voiceAppNamesReadOnly()
    override fun save(names: List<TaughtName>): Boolean = setVoiceAppNames(names)
}
