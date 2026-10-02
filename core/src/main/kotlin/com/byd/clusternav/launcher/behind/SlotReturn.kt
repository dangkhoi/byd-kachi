package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.camera.CameraGuard
import com.byd.clusternav.modules.clustercast.StackEntry
import com.byd.clusternav.modules.clustercast.StackParse

/**
 * ═══ Ô ⇄ TOÀN MÀN mà KHÔNG giết app: K7 (ô → display 0) · K8 (display 0 ẩn → ô) — quyết định thuần (`:core`) ══════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.2.6 (R1.8) · §4.4.3 dòng 9–10 · §4.9 K7/K8 · §5 T-M2/T-M6.
 * Số đo [ĐO máy ảo Android 10 02/10, `docs/diagnostics/behind-home-emulator-2026-10-01/finish/`]:
 *  - **T-M2 (a)** Intent từ HOME (`AppOpener.openByIntent`, cầu kiểm thử `open`) với app ĐANG ở màn ảo ô: `am_new_intent`
 *    TẠI CHỖ, task ở lại màn ảo, display 0 không đổi — 4/4 app (VietMap, YT Music, Đồng hồ, Kiki) ⇒ Intent KHÔNG tách
 *    được app ra khỏi ô (bảng chạm dòng 10: không kênh ⇒ hỏi quyền).
 *  - **T-M2 (b)** K7 ([detachCmd]): `wm_task_moved [task,1,0]` + `am_focused_stack [0,0,…,reparentToDisplay]`, task toàn
 *    màn 1920×1080 trên display 0, pid giữ — 4/4 app.
 *  - **T-M6** K8 ([BehindHomePlan.bringToFrontCmd]) từ một stack display 0 ẨN về màn ảo ô: pid giữ, 0
 *    `am_focused_stack [0,0,…]`, 0 mẫu `visible=true` trên display 0 — 5/5 (VietMap + YT Music trong S của Kachi, Waze
 *    `standard`, Kiki `singleInstance`, Đồng hồ sau K7 + HOME).
 *
 * Không app nào đỏ trên máy ảo. App đỏ trên ROM khác lộ ra ở bản đọc NGAY SAU lệnh ([afterK8]) ⇒ bên gọi lùi về đường mở ô
 * golden (force-stop + mở lại) — luật đo cho từng lượt, không bảng tên gói (CLAUDE.md §7, R-nf3).
 */
object SlotReturn {

    /** Kết quả lượt đưa về ô. Bên gọi: [IN_SLOT] xong · [KEEP] không làm gì · [GONE] thẻ "app đã đóng" · còn lại ⇒ golden. */
    enum class Back { IN_SLOT, KEEP, GONE, NOT_BEHIND, FRONT_RESTORED, GOLDEN, UNREAD }

    /** Task đang ở đâu so với ô (màn ảo `vd`). */
    enum class Where { IN_SLOT, FRONT_MAIN, HIDDEN_MAIN, ELSEWHERE, GONE }

    /**
     * K7 — ĐÚNG chuỗi đã đo ở T-M2(b) (`-f 0x20000000` = SINGLE_TOP, chế độ cửa sổ 1). Không nháy đơn: rào [CameraGuard] cấm
     * `'` (chuỗi có thể nằm trong `sh -c '…'`); component lọc bằng [BehindHomePlan.safeComponent], `$` của lớp lồng thoát
     * thành `\$` (cùng luật `TripPlan.launchCmd`).
     */
    fun detachCmd(comp: String): String {
        require(BehindHomePlan.safeComponent(comp)) { "component không hợp lệ: $comp" }
        return "am start --display ${BehindHomePlan.MAIN_DISPLAY} --windowingMode 1 -f 0x20000000 " +
            "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n ${comp.replace("$", "\\$")}"
    }

    /**
     * K7 qua rào: chỉ chạy khi màn nhà Kachi ([homeComps]) đang hiện trên display 0; dấu hiệu camera [sig] đã biết ⇒ thấy
     * camera thì không chạy ([CameraGuard.onHomeUnlessCamera]). Bốn câu CLAUDE.md §4: display 0 · đúng app của ô người dùng
     * vừa chạm · task `standard` sẵn có của app đó (reparentToDisplay, không stack hệ thống) · hoàn tác = K8.
     */
    fun guardedDetachCmd(sig: String?, homeComps: List<String>, comp: String): String =
        CameraGuard.onHomeUnlessCamera(sig, homeComps, detachCmd(comp))

    /** Task `standard` của [pkg] trên màn ảo ô [vd] — đối tượng của K7. */
    fun slotTask(entries: List<StackEntry>, vd: Int, pkg: String): StackEntry? =
        if (vd < 1) null
        else entries.firstOrNull { it.displayId == vd && it.pkg == pkg && it.activityType == BehindHomePlan.STANDARD && !it.isPinned }

