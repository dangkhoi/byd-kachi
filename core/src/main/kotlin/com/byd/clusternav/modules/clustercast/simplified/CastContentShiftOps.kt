package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.DisplayParse

/**
 * ═══ 3.1 · R6 — "Dịch nội dung": ÁP → ĐO → GIỮ hoặc TRẢ (đường thi hành) ═════════════════════════════════════════════════
 *
 * Phần thuần (giá trị, bộ đọc cửa sổ, phán quyết) ở [CastContentShift]; đây là chỗ chạm shell, chạy TRÊN executor của
 * coordinator (hàng gộp `SimpleCastCoordinator.shiftQueue` — cùng cơ chế với bốn thanh khung, [CastCoalescingQueue]).
 *
 * ## Bốn câu CLAUDE.md §4 cho `wm overscan … -d <cụm>`
 *  1. **Display**: chỉ `liveDisplayId` đã xác minh sống (≥ 1, owner-guard của `ClusterDisplayResolver`); chưa xác minh ⇒ 0 lệnh.
 *     Đường hoàn tác dùng `undoTargetDisplay` (dò tươi một lần) như `closeProjectionSync`.
 *  2. **App**: chỉ khi phiên là `CastingFull` của app THƯỜNG và gói khớp yêu cầu — không CP/AA (chúng có cấu hình riêng), không
 *     chia đôi (overscan là của cả display ⇒ dời cả hai nửa). Không `if` theo tên gói: app nào ăn hay không là do ĐO (§7).
 *  3. **Loại**: lệnh mức display, không chạm stack/task. Chỉ đời xe có lệnh ([ProjectionRecipe.supportsOverscan]: DiLink < 5 VÀ
 *     API ≤ 29) và chỉ cụm Bo tròn (Chữ nhật giữ overscan 1:1 cho lớp km/h — `ClusterRectLayout.oneToOne`).
 *  4. **Hoàn tác**: đo thấy không dời ⇒ trả overscan đọc được TRƯỚC khi áp, ngay trong lượt. Dừng chiếu ⇒ TOP về 0 (đọc sự
 *     thật trên cụm, không đọc cờ). Tiến trình chết ⇒ lượt mở chiếu kế tiếp áp `NORMAL_DEFAULT` (overscan 0) lên id dò sống,
 *     `closeOrphanProjection` thì `wm overscan reset` — cả hai quyết bằng VD dò thật (§5), không cần dấu riêng.
 */
