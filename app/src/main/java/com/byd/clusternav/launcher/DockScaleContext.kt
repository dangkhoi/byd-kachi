package com.byd.clusternav.launcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 2.89 · B3 DOCK-SCALE — `Context` CO/GIÃN cho cây view của thanh nút xe (`kachi-289-field-fixes` §B3) ════════════════
 *
 * Cả cây view của thanh (ô nút, ô đọc, ô gói lệnh, ô Launcher, khối lối tắt, lề, bo góc) dựng bằng `Context` này ⇒ MỌI
 * `KachiTheme.dpi(ctx, …)` và mọi cỡ chữ `sp` đọc mật độ từ đây ⇒ co/giãn ĐỒNG ĐỀU theo % mà không sửa một hằng nào
 * (`ControlTileFactory` không phải biết gì). Phép số ở [BarScale] (`:core`).
 *
 * ## Cơ chế [ĐO AOSP r47]
 *  • `ContextThemeWrapper.applyOverrideConfiguration` phải gọi TRƯỚC `getResources` (`ContextThemeWrapper.java:100-110`);
 *    có ghi đè ⇒ `getResources` = `createConfigurationContext(ghi đè).getResources()` (`:131-140`) — `Resources` riêng của
 *    chính `Context` này, KHÔNG state hệ thống, chết theo tiến trình ⇒ không cần đường trả lại (CLAUDE.md §5).
 *  • Ghi đè `densityDpi` + ngôn ngữ của base (Pass 2 · vietmap-dock-r1-7 — `Configuration()` = `unset()`, mọi trường khác "không
 *    ghi đè"); `ResourcesImpl` đặt
 *    `density = densityDpi × DENSITY_DEFAULT_SCALE`, `scaledDensity = density × fontScale` (`ResourcesImpl.java:435-443`)
 *    ⇒ chữ `sp` theo ĐÚNG cùng hệ số (owner: *"kéo bao nhiêu hiển thị bấy nhiêu"*).
 *  • Theme: `initializeTheme` chép theme của base rồi `applyStyle(themeResId)` (`:207-217`); `themeResId = 0` thì
 *    `getTheme` chọn theme MẶC ĐỊNH đè lên (`:168-176`) ⇒ phải truyền đúng theme của Activity — đọc từ manifest lúc chạy
 *    ([themeOf]), không chép tay.
 *  • Chuỗi `ContextWrapper` giữ nguyên tới Activity ⇒ `ShortcutHub.hostOf` (lần ngược wrapper) vẫn tìm ra màn.
 *
 * ## ⚠ K1 — KHÔNG bộ nhớ đệm toàn cục
 * Wrapper bọc CHÍNH Activity truyền vào. Một bảng `object` khoá bằng (pct, dpi) sẽ trả wrapper bọc Activity ĐÃ CHẾT sau
 * `recreate()` (đổi ngôn ngữ) ⇒ chạm lối tắt trên thanh không làm gì, nhãn theo locale cũ, rò Activity. Nên [wrap] luôn
 * dựng mới; chỗ gọi giữ nó trong TRƯỜNG của chính view (sống/chết cùng Activity) — `ControlDockView.ui`.
 */
internal object DockScaleContext {

    /** `Context` cho [pct]: 100 % ⇒ CHÍNH [base] (đường cũ, không lớp mới); khác ⇒ wrapper có `densityDpi` ghi đè. */
    fun wrap(base: Context, pct: Int): Context = if (BarScale.isIdentity(pct)) base else Scaled(base, BarScale.snap(pct))

    /** `Context` GỐC (chưa co/giãn) của [ctx] — đo đích chạm THẬT, dựng thông báo cỡ thật. Không co ⇒ chính [ctx]. */
    fun unscaled(ctx: Context): Context = scaledOf(ctx)?.baseContext ?: ctx

    /**
     * 48 dp THẬT ([Sp.TOUCH] trên mật độ gốc) — sàn đích chạm của thanh nút ở mọi cỡ. Ở 100 % (không lớp co/giãn) đúng
     * bằng `dpi(ctx, Sp.TOUCH)` như trước.
     */
    fun touchFloorPx(ctx: Context): Int = dpi(unscaled(ctx), Sp.TOUCH)

    /** `true` ⇔ [ctx] là Context co/giãn của thanh (cỡ ≠ 100 %, [wrap] đã bọc). 2.96 DOCK-ICON-EVEN-GAP. */
    fun isScaled(ctx: Context): Boolean = scaledOf(ctx) != null

    private fun scaledOf(ctx: Context): Scaled? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is Scaled) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Theme của Activity chứa [base] (manifest, đọc lúc chạy) → theme app → `Theme.DeviceDefault.NoActionBar` (đúng giá trị
     * manifest hôm nay — chỉ là lưới cuối khi không tra được, không bao giờ để 0, xem KDoc lớp).
     */
    private fun themeOf(base: Context): Int {
        var c: Context? = base
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val fromActivity = (c as? Activity)?.let { a ->
            try {
                a.packageManager.getActivityInfo(a.componentName, 0).themeResource
            } catch (e: PackageManager.NameNotFoundException) {
                0
            }
        } ?: 0
        return when {
            fromActivity != 0 -> fromActivity
            base.applicationInfo.theme != 0 -> base.applicationInfo.theme
            else -> android.R.style.Theme_DeviceDefault_NoActionBar
        }
    }

    /** Wrapper co/giãn — [pct] đã snap. Chỉ dựng qua [wrap]. */
    private class Scaled(base: Context, val pct: Int) : ContextThemeWrapper(base, themeOf(base)) {
        /** `densityDpi` gốc lúc dựng — `ControlDockView` so với số hiện tại để dựng lại khi mật độ hệ thống đổi. */
        val baseDpi: Int = base.resources.configuration.densityDpi

        init {
            // Review 2.89 Pass 2 · vietmap-dock-r1-7: mang theo NGÔN NGỮ của base (LangHost — `createConfigurationContext` có
            // locale, `LangHost.kt:68`). [ĐO nguồn r47] `ContextImpl.createConfigurationContext` (`ContextImpl.java:2232-2244`)
            // dựng `Resources` qua `ResourcesManager.getResources` (`ResourcesManager.java:859-883`): khoá chỉ mang ghi đè MỚI,
            // nền = ghi đè của token Activity (`:777-783`) rồi cấu hình hệ thống (`generateConfig` `:493-509`) — ghi đè locale
            // của LangHost không có mặt ⇒ ghi đè chỉ densityDpi làm chuỗi đọc qua `ui` ở cỡ ≠ 100 % theo ngôn ngữ MÁY.
            applyOverrideConfiguration(Configuration().apply {
                densityDpi = BarScale.scaledDpi(baseDpi, pct)
                setLocales(base.resources.configuration.locales)
            })
        }
    }

    /** `densityDpi` GỐC mà [ctx] (nếu co/giãn) được dựng từ đó; không co ⇒ mật độ của chính nó. */
    fun baseDpiOf(ctx: Context): Int = scaledOf(ctx)?.baseDpi ?: ctx.resources.configuration.densityDpi
}
