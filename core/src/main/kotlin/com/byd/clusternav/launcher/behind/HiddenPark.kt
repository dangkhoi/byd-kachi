package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.FreeformLaunch
import com.byd.clusternav.launcher.ShellAppLauncher
import com.byd.clusternav.launcher.StackReads
import com.byd.clusternav.launcher.behind.BehindHomeSequence.Outcome
import com.byd.clusternav.launcher.behind.BehindHomeSequence.Result
import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ A2 · 2.89 — Ô 7 cho app KHÔNG ở ô: mở X lên màn ảo ẨN của Kachi rồi ĐỂ YÊN ở đó (thuần, chặn, `:core`) ════════════
 *
 * Backlog `TRIP-MUSIC-IN-SLOT` (2) · spec `docs/specs/kachi-289-field-fixes.html` §A2. Bên gọi: bước nhạc của chuyến lên xe
 * khi app nhạc KHÔNG nằm trong ô nào (`TripMusicRun`).
 *
 * ## Vì sao không đi chuỗi BEHIND-HOME ([BehindHomeSequence.startBehindHidden]) nữa
 *  - [ĐO xe 05/10, Seal DL3, `oncar-2026-10-05-slot-cluster.md` §1] mở giữ chỗ ném NPE trong system_server
 *    (`ActivityStackSupervisor.handleNonResizableTaskIfNeeded`) ⇒ chuỗi KHÔNG BAO GIỜ đẩy được X ra sau màn nhà trên ROM này;
 *  - lối lùi của chuỗi đó khi X kẹt màn ảo ẩn là K7 (đưa X sang display 0) ⇒ [ĐO xe 05/10 §2] đổi display = activity dựng
 *    lại (`am_relaunch_resume_activity`) ⇒ player YouTube `released`, nhạc tắt hẳn.
 * Ô 7 (owner 05/10: *"giả lập 1 ô số 7 … nhét các app chạy nền vào đó"*) giữ X Ở YÊN trên màn ảo nó được mở: không giữ chỗ,
 * không `move-task`, không K7, không lệnh nào lên display 0 (trừ K12 khi chính X tự thoát lên trước màn nhà).
 *
 * CLAUDE.md §6 — đường mới ĐỨNG TRƯỚC đường cũ ở bước nhạc là NGOẠI LỆ có đo: đường cũ đã ĐO hỏng trên xe (hai điểm trên) và
 * lối lùi của nó phá đúng thứ bước nhạc cần (nhạc phát). Đường cũ vẫn còn, đứng SAU, khi đường này không chạy được
 * (`TripMusicPlace.fallBack`: không tạo được màn ảo / X không lên màn ảo).
 *
 * ## Thứ tự
 * đọc (hỏng ⇒ 0 lệnh) → đang chạy (task + `pidof`) ⇒ 0 lệnh → tạo màn ảo ẩn → K4 (byte đường mở ô) → chờ X lên đỉnh màn ảo
 * (≤ [BehindHomeSequence.X_TOP_WAIT_MS]) → trao màn ảo cho sổ ô 7 ([Port.park]). X tự lên display 0 trước màn nhà ⇒ dấu bền
 * + K12 NGAY (cùng luật `afterStage`), rồi màn ảo trống thì nhả. Đọc HỎNG sau lệnh ⇒ GIỮ màn ảo ([Result.UNREAD]) — luật
 * rào nhả D2: không bao giờ nhả màn ảo còn có thể chứa app người dùng.
 *
 * ## Bốn câu CLAUDE.md §4 (lệnh duy nhất đổi trạng thái: K4 `am start --display <vd> … -n <X>`)
 *  1. **display**: đúng màn ảo vừa tạo ([Port.create], Kachi sở hữu, đã đăng ký cổng ownership, `vd ≥ 1`); không bao giờ 0;
 *  2. **app**: đúng gói X (qua [ShellAppLauncher.PKG], component qua [BehindHomePlan.safeComponent]); từ chối chính Kachi / app
 *     hệ thống (R0.6);
 *  3. **loại stack**: task `standard` mới của X trên màn ảo riêng (task NGUỘI cũ — không tiến trình — bị K4 kéo vào, như L4 · D4);
 *  4. **hoàn tác**: nhận lại vào ô (`ParkedApps.claim`, đổi mặt vẽ — 0 lệnh) · trần ô 7 / Kachi chết ⇒ nhả màn ảo, cờ 256
 *     DESTROY_CONTENT_ON_REMOVAL kết thúc activity trên đó (A10 r47 `ActivityDisplay.remove` `:1120-1160`). Không trạng thái hệ
 *     thống bền nào (§5): không `wm`, không cờ, không ghi `settings`.
 */
