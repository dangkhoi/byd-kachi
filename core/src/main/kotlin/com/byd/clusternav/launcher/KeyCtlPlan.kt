package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceIntent

/**
 * ═══ FIX286 · R-KC — một lần bấm (đã gộp) ⇒ **MỘT ý định nút** cho đường thi hành của giọng nói/ô ═══════════════
 *
 * Phím KHÔNG có đường ghi riêng (CLAUDE.md §4.1 DRY · spec R-KC KC2): kết quả ở đây là một [VoiceIntent.Control] —
 * đúng thứ mà câu nói *"tăng gió"* / *"mở cốp"* sinh ra — và tầng `:app` giao nó cho `VoiceControlDispatch.run`, nơi
 * đã có MỌI cổng: cốp chỉ MỞ khi đứng yên ([CtlSafetyPolicy.REQUIRES_STATIONARY], đọc tốc độ TƯƠI) · nấc đáy gió là
 * AUTO ([ClimateAuto]) · *"xe này không có"* ([CarControlPort.wiredOnThisCar]/[CarControlPort.partAbsentOnThisCar])
 * · đọc lại xác nhận ([CarControlPort.readState]) · ghi qua [actByKind] → `WakeOnWriteControl` → `HalBindingTable.write`
 * (cổng có-mặt, lượt nhả, nhật ký `ctl-writes.log`). Không có cổng xác nhận giọng nói ở đây: gán phím là thao tác
 * chủ ý trong Cài đặt, ngang một cú chạm ô — ô cũng không hỏi.
 *
 * ## Đảo / Kế tiếp — nguồn trạng thái (2.87 · R-FL2, spec `kachi-287-look-and-keys.html` §4.3)
 * Giải ra hành động CỤ THỂ ngay tại đây — Đảo ⇒ Bật/Tắt · Mở/Đóng, Kế tiếp ⇒ đặt mức n — nên [VoiceIntent.Control]
 * giao xuống y hệt câu nói *"đóng cốp"* / *"bật lọc bụi"*, và MỌI cổng của đường thi hành (cốp chỉ MỞ khi đứng yên…)
 * áp lên hành động ĐÃ GIẢI. Thứ tự hỏi trạng thái:
 *  1. nút có [ControlDef.readKey] và đọc ra số hợp lệ ⇒ **xe** ([Basis.CAR], y như 2.86);
 *  2. còn lại ⇒ **lệnh cuối Kachi đã gửi** ([ControlLastSent], [Basis.MEMORY]) — cùng bảng với ô trên màn.
 * Ghi vào bảng KHÔNG ở đây: nó ở tầng thi hành, và CHỈ khi lệnh ghi thành công (`VoiceControlDispatch.finish(ok)`) —
 * cổng tốc độ chặn MỞ cốp thì bảng giữ nguyên "đóng". Giới hạn của bảng (đổi bằng chìa/công tắc cửa, tiến trình `:wake`)
 * ở KDoc [ControlLastSent].
 *
 * Thuần (`:core`) ⇒ bài kiểm bảng chạy off-car (`KeyCtlPlanTest`).
 */
object KeyCtlPlan {

    /** Trạng thái mà Đảo / Kế tiếp đã dựa vào — vào nhật ký `KeyCtl` để soát trên xe (§11), không đổi hành vi. */
    enum class Basis { DIRECT, CAR, MEMORY }

    sealed interface Outcome {
        /** Giao [intent] cho đường thi hành của nút. [basis] = nguồn trạng thái của Đảo / Kế tiếp ([Basis.DIRECT] cho việc khác). */
        data class Run(val intent: VoiceIntent.Control, val basis: Basis = Basis.DIRECT) : Outcome

        /**
         * Kế tiếp trên nút < 2 lựa chọn — không có "kế tiếp" nào để chọn. Mã đích đã qua [KeyCtlTargets.decode] KHÔNG BAO
         * GIỜ ra ca này ([KeyCtlTargets.actionsFor] không sinh NEXT cho nút ấy); chỉ để hàm thuần không chia cho 0.
         */
        data object Invalid : Outcome
    }

