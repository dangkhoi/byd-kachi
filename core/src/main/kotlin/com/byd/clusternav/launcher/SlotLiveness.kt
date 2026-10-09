package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.StackParse

/**
 * ═══ LUẬT "APP TRONG Ô CÒN SỐNG KHÔNG" (thuần JVM :core) ══════════════════════════════════════════════════════
 *
 * [ĐO] 2026-09-14 (`docs/diagnostics/waze-into-slot-research-2026-09-14.md` §5): app đang chiếu trong ô bị
 * `am force-stop` thì `SurfaceView` **giữ nguyên khung hình cuối** — ô trông vẫn sống, chạm vào không có gì xảy
 * ra. Kênh im lặng phải nói: muốn nói được thì trước hết phải **biết** nó chết, mà cái biết đó đến từ một phép
 * đo lặp (`am stack list` → còn task của gói trên màn ảo của ô không).
 *
 * Lớp này giữ phần QUYẾT ĐỊNH của phép đo đó, tách khỏi shell/Handler để test off-device. Hai luật:
 *
 *  1. **Chưa từng thấy sống thì không được kết luận chết.** `am start` tới lúc task hiện ra mất vài giây; nếu
 *     nhịp đo đầu tiên rơi vào khoảng đó mà đã kết luận thì ô vừa mở đã bị coi là "app đã đóng" — và ô sẽ bị trả về
 *     trong suốt (luật hoàn ô L6) ngay ở lần dùng đầu tiên, tức là sai ở chỗ tệ nhất.
 *  2. **Phải trượt [missesToDie] nhịp liên tiếp.** Một nhịp hụt đơn lẻ (shell timeout, app đang đổi task, dump
 *     bị cắt) không phải cái chết. Mặc định 2 nhịp × 5 s = 10 s im lặng mới kết luận.
 *
 * 2.93 · SLOT-APP-ESCAPE + SHORTCUTS-B-ESCAPE (spec `docs/specs/kachi-293-slot.html` R3): nhịp vắng màn ảo ô mà task của
 * gói còn ở display KHÁC ([observe] `away` — `SlotPresence.ELSEWHERE`, cùng bản đọc, 0 lệnh thêm) là bằng chứng DƯƠNG "app ra
 * khỏi ô, vẫn mở" — không phải "chưa kịp vào". [ĐO máy ảo 02/10 `finish/esc-after-chain`, QA 04/10] Waze tự `launchToSide` ra
 * display 0 ~1 s sau khi vào ô (cơ chế [ĐO nguồn A10/A12]: activity thứ hai do CHÍNH app mở, không `allowEmbedded` ⇒ cả task về
 * display 0 — `waze-into-slot-research-2026-09-14.md` §1) ⇒ bộ đo CHƯA từng thấy app trong ô ⇒ luật 1 cấm kết luận ⇒ ô đen
 * mãi. Nay: [ELSEWHERE_SWEEPS] nhịp ĐỌC ĐƯỢC liên tiếp thấy app ở chỗ khác ⇒ kết luận cả khi chưa từng thấy sống, cờ
 * [elsewhere] (bên gọi báo đúng *"đã rời ô, vẫn mở ngoài ô"* thay vì coi là chết). Màn ảo nhận lại từ ô 7 ([adopted]) giữ luật
 * cũ (mở lại app — PARK-2b).
 *
 * Senior review 2.93 Pass 2 [P3] — nhịp "ở chỗ khác" chỉ ĐẾM cho kết luận chưa-từng-thấy-sống khi màn ảo ô KHÔNG còn task của
 * app nào khác ([observe] `othersInSlot`, [othersInSlot] — cùng bản đọc, 0 lệnh). Lý do: kết luận dẫn tới luật hoàn ô ⇒ host nhả
 * màn ảo ⇒ cờ 256 kết thúc MỌI activity còn trên đó — một quyết định về app B không được kết thúc app khác còn ở ô (ô chưa trống,
 * không phải "app rời ô"). Ca đã thấy khi đọc mã [SUY]: đặt tạm tại chỗ (`VdAppHost.swapApp` → `KachiHomeSlots.evictBehind`) — B
 * rơi thẳng về display 0, A còn ở ĐỈNH màn ảo, chuỗi trả `B_NOT_IN_SLOT` ⇒ host nhận lại A; bộ đo của B đăng ký TRƯỚC chuỗi nên
 * chuỗi chậm hơn hai nhịp (trên xe mọi lệnh xếp một hàng `ShellTransport`) là kết luận "B ở chỗ khác" tới trước và kết thúc A.
 * Đường đó KHÔNG còn chỗ gọi từ 2.89-thử1 (`swapInPlace` — [ĐO grep 06/10]) ⇒ ẩn, không phải lỗi đang chạy; cổng giữ cho ngày nó
 * được nối lại. Đã thấy sống thì giữ luật 2 như 2.92 (không đổi).
 *
 * Sau khi đã báo chết, bộ đếm **không tự bật lại**: ô đi luật hoàn ô (L6 `SlotRevertPlan`). Một chu kỳ đo mới chỉ
 * bắt đầu khi ô đăng ký lại với `SlotLiveProbe.watch` (người dùng bấm mở lại) — và lần ấy là **một bản mới** của
 * lớp này, không phải bản cũ được bật lại.
 *
 * ⚠ [SOÁT Pass H2 · §8] Bản đầu có thêm `reset()`/`hasSeenAlive()` cho đúng câu KDoc này, nhưng **không chỗ nào
 * trong mã sản phẩm gọi chúng** (đường mở lại dựng một `Sub` mới ⇒ một `SlotLiveness` mới). Hai hàm chỉ-test-gọi
 * cộng một câu KDoc mô tả cơ chế không tồn tại là đúng cái bẫy CLAUDE.md §8 nói tới, nên chúng đã bị gỡ.
 */
