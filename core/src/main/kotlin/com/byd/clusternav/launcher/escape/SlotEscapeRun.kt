package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.escape.SlotEscapePlan.Adopt
import com.byd.clusternav.launcher.escape.SlotEscapePlan.Step
import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.modules.clustercast.StackEntry
import com.byd.clusternav.modules.clustercast.StackParse

/**
 * ═══ 2.98 · R7 — THI HÀNH một lượt (thuần JVM, nhận kênh shell; khuôn `FloatingOrphanSweep`) ═══════════════════════════
 *
 * Mọi lệnh đổi trạng thái đứng SAU một bản đọc `am stack list` của chính lượt đó và đi qua [SlotEscapePlan] (guard ở tầng thi
 * hành — CLAUDE.md §5). Dấu bền ghi TRƯỚC lệnh đổi chế độ, xoá SAU lệnh trả. Bên gọi (`:app` `SlotEscape`) chạy trên MỘT luồng
 * nền và bắt `IOException`/`RuntimeException` của kênh: ném giữa chừng ⇒ dấu còn ⇒ lượt đối chiếu sau xử lý tiếp.
 *
 * ## Chi phí lệnh shell mỗi sự kiện (cột "Chi phí" spec 2.98 R7)
 *  - [adopt]: 1 đọc (`am stack list`) + 1 thử mã (chỉ đọc) + 2 lệnh (mode 5 · resize) + 1 đọc lại = 5. Lần đầu mỗi tiến trình hệ
 *    dời khung xuống dưới đỉnh ổn định ([EscapeFit]) ⇒ +1 resize +1 đọc = 7. Hỏng ⇒ +1–2 (trả).
 *  - [reconcile]: 0 khi không có dấu; có dấu ⇒ 1 đọc + (0–1 resize) + (0–1 đưa lại lên) + (1–2 mỗi dấu phải trả). R10: đưa lại lên
 *    = `am task focus`, +1 đọc xác nhận MỘT lần mỗi tiến trình; focus hỏng ⇒ +1 `dumpsys activity recents` (+0–1 `am start`). Soát Pass 9: có lớp
 *    che mà chưa biết đỉnh ổn định và vừa kéo khung ⇒ +1 đọc +0–1 resize, MỘT lần mỗi tiến trình ([TopMemory.probed]).
 *  - [claims]: 0 khi không có dấu; có dấu ⇒ 1 đọc.
 */