    /**
     * @param count số lần bấm đã gộp (≥ 1) — STEP: số nấc; SELECT Kế tiếp: số bước vòng; hành động khác bỏ qua.
     * @param memory bảng lệnh cuối của tiến trình — mặc định [ControlLastSent.shared] (bài kiểm tiêm bảng riêng).
     * @param readState đọc trạng thái THẬT của nút (`CarControlPort.readState` — 0/1 cho TOGGLE, % cho kính, chỉ số mức
     *   cho SELECT). Chỉ được gọi cho Đảo / Kế tiếp của nút CÓ readKey — mọi ca khác KHÔNG tốn một lượt đọc HAL nào.
     */
    fun of(
        def: ControlDef,
        t: KeyCtlTarget,
        count: Int = 1,
        memory: ControlLastSent = ControlLastSent.shared,
        readState: () -> Int?,
    ): Outcome {
        val n = count.coerceAtLeast(1)
        fun run(value: Int? = null, relative: Int = 0, basis: Basis = Basis.DIRECT) =
            Outcome.Run(VoiceIntent.Control(def.id, value, relative), basis)
        // Xe trước (nếu có đường đọc VÀ số đọc hợp lệ), không thì lệnh cuối — luật ở KDoc lớp.
        fun current(valid: (Int) -> Boolean): Pair<Int, Basis> =
            (if (def.readKey.isNotBlank()) readState()?.takeIf(valid) else null)?.let { it to Basis.CAR }
                ?: (memory.index(def.id) to Basis.MEMORY)
        return when (t.action) {
            KeyCtlAction.ON, KeyCtlAction.OPEN, KeyCtlAction.PRESS -> run(value = 1)
            KeyCtlAction.OFF, KeyCtlAction.CLOSE -> run(value = 0)
            KeyCtlAction.SET -> run(value = t.level)
            KeyCtlAction.UP -> run(relative = n)
            KeyCtlAction.DOWN -> run(relative = -n)
            // `> 0` = đang bật/mở — CÙNG phép mà `VoiceReadback.act` dùng (kính đọc %, TOGGLE đọc 0/1, rèm mức 2 = Nửa).
            KeyCtlAction.FLIP -> current { true }.let { (cur, b) -> run(value = if (cur > 0) 0 else 1, basis = b) }
            KeyCtlAction.NEXT -> {
                val size = def.args.size
                if (size < 2) return Outcome.Invalid
                // Mức đọc ngoài thang = không phải số hợp lệ ⇒ lùi về lệnh cuối (2.86: từ chối, không bắn).
                val (cur, b) = current { it in 0 until size }
                run(value = (cur.coerceIn(0, size - 1) + n) % size, basis = b)
            }
        }
    }

    /** Mã đích hỏng, hoặc nút/hành động không còn trên bản Kachi đang chạy (registry đổi) — gán lại. */
    fun invalidReply(spec: String, lang: Lang = Strings.current): String = Strings.fIn(
        lang,
        "Phím gán nút xe không còn hợp lệ ({0}) — gán lại ở Cài đặt › Nút vật lý",
        "This key binding is no longer valid ({0}) — rebind it in Settings › Physical buttons",
        spec,
    )
}

/**
 * ═══ FIX286 · R-KC · KC4 — CHỐNG DỒN cho phím gán nút xe (núm vặn bắn sự kiện liên tục) ═════════════════════════
 *
 * Owner muốn núm vặn âm lượng *"vặn gió"*. Một núm có thể bắn hàng chục sự kiện mỗi giây; mỗi sự kiện mà thành một
 * lượt ghi HAL (≈ 23 ms + đọc lại, [ĐO xe 09-16]) thì xe nhận hàng trăm lệnh cho một cú vặn. Luật (theo TỪNG nút, nên
 * hai phím CW/CCW gán `fan +1`/`fan −1` gộp chung một tổng có dấu):
 *  1. **Cạnh đầu**: lần bấm đầu sau [windowMs] im lặng ⇒ bắn NGAY (phím phản hồi tức thì như một cú chạm).
 *  2. **Trong cửa sổ**: STEP cộng dồn nấc có dấu · SELECT Kế tiếp cộng số bước · đặt-thẳng (Bật/Tắt/Mở/Đóng/mức) lấy
 *     lần CUỐI · Đảo / Bấm **bỏ** (chống bấm kép — hai lần đảo trong 0,4 s là một cú nảy phím, không phải ý người lái).
 *  3. **Cạnh cuối** ([flush], chỗ gọi hẹn tại [Step.Hold.flushAtMs]): bắn phần đã gộp thành MỘT lệnh, mở cửa sổ mới.
 * ⇒ **mỗi sự kiện là một nấc** (không mất nấc nào), mà số lệnh HAL của một nút ≤ 1 + (thời gian vặn / [windowMs]).
 * Cạnh cuối cho lượt đọc mốc STEP chạy sau lệnh trước ≥ [windowMs] (giả định 300 ms `VoiceReadback.READBACK_SETTLE_MS`)
 * nên nấc sau không cộng vào số cũ — TRỪ nhánh rời-AUTO (2 lệnh, luồng riêng, `VoiceClimateStep`): hụt ≤1 nấc (Pass 5).
 *
 * Không khoá luồng: chỗ gọi (`onKeyEvent` + `Handler` luồng chính) tuần tự; `@Synchronized` chỉ để an toàn nếu sau này
 * có lối gọi thứ hai. Thuần + đồng hồ tiêm ⇒ bốn bài throttle trong `KeyCtlPlanTest` (không có lớp test riêng).
 */
