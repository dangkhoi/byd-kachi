package com.byd.clusternav.modules.hal

import com.byd.clusternav.modules.hal.BydHal.EV_INVALID_FLOAT
import com.byd.clusternav.modules.hal.BydHal.EV_INVALID_INT
import java.lang.reflect.Array as RArray

/**
 * ═══ VAI "ĐỌC qua reflection" của [BydHal]: getter theo TÊN + feature-id ĐỒNG BỘ (`get(int[], Class)`) ═══════════
 *
 * Tách khỏi `BydHal.kt` (666 dòng → trần 500, CLAUDE.md §4.1) theo VAI, đúng khuôn `DEBT-500`
 * (`HalBindingTable`→`HalReadTables`). [BydHal] giữ nguyên MẶT TIỀN — mọi call site cũ (`BydHal.callGetter` ·
 * `BydHal.readFeature` · `BydHal.tryGet` · `BydHal.hasSyncGet` · `BydHal.firstReadable` …) vẫn gọi y nguyên và uỷ
 * quyền một dòng xuống đây; không đổi chữ ký, không đổi giá trị trả về, không đổi ngoại lệ.
 *
 * ⚠ ĐÂY LÀ REFLECTION HAL — từng hàm chép NGUYÊN VĂN từ `BydHal.kt`, KHÔNG sửa một chuỗi tên method/field nào
 * (`intValue` · `floatValue` · `bufferDataValue` · `get`). Cache method (`getterCache` · `getMethodCache`) vẫn là
 * process-global trong object, đúng như trước. Mức bằng chứng của từng dòng KDoc (`[ĐO …]`) giữ nguyên nguồn.
 *
 * Ở lại `BydHal.kt`: bypass-context/device (resolve), đường GHI (`setInt`/`setBytes`/`callNamedInt`/`invokeM`),
 * feature-id theo tên + cache từ chối, `writeNavFrame`/`clearNavFrame` (độc quyền `NavigationHudOwner` —
 * `PhysicalHudOwnershipTest`), `root`. Đường đẩy CONTENT B3 T4 ở [BydHalContentPush].
 */
