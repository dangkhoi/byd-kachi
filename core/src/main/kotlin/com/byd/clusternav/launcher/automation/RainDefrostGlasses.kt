package com.byd.clusternav.launcher.automation

/**
 * Hai cái sấy kính mà automation mưa→sấy điều khiển — **mỗi cái một làn** (kachi-automation V8).
 *
 * Gương chiếu hậu KHÔNG phải một kính riêng: [ĐO featmap] không có lệnh sấy gương riêng, `getDefrostRearConfig()`
 * = 2 khớp `DEFROST_REAR_MIRROR_HEATING_HAS_FUNC` ⇒ [SUY] gương đi chung mạch sấy sau. Thêm `MIRROR` ở đây là dựng
 * một làn ghi vào hư không.
 *
 * @property label tên kính trong dòng log (`#seq TRƯỚC …` / `#seq SAU …`).
 */
enum class RainGlass(val label: String) { FRONT("TRƯỚC"), REAR("SAU") }

/** Ba khoá prefs của tính năng, đúng thứ tự tên — kiểu có tên thay vì `Triple` để không ai ghi lộn cột. */
data class RainDefrostKeys(val enabled: Boolean, val front: Boolean, val rear: Boolean)

/**
 * Lựa chọn HIỆU LỰC của người dùng — **chỗ DUY NHẤT** dịch 3 khoá cũ ⇄ 2 ô (spec V8 · D2).
 *
 * ## Vì sao giữ 3 khoá cũ mà không mở khoá mới
 * Bản V7 có công tắc chính `rain_defrost_enabled` (mặc định `false`) khoá hai ô con `rain_defrost_front`/`_rear`
 * (mặc định `true`). V8 bỏ công tắc chính khỏi giao diện, nhưng **không** bỏ khoá: hiệu lực = `enabled && con`
 * ([fromKeys]) ⇒ không cần bước di trú, và hạ cấp về V7 vẫn đọc đúng nghĩa (`enabled` = có ít nhất một kính).
 *
 * ## Vì sao đổi một ô phải ghi CẢ BA khoá
 * Prefs cài mới là `(false, true, true)`. Nếu tích ô "sau" chỉ bằng cách bật `enabled`, thì `front = true` cũ
 * **sống lại** ⇒ Kachi sấy cả kính người dùng không chọn. [toKeys] ghi `enabled = any` cùng hai con đúng như
 * đang thấy trên màn, nên không còn trạng thái ẩn nào.
 */
data class RainDefrostChoice(val front: Boolean, val rear: Boolean) {

    /** Có ít nhất một kính được chọn — `false` ⇒ tính năng tắt (không đọc cảm biến, không ghi nút nào). */
    val any: Boolean get() = front || rear

    fun has(glass: RainGlass): Boolean = when (glass) {
        RainGlass.FRONT -> front
        RainGlass.REAR -> rear
    }

    /** Đổi đúng một ô, ô kia giữ nguyên — đó là toàn bộ nghĩa của *"độc lập"*. */
    fun with(glass: RainGlass, on: Boolean): RainDefrostChoice = when (glass) {
        RainGlass.FRONT -> copy(front = on)
        RainGlass.REAR -> copy(rear = on)
    }

    /** Các kính đã chọn, theo thứ tự [RainGlass] — thứ tự chỉ để log đọc ổn định, KHÔNG còn là mỏ neo. */
    val glasses: List<RainGlass> get() = RainGlass.entries.filter(::has)

    /** Ba khoá ghi xuống trong MỘT lượt `edit()` — xem KDoc lớp. */
    fun toKeys(): RainDefrostKeys = RainDefrostKeys(enabled = any, front = front, rear = rear)

    companion object {
        val NONE = RainDefrostChoice(front = false, rear = false)

        /** Đọc 3 khoá (mặc định cài mới: `false, true, true` ⇒ [NONE]). */
        fun fromKeys(enabled: Boolean, front: Boolean, rear: Boolean): RainDefrostChoice =
            RainDefrostChoice(front = enabled && front, rear = enabled && rear)
    }
}

/**
 * Một kính ở một nhịp: đọc được gì, ký ức trước nhịp, và quyết định của [RainDefrostOwner.step].
 *
 * @property rainSpeed lần đọc mưa đã lọc ([RainDefrostPolicy.plausible]); `null` = không đọc được ⇒ kính không
 *   được đọc, không được ghi.
 * @property read kính đang bật? đọc TỪ XE; `null` = không đọc (mưa không rõ) hoặc đọc lỗi.
 */
