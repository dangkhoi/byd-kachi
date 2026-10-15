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
 */
class CarEventFeed(
    private val gateway: HalGateway,
    val cache: HalEventCache,
    private val want: (HalSignalDemand.Source, Boolean) -> Unit,
    private val subscribe: (String) -> Unit,
    private val onDatums: (Set<String>) -> Unit,
    private val log: (String) -> Unit,
    private val count: () -> Unit = { KachiPerf.add(KachiPerf.Counter.HAL_EVENT) },
) {
    private var screenIds: Set<String> = emptySet()
    private var lit = true
    private var seatIds: Set<String>? = null
    private var lastLine: String? = null
    private var lastScreenWant = false
    private var lastSeatWant = false
    private var mode = "POLL"
    private val bindings = HashMap<String, HalEventMap.Binding?>()

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
        replan()
    }

    /** Màn xe sáng/tắt (cùng nhịp hỏi màn của vòng poll — không thêm binder). */
    @Synchronized
    fun lit(on: Boolean) {
        if (on == lit) return
        lit = on
        replan()
    }

    /** `SeatAutoOffWatch` bắt đầu canh [datums] (`null` = thôi canh). */
    @Synchronized
    fun seatWatch(datums: Set<String>?) {
        if (datums == seatIds) return
        seatIds = datums
        replan()
    }

    /** Một dòng `hal.*` của helper (luồng đọc socket). */
    fun onMessage(msg: HalEventWire.Msg) {
        when (msg) {
            is HalEventWire.Msg.Hello -> {
                cache.onHello()
                setMode("SỰ KIỆN", "helper giao thức v${msg.protocol}; datum có sự kiện ⇒ poll lùi về đối soát ${cache.reconcileMs / 1000} s")
                synchronized(this) { lastLine?.let(subscribe) }   // gửi lại cho chắc (client cũng gửi khi nối)
            }
            is HalEventWire.Msg.Reg -> {
                cache.onReg(msg.ok, msg.rejected)
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

    /** Gọi trong khoá. */
    private fun replan() {
        val screenB = if (lit) screenIds.mapNotNull(::bindingOf) else emptyList()
        val seat = seatIds
        val seatB = seat?.mapNotNull(::bindingOf).orEmpty()
        val sig = if (seat != null) HalEventMap.SEAT_SIGNATURE.mapNotNull { gateway.featureIdByName(it) }.toSet() else emptySet()
        cache.setPlan((screenB + seatB).distinctBy { it.datumId }, sig)
        val line = cache.subscriptionLine(HalBindingTable.deviceFqn(HalEventMap.SEAT_SIGNATURE_DEVICE))
        val sw = screenB.isNotEmpty()
        val tw = seat != null && (seatB.isNotEmpty() || sig.isNotEmpty())
        // Thứ tự: xin nguồn TRƯỚC rồi mới gửi dòng — client chưa chạy thì dòng được giữ và gửi lúc nối.
        if (sw && !lastScreenWant) want(HalSignalDemand.Source.CAR_EVENTS, true)
        if (tw && !lastSeatWant) want(HalSignalDemand.Source.SEAT_WATCH, true)
        if (line != lastLine) { lastLine = line; subscribe(line) }
        if (!sw && lastScreenWant) want(HalSignalDemand.Source.CAR_EVENTS, false)
        if (!tw && lastSeatWant) want(HalSignalDemand.Source.SEAT_WATCH, false)
        lastScreenWant = sw
        lastSeatWant = tw
    }

    private fun hex(s: Set<Int>) = s.joinToString(",") { Integer.toHexString(it) }.ifEmpty { "—" }
}
