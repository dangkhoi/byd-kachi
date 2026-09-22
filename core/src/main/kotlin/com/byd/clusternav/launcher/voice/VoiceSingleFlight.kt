package com.byd.clusternav.launcher.voice

/**
 * ═══ [P0 xe 2026-09-16] MỘT MICRO TẠI MỘT THỜI ĐIỂM, VÀ MỘT CẦU CHÌ THEO PHÚT ════════════════════════════════
 *
 * ## Bệnh nó chữa — [ĐO xe] `voice-1.68-real.txt`, 12 phút trên xe owner
 * **309** lượt mở micro, trong khi chỉ có **7** dòng `lượt 1 (ngữ pháp)` — tức chỉ **7 phiên thật sự bắt đầu**,
 * và **không một dòng** `"đã có một phiên nghe đang chạy"` nào (người lái không hề bấm lại). Hơn 300 lượt mở
 * còn lại đến từ chính vòng hội thoại/hỏi-lại tự nuôi nhau: mô hình bịa ra `"ừ"` (102 lần) và `"ừm"` (102 lần)
 * trong lúc gần như im lặng, mỗi chuỗi ấy được coi là một lượt nói và mở lại micro.
 *
 * Trần `MAX_FOLLOW_UPS = 5` **đã có** và vẫn bị vượt ~7–20 lần. [SUY, đọc mã] hai chỗ rò, và bản vá này không
 * phụ thuộc vào việc chẩn đoán đúng chỗ nào:
 *  1. đường **hỏi lại** (`askAgain` → `listenAgain`) cũng mở micro nhưng **không** đi qua bộ đếm `followUps`;
 *  2. `VoiceSession.capturing` là **một** cờ cho cả tiến trình, đặt/xoá trong `whileCapturing` — hai lượt chồng
 *     nhau thì lượt xong TRƯỚC xoá cờ cho cả hai, nên cổng `micOpen()` thôi canh. [ĐO] nhật ký có **4** dòng
 *     `mic mở sau …` trong 300 ms (19:09:00.340/.446/.473/.495) rồi **4** kết quả `sherpa ra` mâu thuẫn nhau
 *     (*"tắt đèn đọc tất cả cửa sổ"* vs *"tắt đèn tất cả cửa sổ"*).
 *
 * ## Vì sao chốt nằm ở ĐÂY chứ không ở `VoiceSession`
 * Vì đây là chốt **cuối cùng trước phần cứng**: nó được xin ngay tại chỗ mở `AudioRecord` ([VoiceCapture]), nên
 * mọi đường mở micro — lượt chính, hội thoại, hỏi lại, xác nhận, và **mọi đường ai đó thêm sau này** — đều đi
 * qua nó mà không phải nhớ gọi thêm gì. Một chốt đặt ở tầng phiên chỉ canh được những đường mà tầng phiên biết;
 * đúng hai chỗ rò ở trên là hai đường mà nó **không** biết. Cùng luật CLAUDE.md §5: *"guard cứng đặt ở tầng thi
 * hành, không đặt ở tầng UI"*.
 *
 * ## Vì sao cầu chì theo phút, khi đã có chốt một-lượt
 * Chốt một-lượt chặn **chồng lấn**; nó không chặn **nối đuôi**. Một vòng lặp tuần tự (mở → nghe → bịa ra `"ừ"` →
 * mở lại) không bao giờ có hai micro cùng lúc mà vẫn ăn hết CPU — đúng thứ làm ô YouTube của owner không cuộn
 * nổi. Cầu chì là lớp cuối: nó **không** biết gì về nguyên nhân, nên một lỗi tương lai chưa ai nghĩ ra cũng bị
 * nó chặn. [ĐO] 309 lượt / 12 phút ≈ **26 lượt/phút**; trần [MAX_OPENS_PER_MINUTE] = 12 vẫn rộng gấp đôi một
 * phiên hội thoại dài nhất hợp lệ (1 lượt chính + 5 lượt nối) mà đã chặn đứng con số 26.
 *
 * ## THUẦN có chủ ý — không `android.util.Log`, không `SystemClock`
 * Lớp này là một máy trạng thái có đồng hồ, tức đúng thứ phải kiểm được bằng cách **bơm thời gian giả**. Nó trả
 * về một [Grant] mô tả chuyện gì xảy ra và để [VoiceCapture] ghi nhật ký — chỗ đã có Android trong tay. Nhét
 * `Log` vào đây là biến mọi bài kiểm thành bài cần Robolectric.
 *
 * ## Vì sao nằm ở `:core` chứ không cạnh [VoiceCapture]
 * Nó **thuần** (không một dòng `android.*`), và `LayeringRulesTest` khoá đúng điều đó: *"số file thuần còn
 * nằm trong `:app` chỉ được GIẢM"*. Một máy trạng thái có đồng hồ để ở `:app` nghĩa là bài kiểm của nó phải
 * chạy qua Robolectric để chứng minh một thứ không liên quan gì tới Android. Vì ở `:core` nên `internal`
 * không tới được `:app` — công khai, và cái giữ cho nó không bị gọi bừa là **chỗ gọi duy nhất** ngay trước
 * `openRecord()` (bài canh `VoiceLoopGuardWiringContractTest` ghim số chỗ gọi ấy).
 */