data class RainGlassPlan(
    val glass: RainGlass,
    val rainSpeed: Int?,
    val read: Boolean?,
    val before: RainDefrostState,
    val step: RainDefrostStep,
) {
    val action: RainDefrostAction get() = step.action
}

/**
 * Kết quả một làn sau khi đã ghi.
 *
 * @property writeOk `null` = không ghi (Leave, hoặc lượt ghi bị bỏ vì người dùng vừa đổi lựa chọn giữa nhịp).
 * @property committed `false` = ký ức của nhịp này bị BỎ vì [RainGlassMemory.forget] đã chạy giữa nhịp.
 */
data class RainGlassOutcome(
    val plan: RainGlassPlan,
    val writeOk: Boolean?,
    val after: RainDefrostState,
    val committed: Boolean,
)

/**
 * Cổng I/O của một nhịp — `:app` cài bằng HAL + prefs, test cài bằng xe giả.
 *
 * ⚠ Hợp đồng: [readRain] · [readGlass] · [writeGlass] · [log] **không được ném**. Đọc lỗi ⇒ `null`, ghi lỗi ⇒
 * `false` (bản `:app` bọc `runCatching` từng lời gọi HAL). Một làn ném thì làn kia không chạy — đúng thứ ràng buộc
 * V8 sinh ra để gỡ. [choice] là ngoại lệ có chủ ý: nó chạy TRƯỚC mọi làn, nên nếu nó ném (đọc prefs lỗi) thì cả
 * nhịp bỏ ở chỗ gọi (`RainDefrostApplier.tick` bọc cả nhịp) — ký ức giữ nguyên. Nuốt lỗi thành `NONE` ở đây thì
 * ngược lại: nhịp sẽ [RainGlassMemory.forget] mọi kính vì một lần đọc hỏng.
 */
interface RainGlassIo {
    fun choice(): RainDefrostChoice
    fun readRain(): Int?
    fun readGlass(glass: RainGlass): Boolean?
    fun writeGlass(glass: RainGlass, on: Boolean): Boolean
    fun log(line: String)
}

/** Ảnh chụp ký ức + thế hệ của từng kính ở đầu nhịp — xem [RainGlassMemory]. */
data class RainGlassSnapshot(
    val states: Map<RainGlass, RainDefrostState>,
    val epochs: Map<RainGlass, Int>,
) {
    fun stateOf(glass: RainGlass): RainDefrostState = states[glass] ?: RainDefrostState()
    fun epochOf(glass: RainGlass): Int = epochs[glass] ?: 0
}

/**
 * Ký ức chủ quyền của TỪNG kính + số thế hệ của từng kính (spec V8 · D4). RAM, không lưu bền — lý do ở KDoc
 * [RainDefrostState].
 *
 * ## Vì sao có thế hệ mà không `synchronized` cả nhịp
 * Nhịp chạy trên luồng nền và gọi HAL (có thể chậm); [forget] đến từ luồng vẽ (người dùng bỏ/tích một ô). Giữ
 * khoá qua lượt HAL = cú chạm Cài đặt đứng chờ HAL. Thay vào đó nhịp chụp [snapshot] (ký ức + thế hệ), làm I/O
 * **không giữ khoá**, rồi [commit] chỉ ghi khi thế hệ của kính đó chưa đổi ⇒ [forget] giữa nhịp luôn THẮNG lượt
 * commit của nhịp đang chạy dở. Cùng khuôn token thế hệ của `AutomationService`.
 */
class RainGlassMemory {
    private val lock = Any()
    private val states = HashMap<RainGlass, RainDefrostState>()
    private val epochs = HashMap<RainGlass, Int>()

    fun snapshot(): RainGlassSnapshot = synchronized(lock) {
        RainGlassSnapshot(states.toMap(), RainGlass.entries.associateWith { epochs[it] ?: 0 })
    }

    /** Thế hệ của [glass] vẫn là [epochSeen]? — gác NGAY TRƯỚC lượt ghi xe (tầng thi hành, CLAUDE.md §5). */
    fun isCurrent(glass: RainGlass, epochSeen: Int): Boolean = synchronized(lock) {
        (epochs[glass] ?: 0) == epochSeen
    }

    /** Ghi ký ức sau nhịp. `false` = đã bị [forget] giữa nhịp ⇒ bỏ, ký ức sạch của lượt quên được giữ. */
    fun commit(glass: RainGlass, epochSeen: Int, state: RainDefrostState): Boolean = synchronized(lock) {
        if ((epochs[glass] ?: 0) != epochSeen) return@synchronized false
        if (state == RainDefrostState()) states.remove(glass) else states[glass] = state
        true
    }

