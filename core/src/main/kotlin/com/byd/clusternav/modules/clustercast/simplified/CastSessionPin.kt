package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ V-CLUSTER · VC-R6 — GHIM hình học của PHIÊN chiếu (sửa refute C2 · B6) ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §11.4.6. Owner 2026-09-30: *"Phần cụm lưu hết thành profile nhé"*
 * ⇒ DPI/khung từng app (`config_*`) và tỉ lệ chia đôi nay theo HỒ SƠ, tức prefs sống đổi ngay khi người lái chạm chip hồ
 * sơ. Cụm thì không được đổi theo cú chạm đó (VC-R5). Tệp này là chỗ hai điều ấy gặp nhau.
 *
 * ## Bệnh nó chữa [ĐO code b5c0e87]
 *  • **B6** — vòng 2 s `FloatingBubbleService` → `repinEscapedCastApps` → đọc `prefs.splitRatioLeftPercent()` và
 *    `applySavedProfile` đọc lại prefs ⇒ đổi hồ sơ giữa lúc chiếu rồi một app bị kéo khỏi cụm là `am task resize` +
 *    `wm density` của hồ sơ MỚI tự nổ lên cụm, không ai bấm.
 *  • **C2** — `CastingSplit` không mang tỉ lệ ⇒ −/+ (dải kẹp + khoá lưu), chip DPI, ô thứ hai và repin đều đọc tỉ lệ
 *    của hồ sơ mới trong khi cụm vẫn chia theo tỉ lệ cũ ⇒ lưu nhầm ô nhớ, hai nửa chồng nhau hoặc hở.
 *
 * ## Luật (CLAUDE.md §5 — quyết bằng sự thật của phiên, không bằng cờ/prefs sống)
 *  1. **Bắt đầu phiên** (ý định chiếu của người lái) đọc hồ sơ ĐANG DÙNG **một lần**: [pinFull], [pinSlot],
 *     [sessionLeftPercent]. Kết quả nằm trong trạng thái (`CastingFull.pinned` · `SlotState.pinned` ·
 *     `CastingSplit.leftPercent`).
 *  2. **Áp tự động** (repin) chỉ dùng bản ghim — `CastGeometryController.applyPinned` không đọc prefs.
 *  3. **Chỉnh tay** (−/+, chip DPI, chip tỉ lệ) chạy lệnh như hôm nay, lưu cho hồ sơ ĐANG DÙNG dưới ô nhớ của tỉ lệ
 *     PHIÊN, rồi cập nhật **đúng trường vừa áp** của bản ghim ([basePin]). Không lấy nguyên bản ghi vừa lưu làm bản
 *     ghim: sau khi đổi hồ sơ giữa phiên, bản ghi đó mang DPI của hồ sơ mới trong khi cụm vẫn ở DPI đã ghim — lấy nó là
 *     để lượt repin kế tiếp đổi DPI của cụm mà không ai chạm vào chip DPI.
 *
 * Thân hàm là hàm mở rộng `internal` cùng package (khuôn `SimpleCastCoordinatorOps/Intents.kt`) vì trần 500 dòng của
 * `SimpleCastCoordinator.kt`; lớp đó chỉ còn lời gọi `executor.submit { … }`. Tệp này KHÔNG chứa chuỗi lệnh shell nào —
 * mọi lệnh vẫn ở `CastGeometryController`/`CastDensityControl` (bài canh `PersistentWindowStateWriterGuardTest`).
 */

/** Một lượt đọc hồ sơ cho phiên FULL: cấu hình áp lúc bắt đầu ([config]) + bản ghim ([pinned], `null` = chưa lưu gì). */
internal class FullPin(val config: DisplayConfig, val pinned: DisplayConfig?)

/**
 * Đọc hồ sơ ĐANG DÙNG **một lần** cho phiên FULL của [pkg]. CP/AA dùng hằng đã đo và không có gì để ghim (resize của
 * chúng không ăn — `ClusterNavBridgeGeometry.geometryTargets`). NORMAL: bản ghi FULL đã lưu thắng [DisplayConfig.NORMAL_DEFAULT]
 * — đúng phép `DisplayConfigurator.resolveConfig` cũ, nay chỉ một lượt đọc thay vì hai (resolve + applySavedProfile).
 */
