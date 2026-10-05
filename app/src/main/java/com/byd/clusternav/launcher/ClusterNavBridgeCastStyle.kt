package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.ClusterProfile
import com.byd.clusternav.modules.clustercast.simplified.CastSessionStyle
import com.byd.clusternav.modules.clustercast.simplified.CastStyle
import com.byd.clusternav.modules.clustercast.simplified.CastStyleApply
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.castSession

/**
 * ═══ B1b · CLUSTER-RECT-OPTION — nửa "Kiểu chiếu cụm: Bo tròn / Chữ nhật" của [ClusterNavBridge] ══════════════════════════
 *
 * Owner 05/10: *"ủa chữ nhật làm luôn chứ, thêm option chọn là chữ nhật hay bo tròn là OK"*. Spec `kachi-289-field-fixes.html`
 * mục B1b. Tệp riêng (trần 500 dòng — `ClusterNavBridgeCast.kt`), hàm mở rộng như mọi nửa khác của cầu (spec N2: một cầu).
 *
 * ## Ba điều cầu này giữ (R5)
 *  1. **Ghi lựa chọn = CHỈ prefs** ([setCastStyle]) — 0 lệnh AutoContainer, kể cả khi đang chiếu. Coordinator đọc lựa chọn MỘT
 *     lần đầu lượt mở chiếu và ghim theo phiên (`ProjectionManager.session`).
 *  2. **"Áp ngay" chỉ khi không có app đang chiếu** ([applyCastStyleNow] kiểm LẠI trạng thái lúc chạy — CLAUDE.md §5) và đi
 *     đúng đường [restoreCluster] ⇒ lượt mở qua cổng theme: màn ảo cụm còn ⇒ `VD_PRESENT`, không gửi theme (D4).
 *  3. **Hàng chỉ hiện khi đời xe cho Chữ nhật** ([castStyleOffered]) — Seal `car.type=138` (bảng B.2); xe khác ẩn hẳn, không
 *     "hiện mà bấm không ăn" (opcode 31 trên cụm 8.8" đẩy cụm về simple mode [ĐO DashCast INC-20260625]).
 */

/**
 * Đời xe này có cho chọn Bo tròn / Chữ nhật không. `resolveCached` không shell (prefs + getprop phản chiếu); mã `car.type` dò
 * qua dadb ở lượt mở chiếu đầu (`refineByShell`) xoá đệm ⇒ lần mở trang sau nói đúng.
 */
fun ClusterNavBridge.castStyleOffered(): Boolean =
    runCatching { ClusterProfile.resolveCached(app).supportsStyle }.getOrDefault(false)

/** Lựa chọn của HỒ SƠ đang dùng (khoá `cast_style`); lỗi đọc ⇒ Bo tròn (D3). */
fun ClusterNavBridge.castStyle(): CastStyle = runCatching { coordinator.prefs.castStyle() }.getOrDefault(CastStyle.CURVED)

/** Ghi lựa chọn — CHỈ prefs, không lệnh nào (áp ở lượt mở chiếu kế tiếp, hoặc [applyCastStyleNow]). */
fun ClusterNavBridge.setCastStyle(style: CastStyle) {
    coordinator.prefs.setCastStyle(style)
}

/**
 * Review 2.89 Pass 3 · cluster-r2-5 — Kachi vẽ được số km/h không (quyền vẽ trên ứng dụng khác). Chọn Chữ nhật mà `false` ⇒ lượt mở
 * chiếu dùng Bo tròn (`CastStyleApply.withReadout`) — màn Cài đặt nói lý do.
 */
fun ClusterNavBridge.castStyleReadoutDrawable(): Boolean = SimpleCastRuntime.canDrawReadout(app)

/** Kiểu cụm của phiên chiếu đang mở (`null` = không có phiên) — để màn Cài đặt nói "cụm đang …". */
fun ClusterNavBridge.castStyleSession(): CastSessionStyle? = runCatching { coordinator.castSession }.getOrNull()

/**
 * Kiểu lượt mở chiếu kế tiếp THẬT SỰ dùng: Chữ nhật mà chưa vẽ được km/h ⇒ Bo tròn ([CastStyleApply.withReadout], cùng luật
 * với `SimpleCastRuntime.desiredStyleFor`). Review 2.89 vòng 3 · r3a-3: so nút "Áp ngay" / câu "Cụm đang" với kiểu này, không
 * với lựa chọn thô — nếu không nút hiện mãi mà bấm vẫn ra Bo tròn.
 */
fun ClusterNavBridge.castStyleEffective(): CastStyle = CastStyleApply.withReadout(castStyle(), castStyleReadoutDrawable())

/** Có hiện nút "Áp ngay" không ([CastStyleApply.offer]) — theo kiểu hiệu lực [castStyleEffective]. */
fun ClusterNavBridge.castStyleApplyOffered(): Boolean =
    CastStyleApply.offer(castStyleEffective(), castStyleSession(), castState(), castEnabled())

/**
 * "Áp ngay": kiểm LẠI ngay lúc bấm (trạng thái có thể đã đổi từ lúc vẽ nút) rồi đi đúng đường [restoreCluster]. Trả `false`
 * (không làm gì) khi đang có app chiếu hoặc Cast tắt — chỗ gọi nói lý do.
 */
fun ClusterNavBridge.applyCastStyleNow(): Boolean {
    if (!castEnabled() || !CastStyleApply.stateAllows(castState())) return false
    restoreCluster()
    return true
}
