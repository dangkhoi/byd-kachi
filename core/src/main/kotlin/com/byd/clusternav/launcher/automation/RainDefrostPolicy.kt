package com.byd.clusternav.launcher.automation

/**
 * Việc automation mưa→sấy muốn làm với hai nút sấy kính **ở một nhịp poll**.
 *
 * Ba khả năng, không hơn: bật · tắt · không đụng. `Leave` là ca **thường gặp nhất** (trời khô, hoặc sấy đang
 * đúng trạng thái mong muốn) nên nó phải là một giá trị tường minh, không phải `null` — `null` ở vị trí này từng
 * là chỗ chỗ gọi tự diễn giải thành "ghi lại cho chắc", tức ghi HAL mỗi phút cho một chiếc xe đang đỗ khô.
 */
sealed interface RainDefrostAction {
    /** Bật sấy TRƯỚC + SAU (spec R1.3 — owner chốt bật cả hai, không chỉ kính lái). */
    object TurnOn : RainDefrostAction

    /** Tắt sấy — CHỈ khi chính automation đã bật nó (spec R1.5). */
    object TurnOff : RainDefrostAction

    /** Không ghi gì cả. */
    object Leave : RainDefrostAction
}

/**
 * ═══ AUTOMATION #1 · MƯA → TỰ SẤY KÍNH · LUẬT QUYẾT ĐỊNH ═════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R1 (§Design › :core). Thuần Kotlin (`:core`, cấm `android.*`) ⇒ chạy
 * off-car; đọc HAL và ghi hai nút sấy là việc của `RainDefrostApplier` (`:app`).
 *
 * ## Trigger mưa — [ĐO] on-car 2026-09-20, KHÔNG phải suy đoán
 * `docs/diagnostics/oncar-1.84-session-2026-09-20.md` §1: `SETTING_FRONT_RAIN_WIPER_SPEED` (feature-id
 * `1196425250`, device `BYDAutoSettingDevice`) = **1 khô** (4/4 reads) · **2 khi tạt nước lên cảm biến** (7/7
 * reads), càng mưa to càng cao, **bistable** (tự về 1 khi khô, không latch).
 *
 * ⚠ Phiên đó cũng [ĐO] **bác bỏ** ba getter gạt mưa khác: `WIPER_FRONT_WIPER_LEVEL` **latch** (giữ 8 sau khi đã
 * tắt gạt), `WIPER_RELAY_STATE` **chết** (0 cả khi owner xác nhận đang gạt, 8/8 reads đứng im),
 * `WIPER_AREA_FRONT_STATE` trả `-10011` (invalid). Nghĩa là: spec cũ
 * `docs/specs/kachi-rain-defrost-and-rebind.html` dựng trigger trên **rơ-le gạt × cờ auto × độ ẩm** — đứng trên
 * một getter đã chứng minh là chết. Đừng quay lại đường đó.
 *
 * ## Vì sao ngưỡng là `> DRY` chứ không phải `>= RAIN_ON`
 * Hai phép so cho ra cùng kết quả trên thang đã đo (1 · 2 · cao hơn), nhưng ý nghĩa khác nhau: thang này là
 * **tốc độ gạt**, mở lên trên (mưa to hơn ⇒ số lớn hơn), và điều duy nhất đo được chắc chắn là *"1 = khô"*.
 * Neo vào [DRY] phát biểu đúng điều đã biết; neo vào [RAIN_ON] sẽ thành lời nói dối nếu firmware bản sau chèn
 * một mức 1.5 hay đổi bước thang.
 */
object RainDefrostPolicy {

    /** Trời khô — [ĐO] 4/4 reads sau khi lau khô cảm biến. */
    const val DRY = 1

    /** Mức mưa thấp nhất đã đo được — [ĐO] 7/7 reads khi tạt nước lên cảm biến sau gương. */
    const val RAIN_ON = 2

