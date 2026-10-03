package com.byd.clusternav.launcher

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.widget.ImageView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import java.util.WeakHashMap
import kotlin.math.roundToInt
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ VISUAL-REFRESH P2 · T9 (biến thể 32/48dp) + AC2.6 (chọn/không-chọn nhìn ra được) ══════════════════════════
 *
 * Spec `docs/specs/kachi-visual-refresh.html` §R2 · §4.2 luật 6 (*"ba cỡ, ba mức chi tiết — sinh từ cùng nguồn,
 * khác nhau ở bộ lọc chi tiết"*). 21 icon `large` trong `design/icon-grammar.json` có thêm `ic_<id>_l.xml` (32dp) và
 * `ic_<id>_xl.xml` (48dp) do `scripts/design/gen-icons.py` sinh: **cùng SVG**, chỉ thêm lớp `data-min="32"` (đĩa
 * nền, thân xe mờ) — không phải phóng to bản 24.
 *
 * ## Hai hợp đồng ở đây
 *  • [res]: mặt lớn ([KachiSpace.ICON_L]/[KachiSpace.ICON_XL]/[KachiSpace.ICON_XXL]) tra ra biến thể đúng cỡ; icon
 *    không có biến thể lùi về bản 24 (vẫn đúng hình, chỉ thiếu lớp nền). Bảng [LARGE] khoá theo **id drawable gốc**
 *    chứ không theo tên `ic-…`: hai tên trỏ cùng tệp (`ic-apps` → `ic_grid`) thì cùng nhận biến thể.
 *  • [tint]: [ĐO] `design/contrast-families.md` — trên nền SÁNG mọi họ màu `main`/`light` < 3:1 ⇒ chủ đề SÁNG **tiếp
 *    tục tint INK** ở mọi cỡ (thang alpha ba bậc sống qua tint nên hình vẫn có lớp); chủ đề TỐI bỏ colour filter ở
 *    mặt lớn để thấy màu/chuyển sắc, và ô **chưa chọn** hạ bão hoà + độ mờ qua `ColorMatrixColorFilter` (AC2.6 —
 *    không `setTint` đơn sắc). Mặt nhỏ (thanh trên, chip, hàng nút nhóm) giữ tint mực như 1.69.
 */
internal object KachiIcons {

    /** drawable 24dp → (32dp, 48dp). Sinh cùng lượt với bộ icon; `IconStyleContractTest` khoá đủ 21 mục và không mồ côi. */
    private val LARGE: Map<Int, Pair<Int, Int>> = mapOf(
        R.drawable.ic_bolt to (R.drawable.ic_bolt_l to R.drawable.ic_bolt_xl),
        R.drawable.ic_car to (R.drawable.ic_car_l to R.drawable.ic_car_xl),
        R.drawable.ic_door to (R.drawable.ic_door_l to R.drawable.ic_door_xl),
        R.drawable.ic_grid to (R.drawable.ic_grid_l to R.drawable.ic_grid_xl),
        R.drawable.ic_group_battery_health to (R.drawable.ic_group_battery_health_l to R.drawable.ic_group_battery_health_xl),
        R.drawable.ic_group_climate to (R.drawable.ic_group_climate_l to R.drawable.ic_group_climate_xl),
        R.drawable.ic_group_doors to (R.drawable.ic_group_doors_l to R.drawable.ic_group_doors_xl),
        R.drawable.ic_group_energy to (R.drawable.ic_group_energy_l to R.drawable.ic_group_energy_xl),
        R.drawable.ic_group_lights to (R.drawable.ic_group_lights_l to R.drawable.ic_group_lights_xl),
        R.drawable.ic_group_trip to (R.drawable.ic_group_trip_l to R.drawable.ic_group_trip_xl),
        R.drawable.ic_group_tyres to (R.drawable.ic_group_tyres_l to R.drawable.ic_group_tyres_xl),
        R.drawable.ic_group_windows to (R.drawable.ic_group_windows_l to R.drawable.ic_group_windows_xl),
        R.drawable.ic_leaf to (R.drawable.ic_leaf_l to R.drawable.ic_leaf_xl),
        R.drawable.ic_lock to (R.drawable.ic_lock_l to R.drawable.ic_lock_xl),
        R.drawable.ic_music to (R.drawable.ic_music_l to R.drawable.ic_music_xl),
        R.drawable.ic_photo to (R.drawable.ic_photo_l to R.drawable.ic_photo_xl),
        R.drawable.ic_speed to (R.drawable.ic_speed_l to R.drawable.ic_speed_xl),
        R.drawable.ic_sun to (R.drawable.ic_sun_l to R.drawable.ic_sun_xl),
        R.drawable.ic_window_close to (R.drawable.ic_window_close_l to R.drawable.ic_window_close_xl),
        R.drawable.ic_window_open to (R.drawable.ic_window_open_l to R.drawable.ic_window_open_xl),
    )

    /** Icon cho một mặt cỡ [sizeDp]: biến thể 48 từ [KachiSpace.ICON_XL] (44), biến thể 32 từ [KachiSpace.ICON_L]. */
    fun res(icon: String, sizeDp: Int): Int {
        val base = KachiTheme.iconRes(icon)
        if (base == 0) return 0
        val v = LARGE[base] ?: return base
        return when {
            sizeDp >= Sp.ICON_XL -> v.second
            sizeDp >= Sp.ICON_L -> v.first
            else -> base
        }
    }

    /**
     * Hợp đồng tint (KDoc lớp). [inkHex] = mực khi tint (mặc định [KachiTheme.INK]; ô điều khiển đang bật truyền
     * [KachiTheme.INK_ON_ACCENT]). [selected] chỉ có nghĩa ở mặt lớn + chủ đề tối.
     */
    fun tint(img: ImageView, sizeDp: Int, selected: Boolean, inkHex: String = KachiTheme.INK) {
        drawn(img).also { it.tinted = true; it.selected = selected; it.ink = inkHex }
        when {
            !KachiTheme.night || sized(img, sizeDp) < Sp.ICON_L -> img.setColorFilter(c(inkHex))
            selected -> img.colorFilter = null
            else -> img.colorFilter = UNSELECTED
        }
    }

    /**
     * 2.76 L7 — ô NÚT đổi hình theo MỨC: họ có hình theo mức ([CapabilityIcons.forLevel]) thì mức ≥ 1 vẽ đúng hình
     * của mức ấy (một/hai làn nhiệt · một/hai bông tuyết); mức 0 / họ không khai ⇒ hình khái niệm [concept] như cũ.
     * Cùng nguồn hình với chip ([TopStripChips]) và [DatumIconView] ⇒ ba bề mặt không bao giờ nói ba mức khác nhau.
     *
     * `setImageResource` **chỉ khi đổi thật** (ghi id vào `tag`): [ControlTileFactory] gọi `look` theo nhịp trạng
     * thái xe (1 Hz), mà `setImageResource` luôn giải mã lại drawable — không có cửa này là một lượt cấp phát/giây cho
     * mỗi ô (cùng lẽ `ControlLevelBar.light` chỉ đổi alpha). Không tra ra hình (`0`) ⇒ giữ nguyên, không xoá icon.
     */
    fun byLevel(img: ImageView, concept: String, level: Int, sizeDp: Int) {
        drawn(img).also { it.concept = concept; it.level = level }
        val r = res(CapabilityIcons.forLevel(concept, level) ?: concept, sized(img, sizeDp))
        if (r != 0 && img.tag != r) { img.setImageResource(r); img.tag = r }
    }

    // ── L5 WIDGET-FIT-ALL (2.87, soát vòng 1 P3) — biến thể + tint theo cỡ ĐÃ KHỚP ─────────────────────────────────
    /**
     * Lần vẽ cuối của một icon — CHỈ giá trị, không giữ view nào (khoá yếu tự rơi khi ô bị gỡ; giá trị trỏ về khoá thì
     * `WeakHashMap` không dọn được — bài học [WidgetRefreshers]). [fittedDp] = cỡ do `FitScale` đặt (0 = chưa khớp).
     */
    private class Drawn {
        var concept: String? = null
        var level = 0
        var tinted = false
        var selected = false
        var ink = KachiTheme.INK
        var fittedDp = 0
    }

    /** CHỈ luồng chính chạm (bộ dựng ô, `look`, lượt đo của `FitGridLayout`). */
    private val drawnBy = WeakHashMap<ImageView, Drawn>()

    private fun drawn(img: ImageView): Drawn = drawnBy.getOrPut(img) { Drawn() }

    /** Cỡ chọn biến thể/tint: cỡ ĐÃ KHỚP nếu `FitScale` đã đặt, không thì cỡ của bộ dựng [sizeDp]. */
    private fun sized(img: ImageView, sizeDp: Int): Int = drawnBy[img]?.fittedDp?.takeIf { it > 0 } ?: sizeDp

    /**
     * `FitScale` vừa đổi cỡ icon cố định thành [sidePx] (cạnh nhỏ) ⇒ chọn lại biến thể 32/48dp + luật tint theo cỡ THẬT
     * và vẽ lại đúng lần vẽ cuối (hình theo mức, chọn/không chọn, mực). Không có bước này thì ô `DOCK` 20dp được nhân ×2
     * thành 40dp vẫn mang biến thể + tint của mặt NHỎ, khác hẳn ô `BIG` cùng cỡ trên màn (soát vòng 1, P3). Icon chưa
     * từng qua [tint]/[byLevel] (vd icon ô nén tô màu riêng) ⇒ chỉ ghi cỡ, không vẽ gì.
     */
    fun refit(img: ImageView, sidePx: Int) {
        val dp = (sidePx / img.resources.displayMetrics.density).roundToInt()
        val d = drawn(img)
        if (d.fittedDp == dp) return
        d.fittedDp = dp
        d.concept?.let { byLevel(img, it, d.level, dp) }
        if (d.tinted) tint(img, dp, d.selected, d.ink)
    }

    /**
     * Ô chưa chọn ở chủ đề tối: bão hoà 35 % + độ mờ 72 % — [ĐO] Δ độ sáng so với ô đang chọn ≥ 20 % (AC2.6) mà hình
     * vẫn đọc được vì lớp `main` luôn mang đủ hình (§4.2 luật 2).
     */
    private val UNSELECTED: ColorMatrixColorFilter = ColorMatrixColorFilter(
        ColorMatrix().apply {
            setSaturation(0.35f)
            postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 0.72f, 0f,
            )))
        },
    )
}