object VoiceSingleFlight {

    /** Trần số lượt mở micro trong một cửa sổ [WINDOW_MS] — xem KDoc lớp. */
    const val MAX_OPENS_PER_MINUTE = 12

    const val WINDOW_MS = 60_000L

    /**
     * Nhãn của bộ nghe **"Hey Kachi"** — lượt DUY NHẤT giữ micro **liên tục** thay vì theo phiên.
     *
     * Nó khác mọi nhãn khác ở hai điểm, và cả hai đều phải được lớp này biết (nếu không thì hai tính năng đúng
     * khi đứng riêng sẽ sai khi gặp nhau — đúng họ lỗi giao-điểm mà dự án đã trả giá):
     *  1. **[acquireWake] KHÔNG tiêu hạn mức phút** — xem KDoc ở đó.
     *  2. **Nó nhường được** ([requestYield]) — vì nếu không thì người lái bấm nút mic sẽ chỉ nhận `Busy("wake")`
     *     mãi mãi.
     */
    /**
     * Nhãn lượt nghe **do NGƯỜI bấm mở** — nút mic / ô thanh nút / phím vô-lăng / cầu kiểm thử đều mở lượt
     * ĐẦU của phiên bằng nhãn này (khớp `VoiceCapture.LABEL_MAIN = "chinh"`, cùng lệ ASCII).
     *
     * ## Vì sao lượt này KHÔNG bao giờ bị cầu chì từ chối ([acquire] `userInitiated`)
     * [ĐO xe — team 2026-09-22] người dùng gặp *"seri ngu: nói gì cũng không hiểu, phải tắt voice mở lại"*.
     * Gốc: một câu nghe nhầm (vd *"tắt kính lái"*) đi qua vòng hỏi-lại/hội-thoại (mỗi lượt là một lần mở mic,
     * tối đa `MAX_FOLLOW_UPS` = 5) cộng vài lần bấm lại ⇒ đốt hết [MAX_OPENS_PER_MINUTE] = 12 trong 60 s ⇒ mọi
     * lượt sau nhận `Fused` ⇒ chuỗi rỗng ⇒ *"không nghe thấy"* tới khi các suất **già đi** (≤ 60 s) hoặc tiến
     * trình chết (đó là điều *"tắt voice mở lại"* làm — [opens] là quỹ mức tiến-trình, [start] một phiên mới
     * KHÔNG xoá nó). Trần 12/phút sinh ra để chặn **vòng lặp phiên tự nuôi** (309 lượt/12 phút từ ảo giác từ
     * đệm) — thứ ấy do **vòng auto** (hỏi-lại/hội-thoại/xác-nhận) tự đẻ, KHÔNG phải người bấm nút 309 lần. Nên
     * lượt do người chủ động mở **luôn được cấp** (vẫn ghi một suất để các lượt auto SAU nó trong cùng phiên
     * vẫn bị đếm và vẫn chặn được vòng auto). Cùng tinh thần [acquireWake] không tiêu quỹ.
     */
    const val LABEL_COMMAND = "chinh"

    /** Lượt này có phải do NGƯỜI chủ động mở không — xem [LABEL_COMMAND]. */
    fun isCommandLabel(label: String): Boolean = label == LABEL_COMMAND

    const val LABEL_WAKE = "wake"

    /** Chủ hiện tại có phải bộ nghe wake không (nhãn có thể mang hậu tố `#<số>` của từng lượt chạy). */
    fun isWakeLabel(label: String): Boolean = label == LABEL_WAKE || label.startsWith("$LABEL_WAKE#")

