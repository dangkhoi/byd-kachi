package com.byd.clusternav.modules.hal

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import com.byd.clusternav.navigation.LaneInfo
import com.byd.clusternav.navigation.NavOutputDecision
import java.lang.reflect.Array as RArray

/**
 * Hạ tầng HAL DÙNG CHUNG cho các module chạm xe IN-PROCESS (không shell-out dadb).
 * Gồm: (1) BydPermissionBypassContext (port kim.apk) — bọc Context để check*Permission→0 cho quyền BYDAUTO,
 * cho HAL getInstance chạy từ app uid không cần OEM perm; (2) reflection getInstance/set/get/probe (SDK BYDAuto
 * KHÔNG có trên classpath → reflection thuần như NavOpen.java).
 *
 * Đây là INFRA (như ModuleHost), nhiều module HAL dùng chung. Xoá hết module HAL (inprochal/tpms/vehicle)
 * → có thể xoá luôn thư mục modules/hal/. Không module nào ngoài HAL import nó.
 */
object BydHal {
    const val EV = "android.hardware.bydauto.BYDAutoEventValue"
    const val IDS = "android.hardware.bydauto.BYDAutoFeatureIds"

    // FQN device (reflection) — đủ cho các module hiện có.
    const val INSTRUMENT = "android.hardware.bydauto.instrument.BYDAutoInstrumentDevice"
    const val SETTING = "android.hardware.bydauto.setting.BYDAutoSettingDevice"
    const val TYRE = "android.hardware.bydauto.tyre.BYDAutoTyreDevice"
    const val SPEED = "android.hardware.bydauto.speed.BYDAutoSpeedDevice"
    const val GEARBOX = "android.hardware.bydauto.gearbox.BYDAutoGearboxDevice"
    const val CHARGING = "android.hardware.bydauto.charging.BYDAutoChargingDevice"
    const val ENERGY = "android.hardware.bydauto.energy.BYDAutoEnergyDevice"
    const val STATISTIC = "android.hardware.bydauto.statistic.BYDAutoStatisticDevice"
    // Điều hoà / tiện-nghi ghế (device 1023 = ghế làm-mát/sưởi). Dùng CHUNG hạ tầng device()/setInt() —
    // KHÔNG chế reflection mới; feature-id ghế + value ở :core `com.byd.clusternav.comfort.SeatComfort`.
    const val AC = "android.hardware.bydauto.ac.BYDAutoAcDevice"
    // Cảm biến bụi mịn PM2.5 (device 1008) — ĐỌC mức bụi (getPM2p5Level()[0]). Dùng CHUNG hạ tầng device()
    // + reflection getter; các method GHI "tự lọc" (enablePurificationFunctionPrompt/setAutoCleanAirState/
    // setQuickCleanAirState) nằm trên AC device, CHỈ có trên ROM xe (KHÔNG trong SDK jar) ⇒ gọi qua reflection
    // ở com.byd.clusternav.comfort.Pm25FilterApplier — KHÔNG thêm lời gọi biên dịch cho method ROM-only ở đây.
    const val PM2P5 = "android.hardware.bydauto.pm2p5.BYDAutoPM2p5Device"
    // Nguồn HƯỚNG cho dead-reckoning (recon: getInstance được không trên ROM này?): góc lái + tốc độ 4 bánh.
    const val BODYWORK = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"   // getSteeringWheelValue (±780°)
    const val SPECIAL = "android.hardware.bydauto.special.BYDAutoSpecialDevice"      // getWheelSpeed(area) 4 bánh → yaw
    // (ENGINE / EngineVoiceSimulator đã theo tiếng pô sang app com.byd.posound — bỏ khỏi ClusterNav)

    // Quyền KHÔNG có substring "byd" nhưng kim.apk vẫn allowlist (để bypass đầy đủ như reference).
    private val EXTRA_PERMS = setOf(
        "android.permission.WRITE_SECURE_SETTINGS", "android.permission.INJECT_EVENTS",
        "android.permission.MEDIA_CONTENT_CONTROL", "android.permission.START_ACTIVITIES_FROM_BACKGROUND")

    /**
     * Context bọc: check*Permission→0 + enforce*→no-op cho mọi quyền BYDAUTO/BYDACQUISITION/byd + EXTRA_PERMS
     * (port BydPermissionBypassContext kim.apk) VÀ getPackageName="com.byd.dashcast" + getApplicationContext=this
     * (khớp NavOpen.wrap đã CHỨNG MINH ghi được cụm — getInstance vài ROM cần spoof package này).
     */
    fun bypass(base: Context): Context = object : ContextWrapper(base) {
        private fun byd(p: String?): Boolean = !p.isNullOrBlank() &&
            (p.contains("BYDAUTO", true) || p.contains("BYDACQUISITION", true) || p.contains("byd", true) || p in EXTRA_PERMS)
        override fun checkPermission(p: String, pid: Int, uid: Int) = if (byd(p)) PackageManager.PERMISSION_GRANTED else super.checkPermission(p, pid, uid)
        override fun checkCallingPermission(p: String) = if (byd(p)) PackageManager.PERMISSION_GRANTED else super.checkCallingPermission(p)
        override fun checkCallingOrSelfPermission(p: String) = if (byd(p)) PackageManager.PERMISSION_GRANTED else super.checkCallingOrSelfPermission(p)
        override fun checkSelfPermission(p: String) = if (byd(p)) PackageManager.PERMISSION_GRANTED else super.checkSelfPermission(p)
        override fun enforcePermission(p: String, pid: Int, uid: Int, m: String?) { if (!byd(p)) super.enforcePermission(p, pid, uid, m) }
        override fun enforceCallingPermission(p: String, m: String?) { if (!byd(p)) super.enforceCallingPermission(p, m) }
        override fun enforceCallingOrSelfPermission(p: String, m: String?) { if (!byd(p)) super.enforceCallingOrSelfPermission(p, m) }
        override fun getPackageName() = "com.byd.dashcast"
        override fun getApplicationContext(): Context = this
    }