internal fun SimpleCastCoordinator.pinFull(pkg: String, appType: AppType): FullPin {
    if (appType != AppType.NORMAL) return FullPin(DisplayConfig.forAppType(appType), null)
    val saved = prefs.displayConfigFor(pkg, CastProfile.FULL)
    logPin(pkg, CastProfile.FULL, saved)
    return FullPin(saved ?: DisplayConfig.NORMAL_DEFAULT, saved)
}

/**
 * Tỉ lệ nửa trái cho một lượt chiếu ô: đang chia đôi ⇒ tỉ lệ CỦA PHIÊN (mặt thứ hai của C2 — ô thứ hai không đọc lại
 * prefs); chưa chia ⇒ đọc hồ sơ đang dùng **một lần**, chuẩn hoá về một trong `CastProfile.SPLIT_PERCENTS`.
 */
internal fun SimpleCastCoordinator.sessionLeftPercent(current: SimpleCastState): Int =
    (current as? SimpleCastState.CastingSplit)?.leftPercent
        ?: CastProfile.normalizePercent(prefs.splitRatioLeftPercent())

/** Bản ghi đã lưu của ô ([side] × [leftPercent] của phiên) — đọc **một lần** lúc ô bắt đầu. */
internal fun SimpleCastCoordinator.pinSlot(pkg: String, side: ClusterSlotSide, leftPercent: Int): DisplayConfig? {
    val profile = CastProfile.of(side, leftPercent)
    return prefs.displayConfigFor(pkg, profile).also { logPin(pkg, profile, it) }
}

/**
 * Một dòng log lúc ghim — đọc bằng logcat/`ClusterDiag` là biết phiên đang giữ DPI/khung nào mà không phải mò UI
 * (CLAUDE.md §11, §15). `profile` là ô nhớ hình học (`FULL` / `L30` / `R30`), mang luôn tỉ lệ.
 */
private fun SimpleCastCoordinator.logPin(pkg: String, profile: CastProfile, pinned: DisplayConfig?) {
    log("pin: pkg=$pkg profile=${profile.key} density=${pinned?.density ?: "-"} bounds=${pinned?.bounds ?: "-"} pct=${profile.percent ?: "-"}")
}

/**
 * Nền để cập nhật MỘT trường của bản ghim sau một lượt chỉnh tay: bản ghim nếu có; chưa có ⇒ cấu hình đang áp của phiên
 * nhưng **không khung** — DPI của nó là DPI thật đang trên cụm (FULL: `DisplayConfigurator.apply` vừa đặt), nên repin sau
 * đó không đổi DPI của cụm.
 */
private fun basePin(pinned: DisplayConfig?, applied: DisplayConfig): DisplayConfig = pinned ?: applied.copy(bounds = null)

/**
 * Nền cho một ô chưa có bản ghim: DPI `reset` ⇒ repin KHÔNG chạm DPI (DPI là của cả VD, do ô đầu tiên đặt lúc chia) —
 * chỉ khung được áp lại. `wmSize`/`overscan` chép từ cấu hình của ô để bản ghi qua được chốt `isShellSafe`.
 */
private fun slotBase(slot: SlotState): DisplayConfig =
    slot.pinned ?: slot.displayConfig.copy(density = CastGeometryGuard.DENSITY_RESET, bounds = null)

// ─── Chỉnh TƯỜNG MINH — chạy lệnh như hôm nay, rồi cập nhật bản ghim bằng đúng giá trị vừa áp ──────────────────────

/** Thân của `resizeActiveTarget` (−/+ toàn cụm). */
internal fun SimpleCastCoordinator.resizeFullBody(left: Int, top: Int, right: Int, bottom: Int) {
    val current = state as? SimpleCastState.CastingFull ?: return
    if (!current.appType.isResizable || right <= left || bottom <= top) {
        log("resizeActiveTarget: skip (state/bounds invalid) [$left,$top,$right,$bottom]")
        return
    }
    if (!geometry.resizeFull(current.targetPkg, left, top, right, bottom)) return
    val bounds = CastBounds(left, top, right, bottom)
    replaceState(current, current.copy(pinned = basePin(current.pinned, current.displayConfig).copy(bounds = bounds)))
}