    /** Kết quả xin mở micro. */
    sealed interface Grant {
        /** Được mở. Chỗ gọi **phải** gọi [release] trong `finally`. */
        object Ok : Grant

        /** Đang có một lượt khác giữ micro — [holder] là nhãn của lượt ấy (để nhật ký nói được ai chắn ai). */
        data class Busy(val holder: String) : Grant

        /** Cầu chì: đã [opens] lượt trong cửa sổ vừa qua. */
        data class Fused(val opens: Int) : Grant
    }

    private val lock = Any()
    private var holder: String? = null

    /**
     * Có ai đang **xin chủ hiện tại nhường** micro không — chỉ có nghĩa với chủ `wake` (xem [requestYield]).
     * Nằm trong cùng `lock` với [holder] để không phải nghĩ về thứ tự nhìn thấy giữa hai luồng.
     */
    private var yieldWanted = false

    /** Mốc giờ của các lượt **đã được cấp**, cũ → mới. Chỉ giữ trong cửa sổ, nên nó không lớn quá [MAX_OPENS_PER_MINUTE]. */
    private val opens = ArrayDeque<Long>()

    /**
     * Xin quyền mở micro.
     *
     * @param label nhãn lượt (`chinh` · `hoi-thoai` · `hoi-lai` · `xac-nhan`) — đi vào nhật ký khi bị chắn.
     * @param nowMs đồng hồ, bơm được để kiểm off-device.
     */
    fun acquire(label: String, nowMs: Long = System.currentTimeMillis()): Grant = synchronized(lock) {
        holder?.let { return Grant.Busy(it) }
        trim(nowMs)
        // ⚠ Cầu chì đếm lượt **ĐƯỢC CẤP**, không đếm lượt XIN. Đếm lượt xin thì một tràng bị chốt một-lượt chắn
        // lại (tức đã vô hại) vẫn đốt hết hạn mức, và người lái mất micro vì một lỗi mà lớp trước đã chặn xong.
        // ⚠ Lượt do NGƯỜI chủ động mở ([LABEL_COMMAND]) KHÔNG bao giờ bị `Fused` — xem KDoc [LABEL_COMMAND]
        // (vá "seri ngu"). Vẫn ghi một suất để các lượt AUTO sau nó trong cùng phiên vẫn bị đếm và chặn được.
        if (!isCommandLabel(label) && opens.size >= MAX_OPENS_PER_MINUTE) return Grant.Fused(opens.size)
        opens.addLast(nowMs)
        holder = label
        yieldWanted = false
        return Grant.Ok
    }

    /**
     * Xin micro cho **bộ nghe wake** — giống [acquire] nhưng **KHÔNG tiêu một suất của hạn mức phút**.
     *
     * ## Vì sao phải khác (nếu không thì tính năng nền giết tính năng chính)
     * Hạn mức [MAX_OPENS_PER_MINUTE] sinh ra để chặn **vòng lặp phiên tự nuôi nhau** (309 lượt/12 phút). Bộ nghe
     * wake không phải một phiên: nó là **một** lượt giữ mic dài, và nó buộc phải nhả–xin lại mỗi lần load-guard
     * cắt (hệ nóng) hay mỗi lần vừa nổ wake. [SUY, đọc mã] với `SUSPEND_NAP_MS = 2 s`, một chiếc xe đang nóng
     * (đúng ca [ĐO] load 14) làm vòng ngoài xin lại ~30 lần/phút ⇒ **hạn mức bị bộ nghe nền tiêu hết**, và cú
     * bấm nút mic của người lái nhận `Fused` — tức một tính năng **mặc định TẮT** làm chết tính năng chính.
     *
     * Chỗ hạn mức được tiêu đúng là **[handoff]**: một `wake → command` là một lượt mở phiên thật, và một tràng
     * false-accept vẫn bị trần 12/phút chặn ở đó. Trần tốc độ của riêng vòng wake là việc của bộ nghe (load-guard
     * + nghỉ có tăng dần), không phải của hạn mức phiên.
     */
    fun acquireWake(label: String = LABEL_WAKE): Grant = synchronized(lock) {
        holder?.let { return Grant.Busy(it) }
        holder = label
        yieldWanted = false
        return Grant.Ok
    }

    /** Nhả micro. An toàn khi gọi thừa (lượt bị chắn vẫn có thể chạy qua `finally` của chỗ gọi). */
    fun release() = synchronized(lock) { holder = null; yieldWanted = false }

