package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.SavedPlace
import com.byd.clusternav.launcher.SavedPlaces
import com.byd.clusternav.launcher.voiceLangOf

/**
 * ═══ ẢNH CHỤP NGỮ PHÁP — phần ĐỘNG của ngữ pháp voice, ở dạng một TỆP mà tiến trình khác đọc được ═══════════════
 *
 * Spec `docs/specs/kachi-voice-rearchitecture-and-remaining.html` §8.2 (A). Vá **[P1] Pass 1 (2026-09-26)**: từ
 * CLOSE-3, khi "Hey Kachi" BẬT thì MỌI lối vào (nút mic · phím · `EXTRA_START_VOICE`) mở phiên ở tiến trình `:wake`,
 * mà phiên ấy dựng ngữ pháp với `profiles = emptyList()` / `places = emptyList()` ⇒ người dùng mất *"đổi sang hồ sơ
 * X"* và *"về nhà"*. Ba chỗ cần đúng dữ liệu này ở `:app`: `VoiceRecognizer.open` (hotword biasing),
 * `VoiceIntentParser.savedPlace` (phân tích), `VoiceTargetDispatch.runNavSaved` (giải địa chỉ từ `state().savedPlaces`).
 *
 * ## Vì sao là TỆP, không phải `SharedPreferences`
 * `WorkspacePrefs` = `SharedPreferences MODE_PRIVATE`: mỗi tiến trình giữ **một bản cache** nạp lúc mở tệp, và
 * không thấy tiến trình khác ghi. `:wake` sống suốt chuyến ⇒ nó đọc ra hồ sơ/sổ địa chỉ **của lúc nó khởi động**:
 * một sổ địa chỉ CŨ là *dẫn sai đường*, tệ hơn không hiểu. Đọc một tệp thì đi thẳng hệ tệp, không cache (cùng lẽ
 * marker `kachi_wake_disabled` của `Prefs`). Tiến trình chính ghi tệp này ở **mọi** đường ghi hồ sơ/sổ địa chỉ
 * (`VoiceGrammarSnapshotStore`, `:app`); `:wake` đọc lại **mỗi phiên**.
 *
 * ## Dạng lưu — TSV tự đọc được, một bản ghi một dòng
 * ```
 * kachi-grammar	v1	1758900000000
 * active	Vợ
 * profile	Mặc định
 * profile	Vợ
 * place	Nhà|123 Nguyễn Trãi, Hà Nội|21.0045|105.8412
 * ```
 * Dòng `place` mang **đúng** chuỗi [SavedPlaces.encode] đang dùng cho prefs (một mục = một dòng) — không nghĩ ra
 * bộ mã hoá thứ hai cho cùng một thứ (CLAUDE.md §4.1 DRY). Tên hồ sơ tách bằng `\t` với `limit = 2` nên một dấu tab
 * trong tên không làm hỏng dòng; `\r`/`\n` đã bị `WorkspacePrefs.addProfile` khử ở cửa vào, ở đây khử lần nữa cho
 * chắc ([cleanName]). [decode] **không ném**: thiếu/hỏng header ⇒ [EMPTY] + lý do; dòng hỏng ⇒ bỏ dòng đó + lý do.
 *
 * Thuần Kotlin (`:core`) ⇒ round-trip / tệp hỏng / Unicode kiểm off-car ([VoiceGrammarSnapshotTest]).
 *
 * @property profiles tên hồ sơ, **đúng thứ tự** `WorkspacePrefs.profiles()`.
 * @property activeProfile hồ sơ đang dùng (`""` khi chưa có ảnh chụp).
 * @property places sổ địa chỉ của hồ sơ đang dùng — cấu trúc y như `HomeUiState.savedPlaces`.
 * @property writtenAtMs mốc ghi (`System.currentTimeMillis()` của tiến trình chính), `0` = không rõ.
 * @property wake FIX286 · VK4 — prefs tươi cho `:wake` ([VoiceWakePrefs]): năm bản ghi `wake` · `keyhold` · `confirm`
 *   · `nav` · `music`, chỉ ghi khi có giá trị ⇒ tệp ≤ 2.85 (không có chúng) đọc ra [VoiceWakePrefs.EMPTY], và bản ghi
 *   này không đổi một byte của ba loại bản ghi cũ.
 * @property uiLang spec `kachi-i18n-zh-th-ms.html` R6 · §4.4 — ngôn ngữ GIAO DIỆN **đã giải nghĩa** (`LangHost` của
 *   tiến trình chính: AUTO đã thành VI/EN) của hồ sơ đang dùng; bản ghi `lang`. `:wake` không có `Strings.current`
 *   (chỉ tiến trình chính ghi biến đó) và không được gọi `WorkspacePrefs.langMode()` (nó tự ghi khi migrate) ⇒ đây là
 *   đường DUY NHẤT để phiên phím vô-lăng biết người dùng đọc tiếng gì. `null` = tệp cũ / chưa ghi ⇒ [voiceLang] = VI
 *   (đúng hành vi `:wake` trước bản này) và tài nguyên của `:wake` giữ locale máy.
 */