class SlotLiveness(
    private val missesToDie: Int = DEFAULT_MISSES,
    /**
     * Ô 7 (2.89-thử1, spec 287 §4.6d) — màn ảo NHẬN LẠI từ chỗ đỗ: app đã ở sẵn trên đó (không có lượt `am start` nào
     * đang chạy) ⇒ luật 1 không áp: [ADOPTED_MISSES] nhịp hụt (PARK-2b: MỘT nhịp — bên gọi chỉ nạp nhịp ĐỌC ĐƯỢC, và không
     * lượt mở nào đang dở trên màn ảo đã lấy ra ⇒ một lần vắng là kết luận) mà CHƯA từng thấy sống = app đã rời màn ảo lúc
     * đang đỗ (mở toàn màn ở display 0 · chết · bị dừng) ⇒ kết luận với [missing] = `true` để bên gọi mở app như đường thường
     * thay vì để khung đen. Đã thấy sống ⇒ cái chết sau đó vẫn cần [missesToDie] nhịp như mọi ô. Mặc định `false` = hôm nay.
     */
    private val adopted: Boolean = false,
) {

    /** Đã thấy app sống ít nhất một nhịp — ĐỌC được cho chỗ dàn dựng BEHIND-HOME (A5: chỉ ô đã sống mới được dùng). */
    var seenAlive = false
        private set

    /** Kết luận vừa trả của [observe] là "màn ảo nhận lại KHÔNG có app" (chỉ khi [adopted]), không phải "app vừa chết". */
    var missing = false
        private set

    /** 2.93 · R3 — kết luận vừa trả của [observe] là "app RA KHỎI ô, task còn ở display khác" (không phải đã đóng). */
    var elsewhere = false
        private set
    private var misses = 0
    private var aways = 0
    private var reported = false

    /**
     * Nạp một nhịp đo ([away] = vắng màn ảo ô NHƯNG task còn ở display khác — chỉ có nghĩa khi `alive = false`; [othersInSlot] =
     * màn ảo ô còn task của app KHÁC — KDoc lớp, Pass 2). Trả `true` **đúng một lần**, tại nhịp mà ô chuyển từ sống sang chết
     * (hoặc, với [adopted], tại nhịp kết luận màn ảo nhận lại không có app — [missing]; hoặc tại nhịp thứ [ELSEWHERE_SWEEPS] liên
     * tiếp thấy app ở chỗ khác mà màn ảo ô không còn app nào khác, khi chưa từng thấy nó trong ô — [elsewhere]).
     */
    fun observe(alive: Boolean, away: Boolean = false, othersInSlot: Boolean = false): Boolean {
        if (reported) return false
        if (alive) { seenAlive = true; misses = 0; aways = 0; return false }
        aways = if (away && !othersInSlot) aways + 1 else 0
        if (!seenAlive && !adopted) {
            if (aways < ELSEWHERE_SWEEPS) return false
            reported = true
            elsewhere = true
            return true
        }
        misses++
        if (misses < (if (seenAlive) missesToDie else ADOPTED_MISSES)) return false
        reported = true
        missing = !seenAlive
        elsewhere = away
        return true
    }

    companion object {
        /** Số nhịp hụt liên tiếp để kết luận chết (nhịp đo = [PROBE_PERIOD_MS]). */
        const val DEFAULT_MISSES = 2

        /** PARK-2b — màn ảo nhận lại từ ô 7, chưa thấy app: một nhịp ĐỌC ĐƯỢC vắng app là đủ kết luận "trống" ([missing]). */
        const val ADOPTED_MISSES = 1

        /**
         * 2.93 · R3 — số nhịp ĐỌC ĐƯỢC liên tiếp thấy app ở display khác (chưa từng thấy nó trong ô) để kết luận [elsewhere]: cùng
         * độ chắc của luật 2 ([DEFAULT_MISSES] nhịp × 5 s) — một nhịp đơn lẻ có thể rơi giữa lúc hệ dời task.
         */
        const val ELSEWHERE_SWEEPS = 2

        /**
         * Senior review 2.93 Pass 2 [P3] — bản đọc [stackList] (nguyên văn `am stack list`) có task của app KHÁC [pkg] trên màn ảo
         * ô [vd] không. Cùng bộ đọc A10 `Stack id=` / A12 `RootTask id=` ([StackParse]) với `SlotPresence` — đọc rỗng ⇒ `false`
         * (bên gọi chỉ hỏi khi `SlotPresence` đã đọc được ra `ELSEWHERE`). Đầu vào `othersInSlot` của [observe].
         */
        fun othersInSlot(stackList: String, pkg: String, vd: Int): Boolean =
            vd >= 1 && StackParse.parse(stackList).any { it.displayId == vd && it.pkg != pkg }

        /**
         * 2.98 · R7 (owner 09/10 #1: *"mở waze nó nhảy full… lâu lắm nó mới nhảy vào ô"* — [ĐO máy ảo] 10–14 s) — mốc đo THÊM, tính từ
         * lúc một ô bắt đầu được đo sau lượt mở app vào ô (`SlotLiveProbe.watch`). App thoát ô ([ĐO] ~1 s sau khi vào) ⇒ hai mốc
         * liên tiếp thấy "ở chỗ khác" ([ELSEWHERE_SWEEPS]) ⇒ kết luận ~2–3,5 s thay vì 10 s. Có TRẦN: đúng [LAUNCH_BURST_MS].size lượt
         * đọc mỗi lần mở (cùng MỘT `am stack list` cho mọi ô, dùng lại bản đọc mới qua `StackListSnapshot`), không lặp, không chạy khi
         * không mở app nào. Chỉ bật khi R7 bật (`SlotEscapeHome`); R7 tắt ⇒ rỗng = hành vi trước 2.98.
         */
        val LAUNCH_BURST_MS: List<Long> = listOf(1_000L, 2_000L, 3_000L, 4_500L, 6_500L, 9_000L)

        /** Chu kỳ đo: 5 giây — trần "không poll dày" của H2; 1 lệnh `am stack list` cho TẤT CẢ ô mỗi nhịp. */
        const val PROBE_PERIOD_MS = 5_000L

        /** Trần lùi nhịp khi kết quả đứng yên (K8) — 15 s. */
        const val PROBE_PERIOD_MAX_MS = 15_000L

        /** Số nhịp đứng yên liên tiếp trước khi bắt đầu lùi nhịp. */
        const val PROBE_BACKOFF_AFTER = 2

        /**
         * K8 (1.70) — nhịp đo TIẾP THEO theo số nhịp mà kết quả **không đổi** liên tiếp.
         *
         * [ĐO xe 2026-09-17] `KachiPerf shell=36/phút`, trong đó `am stack list` mỗi 5 s = 12/phút chỉ để thấy
         * cùng một câu trả lời hàng giờ (app trong ô sống yên). Lùi 5 → 10 → 15 s khi đứng yên; **bất kỳ** thay
         * đổi nào (một ô vắng task) đưa về 5 s ngay, nên kết luận chết vẫn cần 2 nhịp hụt **ở nhịp 5 s** — tức
         * chậm nhất là 15 s (nhịp đang lùi) + 5 s, thay vì 10 s.
         */
        fun probePeriodMs(unchangedSweeps: Int): Long {
            if (unchangedSweeps < PROBE_BACKOFF_AFTER) return PROBE_PERIOD_MS
            val steps = (unchangedSweeps - PROBE_BACKOFF_AFTER + 2).toLong()
            return (PROBE_PERIOD_MS * steps).coerceAtMost(PROBE_PERIOD_MAX_MS)
        }
    }
}