    /**
     * Trần hợp lý của thang tốc độ gạt, dùng bởi [plausible].
     *
     * `12` là số rộng rãi (thang thật đã đo chỉ tới 2–3; không xe nào có 12 mức gạt) — chủ ý **không** khít, vì
     * mục đích của nó không phải đoán đúng số mức mà là **chặn giá trị SENTINEL**: các getter họ gạt mưa của
     * chính ROM này trả `65535` và `-10011` khi hỏng ([ĐO] §1 doc trên).
     */
    const val MAX_PLAUSIBLE = 12

    /**
     * Có đang mưa theo một lần đọc — nguồn DUY NHẤT của phép so ngưỡng.
     *
     * Tách ra thành hàm (thay vì để `> DRY` rải ở hai nơi) vì `:app` cũng cần trả lời đúng câu này khi ghi nhật
     * ký chẩn đoán; hai chỗ so tay là hai chỗ lệch nhau khi ngưỡng đổi.
     */
    fun isRaining(rainSpeed: Int): Boolean = rainSpeed > DRY

    /**
     * Lọc một lần đọc HAL: trả chính nó nếu đọc được và nằm trong dải hợp lý, `null` nếu không.
     *
     * ## Vì sao phép lọc ở ĐÂY, không ở `:app`
     * [decide] nhận `Int` nên nó **buộc phải** coi mọi con số là thật. Mà [ĐO] §1: họ getter gạt mưa của ROM này
     * trả `65535` (latch) và `-10011` (invalid) khi hỏng — `65535 > DRY` ⇒ nếu sentinel lọt vào [decide] thì
     * automation **bật sấy giữa trời nắng** và giữ nguyên như thế, im lặng. Chặn ở `:app` thì phép chặn không có
     * bài canh (`:app` không test off-car được đường HAL); chặn ở đây thì nó là dữ kiện có test.
     *
     * ⚠ KHÔNG gộp vào [decide]: chỗ gọi phải phân biệt được *"trời khô"* (⇒ có thể phải TẮT sấy mình đã bật) với
     * *"không đọc được"* (⇒ **không kết luận gì**, giữ nguyên). Gộp lại là biến một lần đọc lỗi thành một lệnh
     * tắt sấy đang cần dùng.
     */
    fun plausible(raw: Int?): Int? = raw?.takeIf { it >= DRY && it <= MAX_PLAUSIBLE }

    /**
     * Quyết định một nhịp — spec R1.3 · R1.4 · R1.5.
     *
     * | mưa | sấy đang bật | của automation | ⇒ |
     * |---|---|---|---|
     * | có | không | – | **TurnOn** |
     * | có | có | – | Leave (đã đúng trạng thái) |
     * | không | có | **có** | **TurnOff** |
     * | không | có | không | Leave — sấy của NGƯỜI LÁI, không đụng (R1.5) |
     * | không | không | – | Leave |
     *
     * ## ⚠ Hàm này KHÔNG tự lo được R1.5 — phải gọi qua [RainDefrostOwner.step]
     * Đọc bảng ở hàng đầu: *mưa + sấy không bật* ⇒ TurnOn, **bất kể** [ownedByAuto]. Nên nếu người lái tự tắt
     * sấy giữa cơn mưa, nhịp poll kế tiếp thấy đúng ca đó và **bật lại** — automation giành nút với người đang
     * lái xe, mỗi 5 phút một lần. Spec R1.5 nói ngược lại (*"nhả quyền, không bật lại tới lần mưa sau"*).
     *
     * Chỗ vá không nằm trong hàm này: nó chỉ thấy **một** nhịp, còn *"người lái vừa tắt"* là một **thay đổi giữa
     * hai nhịp**. [RainDefrostOwner] giữ đúng phần ký ức đó rồi mới gọi vào đây ⇒ luật vẫn ở một chỗ, không có
     * bản sao thứ hai.
     *
     * @param rainSpeed lần đọc `SETTING_FRONT_RAIN_WIPER_SPEED` đã qua [plausible].
     * @param defrostOn sấy kính TRƯỚC đang bật (đọc từ xe, không phải cờ RAM — cờ RAM không thấy người lái bấm).
     * @param ownedByAuto cái sấy đang bật do CHÍNH automation bật ([RainDefrostOwner] giữ).
     */
    fun decide(rainSpeed: Int, defrostOn: Boolean, ownedByAuto: Boolean): RainDefrostAction = when {
        isRaining(rainSpeed) && !defrostOn -> RainDefrostAction.TurnOn
        !isRaining(rainSpeed) && defrostOn && ownedByAuto -> RainDefrostAction.TurnOff
        else -> RainDefrostAction.Leave
    }
}

