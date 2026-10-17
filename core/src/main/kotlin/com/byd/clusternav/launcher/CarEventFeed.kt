package com.byd.clusternav.launcher

/**
 * ═══ 3.1.6 · HAL-EVENTS — nhu cầu màn → kế hoạch sự kiện → helper; sự kiện → [HalEventCache] → `CarStatusRepository` ════════
 *
 * Thuần `:core` (mọi tác dụng phụ là tham số) ⇒ kiểm off-car bằng đồng hồ/sink giả. Một bản mỗi tiến trình (`AppContainer`).
 *
 * ## Ai cần gì
 *  - **Màn chính** ([screen]): datum đang bày + datum mà các nút đang hiện mượn để đọc. Chỉ khi màn xe SÁNG ([lit]) — màn tắt ⇒
 *    không xin (cùng luật `LampSignalDemand.Watch`). Nguồn helper: [HalSignalDemand.Source.CAR_EVENTS].
 *  - **Canh ghế** ([seatWatch]): datum ghế đang canh + hai id dấu vân tay `0x095` ([HalEventMap.SEAT_SIGNATURE]). Nguồn
 *    [HalSignalDemand.Source.SEAT_WATCH] — chạy cả khi màn chính không bày ghế.
 * Datum không map được ([HalEventMap.bindingOf] `null`) thì không xin — poll như cũ.
 *
 * ## Chế độ (một dòng log mỗi lần ĐỔI — CLAUDE.md §11: bằng chứng buổi xe đọc từ log)
 * `POLL` (chưa nối / helper cũ không chào / đứt dây) ↔ `SỰ KIỆN` (đã `hal.hello`). Mỗi device: một dòng kết quả đăng ký. Mỗi
 * feature-id: một dòng ở sự kiện ĐẦU TIÊN (giá trị kèm theo). Đó là đủ để chuyến sau chứng minh cả bảng.
 *
 * ## Tập xin = HỢP mọi nguồn, NỚI ngay · THU HẸP sau hoãn (3.1.8 · HAL-EVENTS-SUB-NARROW)
 * [ĐO xe 15/10 16:13:54–55] lượt đầu đăng ký đủ 8 device; 0,5 s sau HOME `onStop` (AutoNav mở Google Maps toàn màn display 0) ⇒
 * nhu cầu màn về `null` ⇒ dòng `sub` chỉ còn 4 id ghế ⇒ helper huỷ 7 device. Đúng luật (màn khuất thì không xin), nhưng một lần
 * khuất/dựng lại ngắn (HOME dựng lại sau cài đặt, màn chớp tắt, ảnh state trung gian) là một vòng huỷ-đăng-ký-lại cả bus. Luật:
 *  - tập gửi đi luôn là HỢP màn ∪ canh ghế (một dòng mỗi client — helper THAY tập cũ của client bằng dòng mới, nên mọi nguồn trong
 *    tiến trình PHẢI đi chung một dòng ở đây; `KachiHalEvents.reconcile` hợp các client);
 *  - nhu cầu THÊM id ⇒ gửi NGAY (kể cả lúc đang hoãn thu hẹp — id cũ vẫn giữ trong lúc hoãn);
 *  - nhu cầu BỚT id ⇒ giữ tập cũ ∪ tập mới, hẹn [narrowHoldMs]; hết hạn mà nhu cầu vẫn thấp ⇒ thu về đúng tập mới. Trong hạn nhu cầu
 *    quay lại ⇒ huỷ hẹn, 0 dòng `sub` mới, 0 lượt đăng ký lại;
 *  - không có [schedule] (container test / không hub) ⇒ thu hẹp NGAY như trước;
 *  - mỗi lần dòng `sub` ĐỔI: một dòng log `đăng ký sự kiện: …` kèm số id thêm/bớt và LÝ DO (màn/màn tắt/canh ghế/hết hoãn).
 */