/** Thân của `resizeActiveSlot` (−/+ một nửa) — ô nhớ theo tỉ lệ CỦA PHIÊN (C2), không theo prefs. */
internal fun SimpleCastCoordinator.resizeSlotBody(side: ClusterSlotSide, left: Int, top: Int, right: Int, bottom: Int) {
    val current = state as? SimpleCastState.CastingSplit ?: return
    val slot = (if (side == ClusterSlotSide.LEFT) current.left else current.right) ?: return
    if (right <= left || bottom <= top) {
        log("resizeActiveSlot: skip (bounds invalid) [$left,$top,$right,$bottom]")
        return
    }
    if (!geometry.resizeSlot(slot.pkg, CastProfile.of(side, current.leftPercent), left, top, right, bottom)) return
    val updated = slot.copy(pinned = slotBase(slot).copy(bounds = CastBounds(left, top, right, bottom)))
    replaceState(current, if (side == ClusterSlotSide.LEFT) current.copy(left = updated) else current.copy(right = updated))
}

/**
 * Thân của `applySplitRatioLive` — lượt DUY NHẤT được đổi tỉ lệ của phiên. Lưu tỉ lệ cho hồ sơ đang dùng (như hôm nay),
 * resize tại chỗ cả hai ô, rồi phiên nhận tỉ lệ mới + khung mới của từng ô.
 *
 * Ô resize HỎNG giữ bản ghim nhưng **bỏ khung**: khung cũ thuộc tỉ lệ cũ, để lại thì repin sau đó (đặt ô theo tỉ lệ mới
 * rồi áp khung cũ) làm hai nửa chồng nhau — đúng lỗi C2 mà tệp này chữa.
 */
internal fun SimpleCastCoordinator.applySplitRatioLiveBody(leftPercent: Int) {
    // Validate the incoming ratio to one of the 9 supported buckets (10..90). The UI only ever
    // sends CastProfile.SPLIT_PERCENTS values, but this is a public entry point: an out-of-set
    // percent would (a) persist a bad ratio that corrupts the NEXT split cast's fitToCluster
    // bounds, (b) drive a degenerate `am task resize` (resizeSlot has no bounds guard), and
    // (c) file per-slot bounds under a normalized profile key (CastProfile.of below) that no
    // longer matches the geometry. Clamp to the R3 default (50) so all three stay consistent.
    val pct = CastProfile.normalizePercent(leftPercent)
    prefs.setSplitRatioLeftPercent(pct)
    val current = state as? SimpleCastState.CastingSplit ?: return
    val (width, height) = mover.queryDisplaySize(displayId) ?: (1920 to 720)
    val boundary = width * pct / 100
    fun reslot(slot: SlotState, side: ClusterSlotSide, b: CastBounds): SlotState =
        if (geometry.resizeSlot(slot.pkg, CastProfile.of(side, pct), b.left, b.top, b.right, b.bottom)) {
            slot.copy(pinned = slotBase(slot).copy(bounds = b))
        } else {
            slot.copy(pinned = slot.pinned?.copy(bounds = null))
        }
    val left = current.left?.let { reslot(it, ClusterSlotSide.LEFT, CastBounds(0, 0, boundary, height)) }
    val right = current.right?.let { reslot(it, ClusterSlotSide.RIGHT, CastBounds(boundary, 0, width, height)) }
    replaceState(current, current.copy(left = left, right = right, leftPercent = pct))
}

/** Thân của `setDensity` (chip DPI khi chiếu toàn cụm). */
internal fun SimpleCastCoordinator.setDensityBody(vd: Int, dpi: Int?) {
    val current = state
    val full = current as? SimpleCastState.CastingFull
    val applied = CastDensityControl.set(shell, prefs, vd, dpi, full?.targetPkg) ?: return
    if (full != null) replaceState(full, full.copy(pinned = basePin(full.pinned, full.displayConfig).copy(density = applied)))
}

/** Thân của `setDensitySplit` (chip DPI khi chia đôi) — DPI là của cả VD ⇒ cả hai ô nhận cùng giá trị vừa áp. */
internal fun SimpleCastCoordinator.setDensitySplitBody(vd: Int, dpi: Int?) {
    val split = state as? SimpleCastState.CastingSplit ?: return
    val applied = CastDensityControl.setForSplit(shell, prefs, vd, dpi, split) ?: return
    replaceState(
        split,
        split.copy(
            left = split.left?.let { it.copy(pinned = slotBase(it).copy(density = applied)) },
            right = split.right?.let { it.copy(pinned = slotBase(it).copy(density = applied)) },
        ),
    )
}
