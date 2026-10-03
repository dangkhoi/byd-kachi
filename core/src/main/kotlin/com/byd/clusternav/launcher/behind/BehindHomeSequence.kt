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
    /** Dấu hiệu màn camera của đời xe (`ClusterProfile.cameraSignature`, `null` = chưa biết) — chỉ cho K7 lùi của [startBehindHidden]. */
    private val cameraSig: String? = null,
) {

    /**
     * L4 · D2(a) — chỗ dàn dựng ẨN: một màn ảo riêng của Kachi KHÔNG gắn vào ô nào (bố cục không có ô app sống). Phần Android
     * ở `:app` (`StagingDisplay`); bản giả trong test. Thứ tự gọi do [startBehindHidden] giữ.
     */
    interface HiddenStagePort {
        /** Tạo màn ảo ẩn (cùng cờ 8|256 của màn ảo ô, đăng ký với cổng ownership) ⇒ id ≥ 1; hỏng ⇒ `null`. */
        fun create(): Int?

        /** Mở activity CHE của chính Kachi lên đỉnh màn ảo [vd] (API trong tiến trình — Kachi là chủ màn ảo riêng tư). */
        fun cover(vd: Int): Boolean

        /** Gỡ mọi task che (kể cả mồ côi của lượt trước). */
        fun uncover(): Int

        /** Nhả màn ảo [vd] (gỡ đăng ký + `release`). [startBehindHidden] chỉ gọi khi bản đọc thấy màn ảo đã TRỐNG. */
        fun release(vd: Int)
    }

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
     * L4 · D4 — task PHẢI kèm tiến trình sống ([BehindHomePlan.running], `pidof`): task không tiến trình (BYD giết app lúc
     * tắt máy, task còn — [ĐO máy ảo `e2e/e2b-bg-ytmusic-dead-proc`]) là NGUỘI ⇒ K4 kéo task đó vào màn ảo dàn dựng.
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

        /** L4 · D1(e) — X là app hệ thống (R0.6): từ chối, 0 lệnh. Trước L4 ra `KEPT_UNDER` chung chung — sổ không nói được vì sao. */
        SYSTEM_APP,

        /** L4 · D2 — không có chỗ dàn dựng: không ô sống VÀ không tạo được màn ảo ẩn. 0 lệnh đổi cửa sổ. */
        NO_STAGE,

        /** L4 · D1 — bên thi hành (`BehindHomeRunner`): chưa có kênh / BEHIND-HOME đã tự tắt trong tiến trình (R0.5a). 0 lệnh. */
        NO_CHANNEL, DISABLED,

        /** L4 · D1 — bên CHỜ (chuyến lên xe) không nhận được kết quả trong hạn; chuỗi có thể vẫn đang chạy ⇒ "chưa rõ", không đoán. */
        TIMEOUT,
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
     *
     * L4 · D3 — [view] khác `null` = lệnh K4-VIEW (`TripMusicPlan.viewCmd`: mở LINK bằng một ACTIVITY, nhắm đúng gói) thay
     * cho K4 MAIN; bỏ phép "đang chạy" (app nhạc VỪA được chạy ngầm, nay giao link cho nó — task cũ bị kéo vào màn ảo
     * `reparentToDisplay`, nguồn A10 `ActivityStarter.java:2096-2170`). App trung chuyển thoát lên display 0 ⇒ [waitTop]
     * thôi chờ ngay, [afterStage] dấu + K12.
     */
    fun startBehind(x: String, stage: BehindHomePlan.Stage, xComp: String? = null, view: ((Int) -> String)? = null): Outcome {
        val tag = "behind X=$x qua ô ${stage.slot} (vd=${stage.vd} C=${stage.pkg})${if (view != null) " VIEW" else ""}"
        refuse(tag, x)?.let { return it }
        val before = read()
        if (view == null && isRunning(before, x)) {
            return Outcome(Result.ALREADY_RUNNING, "$tag → đã có task + tiến trình (am stack list + pidof), 0 lệnh đổi cửa sổ")
        }
        val k4 = view ?: resolveK4(x, xComp) ?: return Outcome(Result.X_NOT_STAGED, "$tag → không phân giải được component X, 0 lệnh đổi cửa sổ")
        val cComp = before.firstOrNull { it.displayId == stage.vd && it.pkg == stage.pkg }?.comp
        if (cComp == null || !BehindHomePlan.safeComponent(cComp)) {
            return Outcome(Result.X_NOT_STAGED, "$tag → không thấy component của app ô (C=$cComp), 0 lệnh đổi cửa sổ")
        }
        val homeWasTop = BehindHomePlan.homeOnTop(before, homeComps)
        sh(k4(stage.vd))
        val waited = waitTop(stage.vd, x, homeWasTop).ms
        sh(BehindHomePlan.bringToFrontCmd(stage.vd, cComp))
        return afterStage(tag, x, waited, evict(stage.vd, x, stage.pkg), homeWasTop)
    }

    /**
     * L4 · D2(a) — như [startBehind] nhưng chỗ dàn dựng là màn ảo ẨN của Kachi ([port]) — bố cục không có ô app sống.
     * Đường mới ⇒ ĐỨNG CUỐI chuỗi (CLAUDE.md §6): bên gọi chỉ tới đây khi [BehindHomePlan.stagingSlot] không có ô nào.
     *
     * Thứ tự (đo trên máy ảo, `p3/e2e-L4`): tạo màn ảo → K4 (hoặc K4-VIEW [view]) mở X lên đó → chờ X lên đỉnh → mở
     * activity CHE của Kachi lên đỉnh màn ảo (thay app C của ô — điều kiện cứng R0.2: X ở ĐỈNH nguồn lúc `move-task` ⇒ S
     * lên che màn nhà, `TaskRecord.reparent` A10 `:728-749`) → [evict] với B = Kachi → gỡ che → đọc tới khi màn ảo TRỐNG
     * → mới nhả. Rào nhả (D2): KHÔNG BAO GIỜ nhả khi còn task của APP NGƯỜI DÙNG trên màn ảo (A10 `ActivityDisplay.remove`
     * `:1120-1160`: cờ 256 kết thúc activity thay vì đẩy lên display 0 — nhưng ROM BYD [CHƯA BIẾT] ⇒ không dựa vào nó cho app
     * người dùng; lớp che của chính Kachi thì được — nó tự gỡ nếu bị đẩy sang display khác). X không ra được ⇒ K7
     * (`SlotReturn.guardedDetachCmd` — rào màn nhà đang hiện + camera) đưa X ra display 0, ghi dấu, K12 ⇒ X sống sau màn nhà,
     * màn ảo trống rồi mới nhả. K7 cũng không chạy được ⇒ GIỮ màn ảo (chết theo tiến trình, cờ 256), một dòng log.
     */
    fun startBehindHidden(x: String, port: HiddenStagePort, xComp: String? = null, view: ((Int) -> String)? = null): Outcome {
        val tag = "behind-hidden X=$x${if (view != null) " VIEW" else ""}"
        refuse(tag, x)?.let { return it }
        val before = read()
        if (view == null && isRunning(before, x)) {
            return Outcome(Result.ALREADY_RUNNING, "$tag → đã có task + tiến trình (am stack list + pidof), 0 lệnh đổi cửa sổ")
        }
        val k4 = view ?: resolveK4(x, xComp) ?: return Outcome(Result.X_NOT_STAGED, "$tag → không phân giải được component X, 0 lệnh đổi cửa sổ")
        val homeWasTop = BehindHomePlan.homeOnTop(before, homeComps)
        val vd = port.create()?.takeIf { it >= 1 } ?: return Outcome(Result.NO_STAGE, "$tag → không tạo được màn ảo ẩn, 0 lệnh")
        var w = Waited(0L, fell = false)
        var out = Outcome(Result.KEPT_UNDER, "$tag → chưa đẩy")
        try {
            sh(k4(vd))
            w = waitTop(vd, x, homeWasTop)
            out = when {
                // X tự lên display 0 TRƯỚC màn nhà trong lúc dàn (trung chuyển VIEW — [ĐO máy ảo `p3/e2e-L4/m5a`]) ⇒ không
                // dựng lớp che, không đẩy: đưa màn nhà lên NGAY bên dưới (mỗi bước thêm ở đây là thêm thời gian che nhà).
                w.fell -> Outcome(Result.KEPT_UNDER, "$tag → X tự lên display 0 khi đang dàn, 0 move-task")
                port.cover(vd) && !waitTop(vd, selfPkg, false).timedOut -> evict(vd, x, selfPkg)
                else -> Outcome(Result.KEPT_UNDER, "$tag → không dựng được lớp che, 0 move-task")
            }
        } finally {
            runCatching { port.uncover() }
        }
        val waited = w.ms
        val first = if (w.fell) afterStage(tag, x, waited, out, homeWasTop) else null
        val v = vacate(vd, x)
        // Rào nhả chỉ canh task của APP NGƯỜI DÙNG: lớp che của chính Kachi gỡ chậm (`finishAndRemoveTask` chờ activity dừng
        // hẳn — [ĐO máy ảo `e6-hidden` lượt 1]: 4,9 s) ⇒ còn trong bản đọc vẫn nhả, cờ 256 kết thúc nó; nó tự gỡ nếu bị hệ đẩy
        // sang display khác (`StageCoverActivity`). Giữ lại vì nó = màn ảo sống tới khi tiến trình chết, vẽ vô ích.
        val foreign = v.left.filter { it.pkg != selfPkg }
        val gone = if (foreign.isEmpty()) {
            runCatching { port.release(vd) }
            if (v.left.isEmpty()) "nhả" else "nhả (lớp che chưa gỡ xong)"
        } else "GIỮ (còn ${foreign.joinToString { it.comp }})"
        if (first != null) return first.copy(line = "${first.line} · vd=$vd $gone")
        if (v.rescued) {
            return Outcome(Result.X_FRONT_HOME_RESTORED, "$tag chờ=${waited}ms · ${out.line} · X kẹt màn ảo ẩn → K7 + dấu + K12 · vd=$vd $gone")
        }
        val res = afterStage(tag, x, waited, out, homeWasTop)
        return res.copy(line = "${res.line} · vd=$vd $gone")
    }

    /** Kết quả dọn màn ảo ẩn: task còn lại trên đó + X có phải nhờ K7 mới ra được không (màn nhà bị che thoáng qua). */
    private data class Vacated(val left: List<StackEntry>, val rescued: Boolean)

    /** Hai từ chối chung trước MỌI lệnh ([startBehind] · [startBehindHidden]): chính Kachi / app hệ thống (R0.6) / tên gói lạ. */
    private fun refuse(tag: String, x: String): Outcome? = when {
        x == selfPkg -> Outcome(Result.KEPT_UNDER, "$tag → từ chối (chính mình), 0 lệnh")
        anchor.isSystemApp(x) -> Outcome(Result.SYSTEM_APP, "$tag → từ chối (app hệ thống, R0.6), 0 lệnh")
        // Tên gói đi vào lệnh shell (phân giải, K4) ⇒ lọc bằng CÙNG regex của đường mở app; lạ ⇒ dừng, 0 lệnh.
        !x.matches(ShellAppLauncher.PKG) -> Outcome(Result.X_NOT_STAGED, "$tag → tên gói lạ, 0 lệnh")
        else -> null
    }

    /** L4 · D4 — có task THÌ mới hỏi `pidof` (một lệnh chỉ đọc): task không tiến trình = nguội ([BehindHomePlan.running]). */
    private fun isRunning(entries: List<StackEntry>, x: String): Boolean =
        entries.any { it.pkg == x } && BehindHomePlan.running(entries, x, runCatching { sh(BehindHomePlan.pidCmd(x)) }.getOrDefault(""))

    /** K4 (MAIN/LAUNCHER, byte của đường mở ô) cho X — `null` = không phân giải được component an toàn. */
    private fun resolveK4(x: String, xComp: String?): ((Int) -> String)? {
        val comp = xComp ?: FreeformLaunch.parseComponent(runCatching { sh(FreeformLaunch.resolveCmd(x)) }.getOrDefault(""))
        if (comp == null || !BehindHomePlan.safeComponent(comp)) return null
        return { vd -> BehindHomePlan.stageCmd(vd, comp) }
    }

    /** Kết quả chờ: số ms đã chờ; [fell] = X tự lên display 0 trước màn nhà trong lúc chờ; [timedOut] = hết trần mà chưa lên đỉnh. */
    private data class Waited(val ms: Long, val fell: Boolean) {
        val timedOut: Boolean get() = !fell && ms >= X_TOP_WAIT_MS
    }

    /**
     * Chờ [pkg] lên đỉnh màn ảo [vd] tối đa [X_TOP_WAIT_MS]. L4: X TỰ lên display 0 trước màn nhà trong lúc chờ
     * ([BehindHomePlan.fellFront] — Waze `launchToSide` [ĐO `p3/e2e-L4/m1-stale-task-k4`], trung chuyển VIEW [ĐO
     * `p3/e2e-L4/m5a`, T-M3]) ⇒ thôi chờ ngay ([Waited.fell]): chờ tiếp 4 s là 4 s màn nhà bị che.
     */
    private fun waitTop(vd: Int, pkg: String, homeWasTop: Boolean): Waited {
        var waited = 0L
        while (waited < X_TOP_WAIT_MS) {
            val r = read()
            if (BehindHomePlan.topIs(r, vd, pkg)) return Waited(waited, fell = false)
            if (homeWasTop && BehindHomePlan.fellFront(r, pkg)) return Waited(waited, fell = true)
            sleep(X_TOP_STEP_MS); waited += X_TOP_STEP_MS
        }
        return Waited(waited, fell = false)
    }

    /**
     * Sau khi gỡ lớp che: đọc tới khi màn ảo ẩn [vd] chỉ còn (hoặc không còn) task của X. X còn ở đó (đẩy hỏng) ⇒ K7 qua
     * rào đưa X ra display 0 + dấu + K12. Trả các task CÒN trên màn ảo sau cùng (rỗng ⇒ nhả được).
     */
    private fun vacate(vd: Int, x: String): Vacated {
        val left = settle(vd)
        val stuck = left.firstOrNull { it.pkg == x && BehindHomePlan.safeComponent(it.comp) }
        if (stuck == null || homeComps.isEmpty()) return Vacated(left, rescued = false)
        sh(SlotReturn.guardedDetachCmd(cameraSig, homeComps.toList(), stuck.comp))
        val after = settle(vd)
        if (after.any { it.pkg == x }) return Vacated(after, rescued = false)      // rào K7 chặn (camera / màn nhà không hiện)
        markMain(read(), setOf(x))
        runCatching { sh(goHomeCmd) }
        return Vacated(after, rescued = true)
    }

    /** Đọc lại tối đa [SETTLE_READS] lượt cho tới khi màn ảo [vd] không còn task của chính Kachi (lớp che vừa gỡ). */
    private fun settle(vd: Int): List<StackEntry> {
        var onVd = read().filter { it.displayId == vd }
        var i = 0
        while (onVd.any { it.pkg == selfPkg } && i < SETTLE_READS) { sleep(X_TOP_STEP_MS); onVd = read().filter { it.displayId == vd }; i++ }
        return onVd
    }

    /**
     * Đuôi chung của hai đường dàn dựng. Đọc lại cả khi `MOVED` (review lượt 3 [P3]): `verifyMoved` chỉ so đỉnh display 0
     * với bản đọc NGAY TRƯỚC move-task — nếu X đã có một task tự lên trước màn nhà từ lúc dàn (trung chuyển ở lại màn ảo,
     * task chính mở NEW_TASK lên display 0) thì đỉnh "không đổi" mà màn nhà vẫn bị che. Chỉ bỏ qua `MOVED_HOME_RESTORED`.
     */
    private fun afterStage(tag: String, x: String, waited: Long, out: Outcome, homeWasTop: Boolean): Outcome {
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

        /** Số lượt đọc chờ màn ảo ẩn hết task che sau khi gỡ (`finishAndRemoveTask` không đồng bộ) — 8 × 250 ms. */
        const val SETTLE_READS = 8
    }
}