    /** Quên ký ức của MỘT kính (người dùng đổi ô đó, hoặc kính không còn được chọn) — kính kia không bị chạm. */
    fun forget(glass: RainGlass) {
        synchronized(lock) {
            states.remove(glass)
            epochs[glass] = (epochs[glass] ?: 0) + 1
        }
    }

    /** Quên cả hai — động cơ tắt hẳn. */
    fun forgetAll() = RainGlass.entries.forEach(::forget)
}

/**
 * ═══ kachi-automation V8 · MƯA → SẤY: HAI KÍNH ĐỘC LẬP ══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` §V8. Owner 2026-09-30: *"Cần tách auto này độc lập, không constrain
 * nhau"*. V7 có một **mỏ neo** (`pick.first()`): chỉ đọc kính đầu, một ký ức, quyết định của kính đầu áp cho mọi
 * kính đã chọn, và chỉ rc của kính đầu được kiểm. Hệ quả [ĐO nguồn + ĐO log 22/09]: tích cả hai mà kính trước đang
 * bật thì kính sau bị BYD tự tắt (~14′) **không bao giờ** được bật lại; hết mưa thì tắt luôn kính sau người lái tự
 * bật; kính sau ghi hỏng không ai biết.
 *
 * ## Một nhịp ([tick])
 *  1. Chụp ký ức → đọc lựa chọn (thứ tự này có chủ ý: lựa chọn đổi sau ảnh chụp thì [RainGlassMemory.forget] đã
 *     tăng thế hệ ⇒ commit của nhịp này bị bỏ; ký ức không bao giờ được ghi từ một lựa chọn cũ).
 *  2. Kính KHÔNG chọn ⇒ quên ký ức — kể cả khi lựa chọn đổi KHÔNG qua cầu (hạ/nâng cấp, sửa tay tệp prefs; `prefs_set`
 *     của cầu kiểm thử thì từ chối ba khoá này — `TestBridgePrefsSet` nhánh `else`).
 *  3. Không chọn gì ⇒ dừng: không đọc cảm biến, không ghi, không log (tính năng tắt là trạng thái thường).
 *  4. Đọc mưa **một lần** (đầu vào chung — đọc hai lần là gấp đôi lượt HAL không thêm thông tin). Không đọc được
 *     ⇒ mọi kính giữ nguyên, **không đọc kính nào**.
 *  5. Mỗi kính đã chọn, độc lập: đọc chính nó → [RainDefrostOwner.step] với ký ức của chính nó → ghi chính nó →
 *     [settle] theo rc của chính nó → commit → một dòng log.
 *
 * Luật V3 (*"đang mưa mà sấy tắt thì luôn bật lại"*) và R1.5 (*"chỉ tắt cái do Kachi bật"*) KHÔNG viết lại ở đây:
 * mỗi làn gọi đúng [RainDefrostOwner.step] — một luật, hai lần áp.
 */
object RainDefrostGlasses {

    /** Một kính: `rainSpeed`/`read` `null` ⇒ [RainDefrostOwner.stepUnknown] (giữ nguyên); còn lại ⇒ [RainDefrostOwner.step]. */
    fun plan(glass: RainGlass, before: RainDefrostState, rainSpeed: Int?, read: Boolean?): RainGlassPlan {
        // Lọc lại dù chỗ gọi đã lọc: `plausible` idempotent, và sentinel lọt tới `step` là bật sấy giữa trời nắng.
        val speed = RainDefrostPolicy.plausible(rainSpeed)
        val step = if (speed == null || read == null) {
            RainDefrostOwner.stepUnknown(before)
        } else {
            RainDefrostOwner.step(before, speed, read)
        }
        return RainGlassPlan(glass, speed, read, before, step)
    }

    /**
     * Ký ức của kính sau khi biết rc của **chính kính đó**.
     *
     * `TurnOn` mà lệnh không tới xe (`writeOk != true`) ⇒ [RainDefrostOwner.unclaim] (V8 · D5: `owned = false`,
     * kể cả khi trước đó `true`). Mọi nhánh khác ⇒ ký ức của `step` (TurnOff hỏng vẫn xoá sạch: hết mưa là hết cơn).
     */
    fun settle(plan: RainGlassPlan, writeOk: Boolean?): RainDefrostState =
        if (plan.action == RainDefrostAction.TurnOn && writeOk != true) {
            RainDefrostOwner.unclaim(plan.before, plan.step)
        } else {
            plan.step.state
        }

