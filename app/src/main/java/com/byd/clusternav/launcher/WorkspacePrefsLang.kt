package com.byd.clusternav.launcher

import com.byd.clusternav.Lang as ClusterNavLang
import com.byd.clusternav.launcher.voice.VoiceGrammarSnapshotStore

/**
 * ═══ S4 · R3a — NGÔN NGỮ theo hồ sơ: một chỗ lưu, hai bề mặt đọc ════════════════════════════════════════════
 *
 * Tách khỏi `WorkspacePrefs.kt` ở lượt soát 1.66 (trần 500 dòng — CLAUDE.md §4.1), **theo VAI**: ba hàm dưới đây
 * là nhóm duy nhất của lớp ấy KHÔNG đọc/ghi tệp `kachi_workspace` cho giá trị cuối cùng — chúng uỷ quyền sang chỗ
 * lưu ngôn ngữ dùng chung của cả APK ([ClusterNavLang]) và giữ một bản PHÁT một chiều. Đọc riêng thì cái sai lệch
 * có chủ ý ấy (chi tiết ngay dưới) nằm gọn một chỗ, thay vì lẫn giữa 40 hàm đọc prefs thường.
 *
 * ⚠ Không đổi một dòng hành vi nào lúc tách: cùng package, cùng tên, cùng chữ ký, cùng khoá
 * ([WorkspacePrefs.K_LANG]) — là **hàm mở rộng của chính [WorkspacePrefs]**, đúng khuôn `WorkspacePrefsProfile.kt`,
 * nên bề mặt gọi (`prefs.langMode()`) không đổi một ký tự.
 */

// ── Ngôn ngữ (S4 · R3a — nay THEO HỒ SƠ; nguồn là `<hồ sơ>__lang`, xem [broadcastLang]) — U5 · T3 ──
/**
 * ⚠⚠ **KHÔNG có khoá `lang` trong tệp `kachi_workspace`** — hai hàm này **uỷ quyền** sang chỗ lưu ngôn ngữ đã
 * tồn tại của ClusterNav ([com.byd.clusternav.Lang], tệp `clusternav_lang`, khoá `lang`).
 *
 * ## Đây là SAI LỆCH CÓ CHỦ Ý so với spec §3.1, và lý do quan trọng hơn câu chữ của spec
 * Spec ghi *"`WorkspacePrefs` khoá `lang`"*. Làm đúng chữ đó thì trong **một APK** sẽ có **hai** công tắc ngôn
 * ngữ: một của launcher (`kachi_workspace/lang`) và một của màn ClusterNav (`clusternav_lang/lang`, đang có
 * selector `seg_language` của màn ClusterNav cũ, nay đã gỡ; `ClusterNavActivity` vẫn đọc). Hai công tắc cho
 * **một** câu hỏi *"người ngồi đây đọc thứ tiếng nào"* chính là **bẫy hai-bản-sao** mà dự án đã trả giá bốn lần
 * (`customLayout` · `unitPrefs` ×4 bản · `wallpaper` · và chính `themeMode` trước T1). Biểu hiện ở đây sẽ rất khó
 * chối: chọn English trong Cài đặt Kachi rồi bấm "Mở màn ClusterNav" thì màn đó **vẫn tiếng Việt**.
 *
 * Nên chọn ngược lại: **một chỗ lưu, hai bề mặt đọc.** Chỗ lưu là chỗ đã có (`Lang`) vì
 *  1. nó **đã** mang đúng ba giá trị cần thiết (`auto`/`vi`/`en`) và đã có phép đọc tương thích ngược;
 *  2. màn ClusterNav đang **niêm phong** — không sửa được một dòng, nên chỗ lưu phải là chỗ nó đã đọc;
 *  3. `Lang.setChoice` cập nhật luôn cache của nó ⇒ hai bề mặt không thể lệch, kể cả trong cùng một lượt chạy.
 *
 * Cái mất: khoá này không nằm trong tệp prefs chính của launcher. Bù lại bằng máy, không bằng lời —
 * `SettingsCoverageContractTest` đã được **nới gốc quét** để đọc `Lang.kt`, nên `lang` và `clusternav_lang` đều
 * phải khai trong [SettingsCatalog] (và khai sai thì đỏ hai chiều).
 *
 * ## Vì sao vẫn đi qua `WorkspacePrefs` chứ không cho tầng UI gọi thẳng `Lang`
 * Để launcher chỉ có **MỘT** cửa đọc/ghi cấu hình (`repository` → `WorkspacePrefs`), đúng luật *tầng UI 0 lần ghi
 * bền trực tiếp*. Cho `SettingsSections` gọi `Lang.setChoice` thì tầng UI lại ghi thẳng xuống đĩa — đúng thứ RW0
 * vừa dọn xong.
 */
