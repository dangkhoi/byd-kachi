package com.byd.clusternav.modules.clustercast.simplified

import kotlin.math.roundToInt

/**
 * ═══ B1b · CLUSTER-RECT-OPTION (D1) — SỐ KM/H DO KACHI VẼ trên cụm Chữ nhật: luật THUẦN ════════════════════════════════════
 *
 * Ở theme2 FULL (Chữ nhật) cụm Seal MẤT số km/h gốc, chỉ còn chữ "m/h" lạc ở góc [ĐO xe 05/10 §4,
 * `cluster-rect-seal-2026-10-05.md` §3]. Owner chọn (D1, 05/10): Kachi tự vẽ số km/h. Một con số tốc độ SAI hoặc CŨ trên đồng
 * hồ còn tệ hơn không có ⇒ luật ở đây thiên về TẮT/"––": không bao giờ giữ số cũ, không bao giờ giả 0.
 *
 * Phần Android (cửa sổ, luồng đọc 4 Hz, vẽ) ở `:app` `ClusterSpeedReadoutOverlay`; tệp này chỉ quyết: hiện hay không
 * ([visible]), hiện chữ gì ([face]), màu gì ([palette]), ở đâu ([ClusterRectLayout.SPEED_BOX]).
 *
 * ## Khi nào HIỆN — đủ cả năm (thiếu một ⇒ gỡ cửa sổ)
 *  1. Có id cụm ĐÃ XÁC MINH (`liveDisplayId ≥ 1` — `ClusterDisplayResolver` đã loại display 0 và màn ảo của ô Kachi; lượt dò
 *     gần nhất hụt ⇒ id đã xác minh của PHIÊN — Pass 2 · cluster-r1-4, `speedReadoutInputs`). Không bao giờ display 1 gõ cứng
 *     (lỗi của `SpeedBadgeOverlay`, backlog riêng).
 *  2. Trạng thái ∈ {Idle, CastingFull app thường, CastingSplit}. Opening/Closing/Stopping/Off/Error ⇒ gỡ. CarPlay/Android
 *     Auto ⇒ gỡ (D5: hằng màn của chúng ở Chữ nhật [CHƯA BIẾT f]).
 *  3. Kiểu khung của phiên = Chữ nhật VÀ kiểu tin ∈ {Chữ nhật, chưa rõ} — chưa rõ vẫn hiện: thà trùng số với km/h gốc còn hơn
 *     mất tốc độ.
 *  4. Cấu hình màn là [DisplayConfig.NORMAL_DEFAULT] (`wm size 1920x720`, overscan 0 ⇒ toạ độ cụm 1:1 — hộp số mới đúng chỗ),
 *     và kích ĐO ĐƯỢC của display (nếu đo được) đúng 1920×720.
 *  5. Latch của cổng theme TẮT ([ClusterThemeGuard.latched]).
 *
 * ## Dữ liệu
 * `SpeedProvider.mpsOrNull()` = `BYDAutoSpeedDevice.getCurrentSpeed` — số ĐỒNG HỒ (cao hơn thực 5–8 % theo thiết kế), cùng
 * nghĩa với số km/h gốc của cụm [SUY]. ×3,6, làm tròn, kẹp 0..[MAX_KMH] ([kmh]). Đọc [TICK_MS] một lần (4 Hz) [SUY — tần số
 * HAL CHƯA BIẾT e]. Quá [STALE_MS] không có lần đọc tốt ⇒ "––" mờ. Giới hạn: HAL trả một giá trị cũ mà vẫn "hợp lệ" thì
 * không phát hiện được [CHƯA BIẾT]; Kachi chết thì cụm mất km/h tới khi tắt chiếu (owner chấp nhận, D1).
 */
object SpeedReadoutPolicy {

    /** Nhịp đọc + vẽ: 4 Hz. */
    const val TICK_MS: Long = 250L

    /** Quá ngần này không có lần đọc tốt ⇒ "––" mờ. */
    const val STALE_MS: Long = 1_000L

    /** Trần hiển thị (3 chữ số). */
    const val MAX_KMH: Int = 299

    /** Chữ hiện khi không có số tin được — hai gạch ngang (en dash), KHÔNG phải "0". */
    const val NO_VALUE: String = "––"

    /** Bo góc của viên số (px cụm). */
    const val CORNER_PX: Int = 24

    /** Cỡ số (px cụm, chữ số đều độ rộng) — [ĐOÁN thẩm mỹ] so với số gốc theme1 ~66 px; chỉnh trên xe. */
    const val DIGITS_PX: Int = 84

    /** Cỡ nhãn "km/h" (px cụm). */
    const val UNIT_PX: Int = 24

    /** Nhãn đơn vị — đơn vị đo quốc tế, cùng chữ ở mọi ngôn ngữ (cụm Seal VN in "km/h"). */
    const val UNIT: String = "km/h"

    /** Kích cụm mà [ClusterRectLayout] đo. */
    private const val W = ClusterRectLayout.WIDTH
    private const val H = ClusterRectLayout.HEIGHT

    /** Ảnh chụp đầu vào ([SimpleCastCoordinator.speedReadoutInputs]). */
    data class Inputs(
        val state: SimpleCastState,
        val session: CastSessionStyle?,
        val latched: Boolean,
        val liveDisplayId: Int,
    )

    /** Chữ + độ mờ của MỘT nhịp vẽ. */
    data class Face(val text: String, val dim: Boolean)

    /** Màu ARGB của viên số. */
    data class Palette(val background: Int, val digits: Int, val unit: Int)

