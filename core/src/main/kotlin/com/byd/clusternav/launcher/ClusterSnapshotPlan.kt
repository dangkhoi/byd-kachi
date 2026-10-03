package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral

/**
 * Kiểu giá trị mà `SharedPreferences` phân biệt ở tầng ĐỌC. `getBoolean` trên một giá trị ghi bằng `putString`
 * **ném** `ClassCastException` — và chỗ đọc là dịch vụ đang chạy trên xe (xem KDoc [PrefSnapshot]).
 */
enum class PrefType {
    BOOLEAN, INT, LONG, FLOAT, STRING, STRING_SET;

    companion object {
        /** Kiểu của [v]; `null` = `null` hoặc kiểu lạ (không phải một trong sáu kiểu prefs biết). */
        fun of(v: Any?): PrefType? = when (v) {
            is Boolean -> BOOLEAN
            is Int -> INT
            is Long -> LONG
            is Float -> FLOAT
            is String -> STRING
            is Set<*> -> if (v.all { it is String }) STRING_SET else null
            else -> null
        }
    }
}

/**
 * Một **họ khoá dựng động** đi theo hồ sơ như MỘT khối (spec §11.4.2) — ví dụ `cast_geometry` = mọi
 * `config_{size,overscan,density,bounds}_<gói>[__L|R<pct>]` của `simple_cast_prefs`.
 *
 * Họ không liệt kê được bằng tên (tên gói do máy quyết), nên ảnh chụp mang một **mốc có mặt** [marker]: có mốc ⇒
 * ảnh này BIẾT cả họ (khoá vắng = phải xoá); không mốc ⇒ ảnh này chưa từng có họ (không được chạm gì).
 *
 * @param owns khoá nào thuộc họ (neo hai đầu — khoá lạ mang tiền tố không phải của họ).
 * @param valueOk giá trị của một khoá họ có hợp lệ không (bộ kiểm trước shell, VC-R4).
 */
class SnapshotFamily(
    val id: String,
    val file: String,
    val prefixes: List<String>,
    val owns: (String) -> Boolean,
    val valueOk: (key: String, value: String) -> Boolean,
) {
    /** Mốc có mặt trong ảnh chụp. Tiền tố `@` không bao giờ là khoá prefs thật (bài canh `ProfileScopeClusterTest`). */
    val marker: String get() = ClusterSnapshotPlan.FAMILY_MARKER_PREFIX + id

    override fun toString(): String = "SnapshotFamily($id @ $file)"
}

/**
 * ═══ V-CLUSTER · VC-R2/R8 — CHỤP · ÁP · LÀM SẠCH ảnh chụp ClusterNav: phép thuần, tầng `:app` chỉ đổ vào `Editor` ═══
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §11.4.2 · §11.4.7. Thuần Kotlin ⇒ kiểm off-car. Trước bản này
 * logic chụp–áp nằm trọn trong `WorkspacePrefsProfile.kt` (`:app`, không dựng được `SharedPreferences` trong JVM),
 * tức mọi hợp đồng của nó chỉ được canh bằng **đọc chữ**. Ba thứ mới (họ tiền tố hai chiều, khoá hoãn, kiểm kiểu)
 * đều là chỗ sai im lặng mất cấu hình người lái — nên chúng phải là phép tính có test chạy thật.
 *
 * ## Ba hợp đồng
 *  1. **Khoá cố định**: giữ nguyên hợp đồng S4 — vắng ⇒ `null` tường minh ⇒ lượt áp XOÁ khoá đó.
 *  2. **Họ tiền tố**: có mốc ⇒ tệp sống sau khi áp bằng ĐÚNG họ của ảnh (khoá thừa bị xoá — refute C4, không thì cấu
 *     hình từng app của A tràn sang B); không mốc ⇒ không chạm (hồ sơ cũ chưa có họ giữ nguyên tệp sống).
 *  3. **Khoá hoãn** (`cast_enabled`): lượt áp KHÔNG BAO GIỜ ghi khoá sống — chỉ ghi/xoá bản chờ ([CastEnableDeferral]).
 */
object ClusterSnapshotPlan {

    /** Tiền tố dành riêng cho mốc họ trong ảnh chụp. */
    const val FAMILY_MARKER_PREFIX = "@family:"

    /** Ảnh chụp (hoặc ảnh đã làm sạch) + các khoá đã bỏ vì hỏng — chỗ gọi GHI LOG, không ném. */
    data class Shot(val values: Map<String, Any?>, val dropped: List<String>)