    /**
     * Task [taskId] đang ở đâu: trên ô [vd] · trước display 0 (đang hiện) · display 0 ẨN đúng hình S (stack chỉ gồm task
     * của chính gói đó, `standard` bằng chữ, toàn màn, không ghim, không phải đỉnh) · nơi khác (display khác / stack lẫn) ·
     * mất. Bản đọc rỗng ⇒ [Where.GONE] — bên gọi tách riêng ca "không đọc được" trước khi gọi.
     */
    fun whereIs(entries: List<StackEntry>, taskId: Int, vd: Int): Where {
        val t = entries.firstOrNull { it.taskId == taskId } ?: return Where.GONE
        return when {
            t.displayId == vd -> Where.IN_SLOT
            t.displayId != BehindHomePlan.MAIN_DISPLAY -> Where.ELSEWHERE
            t.visible -> Where.FRONT_MAIN
            hiddenAlone(entries, t) -> Where.HIDDEN_MAIN
            else -> Where.ELSEWHERE
        }
    }

    /**
     * R1.8 — task của [pkg] mà Kachi ĐÃ đẩy ra sau màn nhà ([marks] = dấu bền `BehindMarks`, đúng id + đúng gói) và đang
     * nằm ẩn trên display 0 đúng hình S. Không dấu ⇒ `null` ⇒ đường golden hôm nay (app người dùng tự mở toàn màn rồi bấm
     * HOME KHÔNG đổi hành vi — CLAUDE.md §6).
     */
    fun markedBehind(entries: List<StackEntry>, marks: Map<Int, String>, pkg: String): StackEntry? =
        entries.firstOrNull { marks[it.taskId] == pkg && it.pkg == pkg && whereIs(entries, it.taskId, NO_VD) == Where.HIDDEN_MAIN }

    /** Đọc lại sau K8: về ô ⇒ [Back.IN_SLOT]; lên TRƯỚC display 0 ⇒ [Back.FRONT_RESTORED] (bên thi hành bắn K12); khác ⇒ golden. */
    fun afterK8(after: List<StackEntry>, taskId: Int, vd: Int): Back = when (whereIs(after, taskId, vd)) {
        Where.IN_SLOT -> Back.IN_SLOT
        Where.FRONT_MAIN -> Back.FRONT_RESTORED
        else -> Back.GOLDEN
    }

    private fun hiddenAlone(entries: List<StackEntry>, t: StackEntry): Boolean =
        BehindHomePlan.topStackId(entries, BehindHomePlan.MAIN_DISPLAY) != t.stackId &&
            entries.filter { it.stackId == t.stackId }.all {
                it.pkg == t.pkg && !it.visible && !it.isPinned &&
                    it.activityType == BehindHomePlan.STANDARD && it.mode == BehindHomePlan.FULLSCREEN
            }

    /** Không phải id màn ảo nào (≥ 1 mới là ô) — để [whereIs] chỉ phân loại display 0. */
    private const val NO_VD = -1
}

/**
 * ═══ CHUỖI K7 / K8 (chặn, chạy trên luồng nền — `:app` đưa lên luồng `kachi-behind` hoặc luồng mở app của ô) ═══════════
 *
 * Mọi bước đọc lại `am stack list` ngay sau lệnh (CLAUDE.md §5 — quyết bằng sự thật, không bằng cờ RAM). [goHomeCmd] =
 * `AccessibilityRebind.GO_HOME_UNLESS_CAMERA` (K12, byte 2.83), truyền vào để `:core/launcher` không phụ thuộc navaccess.
 */
