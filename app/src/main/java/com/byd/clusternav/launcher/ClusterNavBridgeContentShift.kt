package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.simplified.CastContentShift
import com.byd.clusternav.modules.clustercast.simplified.CastShiftRequest
import com.byd.clusternav.modules.clustercast.simplified.CastShiftResult
import com.byd.clusternav.modules.clustercast.simplified.CastStyle
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.modules.clustercast.simplified.contentShiftSupported
import com.byd.clusternav.modules.clustercast.simplified.shiftContent

/**
 * ═══ 3.1 · R6 — nửa "Dịch nội dung" của [ClusterNavBridge] (spec `kachi-301-cast-steps-dialog-ia.html` R6) ═══════════════════
 *
 * Phần THUẦN (quyết định hiện/ẩn, số của phiên, gửi lệnh); hình ở `SettingsCastShiftRow`. Cơ chế + phép đo ở `:core`
 * (`CastContentShiftOps`). Hàng chỉ có khi MỌI điều kiện sau cùng đúng — thiếu một là ẩn, không "hiện mà bấm không ăn":
 *  - đang chiếu TOÀN cụm một app thường (overscan là của cả display ⇒ chia đôi thì dời cả hai nửa; CP/AA có cấu hình riêng);
 *  - cụm Bo tròn (Chữ nhật giữ overscan 1:1 cho lớp km/h);
 *  - đời xe + API có `wm overscan` (`ClusterProfile.supportsOverscan` ∧ API ≤ 29);
 *  - app CHƯA bị đo là "không nhận" trên xe này (phán quyết bền, phạm vi XE).
 */
fun ClusterNavBridge.contentShiftAvailable(target: CastGeometryTarget): Boolean {
    if (target.side != null) return false
    val s = castState() as? SimpleCastState.CastingFull ?: return false
    if (!s.appType.isResizable || s.targetPkg != target.pkg) return false
    if (castStyleSession()?.frame == CastStyle.RECT) return false
    return runCatching { coordinator.contentShiftSupported && coordinator.prefs.contentShiftVerdict(target.pkg) != false }
        .getOrDefault(false)
}

/** Độ dịch ĐANG trên cụm theo phiên (bản ghim — cập nhật sau mỗi lượt áp/đo), px, âm = lên. */
fun ClusterNavBridge.contentShift(target: CastGeometryTarget): Int {
    val s = castState() as? SimpleCastState.CastingFull ?: return 0
    if (s.targetPkg != target.pkg) return 0
    return CastContentShift.topOf((s.pinned ?: s.displayConfig).overscan)
}

/** Gửi độ dịch MỚI (đã kẹp −200..200) — gộp trên hàng của coordinator; kết cục về qua [observeContentShift]. */
fun ClusterNavBridge.setContentShift(target: CastGeometryTarget, px: Int) {
    if (target.side != null) return
    coordinator.shiftContent(target.pkg, CastContentShift.clamp(px))
}

/** Còn lệnh dịch đang chờ/đang áp ⇒ màn giữ ý định, chưa đồng bộ về sự thật. */
fun ClusterNavBridge.contentShiftBusy(): Boolean = runCatching { coordinator.shiftQueue.busy(Unit) }.getOrDefault(false)

/** Nghe kết cục (luồng coordinator — bên nghe tự `post`). Trả hàm gỡ. */
fun ClusterNavBridge.observeContentShift(onOutcome: (CastShiftRequest, CastShiftResult) -> Unit): () -> Unit =
    coordinator.shiftQueue.addListener(onOutcome)