    /**
     * Lượt ghi vào tệp sống: `writes[k] = null` ⇒ XOÁ `k`; còn lại ⇒ ghi đúng kiểu. [dropped] để ghi log.
     *
     * FIX286 · PI5 — [deferred] = quyết định của từng khoá hoãn (khoá sống → [CastEnableDeferral.OnApply]) để dòng log
     * đổi hồ sơ nói được `cast=SetPending(…)|ClearPending` kể cả khi lượt áp KHÔNG ghi gì (ClearPending lúc không có bản chờ).
     */
    data class Edit(
        val writes: Map<String, Any?>,
        val dropped: List<String>,
        val deferred: Map<String, CastEnableDeferral.OnApply> = emptyMap(),
    )

    /**
     * FIX286 · PI1/PI2 — kết quả lượt **merge một lần lúc NHẬP** ([mergeImport]).
     *
     * Bản ghi = khoá họ bỏ tiền tố trường (`config_density_<gói>__L30` → `<gói>__L30`): một (gói, biến thể), bốn trường
     * đi cùng nhau (spec K4). [fromFile] = bản ghi lấy từ TỆP · [fromCar] = bản ghi chép từ tệp sống của XE NHẬN ·
     * [replacing] = bản ghi của tệp mà xe nhận đang có với giá trị KHÁC (đổi sang hồ sơ này sẽ thay khung đó) ·
     * [deferredFromCar] = khoá hoãn mà tệp vắng/null nên lấy giá trị ĐANG CHẠY của xe. [fileHadFamily] = tệp có mốc họ
     * (xuất từ ≥ 2.84). [dropped] = khoá của xe có giá trị hỏng — không chép, chỗ gọi ghi log.
     */
    data class ImportMerge(
        val values: Map<String, Any?>,
        val fileHadFamily: Boolean,
        val fromFile: Set<String>,
        val fromCar: Set<String>,
        val replacing: Set<String>,
        val deferredFromCar: Set<String>,
        val dropped: List<String>,
    )

    /** Khoá [key] có thuộc phạm vi ảnh chụp của một tệp không (khoá cố định · khoá họ · mốc họ). */
    fun inScope(key: String, fixedKeys: Collection<String>, families: Collection<SnapshotFamily>): Boolean =
        key in fixedKeys || families.any { key == it.marker || it.owns(key) }

    /**
     * Ảnh chụp một tệp. [fixed] = khoá cố định → giá trị sống (chỗ gọi dựng bằng `associateWith`, GIỮ khoá vắng dưới
     * dạng `null`); [live] = toàn bộ tệp sống (để đọc họ + bản chờ).
     *
     * Khoá hoãn: giá trị ghi vào ảnh là lựa chọn của hồ sơ = bản chờ nếu có, không thì giá trị sống. Khoá họ có giá trị
     * hỏng KHÔNG vào ảnh (vào [Shot.dropped]) — ảnh chụp là thứ đi ra tệp xuất, không được mang rác đi xa hơn.
     */
    fun snapshot(
        fixed: Map<String, Any?>,
        live: Map<String, Any?>,
        families: Collection<SnapshotFamily>,
        deferred: Map<String, String>,
    ): Shot {
        val out = LinkedHashMap(fixed)
        val dropped = mutableListOf<String>()
        deferred.forEach { (liveKey, pendingKey) ->
            if (liveKey in out) out[liveKey] = CastEnableDeferral.desiredForSnapshot(live[liveKey], live[pendingKey])
        }
        families.forEach { f ->
            live.forEach { (k, v) ->
                if (!f.owns(k)) return@forEach
                if (v is String && f.valueOk(k, v)) out[k] = v else dropped += k
            }
            out[f.marker] = true
        }
        return Shot(out, dropped)
    }