class HiddenPark(
    private val sh: (String) -> String,
    private val selfPkg: String,
    /** K12 — `AccessibilityRebind.GO_HOME_UNLESS_CAMERA` (byte 2.83). */
    private val goHomeCmd: String,
    /** Các dạng in của màn nhà Kachi (`DefaultHome.shownComponents`). */
    private val homeComps: Collection<String>,
    /** `ApplicationInfo.FLAG_SYSTEM`; không hỏi được ⇒ `true` (an toàn: không mở). */
    private val isSystemApp: (String) -> Boolean,
    /** Ghi dấu bền `kachi_behind_marks` (`commit()`) — TRƯỚC K12 (CLAUDE.md §5). */
    private val markBehind: (Int, String) -> Boolean,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {

    /** Phần Android (`StagingDisplay` ở `:app`); bản giả trong test. */
    interface Port {
        /** Tạo màn ảo ẩn (cờ 8|256, mặt vẽ không ai xem, đăng ký cổng ownership) ⇒ id ≥ 1; hỏng ⇒ `null`. */
        fun create(): Int?

        /** Trao màn ảo [vd] (đang có [pkg]) cho sổ ô 7 — KHÔNG nhả. `false` = sổ không nhận ⇒ màn ảo vẫn của lượt này. */
        fun park(vd: Int, pkg: String): Boolean

        /** Nhả màn ảo [vd]. [HiddenPark] chỉ gọi khi bản đọc ĐỌC ĐƯỢC thấy trên đó không còn task nào của app người dùng. */
        fun release(vd: Int)
    }

    fun park(x: String, port: Port, xComp: String? = null): Outcome {
        val tag = "park-hidden X=$x"
        refuse(x)?.let { return Outcome(it.first, "$tag → ${it.second}, 0 lệnh") }
        val before = StackReads.read(sh).entries ?: return Outcome(Result.NO_CHANNEL, "$tag → không đọc được am stack list, 0 lệnh")
        if (before.any { it.pkg == x } && BehindHomePlan.running(before, x, runCatching { sh(BehindHomePlan.pidCmd(x)) }.getOrDefault(""))) {
            return Outcome(Result.ALREADY_RUNNING, "$tag → đã có task + tiến trình (am stack list + pidof), 0 lệnh đổi cửa sổ")
        }
        val comp = xComp ?: FreeformLaunch.parseComponent(runCatching { sh(FreeformLaunch.resolveCmd(x)) }.getOrDefault(""))
        if (comp == null || !BehindHomePlan.safeComponent(comp)) {
            return Outcome(Result.X_NOT_STAGED, "$tag → không phân giải được component X, 0 lệnh đổi cửa sổ")
        }
        val homeWasTop = BehindHomePlan.homeOnTop(before, homeComps)
        val vd = port.create()?.takeIf { it >= 1 } ?: return Outcome(Result.NO_STAGE, "$tag → không tạo được màn ảo ẩn, 0 lệnh")
        sh(BehindHomePlan.stageCmd(vd, comp))
        var waited = 0L
        while (true) {
            val r = StackReads.read(sh).entries
            // "X che màn nhà" xét TRƯỚC "X ở đỉnh màn ảo": trung chuyển của X có thể nằm ở đỉnh màn ảo trong khi activity chính
            // đã lên display 0 ([ĐO máy ảo `m5a`]: 3310 ở màn ảo, 3311 trước màn nhà) — đỗ trước là bỏ quên màn nhà bị che.
            if (r != null && homeWasTop && BehindHomePlan.fellFront(r, x)) return fell(tag, vd, x, port, r, waited)
            if (r != null && BehindHomePlan.topIs(r, vd, x)) return checked(tag, x, homeWasTop, handOff(tag, vd, x, port, "X lên đỉnh màn ảo sau ${waited}ms"))
            if (waited >= BehindHomeSequence.X_TOP_WAIT_MS) return checked(tag, x, homeWasTop, timedOut(tag, vd, x, port, r))
            sleep(BehindHomeSequence.X_TOP_STEP_MS); waited += BehindHomeSequence.X_TOP_STEP_MS
        }
    }

    /** Trao màn ảo cho sổ ô 7; sổ không nhận ⇒ GIỮ màn ảo (X đang sống trên đó — rào nhả D2), X vẫn chạy ẩn. */
    private fun handOff(tag: String, vd: Int, x: String, port: Port, why: String): Outcome =
        if (runCatching { port.park(vd, x) }.getOrDefault(false)) Outcome(Result.PARKED, "$tag → $why · vd=$vd đỗ ô 7")
        else Outcome(Result.KEPT_UNDER, "$tag → $why · sổ ô 7 không nhận ⇒ GIỮ màn ảo vd=$vd (X chạy ẩn)")

    /**
     * Đọc lại MỘT lần sau khi X đã ở màn ảo (cùng lẽ `BehindHomeSequence.afterStage`, review lượt 3 [P3]): trung chuyển có thể mở
     * activity chính NEW_TASK lên display 0 SAU lần đọc thấy X ở đỉnh màn ảo ⇒ dấu + K12. Đọc hỏng ⇒ giữ mã, ghi rõ (không K12
     * trên một bản đọc không có). Màn nhà không ở đỉnh từ đầu ⇒ không giành màn hình.
     */
    private fun checked(tag: String, x: String, homeWasTop: Boolean, o: Outcome): Outcome {
        if (!homeWasTop || o.result == Result.UNREAD || o.result == Result.X_NOT_STAGED) return o
        val now = StackReads.read(sh).entries ?: return o.copy(line = "${o.line} · đọc lại hỏng ⇒ chưa rõ X có lên trước màn nhà")
        if (!BehindHomePlan.fellFront(now, x)) return o
        val marked = BehindHomePlan.mainTasksOf(now, setOf(x)).count { e -> runCatching { markBehind(e.taskId, e.pkg) }.getOrDefault(false) }
        runCatching { sh(goHomeCmd) }
        return Outcome(Result.X_FRONT_HOME_RESTORED, "${o.line} · X lên trước màn nhà → dấu=$marked K12 ($tag)")
    }

    /**
     * X tự lên display 0 TRƯỚC màn nhà trong lúc chờ (activity trung chuyển mở NEW_TASK — [ĐO máy ảo `p3/e2e-L4/m5a`]) ⇒ dấu
     * mọi task của X trên display 0 → K12 NGAY (mỗi bước thêm là thêm thời gian che màn nhà) → màn ảo: còn X ⇒ đỗ; trống ⇒ nhả.
     */
    private fun fell(tag: String, vd: Int, x: String, port: Port, r: List<StackEntry>, waited: Long): Outcome {
        val marked = BehindHomePlan.mainTasksOf(r, setOf(x)).count { e -> runCatching { markBehind(e.taskId, e.pkg) }.getOrDefault(false) }
        runCatching { sh(goHomeCmd) }
        val left = StackReads.read(sh).entries?.filter { it.displayId == vd }
        val tail = when {
            left == null -> "vd=$vd GIỮ (đọc lại hỏng)"
            left.any { it.pkg == x } -> if (runCatching { port.park(vd, x) }.getOrDefault(false)) "vd=$vd đỗ ô 7" else "vd=$vd GIỮ"
            left.none { it.pkg != selfPkg } -> { runCatching { port.release(vd) }; "vd=$vd nhả" }
            else -> "vd=$vd GIỮ (còn ${left.joinToString { it.comp }})"
        }
        return Outcome(Result.X_FRONT_HOME_RESTORED, "$tag → X lên trước màn nhà sau ${waited}ms → dấu=$marked K12 · $tail")
    }

    /** Hết trần chờ mà X chưa lên đỉnh: X có trên màn ảo ⇒ đỗ như thường; không có và màn ảo trống ⇒ nhả, `X_NOT_STAGED`. */
    private fun timedOut(tag: String, vd: Int, x: String, port: Port, r: List<StackEntry>?): Outcome {
        if (r == null) return Outcome(Result.UNREAD, "$tag → đọc lại hỏng ⇒ chưa rõ X ở đâu · GIỮ màn ảo vd=$vd")
        val left = r.filter { it.displayId == vd }
        if (left.any { it.pkg == x }) return handOff(tag, vd, x, port, "X trên màn ảo (chưa ở đỉnh) sau ${BehindHomeSequence.X_TOP_WAIT_MS}ms")
        if (left.any { it.pkg != selfPkg }) {
            return Outcome(Result.KEPT_UNDER, "$tag → X không lên màn ảo; còn ${left.joinToString { it.comp }} ⇒ GIỮ màn ảo vd=$vd")
        }
        runCatching { port.release(vd) }
        return Outcome(Result.X_NOT_STAGED, "$tag → X không lên màn ảo sau ${BehindHomeSequence.X_TOP_WAIT_MS}ms · vd=$vd nhả")
    }

    /** Từ chối trước MỌI lệnh — cùng ba luật với chuỗi BEHIND-HOME: chính Kachi / app hệ thống (R0.6) / tên gói lạ. */
    private fun refuse(x: String): Pair<Result, String>? = when {
        x == selfPkg -> Result.KEPT_UNDER to "từ chối (chính mình)"
        !x.matches(ShellAppLauncher.PKG) -> Result.X_NOT_STAGED to "tên gói lạ"
        runCatching { isSystemApp(x) }.getOrDefault(true) -> Result.SYSTEM_APP to "từ chối (app hệ thống, R0.6)"
        else -> null
    }
}
