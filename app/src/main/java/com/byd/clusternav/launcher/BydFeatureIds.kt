package com.byd.clusternav.launcher

import android.util.Log

/**
 * ═══ V3 · R11 — TRA **TÊN HẰNG → SỐ** và **SỐ → DEVICE** bằng reflection, ngay trên xe ═══════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` **R11**. Bản thi hành của [HalGateway.featureIdByName] /
 * [HalGateway.deviceForFeature]; [BydHalGateway] uỷ quyền xuống đây.
 *
 * ## Vì sao lớp này tồn tại — [ĐO nguồn fw-dl3 2026-09-16]
 * `BYDAutoFeatureIds` **không** phải một bảng hằng cố định. Nó khai `public static final int X;` rồi gán trong
 * `static {}` theo cấu hình xe:
 * ```java
 * AC_AUTO_CLEAN_AIR = (!isCanFD && isToyota) ? 1282416678 : 1301291046;
 * ```
 * (`BYDAutoFeatureIds.java`, dòng static-init). Còn `BYDAutoDeviceFeaturesMap` giữ **bảng thật** *"device nào
 * chứa id nào"*:
 * ```java
 * public static Set<Integer> getFeatureIdsFromDevice(int deviceType) { … }
 * static { map.put(1000, AcMap…); map.put(1001, BodyworkMap…); … map.put(1061, BigdataMap…); }
 * ```
 * Hai thứ ấy trả lời đúng hai câu hỏi đã làm 12 datum im lặng suốt lượt xe 09-16 (*"số nào"* và *"device nào"*) —
 * và chúng trả lời bằng **dữ liệu của chính chiếc xe đang chạy**, không bằng một bản decompile của xe khác.
 *
 * ## Ba tính chất
 *  1. **Cache theo PHIÊN.** Một lượt `Class.forName` + `getField` cho mỗi khung dữ liệu là công lặp lại y nguyên
 *     (giá trị hằng không đổi trong đời tiến trình). Cache cả ca **không tìm thấy** ([MISSING]) — nếu không thì
 *     một tên gõ sai sẽ phản chiếu lại vào reflection ở mỗi nhịp poll.
 *  2. **Thiếu tên ⇒ log ĐÚNG MỘT LẦN.** Cùng luật với rejection-cache của [com.byd.clusternav.modules.hal.BydHal]:
 *     một dòng cảnh báo mỗi nhịp là cách làm nhật ký trên xe trở nên vô dụng (10 804 dòng / 47 phút, [ĐO] 09-16).
 *  3. **Off-car im lặng.** Không có lớp ⇒ `null` ⇒ chỗ gọi lùi về đường cũ. Không ném ra ngoài bao giờ.
 */
object BydFeatureIds {

    private const val TAG = "KachiFeatureIds"

    /** Sentinel *"đã tra, không có"* — phân biệt với *"chưa tra"* mà không cần một `Map` thứ hai. */
    private const val MISSING = Int.MIN_VALUE

    private val idCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val deviceCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /** `""` = *"đã tra, không device nào chứa id này"* — cùng vai [MISSING]. */
    private const val NO_DEVICE = ""

    /**
     * Giá trị của hằng [constName]; `null` = không có / off-car.
     *
     * Nhận cả hai dạng: `"ENGINE_POWER"` (hằng ở lớp NGOÀI) và `"Engine.ENGINE_POWER"` (hằng ở lớp LỒNG). Thử cả
     * hai vì [ĐO nguồn] framework khai **cả hai** — cùng tên, cùng giá trị, hai chỗ (lớp ngoài giữ bản phẳng, lớp
     * lồng giữ bản gom theo device). Ép chọn một dạng là mời gõ sai ở nửa số dòng bind.
     */
    fun idByName(constName: String): Int? {
        val cached = idCache[constName]
        if (cached != null) return cached.takeIf { it != MISSING }
        val v = lookup(constName)
        idCache[constName] = v ?: MISSING
        if (v == null) Log.w(TAG, "không có hằng feature-id \"$constName\" trên xe này — mục này để trống")
        return v
    }

    /**
     * FQN device **chứa** [featureId] theo `BYDAutoDeviceFeaturesMap`; `null` = không tra được.
     *
     * Quét các mã device đã biết ([DEVICE_TYPES]) và trả cái ĐẦU TIÊN chứa id. Một id nằm trong hai device là ca
     * chưa từng thấy trong bảng thật; nếu có, lấy cái đầu vẫn đúng hơn hẳn phép đoán theo [Domain] mà nó thay.
     */
    fun deviceFqnForFeature(featureId: Int): String? {
        deviceCache[featureId]?.let { return it.takeIf { s -> s != NO_DEVICE } }
        val fqn = scanDevices(featureId)
        deviceCache[featureId] = fqn ?: NO_DEVICE
        return fqn
    }

