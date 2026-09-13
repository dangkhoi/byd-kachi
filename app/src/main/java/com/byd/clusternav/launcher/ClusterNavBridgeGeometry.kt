package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.simplified.CastBounds
import com.byd.clusternav.modules.clustercast.simplified.CastProfile
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide
import com.byd.clusternav.modules.clustercast.simplified.DisplayConfig
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState

/**
 * ═══ Nửa "KHUNG & DPI khi đang chiếu" của [ClusterNavBridge] ════════════════════════════════════
 *
 * Spec `docs/specs/kachi-remove-legacy-screen.html` **R2(a)**: bộ chỉnh khung/DPI của màn cũ
 * (`CastGeometryEditor`, đã gỡ 2026-09-13) chuyển vào Cài đặt › Chiếu cụm. Phần **thuần** (đọc/ghi
 * `displayConfig` theo *gói + hồ sơ*, dải DPI, áp khung sống, đặt lại) ở đây; phần hình (thanh −/+,
 * chip DPI) ở `SettingsSectionsCast`.
 *
 * ## Vì sao KHÔNG bê nguyên `CastGeometryEditor` sang
 * Lớp cũ là một **bộ dựng View**: nó giữ `Activity`, tự `findViewById(R.id.cast_geometry_container)`,
 * tự dựng `CastResizeView` rồi *kéo-thả* để chọn khung. Cầu thì không giữ giao diện (KDoc
 * [ClusterNavBridge]) — nên cái chuyển sang đây là **quyết định**, không phải hình:
 *  - ô nào đang chỉnh được (`CastingFull` app thường · hai nửa khi `CastingSplit`);
 *  - khung hiện tại của ô đó (hồ sơ đã lưu thắng khung mặc định — R6 của bản gốc);
 *  - dải DPI 320/240/160 và **đường ghi đúng** cho từng trạng thái (xem [setGeometryDensity]).
 *
 * ## Một ô = (gói, nửa nào) — không phải chỉ gói
 * `simple_cast_prefs` lưu `displayConfig` theo **gói + [CastProfile]**, mà hồ sơ split còn mang cả tỉ
 * lệ chia ([CastProfile.of]). Cùng một app ở nửa trái tỉ lệ 30 và nửa trái tỉ lệ 50 là **hai** ô nhớ
 * khác nhau — đó là chủ ý của bản gốc (đổi tỉ lệ thì khung cũ không còn nghĩa), nên [CastGeometryTarget]
 * mang theo `side` và mọi hàm tự tra tỉ lệ ĐANG chạy thay vì nhận nó từ giao diện.
 */

/**
 * Một ô chỉnh được: [side] `null` ⇒ đang chiếu TOÀN cụm; còn lại là một nửa khi đang chia đôi.
 *
 * [pkg] đi kèm vì khoá lưu là (gói, hồ sơ) — hai nửa hai app khác nhau thì hai khung khác nhau.
 */
data class CastGeometryTarget(val pkg: String, val side: ClusterSlotSide?)

/** Dải DPI của màn cũ, nguyên thứ tự `CastGeometryEditor.densityCycle` (320 → 240 → 160). */
fun ClusterNavBridge.geometryDensityOptions(): List<Int> = listOf(320, 240, 160)

/**
 * Các ô đang chỉnh được — **rỗng nghĩa là không có gì để hiện** (chưa chiếu, hoặc đang chiếu CarPlay /
 * Android Auto).
 *
 * Hai ca rỗng có lý do khác nhau nhưng cùng một kết luận: CP/AA là `appType.isProtected` — resize task
 * của chúng đã được chứng minh là **không ăn** (`CastGeometryEditor.updateVisibility` ẩn hẳn bộ chỉnh),
 * nên hiện bốn thanh −/+ chỉ để chúng không làm gì là tệ hơn không hiện.
 */
fun ClusterNavBridge.geometryTargets(): List<CastGeometryTarget> = when (val state = castState()) {
    is SimpleCastState.CastingFull ->
        if (state.appType.isResizable) listOf(CastGeometryTarget(state.targetPkg, null)) else emptyList()
    is SimpleCastState.CastingSplit -> listOfNotNull(
        state.left?.let { CastGeometryTarget(it.pkg, ClusterSlotSide.LEFT) },
        state.right?.let { CastGeometryTarget(it.pkg, ClusterSlotSide.RIGHT) },
    )
    else -> emptyList()
}

