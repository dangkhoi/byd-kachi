package com.byd.clusternav.launcher

/**
 * ═══ 3.1.6 · HAL-EVENTS — bộ nhớ giá trị theo SỰ KIỆN của helper, đứng TRƯỚC gateway (thuần `:core`) ═══════════════════════
 *
 * Cơ chế (doc `hal-events-body-seat-2026-10-15.md` §3): app gửi helper danh sách feature-id đang cần ([subscriptionLine]); helper
 * đăng ký listener chung trên từng device và đẩy `(device, featureId, value)` mỗi lần giá trị đổi; [EventCachedGateway] trả giá trị
 * này thay lượt gọi getter. Poll còn lại ba vai:
 *  1. **Đối soát chậm**: một giá trị chỉ dùng được [reconcileMs] (60 s) kể từ lần làm mới cuối (sự kiện HOẶC lượt đọc thật); quá
 *     hạn ⇒ lượt poll kế đi gateway thật rồi cất lại ([store]).
 *  2. **Datum không đẩy / chưa chứng minh** (F6-TRUST): KHÔNG id nào được dùng thay poll khi DEVICE của nó chưa đẩy ≥ 1 sự kiện (bất
 *     kỳ id) tới helper trong lần nối này (soát Pass 6: bằng chứng bus ≠ bằng chứng uid shell). Device đã chứng minh ⇒
 *     • id đã có sự kiện của CHÍNH nó, hoặc id ở [HalEventMap.PUSH_PROVEN] ⇒ hạn [reconcileMs] (60 s);
 *     • id còn lại của device ấy (3.1.9 HAL-POLL-COVERED) ⇒ hạn [inferredMs] (30 s) — id có thể im lặng trên bus (chưa đổi giá trị
 *       lần nào ⇒ không có sự kiện để chứng minh riêng), nên số cũ bị chặn ở 30 s chứ không 60 s. Trước 3.1.9 id ấy phải chờ sự kiện
 *       của chính nó ⇒ ô điều khiển AC chưa ai chỉnh vẫn bị đọc mỗi 1 s mãi ([ĐO xe 15/10 18:02–18:05] ≈ 250 lượt/phút).
 *     Bằng chứng theo id/device sống theo LẦN NỐI, không theo kế hoạch: [setPlan] thu hẹp rồi nở lại không xoá nó (3.1.9 — [ĐO xe 15/10]
 *     18:01:15 thu hẹp `4 id / 1 device` rồi 18:02:36 nở lại: bản cũ quên sự kiện AC 17:59:46 ⇒ AC quay về poll 1 s).
 *  3. **Helper vắng**: chưa `hal.hello` / đứt dây ([linkDown]) ⇒ [hit] luôn `null` ⇒ hành vi y hệt trước (poll).
 *
 * Luồng: mọi hàm `@Synchronized` (ghi từ luồng đọc socket, đọc từ luồng poll/IO).
 */
