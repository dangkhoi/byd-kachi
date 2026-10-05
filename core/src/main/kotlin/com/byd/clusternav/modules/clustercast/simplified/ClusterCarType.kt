package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ CLUSTER-THEME-SAFE B1a (2.89) — mã đời xe `persist.sys.car.type` (thuần: lệnh + parser) ═══════════════════════════
 *
 * [ĐO 14/09 + 29/09] Seal: `[persist.sys.car.type]: [138]` (`vehicle_40d_code`); `ro.product.model` KHÔNG phân biệt được đời
 * xe (mọi đầu máy báo `BYD AUTO`). [ĐO-RE] SL6 = 162 (`factory-hud-nav-RE-avenues-2026-08-19.md:26`). Bảng mã → kiểu cụm gốc
 * nằm ở `ClusterProfile` (`:app`, CLAUDE.md §7), không ở đây.
 *
 * Nguồn đọc: `:app` đọc trong tiến trình (`SysProps`) trước; [CHƯA BIẾT] uid app có đọc được prop `persist.sys.*` này không
 * ⇒ rơi về [CMD] qua kênh dadb (uid shell). Cả hai hỏng ⇒ `null` = xe LẠ (RECT bị ẩn — hướng an toàn).
 */
object ClusterCarType {
    const val PROP: String = "persist.sys.car.type"

    /** Lệnh ĐỌC qua dadb — chỉ đọc, không đổi gì. */
    const val CMD: String = "getprop $PROP"

    private val BARE = Regex("^\\d{1,6}$")
    private val LISTED = Regex("\\[" + Regex.escape(PROP) + "\\]:\\s*\\[(\\d{1,6})\\]")

    /**
     * Giá trị từ `getprop persist.sys.car.type` (một dòng số) HOẶC từ bản `getprop` đầy đủ (dòng `[key]: [value]`). Rỗng /
     * không phải số / quá dài ⇒ `null` (không đoán).
     */
    fun parse(out: String?): String? {
        val t = out?.trim().orEmpty()
        if (t.isEmpty()) return null
        if (BARE.matches(t)) return t
        return LISTED.find(t)?.groupValues?.get(1)
    }
}