    /**
     * Lượt áp ảnh [shot] (đã lọc bằng [inScope]) lên tệp sống [live].
     *
     * Kiểm KIỂU trước khi ghi (VC-R8, vá lỗi có sẵn [P1]): kiểu khai sẵn ở [declaredTypes] thắng; không khai thì kiểu
     * phải bằng kiểu của giá trị SỐNG (nếu tệp sống đang có khoá). Sai kiểu ⇒ bỏ, không ghi — một ảnh độc đặt `enabled`
     * thành chuỗi là `ClassCastException` trong dịch vụ đang chạy trên đường.
     */
    fun apply(
        live: Map<String, Any?>,
        shot: Map<String, Any?>,
        fixedKeys: Collection<String>,
        families: Collection<SnapshotFamily>,
        declaredTypes: Map<String, PrefType>,
        deferred: Map<String, String>,
    ): Edit {
        val writes = LinkedHashMap<String, Any?>()
        val dropped = mutableListOf<String>()
        val decisions = LinkedHashMap<String, CastEnableDeferral.OnApply>()
        shot.forEach { (k, v) ->
            if (k !in fixedKeys) return@forEach
            val pendingKey = deferred[k]
            if (pendingKey != null) {
                decisions[k] = deferPending(live, k, pendingKey, v, writes, dropped)
                return@forEach
            }
            if (v == null) { writes[k] = null; return@forEach }
            val actual = PrefType.of(v)
            val expected = declaredTypes[k] ?: PrefType.of(live[k])
            if (actual == null || (expected != null && actual != expected)) {
                dropped += "$k (kiểu $actual, cần $expected)"
                return@forEach
            }
            writes[k] = v
        }
        families.forEach { f ->
            if (shot[f.marker] != true) return@forEach
            val want = LinkedHashMap<String, String>()
            shot.forEach { (k, v) ->
                if (!f.owns(k)) return@forEach
                if (v is String && f.valueOk(k, v)) want[k] = v else dropped += k
            }
            live.keys.filter { f.owns(it) && it !in want }.forEach { writes[it] = null }
            want.forEach { (k, v) -> if (live[k] != v) writes[k] = v }
        }
        return Edit(writes, dropped, decisions)
    }

    /**
     * FIX286 · PI1/PI2 (owner 03/10 *"2 theo đề xuất"*) — **merge một lần lúc NHẬP**: ảnh chụp [shot] (đã [sanitize]) của
     * tệp nhập + tệp sống [live] của xe nhận ⇒ ảnh chụp ghi cho hồ sơ MỚI.
     *
     *  1. Họ có bản ghi trong tệp ⇒ **tệp thắng** (cả bản ghi, không trộn trường của xe vào).
     *  2. Bản ghi tệp KHÔNG có mà xe nhận có ⇒ chép của xe (từng giá trị qua `valueOk`; hỏng ⇒ [ImportMerge.dropped]).
     *  3. Tệp không có mốc (≤ 2.83) ⇒ (2) cho mọi bản ghi ⇒ toàn họ của xe; ảnh kết quả LUÔN mang mốc.
     *  4. Khoá hoãn ([deferred], phải là khoá cố định của tệp này) vắng hoặc `null` trong tệp ⇒ giá trị ĐANG CHẠY của xe
     *     (khoá sống, KHÔNG phải bản chờ — đổi sang hồ sơ nhập không được tự đẻ ra một lượt bật/tắt chiếu). Có giá trị ⇒
     *     tệp thắng, vẫn đi đường hoãn [CastEnableDeferral] lúc áp (VC-R5 không đổi).
     *
     * ⚠ Đây là nới refute C4 **có chủ ý, CHỈ ở lượt nhập**: khung của hồ sơ đang dùng lúc nhập được chép MỘT lần sang hồ
     * sơ nhập. Cơ chế phân biệt với lượt đổi hồ sơ thường: hàm này chỉ có MỘT chỗ gọi (`WorkspacePrefs.importProfile`) và
     * chỉ đổi ẢNH CHỤP ghi cho hồ sơ mới; [apply] (mọi lượt đổi hồ sơ) không đổi byte ⇒ đổi A ⇄ B sau đó vẫn hai chiều.
     */
    fun mergeImport(
        shot: Map<String, Any?>,
        live: Map<String, Any?>,
        fixedKeys: Collection<String>,
        families: Collection<SnapshotFamily>,
        deferred: Map<String, String>,
    ): ImportMerge {
        val out = LinkedHashMap(shot)
        val dropped = mutableListOf<String>()
        val fromFile = LinkedHashSet<String>()
        val fromCar = LinkedHashSet<String>()
        val replacing = LinkedHashSet<String>()
        var fileHadFamily = false
        families.forEach { f ->
            fileHadFamily = fileHadFamily || shot[f.marker] == true
            val fileRecords = recordsOf(f, shot)
            val carRecords = recordsOf(f, live)
            fromFile += fileRecords.keys
            fileRecords.forEach { (rec, fields) -> carRecords[rec]?.let { if (it != fields) replacing += rec } }
            carRecords.filterKeys { it !in fileRecords }.forEach { (rec, fields) ->
                fields.forEach { (k, v) -> if (v is String && f.valueOk(k, v)) out[k] = v else dropped += k }
                if (fields.keys.any { out.containsKey(it) }) fromCar += rec
            }
            out[f.marker] = true
        }
        val deferredFromCar = LinkedHashSet<String>()
        deferred.keys.filter { it in fixedKeys && shot[it] == null }.forEach { liveKey ->
            // Giá trị ĐANG CHẠY; vắng / sai kiểu ⇒ `null` = mặc định — đúng thứ `CastEnableDeferral.onApply` coi là hiệu lực.
            out[liveKey] = live[liveKey] as? Boolean
            deferredFromCar += liveKey
        }
        return ImportMerge(out, fileHadFamily, fromFile, fromCar, replacing, deferredFromCar, dropped)
    }