class HalEventCache(
    private val clock: () -> Long = System::currentTimeMillis,
    val reconcileMs: Long = RECONCILE_MS,
    /** Hạn của id chỉ được tin nhờ DEVICE đã chứng minh (chưa có sự kiện của chính nó) — vai 2 ở KDoc lớp. */
    val inferredMs: Long = reconcileMs / 2,
) {
    /** khoá đọc → binding (một khoá có thể mang nhiều datum: lấy binding đầu, cùng getter ⇒ cùng id). */
    private var byKey: Map<String, HalEventMap.Binding> = emptyMap()
    private var datumsByFid: Map<Int, Set<String>> = emptyMap()
    private var keyByDatum: Map<String, String> = emptyMap()

    /** Giá trị thô + mốc làm mới theo feature-id. */
    private val values = HashMap<Int, String>()
    private val at = HashMap<Int, Long>()
    private val seen = HashSet<Int>()
    /** Device (FQN) đã có ÍT NHẤT một sự kiện tới tiến trình helper trong lần nối này — bằng chứng native đẩy cho uid shell của device ấy. */
    private val seenDevice = HashSet<String>()
    private val eventAt = HashMap<Int, Long>()
    private val registered = HashSet<Int>()
    /** Tập id đăng ký theo device (`hal.reg` gần nhất) — để [onReg] gỡ id thôi đăng ký. */
    private val regByDevice = HashMap<String, Set<Int>>()
    private var hello = false

    /** Dấu vân tay ngoài datum (vd `0x095…` của ghế): id → mốc thấy gần nhất. */
    private var signals: Set<Int> = emptySet()
    private val signalAt = HashMap<Int, Long>()

    /** Helper hiểu giao thức sự kiện và đang nối. */
    @get:Synchronized val live: Boolean get() = hello

    /** Thay toàn bộ kế hoạch (datum + id dấu vân tay). Giá trị đã có của id còn trong kế hoạch được giữ. */
    @Synchronized
    fun setPlan(bindings: List<HalEventMap.Binding>, signalIds: Set<Int> = emptySet()) {
        byKey = bindings.groupBy { it.key }.mapValues { it.value.first() }
        keyByDatum = bindings.associate { it.datumId to it.key }
        datumsByFid = bindings.flatMap { b -> b.ids.map { it to b.datumId } }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
        signals = signalIds
        val keep = datumsByFid.keys + signalIds
        // 3.1.9 — `seen` KHÔNG thu theo kế hoạch: nó là bằng chứng *"bus đã đẩy id này tới helper trong lần nối này"*, sự thật ấy không
        // mất khi màn tạm rời (thu hẹp) rồi quay lại; chỉ [linkDown] xoá. Giá trị + mốc thì thu (id rời kế hoạch không còn được làm mới).
        values.keys.retainAll(keep); at.keys.retainAll(keep); eventAt.keys.retainAll(keep)
    }

    /** Dòng `sub …` cho helper: `fqn=id,id;…`. Không có gì cần ⇒ `"sub"` (thôi xin hết). */
    @Synchronized
    fun subscriptionLine(signalDevice: String? = null): String {
        val byDev = LinkedHashMap<String, MutableSet<Int>>()
        byKey.values.forEach { b -> byDev.getOrPut(b.device) { sortedSetOf() } += b.ids }
        if (signalDevice != null && signals.isNotEmpty()) byDev.getOrPut(signalDevice) { sortedSetOf() } += signals
        if (byDev.isEmpty()) return "sub"
        return "sub " + byDev.entries.joinToString(";") { (d, ids) -> d + "=" + ids.joinToString(",") }
    }

    /** Giá trị thô thay lượt đọc [key], hoặc `null` ⇒ gọi gateway thật. Xem KDoc lớp (ba vai của poll). */
    @Synchronized
    fun hit(key: String): String? {
        if (!hello) return null
        val b = byKey[key] ?: return null
        val now = clock()
        for (id in b.ids) {
            if (id !in registered) return null
            // Soát Pass 6 [P2]: bằng chứng BUS đẩy (đo ở tiến trình BYD) chưa phải bằng chứng helper (uid shell) NHẬN được ⇒ không id nào
            // được tin khi device ấy chưa đẩy ≥ 1 sự kiện tới helper trong lần nối này; trước đó poll như cũ.
            if (b.device !in seenDevice) return null
            // 3.1.9 HAL-POLL-COVERED — device đã chứng minh: id có bằng chứng riêng (sự kiện của nó / PUSH_PROVEN) hạn 60 s, còn lại 30 s.
            val ttl = if (id in seen || b.trusted) reconcileMs else inferredMs
            val t = at[id] ?: return null
            if (now - t !in 0 until ttl) return null
        }
        return compose(b)
    }

    /** Datum [datumId] đang được phục vụ bằng sự kiện (poll của nó có thể nghỉ tới hạn đối soát). */
    @Synchronized
    fun covers(datumId: String): Boolean = keyByDatum[datumId]?.let { hit(it) != null } ?: false

    /**
     * 3.1.9 — đường sự kiện của [datumId] ĐỨNG ở đâu, KHÔNG xét hạn đối soát (để log *"còn poll gì, vì sao"* không nhảy mỗi phút):
     * [Trust.OFF] helper vắng · [Trust.NONE] không có trong kế hoạch · [Trust.UNPROVEN] đăng ký chưa OK / device chưa đẩy sự kiện nào ·
     * [Trust.PROVEN].
     */
    @Synchronized
    fun trust(datumId: String): Trust {
        if (!hello) return Trust.OFF
        val b = byKey[keyByDatum[datumId] ?: return Trust.NONE] ?: return Trust.NONE
        if (b.ids.any { it !in registered }) return Trust.UNPROVEN
        return if (b.device in seenDevice) Trust.PROVEN else Trust.UNPROVEN
    }

    enum class Trust { OFF, NONE, UNPROVEN, PROVEN }

    /** Một lượt đọc THẬT của [key] (đối soát) — cất lại để các lượt kế dùng, chỉ khi khoá nằm trong kế hoạch và đang nối. */
    @Synchronized
    fun store(key: String, raw: String) {
        if (!hello) return
        val b = byKey[key] ?: return
        val parts = if (b.ids.size == 1) listOf(raw.trim()) else splitArray(raw) ?: return
        if (parts.size < b.ids.size) return
        val now = clock()
        b.ids.forEachIndexed { i, id -> values[id] = parts[i]; at[id] = now }
    }

    /**
     * Sự kiện `(featureId, v, d)` ⇒ mã datum ĐỔI giá trị (rỗng = không thuộc kế hoạch / không đổi). `first` = lần đầu thấy id này
     * trong lần nối (để log bằng chứng đúng một dòng mỗi id).
     */
    @Synchronized
    fun onEvent(fid: Int, v: Int, d: Double): EventResult {
        val now = clock()
        if (fid in signals) {
            val first = signalAt.put(fid, now) == null
            return EventResult(emptySet(), first, signal = true)
        }
        val datums = datumsByFid[fid] ?: return EventResult(emptySet(), false)
        val b = byKey[keyByDatum[datums.first()]] ?: return EventResult(emptySet(), false)
        val raw = when {
            b.valid != null && v !in b.valid -> SENTINEL_INVALID.toString()
            b.kind == HalEventMap.Kind.DOUBLE -> d.toString()
            else -> v.toString()
        }
        val first = seen.add(fid)
        seenDevice += b.device
        val changed = values.put(fid, raw) != raw
        at[fid] = now
        eventAt[fid] = now
        return EventResult(if (changed || first) datums else emptySet(), first)
    }

    data class EventResult(val datums: Set<String>, val first: Boolean, val signal: Boolean = false)

    /** Mốc SỰ KIỆN gần nhất (không tính lượt đối soát) của datum [datumId] và giá trị thô của nó; `null` = chưa có sự kiện. */
    @Synchronized
    fun lastEvent(datumId: String): Pair<Long, String>? {
        val b = byKey[keyByDatum[datumId] ?: return null] ?: return null
        val id = b.ids.singleOrNull() ?: return null
        val t = eventAt[id] ?: return null
        return t to (values[id] ?: return null)
    }

    /** Mốc thấy dấu vân tay [fid] gần nhất trong lần nối này; `null` = chưa thấy. */
    @Synchronized fun signalSeenAt(fid: Int): Long? = signalAt[fid]

    /** Id dấu vân tay đã đăng ký thành công. */
    @Synchronized fun signalRegistered(fid: Int): Boolean = fid in registered

    /** Helper chào (`hal.hello`) — nối được và hiểu giao thức. */
    @Synchronized fun onHello() { hello = true }

    /**
     * Kết quả đăng ký của một device (`hal.reg`): id ok được ghi nhận, id bị từ chối/lỗi bị gỡ.
     *
     * 3.1.8 · HAL-EVENTS-SUB-NARROW — `ok` của helper là TOÀN BỘ tập đang đăng ký của [device] (`KachiHalEvents.publishReg`), không
     * phải phần thêm ⇒ [device] biết thì THAY tập của device ấy (id thôi đăng ký — vd `ok=—` lúc thu hẹp — rời [registered]). Trước
     * đây chỉ cộng/trừ ⇒ id đã huỷ trên helper vẫn được coi là "đăng ký" mãi tới khi đứt dây ([ĐO] xe 15/10 16:13:55: 7 device về
     * `ok=—`; [SUY từ nguồn] app vẫn giữ id của chúng trong [registered]).
     */
    @Synchronized
    fun onReg(ok: Set<Int>, rejected: Set<Int>, device: String? = null) {
        // Soát Pass 8 [P3]: dòng `hal.reg` thiếu `dev` ⇒ `HalEventWire` trả "" — không khoá tập theo "" (mọi device dồn một khoá, dòng sau
        // xoá tập của dòng trước); thiếu device ⇒ cộng/trừ như cũ (fail-open về poll, không gỡ nhầm).
        if (!device.isNullOrEmpty()) {
            regByDevice.put(device, ok)?.let { prev -> registered -= (prev - ok) }
        }
        registered += ok
        registered -= rejected
    }

    /** Đứt dây / dừng ⇒ không còn biết gì: mọi lượt đọc về gateway. */
    @Synchronized
    fun linkDown() {
        hello = false
        registered.clear(); regByDevice.clear(); values.clear(); at.clear(); seen.clear(); seenDevice.clear(); eventAt.clear(); signalAt.clear()
    }

    private fun compose(b: HalEventMap.Binding): String? {
        val parts = b.ids.map { values[it] ?: return null }
        return if (parts.size == 1) parts[0] else parts.joinToString(", ", "[", "]")
    }

    private fun splitArray(raw: String): List<String>? {
        val s = raw.trim()
        if (!s.startsWith("[")) return null
        return s.removePrefix("[").removeSuffix("]").split(',').map { it.trim() }
    }

    companion object {
        /** Hạn dùng một giá trị không có sự kiện mới — sau đó một lượt đọc thật đối soát (owner: "reconcile chậm"). */
        const val RECONCILE_MS = 60_000L

        /** `BYDAutoManager.BYDAUTO_COMMAND_RESULT_INVALID_VALUE` — getter trả mã này khi tự lọc dải ([HalEventMap.Feat.valid]). */
        const val SENTINEL_INVALID = -2147482645
    }
}

