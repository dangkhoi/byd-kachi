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
) : HalGateway {

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

    override fun featureGet(deviceFqn: String, id: Int): String? = features[id]

    override fun featureSet(deviceFqn: String, id: Int, value: Int): Long? {
        featureSetCalls.add(FeatureCall(deviceFqn, id, value))
        return featureRc
    }

    override fun settingGet(key: String): String? = settings[key]
    override fun settingSet(key: String, value: Int): Long? = null
    override fun localGet(target: String, method: String, arg: Int?): String? = null

    override fun localSet(target: String, method: String, args: IntArray): Boolean {
        localCalls.add(LocalCall(target, method, args.toList()))
        return localOk
    }
}
