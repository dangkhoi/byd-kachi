package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ Nền chiếu `ClusterBlackActivity` trên màn ảo cụm — đặt (lượt mở) + đặt BÙ (lượt chiếu đầu từ Idle) ═══
 *
 * 2.96 · R7 — [ĐO xe 07/10 20:48] lượt mở chạm hạn 25 s (`TIMEOUT: openProjection` 20:48:47.959) SAU khi 16/35 đã gửi
 * (20:48:38.4 / :40.6) nhưng TRƯỚC lệnh `am start … ClusterBlackActivity` ⇒ cụm ở chế độ chiếu mà không có nền: [ĐO dumpsys 20:55]
 * 0 cửa sổ ClusterBlack trên display 4, ảnh cụm còn khung GMaps cũ đứng yên phía sau cửa sổ đang chạy.
 *
 * ## Vì sao đặt BÙ ở lượt chiếu kế tiếp, không đặt trong `finally` của lượt mở
 * Lượt mở bị ngắt CHÍNH VÌ đã hết hạn — chạy thêm `am start` + ngủ 1 s + `am stack list` + resize trong `finally` là kéo thao tác
 * quá hạn thêm vài giây, chiếm worker duy nhất đúng lúc lệnh tự-chiếu đang chờ (BubbleAutostart bắn khi về Idle). Đặt bù ở đầu
 * `handleCastFull`/`handleCastSlot` (khi đi từ Idle, ngay trước khi đặt app) chạy trong hạn của CHÍNH thao tác đó, trên id vừa
 * dò TƯƠI, và chỉ khi sự thật (`am stack list`) nói nền chưa có (CLAUDE.md §5 — không cờ RAM).
 * [ĐO mã] trạng thái sau lượt mở bị ngắt vẫn nhất quán: `ProjectionManager.isOpen` = true từ khi 35 gửi xong ⇒ `setError` hồi về
 * Idle (không Off) ⇒ lượt chiếu từ Idle được phép — khớp sự thật phía xe (chiếu đang mở). Không cần sửa trạng thái.
 *
 * ## Bốn câu CLAUDE.md §4 (cho lệnh `am start` đặt bù)
 *  1. **Display**: chỉ `vd` ≥ 1 do bên gọi vừa dò LIVE (`detectClusterDisplay`, R1/R2) — `vd < 1` ⇒ không làm gì.
 *  2. **App**: đúng MỘT component — `<selfPackage>/…ClusterBlackActivity` của chính Kachi; không chạm app/stack nào khác.
 *  3. **Stack**: không di chuyển/gỡ stack nào; chỉ khởi activity của chính mình (freeform, như lượt mở đã chạy trên xe) rồi
 *     resize ĐÚNG task của nó trên `vd`.
 *  4. **Hoàn tác**: như nền đặt lúc mở — tắt chiếu gỡ nó (`ClusterThemeGuard.removePlaceholder`).
 */

/**
 * Đặt nền chiếu lên [vd] — đúng chuỗi lệnh lượt mở đã chạy trên xe (tách nguyên văn từ `openProjectionBody`, CLAUDE.md §6).
 *
 * Component MUST be <installed applicationId>/<full class FQN>. The class FQN keeps the code namespace (com.byd.clusternav.*),
 * which DIFFERS from the applicationId. A leading-dot class would be resolved against the PACKAGE part (selfPackage) → a class
 * that does NOT exist. So spell the class in full — never relative.
 */
internal fun SimpleCastCoordinator.placeClusterPlaceholder(vd: Int) {
    shell.execute("am start --display $vd --windowingMode 5" +
        " -n '$selfPackage/com.byd.clusternav.modules.clustercast.ClusterBlackActivity'")
    Thread.sleep(1000)
    val stackResult = shell.execute("am stack list")
    if (stackResult.success) {
        val taskId = CastStackParser.findTaskId(stackResult.stdout, selfPackage, vd)
            ?: CastStackParser.findTaskId(stackResult.stdout, "ClusterBlackActivity", vd)
        if (taskId != null) shell.execute("am task resize $taskId 0 0 1920 720")
    }
}

/**
 * 2.96 · R7 — đảm bảo nền chiếu CÓ trên [vd] trước khi đặt app đầu tiên của phiên. Đọc `am stack list`: đã có ClusterBlack của
 * Kachi trên [vd] ⇒ 0 lệnh ghi; chưa có ⇒ log + [placeClusterPlaceholder]. Đọc hỏng ⇒ log, KHÔNG đặt mù. Trả `true` khi đã đặt bù.
 */
internal fun SimpleCastCoordinator.ensureClusterPlaceholder(vd: Int, tag: String): Boolean {
    if (vd < 1) return false
    val out = shell.execute("am stack list")
    if (!out.success) { log("$tag: không đọc được am stack list — không đặt bù nền chiếu"); return false }
    if (ClusterPlaceholder.presentOn(out.stdout, vd, selfPackage)) return false
    log("$tag: nền chiếu ClusterBlack VẮNG trên màn ảo cụm $vd (lượt mở trước bị ngắt?) ⇒ đặt bù trước khi chiếu app (R7)")
    placeClusterPlaceholder(vd)
    return true
}