    /** System context (ActivityThread) đã bọc bypass — kiểu MapMode dùng. null nếu fail. */
    fun systemBypassContext(): Context? = runCatching {
        exemptHiddenApis()
        val at = Class.forName("android.app.ActivityThread")
        val thread = at.getMethod("currentActivityThread").invoke(null)
        (at.getMethod("getSystemContext").invoke(thread) as? Context)?.let { bypass(it) }
    }.getOrNull()

    /** getInstance(Context) của device qua reflection (thử nhiều ctx: system rồi app, đều bọc bypass). null nếu fail. */
    fun device(fqn: String, vararg ctxs: Context?): Any? {
        for (c in ctxs) {
            val d = runCatching { Class.forName(fqn).getMethod("getInstance", Context::class.java).invoke(null, c) }.getOrNull()
            if (d != null) return d
        }
        return null
    }

    // D2 (closeout 1.28): cache name→id so writeNavFrame's ~20 featureId() lookups/frame stop hitting reflection
    // on the ~4/sec hot path. Mirrors the getterCache pattern below. Behaviour IDENTICAL: same Int, or null when
    // the field is absent (ConcurrentHashMap can't hold null → a separate absent-set records the misses so the
    // reflect+catch runs once per name, not once per frame). Field values are static-final ints → stable.
    private val featureIdCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val featureIdAbsent = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun featureId(name: String): Int? {
        featureIdCache[name]?.let { return it }
        if (name in featureIdAbsent) return null
        val v = runCatching { Class.forName(IDS).getField(name).getInt(null) }.getOrNull()
        if (v != null) featureIdCache[name] = v else featureIdAbsent.add(name)
        return v
    }
    /** Test-only: clear the featureId cache between tests (cache is process-global in the object). */
    internal fun resetFeatureIdCacheForTest() { featureIdCache.clear(); featureIdAbsent.clear() }

    /** Mọi field trong BYDAutoFeatureIds khớp 1 trong [subs] (substring, không phân biệt hoa thường) → (tên, id). */
    fun featureIdsMatching(vararg subs: String): List<Pair<String, Int>> = runCatching {
        Class.forName(IDS).fields
            .filter { f -> subs.any { f.name.contains(it, true) } }
            .mapNotNull { f -> runCatching { f.name to f.getInt(null) }.getOrNull() }
            .sortedBy { it.first }
    }.getOrElse { emptyList() }

    /** Ghi 1 feature int qua set(int[], EventValue). Trả rc; ném nếu không có method set. */
    fun setInt(dev: Any, id: Int, value: Int): Any? = setEv(dev, id) { ev ->
        evField("intValue").setInt(ev, value)
    }

    /** Ghi buffer (vd tên đường UTF-16LE) qua set(int[], EventValue).bufferDataValue. */
    fun setBytes(dev: Any, id: Int, bytes: ByteArray): Any? = setEv(dev, id) { ev ->
        evField("bufferDataValue").set(ev, bytes)
    }

    // D2 (closeout 1.28) reflection-handle cache: the EventValue class/ctor/fields and the device
    // set(int[],EventValue) method are STABLE per class/process. writeNavFrame formerly resolved all of them
    // ~15×/frame (getDeclaredConstructor + getField + a full dev.javaClass.methods scan). Cache once. Behaviour +
    // thrown-exception semantics are identical: a device missing set(int[],EventValue) still throws
    // NoSuchMethodException, and off-car the EV Class.forName still throws (callers already runCatching it).
    private val evClass by lazy { Class.forName(EV) }
    private val evCtor by lazy { evClass.getDeclaredConstructor() }
    private val evFieldCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Field>()
    private fun evField(name: String): java.lang.reflect.Field =
        evFieldCache[name] ?: evClass.getField(name).also { evFieldCache[name] = it }
    private val setMethodCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Method>()
    private fun setMethod(dev: Any): java.lang.reflect.Method {
        setMethodCache[dev.javaClass.name]?.let { return it }
        val set = dev.javaClass.methods.firstOrNull {
            it.name == "set" && it.parameterTypes.size == 2 && it.parameterTypes[0] == IntArray::class.java
        } ?: throw NoSuchMethodException("set(int[], EventValue)")
        setMethodCache[dev.javaClass.name] = set
        return set
    }

    private inline fun setEv(dev: Any, id: Int, fill: (Any) -> Unit): Any? {
        val ev = evCtor.newInstance()
        fill(ev)
        return setMethod(dev).invoke(dev, intArrayOf(id), ev)
    }

