package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.simplified.CastBounds
import com.byd.clusternav.modules.clustercast.simplified.CastGeometryGuard
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
 *
 * ## V-CLUSTER (2026-09-30) — màn nói giá trị của PHIÊN, không phải prefs của hồ sơ (VC-R9, sửa refute C5)
 * DPI/khung và tỉ lệ nay theo HỒ SƠ, nên ngay sau khi đổi hồ sơ giữa lúc chiếu thì prefs mang giá trị của hồ sơ mới
 * trong khi cụm vẫn hiện giá trị đã GHIM của phiên (`CastSessionPin.kt`). Bốn thanh −/+ và chip DPI phải đọc bản ghim
 * ([geometryBounds]/[geometryDensity]), dải kẹp phải theo tỉ lệ PHIÊN ([geometryBand]) — đọc prefs là vẽ một con số
 * không có trên cụm, và cú −/+ kế tiếp nhảy từ con số sai đó. Giá trị của hồ sơ chỉ hiện ở một dòng phụ, và chỉ khi
 * nó khác ([geometryProfileDiffers]).
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
    val split = (width * bandPct() / 100).coerceIn(1, (width - 1).coerceAtLeast(1))
    return when (target.side) {
        null -> 0 to width
        ClusterSlotSide.LEFT -> 0 to split
        ClusterSlotSide.RIGHT -> split to width
    }
}

/**
 * Khung hiện tại của ô — **bản GHIM của phiên thắng khung mặc định** (R6 của bản gốc: mở lại đúng chỗ người dùng đã
 * kéo lần trước — nay qua bản ghim, V-CLUSTER VC-R9), mặc định thì là cả dải của ô.
 */
fun ClusterNavBridge.geometryBounds(target: CastGeometryTarget): CastBounds = boundsOf(target, sessionConfig(target))

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
 * DPI đang áp cho ô — bản GHIM của phiên, lùi về 240 (`DisplayConfig.NORMAL_DEFAULT.density`).
 *
 * Bản gốc từng ghim nhãn ở "320" bất kể thực tế và owner (2026-08-12) báo là gây hiểu nhầm; chỗ này
 * lặp lại `CastGeometryEditor.densityIndexFor`: đọc số THẬT rồi mới vẽ nhãn.
 */
fun ClusterNavBridge.geometryDensity(target: CastGeometryTarget): Int = densityOf(sessionConfig(target))

/** DPI + khung mà HỒ SƠ đang dùng lưu cho một ô, dạng giống hệt thứ bốn thanh −/+ và chip DPI đang hiện. */
data class CastGeometryProfileValue(val density: Int, val bounds: CastBounds)

/**
 * V-CLUSTER · VC-R9 — giá trị HỒ SƠ đang dùng lưu cho ô [target] (dưới ô nhớ của tỉ lệ PHIÊN), **chỉ khi** nó khác giá
 * trị của phiên đang hiện; `null` = giống, không có gì để nói. Tầng Settings vẽ dòng *"Hồ sơ này: DPI … — áp dụng từ
 * lần chiếu sau"*.
 *
 * Hai phía so bằng CÙNG một phép dựng ([boundsOf]/[densityOf]) và cùng một mặc định cho "chưa lưu gì" (toàn cụm:
 * [DisplayConfig.NORMAL_DEFAULT], đúng thứ lượt chiếu kế dùng; một nửa: cả dải + 240) — nên chưa đổi hồ sơ thì không
 * bao giờ có dòng phụ giả (bản ghi `"reset"` và `"240"` cùng ra nhãn 240, y như chip đang vẽ).
 *
 * ⚠ Senior review Pass 2 — một NỬA mà tỉ lệ PHIÊN ≠ tỉ lệ HỒ SƠ ⇒ `null`: lần chia đôi kế của hồ sơ ghim ô nhớ của tỉ lệ
 * HỒ SƠ (`sessionLeftPercent` ← `splitRatioLeftPercent`), tức một khoá khác, một dải khác. In bản ghi của tỉ lệ PHIÊN
 * kèm *"áp dụng từ lần chiếu sau"* là nói sai điều lần chiếu sau sẽ làm (VC-R9). Chuyện "lần sau chia khác" đã có dòng
 * tỉ lệ *"Đang chia … · hồ sơ này …"* nói (`SettingsCastSection.rebuildSplit`).
 */
