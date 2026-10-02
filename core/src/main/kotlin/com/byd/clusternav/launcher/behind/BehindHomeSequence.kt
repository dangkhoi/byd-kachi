package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.FreeformLaunch
import com.byd.clusternav.launcher.ShellAppLauncher
import com.byd.clusternav.modules.clustercast.StackEntry
import com.byd.clusternav.modules.clustercast.StackParse

/**
 * ═══ BEHIND-HOME — CHUỖI thi hành (thuần, chặn, `:core`) ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R0.1–R0.5 · §4.2 (A4). Mọi quyết định do [BehindHomePlan];
 * lớp này chỉ nối các bước theo ĐÚNG thứ tự đã đo và đọc lại sau mỗi lệnh. Phần chạm Android (mở/gỡ activity giữ chỗ,
 * hỏi `PackageManager`) đi qua [AnchorPort] ⇒ chuỗi chạy off-device với shell ghi âm + fixture nguyên văn
 * (`BehindHomeSequenceTest`). Bên `:app` (`BehindHomeRunner`) chỉ cấp luồng + mutex + [AnchorPort] thật.
 *
 * Chạy trên một luồng nền DUY NHẤT của tiến trình (mutex — R-nf4): chuỗi có ngủ chờ, KHÔNG gọi trên luồng chính.
 *
 * ## Mọi đường hỏng đều lùi về O1
 * Không đẩy được ⇒ A ở lại dưới B trong màn ảo của ô (sống, ẩn; chết cùng màn ảo theo cờ 256). Không có đường hỏng
 * nào để lại stack giữ chỗ: gỡ ở mọi lối ra ([finish]). Màn nhà mất đỉnh sau lệnh ⇒ [goHomeCmd] (rào camera K12).
 */