/**
 * Khung cụm (rộng × cao) đọc từ `wmSize` của cấu hình ĐANG áp — `CastGeometryEditor` cũng đọc từ đó
 * thay vì hằng 1920×720, vì cụm của mỗi đời xe một khác (CLAUDE.md §7).
 *
 * Lùi về [DisplayConfig.NORMAL_DEFAULT] khi chưa chiếu: bốn thanh −/+ luôn cần một biên để kẹp, và
 * biên sai còn hơn biên bằng 0 (bằng 0 thì mọi giá trị đều bị kẹp về 0 — người dùng bấm không lên số).
 */
fun ClusterNavBridge.geometryFrame(): Pair<Int, Int> {
    val wmSize = when (val state = castState()) {
        is SimpleCastState.CastingFull -> state.displayConfig.wmSize
        is SimpleCastState.CastingSplit -> (state.left ?: state.right)?.displayConfig?.wmSize
        else -> null
    } ?: DisplayConfig.NORMAL_DEFAULT.wmSize
    val parts = wmSize.split("x")
    val width = parts.getOrNull(0)?.toIntOrNull() ?: FALLBACK_WIDTH
    val height = parts.getOrNull(1)?.toIntOrNull() ?: FALLBACK_HEIGHT
    return width to height
}

/**
 * Dải X mà ô [target] được phép chiếm: FULL ⇒ cả khung; split ⇒ đúng nửa của nó theo tỉ lệ đang chạy.
 *
 * Lặp lại `CastGeometryEditor.setupSplitResizeControls` (`resizeView.setSlotBand(bandMinX, bandMaxX)`):
 * kéo ô trái sang phần của ô phải thì hai app chồng nhau trên cụm — lỗi hình mà người lái phải dừng xe
 * mới sửa được.
 */
fun ClusterNavBridge.geometryBand(target: CastGeometryTarget): Pair<Int, Int> {
    val (width, _) = geometryFrame()
    val split = (width * splitPct() / 100).coerceIn(1, (width - 1).coerceAtLeast(1))
    return when (target.side) {
        null -> 0 to width
        ClusterSlotSide.LEFT -> 0 to split
        ClusterSlotSide.RIGHT -> split to width
    }
}

/**
 * Khung hiện tại của ô — **hồ sơ đã lưu thắng khung mặc định** (R6 của bản gốc: mở lại đúng chỗ người
 * dùng đã kéo lần trước), mặc định thì là cả dải của ô.
 */
fun ClusterNavBridge.geometryBounds(target: CastGeometryTarget): CastBounds {
    val (_, height) = geometryFrame()
    val (bandMin, bandMax) = geometryBand(target)
    val saved = savedConfig(target)?.bounds
        ?: (castState() as? SimpleCastState.CastingFull)?.displayConfig?.bounds?.takeIf { target.side == null }
    return saved ?: CastBounds(bandMin, 0, bandMax, height)
}

/**
 * Áp khung MỚI cho ô — hai đường khác nhau, đúng như `CastGeometryEditor` gọi:
 * FULL → `resizeActiveTarget`, một nửa → `resizeActiveSlot(side, …)`.
 *
 * Giá trị được **kẹp** vào dải của ô và vào chiều cao cụm trước khi gửi: thanh −/+ có thể bấm quá biên,
 * và một `am task resize` với khung ngoài màn hình là task biến mất khỏi cụm (CLAUDE.md §4 — lệnh đổi
 * state hệ thống phải có phạm vi tường minh).
 */
fun ClusterNavBridge.setGeometryBounds(target: CastGeometryTarget, bounds: CastBounds) {
    val clamped = clampToBand(target, bounds)
    when (target.side) {
        null -> coordinator.resizeActiveTarget(clamped.left, clamped.top, clamped.right, clamped.bottom)
        else -> coordinator.resizeActiveSlot(target.side, clamped.left, clamped.top, clamped.right, clamped.bottom)
    }
}

/** Đưa ô về khung mặc định của nó (cả dải, cao hết cụm) — nút "Đặt lại" của màn cũ. */
fun ClusterNavBridge.resetGeometry(target: CastGeometryTarget) {
    val (_, height) = geometryFrame()
    val (bandMin, bandMax) = geometryBand(target)
    setGeometryBounds(target, CastBounds(bandMin, 0, bandMax, height))
}

/**
 * DPI đang áp cho ô — hồ sơ đã lưu, lùi về 240 (`DisplayConfig.NORMAL_DEFAULT.density`).
 *
 * Bản gốc từng ghim nhãn ở "320" bất kể thực tế và owner (2026-08-12) báo là gây hiểu nhầm; chỗ này
 * lặp lại `CastGeometryEditor.densityIndexFor`: đọc số THẬT rồi mới vẽ nhãn.
 */