/**
 * Ký ức của automation mưa→sấy giữa các nhịp poll — hai bit, cả hai đều **RAM** (không lưu bền).
 *
 * ## Vì sao KHÔNG lưu bền
 * Spec §Quyết định thiết kế: mưa qua đêm là ca hiếm, và *"nổ máy lại thì automation quên mình từng bật sấy"* là
 * hướng sai **an toàn** — nó chỉ làm automation không tắt hộ một lần, chứ không bao giờ làm nó tắt sấy mà người
 * lái đang cần. Ngược lại, lưu bền một cờ *"sấy này của tôi"* qua một lần nổ máy khác là mời automation tắt một
 * cái sấy mà người lái vừa tự bật sáng nay.
 *
 * @property owned automation đã bật sấy và người lái CHƯA can thiệp ⇒ được phép tắt lại khi hết mưa.
 * @property suppressed người lái đã tự tắt sấy **giữa cơn mưa này** ⇒ nhả quyền tới khi trời khô (spec R1.5).
 */
data class RainDefrostState(
    val owned: Boolean = false,
    val suppressed: Boolean = false,
)

/** Kết quả một nhịp: làm gì với xe, và ký ức mới. */
data class RainDefrostStep(
    val action: RainDefrostAction,
    val state: RainDefrostState,
)

/**
 * ═══ CHỦ QUYỀN NÚT SẤY QUA THỜI GIAN — nơi R1.5 thành thật ═══════════════════════════════════════════════════
 *
 * Thuần (không đồng hồ, không I/O) ⇒ bơm chuỗi nhịp giả mà kiểm. `RainDefrostApplier` (`:app`) giữ đúng **một**
 * [RainDefrostState] trong RAM và gọi [step] mỗi nhịp; mọi luật ở đây.
 *
 * ## Ba việc nó làm mà [RainDefrostPolicy.decide] một mình không làm được
 *  1. **Thấy người lái tắt sấy** — `owned && !defrostOn` giữa cơn mưa là chuyện chỉ nhận ra được khi so với nhịp
 *     trước. Nhận ra ⇒ nhả quyền + [RainDefrostState.suppressed].
 *  2. **Không giành nút** — đang `suppressed` thì im tới hết cơn mưa, dù luật một-nhịp nói TurnOn.
 *  3. **Đặt lại khi trời khô** — hết mưa là hết cơn; lần mưa **sau** automation lại được bật (spec R1.5 nguyên
 *     văn: *"không bật lại tới lần mưa sau"*).
 */
object RainDefrostOwner {

