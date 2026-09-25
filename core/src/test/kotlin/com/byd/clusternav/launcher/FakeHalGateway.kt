package com.byd.clusternav.launcher

/**
 * [HalGateway] GIẢ cho test JVM off-car: trả giá trị theo map (getter theo method, feature theo id) + GHI LẠI mọi
 * lời gọi (arg/rc) để assert định tuyến của [HalBindingTable]. Mặc định trả null/false (mô phỏng off-car → "—").
 */
class FakeHalGateway(
    private val getters: Map<String, String?> = emptyMap(),   // method → giá trị thô
    private val features: Map<Int, String?> = emptyMap(),      // feature-id → giá trị thô
    private val settings: Map<String, String?> = emptyMap(),   // car-setting key → giá trị thô
    private val namedRc: Long? = null,
    private val featureRc: Long? = null,
    private val localOk: Boolean = false,
    /** method → (arg → giá trị) cho getter per-index (áp lốp 4 góc `getTyrePressureValue(area)`…); ưu tiên trước [getters]. */
    private val gettersByArg: Map<String, Map<Int, String?>> = emptyMap(),
    /**
     * V3 · R11 — bảng giả *"tên hằng `BYDAutoFeatureIds` → số"* của một chiếc xe tưởng tượng.
     *
     * Có mặt để kiểm được đúng cái đã hỏng trên xe thật: cùng một tên hằng mang **hai số khác nhau** tuỳ cấu
     * hình xe, nên bind-theo-số là sai từ gốc. Bảng này để bài test dựng ra hai "chiếc xe" mà không cần xe.
     */
    private val featureNames: Map<String, Int> = emptyMap(),
    /** V3 · R11(b) — bảng giả *"feature-id → FQN device chứa nó"* (`BYDAutoDeviceFeaturesMap` của xe giả). */
    private val featureDevices: Map<Int, String> = emptyMap(),
    /**
     * H1 · T2 — giá trị đường [BindingRoute.Local] (Android, KHÔNG qua HAL BYDAuto), khoá theo **tên method**
     * (`"getStreamVolume"`), cùng khuôn [getters].
     *
     * Thêm 2026-09-16 cùng datum `media_vol`: trước đó mọi gateway giả trả `null` cho `localGet`, nên một datum đi
     * đường Local sẽ *"không bao giờ chảy giá trị"* trong bài FULL WIRE — một ô trống mà không bài nào giải thích.
     */
    private val locals: Map<String, String?> = emptyMap(),
    /**
     * Máy giả này có **bảng feature-id THẬT** không (`HalGateway.featureMapAvailable`). Mặc định `false` = off-car,
     * giữ nguyên nghĩa của mọi bài kiểm cũ; đặt `true` để dựng một "chiếc xe có bảng" cho `writeFailureIsReal` /
     * `featureAbsentOnCar` (thêm ở review Pass 2 · 2026-09-26).
     */
    private val featureMapPresent: Boolean = false,
) : HalGateway {

    override fun featureMapAvailable(): Boolean = featureMapPresent

    override fun featureIdByName(constName: String): Int? = featureNames[constName]

    override fun deviceForFeature(featureId: Int): String? = featureDevices[featureId]

    data class NamedCall(val fqn: String, val method: String, val args: List<Int>)
    data class FeatureCall(val fqn: String, val id: Int, val value: Int)
    data class LocalCall(val target: String, val method: String, val args: List<Int>)

    val getterArgs = mutableMapOf<String, Int?>()
    val namedCalls = mutableListOf<NamedCall>()
    val featureSetCalls = mutableListOf<FeatureCall>()
    val localCalls = mutableListOf<LocalCall>()

    override fun getter(deviceFqn: String, method: String, arg: Int?): String? {
        getterArgs[method] = arg
        gettersByArg[method]?.let { byArg -> if (arg != null) return byArg[arg] }
        return getters[method]
    }

    override fun namedInt(deviceFqn: String, method: String, args: IntArray): Long? {
        namedCalls.add(NamedCall(deviceFqn, method, args.toList()))
        return namedRc
    }

    /** Device của lượt [featureGet] gần nhất — chốt *"bảng của framework đã quyết device"* (V3 · R11 b). */
    var lastFeatureGetDevice: String? = null
        private set

    override fun featureGet(deviceFqn: String, id: Int): String? {
        lastFeatureGetDevice = deviceFqn
        return features[id]
    }

    override fun featureSet(deviceFqn: String, id: Int, value: Int): Long? {
        featureSetCalls.add(FeatureCall(deviceFqn, id, value))
        return featureRc
    }

    override fun settingGet(key: String): String? = settings[key]
    override fun settingSet(key: String, value: Int): Long? = null
    override fun localGet(target: String, method: String, arg: Int?): String? {
        getterArgs[method] = arg
        return locals[method]
    }

    override fun localSet(target: String, method: String, args: IntArray): Boolean {
        localCalls.add(LocalCall(target, method, args.toList()))
        return localOk
    }
}
