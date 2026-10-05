package com.byd.clusternav.launcher

/**
 * BẢNG NỐI HAL (W1b) — map `id` (telemetry/control) → đường chạm xe THẬT, theo `bindingKey` trong registry
 * ([TelemetryRegistry] / [ControlRegistry], :core). Đây là bộ ĐỊNH TUYẾN + PARSE thuần, mọi lời gọi HAL thật đi
 * qua [HalGateway] (tiêm vào) nên **test được off-car** bằng gateway giả. Adapter thật = [BydHalGateway] (bọc
 * [com.byd.clusternav.modules.hal.BydHal] — TÁI DÙNG reflection sẵn có, KHÔNG mở reflection mới).
 *
 * ── 4 đường (theo dạng bindingKey — xem [routeOf]) ──────────────────────────────────────────────────
 *  (a) **named-method proven** `"BYDAutoXxxDevice.method"` → FQN `android.hardware.bydauto.<seg>.<Class>` +
 *      [HalGateway.getter] (đọc) / [HalGateway.namedInt] (ghi N int). Công thức đã proven ở
 *      [com.byd.clusternav.comfort.SeatComfortApplier] / [com.byd.clusternav.body.BodyworkControl] /
 *      [com.byd.clusternav.comfort.Pm25FilterApplier] / [com.byd.clusternav.SpeedProvider].
 *  (b) **feature-id số decimal** `"501219340"` (Overdrive raw) → `set(int[]{id}, EventValue)` /
 *      `get(int[]{id})` qua [HalGateway.featureSet]/[HalGateway.featureGet]. Thiết bị chọn theo [Domain]
 *      (best-effort — id-chính-xác-thuộc-thiết-bị-nào là grab-list §9 on-car; off-car null nên vô hại).
 *  (c) **car-setting key** `"unit_temperature"` (lowercase snake) → [HalGateway.settingGet]/[settingSet].
 *  (d) **local Android** `"AudioManager.setStreamVolume"` / `"AutoContainer.sendInfo"` → [HalGateway.localGet]/
 *      [localSet] (KHÔNG qua HAL BYDAuto). ⚠ AutoContainer (cast) KHÔNG wire ở đây — cast do
 *      `SimpleCastRuntime` sở hữu (ràng buộc: KHÔNG đụng logic cluster-cast).
 *  • **command-wrapper / id chưa chắc** (`"BODYWORK_CMD_HOOD"`, `"INSTRUMENT_HEADLIGHT_ON_OFF"`, `"UNMAPPED_*"`) và
 *    **GPS** `"NaviInfo.lat"` (BLOCKED-BY-DESIGN — xem [LOCAL_TARGETS]) → [BindingRoute.None] ⇒ unavailable (null).
 *
 * ── Degrade-safe (R9) ────────────────────────────────────────────────────────────────────────────────
 * Off-car / thiếu method / HAL ném → gateway trả null/false → đây trả `null` (đọc) / `null` rc (ghi) ⇒ UI "—".
 * rc == [SENTINEL_NOT_PROVISIONED] / [SENTINEL_INVALID] (feature không có trên trim) cũng ⇒ unavailable.
 * KHÔNG gate an toàn (owner bỏ 2026-09-10): KHÔNG đọc tốc độ/số/permit để chặn ghi.
 */
