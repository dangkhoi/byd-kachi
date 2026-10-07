package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.behind.SlotReturn
import com.byd.clusternav.modules.clustercast.StackEntry
import com.byd.clusternav.modules.clustercast.StackParse

/**
 * ═══ L4 · D3(ii) — giao LINK cho app nhạc ĐANG Ở MỘT Ô bằng K4-VIEW, giữ màn nhà không bị che (thuần, chặn, `:core`) ════
 *
 * Chỉ tới đây khi phiên nhạc ĐO ĐƯỢC không nhận link ([TripMusicPlan.play] ⇒ `View`): app ở ô vừa được ô mở mà không có
 * phiên (YouTube nguội trên trang chủ — [ĐO máy ảo] e3) hoặc phiên không có `ACTION_PLAY_FROM_URI`. Owner 01/10: *"có trong
 * khung nào thì mở ở khung đó"* ⇒ link đi vào CHÍNH màn ảo của ô (`TripMusicPlan.viewCmd`).
 *
 * App trung chuyển có thể mở activity chính bằng NEW_TASK ⇒ app KHÔNG được mở lên màn ảo riêng tư của Kachi (A10 r47
 * `ActivityStackSupervisor.isCallerAllowedToLaunchOnDisplay` `:1067-1130`: màn ảo của uid khác + activity không
 * `FLAG_ALLOW_EMBEDDED` ⇒ từ chối) ⇒ hệ đặt nó lên display 0, TRƯỚC màn nhà ([ĐO máy ảo 02/10] T-M3 với YT Music:
 * `reparentToDisplay` khi task đã ở màn ảo). Khi đó, NGAY lần đọc thấy (không đợi phát): dấu bền mọi task của app trên
 * display 0 → K12 (rào camera, byte 2.83) → K8 đưa task về lại ô (`am start --display <vd> -n <comp>` — [ĐO] T-M6 5/5 app,
 * pid giữ, 0 tiêu điểm display 0). Màn nhà Kachi không ở đỉnh lúc đầu (camera / app khác) ⇒ không K12, không K8.
 *
 * Bốn câu CLAUDE.md §4 của lệnh mới (K4-VIEW) ở KDoc [TripMusicPlan.viewCmd]; K12/K8 là lệnh đã có (bảng §4.9).
 */
class TripMusicView(
    private val sh: (String) -> String,
    /** K12 — `AccessibilityRebind.GO_HOME_UNLESS_CAMERA`. */
    private val goHomeCmd: String,
    /** `DefaultHome.shownComponents` — "màn nhà ở đỉnh" nhận cả hai dạng in. */
    private val homeComps: Collection<String>,
    /** Ghi dấu bền `kachi_behind_marks` (`commit()`) — TRƯỚC K12 (CLAUDE.md §5). */
    private val mark: (Int, String) -> Boolean,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    enum class Result {
        /** Link đã vào ô, app ở lại ô. */
        STAYED,
        /** App thoát lên display 0 ⇒ K12 ⇒ K8 đưa về lại ô (đọc lại thấy ở ô). */
        RETURNED,
        /** App thoát lên display 0 ⇒ K12; không về được ô (rào / K8 không ăn) — app sống SAU màn nhà, có dấu. */
        BEHIND,
        /** Không thấy task của app trên ô ⇒ 0 lệnh. */
        NOT_IN_SLOT,
    }

    data class Outcome(val result: Result, val line: String)

    private fun read(): List<StackEntry> = StackParse.parse(runCatching { sh(BehindHomePlan.LIST_CMD) }.getOrDefault(""))

    /** [fullscreenExtra] — 2.96 · R10, xem [TripMusicPlan.viewCmd]. */
    fun inSlot(pkg: String, vd: Int, url: String, fullscreenExtra: String? = null): Outcome {
        val tag = "view-in-slot vd=$vd $pkg"
        val before = read()
        SlotReturn.slotTask(before, vd, pkg) ?: return Outcome(Result.NOT_IN_SLOT, "$tag → không thấy task của app trên ô, 0 lệnh")
        val homeWasTop = BehindHomePlan.homeOnTop(before, homeComps)
        sh(TripMusicPlan.viewCmd(vd, url, pkg, fullscreenExtra))
        for (i in 0 until READS) {
            sleep(STEP_MS)
            val r = read()
            if (!homeWasTop || !BehindHomePlan.fellFront(r, pkg)) continue
            val marked = BehindHomePlan.mainTasksOf(r, setOf(pkg)).count { runCatching { mark(it.taskId, it.pkg) }.getOrDefault(false) }
            runCatching { sh(goHomeCmd) }
            return back(tag, pkg, vd, "đọc=${i + 1} dấu=$marked K12")
        }
        return Outcome(Result.STAYED, "$tag → ở lại ô")
    }

    /**
     * Sau K12: màn nhà ở đỉnh + task của app nằm trên display 0 (DƯỚI màn nhà) ⇒ K8 về ô, đọc lại. Không đòi `visible=false`:
     * [ĐO máy ảo `m5a`, fixture `l4-view-after-k12`] ngay sau K12 task app còn `visible=true` (chuyển cảnh chưa xong) trong khi
     * stack màn nhà đã ở đỉnh — "dưới màn nhà" đọc bằng [BehindHomePlan.homeOnTop], không bằng cờ hiện.
     */
    private fun back(tag: String, pkg: String, vd: Int, note: String): Outcome {
        var after = read()
        var i = 0
        while (!BehindHomePlan.homeOnTop(after, homeComps) && i < HOME_READS) { sleep(STEP_MS); after = read(); i++ }
        val t = after.firstOrNull { it.pkg == pkg && it.displayId == BehindHomePlan.MAIN_DISPLAY && it.activityType == BehindHomePlan.STANDARD }
        if (t == null || !BehindHomePlan.homeOnTop(after, homeComps) || !BehindHomePlan.safeComponent(t.comp)) {
            return Outcome(Result.BEHIND, "$tag → thoát lên display 0 · $note · không K8 (task=${t?.taskId})")
        }
        sh(BehindHomePlan.bringToFrontCmd(vd, t.comp))
        for (i in 0 until READS) {
            sleep(STEP_MS)
            if (read().any { it.taskId == t.taskId && it.displayId == vd }) return Outcome(Result.RETURNED, "$tag → thoát · $note · K8 về ô")
        }
        return Outcome(Result.BEHIND, "$tag → thoát · $note · K8 không ăn — app sau màn nhà")
    }

    companion object {
        const val READS = 12
        const val STEP_MS = 250L

        /** Sau K12: đọc lại tối đa chừng này lượt chờ màn nhà lên đỉnh trước khi quyết K8. */
        const val HOME_READS = 4
    }
}