fun ClusterNavBridge.geometryProfileDiffers(target: CastGeometryTarget): CastGeometryProfileValue? {
    if (target.side != null && sessionSplitPct() != splitPct()) return null
    val fullDefault = DisplayConfig.NORMAL_DEFAULT.takeIf { target.side == null }
    val profile = savedConfig(target) ?: fullDefault
    val session = sessionConfig(target)
    val want = CastGeometryProfileValue(densityOf(profile), boundsOf(target, profile))
    return want.takeIf { it != CastGeometryProfileValue(densityOf(session), boundsOf(target, session)) }
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

/** Tỉ lệ cho dải/ô nhớ của một nửa: tỉ lệ CỦA PHIÊN khi đang chia đôi (VC-R6), không thì tỉ lệ hồ sơ lưu. */
private fun ClusterNavBridge.bandPct(): Int = sessionSplitPct() ?: splitPct()

/**
 * Bản ghi HỒ SƠ đang dùng lưu cho ô: FULL dùng khoá không hậu tố, split dùng khoá (nửa, tỉ lệ PHIÊN) — đúng ô nhớ mà
 * lượt chỉnh tay đang ghi vào (`CastSessionPin.resizeSlotBody`).
 */
private fun ClusterNavBridge.savedConfig(target: CastGeometryTarget): DisplayConfig? = runCatching {
    val profile = target.side?.let { CastProfile.of(it, bandPct()) } ?: CastProfile.FULL
    coordinator.prefs.displayConfigFor(target.pkg, profile)
}.getOrNull()

/**
 * Cấu hình của PHIÊN cho ô: bản ghim; chưa ghim gì ⇒ toàn cụm dùng cấu hình đang áp của phiên, một nửa thì `null`
 * (dải mặc định + 240). Chỉ nhận khi gói của ô khớp trạng thái (màn có thể giữ một [CastGeometryTarget] cũ).
 */
private fun ClusterNavBridge.sessionConfig(target: CastGeometryTarget): DisplayConfig? = when (val s = castState()) {
    is SimpleCastState.CastingFull ->
        if (target.side == null && s.targetPkg == target.pkg) s.pinned ?: s.displayConfig else null
    is SimpleCastState.CastingSplit ->
        (if (target.side == ClusterSlotSide.LEFT) s.left else s.right)?.takeIf { it.pkg == target.pkg }?.pinned
    else -> null
}

private fun ClusterNavBridge.boundsOf(target: CastGeometryTarget, config: DisplayConfig?): CastBounds {
    val (_, height) = geometryFrame()
    val (bandMin, bandMax) = geometryBand(target)
    return config?.bounds ?: CastBounds(bandMin, 0, bandMax, height)
}

private fun ClusterNavBridge.densityOf(config: DisplayConfig?): Int {
    val dpi = config?.density?.toIntOrNull() ?: return DEFAULT_DENSITY
    return if (dpi in geometryDensityOptions()) dpi else DEFAULT_DENSITY
}

private fun ClusterNavBridge.clampToBand(target: CastGeometryTarget, bounds: CastBounds): CastBounds {
    val (_, height) = geometryFrame()
    val (bandMin, bandMax) = geometryBand(target)
    return CastGeometryGuard.clampBounds(bounds, bandMin, bandMax, height)
}

// V-CLUSTER (2026-09-30) — phép kẹp DỜI sang `:core` `CastGeometryGuard.clampBounds` (thân + bài test dời nguyên): tầng
// UI này và lượt áp hình học của phiên chiếu phải dùng CHUNG một phép kẹp (spec §11.4.4, DRY). Lý do `MIN_SPAN` tự co
// lại (dải tỉ lệ 10 %, `wmSize` lạ) nay nằm ở KDoc bên đó.

/** Bước của một lần bấm −/+ (px trên cụm). Đủ thấy khác sau một cú chạm, đủ nhỏ để canh được mép. */
internal const val GEOMETRY_STEP_PX = 20

/** `DisplayConfig.NORMAL_DEFAULT.density` = "240". */
private const val DEFAULT_DENSITY = 240

private const val FALLBACK_WIDTH = 1920
private const val FALLBACK_HEIGHT = 720