object BydHalRead {
    /** Gọi getter tên [name] (0 hoặc 1 tham số int) qua reflection → chuỗi giá trị. null nếu không có/ném.
     *  ĐÂY là cách đọc THẬT trên ROM này (getCurrentSpeed(), getTyrePressureValue(area)...) — KHÔNG cần listener.
     *  Method cache theo (class#name#arity) → hot-path (steering mỗi tick) khỏi scan getMethods() lại.
     *
     *  Kết quả là MẢNG (`int[]`/`float[]`/`byte[]`/`Object[]`) → [arrayToStr], KHÔNG `toString()` (§B remediation
     *  2026-09-15: `int[].toString()` = `"[I@hash"` → `coerceInt` null → UI "—" cho `getPM2p5Level/Value` [ĐO
     *  `BYDAutoPM2p5Device.java:84,92` trả `int[]`], `getAllRadarProbeStates` [`BYDAutoRadarDevice.java:64`]). */
    private val getterCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Method>()
    /**
     * @param onError hardening 2026-09-25 (audit F5): NGUYÊN NHÂN của `null` (ROM thiếu method ⇒
     *   `NoSuchMethodException`; HAL ném ⇒ ngoại lệ gốc) được đưa ra seam này để gateway log-once. Giá trị trả về
     *   KHÔNG đổi — `null` vẫn là `null`; mặc định `null` = mọi call site cũ y nguyên.
     */
    fun callGetter(dev: Any, name: String, arg: Int? = null, onError: ((Throwable) -> Unit)? = null): String? {
        val arity = if (arg == null) 0 else 1
        val key = "${dev.javaClass.name}#$name#$arity"
        val m = getterCache[key] ?: dev.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == arity &&
                (arg == null || it.parameterTypes[0] == Int::class.javaPrimitiveType)
        }?.also { getterCache[key] = it } ?: run {
            onError?.invoke(NoSuchMethodException("${dev.javaClass.simpleName}.$name/$arity"))
            return null
        }
        return runCatching {
            val r = if (arg == null) m.invoke(dev) else m.invoke(dev, arg)
            when {
                r == null -> "null"
                r.javaClass.isArray -> arrayToStr(r)
                else -> r.toString()
            }
        }.getOrElse { onError?.invoke(it); null }
    }

    /**
     * Mảng (bất kỳ kiểu phần tử, qua `java.lang.reflect.Array`) → chuỗi ĐỌC ĐƯỢC cho tầng parse ở :core:
     *  • **1 phần tử** → chỉ chuỗi phần tử đó (`"3"`), để `HalBindingTable.coerceInt` đọc thẳng — đây là dạng
     *    ưu tiên cho các getter "mảng bọc 1 số" như `getPM2p5Level()[0]` (docs/diagnostics/byd-pm25-airclean-RE-2026-09-04.md).
     *  • **≥2 phần tử** → `"[a, b, c]"` (khớp `Arrays.toString`) — KHÔNG cắt còn `[0]` vì sẽ mất dữ liệu của
     *    consumer danh sách; `HalBindingTable.coerceInt` tự lấy phần tử đầu khi ô chỉ cần một số.
     *  • **rỗng** → `"[]"`.
     * ✔ `HalBindingTable.coerceInt`/`coerceDouble` (:core) ĐÃ lấy phần tử đầu của `"[a, b]"` (hàm `firstOfArray`),
     * nên getter mảng ≥2 phần tử vẫn ra scalar đúng cho ô cần số.
     *
     * Phần tử `null` (mảng `Object[]`) → chuỗi `"null"`, KHÔNG ném: một ô rỗng không được làm mất cả lượt đọc.
     */
    fun arrayToStr(arr: Any): String {
        val n = RArray.getLength(arr)
        return when (n) {
            0 -> "[]"
            1 -> RArray.get(arr, 0)?.toString() ?: "null"
            else -> (0 until n).joinToString(", ", "[", "]") { RArray.get(arr, it)?.toString() ?: "null" }
        }
    }

    /** Đọc nhiều getter (tên, arg?) → list "name(arg)=value". Bỏ getter không có. */
    fun readGetters(dev: Any, specs: List<Pair<String, Int?>>): List<String> =
        specs.mapNotNull { (name, arg) -> callGetter(dev, name, arg)?.let { "$name${arg?.let { a -> "($a)" } ?: ""}=$it" } }

    // ── ĐỌC feature-id đồng bộ (§A remediation 2026-09-15) ────────────────────────────────────────────
    // [ĐO từ source] API đọc feature THẬT của mọi device BYDAuto là **`get(int[] ids, Class<?> type)` 2-arg**
    // (`../jadx-tmap/sources/android/hardware/bydauto/AbsBYDAutoDevice.java:84`) trả `BYDAutoEventValue`; app chạy
    // được (OpenBYD) gọi `dev.get(new int[]{id}, Integer.TYPE).intValue`
    // (`../jadx-openbyd/sources/com/sr/openbyd/proxy/CarControlImpl.java:239-240`). Bản cũ dò `get(int[])` 1-arg —
    // KHÔNG TỒN TẠI → hasSyncGet=false cho MỌI device → mọi telemetry route Feature = "—".
    private val getMethodCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Method>()
    private fun getMethodOrNull(dev: Any): java.lang.reflect.Method? {
        getMethodCache[dev.javaClass.name]?.let { return it }
        return dev.javaClass.methods.firstOrNull {
            it.name == "get" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == IntArray::class.java && it.parameterTypes[1] == Class::class.java
        }?.also { getMethodCache[dev.javaClass.name] = it }
    }

    /** Device có `get(int[], Class)` đồng bộ không (`AbsBYDAutoDevice.java:84` — mọi device kế thừa đều có). */
    fun hasSyncGet(dev: Any): Boolean = getMethodOrNull(dev) != null

    /**
     * Thử ĐỌC đồng bộ 1 feature-id: `dev.get(intArrayOf(id), [type])` → trả object `BYDAutoEventValue` (đọc field
     * qua [readValue]/[readFeature]). [type] = `Integer.TYPE` (mặc định — cách OpenBYD đọc) hoặc `Float.TYPE`.
     * **Degrade-safe:** null nếu device không có method 2-arg / HAL ném / HAL trả null.
     */
    fun tryGet(dev: Any, id: Int, type: Class<*> = Integer.TYPE, onError: ((Throwable) -> Unit)? = null): Any? {
        val get = getMethodOrNull(dev) ?: run {
            onError?.invoke(NoSuchMethodException("${dev.javaClass.simpleName}.get(int[], Class)"))
            return null
        }
        return runCatching { get.invoke(dev, intArrayOf(id), type) }.getOrElse { onError?.invoke(it); null }
    }

    /**
     * ĐỌC 1 feature-id ra chuỗi `"int=<n> float=<f> buf=<len|->"` ([readValue]) cho tầng parse :core
     * (`HalBindingTable.coerceInt` đọc `int=`, `coerceDouble` đọc `float=`). Đây là đường thuần (không Context)
     * mà [com.byd.clusternav.launcher.BydHalGateway.featureGet] bọc — test off-car được bằng fake device.
     * null khi: không có `get` 2-arg · HAL ném/trả null · object trả về không có field EventValue nào ·
     * cả `intValue`/`floatValue` đều là sentinel [EV_INVALID_INT]/[EV_INVALID_FLOAT] và không có buffer.
     *
     * ⚠ [SOÁT 2026-09-15 · P1] Ô sentinel bị **rút khỏi chuỗi** (`int=-` / `float=-`), KHÔNG in số thô như
     * [readValue]. Lý do: `BYDAutoEventValue` khởi tạo CẢ HAI field bằng sentinel, nên một feature kiểu float hợp lệ
     * vẫn mang `intValue = -999999999`. In nguyên thì `HalBindingTable.coerceInt` (ưu tiên `int=`) đọc ra
     * **-999999999** và ô hiện một con số BỊA — đúng cái bệnh "số vô nghĩa" mà §A sinh ra để chữa (bộ lọc sentinel
     * của :core chỉ biết rc `-2147482648/-2147482645`, KHÔNG biết sentinel EventValue). Ô bị rút không khớp regex
     * `int=(-?\d+)` / `float=(-?[0-9.]+)` ⇒ :core tự lùi sang ô còn lại.
     */
    fun readFeature(dev: Any, id: Int, type: Class<*> = Integer.TYPE, onError: ((Throwable) -> Unit)? = null): String? {
        val ev = tryGet(dev, id, type, onError) ?: return null
        val item = if (ev.javaClass.isArray) (if (RArray.getLength(ev) > 0) RArray.get(ev, 0) else null) else ev
        if (item == null) return null
        val i = runCatching { item.javaClass.getField("intValue").getInt(item) }.getOrNull()
        val f = runCatching { item.javaClass.getField("floatValue").getFloat(item) }.getOrNull()
        val buf = runCatching { item.javaClass.getField("bufferDataValue").get(item) as? ByteArray }.getOrNull()
        if (i == null && f == null && buf == null) return null
        val intOk = i != null && i != EV_INVALID_INT
        val floatOk = f != null && f != EV_INVALID_FLOAT
        if (!intOk && !floatOk && buf == null) return null
        return "int=${if (intOk) i.toString() else "-"}" +
            " float=${if (floatOk) f.toString() else "-"}" +
            " buf=${buf?.size ?: "-"}"
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
}