    /** Gọi method [name] (0/1 arg int, có thể String) → (ok, "rc=..." / lỗi). Cho probe HAL set/get/hasFeature bất kỳ. */
    fun invokeM(dev: Any, name: String, arg: Any? = null): Pair<Boolean, String> {
        val m = dev.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == (if (arg == null) 0 else 1) &&
                (arg == null ||
                    (arg is Int && it.parameterTypes[0] == Int::class.javaPrimitiveType) ||
                    (arg is String && it.parameterTypes[0] == String::class.java))
        } ?: return false to "no method $name(${arg?.javaClass?.simpleName ?: ""})"
        return runCatching { true to "${if (arg == null) m.invoke(dev) else m.invoke(dev, arg)}" }.getOrElse { false to root(it) }
    }

    /**
     * Gọi method [method] với **N tham số int** qua reflection (`getMethod(method, int×N).invoke(dev, args)`).
     * Trả `"rc=<giá trị trả về>"` khi gọi được (kể cả rc lỗi của HAL, hoặc method void → `"rc=null"`), hoặc
     * chuỗi lỗi rút gọn [root] khi ROM thiếu method / HAL ném. **Degrade-safe: KHÔNG BAO GIỜ ném.**
     *
     * Đây là ĐƯỜNG GHI method-TÊN dùng chung — cho probe HAL on-car (vehicleTest-only),
     * [com.byd.clusternav.body.BodyworkControl] (cửa sổ/cốp) và (tương lai) seat-fix. Các method
     * GHI trên device BYDAuto (`setBodyWindowCtrlState` / `setHetchDoorStatus` / `setSeatVentilatingState`…)
     * KHÔNG có trong SDK jar ⇒ chỉ gọi được qua reflection tên-method, giống cách
     * [com.byd.clusternav.comfort.Pm25FilterApplier] gọi `setAutoCleanAirState` trên AC device.
     */
    fun callNamedInt(dev: Any, method: String, vararg args: Int): String = runCatching {
        val types = Array<Class<*>?>(args.size) { Int::class.javaPrimitiveType }
        val boxed = Array<Any?>(args.size) { args[it] }
        val m = dev.javaClass.getMethod(method, *types)
        "rc=${m.invoke(dev, *boxed)}"
    }.getOrElse { root(it) }

    /** Gọi getter tên [name] (0 hoặc 1 tham số int) qua reflection → chuỗi giá trị. null nếu không có/ném.
     *  ĐÂY là cách đọc THẬT trên ROM này (getCurrentSpeed(), getTyrePressureValue(area)...) — KHÔNG cần listener.
     *  Method cache theo (class#name#arity) → hot-path (steering mỗi tick) khỏi scan getMethods() lại. */
    private val getterCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Method>()
    fun callGetter(dev: Any, name: String, arg: Int? = null): String? {
        val key = "${dev.javaClass.name}#$name#${if (arg == null) 0 else 1}"
        val m = getterCache[key] ?: dev.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == (if (arg == null) 0 else 1) &&
                (arg == null || it.parameterTypes[0] == Int::class.javaPrimitiveType)
        }?.also { getterCache[key] = it } ?: return null
        return runCatching { (if (arg == null) m.invoke(dev) else m.invoke(dev, arg))?.toString() ?: "null" }.getOrNull()
    }

    /** Đọc nhiều getter (tên, arg?) → list "name(arg)=value". Bỏ getter không có. */
    fun readGetters(dev: Any, specs: List<Pair<String, Int?>>): List<String> =
        specs.mapNotNull { (name, arg) -> callGetter(dev, name, arg)?.let { "$name${arg?.let { a -> "($a)" } ?: ""}=$it" } }

    /** Device có get(int[]) đồng bộ không (đa số BYDAuto device KHÔNG — đọc qua listener). */
    fun hasSyncGet(dev: Any): Boolean = dev.javaClass.methods.any {
        it.name == "get" && it.parameterTypes.size == 1 && it.parameterTypes[0] == IntArray::class.java
    }

    /** Thử ĐỌC đồng bộ: nếu device có get(int[]) → gọi, trả kết quả (EventValue[]/EventValue). null nếu không có method/ném. */
    fun tryGet(dev: Any, id: Int): Any? {
        if (!hasSyncGet(dev)) return null
        val get = dev.javaClass.methods.first { it.name == "get" && it.parameterTypes.size == 1 && it.parameterTypes[0] == IntArray::class.java }
        return runCatching { get.invoke(dev, intArrayOf(id)) }.getOrNull()
    }

    /** KIỂM CHỨNG GHI (cho self-test): set 1 feature int → (ok, chi tiết). ok=true nếu set() KHÔNG ném
     *  (bắt được SecurityException/HAL chặn). LƯU Ý: "không ném" mạnh hơn getInstance-non-null nhưng vẫn
     *  chưa chắc cụm render (set có thể trả rc lỗi / no-op âm thầm) → module vẫn bảo "nhìn cụm để chắc". */
    fun writeProbe(dev: Any, featureName: String, value: Int): Pair<Boolean, String> {
        val id = featureId(featureName) ?: return false to "không có feature-id $featureName"
        return runCatching { true to "rc=${setInt(dev, id, value)}" }.getOrElse { false to root(it) }
    }

    /** SET_NAVI_SCREEN_STATUS_SET (0x4C10E015 · BYDAutoSettingDevice) chọn CHẾ ĐỘ hiển thị nav trên cụm =
     *  đúng cái menu OEM "Đơn giản / Màn hình nhỏ / Toàn màn hình / OFF" (mở khoá 2026-08-13). Giá trị:
     *  ⚠️ CHƯA map chắc trên xe — navopen dùng 3 (rc=0, ứng viên "Toàn màn hình"), AmapService reset = 3.
     *  Cần sweep 0/1/2/3 on-car để chốt value = "Đơn giản" (Giữa+ETA). Default 3 = value đã-proven rc=0. */
    const val NAV_SCREEN_MODE_ON = 3

    /** RE 2026-08-15 (insight owner "mũi tên/cự ly/tên đường có tách domestic↔oversea"): CẢ HỌ guidance có bản
     *  OVERSEA (export) song song domestic — suffix id TRÙNG, chỉ khác prefix 0x1F7 (oversea) vs 0x43F (domestic).
     *  Xe export (Seal) đọc họ 0x1F7 → HUD KHÔNG lên gì khi app chỉ ghi 0x43F. Raw-id FALLBACK vì lớp reflect
     *  `BYDAutoFeatureIds` (bản tmap) thiếu các tên oversea. Nguồn: DiCarServer Instrument.java / InstrumentMapper. */
    const val EASY_NAVI_GUIDE_OVERSEA_ID = 0x1F701010   // mũi tên — INSTRUMENT_EASY_NAVI_GUIDE_INFOR_SET (~ GUIDE_INFO_SIMPLE)
    const val CROSSING_DIST_OVERSEA_ID = 0x1F701018     // cự ly  — INSTRUMENT_DISTANCE_TARGET_HEAD_SET (~ FRONT_CROSSING_DISTANCE)
    const val PATHNAME_OVERSEA_ID = 0x1F7A1008          // tên đường — INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_OVERASEA_SET (~ NEXT_PATHNAME)

    // ── TASK 5 (closeout 1.28): per-feature-id in-process rejection cache ─────────────────────────────────
    /**
     * rc mà BYDAuto HAL trả khi feature-id KHÔNG được provision trên trim này = `0x800003E8`
     * = `Int.MIN_VALUE + 1000` = **-2147482648**. Quan sát LẶP LẠI trên xe owner (Seal), KHÔNG suy đoán:
     *   `docs/diagnostics/oncar-runbook-4mode-track-a-probes-2026-08-14.md` — "rc=-2147482648 (REJECTED)";
     *   `docs/diagnostics/app-code-updates-2026-08-16.md` — "rc=-2147482648 + no permission device 1007";
     *   `scripts/vehicle/hud-provisioning-compare.sh` — "-2147482648 = NOT provisioned/no-permission".
     * ⚠ ĐÂY KHÔNG PHẢI `Int.MIN_VALUE` (-2147483648) dù vài tài liệu prose gọi nhầm là "Int.MIN_VALUE" —
     * lệch đúng 1000 (0x3E8). Dùng đúng số đo được, nếu không cache sẽ KHÔNG BAO GIỜ khớp → vô dụng.
     */
    const val NOT_PROVISIONED_RC = -2147482648

    // Feature-id (INT/bytes) đã bị HAL từ chối (rc == NOT_PROVISIONED_RC) → SKIP ở frame sau (hết spam
    // 'no permission device 1007' mỗi frame). In-memory, reset khi process restart. Xe provision oversea
    // (Sealion 6) KHÔNG bao giờ nhận sentinel → không cache → VẪN ghi (KHÔNG hard-remove code oversea).
    private val rejectedFeatures = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    // SDK method (sendSimpleGuidanceInfo/sendNextPathName/sendRestRouteInfo) NÉM lỗi (no-permission hoặc method
    // absent — cả hai đều ỔN ĐỊNH theo process) → skip lần sau. Key = tên method (stable). Sealion 6 chạy OK →
    // không ném → không cache → vẫn gọi.
    private val rejectedSdk = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    internal fun isFeatureRejected(id: Int): Boolean = id in rejectedFeatures
    /** Ghi nhận rc của 1 INT/bytes write; cache [id] nếu rc == [NOT_PROVISIONED_RC]. Trả true nếu vừa cache. */
    internal fun recordFeatureRc(id: Int, rc: Any?): Boolean {
        val rejected = (rc as? Int) == NOT_PROVISIONED_RC
        if (rejected) rejectedFeatures.add(id)
        return rejected
    }
    internal fun isSdkRejected(key: String): Boolean = key in rejectedSdk
    internal fun recordSdkFailure(key: String) { rejectedSdk.add(key) }
    /** Test-only: xả cache giữa các test (cache là process-global trong object). */
    internal fun resetRejectionCacheForTest() { rejectedFeatures.clear(); rejectedSdk.clear() }

    /** setInt qua cache rejection: skip nếu id đã bị từ chối; nếu không → setInt rồi cache khi rc == sentinel. */
    private fun cachedSetInt(dev: Any, id: Int, value: Int): String {
        if (isFeatureRejected(id)) return "skip"
        val r = runCatching { setInt(dev, id, value) }.getOrElse { return root(it) }
        recordFeatureRc(id, r)
        return "$r"
    }
    /** setBytes qua cache rejection (chung set rejectedFeatures theo id — kill spam tên-đường oversea trên owner). */
    private fun cachedSetBytes(dev: Any, id: Int, bytes: ByteArray): String {
        if (isFeatureRejected(id)) return "skip"
        val r = runCatching { setBytes(dev, id, bytes) }.getOrElse { return root(it) }
        recordFeatureRc(id, r)
        return "$r"
    }
    /** Gọi 1 SDK method qua cache rejection: skip nếu đã ném trước đó; nếu không → gọi và cache khi ném. */
    private fun cachedSdk(key: String, rc: StringBuilder, tag: String, block: () -> Any?) {
        if (isSdkRejected(key)) { rc.append(" $tag=skip"); return }
        runCatching { block() }
            .onSuccess { rc.append(" $tag=$it") }
            .onFailure { e -> rc.append(" $tag!=").append(root(e)); recordSdkFailure(key) }
    }

    // D2 (closeout 1.28): cache the OEM SDK method handles (sendSimpleGuidanceInfo / sendNextPathName /
    // sendRestRouteInfo) resolved via getMethod on the real-push path. Keyed by device-class#method (param types
    // are fixed per name here). getMethod still THROWS NoSuchMethodException when the method is absent → cachedSdk's
    // runCatching records the SDK-rejection (TASK 5) exactly as before → skipped next frame. On Sealion 6 (methods
    // present) the handle is resolved once then reused instead of scanned every real push.
    private val sdkMethodCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Method>()
    private fun sdkMethod(dev: Any, name: String, vararg params: Class<*>?): java.lang.reflect.Method {
        val key = "${dev.javaClass.name}#$name"
        return sdkMethodCache[key] ?: dev.javaClass.getMethod(name, *params).also { sdkMethodCache[key] = it }
    }

    /** Ghi 1 frame nav IN-PROCESS lên cụm (status=2 + chọn mode nav-screen + icon/khoảng-cách/tên-đường) qua
     *  bypass-context. Đây là CƠ CHẾ THẬT tạo "Giữa + ETA" (khớp navopen), KHÁC ch1000 op39 (no-op trên xe này).
     *  [screenMode] = giá trị SET_NAVI_SCREEN_STATUS_SET (xem [NAV_SCREEN_MODE_ON]). Trả tóm tắt rc.
     *  Owner hợp lệ DUY NHẤT: [com.byd.clusternav.NavigationHudOwner] (giữ ownership boundary — xem PhysicalHudOwnershipTest). */
    fun writeNavFrame(
        ctx: Context, icon: Int, segMeters: Int, road: String, screenMode: Int = NAV_SCREEN_MODE_ON,
        routeSeconds: Int = -1, routeMeters: Int = -1, arrivalClock: String? = null,
        keepAlive: Boolean = false, writeSurface: Boolean = true,
    ): String {
        val sys = systemBypassContext()
        val instr = device(INSTRUMENT, sys, bypass(ctx)) ?: return "InstrumentDevice null (không ghi được)"
        val setting = device(SETTING, sys, bypass(ctx))
        val rc = StringBuilder()
        // TASK 5: mọi INT write đi qua cachedSetInt (skip id đã bị HAL từ chối; cache khi gặp sentinel not-provisioned).
        fun w(name: String, v: Int) { featureId(name)?.let { id -> rc.append(" $name=").append(cachedSetInt(instr, id, v)) } }
        // Ghi 1 feature INT theo TÊN (reflect BYDAutoFeatureIds), FALLBACK raw-id cho các tên OVERSEA vắng trong lớp reflect.
        fun wi(name: String, rawId: Int, v: Int, tag: String) {
            (featureId(name) ?: rawId).let { id -> rc.append(" $tag=").append(cachedSetInt(instr, id, v)) }
        }
        // TASK 2: status + screen-mode là cờ SESSION LATCH → CHỈ ghi lúc real push; BỎ ở keep-alive (đỡ churn ~4×/s).
        // TÁCH NỘI DUNG / BỀ MẶT (2026-08-24, docs/diagnostics/nav-io-asis-2026-08-24.html §B, owner OQ1/OQ4):
        //  • SEND_NAVI_STATUS = latch "đang dẫn", thuộc NỘI DUNG (HUD kính đọc) ⇒ ghi theo real-push, KHÔNG gate writeSurface.
        //  • SET_NAVI_SCREEN_STATUS = dựng BỀ MẶT nav giữa CỤM (tranh display cụm với Cast) ⇒ chỉ ghi khi writeSurface
        //    (caller truyền = navOnlyMode: Cast OFF). Cast ON ⇒ writeSurface=false ⇒ chỉ nội dung lên HUD, KHÔNG dựng cụm.
        if (!keepAlive) w("INSTRUMENT_SEND_NAVI_STATUS_SET", 2)
        if (!keepAlive && writeSurface) featureId("SET_NAVI_SCREEN_STATUS_SET")?.let { id -> setting?.let { s -> rc.append(" NAVI_SCREEN=").append(cachedSetInt(s, id, screenMode)) } }
        // CONTENT (LUÔN ghi, kể cả keep-alive): guidance icon + dualIcon + cự ly + tên đường.
        w("INSTRUMENT_GUIDE_INFO_SIMPLE_SET", icon)
        w("INSTRUMENT_GUIDE_INFO_AND_ROAD_AHEAD_DISTANCE_SET", icon)   // OpenBYD "dualIcon" (0x43F01030) — ghi icon vào cả feature này
        w("INSTRUMENT_FRONT_CROSSING_DISTANCE_SET", segMeters)
        featureId("INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET")?.let { id -> rc.append(" PATHNAME=").append(cachedSetBytes(instr, id, road.toByteArray(Charsets.UTF_16LE))) }
        // THỬ NGHIỆM (2026-08-15, insight owner): guidance có bản OVERSEA (export) song song domestic (suffix id trùng,
        // prefix 0x1F7 vs 0x43F). Xe export (Seal) đọc họ 0x1F7 → HUD không lên GÌ khi app chỉ ghi 0x43F. Ghi THÊM
        // bản oversea cho mũi tên + cự ly + tên đường (KHÔNG bỏ domestic). Feature hiển thị → ghi lành tính; null-safe;
        // fallback raw-id (BYDAutoFeatureIds thiếu tên oversea). Xác nhận trên xe: HUD export lên mũi tên/cự ly/tên chưa.
        (featureId("INSTRUMENT_EASY_NAVI_GUIDE_INFOR_SET") ?: EASY_NAVI_GUIDE_OVERSEA_ID).let { id ->
            rc.append(" GUIDE_OVERSEA=").append(cachedSetInt(instr, id, icon))
        }
        (featureId("INSTRUMENT_DISTANCE_TARGET_HEAD_SET") ?: CROSSING_DIST_OVERSEA_ID).let { id ->
            rc.append(" DIST_OVERSEA=").append(cachedSetInt(instr, id, segMeters))
        }
        (featureId("INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_OVERASEA_SET") ?: PATHNAME_OVERSEA_ID).let { id ->
            rc.append(" PATHNAME_OVERSEA=").append(cachedSetBytes(instr, id, road.toByteArray(Charsets.UTF_16LE)))
        }
        // FULL DATA HUD (2026-08-15, owner "ghi hết data lên HUD, domestic + oversea"): thời-gian-còn-lại (giờ/phút/
        // giây/ngày), quãng-đường-còn-lại, giờ-tới (ETA) — CHƯA từng ghi lên HUD (trước chỉ vào CỤM qua broadcast).
        // Ghi vào CẢ 2 họ; CHỈ khi có giá trị hợp lệ (bỏ qua lúc keep-alive/absent để không xoá trắng số đang hiện).
        // Toàn feature HIỂN THỊ (không phải switch) → lành tính. Id: DiCarServer Instrument.java (0x43F dom / 0x1F7 oversea).
        if (routeSeconds >= 0) {
            val d = routeSeconds / 86400; val h = (routeSeconds % 86400) / 3600
            val m = (routeSeconds % 3600) / 60; val s = routeSeconds % 60
            wi("INSTRUMENT_NAVI_TRIP_INFO_HOUR_SET", 0x43F02010, h, "RT_H");   wi("INSTRUMENT_REMAIN_DRIVE_TIME_HOUR_SET", 0x1F702010, h, "RT_HO")
            wi("INSTRUMENT_NAVI_TRIP_INFO_MINUTE_SET", 0x43F02018, m, "RT_M"); wi("INSTRUMENT_REMAIN_DRIVE_TIME_MINUTE_SET", 0x1F702018, m, "RT_MO")
            wi("INSTRUMENT_NAVI_TRIP_REMAINING_SECOND_SET", 0x43F0201E, s, "RT_S"); wi("INSTRUMENT_REMAIN_DRIVE_TIME_SECOND_SET", 0x1F70201E, s, "RT_SO")
            wi("INSTRUMENT_REMAIN_DRIVING_TIME_DAY_SET", 0x43F02024, d, "RT_D")   // day: domestic only (oversea không có ô ngày riêng)
        }
        if (routeMeters >= 0) {
            wi("INSTRUMENT_NAVI_TRIP_INFO_MILEAGE_SET", 0x43F02028, routeMeters, "MILE"); wi("INSTRUMENT_REMAIN_MILEAGE_SET", 0x1F702028, routeMeters, "MILE_O")
        }
        arrivalClock?.split(":")?.let { p ->
            p.getOrNull(0)?.trim()?.toIntOrNull()?.let { h ->
                wi("INSTRUMENT_EXPECTED_ARRIVE_HOUR_SET", 0x43F09018, h, "ETA_H"); wi("INSTRUMENT_EXPECT_ARRIVAL_TIME_HOUR_SET", 0x1F705018, h, "ETA_HO")
            }
            p.getOrNull(1)?.trim()?.toIntOrNull()?.let { m ->
                wi("INSTRUMENT_EXPECTED_ARRIVE_MINUTE_SET", 0x43F09020, m, "ETA_M"); wi("INSTRUMENT_EXPECT_ARRIVAL_TIME_MINUTE_SET", 0x1F705020, m, "ETA_MO")
            }
        }
        // 1.26 — GỌI THÊM OEM SDK method của BYDAutoInstrumentDevice (như OpenBYD CarControlImpl): ngoài ghi raw
        // feature, gọi thẳng method native. GIẢ THUYẾT: method SDK mới là cái bật TÊN ĐƯỜNG + guidance lên HUD kính
        // (raw feature chỉ tới cụm-centre). Reflect + invoke trên instr; null-safe (method vắng/lỗi → bỏ qua, log rc).
        // TASK 2: 3 SDK call BỎ ở keep-alive (chỉ real push). TASK 5: cachedSdk cache theo tên method — ném lỗi lần
        // đầu (no-permission / method absent, cả hai ỔN ĐỊNH theo process) → skip lần sau (hết spam 'no permission
        // device 1007'). Xe provision oversea (Sealion 6) SDK chạy OK → không ném → không cache → vẫn gọi.
        if (!keepAlive) {
            cachedSdk("sendSimpleGuidanceInfo", rc, "sdk.guide") {
                sdkMethod(instr, "sendSimpleGuidanceInfo", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(instr, icon, segMeters)
            }
            if (road.isNotBlank()) cachedSdk("sendNextPathName", rc, "sdk.road") {
                sdkMethod(instr, "sendNextPathName", String::class.java).invoke(instr, road)
            }
            if (routeSeconds >= 0 && routeMeters >= 0) cachedSdk("sendRestRouteInfo", rc, "sdk.rest") {
                sdkMethod(instr, "sendRestRouteInfo", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                    .invoke(instr, routeSeconds / 3600, (routeSeconds % 3600) / 60, routeMeters.toLong())
            }
        }
        return rc.toString().trim()
    }

    /** TẮT nav HUD (status=4 + clear guide/dist) khi hết dẫn đường — như DashCast setNaviActive(false). */
    fun clearNavFrame(ctx: Context): String {
        val instr = device(INSTRUMENT, systemBypassContext(), bypass(ctx)) ?: return "InstrumentDevice null"
        val rc = StringBuilder()
        fun w(name: String, v: Int) { featureId(name)?.let { id -> rc.append(" $name=").append(runCatching { setInt(instr, id, v) }.getOrElse { root(it) }) } }
        w("INSTRUMENT_SEND_NAVI_STATUS_SET", 4)
        w("INSTRUMENT_GUIDE_INFO_SIMPLE_SET", 0)
        w("INSTRUMENT_FRONT_CROSSING_DISTANCE_SET", -1)
        return rc.toString().trim()
    }

    // ── B3 T4 (spec b3-full-nav-capture §R3/§R4/§R5): payload gốc cho nguồn ẢNH (NavOutputOwner) ──────────
    // 3 method THÊM (additive) — KHÔNG đổi hành vi [writeNavFrame]. "CỨ BẮN đủ field" (R3a/OQ4): ghi thẳng
    // register, HUD/cụm hỗ trợ thì lên, không thì kệ (cache reject nuốt spam). Tất cả CONTENT-only: KHÔNG chạm
    // session latch (SEND_NAVI_STATUS / SET_NAVI_SCREEN_STATUS / 3 SDK) — latch vẫn là ĐỘC QUYỀN của
    // [com.byd.clusternav.NavigationHudOwner] (PhysicalHudOwnershipTest: chỉ owner đó gọi writeNavFrame). Nguồn ẢNH
    // chỉ lên khi kênh DATA im (SourceArbiter IMAGE<DATA) nên hai owner KHÔNG cùng ghi. Nhận [instr] đã-resolve từ
    // owner (device() cache handle reflect). Mọi write qua cachedSetInt/cachedSetBytes (skip id đã bị HAL từ chối).

    // Register làn cụm (DiCarServer). Lane 1: arrow=0x19802058, recommended=0x19802064; bước mỗi làn = 0x10.
    // ⚠ ON-CAR-VERIFY (OQ3): id làn 2..8 SUY từ bước 0x10 (chỉ lane-1 có bằng chứng trong spec); ưu tiên tên
    // reflect nếu ROM có, fallback raw-id (giống lối oversea của writeNavFrame). Trim không provision → overlay
    // tự vẽ (T5) là đường SONG SONG, không phải fallback.
    const val LANE_1_GUIDANCE_ARROW_ID = 0x19802058
    const val LANE_1_RECOMMENDED_ID = 0x19802064
    const val LANE_ID_STRIDE = 0x10
    const val MAX_CLUSTER_LANES = 8
    // Camera (0x43F03* domestic — RE docs/_handoff/re-hud-track4-hal-can.md Phụ lục A).
    const val GUIDE_INFO_CAMERA_ID = 0x43F03010
    const val CAMERA_DISPLAY_STATE_ID = 0x43F03018
    const val NAVI_CAM_REMAINING_MILEAGE_ID = 0x43F0301C

    /** THÊM (B3 T4): đẩy CONTENT mũi tên + cự-ly + tên-đường vào cụm-centre + HUD (domestic 0x43F + oversea 0x1F7),
     *  KHÔNG chạm session latch/SDK (đó là NavigationHudOwner). [segMeters] < 0 → bỏ ghi cự-ly (không xoá trắng số
     *  đang hiện); [road] null/blank → bỏ ghi tên. Degrade-safe qua cachedSetInt/cachedSetBytes. */
    fun pushNavigation(instr: Any, icon: Int, segMeters: Int = -1, road: String? = null): String {
        val rc = StringBuilder()
        fun w(name: String, v: Int) { featureId(name)?.let { id -> rc.append(" $name=").append(cachedSetInt(instr, id, v)) } }
        w("INSTRUMENT_GUIDE_INFO_SIMPLE_SET", icon)
        w("INSTRUMENT_GUIDE_INFO_AND_ROAD_AHEAD_DISTANCE_SET", icon)   // OpenBYD dualIcon (0x43F01030)
        (featureId("INSTRUMENT_EASY_NAVI_GUIDE_INFOR_SET") ?: EASY_NAVI_GUIDE_OVERSEA_ID).let { id ->
            rc.append(" GUIDE_OVERSEA=").append(cachedSetInt(instr, id, icon))
        }
        if (segMeters >= 0) {
            w("INSTRUMENT_FRONT_CROSSING_DISTANCE_SET", segMeters)
            (featureId("INSTRUMENT_DISTANCE_TARGET_HEAD_SET") ?: CROSSING_DIST_OVERSEA_ID).let { id ->
                rc.append(" DIST_OVERSEA=").append(cachedSetInt(instr, id, segMeters))
            }
        }
        if (!road.isNullOrBlank()) {
            val bytes = road.toByteArray(Charsets.UTF_16LE)
            featureId("INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET")?.let { id -> rc.append(" PATHNAME=").append(cachedSetBytes(instr, id, bytes)) }
            (featureId("INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_OVERASEA_SET") ?: PATHNAME_OVERSEA_ID).let { id ->
                rc.append(" PATHNAME_OVERSEA=").append(cachedSetBytes(instr, id, bytes))
            }
        }
        return rc.toString().trim()
    }

    /**
     * THÊM (B-III, 2026-08-22): **XOÁ TRẮNG ô cự-ly** trên cụm/HUD, giữ nguyên mọi thứ khác.
     *
     * VÌ SAO cần một hàm riêng thay vì "cứ ghi -1 qua [pushNavigation]": [pushNavigation] coi `segMeters < 0` là
     * "BỎ GHI, giữ số cũ" (nhánh `if (segMeters >= 0)` ngay trên). Nên khi [com.byd.clusternav.NavOutputOwner]
     * mất tin vào cự-ly (guard [com.byd.clusternav.navigation.TurnDistancePlausibility] chưa warmup xong / vừa
     * đổi nguồn), nếu chỉ truyền -1 thì **số của nguồn CŨ nằm lại trên cụm** — tệ hơn hiện trạng.
     *
     * ⚠ MỨC BẰNG CHỨNG (CLAUDE.md §2) — ĐỌC KỸ, ĐỪNG THĂNG HẠNG:
     *  • **ĐÃ CHỨNG MINH**: `clearNavFrame` (ở trên, cùng file) ghi -1 vào ĐÚNG feature id này và chạy tốt
     *    trên xe hôm nay.
     *  • **CHƯA BIẾT**: `clearNavFrame` ghi -1 **kèm `INSTRUMENT_SEND_NAVI_STATUS_SET = 4` trong cùng lời
     *    gọi**, tức cụm ẨN HẲN widget nav nên chưa bao giờ phải *render* số -1. Hàm này cố ý KHÔNG chạm latch,
     *    nên đây là lần đầu -1 được ghi vào ô cự-ly **trong khi widget đang hiện**. Firmware coi <0 là "ẩn ô"
     *    hay render raw (`-1` / `0xFFFFFFFF`) thì **chưa probe on-car** — OQ13 trong spec.
     *  • Vì vậy KHÔNG được nói "xấu nhất là no-op ⇒ không bao giờ tệ hơn hiện trạng" (câu đó đã bị gỡ khỏi
     *    KDoc này 08-22 vòng 1): nếu cụm render raw thì tài xế thấy một con số BỊA, tệ hơn hẳn "giữ số cũ".
     *    Phải chạy probe OQ13 TRƯỚC khi ship đường này ra xe thật.
     *
     * CONTENT-only: KHÔNG chạm session latch (SEND_NAVI_STATUS / SET_NAVI_SCREEN_STATUS / SDK — độc quyền của
     * [com.byd.clusternav.NavigationHudOwner]) và KHÔNG chạm icon (mũi tên vẫn phải hiện: hướng còn đáng tin,
     * chỉ cự-ly là không).
     */
    fun blankNavDistance(instr: Any): String {
        val rc = StringBuilder()
        featureId("INSTRUMENT_FRONT_CROSSING_DISTANCE_SET")?.let { id ->
            rc.append(" FRONT_CROSSING=").append(cachedSetInt(instr, id, NAV_DISTANCE_BLANK))
        }
        (featureId("INSTRUMENT_DISTANCE_TARGET_HEAD_SET") ?: CROSSING_DIST_OVERSEA_ID).let { id ->
            rc.append(" DIST_OVERSEA=").append(cachedSetInt(instr, id, NAV_DISTANCE_BLANK))
        }
        return rc.toString().trim()
    }

    /** Giá trị "không có cự-ly" mà cụm/HUD hiểu là xoá trắng ô — cùng giá trị `clearNavFrame` đang dùng. */
    const val NAV_DISTANCE_BLANK = -1

    /** THÊM (B3 T4): đẩy dải làn vào register cụm — mỗi làn: LANE_n_GUIDANCE_ARROW_SET (mã hướng qua
     *  [NavOutputDecision.laneArrowCode]) + IS_LANE_n_RECOMMENDED_SET (1 sáng / 0 mờ, R4). Tối đa
     *  [MAX_CLUSTER_LANES]. Degrade-safe; làn rỗng → no-op. */
    fun pushLane(instr: Any, info: LaneInfo): String {
        if (info.isEmpty()) return ""
        val rc = StringBuilder()
        val n = minOf(info.count, MAX_CLUSTER_LANES)
        for (i in 0 until n) {
            val lane = info.lanes[i]
            val laneNo = i + 1
            val arrowId = featureId("INSTRUMENT_LANE_${laneNo}_GUIDANCE_ARROW_SET") ?: (LANE_1_GUIDANCE_ARROW_ID + i * LANE_ID_STRIDE)
            val recId = featureId("IS_LANE_${laneNo}_RECOMMENDED_SET") ?: (LANE_1_RECOMMENDED_ID + i * LANE_ID_STRIDE)
            rc.append(" L$laneNo=").append(cachedSetInt(instr, arrowId, NavOutputDecision.laneArrowCode(lane.arrows)))
            rc.append(" L${laneNo}R=").append(cachedSetInt(instr, recId, if (lane.recommended) 1 else 0))
        }
        return rc.toString().trim()
    }

    /** THÊM (B3 T4): đẩy icon camera + cự-ly (R5a) — INSTRUMENT_GUIDE_INFO_CAMERA_SET (0x43F03010) + display-state
     *  + remaining-mileage. [iconCode] 0 = tắt icon; [distanceMeters] < 0 → bỏ ghi cự-ly. Degrade-safe. */
    fun pushCamera(instr: Any, iconCode: Int, distanceMeters: Int = -1): String {
        val rc = StringBuilder()
        (featureId("INSTRUMENT_GUIDE_INFO_CAMERA_SET") ?: GUIDE_INFO_CAMERA_ID).let { id ->
            rc.append(" CAM=").append(cachedSetInt(instr, id, iconCode))
        }
        (featureId("INSTRUMENT_CAMERA_DISPLAY_STATE_SET") ?: CAMERA_DISPLAY_STATE_ID).let { id ->
            rc.append(" CAM_STATE=").append(cachedSetInt(instr, id, if (iconCode != 0) 1 else 0))
        }
        if (distanceMeters >= 0) (featureId("INSTRUMENT_NAVI_CAM_REMAINING_MILEAGE_SET") ?: NAVI_CAM_REMAINING_MILEAGE_ID).let { id ->
            rc.append(" CAM_DIST=").append(cachedSetInt(instr, id, distanceMeters))
        }
        return rc.toString().trim()
    }

    /** Đọc đồng bộ feature đầu tiên ra giá trị (cho self-test read). null nếu không đọc được cái nào. */
    fun firstReadable(dev: Any, ids: List<Pair<String, Int>>): Pair<String, String>? {
        for ((n, id) in ids) {
            val r = tryGet(dev, id) ?: continue
            return n to readValue(r)
        }
        return null
    }

    /** Rút giá trị đọc được từ kết quả get() (EventValue hoặc mảng) → chuỗi int/float/buffer. */
    fun readValue(result: Any?): String {
        if (result == null) return "null"
        val item = if (result.javaClass.isArray && RArray.getLength(result) > 0) RArray.get(result, 0) else result
        if (item == null) return "null(empty)"
        val i = runCatching { item.javaClass.getField("intValue").getInt(item) }.getOrNull()
        val f = runCatching { item.javaClass.getField("floatValue").getFloat(item) }.getOrNull()
        val buf = runCatching { (item.javaClass.getField("bufferDataValue").get(item) as? ByteArray)?.size }.getOrNull()
        return "int=$i float=$f buf=${buf ?: "-"}"
    }

    /** Liệt kê method (lọc theo tiền tố) để PROBE API thật trên ROM (vd "get","set","register","on"). */
    fun methods(dev: Any, vararg prefixes: String): List<String> =
        dev.javaClass.methods
            .filter { m -> prefixes.isEmpty() || prefixes.any { m.name.startsWith(it) } }
            .map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
            .distinct().sorted()

    fun exemptHiddenApis() {
        runCatching {
            val vm = Class.forName("dalvik.system.VMRuntime")
            val rt = vm.getMethod("getRuntime").invoke(null)
            vm.getMethod("setHiddenApiExemptions", Array<String>::class.java).invoke(rt, arrayOf("L") as Any)
        }
    }

    fun root(t: Throwable): String {
        var c: Throwable = t
        while (c.cause != null && c.cause !== c) c = c.cause!!
        return "${c.javaClass.simpleName}: ${c.message}"
    }

}