/** Phần THUẦN của R7 (test off-device trên dump thật, CLAUDE.md §10). */
internal object ClusterPlaceholder {
    /** `true` khi `am stack list` có task ClusterBlack của [selfPkg] trên [vd] (khớp gói TRỌN + tên lớp — `isPlaceholderComp`). */
    fun presentOn(amStackList: String, vd: Int, selfPkg: String): Boolean =
        CastStackParser.parseTasks(amStackList).any { it.displayId == vd && ClusterThemePlan.isPlaceholderComp(it.component, selfPkg) }
}

/**
 * 2.96 · R15 — khe còn lại của R7: lượt mở bị ngắt/lỗi SAU khi 16/35 đã gửi (`projection.isOpen`) mà người lái KHÔNG chiếu app nào
 * ⇒ cụm ở chế độ chiếu không nền (R7 chỉ đặt bù ở lượt chiếu app kế tiếp). Hẹn một việc NỀN "placeholder-recover":
 *  • chỉ vào hàng khi executor RẢNH HẲN ([BoundedCastExecutor.submitIfIdle], R5) ⇒ không bao giờ đẩy lệnh chiếu/dừng của người dùng;
 *    bận ⇒ thử lại sau [PlaceholderRecover.RETRY_MS], tối đa [PlaceholderRecover.MAX_ATTEMPTS] lần rồi bỏ (có log);
 *  • trước khi hẹn VÀ khi chạy đều hỏi lại [PlaceholderRecover.stillWanted] (chiếu còn mở + trạng thái vẫn Idle/Error — đã chiếu
 *    app thì R7 lo, đã đóng/đang mở lại thì không chạm);
 *  • thân = [ensureClusterPlaceholder] trên id dò TƯƠI (đọc `am stack list` trước, có rồi ⇒ 0 lệnh ghi — bốn câu §4 ở KDoc đầu tệp);
 *    chưa dò thấy màn ảo cụm (đúng lý do lượt mở chạm hạn — R6) ⇒ tính là một lần thử, hẹn lại.
 * [delayMs] = khoảng trước lần thử ĐẦU (coordinator: sau nhịp tự hồi Error → Idle 3 s).
 */
internal fun SimpleCastCoordinator.schedulePlaceholderRecover(delayMs: Long, attempt: Int = 1) {
    if (!PlaceholderRecover.stillWanted(projection.isOpen, state)) return
    if (attempt == 1) log("placeholder-recover: lượt mở dừng giữa chừng khi chiếu đã mở ⇒ hẹn đặt bù nền chiếu (R15)")
    executor.schedule(delayMs) {
        if (!PlaceholderRecover.stillWanted(projection.isOpen, state)) {
            log("placeholder-recover: bỏ — trạng thái đã đổi (${state}, isOpen=${projection.isOpen})")
            return@schedule
        }
        val queued = executor.submitIfIdle("placeholder-recover") {
            if (!PlaceholderRecover.stillWanted(projection.isOpen, state)) return@submitIfIdle
            val vd = detectClusterDisplay()
            if (vd >= 1) ensureClusterPlaceholder(vd, "placeholder-recover") else retryPlaceholderRecover(attempt, "chưa dò thấy màn ảo cụm")
        }
        if (!queued) retryPlaceholderRecover(attempt, "executor bận")
    }
}

private fun SimpleCastCoordinator.retryPlaceholderRecover(attempt: Int, why: String) {
    if (attempt >= PlaceholderRecover.MAX_ATTEMPTS) {
        log("placeholder-recover: bỏ sau $attempt lần ($why) — lượt chiếu app kế tiếp sẽ đặt bù (R7)")
        return
    }
    log("placeholder-recover: $why — thử lại lần ${attempt + 1}/${PlaceholderRecover.MAX_ATTEMPTS}")
    schedulePlaceholderRecover(PlaceholderRecover.RETRY_MS, attempt + 1)
}

/** Phần THUẦN của R15. */
internal object PlaceholderRecover {
    /** Trước lần thử đầu: sau nhịp tự hồi Error → Idle (3 s) của `setError`. */
    const val FIRST_DELAY_MS = 4_000L
    const val RETRY_MS = 3_000L
    const val MAX_ATTEMPTS = 5

    /** Chỉ khi chiếu CÒN mở và chưa có phiên chiếu app / lượt mở-đóng mới (Idle hoặc Error vừa đặt). */
    fun stillWanted(projectionOpen: Boolean, state: SimpleCastState): Boolean =
        projectionOpen && (state is SimpleCastState.Idle || state is SimpleCastState.Error)
}
