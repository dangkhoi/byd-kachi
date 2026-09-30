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

    /** Lượt ghi vào tệp sống: `writes[k] = null` ⇒ XOÁ `k`; còn lại ⇒ ghi đúng kiểu. [dropped] để ghi log. */
    data class Edit(val writes: Map<String, Any?>, val dropped: List<String>)

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
        shot.forEach { (k, v) ->
            if (k !in fixedKeys) return@forEach
            val pendingKey = deferred[k]
            if (pendingKey != null) {
                deferPending(live, k, pendingKey, v, writes, dropped)
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
        return Edit(writes, dropped)
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
    ) {
        when (val d = CastEnableDeferral.onApply(live[liveKey], desired)) {
            is CastEnableDeferral.OnApply.SetPending -> if (live[pendingKey] != d.on) writes[pendingKey] = d.on
            CastEnableDeferral.OnApply.ClearPending -> if (pendingKey in live) writes[pendingKey] = null
            is CastEnableDeferral.OnApply.Drop -> dropped += d.reason
        }
    }
}