/**
 * Dòng giao thức của helper (`hal-helper/KachiHalMain.java`, khối HAL-EVENTS) — THUẦN. Tách trường bằng các phép tìm RỜI như
 * `HalSignalClient.parseLine` (thêm trường không làm client mù).
 */
object HalEventWire {
    const val TOPIC_HELLO = "hal.hello"
    const val TOPIC_REG = "hal.reg"
    const val TOPIC_EV = "hal.ev"

    sealed class Msg {
        data class Hello(val protocol: Int) : Msg()
        data class Reg(val deviceType: Int, val device: String, val ok: Set<Int>, val rejected: Set<Int>, val error: String) : Msg()
        data class Event(val featureId: Int, val device: String, val v: Int, val d: Double) : Msg()
    }

    private fun str(line: String, k: String): String? = Regex("\"$k\"\\s*:\\s*\"([^\"]*)\"").find(line)?.groupValues?.get(1)
    private fun num(line: String, k: String): String? = Regex("\"$k\"\\s*:\\s*(-?[0-9.Ee+-]+)").find(line)?.groupValues?.get(1)

    private fun hexSet(s: String?): Set<Int> =
        s.orEmpty().split(',').mapNotNull { h -> h.trim().takeIf { it.isNotEmpty() }?.let { java.lang.Long.parseLong(it, 16).toInt() } }.toSet()

