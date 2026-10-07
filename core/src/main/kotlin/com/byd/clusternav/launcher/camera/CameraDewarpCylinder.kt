package com.byd.clusternav.launcher.camera

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * ═══ 2.94 R1 · *Thẳng rộng* của camera GƯƠNG = phép chiếu TRỤ, trục = thân xe — bản gốc Kotlin, THUẦN ═══════════════
 *
 * Spec `docs/specs/kachi-294-plan.html` §3 R1 · §4.1. Owner 07/10 (sau khi xem ảnh xem trước): *"cam trái phải thì theo
 * hướng trụ, cam sau trước thì lấy option 3"* ⇒ camera gương ở *Thẳng rộng* đi nhánh này; camera sau/trước quy về
 * *Nắn thẳng* ([CameraViewMode.forCamera]) nên KHÔNG bao giờ tới đây.
 *
 * ## Vì sao trụ, không phải κ xuyên tâm của 2.92–2.93
 * κ xuyên tâm (`θ = κ·atan(r, κF)`) chỉ giữ thẳng đường đi QUA tâm quang ⇒ bậu cửa xe bên cạnh, vạch làn, mép lề bị
 * uốn. Phép trụ có trục = thân xe: mọi đường thế giới **song song thân xe** nằm trong một mặt phẳng CHỨA trục ⇒ có một
 * góc φ quanh trục KHÔNG ĐỔI ⇒ hiện thành **một đường thẳng** trên khung, bất kể hàm nào dùng dọc trục (chứng minh giải
 * tích; bài `CameraDewarpCylinderTest` ghim bằng số). Giá phải trả: điểm tụ của các đường ấy ở 90° phía đuôi — phép
 * chiếu giữ chúng thẳng không vẽ được gì quá điểm đó ⇒ mép đuôi ~86° (2.93: 96°) — owner đã xem và chọn.
 *
 * ## Công thức (cùng ký hiệu [CameraDewarp.mapDstToSrc], p-space đơn vị nửa bề ngang ô)
 * ```
 * lam   = κ·atan(p.x, κ·F)                 // góc từ mặt phẳng vuông góc trục; trục = local a (thân xe ở camera gương)
 * |lam| ≥ π/2 − 1e-3  ⇒ ĐEN                // chốt cực: thu phóng < 100 % hay dịch lớn có thể vượt cực ⇒ gập ảnh
 * phi   = p.y / F                          // góc QUANH trục, đẳng khoảng
 * |phi| ≥ π ⇒ ĐEN                          // chốt quanh trục: sin/cos tuần hoàn ⇒ F nhỏ sẽ lặp ảnh
 * ray   = (sin lam, cos lam·sin phi, cos lam·cos phi)
 * rho   = |ray.xy| ; theta = atan(rho, ray.z)
 * r_src = (K·SCALE)·theta                  // cùng ống kính f-θ của nhánh xuyên tâm
 * proj  = (cx + ray.x/rho·r_src·0.5, cy + ray.y/rho·r_src·0.5·aspect)   // rho ≈ 0 ⇒ proj = tâm quang
 * out   = mix(local, proj, amount)
 * ```
 * Gần tâm: `src ≈ c + p·(K·S/F)` — cùng độ phóng với nhánh xuyên tâm cùng F, và bảo giác tại tâm (`dλ/ds = dφ/dt = 1/F`).
 *
 * ## Trục = local a — SỰ THẬT của camera gương, không suy từ góc xoay
 * Research công cụ ngoài repo (README §1) [ĐO, bản port numpy khớp ảnh chụp xe NCC 0,991]: ở camera gương trục dọc thân
 * xe là **local a** (trục x của ô chưa xoay); đuôi xe ở phía a nhỏ (gương trái). Người lái đổi chip *Xoay* thì thân xe
 * vẫn là local a (xoay xảy ra TRƯỚC bước này, trong [CameraDewarp.rotateDstToLocal]) ⇒ không cần uniform chọn trục.
 * Rủi ro mở (README §5.1): giả định camera không lệch yaw so với thân xe — nhìn thẳng trên ảnh, chưa fit số.
 *
 * Bản GLSL: khối `if (uCyl > 0.5)` của [CameraDewarpShader.FRAGMENT] — dịch từng dòng của [map]; bài test ghim cả hai.
 */
internal object CameraDewarpCylinder {

    /** Chốt cực `π/2 − 1e-3` (rad): `|λ|` chạm tới đây ⇒ đen đặc (ảnh gập qua điểm tụ — `tmp/var1.png` của research). */
    const val POLE_GUARD_RAD: Double = PI / 2.0 - 1e-3

    /** Đúng hằng ấy viết trong GLSL — bài `CameraDewarpCylinderTest` so với [POLE_GUARD_RAD]. */
    const val POLE_GUARD_GLSL: String = "1.5697963"

    /**
     * Chốt quanh trục `|φ| ≥ π` ⇒ đen (soát Opus 07/10 [P3]). `sin/cos φ` tuần hoàn 2π: bỏ chốt thì F nhỏ (`camera_wide_focal`
     * 25 %, `prefs_set` ghi được) cho `φ = 2π − 0,3` ở hàng khung `b ≈ 0,95` ⇒ lấy lại đúng tia `φ = −0,3` — ảnh LẶP lại
     * trong khung. `|φ| = π` đã là tia sau lưng camera (ngoài vòng ảnh 97°) ⇒ đen ở đây không mất pixel thật nào.
     */
    const val PHI_GUARD_RAD: Double = PI

    /** Đúng hằng ấy viết trong GLSL — bài `CameraDewarpCylinderTest` so với [PHI_GUARD_RAD]. */
    const val PHI_GUARD_GLSL: String = "3.1415927"

    /** Toạ độ nguồn NGOÀI ô ⇒ [CameraDewarp.sample] trả `null` / shader trả đen đặc. */
    private val OUTSIDE: Pair<Float, Float> = -1f to -1f

    /**
     * local(đích) → local(nguồn) theo phép trụ. Chỉ gọi từ [CameraDewarp.mapDstToSrc] SAU các cổng chung
     * ([DewarpParams.enabled], `aspect` hợp lệ) — [px]/[py] là p-space đã tính ở đó.
     */
    fun map(u: Float, v: Float, px: Float, py: Float, p: DewarpParams, asp: Float): Pair<Float, Float> {
        val kappa = p.kappa.toDouble()
        val focal = p.focal.toDouble()
        val lam = kappa * atan2(px.toDouble(), kappa * focal)
        if (abs(lam) >= POLE_GUARD_RAD) return OUTSIDE
        val phi = py.toDouble() / focal
        if (abs(phi) >= PHI_GUARD_RAD) return OUTSIDE
        val rayX = sin(lam)
        val rayY = cos(lam) * sin(phi)
        val rayZ = cos(lam) * cos(phi)
        val rho = hypot(rayX, rayY)
        var projX = p.centerX
        var projY = p.centerY
        if (rho > CameraDewarp.CENTER_EPS) {
            val rSrc = p.gain.toDouble() * atan2(rho, rayZ)
            projX = (p.centerX + rayX / rho * rSrc * 0.5).toFloat()
            projY = (p.centerY + rayY / rho * rSrc * 0.5 * asp).toFloat()
        }
        val a = p.amount.coerceIn(0f, 1f)
        return (u + (projX - u) * a) to (v + (projY - v) * a)
    }
}