fun ClusterNavBridge.geometryDensity(target: CastGeometryTarget): Int {
    val saved = savedConfig(target)?.density
        ?: (castState() as? SimpleCastState.CastingFull)?.displayConfig?.density?.takeIf { target.side == null }
    val dpi = saved?.toIntOrNull() ?: return DEFAULT_DENSITY
    return if (dpi in geometryDensityOptions()) dpi else DEFAULT_DENSITY
}

/**
 * Đổi DPI — **một** điều khiển cho cả cụm, vì trên Android 10 `wm density` là display-global (D4 của
 * bản gốc). Hai đường ghi khác nhau và chọn nhầm là mất trắng:
 *  - đang chia đôi ⇒ [setDensitySplit][com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator.setDensitySplit]
 *    (lưu dưới hồ sơ theo tỉ lệ của TỪNG ô đang có);
 *  - còn lại ⇒ `setDensity` — bản FULL-only, và nó **no-op** khi đang split (R4/#5 của bản gốc).
 */
fun ClusterNavBridge.setGeometryDensity(dpi: Int) {
    if (castState() is SimpleCastState.CastingSplit) coordinator.setDensitySplit(dpi) else coordinator.setDensity(dpi)
}

// ── Nội bộ ──────────────────────────────────────────────────────────────────────────────────────

/** Hồ sơ lưu của ô: FULL dùng khoá không hậu tố, split dùng khoá (nửa, tỉ lệ đang chạy). */
private fun ClusterNavBridge.savedConfig(target: CastGeometryTarget): DisplayConfig? = runCatching {
    val profile = target.side?.let { CastProfile.of(it, splitPct()) } ?: CastProfile.FULL
    coordinator.prefs.displayConfigFor(target.pkg, profile)
}.getOrNull()

private fun ClusterNavBridge.clampToBand(target: CastGeometryTarget, bounds: CastBounds): CastBounds {
    val (_, height) = geometryFrame()
    val (bandMin, bandMax) = geometryBand(target)
    return clampBounds(bounds, bandMin, bandMax, height)
}

/**
 * Kẹp [bounds] vào dải `[bandMin, bandMax]` × `[0, frameHeight]`, mỗi cạnh ít nhất [MIN_SPAN] — **hàm thuần**
 * (không đọc gì của cầu) để còn test off-device được (CLAUDE.md §10).
 *
 * ## Vì sao [MIN_SPAN] phải tự co lại, không được dùng thẳng
 * `coerceIn(min, max)` **NÉM** `IllegalArgumentException` khi `min > max` — nó không kẹp, nó nổ. Bản đầu viết
 * `coerceIn(bandMin, bandMax - MIN_SPAN)`, tức chỉ cần dải hẹp hơn 80px là màn *Cài đặt › Chiếu cụm* văng ngay
 * lúc mở. Dải hẹp hơn 80px KHÔNG phải chuyện giả tưởng: tỉ lệ chia thấp nhất là **10%**
 * ([CastProfile.SPLIT_PERCENTS] = 10..90), nên một cụm rộng < 800px cho nửa trái < 80px; và `wmSize` là chuỗi
 * ĐỌC TỪ PREFS của cụm từng đời xe (CLAUDE.md §7 — không được hardcode 1920×720), một giá trị lạ như `"720x0"`
 * đưa `frameHeight` về 0.
 *
 * Cạnh tối thiểu vì thế co theo dải thật: dải hẹp thì ô chỉ nhỏ đi, chứ màn không được chết.
 */
internal fun clampBounds(bounds: CastBounds, bandMin: Int, bandMax: Int, frameHeight: Int): CastBounds {
    val hi = bandMax.coerceAtLeast(bandMin)
    val height = frameHeight.coerceAtLeast(0)
    val spanX = MIN_SPAN.coerceAtMost(hi - bandMin)
    val spanY = MIN_SPAN.coerceAtMost(height)
    val left = bounds.left.coerceIn(bandMin, hi - spanX)
    val right = bounds.right.coerceIn(left + spanX, hi)
    val top = bounds.top.coerceIn(0, height - spanY)
    val bottom = bounds.bottom.coerceIn(top + spanY, height)
    return CastBounds(left, top, right, bottom)
}

/** Bước của một lần bấm −/+ (px trên cụm). Đủ thấy khác sau một cú chạm, đủ nhỏ để canh được mép. */
internal const val GEOMETRY_STEP_PX = 20

/** Cạnh nhỏ nhất còn nhìn thấy được — dưới mức này thì app coi như biến mất khỏi cụm. */
private const val MIN_SPAN = 80

/** `DisplayConfig.NORMAL_DEFAULT.density` = "240". */
private const val DEFAULT_DENSITY = 240

private const val FALLBACK_WIDTH = 1920
private const val FALLBACK_HEIGHT = 720