class SlotEscapeRun(
    private val sh: (String) -> String,
    private val codes: TaskBinderCodes,
    private val cameraSig: String?,
    private val homeComps: List<String>,
    private val store: MarkerStore,
    /**
     * Soát Pass 7: Kachi có đang là MÀN HÌNH CHÍNH MẶC ĐỊNH của hệ không (`DefaultHome.isCurrent == true`). `HomeActivityCmd.GO_HOME`
     * mở home MẶC ĐỊNH — Kachi cài kiểu thường (alias HOME tắt) thì lệnh đó mở launcher BYD đè lên Kachi; `false` ⇒ các bước
     * "Home trước" bị bỏ (trả: chỉ mode 1 — app toàn màn ở trên như trước R7; không bao giờ mở launcher khác).
     */
    private val homeAllowed: Boolean = true,
    /**
     * Owner 09/10 #2 — đẩy thanh tiêu đề freeform lên TRÊN ô bao nhiêu px ([EscapeFit.taskRect]); 0 = khung task = khung ô (không có
     * lớp che — dịch vụ trợ năng chưa nối — thì thanh tiêu đề phải nằm trong ô, không đè đầu màn nhà).
     */
    private val liftPx: Int = 0,
    /** Đỉnh ổn định đã học ([EscapeFit.learnedMinTop]) — MỘT bản cho cả tiến trình (bên gọi giữ), dùng chung giữa các lượt. */
    private val tops: TopMemory = TopMemory(),
) {

    /**
     * Đỉnh ổn định display 0 đã học từ một lần hệ dời khung task ([EscapeFit]); `null` = chưa học. [probed] (soát Pass 9) = lượt
     * đối chiếu đã tốn MỘT lần đọc lại để học sau khi kéo khung — chỉ một lần mỗi tiến trình, học được hay không.
     */
    class TopMemory {
        @Volatile var minTop: Int? = null
        @Volatile var probed = false
        /** R10 — `am task focus` đã xác nhận đưa task lên đỉnh (`true`) / hỏng trên ROM này (`false` ⇒ đi thẳng đường lùi); `null` = chưa thử. */
        @Volatile var focusWorks: Boolean? = null
    }

    private fun want(slot: PxRect): PxRect = EscapeFit.taskRect(slot, liftPx, tops.minTop)

    /** [actual] (khung task đọc lại) là [asked] bị hệ dời xuống ⇒ học đỉnh ổn định; trả `true` khi vừa học được. */
    private fun learn(asked: PxRect, actual: PxRect?): Boolean {
        if (liftPx <= 0) return false
        val top = EscapeFit.learnedMinTop(asked, actual) ?: return false
        tops.minTop = top
        return true
    }

    /** Dấu bền ([EscapeMarkers]) — ghi phải ĐỒNG BỘ (`commit`) vì đứng trước lệnh đổi trạng thái. */
    interface MarkerStore {
        fun read(): List<EscapeMarker>
        fun write(list: List<EscapeMarker>): Boolean
    }

    /**
     * Kết quả nhận: [ok] = app đã ở đúng khung ô, freeform; [onTop] = lệnh nhận đã đưa task lên ĐỈNH display 0 (toTop — bên gọi chỉ
     * hiện lớp che khi đó; `false` = một app khác đang ở đỉnh, task nằm dưới nó); [line] = một dòng nhật ký `KachiEscape`.
     */
    data class Adopted(val ok: Boolean, val task: Int?, val line: String, val onTop: Boolean = false, val rect: PxRect? = null)

    /**
     * Kết quả đối chiếu: [onTop] = task đang được quản đã/đang ở trên màn nhà (bên gọi hiện lớp che theo khung) · [gone] = dấu mà
     * task đã mất trong khi ô còn hiện gói (bên gọi cho ô đi luật hoàn ô) · [released] = gói vừa trả toàn màn.
     */
    data class Reconciled(
        val onTop: List<EscapeMarker>,
        val gone: List<EscapeMarker>,
        val released: List<String>,
        val line: String,
    ) {
        companion object { val NOTHING = Reconciled(emptyList(), emptyList(), emptyList(), "no-markers") }
    }

    private fun read(): String? = sh(SlotEscapePlan.LIST_CMD).takeIf { "Stack id=" in it }

    /** Nhận app [pkg] vừa thoát ô [slot] (khung ô [rect] trên display 0, mật độ [densityDpi]). */
    fun adopt(slot: Int, pkg: String, shownPkg: String?, rect: PxRect, densityDpi: Int): Adopted {
        val before = read() ?: return Adopted(false, null, "adopt $pkg ô $slot ⇒ dừng: NO_READ")
        val entries = StackParse.parse(before)
        val d = SlotEscapePlan.checkAdopt(entries, pkg, shownPkg, rect, densityDpi, homeComps)
        if (d is Adopt.Stop) return Adopted(false, null, "adopt $pkg ô $slot ⇒ dừng: ${d.why}")
        val go = d as Adopt.Go
        val t = go.task.taskId
        // Thử bảng mã bằng lệnh CHỈ ĐỌC trước mọi lệnh ghi — mã lệch đời ROM thì không được bắn mã 89 (CLAUDE.md §3).
        val probe = TaskBinderCodes.parseRect(sh(codes.boundsCmd(t)))
            ?: return Adopted(false, t, "adopt $pkg ô $slot task $t ⇒ dừng: mã ${codes.getTaskBounds} không trả khung (bảng mã lệch?)")
        // Dấu TRƯỚC lệnh đổi (CLAUDE.md §5) — không ghi được thì không đổi gì. Dấu giữ khung TASK (có thể cao hơn ô — [EscapeFit]).
        var asked = want(rect)
        if (!store.write(EscapeMarkers.upsert(store.read(), EscapeMarker(pkg, slot, t, asked)))) {
            return Adopted(false, t, "adopt $pkg ô $slot task $t ⇒ dừng: không ghi được dấu")
        }
        val (mode, resize) = SlotEscapePlan.adoptCmds(codes, t, go.toTop, asked)
        if (!TaskBinderCodes.parseOk(sh(mode))) {
            // Mã 89 ném (SecurityException / không có hàm) ⇒ không có gì đã đổi: xoá dấu, không resize.
            store.write(EscapeMarkers.remove(store.read(), pkg))
            return Adopted(false, t, "adopt $pkg ô $slot task $t ⇒ dừng: mã ${codes.setTaskWindowingMode} trả ngoại lệ")
        }
        sh(resize)
        var after = read()
        // Hệ DỜI khung xuống dưới đỉnh ổn định (thanh tiêu đề không được lên trên vùng thanh trạng thái — [EscapeFit]) ⇒ học đỉnh
        // đó rồi xin lại khung đã trừ, đáy giữ đúng đáy ô: +1 resize +1 đọc, chỉ lần đầu mỗi tiến trình (lần sau [want] đã trừ sẵn).
        if (after != null && !SlotEscapePlan.verify(after, t, asked) && learn(asked, SlotEscapePlan.taskBounds(after, t))) {
            asked = want(rect)
            store.write(EscapeMarkers.upsert(store.read(), EscapeMarker(pkg, slot, t, asked)))
            sh(SlotEscapePlan.resizeCmd(t, asked))
            after = read()
        }
        if (after != null && SlotEscapePlan.verify(after, t, asked)) {
            return Adopted(true, t, "adopt $pkg ô $slot task $t ${go.task.mode}→freeform $asked (ô $rect) toTop=${go.toTop} (khung cũ $probe) ⇒ OK",
                onTop = go.toTop, rect = asked)
        }
        // Không khớp (freeform chưa có hiệu lực — cờ bật mà đầu xe chưa khởi động lại; hệ kẹp khung…) ⇒ trả ngay.
        val wasTop = BehindHomePlan.topVisibleStackId(entries, SlotEscapePlan.MAIN_DISPLAY) == go.task.stackId
        SlotEscapePlan.releaseCmds(codes, t, homeFirst = go.toTop && !wasTop && homeAllowed, cameraSig, homeComps).forEach { sh(it) }
        store.write(EscapeMarkers.remove(store.read(), pkg))
        return Adopted(false, t, "adopt $pkg ô $slot task $t ⇒ đọc lại không khớp $asked ⇒ trả toàn màn (mode 1)")
    }

    /**
     * Đối chiếu MỌI dấu với sự thật. [shownAt] = gói ô đang hiện · [installed] = gói còn cài · [slotRect] = khung ô hiện tại trên
     * display 0 (`null` = chưa đo được ⇒ không kéo lại) · [refront] = màn nhà Kachi vừa lên trước (onResume) ⇒ đưa task đang quản
     * lên trên nó.
     */
    fun reconcile(
        shownAt: (Int) -> String?,
        installed: (String) -> Boolean,
        slotRect: (Int) -> PxRect?,
        refront: Boolean,
    ): Reconciled {
        val markers = store.read()
        if (markers.isEmpty()) return Reconciled.NOTHING
        val out = read() ?: return Reconciled(emptyList(), emptyList(), emptyList(), "reconcile ⇒ NO_READ")
        val steps = SlotEscapePlan.reconcile(markers, StackParse.parse(out), shownAt, installed)
        var list = markers
        val onTop = ArrayList<EscapeMarker>()
        val gone = ArrayList<EscapeMarker>()
        val released = ArrayList<String>()
        val log = ArrayList<String>()
        for (s in steps) {
            when (s) {
                is Step.Keep -> {
                    var m = s.marker
                    val id = s.task.taskId
                    // Khung TASK THẬT trên chính bản đọc này (0 lệnh thêm). Soát Pass 9 [P2]: (1) tiến trình mới (BYD giết Kachi mỗi lần tắt
                    // máy) ⇒ gợi lại đỉnh ổn định từ dấu đang đúng khung ([EscapeFit.seedMinTop]) — không thì lượt này xin lại khung lý tưởng,
                    // hệ dời xuống (đáy lòi khỏi ô) và kẹt tới mốc đối chiếu kế; (2) hệ dời khung xuống giữ cỡ ⇒ học ([learn]).
                    val actual = SlotEscapePlan.taskBounds(out, id)
                    val slot = slotRect(m.slot)?.takeUnless { it.isEmpty }
                    if (tops.minTop == null && slot != null) EscapeFit.seedMinTop(m.rect, actual, slot, liftPx)?.let { tops.minTop = it }
                    learn(m.rect, actual)
                    // Kéo lại chỉ khi khung MUỐN khác DẤU (ô đổi khung · trợ năng nối/rời đổi [liftPx] · vừa học đỉnh) — không so với khung
                    // thật: hệ từ chối khung xin (kẹp cỡ · dời lên vì đáy ổn định — [EscapeFit.learnedMinTop] không học được) thì so với khung
                    // thật là mỗi mốc đối chiếu lại bắn một resize vô ích, mãi (soát Pass 9 [P2]). Không đo được ô ⇒ không kéo.
                    val now = slot?.let(::want)
                    if (now != null && now != m.rect) {
                        sh(SlotEscapePlan.resizeCmd(id, now))
                        m = m.copy(rect = now)
                        log += "${m.pkg} refit $now"
                        // Có lớp che mà chưa biết đỉnh ổn định ⇒ đọc lại MỘT lần mỗi tiến trình ([TopMemory.probed]) để học + xin lại khung đã
                        // trừ ngay trong lượt này (như [adopt]) — không để đáy task lòi khỏi ô tới mốc đối chiếu kế. +1 đọc +0–1 resize, một lần.
                        if (liftPx > 0 && tops.minTop == null && !tops.probed) {
                            tops.probed = true
                            val again = read()
                            if (again != null && learn(now, SlotEscapePlan.taskBounds(again, id))) {
                                val fixed = want(slot)
                                if (fixed != now) { sh(SlotEscapePlan.resizeCmd(id, fixed)); m = m.copy(rect = fixed); log += "${m.pkg} learned top ${tops.minTop} refit $fixed" }
                            }
                        }
                    } else if (now != null && actual != null && actual != now) {
                        log += "${m.pkg} hệ giữ $actual ≠ $now (không kéo lại)"
                    }
                    if (m != markers.firstOrNull { it.pkg == m.pkg }) { list = EscapeMarkers.upsert(list, m); store.write(list) }
                    var top = s.onTop
                    if (refront && !top) refront(s.task).let { (ok, note) -> top = ok; log += "${m.pkg} $note" }
                    if (top) onTop += m
                    log += "${m.pkg} keep task ${m.task} onTop=$top"
                }
                is Step.Release -> {
                    val homeFirst = s.homeFirst && homeAllowed
                    SlotEscapePlan.releaseCmds(codes, s.task.taskId, homeFirst, cameraSig, homeComps).forEach { sh(it) }
                    list = EscapeMarkers.remove(list, s.marker.pkg); store.write(list)
                    released += s.marker.pkg
                    log += "${s.marker.pkg} release task ${s.task.taskId} homeFirst=$homeFirst"
                }
                is Step.Forget -> { list = EscapeMarkers.remove(list, s.marker.pkg); store.write(list); log += "${s.marker.pkg} forget (đã gỡ)" }
                is Step.Gone -> { gone += s.marker; log += "${s.marker.pkg} gone" }
                is Step.Dormant -> log += "${s.marker.pkg} dormant"
                is Step.Wait -> log += "${s.marker.pkg} wait"
            }
        }
        return Reconciled(onTop, gone, released, "reconcile ${markers.size} dấu ⇒ ${log.joinToString(" · ")}")
    }

    /**
     * R10 — đưa task [task] (id từ CHÍNH bản đọc của lượt) lên trên màn nhà: `am task focus` ([SlotEscapePlan.focusCmd]). Lần đầu mỗi
     * tiến trình +1 `am stack list` xác nhận task đã lên đỉnh display 0 ([TopMemory.focusWorks]) — ROM có lệnh mà không có tác dụng
     * thì không được để lớp che dựng lên khung trống. Focus hỏng / không tác dụng ⇒ đường lùi `am start MAIN/LAUNCHER` CHỈ khi intent
     * gốc của task là MAIN/LAUNCHER (+1 `dumpsys activity recents`) — không thì không đưa lên (chồng instance tệ hơn). `true` = đã lên.
     */
    private fun refront(task: StackEntry): Pair<Boolean, String> {
        if (tops.focusWorks != false) {
            val cmd = SlotEscapePlan.focusCmd(task.taskId, cameraSig, homeComps) ?: return false to "refront: không lệnh"
            when (SlotEscapePlan.focusResult(sh(cmd))) {
                SlotEscapePlan.Focus.SKIPPED -> return false to "refront bỏ (rào màn nhà/camera)"
                SlotEscapePlan.Focus.OK -> {
                    if (tops.focusWorks == true) return true to "refront focus"
                    val after = read() ?: return false to "refront focus: đọc lại hỏng"
                    if (BehindHomePlan.topVisibleStackId(StackParse.parse(after), SlotEscapePlan.MAIN_DISPLAY) == task.stackId) {
                        tops.focusWorks = true
                        return true to "refront focus (xác nhận đỉnh)"
                    }
                    tops.focusWorks = false
                }
                SlotEscapePlan.Focus.FAILED -> tops.focusWorks = false
            }
        }
        if (!SlotEscapePlan.rootIsLauncher(sh(SlotEscapePlan.RECENTS_CMD), task.taskId)) {
            return false to "refront: focus không dùng được, intent gốc task ${task.taskId} không MAIN/LAUNCHER ⇒ không đưa lên"
        }
        val start = SlotEscapePlan.launcherFrontCmd(task.comp, cameraSig, homeComps) ?: return false to "refront: component lạ"
        sh(start)
        return true to "refront am start (lùi)"
    }

    /** Ô [slot] có task freeform đang sống của [pkg] do Kachi quản không ⇒ host KHÔNG mở lại app vào màn ảo. 0 lệnh khi không dấu. */
    fun claims(slot: Int, pkg: String): Boolean {
        val markers = store.read()
        if (markers.none { it.pkg == pkg && it.slot == slot }) return false
        val out = read() ?: return false
        return SlotEscapePlan.claims(markers, StackParse.parse(out), slot, pkg) != null
    }
}