class CarEventFeed(
    private val gateway: HalGateway,
    val cache: HalEventCache,
    private val want: (HalSignalDemand.Source, Boolean) -> Unit,
    private val subscribe: (String) -> Unit,
    private val onDatums: (Set<String>) -> Unit,
    private val log: (String) -> Unit,
    private val count: () -> Unit = { KachiPerf.add(KachiPerf.Counter.HAL_EVENT) },
    /** Hẹn [task] sau `ms` (luồng nền), trả hàm huỷ; `null` = không hẹn được ⇒ thu hẹp ngay. Prod: luồng hub (`HalSignalHub.after`). */
    private val schedule: (ms: Long, task: () -> Unit) -> (() -> Unit)? = { _, _ -> null },
    private val narrowHoldMs: Long = NARROW_HOLD_MS,
    /**
     * 3.1.12 · SEAT-AUTOOFF-ANY-ON — helper vừa chào (`hal.hello`, lần nối đầu HOẶC nối lại sau đứt dây): bộ nhớ sự kiện vừa trắng,
     * sự kiện lúc đứt dây đã mất ⇒ người nghe theo sự kiện (canh ghế) đọc lại sự thật của xe một lượt. Gọi trên luồng đọc socket —
     * bên nhận PHẢI không chặn.
     */
    private val onLinkUp: () -> Unit = {},
) {
    private var screenIds: Set<String> = emptySet()
    private var lit = true
    private var seatIds: Set<String>? = null
    private var lastLine: String? = null
    private var lastScreenWant = false
    private var lastSeatWant = false
    private var mode = "POLL"
    private val bindings = HashMap<String, HalEventMap.Binding?>()

    /** Kế hoạch đang GỬI (có thể rộng hơn nhu cầu lúc đang hoãn thu hẹp). */
    private var applied: List<HalEventMap.Binding> = emptyList()
    private var appliedSig: Set<Int> = emptySet()
    private var cancelNarrow: (() -> Unit)? = null
    private var narrowGen = 0

    /** Màn chính công bố nhu cầu ([demand] `null` = màn đã rời / chưa tính ⇒ không xin gì cho màn). */
    @Synchronized
    fun screen(demand: Set<String>?, controls: Set<String>) {
        val ids = HashSet<String>()
        if (demand != null) {
            ids += demand
            controls.forEach { c -> readPathOf(c)?.id?.let(ids::add) }
        }
        if (ids == screenIds) return
        screenIds = ids
        replan(if (demand == null) "màn rời/chưa công bố" else "nhu cầu màn ${ids.size} datum/nút")
    }

    /** Màn xe sáng/tắt (cùng nhịp hỏi màn của vòng poll — không thêm binder). */
    @Synchronized
    fun lit(on: Boolean) {
        if (on == lit) return
        lit = on
        replan(if (on) "màn xe sáng" else "màn xe tắt")
    }

    /** `SeatAutoOffWatch` bắt đầu canh [datums] (`null` = thôi canh). */
    @Synchronized
    fun seatWatch(datums: Set<String>?) {
        if (datums == seatIds) return
        seatIds = datums
        replan(if (datums == null) "thôi canh ghế" else "canh ghế ${datums.size} datum")
    }

    /** Một dòng `hal.*` của helper (luồng đọc socket). */
    fun onMessage(msg: HalEventWire.Msg) {
        when (msg) {
            is HalEventWire.Msg.Hello -> {
                cache.onHello()
                runCatching { onLinkUp() }
                setMode("SỰ KIỆN", "helper giao thức v${msg.protocol}; datum có sự kiện ⇒ poll lùi về đối soát ${cache.reconcileMs / 1000} s")
                synchronized(this) { lastLine?.let(subscribe) }   // gửi lại cho chắc (client cũng gửi khi nối)
            }
            is HalEventWire.Msg.Reg -> {
                cache.onReg(msg.ok, msg.rejected, msg.device)
                log("đăng ký ${msg.device.substringAfterLast('.')} (type ${msg.deviceType}): ok=${hex(msg.ok)} từ-chối=${hex(msg.rejected)}" +
                    (if (msg.error.isNotEmpty()) " lỗi=${msg.error}" else ""))
            }
            is HalEventWire.Msg.Event -> {
                count()
                val r = cache.onEvent(msg.featureId, msg.v, msg.d)
                if (r.first) {
                    val who = if (r.signal) "dấu vân tay" else r.datums.joinToString(",").ifEmpty { "?" }
                    log("sự kiện ĐẦU ${msg.device.substringAfterLast('.')} 0x${Integer.toHexString(msg.featureId)} v=${msg.v} d=${msg.d} → $who")
                }
                if (r.datums.isNotEmpty()) onDatums(r.datums)
            }
        }
    }

    /** Mất dây / dừng ⇒ mọi lượt đọc về gateway (poll y như trước). */
    fun onLinkDown() {
        cache.linkDown()
        setMode("POLL", "không nối helper sự kiện (helper vắng/cũ/đứt dây) — poll như trước")
    }

    private fun setMode(m: String, why: String) {
        val changed = synchronized(this) { (mode != m).also { mode = m } }
        if (changed) log("chế độ=$m · $why")
    }

    private fun bindingOf(id: String): HalEventMap.Binding? =
        if (bindings.containsKey(id)) bindings[id] else runCatching { HalEventMap.bindingOf(id, gateway) }.getOrNull().also { bindings[id] = it }

    /** Gọi trong khoá. Xem "Tập xin = HỢP mọi nguồn" ở KDoc lớp. */
    private fun replan(reason: String, due: Boolean = false) {
        val screenB = if (lit) screenIds.mapNotNull(::bindingOf) else emptyList()
        val seat = seatIds
        val seatB = seat?.mapNotNull(::bindingOf).orEmpty()
        val sig = if (seat != null) HalEventMap.SEAT_SIGNATURE.mapNotNull { gateway.featureIdByName(it) }.toSet() else emptySet()
        val target = (screenB + seatB).distinctBy { it.datumId }
        val sw = screenB.isNotEmpty()
        val tw = seat != null && (seatB.isNotEmpty() || sig.isNotEmpty())
        val narrows = applied.any { a -> target.none { it.datumId == a.datumId } } || !sig.containsAll(appliedSig)
        // Thứ tự: xin nguồn TRƯỚC rồi mới gửi dòng — client chưa chạy thì dòng được giữ và gửi lúc nối.
        if (sw && !lastScreenWant) want(HalSignalDemand.Source.CAR_EVENTS, true)
        if (tw && !lastSeatWant) want(HalSignalDemand.Source.SEAT_WATCH, true)
        if (sw) lastScreenWant = true
        if (tw) lastSeatWant = true
        if (narrows && !due) {
            val cancel = cancelNarrow ?: run {
                val g = ++narrowGen
                schedule(narrowHoldMs) { narrowDue(g) }
            }
            if (cancel != null) {
                // Hoãn: giữ CŨ ∪ MỚI (id mới gửi ngay, id cũ chưa bỏ); nguồn helper cũng giữ tới lúc thu hẹp thật.
                cancelNarrow = cancel
                send((applied + target).distinctBy { it.datumId }, appliedSig + sig, "$reason · hoãn thu hẹp ${narrowHoldMs / 1000} s")
                return
            }
        }
        cancelNarrow?.invoke()
        cancelNarrow = null
        narrowGen++
        send(target, sig, reason)
        if (!sw && lastScreenWant) want(HalSignalDemand.Source.CAR_EVENTS, false)
        if (!tw && lastSeatWant) want(HalSignalDemand.Source.SEAT_WATCH, false)
        lastScreenWant = sw
        lastSeatWant = tw
    }

    /** Hẹn thu hẹp tới hạn (luồng hẹn) — thế hệ cũ (đã huỷ / đã thu) bỏ qua. */
    @Synchronized
    private fun narrowDue(g: Int) {
        if (g != narrowGen || cancelNarrow == null) return
        cancelNarrow = null
        replan("hết hoãn thu hẹp", due = true)
    }

    /** Đặt kế hoạch [plan] + dấu vân tay [sig] vào bộ nhớ, gửi dòng `sub` nếu ĐỔI (một dòng log kèm [reason]). */
    private fun send(plan: List<HalEventMap.Binding>, sig: Set<Int>, reason: String) {
        val before = idsOf(applied, appliedSig)
        applied = plan
        appliedSig = sig
        cache.setPlan(plan, sig)
        val line = cache.subscriptionLine(HalBindingTable.deviceFqn(HalEventMap.SEAT_SIGNATURE_DEVICE))
        if (line == lastLine) return
        lastLine = line
        val after = idsOf(plan, sig)
        val devices = line.removePrefix("sub").split(';').count { it.isNotBlank() }
        log("đăng ký sự kiện: ${after.size} id / $devices device (+${(after - before).size} −${(before - after).size}) · lý do: $reason")
        subscribe(line)
    }

    private fun idsOf(plan: List<HalEventMap.Binding>, sig: Set<Int>): Set<Int> = plan.flatMap { it.ids }.toSet() + sig

    private fun hex(s: Set<Int>) = s.joinToString(",") { Integer.toHexString(it) }.ifEmpty { "—" }

    companion object {
        /**
         * Hoãn thu hẹp tập xin: 10 s — dài hơn một lần HOME dựng lại [ĐO xe 15/10: khung đầu trễ 2,7–5,8 s, `Davey!`], ngắn so với chi
         * phí giữ (sự kiện của datum màn đã rời đẩy thêm ≤ 10 s qua socket loopback, 0 lượt HAL).
         */
        const val NARROW_HOLD_MS = 10_000L
    }
}
