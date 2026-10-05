package com.byd.clusternav.launcher.behind

import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ A4 · SLOT-PLACE-KEEPS-MUSIC (2.89) — mở app vào ô khi app ĐÃ có task: đưa task sang, KHÔNG `am force-stop` ═══════════
 *
 * Bệnh: đường mở ô golden (`VdAppHost.launchInto`) LUÔN `am force-stop <gói>` rồi `am start --display <vd>`. Force-stop có lý do
 * thật — `am start` khi app còn task toàn màn ở display 0 có thể TÁI DÙNG task đó (app nhảy toàn màn: lỗi Gmail) — nhưng nó
 * giết mọi thứ của gói: [ĐO máy ảo QA 2.89] YT Music đang phát, đặt vào ô ⇒ tắt nhạc. Spec `kachi-289-field-fixes.html` §A4.
 *
 * Luật (đường mới đứng TRƯỚC force-stop CHỈ khi tiền đề của nó ĐO được trên bản `am stack list` của chính lượt mở — CLAUDE.md §6):
 *
 * | Bản đọc | Đường |
 * |---|---|
 * | không đọc được | [Way.GOLDEN] — force-stop + mở như hôm nay |
 * | gói không có task nào, tiến trình KHÔNG sống (`pidof` rỗng) | [Way.GOLDEN] — app nguội: force-stop là 0 thiệt hại, đường cũ y byte |
 * | gói không có task nào mà tiến trình SỐNG (nhạc phát bằng dịch vụ, không activity) | [Way.START] — đúng lệnh mở của đường golden, BỎ force-stop + giấc 1 s; đọc lại: task lên màn ảo ô ⇒ xong, không ⇒ golden |
 * | task của gói ĐÃ ở màn ảo ô | [Way.IN_SLOT] — 0 lệnh |
 * | task mang dấu BEHIND-HOME, ẩn đúng hình S | [Way.GOLDEN] — R1.8 (`bringBackMarked`) chạy ngay trước và đã thử K8 trên đúng task này |
 * | task `standard` không ghim ở display khác (display 0 · màn ảo ô 7 · màn ảo khác) | [Way.BRING] — K8 `am start --display <vd> -n <comp>` |
 * | …nhưng ẩn ở display 0 mà màn nhà Kachi KHÔNG ở đỉnh (camera lùi / app khác) | [Way.GOLDEN] — cổng R1.8 (review lượt 6 [P2]): K8 dưới camera có thể làm app nổi lên che camera |
 * | chỉ có task ghim / loại lạ / component không an toàn | [Way.GOLDEN] — chưa đo cách K8 xử lý, giữ đường cũ |
 *
 * ## Vì sao [Way.START] an toàn khi KHÔNG có task — force-stop của đường golden có MỘT lý do: không để `am start` tái dùng task toàn
 * màn sẵn có ở display 0 (lỗi Gmail nhảy toàn màn, KDoc `VdAppHost.launchInto`). `am stack list` (mọi stack mọi display,
 * `getAllStackInfos`) không có task nào của gói ⇒ không có gì để tái dùng ⇒ `am start --display <vd>` dựng task MỚI trên màn ảo
 * ô. Ca đó chính là ca QA: [ĐO máy ảo QA 04/10, backlog `SLOT-PLACE-KEEPS-MUSIC`] YT Music phát qua phím media (không task) ⇒
 * đặt vào ô ⇒ force-stop ⇒ tắt nhạc. [SUY] app đẩy activity đầu sang display 0 (không chạy được trên màn ảo) ⇒ đọc lại thấy
 * task ngoài ô ⇒ golden như hôm nay (🚗 kiểm trên xe).
 *
 * ## Vì sao K8 (đưa task sang) mà không "mở mới" — [ĐO nguồn A10 r47] `ActivityStarter.java:2164-2171`
 * `am start --display <vd>` tìm thấy task sẵn có của app ở display KHÁC ⇒ `reparent(…, "reparentToDisplay")` — task SANG ô, tiến
 * trình giữ nguyên (không giết dịch vụ phát nhạc). Đổi display ⇒ activity dựng lại ([ĐO xe 05/10] YouTube `am_relaunch_resume_activity`
 * — player trong activity dừng; YT Music phát bằng dịch vụ [SUY mạnh: relaunch activity không chạm dịch vụ]). Owner chấp nhận
 * relaunch (brief 2.89 A4). K8 sau khi gửi đọc lại; không vào ô ⇒ golden (`SlotReturnSequence.k8`, cùng luật R1.8 [ĐO máy ảo T-M6 5/5]).
 *
 * ## Bốn câu CLAUDE.md §4 cho K8 ở đây
 *  1. **Display nào** — đúng màn ảo của ô đang mở app (`vd ≥ 1`, host của ô vừa tạo/giữ); `vd < 1` ⇒ golden (không lệnh mới).
 *  2. **App nào** — đúng gói ô đang mở; task chọn là task của CHÍNH gói đó (`StackEntry.pkg`), component lọc `safeComponent`.
 *  3. **Loại stack nào** — chỉ `standard` bằng chữ, không ghim; home/recents/pinned không bao giờ.
 *  4. **Hoàn tác** — không hoàn tác (người dùng đã xin đặt app vào ô); K8 không ăn ⇒ golden như hôm nay; lên TRƯỚC display 0 ⇒ K12
 *     rồi golden (đường R1.8 sẵn có). Không trạng thái hệ thống bền nào (§5).
 */
