package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.behind.BehindHomeSequence
import com.byd.clusternav.launcher.behind.SlotReturn
import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ A2 · TRIP-MUSIC-IN-SLOT (2.89) — app nhạc Ở ĐÂU thì giao lệnh phát Ở ĐÓ (thuần, `:core`) ════════════════════════════
 *
 * Owner 05/10 (xe 2.88): *"youtube nằm ở khung 1 nên nó tự mở chứ có cần phải setting mở app khi nổ máy đâu?"* — Cài đặt ghi
 * *"hết hạn chờ – chuyến này không mở app"*. [ĐO mã 2.88] (1) chuyến chờ cổng chung `SLOTS` (có ô app mà chưa ô nào đo thấy
 * sống) tới hết hạn 180 s dù bước nhạc không cần ô dàn dựng nào; (2) nhạc ngoài ô đi chuỗi BEHIND-HOME — [ĐO xe 05/10] giữ chỗ
 * ném NPE trên ROM BYD, lối lùi K7 đổi display ⇒ activity dựng lại ⇒ mất nhạc; (3) chuyến đọc "app ở ô" từ ảnh chụp ĐẦU
 * chuyến — ô của app nhạc có thể chưa hiện trong ảnh đó [SUY], hai đường (ô mở app bằng `force-stop` + `am start`, chuyến mở
 * app qua chỗ dàn dựng) giành nhau đúng một app.
 *
 * Lớp này quyết, bên `:app` (`TripMusicRun`) chỉ đọc + làm:
 *  - [where] — chỗ của app nhạc đọc MỚI mỗi nhịp (ô 0-based của bố cục đang hiện + chỗ dàn dựng của host ô đó);
 *  - [slot] / [await] — app ở ô ⇒ chờ CHÍNH ô đó có app sống (`SlotLiveProbe` của host; nhịp đo chưa nói ⇒ đọc thẳng
 *    `am stack list` mỗi [DIRECT_READ_EVERY] nhịp — CÙNG phép đo của nhịp đo ô) tới [SLOT_WAIT_MS], không quá hạn chuyến. Sống
 *    ⇒ phiên nhạc (0 lệnh cửa sổ) hoặc K4-VIEW `--display <màn ảo ô>` vào CHÍNH task đó ([TripMusicPlan.viewRoute]). Không
 *    `force-stop`, không BEHIND-HOME, không task thứ hai;
 *  - [fallBack] — app ngoài ô ⇒ ô 7 ([com.byd.clusternav.launcher.behind.HiddenPark]) trước; đường cũ (BEHIND-HOME) chỉ khi ô 7
 *    không chạy được.
 */
object TripMusicPlace {

    /** Trần chờ app trong ô sống ([SUY] ô mở app lúc nổ máy: `force-stop` + 1 s + `am start` + 2 s + nhịp đo 5 s ≈ 10–30 s). */
    const val SLOT_WAIT_MS = 90_000L
    const val SLOT_POLL_MS = 1_000L

    /** Nhịp đo ô chưa nói "sống" mà host ô đã có màn ảo ⇒ đọc thẳng `am stack list` mỗi chừng này nhịp chờ (một lệnh CHỈ ĐỌC). */
    const val DIRECT_READ_EVERY = 3

    /**
     * Một lượt đọc MỚI từ màn chính. [slot] = ô (0-based) chứa app trong bố cục đang hiện (`null` = không ở ô nào); [stage] =
     * chỗ dàn dựng của host ô đó (`null` = host chưa mở xong / chưa có màn ảo — `VdAppHost.stage()`).
     */
    data class Where(val slot: Int?, val stage: BehindHomePlan.Stage?)

    /** Chỗ của [pkg] từ bảng ô [slots] (gói → ô, cái đầu thắng) + [stages] (`WorkspaceView.stagingCandidates`). */
    fun where(pkg: String, slots: Map<String, Int>, stages: List<BehindHomePlan.Stage>): Where {
        val i = slots[pkg]
        return Where(i, i?.let { s -> stages.firstOrNull { it.slot == s && it.pkg == pkg } })
    }

    /**
     * Ô của app nhạc lúc bước nhạc BẮT ĐẦU (review 2.89 Pass 1 · behaviour-1). [fresh] = lượt đọc MỚI ([where] qua màn chính);
     * `null` = màn chưa trả lời trong hạn (`TripHub.onMain` 2 s) — KHÔNG có nghĩa "không ở ô" ⇒ dùng ô trong ảnh chụp
     * [snapshot] mà chuyến vừa đọc (`HomeView.slots`). Chỉ một lượt đọc thật `Where(slot = null)` mới là "ngoài ô". Ô sai
     * trong ảnh chụp tự sửa: [await] đọc lại mỗi nhịp và trả [Waited.Left] khi app không còn trong bố cục.
     * Trước đây `null` bị coi như ngoài ô ⇒ ô 7 + BEHIND-HOME giành đúng app đang mở trong ô (A2-R1 cấm).
     */
    fun entrySlot(fresh: Where?, snapshot: Int?): Int? = if (fresh != null) fresh.slot else snapshot

