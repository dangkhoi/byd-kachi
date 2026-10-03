package com.byd.clusternav.launcher.voice

/**
 * ═══ FIX286 · VK4 — PREFS TƯƠI cho `:wake`, đi chung tệp ảnh chụp ngữ pháp (thuần, `:core`) ══════════════════════
 *
 * Đóng backlog `VOICE-WAKE-PREFS-STALE`. [ĐO AOSP android-10.0.0_r47 `core/java/android/app/ContextImpl.java:447-474`]
 * `getSharedPreferences(file, MODE_PRIVATE)` trả bản `SharedPreferencesImpl` đã cache theo TIẾN TRÌNH và chỉ gọi
 * `startReloadIfChangedUnexpectedly()` khi có `MODE_MULTI_PROCESS` (hoặc targetSdk < 11) ⇒ `:wake` (sống suốt chuyến)
 * đọc ra giá trị **của lúc nó mở tệp lần đầu**. Tới 2.85 điều đó làm cũ: tập hỏi-xác-nhận, app dẫn đường / nhạc mặc
 * định ([SUY nguồn 02/10] `VoiceWiring.dispatcher`). Từ 2.86 còn thêm hai thứ quyết **chế độ** của `:wake`
 * ([VoiceWakeMode]): công tắc "Hey Kachi" và bảng gán phím — cũ thì `:wake` đang HOLD không thấy người dùng vừa bật
 * "Hey Kachi" (chỉ dựng bộ nghe khi tiến trình chết đi sống lại).
 *
 * Khuôn chữa là khuôn đã chạy của ảnh chụp ngữ pháp (§8.2 A): tiến trình CHÍNH ghi tệp nguyên tử ở mọi đường ghi,
 * `:wake` đọc TỆP mỗi phiên + mỗi lần quyết chế độ. Đi CHUNG tệp `grammar-snapshot.tsv` thay vì tệp thứ hai: cùng
 * người ghi, cùng người đọc, cùng thời điểm đọc — tệp thứ hai là bộ ghi nguyên tử + cổng tiến trình chính thứ hai
 * (global §4.1 DRY). Lựa chọn ghi ở spec §9.
 *
 * Mỗi trường `null` = ảnh chụp KHÔNG mang (tệp ghi bởi ≤ 2.85, hoặc tiến trình chính chưa ghi lần nào từ khi nâng cấp)
 * ⇒ chỗ đọc lùi về đường cũ của nó — tức đúng hành vi 2.85, không tệ hơn.
 *
 * @property wakeSwitch công tắc "Hey Kachi" của owner (`voice_wake_enabled`) — CHƯA gồm cầu chì false-accept: cầu chì
 *   là tệp marker `kachi_wake_disabled`, `:wake` tự đọc tươi (`File.exists`, không cache).
 * @property keyHold [VoiceWakeMode.keyHold] tính ở tiến trình chính.
 * @property confirmIds tập hỏi-xác-nhận HIỆU LỰC (đã gồm mặc định 2.86 — mở cửa sổ trời, FIX286 · SR5).
 * @property navDefault app dẫn đường mặc định (`Prefs.voiceNavDefaultApp`).
 * @property musicDefault app nhạc mặc định; `""` = tự chọn (`Prefs.voiceMusicDefaultApp`).
 */
data class VoiceWakePrefs(
    val wakeSwitch: Boolean? = null,
    val keyHold: Boolean? = null,
    val confirmIds: Set<String>? = null,
    val navDefault: String? = null,
    val musicDefault: String? = null,
) {

    /**
     * Chế độ của `:wake`. Ảnh chụp có công tắc ⇒ công tắc ∧ ¬cầu chì; không có ⇒ [ownWakeEffective] (đọc prefs cũ
     * của chính `:wake` — hành vi 2.85). Thiếu [keyHold] ⇒ `false` (2.85 không có HOLD) — không bao giờ tự bật thêm.
     */
    fun mode(ownWakeEffective: Boolean, fuseTripped: Boolean): VoiceWakeMode = VoiceWakeMode.of(
        wakeEnabled = wakeSwitch?.let { it && !fuseTripped } ?: ownWakeEffective,
        keyHold = keyHold ?: false,
    )

    companion object {
        val EMPTY = VoiceWakePrefs()
    }
}
