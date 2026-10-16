package com.byd.clusternav.launcher

/**
 * ADAPTER ĐỌC XE (W1b) — nối [CarDataPort] (6 method cũ, GIỮ cho widget hiện có) + [CarStatusReader] (build
 * [CarStatus] đầy đủ theo 2 nhịp poll của [CarStatusRepository]). Mọi giá trị đọc qua [HalBindingTable] theo `id`
 * của [TelemetryRegistry]; off-car → null ⇒ UI "—" (R3/OQ1: KHÔNG demo).
 *
 * Map `id` → field [CarStatus] theo §2 handoff (id ↔ tên field gần khớp). Per-index (kính/lốp/đèn) suy từ hậu tố
 * id trong [HalBindingTable]. KHÔNG gate an toàn.
 *
 * ## H1 (PERF 2026-09-16) — [demand]: đọc thứ ĐANG HIỆN, giữ nguyên phần còn lại
 * [demand] trả tập `id` mà màn hình thật sự bày ra ([CarDataDemand.of]); `null` = *"không tính được ⇒ đọc hết"*
 * (mặc định, và là hành vi y hệt mọi bản trước 1.67). Datum ngoài tập ấy **KHÔNG được đọc** và **GIỮ giá trị cũ**
 * của [CarStatus] — cố ý không đặt `null`, vì `null` ở đây nghĩa là *"không đọc được"* và sẽ biến ô đang hiện
 * thành "—" ngay khi nó rời khỏi tập nhu cầu trong một nhịp giao thời.
 *
 * ⚠ Cổng CHỈ áp cho hai hàm nhịp ([readFast]/[readSlow]). Sáu method [CarDataPort] và mọi đường đọc tường minh
 * (cầu `sweep`/`read`, màn kiểm-tra-từng-nút, câu hỏi bằng giọng) đi thẳng [HalBindingTable] nên không bị lọc —
 * xem khối ⚠ trong KDoc [CarDataDemand].
 */
