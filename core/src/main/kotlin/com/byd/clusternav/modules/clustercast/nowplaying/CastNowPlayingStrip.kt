package com.byd.clusternav.modules.clustercast.nowplaying

import com.byd.clusternav.launcher.camera.ClusterBandSpec
import com.byd.clusternav.modules.clustercast.simplified.CastBounds
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState

/**
 * ═══ 3.1.12 · CAST-NOW-PLAYING — dải "♪ tên bài — nghệ sĩ" Kachi vẽ trên MẶT CHIẾU cụm — phần THUẦN ═══════════════════════
 *
 * [ĐO xe 17/10] `com.byd.mediacontroller` đã gửi tên bài của mọi app lên cụm (`0x43FB1008`, `sendMusicName result: 0`); ô nhạc
 * gốc ở giữa cụm chỉ bị mặt chiếu của Kachi che (owner). ⇒ Khi đang chiếu, Kachi tự vẽ tên bài ở một góc của khung chiếu, qua
 * CÙNG đường lớp phủ cụm của badge tốc độ (`ClusterOverlayDisplays` + `TYPE_APPLICATION_OVERLAY`, `:app` `CastNowPlayingOverlay`).
 * Không HAL, không opcode, không `am`/`wm`. Doc `docs/diagnostics/cluster-now-playing-2026-10-16.md` §9.
 *
 * Tệp này quyết: hiện/ẩn ([CastNowPlayingPlan]) · chữ ([CastNowPlayingText]) · chỗ đặt ([CastNowPlayingPlace]) · màu theo
 * sáng/tối của HỆ THỐNG xe — cụm theo `uiMode` hệ thống [ĐO 07/10], cùng nguồn `ClusterBackdrop` ([CastNowPlayingStyle]).
 */
object CastNowPlayingDefaults {
    /**
     * MẶC ĐỊNH TẮT (3.1.13, owner 17/10 trước OTA: "mặc định tắt là đc" — dải chưa từng chạy trên cụm thật; ai muốn thì bật
     * trong Cài đặt). Chi phí khi bật: KHÔNG chiếu ⇒ 0 việc (chỉ hai bộ nghe đã đăng ký sẵn, không gọi gì); đang chiếu ⇒ MỘT bộ
     * nghe phiên nhạc (callback khi đổi bài/trạng thái, không nhịp) + MỘT cửa sổ nhỏ chỉ gắn khi có bài để hiện (gỡ khi dừng
     * quá [CastNowPlayingPlan.AUTO_HIDE_MS] / hết phiên / hết chiếu). Sự kiện trùng ⇒ 0 thao tác cửa sổ. Lợi ích đúng chỗ người
     * lái mất: tên bài mà cụm gốc vốn hiện, nay bị mặt chiếu che.
     */
    const val ENABLED: Boolean = false

    /** Góc mặc định: phải-trên — chỗ app dẫn đường (GMaps/CarPlay) ít đặt chỉ dẫn nhất (thẻ rẽ ở trái-trên, ETA ở dưới). */
    val CORNER: CastNowPlayingCorner = CastNowPlayingCorner.TOP_RIGHT
}

/** Góc của khung chiếu. [key] là chuỗi lưu bền (ASCII, không đổi). */
enum class CastNowPlayingCorner(val key: String, val right: Boolean, val bottom: Boolean, val center: Boolean = false) {
    TOP_LEFT("tl", right = false, bottom = false),
    /** 3.1.20 (owner 26/10): giữa mép trên của màn cụm, bề ngang CỐ ĐỊNH = 1/3 bề ngang màn ([CastNowPlayingPlace.CENTER_WIDTH_FRAC]). */
    TOP_CENTER("tc", right = false, bottom = false, center = true),
    TOP_RIGHT("tr", right = true, bottom = false),
    BOTTOM_LEFT("bl", right = false, bottom = true),
    BOTTOM_RIGHT("br", right = true, bottom = true);

    companion object {
        /** Chuỗi lạ / vắng ⇒ [CastNowPlayingDefaults.CORNER] (dữ liệu hỏng không được làm mất dải). */
        fun parse(s: String?): CastNowPlayingCorner = values().firstOrNull { it.key == s?.trim() } ?: CastNowPlayingDefaults.CORNER
    }
}

/** Phiên nhạc đang chọn (`MediaBridge.pick`: đang phát trước). */
data class CastNowPlayingMedia(val title: String?, val artist: String?, val playing: Boolean)