internal class CastContentShifter(
    private val shell: SimpleCastShell,
    private val configurator: DisplayConfigurator,
    private val sleepMs: (Long) -> Unit,
    private val log: (String) -> Unit,
) {
    /** Kết cục + overscan ĐANG trên cụm sau lượt ([overscan] `null` = không biết). */
    class Outcome(val result: CastShiftResult, val overscan: String?, val measured: Boolean = true)

    /**
     * Đặt TOP của overscan cụm [vd] = [top] cho app [pkg] rồi đo. [sessionOverscan] chỉ là chỗ lùi khi không đọc được overscan
     * đang áp (sự thật thắng bản ghim — app nhận lại có thể đã đứng trên cụm với độ dịch cũ).
     */
    fun shift(vd: Int, pkg: String, sessionOverscan: String, top: Int): Outcome {
        val from = readOverscan(vd) ?: sessionOverscan
        val to = CastContentShift.withTop(from, top)
        if (to == from) return Outcome(CastShiftResult.UNCHANGED, from)
        if (CastContentShift.topOf(to) == 0) {
            // Về 0 = trạng thái an toàn gốc ⇒ áp thẳng, không đo/không trả (trả lại một độ dịch khi người lái xin 0 là sai chiều).
            val ok = configurator.applyOverscan(vd, to)
            log("dịch nội dung: $pkg về 0 ($from → $to) ${if (ok) "OK" else "HỎNG"}")
            return if (ok) Outcome(CastShiftResult.APPLIED, to, measured = false) else Outcome(CastShiftResult.FAILED, null)
        }
        sleepMs(SETTLE_MS)   // khung vừa đặt/resize ⇒ chờ WM xếp xong trước khi lấy mốc "trước"
        val before = probe(vd, pkg) ?: run {
            log("dịch nội dung: không thấy cửa sổ $pkg trên display $vd ⇒ không đo được, KHÔNG áp")
            return Outcome(CastShiftResult.FAILED, from)
        }
        if (!configurator.applyOverscan(vd, to)) {
            log("dịch nội dung: wm overscan $to -d $vd hỏng")
            return Outcome(CastShiftResult.FAILED, null)
        }
        val requested = CastContentShift.topOf(to) - CastContentShift.topOf(from)
        var after: CastContentShift.WindowProbe? = null
        repeat(POLLS) {
            sleepMs(POLL_MS)
            after = probe(vd, pkg)
            val a = after
            if (a != null && CastContentShift.moved(before, a, requested)) {
                log("dịch nội dung: $pkg nhận $from → $to (đỉnh nội dung ${before.contentTop} → ${a.contentTop})")
                return Outcome(CastShiftResult.APPLIED, to)
            }
        }
        // Không dời (hoặc mất cửa sổ): TRẢ ngay giá trị trước khi áp — không để cụm mang độ dịch "bấm không ăn".
        val back = configurator.applyOverscan(vd, from)
        val seen = after?.contentTop?.toString() ?: "không đọc được"
        log("dịch nội dung: $pkg KHÔNG dời (đỉnh ${before.contentTop} → $seen, xin $requested px) ⇒ trả $from ${if (back) "OK" else "HỎNG"}")
        return when {
            !back -> Outcome(CastShiftResult.FAILED, null)
            after == null -> Outcome(CastShiftResult.FAILED, from)   // mất cửa sổ giữa chừng ≠ app không nhận ⇒ không đánh dấu
            else -> Outcome(CastShiftResult.INEFFECTIVE, from)
        }
    }

    /** Overscan đang áp trên [vd] (`l,t,r,b`), `null` = không đọc được. CHỈ ĐỌC. */
    fun readOverscan(vd: Int): String? {
        val out = shell.execute(CastContentShift.DISPLAYS_CMD)
        if (!out.success) return null
        val p = DisplayParse.overscan(out.stdout, vd) ?: return null
        return CastGeometryGuard.overscan(p.joinToString(","))
    }

    private fun probe(vd: Int, pkg: String): CastContentShift.WindowProbe? {
        val out = shell.execute(CastContentShift.WINDOWS_CMD)
        return if (out.success) CastContentShift.probe(out.stdout, pkg, vd) else null
    }

    companion object {
        /** Chờ trước mốc "trước" (ms). */
        const val SETTLE_MS = 250L
        /** Nhịp đọc lại sau khi áp (ms) × số lượt — tối đa ~0,6 s rồi kết luận "không dời". */
        const val POLL_MS = 150L
        const val POLLS = 4
    }
}

/**
 * Đời xe/API này có `wm overscan` VÀ màn cụm của phiên KHÔNG đo được là freeform (hàng "Dịch nội dung" chỉ hiện khi `true`). Chỉ đọc
 * RAM. 3.1 · R26 [ĐO xe 13/10 `oncar-2026-10-13/04-waze-overscan.txt`]: trên màn cụm freeform, overscan 0,-20,0,0 KHÔNG đổi `mFrame`
 * của Waze — task freeform bỏ qua overscan ⇒ hàng vô tác dụng, ẩn. Chưa đo (`null`) ⇒ giữ hành vi cũ (hiện, tự đo ở lần bấm đầu).
 */
val SimpleCastCoordinator.contentShiftSupported: Boolean
    get() = projection.recipe.supportsOverscan && clusterFreeform != true

/** Gửi một bước "Dịch nội dung" cho app [pkg] đang chiếu toàn cụm (gộp — chỉ giá trị cuối của chuỗi bấm được áp). */
fun SimpleCastCoordinator.shiftContent(pkg: String, shift: Int): Boolean =
    shiftQueue.offer(CastShiftRequest(pkg, CastContentShift.clamp(shift)))