    /**
     * Luật hiện (KDoc lớp, 5 điều). [measuredSize] = kích display ĐO ĐƯỢC ở `:app` (`Display.getRealSize`), `null` = chưa đo ⇒
     * chỉ dựa cấu hình phiên.
     */
    fun visible(inp: Inputs, measuredSize: Pair<Int, Int>? = null): Boolean {
        if (inp.liveDisplayId < 1 || inp.latched) return false
        val s = inp.session ?: return false
        if (s.frame != CastStyle.RECT) return false
        if (s.believed != BelievedStyle.RECT && s.believed != BelievedStyle.UNKNOWN) return false
        if (measuredSize != null && measuredSize != (W to H)) return false
        return when (val st = inp.state) {
            SimpleCastState.Idle -> true
            is SimpleCastState.CastingFull -> st.appType == AppType.NORMAL && oneToOne(st.displayConfig)
            is SimpleCastState.CastingSplit -> listOfNotNull(st.left, st.right).all { oneToOne(it.displayConfig) }
            else -> false
        }
    }

    /** Cấu hình màn cho toạ độ cụm 1:1: đúng `wm size`/overscan của [DisplayConfig.NORMAL_DEFAULT] (DPI không đổi toạ độ px). */
    fun oneToOne(c: DisplayConfig): Boolean =
        c.wmSize == DisplayConfig.NORMAL_DEFAULT.wmSize && c.overscan == DisplayConfig.NORMAL_DEFAULT.overscan

    /** m/s → km/h hiển thị: ×3,6, làm tròn, kẹp 0..[MAX_KMH]. `null`/NaN/vô cực ⇒ `null` (không đọc được — KHÔNG phải 0). */
    fun kmh(mps: Double?): Int? {
        if (mps == null || mps.isNaN() || mps.isInfinite()) return null
        return (mps * 3.6).roundToInt().coerceIn(0, MAX_KMH)
    }

    /**
     * Chữ của nhịp vẽ lúc [nowMs] (đồng hồ đơn điệu): số đọc tốt gần nhất nếu còn trong [STALE_MS]; còn lại "––" mờ. Đồng hồ
     * lùi (mốc tương lai) cũng là không tin được ⇒ "––".
     */
    fun face(lastGoodKmh: Int?, lastGoodAtMs: Long?, nowMs: Long): Face {
        if (lastGoodKmh == null || lastGoodAtMs == null) return Face(NO_VALUE, dim = true)
        val age = nowMs - lastGoodAtMs
        return if (age in 0..STALE_MS) Face(lastGoodKmh.toString(), dim = false) else Face(NO_VALUE, dim = true)
    }

    /** Quyết định của canh gác luồng chính ([watchdog]). */
    enum class Watch { KEEP, DIM, DETACH }

    /** Luồng đọc HAL không đẩy nhịp vẽ nào quá ngần này ⇒ coi là treo, GỠ cửa sổ (4 × [STALE_MS]). */
    const val WORKER_STALL_MS: Long = 4 * STALE_MS

    /**
     * Review 2.89 Pass 3 · cluster-r2-4 — CANH GÁC chạy trên LUỒNG CHÍNH mỗi [TICK_MS] khi cửa sổ đang gắn, tách khỏi luồng đọc HAL
     * (`SpeedProvider.mpsOrNull` là lời gọi binder phản chiếu — treo là luồng đó không bao giờ đẩy nhịp vẽ, số cũ đứng mãi ở độ sáng
     * đầy). [lastRenderAtMs] = lần CUỐI luồng đọc đẩy được một nhịp vẽ (ghi trên luồng chính), [lastGoodAtMs] = lần đọc tốt cuối
     * (đồng hồ đơn điệu, cùng [nowMs]):
     *  • luồng đọc im quá [WORKER_STALL_MS] (hoặc chưa từng đẩy, hoặc đồng hồ lùi) ⇒ [Watch.DETACH] — không giữ số cũ trên đồng hồ;
     *  • số tốt cuối quá [STALE_MS] (hoặc chưa có) ⇒ [Watch.DIM] ("––" mờ — cùng luật [face]);
     *  • còn lại ⇒ [Watch.KEEP].
     * Gỡ dựa vào nhịp VẼ chứ không dựa vào số tốt: HAL trả `null` nhanh (không treo) thì luồng đọc vẫn đẩy "––" — gỡ lúc đó là gắn/gỡ
     * lặp mỗi vài giây.
     */
    fun watchdog(lastGoodAtMs: Long?, lastRenderAtMs: Long?, nowMs: Long): Watch {
        val renderAge = lastRenderAtMs?.let { nowMs - it }
        if (renderAge == null || renderAge !in 0..WORKER_STALL_MS) return Watch.DETACH
        val goodAge = lastGoodAtMs?.let { nowMs - it }
        return if (goodAge != null && goodAge in 0..STALE_MS) Watch.KEEP else Watch.DIM
    }

    /**
     * Màu theo ngày/đêm của Kachi (`ThemeMode`): nền đen alpha 0,60 ngày / 0,72 đêm (viên số đè lên BẢN ĐỒ, không lên nền cụm
     * ⇒ nền tối cố định để luôn đủ tương phản); số trắng `#FFFFFF` ngày / `#DADADA` đêm (đỡ chói); nhãn = màu số alpha 70 %.
     */
    fun palette(night: Boolean): Palette {
        val bgAlpha = if (night) 0xB8 else 0x99          // 0,72 · 0,60
        val ink = if (night) 0xDADADA else 0xFFFFFF
        return Palette(background = bgAlpha shl 24, digits = (0xFF shl 24) or ink, unit = (0xB3 shl 24) or ink)
    }

    /** Màu số khi "––" (mờ): alpha 40 % của màu số. */
    fun dimmed(argb: Int): Int = (0x66 shl 24) or (argb and 0x00FFFFFF)
}
