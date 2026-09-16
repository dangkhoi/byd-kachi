package com.byd.clusternav.launcher

/**
 * ═══ PHẦN **THUẦN** của tầng nối HAL: hợp đồng ra ngoài + phép phân giải đường đọc ═══════════════════════════
 *
 * Tách khỏi `HalBindingTable.kt` ở 1.66 (trần 500 dòng — CLAUDE.md §4.1) và tách theo đúng **vai**, không theo
 * số dòng: tệp kia là bộ **định tuyến + parse** *có gateway trong tay* (nó thật sự gọi xe), còn ở đây là những thứ
 * **không chạm xe một lần nào**:
 *  • [BindingRoute] + [HalGateway] — **hợp đồng** mà cả gateway thật ([BydHalGateway]) lẫn mọi gateway giả trong bài
 *    kiểm đều phải khớp;
 *  • [ReadPath] + [readPathOf] + [applyInverted] (H1 · T5, thêm 2026-09-16) — phép **phân giải đường ĐỌC** của một mã,
 *    chỉ tra hai bộ đăng ký. Ở đây thì nó kiểm được off-car một mình, và `HalBindingTable.kt` không phải phình thêm.
 *
 * ⚠ Không đổi một dòng hành vi nào lúc tách: cùng package, cùng tên, cùng chữ ký.
 */

/**
 * ═══ H1 · ĐƯỜNG **ĐỌC** đã phân giải của một mã (datum HOẶC nút) — THUẦN, không chạm gateway ══════════════
 *
 * @property id mã **datum** thật sự được đọc (nút mượn đường đọc của datum) — dùng để tra `halDevice` và
 *   `HalBindingTable.INVALID_VALUES`, nên phải là mã của datum chứ không phải của nút.
 * @property key khoá binding của datum ấy · @property domain để chọn device cho đường feature-id ·
 * @property arg tham số int của getter (`null` = getter 0-arg).
 */
data class ReadPath(val id: String, val key: String, val domain: Domain, val arg: Int?)

/**
 * Phân giải đường ĐỌC của [id] (spec `kachi-live-state-ux.html` §4.3 · T5).
 *
 * Datum → **y hệt 1.68** (khoá = `bindingKey`, tham số = `HalBindingTable.readArg` theo mã). Nút → khoá đọc là
 * [ControlDef.readKey], mà đó là một **mã datum** ⇒ mượn nguyên đường đọc của datum ấy, nhờ vậy getter của xe chỉ được
 * khai một chỗ duy nhất trong cả dự án ([TelemetryRegistry]). [ControlDef.readArg] ghi đè tham số khi nút cần **vùng
 * khác** với datum (`temp` cần area 1, còn `inside_temp` khai 0 — xem KDoc ở đó).
 *
 * Nút chưa khai `readKey`, hoặc khai một mã datum không có thật ⇒ `null`. **KHÔNG lùi về `bindingKey`**: đó là khoá
 * GHI, và đọc qua nó chính là gốc bệnh 1.66 (spec §2.2) — xem KDoc `HalBindingTable.readState`.
 *
 * Ở đây (`HalRoutes.kt`) chứ không trong bảng nối vì nó **thuần**: chỉ tra hai bộ đăng ký, không gọi HAL một lần nào —
 * cùng vai với [BindingRoute] và [routeOf]; và `HalBindingTable.kt` đã chạm trần 500 dòng (CLAUDE.md §4.1).
 */
fun readPathOf(id: String): ReadPath? {
    TelemetryRegistry.byId(id)?.let { return ReadPath(id, it.bindingKey, it.domain, HalBindingTable.readArg(id)) }
    val def = ControlRegistry.byId(id) ?: return null
    val datum = TelemetryRegistry.byId(def.readKey) ?: return null
    return ReadPath(datum.id, datum.bindingKey, datum.domain, def.readArg ?: HalBindingTable.readArg(datum.id))
}