data class VoiceGrammarSnapshot(
    val profiles: List<String> = emptyList(),
    val activeProfile: String = "",
    val places: List<SavedPlace> = emptyList(),
    val writtenAtMs: Long = 0L,
    val wake: VoiceWakePrefs = VoiceWakePrefs.EMPTY,
    val uiLang: Lang? = null,
) {

    /** Nhãn sổ địa chỉ cho hotword + parser — cùng hàm mà đường in-process dùng ([VoicePlaces.labelsOf]). */
    fun placeLabels(): List<String> = VoicePlaces.labelsOf(places)

    /** Ngôn ngữ GIỌNG NÓI của phiên `:wake` = `voiceLangOf(uiLang)`; ảnh chụp thiếu/cũ ⇒ VI (xem [uiLang]). */
    val voiceLang: Lang get() = voiceLangOf(uiLang ?: Lang.VI)

    /**
     * `HomeUiState` cho `VoiceDispatcher` của phiên `:wake` — **CHỈ ba trường là thật**: `profiles` (parse),
     * `savedPlaces` (parse + giải địa chỉ), `activeProfile`. Chưa có ảnh chụp ⇒ mặc định của `HomeUiState` (hồ sơ
     * "Mặc định", sổ rỗng) — đúng hành vi trước bản vá, không tệ hơn.
     *
     * ⚠ VOICE-WAKE-SLOTCOUNT (2026-10-02) — MỌI trường khác là **mặc định, không phải màn thật**: `workspace` (bố cục
     * `THREE` = 3 ô) · `customLayout` (`null`) · `carStatus` (`CarStatus()` rỗng) · … [ĐO ảnh owner 02/10] bố cục tự vẽ
     * 6 khung mà `:wake` trả *"bố cục hiện chỉ có 3 ô"*. Quyết định nào cần chúng thì `:wake` KHÔNG được quyết bằng
     * state này: gắn ô ⇒ giao Activity (`VoiceDispatcher.placeInSlot` → `VoiceWakeHomeRelay.performSlot`); số liệu xe
     * ⇒ đọc tươi (`freshCar`, nhu cầu màn của `:wake` = rỗng) — TRỪ `carStatus.controls` (gió đang AUTO?): ở `:wake` nó
     * luôn rỗng ⇒ `null` = "chưa biết", hàng thiết kế của `ClimateAuto.stepIntent`, chưa đọc tươi (backlog
     * `VOICE-WAKE-AUTOON`). Bài canh `VoiceWakeFakeStateContractTest` liệt kê đúng những chỗ dispatcher đọc `state()` —
     * thêm một chỗ đọc trường khác (hoặc chuyển nguyên lambda sang lớp mới) là ĐỎ.
     */
    fun homeState(): HomeUiState = HomeUiState(
        activeProfile = activeProfile.ifBlank { HomeUiState.DEFAULT_PROFILE },
        profiles = profiles.ifEmpty { listOf(HomeUiState.DEFAULT_PROFILE) },
        savedPlaces = places,
    )

    fun encode(): String = buildString {
        append(HEADER).append(SEP).append(VERSION).append(SEP).append(writtenAtMs).append('\n')
        append(REC_ACTIVE).append(SEP).append(cleanName(activeProfile)).append('\n')
        profiles.forEach { append(REC_PROFILE).append(SEP).append(cleanName(it)).append('\n') }
        places.forEach { append(REC_PLACE).append(SEP).append(SavedPlaces.encode(listOf(it))).append('\n') }
        wake.wakeSwitch?.let { append(REC_WAKE).append(SEP).append(bit(it)).append('\n') }
        wake.keyHold?.let { append(REC_KEYHOLD).append(SEP).append(bit(it)).append('\n') }
        wake.confirmIds?.let { ids -> append(REC_CONFIRM).append(SEP).append(ids.sorted().joinToString(ID_SEP)).append('\n') }
        wake.navDefault?.let { append(REC_NAV).append(SEP).append(cleanName(it)).append('\n') }
        wake.musicDefault?.let { append(REC_MUSIC).append(SEP).append(cleanName(it)).append('\n') }
        uiLang?.let { append(REC_LANG).append(SEP).append(it.code).append('\n') }
    }

    /** Kết quả [decode]: [snapshot] luôn dùng được; [problem] ≠ `null` khi có gì đó bị bỏ (để chỗ gọi ghi log). */
    data class Decoded(val snapshot: VoiceGrammarSnapshot, val problem: String?)

    companion object {
        const val HEADER = "kachi-grammar"
        const val VERSION = "v1"
        private const val SEP = '\t'
        private const val REC_ACTIVE = "active"
        private const val REC_PROFILE = "profile"
        private const val REC_PLACE = "place"

        // FIX286 · VK4 — prefs tươi của `:wake` ([VoiceWakePrefs]). Mã ASCII thường (cùng luật [safe]).
        private const val REC_WAKE = "wake"
        private const val REC_KEYHOLD = "keyhold"
        private const val REC_CONFIRM = "confirm"
        private const val REC_NAV = "nav"
        private const val REC_MUSIC = "music"

        /** spec `kachi-i18n-zh-th-ms.html` R6 — [uiLang] bằng [Lang.code] (`vi`/`en`/`zh`/`th`/`ms`). */
        private const val REC_LANG = "lang"
        private const val ID_SEP = ","

        private fun bit(b: Boolean): String = if (b) "1" else "0"

        val EMPTY = VoiceGrammarSnapshot()

        /** Tên hồ sơ không được chứa ký tự xuống dòng (phá bản ghi một-dòng). Cùng phép khử với `WorkspacePrefs.addProfile`. */
        private fun cleanName(raw: String): String = raw.replace(Regex("[\\r\\n]+"), " ").trim()

        /**
         * [SOÁT 2.68 · Pass 2 · P2 — PII] Nội dung tệp này mang **địa chỉ nhà người dùng**, và lý do bỏ dòng
         * ([Decoded.problem]) được `VoiceGrammarSnapshotStore.read` ghi ra **logcat**. Nên chỉ nhắc lại nguyên văn
         * phần chắc chắn KHÔNG phải dữ liệu người dùng: một mã ASCII ngắn, không dấu, không khoảng trắng, không `|`
         * (mọi loại bản ghi hợp lệ đều đúng dạng đó). Bất cứ gì khác ⇒ chỉ nói **độ dài**. Một tệp bị lệch dòng
         * (mất tiền tố `place\t`) mà cứ `take(40)` là đẩy nguyên số nhà + tên đường vào log của cả máy.
         *
         * [Pass 3 · P3] Chỉ **chữ thường**: mọi mã hợp lệ của định dạng này đều viết thường ([HEADER] · [VERSION] ·
         * `active`/`profile`/`place`), nên bỏ chữ HOA không mất một mẩu chẩn đoán nào — mà lại đóng khe còn lại: tệp
         * lệch dòng làm một **tên hồ sơ** thành `kind`, và tên hồ sơ ASCII một từ (*"Alice"*, *"Mom"*) là tên người thật.
         */
        private fun safe(raw: String): String =
            if (raw.length <= 24 && raw.matches(Regex("[a-z0-9_.:\\-]*"))) "'$raw'" else "${raw.length} ký tự"

        /**
         * Đọc ảnh chụp từ chuỗi. **Không ném.**
         *  • `null`/rỗng ⇒ [EMPTY] + lý do (tiến trình chính chưa ghi lần nào).
         *  • header sai/khác version ⇒ [EMPTY] + lý do (không đoán một định dạng chưa biết).
         *  • dòng hỏng (loại bản ghi lạ · `place` không giải mã được) ⇒ **bỏ dòng đó**, giữ phần còn lại, ghi lý do đầu.
         */
        fun decode(raw: String?): Decoded {
            if (raw.isNullOrBlank()) return Decoded(EMPTY, "chưa có ảnh chụp")
            val lines = raw.split('\n')
            val head = lines[0].split(SEP)
            if (head.getOrNull(0) != HEADER) return Decoded(EMPTY, "header lạ: ${safe(lines[0])}")
            if (head.getOrNull(1) != VERSION) return Decoded(EMPTY, "version không hỗ trợ: ${safe(head.getOrNull(1) ?: "")}")
            val at = head.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L
            var active = ""
            val profiles = ArrayList<String>()
            val places = ArrayList<SavedPlace>()
            var wake = VoiceWakePrefs.EMPTY
            var uiLang: Lang? = null
            var problem: String? = null
            fun note(msg: String) { if (problem == null) problem = msg }
            // Bit hỏng ⇒ bỏ bản ghi (trường giữ `null` = chỗ đọc lùi về đường cũ), không đoán 0/1.
            fun bitOf(v: String, row: Int): Boolean? = when (v.trim()) {
                "1" -> true
                "0" -> false
                else -> null.also { note("dòng $row: cờ hỏng ${safe(v)}") }
            }
            lines.drop(1).forEachIndexed { i, line ->
                if (line.isBlank()) return@forEachIndexed
                val kind = line.substringBefore(SEP)
                val value = if (line.contains(SEP)) line.substringAfter(SEP) else ""
                val row = i + 2
                when (kind) {
                    REC_ACTIVE -> active = cleanName(value)
                    REC_PROFILE -> cleanName(value).takeIf { it.isNotEmpty() }?.let { profiles += it } ?: note("dòng $row: hồ sơ rỗng")
                    REC_PLACE -> SavedPlaces.decode(value).firstOrNull()?.let { places += it } ?: note("dòng $row: địa chỉ hỏng")
                    REC_WAKE -> bitOf(value, row)?.let { wake = wake.copy(wakeSwitch = it) }
                    REC_KEYHOLD -> bitOf(value, row)?.let { wake = wake.copy(keyHold = it) }
                    REC_CONFIRM -> wake = wake.copy(confirmIds = value.split(ID_SEP).map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                    REC_NAV -> wake = wake.copy(navDefault = cleanName(value))
                    REC_MUSIC -> wake = wake.copy(musicDefault = cleanName(value))
                    // Mã lạ ⇒ bỏ bản ghi (`null` = VI cho giọng, locale máy cho tài nguyên), không đoán một tiếng.
                    REC_LANG -> Lang.entries.firstOrNull { it.code == value.trim() }?.let { uiLang = it }
                        ?: note("dòng $row: ngôn ngữ lạ ${safe(value)}")
                    else -> note("dòng $row: bản ghi lạ ${safe(kind)}")
                }
            }
            return Decoded(VoiceGrammarSnapshot(profiles, active, places.take(SavedPlaces.MAX), at, wake, uiLang), problem)
        }
    }
}