class BehindHomeSequence(
    private val sh: (String) -> String,
    private val anchor: AnchorPort,
    private val selfPkg: String,
    /** K12 — `AccessibilityRebind.GO_HOME_UNLESS_CAMERA` (byte 2.83), truyền vào để `:core/launcher` không phụ thuộc navaccess. */
    private val goHomeCmd: String,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    /** Các dạng in của màn nhà Kachi (`DefaultHome.shownComponents`) — để [BehindHomePlan.homeOnTop] nhận cả màn nhà `standard`. */
    private val homeComps: Collection<String> = emptyList(),
) {

    /** Phần Android của chuỗi — `BehindHomeRunner.AndroidAnchor` ở `:app`, bản giả trong test. */
    interface AnchorPort {
        /** `pkg/cls` của activity giữ chỗ, đúng dạng `am stack list` in (`ComponentName.flattenToString`). */
        val component: String

        /** Mở activity giữ chỗ (NEW_TASK · MULTIPLE_TASK · khoá [BehindHomePlan.AVOID_MOVE_TO_FRONT] · display 0). */
        fun start(): Boolean

        /** Gỡ MỌI task giữ chỗ của Kachi (`AppTask.finishAndRemoveTask`) — kể cả mồ côi của lượt/tiến trình trước. */
        fun removeAll(): Int

        /** App hệ thống (`ApplicationInfo.FLAG_SYSTEM`); không hỏi được ⇒ `true` (an toàn: không đẩy). */
        fun isSystemApp(pkg: String): Boolean

        /** Ghi dấu bền [BehindMarks] (`commit()`) — TRƯỚC `move-task` (CLAUDE.md §5). `false` ⇒ không được đẩy. */
        fun markBehind(taskId: Int, pkg: String): Boolean

        /** Gỡ dấu của task không ra được sau màn nhà. */
        fun unmarkBehind(taskId: Int)
    }

    /**
     * [ALREADY_RUNNING] (nhóm B, R1.5 dòng 12 / R2.4): X đã có TASK trong `am stack list` — đang chạy toàn màn, sau màn
     * nhà, trong một ô, hay đang chiếu cụm đều ra đây ⇒ 0 lệnh đổi cửa sổ (dàn lại một app đang có cửa sổ là kéo nó khỏi
     * chỗ người dùng đang dùng). Không phải lỗi: bên thi hành không đếm nó vào `BEHIND_FAIL`.
     *
     * Đo bằng TASK, không bằng `pidof` (review lượt 2 [P2], [ĐO máy ảo 02/10 `e2e/r2-alias-trip`]): widget YT Music ở ô 2
     * làm hệ bật tiến trình YT Music bằng broadcast (`am_proc_start … broadcast … MusicWidgetProvider`) 3,6 s trước bước
     * nhạc ⇒ `pidof` có ⇒ chuyến bỏ qua, nhạc KHÔNG BAO GIỜ phát. Tiến trình không có task (widget [ĐO]; dịch vụ duyệt nhạc
     * mà đầu xe bind [SUY]) không có cửa sổ nào để "kéo khỏi chỗ người dùng"; K4 tạo task MỚI trên màn ảo vì `findTask` chỉ tìm
     * trong các stack (không có task ⇒ không có gì để tái dùng lên display 0).
     *
     * [X_FRONT_HOME_RESTORED] (R0.3): X không ở lại màn ảo dàn dựng mà tự lên display 0 trước màn nhà ⇒ K12 đã đưa màn nhà
     * lên lại — kể cả khi task X trên màn ảo đã ra sau (`MOVED`) mà X còn MỘT task khác ở trước màn nhà. Là lùi (đếm
     * `BEHIND_FAIL`): màn nhà bị che trong lúc chờ — không phải đường đã đo của R0.3.
     */
    enum class Result {
        MOVED, MOVED_HOME_RESTORED, KEPT_UNDER, B_NOT_IN_SLOT, X_NOT_STAGED, ANCHOR_IN_FRONT, ALREADY_RUNNING, X_FRONT_HOME_RESTORED,
    }

    /** Kết quả một lượt + một dòng log `KachiBehind` đọc được trên màn Chẩn đoán. */
    data class Outcome(val result: Result, val line: String) {
        val moved: Boolean get() = result == Result.MOVED || result == Result.MOVED_HOME_RESTORED
    }

    private fun read(): List<StackEntry> = StackParse.parse(runCatching { sh(BehindHomePlan.LIST_CMD) }.getOrDefault(""))

    /**
     * R0.1 — đẩy app A (đang ở màn ảo [vd], DƯỚI app B vừa mở vào cùng màn ảo) ra sau màn nhà.
     *
     * Bước 1–2 (B vào màn ảo trước) là việc của bên gọi (đường mở ô sẵn có). Ở đây: đọc lại (chờ tối đa
     * [B_TOP_TRIES] lượt cho B lên đỉnh) → S → đọc lại → move-task → đọc lại → gỡ giữ chỗ.
     */
    fun evict(vd: Int, a: String, b: String): Outcome {
        val tag = "evict vd=$vd A=$a B=$b"
        val cleaned = anchor.removeAll()          // mồ côi của lượt trước — task giữ chỗ sống qua lần BYD giết Kachi
        var r1 = read()
        var check = BehindHomePlan.checkEvict(r1, vd, a, b, selfPkg, anchor.isSystemApp(a))
        var tries = 1
        while (check.isTransient() && tries < B_TOP_TRIES) {
            sleep(B_TOP_STEP_MS); tries++
            r1 = read()
            check = BehindHomePlan.checkEvict(r1, vd, a, b, selfPkg, anchor.isSystemApp(a))
        }
        val go = check as? BehindHomePlan.Evict.Go ?: run {
            val why = (check as BehindHomePlan.Evict.Stop).why
            // A còn ở đỉnh và B không có ⇒ B không vào được ô (app tự rơi về display 0 — Waze/Netflix): bên gọi trả ô về A.
            val res = if (why == BehindHomePlan.Why.A_ON_TOP || why == BehindHomePlan.Why.B_NOT_ON_TOP) {
                if (BehindHomePlan.topIs(r1, vd, a)) Result.B_NOT_IN_SLOT else Result.KEPT_UNDER
            } else Result.KEPT_UNDER
            return Outcome(res, "$tag → $res ($why) đọc=$tries dọn=$cleaned")
        }
        return moveBehind(tag, vd, go.taskId, a, b, cleaned)
    }

    /**
     * R0.3 — chạy app X phía sau màn nhà qua ô dàn dựng [stage] (ô sống, app C ≠ X): K4 mở X vào màn ảo của ô → chờ X
     * lên đỉnh (tối đa [X_TOP_WAIT_MS]) → K3 đưa C lên lại → rồi đúng như [evict] với A = X, B = C.
     * [xComp] `null` ⇒ tự phân giải (`cmd package resolve-activity`). X lên chậm hơn trần ⇒ vẫn đưa C lên (X nằm dưới C
     * trong màn ảo, O1) rồi thử đẩy như thường.
     */
    fun startBehind(x: String, stage: BehindHomePlan.Stage, xComp: String? = null): Outcome {
        val tag = "behind X=$x qua ô ${stage.slot} (vd=${stage.vd} C=${stage.pkg})"
        if (anchor.isSystemApp(x) || x == selfPkg) return Outcome(Result.KEPT_UNDER, "$tag → từ chối (hệ thống/chính mình), 0 lệnh")
        // Tên gói đi vào lệnh shell (phân giải, K4) ⇒ lọc bằng CÙNG regex của đường mở app; lạ ⇒ dừng, 0 lệnh.
        if (!x.matches(ShellAppLauncher.PKG)) return Outcome(Result.X_NOT_STAGED, "$tag → tên gói lạ, 0 lệnh")
        val before = read()
        if (before.any { it.pkg == x }) {
            return Outcome(Result.ALREADY_RUNNING, "$tag → đã có task (am stack list), 0 lệnh đổi cửa sổ")
        }
        val comp = xComp ?: FreeformLaunch.parseComponent(runCatching { sh(FreeformLaunch.resolveCmd(x)) }.getOrDefault(""))
        val cComp = before.firstOrNull { it.displayId == stage.vd && it.pkg == stage.pkg }?.comp
        if (comp == null || cComp == null || !BehindHomePlan.safeComponent(comp) || !BehindHomePlan.safeComponent(cComp)) {
            return Outcome(Result.X_NOT_STAGED, "$tag → không phân giải được component (X=$comp C=$cComp), 0 lệnh đổi cửa sổ")
        }
        val homeWasTop = BehindHomePlan.homeOnTop(before, homeComps)
        sh(BehindHomePlan.stageCmd(stage.vd, comp))
        var waited = 0L
        while (waited < X_TOP_WAIT_MS && !BehindHomePlan.topIs(read(), stage.vd, x)) { sleep(X_TOP_STEP_MS); waited += X_TOP_STEP_MS }
        sh(BehindHomePlan.bringToFrontCmd(stage.vd, cComp))
        val out = evict(stage.vd, x, stage.pkg)
        // Đọc lại cả khi `MOVED` (review lượt 3 [P3]): `verifyMoved` chỉ so đỉnh display 0 với bản đọc NGAY TRƯỚC move-task
        // — nếu X đã có một task tự lên trước màn nhà từ lúc dàn (trung chuyển ở lại màn ảo, task chính mở NEW_TASK lên
        // display 0) thì đỉnh "không đổi" mà màn nhà vẫn bị che. Chỉ bỏ qua `MOVED_HOME_RESTORED` (K12 đã bắn).
        val now = if (out.result != Result.MOVED_HOME_RESTORED && homeWasTop) read() else emptyList()
        if (BehindHomePlan.fellFront(now, x)) {
            // X không ở lại màn ảo mà tự lên display 0 TRƯỚC màn nhà (activity trung chuyển mở NEW_TASK — cùng cơ chế [ĐO]
            // T-M3 với ý-định VIEW của YT Music) ⇒ người dùng xin CHẠY NGẦM mà thấy X che màn nhà. K12 (rào camera, byte 2.83)
            // đưa màn nhà lên lại: X còn sống, nằm ngay sau màn nhà (owner: "không che home"). Dấu TRƯỚC K12 (nhận xét review
            // lượt 3): X sau màn nhà mà không mang dấu thì Kachi chết là X nổi lên, lượt trả lại không đưa màn nhà lên.
            val marked = markMain(now, setOf(x))
            runCatching { sh(goHomeCmd) }
            return Outcome(Result.X_FRONT_HOME_RESTORED, "$tag chờ=${waited}ms · ${out.line} · X lên trước màn nhà → dấu=$marked K12")
        }
        return out.copy(line = "$tag chờ=${waited}ms · ${out.line}")
    }

    private fun moveBehind(tag: String, vd: Int, taskA: Int, a: String, b: String, cleaned: Int): Outcome {
        val before = read()
        if (!anchor.start()) return Outcome(Result.KEPT_UNDER, "$tag → không mở được giữ chỗ")
        var pick: BehindHomePlan.Anchor = BehindHomePlan.Anchor.Missing
        var r2 = before
        for (i in 0 until ANCHOR_TRIES) {
            sleep(ANCHOR_STEP_MS)
            r2 = read()
            pick = BehindHomePlan.pickAnchor(before, r2, anchor.component)
            if (pick != BehindHomePlan.Anchor.Missing) break
        }
        val s = (pick as? BehindHomePlan.Anchor.Ok)?.stackId
        if (s == null) {
            val homeWasTop = BehindHomePlan.homeOnTop(before, homeComps)
            val gone = finish()
            val res = if (pick is BehindHomePlan.Anchor.InFront) {
                if (homeWasTop) runCatching { sh(goHomeCmd) }      // K12 — giữ chỗ đã che nhà: đưa nhà lên qua rào camera
                Result.ANCHOR_IN_FRONT
            } else Result.KEPT_UNDER
            return Outcome(res, "$tag → $res (giữ chỗ $pick) gỡ=$gone")
        }
        // Đọc lại NGAY TRƯỚC lệnh: A vẫn không ở đỉnh màn ảo (R0.2) — giữa hai lượt đọc app có thể tự đổi thứ tự.
        val again = BehindHomePlan.checkEvict(r2, vd, a, b, selfPkg, anchor.isSystemApp(a))
        if (again !is BehindHomePlan.Evict.Go || again.taskId != taskA) {
            val gone = finish()
            return Outcome(Result.KEPT_UNDER, "$tag → KEPT_UNDER (đọc lại trước lệnh: $again) gỡ=$gone")
        }
        val top0 = BehindHomePlan.topStackId(r2, BehindHomePlan.MAIN_DISPLAY)
        val homeWasTop = BehindHomePlan.homeOnTop(r2, homeComps)
        // Dấu bền TRƯỚC lệnh đổi cửa sổ: A sống qua lần BYD giết Kachi, lượt thức sau phải biết A là của ta (BehindMarks).
        if (!anchor.markBehind(taskA, a)) {
            val gone = finish()
            return Outcome(Result.KEPT_UNDER, "$tag → KEPT_UNDER (ghi dấu bền hỏng — không đẩy) gỡ=$gone")
        }
        sh(BehindHomePlan.moveTaskCmd(taskA, s))
        val r3 = read()
        val moved = BehindHomePlan.verifyMoved(r3, taskA, s, top0)
        val gone = finish()
        val res = when (moved) {
            BehindHomePlan.Moved.OK -> Result.MOVED
            BehindHomePlan.Moved.NOT_MOVED -> { anchor.unmarkBehind(taskA); Result.KEPT_UNDER }
            BehindHomePlan.Moved.FRONT_CHANGED, BehindHomePlan.Moved.S_VISIBLE -> {
                // [ĐO máy ảo 02/10 `finish/esc`] B (Waze) thoát khỏi ô ra display 0 (`launchToSide`) GIỮA lần đọc lại và
                // move-task ⇒ A thành đỉnh màn ảo lúc lệnh chạy (O2-sai) ⇒ S lên trước. K12 đưa màn nhà lên; mọi task của A/B
                // còn ở display 0 nằm sau màn nhà ⇒ ghi dấu TRƯỚC K12, để Kachi chết thì lượt trả lại nhận ra chúng.
                if (homeWasTop) { markMain(r3, setOf(a, b)); runCatching { sh(goHomeCmd) } }
                Result.MOVED_HOME_RESTORED
            }
        }
        return Outcome(res, "$tag → $res task=$taskA S=$s top0=$top0 kiểm=$moved gỡ=$gone dọn-trước=$cleaned")
    }

    /**
     * Ghi dấu bền cho mọi task của [pkgs] trên display 0 ([BehindHomePlan.mainTasksOf]) — gọi NGAY TRƯỚC K12. Trả số dấu
     * ghi được (ghi hỏng vẫn bắn K12: đưa màn nhà lên lại quan trọng hơn dấu).
     */
    private fun markMain(entries: List<StackEntry>, pkgs: Set<String>): Int =
        BehindHomePlan.mainTasksOf(entries, pkgs).count { e -> e.pkg != selfPkg && runCatching { anchor.markBehind(e.taskId, e.pkg) }.getOrDefault(false) }

    /** Gỡ giữ chỗ ở MỌI lối ra (R0.7). */
    private fun finish(): Int = runCatching { anchor.removeAll() }.getOrDefault(-1)

    /** B chưa kịp lên đỉnh / A chưa kịp xuống — đọc lại vài lượt trước khi kết luận. */
    private fun BehindHomePlan.Evict.isTransient(): Boolean =
        this is BehindHomePlan.Evict.Stop &&
            (why == BehindHomePlan.Why.B_NOT_ON_TOP || why == BehindHomePlan.Why.A_ON_TOP || why == BehindHomePlan.Why.NO_READ)

    companion object {
        const val B_TOP_TRIES = 4
        const val B_TOP_STEP_MS = 400L
        const val ANCHOR_TRIES = 12
        const val ANCHOR_STEP_MS = 150L
        const val X_TOP_WAIT_MS = 4_000L
        const val X_TOP_STEP_MS = 250L
    }
}