class SlotReturnSequence(
    private val sh: (String) -> String,
    private val goHomeCmd: String,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    /**
     * Kết quả K7. [taskId] khác `null` ⇒ app đang toàn màn trước display 0 (hoặc đã rời ô sang display 0 mà màn nhà không ở
     * đỉnh — camera / app khác ở trên, hình chưa đo — xử lý như toàn màn). `null` ⇒ không ở toàn màn; [back] khác `null`
     * ⇒ K7 ĐÃ đưa app rời ô nhưng nó không còn ở trước display 0 khi đọc lại, và chuỗi đã xử lý xong như một lượt về ô
     * ([SlotReturn.Back.IN_SLOT] về ô · [SlotReturn.Back.GONE] app đã đóng · còn lại ⇒ bên gọi đi đường golden).
     */
    data class Detached(val taskId: Int?, val line: String, val back: SlotReturn.Back? = null)
    data class Returned(val result: SlotReturn.Back, val taskId: Int?, val line: String)

    private fun read(): List<StackEntry> = StackParse.parse(runCatching { sh(BehindHomePlan.LIST_CMD) }.getOrDefault(""))

    /** Bảng chạm dòng 9 — K7 (qua rào) cho app [pkg] của ô [vd], rồi đọc lại tới khi thấy task trước display 0. */
    fun detach(vd: Int, pkg: String, sig: String?, homeComps: List<String>): Detached {
        val tag = "detach vd=$vd $pkg"
        val t = SlotReturn.slotTask(read(), vd, pkg) ?: return Detached(null, "$tag → không thấy task của app trên ô, 0 lệnh")
        if (!BehindHomePlan.safeComponent(t.comp) || homeComps.isEmpty()) return Detached(null, "$tag → component/HOME lạ, 0 lệnh")
        sh(SlotReturn.guardedDetachCmd(sig, homeComps, t.comp))
        var where = SlotReturn.Where.IN_SLOT
        var last: List<StackEntry> = emptyList()
        for (i in 0 until DETACH_READS) {
            sleep(STEP_MS)
            last = read()
            where = SlotReturn.whereIs(last, t.taskId, vd)
            if (where == SlotReturn.Where.FRONT_MAIN) return Detached(t.taskId, "$tag → FULL task=${t.taskId} đọc=${i + 1}")
        }
        // Review lượt 4 [P2]: K7 ĐÃ đưa app rời ô nhưng mọi lần đọc lại đều không thấy nó ở trước — người dùng bấm HOME/Back
        // trước lần đọc thấy toàn màn (fixture `tm2-detached-home`: app ẩn ngay dưới màn nhà). Bên gọi chỉ đo lại ô thì không
        // bao giờ thấy app sống ở đó (`SlotLiveness` mới chưa từng thấy sống ⇒ không kết luận) ⇒ ô ĐEN, không thẻ, không đường
        // về. "HOME = thu lại" ⇒ K8 đưa về ô ngay, cùng phép của [bringBack].
        // Review lượt 5 [P2]: K8 tại chỗ CHỈ khi màn nhà Kachi đang là đỉnh display 0 — đúng điều kiện của [bringBack] (chạy từ
        // `onStart` màn nhà). Camera / app khác ở trên ⇒ KHÔNG K8 lúc này: trên ROM mà K8 làm app nổi lên display 0 (nhánh
        // FRONT_RESTORED, chưa đo trên xe) app sẽ che màn camera, và K12 sau đó đưa màn nhà lên đè camera. Ca đó rơi xuống dưới.
        if (where == SlotReturn.Where.HIDDEN_MAIN && BehindHomePlan.homeOnTop(last, homeComps)) {
            val back = k8(tag, vd, last.first { it.taskId == t.taskId })
            return Detached(null, "${back.line} (rời ô nhưng không thấy ở trước — HOME/Back trước lần đọc)", back.result)
        }
        if (where == SlotReturn.Where.GONE && last.isNotEmpty()) {
            return Detached(null, "$tag → app đã đóng sau K7 task=${t.taskId}", SlotReturn.Back.GONE)
        }
        // Review lượt 5 [P2/P3]: K7 đã đưa app rời ô sang display 0 nhưng không ở trước, mà cũng không "ẩn dưới màn nhà" —
        // ẩn dưới camera / app khác, hoặc ngoài hai hình đã đo (đỉnh mà chưa `visible`: ROM chậm hơn 8 × 250 ms, màn tắt) ⇒ đo
        // lại ô cũng là ô đen câm như ca trên ⇒ coi như đang toàn màn: 0 lệnh thêm, thẻ ở ô, K8 khi màn nhà hiện lại
        // ([bringBack]: còn trước / nơi khác ⇒ giữ thẻ · ẩn đúng hình S ⇒ K8 · mất ⇒ "đã đóng"). Camera tắt ⇒ app toàn màn
        // hiện lại đúng như người dùng vừa xin.
        if (last.any { it.taskId == t.taskId && it.displayId == BehindHomePlan.MAIN_DISPLAY }) {
            return Detached(t.taskId, "$tag → rời ô, ở display 0 ($where, không K8 tại chỗ) ⇒ coi như toàn màn task=${t.taskId}")
        }
        return Detached(null, "$tag → không lên toàn màn ($where — rào chặn / màn nhà không hiện) task=${t.taskId}")
    }

    /**
     * Về lại ô: task [taskId] (đã tách bằng K7) → K8 → đọc lại. Màn nhà vừa hiện lại mà hệ chưa hạ cờ `visible` của app
     * ⇒ đọc lại tối đa [FRONT_TRIES] lượt trước khi kết luận "người dùng đang dùng nó" ([SlotReturn.Back.KEEP]).
     *
     * Review lượt 6 [P2]: K8 CHỈ khi màn nhà Kachi ([homeComps]) đang là đỉnh display 0 — CÙNG cổng với K8 tại chỗ của
     * [detach]. Camera lùi lên giữa lúc bấm HOME và lần đọc này (lùi xe ngay sau khi thoát app) ⇒ app ẩn dưới camera; trên
     * ROM mà K8 làm app nổi lên display 0 (`FRONT_RESTORED`, OC-9 chưa đo trên xe) app sẽ che camera rồi K12 đưa màn nhà đè
     * lên camera. Không ở đỉnh ⇒ [SlotReturn.Back.KEEP]: giữ thẻ, 0 lệnh; màn nhà hiện lại / chạm thẻ ⇒ lượt sau K8.
     */
    fun bringBack(vd: Int, taskId: Int, homeComps: Collection<String>): Returned {
        val tag = "return vd=$vd task=$taskId"
        var entries = read()
        var where = SlotReturn.whereIs(entries, taskId, vd)
        var tries = 1
        while (entries.isNotEmpty() && where == SlotReturn.Where.FRONT_MAIN && tries < FRONT_TRIES) {
            sleep(STEP_MS); entries = read(); where = SlotReturn.whereIs(entries, taskId, vd); tries++
        }
        if (entries.isEmpty()) return Returned(SlotReturn.Back.KEEP, taskId, "$tag → không đọc được, giữ nguyên")
        return when (where) {
            SlotReturn.Where.IN_SLOT -> Returned(SlotReturn.Back.IN_SLOT, taskId, "$tag → đã ở ô, 0 lệnh")
            SlotReturn.Where.FRONT_MAIN, SlotReturn.Where.ELSEWHERE -> Returned(SlotReturn.Back.KEEP, taskId, "$tag → $where, giữ nguyên (đọc=$tries)")
            SlotReturn.Where.GONE -> Returned(SlotReturn.Back.GONE, taskId, "$tag → app đã đóng")
            SlotReturn.Where.HIDDEN_MAIN ->
                if (BehindHomePlan.homeOnTop(entries, homeComps)) k8(tag, vd, entries.first { it.taskId == taskId })
                else Returned(SlotReturn.Back.KEEP, taskId, "$tag → ẩn nhưng màn nhà không ở đỉnh (camera / app khác), giữ nguyên, 0 lệnh")
        }
    }

    /**
     * R1.8 — app [pkg] Kachi đã đẩy ra sau màn nhà ([marks]) ⇒ K8 về ô [vd] thay cho force-stop + mở lại.
     *
     * Review lượt 6 [P2]: K8 CHỈ khi màn nhà Kachi ([homeComps]) đang là đỉnh display 0 (cùng lẽ [bringBack]) — ô dựng lại
     * đúng lúc camera lùi đang lên (nổ máy rồi lùi ngay) ⇒ [SlotReturn.Back.GOLDEN]: bên gọi đi đường mở ô golden
     * (force-stop + `am start --display <vd>`, đường đã chạy ngoài xe từ 2.83), không K8 dưới camera.
     */
    fun bringBackMarked(vd: Int, pkg: String, marks: Map<Int, String>, homeComps: Collection<String>): Returned {
        val tag = "r1.8 vd=$vd $pkg"
        val entries = read()
        if (entries.isEmpty()) return Returned(SlotReturn.Back.UNREAD, null, "$tag → không đọc được ⇒ golden")
        val t = SlotReturn.markedBehind(entries, marks, pkg) ?: return Returned(SlotReturn.Back.NOT_BEHIND, null, "$tag → không có task mang dấu sau màn nhà ⇒ golden")
        if (!BehindHomePlan.homeOnTop(entries, homeComps)) {
            return Returned(SlotReturn.Back.GOLDEN, t.taskId, "$tag → màn nhà không ở đỉnh (camera / app khác) ⇒ golden, 0 lệnh K8")
        }
        return k8(tag, vd, t)
    }

    private fun k8(tag: String, vd: Int, t: StackEntry): Returned {
        if (vd < 1 || !BehindHomePlan.safeComponent(t.comp)) return Returned(SlotReturn.Back.GOLDEN, t.taskId, "$tag → vd/component lạ ⇒ golden")
        sh(BehindHomePlan.bringToFrontCmd(vd, t.comp))
        var res = SlotReturn.Back.GOLDEN
        for (i in 0 until RETURN_READS) {
            sleep(STEP_MS)
            res = SlotReturn.afterK8(read(), t.taskId, vd)
            if (res != SlotReturn.Back.GOLDEN) break
        }
        // Lên TRƯỚC display 0 thay vì vào ô ⇒ đưa màn nhà lên lại qua rào camera (K12), rồi bên gọi đi đường golden.
        if (res == SlotReturn.Back.FRONT_RESTORED) runCatching { sh(goHomeCmd) }
        return Returned(res, t.taskId, "$tag → K8 ${t.comp} → $res")
    }

    companion object {
        const val STEP_MS = 250L
        const val DETACH_READS = 8
        const val RETURN_READS = 8
        const val FRONT_TRIES = 6
    }
}