/** Thứ đang hiện trên cụm. So bằng `==` ⇒ sự kiện trùng không chạm cửa sổ. */
data class CastNowPlayingContent(val text: String, val playing: Boolean)

/**
 * Hiện/ẩn. Không luồng, không nhịp: bên gọi chạy mọi hàm trên MỘT luồng; [Step.checkInMs] ≠ `null` ⇒ hẹn ĐÚNG một lượt
 * [onPauseCheck] (chỉ ở lượt dừng ĐẦU của một bài — sự kiện dừng lặp lại không dời mốc).
 */
class CastNowPlayingPlan(private val autoHideMs: Long = AUTO_HIDE_MS) {

    data class Step(val content: CastNowPlayingContent?, val changed: Boolean, val checkInMs: Long?)

    var shown: CastNowPlayingContent? = null
        private set

    private var active = false
    private var media: CastNowPlayingMedia? = null
    private var pausedAtMs: Long? = null
    private var pausedText: String? = null

    /** Bật công tắc + đang chiếu + cụm không bị "dọn" ⇒ `true`. Tắt ⇒ ẩn ngay. */
    fun onActive(on: Boolean, nowMs: Long): Step { active = on; return eval(nowMs) }

    fun onMedia(m: CastNowPlayingMedia?, nowMs: Long): Step { media = m; return eval(nowMs) }

    fun onPauseCheck(nowMs: Long): Step = eval(nowMs)

    private fun eval(nowMs: Long): Step {
        var check: Long? = null
        val m = media
        val text = m?.let { CastNowPlayingText.line(it.title, it.artist) }
        val target: CastNowPlayingContent? = when {
            !active || m == null || text == null -> { pausedAtMs = null; pausedText = null; null }
            m.playing -> { pausedAtMs = null; pausedText = null; CastNowPlayingContent(text, playing = true) }
            else -> {
                val since = pausedAtMs?.takeIf { pausedText == text } ?: nowMs.also {
                    pausedAtMs = it; pausedText = text; check = autoHideMs
                }
                if (nowMs - since >= autoHideMs) null else CastNowPlayingContent(text, playing = false)
            }
        }
        val changed = target != shown
        shown = target
        return Step(target, changed, check)
    }

    companion object {
        /** Dừng lâu hơn chừng này ⇒ gỡ dải (một lượt hẹn, không nhịp). */
        const val AUTO_HIDE_MS: Long = 10_000L
    }
}

/**
 * Chữ của dải — ĐẦY ĐỦ, KHÔNG cắt (owner 17/10: *"đừng cắt, cho chạy marquee all thôi"*): dài hơn khung thì `:app` cho chữ chạy
 * (marquee của `TextView`), vừa khung thì đứng yên. Tên video YouTube có thể rất dài [ĐO xe 17/10: phiên YouTube có đủ tên +
 * kênh]. Không bao giờ log chữ này (nội dung người dùng nghe/xem).
 */
object CastNowPlayingText {
    const val SEP = " — "

    /** `title — artist`, gọn khoảng trắng; nghệ sĩ trống/trùng tên bài ⇒ bỏ. Tên bài trống ⇒ `null` (không hiện). */
    fun line(title: String?, artist: String?): String? {
        val t = clean(title) ?: return null
        val a = clean(artist)?.takeIf { !it.equals(t, ignoreCase = true) }
        return if (a == null) t else t + SEP + a
    }

    /** Dấu đầu dải: ♪ khi phát, ‖ khi dừng. */
    fun glyph(playing: Boolean): String = if (playing) "♪" else "‖"

    /** Chuỗi ĐÚNG đặt vào view: dấu + một khoảng trắng + chữ đầy đủ. */
    fun display(c: CastNowPlayingContent): String = glyph(c.playing) + " " + c.text

    private fun clean(s: String?): String? = s?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Chỗ đặt — toạ độ của display cụm (logic, đã tính `wm size`). Khung = khung chiếu ([castFrame]) giao với dải nhìn thấy của đời
 * cụm (`ClusterProfile.band`, cỡ tham chiếu kéo theo tỉ lệ display); không có khung ⇒ trọn display.
 */
object CastNowPlayingPlace {

