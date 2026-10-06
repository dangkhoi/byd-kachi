package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ B1b · CLUSTER-RECT-OPTION — chỗ nối KIỂU CỤM của phiên vào SimpleCastCoordinator ══════════════════════════════════
 *
 * Hàm mở rộng cùng package (khuôn `CastSessionPin.kt` / `SimpleCastCoordinatorOps.kt`) vì `SimpleCastCoordinator.kt` đã sát trần
 * 500 dòng. Không chứa chuỗi lệnh shell nào: lệnh khung vẫn ở `CastGeometryController` (`applyPinned` · `verifyFrame`).
 *
 * Nguồn sự thật DUY NHẤT của kiểu phiên là [ProjectionManager.session] — ghim MỘT lần khi mở chiếu thành công, xoá khi đóng.
 * Lựa chọn của hồ sơ (`cast_style`) chỉ được đọc ở lượt mở (`desiredStyleOnce`); đổi giữa phiên = 0 lệnh (R5 · mẫu VC-R6).
 */

/** Kiểu khung của phiên đang mở; không có phiên ⇒ Bo tròn (đường cũ). */
internal val SimpleCastCoordinator.frameStyle: CastStyle
    get() = projection.session?.frame ?: CastStyle.CURVED

/** Kiểu cụm của phiên chiếu đang mở (`null` = không có phiên) — cho màn Cài đặt ở `:app` (chỉ đọc). */
val SimpleCastCoordinator.castSession: CastSessionStyle?
    get() = projection.session

/**
 * Áp bản ghim của một ô sau lượt đặt (hoặc repin). Bo tròn: đúng [CastGeometryController.applyPinned] như trước. Chữ nhật:
 * thêm bước ĐỌC LẠI khung từ `am stack list`, lệch thì thử lại MỘT lần rồi log ([CastGeometryController.verifyFrame], F4b).
 */
internal fun SimpleCastCoordinator.applySessionPin(pkg: String, pinned: DisplayConfig?) {
    val asked = geometry.applyPinned(pkg, pinned)
    if (asked != null && frameStyle == CastStyle.RECT) geometry.verifyFrame(pkg, asked)
}

/**
 * 2.90 · R1 — nhãn app bóng nổi đã chặn lượt đổi theme GẦN NHẤT ([ClusterThemeGuard.lastBlockers]); rỗng = không bị chặn. Cho
 * Cài đặt / Chẩn đoán ở `:app` nói "tắt bóng … rồi Áp ngay" (chỉ đọc).
 */
val SimpleCastCoordinator.themeBlockers: List<String>
    get() = themeGuard.lastBlockers

/**
 * 2.90 · R9 — lượt đổi theme gần nhất đã DỌN cụm (`VM_BUBBLE_VIS show=false`) mà bóng nổi vẫn còn ⇒ bản mod chưa hỗ trợ ẩn bóng
 * ([ClusterThemeGuard.lastBubbleOldMod]). Cài đặt đổi câu thành "bản mod VietMap cũ … tắt VietMap rồi Áp ngay" (chỉ đọc).
 */
val SimpleCastCoordinator.themeBubbleOldMod: Boolean
    get() = themeGuard.lastBubbleOldMod

/**
 * Review 2.89 Pass 2 · whole-r1-5 — lượt mở GẦN NHẤT bị cổng theme DỪNG ([ProjectionManager.abortedOn]) và khoảng 15 s giữa hai
 * lần đổi theme còn bao nhiêu ms ([ClusterThemeGuard.remainingGapMs] — mốc muộn hơn giữa sổ bền và RAM); `null` = không bị
 * khoảng này chặn. Cho vòng thử lại tự mở chiếu lúc nổ máy (`BubbleAutostart`, `:app`): chờ hết khoảng rồi mới thử, không đốt lượt thử
 * trong lúc cổng chắc chắn còn nói `TOO_SOON` [SUY đọc mã: 5 lượt × 3 s ≈ khoảng 15 s]. Chỉ ĐỌC (không shell, không ghi).
 */
fun SimpleCastCoordinator.themeGapRetryMs(): Long? =
    if (projection.abortedOn != null) themeGuard.remainingGapMs()?.takeIf { it > 0 } else null
