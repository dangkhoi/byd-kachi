package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.launcher.Lang as CoreLang
import com.byd.clusternav.launcher.Strings as CoreStrings

/**
 * Đa ngôn ngữ NHẸ — Tiếng Việt (gốc) + English, từ 2026-10-03 thêm 简体中文 · ไทย · Bahasa Melayu (spec
 * `docs/specs/kachi-i18n-zh-th-ms.html`). Cố ý KHÔNG dùng resource `values-en/strings.xml`:
 *
 * app này dựng UI bằng code với chuỗi inline (khoảng 150 chuỗi rải trong các Activity), không tham chiếu
 * `@string/…`. Chuyển hết sang resource sẽ phải (a) đặt id cho từng chuỗi, (b) đổi mọi call site sang
 * getString, (c) thêm cơ chế đổi locale runtime cho UI-dựng-bằng-code — nhiều churn, dễ sót, rủi ro cao cho
 * app chạy trên xe. Thay vào đó để bản dịch NGAY TẠI call site: `Lang.t("Chiếu lên cụm", "Cast to cluster")`.
 * Đọc code là thấy cả hai thứ tiếng, không phải nhảy sang file khác. Ba tiếng mới KHÔNG viết tại chỗ: cặp (vi, en)
 * là khoá tra bảng dịch của `:core` ([CoreStrings.t] → `I18nCatalog`), thiếu dòng ⇒ tiếng Anh.
 *
 * Đổi ngôn ngữ → Activity gọi `recreate()` để dựng lại UI bằng cache mới.
 */
object Lang {

    /** Ngôn ngữ ĐÃ GIẢI NGHĨA của màn cũ — mỗi mục ánh xạ đúng một [CoreLang] ([core]) để [t]/[f] tra cùng một bảng. */
    enum class L(val code: String, val label: String, val core: CoreLang) {
        VI("vi", "Tiếng Việt", CoreLang.VI),
        EN("en", "English", CoreLang.EN),
        ZH("zh", "简体中文", CoreLang.ZH),
        TH("th", "ไทย", CoreLang.TH),
        MS("ms", "Bahasa Melayu", CoreLang.MS),
    }

    /**
     * Lựa chọn ngôn ngữ của người dùng, LƯU THÔ (không giải nghĩa):
     *  • [AUTO] — theo locale máy/xe (máy tiếng Việt → VI, còn lại → EN). Mặc định lần đầu.
     *  • [VI] / [EN] / [ZH] / [TH] / [MS] — người dùng chốt cứng một thứ tiếng.
     * `code` là chuỗi lưu vào SharedPreferences (cùng khoá [K] như trước). Giá trị `vi`/`en` cũ vẫn hợp lệ →
     * ánh xạ về [VI]/[EN]; mọi giá trị khác / null → [AUTO] (tương thích ngược).
     *
     * ⚠⚠ `code` PHẢI khớp từng ký tự với `com.byd.clusternav.launcher.LangMode.code` (cùng một giá trị trên đĩa —
     * KDoc `WorkspacePrefs.langMode`). Thiếu một mã ở đây thì lựa chọn đó im lặng rơi về [AUTO] ở màn cũ;
     * `LauncherLocaleContractTest` đối chiếu hai bộ mã bằng máy.
     */
    enum class Choice(val code: String) {
        AUTO("auto"),
        VI("vi"),
        EN("en"),
        ZH("zh"),
        TH("th"),
        MS("ms"),
        ;

        companion object {
            /** Đọc mã từ đĩa — mã lạ/`null` ⇒ [AUTO]. Tổng (không ném): một mã của bản mới hơn không làm sập bản cũ. */
            fun of(code: String?): Choice = entries.firstOrNull { it.code == code } ?: AUTO
        }
    }

    private const val PREF = "clusternav_lang"
    private const val K = "lang"

    @Volatile private var cache: L? = null

    /** Nạp ngôn ngữ ĐÃ GIẢI NGHĨA vào cache. Gọi ở đầu `onCreate` của mỗi Activity, TRƯỚC khi dựng UI. */
    fun load(ctx: Context): L {
        cache?.let { return it }
        val l = resolve(ctx, choice(ctx))
        cache = l
        return l
    }

    /** Lựa chọn THÔ đã lưu (mặc định [Choice.AUTO]). Đọc trực tiếp pref — dùng để seed selector. */
    fun choice(ctx: Context): Choice =
        Choice.of(ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(K, null))

    /** Giải nghĩa một [Choice] thành ngôn ngữ cụ thể ([Choice.AUTO] → theo locale máy). */
    private fun resolve(ctx: Context, choice: Choice): L = when (choice) {
        Choice.VI -> L.VI
        Choice.EN -> L.EN
        Choice.ZH -> L.ZH
        Choice.TH -> L.TH
        Choice.MS -> L.MS
        Choice.AUTO -> defaultFor(ctx)
    }

