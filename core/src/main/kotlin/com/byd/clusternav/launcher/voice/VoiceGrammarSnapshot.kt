package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.SavedPlace
import com.byd.clusternav.launcher.SavedPlaces

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
 */
data class VoiceGrammarSnapshot(
    val profiles: List<String> = emptyList(),
    val activeProfile: String = "",
    val places: List<SavedPlace> = emptyList(),
    val writtenAtMs: Long = 0L,
) {

    /** Nhãn sổ địa chỉ cho hotword + parser — cùng hàm mà đường in-process dùng ([VoicePlaces.labelsOf]). */
    fun placeLabels(): List<String> = VoicePlaces.labelsOf(places)

    /**
     * `HomeUiState` **đủ cho `VoiceDispatcher`** của phiên `:wake`: nó đọc `state().profiles` (parse), `state().savedPlaces`
     * (parse + giải địa chỉ) và `state().activeProfile`. Chưa có ảnh chụp ⇒ mặc định của `HomeUiState` (hồ sơ
     * "Mặc định", sổ rỗng) — đúng hành vi trước bản vá, không tệ hơn.
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
            var problem: String? = null
            fun note(msg: String) { if (problem == null) problem = msg }
            lines.drop(1).forEachIndexed { i, line ->
                if (line.isBlank()) return@forEachIndexed
                val kind = line.substringBefore(SEP)
                val value = if (line.contains(SEP)) line.substringAfter(SEP) else ""
                when (kind) {
                    REC_ACTIVE -> active = cleanName(value)
                    REC_PROFILE -> cleanName(value).takeIf { it.isNotEmpty() }?.let { profiles += it } ?: note("dòng ${i + 2}: hồ sơ rỗng")
                    REC_PLACE -> SavedPlaces.decode(value).firstOrNull()?.let { places += it } ?: note("dòng ${i + 2}: địa chỉ hỏng")
                    else -> note("dòng ${i + 2}: bản ghi lạ ${safe(kind)}")
                }
            }
            return Decoded(VoiceGrammarSnapshot(profiles, active, places.take(SavedPlaces.MAX), at), problem)
        }
    }
}