    /**
     * MỘT nhịp — xem KDoc đối tượng. Thuần trừ [io] ⇒ test bơm xe giả chạy thật cả đường đọc/quyết/ghi/ký ức.
     *
     * @param seq số thứ tự nhịp, có mặt trong MỌI dòng log (D7 — khoá `LogLineThrottle` luôn khác nhau).
     * @return một kết quả cho mỗi kính đã chọn; rỗng khi không chọn gì.
     */
    fun tick(seq: Long, memory: RainGlassMemory, io: RainGlassIo): List<RainGlassOutcome> {
        val snap = memory.snapshot()
        val choice = io.choice()
        RainGlass.entries.filterNot(choice::has).forEach(memory::forget)
        if (!choice.any) return emptyList()
        val raw = io.readRain()
        val speed = RainDefrostPolicy.plausible(raw)
        io.log(headLine(seq, raw, speed, choice))
        return choice.glasses.map { lane(seq, it, snap, speed, memory, io) }
    }

    /** Một làn — không đọc, không ghi, không nhìn ký ức của kính nào khác ngoài [glass]. */
    private fun lane(
        seq: Long,
        glass: RainGlass,
        snap: RainGlassSnapshot,
        speed: Int?,
        memory: RainGlassMemory,
        io: RainGlassIo,
    ): RainGlassOutcome {
        val epoch = snap.epochOf(glass)
        val read = if (speed == null) null else io.readGlass(glass)
        val plan = plan(glass, snap.stateOf(glass), speed, read)
        val on = when (plan.action) {
            RainDefrostAction.TurnOn -> true
            RainDefrostAction.TurnOff -> false
            RainDefrostAction.Leave -> null
        }
        // Gác ở tầng THI HÀNH: người dùng vừa bỏ/tích ô này giữa nhịp ⇒ không phát lệnh HAL theo lựa chọn cũ.
        val writeOk = if (on != null && memory.isCurrent(glass, epoch)) io.writeGlass(glass, on) else null
        val after = settle(plan, writeOk)
        val outcome = RainGlassOutcome(plan, writeOk, after, memory.commit(glass, epoch, after))
        io.log(logLine(seq, outcome))
        return outcome
    }

    /** Dòng đầu nhịp: `#41 mưa raw=3 → 3 (mưa) · chọn trước=có sau=có`. */
    fun headLine(seq: Long, raw: Int?, speed: Int?, choice: RainDefrostChoice): String {
        val rain = when {
            speed == null -> "không đọc được ⇒ giữ nguyên mọi kính"
            RainDefrostPolicy.isRaining(speed) -> "$speed (mưa)"
            else -> "$speed (khô)"
        }
        return "#$seq mưa raw=$raw → $rain · chọn ${choiceText(choice)}"
    }

    /** Dòng một kính: `#41 SAU đọc=tắt owned=true ⇒ TurnOn · ghi=ok ⇒ owned=true`. */
    fun logLine(seq: Long, outcome: RainGlassOutcome): String {
        val p = outcome.plan
        val read = when {
            p.rainSpeed == null -> "—"
            p.read == null -> "lỗi"
            p.read -> "bật"
            else -> "tắt"
        }
        val write = when (outcome.writeOk) {
            null -> "—"
            true -> "ok"
            false -> "hỏng"
        }
        val stale = if (outcome.committed) "" else " · bỏ ký ức nhịp này (vừa đổi lựa chọn)"
        return "#$seq ${p.glass.label} đọc=$read owned=${p.before.owned} ⇒ ${actionName(p.action)} · " +
            "ghi=$write ⇒ owned=${outcome.after.owned}$stale"
    }

    /** Dòng đổi lựa chọn: `đổi lựa chọn #c3: trước=có sau=không ⇒ ghi enabled=true front=true rear=false · quên SAU · nhịp mưa ≤60 s`. */
    fun choiceLine(n: Long, after: RainDefrostChoice, glass: RainGlass): String {
        val k = after.toKeys()
        return "đổi lựa chọn #c$n: ${choiceText(after)} ⇒ ghi enabled=${k.enabled} front=${k.front} " +
            "rear=${k.rear} · quên ${glass.label} · nhịp mưa ≤60 s"
    }

    private fun choiceText(c: RainDefrostChoice): String =
        "trước=${if (c.front) "có" else "không"} sau=${if (c.rear) "có" else "không"}"

    private fun actionName(a: RainDefrostAction): String = when (a) {
        RainDefrostAction.TurnOn -> "TurnOn"
        RainDefrostAction.TurnOff -> "TurnOff"
        RainDefrostAction.Leave -> "Leave"
    }
}