    /** `null` = không phải dòng HAL-EVENTS (hoặc hỏng) — bên gọi xử lý như cũ. */
    fun parse(line: String?): Msg? {
        val s = line ?: return null
        val topic = str(s, "topic") ?: return null
        return runCatching {
            when (topic) {
                TOPIC_HELLO -> Msg.Hello(num(s, "type")!!.toInt())
                TOPIC_REG -> Msg.Reg(num(s, "type")!!.toInt(), str(s, "dev").orEmpty(), hexSet(str(s, "ok")), hexSet(str(s, "rej")), str(s, "err").orEmpty())
                TOPIC_EV -> Msg.Event(num(s, "type")!!.toInt(), str(s, "dev").orEmpty(), num(s, "v")!!.toInt(), num(s, "d")?.toDoubleOrNull() ?: 0.0)
                else -> null
            }
        }.getOrNull()
    }
}

/**
 * Gateway bọc: lượt đọc getter / feature-id có trong kế hoạch sự kiện ⇒ hỏi [cache] trước; trượt ⇒ gateway thật rồi cất lại (đối
 * soát). Mọi thứ khác (ghi, setting, local, tra tên) đi thẳng [inner]. Đặt ở ĐÂY (tầng gateway) để mọi người đọc — vòng poll, ô
 * điều khiển, giọng nói, canh ghế — cùng hưởng, không thêm một đường đọc nào trong `HalBindingTable`.
 */
class EventCachedGateway(private val inner: HalGateway, private val cache: HalEventCache) : HalGateway by inner {
    override fun getter(deviceFqn: String, method: String, arg: Int?): String? {
        val key = HalEventMap.readKeyNamed(deviceFqn, method, arg)
        cache.hit(key)?.let { KachiPerf.add(KachiPerf.Counter.HAL_EVENT_HIT); return it }
        return inner.getter(deviceFqn, method, arg)?.also { cache.store(key, it) }
    }

    override fun featureGet(deviceFqn: String, id: Int): String? {
        val key = HalEventMap.readKeyFeature(id)
        cache.hit(key)?.let { KachiPerf.add(KachiPerf.Counter.HAL_EVENT_HIT); return it }
        return inner.featureGet(deviceFqn, id)?.also { cache.store(key, it) }
    }
}