    /** Neo cửa sổ: [right]/[bottom] = gravity RIGHT/BOTTOM, [x]/[y] = khoảng cách tới mép đó (đúng nghĩa `WindowManager`). */
    data class Anchor(
        val right: Boolean, val bottom: Boolean, val x: Int, val y: Int, val maxWidth: Int, val textPx: Int,
        /** Canh giữa ngang theo DISPLAY (gravity CENTER_HORIZONTAL, [x] = 0) với bề ngang cố định [fixedWidth]. */
        val centered: Boolean = false,
        /** Bề ngang cửa sổ cố định (px); `null` = vừa chữ (tới [maxWidth]). */
        val fixedWidth: Int? = null,
    )

    /** Khung chiếu của phiên: FULL ⇒ khung đã ghim/đang áp; chia đôi ⇒ hợp hai ô; còn lại / không khung ⇒ `null` (trọn display). */
    fun castFrame(state: SimpleCastState?): CastBounds? = when (state) {
        is SimpleCastState.CastingFull -> (state.pinned?.bounds ?: state.displayConfig.bounds)
        is SimpleCastState.CastingSplit -> listOfNotNull(
            state.left?.let { it.pinned?.bounds ?: it.displayConfig.bounds },
            state.right?.let { it.pinned?.bounds ?: it.displayConfig.bounds },
        ).takeIf { it.isNotEmpty() }?.let { b ->
            CastBounds(b.minOf { it.left }, b.minOf { it.top }, b.maxOf { it.right }, b.maxOf { it.bottom })
        }
        else -> null
    }

    fun frame(displayW: Int, displayH: Int, cast: CastBounds?, band: ClusterBandSpec?): CastBounds {
        val full = CastBounds(0, 0, displayW, displayH)
        val base = cast?.let { intersect(it, full) } ?: full
        val seen = band?.let {
            CastBounds(it.left * displayW / it.refW, it.top * displayH / it.refH, it.right * displayW / it.refW, it.bottom * displayH / it.refH)
        } ?: return base
        return intersect(base, seen) ?: base
    }

    fun anchor(corner: CastNowPlayingCorner, frame: CastBounds, displayW: Int, displayH: Int): Anchor {
        val margin = (displayH * MARGIN_FRAC).toInt()
        val textPx = (displayH * TEXT_FRAC).toInt().coerceAtLeast(MIN_TEXT_PX)
        if (corner.center) {
            val w = (displayW * CENTER_WIDTH_FRAC).toInt().coerceAtLeast(1)
            return Anchor(right = false, bottom = false, x = 0, y = (frame.top + margin).coerceAtLeast(0),
                maxWidth = w, textPx = textPx, centered = true, fixedWidth = w)
        }
        val x = if (corner.right) displayW - frame.right + margin else frame.left + margin
        val y = if (corner.bottom) displayH - frame.bottom + margin else frame.top + margin
        return Anchor(
            right = corner.right, bottom = corner.bottom,
            x = x.coerceAtLeast(0), y = y.coerceAtLeast(0),
            maxWidth = (frame.width - 2 * margin).coerceAtLeast(1),
            textPx = (displayH * TEXT_FRAC).toInt().coerceAtLeast(MIN_TEXT_PX),
        )
    }

    private fun intersect(a: CastBounds, b: CastBounds): CastBounds? {
        val l = maxOf(a.left, b.left); val t = maxOf(a.top, b.top)
        val r = minOf(a.right, b.right); val btm = minOf(a.bottom, b.bottom)
        return if (l < r && t < btm) CastBounds(l, t, r, btm) else null
    }

    /** Cỡ chữ theo chiều cao display (720 ⇒ 30 px) — không theo DPI chiếu (người lái đổi DPI app, không đổi dải). */
    const val TEXT_FRAC = 0.042f
    /** Bề ngang dải ở [CastNowPlayingCorner.TOP_CENTER] = phần này của bề ngang màn cụm (owner 26/10: 1/3). */
    const val CENTER_WIDTH_FRAC = 1.0 / 3
    const val MARGIN_FRAC = 0.022f
    const val MIN_TEXT_PX = 14
}

/** Màu theo sáng/tối HỆ THỐNG (cụm theo `uiMode` hệ thống [ĐO 07/10]). Tương phản chữ/nền ≥ 7:1 cả hai chế độ (bài canh). */
object CastNowPlayingStyle {
    data class Colors(val background: Int, val text: Int)

    val NIGHT = Colors(background = 0xD9101418.toInt(), text = 0xFFF2F5F8.toInt())
    val DAY = Colors(background = 0xE6F4F7FA.toInt(), text = 0xFF1A232C.toInt())

    fun colors(systemNight: Boolean): Colors = if (systemNight) NIGHT else DAY
}