    enum class Slot { ALIVE, WAIT, LEFT }

    /**
     * Một nhịp chờ ô của [pkg]: rời bố cục (đổi hồ sơ / người lái đổi ô) ⇒ [Slot.LEFT]; host ô có màn ảo VÀ (nhịp đo đã thấy
     * sống, hoặc [entries] — một bản `am stack list` đọc thẳng — thấy task `standard` của app trên CHÍNH màn ảo đó) ⇒
     * [Slot.ALIVE]; còn lại ⇒ [Slot.WAIT]. Đọc hỏng ([entries] `null`) KHÔNG phải sống.
     */
    fun slot(pkg: String, w: Where, entries: List<StackEntry>?): Slot {
        val st = w.stage
        return when {
            w.slot == null -> Slot.LEFT
            st == null || st.pkg != pkg || st.vd < 1 -> Slot.WAIT
            st.alive -> Slot.ALIVE
            entries != null && SlotReturn.slotTask(entries, st.vd, pkg) != null -> Slot.ALIVE
            else -> Slot.WAIT
        }
    }

    /** Kết quả [await]. [ms] = đã chờ bao lâu (nhật ký + câu Cài đặt). */
    sealed interface Waited {
        data class Alive(val slot: Int, val vd: Int, val ms: Long) : Waited
        data class Timeout(val slot: Int, val ms: Long) : Waited
        /** App rời bố cục giữa lúc chờ ⇒ bên gọi đi đường "ngoài ô". */
        data class Left(val ms: Long) : Waited
    }

    /**
     * Chờ (CHẶN — chỉ luồng `kachi-trip`) ô [slot0] của [pkg] có app sống, tới [until] (mốc [now]; bên gọi lấy
     * `min(bây giờ + [SLOT_WAIT_MS], hạn chuyến)`). [read] = một lượt đọc MỚI từ màn chính (`null` = màn chưa trả lời ⇒ chờ
     * tiếp, ô cuối cùng biết vẫn là [slot0]); [stacks] = một bản `am stack list` (`null` = đọc hỏng), chỉ gọi mỗi
     * [DIRECT_READ_EVERY] nhịp khi host ô đã có màn ảo mà nhịp đo chưa nói sống. [onSlot] báo ô hiện tại (Cài đặt hiện
     * *"đang chờ YouTube ở ô 1"* — cờ RAM chỉ để hiển thị, CLAUDE.md §5).
     */
    fun await(
        pkg: String,
        slot0: Int,
        until: Long,
        now: () -> Long,
        sleep: (Long) -> Unit,
        read: () -> Where?,
        stacks: () -> List<StackEntry>?,
        onSlot: (Int) -> Unit = {},
    ): Waited {
        val t0 = now()
        var slot = slot0
        var n = 0
        while (true) {
            val w = read() ?: Where(slot, null)
            w.slot?.let { if (it != slot) { slot = it; onSlot(it) } }
            val needDirect = w.stage?.let { it.pkg == pkg && it.vd >= 1 && !it.alive } == true && n % DIRECT_READ_EVERY == DIRECT_READ_EVERY - 1
            when (slot(pkg, w, if (needDirect) stacks() else null)) {
                Slot.ALIVE -> return Waited.Alive(slot, w.stage!!.vd, now() - t0)
                Slot.LEFT -> return Waited.Left(now() - t0)
                Slot.WAIT -> Unit
            }
            if (now() >= until) return Waited.Timeout(slot, now() - t0)
            sleep(SLOT_POLL_MS)
            n++
        }
    }

    /**
     * 2.96 · R9 — số lần giao link K4-VIEW tối đa cho app ở ô ([viewWhenReady]): lần đầu + tối đa 3 lần sau khi chờ ô sống lại.
     * Chặn vòng lặp khi nhịp đo ô nói "sống" mà lượt giao link vẫn không thấy ô (hai lượt đọc khác thời điểm).
     */
    const val VIEW_TRIES = 4

