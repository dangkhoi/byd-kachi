package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ 2.90 · R1 — bảng DỮ LIỆU các app "bóng nổi" giữ cửa sổ phủ trên màn ảo cụm ══════════════════════════════════════════
 *
 * [ĐO xe 06/10, `docs/diagnostics/oncar-2026-10-06-cluster-rect.md` F5] bóng nổi của mod VietMap giữ 3 cửa sổ
 * `TYPE_APPLICATION_OVERLAY` của `vn.vietmap.live` trên màn ảo cụm — kể cả khi tắt chiếu ⇒ màn ảo "không trống" mỗi khi VietMap
 * chạy, cổng theme mức B ([ClusterThemePlan]) không được gửi. Kachi KHÔNG tự dừng app bên thứ ba: chỉ nhận ra đúng ca này để
 * nói lý do rõ ("tắt bóng VietMap rồi Áp ngay") thay cho một lý do chung chung.
 *
 * Generic (CLAUDE.md §7): không `if (pkg == …)` trong luật — luật chỉ tra bảng này. Thêm app CHỈ khi đã đo được cửa sổ của nó
 * trên màn ảo cụm (CLAUDE.md §14).
 */
object ClusterBubbleApps {

    /** Gói → nhãn người lái đọc được. */
    val KNOWN: Map<String, String> = mapOf(
        "vn.vietmap.live" to "VietMap",
    )

    /** Nhãn của [pkg] nếu là app bóng nổi đã biết; `null` = không phải. */
    fun labelOf(pkg: String): String? = KNOWN[pkg]
}