object SlotOpenPlan {

    enum class Way { GOLDEN, IN_SLOT, BRING, START }

    /** [task] = task được chọn (BRING / IN_SLOT; GOLDEN có thể kèm để ghi nhật ký) · [why] = lý do ASCII-ngắn cho nhật ký. */
    data class Pick(val way: Way, val task: StackEntry?, val why: String)

    /**
     * [entries] = bản đọc `am stack list` của lượt mở (`null` / rỗng = đọc hỏng) · [vd] màn ảo ô · [pkg] gói ô đang mở ·
     * [marks] dấu bền BEHIND-HOME (`BehindMarks`) · [homeComps] các dạng component màn nhà Kachi (`DefaultHome.shownComponents`) ·
     * [alive] = tiến trình của gói đang sống (`pidof`) — CHỈ được hỏi khi bản đọc không có task nào của gói (một lệnh shell).
     */
    fun pick(
        entries: List<StackEntry>?, vd: Int, pkg: String, marks: Map<Int, String>, homeComps: Collection<String>,
        alive: () -> Boolean = { false },
    ): Pick {
        if (vd < 1 || pkg.isBlank()) return Pick(Way.GOLDEN, null, "vd/pkg invalid")
        val e = entries?.takeIf { it.isNotEmpty() } ?: return Pick(Way.GOLDEN, null, "unread")
        val mine = e.filter { it.pkg == pkg }
        if (mine.isEmpty()) return if (alive()) Pick(Way.START, null, "no task, process alive") else Pick(Way.GOLDEN, null, "no task")
        mine.firstOrNull { it.displayId == vd }?.let { return Pick(Way.IN_SLOT, it, "already in slot") }
        SlotReturn.markedBehind(e, marks, pkg)?.let { return Pick(Way.GOLDEN, it, "marked behind (R1.8 tried)") }
        val t = mine.firstOrNull(::movable) ?: return Pick(Way.GOLDEN, mine.first(), "no movable task")
        if (t.displayId == BehindHomePlan.MAIN_DISPLAY && !t.visible && !BehindHomePlan.homeOnTop(e, homeComps)) {
            return Pick(Way.GOLDEN, t, "hidden on display 0, home not on top")
        }
        return Pick(Way.BRING, t, "task ${t.taskId} on display ${t.displayId}")
    }

    /** Task đưa được: `standard` bằng CHỮ (chuỗi trống không tính — cùng rào `SlotClosePlan`), không ghim, component an toàn. */
    private fun movable(t: StackEntry): Boolean =
        t.activityType == BehindHomePlan.STANDARD && !t.isPinned && BehindHomePlan.safeComponent(t.comp)
}
