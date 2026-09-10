package com.byd.clusternav.launcher

/**
 * Danh sách app **dùng gần đây** cho ngăn kéo "Mở ứng dụng" (U3) — logic THUẦN, test được off-car.
 *
 * Nguồn dữ liệu là **chính hành vi mở app trong Kachi** (mỗi lần mở-thường thì gọi [touch]), CỐ Ý không dùng
 * `UsageStatsManager`: cái đó cần quyền truy-cập-sử-dụng đặc biệt, mà màn cài đặt hệ thống trên đầu xe khoá lại
 * không mở được (cùng lớp vấn đề với quyền đọc thông báo của ClusterNav). Cách này không cần quyền nào.
 */
object RecentApps {

    /** Số app gần đây giữ lại tối đa (một hàng lưới ~6 cột × 2). */
    const val CAP = 12

    /** Ký tự nối khi lưu — tên gói KHÔNG chứa xuống dòng nên an toàn (cùng quy ước với danh sách hồ sơ). */
    private const val SEP = "\n"

    /**
     * Ghi nhận vừa mở [pkg]: đưa lên ĐẦU danh sách, bỏ bản trùng cũ, cắt còn [cap] phần tử.
     * [pkg] rỗng/toàn khoảng trắng ⇒ trả nguyên danh sách cũ (không làm bẩn dữ liệu).
     */
    fun touch(current: List<String>, pkg: String, cap: Int = CAP): List<String> {
        val clean = pkg.trim()
        if (clean.isEmpty() || cap <= 0) return current
        val out = ArrayList<String>(minOf(current.size + 1, cap))
        out.add(clean)
        for (p in current) {
            if (out.size >= cap) break
            if (p != clean && p.isNotBlank()) out.add(p)
        }
        return out
    }

    /** Chuỗi đã lưu → danh sách (bỏ dòng rỗng, khử trùng, cắt [cap]). Chịu được null/chuỗi rác. */
    fun decode(raw: String?, cap: Int = CAP): List<String> =
        raw?.split(SEP)?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.take(cap) ?: emptyList()

    /** Danh sách → chuỗi để lưu bền. */
    fun encode(list: List<String>, cap: Int = CAP): String =
        list.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(cap).joinToString(SEP)
}
