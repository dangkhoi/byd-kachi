package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState

/**
 * Renders the single-icon floating bubble (R5 / #7 — docs/specs/cast-nav-ux-release-v104.html).
 *
 * The bubble is ONE icon sized like an app icon ([ICON_SIZE_DP]dp), with **no background / border / fill**.
 * Idle/active dimming is the WINDOW alpha owned by [FloatingBubbleService], not a per-view background. The prior
 * three-zone (Trái/Phải/Full) layout, its painting and its zone-hit-test are gone; slot casting now lives in the
 * long-press submenu ([BubbleSubmenuOverlay]).
 *
 * ## ⚠ WP6 · R6.2 (owner 2026-09-20) — glyph nay là **icon app Kachi**, không còn mũi tên xanh
 * Owner: *"đổi icon nút nổi thành icon app Kachi, nhỏ gọn"*. Dùng **`R.drawable.launcher_fg`** = đúng lớp trước của
 * adaptive icon (hoa anh đào), tức CHÍNH tài sản đang làm icon app — chứ không chép hình học sang một tệp `ic_*`
 * thứ hai. Hai lý do:
 *  • **một hình, một nguồn**: `ic_launcher.xml` 24dp là bản MỘT TÔNG cho smallIcon thông báo (hệ thống tô trắng
 *    theo hợp đồng), nên dùng nó ở đây sẽ phải tự tô màu bằng tay ⇒ mã màu viết cứng trong Kotlin, đúng thứ
 *    `ThemePaletteContractTest` sinh ra để chặn. `launcher_fg` mang luôn màu riêng của thương hiệu;
 *  • **đọc được trên mọi nền**: nút nổi nằm trên pixel của app đang chiếu (bản đồ sáng, video tối, gì cũng có).
 *    Một glyph NHIỀU TÔNG (hồng chuyển sắc + nhị vàng) còn hình để nhận ra ở cả hai; mũi tên một tông
 *    `#1565C0` cũ thì tan vào mọi nền xanh đậm.
 *
 * *"Nhỏ gọn"* = **hộp** co từ 52 → [ICON_SIZE_DP]dp (đúng sàn chạm [TOUCH_MIN_DP], không xuống dưới — nút này bắn
 * một lệnh chiếu thật) và **lề trong về 0**: `launcher_fg` đã tự chừa lề quang học của adaptive icon ([ĐO] hộp mực
 * 27.94..80.06 trên khung 108 = 48.3%), nên cộng thêm lề nữa là thu hai lần.
 */
internal class BubbleRenderer(private val context: Context) {

    /** The single bubble icon, or null before [buildBubble] / after [clearViews]. */
    var iconView: ImageView? = null
        private set

    /**
     * Build the one-icon bubble: a transparent [ImageView] showing only the Kachi blossom, in a box of exactly
     * [ICON_SIZE_DP]dp (= the [TOUCH_MIN_DP]dp automotive touch-target floor). It is clickable, long-clickable and
     * focusable so the gesture handler and TalkBack both see one actionable target. Window WRAP_CONTENT + this box
     * give the bubble its size (see [FloatingBubbleService.showBubble]).
     *
     * ⚠⚠ **`maxWidth`/`maxHeight` + `adjustViewBounds` là BẮT BUỘC, không phải trang trí.** `launcher_fg` khai cỡ
     * riêng **108dp** (nó là lớp trước của adaptive icon), và `minimumWidth`/`minimumHeight` chỉ đặt SÀN — cỡ nội
     * tại vẫn thắng. [ĐO máy ảo API 29, density 240] bản đầu của WP6 chỉ có `minimum*` ⇒ cửa sổ ra **162×162 px =
     * 108dp**, tức nút nổi TO GẤP ĐÔI thay vì *"nhỏ gọn"* như owner yêu cầu. Cặp `max*` + `adjustViewBounds` hạ
     * đúng khung đo (`resolveAdjustedSize` lấy `min(cỡ nội tại, max, spec)`) ⇒ 72×72 px = 48dp.
     */
    fun buildBubble(): View {
        val size = dp(ICON_SIZE_DP)
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.launcher_fg)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = null // R5: no background / border / fill — just the glyph
            adjustViewBounds = true
            maxWidth = size
            maxHeight = size
            minimumWidth = size
            minimumHeight = size
            // WP6 · R6.2 — KHÔNG padding: lề quang học đã nằm trong chính `launcher_fg` (xem KDoc lớp).
            setPadding(0, 0, 0, 0)
            isClickable = true
            isLongClickable = true
            isFocusable = true
            contentDescription = CONTENT_DESC_IDLE
        }
        iconView = icon
        return icon
    }

    /**
     * Update ONLY the icon's content description to reflect whether a tap will cast or return
     * (no background/tint change — the glyph stays a plain transparent icon, R5). Returns true when
     * a view exists to update. The visual idle/active state is the window alpha, handled elsewhere.
     */
    fun refreshFromState(state: SimpleCastState): Boolean {
        val icon = iconView ?: return false
        icon.contentDescription = contentDescriptionFor(state)
        return true
    }

    fun clearViews() {
        iconView = null
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()

    companion object {
        /**
         * Rendered icon side (dp). WP6 · R6.2: **52 → 48** = đúng [TOUCH_MIN_DP], sàn đích-chạm của xe. Không hạ
         * thêm: nút này bắn một lệnh chiếu THẬT lên cụm, nên nó không được nhỏ hơn sàn đó.
         */
        internal const val ICON_SIZE_DP = 48

        /** Automotive minimum touch target (dp) enforced for the icon AND each submenu row (R5/R9). */
        internal const val TOUCH_MIN_DP = 48

        /** Accessibility label when nothing is on the cluster (a tap will cast the foreground). */
        internal const val CONTENT_DESC_IDLE = "ClusterNav cast"

        /** Accessibility label while casting (a tap will return to the cluster gauges). */
        internal const val CONTENT_DESC_CASTING = "ClusterNav cast · chạm để trả về"

        /** Accessibility label during a transient state (not actionable yet). */
        internal const val CONTENT_DESC_BUSY = "ClusterNav cast · đang xử lý"

        /**
         * Content description for a cast state — pure so it is unit-testable without a Context.
         * Idle → [CONTENT_DESC_IDLE]; casting (full or split) → [CONTENT_DESC_CASTING]; any transient
         * state → [CONTENT_DESC_BUSY]. Mirrors [BubbleGesturePlanner.tapOutcome] so the spoken hint
         * always matches what the tap will do.
         */
        fun contentDescriptionFor(state: SimpleCastState): String = when (state) {
            is SimpleCastState.Idle -> CONTENT_DESC_IDLE
            is SimpleCastState.CastingFull, is SimpleCastState.CastingSplit -> CONTENT_DESC_CASTING
            else -> CONTENT_DESC_BUSY
        }
    }
}
