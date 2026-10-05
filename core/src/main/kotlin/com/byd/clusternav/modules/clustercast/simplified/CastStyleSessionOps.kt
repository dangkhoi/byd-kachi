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

/** Kiểu cụm của phiên chiếu đang mở (`null` = không có phiên) — cho màn Cài đặt và lớp km/h ở `:app` (chỉ đọc). */
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
 * Ảnh chụp đầu vào của [SpeedReadoutPolicy] — đọc trường SỐNG của coordinator (không shell, không prefs): trạng thái, kiểu
 * phiên, latch của cổng theme, id cụm ĐÃ XÁC MINH ([SimpleCastCoordinator.liveDisplayId], `-1` = chưa) — lớp km/h ở `:app`
 * chỉ được gắn lên ĐÚNG display này, không bao giờ display 1/0 hay màn ảo của ô.
 *
 * Review 2.89 Pass 2 · cluster-r1-4: lượt dò gần nhất hụt (`liveDisplayId = -1` — một lần `dumpsys` chớp lúc chiếu ô) KHÔNG tắt
 * số km/h cả phiên: rơi về id đã xác minh của PHIÊN ([ProjectionManager.sessionDisplayId], xoá cùng phiên). Id sống mới hơn
 * (≥ 1) luôn thắng. Lệnh đặt / `wm -d` vẫn chỉ theo `liveDisplayId` (R1).
 */
fun SimpleCastCoordinator.speedReadoutInputs(): SpeedReadoutPolicy.Inputs =
    SpeedReadoutPolicy.Inputs(
        state = state,
        session = projection.session,
        latched = themeGuard.latched,
        liveDisplayId = liveDisplayId.takeIf { it >= 1 } ?: projection.sessionDisplayId,
    )

/**
 * Review 2.89 Pass 2 · whole-r1-5 — lượt mở GẦN NHẤT bị cổng theme DỪNG ([ProjectionManager.abortedOn]) và khoảng 15 s giữa hai
 * lần đổi theme còn bao nhiêu ms ([ClusterThemeGuard.remainingGapMs] — mốc muộn hơn giữa sổ bền và RAM); `null` = không bị
 * khoảng này chặn. Cho vòng thử lại tự mở chiếu lúc nổ máy (`BubbleAutostart`, `:app`): chờ hết khoảng rồi mới thử, không đốt lượt thử
 * trong lúc cổng chắc chắn còn nói `TOO_SOON` [SUY đọc mã: 5 lượt × 3 s ≈ khoảng 15 s]. Chỉ ĐỌC (không shell, không ghi).
 */
fun SimpleCastCoordinator.themeGapRetryMs(): Long? =
    if (projection.abortedOn != null) themeGuard.remainingGapMs()?.takeIf { it > 0 } else null