    /**
     * Kết quả [viewWhenReady]. [outcome] = kết quả lần giao CUỐI (`null` = ô chưa có màn ảo, 0 lệnh); [code] = mã bước;
     * [slot] = ô cuối cùng biết; [tries] = số lần giao; [waitedMs] = tổng thời gian; [why] = lý do bỏ cuộc (`null` = đã giao /
     * không phải lỗi "ô chưa sẵn"): `tries` (hết [VIEW_TRIES]) · `deadline` (hết mốc trước khi chờ lại) · `slot-wait` (chờ ô
     * quá trần) · `left` (app rời bố cục giữa lúc chờ).
     */
    data class ViewTry(
        val outcome: BehindHomeSequence.Outcome?,
        val code: TripStepCode,
        val slot: Int?,
        val tries: Int,
        val waitedMs: Long,
        val why: String?,
    )

    /**
     * 2.96 · R9 — giao link K4-VIEW, KHÔNG bỏ cuộc khi ô của app chưa sẵn ([ĐO log xe 07/10 21:05:14]: chờ ô nói sống ở
     * +4358 ms, ~15 s sau lúc giao link host ô KHÔNG còn chỗ dàn dựng của app ⇒ `view:SLOT_NOT_READY` ⇒ NOOP; người lái chạm
     * ⇒ YouTube phát bài khác từ đầu). Lần giao ra [TripStepCode.SLOT_NOT_READY] (0 lệnh — [TripOutcome.ofView]) ⇒ chờ CHÍNH ô
     * đó sống lại bằng CÙNG [await] (cùng nhịp, cùng trần [SLOT_WAIT_MS] qua [until] mà bên gọi lấy từ hạn chuyến) rồi giao
     * lại; tối đa [VIEW_TRIES] lần. Hết trần ⇒ [TripStepCode.SLOT_WAIT] (câu Cài đặt "hết hạn chờ"), hết lần / rời bố cục ⇒
     * [TripStepCode.SLOT_NOT_READY] — cả hai kèm [ViewTry.why] cho nhật ký (không còn NOOP câm).
     * App ngoài ô ([slot0] `null`) ⇒ đúng MỘT lần giao như cũ. Không lệnh nào ở đây: [view] là cổng của bên thi hành.
     */
    fun viewWhenReady(
        pkg: String,
        slot0: Int?,
        until: Long,
        now: () -> Long,
        sleep: (Long) -> Unit,
        read: () -> Where?,
        stacks: () -> List<StackEntry>?,
        onSlot: (Int) -> Unit = {},
        view: (inSlot: Boolean) -> BehindHomeSequence.Outcome?,
    ): ViewTry {
        val t0 = now()
        var slot = slot0
        var tries = 0
        while (true) {
            val o = view(slot != null)
            tries++
            val code = TripOutcome.ofView(slot != null, o?.result)
            val s = slot
            if (code != TripStepCode.SLOT_NOT_READY || s == null) return ViewTry(o, code, slot, tries, now() - t0, null)
            if (tries >= VIEW_TRIES) return ViewTry(o, code, s, tries, now() - t0, "tries")
            if (now() >= until) return ViewTry(o, TripStepCode.SLOT_WAIT, s, tries, now() - t0, "deadline")
            onSlot(s)
            sleep(SLOT_POLL_MS)
            when (val w = await(pkg, s, until, now, sleep, read, stacks, onSlot)) {
                is Waited.Alive -> slot = w.slot
                is Waited.Timeout -> return ViewTry(o, TripStepCode.SLOT_WAIT, w.slot, tries, now() - t0, "slot-wait")
                is Waited.Left -> return ViewTry(o, TripStepCode.SLOT_NOT_READY, s, tries, now() - t0, "left")
            }
        }
    }

    /** Mốc dừng chờ ô: [SLOT_WAIT_MS] từ [now], không quá hạn chuyến [tripDeadlineAt]. */
    fun until(now: Long, tripDeadlineAt: Long): Long = minOf(now + SLOT_WAIT_MS, tripDeadlineAt)

    /**
     * App nhạc NGOÀI ô: ô 7 ([com.byd.clusternav.launcher.behind.HiddenPark]) ra [r] ⇒ có thử đường CŨ (BEHIND-HOME qua ô sống /
     * màn ảo ẩn) không. CHỈ khi ô 7 không chạy được mà đường cũ còn cửa: không tạo được màn ảo ẩn ([BehindHomeSequence.Result.NO_STAGE]
     * — ô sống vẫn dàn được) · X không lên màn ảo ẩn ([BehindHomeSequence.Result.X_NOT_STAGED]). Mọi mã khác ⇒ KHÔNG: đã đỗ /
     * đang chạy / đã ra sau màn nhà (xong) · đọc hỏng / không kênh / đã tắt / app hệ thống (đường cũ cũng ra như vậy, hoặc
     * thêm lệnh trên một bản đọc không có).
     */
    fun fallBack(r: BehindHomeSequence.Result): Boolean =
        r == BehindHomeSequence.Result.NO_STAGE || r == BehindHomeSequence.Result.X_NOT_STAGED
}