    /**
     * Nhả micro **chỉ khi chủ đúng là [label]** — dùng cho bộ nghe wake.
     *
     * ## Ca hỏng nó chặn
     * Bộ nghe wake sống trên một luồng nền và có thể bị dừng (tắt màn / tắt công tắc / service chết + START_STICKY
     * dựng lại) **trong lúc** đang cuộn nốt vòng của nó. Một luồng cũ đang thoát mà gọi [release] trần sẽ xoá chủ
     * của **lượt mới** ⇒ ngay sau đó một phiên lệnh xin được mic **cùng lúc** với bộ nghe mới ⇒ đúng thứ cả lớp
     * này sinh ra để chặn: **hai `AudioRecord` mở một lúc**. Mỗi lượt chạy mang nhãn riêng (`wake#<n>`) nên phép
     * so nhãn ở đây là phép so *quyền sở hữu*, không phải phép so tên.
     */
    fun release(label: String) = synchronized(lock) {
        if (holder == label) { holder = null; yieldWanted = false }
    }

    /**
     * Xin chủ hiện tại **nhường** micro (chỉ bộ nghe wake có hỏi). Không chặn, không cướp: chỉ dựng cờ.
     *
     * Bộ nghe wake hỏi [yieldRequested] mỗi khung (~100 ms) nên nó nhả trong khoảng một khung + `stop/release`.
     * Chỗ gọi (phiên lệnh) chờ **có trần cứng** rồi bỏ qua — không có đường chờ vô hạn nào ở đây.
     */
    fun requestYield() = synchronized(lock) { if (holder != null) yieldWanted = true }

    /** Chủ hiện tại có đang bị xin nhường không — bộ nghe wake hỏi mỗi khung. */
    fun yieldRequested(): Boolean = synchronized(lock) { yieldWanted }

    /**
     * Chuyển micro NGUYÊN TỬ từ [fromLabel] sang [toLabel] — cho "Hey Kachi": bộ nghe wake đang GIỮ mic liên tục
     * (`holder = "wake"`), khi bắt được câu gọi thì trao thẳng cho phiên lệnh mà **không có khe hở** giữa
     * `release()` và `acquire()` cho lượt khác chen vào (một mic tại một thời điểm — R-nf3).
     *
     *  • Chỉ chuyển nếu chủ hiện tại **đúng** là [fromLabel] (chủ đã đổi ⇒ trả [Grant.Busy], không cướp bừa).
     *  • Lượt mới VẪN tính vào **cầu chì** — một wake→command là một lượt mở mới hợp lệ; storm false-accept vẫn
     *    bị trần 12/phút chặn. Nếu chạm cầu chì: **giữ nguyên chủ wake** (không tạo khe trống), phiên lệnh nhường.
     */
    fun handoff(fromLabel: String, toLabel: String, nowMs: Long = System.currentTimeMillis()): Grant = synchronized(lock) {
        if (holder != fromLabel) return Grant.Busy(holder ?: "?")
        trim(nowMs)
        if (opens.size >= MAX_OPENS_PER_MINUTE) return Grant.Fused(opens.size)
        opens.addLast(nowMs)
        holder = toLabel
        yieldWanted = false
        return Grant.Ok
    }

    /** Có lượt nào đang giữ micro không — chỉ để nhật ký/chẩn đoán, KHÔNG dùng làm cổng (xem KDoc lớp). */
    fun busy(): Boolean = synchronized(lock) { holder != null }

    /** Số lượt đã mở trong cửa sổ tính tới [nowMs]. */
    fun opensInWindow(nowMs: Long = System.currentTimeMillis()): Int = synchronized(lock) { trim(nowMs); opens.size }

    /** Xoá sạch trạng thái — **chỉ cho bài kiểm**; tiến trình thật không bao giờ cần. */
    fun reset() = synchronized(lock) { holder = null; opens.clear(); yieldWanted = false }

    private fun trim(nowMs: Long) {
        // `<=` chứ không `<`: một mốc đúng bằng mép cửa sổ đã ra ngoài cửa sổ. Dùng `<` thì trần thành 13 ở đúng
        // nhịp đều đặn nhất — loại lệch-một mà không bài kiểm nào bắt được nếu chỉ thử các mốc ngẫu nhiên.
        while (opens.isNotEmpty() && nowMs - opens.first() >= WINDOW_MS) opens.removeFirst()
    }
}
