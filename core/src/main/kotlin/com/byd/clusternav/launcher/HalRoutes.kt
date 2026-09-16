package com.byd.clusternav.launcher

/**
 * ═══ HAI KIỂU của tầng nối HAL: **ĐƯỜNG** đã phân loại và **CỬA** ra ngoài ══════════════════════════════════
 *
 * Tách khỏi `HalBindingTable.kt` ở 1.66 (trần 500 dòng — CLAUDE.md §4.1) và tách theo đúng **vai**, không theo
 * số dòng: tệp kia là bộ **định tuyến + parse** (nó quyết `bindingKey` nào đi đường nào và đọc chuỗi thô ra số),
 * còn hai khai báo dưới đây là **hợp đồng** — thứ mà cả gateway thật ([BydHalGateway]) lẫn mọi gateway giả trong
 * bài kiểm đều phải khớp. Hai vai khác nhau thì đọc riêng dễ hơn đọc chung.
 *
 * ⚠ Không đổi một dòng hành vi nào lúc tách: cùng package, cùng tên, cùng chữ ký.
 */

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
}