/** Thân áp của [SimpleCastCoordinator.shiftQueue] (chạy trên executor). */
internal fun SimpleCastCoordinator.runContentShift(request: CastShiftRequest): CastShiftResult {
    val current = state as? SimpleCastState.CastingFull ?: return CastShiftResult.DROPPED
    if (current.targetPkg != request.pkg || !current.appType.isResizable) return CastShiftResult.DROPPED
    if (!projection.recipe.supportsOverscan || frameStyle == CastStyle.RECT) {
        log("dịch nội dung: bỏ — đời xe không có wm overscan hoặc cụm Chữ nhật (1:1)")
        return CastShiftResult.DROPPED
    }
    val vd = liveDisplayId
    if (vd < 1) {
        log("dịch nội dung: bỏ — display cụm chưa xác minh live ($vd)")
        return CastShiftResult.DROPPED
    }
    if (geometry.displayFacts().freeform == true) {   // 3.1 · R26: freeform bỏ qua overscan [ĐO xe 13/10] ⇒ 0 lệnh wm
        log("dịch nội dung: bỏ — màn cụm freeform (overscan không tác dụng)")
        return CastShiftResult.DROPPED
    }
    val base = current.pinned ?: current.displayConfig.copy(bounds = null)
    val out = CastContentShifter(shell, configurator, { Thread.sleep(it) }, { log(it) })
        .shift(vd, request.pkg, base.overscan, request.shift)
    when (out.result) {
        CastShiftResult.APPLIED -> {
            persistContentShift(request.pkg, request.shift)
            if (out.measured) prefs.setContentShiftVerdict(request.pkg, true)
        }
        CastShiftResult.INEFFECTIVE -> prefs.setContentShiftVerdict(request.pkg, false)
        else -> Unit
    }
    // Bản ghim của phiên = sự thật vừa đọc/áp trên cụm (màn đọc số từ đây — `ClusterNavBridgeContentShift`).
    out.overscan?.let { os -> if (os != base.overscan) replaceState(current, current.copy(pinned = base.copy(overscan = os))) }
    return out.result
}

/**
 * Lưu độ dịch vào bản ghi (gói, FULL Bo tròn) của hồ sơ đang dùng — trường `overscan` SẴN CÓ của [DisplayConfig], không đường lưu
 * thứ hai. Chưa có bản ghi ⇒ nền không khung (như `CastDensityControl`: không đóng khung lên app chưa từng resize).
 */
private fun SimpleCastCoordinator.persistContentShift(pkg: String, shift: Int) {
    val existing = prefs.displayConfigFor(pkg, CastProfile.FULL) ?: DisplayConfig.NORMAL_DEFAULT.copy(bounds = null)
    prefs.saveDisplayConfig(pkg, CastProfile.FULL, existing.copy(overscan = CastContentShift.withTop(existing.overscan, shift)))
}

/**
 * Lượt chiếu TOÀN cụm vừa đặt xong app (sau khung + DPI): áp độ dịch đã lưu CUỐI CÙNG, kèm phép đo. App đã đo "không nhận" trên
 * xe này ⇒ bỏ qua (0 lệnh).
 */
internal fun SimpleCastCoordinator.applySavedContentShift(pkg: String, shift: Int, reconcile: Boolean = false) {
    // [reconcile]: nhánh "app ĐÃ trên cụm" không qua `DisplayConfigurator.apply` ⇒ cụm có thể còn độ dịch của phiên/hồ sơ trước —
    // kể cả khi hồ sơ nay lưu 0 thì vẫn phải đưa cụm về đúng giá trị (đọc sự thật, `UNCHANGED` nếu đã đúng).
    if (shift == 0 && !(reconcile && projection.recipe.supportsOverscan)) return
    if (projection.recipe.supportsOverscan && geometry.displayFacts().freeform == true) {   // 3.1 · R26 — không áp độ dịch đã lưu
        log("dịch nội dung: màn cụm freeform — bỏ độ dịch đã lưu ($shift) của $pkg")
        return
    }
    if (shift != 0 && prefs.contentShiftVerdict(pkg) == false) {
        log("dịch nội dung: $pkg đã đo KHÔNG nhận trên xe này — bỏ qua độ dịch đã lưu ($shift)")
        return
    }
    log("dịch nội dung: áp độ dịch đã lưu $shift cho $pkg ⇒ ${runContentShift(CastShiftRequest(pkg, shift))}")
}

/**
 * Dừng chiếu toàn cụm (app thường): TOP overscan của cụm về 0 nếu ĐANG khác 0 — đọc sự thật trên cụm (một lệnh đọc), không đọc
 * cờ RAM (§5). Chỉ đời xe có lệnh. Đường mới, đứng CUỐI nhánh dừng (§6).
 */
internal fun SimpleCastCoordinator.undoContentShift() {
    if (!projection.recipe.supportsOverscan) return
    val vd = undoTargetDisplay("stop.contentShift") ?: return
    val shifter = CastContentShifter(shell, configurator, {}, { log(it) })
    val now = shifter.readOverscan(vd) ?: return
    if (CastContentShift.topOf(now) == 0) return
    val ok = configurator.applyOverscan(vd, CastContentShift.withTop(now, 0))
    log("dịch nội dung: dừng chiếu ⇒ trả TOP overscan $now → 0 trên $vd ${if (ok) "OK" else "HỎNG"}")
}