    /**
     * Một nhịp poll.
     *
     * ⚠ Chỗ gọi **chỉ** gọi hàm này khi lần đọc mưa đã qua [RainDefrostPolicy.plausible]. Đọc lỗi ⇒ giữ nguyên
     * cả hành động lẫn ký ức (`Leave` + `state` cũ) — xem [stepUnknown].
     *
     * @param state ký ức của nhịp TRƯỚC.
     * @param rainSpeed lần đọc đã lọc.
     * @param defrostOn sấy TRƯỚC đang bật, đọc **từ xe**.
     */
    fun step(state: RainDefrostState, rainSpeed: Int, defrostOn: Boolean): RainDefrostStep {
        if (!RainDefrostPolicy.isRaining(rainSpeed)) {
            // Hết mưa = hết cơn: quyết định (tắt nếu là sấy của mình) rồi xoá sạch ký ức để lần mưa sau được bật.
            val action = RainDefrostPolicy.decide(rainSpeed, defrostOn, state.owned)
            return RainDefrostStep(action, RainDefrostState())
        }

        // Đang mưa. Người lái vừa tắt cái sấy của automation ⇒ nhả quyền, im tới hết cơn mưa.
        if (state.owned && !defrostOn) {
            return RainDefrostStep(RainDefrostAction.Leave, RainDefrostState(owned = false, suppressed = true))
        }

        // Đã nhả quyền trong cơn mưa này ⇒ không giành nút, kể cả khi luật một-nhịp nói TurnOn.
        if (state.suppressed) return RainDefrostStep(RainDefrostAction.Leave, state)

        val action = RainDefrostPolicy.decide(rainSpeed, defrostOn, state.owned)
        // Chỉ nhận chủ quyền ở đúng nhịp mình RA LỆNH bật. Sấy đang bật mà không phải mình bật (người lái bật
        // trước khi automation kịp thấy mưa) thì `owned` giữ nguyên `false` ⇒ hết mưa sẽ KHÔNG tắt hộ (R1.5).
        val owned = if (action == RainDefrostAction.TurnOn) true else state.owned
        return RainDefrostStep(action, state.copy(owned = owned))
    }

    /**
     * Nhịp mà **không đọc được** mưa (HAL trả `null`/sentinel — xem [RainDefrostPolicy.plausible]).
     *
     * Giữ nguyên tất cả: không ghi xe, không đổi ký ức. Có hàm riêng thay vì để chỗ gọi tự `return` cho nhánh
     * này, vì *"không biết"* là một trạng thái **thật** của đường HAL trên xe (không phải ca lý thuyết), và để nó
     * chưa từng đi qua một bài canh nào là cách nó âm thầm thành *"coi như trời khô"* — tức tắt sấy đang cần.
     */
    fun stepUnknown(state: RainDefrostState): RainDefrostStep =
        RainDefrostStep(RainDefrostAction.Leave, state)

    /**
     * Ký ức đúng khi lệnh bật **không tới được xe** — trả về ký ức của nhịp TRƯỚC.
     *
     * ## Vì sao cần: một lần ghi hỏng từng làm automation bỏ cả cơn mưa, im lặng
     * [step] nhận chủ quyền ở đúng nhịp nó **ra lệnh** bật, chứ không phải nhịp lệnh ấy **thành công** — nó thuần,
     * nó không biết xe có nhận hay không. Nếu chỗ gọi cứ nhận `owned = true` trong khi xe từ chối lệnh, thì nhịp
     * sau đọc về `sấy = tắt` **cùng với** `owned = true`, tức đúng dấu hiệu *"người lái vừa tự tắt"* ⇒
     * [RainDefrostState.suppressed] ⇒ automation im tới hết cơn mưa. Một lần ghi hỏng (rc ≠ 0 trong một giây xe
     * đang bận) biến thành mất cả cơn mưa, và người lái không thấy vì sao.
     *
     * Nhả chủ quyền thì nhịp sau chỉ đơn giản **thử lại** — không có nguy cơ giành nút với người lái, vì đường
     * nhận ra *"người lái tự tắt"* đòi `owned = true`, mà `owned` chỉ bật lên sau một lượt ghi ĐÃ thành công.
     *
     * ⚠ Chỉ gọi cho nhánh [RainDefrostAction.TurnOn] ghi hỏng. Với [RainDefrostAction.TurnOff] ghi hỏng thì giữ
     * nguyên `step.state` là đúng: ký ức ở đó đã được xoá sạch vì **hết mưa** (hết cơn ⇒ hết chủ quyền), không
     * phải vì lệnh thành công; hoàn nguyên nó sẽ giữ lại một chủ quyền của cơn mưa đã qua.
     */
    fun unclaim(before: RainDefrostState, step: RainDefrostStep): RainDefrostState =
        if (step.action == RainDefrostAction.TurnOn) before else step.state
}