class HalBindingTable(
    private val gateway: HalGateway,
    /** FIX286 · SR2 — bộ hẹn lệnh nhả; mặc định không hẹn gì (xem KDoc [WriteReleaseScheduler]). */
    releaser: WriteReleaseScheduler = WriteReleaseScheduler.NONE,
    /** FIX286 · SR6 — nơi nhận dòng nhật ký `ctl`; mặc định không ghi, không đọc thêm lượt HAL nào. */
    journal: CtlJournal = CtlJournal.NONE,
) {

    /** Nhịp quanh một lệnh ghi (cổng có-mặt · huỷ/hẹn nhả · nhật ký) — xem KDoc [ControlWriteFlow]. */
    private val flow = ControlWriteFlow(
        releaser = releaser,
        journal = journal,
        getterInt = ::getterInt,
        readState = ::readState,
        send = ::send,
        readable = { id -> readPathOf(id) != null },
    )

    // ── ĐỌC ──────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Giá trị THÔ (chuỗi) của [id] hoặc null (unavailable/off-car/NEEDS_CAR/sentinel). Giá trị "không hợp lệ" riêng
     * của từng getter ([INVALID_VALUES], vd tầm điện 1000/1023) cũng ⇒ null, để KHÔNG hiện số vô nghĩa lên ô.
     */
    fun readRaw(id: String): String? = answerRaw(id)?.takeUnless { isInvalidValue(id, it) }

    /**
     * Lời đáp của getter **TRƯỚC** phép lọc [INVALID_VALUES] — sentinel / không đường đọc / off-car vẫn ⇒ `null`.
     *
     * Soát 2.88 Pass 2: chỉ tầng poll dùng, để tách *"getter CÓ, đang trả mã chưa-có-số"* (vd màu lốp cụm 0, áp suất
     * 4094) khỏi *"getter vắng"* — [HalAbsentCache] chỉ được cho nguội cái sau ([HalReadTables.INVALID_IS_PENDING]).
     * Mọi chỗ HIỂN THỊ đi [readRaw] (đã lọc).
     */
    fun answerRaw(id: String): String? {
        val p = readPathOf(id) ?: return null
        val raw = when (val r = routeOf(p.key)) {
            is BindingRoute.NamedMethod -> gateway.getter(r.fqn, r.method, p.arg)
            is BindingRoute.Feature -> gateway.featureGet(deviceForFeature(r.id, p.id, p.domain), r.id)
            // V3 · R11 — tên hằng: tra giá trị lúc chạy; không tra được ⇒ unavailable (KHÔNG đoán một con số).
            is BindingRoute.FeatureName -> gateway.featureIdByName(r.constName)
                ?.let { fid -> gateway.featureGet(deviceForFeature(fid, p.id, p.domain), fid) }
            is BindingRoute.Setting -> gateway.settingGet(r.key)
            is BindingRoute.Local -> gateway.localGet(r.target, r.method, p.arg)
            BindingRoute.None -> null
        } ?: return null
        return raw.takeUnless { rawIsSentinel(it) }
    }

    /**
     * ═══ H1 · GIÁ TRỊ THẬT của một **nút** (mọi [ControlKind]) — hoặc `null` khi chưa đọc được ═══════════
     *
     * Spec `docs/specs/kachi-live-state-ux.html` **T5**. `null` = *"xe không trả lời"* (off-car · máy ảo · trim không
     * provision · **nút chưa khai [ControlDef.readKey]**) ⇒ chỗ gọi hiểu là ⚠ và giữ hành vi 1.68 (lùi về mức đang nhớ
     * trong RAM) — `VoiceDispatcher.runControl` và ô −/+ đều làm đúng thế.
     *
     * **KHÔNG lùi về [ControlDef.bindingKey]** khi `readKey` rỗng: đó là gốc bệnh 1.66 (spec §2.2) — `bindingKey` là
     * khoá **GHI**, đọc `fan` qua `501219340` (= `AC_WIND_LEVEL_SET`) hay `temp` qua `setAcTemperature` chỉ ra rác/
     * sentinel, mà rác tệ hơn hẳn `null`: `null` hiện ⚠, còn rác thì được cộng một nấc rồi **bắn xuống xe**.
     *
     * ⚠ Ba nút còn rỗng (`readl` · `sunshade` · `windows_all`) là CỐ Ý — chúng còn ở mức [CHƯA BIẾT]/[SUY] ngay trong
     * spec §4.5 nên không được đoán một getter. Sáu nút của lượt T2 (`seatc` `seath` `defrost` `defrost_rear`
     * `ac_auto` `vol`) nay ĐÃ nối, xem KDoc `ControlReadKeyTest`.
     *
     * ## Ba phép đổi, theo đúng thứ tự — và cả ba đều là DỮ LIỆU của dòng registry, không phải nhánh theo mã
     *  1. **thang mức** ([ControlLevels]) khi getter trả mã mức của khung chứ không phải 0/1 — ghế mát [ĐO] raw 3 ⇐
     *     màn xe *"mức 2"*; mã NGOÀI thang ⇒ `null` (⚠), không làm tròn thành *"mức 1"*;
     *  2. nút thang mức mà lại là [ControlKind.TOGGLE] ⇒ quy về 0/1 (*"mức 0 = tắt"*), vì đó là thứ ô bật/tắt và
     *     `VoiceDispatcher` đang đọc;
     *  3. **cờ đảo** ([ControlDef.readInverted]) cho getter mà 0 nghĩa là ĐANG BẬT (`ac_auto`).
     *
     * ⚠ [SOÁT 1.69 · P3] Phép (3) áp cho **mặt bật/tắt**, nên nó chạy sau (2) — kể cả trên nhánh thang mức. Bản
     * trước `return` thẳng ở nhánh thang ⇒ một nút vừa khai thang vừa khai [ControlDef.readInverted] sẽ **im lặng
     * không đảo**, tức đúng cái *"ô nói ngược"* mà cờ ấy sinh ra để chặn (hôm nay chưa nút nào khai cả hai, nên
     * bản vá này không đổi một con số nào — nó chặn lượt sửa SAU, và `ControlLevelsTest` khoá cả hai chiều).
     * Nút thang mức KHÔNG phải TOGGLE (CYCLE của T6) thì `readInverted` **vô nghĩa** — một *mức* không có mặt
     * đối nghịch để lật (đảo "mức 2" thành 0 là bịa) ⇒ bất biến ấy khoá bằng bài kiểm ở registry, không bằng một
     * phép đảo thầm lặng ở đây.
     */
    fun readState(id: String): Int? {
        val def = ControlRegistry.byId(id) ?: return null
        // FIX286 · SR3 — số đọc của một datum PHẦN TRĂM mà nằm ngoài 0..100 thì không phải phần trăm (255/65535/-1 =
        // mã "không hợp lệ" của khung) ⇒ `null` (⚠), không phải "đang mở". Luật ở [percentReadingValid].
        val raw = readInt(id)?.takeIf { percentReadingValid(id, it) }
        if (ControlLevels.levelCount(id) == 0) return applyInverted(raw, def.readInverted)
        val level = raw?.let { ControlLevels.levelOf(id, it) } ?: return null
        if (def.kind != ControlKind.TOGGLE) return level
        return applyInverted(if (level > 0) 1 else 0, def.readInverted)
    }

    /**
     * [id] → Int (parse "int=.. float=.." của EventValue hoặc số thuần); sentinel/absent → null.
     *
     * 1.85: datum khai trong [HalReadTables.ARRAY_INDEX] lấy **phần tử thứ N** của getter trả mảng (bụi mịn NGOÀI
     * xe = ô [1] của `getPM2p5Value()`), thay vì ô [0] mà `firstOfArray` lấy mặc định.
     */
    fun readInt(id: String): Int? {
        val idx = HalReadTables.ARRAY_INDEX[id] ?: return coerceInt(readRaw(id))
        return coerceIntAt(readRaw(id), idx)
    }

    /** [id] → Double (float= của EventValue hoặc số thuần). */
    fun readDouble(id: String): Double? = coerceDouble(readRaw(id))

    /** [id] → Boolean (1/0, true/false, "on"/"off"). */
    fun readBool(id: String): Boolean? = coerceBool(readRaw(id))

    /** [id] → chuỗi hiển thị (bỏ khoảng trắng thừa). */
    fun readString(id: String): String? = readRaw(id)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    // ⚠ (V) FEATURE-FILTER 2026-09-17: `readIntList` đã xoá — chỗ gọi DUY NHẤT của nó là nhánh `ARRAY_INDEX`
    // trong [readInt], mà bảng ấy chỉ phục vụ hai ô thời-gian-sạc vừa bị owner gỡ. Xem `HalReadTables`.

    // ── GHI (control) ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Ghi 1 control [id] với tham số chính [primary] (TOGGLE 0/1 · STEP giá trị · COVER 0/1 · SELECT index ·
     * BUTTON 1). Trả rc (Long) hoặc null nếu unavailable/off-car. KHÔNG gate an toàn (tốc độ/số/permit). Args cuối
     * tính bởi [writeArgs] (named-method nhiều-arg như ghế/kính lấy đúng công thức proven).
     *
     * FIX286: cổng **có-mặt** ([ControlDef.presence] — xe tự nói không có bộ phận ⇒ `null`, 0 lượt HAL), lệnh **nhả**
     * ([ControlDef.release]) và nhật ký `ctl` đi qua [ControlWriteFlow]; nút không khai gì thì đúng một lượt [send].
     */
    fun write(id: String, primary: Int): Long? {
        val def = ControlRegistry.byId(id) ?: return null
        return flow.write(def, primary, writeArgs(def, primary), describeWrite(def).first)
    }

    /** Gửi tham số cuối [args] xuống đúng đường ghi của [def] — MỘT bảng định tuyến cho lệnh lẫn lượt nhả. */
    private fun send(def: ControlDef, args: IntArray, primary: Int): Long? = when (val r = routeOf(def.bindingKey)) {
        is BindingRoute.NamedMethod -> gateway.namedInt(r.fqn, r.method, args)
        is BindingRoute.Feature ->
            gateway.featureSet(deviceForFeature(r.id, def), r.id, args.firstOrNull() ?: primary)
        is BindingRoute.FeatureName -> gateway.featureIdByName(r.constName)
            ?.let { fid -> gateway.featureSet(deviceForFeature(fid, def), fid, args.firstOrNull() ?: primary) }
        is BindingRoute.Setting -> gateway.settingSet(r.key, args.firstOrNull() ?: primary)
        is BindingRoute.Local -> if (gateway.localSet(r.target, r.method, args)) 0L else null
        BindingRoute.None -> null
    }

    /**
     * Một getter named-method ([Presence.getter] · [HalProbe.getter]) thành số; khoá không phải named-method, đọc
     * hỏng, sentinel ⇒ `null`. Không qua [readRaw] vì đây KHÔNG phải một datum (không có ô, không vào nhịp poll).
     */
    private fun getterInt(key: String, arg: Int?): Int? {
        val r = routeOf(key) as? BindingRoute.NamedMethod ?: return null
        val raw = gateway.getter(r.fqn, r.method, arg) ?: return null
        return if (rawIsSentinel(raw)) null else coerceInt(raw)
    }

    /**
     * Nút [id] **không có trên xe này**: hoặc trỏ vào một feature-id mà bảng của xe không có (luật thuần ở
     * `featureAbsentOnCar`), hoặc (FIX286 · SR4) getter cấu hình của nó trả một mã "vắng" ([ControlDef.presence]).
     */
    fun featureAbsentOnCar(id: String): Boolean = ControlRegistry.byId(id)
        ?.let { featureAbsentOnCar(gateway, it.bindingKey) || flow.presenceAbsent(it) != null } ?: false

    /** FIX286 · SR4 — riêng vế *"xe tự báo không có bộ phận"* của [featureAbsentOnCar] (câu nói khác cho người lái). */
    fun partAbsentOnCar(id: String): Boolean = ControlRegistry.byId(id)?.let { flow.presenceAbsent(it) != null } ?: false

    /**
     * Máy này có **bảng feature-id THẬT** của xe không (uỷ quyền [HalGateway.featureMapAvailable]).
     *
     * Tách khỏi [featureAbsentOnCar] vì hai câu hỏi khác nhau: *"nút này vắng"* vs *"máy này không có xe nào cả"*.
     * Chỗ gọi: `CarControlAdapter.writeFailureIsReal` — xem KDoc `CarControlPort.writeFailureIsReal`.
     */
    fun featureMapPresent(): Boolean = gateway.featureMapAvailable()

    // ── Tra registry (id toàn cục duy nhất: telemetry trước, control sau) ──────────────────────────────
    /** FQN thiết bị cho đường feature-id, chọn theo [Domain] (best-effort — id↔device chính xác = grab-list §9). */
    private fun featureDeviceFqn(domain: Domain): String = Companion.featureDeviceFqn(domain)

    /** FQN thiết bị feature-id cho một [def]: ưu tiên ghi đè [ControlDef.halDevice], nếu không thì theo [Domain]. */
    private fun featureDeviceFor(def: ControlDef): String =
        def.halDevice?.let { deviceFqn(it) } ?: featureDeviceFqn(def.domain)

    /**
     * Như trên nhưng tra theo [id] (đường ĐỌC): ưu tiên [TelemetrySpec.halDevice] (§C — thêm 2026-09-15), rồi
     * [ControlDef.halDevice] (id vừa đọc vừa ghi), cuối cùng theo [domain].
     */
    private fun featureDeviceFor(id: String, domain: Domain): String =
        TelemetryRegistry.byId(id)?.let { featureDeviceFor(it) }
            ?: ControlRegistry.byId(id)?.halDevice?.let { deviceFqn(it) }
            ?: featureDeviceFqn(domain)

    /**
     * ═══ V3 · R11(b) — HỎI FRAMEWORK feature-id này thuộc device nào, thay vì đoán theo [Domain] ═════════
     *
     * [ĐO nguồn fw-dl3] `BYDAutoDeviceFeaturesMap.getFeatureIdsFromDevice(int deviceType)` là **bảng thật** của
     * chính chiếc xe: 50+ device (`1000` Ac … `1061` BigData), mỗi cái một `Set<Integer>`. Còn
     * `AbsBYDAutoDevice.checkDeviceFeatures` từ chối đúng khi id **không** nằm trong set của device được gọi —
     * tức câu *"gọi sai device"* trả lời được bằng máy, không cần đoán.
     *
     * Tầng dưới ([HalGateway.deviceForFeature]) trả `null` khi off-car / framework không có bảng ⇒ **lùi về**
     * phép đoán cũ (`halDevice` khai tay, rồi [Domain]). Đó là chủ ý: bảng là một phép **cải thiện**, không phải
     * một cổng — thiếu nó thì mọi thứ chạy y như 1.65, không có gì im lặng tắt đi.
     */
    private fun deviceForFeature(featureId: Int, id: String, domain: Domain): String =
        gateway.deviceForFeature(featureId) ?: featureDeviceFor(id, domain)

    /** Như trên, cho đường GHI (đã có sẵn [ControlDef]). */
    private fun deviceForFeature(featureId: Int, def: ControlDef): String =
        gateway.deviceForFeature(featureId) ?: featureDeviceFor(def)

    companion object {
        // [SOÁT 2026-09-16 · P3] Bốn mẫu dựng MỘT lần, không `Pattern.compile` lại ở MỖI lượt đọc datum ([ĐO xe, bản 1.67
        // H1] ~510 getter HAL/phút × `readRaw → rawIsSentinel → coerceInt`, riêng coerceInt hai mẫu). Thuần cơ học.
        private val INT_RE = Regex("int=(-?\\d+)")
        private val FLOAT_RE = Regex("float=(-?[0-9.]+)")
        private val DIGITS_RE = Regex("-?\\d+")                    // bindingKey toàn số ⇒ feature-id, xem [routeOf]
        private val SETTING_KEY_RE = Regex("[a-z][a-z0-9_]*")      // lowercase snake ⇒ khoá `Settings`

        /** Arg int cho GETTER named-method theo id — bảng ở [HalReadTables.readArg] (uỷ quyền, giữ chữ ký cũ). */
        fun readArg(id: String): Int? = HalReadTables.readArg(id)

        /** Giá trị "không hợp lệ" riêng từng getter ⇒ unavailable — xem [HalReadTables.INVALID_VALUES]. */
        val INVALID_VALUES: Map<String, Set<Int>> get() = HalReadTables.INVALID_VALUES

        /** [raw] (lời đáp của [answerRaw]) là một mã [INVALID_VALUES] của datum [id] ⇒ không phải con số để hiện. */
        fun isInvalidValue(id: String, raw: String): Boolean {
            val invalid = readPathOf(id)?.id?.let { INVALID_VALUES[it] } ?: return false
            return coerceInt(raw)?.let { it in invalid } == true
        }

        /** FQN thiết bị feature-id cho một [spec] ĐỌC: ưu tiên [TelemetrySpec.halDevice], nếu không thì theo [Domain]. */
        fun featureDeviceFor(spec: TelemetrySpec): String =
            spec.halDevice?.let { deviceFqn(it) } ?: featureDeviceFqn(spec.domain)

        /** rc HAL khi feature KHÔNG provision trên trim (= `Int.MIN_VALUE + 1000`). Đo lặp trên xe owner. */
        const val SENTINEL_NOT_PROVISIONED = -2147482648L

        /** rc HAL "giá trị không hợp lệ" (khác NOT_PROVISIONED đúng 3). */
        const val SENTINEL_INVALID = -2147482645L

        /**
         * rc là một mã lệnh HỎNG của khung ⇒ coi như unavailable. Phủ CẢ DẢI `FAILED -2147482648 · BUSY -2147482647 ·
         * TIMEOUT -2147482646 · INVALID_VALUE -2147482645` (2.88 · R8 — [ĐO source] jadx-tmap
         * `tyre/BYDAutoTyreDevice.java:31-35`; cùng bộ số ở `ac/BYDAutoAcDevice.java:11,15` ·
         * `bodywork/BYDAutoBodyworkDevice.java:185,189`): bản trước chỉ bắt hai đầu dải, nên một
         * getter trả BUSY/TIMEOUT lọt qua như một giá trị thật (vd *"mã -2147482647"*, hoặc tệ hơn: một lệnh ghi bị
         * coi là đã nhận).
         */
        fun isSentinelRc(rc: Long?): Boolean = rc != null && rc in SENTINEL_NOT_PROVISIONED..SENTINEL_INVALID

        /** `*_COMMAND_BUSY` / `*_COMMAND_TIMEOUT` — [ĐO source] jadx-tmap `tyre/BYDAutoTyreDevice.java:31,35`. */
        const val SENTINEL_BUSY = -2147482647L
        const val SENTINEL_TIMEOUT = -2147482646L

        /**
         * NGHĨA của một mã sentinel, ASCII, cho lời đáp `getid` của cầu kiểm thử (soát 2.88 regress-5). Tách CƠ CHẾ khỏi
         * QUY KẾT (CLAUDE.md §2): chỉ `FAILED` mới được đọc thành *"trim này không có"*; BUSY/TIMEOUT là **tạm thời** —
         * kết luận "không có" từ một lượt bận là ghi sai bằng chứng lớp 1. `null` = [rc] không phải sentinel.
         */
        fun sentinelMeaning(rc: Long?): String? = when (rc) {
            SENTINEL_NOT_PROVISIONED -> "FAILED - feature khong co tren trim nay (hoac HAL tu choi)"
            SENTINEL_BUSY, SENTINEL_TIMEOUT -> "BUSY/TIMEOUT tam thoi - chay lai, CHUA ket luan duoc trim"
            SENTINEL_INVALID -> "INVALID_VALUE - gia tri khong hop le"
            else -> null
        }

        /**
         * Tiền tố `bindingKey` đi đường [BindingRoute.Local] (Android, không qua HAL BYDAuto). `LocationManager` (GPS)
         * CỐ Ý không có ở đây — BLOCKED-BY-DESIGN: quyền location đã retire (`DeadReckonRetirementTest` ở :app ghim
         * manifest không xin quyền location), mở lại = quyết định owner.
         */
        val LOCAL_TARGETS: Set<String> = setOf(
            "AudioManager",
            // Tên service chỉ viết chữ ở `ProjectionRecipe` (CLUSTER-THEME-SAFE B1a — `ThemeOpcodeLiteralContractTest`).
            com.byd.clusternav.modules.clustercast.simplified.ProjectionRecipe.SVC_DILINK3,
        )

        /**
         * Tham số cuối cho GHI — bảng ở [HalWriteArgs.writeArgs] (uỷ quyền, giữ chữ ký cũ; tách 2026-10-02 vì trần
         * 500 dòng). THUẦN (không đọc gateway) ⇒ kiểm được off-car; [ControlWriteArgsTest] khoá cả họ "hai mã một lệnh".
         */
        fun writeArgs(def: ControlDef, primary: Int): IntArray = HalWriteArgs.writeArgs(def, primary)

        // ⚠ (V) FEATURE-FILTER 2026-09-17: `CHARGE_STOP_MARKS` + `chargeStopCapacityEnum(%)` (% → enum
        // `CHARGE_STOP_CAPACITY_*` của `BYDAutoChargingDevice`) đã xoá cùng nút `target_soc_set` — chỗ gọi duy nhất.

        /**
         * Phân loại `bindingKey` → [BindingRoute] (thuần, test được):
         *  • rỗng → [BindingRoute.None];
         *  • toàn số (có thể âm) → [BindingRoute.Feature];
         *  • có `.`: tiền tố `BYDAuto…` → [NamedMethod] (FQN suy ra); [LOCAL_TARGETS] → [Local]; còn lại (vd
         *    `NaviInfo` — GPS, BLOCKED-BY-DESIGN) → [None];
         *  • lowercase snake (`unit_temperature`) → [Setting];
         *  • còn lại (UPPER_SNAKE / PascalCase command) → [None] (NEEDS_CAR grab-list).
         */
        fun routeOf(bindingKey: String): BindingRoute {
            if (bindingKey.isBlank()) return BindingRoute.None
            if (bindingKey.matches(DIGITS_RE)) {
                return bindingKey.toIntOrNull()?.let { BindingRoute.Feature(it) } ?: BindingRoute.None
            }
            val dot = bindingKey.indexOf('.')
            if (dot > 0) {
                val prefix = bindingKey.substring(0, dot)
                val method = bindingKey.substring(dot + 1)
                return when {
                    // ⚠ Phải xét TRƯỚC nhánh `BYDAuto…` ngay dưới: `BYDAutoFeatureIds` cũng bắt đầu bằng
                    // "BYDAuto", và [deviceFqn] sẽ biến nó thành `android.hardware.bydauto.featureids.…` — một
                    // lớp không tồn tại ⇒ mọi dòng bind-theo-tên im lặng trở thành "off-car".
                    prefix == FEATURE_IDS_CLASS -> BindingRoute.FeatureName(method)
                    prefix.startsWith("BYDAuto") -> BindingRoute.NamedMethod(deviceFqn(prefix), method)
                    prefix in LOCAL_TARGETS -> BindingRoute.Local(prefix, method)
                    else -> BindingRoute.None
                }
            }
            if (bindingKey.matches(SETTING_KEY_RE)) return BindingRoute.Setting(bindingKey)
            return BindingRoute.None
        }

        /**
         * FQN thiết bị cho đường feature-id, chọn theo [Domain] (best-effort — id↔device chính xác = grab-list §9).
         * Tách khỏi instance để [describeWrite] mô tả được đường ghi **thuần** (không cần gateway/xe) cho cầu kiểm thử.
         */
        fun featureDeviceFqn(domain: Domain): String = deviceFqn(
            when (domain) {
                Domain.ENERGY -> "BYDAutoStatisticDevice"
                Domain.DRIVETRAIN -> "BYDAutoSettingDevice"
                Domain.CLIMATE -> "BYDAutoAcDevice"
                Domain.TYRES -> "BYDAutoInstrumentDevice"
                Domain.BODY -> "BYDAutoBodyworkDevice"
                Domain.LIGHTS -> "BYDAutoLightDevice"
                Domain.IDENTITY -> "BYDAutoBodyworkDevice"
                Domain.INFOTAINMENT -> "BYDAutoSettingDevice"
            },
        )

        /**
         * Mô tả THUẦN đường GHI của một control cho cầu kiểm thử — KHÔNG chạm gateway/xe, nên tính được off-car và
         * khoá bằng test ở `:core`. Trả (nhãn route, FQN thiết bị) đúng như [write] sẽ định tuyến:
         *  • named-method → `"named:<method>"`, device = FQN suy ra từ prefix;
         *  • feature-id   → `"feature:0x%08x"`, device = [featureDeviceFqn] theo [ControlDef.domain];
         *  • car-setting  → `"setting:<key>"`, device = "";
         *  • local        → `"local:<target>.<method>"`, device = target;
         *  • chưa map      → `"none"`, device = "" (command-wrapper / id chưa chắc — grab-list §9).
         */
        fun describeWrite(def: ControlDef): Pair<String, String> = when (val r = routeOf(def.bindingKey)) {
            is BindingRoute.NamedMethod -> "named:${r.method}" to r.fqn
            is BindingRoute.Feature -> "feature:0x%08x".format(r.id) to
                (def.halDevice?.let { deviceFqn(it) } ?: featureDeviceFqn(def.domain))
            // Mô tả THUẦN ⇒ **không** tra reflection ở đây (hàm này chạy được off-car, đó là cả điểm của nó):
            // nói đúng rằng giá trị sẽ được tra lúc chạy, và device sẽ do bảng của framework quyết.
            is BindingRoute.FeatureName -> "feature_name:${r.constName}" to
                (def.halDevice?.let { deviceFqn(it) } ?: featureDeviceFqn(def.domain))
            is BindingRoute.Setting -> "setting:${r.key}" to ""
            is BindingRoute.Local -> "local:${r.target}.${r.method}" to r.target
            BindingRoute.None -> "none" to ""
        }

        /**
         * Tham số CHÍNH (primary) cho một control theo [ControlDef.kind] khi cầu kiểm thử KHÔNG truyền `--ei v`.
         * TOGGLE/COVER/BUTTON → 1 (bật/mở/bấm — mặt "làm việc" của nút); STEP → giá trị mặc định của nút;
         * SELECT → 0 (lựa chọn đầu). Truyền `v` thì dùng thẳng `v` (đã clamp cho STEP ở [ControlDef.clamp]).
         */
        fun defaultPrimary(def: ControlDef): Int = when (def.kind) {
            ControlKind.STEP -> def.value
            ControlKind.SELECT -> 0
            ControlKind.TOGGLE, ControlKind.COVER, ControlKind.BUTTON -> 1
        }

        /**
         * Tên lớp hằng feature-id của framework BYD. Tiền tố `bindingKey` cho đường [BindingRoute.FeatureName].
         *
         * FQN đầy đủ (`android.hardware.bydauto.BYDAutoFeatureIds`) dựng ở tầng thi hành — nó nằm **thẳng** trong
         * package `bydauto`, không theo công thức `<seg>.<Class>` của [deviceFqn].
         */
        const val FEATURE_IDS_CLASS = "BYDAutoFeatureIds"

        /** FQN của lớp hằng feature-id — một chỗ duy nhất dựng chuỗi này. */
        const val FEATURE_IDS_FQN = "android.hardware.bydauto.$FEATURE_IDS_CLASS"

        /** FQN của bảng feature→device của framework BYD ([HalGateway.deviceForFeature] dùng). */
        const val FEATURES_MAP_FQN = "android.hardware.bydauto.BYDAutoDeviceFeaturesMap"

        /** FQN thiết bị BYDAuto từ tên đơn giản: `BYDAutoPM2p5Device` → `android.hardware.bydauto.pm2p5.BYDAutoPM2p5Device`. */
        fun deviceFqn(simpleClass: String): String {
            val seg = simpleClass.removePrefix("BYDAuto").removeSuffix("Device").lowercase()
            return "android.hardware.bydauto.$seg.$simpleClass"
        }

        /**
         * Chuỗi thô có phải sentinel không. [SOÁT 2026-09-16 · P3] Bản trước `coerceInt(raw)?.toLong().let { isSentinelRc(it) }`:
         * `?.` bám vào `toLong()` chứ KHÔNG vào `let` ⇒ `let` chạy trên giá trị có thể `null`, chỉ lọt vì [isSentinelRc] nhận `Long?` — siết chữ ký kia một lần là thành NPE trên xe.
         */
        fun rawIsSentinel(raw: String): Boolean = isSentinelRc(coerceInt(raw)?.toLong())

        /**
         * Mảng từ gateway ("[a, b, …]" — `BydHal.arrayToStr`, ≥2 phần tử; 1 phần tử đã là số trần) → phần tử ĐẦU.
         * [ĐO] §B remediation 2026-09-15: getter trả `int[]`/`byte[]` (PM2.5 value/level, wheel_speed…) từng ra
         * `"[I@hash"` ⇒ "—". Không phải mảng → trả nguyên chuỗi.
         */
        private fun firstOfArray(s: String): String =
            if (s.startsWith("[")) s.removePrefix("[").substringBefore(',').substringBefore(']').trim() else s

        /**
         * ═══ 1.85 · Phần tử **thứ [index]** của một giá trị mảng — và vì sao nó KHÔNG có đường lùi ═══════════════
         *
         * Gateway trả mảng dưới dạng `"[a, b, …]"` (`BydHal.arrayToStr`). Hàm này cắt ô thứ [index]; `index = 0`
         * tương đương [firstOfArray] nên chỗ gọi mặc định không cần nó.
         *
         * **Chuỗi KHÔNG phải mảng + [index] > 0 ⇒ `null`**, cố ý không lùi về số thuần. Chủ duy nhất hôm nay là
         * `pm25_outside`: nếu ROM nào trả về một số đơn (chỉ đo trong cabin) thì đường lùi sẽ hiện **số trong
         * cabin dưới nhãn "ngoài xe"** — một con số sai mà trông như đang sống, đúng họ lỗi *"nhãn hứa việc A, hiện
         * việc B"* mà dự án đã trả giá ở nút *"Kính 50%"* và cặp `lock`/`door`. `null` thì ô hiện "—" và người xem
         * biết là chưa đọc được. Mảng ngắn hơn [index] cũng `null`, cùng một lẽ.
         */
        fun coerceIntAt(raw: String?, index: Int): Int? {
            val s = raw?.trim() ?: return null
            if (index == 0) return coerceInt(s)
            if (!s.startsWith("[")) return null
            val parts = s.removePrefix("[").removeSuffix("]").split(',')
            return parts.getOrNull(index)?.let { coerceInt(it.trim()) }
        }

        /**
         * Parse Int từ chuỗi thô: ưu tiên `int=<n>` (EventValue), rồi số thuần, rồi `float=<x>` làm tròn; mảng → [0].
         *
         * Nhánh `float=` [SOÁT 2026-09-15 · P1]: `BydHal.readFeature` **rút** ô sentinel ra khỏi chuỗi (`int=-`) vì
         * `BYDAutoEventValue` khởi tạo cả hai field bằng sentinel — một feature kiểu float hợp lệ vẫn mang
         * `intValue = -999999999`. Không có nhánh này thì mọi datum float-only (đọc bằng [readInt]) thành "—".
         */
        fun coerceInt(raw: String?): Int? {
            val s = raw?.trim()?.let(::firstOfArray) ?: return null
            INT_RE.find(s)?.let { return it.groupValues[1].toIntOrNull() }
            s.toIntOrNull()?.let { return it }
            FLOAT_RE.find(s)?.groupValues?.get(1)?.toDoubleOrNull()
                ?.let { if (it.isFinite()) return Math.round(it).toInt() }
            return s.toDoubleOrNull()?.let { if (it.isFinite()) Math.round(it).toInt() else null }
        }

        /** Parse Double từ chuỗi thô: ưu tiên `float=<x>` (EventValue), rồi số thuần / `int=`; mảng → [0]. */
        fun coerceDouble(raw: String?): Double? {
            val s = raw?.trim()?.let(::firstOfArray) ?: return null
            FLOAT_RE.find(s)?.let { return it.groupValues[1].toDoubleOrNull() }
            s.toDoubleOrNull()?.let { return it }
            return coerceInt(s)?.toDouble()
        }

        /** Parse Boolean: true/false, on/off, 1/0 (>0 = true). */
        fun coerceBool(raw: String?): Boolean? {
            val s = raw?.trim()?.lowercase() ?: return null
            return when (s) {
                "true", "on", "yes" -> true
                "false", "off", "no" -> false
                else -> coerceInt(s)?.let { it > 0 }
            }
        }
    }
}
