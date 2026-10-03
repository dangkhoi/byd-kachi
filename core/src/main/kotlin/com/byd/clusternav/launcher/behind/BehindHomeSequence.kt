package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.FreeformLaunch
import com.byd.clusternav.launcher.ShellAppLauncher
import com.byd.clusternav.launcher.StackReads
import com.byd.clusternav.modules.clustercast.StackEntry

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
    interface HiddenStagePort : CoverPort {
        /** Tạo màn ảo ẩn (cùng cờ 8|256 của màn ảo ô, đăng ký với cổng ownership) ⇒ id ≥ 1; hỏng ⇒ `null`. */
        fun create(): Int?

        /** Nhả màn ảo [vd] (gỡ đăng ký + `release`). [startBehindHidden] chỉ gọi khi bản đọc thấy màn ảo đã TRỐNG. */
        fun release(vd: Int)

        /** Màn ảo ẩn của các lượt TRƯỚC còn sống (bị GIỮ: rào nhả / đọc hỏng / chuỗi ném giữa chừng) — [HiddenStageReclaim]. */
        fun kept(): Collection<Int> = emptyList()

        /** Nhả màn ảo [vd] của một lượt trước — [HiddenStageReclaim] chỉ gọi khi bản đọc ĐỌC ĐƯỢC thấy không còn app người dùng. */
        fun reclaim(vd: Int) {}
    }

    /**
     * Lớp CHE của chính Kachi (`StageCoverActivity`) trên một màn ảo CỦA KACHI — màn ảo ẩn ([HiddenStagePort]) hoặc màn ảo
     * của một ô ([evictCovered], L8). Phần Android ở `:app` (`StagingDisplay`), bản giả trong test.
     */
    interface CoverPort {
        /** Mở activity CHE của chính Kachi lên đỉnh màn ảo [vd] rồi CHỜ nó `onResume` (API trong tiến trình — Kachi là chủ màn ảo riêng tư). */
        fun cover(vd: Int): Boolean

        /** Gỡ mọi task che (kể cả mồ côi của lượt trước). */
        fun uncover(): Int
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

        /** Review 287 [P1] — đã ra lệnh rồi KHÔNG đọc lại được `am stack list` (kênh đứt giữa chuỗi): chưa rõ X ở đâu ⇒ GIỮ màn ảo. */
        UNREAD,
    }

    /** Kết quả một lượt + một dòng log `KachiBehind` đọc được trên màn Chẩn đoán. */
    data class Outcome(val result: Result, val line: String) {
        val moved: Boolean get() = result == Result.MOVED || result == Result.MOVED_HOME_RESTORED

        /** L8 — X đã RA KHỎI chỗ dàn dựng và sống trên display 0 sau màn nhà (kể cả khi phải K12 đưa màn nhà lên lại). */
        val outOfStage: Boolean get() = moved || result == Result.X_FRONT_HOME_RESTORED
    }

    /** Một bản đọc; `null` = ĐỌC HỎNG (ném / parse rỗng — display 0 luôn có stack màn nhà) — KHÔNG phải "trống" ([StackReads.read]). */
    private fun readOrNull(): List<StackEntry>? = StackReads.read(sh).entries

    /** Bản đọc cho các phép mà rỗng đã là "không làm" ([BehindHomePlan.checkEvict] `NO_READ`, [BehindHomePlan.fellFront] `false`…). */
    private fun read(): List<StackEntry> = readOrNull().orEmpty()

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
     * màn ảo trống rồi mới nhả. K7 cũng không chạy được ⇒ GIỮ màn ảo, một dòng log; lượt sau thu hồi khi đọc thấy nó trống
     * ([HiddenStageReclaim]). Review 287 [P1]: đọc HỎNG ≠ trống — trước lệnh ⇒ 0 lệnh (`NO_CHANNEL`); sau lệnh ⇒ GIỮ, [Result.UNREAD].
     */
    fun startBehindHidden(x: String, port: HiddenStagePort, xComp: String? = null, view: ((Int) -> String)? = null): Outcome {
        val tag = "behind-hidden X=$x${if (view != null) " VIEW" else ""}"
        refuse(tag, x)?.let { return it }
        // Không đọc được ⇒ không biết X có đang ở ô / cụm không (K4 lúc này kéo X khỏi chỗ người dùng) ⇒ 0 lệnh.
        val before = readOrNull() ?: return Outcome(Result.NO_CHANNEL, "$tag → không đọc được am stack list, 0 lệnh")
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
        val foreign = v.left?.filter { it.pkg != selfPkg }
        val gone = when {
            foreign == null -> "GIỮ (không đọc được am stack list)"     // [P1]: đọc hỏng ≠ trống — không biết còn app người dùng không
            foreign.isEmpty() -> { runCatching { port.release(vd) }; if (v.left.isNullOrEmpty()) "nhả" else "nhả (lớp che chưa gỡ xong)" }
            else -> "GIỮ (còn ${foreign.joinToString { it.comp }})"
        }
        if (first != null && (v.left != null || first.outOfStage)) return first.copy(line = "${first.line} · vd=$vd $gone")
        if (v.left == null) return Outcome(Result.UNREAD, "$tag chờ=${waited}ms · ${out.line} · đọc lại hỏng ⇒ chưa rõ X ở đâu · vd=$vd $gone")
        if (v.rescued) {
            return Outcome(Result.X_FRONT_HOME_RESTORED, "$tag chờ=${waited}ms · ${out.line} · X kẹt màn ảo ẩn → K7 + dấu + K12 · vd=$vd $gone")
        }
        val res = afterStage(tag, x, waited, out, homeWasTop)
        return res.copy(line = "${res.line} · vd=$vd $gone")
    }

    /**
     * L8 — *chạy nền* từ đầu ô (owner 03/10: *"đẩy app ra chạy nền … để UI trong suốt thấy nền background"*) cho MỌI ô app,
     * kể cả ô không có app LƯU khác (mở khoá D-L6-1). Cùng kỹ thuật lớp che của [startBehindHidden], nhưng chỗ dàn dựng là
     * CHÍNH màn ảo của ô (A đang ở đó, đã sống ⇒ không K4): lớp che của Kachi lên đỉnh màn ảo ô (A thôi là đỉnh — R0.2, A10
     * r47 `TaskRecord.java:736-737` `wasFront`) → [evict] với B = Kachi (giữ chỗ → move-task → đọc lại, byte không đổi) → gỡ
     * che → đọc lại. KHÔNG nhả / đổi màn ảo nào (màn ảo của ô là của host ô; ô đổi theo luật hoàn ô ở bên gọi).
     *
     * Bốn câu CLAUDE.md §4 (lệnh đổi trạng thái của lượt: lớp che · giữ chỗ · `am stack move-task` · K12):
     *  1. **display**: lớp che lên đúng màn ảo [vd] của ô (`vd < 1` ⇒ 0 lệnh); giữ chỗ + move-task về display 0 (đích S);
     *  2. **app**: đúng gói [a] của ô — từ chối chính Kachi / app hệ thống (R0.6) / tên gói lạ; [a] phải có ĐÚNG MỘT task
     *     trên [vd], ở ĐỈNH màn ảo, không lẫn display khác (đang chiếu cụm) — [BehindHomePlan.checkEvict];
     *  3. **loại stack**: task của [a] `standard` bằng CHỮ, không ghim; lớp che là task `standard` của Kachi;
     *  4. **hoàn tác**: hỏng trước move-task ⇒ gỡ che, A vẫn ở ô như cũ (0 lệnh đổi app); A lên trước màn nhà ⇒ dấu + K12
     *     (rào camera); app sau màn nhà về lại ô bằng K8 khi ô mở lại nó (R1.8, dấu bền).
     *
     * Rào màn nhà: chỉ chạy khi màn nhà Kachi đang ở ĐỈNH display 0 (người dùng vừa chạm nút trên màn nhà; camera lùi / app
     * khác ở trên ⇒ 0 lệnh). Kết quả "đã ra khỏi ô" (MOVED · MOVED_HOME_RESTORED · X_FRONT_HOME_RESTORED) chỉ trả khi bản
     * đọc CUỐI không còn task nào của [a] trên [vd] — bên gọi đổi ô theo SỰ THẬT đó, không theo mã (CLAUDE.md §5).
     */
    fun evictCovered(vd: Int, a: String, cover: CoverPort): Outcome {
        val tag = "slot-back vd=$vd A=$a"
        if (vd < 1) return Outcome(Result.X_NOT_STAGED, "$tag → màn ảo không hợp lệ, 0 lệnh")
        refuse(tag, a)?.let { return it }
        val before = read()
        val mine = before.filter { it.displayId == vd && it.pkg == a }
        val stop = when {
            before.isEmpty() -> "không đọc được am stack list"
            mine.isEmpty() -> "A không ở màn ảo ô"
            mine.any { it.activityType != BehindHomePlan.STANDARD || it.isPinned } -> "task A không phải standard"
            !BehindHomePlan.topIs(before, vd, a) -> "A không ở đỉnh màn ảo ô"
            homeComps.isEmpty() || !BehindHomePlan.homeOnTop(before, homeComps) -> "màn nhà không ở đỉnh display 0"
            else -> null
        }
        if (stop != null) return Outcome(if (mine.isEmpty()) Result.X_NOT_STAGED else Result.KEPT_UNDER, "$tag → $stop, 0 lệnh")
        val out = try {
            when {
                !cover.cover(vd) -> Outcome(Result.KEPT_UNDER, "$tag → lớp che không lên, 0 move-task")
                waitTop(vd, selfPkg, false).timedOut -> Outcome(Result.KEPT_UNDER, "$tag → lớp che không lên đỉnh màn ảo ô, 0 move-task")
                else -> evict(vd, a, selfPkg)
            }
        } finally {
            runCatching { cover.uncover() }
        }
        val res = afterStage(tag, a, 0L, out, homeWasTop = true)
        if (!res.outOfStage) return res
        // Đọc CUỐI quyết ô (bên gọi nhả màn ảo ô theo kết quả này — cờ 256 kết thúc activity còn trên đó): không đọc được /
        // A còn task trên màn ảo ⇒ ô GIỮ app (nhịp đo ô tự thấy nếu A thật ra đã rời — luật hoàn ô `APP_DIED`).
        val fin = read()
        if (fin.isEmpty() || fin.any { it.displayId == vd && it.pkg == a }) {
            return Outcome(Result.KEPT_UNDER, "${res.line} · đọc lại: ${if (fin.isEmpty()) "không đọc được" else "A còn task trên màn ảo ô"} ⇒ ô giữ app")
        }
        return res
    }

    /** Kết quả dọn màn ảo ẩn: task còn lại ([left] `null` = ĐỌC HỎNG — chưa biết, cấm nhả) + X có nhờ K7 mới ra không. */
    private data class Vacated(val left: List<StackEntry>?, val rescued: Boolean)

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
        val left = settle(vd) ?: return Vacated(null, rescued = false)
        val stuck = left.firstOrNull { it.pkg == x && BehindHomePlan.safeComponent(it.comp) }
        if (stuck == null || homeComps.isEmpty()) return Vacated(left, rescued = false)
        sh(SlotReturn.guardedDetachCmd(cameraSig, homeComps.toList(), stuck.comp))
        // [P1] Đọc HỎNG sau K7 ≠ "X đã rời": không dấu, không K12 (K7 có thể đã bị rào chặn vì app khác ở trước — K12 lúc đó
        // kéo người dùng khỏi app họ đang dùng), không nhả.
        val after = settle(vd) ?: return Vacated(null, rescued = false)
        if (after.any { it.pkg == x }) return Vacated(after, rescued = false)      // rào K7 chặn (camera / màn nhà không hiện)
        // Soát vòng 2 [P3]: bản đọc cho DẤU hỏng ⇒ không dấu, không K12 (X sau màn nhà mà không mang dấu thì Kachi chết là X
        // nổi lên, lượt trả lại không nhận ra) — cùng luật nhánh trên: chưa rõ, GIỮ màn ảo, lượt sau thu hồi.
        val main = readOrNull() ?: return Vacated(null, rescued = false)
        markMain(main, setOf(x))
        runCatching { sh(goHomeCmd) }
        return Vacated(after, rescued = true)
    }

    /** Đọc lại (≤ 1 + [SETTLE_READS] lượt) tới khi màn ảo [vd] hết task của chính Kachi (lớp che vừa gỡ). `null` = đọc HỎNG. */
    private fun settle(vd: Int): List<StackEntry>? =
        StackReads.settle(sh, sleep, SETTLE_READS + 1, X_TOP_STEP_MS) { e -> e.none { it.displayId == vd && it.pkg == selfPkg } }
            .read.entries?.filter { it.displayId == vd }

    /**
     * Đuôi chung của hai đường dàn dựng. Đọc lại cả khi `MOVED` (review lượt 3 [P3]): `verifyMoved` chỉ so đỉnh display 0
     * với bản đọc NGAY TRƯỚC move-task — nếu X đã có một task tự lên trước màn nhà từ lúc dàn (trung chuyển ở lại màn ảo,
     * task chính mở NEW_TASK lên display 0) thì đỉnh "không đổi" mà màn nhà vẫn bị che. Chỉ bỏ qua `MOVED_HOME_RESTORED`.
     */
    private fun afterStage(tag: String, x: String, waited: Long, out: Outcome, homeWasTop: Boolean): Outcome {
        if (out.result == Result.MOVED_HOME_RESTORED || !homeWasTop) return out.copy(line = "$tag chờ=${waited}ms · ${out.line}")
        // Soát vòng 2 [P3]: đọc HỎNG ≠ "X không lên trước màn nhà" — trước đây `[]` ⇒ `fellFront` false ⇒ trả nguyên mã
        // (KEPT_UNDER/MOVED = OK) trong khi X có thể đang che màn nhà. Chưa rõ ⇒ UNREAD: không dấu (không có task id thật),
        // không K12 (không quyết đổi cửa sổ trên một bản đọc không có).
        val now = readOrNull()
            ?: return Outcome(Result.UNREAD, "$tag chờ=${waited}ms · ${out.line} · đọc lại hỏng ⇒ chưa rõ X có lên trước màn nhà, 0 dấu 0 K12")
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
        // Soát vòng 2 [P3]: đọc lại HỎNG sau move-task ≠ NOT_MOVED — bản cũ (`[]` ⇒ NOT_MOVED) GỠ dấu bền trong khi A có thể đã
        // sau màn nhà (CLAUDE.md §5: dấu phải sống lâu hơn thay đổi). Chưa rõ ⇒ GIỮ dấu, gỡ giữ chỗ (R0.7), 0 K12, UNREAD.
        val r3 = readOrNull() ?: run {
            val gone = finish()
            return Outcome(Result.UNREAD, "$tag → UNREAD (đọc lại sau move-task hỏng — giữ dấu) task=$taskA S=$s gỡ=$gone dọn-trước=$cleaned")
        }
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