    /**
     * Cầu kiểm thử `featmap` — đổ **toàn bộ** bảng thật của xe ra dạng thuần để ghi JSON.
     *
     * @return `names` = tên hằng → giá trị (cả lớp ngoài lẫn lớp lồng, tên lồng có tiền tố); `devices` = mã
     *   device → danh sách id. Rỗng ⇒ off-car / không có framework BYD.
     *
     * Đây là thứ biến *"lần xe sau đoán tiếp"* thành *"lần xe sau một lệnh là có bảng"*: có tệp này thì mọi dòng
     * `bindingKey` còn NEEDS-ONCAR tra được **off-car**, không phải cắm máy lần nữa.
     */
    fun dump(): Pair<Map<String, Int>, Map<Int, List<Int>>> {
        val names = LinkedHashMap<String, Int>()
        val devices = LinkedHashMap<Int, List<Int>>()
        runCatching {
            val cls = Class.forName(HalBindingTable.FEATURE_IDS_FQN)
            collectInts(cls, "", names)
            cls.declaredClasses.forEach { nested -> collectInts(nested, nested.simpleName + ".", names) }
        }.onFailure { Log.i(TAG, "featmap: không có ${HalBindingTable.FEATURE_IDS_FQN} (off-car?)") }
        runCatching {
            val m = featuresOf() ?: return@runCatching
            DEVICE_TYPES.forEach { type ->
                val set = m(type) ?: return@forEach
                if (set.isNotEmpty()) devices[type] = set.sorted()
            }
        }.onFailure { Log.i(TAG, "featmap: không đọc được bảng device (off-car?)") }
        return names to devices
    }

    // ── reflection ───────────────────────────────────────────────────────────────────────────────

    private fun lookup(constName: String): Int? = runCatching {
        val cls = Class.forName(HalBindingTable.FEATURE_IDS_FQN)
        val dot = constName.indexOf('.')
        if (dot > 0) {
            val nested = cls.declaredClasses.firstOrNull { it.simpleName == constName.substring(0, dot) }
            val short = constName.substring(dot + 1)
            nested?.let { intField(it, short) }?.let { return@runCatching it }
            return@runCatching intField(cls, short)
        }
        intField(cls, constName) ?: cls.declaredClasses.firstNotNullOfOrNull { intField(it, constName) }
    }.getOrNull()

    private fun intField(cls: Class<*>, name: String): Int? = runCatching {
        cls.getField(name).takeIf { it.type == Int::class.javaPrimitiveType }?.getInt(null)
    }.getOrNull()

    private fun collectInts(cls: Class<*>, prefix: String, out: MutableMap<String, Int>) {
        cls.fields.forEach { f ->
            if (f.type != Int::class.javaPrimitiveType) return@forEach
            runCatching { out[prefix + f.name] = f.getInt(null) }
        }
    }

    /** `getFeatureIdsFromDevice` đã bọc thành một lambda, hoặc `null` nếu không có lớp/phương thức. */
    private fun featuresOf(): ((Int) -> Set<Int>?)? = runCatching {
        val cls = Class.forName(HalBindingTable.FEATURES_MAP_FQN)
        val m = cls.getMethod("getFeatureIdsFromDevice", Int::class.javaPrimitiveType)
        return@runCatching { type: Int ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                (m.invoke(null, type) as? Collection<*>)?.filterIsInstance<Int>()?.toSet()
            }.getOrNull()
        }
    }.getOrNull()

    private fun scanDevices(featureId: Int): String? = runCatching {
        val m = featuresOf() ?: return null
        val type = DEVICE_TYPES.firstOrNull { m(it)?.contains(featureId) == true } ?: return null
        DEVICE_FQN[type]
    }.getOrNull()

    /**
     * Mã device của framework ([ĐO nguồn] `BYDAutoDeviceFeaturesMap.java` khối `static {}`).
     *
     * Quét cả **dải** [DEVICE_TYPE_FIRST]–[DEVICE_TYPE_LAST] thay vì chép 50 con số: bảng thật có lỗ (1003 ·
     * 1033 nằm rời, 1044 · 1050–1060 trống) và `getFeatureIdsFromDevice` trả một tập RỖNG cho mã lạ — không ném.
     * Quét cả dải thì bản ROM sau thêm một device là ta tra được ngay, còn chép tay thì nó vô hình.
     */
    private val DEVICE_TYPES: List<Int> = (DEVICE_TYPE_FIRST..DEVICE_TYPE_LAST).toList()

    /** Mã device đầu/cuối của bảng framework ([ĐO nguồn] `map.put(1000, AcMap…)` … `map.put(1061, BigdataMap…)`). */
    private const val DEVICE_TYPE_FIRST = 1000
    private const val DEVICE_TYPE_LAST = 1061

    /**
     * Mã device → FQN lớp device tương ứng — **chỉ những mã Kachi thật sự gọi tới**.
     *
     * Không khai đủ 50 dòng có chủ ý: mã nào không có ở đây thì [deviceFqnForFeature] trả `null` ⇒ chỗ gọi lùi
     * về phép đoán cũ, đúng như khi không có framework. Khai bừa một FQN chưa ai kiểm là dựng một đường gọi vào
     * một lớp có thể không tồn tại — tệ hơn hẳn việc lùi về đường đang chạy.
     */
    private val DEVICE_FQN: Map<Int, String> = mapOf(
        1000 to "BYDAutoAcDevice",
        1001 to "BYDAutoBodyworkDevice",
        1004 to "BYDAutoLightDevice",
        1006 to "BYDAutoEnergyDevice",
        1007 to "BYDAutoInstrumentDevice",
        1008 to "BYDAutoPM2p5Device",
        1009 to "BYDAutoChargingDevice",
        1012 to "BYDAutoEngineDevice",
        1013 to "BYDAutoSpeedDevice",
        1014 to "BYDAutoStatisticDevice",
        1016 to "BYDAutoTyreDevice",
        1023 to "BYDAutoSettingDevice",
        1025 to "BYDAutoRadarDevice",
        1038 to "BYDAutoADASDevice",
        1041 to "BYDAutoDoorLockDevice",
        1042 to "BYDAutoSafetyBeltDevice",
        1046 to "BYDAutoWiperDevice",
        1049 to "BYDAutoSpecialDevice",
    ).mapValues { (_, simple) -> HalBindingTable.deviceFqn(simple) }
}
