package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.StackReads
import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ L4 · D2(a) — THU HỒI màn ảo dàn dựng ẨN bị GIỮ ở các lượt trước (thuần, chặn, `:core`) ═══════════════════════════
 *
 * Review 287 [P3]: [BehindHomeSequence.startBehindHidden] cố ý GIỮ màn ảo ẩn khi bản đọc còn thấy app người dùng trên đó
 * (K7 bị rào chặn — R-L4.3) hoặc KHÔNG đọc được (`UNREAD` — [P1]); `BehindHomeRunner.failed` (chuỗi ném giữa chừng) cũng
 * không nhả. Trước bản vá, màn ảo đó + luồng `kachi-stage` + `ImageReader` cỡ display 0 sống tới khi BYD giết Kachi — mỗi
 * lần chạm lối tắt / mỗi chuyến lại rò thêm một cái. Nay MỖI lượt của bên thi hành (mutex `kachi-behind`, trước thân lượt)
 * đọc `am stack list` MỘT lần và nhả màn ảo nào đã TRỐNG app người dùng.
 *
 * Rào nhả giữ NGUYÊN luật D2 (L4-c): chỉ nhả khi bản đọc ĐỌC ĐƯỢC chứng minh không còn task nào của gói khác Kachi trên màn
 * ảo đó (lớp che của chính Kachi thì được — cờ 256 kết thúc nó). Đọc hỏng ⇒ giữ hết, lượt sau thử lại. Không có màn ảo nào
 * bị giữ ⇒ 0 lệnh (không tốn một lượt đọc).
 *
 * Bốn câu CLAUDE.md §4: không có lệnh shell đổi trạng thái nào — một lệnh CHỈ ĐỌC (`am stack list`) + `VirtualDisplay.release`
 * trong tiến trình, đúng các màn ảo DO KACHI TẠO cho dàn dựng ẩn ([BehindHomeSequence.HiddenStagePort.kept]).
 */
object HiddenStageReclaim {

    /** Màn ảo nào trong [kept] nhả được theo bản đọc [entries]: không còn task nào của gói khác [selfPkg]. Đọc hỏng ⇒ không màn nào. */
    fun reclaimable(entries: List<StackEntry>?, kept: Collection<Int>, selfPkg: String): List<Int> {
        if (entries.isNullOrEmpty() || selfPkg.isBlank()) return emptyList()
        return kept.filter { vd -> vd >= 1 && entries.none { it.displayId == vd && it.pkg != selfPkg } }.distinct()
    }

    /** Một lượt thu hồi qua [port]; trả một dòng nhật ký ASCII, `null` = không có màn ảo nào bị giữ (0 lệnh). */
    fun run(sh: (String) -> String, port: BehindHomeSequence.HiddenStagePort, selfPkg: String): String? {
        val kept = port.kept().toList()
        if (kept.isEmpty()) return null
        val read = StackReads.read(sh)
        val free = reclaimable(read.entries, kept, selfPkg)
        free.forEach { vd -> runCatching { port.reclaim(vd) } }
        return "stage reclaim kept=$kept freed=$free${read.error?.let { " unread=$it" }.orEmpty()}"
    }
}
