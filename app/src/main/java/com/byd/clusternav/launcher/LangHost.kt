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
        val lang = WorkspacePrefs(base).langMode().resolve(systemLanguage(base))
        Strings.current = lang   // ⇐ CHỖ GHI DUY NHẤT của `Strings.current` trong toàn dự án
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(if (lang == Lang.EN) Locale.ENGLISH else VIETNAMESE)
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
    fun locale(): Locale = if (Strings.current == Lang.EN) Locale.ENGLISH else VIETNAMESE

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
}