class CarDataAdapter(
    private val table: HalBindingTable,
    private val demand: () -> Set<String>? = { null },
    private val absent: HalAbsentCache = HalAbsentCache(),
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * Tập mã NÚT đang hiện có đường đọc ([CarDataDemand.controlsOf]) — đọc giá trị THẬT của chúng vào
     * [CarStatus.controls] mỗi nhịp CHẬM. Mặc định rỗng ⇒ mọi test/reader cũ không đổi hành vi.
     */
    private val controlDemand: () -> Set<String> = { emptySet() },
    /**
     * TRIP-TIME-6MIN (owner duyệt 2026-09-27) — làm mượt `trip_hours` (HAL trả bậc 0,1 h = 6 phút, [ĐO xe Seal 11:48]).
     * **MỘT** instance cho một adapter vì nó có trạng thái (mốc + đồng hồ trôi); dùng chung [clock] để test bằng đồng
     * hồ giả. Chỉ nhận giá trị ĐỌC THẬT — xem [CarReadGate.dblVia].
     */
    private val tripTime: TripTimeSmoother = TripTimeSmoother(clock),
    /** 3.1.6 HAL-EVENTS — datum đang được SỰ KIỆN phục vụ (`HalEventCache.covers`) ⇒ nhịp 3 s / 1 s nghỉ. Mặc định: poll như cũ. */
    private val covered: (String) -> Boolean = { false },
    /** 3.1.9 HAL-POLL-COVERED — lượt đọc tốc độ THẬT của nhịp nhanh góp vào mẫu chung (nhịp tim dẫn đường dùng lại). `null` = không góp. */
    private val speedShare: com.byd.clusternav.navigation.SpeedShare? = null,
    /** 3.1.9 — chỗ đường sự kiện của một datum đứng (`HalEventCache.trust`) cho dòng [HalPollReport]; mặc định: không log. */
    private val trust: ((String) -> HalEventCache.Trust)? = null,
    private val log: (String) -> Unit = {},
) : CarDataPort, CarStatusReader {

    // Cổng đọc hai lớp (nhu cầu + cache vắng) — tách ra [CarReadGate] (3.1.6 soát Pass 6: tệp chạm trần 500 dòng).

    private fun gate() = eventGate ?: CarReadGate(table, demand(), absent, clock())

    /** 3.1.6 HAL-EVENTS — cổng + id của lượt [readEvents] đang chạy (mọi lượt đọc đi dưới khoá `publish` của repo ⇒ không chồng). */
    @Volatile private var eventGate: CarReadGate? = null
    @Volatile private var eventIds: Set<String>? = null

    /** 3.1.6 HAL-EVENTS — đọc lại CHỈ [ids] ∩ nhu cầu qua ba thân nhịp (giá trị từ `HalEventCache` ⇒ 0 HAL) + nút mượn chúng. */
    override fun readEvents(prev: CarStatus, ids: Set<String>): CarStatus {
        // Nút cốp mượn `tailgate_status` dù màn không bày datum ấy ⇒ readKey của nút mirror cũng thuộc lượt này (mirrorBody chép vào nút).
        val want = demand()?.let { d -> val k = d + mirrored().map { it.readKey }; ids.filterTo(HashSet()) { it in k } } ?: ids
        eventGate = CarReadGate(table, want, absent, clock(), count = false)
        eventIds = ids
        return try { readBody(readSlow(readFast(prev))) } finally { eventGate = null; eventIds = null }
    }

    /**
     * Nhịp NHANH đáng chạy khi màn bày ít nhất một datum nhanh, HOẶC có ô điều khiển **còn đáng hỏi** (để setpoint
     * đọc realtime — #5 owner 2026-09-21, GIỮ NGUYÊN).
     *
     * ## K1b (2026-09-25) — vì sao không còn là `controlDemand().isNotEmpty()`
     * [ĐO máy ảo clusternav10 · 2.65 · màn chính đứng yên · KachiPerf 3 cửa sổ 60 s]: `HAL đọc = 420/phút` trong khi
     * vòng 09-16 đo idle = 0/phút. Dock mặc định mang 10 nút ⇒ điều kiện cũ luôn `true` ⇒ vòng 1 Hz **không bao giờ
     * ngủ**, kể cả khi mọi nút đều đã bị [HalAbsentCache] xử nguội (off-car / trim không provision / HAL từ chối).
     *
     * Nay: mọi nút đang hiện đều nguội **và** không có datum nhanh ⇒ `false` ⇒ [CarStatusRepository] lùi về hỏi lại
     * mỗi `slowMs`. Đường phục hồi KHÔNG bị gate bằng dữ liệu mà chỉ chính nó làm mới (CLAUDE.md §3): mốc thử lại
     * nằm trong cache theo **đồng hồ**, không theo lượt đọc — tới hạn thì [HalAbsentCache.shouldRead] tự `true`,
     * hàm này tự `true`, vòng nhanh tự dậy trong vòng một nhịp chậm (khoá bằng `CarDataAdapterTest` K1b(e)).
     */
    override fun fastNeeded(): Boolean {
        if (CarDataDemand.needsFast(demand())) return true
        val want = controlDemand()
        if (want.isEmpty()) return false
        val now = clock()
        // 3.1.6 HAL-EVENTS: nút mà datum của nó đang được sự kiện phục vụ ⇒ không cần nhịp 1 s (sự kiện tự làm mới — [readEvents]).
        // 3.1.6 · R32: nút đọc ở nhịp thân xe ([CarDataDemand.mirrorsBody] — cốp) không đánh thức nhịp 1 s.
        return want.any { absent.shouldRead(it, now) && !controlCovered(it) &&
            ControlRegistry.byId(it)?.let(CarDataDemand::mirrorsBody) != true }
    }

    /**
     * 3.1.9 HAL-POLL-COVERED — ô điều khiển [id] đang được sự kiện phục vụ: datum nó mượn `covered`, VÀ nút đọc đúng khoá của datum ấy
     * (cùng tham số getter — [ControlDef.readArg] khác tham số của datum ⇒ khoá đọc khác khoá trong kế hoạch ⇒ không sự kiện nào làm mới
     * nó ⇒ `false`, poll như cũ). MỘT luật cho [fastNeeded] và [readControls] — hai bên lệch nhau là nhịp 1 s chạy mà không đọc gì, hoặc
     * nghỉ mà ô đóng băng.
     */
    private fun controlCovered(id: String): Boolean {
        val p = readPathOf(id) ?: return false
        return p.arg == HalBindingTable.readArg(p.id) && covered(p.id)
    }

    /**
     * [SOÁT P2-1 · 2026-09-16] Quên mọi kết luận *"xe này không có datum ấy"* ([HalAbsentCache.clear]).
     *
     * Vì sao phải có chỗ gọi, không để `clear()` nằm không: giãn nhịp chạm trần **10 phút**, nên một datum vắng
     * lâu rồi mới có (ETA sạc lúc vừa cắm sạc, ghế/ECU lúc vừa nổ máy) có thể câm tới 10 phút — trong khi người
     * dùng vừa mở màn chính lên đúng để xem nó.
     *
     * Gọi CÙNG chỗ với [CarDataDemand.Holder.clear] (màn rời tiền cảnh): lượt poll đầu sau khi màn quay lại vốn
     * đã là một lượt **đọc hết** (nhu cầu `null`), nên gỡ kết luận cũ ở đúng đó **không tốn thêm một lượt đọc
     * nào** — nó chỉ làm lượt đọc-hết ấy thật sự đọc hết.
     */
    fun forgetAbsent() = absent.clear()
    fun forgetAbsent(id: String) = absent.forget(id)

    /**
     * K1b (2026-09-25) — người dùng vừa **tác động** nút [controlId] ⇒ quên nguội cho CẢ HAI khoá mà nút ấy có thể
     * nằm dưới: khoá **nút** (đường [readControls] · `readState(nút)`) và khoá **datum** `readKey` (đường nhịp
     * chậm · `CarReadGate.read(datum)`). Hai đường đọc cùng một getter nhưng nhớ riêng, vì `readState` còn có thể `null`
     * do thang mức chưa khớp ([ControlLevels]) chứ không chỉ do xe không có — trộn chung là để một lỗi thang mức
     * làm câm cả ô cụm. Chỗ gọi: `WakeOnWriteControl` (`:app`) sau mỗi lệnh ghi. Nút không có `readKey` ⇒ chỉ
     * quên khoá nút.
     *
     * ## UX4 (2026-09-26) — cặp **mức ↔ AUTO** là MỘT cặp, quên nửa này phải quên cả nửa kia
     * Ô gió gộp ghi `ac_auto` (bật/rời gió tự động — [ClimateAuto.StepIntent]) chứ không ghi `fan` khi vào nấc đáy.
     * Tới lượt này hàm chỉ quên đúng mã vừa ghi ⇒ `fan`/`ac_wind` còn **nguội** nên nhịp kế không đọc lại, và ô hiện
     * lại con số cũ dù xe đã sang AUTO — đúng họ lỗi *"hai nửa của một ô nói hai điều"*.
     *
     * Suy từ **DỮ LIỆU**, không một `if (id == "fan")` nào (CLAUDE.md §7): họ hàng của mã vừa ghi là (a) nút khai
     * [ControlDef.autoId] **trỏ tới** nó (chiều ngược: ghi `ac_auto` ⇒ `fan`), (b) nút mà chính nó trỏ tới (chiều
     * xuôi: ghi `fan` ⇒ `ac_auto`), và với mỗi nút họ hàng là `readKey` của nút ấy + các datum **bạn đồng hành**
     * ([CarDataDemand.COMPANION], vd `ac_wind` ⇒ `ac_wind_auto`). Nút thứ hai khai `autoId` là tự có, không sửa mã.
     *
     * Quên thừa một mã chỉ tốn **một** lượt đọc ở nhịp kế (rồi nguội lại như cũ), còn quên thiếu thì ô nói sai tới
     * 10 phút — nên ở đây chọn phủ cả hai chiều.
     */
    fun forgetAbsentControl(controlId: String) {
        val self = ControlRegistry.byId(controlId)
        val kin = ControlRegistry.ALL.filter { it.autoId == controlId } +
            listOfNotNull(self, self?.autoId?.takeIf { it.isNotBlank() }?.let(ControlRegistry::byId))
        val keys = LinkedHashSet<String>().apply { add(controlId) }
        kin.forEach { d ->
            keys += d.id
            if (d.readKey.isNotBlank()) { keys += d.readKey; keys += CarDataDemand.COMPANION[d.readKey].orEmpty() }
        }
        keys.forEach(absent::forget)
    }

    // ── 6 method CŨ (tương thích WorkspaceView/WidgetViews) ────────────────────────────────────────────
    override fun batteryPercent(): Int? = table.readInt("soc")
    override fun rangeKm(): Int? = table.readInt("ev_range_km")

    /** Áp suất 4 lốp theo BAR cho widget cũ (HAL trả kPa → ÷100). [trước-trái, trước-phải, sau-trái, sau-phải]. */
    override fun tirePressuresBar(): List<Double>? {
        val fl = table.readDouble("tyre_p_fl"); val fr = table.readDouble("tyre_p_fr")
        val rl = table.readDouble("tyre_p_rl"); val rr = table.readDouble("tyre_p_rr")
        if (fl == null && fr == null && rl == null && rr == null) return null
        fun bar(kpa: Double?) = ((kpa ?: 0.0) / 100.0)
        return listOf(bar(fl), bar(fr), bar(rl), bar(rr))
    }

    override fun pm25Level(): Int? = table.readInt("pm25_level")
    override fun speedKmh(): Int? = table.readInt("speed")
    override fun outsideTempC(): Int? = table.readInt("ext_temp")

    // ── NHỊP NHANH (~1s): tốc độ / động lực / công suất ─────────────────────────────────────────────────
    override fun readFast(prev: CarStatus): CarStatus {
        val g = gate()
        val d = prev.drivetrain
        val speed = g.int("speed", d.speedKmh)
        // 3.1.9 — chỉ lượt ĐỌC THẬT (màn/ghim cần tốc độ) mới góp; `null` (đọc hỏng / đang nguội) không ghi đè. Nhịp này KHÔNG đọc từ mẫu chung.
        if (g.wants("speed")) speedShare?.offer(speed?.toDouble())
        return prev.copy(
            drivetrain = CarStatus.Drivetrain(
                speedKmh = speed,
                gear = g.str("gear", d.gear),
            ),
            energy = prev.energy.copy(motorPowerKw = g.int("motor_power", prev.energy.motorPowerKw)),
            // #5 (owner 2026-09-21 "không realtime"): giá trị Ô ĐIỀU KHIỂN (nhiệt/gió/…) đọc ở nhịp NHANH (1s) để
            // người lái chỉnh trên màn AC gốc thì launcher đổi trong ~1s, thay vì chờ tới 10s của nhịp chậm. Rẻ:
            // ≤ số ô control đang hiện có readKey ([controlDemand]); nút đọc-không-ra ⇒ giữ giá trị cũ (không bịa).
            controls = readControls(prev.controls),
        )
    }

    // ── NHỊP THÂN XE (~3 s, 3.1.6): cửa — chỉ khi màn bày ([CarDataDemand.BODY_IDS]) ──────────────────────────────
    override fun bodyNeeded(): Boolean {
        val d = demand()
        // 3.1.6 · R32 — nút cốp đang hiện ⇒ nhịp thân xe chạy (cốp chỉ đọc ở đây, không ở nhịp nhanh 1 s — [readControls]).
        if (mirrored().any { !covered(it.readKey) }) return true
        if (!CarDataDemand.needsBody(d)) return false
        // 3.1.6 HAL-EVENTS: nhịp 3 s chỉ còn là ĐƯỜNG LÙI — mọi datum nó đọc đều có sự kiện (helper sống, đăng ký OK, chưa quá hạn
        // đối soát) ⇒ nghỉ; một cái không ⇒ chạy như trước.
        return (d?.filter { it in CarDataDemand.BODY_IDS } ?: CarDataDemand.BODY_IDS).any { !covered(it) }
    }

    /** 3.1.6 · R32 — nút đang hiện lấy trạng thái từ nhịp thân xe ([CarDataDemand.mirrorsBody]: `trunk`). */
    private fun mirrored(): List<ControlDef> = controlDemand().mapNotNull(ControlRegistry::byId).filter(CarDataDemand::mirrorsBody)

    override fun readBody(prev: CarStatus): CarStatus {
        // R32: nút cốp đang hiện mà màn KHÔNG bày datum cốp ⇒ vẫn đọc datum ấy ở đây (một lượt / 3 s); lượt sự kiện giữ cổng của nó.
        val mine = mirrored()
        val g = eventGate ?: CarReadGate(table, demand()?.let { d -> d + mine.map { it.readKey } }, absent, clock()); val b = prev.body
        val body = b.copy(
            doorLfOpen = g.bool("door_lf", b.doorLfOpen), doorRfOpen = g.bool("door_rf", b.doorRfOpen),
            doorLrOpen = g.bool("door_lr", b.doorLrOpen), doorRrOpen = g.bool("door_rr", b.doorRrOpen),
            tailgateOpen = g.bool("tailgate_status", b.tailgateOpen),   // 3.1.6 · R32 — cốp `getDoorState(6)`
        )
        return prev.copy(body = body, controls = mirrorBody(prev.controls, body, mine.filter { g.wants(it.readKey) }))
    }

    /**
     * 3.1.6 · R32 — nút đang hiện mà trạng thái CHÍNH LÀ số datum nhịp thân xe ([CarDataDemand.mirrorsBody] — `trunk`) lấy số vừa
     * đọc ở trên: 0 lượt HAL thêm, và [readControls] (nhịp NHANH 1 s) bỏ qua nút ấy ⇒ cốp chỉ được đọc ở nhịp thân xe 3 s.
     * Đọc không ra (`null`) ⇒ GIỮ số cũ, cùng luật [readControls].
     */
    private fun mirrorBody(prev: Map<String, Int>, body: CarStatus.Body, ids: List<ControlDef>): Map<String, Int> {
        if (ids.isEmpty()) return prev
        val out = HashMap(prev)
        ids.forEach { d -> bodyBool(d.readKey, body)?.let { out[d.id] = if (it) 1 else 0 } }
        return out
    }

    /** Cờ của datum nhịp thân xe [id] trong [b] — đúng [CarDataDemand.BODY_IDS], không datum nào khác. */
    private fun bodyBool(id: String, b: CarStatus.Body): Boolean? = when (id) {
        "door_lf" -> b.doorLfOpen; "door_rf" -> b.doorRfOpen; "door_lr" -> b.doorLrOpen; "door_rr" -> b.doorRrOpen
        "tailgate_status" -> b.tailgateOpen
        else -> null
    }

    // ── NHỊP CHẬM (~10s): pin/tầm/sạc · khí hậu · lốp · thân xe · đèn · danh tính ────────────────────────
    override fun readSlow(prev: CarStatus): CarStatus {
        val g = gate()
        if (eventGate == null) reportPolls()
        val e = prev.energy; val c = prev.climate; val t = prev.tyres
        val b = prev.body; val l = prev.lights; val i = prev.identity
        return prev.copy(
            energy = e.copy(   // GIỮ motorPowerKw của nhịp nhanh
                soc = g.int("soc", e.soc),
                evRangeKm = g.int("ev_range_km", e.evRangeKm),
                fuelRangeKm = g.int("fuel_range_km", e.fuelRangeKm),
                odometerKm = g.int("odometer", e.odometerKm),
                sohPct = g.int("soh_oem", e.sohPct),
                fuelPct = g.int("fuel_pct", e.fuelPct),
                tripKm = g.dbl("trip_km", e.tripKm),
                // TRIP-TIME-6MIN: HAL bậc 0,1 h ⇒ làm mượt theo phút (một nguồn cho widget · chip · câu hỏi bằng giọng).
                tripHours = g.dblVia("trip_hours", e.tripHours, tripTime::smooth),
                consumption50 = g.dbl("consumption_50km", e.consumption50),
                // Điện 12V — trước 2026-09-16 nằm ở cụm `Safety`, chuyển sang đây cùng lượt gỡ ADAS. (`volt_12v_level`
                // gỡ 2026-09-25 cùng bốn datum năng lượng chết khác — nhật ký ở `TelemetryRegistry`.)
                volt12v = g.dbl("volt_12v", e.volt12v),
            ),
            climate = CarStatus.Climate(
                pm25Level = g.int("pm25_level", c.pm25Level),
                pm25ValueUgm3 = g.int("pm25_value", c.pm25ValueUgm3),
                // 1.85 — ô [1] của CÙNG getter (`HalReadTables.ARRAY_INDEX`). Hai lượt `g.int` = hai lời gọi HAL
                // cho một mảng; chấp nhận vì cổng `wanted(id)` chỉ đọc khi datum ĐANG HIỆN, và gộp lại sẽ phải
                // dựng một đường "đọc một lần trả nhiều datum" mà hôm nay chỉ có đúng một chủ.
                pm25OutsideUgm3 = g.int("pm25_outside", c.pm25OutsideUgm3),
                pm25Online = g.bool("pm25_online", c.pm25Online),
                cabinTempC = g.int("cabin_temp", c.cabinTempC),
                outsideTempC = g.int("ext_temp", c.outsideTempC),
                acOn = g.bool("ac_on", c.acOn),
                fanLevel = g.int("ac_wind", c.fanLevel),
                recircOn = g.bool("ac_cycle", c.recircOn),
                anionOn = g.bool("anion_state", c.anionOn),
                setTempC = g.int("inside_temp", c.setTempC),
                tempUnit = g.str("temp_unit", c.tempUnit),
                // H1 · T2 — năm getter [ĐO xe 2026-09-16]. Ba mục giữ mã THÔ (`seat*Raw`, `acModeRaw`): phép đổi
                // (thang mức · đảo AUTO) làm ở chỗ HIỂN THỊ/chỗ đọc của nút, đúng MỘT lần — xem KDoc ở `CarStatus`.
                // ⚠ Cả năm dòng phải truyền giá trị CŨ làm `prev`: [CarReadGate.read] phân biệt *"ô không hiện"* (trả prev)
                // với *"đọc không ra"* (trả null = ⚠). Truyền `null` rồi tự `?: cũ` ở ngoài là **xoá mất** phân biệt
                // ấy — datum nguội sẽ đóng băng con số cuối thay vì hiện "—".
                seatVentRaw = g.int("seat_vent_state", c.seatVentRaw),
                seatHeatRaw = g.int("seat_heat_state", c.seatHeatRaw),
                // UX5b (owner 2026-09-27) — ghế PHỤ: cùng getter, `seatID` 2 (một chỗ khai: `HalReadTables.readArg`).
                // Hai dòng này là điều làm cho chip/ô ghế phụ có số THẬT; thiếu chúng thì datum mới compile xanh mà
                // vĩnh viễn "—" (CLAUDE.md §8) và `TelemetryReadoutTest` ca FULL WIRE đỏ.
                seatVentRRaw = g.int("seat_vent_state_r", c.seatVentRRaw),
                seatHeatRRaw = g.int("seat_heat_state_r", c.seatHeatRRaw),
                defrostFrontOn = g.bool("defrost_front_state", c.defrostFrontOn),
                defrostRearOn = g.bool("defrost_rear_state", c.defrostRearOn),
                acModeRaw = g.int("ac_mode_auto", c.acModeRaw),
                acWindAutoRaw = g.int("ac_wind_auto", c.acWindAutoRaw),   // 1.85 — chỉ báo gió auto (0 = AUTO)
            ),
            tyres = readTyres(g, t),
            body = CarStatus.Body(
                windowLfPct = g.int("window_lf", b.windowLfPct),
                windowRfPct = g.int("window_rf", b.windowRfPct),
                windowLrPct = g.int("window_lr", b.windowLrPct),
                windowRrPct = g.int("window_rr", b.windowRrPct),
                // 3.1.6 — cửa đọc ở nhịp THÂN XE ([readBody], ~3 s), không ở đây: GIỮ giá trị của nhịp ấy.
                doorLfOpen = b.doorLfOpen, doorRfOpen = b.doorRfOpen, doorLrOpen = b.doorLrOpen, doorRrOpen = b.doorRrOpen,
                sunshadePct = g.int("sunshade_pct", b.sunshadePct),
                powerLevel = g.int("power_level", b.powerLevel),
                vehicleType = g.str("vehicle_type", b.vehicleType),
                sunroofOpen = g.bool("sunroof_state", b.sunroofOpen),
                emergencyAlarm = g.bool("emergency_alarm", b.emergencyAlarm),
            ),
            lights = CarStatus.Lights(
                lowBeam = g.bool("light_low_beam", l.lowBeam),
                highBeam = g.bool("light_high_beam", l.highBeam),
                frontFog = g.bool("light_front_fog", l.frontFog),
                drl = g.bool("light_drl", l.drl),
                headlightMode = g.int("headlight_feedback", l.headlightMode),
                rearFog = g.bool("light_rear_fog", l.rearFog),
                leftTurn = g.bool("light_left_turn", l.leftTurn),
                rightTurn = g.bool("light_right_turn", l.rightTurn),
                sideLight = g.bool("light_side", l.sideLight),
            ),
            identity = CarStatus.Identity(
                vin = g.str("vin", i.vin),
                oilLevelPct = g.int("oil_level", i.oilLevelPct),
            ),
            // A9 — âm lượng Android (`AudioManager`), nhịp CHẬM: nó chỉ đổi khi người ta bấm, và đọc nó không tốn
            // một lời gọi HAL nào nên không cần nhịp nhanh.
            infotainment = CarStatus.Infotainment(
                mediaVolume = g.int("media_vol", prev.infotainment.mediaVolume),
            ),
        )
    }

    /**
     * ═══ 2.88 · Lốp: áp suất + nhiệt + 13 mã trạng thái của xe, đọc CÓ ĐIỀU KIỆN (spec `kachi-288-tyre-car-state` R7) ═══
     *
     * Màu cụm của bánh x (`tyre_c_x`) luôn đọc cùng áp suất — đường phục hồi không bị gate bằng dữ liệu chỉ chính nó
     * làm mới (CLAUDE.md §3): màu đổi khỏi *trắng* là nhịp kế đọc lại mã TPMS. Ba luật cắt lượt đọc:
     *  1. Màu *trắng* ([TyreJudge.COLOUR_WHITE] = cụm đã phán bình thường ⇒ M1 dừng ngay) ⇒ KHÔNG đọc PS/LK của bánh ấy,
     *     trả `null` (số cũ của một lượt phán đã hết hiệu lực không còn là sự thật — cùng lẽ *"đang nguội ⇒ null"*).
     *  2. `tyre_sys` CHỈ đọc khi có ít nhất một bánh mà màu cụm **không** phải 1/2/3 hợp lệ: M1 không bao giờ hỏi SYS,
     *     nên cụm đã tô vàng/đỏ thì SYS là lượt đọc vô ích (soát 2.88 regress-2).
     *  3. Mã TPMS còn cần đọc (PS · LK · SYS) GIỮ số cũ tối đa [TPMS_HOLD_MS] (≈ mỗi 3 nhịp chậm 10 s), xem [tpmsDue];
     *     chưa có số cũ (lần đầu · vừa rời *trắng*) ⇒ đọc NGAY, nên chữ lý do không trễ một nhịp nào khi cụm vừa đổi màu.
     *
     * Ngân sách K1 (< 150 lượt/phút) đo trên màn MẶC ĐỊNH (`w_board` có lốp ⇒ MỌI người dùng bố cục mặc định đều
     * trả giá này, không chỉ người chọn chip) — [ĐO] `TyreChipTest` R7 K1, xem bảng §4.5 của spec.
     */
    private fun readTyres(g: CarReadGate, t: CarStatus.Tyres): CarStatus.Tyres {
        val cFl = g.int("tyre_c_fl", t.cFl); val cFr = g.int("tyre_c_fr", t.cFr)
        val cRl = g.int("tyre_c_rl", t.cRl); val cRr = g.int("tyre_c_rr", t.cRr)
        // 3.1.6 HAL-EVENTS: lượt sự kiện KHÔNG chạm mốc TPMS (nó chỉ bày vài datum ⇒ `tpmsDue` sẽ xoá mốc và ép nhịp kế đọc lại 13 mã).
        val due = if (eventGate != null) false else tpmsDue(g.now, TyreIds.RAW_STATES.all(g::wants))
        fun state(id: String, prev: Int?) = if (due || prev == null) g.int(id, prev) else prev
        fun ifNeed(c: Int?, id: String, prev: Int?) = if (c != TyreJudge.COLOUR_WHITE) state(id, prev) else null
        val clusterJudgedAll = listOf(cFl, cFr, cRl, cRr).all { it in CLUSTER_COLOURS }
        return CarStatus.Tyres(
            pFlKpa = g.dbl("tyre_p_fl", t.pFlKpa),
            pFrKpa = g.dbl("tyre_p_fr", t.pFrKpa),
            pRlKpa = g.dbl("tyre_p_rl", t.pRlKpa),
            pRrKpa = g.dbl("tyre_p_rr", t.pRrKpa),
            tFlC = g.int("tyre_t_fl", t.tFlC),
            tFrC = g.int("tyre_t_fr", t.tFrC),
            tRlC = g.int("tyre_t_rl", t.tRlC),
            tRrC = g.int("tyre_t_rr", t.tRrC),
            cFl = cFl, cFr = cFr, cRl = cRl, cRr = cRr,
            psFl = ifNeed(cFl, "tyre_ps_fl", t.psFl), psFr = ifNeed(cFr, "tyre_ps_fr", t.psFr),
            psRl = ifNeed(cRl, "tyre_ps_rl", t.psRl), psRr = ifNeed(cRr, "tyre_ps_rr", t.psRr),
            lkFl = ifNeed(cFl, "tyre_lk_fl", t.lkFl), lkFr = ifNeed(cFr, "tyre_lk_fr", t.lkFr),
            lkRl = ifNeed(cRl, "tyre_lk_rl", t.lkRl), lkRr = ifNeed(cRr, "tyre_lk_rr", t.lkRr),
            sys = if (clusterJudgedAll) null else state("tyre_sys", t.sys),
            // 2.93 TYRE-BURST-REASON — mã báo 4092/4093 của CHÍNH lượt đọc áp suất ở trên (không thêm lượt HAL nào).
            pcFl = g.code("tyre_p_fl", t.pcFl), pcFr = g.code("tyre_p_fr", t.pcFr),
            pcRl = g.code("tyre_p_rl", t.pcRl), pcRr = g.code("tyre_p_rr", t.pcRr),
        )
    }

    /**
     * Luật 3 của [readTyres]: nhịp này có đọc lại mã TPMS không. Theo **ĐỒNG HỒ**, không đếm nhịp (soát 2.88 Pass 2):
     * mã giữ lại không bao giờ già quá [TPMS_HOLD_MS]. Bản đếm nhịp giữ được một mã cũ HÀNG GIỜ: vòng poll dừng khi màn
     * khuất (`repeatOnLifecycle(STARTED)`) mà bộ đếm + [CarStatus] cũ vẫn còn, nên lượt đầu khi màn quay lại có thể giữ
     * PS/LK/SYS của trước lúc khuất thêm 2 nhịp ⇒ xe không có kênh màu cụm mà lốp đã non trong lúc đó vẫn XANH (M6) tới
     * ~20 s. Cùng lẽ khi lốp rời màn rồi bày lại (đổi hồ sơ / đổi bố cục): nhịp không bày ĐỦ 13 mã ⇒ quên mốc ⇒ lần bày
     * đủ kế đọc NGAY. Nhịp ấy KHÔNG ép đọc: không bề mặt nào vẽ mã thô khi chưa bày đủ bốn bánh (ô lốp lẻ cũ chỉ in số),
     * và mã chưa từng có vẫn đọc ngay qua nhánh `prev == null`. Đồng hồ lùi ⇒ đọc (không bao giờ khoá).
     */
    private fun tpmsDue(now: Long, showingAll: Boolean): Boolean {
        if (!showingAll) { tpmsReadAt = null; return false }
        val last = tpmsReadAt
        if (last != null && now - last in 0 until TPMS_HOLD_MS) return false
        tpmsReadAt = now
        return true
    }

    /** Mốc lượt đọc mã TPMS gần nhất khi màn bày đủ lốp; `null` = chưa có / vừa không bày ⇒ nhịp kế đọc ngay. */
    private var tpmsReadAt: Long? = null

    /**
     * Giá trị THẬT của các nút đang hiện, giữ giá trị CŨ cho nút không còn trong [controlDemand] (một nhịp giao
     * thời không nên xoá về "—"). Nút đọc ra `null` (off-car / getter chưa provision) ⇒ **loại khỏi map** để ô lùi
     * về mức RAM thay vì hiện số bịa. Đọc qua [HalBindingTable.readState] — cùng đường mà nút ± dùng ở [ControlTileFactory].
     *
     * ## K1b (2026-09-25) — đi qua CÙNG cổng vắng [HalAbsentCache] như [CarReadGate.read]
     * Trước K1b hàm này gọi thẳng `readState` mỗi nhịp nhanh, **không** hỏi cache ⇒ nút off-car / chưa provision /
     * HAL từ chối bị hỏi 1 Hz mãi mãi: [ĐO máy ảo 2.65, màn chính đứng yên] `HAL đọc = 420/phút · bỏ-không-hiện =
     * 426 · bỏ-xe-không-có = 120` dù vòng 09-16 đo idle = 0/phút; trên xe [ĐO 09-16] ≈23 ms/lượt HAL ⇒ 7 lượt/s ≈ 16 %
     * một lõi chỉ để nuôi dock. Nay: 3 lần `null` liên tiếp ⇒ nguội, thử lại giãn dần 60 s → 10 phút (luật sẵn có
     * của cache), đếm [KachiPerf.Counter.HAL_SKIP_ABSENT]. Khoá cache = **mã nút** (không phải `readKey`) — xem
     * [forgetAbsentControl] vì sao hai khoá tách nhau; hai không gian mã không giao nhau (`readPathOf` đã dựa vào đó).
     *
     * Nút có giá trị thật ⇒ `record(got = true)` xoá entry ⇒ vẫn đọc MỖI nhịp (#5 không đổi). Người dùng bấm nút ⇒
     * [forgetAbsentControl] ⇒ nhịp kế đọc lại ngay dù đang nguội.
     */
    private fun readControls(prev: Map<String, Int>): Map<String, Int> {
        val ev = eventIds
        // 3.1.9 HAL-POLL-COVERED — nhịp THƯỜNG cũng bỏ nút đang được sự kiện phục vụ ([controlCovered], cùng luật [fastNeeded]): số của nó
        // tới bằng [readEvents]; hết hạn đối soát ⇒ `covered` tự `false` ⇒ nhịp kế đọc thật một lượt. [ĐO xe 15/10] trước bản này 4 getter AC
        // ≈ 250 lượt/phút ở nhịp 1 s dù AC đã có sự kiện.
        val want = controlDemand().let { w -> if (ev == null) w.filterNotTo(HashSet(), ::controlCovered) else w.filterTo(HashSet()) { readPathOf(it)?.id in ev } }
        if (want.isEmpty()) return prev
        val now = clock()
        val out = HashMap<String, Int>(prev)   // giữ nút cũ (ngoài nhu cầu lượt này) — tránh nháy "—" khi giao thời
        for (id in want) {
            // 3.1.6 · R32 — nút lấy số từ nhịp THÂN XE ([readBody] → [mirrorBody], 3 s): không đọc ở nhịp nhanh 1 s này.
            if (ControlRegistry.byId(id)?.let(CarDataDemand::mirrorsBody) == true) continue
            if (!absent.shouldRead(id, now)) { KachiPerf.add(KachiPerf.Counter.HAL_SKIP_ABSENT); continue }
            val v = runCatching { table.readState(id) }.getOrNull()
            absent.record(id, v != null, now)
            if (v != null) out[id] = v   // đọc không ra ⇒ GIỮ giá trị cũ nếu có, không ghi đè bằng bịa
        }
        return out
    }

    /** 3.1.9 — dòng [HalPollReport] gần nhất đã log; chỉ log khi đổi. */
    @Volatile private var lastPollLine: String? = null

    private fun reportPolls() {
        val t = trust ?: return
        val line = runCatching { HalPollReport.line(demand(), controlDemand(), t) }.getOrNull() ?: return
        if (line == lastPollLine) return
        lastPollLine = line
        log(line)
    }

    private companion object {
        /**
         * Tuổi tối đa của một mã TPMS giữ lại (luật 3 của [readTyres], [tpmsDue]): 25 s = 2,5 nhịp chậm 10 s ⇒ trên vòng
         * poll thật nó rơi đúng **mỗi 3 nhịp** (nhịp 20 s giữ, nhịp 30 s đọc — dư ±5 s cho trễ của chính lượt đọc).
         * [ĐO off-car 2026-10-04, `TyreChipTest` R7 K1, màn mặc định, gateway trả lời mọi lượt đọc] tổng lượt đọc/phút
         * cho các ca màu cụm (trắng · đỏ · mã lạ): mỗi nhịp = 126 · 174 · 180; mỗi 2 nhịp = 126 · **150** · 153; mỗi 3
         * nhịp = 126 · 142 · 144 ⇒ chỉ 3 nhịp giữ mọi ca < 150 (K1). Chữ lý do / lời phán dự phòng trễ tối đa ~30 s —
         * cụm đồng hồ của xe vẫn báo ngay.
         */
        const val TPMS_HOLD_MS = 25_000L

        /** Màu cụm HỢP LỆ (1 trắng · 2 vàng · 3 đỏ) — luật M1 dừng ở đó, không hỏi SYS. */
        val CLUSTER_COLOURS = setOf(TyreJudge.COLOUR_WHITE, TyreJudge.COLOUR_YELLOW, TyreJudge.COLOUR_RED)
    }
}