fun WorkspacePrefs.langMode(): LangMode {
    // PROFILE-IMPORT-TYPES: đọc qua [stringOrNull] — giá trị sai kiểu (tệp nhập ≤ 2.84) = vắng ⇒ đi đường lùi, không ném.
    sp.stringOrNull(key(WorkspacePrefs.K_LANG))?.let { return LangMode.of(it) }
    // Lùi MỘT lần về chỗ lưu chung cũ rồi ghi sang hồ sơ: người đang dùng English không được mất lựa chọn đó chỉ
    // vì bản mới chia khoá theo hồ sơ (cùng luật [profileString], chỉ khác chỗ lưu).
    val legacy = LangMode.of(ClusterNavLang.choice(appCtx).code)
    sp.edit().putString(key(WorkspacePrefs.K_LANG), legacy.code).apply()
    return legacy
}

fun WorkspacePrefs.setLangMode(mode: LangMode) {
    sp.edit().putString(key(WorkspacePrefs.K_LANG), mode.code).apply()
    broadcastLang(mode)
    // spec `kachi-i18n-zh-th-ms.html` R6 — `:wake` đọc tiếng giao diện từ ảnh chụp ngữ pháp (nó không có
    // `Strings.current` và không được gọi `langMode()`), nên đổi tiếng phải chụp lại, SAU `apply()` (khuôn mọi đường ghi).
    VoiceGrammarSnapshotStore.write(this)
}

/**
 * Phát lựa chọn ngôn ngữ của hồ sơ đang dùng sang chỗ lưu **dùng chung cả APK** (`clusternav_lang`).
 *
 * ⚠ Hai chỗ lưu, nhưng KHÔNG phải bẫy hai-bản-sao: bản theo hồ sơ (`<hồ sơ>__lang`) là **nguồn sự thật**, bản kia
 * chỉ là **bản PHÁT** cho `attachBaseContext` của màn ClusterNav đọc (nó không biết hồ sơ là gì). Một chiều, luôn
 * ghi từ nguồn sang bản phát, không bao giờ ngược lại — xem [ProfileScope.LAUNCHER_OWNED_CLUSTERNAV_KEYS].
 *
 * Gọi từ [setLangMode] **và** từ lượt đổi hồ sơ (`PrefsWorkspaceRepository.switchProfile`): thiếu lời gọi thứ hai
 * thì đổi sang một hồ sơ dùng English mà màn ClusterNav vẫn tiếng Việt.
 */
internal fun WorkspacePrefs.broadcastLang(mode: LangMode = langMode()) =
    ClusterNavLang.setChoice(appCtx, choiceFor(mode))

/**
 * [LangMode] (`:core`) → `Lang.Choice` (màn cũ) theo MÃ ĐĨA — phép ánh xạ TỔNG: mã không có ở `Choice` ⇒ `AUTO`.
 *
 * ⚠ [ĐO] 2026-10-03 (spec `kachi-i18n-zh-th-ms.html`): bản cũ viết `entries.first { … }` — `:core` thêm ZH/TH/MS mà
 * `Choice` chưa có thì `NoSuchElementException` ở **mỗi** lần chọn tiếng VÀ mỗi lần đổi hồ sơ (cả hai đi qua
 * [broadcastLang]). Hai bộ mã giờ khớp nhau (`LauncherLocaleContractTest` canh bằng máy); lùi `AUTO` chỉ là lưới an
 * toàn để một lệch mã sau này là "màn cũ theo xe" chứ không phải launcher sập trên xe đang chạy.
 */
internal fun choiceFor(mode: LangMode): ClusterNavLang.Choice = ClusterNavLang.Choice.of(mode.code)