/**
 * Áp [ControlDef.readInverted]: giá trị đọc → quy ước chung **"1 = đang bật"** (0 ⇒ 1, khác 0 ⇒ 0); `null` đi thẳng
 * qua (*"chưa đọc được"* không có mặt đối nghịch nào để đảo).
 *
 * Hàm RIÊNG, không nhét thẳng vào `readState`, để kiểm được off-car **kể cả khi chưa nút nào khai cờ đó**: `ac_auto`
 * (`getAcControlMode()`, `AC_CTRLMODE_AUTO = 0` — `ac/BYDAutoAcDevice.java:29-30`; [ĐO xe 2026-09-16] đọc ra 0 = AUTO
 * đang bật) còn chờ datum, nên không tách thì cơ chế đảo nằm đó mà **không bài kiểm nào chạm tới** cho tới lượt T6 —
 * đúng hình dạng `CastShell.evictVd` mà CLAUDE.md §8 nói tới.
 */
fun applyInverted(raw: Int?, inverted: Boolean): Int? =
    if (raw == null || !inverted) raw else if (raw == 0) 1 else 0

/** Đường nối HAL đã phân loại cho một `bindingKey`. */
sealed class BindingRoute {
    /** named-method proven trên device BYDAuto ([fqn] = FQN đầy đủ). */
    data class NamedMethod(val fqn: String, val method: String) : BindingRoute()

    /** feature-id số (Overdrive raw) — ghi `set(int[]{id})` / đọc `get(int[]{id})`. */
    data class Feature(val id: Int) : BindingRoute()

    /**
     * ═══ V3 · R11 — feature-id khai bằng **TÊN HẰNG**, giá trị tra lúc CHẠY ══════════════════════════════
     *
     * `"BYDAutoFeatureIds.Engine.ENGINE_FRONT_MOTOR_SPEED"` ⇒ `FeatureName("Engine.ENGINE_FRONT_MOTOR_SPEED")`.
     *
     * ## [ĐO nguồn fw-dl3 2026-09-16] vì sao số decimal viết cứng là SAI từ gốc
     * `BYDAutoFeatureIds` khai `public static final int X;` **không có giá trị** và gán trong `static {}` theo cấu
     * hình xe:
     * ```java
     * int i9 = 1141899272;
     * if (!BYDAutoFeatureIds.isCanFD && !BYDAutoFeatureIds.isToyota) { i9 = 1141901320; }
     * ENGINE_FRONT_MOTOR_SPEED = i9;
     * ```
     * (`fw-dl3/.../BYDAutoFeatureIds.java:13207-13211`). Tức **cùng một tín hiệu mang hai con số khác nhau** tuỳ
     * `isCanFD`/`isToyota` của chính chiếc xe. Số ta chép từ `jadx-tmap` là con số của **một** cấu hình; trên xe
     * owner nó có thể không tồn tại ⇒ `AbsBYDAutoDevice.checkDeviceFeatures` từ chối ⇒ *"You have no permission to
     * use the feature 0x… with this device N"*, và 12 datum im lặng suốt 10 804 dòng log.
     *
     * ⇒ Tên hằng là thứ **ổn định giữa các cấu hình**; con số thì không. Bind theo tên, hỏi framework lúc chạy.
     *
     * @property constName tên hằng, có thể kèm lớp lồng (`"Engine.ENGINE_POWER"`) hoặc không (`"ENGINE_POWER"` —
     *   hằng cũng được khai ở lớp ngoài). Tầng thi hành thử cả hai.
     */
    data class FeatureName(val constName: String) : BindingRoute()

    /** car-setting key. */
    data class Setting(val key: String) : BindingRoute()

    /** local Android (AudioManager / AutoContainer) — KHÔNG qua HAL BYDAuto. */
    data class Local(val target: String, val method: String) : BindingRoute()

    /** chưa map được (command-wrapper / id chưa chắc / GPS) → unavailable (grab-list §9). */
    object None : BindingRoute()
}

/**
 * Trừu tượng thao tác HAL/local — để [HalBindingTable] test được off-car (gateway giả). Impl thật = [BydHalGateway]
 * (bọc [com.byd.clusternav.modules.hal.BydHal]). MỌI method degrade-safe: off-car/thiếu/ném → null/false.
 */
interface HalGateway {
    /** Đọc getter named-method (0/1 int arg) → chuỗi giá trị thô, null nếu không đọc được. */
    fun getter(deviceFqn: String, method: String, arg: Int?): String?

    /** Ghi named-method N int → rc (Long), null nếu off-car/thiếu method/ném. */
    fun namedInt(deviceFqn: String, method: String, args: IntArray): Long?