class KeyCtlThrottle(private val windowMs: Long = WINDOW_MS) {

    sealed interface Step {
        /** Bắn ngay [target] với [count] lần (STEP: số nấc, chiều nằm ở [target].action). */
        data class Fire(val target: KeyCtlTarget, val count: Int) : Step

        /** Đã gộp. [schedule] = `true` ⇒ chỗ gọi PHẢI hẹn [flush] tại [flushAtMs] (chỉ lần đầu của mỗi cửa sổ). */
        data class Hold(val flushAtMs: Long, val schedule: Boolean) : Step

        /** Bỏ (chống bấm kép cho Đảo / Bấm). */
        data object Drop : Step
    }

    private class Slot(var lastFireAt: Long) {
        var pending: KeyCtlTarget? = null
        var count = 0          // STEP: tổng có dấu · NEXT: số bước · đặt-thẳng: 1
        var scheduled = false
    }

    private val slots = HashMap<String, Slot>()

    @Synchronized
    fun press(t: KeyCtlTarget, nowMs: Long): Step {
        val s = slots[t.controlId]
        if (s == null || (s.pending == null && nowMs - s.lastFireAt >= windowMs)) {
            slots[t.controlId] = s?.also { it.lastFireAt = nowMs } ?: Slot(nowMs)
            return Step.Fire(t, 1)
        }
        when (t.action) {
            KeyCtlAction.FLIP, KeyCtlAction.PRESS -> return Step.Drop
            KeyCtlAction.UP, KeyCtlAction.DOWN -> {
                val prevStep = s.pending?.action.let { it == KeyCtlAction.UP || it == KeyCtlAction.DOWN }
                s.count = (if (prevStep) s.count else 0) + (if (t.action == KeyCtlAction.UP) 1 else -1)
                s.pending = t
            }
            KeyCtlAction.NEXT -> {
                s.count = (if (s.pending?.action == KeyCtlAction.NEXT) s.count else 0) + 1
                s.pending = t
            }
            else -> { s.pending = t; s.count = 1 }
        }
        val flushAt = s.lastFireAt + windowMs
        val first = !s.scheduled
        s.scheduled = true
        return Step.Hold(flushAt, first)
    }

    /** Cạnh cuối của cửa sổ: phần đã gộp thành MỘT lệnh, hoặc `null` (không có gì / +1 −1 triệt tiêu). */
    @Synchronized
    fun flush(controlId: String, nowMs: Long): Step.Fire? {
        val s = slots[controlId] ?: return null
        val p = s.pending
        s.pending = null
        s.scheduled = false
        p ?: return null
        val fire = when (p.action) {
            KeyCtlAction.UP, KeyCtlAction.DOWN -> when {
                s.count > 0 -> Step.Fire(p.copy(action = KeyCtlAction.UP), s.count)
                s.count < 0 -> Step.Fire(p.copy(action = KeyCtlAction.DOWN), -s.count)
                else -> null
            }
            else -> Step.Fire(p, s.count.coerceAtLeast(1))
        }
        s.count = 0
        if (fire != null) s.lastFireAt = nowMs
        return fire
    }

    companion object {
        /**
         * 400 ms — trên giả định đọc-lại 300 ms (`VoiceReadback.READBACK_SETTLE_MS`, chưa đo trên xe) cộng biên, và dưới
         * ngưỡng ~500 ms người ta thấy là *"máy trễ"*. [CHƯA BIẾT] nhịp sự kiện của núm thật trên xe — chốt bằng
         * nhật ký `onKeyEvent DOWN` (§11) khi anh em gán núm.
         */
        const val WINDOW_MS = 400L
    }
}