    /** Bản ghi của họ [f] trong [values]: (khoá bỏ tiền tố trường) → các khoá thuộc bản ghi đó với giá trị thô. */
    private fun recordsOf(f: SnapshotFamily, values: Map<String, Any?>): Map<String, Map<String, Any?>> {
        val out = LinkedHashMap<String, MutableMap<String, Any?>>()
        values.forEach { (k, v) ->
            if (!f.owns(k)) return@forEach
            val prefix = f.prefixes.firstOrNull { k.startsWith(it) } ?: return@forEach
            out.getOrPut(k.removePrefix(prefix)) { LinkedHashMap() }[k] = v
        }
        return out
    }

    /**
     * FIX286 · PI5 — một dòng tóm tắt lượt áp của MỘT tệp cho log đổi hồ sơ: `cast=<quyết định>` · khung ghi N · xoá M ·
     * bỏ K. `null` = tệp không có họ lẫn khoá hoãn nào ⇒ không có gì đáng nói (bớt rác log ở các tệp khác).
     */
    fun describe(edit: Edit, families: Collection<SnapshotFamily>, deferred: Map<String, String>): String? {
        if (families.isEmpty() && edit.deferred.isEmpty()) return null
        val familyWrites = edit.writes.filterKeys { k -> families.any { it.owns(k) } }
        val cast = if (edit.deferred.isEmpty()) "-" else edit.deferred.values.joinToString(",")
        val pendingWrites = edit.writes.keys.count { it in deferred.values }
        return "cast=$cast (ghi khoá chờ $pendingWrites) · khung ghi ${familyWrites.count { it.value != null }} · " +
            "xoá ${familyWrites.count { it.value == null }} · bỏ ${edit.dropped.size}"
    }

    /**
     * Lớp 1 — làm sạch ảnh chụp đến từ **tệp nhập** trước khi nó chạm đĩa. Giữ: khoá cố định đúng kiểu khai sẵn (hoặc
     * không khai) · mốc họ mang đúng `true` · khoá họ hợp lệ với giá trị hợp lệ. Mọi thứ khác ⇒ [Shot.dropped].
     */
    fun sanitize(
        shot: Map<String, Any?>,
        fixedKeys: Collection<String>,
        families: Collection<SnapshotFamily>,
        declaredTypes: Map<String, PrefType>,
    ): Shot {
        val out = LinkedHashMap<String, Any?>()
        val dropped = mutableListOf<String>()
        shot.forEach { (k, v) ->
            val family = families.firstOrNull { it.owns(k) }
            val ok = when {
                k in fixedKeys -> v == null || PrefType.of(v).let { t -> t != null && (declaredTypes[k] ?: t) == t }
                families.any { it.marker == k } -> v == true
                family != null -> v is String && family.valueOk(k, v)
                else -> false
            }
            if (ok) out[k] = v else dropped += k
        }
        return Shot(out, dropped)
    }

    private fun deferPending(
        live: Map<String, Any?>,
        liveKey: String,
        pendingKey: String,
        desired: Any?,
        writes: MutableMap<String, Any?>,
        dropped: MutableList<String>,
    ): CastEnableDeferral.OnApply {
        val d = CastEnableDeferral.onApply(live[liveKey], desired)
        when (d) {
            is CastEnableDeferral.OnApply.SetPending -> if (live[pendingKey] != d.on) writes[pendingKey] = d.on
            CastEnableDeferral.OnApply.ClearPending -> if (pendingKey in live) writes[pendingKey] = null
            is CastEnableDeferral.OnApply.Drop -> dropped += d.reason
        }
        return d
    }
}