    /** Đọc feature-id (`get(int[]{id})`) → chuỗi giá trị (kiểu "int=.. float=.. buf=.."), null nếu không đọc. */
    fun featureGet(deviceFqn: String, id: Int): String?

    /** Ghi feature-id (`set(int[]{id}, EventValue.intValue)`) → rc (Long), null nếu off-car/ném. */
    fun featureSet(deviceFqn: String, id: Int, value: Int): Long?

    /** Đọc car-setting → chuỗi, null nếu chưa hỗ trợ. */
    fun settingGet(key: String): String?

    /** Ghi car-setting → rc/status (Long), null nếu chưa hỗ trợ. */
    fun settingSet(key: String, value: Int): Long?

    /** Đọc local (AudioManager…) → chuỗi, null nếu không có. */
    fun localGet(target: String, method: String, arg: Int?): String?

    /** Ghi local (AudioManager…) → true nếu thành công. AutoContainer (cast) KHÔNG wire ở đây → false. */
    fun localSet(target: String, method: String, args: IntArray): Boolean

    /**
     * V3 · R11(a) — giá trị của một hằng `BYDAutoFeatureIds` **trên chiếc xe này**; `null` = không có tên đó /
     * off-car. Xem [BindingRoute.FeatureName] về việc vì sao con số không cố định.
     *
     * Mặc định `null` để mọi gateway giả trong bài test giữ nguyên nghĩa *"không có framework"* — thêm một
     * phương thức bắt buộc vào đây là bắt 10 bài test dựng một bảng chúng không quan tâm.
     */
    fun featureIdByName(constName: String): Int? = null

    /**
     * V3 · R11(b) — FQN device **chứa** feature-id này, theo bảng của chính framework; `null` = không tra được
     * (off-car / bảng không có / id không thuộc device nào). Xem `HalBindingTable.deviceForFeature`.
     */
    fun deviceForFeature(featureId: Int): String? = null

    /**
     * Máy này có **bảng feature-id thật** của framework BYD không.
     *
     * Mặc định `false` ⇒ off-car / máy ảo / mọi gateway giả trong bài kiểm đều nói *"không biết"*, và [featureAbsentOnCar]
     * vì thế không bao giờ dám kết luận một nút là *"xe này không có"*. Xem KDoc ở đó.
     */
    fun featureMapAvailable(): Boolean = false
}

/**
 * Nút này có đường GHI trỏ vào một feature-id mà **chiếc xe này không có** không?
 *
 * ## Vì sao câu hỏi tồn tại — [ĐO xe 2026-09-16] và phản hồi tester
 * `ac_auto` khai feature `1324355606`, mà id đó **không nằm trong `BYDAutoFeatureIds` của xe owner**; owner thì
 * xác nhận xe **CÓ** điều hoà auto. Nói *"điều hoà"* vì thế chỉ nhận lại một câu thất bại chung chung, giống hệt
 * câu của một lỗi tạm thời — người lái không có cách nào biết là **chờ cũng vô ích**. Tester nêu đúng chỗ này:
 * *"điều hoà với lọc bụi nó không hiểu là cái gì"*.
 *
 * ## Ba điều kiện, và điều kiện đầu là thứ giữ cho nó không nói bừa
 *  1. **Bảng phải có thật** ([HalGateway.featureMapAvailable]). Thiếu vế này thì trên máy ảo mọi nút đều bị khai
 *     là *"xe này không có"* — một câu sai theo kiểu làm người ta tin là xe hỏng.
 *  2. Đường GHI phải **phân giải ra một feature-id** (số, hoặc tên hằng tra ra số). Đường named-method /
 *     setting / local không đi qua bảng ấy nên không trả lời được ⇒ `false`.
 *  3. Bảng có, id phân giải được, mà `deviceForFeature` vẫn `null` ⇒ **vắng thật**.
 */
fun featureAbsentOnCar(gateway: HalGateway, bindingKey: String): Boolean {
    if (!gateway.featureMapAvailable()) return false
    val fid = when (val r = HalBindingTable.routeOf(bindingKey)) {
        is BindingRoute.Feature -> r.id
        is BindingRoute.FeatureName -> gateway.featureIdByName(r.constName) ?: return false
        else -> return false
    }
    return gateway.deviceForFeature(fid) == null
}