    /**
     * Lần đầu chạy / AUTO: đoán theo locale máy — máy tiếng Việt → VI, còn lại → EN. KHÔNG tự chọn zh/th/ms (spec
     * `kachi-i18n-zh-th-ms.html` OQ1 — cùng luật `LangMode.resolve` của `:core`).
     */
    private fun defaultFor(ctx: Context): L =
        if (runCatching {
                ctx.resources.configuration.locales[0].language
            }.getOrNull() == "vi") L.VI else L.EN

    fun cur(ctx: Context): L = load(ctx)

    /** Lưu lựa chọn THÔ + cập nhật cache sang ngôn ngữ đã giải nghĩa. Activity gọi `recreate()` sau đó. */
    fun setChoice(ctx: Context, choice: Choice) {
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(K, choice.code).apply()
        cache = resolve(ctx, choice)
    }

    /** Tương thích ngược: đặt cứng một thứ tiếng = [Choice] cùng mã (mã của [L] và [Choice] trùng nhau từng ký tự). */
    fun set(ctx: Context, l: L) = setChoice(ctx, Choice.of(l.code))

    fun toggle(ctx: Context): L {
        val next = if (cur(ctx) == L.VI) L.EN else L.VI
        set(ctx, next); return next
    }

    /**
     * Chọn chuỗi theo ngôn ngữ ĐANG dùng. Dùng dạng không-Context để call site gọn; Activity của màn cũ chịu
     * trách nhiệm [load] trước khi dựng UI.
     *
     * Uỷ quyền cho [CoreStrings.t] (một phép chọn cho cả APK): VI → [vi], EN → [en] — y từng byte như bản hai
     * nhánh cũ; ZH/TH/MS → bảng dịch theo cặp, thiếu dòng ⇒ [en].
     *
     * ⚠ [vi]/[en] phải là chữ CỐ ĐỊNH (khoá bảng dịch) — câu có biến dùng [f]. `I18nSourceGuardTest` đỏ nếu thấy `$`.
     */
    fun t(vi: String, en: String): String = CoreStrings.t(vi, en, effective().core)

    /**
     * Câu CÓ BIẾN: `Lang.f("đang tải… {0}%", "downloading… {0}%", pct)` thay cho `Lang.t("đang tải… $pct%", …)`.
     * Khoá bảng dịch là MẪU tĩnh; `{n}` thay bằng `args[n].toString()` (đúng phép `"$x"` của Kotlin) ⇒ VI/EN ra y
     * từng byte như chuỗi mẫu `$` cũ. Xem [CoreStrings.fIn].
     */
    fun f(vi: String, en: String, vararg args: Any?): String = CoreStrings.fIn(effective().core, vi, en, *args)

    /**
     * ═══ NGÔN NGỮ HIỆU LỰC — cache của màn cũ, RỒI MỚI tới kênh của launcher ═════════════════════════════════
     *
     * ## [ĐO] Bệnh: view tự vẽ nói tiếng Việt trong giao diện English (soát ảnh Settings v2, 2026-09-13)
     * `BadgePlacementView` / `VmBubblePlacementView` / `SeatDiagramView` là view của màn ClusterNav cũ, chữ vẽ
     * thẳng lên [android.graphics.Canvas] nên **không** đi qua `R.string`. Nay chúng được nhúng vào các nhóm của
     * màn Cài đặt Kachi (`SettingsNavSection` · `SettingsCarSection`), mà launcher **không bao giờ gọi** [load] —
     * nó áp ngôn ngữ bằng đường riêng ([com.byd.clusternav.launcher.LangHost.wrap]: ghi
     * [CoreStrings.current] + đặt locale của `Context`). [cache] vì thế còn `null` ⇒ `cache == L.EN` sai ⇒ **mọi**
     * chuỗi rơi về tiếng Việt, im lặng, chỉ người dùng English thấy.
     *
     * ## Vì sao lùi về [CoreStrings.current] chứ không tự đọc prefs/locale ở đây
     * Hai kênh đã **chung một nguồn lưu**: `WorkspacePrefs.langMode()` đọc chính [choice] của lớp này, và
     * `WorkspacePrefs.setLangMode` ghi qua chính [setChoice] (⇒ [cache] được cập nhật ngay cả khi người dùng đổi
     * ngôn ngữ TỪ launcher). Nên chỗ này không thêm nguồn sự thật thứ ba — nó chỉ đọc kênh mà [LangHost] vừa ghi,
     * và chỉ khi màn cũ **chưa từng** nạp cache. Thứ tự này giữ nguyên hành vi màn cũ: ở đó [load] chạy đầu
     * `onCreate` nên [cache] luôn khác `null` trước khi có chữ nào được vẽ.
     *
     * Ánh xạ [CoreLang] → [L] là `when` VÉT CẠN: thêm một tiếng ở `:core` mà quên ở đây thì không biên dịch được
     * (bản hai nhánh cũ đưa mọi tiếng ≠ EN về tiếng Việt, im lặng).
     */
    private fun effective(): L = cache ?: when (CoreStrings.current) {
        CoreLang.VI -> L.VI
        CoreLang.EN -> L.EN
        CoreLang.ZH -> L.ZH
        CoreLang.TH -> L.TH
        CoreLang.MS -> L.MS
    }
}
