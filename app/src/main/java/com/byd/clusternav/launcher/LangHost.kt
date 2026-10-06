package com.byd.clusternav.launcher

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * ═══ U5 · T3 — CHỦ SỞ HỮU DUY NHẤT của việc ÁP ngôn ngữ ═══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-and-light-theme.html` §3.1. Song sinh với [ThemeHost]: cùng vai *"mắt xích cuối của
 * đường một chiều"*, cùng luật một-nơi-ghi-duy-nhất.
 *
 * `WorkspacePrefs.langMode` (lưu bền, dùng chung với ClusterNav) → `HomeUiState.langMode` (nguồn sự thật) →
 * `HomeViewModel.setLangMode` (intent) → **[wrap]** (đọc để vẽ) → `Strings.current` + locale của `Context`.
 *
 * ## Vì sao PHẢI áp ở HAI chỗ, không phải một
 * Launcher có **hai** họ chuỗi và chúng đi hai đường khác nhau:
 *  1. **Nhãn dữ liệu ở `:core`** (123 datum · 64 nút · 12 nhóm…) đọc `Strings.current` — thuần Kotlin, không biết
 *     `Context`.
 *  2. **Chữ trên màn ở `:app`** đọc `values/strings_kachi.xml` / `values-en/strings_kachi.xml` — Android chọn tệp theo
 *     **locale của `Context`**, không theo `Strings.current`.
 *
 * Áp một chỗ thôi thì ra đúng cái nửa-vời tệ nhất: chọn English mà tiêu đề vẫn tiếng Việt (thiếu locale), hoặc tiêu đề
 * tiếng Anh mà mọi ô dữ liệu xe vẫn tiếng Việt (thiếu `Strings.current`). Nên [wrap] làm **cả hai trong một hàm** —
 * hai lời gọi ở hai chỗ khác nhau là cách chắc chắn để một chỗ được sửa và chỗ kia không.
 *
 * ## ⚠ Vì sao ca "Theo xe" cũng PHẢI đặt locale (khác hẳn [com.byd.clusternav.ThemeMode.wrap])
 * Bản chủ đề của ClusterNav trả `base` nguyên vẹn cho ca `SYSTEM` — đúng, vì `uiMode` của máy vốn đã là thứ ta muốn.
 * Với ngôn ngữ thì **không** đúng: `values-en/` chỉ khớp locale `en*`, còn mọi locale khác (`ja`, `th`, `zh`…) rơi về
 * `values/` = **tiếng Việt**. Trong khi [LangMode.resolve] cho "mọi thứ khác → EN". Nghĩa là trên một xe locale Nhật,
 * bỏ qua bước đặt locale sẽ cho: nhãn `:core` tiếng Anh + chữ trên màn tiếng Việt. Nên ở đây luôn đặt locale **đã giải
 * nghĩa**, không bao giờ trả `base` trần — đó chính là điều làm hai kênh không thể lệch nhau.
 */
internal object LangHost {

    /**
     * Gọi từ `attachBaseContext(base)` dạng `super.attachBaseContext(LangHost.wrap(base))`.
     *
     * Chạy **trước** `onCreate` và **mỗi lần** Activity được dựng (kể cả lượt `recreate()` sau khi đổi ngôn ngữ), nên
     * không cần một đường "áp lại" thứ hai lúc chạy — thứ mà nếu có sẽ là nơi ghi `Strings.current` thứ hai.
     *
     * @return `Context` đã ép locale; mọi `getString` sau đó (kể cả trong view dựng bằng mã) sẽ tra đúng tệp tài nguyên.
     */
    fun wrap(base: Context): Context {
        val lang = chosen(base)
        Strings.current = lang   // ⇐ CHỖ GHI DUY NHẤT của `Strings.current` trong toàn dự án
        return localized(base, lang)
    }

    /**
     * Áp ngôn ngữ NGƯỜI DÙNG đã chọn lên tài nguyên của [base] — **không** ghi `Strings.current`.
     *
     * Cho các màn phụ của tiến trình chính (`DiagActivity` · `VietMapWidgetDiagActivity` · `ClusterBlackActivity` ·
     * `ClusterNavActivity`) — spec `kachi-i18n-zh-th-ms.html` R9. Trước đây chúng chỉ áp `ThemeMode.wrap` ⇒ tài nguyên
     * theo locale MÁY; khi đã có `values-zh-rCN/-th/-ms` thì một xe đặt tiếng Trung mà người dùng chọn English sẽ ra
     * màn phụ tiếng Trung, cạnh chữ `Lang.t` tiếng Anh trên CÙNG màn đó.
     *
     * Không ghi `Strings.current`: đúng một nơi ghi là [wrap] (`LauncherLocaleContractTest`). Đọc `WorkspacePrefs` ⇒
     * CHỈ dùng ở tiến trình chính; `:wake` dùng bản [localized] nhận `Lang` tường minh (không được gọi
     * `WorkspacePrefs.langMode()` — nó tự ghi khi migrate, xem `VoiceWakeIsolationContractTest`).
     */
    fun localized(base: Context): Context = localized(base, chosen(base))

    /** Như trên với ngôn ngữ ĐÃ GIẢI NGHĨA [lang] (vd `:wake` đọc từ ảnh chụp ngữ pháp). Một chỗ dựng cấu hình locale. */
    fun localized(base: Context, lang: Lang): Context {
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(localeOf(lang))
        return base.createConfigurationContext(cfg)
    }

    /**
     * `Locale` ứng với ngôn ngữ ĐANG dùng — cho những chỗ định dạng **không** đi qua tài nguyên.
     *
     * ⚠ [ĐO] máy ảo 2026-09-12: hai chỗ định dạng NGÀY viết cứng `Locale.forLanguageTag("vi")` (đồng hồ thanh trên +
     * widget đồng hồ), nên ở bản English thứ trong tuần vẫn hiện *"Thứ Bảy"*. Chuỗi tiếng Việt đó **không nằm trong
     * mã** — nó do `SimpleDateFormat` sinh ra — nên cả bài canh "0 chuỗi viết cứng" lẫn phép so hai tệp tài nguyên
     * đều không thể thấy. Đưa về một chỗ để lần sau chỉ có một thứ phải sửa.
     */
    fun locale(): Locale = localeOf(Strings.current)

    /**
     * `Locale` của GIỌNG NÓI (giọng đọc Android · geocoder) = locale của `voiceLangOf(lang)`: EN → English, mọi tiếng
     * khác → tiếng Việt (spec R6). Giao diện ZH/TH/MS mà giọng đọc theo [locale] thì máy đọc đi tìm giọng zh/th/ms cho
     * một câu trả lời tiếng Việt. Gọi ở `AndroidTtsSpeaker.configure` + `VoiceGeocoder.onDevice`.
     *
     * [lang] = ngôn ngữ giao diện HOẶC ngôn ngữ giọng nói đã suy (phép `voice` là luỹ đẳng: VI/EN giữ nguyên). Mặc
     * định [Strings.current] chỉ đúng ở tiến trình chính; `:wake` truyền tiếng từ ảnh chụp ngữ pháp (ở đó
     * `Strings.current` không bao giờ được ghi — luôn VI).
     */
    fun voiceLocale(lang: Lang = Strings.current): Locale = localeOf(lang.voice)

    /**
     * Mẫu ngày "thứ + ngày/tháng" cho đồng hồ thanh trên + widget đồng hồ — MỘT chỗ khai (spec §4.5).
     *
     * VI/EN/TH/MS giữ `"EEEE, dd/MM"` (VI/EN y byte như trước). ZH dùng `"M月d日 EEEE"` (`10月3日 星期六`): thứ tự "thứ,
     * ngày/tháng" đọc ngược với người Trung Quốc. `月`/`日` không phải chữ cái A–Z/a–z nên `SimpleDateFormat` coi là
     * chữ thường, không cần nháy. Không mẫu nào có năm (lịch Phật giáo không thể lộ — xem [THAI]).
     */
    fun datePattern(): String = when (Strings.current) {
        Lang.ZH -> "M月d日 EEEE"
        Lang.VI, Lang.EN, Lang.TH, Lang.MS -> "EEEE, dd/MM"
    }

    /** 2.91 VOICE-APP-NAMES — locale CỦA một tiếng (nhãn app locale thứ hai, `AppAltLabels`): cùng bảng [localeOf]. */
    fun localeFor(lang: Lang): Locale = localeOf(lang)

    /** `when` VÉT CẠN — thêm một [Lang] mà quên ở đây là lỗi biên dịch, không phải màn rơi về tiếng Việt im lặng. */
    private fun localeOf(lang: Lang): Locale = when (lang) {
        Lang.VI -> VIETNAMESE
        Lang.EN -> Locale.ENGLISH
        Lang.ZH -> Locale.SIMPLIFIED_CHINESE
        Lang.TH -> THAI
        Lang.MS -> MALAY
    }

    /** Lựa chọn của hồ sơ đang dùng, đã giải nghĩa theo locale máy (AUTO: `vi` → VI, còn lại → EN). */
    private fun chosen(base: Context): Lang = resolved(WorkspacePrefs(base), base)

    /**
     * Tiếng giao diện ĐÃ GIẢI NGHĨA của hồ sơ đang dùng trong [prefs] — CÙNG phép với [wrap] (một chỗ, không bản sao).
     * Cho `VoiceGrammarSnapshotStore.write` ghi vào ảnh chụp ngữ pháp để `:wake` biết người dùng đọc tiếng gì (spec
     * `kachi-i18n-zh-th-ms.html` R6). ⚠ CHỈ tiến trình chính: `langMode()` tự ghi khi migrate.
     */
    fun resolved(prefs: WorkspacePrefs, ctx: Context = prefs.appCtx): Lang = prefs.langMode().resolve(systemLanguage(ctx))

    /**
     * Lựa chọn ngôn ngữ có ĐỔI giữa hai lượt render không (⇒ chỗ gọi dựng lại màn).
     *
     * `prev == null` (lượt render ĐẦU) trả `false`: [wrap] vừa áp đúng ngôn ngữ vài mili-giây trước, dựng lại lúc này
     * là một lượt `recreate()` vô ích ngay khi mở launcher — đúng cái bẫy mà [ThemeHost] cũng phải chừa
     * (`&& prev != null`).
     */
    fun changed(prev: HomeUiState?, next: HomeUiState): Boolean = prev != null && prev.langMode != next.langMode

    /**
     * Mã ISO-639 của locale máy/xe, `null` nếu không đọc được.
     *
     * ⚠ Đọc `configuration.locales[0]` (API 24+; minSdk của dự án là 29) chứ không `locale` đã bỏ dùng. Bọc
     * `runCatching` theo đúng lối `com.byd.clusternav.Lang.defaultFor`: đọc cấu hình lúc `attachBaseContext` là thời
     * điểm sớm nhất của Activity, và một ngoại lệ ở đây sẽ làm launcher **không mở được** — trong khi hậu quả đúng của
     * việc không đọc được locale chỉ là "hiện tiếng Anh".
     */
    private fun systemLanguage(ctx: Context): String? =
        runCatching { ctx.resources.configuration.locales[0].language }.getOrNull()

    /** `Locale.ENGLISH` có sẵn, tiếng Việt thì không — dựng một lần thay vì mỗi lần mở màn. */
    private val VIETNAMESE: Locale = Locale("vi")

    /**
     * Tiếng Thái KHÔNG kèm quốc gia — khớp `values-th`. [ĐO] JDK 17 `Calendar.createCalendar` dựng `BuddhistCalendar`
     * cho `th_TH` (năm 2569), còn libcore Android 10/12 luôn Gregorian ⇒ `Locale("th", "TH")` làm test off-car và xe
     * lệch nhau ngay khi một mẫu có năm. `LangHostTest` khoá điều này.
     */
    private val THAI: Locale = Locale("th")

    /** Tiếng Mã Lai — khớp `values-ms`. */
    private val MALAY: Locale = Locale("ms")
}
