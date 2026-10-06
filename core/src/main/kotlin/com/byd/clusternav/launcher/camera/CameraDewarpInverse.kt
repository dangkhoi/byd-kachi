package com.byd.clusternav.launcher.camera

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.tan

/**
 * ═══ NGHỊCH ĐẢO + MÔ HÌNH CHỤP của [CameraDewarp] — tách khỏi `CameraDewarp.kt` (2.92, trần 500 dòng) ═══════════
 *
 * Spec `docs/specs/kachi-292-camera-full-view.html` T0. **Tách thuần**: cùng thân hàm, cùng tên gọi
 * (`CameraDewarp.forwardSrcToDst(…)`, …) — chúng là **hàm mở rộng** trên đối tượng [CameraDewarp], nên mọi chỗ gọi cũ
 * (bài test, [CameraDewarpTestPattern]) không đổi một chữ. Thay đổi DUY NHẤT về toán là hệ số họ phép chiếu
 * [DewarpParams.kappa] (2.92 · "Thẳng rộng"), và với `κ = 1` mỗi hàm trả **đúng** giá trị của bản trước (nhân/chia
 * cho `1.0` là phép chính xác IEEE — bài `CameraDewarpKappaTest.kappa 1 trung tung bit` ghim).
 *
 * Phía xuôi (lấy mẫu) nằm ở [CameraDewarp.mapDstToSrc]; tệp này là phía NGƯỢC: một điểm của ảnh mắt cá hiện ra ở
 * đâu trên khung đã nắn — dùng để dựng ảnh kiểm tra và chứng minh "đường thẳng vẫn thẳng" bằng số.
 */

/**
 * Nghịch đảo của [CameraDewarp.mapDstToSrc]: **local(nguồn) → local(đích)** — điểm nào của ảnh fisheye hiện ra ở đâu
 * trên khung đã nắn. Dùng để dựng ảnh kiểm tra và để chứng minh "đường thẳng vẫn thẳng".
 *
 * Bán kính hiệu dụng `r_eff(r_dst) = (1−a)·r_dst + a·K·SCALE·κ·atan(r_dst/(κF))` **tăng đơn điệu** nên nghịch đảo là
 * duy nhất; giải bằng công thức đóng khi `a == 1` (`r_dst = κF·tan(r_src/(K·SCALE·κ))`), còn lại chia đôi.
 *
 * @return `null` khi bán kính nguồn **không với tới được**: với `a == 1` phép nắn chỉ chạm tới
 *   [DewarpParams.reachableSrcRadius] (`K·SCALE·κ·π/2`) — với κ = 1 đó là tia 90°, vành ngoài của vòng ảnh nằm sau
 *   90° nên **không có** điểm đích nào chiếu tới; đó là tính chất của phối cảnh thẳng chứ không phải lỗi.
 */
fun CameraDewarp.forwardSrcToDst(u: Float, v: Float, p: DewarpParams, aspect: Float): Pair<Float, Float>? {
    if (!p.enabled) return u to v
    val asp = if (aspect > 0f && !aspect.isNaN() && !aspect.isInfinite()) aspect else 1f
    val px = (u - p.centerX) * 2f
    val py = (v - p.centerY) * 2f / asp
    val rSrc = hypot(px.toDouble(), py.toDouble())
    if (rSrc <= CameraDewarp.CENTER_EPS) return u to v
    val rDst = solveDstRadius(rSrc, p) ?: return null
    val dirX = px / rSrc.toFloat()
    val dirY = py / rSrc.toFloat()
    return (p.centerX + dirX * rDst * 0.5f) to (p.centerY + dirY * rDst * 0.5f * asp)
}

/** `r_eff` của [forwardSrcToDst] — tách ra để test đơn điệu trực tiếp trên nó. */
fun CameraDewarp.effectiveSrcRadius(rDst: Float, p: DewarpParams): Float {
    val a = p.amount.coerceIn(0f, 1f)
    val kappa = p.kappa.toDouble()
    val bent = p.gain.toDouble() * (kappa * atan2(rDst.toDouble(), kappa * p.focal.toDouble()))
    return ((1.0 - a) * rDst + a * bent).toFloat()
}

private fun solveDstRadius(rSrc: Double, p: DewarpParams): Float? {
    val a = p.amount.coerceIn(0f, 1f)
    val gain = p.gain.toDouble()
    val kappa = p.kappa.toDouble()
    if (a >= 1f - 1e-6f) {
        val theta = rSrc / gain
        if (theta / kappa >= PI / 2.0 - 1e-9) return null
        return (kappa * p.focal.toDouble() * tan(theta / kappa)).toFloat()
    }
    var hi = 1.0
    var guard = 0
    while (CameraDewarp.effectiveSrcRadius(hi.toFloat(), p) < rSrc) {
        hi *= 2.0
        if (++guard > 200) return null
    }
    var lo = 0.0
    repeat(80) {
        val mid = 0.5 * (lo + hi)
        if (CameraDewarp.effectiveSrcRadius(mid.toFloat(), p) < rSrc) lo = mid else hi = mid
    }
    return (0.5 * (lo + hi)).toFloat()
}

/**
 * Ống kính đẳng khoảng "lý tưởng" **mô tả đúng bởi [p]**: một điểm của khung phối cảnh thẳng cách quang tâm
 * `(sceneX, sceneY)` (đơn vị: mặt phẳng ở khoảng cách 1, tức `tan` của góc tia) rơi vào đâu trên ảnh nguồn.
 *
 * Đây là **mô hình chụp**, đối xứng với [CameraDewarp.mapDstToSrc] là **mô hình lấy mẫu**. Vì `K·SCALE` dùng chung,
 * nắn với `amount = 1`, `κ = 1` sẽ khôi phục mặt phẳng **chính xác** (tỉ lệ `F/2`, xem bài `duong thang van thang`) ⇒
 * bài test dùng nó làm chuẩn, và [CameraDewarpTestPattern] dùng nó để vẽ ảnh fisheye tổng hợp. Không phụ thuộc κ: κ
 * là chuyện của khung RA, không phải của ống kính.
 */
fun CameraDewarp.idealEquidistantSource(sceneX: Float, sceneY: Float, p: DewarpParams, aspect: Float): Pair<Float, Float> {
    val asp = if (aspect > 0f) aspect else 1f
    val rho = hypot(sceneX.toDouble(), sceneY.toDouble())
    if (rho <= CameraDewarp.CENTER_EPS) return p.centerX to p.centerY
    val theta = atan(rho)
    val rSrc = p.gain.toDouble() * theta
    val dirX = sceneX / rho
    val dirY = sceneY / rho
    return (p.centerX + (dirX * rSrc * 0.5).toFloat()) to
        (p.centerY + (dirY * rSrc * 0.5 * asp).toFloat())
}

/** Bán kính nguồn (đơn vị nửa-bề-ngang ô) của tia [thetaRad] trên ống kính đẳng khoảng của [p]. */
fun CameraDewarp.equidistantSrcRadius(thetaRad: Float, p: DewarpParams): Float = p.gain * thetaRad

/** Góc tia (rad) ứng với bán kính nguồn [rSrc] — nghịch đảo của [equidistantSrcRadius]. */
fun CameraDewarp.equidistantTheta(rSrc: Float, p: DewarpParams): Float = if (p.gain > 0f) rSrc / p.gain else 0f
